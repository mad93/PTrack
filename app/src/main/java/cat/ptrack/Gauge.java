package cat.ptrack;

import android.content.Context;
import android.graphics.*;
import android.view.View;

public final class Gauge extends View {
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval=new RectF();
    private final Labels labels;
    private final String label,unit;
    private final double min,max;
    private Double value;
    public Gauge(Context c){this(c,"","",-80,120);}
    public Gauge(Context c,String label,String unit,double min,double max){super(c);this.label=label;this.unit=unit;this.min=min;this.max=max;labels=new Labels(c);}
    public void value(Double v){value=v;setContentDescription(label+" "+(v==null?"—":new Labels(getContext()).n(v,1))+" "+unit);invalidate();}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);float width=getWidth(),height=getHeight(),r=Math.min(width*.40f,height*.43f),cx=width/2,cy=height*.51f;
        oval.set(cx-r,cy-r,cx+r,cy+r);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(r*.12f);p.setStrokeCap(Paint.Cap.ROUND);p.setColor(0xFF25443D);canvas.drawArc(oval,150,240,false,p);
        if(min<0){p.setColor(0xFF319F9F);canvas.drawArc(oval,150,(float)(240*(-min)/(max-min)),false,p);}
        if(value!=null){double v=Math.max(min,Math.min(max,value));float zero=(float)(150+240*(Math.max(0,min)-min)/(max-min)),angle=(float)(150+240*(v-min)/(max-min));p.setColor(value<0?0xFF65DEDA:0xFFBCF46A);canvas.drawArc(oval,Math.min(zero,angle),Math.max(.5f,Math.abs(angle-zero)),false,p);}
        p.setStyle(Paint.Style.FILL);p.setTextAlign(Paint.Align.CENTER);p.setColor(0xFFF0F6ED);p.setTextSize(r*.47f);canvas.drawText(value==null?"—":labels.n(value,1),cx,cy+9,p);
        p.setTextSize(r*.17f);p.setColor(0xFFA8BFB5);canvas.drawText(unit,cx,cy+r*.40f,p);p.setTextSize(r*.18f);canvas.drawText(label,cx,cy+r*.78f,p);
        p.setTextSize(r*.13f);canvas.drawText(""+(int)min,cx-r*.83f,cy+r*.64f,p);canvas.drawText(""+(int)max,cx+r*.83f,cy+r*.64f,p);
    }
}
