package cat.ptrack;

import android.content.Context;
import android.graphics.*;
import android.graphics.pdf.PdfDocument;
import java.io.*;
import java.text.DateFormat;
import java.util.*;
import java.util.function.ToDoubleFunction;

public final class Report {
    private static final Object REPORT_LOCK=new Object();
    private final Context context;
    private final Labels l;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    public Report(Context context) { this.context=context; l=new Labels(context); }
    public File file(long id) { return new File(new File(context.getFilesDir(),"reports"),"ptrack-"+id+"-"+l.locale.getLanguage()+".pdf"); }
    public File generate(Store store,long id) throws IOException {
        synchronized(REPORT_LOCK){return generateLocked(store,id);}
    }
    private File generateLocked(Store store,long id) throws IOException {
        List<Sample> samples=store.samples(id); TripStats stats=new TripStats(); samples.forEach(stats::add);
        File output=file(id); if(!output.getParentFile().isDirectory()&&!output.getParentFile().mkdirs()) throw new IOException("Report directory");
        File temporary=new File(output.getPath()+".tmp");
        PdfDocument document=new PdfDocument();
        try {
            PdfDocument.Page page=document.startPage(new PdfDocument.PageInfo.Builder(595,842,1).create()); Canvas c=page.getCanvas();
            title(c,id,store.isDemo(id));
            text(c,l.t("INFORME DE VIATGE","INFORME DE VIAJE","TRIP REPORT"),36,103,11,0xFF56746D);
            DateFormat date=DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT,l.locale);
            text(c,stats.count>0 ? date.format(new Date(stats.start))+" → "+date.format(new Date(stats.end)) : "—",36,130,11,Color.DKGRAY);
            String[][] rows={
                {l.t("Càrrega inicial / final","Carga inicial / final","Start / end charge"),l.n(stats.startSoc,1)+" % → "+l.n(stats.endSoc,1)+" %"},
                {l.t("Distància registrada","Distancia registrada","Recorded distance"),l.n(stats.km,2)+" km"},
                {l.t("Durada total","Duración total","Total duration"),l.n((stats.end-stats.start)/60000.0,1)+" min"},
                {l.t("Velocitat mitjana registrada","Velocidad media registrada","Recorded average speed"),l.n(stats.averageSpeed(),1)+" km/h"},
                {l.t("Consum mitjà net","Consumo medio neto","Net average consumption"),l.n(stats.consumption(),2)+" kWh/100 km"},
                {l.t("Energia consumida / recuperada","Energía consumida / recuperada","Consumed / recovered energy"),l.n(stats.consumed,3)+" / "+l.n(stats.recovered,3)+" kWh"},
                {l.t("Pujada / baixada estimada (GPS)","Ascenso / descenso estimado (GPS)","Estimated ascent / descent (GPS)"),stats.hasAltitude?l.n(stats.ascent,0)+" / "+l.n(stats.descent,0)+" m":"—"},
                {l.t("Cobertura de lectures","Cobertura de lecturas","Reading coverage"),l.n(stats.coveredSeconds/60,1)+" min · "+stats.count+" "+l.t("mostres","muestras","samples")}
            };
            int y=169;
            for(String[] row:rows){text(c,row[0],36,y,11,Color.DKGRAY);text(c,row[1],340,y,11,Color.BLACK);y+=31;}
            chart(c,samples,s->s.soc(),l.signal("soc"),"%",443,150,0,100);
            lines(c,new String[]{
                l.t("Vehicle: Peugeot e-2008 2020 · perfil Stellantis e-CMP.","Vehículo: Peugeot e-2008 2020 · perfil Stellantis e-CMP.","Vehicle: Peugeot e-2008 2020 · Stellantis e-CMP profile."),
                l.t("Potència = V × A / 1000. Signe +: consum; signe −: entrada a la bateria.","Potencia = V × A / 1000. Signo +: consumo; signo −: entrada a batería.","Power = V × A / 1000. Positive: discharge; negative: energy into battery."),
                l.t("Energia i distància: integració trapezoïdal de lectures OBD vàlides.","Energía y distancia: integración trapezoidal de lecturas OBD válidas.","Energy and distance: trapezoidal integration of valid OBD readings."),
                l.t("Els buits de més de 10 s no s’integren. La mitjana inclou les aturades registrades.","Los huecos de más de 10 s no se integran. La media incluye paradas registradas.","Gaps over 10 s are excluded. Average speed includes recorded stops."),
                l.t("Altitud GPS: precisió vertical ≤10 m; filtre de desnivell de 5 m. És una estimació.","Altitud GPS: precisión vertical ≤10 m; filtro de desnivel de 5 m. Es una estimación.","GPS altitude: vertical accuracy ≤10 m; 5 m elevation filter. This is an estimate."),
                l.t("— indica dada no disponible. Les dades del viatge es conserven al mòbil.","— indica dato no disponible. Los datos del viaje se guardan en el móvil.","— means unavailable. Trip data is retained on the phone.")
            },36,657);
            text(c,"Profile: Esa Pelttari · CC BY 4.0 · github.com/EsaHub/EscoEvObdScanner",36,780,8,Color.GRAY);
            footer(c,1);document.finishPage(page);
            page=document.startPage(new PdfDocument.PageInfo.Builder(595,842,2).create());c=page.getCanvas();title(c,id,store.isDemo(id));
            chart(c,samples,Sample::power,l.t("Potència de bateria","Potencia de batería","Battery power"),"kW",100,130,Double.NaN,Double.NaN);
            chart(c,samples,Sample::current,l.signal("current"),"A",270,130,Double.NaN,Double.NaN);
            chart(c,samples,Sample::speed,l.signal("speed"),"km/h",440,130,0,Double.NaN);
            chart(c,samples,s->s.altitude()==null?Double.NaN:s.altitude(),l.t("Altitud GPS","Altitud GPS","GPS altitude"),"m",610,115,Double.NaN,Double.NaN);
            footer(c,2);document.finishPage(page);
            try(var stream=new FileOutputStream(temporary)){document.writeTo(stream);stream.getFD().sync();}
        } finally { document.close(); }
        if(!temporary.renameTo(output)) throw new IOException("Cannot finalize PDF");
        return output;
    }
    private void title(Canvas c,long id,boolean demo) {
        text(c,"PTrack",36,52,26,0xFF123C33);text(c,"Peugeot e-2008 · #"+id,36,76,11,Color.DKGRAY);
        if(demo)text(c,l.t("SIMULACIÓ","SIMULACIÓN","SIMULATION"),420,52,13,0xFFAE4B20);
    }
    private void footer(Canvas c,int page) {
        text(c,"PTrack · "+l.t("Lectura OBD sense escriptures","Lectura OBD sin escrituras","Read-only OBD")+" · "+page+" / 2",36,813,9,Color.GRAY);
    }
    private void lines(Canvas c,String[] lines,int x,int y){for(String line:lines){text(c,line,x,y,9,Color.DKGRAY);y+=19;}}
    private void text(Canvas c,String text,float x,float y,float size,int color){paint.setStyle(Paint.Style.FILL);paint.setColor(color);paint.setTextSize(size);c.drawText(text,x,y,paint);}
    private void chart(Canvas c,List<Sample> samples,ToDoubleFunction<Sample> value,String name,String unit,int top,int height,double forcedMin,double forcedMax) {
        text(c,name+" ("+unit+")",36,top,12,0xFF123C33);
        float x=76,y=top+18,w=475,h=height-42;
        double min=Double.POSITIVE_INFINITY,max=Double.NEGATIVE_INFINITY;
        for(Sample s:samples){double v=value.applyAsDouble(s);if(Double.isFinite(v)){min=Math.min(min,v);max=Math.max(max,v);}}
        if(!Double.isFinite(min)){text(c,"—",x,y+30,13,Color.GRAY);return;}
        min=Double.isFinite(forcedMin)?forcedMin:Math.min(0,min);max=Double.isFinite(forcedMax)?forcedMax:Math.max(0,max);
        if(max==min)max=min+1;
        for(int i=0;i<=2;i++){float yy=y+h*i/2;paint.setColor(0xFFDCE5E1);paint.setStrokeWidth(.5f);c.drawLine(x,yy,x+w,yy,paint);text(c,l.n(max-(max-min)*i/2,1),36,yy+3,8,Color.GRAY);}
        long first=samples.get(0).time(),duration=Math.max(1,samples.get(samples.size()-1).time()-first);
        // Bucket extrema preserve short regenerative peaks, with at most 2 points per PDF pixel.
        int buckets=(int)w;double[] low=new double[buckets+1],high=new double[buckets+1];Arrays.fill(low,Double.POSITIVE_INFINITY);Arrays.fill(high,Double.NEGATIVE_INFINITY);
        for(Sample s:samples){double v=value.applyAsDouble(s);if(!Double.isFinite(v))continue;int b=(int)((s.time()-first)*(double)buckets/duration);low[b]=Math.min(low[b],v);high[b]=Math.max(high[b],v);}
        paint.setStrokeWidth(1.1f);paint.setColor(0xFF147D69);Float previousX=null,previousY=null;int previousBucket=-1;
        for(int b=0;b<=buckets;b++)if(Double.isFinite(low[b])){
            float xx=x+b*w/buckets,lo=(float)(y+h-(low[b]-min)/(max-min)*h),hi=(float)(y+h-(high[b]-min)/(max-min)*h);
            c.drawLine(xx,lo,xx,hi,paint);
            // No connecting line over missing data longer than 10 s.
            if(previousX!=null&&(b-previousBucket)*(double)duration/buckets<=10000)c.drawLine(previousX,previousY,xx,lo,paint);
            previousX=xx;previousY=hi;previousBucket=b;
        }
        text(c,"0 min",x,y+h+15,8,Color.GRAY);text(c,l.n(duration/120000.0,1)+" min",x+w/2-12,y+h+15,8,Color.GRAY);text(c,l.n(duration/60000.0,1)+" min",x+w-38,y+h+15,8,Color.GRAY);
    }
}
