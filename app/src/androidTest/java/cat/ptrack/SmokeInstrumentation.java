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
            result.putString("stream","PASS: SQLite persistence, interrupted-trip recovery, SOC, PDFs in ca/es/en, rendering and activity launch\n");finish(Activity.RESULT_OK,result);
        }catch(Throwable failure){result.putString("stream","FAIL: "+failure+"\n");finish(Activity.RESULT_CANCELED,result);}
    }
}
