package app.mojiscope;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
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
import androidx.core.app.NotificationCompat;
import android.util.Log;
import android.view.Display;
import android.widget.Toast;

import app.mojiscope.Interfaces.Stoppable;
import app.mojiscope.Windows.Window;
import app.mojiscope.Windows.WindowCoordinator;

import static androidx.core.app.NotificationCompat.FLAG_FOREGROUND_SERVICE;
import static androidx.core.app.NotificationCompat.FLAG_ONGOING_EVENT;

/**
 * Created by 0xbad1d3a5 on 4/9/2016.
 */
public class MainService extends Service implements Stoppable {

    private static final String TAG = MainService.class.getName();

    public static class CloseMainService extends BroadcastReceiver
    {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "GOT CLOSE");
            context.stopService(new Intent(context, MainService.class));
        }
    }

    public static class ToggleImagePreviewMainService extends BroadcastReceiver
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            SharedPreferences prefs = context.getSharedPreferences(Constants.MOJI_PREF_FILE, Context.MODE_PRIVATE);
            boolean imagePreview = prefs.getBoolean(Constants.MOJI_PREF_IMAGE_FILTER, true);
            prefs.edit().putBoolean(Constants.MOJI_PREF_IMAGE_FILTER, !imagePreview).apply();

            MojiTools.startMojiscopeService(context, new Intent(context, MainService.class));
        }
    }

    public static class ToggleShowHideMainService extends BroadcastReceiver
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            SharedPreferences prefs = context.getSharedPreferences(Constants.MOJI_PREF_FILE, Context.MODE_PRIVATE);
            boolean shown = prefs.getBoolean(Constants.MOJI_PREF_SHOW_HIDE, true);
            prefs.edit().putBoolean(Constants.MOJI_PREF_SHOW_HIDE, !shown).apply();

            MojiTools.startMojiscopeService(context, new Intent(context, MainService.class));
        }
    }

    public static class TogglePageModeMainService extends BroadcastReceiver
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            SharedPreferences prefs = context.getSharedPreferences(Constants.MOJI_PREF_FILE, Context.MODE_PRIVATE);
            TextDirection textDirection = TextDirection.valueOf(prefs.getString(Constants.MOJI_PREF_TEXT_DIRECTION, TextDirection.AUTO.toString()));
            textDirection = TextDirection.Companion.getByValue((textDirection.ordinal() + 1) % 3);
            prefs.edit().putString(Constants.MOJI_PREF_TEXT_DIRECTION, textDirection.toString()).apply();

            MojiTools.startMojiscopeService(context, new Intent(context, MainService.class));
        }
    }

    public static class ToggleInstantModeMainService extends BroadcastReceiver
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            SharedPreferences prefs = context.getSharedPreferences(Constants.MOJI_PREF_FILE, Context.MODE_PRIVATE);
            boolean pageMode = prefs.getBoolean(Constants.MOJI_PREF_INSTANT_MODE, true);
            prefs.edit().putBoolean(Constants.MOJI_PREF_INSTANT_MODE, !pageMode).apply();

            MojiTools.startMojiscopeService(context, new Intent(context, MainService.class));
        }
    }

    public static class ScreenOffReceiver extends BroadcastReceiver
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            SharedPreferences prefs = context.getSharedPreferences(Constants.MOJI_PREF_FILE, Context.MODE_PRIVATE);
            prefs.edit().putBoolean(Constants.MOJI_PREF_SHOW_HIDE, false).apply();

            MojiTools.startMojiscopeService(context, new Intent(context, MainService.class));
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
                        // The consent token can't be reused (Android 14+), so end the session
                        stopSelf();
                    }
                }
            });
        }
    }

    public static final String ACTION_STATE_CHANGED = "app.mojiscope.ACTION_STATE_CHANGED";

    private static boolean isMojiscopeRunning = false;

    private static final int VIRTUAL_DISPLAY_FLAGS = DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY | DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC;
    private static final int NOTIFICATION_ID = 1;

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

        if (!isMojiscopeRunning)
        {
            SharedPreferences prefs = getSharedPreferences(Constants.MOJI_PREF_FILE, Context.MODE_PRIVATE);
            prefs.edit().putBoolean(Constants.MOJI_PREF_SHOW_HIDE, true).apply();
        }

        Log.d(TAG, "CREATING MAINSERVICE: " + System.identityHashCode(this));
        Toast.makeText(this, "Starting capture window...", Toast.LENGTH_LONG).show();

        mMediaProjectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        mHandler = new MainServiceHandler(this, mWindowCoordinator);

        // Set preferences for ratings
        SharedPreferences prefs = getSharedPreferences(Constants.MOJI_PREF_FILE, Context.MODE_PRIVATE);
        int timesLaunched = prefs.getInt(Constants.MOJI_PREF_TIMES_LAUNCHED, 1);
        prefs.edit().putInt(Constants.MOJI_PREF_TIMES_LAUNCHED, timesLaunched + 1).apply();

        ContextCompat.registerReceiver(this, mScreenOffReceiver, mIntentFilter, ContextCompat.RECEIVER_NOT_EXPORTED);

        ServiceCompat.startForeground(this, NOTIFICATION_ID, getNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        isMojiscopeRunning = true;
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

        // Determine if we need to start/stop the capture service
        SharedPreferences prefs = getSharedPreferences(Constants.MOJI_PREF_FILE, Context.MODE_PRIVATE);
        Boolean shown = prefs.getBoolean(Constants.MOJI_PREF_SHOW_HIDE, true);
        if (shown)
        {
            // Re-init CaptureWindow as well as prefs may have changed (BroadcastReceiver go to onStartCommand())
            mWindowCoordinator.getWindow(Constants.WINDOW_CAPTURE).reInit(new Window.ReinitOptions());
        }
        else
        {
            mWindowCoordinator.stopAllWindows();
            stop();
        }

        // Set notification text
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(NOTIFICATION_ID, getNotification());

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

    @Override
    public void onDestroy()
    {
        unregisterReceiver(mScreenOffReceiver);
        stopForeground(STOP_FOREGROUND_REMOVE);
        Log.d(TAG, "DESTORYING MAINSERVICE: " + System.identityHashCode(this));

        stop();
        mWindowCoordinator.stopAllWindows();
        mWindowCoordinator = null;
        isMojiscopeRunning = false;
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
        return isMojiscopeRunning;
    }

    /**
     * This function is here as a bug fix against {@link #onConfigurationChanged(Configuration)} not
     * triggering when the app is first started and immediately switches to another orientation. In
     * such a case onConfigurationChanged will not trigger and {@link Window#reInit(app.mojiscope.Windows.Window.ReinitOptions)} will not
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

    private Notification getNotification()
    {
        String channelId = createNotificationChannel();

        PendingIntent toggleShowHide = PendingIntent.getBroadcast(this, Constants.REQUEST_SERVICE_TOGGLE_SHOW_HIDE, new Intent(this, ToggleShowHideMainService.class), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent toggleImagePreview = PendingIntent.getBroadcast(this, Constants.REQUEST_SERVICE_TOGGLE_IMAGE_PREVIEW, new Intent(this, ToggleImagePreviewMainService.class), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent togglePageMode = PendingIntent.getBroadcast(this, Constants.REQUEST_SERVICE_TOGGLE_PAGE_MODE, new Intent(this, TogglePageModeMainService.class), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent toggleInstantMode = PendingIntent.getBroadcast(this, Constants.REQUEST_SERVICE_TOGGLE_INSTANT_MODE, new Intent(this, ToggleInstantModeMainService.class), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent closeMainService = PendingIntent.getBroadcast(this, Constants.REQUEST_SERVICE_SHUTDOWN, new Intent(this, CloseMainService.class), PendingIntent.FLAG_IMMUTABLE);

        Prefs prefs = MojiTools.getPrefs(this);

        String contentTitle = "Mojiscope";
        switch (prefs.getTextDirectionSetting())
        {
            case AUTO:
                contentTitle = "Mojiscope is determining text direction automatically";
                break;
            case VERTICAL:
                contentTitle = "Mojiscope is reading text vertically";
                break;
            case HORIZONTAL:
                contentTitle = "Mojiscope is reading text horizontally";
                break;
        }

        Notification n;
        if (prefs.getShowHideSetting())
        {
            n = new NotificationCompat.Builder(this, channelId)
                    .setSmallIcon(R.drawable.moji_notification_icon)
                    .setContentTitle(contentTitle)
                    .setContentText(String.format("Instant mode %s, black and white filter %s", prefs.getInstantModeSetting() ? "on" : "off", prefs.getImageFilterSetting() ? "on" : "off"))
                    .setContentIntent(toggleShowHide)
                    .addAction(0, "Instant Mode", toggleInstantMode)
                    .addAction(0, "Image Filter", toggleImagePreview)
                    .addAction(0, "Shutdown", closeMainService)
                    .build();
        }
        else {
            n = new NotificationCompat.Builder(this, channelId)
                    .setSmallIcon(R.drawable.moji_notification_icon)
                    .setContentTitle("Mojiscope is hidden and in power-saving mode")
                    .setContentIntent(toggleShowHide)
                    .build();
        }

        n.flags = FLAG_ONGOING_EVENT | FLAG_FOREGROUND_SERVICE;

        return n;
    }

    private void createVirtualDisplay()
    {
        mDisplayManager = (DisplayManager) getSystemService(DISPLAY_SERVICE);
        mRotation = mDisplayManager.getDisplay(Display.DEFAULT_DISPLAY).getRotation();
        int density = getResources().getDisplayMetrics().densityDpi;

        mRealDisplaySize = MojiTools.getRealScreenSize(this);

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

    private String createNotificationChannel()
    {
        String channelId = Constants.MOJI_CHANNEL_ID;
        String channelName = Constants.MOJI_CHANNEL_NAME;

        NotificationChannel channel = new NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW);
        NotificationManager service = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        service.createNotificationChannel(channel);

        return channelId;
    }
}
