package cat.ptrack;

import java.util.*;
import static cat.ptrack.ConsumptionModel.*;

public final class ConsumptionTests {
    private static int checks;
    private static void check(boolean v) { checks++; if(!v) throw new AssertionError("check "+checks); }
    private static void near(double a,double b) { check(Double.isFinite(a)&&Math.abs(a-b)<1e-7); }
    static List<Sample> trip(double speed,double power,int seconds,Double grade) {
        List<Sample> list=new ArrayList<>();
        for(int t=0;t<=seconds;t+=5) {
            double km=speed*t/3600;
            list.add(new Sample(1000+t*1000L,60,400,power*2.5,speed,km/6371*180/Math.PI,0.0,
                    grade==null?null:100+km*1000*grade,"{}"));
        }
        return list;
    }
    private static ConsumptionLearner.Bucket bucket(ConsumptionLearner m,int bin,Terrain t) {
        return m.buckets().get(new ConsumptionLearner.Key(bin,t));
    }
    public static void main(String[] args) {
        ConsumptionLearner m=new ConsumptionLearner(10);
        List<Sample> steady=trip(60,12,600,null);
        m.addTrip(steady);
        var b=bucket(m,6,Terrain.UNKNOWN);
        near(b.km,10); near(b.consumedKwh,2); near(b.recoveredKwh,0);
        check(b.intervals==120 && b.trips==1);
        check(b.estimate().kwhPer100Km()==null && b.estimate().status().equals("dades insuficients"));
        near(b.estimate().observedKwhPer100Km(),20);
        m.addTrip(steady); m.addTrip(steady);
        near(b.estimate().kwhPer100Km(),20); check(b.estimate().confidence()==Confidence.LOW);
        m.addTrip(steady);m.addTrip(steady);check(b.estimate().confidence()==Confidence.MEDIUM);
        for(int i=0;i<5;i++)m.addTrip(steady);
        check(b.estimate().confidence()==Confidence.HIGH); check(b.trips==10);
        m=new ConsumptionLearner(20); m.addTrip(trip(60,-6,600,null));
        near(bucket(m,3,Terrain.UNKNOWN).recoveredKwh,1);
        m=new ConsumptionLearner(10);
        m.addTrip(trip(60,12,30,null)); check(m.buckets().isEmpty());
        m.addTrip(trip(0,-12,600,null)); check(m.buckets().isEmpty());
        for(int mode=0;mode<9;mode++) {
            List<Sample> invalid=new ArrayList<>();
            for(Sample s:steady) invalid.add(new Sample(s.time(),mode==0?Double.NaN:s.soc(),mode==1?0:s.voltage(),
                    mode==2?2000:s.current(),mode==3?300:s.speed(),mode==4?null:mode==5?91:s.latitude(),
                    mode==6?null:s.longitude(),null,"{}"));
            if(mode==7) invalid.replaceAll(s->new Sample(s.time(),60,400,30,60,0.0,0.0,null,"{}"));
            if(mode==8) invalid.replaceAll(s->new Sample(s.time()*10,60,400,30,60,s.latitude(),s.longitude(),null,"{}"));
            ConsumptionLearner bad=new ConsumptionLearner(10); bad.addTrip(invalid); check(bad.buckets().isEmpty());
        }
        for(Terrain terrain:new Terrain[]{Terrain.LEVEL,Terrain.UPHILL,Terrain.DOWNHILL}) {
            m=new ConsumptionLearner(10); m.addTrip(trip(60,12,600,terrain==Terrain.LEVEL?0:terrain==Terrain.UPHILL?0.04:-0.04));
            check(bucket(m,6,terrain)!=null);
        }
        // Speed-bin and power-zero splitting must conserve both distance and signed energies.
        List<Sample> changing=new ArrayList<>(); double km=0;
        for(int i=0;i<=120;i++) {
            double speed=i%2==0?55:65;
            if(i>0)km+=60*5/3600.0;
            changing.add(new Sample(1000+i*5000L,60,400,i%2==0?30:-30,speed,km/6371*180/Math.PI,0.0,null,"{}"));
        }
        m=new ConsumptionLearner(10); m.addTrip(changing);
        double total=0,consumed=0,recovered=0;
        for(var v:m.buckets().values()){total+=v.km;consumed+=v.consumedKwh;recovered+=v.recoveredKwh;}
        near(total,10); near(consumed,0.5); near(recovered,0.5); check(m.buckets().size()==2);
        // A bad sample splits continuity; two short fragments cannot qualify together.
        List<Sample> fragmented=trip(60,12,90,null);
        Sample s=fragmented.get(9);
        fragmented.set(9,new Sample(s.time(),Double.NaN,400,30,60,s.latitude(),s.longitude(),null,"{}"));
        m=new ConsumptionLearner(10);m.addTrip(fragmented);check(m.buckets().isEmpty());
        // Reject duplicates, reversed clocks, outages, GPS jumps and SOC discontinuities.
        for(int mode=0;mode<5;mode++) {
            List<Sample> broken=trip(60,12,90,null);
            Sample a=broken.get(8),bad=broken.get(9);
            broken.set(9,new Sample(mode==0?a.time():mode==1?a.time()-1:mode==2?bad.time()+20000:bad.time(),
                    mode==3?90:60,400,30,60,mode==4?45.0:bad.latitude(),bad.longitude(),null,"{}"));
            ConsumptionLearner rejectedModel=new ConsumptionLearner(10);rejectedModel.addTrip(broken);
            check(rejectedModel.buckets().isEmpty());
        }
        m=new ConsumptionLearner(10);
        m.addTrip(trip(60,6,600,null));m.addTrip(trip(69,12,600,null));m.addTrip(trip(60,6,600,null));
        near(bucket(m,6,Terrain.UNKNOWN).estimate().kwhPer100Km(),400/31.5);
        // Ambiguous altitude must not be presented as known level terrain.
        m=new ConsumptionLearner(10);m.addTrip(trip(60,12,600,0.02));
        check(bucket(m,6,Terrain.UNKNOWN)!=null);
        boolean rejected=false;try{new ConsumptionLearner(0);}catch(IllegalArgumentException e){rejected=true;}check(rejected);
        System.out.println("PASS: "+checks+" consumption-learning assertions");
    }
}
