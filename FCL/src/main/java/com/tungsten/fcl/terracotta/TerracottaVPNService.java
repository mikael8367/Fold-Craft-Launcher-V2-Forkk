package com.tungsten.fcl.terracotta;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import com.tungsten.fcl.R;
import com.mio.util.AndroidUtilKt;
import com.tungsten.fcl.util.RequestCodes;

import net.burningtnt.terracotta.TerracottaAndroidAPI;

import java.io.IOException;

@SuppressLint("VpnServicePolicy")
public class TerracottaVPNService extends VpnService {

    private static final String TAG                  = "TerracottaVPNService";

    private static final String CHANNEL_ID           = "terracotta_vpn_channel";
    private static final int VPN_NOTIFICATION_ID     = 1;

    public static final String ACTION_START          = "net.burningtnt.terracotta.action.START";
    public static final String ACTION_STOP           = "net.burningtnt.terracotta.action.STOP";
    public static final String ACTION_REPOST         = "net.burningtnt.terracotta.action.REPOST";
    public static final String ACTION_UPDATE_STATE   = "net.burningtnt.terracotta.action.UPDATE_STATE";

    private static final String EXTRA_FROM_DELETE    = "from_delete";
    public static final String EXTRA_STATE_TEXT      = "terracotta_state_text";

    private NotificationManager notificationManager;
    private String currentStateText = null;
    private volatile boolean isStopping = false;

    private ParcelFileDescriptor vpnInterface;

    private static volatile boolean running = false;

    public static boolean isRunning() {
        return running;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;
        Log.d(TAG, "onStartCommand, action = " + action);

        if (notificationManager == null) {
            notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        }

        if (ACTION_STOP.equals(action)) {
            isStopping = true;
            cleanup();
            stopForeground(true);
            stopSelf();
            return Service.START_NOT_STICKY;
        }

        if (ACTION_UPDATE_STATE.equals(action)) {
            if (intent != null && intent.hasExtra(EXTRA_STATE_TEXT)) {
                currentStateText = intent.getStringExtra(EXTRA_STATE_TEXT);
            }

            if (!isStopping && vpnInterface != null) {
                createNotificationChannelIfNeeded();
                Notification n = buildVpnNotification();
                if (n != null) {
                    // Updating an existing tunnel also refreshes its foreground notification.
                    startForeground(VPN_NOTIFICATION_ID, n);
                    return Service.START_STICKY;
                }
            }

            stopSelf(startId);
            return Service.START_NOT_STICKY;
        }

        boolean fromDelete = intent != null && intent.getBooleanExtra(EXTRA_FROM_DELETE, false);

        if (ACTION_REPOST.equals(action) && fromDelete && !isStopping && vpnInterface != null) {
            Log.d(TAG, "Repost VPN notification after user cleared it.");
            if (intent.hasExtra(EXTRA_STATE_TEXT)) {
                currentStateText = intent.getStringExtra(EXTRA_STATE_TEXT);
            }
            Notification notification = buildVpnNotification();
            if (notification == null) {
                stopSelf(startId);
                return Service.START_NOT_STICKY;
            }
            startForeground(VPN_NOTIFICATION_ID, notification);
            return Service.START_STICKY;
        }

        // A sticky restart may deliver a null intent. It must not be interpreted
        // as a fresh VPN request because the native pending request may no longer exist.
        if (!ACTION_START.equals(action)) {
            Log.w(TAG, "Ignoring unsupported or stale start action: " + action);
            stopSelf(startId);
            return Service.START_NOT_STICKY;
        }

        isStopping = false;

        createNotificationChannelIfNeeded();

        Notification notification = buildVpnNotification();
        if (notification == null) {
            try {
                TerracottaAndroidAPI.getPendingVpnServiceRequest().reject();
            } catch (RuntimeException e) {
                Log.w(TAG, "No pending VPN request to reject after notification setup failed", e);
            }
            stopSelf(startId);
            return Service.START_NOT_STICKY;
        }
        startForeground(VPN_NOTIFICATION_ID, notification);

        Builder vpnBuilder = new Builder().setSession("Terracotta Connection");

        try {
            vpnBuilder.addDisallowedApplication(getPackageName());
        } catch (PackageManager.NameNotFoundException ignored) {
        }

        TerracottaAndroidAPI.VpnServiceRequest request = TerracottaAndroidAPI.getPendingVpnServiceRequest();
        try {
            vpnInterface = request.startVpnService(vpnBuilder);
            running = true;
        } catch (RuntimeException e) {
            Log.e(TAG, "Failed to establish Terracotta VPN interface", e);
            try {
                request.reject();
            } catch (RuntimeException rejectError) {
                Log.e(TAG, "Failed to reject pending Terracotta VPN request", rejectError);
            }
            cleanup();
            stopForeground(true);
            stopSelf();
            return Service.START_NOT_STICKY;
        }

        return Service.START_STICKY;
    }

    @Override
    public void onRevoke() {
        Log.w(TAG, "onRevoke(): preempted by another VPN or revoked by user; tearing down.");
        isStopping = true;
        Terracotta.setWaiting(this, false);
        cleanup();
        stopForeground(true);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy(): vpn service finished");
        isStopping = true;
        Terracotta.setWaiting(this, false);
        cleanup();
        super.onDestroy();
    }

    private void createNotificationChannelIfNeeded() {
        if (notificationManager == null)
            return;

        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Terracotta VPN",
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription("Terracotta VPN state");
        channel.setShowBadge(false);
        notificationManager.createNotificationChannel(channel);
    }

    private Notification buildVpnNotification() {
        Terracotta.TerracottaMode mode = Terracotta.getMode();
        if (mode == null)
            return null;

        String title = getString(R.string.terracotta_notification_title);
        String modeText = mode == Terracotta.TerracottaMode.HOST ? getString(R.string.terracotta_player_kind_host) : getString(R.string.terracotta_player_kind_guest);
        if (currentStateText == null) {
            TerracottaState.Ready state = Terracotta.stateProperty().get();
            if (state != null && !(state instanceof TerracottaState.Waiting)) {
                currentStateText = AndroidUtilKt.getLocalizedText(getApplicationContext(), "terracotta_status_" + state);
            }
        }
        String contentText = String.format(getString(R.string.terracotta_notification_desc), modeText, currentStateText);

        Notification.Builder builder;
        builder = new Notification.Builder(this, CHANNEL_ID);

        Intent deleteIntent = new Intent(this, TerracottaVPNService.class)
                .setAction(ACTION_REPOST)
                .putExtra(EXTRA_FROM_DELETE, true)
                .putExtra(EXTRA_STATE_TEXT, currentStateText);
        PendingIntent deletePendingIntent = PendingIntent.getService(
                this,
                RequestCodes.VPN_REPOST_CODE,
                deleteIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        builder.setSmallIcon(R.drawable.img_enchantnet)
                .setContentTitle(title)
                .setContentText(contentText)
                .setWhen(System.currentTimeMillis())
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setDeleteIntent(deletePendingIntent);

        return builder.build();
    }

    private void cleanup() {
        Log.d(TAG, "cleanup(): close tun & cancel notification");

        if (notificationManager != null) {
            notificationManager.cancel(VPN_NOTIFICATION_ID);
        }

        if (vpnInterface != null) {
            try {
                vpnInterface.close();
            } catch (IOException ignored) {
            }
            vpnInterface = null;
        }

        running = false;
    }

}
