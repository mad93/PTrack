package cat.ptrack;

import java.util.*;
import static cat.ptrack.ConsumptionModel.*;

/** Pure Java, read-only analysis. Positive power is battery discharge. */
public final class ConsumptionLearner {
    public record Key(int speedBin, Terrain terrain) {}
    public static final class Bucket {
        public double km, consumedKwh, recoveredKwh;
        public long intervals;
        public int trips;
        public Estimate estimate() {
            Confidence c = km < 5 || intervals < 30 || trips < 3 ? Confidence.INSUFFICIENT
                    : km >= 50 && intervals >= 200 && trips >= 10 ? Confidence.HIGH
                    : km >= 20 && trips >= 5 ? Confidence.MEDIUM : Confidence.LOW;
            Double observed=km>0 ? 100*(consumedKwh-recoveredKwh)/km : null;
            return new Estimate(observed, c == Confidence.INSUFFICIENT ? null : observed,
                    intervals, km, trips, c);
        }
        void merge(Bucket b) { km+=b.km; consumedKwh+=b.consumedKwh; recoveredKwh+=b.recoveredKwh; intervals+=b.intervals; }
    }
    private final int binWidth;
    private final Map<Key,Bucket> buckets = new HashMap<>();
    public ConsumptionLearner(int binWidth) {
        if (binWidth < 1 || binWidth > 250) throw new IllegalArgumentException("binWidth: 1..250 km/h");
        this.binWidth=binWidth;
    }
    public int bin(double speed) { return (int)Math.floor(speed/binWidth); }
    public Map<Key,Bucket> buckets() { return Collections.unmodifiableMap(buckets); }
    public void addTrip(List<Sample> samples) {
        Map<Key,Bucket> trip = new HashMap<>();
        List<Sample> run = new ArrayList<>();
        Sample previous=null;
        for (Sample s : samples) {
            if (!valid(s)) { accumulate(run,trip); run.clear(); previous=null; continue; }
            if (previous!=null && !interval(previous,s)) { accumulate(run,trip); run.clear(); }
            run.add(s); previous=s;
        }
        accumulate(run,trip);
        trip.forEach((key,value)-> { Bucket b=buckets.computeIfAbsent(key,k->new Bucket()); b.merge(value); b.trips++; });
    }
    private static boolean valid(Sample s) {
        return s!=null && s.time()>0 && finite(s.soc(),0,100) && finite(s.voltage(),100,1000)
                && finite(s.current(),-1000,1000) && finite(s.speed(),5,250)
                && s.latitude()!=null && s.longitude()!=null
                && finite(s.latitude(),-90,90) && finite(s.longitude(),-180,180);
    }
    private static boolean finite(double x,double min,double max) { return Double.isFinite(x)&&x>=min&&x<=max; }
    private static boolean interval(Sample a, Sample b) {
        double dt=(b.time()-a.time())/1000.0;
        if (dt<=0 || dt>10 || Math.abs(b.speed()-a.speed())/3.6/dt>8
                || Math.abs(b.soc()-a.soc())>2+dt*0.05) return false;
        double km=(a.speed()+b.speed())*0.5*dt/3600;
        return gpsKm(a,b)<=km*1.5+0.06;
    }
    private static double gpsKm(Sample a, Sample b) {
        double lat=Math.toRadians(b.latitude()-a.latitude()), lon=Math.toRadians(b.longitude()-a.longitude());
        double h=Math.sin(lat/2)*Math.sin(lat/2)+Math.cos(Math.toRadians(a.latitude()))
                *Math.cos(Math.toRadians(b.latitude()))*Math.sin(lon/2)*Math.sin(lon/2);
        return 6371*2*Math.asin(Math.sqrt(Math.min(1,h)));
    }
    private void accumulate(List<Sample> run, Map<Key,Bucket> trip) {
        if (run.size()<2) return;
        double km=0,gps=0;
        boolean altitude=true;
        for (int i=0;i<run.size();i++) {
            Sample s=run.get(i);
            altitude &= s.altitude()!=null && finite(s.altitude(),-500,9000);
            if(i>0) {
                Sample a=run.get(i-1);
                km+=(a.speed()+s.speed())*0.5*(s.time()-a.time())/3600000.0;
                gps+=gpsKm(a,s);
                if (a.altitude()!=null && s.altitude()!=null && Math.abs(s.altitude()-a.altitude())>20) altitude=false;
            }
        }
        Sample first=run.get(0), last=run.get(run.size()-1);
        if(km<1 || last.time()-first.time()<60000 || gps<km*0.5 || gps>km*1.5+0.06) return;
        Terrain terrain=Terrain.UNKNOWN;
        if(altitude) {
            // Endpoints may each have up to 10 m vertical error. Ambiguous grades stay unknown.
            double delta=last.altitude()-first.altitude(), margin=km*1000*0.02;
            if(delta>margin+20) terrain=Terrain.UPHILL;
            else if(delta < -margin-20) terrain=Terrain.DOWNHILL;
            else if(Math.abs(delta)+20<=margin) terrain=Terrain.LEVEL;
        }
        for(int i=1;i<run.size();i++) integrate(run.get(i-1),run.get(i),terrain,trip);
    }
    private void integrate(Sample a, Sample b, Terrain terrain, Map<Key,Bucket> trip) {
        // Split at speed boundaries AND zero power, integrating the same linear signals as TripStats.
        TreeSet<Double> cuts=new TreeSet<>(Arrays.asList(0.0,1.0));
        if(a.speed()!=b.speed()) for(int edge=binWidth;edge<=250;edge+=binWidth) {
            double f=(edge-a.speed())/(b.speed()-a.speed()); if(f>0&&f<1) cuts.add(f);
        }
        if(a.power()*b.power()<0) cuts.add(-a.power()/(b.power()-a.power()));
        List<Double> points=new ArrayList<>(cuts);
        Set<Key> seen=new HashSet<>();
        for(int i=1;i<points.size();i++) {
            double lo=points.get(i-1),hi=points.get(i),mid=(lo+hi)/2;
            double hours=(b.time()-a.time())/3600000.0*(hi-lo);
            double speed=a.speed()+(b.speed()-a.speed())*mid;
            double power=a.power()+(b.power()-a.power())*mid;
            Key key=new Key(bin(speed),terrain);
            Bucket v=trip.computeIfAbsent(key,k->new Bucket());
            v.km+=speed*hours;
            if(power>=0) v.consumedKwh+=power*hours; else v.recoveredKwh-=power*hours;
            if(seen.add(key)) v.intervals++;
        }
    }
}
