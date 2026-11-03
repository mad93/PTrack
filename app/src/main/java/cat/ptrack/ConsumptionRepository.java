package cat.ptrack;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.ArrayList;
import java.util.List;
import static cat.ptrack.ConsumptionModel.*;

/** SQLite adapter. Call from a background worker: queries rebuild a consistent historical snapshot. */
public final class ConsumptionRepository implements ConsumptionModel {
    private final Store store;
    private final int binWidth;
    public ConsumptionRepository(Store store) { this(store,10); }
    public ConsumptionRepository(Store store,int binWidth) {
        new ConsumptionLearner(binWidth); // Validate configuration before touching SQLite.
        this.store=store; this.binWidth=binWidth;
    }
    static void createSchema(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE consumption_profiles(speed_bin INTEGER NOT NULL, terrain TEXT NOT NULL, km REAL NOT NULL, consumed REAL NOT NULL, recovered REAL NOT NULL, intervals INTEGER NOT NULL, trips INTEGER NOT NULL, PRIMARY KEY(speed_bin,terrain))");
        db.execSQL("CREATE TABLE consumption_model(id INTEGER PRIMARY KEY CHECK(id=1), version INTEGER NOT NULL, bin_width INTEGER NOT NULL, rebuilt_at INTEGER NOT NULL)");
    }
    public void recalculate() {
        SQLiteDatabase db=store.getWritableDatabase();
        db.beginTransaction();
        try { rebuild(db); db.setTransactionSuccessful(); }
        finally { db.endTransaction(); }
    }
    private void rebuild(SQLiteDatabase db) {
        ConsumptionLearner learner=new ConsumptionLearner(binWidth);
        try(Cursor trips=db.rawQuery("SELECT id FROM trips WHERE demo=0 AND status='complete' ORDER BY id",null)) {
            while(trips.moveToNext()) {
                List<Sample> samples=new ArrayList<>();
                try(Cursor c=db.rawQuery("SELECT time,soc,voltage,current,speed,lat,lon,altitude FROM samples WHERE trip=? ORDER BY time,id",new String[]{trips.getString(0)})) {
                    while(c.moveToNext()) samples.add(new Sample(c.getLong(0),required(c,1),required(c,2),required(c,3),required(c,4),optional(c,5),optional(c,6),optional(c,7),null));
                }
                learner.addTrip(samples);
            }
        }
        db.delete("consumption_profiles",null,null);
        learner.buckets().forEach((key,b)-> {
            ContentValues v=new ContentValues();
            v.put("speed_bin",key.speedBin()); v.put("terrain",key.terrain().name());
            v.put("km",b.km); v.put("consumed",b.consumedKwh); v.put("recovered",b.recoveredKwh);
            v.put("intervals",b.intervals); v.put("trips",b.trips);
            db.insertOrThrow("consumption_profiles",null,v);
        });
        db.execSQL("INSERT OR REPLACE INTO consumption_model(id,version,bin_width,rebuilt_at) VALUES(1,1,?,?)",new Object[]{binWidth,System.currentTimeMillis()});
    }
    @Override public Estimate estimate(double speedKmh,Terrain terrain) {
        if(!Double.isFinite(speedKmh)||speedKmh<5||speedKmh>250||terrain==null) throw new IllegalArgumentException("speed/terrain");
        SQLiteDatabase db=store.getWritableDatabase();
        db.beginTransaction();
        try {
            // Full replacement prevents double counting, picks up newly completed trips and config changes.
            rebuild(db);
            ConsumptionLearner.Bucket b=new ConsumptionLearner.Bucket();
            try(Cursor c=db.rawQuery("SELECT km,consumed,recovered,intervals,trips FROM consumption_profiles WHERE speed_bin=? AND terrain=?",
                    new String[]{""+(int)Math.floor(speedKmh/binWidth),terrain.name()})) {
                if(c.moveToFirst()) { b.km=c.getDouble(0); b.consumedKwh=c.getDouble(1); b.recoveredKwh=c.getDouble(2); b.intervals=c.getLong(3); b.trips=c.getInt(4); }
            }
            Estimate result=b.estimate();
            db.setTransactionSuccessful();
            return result;
        } finally { db.endTransaction(); }
    }
    private static double required(Cursor c,int index) { return c.isNull(index)?Double.NaN:c.getDouble(index); }
    private static Double optional(Cursor c,int index) { return c.isNull(index)?null:c.getDouble(index); }
}
