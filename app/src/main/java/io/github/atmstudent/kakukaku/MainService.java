package io.github.atmstudent.kakukaku;

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
import android.view.View;
import android.widget.RemoteViews;
import android.widget.Toast;

import io.github.atmstudent.kakukaku.Interfaces.Stoppable;
import io.github.atmstudent.kakukaku.Windows.Window;
import io.github.atmstudent.kakukaku.Windows.WindowCoordinator;

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
            SharedPreferences prefs = context.getSharedPreferences(Constants.KAKUKAKU_PREF_FILE, Context.MODE_PRIVATE);
            boolean imagePreview = prefs.getBoolean(Constants.KAKUKAKU_PREF_IMAGE_FILTER, false);
            prefs.edit().putBoolean(Constants.KAKUKAKU_PREF_IMAGE_FILTER, !imagePreview).apply();

            KakuKakuTools.startKakuKakuService(context, new Intent(context, MainService.class));
        }
    }

    public static class ToggleShowHideMainService extends BroadcastReceiver
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            SharedPreferences prefs = context.getSharedPreferences(Constants.KAKUKAKU_PREF_FILE, Context.MODE_PRIVATE);
            boolean shown = prefs.getBoolean(Constants.KAKUKAKU_PREF_SHOW_HIDE, true);
            prefs.edit().putBoolean(Constants.KAKUKAKU_PREF_SHOW_HIDE, !shown).apply();

            KakuKakuTools.startKakuKakuService(context, new Intent(context, MainService.class));
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

            KakuKakuTools.startKakuKakuService(context, new Intent(context, MainService.class));
        }
    }

    public static class ScreenOffReceiver extends BroadcastReceiver
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            SharedPreferences prefs = context.getSharedPreferences(Constants.KAKUKAKU_PREF_FILE, Context.MODE_PRIVATE);
            prefs.edit().putBoolean(Constants.KAKUKAKU_PREF_SHOW_HIDE, false).apply();

            KakuKakuTools.startKakuKakuService(context, new Intent(context, MainService.class));
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

    public static final String ACTION_STATE_CHANGED = "io.github.atmstudent.kakukaku.ACTION_STATE_CHANGED";

    private static boolean isKakuKakuRunning = false;

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

        if (!isKakuKakuRunning)
        {
            SharedPreferences prefs = getSharedPreferences(Constants.KAKUKAKU_PREF_FILE, Context.MODE_PRIVATE);
            prefs.edit().putBoolean(Constants.KAKUKAKU_PREF_SHOW_HIDE, true).apply();
        }

        Log.d(TAG, "CREATING MAINSERVICE: " + System.identityHashCode(this));
        Toast.makeText(this, "Starting capture window...", Toast.LENGTH_LONG).show();

        mMediaProjectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        mHandler = new MainServiceHandler(this, mWindowCoordinator);

        ContextCompat.registerReceiver(this, mScreenOffReceiver, mIntentFilter, ContextCompat.RECEIVER_NOT_EXPORTED);

        ServiceCompat.startForeground(this, NOTIFICATION_ID, getNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
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

        // Determine if we need to start/stop the capture service
        SharedPreferences prefs = getSharedPreferences(Constants.KAKUKAKU_PREF_FILE, Context.MODE_PRIVATE);
        Boolean shown = prefs.getBoolean(Constants.KAKUKAKU_PREF_SHOW_HIDE, true);
        if (shown)
        {
            // Re-init CaptureWindow as well as prefs may have changed (BroadcastReceiver go to onStartCommand())
            mWindowCoordinator.getWindow(Constants.WINDOW_CAPTURE).reInit(new Window.ReinitOptions());
        }
        else
        {
            // Paused: no windows and no frames. The screen-sharing session stays, because Android 14+ cannot
            // start another one without asking again; stopping it here would end the whole service.
            mWindowCoordinator.stopAllWindows();
            if (mVirtualDisplay != null)
            {
                mVirtualDisplay.setSurface(null);
            }
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

    private Notification getNotification()
    {
        String channelId = createNotificationChannel();

        Prefs prefs = KakuKakuTools.getPrefs(this);
        boolean running = prefs.getShowHideSetting();
        String title = getString(running ? R.string.notification_running : R.string.notification_paused);

        Intent launchIntent = getPackageManager().getLaunchIntentForPackage(getPackageName());
        PendingIntent open = PendingIntent.getActivity(this, 0, launchIntent, PendingIntent.FLAG_IMMUTABLE);

        Notification n = new NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.drawable.kakukaku_notification_icon)
                .setContentTitle(title)
                .setStyle(new NotificationCompat.DecoratedCustomViewStyle())
                .setCustomContentView(buildControls(R.layout.notification_collapsed, prefs, running, title))
                .setCustomBigContentView(buildControls(R.layout.notification_expanded, prefs, running, title))
                .setContentIntent(open)
                .setOnlyAlertOnce(true)
                .build();

        n.flags = FLAG_ONGOING_EVENT | FLAG_FOREGROUND_SERVICE;

        return n;
    }

    private static final int[] NOTIFICATION_BUTTONS = {R.id.notif_btn1, R.id.notif_btn2, R.id.notif_btn3, R.id.notif_btn4};
    private static final int[] NOTIFICATION_LABELS = {R.id.notif_btn1_label, R.id.notif_btn2_label, R.id.notif_btn3_label, R.id.notif_btn4_label};
    private static final int[] NOTIFICATION_STATES = {R.id.notif_btn1_state, R.id.notif_btn2_state, R.id.notif_btn3_state, R.id.notif_btn4_state};

    private PendingIntent broadcast(int requestCode, Class<?> receiver)
    {
        return PendingIntent.getBroadcast(this, requestCode, new Intent(this, receiver), PendingIntent.FLAG_IMMUTABLE);
    }

    /**
     * The notification's buttons: Pause, the Instant mode and Image filter toggles (filled when on, outlined when
     * off, with the state written out) and Shutdown. While paused only Resume and Shutdown are shown.
     */
    private RemoteViews buildControls(int layout, Prefs prefs, boolean running, String title)
    {
        RemoteViews views = new RemoteViews(getPackageName(), layout);
        views.setTextViewText(R.id.notif_title, title);

        PendingIntent toggleShowHide = broadcast(Constants.REQUEST_SERVICE_TOGGLE_SHOW_HIDE, ToggleShowHideMainService.class);
        PendingIntent shutdown = broadcast(Constants.REQUEST_SERVICE_SHUTDOWN, CloseMainService.class);

        if (running)
        {
            bindButton(views, 0, getString(R.string.notification_pause), null, false, toggleShowHide, null);
            bindToggle(views, 1, getString(R.string.notification_instant), prefs.getInstantModeSetting(),
                    broadcast(Constants.REQUEST_SERVICE_TOGGLE_INSTANT_MODE, ToggleInstantModeMainService.class), R.string.notification_instant_description);
            bindToggle(views, 2, getString(R.string.notification_filter), prefs.getImageFilterSetting(),
                    broadcast(Constants.REQUEST_SERVICE_TOGGLE_IMAGE_PREVIEW, ToggleImagePreviewMainService.class), R.string.notification_filter_description);
        }
        else
        {
            bindButton(views, 0, getString(R.string.notification_resume), null, true, toggleShowHide, null);
            views.setViewVisibility(NOTIFICATION_BUTTONS[1], View.GONE);
            views.setViewVisibility(NOTIFICATION_BUTTONS[2], View.GONE);
        }
        bindButton(views, 3, getString(R.string.notification_shutdown), null, false, shutdown, null);

        return views;
    }

    private void bindToggle(RemoteViews views, int slot, String label, boolean on, PendingIntent click, int descriptionResource)
    {
        String state = getString(on ? R.string.notification_on : R.string.notification_off);
        String description = getString(descriptionResource, state, getString(on ? R.string.notification_off : R.string.notification_on));
        bindButton(views, slot, label, state, on, click, description);
    }

    /** [filled] buttons are drawn solid (a toggle that is on, or Resume); [state] is the On/Off text of a toggle, if it is one */
    private void bindButton(RemoteViews views, int slot, String label, String state, boolean filled, PendingIntent click, String description)
    {
        int textColor = ContextCompat.getColor(this, filled ? R.color.md_on_primary : R.color.md_on_surface);

        views.setViewVisibility(NOTIFICATION_BUTTONS[slot], View.VISIBLE);
        views.setInt(NOTIFICATION_BUTTONS[slot], "setBackgroundResource", filled ? R.drawable.notif_button_on : R.drawable.notif_button_off);
        views.setTextViewText(NOTIFICATION_LABELS[slot], label);
        views.setTextColor(NOTIFICATION_LABELS[slot], textColor);

        if (state != null)
        {
            views.setViewVisibility(NOTIFICATION_STATES[slot], View.VISIBLE);
            views.setTextViewText(NOTIFICATION_STATES[slot], state);
            views.setTextColor(NOTIFICATION_STATES[slot], textColor);
        }
        else
        {
            views.setViewVisibility(NOTIFICATION_STATES[slot], View.GONE);
        }

        views.setContentDescription(NOTIFICATION_BUTTONS[slot], description != null ? description : label);
        views.setOnClickPendingIntent(NOTIFICATION_BUTTONS[slot], click);
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

    private String createNotificationChannel()
    {
        String channelId = Constants.KAKUKAKU_CHANNEL_ID;
        String channelName = Constants.KAKUKAKU_CHANNEL_NAME;

        NotificationChannel channel = new NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW);
        NotificationManager service = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        service.createNotificationChannel(channel);

        return channelId;
    }
}
