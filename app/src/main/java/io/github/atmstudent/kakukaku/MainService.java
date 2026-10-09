package io.github.atmstudent.kakukaku;

import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;
import android.util.Log;
import android.view.Display;
import android.widget.Toast;

import io.github.atmstudent.kakukaku.Interfaces.Stoppable;
import io.github.atmstudent.kakukaku.Windows.Window;
import io.github.atmstudent.kakukaku.Windows.WindowCoordinator;


/**
 * Created by 0xbad1d3a5 on 4/9/2016.
 */
public class MainService extends Service implements Stoppable {

    private static final String TAG = MainService.class.getName();

    /** The Pause button of the notification */
    public static class PauseMainService extends BroadcastReceiver
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            if (IsRunning())
            {
                KakuKakuTools.startKakuKakuService(context, new Intent(context, MainService.class).setAction(ACTION_PAUSE));
            }
        }
    }

    public static class ToggleImagePreviewMainService extends BroadcastReceiver
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            SharedPreferences prefs = context.getSharedPreferences(Constants.KAKUKAKU_PREF_FILE, Context.MODE_PRIVATE);
            boolean imagePreview = prefs.getBoolean(Constants.KAKUKAKU_PREF_IMAGE_FILTER, false);
            prefs.edit().putBoolean(Constants.KAKUKAKU_PREF_IMAGE_FILTER, !imagePreview).apply();

            refreshAfterToggle(context);
        }
    }

    public static class TogglePageModeMainService extends BroadcastReceiver
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            SharedPreferences prefs = context.getSharedPreferences(Constants.KAKUKAKU_PREF_FILE, Context.MODE_PRIVATE);
            TextDirection textDirection = TextDirection.valueOf(prefs.getString(Constants.KAKUKAKU_PREF_TEXT_DIRECTION, TextDirection.AUTO.toString()));
            textDirection = TextDirection.Companion.getByValue((textDirection.ordinal() + 1) % 3);
            prefs.edit().putString(Constants.KAKUKAKU_PREF_TEXT_DIRECTION, textDirection.toString()).apply();

            KakuKakuTools.startKakuKakuService(context, new Intent(context, MainService.class));
        }
    }

    public static class ToggleInstantModeMainService extends BroadcastReceiver
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            SharedPreferences prefs = context.getSharedPreferences(Constants.KAKUKAKU_PREF_FILE, Context.MODE_PRIVATE);
            boolean pageMode = prefs.getBoolean(Constants.KAKUKAKU_PREF_INSTANT_MODE, false);
            prefs.edit().putBoolean(Constants.KAKUKAKU_PREF_INSTANT_MODE, !pageMode).apply();

            refreshAfterToggle(context);
        }
    }

    /** The running service re-reads the settings (and updates its notification); a paused one only needs a new notification */
    private static void refreshAfterToggle(Context context)
    {
        if (IsRunning())
        {
            KakuKakuTools.startKakuKakuService(context, new Intent(context, MainService.class));
        }
        else
        {
            ServiceNotification.INSTANCE.post(context, false);
        }
    }

    public static class ScreenOffReceiver extends BroadcastReceiver
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            if (IsRunning())
            {
                KakuKakuTools.startKakuKakuService(context, new Intent(context, MainService.class).setAction(ACTION_PAUSE));
            }
        }
    }

    private class MediaProjectionStopCallback extends MediaProjection.Callback{
        @Override
        public void onStop(){
            Log.d(TAG, "Stopping projection");
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (MediaProjectionStopCallback.this == mMediaProjectionStopCallback){
                        if (mVirtualDisplay != null){
                            mVirtualDisplay.release();
                        }
                        mMediaProjection.unregisterCallback(MediaProjectionStopCallback.this);
                        mMediaProjection = null;
                        mVirtualDisplay = null;
                        mImageReader.close();
                        // The consent token can't be reused (Android 14+), so the session ends; the notification stays with Start
                        if (!mDestroyed)
                        {
                            pause();
                        }
                    }
                }
            });
        }
    }

    public static final String ACTION_STATE_CHANGED = "io.github.atmstudent.kakukaku.ACTION_STATE_CHANGED";
    public static final String ACTION_PAUSE = "io.github.atmstudent.kakukaku.ACTION_PAUSE";

    private static boolean isKakuKakuRunning = false;

    private static final int VIRTUAL_DISPLAY_FLAGS = DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY | DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC;

    private IntentFilter mIntentFilter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
    private ScreenOffReceiver mScreenOffReceiver = new ScreenOffReceiver();

    private Intent mProjectionResultIntent;
    private int mProjectionResultCode;

    private MediaProjectionManager mMediaProjectionManager;
    private MediaProjection mMediaProjection;
    private ImageReader mImageReader;
    private DisplayManager mDisplayManager;
    private VirtualDisplay mVirtualDisplay;
    private MainServiceHandler mHandler;

    private int mRotation;
    private Point mRealDisplaySize = new Point();

    // Paused: the service ends but its notification stays, with Start. Destroyed: the service is gone for good.
    private boolean mPaused = false;
    private boolean mDestroyed = false;

    private MediaProjectionStopCallback mMediaProjectionStopCallback;
    private WindowCoordinator mWindowCoordinator = new WindowCoordinator(this);

    @Override
    public IBinder onBind(Intent intent)
    {
        // Not used
        return null;
    }

    @Override
    public void onCreate()
    {
        super.onCreate();

        Log.d(TAG, "CREATING MAINSERVICE: " + System.identityHashCode(this));
        Toast.makeText(this, "Starting capture window...", Toast.LENGTH_LONG).show();

        mMediaProjectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        mHandler = new MainServiceHandler(this, mWindowCoordinator);

        ContextCompat.registerReceiver(this, mScreenOffReceiver, mIntentFilter, ContextCompat.RECEIVER_NOT_EXPORTED);

        ServiceCompat.startForeground(this, ServiceNotification.NOTIFICATION_ID, ServiceNotification.INSTANCE.build(this, true), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        isKakuKakuRunning = true;
        notifyStateChanged();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        Log.d(TAG, "onStartCommand");

        if (intent.getExtras() != null &&
            intent.getExtras().containsKey(Constants.EXTRA_PROJECTION_RESULT_CODE) &&
            intent.getExtras().containsKey(Constants.EXTRA_PROJECTION_RESULT_INTENT))
        {
            mProjectionResultIntent = (Intent) intent.getExtras().get(Constants.EXTRA_PROJECTION_RESULT_INTENT);
            mProjectionResultCode = intent.getExtras().getInt(Constants.EXTRA_PROJECTION_RESULT_CODE);
        }

        if (ACTION_PAUSE.equals(intent.getAction()))
        {
            pause();
            return START_NOT_STICKY;
        }

        // Re-init CaptureWindow as well as prefs may have changed (BroadcastReceiver go to onStartCommand())
        mWindowCoordinator.getWindow(Constants.WINDOW_CAPTURE).reInit(new Window.ReinitOptions());

        // Set notification text
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(ServiceNotification.NOTIFICATION_ID, ServiceNotification.INSTANCE.build(this, true));

        return START_NOT_STICKY;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig)
    {
        super.onConfigurationChanged(newConfig);

        if (mWindowCoordinator.hasWindow(Constants.WINDOW_CAPTURE))
        {
            final int rotation = mDisplayManager.getDisplay(Display.DEFAULT_DISPLAY).getRotation();

            if (rotation != mRotation)
            {
                Log.d(TAG, "Orientation changed");
                mRotation = rotation;
                createVirtualDisplay();
                mWindowCoordinator.reinitAllWindows();
            }
        }
    }

    /**
     * Stops capturing but leaves the notification, now with a Start button: the screen-sharing permission can't be
     * reused (Android 14+), so Start goes through the app again. The service itself ends.
     */
    private void pause()
    {
        if (mPaused || mDestroyed)
        {
            return;
        }
        mPaused = true;

        mWindowCoordinator.stopAllWindows();
        stop();

        stopForeground(STOP_FOREGROUND_DETACH);
        ServiceNotification.INSTANCE.post(this, false);
        stopSelf();
    }

    @Override
    public void onDestroy()
    {
        mDestroyed = true;
        unregisterReceiver(mScreenOffReceiver);
        if (!mPaused)
        {
            stopForeground(STOP_FOREGROUND_REMOVE);
        }
        Log.d(TAG, "DESTORYING MAINSERVICE: " + System.identityHashCode(this));

        stop();
        mWindowCoordinator.stopAllWindows();
        mWindowCoordinator = null;
        isKakuKakuRunning = false;
        notifyStateChanged();

        Log.d(TAG, String.format("MAINSERVICE: %s DESTROYED", System.identityHashCode(this)));
        super.onDestroy();
    }

    @Override
    public void stop()
    {
        if (mMediaProjection != null)
        {
            mMediaProjection.stop();
        }
    }

    private void notifyStateChanged()
    {
        sendBroadcast(new Intent(ACTION_STATE_CHANGED).setPackage(getPackageName()));
    }

    public static boolean IsRunning()
    {
        return isKakuKakuRunning;
    }

    /**
     * This function is here as a bug fix against {@link #onConfigurationChanged(Configuration)} not
     * triggering when the app is first started and immediately switches to another orientation. In
     * such a case onConfigurationChanged will not trigger and {@link Window#reInit(io.github.atmstudent.kakukaku.Windows.Window.ReinitOptions)} will not
     * update the LayoutParams.
     */
    public void onCaptureWindowFinishedInitializing()
    {
        if (mMediaProjection == null){
            Log.d(TAG, "mMediaProjection is null");
            mMediaProjection = mMediaProjectionManager.getMediaProjection(mProjectionResultCode, mProjectionResultIntent);
            mMediaProjectionStopCallback = new MediaProjectionStopCallback();
            mMediaProjection.registerCallback(mMediaProjectionStopCallback, mHandler);
        }
        createVirtualDisplay();
    }

    public Handler getHandler()
    {
        return mHandler;
    }

    public Image getScreenshot() throws InterruptedException
    {
        long startTime = System.nanoTime();
        Image image = mImageReader.acquireLatestImage();
        while (image == null && System.nanoTime() < startTime + 2000000000){
            Thread.sleep(20);
            image = mImageReader.acquireLatestImage();
        }
        return image;
    }

    private void createVirtualDisplay()
    {
        mDisplayManager = (DisplayManager) getSystemService(DISPLAY_SERVICE);
        mRotation = mDisplayManager.getDisplay(Display.DEFAULT_DISPLAY).getRotation();
        int density = getResources().getDisplayMetrics().densityDpi;

        mRealDisplaySize = KakuKakuTools.getRealScreenSize(this);

        Log.d(TAG, String.format("Starting Projection: %dx%d", mRealDisplaySize.x, mRealDisplaySize.y));
        ImageReader oldReader = mImageReader;
        mImageReader = ImageReader.newInstance(mRealDisplaySize.x, mRealDisplaySize.y, PixelFormat.RGBA_8888, 2);

        if (mVirtualDisplay == null)
        {
            mVirtualDisplay = mMediaProjection.createVirtualDisplay(getClass().getName(), mRealDisplaySize.x, mRealDisplaySize.y, density, VIRTUAL_DISPLAY_FLAGS, mImageReader.getSurface(), null, mHandler);
        }
        else
        {
            // Android 14+ allows only one VirtualDisplay per MediaProjection, so resize it instead of recreating
            mVirtualDisplay.resize(mRealDisplaySize.x, mRealDisplaySize.y, density);
            mVirtualDisplay.setSurface(mImageReader.getSurface());
        }

        if (oldReader != null)
        {
            oldReader.close();
        }
    }
}
