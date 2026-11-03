package cat.ptrack;

public final class CoreTests {
    private static int assertions;
    private static void near(double actual,double expected){assertions++;if(Math.abs(actual-expected)>1e-8||!Double.isFinite(actual))throw new AssertionError(actual+" != "+expected);}
    private static void check(boolean value){assertions++;if(!value)throw new AssertionError();}
    private static Sample sample(long time,double kw,double speed,Double altitude){return new Sample(time,60-time/100000.0,400,kw*2.5,speed,null,null,altitude,"{}");}
    public static void main(String[] args)throws Exception{
        TripStats steady=new TripStats();for(int i=0;i<=360;i++)steady.add(sample(i*10000,10,100,null));
        near(steady.km,100);near(steady.consumed,10);near(steady.consumption(),10);near(steady.averageSpeed(),100);check(!steady.hasAltitude);
        TripStats cross=new TripStats();cross.add(sample(0,10,0,100.0));cross.add(sample(10000,-10,0,106.0));
        near(cross.consumed,25.0/3600);near(cross.recovered,25.0/3600);near(cross.ascent,6);check(Double.isNaN(cross.consumption()));
        cross.add(sample(20000,-10,0,102.0));near(cross.descent,0);cross.add(sample(30000,-10,0,99.0));near(cross.descent,7);
        TripStats gap=new TripStats();gap.add(sample(1000,40,100,null));gap.add(sample(61000,40,100,null));near(gap.km,0);near(gap.consumed,0);gap.add(sample(61000,40,100,null));check(gap.count==2);
        byte[] payload=ElmProtocol.payload("22D410\r62 D4 10 78 00\r>","D410");near(ElmProtocol.unsigned(payload,0,2)/512,60);
        check(ElmProtocol.payload("62D8107800","D410")==null);check(ElmProtocol.payload("62D4107","D410")==null);check(ElmProtocol.payload("NO DATA","D410")==null);
        payload=ElmProtocol.payload("007\r0:62D8160001\r1:2C00\r","D816");near(ElmProtocol.unsigned(payload,0,4)/64-1200,0);
        check(ElmProtocol.payload("0:62D8160001\r2:2C00","D816")==null);
        boolean rejected=false;try{new ElmProtocol(new java.io.ByteArrayInputStream(new byte[0]),new java.io.ByteArrayOutputStream()).exchange("2ED4100000");}catch(java.io.IOException expected){rejected=true;}check(rejected);
        near(new Profile.Signal("soc","6B4","694","D410","[B4:B5]/512","%",0,101).decode("62D4107800"),60);
        near(new Profile.Signal("current","6B4","694","D816","[B4:B7]/64-1200","A",-500,500).decode("62D81600011800"),-80);
        near(new Profile.Signal("speed","6A2","682","D402","[B4:B5]/128","km/h",0,250).decode("62D4023200"),100);
        near(new Profile.Signal("signed","6A2","682","D000","S[B4:B5]/10","",-100,100).decode("62D000FF9C"),-10);
        near(new Profile.Signal("ambient","6A2","682","D434","SB4","°C",-50,70).decode("62D434F6"),-10);
        near(new Profile.Signal("cell_min","6B4","694","D86F","[B4:B5]","V",2,4.5,0.001,0).decode("62D86F0E74"),3.7);
        check(ElmProtocol.payload("00A\r0:62D8160001\r1:2C00\r","D816")==null);
        StringBuilder longReply=new StringBuilder("0F3\r");
        StringBuilder original=new StringBuilder("62D440");
        for(int i=0;i<240;i++)original.append(String.format(java.util.Locale.ROOT,"%02X",i));
        for(int start=0,n=0;start<original.length();start+=14,n++)longReply.append(Integer.toHexString(n%16)).append(':').append(original,start,Math.min(original.length(),start+14)).append('\r');
        byte[] cells=ElmProtocol.payload(longReply.toString(),"D440");check(cells!=null&&cells.length==240);near(cells[239]&255,239);
        check(ElmProtocol.payload(longReply.toString().replace("0F3","0F4"),"D440")==null);
        check(Double.isNaN(new Profile.Signal("soc","6B4","694","D410","[B4:B5]/512","%",0,101).decode("62D410FFFF")));
        System.out.println("PASS: "+assertions+" assertions (integration, regeneration, gaps, elevation, ELM framing, profile formulas and read-only guard)");
    }
}
