package cat.ptrack;

import android.content.Context;
import org.json.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;

public final class Profile {
    public record Signal(String key, String request, String response, String did, String expression, String unit, double min, double max, double scale, double offset) {
        public Signal(String key,String request,String response,String did,String expression,String unit,double min,double max){this(key,request,response,did,expression,unit,min,max,1,0);}
        public double decode(String raw) {
            try {
                byte[] bytes=ElmProtocol.payload(raw,did);
                if(bytes==null) return Double.NaN;
                Matcher m=Pattern.compile("(S?)\\[B(\\d+):B(\\d+)\\]|(S?)B(\\d+)").matcher(expression);
                StringBuffer replaced=new StringBuffer();
                while(m.find()) {
                    int first=Integer.parseInt(m.group(5)!=null ? m.group(5) : m.group(2));
                    int last=m.group(5)!=null ? first : Integer.parseInt(m.group(3));
                    int length=last-first+1;
                    double value=ElmProtocol.unsigned(bytes,first-4,length);
                    if(!Double.isFinite(value)) return Double.NaN;
                    if(("S".equals(m.group(1))||"S".equals(m.group(4))) && value>=Math.pow(2,length*8-1)) value-=Math.pow(2,length*8);
                    m.appendReplacement(replaced,Double.toString(value));
                }
                m.appendTail(replaced);
                double value=new Arithmetic(replaced.toString()).parse()*scale+offset;
                return Double.isFinite(value) && value>=min && value<=max ? value : Double.NaN;
            } catch(Exception e) { return Double.NaN; }
        }
    }
    public record RawSignal(String key,String request,String response,String did,int bytes,double scale,double offset,double min,double max) {
        public String display(String raw,Labels l){
            byte[] data=ElmProtocol.payload(raw,did);if(data==null)return "—";
            StringBuilder text=new StringBuilder();
            if(bytes==0){for(byte b:data)text.append(String.format(Locale.ROOT,"%02X",b&255));return text.toString();}
            for(int i=0;i+bytes<=data.length;i+=bytes){double v=ElmProtocol.unsigned(data,i,bytes)*scale+offset;if(v<min||v>max)continue;if(text.length()>0)text.append(" · ");text.append(i/bytes+1).append(": ").append(l.n(v,bytes==1?0:3));}
            return text.length()==0?"—":text.toString();
        }
    }
    public final Map<String,Signal> signals=new LinkedHashMap<>();
    public final List<RawSignal> rawSignals=new ArrayList<>();
    public Profile(Context c) throws Exception {
        JSONObject root;
        try(var in=c.getAssets().open("stellantis-ecmp.json")) { root=new JSONObject(new String(Streams.bytes(in),StandardCharsets.UTF_8)); }
        JSONObject live=root.getJSONObject("live"); JSONArray ecus=root.getJSONArray("ecus");
        for(Iterator<String> keys=live.keys(); keys.hasNext();) {
            String key=keys.next(); JSONObject l=live.getJSONObject(key);
            for(int i=0;i<ecus.length();i++) {
                JSONObject ecu=ecus.getJSONObject(i); if(!ecu.getString("id").equals(l.getString("ecu"))) continue;
                JSONArray dids=ecu.getJSONArray("dids");
                for(int j=0;j<dids.length();j++) {
                    JSONObject d=dids.getJSONObject(j);
                    if(!d.getString("did").equalsIgnoreCase(l.getString("did"))) continue;
                    String expression=l.optString("expression",d.optString("expression",""));
                    if(l.has("bytes")||expression.isEmpty()){
                        rawSignals.add(new RawSignal(key,hex(ecu.getString("request")),hex(ecu.getString("response")),hex(d.getString("did")),l.optInt("bytes",0),l.optDouble("scale",1),l.optDouble("offset",0),l.optDouble("min",-Double.MAX_VALUE),l.optDouble("max",Double.MAX_VALUE)));
                    }else signals.put(key,new Signal(key,hex(ecu.getString("request")),hex(ecu.getString("response")),hex(d.getString("did")),expression,d.optString("unit",""),l.optDouble("min",-Double.MAX_VALUE),l.optDouble("max",Double.MAX_VALUE),l.optDouble("scale",1),l.optDouble("offset",0)));
                }
            }
        }
        for(String key:Arrays.asList("current","voltage","soc","speed")) if(!signals.containsKey(key)) throw new IllegalStateException("Missing "+key);
    }
    private String hex(String s) { return s.replace("0x","").toUpperCase(Locale.ROOT); }
    /** Small arithmetic grammar, never executes profile text as code. */
    private static final class Arithmetic {
        final String s; int p;
        Arithmetic(String s) { this.s=s.replace(" ",""); }
        double parse() { double v=sum(); if(p!=s.length()) throw new IllegalArgumentException(); return v; }
        double sum() { double v=product(); while(p<s.length()&&(s.charAt(p)=='+'||s.charAt(p)=='-')) { char op=s.charAt(p++); double n=product(); v=op=='+'?v+n:v-n; } return v; }
        double product() { double v=number(); while(p<s.length()&&(s.charAt(p)=='*'||s.charAt(p)=='/')) { char op=s.charAt(p++); double n=number(); v=op=='*'?v*n:v/n; } return v; }
        double number() { if(p<s.length()&&s.charAt(p)=='-'){p++;return -number();} if(p<s.length()&&s.charAt(p)=='('){p++;double v=sum();if(p>=s.length()||s.charAt(p++)!=')')throw new IllegalArgumentException();return v;} int start=p; while(p<s.length()&&(Character.isDigit(s.charAt(p))||s.charAt(p)=='.'))p++; return Double.parseDouble(s.substring(start,p)); }
    }
}
