package cat.ptrack;

import java.util.concurrent.atomic.AtomicInteger;

/** Consumer, integration and freshness tests; no Android runtime, sockets or production DB. */
public final class TelemetryTests {
    private static int checks;
    private static void check(boolean value) { checks++; if(!value) throw new AssertionError("Check "+checks); }
    private static void near(double value,double expected) { check(Double.isFinite(value)&&Math.abs(value-expected)<1e-8); }
    private static Sample s(long time,double current,double speed) { return new Sample(time,70,400,current,speed,null,null,null,"{}"); }
    public static void main(String[] args) throws Exception {
        VehicleTelemetry shared=new VehicleTelemetry();
        check(shared.snapshot().current(0)==null);
        check(shared.snapshot().connection(0).equals("Desconnectat"));
        AtomicInteger phone=new AtomicInteger(),car=new AtomicInteger();
        Runnable mobile=phone::incrementAndGet,headUnit=car::incrementAndGet;
        shared.subscribe(mobile); shared.subscribe(headUnit);
        shared.status("connecting"); check(shared.snapshot().connection(1).equals("Connectant"));
        Sample first=s(1000,30,60);
        shared.accept(7,first,false,100);
        var initial=shared.snapshot();
        check(initial.sample()==first); near(first.power(),12); // 12,000 W -> 12 kW.
        near(initial.instantaneous(100),20); check(initial.flow(100).equals("Consum de bateria"));
        check(initial.current(10100)==first); check(initial.current(10101)==null);
        check(initial.current(99)==null); // Monotonic clock invalid/rewound.
        check(initial.connection(10101).equals("Dades no disponibles"));
        check(phone.get()==2 && car.get()==2);
        TripStats reference=new TripStats(); reference.add(first);
        for(int i=1;i<=60;i++) { Sample sample=s(1000+i*1000,30,60); shared.accept(7,sample,false,100+i*1000); reference.add(sample); }
        var total=shared.snapshot().totals();
        near(total.km(),reference.km); near(total.consumed(),reference.consumed);
        near(total.recovered(),reference.recovered); near(total.average(),reference.consumption()); near(total.average(),20);
        check(total.samples()==61); near(total.km(),1);
        // Car opens/closes without resetting the trip or changing the mobile consumer.
        shared.unsubscribe(headUnit); var before=shared.snapshot().totals();
        shared.accept(7,s(62000,30,60),false,61100);
        check(car.get()==62); check(phone.get()==63); check(shared.snapshot().totals().samples()==before.samples()+1);
        shared.subscribe(headUnit);
        shared.accept(7,s(63000,-30,60),false,62100);
        check(car.get()==63); check(shared.snapshot().totals().tripId()==7);
        check(shared.snapshot().flow(62100).startsWith("Regeneració")); near(shared.snapshot().instantaneous(62100),-20);
        var frozen=shared.snapshot();
        shared.status("missing"); check(shared.snapshot().current(62101)==null);
        check(shared.snapshot().connection(62101).equals("Desconnectat"));
        near(shared.snapshot().totals().consumed(),frozen.totals().consumed());
        shared.status("connecting"); shared.accept(7,s(90000,30,60),false,89100);
        check(shared.snapshot().current(89100)!=null); check(shared.snapshot().totals().tripId()==7);
        // The existing integration must omit the 27-second Bluetooth gap.
        near(shared.snapshot().totals().consumed(),frozen.totals().consumed());
        near(shared.snapshot().totals().recovered(),frozen.totals().recovered());
        long samples=shared.snapshot().totals().samples();
        shared.accept(7,s(90000,400,60),false,99999);
        shared.accept(7,s(89000,400,60),false,99999);
        check(shared.snapshot().totals().samples()==samples); check(shared.snapshot().receivedAt()==89100);
        shared.accept(7,s(91000,10,0),false,90100); check(Double.isNaN(shared.snapshot().instantaneous(90100)));
        shared.accept(7,s(92000,10,4.99),false,91100); check(Double.isNaN(shared.snapshot().instantaneous(91100)));
        shared.accept(7,s(93000,10,5),false,92100); near(shared.snapshot().instantaneous(92100),80);
        shared.accept(7,s(94000,-20,0),false,93100);
        check(shared.snapshot().flow(93100).contains("origen no confirmat"));
        shared.cable(1.0,93100); check(shared.snapshot().flow(93100).contains("Càrrega probable"));
        shared.accept(7,s(100000,-20,0),false,99100);
        check(!shared.snapshot().flow(103101).contains("Càrrega")); // Cable stale, sample still fresh.
        shared.cable(Double.NaN,99100); check(shared.snapshot().cable()==null);
        shared.accept(7,s(101000,Double.NaN,60),false,100100);
        check(shared.snapshot().current(100100)==null); check(shared.snapshot().state().equals("vehicle"));
        shared.accept(7,null,false,100100); check(shared.snapshot().current(100100)==null);
        shared.accept(7,s(102000,30,60),false,101100);
        shared.finish(7); check(!shared.snapshot().totals().active()); check(shared.snapshot().current(101100)==null);
        check(CarDashboard.primary(shared.snapshot(),101100).get(1).value().startsWith("--"));
        check(CarDashboard.details(shared.snapshot(),101100).get(1).value().startsWith("Darrer:"));
        shared.accept(8,s(103000,30,60),true,102100);
        check(shared.snapshot().simulation()); check(shared.snapshot().totals().samples()==1);
        near(shared.snapshot().totals().consumed(),0); check(Double.isNaN(shared.snapshot().totals().average()));
        check(CarDashboard.primary(shared.snapshot(),102100).get(2).detail().contains("SIMULACIÓ"));
        check(CarDashboard.primary(shared.snapshot(),112101).get(0).value().startsWith("-- kW"));
        check(CarDashboard.primary(shared.snapshot(),112101).get(2).value().contains("-- % SOC"));
        check(CarDashboard.primary(shared.snapshot(),112101).get(1).detail().contains("-- kWh"));
        check(CarDashboard.details(shared.snapshot(),112101).get(2).value().equals("-- V · -- A"));
        check(CarDashboard.primary(shared.snapshot(),112101).get(0).title().equals(CarDashboard.primary(shared.snapshot(),102100).get(0).title()));
        shared.subscribe(()->{throw new IllegalStateException("consumer broken");});
        shared.status("missing"); check(shared.snapshot().state().equals("missing"));
        // Immutable retained snapshot cannot change under a concurrent producer.
        var retained=shared.snapshot();
        Thread producer=new Thread(()->{for(int i=0;i<1000;i++)shared.accept(9,s(200000+i*1000,30,60),false,200000+i*1000);});
        producer.start();
        for(int i=0;i<1000;i++) { var v=shared.snapshot(); check(v.sample()==null || (v.totals().tripId()==9 && v.totals().samples()>=1)); }
        producer.join(); check(retained.simulation()); check(retained.totals().tripId()==8);
        check(shared.snapshot().totals().samples()==1000);
        near(new Profile.Signal("current","6B4","694","D816","[B4:B7]/64-1200","A",-500,500).decode("62D81600014000"),80);
        near(new Profile.Signal("current","6B4","694","D816","[B4:B7]/64-1200","A",-500,500).decode("62D81600011800"),-80);
        System.out.println("PASS: "+checks+" telemetry checks (shared publication, TripStats parity, freshness, gaps, signs, units, simulation and lifecycle independence)");
    }
}
