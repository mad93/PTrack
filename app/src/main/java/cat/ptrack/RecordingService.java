package cat.ptrack;

import android.Manifest;
import android.app.*;
import android.bluetooth.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.location.*;
import android.os.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.JSONObject;

public final class RecordingService extends Service {
    public static volatile String state="disconnected";
    public static volatile Sample latest;
    public static volatile boolean running;
    public static volatile long activeTrip=-1;
    public static final Map<String,String> advanced=new ConcurrentHashMap<>();
    private volatile boolean stopping;
    private volatile BluetoothSocket socket;
    private volatile Location location;
    private Store store;
    private boolean demo;
    private long lastValid;
    private LocationManager locations;
    private PowerManager.WakeLock wakeLock;
    private Notification foregroundNotification;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final LocationListener listener=new LocationListener() { @Override public void onLocationChanged(Location l) { location=l; } };

    @Override public void onCreate() { super.onCreate(); store=new Store(this); }
    @Override public int onStartCommand(Intent intent,int flags,int startId) {
        if(intent!=null && "stop".equals(intent.getAction())) { stopping=true; closeSocket(); return START_NOT_STICKY; }
        if(intent!=null && "gps".equals(intent.getAction())){if(running)enableGps();return START_NOT_STICKY;}
        if(running) return START_NOT_STICKY;
        demo=intent!=null&&intent.getBooleanExtra("demo",false);
        Labels l=new Labels(this);
        var channel=new NotificationChannel("trip",l.t("Registre de viatge","Registro de viaje","Trip recording"),NotificationManager.IMPORTANCE_LOW);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
        var open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE);
        var stop=PendingIntent.getService(this,1,new Intent(this,RecordingService.class).setAction("stop"),PendingIntent.FLAG_IMMUTABLE);
        Notification notification=new Notification.Builder(this,"trip").setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("PTrack").setContentText(l.t("Connexió OBD i registre automàtic","Conexión OBD y registro automático","OBD connection and automatic recording"))
            .setContentIntent(open).addAction(new Notification.Action.Builder(null,l.t("Atura","Detener","Stop"),stop).build()).setOngoing(true).build();
        foregroundNotification=notification;
        boolean foreground=intent==null||!intent.getBooleanExtra("background",false);
        boolean gps=foreground && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED;
        try {
            if(Build.VERSION.SDK_INT>=29) startForeground(10,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE | (gps ? ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION : 0));
            else startForeground(10,notification);
        } catch(RuntimeException e) { setState("permission"); stopSelf(); return START_NOT_STICKY; }
        if(gps)enableGps();
        wakeLock=getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"PTrack:recording");wakeLock.setReferenceCounted(false);wakeLock.acquire(60000);
        running=true; stopping=false; latest=null; advanced.clear();
        worker.execute(this::run);
        return START_NOT_STICKY;
    }
    private void run() {
        try {
            store.recover();
            if(demo) { simulate(); return; }
            Profile profile=new Profile(this);
            List<Profile.Signal> extras=new ArrayList<>(profile.signals.values());
            extras.removeIf(s->Arrays.asList("current","voltage","soc","speed").contains(s.key()));
            int extra=0,rawIndex=0,cycles=0;
            while(!stopping) {
                try {
                    setState("connecting");
                    BluetoothAdapter adapter=getSystemService(BluetoothManager.class).getAdapter();
                    String address=getSharedPreferences("settings",0).getString("address","");
                    if(adapter==null||!adapter.isEnabled()||address.isEmpty()) throw new IllegalStateException("No adapter");
                    socket=adapter.getRemoteDevice(address).createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"));
                    BluetoothSocket connecting=socket;
                    Runnable timeout=()->{ try {connecting.close();} catch(Exception ignored){} };
                    handler.postDelayed(timeout,12000);
                    try { socket.connect(); } finally { handler.removeCallbacks(timeout); }
                    ElmProtocol elm=new ElmProtocol(socket.getInputStream(),socket.getOutputStream()); elm.initialize();
                    int bad=0;
                    while(!stopping) {
                        Map<String,Double> values=new HashMap<>(); JSONObject raw=new JSONObject();
                        long cycle=SystemClock.elapsedRealtime();
                        for(String key:Arrays.asList("current","voltage","soc","speed")) {
                            Profile.Signal signal=profile.signals.get(key);
                            String reply=elm.read(signal.request(),signal.response(),signal.did());
                            raw.put(key,reply); double value=signal.decode(reply); values.put(key,value);
                        }
                        boolean valid=values.values().stream().allMatch(Double::isFinite) && SystemClock.elapsedRealtime()-cycle<=8000;
                        if(valid) {
                            Location fix=location;
                            boolean fresh=fix!=null && SystemClock.elapsedRealtimeNanos()-fix.getElapsedRealtimeNanos()<10_000_000_000L && fix.hasAccuracy() && fix.getAccuracy()<=30;
                            boolean altitude=fresh && fix.hasAltitude() && fix.hasVerticalAccuracy()&&fix.getVerticalAccuracyMeters()<=10;
                            Sample s=new Sample(System.currentTimeMillis(),values.get("soc"),values.get("voltage"),values.get("current"),values.get("speed"),fresh?fix.getLatitude():null,fresh?fix.getLongitude():null,altitude?fix.getAltitude():null,raw.toString());
                            accept(s); bad=0; setState("connected");
                            if(!extras.isEmpty()) {
                                Profile.Signal signal=extras.get(extra++%extras.size());
                                String reply=elm.read(signal.request(),signal.response(),signal.did());
                                double value=signal.decode(reply); store.reading(activeTrip,signal.key(),value,reply);
                                advanced.put(signal.key(),new Labels(this).n(value,2)+" "+signal.unit());
                                if(signal.key().equals("charging")) VehicleTelemetry.LIVE.cable(value,SystemClock.elapsedRealtime());
                            }
                            if(++cycles%10==0&&!profile.rawSignals.isEmpty()){
                                Profile.RawSignal signal=profile.rawSignals.get(rawIndex++%profile.rawSignals.size());
                                String reply=elm.read(signal.request(),signal.response(),signal.did());
                                store.reading(activeTrip,signal.key(),null,reply);
                                advanced.put(signal.key(),signal.display(reply,new Labels(this)));
                            }
                        } else {
                            latest=null; advanced.clear(); setState("vehicle");
                            if(activeTrip>=0) store.reading(activeTrip,"invalid_cycle",null,raw.toString());
                            expire(); if(++bad>=3) throw new IllegalStateException("No valid vehicle data");
                        }
                        pause(250);
                    }
                } catch(SecurityException e) { setState("permission"); break; }
                catch(Exception e) { latest=null; advanced.clear(); if(!stopping) setState("missing"); }
                finally { closeSocket(); }
                if(!stopping) { for(int i=0;i<15&&!stopping;i++) { pause(1000); expire(); } }
            }
        } catch(Exception e) { setState("missing"); }
        finally {
            finishTrip(); latest=null; advanced.clear(); running=false;
            if(wakeLock!=null&&wakeLock.isHeld())wakeLock.release();
            if(!state.equals("permission")&&!state.equals("reportError")) setState("disconnected");
            handler.post(()->{stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();});
        }
    }
    private void accept(Sample s) {
        if(wakeLock!=null)wakeLock.acquire(60000);
        if(activeTrip<0) activeTrip=store.begin(demo);
        store.add(activeTrip,s); latest=s; lastValid=SystemClock.elapsedRealtime();
        VehicleTelemetry.LIVE.accept(activeTrip,s,demo,lastValid);
    }
    private void expire() { if(activeTrip>=0 && SystemClock.elapsedRealtime()-lastValid>=45000) finishTrip(); }
    private void finishTrip() {
        long id=activeTrip; activeTrip=-1;
        if(id<0) return;
        store.finish(id);
        VehicleTelemetry.LIVE.finish(id);
        try { new Report(this).generate(store,id); } catch(Exception e) { setState("reportError"); }
    }
    private void simulate() {
        long begin=SystemClock.elapsedRealtime();
        while(!stopping) {
            double t=(SystemClock.elapsedRealtime()-begin)/1000.0;
            double current=70*Math.sin(t/9)+20;
            accept(new Sample(System.currentTimeMillis(),Math.max(0,60-t/180),370,current,Math.max(0,45+30*Math.sin(t/16)),null,null,120+15*Math.sin(t/20),"{\"simulation\":true}"));
            setState("demo"); pause(1000);
        }
    }
    private void setState(String value) { state=value; VehicleTelemetry.LIVE.status(value); }
    private void pause(long ms) { try {Thread.sleep(ms);} catch(InterruptedException e) {Thread.currentThread().interrupt();stopping=true;} }
    private void closeSocket() { BluetoothSocket s=socket; socket=null; if(s!=null)try{s.close();}catch(Exception ignored){} }
    private void enableGps(){
        if(locations!=null||checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED)return;
        try{
            if(Build.VERSION.SDK_INT>=29)startForeground(10,foregroundNotification,ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE|ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
            locations=getSystemService(LocationManager.class);
            locations.requestLocationUpdates(LocationManager.GPS_PROVIDER,2000,2,listener,Looper.getMainLooper());
        }catch(RuntimeException e){locations=null;}
    }
    @Override public void onDestroy() {
        stopping=true; closeSocket();
        if(locations!=null)locations.removeUpdates(listener);
        worker.shutdown(); super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
