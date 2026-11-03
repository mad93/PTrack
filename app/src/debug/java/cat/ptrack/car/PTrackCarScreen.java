package cat.ptrack.car;

import android.util.Log;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import androidx.annotation.NonNull;
import androidx.car.app.CarContext;
import androidx.car.app.Screen;
import androidx.car.app.model.Action;
import androidx.car.app.model.Pane;
import androidx.car.app.model.PaneTemplate;
import androidx.car.app.model.Row;
import androidx.car.app.model.ActionStrip;
import androidx.car.app.model.Template;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import cat.ptrack.CarDashboard;
import cat.ptrack.VehicleTelemetry;

/** Read-only consumer. Never starts/stops recording or opens a database/socket. */
public final class PTrackCarScreen extends Screen {
    private final boolean details;
    private final int page;
    private final Handler main = new Handler(Looper.getMainLooper());
    private boolean visible, queued;
    private long rendered, requested;
    private final Runnable render = () -> {
        queued=false;
        if(visible) { requested=SystemClock.elapsedRealtime(); invalidate(); }
    };
    private final Runnable expire = this::requestRender;
    private final Runnable changed = () -> main.post(this::requestRender);
    public PTrackCarScreen(@NonNull CarContext carContext) {
        this(carContext,0);
    }
    private PTrackCarScreen(@NonNull CarContext carContext, int page) {
        super(carContext);
        this.page=page;
        this.details=page!=0;
        getLifecycle().addObserver(new DefaultLifecycleObserver() {
            @Override public void onStart(@NonNull LifecycleOwner owner) {
                visible=true;
                VehicleTelemetry.LIVE.subscribe(changed);
                CarTestFeed.SOURCE.subscribe(changed);
                requestRender();
            }
            @Override public void onStop(@NonNull LifecycleOwner owner) {
                visible=false;
                VehicleTelemetry.LIVE.unsubscribe(changed);
                CarTestFeed.SOURCE.unsubscribe(changed);
                main.removeCallbacksAndMessages(null); queued=false;
            }
        });
    }
    private void requestRender() {
        if(!visible || queued) return;
        queued=true;
        main.postDelayed(render,Math.max(0,1000-(SystemClock.elapsedRealtime()-Math.max(rendered,requested))));
    }
    @NonNull @Override public Template onGetTemplate() {
        rendered=SystemClock.elapsedRealtime();
        main.removeCallbacks(render); queued=false;
        VehicleTelemetry.Snapshot vehicle=CarTestFeed.snapshot();
        main.removeCallbacks(expire);
        // One expiry timer, not continuous polling, also when OBD stops sending events.
        if(visible && vehicle.current(rendered)!=null) {
            long deadline=vehicle.receivedAt()+VehicleTelemetry.FRESH_MS+1;
            if(vehicle.cable()!=null && vehicle.cableAt()+VehicleTelemetry.FRESH_MS>=rendered)
                deadline=Math.min(deadline,vehicle.cableAt()+VehicleTelemetry.FRESH_MS+1);
            main.postDelayed(expire,deadline-rendered);
        }
        Pane.Builder pane=new Pane.Builder();
        var lines=details?CarDashboard.details(vehicle,rendered).subList(page==1?0:2,page==1?2:4):CarDashboard.primary(vehicle,rendered);
        for(CarDashboard.Line line : lines) {
            Row.Builder row=new Row.Builder().setTitle(line.title()).addText(line.value());
            if(details) row.addText(line.detail());
            pane.addRow(row.build());
        }
        Log.i("PTrackCar","Dashboard requested · "+vehicle.connection(rendered)+(vehicle.simulation()?" · SIMULACIÓ":""));
        String section=page==2?"Bateria":page==1?"Detalls":"PTRACK";
        String title=vehicle.simulation()?section+" · SIMULACIÓ":(details?"PTRACK · "+section:"PTRACK");
        PaneTemplate.Builder template=new PaneTemplate.Builder(pane.build()).setTitle(title)
                .setHeaderAction(details?Action.BACK:Action.APP_ICON);
        template.setActionStrip(new ActionStrip.Builder().addAction(
                new Action.Builder().setTitle(page==0?"Detalls":page==1?"Bateria":"Principal").setOnClickListener(
                        () -> { if(page==2) getScreenManager().popToRoot();
                                else getScreenManager().push(new PTrackCarScreen(getCarContext(),page+1)); }).build()).build());
        return template.build();
    }
}
