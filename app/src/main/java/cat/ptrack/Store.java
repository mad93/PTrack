package cat.ptrack;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;

public final class Store extends SQLiteOpenHelper {
    public Store(Context context) { this(context,"ptrack.db"); }
    public Store(Context context,String name) { super(context, name, null, 1); setWriteAheadLoggingEnabled(true); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE trips(id INTEGER PRIMARY KEY, start INTEGER NOT NULL, end INTEGER, demo INTEGER NOT NULL, status TEXT NOT NULL)");
        db.execSQL("CREATE TABLE samples(id INTEGER PRIMARY KEY, trip INTEGER NOT NULL, time INTEGER NOT NULL, soc REAL, voltage REAL, current REAL, speed REAL, lat REAL, lon REAL, altitude REAL, raw TEXT)");
        db.execSQL("CREATE INDEX sample_trip ON samples(trip,time)");
        db.execSQL("CREATE TABLE readings(id INTEGER PRIMARY KEY, trip INTEGER NOT NULL, time INTEGER NOT NULL, signal TEXT, value REAL, raw TEXT)");
        db.execSQL("CREATE INDEX reading_trip ON readings(trip,time)");
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) { throw new IllegalStateException("Migration required"); }
    public long begin(boolean demo) {
        ContentValues v = new ContentValues(); v.put("start", System.currentTimeMillis()); v.put("demo", demo); v.put("status", "recording");
        return getWritableDatabase().insertOrThrow("trips", null, v);
    }
    public void recover() { getWritableDatabase().execSQL("UPDATE trips SET status='interrupted', end=COALESCE((SELECT MAX(time) FROM samples WHERE trip=trips.id),start) WHERE status='recording'"); }
    public void finish(long id) {
        getWritableDatabase().execSQL("UPDATE trips SET end=COALESCE((SELECT MAX(time) FROM samples WHERE trip=?),start),status='complete' WHERE id=?", new Object[]{id,id});
    }
    public void add(long trip, Sample s) {
        ContentValues v = new ContentValues(); v.put("trip", trip); v.put("time", s.time()); v.put("soc", s.soc());
        v.put("voltage", s.voltage()); v.put("current", s.current()); v.put("speed", s.speed());
        v.put("lat", s.latitude()); v.put("lon", s.longitude()); v.put("altitude", s.altitude()); v.put("raw", s.raw());
        getWritableDatabase().insertOrThrow("samples", null, v);
    }
    public void reading(long trip, String signal, Double value, String raw) {
        ContentValues v = new ContentValues(); v.put("trip",trip); v.put("time",System.currentTimeMillis()); v.put("signal",signal);
        v.put("value",value != null && Double.isFinite(value) ? value : null); v.put("raw",raw);
        getWritableDatabase().insertOrThrow("readings",null,v);
    }
    public List<Sample> samples(long trip) {
        List<Sample> result = new ArrayList<>();
        try(Cursor c = getReadableDatabase().rawQuery("SELECT time,soc,voltage,current,speed,lat,lon,altitude,raw FROM samples WHERE trip=? ORDER BY time,id", new String[]{""+trip})) {
            while(c.moveToNext()) result.add(new Sample(c.getLong(0),c.getDouble(1),c.getDouble(2),c.getDouble(3),c.getDouble(4), number(c,5),number(c,6),number(c,7),c.getString(8)));
        }
        return result;
    }
    private static Double number(Cursor c, int n) { return c.isNull(n) ? null : c.getDouble(n); }
    public Cursor trips() { return getReadableDatabase().rawQuery("SELECT id,start,end,demo,status FROM trips ORDER BY id DESC",null); }
    public boolean isDemo(long id) {
        try(Cursor c=getReadableDatabase().rawQuery("SELECT demo FROM trips WHERE id=?",new String[]{""+id})) { return c.moveToFirst() && c.getInt(0)==1; }
    }
}
