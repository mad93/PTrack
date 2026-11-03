package cat.ptrack;

import android.Manifest;
import android.app.*;
import android.bluetooth.*;
import android.companion.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import android.util.JsonWriter;
import java.io.*;
import java.text.DateFormat;
import java.util.*;
import java.util.concurrent.*;

public final class MainActivity extends Activity {
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private Labels l;
    private Store store;
    private LinearLayout root,body;
    private TextView status,charge,advanced;
    private Gauge power,current;
    private long exportId=-1;
    private String exportType;
    private int page=0;
    private final Runnable refresh=new Runnable(){@Override public void run(){if(page==0)update();handler.postDelayed(this,750);}};

    @Override public void onCreate(Bundle state){super.onCreate(state);store=new Store(this);if(state!=null){exportId=state.getLong("exportId",-1);exportType=state.getString("exportType");page=state.getInt("page",0);}l=new Labels(this);build();}
    @Override protected void onResume(){
        super.onResume();handler.post(refresh);
        if(RecordingService.running)startService(new Intent(this,RecordingService.class).setAction("gps"));
        else if(getSharedPreferences("settings",0).getString("address","").isEmpty())RecordingService.state="unconfigured";
        else if(getSharedPreferences("settings",0).getBoolean("auto",false)&&(Build.VERSION.SDK_INT<31||checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED)){
            try{startForegroundService(new Intent(this,RecordingService.class));}catch(RuntimeException e){RecordingService.state="permission";}
        }
    }
    @Override protected void onSaveInstanceState(Bundle state){state.putLong("exportId",exportId);state.putString("exportType",exportType);state.putInt("page",page);super.onSaveInstanceState(state);}
    @Override protected void onPause(){handler.removeCallbacks(refresh);super.onPause();}
    @Override protected void onDestroy(){io.shutdown();super.onDestroy();}
    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density);}
    private TextView text(String s,int size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setPadding(dp(8),dp(8),dp(8),dp(8));return t;}
    private Button button(String s,Runnable action){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setOnClickListener(v->action.run());return b;}
    private void build(){
        l=new Labels(this);root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(14),dp(12),dp(14),0);root.setBackgroundColor(0xFF0A211C);
        getWindow().setStatusBarColor(0xFF0A211C);getWindow().setNavigationBarColor(0xFF0A211C);setContentView(root);
        root.addView(text("PTrack",30,0xFFBCF46A));root.addView(text("PEUGEOT E-2008 · 2020 · E-CMP",11,0xFF9DB5AA));
        LinearLayout tabs=new LinearLayout(this);
        String[] names={l.t("En directe","En directo","Live"),l.t("Viatges","Viajes","Trips"),l.t("Configuració","Ajustes","Settings")};
        for(int i=0;i<3;i++){final int tab=i;tabs.addView(button(names[i],()->{page=tab;showPage();}),new LinearLayout.LayoutParams(0,dp(52),1));}root.addView(tabs);
        ScrollView scroll=new ScrollView(this);body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));showPage();
    }
    private void showPage(){body.removeAllViews();if(page==0)live();else if(page==1)history();else settings();}
    private void live(){
        status=text("",15,0xFFBCF46A);body.addView(status);
        LinearLayout gauges=new LinearLayout(this);power=new Gauge(this,l.t("Potència","Potencia","Power"),"kW",-80,120);current=new Gauge(this,l.signal("current"),"A",-200,300);
        gauges.addView(power,new LinearLayout.LayoutParams(0,dp(210),1));gauges.addView(current,new LinearLayout.LayoutParams(0,dp(210),1));body.addView(gauges);
        charge=text("— %",38,Color.WHITE);charge.setGravity(Gravity.CENTER);body.addView(charge);
        TextView sign=text(l.t("+ consum   /   − energia cap a la bateria","+ consumo   /   − energía hacia la batería","+ discharge   /   − energy into battery"),12,0xFF9DB5AA);sign.setGravity(Gravity.CENTER);body.addView(sign);
        body.addView(button(l.t("Connecta OBD","Conectar OBD","Connect OBD"),this::connect));
        body.addView(button(l.t("Atura i desa el viatge","Detener y guardar viaje","Stop and save trip"),()->{getSharedPreferences("settings",0).edit().putBoolean("auto",false).apply();if(RecordingService.running)startService(new Intent(this,RecordingService.class).setAction("stop"));}));
        body.addView(text(l.t("TELEMETRIA DEL VEHICLE","TELEMETRÍA DEL VEHÍCULO","VEHICLE TELEMETRY"),12,0xFFBCF46A));advanced=text("",14,0xFFDBE8E0);body.addView(advanced);update();
    }
    private void update(){
        if(status==null||power==null)return;
        String key=RecordingService.state;Sample s=RecordingService.latest;
        // Never leave stale connected readings visible after the poller stalls.
        if(s!=null&&System.currentTimeMillis()-s.time()>10000){s=null;key="vehicle";}
        status.setText(l.status(key));power.value(s==null?null:s.power());current.value(s==null?null:s.current());charge.setText(s==null?"— %":l.n(s.soc(),1)+" %");
        StringBuilder detail=new StringBuilder();
        if(s!=null){detail.append(l.signal("speed")).append("     ").append(l.n(s.speed(),1)).append(" km/h\n");detail.append(l.signal("voltage")).append("     ").append(l.n(s.voltage(),1)).append(" V\n");}
        new TreeMap<>(RecordingService.advanced).forEach((k,v)->detail.append(l.signal(k)).append("     ").append(v).append('\n'));
        if(detail.length()==0)detail.append(l.t("Les dades apareixeran quan el vehicle respongui.","Los datos aparecerán cuando responda el vehículo.","Data appears when the vehicle responds."));
        advanced.setText(detail.toString());
    }
    private boolean permissions(){
        List<String> missing=new ArrayList<>();
        if(Build.VERSION.SDK_INT>=31&&checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED)missing.add(Manifest.permission.BLUETOOTH_CONNECT);
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){missing.add(Manifest.permission.ACCESS_FINE_LOCATION);missing.add(Manifest.permission.ACCESS_COARSE_LOCATION);}
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)missing.add(Manifest.permission.POST_NOTIFICATIONS);
        if(!missing.isEmpty()){requestPermissions(missing.toArray(new String[0]),30);return false;}return true;
    }
    private void connect(){
        if(RecordingService.running){toast(l.t("El servei ja està actiu","El servicio ya está activo","Service is already active"));return;}
        if(!permissions())return;
        String address=getSharedPreferences("settings",0).getString("address","");if(address.isEmpty()){chooseAdapter();return;}
        start(false);
    }
    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] results){super.onRequestPermissionsResult(requestCode,permissions,results);if(requestCode==30){if(Build.VERSION.SDK_INT<31||checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED){if(getSharedPreferences("settings",0).getString("address","").isEmpty())chooseAdapter();else start(false);}else toast(l.status("permission"));}}
    private void start(boolean demo){
        if(RecordingService.running)return;
        if(Build.VERSION.SDK_INT>=31&&checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED){permissions();return;}
        getSharedPreferences("settings",0).edit().putBoolean("auto",!demo).apply();
        try{startForegroundService(new Intent(this,RecordingService.class).putExtra("demo",demo));page=0;showPage();}catch(RuntimeException e){toast(l.status("permission"));}
    }
    private void chooseAdapter(){
        if(Build.VERSION.SDK_INT>=31&&checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED){permissions();return;}
        BluetoothAdapter adapter=getSystemService(BluetoothManager.class).getAdapter();
        if(adapter==null){toast(l.t("Bluetooth no disponible","Bluetooth no disponible","Bluetooth unavailable"));return;}
        if(!adapter.isEnabled()){startActivity(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));return;}
        List<BluetoothDevice> devices=new ArrayList<>(adapter.getBondedDevices());
        if(devices.isEmpty()){new AlertDialog.Builder(this).setMessage(l.t("Aparella primer l’ELM327 als ajustos Bluetooth d’Android.","Empareja primero el ELM327 en los ajustes Bluetooth de Android.","Pair the ELM327 in Android Bluetooth settings first.")).setPositiveButton("OK",(d,w)->startActivity(new Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS))).show();return;}
        String[] names=devices.stream().map(d->d.getName()+"\n"+d.getAddress()).toArray(String[]::new);
        new AlertDialog.Builder(this).setTitle(l.t("Tria l’ELM327 Bluetooth clàssic","Elige el ELM327 Bluetooth clásico","Select classic Bluetooth ELM327")).setItems(names,(d,which)->{getSharedPreferences("settings",0).edit().putString("address",devices.get(which).getAddress()).apply();start(false);}).show();
    }
    private void settings(){
        body.addView(text(l.t("Idioma de l’aplicació i dels informes","Idioma de la aplicación y de los informes","App and report language"),17,Color.WHITE));
        String[] codes={"ca","es","en"},names={"Català","Español","English"};for(int i=0;i<3;i++){String code=codes[i];body.addView(button(names[i],()->{getSharedPreferences("settings",0).edit().putString("lang",code).apply();build();}));}
        body.addView(button(l.t("Selecciona adaptador OBD","Seleccionar adaptador OBD","Select OBD adapter"),()->{if(!RecordingService.running)chooseAdapter();else toast(l.t("Atura abans el registre","Detén antes el registro","Stop recording first"));}));
        body.addView(button(l.t("Prova amb dades simulades","Probar con datos simulados","Try simulated data"),()->start(true)));
        body.addView(button(l.t("Associa l’OBD per a l’inici automàtic","Asociar OBD para inicio automático","Associate OBD for automatic start"),this::associate));
        Switch automatic=new Switch(this);automatic.setTextColor(Color.WHITE);automatic.setText(l.t("Registre automàtic en connectar","Registro automático al conectar","Record automatically on connection"));automatic.setChecked(getSharedPreferences("settings",0).getBoolean("auto",false));automatic.setOnCheckedChangeListener((b,on)->getSharedPreferences("settings",0).edit().putBoolean("auto",on).apply());body.addView(automatic);
        body.addView(text(l.t("Connexió: ELM327 Bluetooth clàssic (SPP). Activa el servei una vegada des del mòbil. Es reconnecta mentre és actiu i quan Android anuncia una connexió Bluetooth de l’adaptador seleccionat. Aparellar no implica una connexió activa. Algunes marques limiten l’execució en segon pla. No hi ha inici després de reiniciar el mòbil.","Conexión: ELM327 Bluetooth clásico (SPP). Activa el servicio una vez desde el móvil. Se reconecta mientras está activo y cuando Android anuncia una conexión Bluetooth del adaptador elegido. Emparejar no implica conexión activa. Algunas marcas limitan la ejecución en segundo plano. No se inicia tras reiniciar el móvil.","Connection: classic Bluetooth ELM327 (SPP). Start the service once on the phone. It retries while active and responds when Android announces a connection from the selected adapter. Pairing does not mean an active connection. Some manufacturers restrict background execution. No startup after reboot."),13,0xFFB3C8BD));
        body.addView(text(l.t("Android Auto: integració pendent de viabilitat. Les plantilles i categories oficials no garanteixen un panell OBD amb rellotges. Aquesta versió mostra el panell al mòbil.","Android Auto: integración pendiente de viabilidad. Las plantillas y categorías oficiales no garantizan un panel OBD con indicadores. Esta versión muestra el panel en el móvil.","Android Auto: integration subject to feasibility. Official templates and categories do not guarantee a custom OBD gauge dashboard. This version displays the dashboard on the phone."),13,0xFFB3C8BD));
        body.addView(text(l.t("Les dades es guarden al dispositiu, sense núvol. La desinstal·lació les elimina: exporta el registre JSON per conservar-les. El PDF es genera en tancar el viatge (45 s sense dades) o en aturar-lo. Un procés interromput conserva les mostres; el PDF es pot recuperar des de l’historial.","Los datos se guardan en el dispositivo, sin nube. Desinstalar los elimina: exporta el registro JSON para conservarlos. El PDF se genera al cerrar el viaje (45 s sin datos) o al detenerlo. Un proceso interrumpido conserva las muestras; el PDF se puede recuperar del historial.","Data stays on the device, without cloud storage. Uninstalling deletes it: export JSON to retain a copy. PDF is generated at trip closure (45 s without data) or on stop. Interrupted processes retain samples; regenerate the PDF from history."),13,0xFFB3C8BD));
        body.addView(button(l.t("Llicències i atribució","Licencias y atribución","Licenses and attribution"),()->{try(var in=getAssets().open("ESCO-LICENSE.txt")){new AlertDialog.Builder(this).setTitle("Esco EV OBD Scanner").setMessage("Car profile: Esa Pelttari, CC BY 4.0\nhttps://github.com/EsaHub/EscoEvObdScanner\nhttps://creativecommons.org/licenses/by/4.0/\n\n"+new String(Streams.bytes(in),java.nio.charset.StandardCharsets.UTF_8)).setPositiveButton("OK",null).show();}catch(IOException ignored){}}));
    }
    private void history(){
        body.addView(text(l.t("Viatges desats al mòbil","Viajes guardados en el móvil","Trips saved on phone"),20,Color.WHITE));
        if(!RecordingService.running)io.execute(()->{store.recover();});
        boolean any=false;
        try(Cursor c=store.trips()) { while(c.moveToNext()) {
            any=true;long id=c.getLong(0);boolean demo=c.getInt(3)==1;String state=c.getString(4);
            String name=DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT,l.locale).format(new Date(c.getLong(1)))+" · #"+id;
            if(demo)name+=" · "+l.t("SIMULACIÓ","SIMULACIÓN","SIMULATION");
            if(state.equals("recording"))name+=" · "+l.t("en curs","en curso","recording");
            if(state.equals("interrupted"))name+=" · "+l.t("interromput","interrumpido","interrupted");
            body.addView(button(name,()->trip(id)));
        }}
        if(!any)body.addView(text(l.t("Encara no hi ha viatges. Connecta l’OBD o prova el mode simulat.","Todavía no hay viajes. Conecta el OBD o prueba el modo simulado.","No trips yet. Connect the OBD or try simulation mode."),16,0xFFB3C8BD));
    }
    private void trip(long id){
        body.removeAllViews();body.addView(text(l.t("Carregant viatge…","Cargando viaje…","Loading trip…"),17,Color.WHITE));
        io.execute(()->{
            TripStats s=new TripStats();store.samples(id).forEach(s::add);
            String info=l.t("Càrrega","Carga","Charge")+": "+l.n(s.startSoc,1)+" → "+l.n(s.endSoc,1)+" %\n"+
                l.t("Distància","Distancia","Distance")+": "+l.n(s.km,2)+" km\n"+
                l.t("Velocitat mitjana","Velocidad media","Average speed")+": "+l.n(s.averageSpeed(),1)+" km/h\n"+
                l.t("Consum net","Consumo neto","Net consumption")+": "+l.n(s.consumption(),2)+" kWh/100 km\n"+
                l.t("Pujada / baixada GPS","Ascenso / descenso GPS","GPS ascent / descent")+": "+(s.hasAltitude?l.n(s.ascent,0)+" / "+l.n(s.descent,0)+" m":"—");
            runOnUiThread(()->{if(isFinishing()||page!=1)return;body.removeAllViews();body.addView(text("#"+id+" · "+(store.isDemo(id)?l.t("SIMULACIÓ","SIMULACIÓN","SIMULATION"):"Peugeot e-2008"),20,Color.WHITE));body.addView(text(info,18,0xFFDBE8E0));body.addView(button(l.t("Exporta informe PDF","Exportar informe PDF","Export PDF report"),()->export(id,"pdf")));body.addView(button(l.t("Exporta registre complet JSON","Exportar registro completo JSON","Export full JSON log"),()->export(id,"json")));body.addView(button(l.t("Torna als viatges","Volver a viajes","Back to trips"),this::historyReset));});
        });
    }
    private void historyReset(){body.removeAllViews();history();}
    private void associate(){
        String address=getSharedPreferences("settings",0).getString("address","");
        if(address.isEmpty()){chooseAdapter();return;}
        CompanionDeviceManager manager=getSystemService(CompanionDeviceManager.class);
        if(manager==null){toast(l.t("Associació no disponible","Asociación no disponible","Association unavailable"));return;}
        if(manager.getAssociations().contains(address)){observePresence(manager,address);return;}
        AssociationRequest request=new AssociationRequest.Builder().addDeviceFilter(new BluetoothDeviceFilter.Builder().setAddress(address).build()).setSingleDevice(true).build();
        manager.associate(request,new CompanionDeviceManager.Callback(){
            @Override public void onDeviceFound(IntentSender sender){try{startIntentSenderForResult(sender,43,null,0,0,0);}catch(IntentSender.SendIntentException e){toast(l.t("No s’ha pogut associar","No se ha podido asociar","Association failed"));}}
            @Override public void onFailure(CharSequence error){toast(l.t("No s’ha pogut associar l’OBD","No se ha podido asociar el OBD","OBD association failed"));}
        },handler);
    }
    private void observePresence(CompanionDeviceManager manager,String address){
        try{
            if(Build.VERSION.SDK_INT>=31)manager.startObservingDevicePresence(address);
            getSharedPreferences("settings",0).edit().putBoolean("auto",true).apply();
            toast(l.t("Inici automàtic activat","Inicio automático activado","Automatic start enabled"));
        }catch(Exception e){toast(l.t("No s’ha pogut activar la detecció","No se ha podido activar la detección","Presence detection could not be enabled"));}
    }
    private void export(long id,String type){
        if(id==RecordingService.activeTrip){toast(l.t("Atura el viatge abans d’exportar","Detén el viaje antes de exportar","Stop the trip before exporting"));return;}
        exportId=id;exportType=type;
        Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).setType(type.equals("pdf")?"application/pdf":"application/json").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,"ptrack-"+id+"."+type);startActivityForResult(intent,42);
    }
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==43&&result==RESULT_OK){observePresence(getSystemService(CompanionDeviceManager.class),getSharedPreferences("settings",0).getString("address",""));return;}if(request!=42||result!=RESULT_OK||data==null||data.getData()==null)return;Uri uri=data.getData();long id=exportId;String type=exportType;
        if(id<0||type==null){toast(l.t("Torna a iniciar l’exportació","Vuelve a iniciar la exportación","Start the export again"));return;}
        io.execute(()->{try(OutputStream out=getContentResolver().openOutputStream(uri,"wt")){
            if(out==null)throw new IOException();
            if(type.equals("pdf")){File file=new Report(this).generate(store,id);try(InputStream in=new FileInputStream(file)){Streams.copy(in,out);}}
            else exportJson(out,id);
            runOnUiThread(()->toast(l.t("Exportació completada","Exportación completada","Export complete")));
        }catch(Exception e){runOnUiThread(()->toast(l.t("No s’ha pogut exportar","No se ha podido exportar","Export failed")));}});
    }
    private void exportJson(OutputStream out,long id)throws IOException{
        JsonWriter writer=new JsonWriter(new OutputStreamWriter(out,java.nio.charset.StandardCharsets.UTF_8));writer.setIndent("  ");writer.beginObject().name("schema").value(1).name("tripId").value(id).name("simulation").value(store.isDemo(id)).name("vehicle").value("Peugeot e-2008 2020").name("profile").value("Stellantis e-CMP v4; Esa Pelttari CC-BY-4.0");
        for(String table:Arrays.asList("trips","samples","readings")){
            writer.name(table).beginArray();String where=table.equals("trips")?"id":"trip";
            try(Cursor c=store.getReadableDatabase().rawQuery("SELECT * FROM "+table+" WHERE "+where+"=?",new String[]{""+id})){
                while(c.moveToNext()){writer.beginObject();for(int i=0;i<c.getColumnCount();i++){writer.name(c.getColumnName(i));switch(c.getType(i)){case Cursor.FIELD_TYPE_NULL->writer.nullValue();case Cursor.FIELD_TYPE_INTEGER->writer.value(c.getLong(i));case Cursor.FIELD_TYPE_FLOAT->writer.value(c.getDouble(i));case Cursor.FIELD_TYPE_BLOB->writer.value(android.util.Base64.encodeToString(c.getBlob(i),android.util.Base64.NO_WRAP));case Cursor.FIELD_TYPE_STRING->writer.value(c.getString(i));default->throw new IOException("Unsupported database value");}}writer.endObject();}
            }writer.endArray();
        }writer.endObject();writer.flush();
    }
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
}
