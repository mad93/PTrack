package cat.ptrack;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.pdf.PdfRenderer;
import android.os.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** Dependency-free on-device smoke test. It never uses the production database. */
public final class SmokeInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle arguments){super.onCreate(arguments);start();}
    private void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    @Override public void onStart(){
        Bundle result=new Bundle();
        try{
            Context target=getTargetContext();
            checkConsumption(target);
            Context isolated=new ContextWrapper(target){
                @Override public File getFilesDir(){File dir=new File(super.getCacheDir(),"smoke-reports");dir.mkdirs();return dir;}
                @Override public SharedPreferences getSharedPreferences(String name,int mode){return super.getSharedPreferences("smoke-"+name,mode);}
            };
            target.deleteDatabase("smoke.db");
            Profile profile=new Profile(isolated);
            require(profile.signals.containsKey("current")&&profile.signals.containsKey("soc")&&!profile.rawSignals.isEmpty(),"Bundled vehicle profile");
            Store store=new Store(isolated,"smoke.db");long id=store.begin(true);
            store.getWritableDatabase().beginTransaction();
            try{
                long epoch=1730000000000L;
                for(int i=0;i<720;i++)store.add(id,new Sample(epoch+i*2000,60-i*20.0/719,370,100*Math.sin(i/23.0),60+20*Math.sin(i/31.0),41.0,2.0,150+40*Math.sin(i/53.0),"{\"simulation\":true}"));
                store.getWritableDatabase().setTransactionSuccessful();
            }finally{store.getWritableDatabase().endTransaction();}
            store.finish(id);store.close();
            store=new Store(isolated,"smoke.db");require(store.samples(id).size()==720,"SQLite persistence");
            TripStats stats=new TripStats();store.samples(id).forEach(stats::add);require(Math.abs(stats.endSoc-40)<0.001,"Final SOC");
            long interrupted=store.begin(true);store.recover();
            try(var cursor=store.trips()){require(cursor.moveToFirst()&&cursor.getLong(0)==interrupted&&cursor.getString(4).equals("interrupted"),"Crash recovery");}
            for(String language:new String[]{"ca","es","en"}){
                isolated.getSharedPreferences("settings",0).edit().putString("lang",language).commit();
                File pdf=new Report(isolated).generate(store,id);require(pdf.length()>1000,"PDF content");
                try(var input=new FileInputStream(pdf)){byte[] header=new byte[5];require(input.read(header)==5&&new String(header,StandardCharsets.US_ASCII).equals("%PDF-"),"PDF header");}
                try(var descriptor=ParcelFileDescriptor.open(pdf,ParcelFileDescriptor.MODE_READ_ONLY);var renderer=new PdfRenderer(descriptor)){
                    require(renderer.getPageCount()==2,"PDF page count");
                    for(int p=0;p<2;p++)try(var page=renderer.openPage(p)){
                        require(page.getWidth()==595&&page.getHeight()==842,"A4 size");
                        Bitmap image=Bitmap.createBitmap(1190,1684,Bitmap.Config.ARGB_8888);image.eraseColor(Color.WHITE);page.render(image,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                        try(var out=new FileOutputStream(new File(isolated.getFilesDir(),"report-"+language+"-"+(p+1)+".png"))){image.compress(Bitmap.CompressFormat.PNG,100,out);}image.recycle();
                    }
                }
            }
            store.close();
            Activity activity=startActivitySync(new Intent(target,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();require(activity!=null,"Activity launch");runOnMainSync(activity::finish);
            result.putString("stream","PASS: consumption migration/rebuild/query, SQLite persistence, interrupted-trip recovery, SOC, PDFs in ca/es/en, rendering and activity launch\n");finish(Activity.RESULT_OK,result);
        }catch(Throwable failure){result.putString("stream","FAIL: "+failure+"\n");finish(Activity.RESULT_CANCELED,result);}
    }
    private void checkConsumption(Context context) {
        String name="consumption-smoke.db";
        context.deleteDatabase(name);
        // Exact v1 tables: opening Store must add the model without losing any original rows.
        try(var db=context.openOrCreateDatabase(name,0,null)) {
            db.execSQL("CREATE TABLE trips(id INTEGER PRIMARY KEY, start INTEGER NOT NULL, end INTEGER, demo INTEGER NOT NULL, status TEXT NOT NULL)");
            db.execSQL("CREATE TABLE samples(id INTEGER PRIMARY KEY, trip INTEGER NOT NULL, time INTEGER NOT NULL, soc REAL, voltage REAL, current REAL, speed REAL, lat REAL, lon REAL, altitude REAL, raw TEXT)");
            db.execSQL("CREATE INDEX sample_trip ON samples(trip,time)");
            db.execSQL("CREATE TABLE readings(id INTEGER PRIMARY KEY, trip INTEGER NOT NULL, time INTEGER NOT NULL, signal TEXT, value REAL, raw TEXT)");
            db.execSQL("CREATE INDEX reading_trip ON readings(trip,time)");
            db.execSQL("INSERT INTO trips VALUES(1,1000,2000,0,'complete')");
            db.execSQL("INSERT INTO samples(trip,time,raw) VALUES(1,1000,'preserve-null')");
            db.execSQL("INSERT INTO readings(trip,time,raw) VALUES(1,1000,'preserve-reading')");
            db.setVersion(1);
        }
        try(Store store=new Store(context,name)) {
            var db=store.getWritableDatabase();
            require(db.getVersion()==2,"Schema migration");
            try(var c=db.rawQuery("SELECT soc,raw FROM samples WHERE trip=1",null)) {
                require(c.moveToFirst()&&c.isNull(0)&&c.getString(1).equals("preserve-null"),"Migration preserves samples and SQL NULL");
            }
            try(var c=db.rawQuery("SELECT raw FROM readings",null)) {
                require(c.moveToFirst()&&c.getString(0).equals("preserve-reading"),"Migration preserves readings");
            }
            for(int t=0;t<5;t++) {
                long trip=store.begin(t==3);
                for(int i=0;i<=120;i++) {
                    double km=60*i*5/3600.0;
                    store.add(trip,new Sample(1000+i*5000L,60,400,30,60,km/6371*180/Math.PI,0.0,null,"original"));
                }
                if(t!=4)store.finish(trip); // Three real complete trips, one demo, one recording.
            }
            ConsumptionRepository repository=new ConsumptionRepository(store);
            var estimate=repository.estimate(60,ConsumptionModel.Terrain.UNKNOWN);
            require(estimate.trips()==3&&estimate.validIntervals()==360,"Only completed real trips qualify");
            require(Math.abs(estimate.kwhPer100Km()-20)<1e-8&&Math.abs(estimate.distanceKm()-30)<1e-8,"Persisted observed consumption");
            repository.recalculate();
            require(repository.estimate(60,ConsumptionModel.Terrain.UNKNOWN).validIntervals()==360,"Rebuild is idempotent");
            require(repository.estimate(100,ConsumptionModel.Terrain.UNKNOWN).kwhPer100Km()==null,"No fabricated speed estimate");
            // A newly completed trip appears automatically on the next query.
            try(var c=db.rawQuery("SELECT id FROM trips WHERE status='recording'",null)) {require(c.moveToFirst(),"Open trip");store.finish(c.getLong(0));}
            require(repository.estimate(60,ConsumptionModel.Terrain.UNKNOWN).trips()==4,"New history refresh");
            require(new ConsumptionRepository(store,20).estimate(60,ConsumptionModel.Terrain.UNKNOWN).trips()==4,"Rebinning");
            try(var c=db.rawQuery("SELECT COUNT(*) FROM samples WHERE raw='original'",null)) {require(c.moveToFirst()&&c.getInt(0)==605,"Analysis preserves originals");}
        }
        try(Store store=new Store(context,name);var c=store.getReadableDatabase().rawQuery("SELECT km FROM consumption_profiles",null)) {
            require(c.moveToFirst()&&Math.abs(c.getDouble(0)-40)<1e-8,"Learned profile survives reopen");
        }
        context.deleteDatabase(name);
    }
}
