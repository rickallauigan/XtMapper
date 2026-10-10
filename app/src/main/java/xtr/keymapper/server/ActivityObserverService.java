package xtr.keymapper.server;

import static android.content.Context.ACTIVITY_SERVICE;

import android.app.ActivityManager;
import android.app.IActivityManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;

import java.util.List;

import xtr.keymapper.ActivityObserver;

public class ActivityObserverService implements Runnable {
    public ActivityObserver mCallback;
    private final IActivityManager am = IActivityManager.Stub.asInterface(ServiceManager.getService(ACTIVITY_SERVICE));
    private HandlerThread mHandlerThread;
    private Handler mHandler;

    public ActivityObserverService(ActivityObserver observer) {
        this.mCallback = observer;

        mHandlerThread = new HandlerThread("activity_observer");
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());
        // Send activity to client app every 5 seconds
        mHandler.post(this);
    }

    @Override
    public void run() {
        ActivityObserver callback;
        synchronized (this) { callback = mCallback; }
        if (callback == null) return;
        try {
            List<ActivityManager.RunningTaskInfo> tasks = am.getTasks(1);
            if (!tasks.isEmpty() && tasks.get(0).topActivity != null)
                callback.onForegroundActivitiesChanged(tasks.get(0).topActivity.getPackageName());
        } catch (RemoteException e) {
            Log.e(RemoteService.TAG, e.getMessage(), e);
        } finally {
            synchronized (this) {
                if (mCallback == callback && mHandler != null) mHandler.postDelayed(this, 5000);
            }
        }
    }

    public synchronized void stop() {
        mCallback = null;
        if (mHandler != null) mHandler.removeCallbacks(this);
        mHandler = null;
        if (mHandlerThread != null) mHandlerThread.quitSafely();
        mHandlerThread = null;
    }
}
