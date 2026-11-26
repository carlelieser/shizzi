package dev.shizzi;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Build;
import android.os.IBinder;

import java.util.ArrayDeque;

/**
 * Pairing from a notification. The pairing code changes every time the Settings dialog closes,
 * so leaving Settings to type it into the app would invalidate it. Instead this foreground service
 * finds the pairing port itself (mDNS _adb-tls-pairing._tcp) and shows a notification with a reply
 * field: type the code there while the Settings dialog stays on screen. All texts are resources, so
 * they follow the system language.
 */
public final class AdbPairingService extends Service {

    static final String ACTION_START = "dev.shizzi.action.ADB_PAIR_START";
    static final String ACTION_STOP = "dev.shizzi.action.ADB_PAIR_STOP";
    static final String KEY_CODE = "pairing_code";
    private static final String CHANNEL = "adb_pairing";
    private static final int ID = 7411;
    private static final String SERVICE_TYPE = "_adb-tls-pairing._tcp";

    /** Port of the pairing dialog that is open right now, or -1. */
    static volatile int port = -1;
    private static volatile boolean running;
    private static volatile AdbPairingService instance;

    private NsdManager nsd;
    private NsdManager.DiscoveryListener discovery;
    // NsdManager (Android 11-13) refuses a second resolve while one is out: one at a time.
    private final ArrayDeque<NsdServiceInfo> queue = new ArrayDeque<>();
    private boolean resolving;

    static void start(Context c) {
        Intent i = new Intent(c, AdbPairingService.class).setAction(ACTION_START);
        if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i);
        else c.startService(i);
    }

    /** Re-posts the reply notification (a RemoteInput reply leaves it "sending" until it is updated). */
    static void rearm() {
        AdbPairingService s = instance;
        if (s == null) return;
        boolean found = port > 0;
        s.notifyNow(s.build(s.getString(found ? R.string.adb_notif_enter : R.string.adb_notif_reopen), found));
    }

    static void stop(Context c) {
        c.stopService(new Intent(c, AdbPairingService.class));
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        instance = this;
        ensureChannel(this);
        startForeground(ID, build(getString(R.string.adb_notif_searching), false));
        if (!running) {
            running = true;
            port = -1;
            startDiscovery();
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        running = false;
        instance = null;
        port = -1;
        stopDiscovery();
        super.onDestroy();
    }

    // ------------------------------------------------------------------ discovery

    private void startDiscovery() {
        nsd = (NsdManager) getSystemService(Context.NSD_SERVICE);
        discovery = new NsdManager.DiscoveryListener() {
            @Override
            public void onDiscoveryStarted(String t) {
            }

            @Override
            public void onServiceFound(NsdServiceInfo info) {
                synchronized (queue) {
                    queue.add(info);
                }
                resolveNext();
            }

            @Override
            public void onServiceLost(NsdServiceInfo info) {
                // The dialog was closed: its code is void now.
                port = -1;
                notifyNow(build(getString(R.string.adb_notif_reopen), false));
            }

            @Override
            public void onDiscoveryStopped(String t) {
            }

            @Override
            public void onStartDiscoveryFailed(String t, int err) {
                notifyNow(build(getString(R.string.adb_notif_discovery_failed, String.valueOf(err)), false));
            }

            @Override
            public void onStopDiscoveryFailed(String t, int err) {
            }
        };
        try {
            nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discovery);
        } catch (Throwable t) {
            notifyNow(build(getString(R.string.adb_notif_discovery_failed, String.valueOf(t.getMessage())), false));
        }
    }

    private void resolveNext() {
        final NsdServiceInfo info;
        synchronized (queue) {
            if (resolving || queue.isEmpty() || !running) return;
            info = queue.poll();
            resolving = true;
        }
        try {
            nsd.resolveService(info, new NsdManager.ResolveListener() {
                @Override
                public void onResolveFailed(NsdServiceInfo i, int err) {
                    resolved();
                }

                @Override
                public void onServiceResolved(NsdServiceInfo i) {
                    port = i.getPort();
                    notifyNow(build(getString(R.string.adb_notif_enter), true));
                    resolved();
                }
            });
        } catch (Throwable t) {
            resolved();
        }
    }

    private void resolved() {
        synchronized (queue) {
            resolving = false;
        }
        resolveNext();
    }

    private void stopDiscovery() {
        try {
            if (nsd != null && discovery != null) nsd.stopServiceDiscovery(discovery);
        } catch (Throwable ignored) {
        }
        discovery = null;
        synchronized (queue) {
            queue.clear();
            resolving = false;
        }
    }

    // ------------------------------------------------------------------ notification

    private static void ensureChannel(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        c.getSystemService(NotificationManager.class).createNotificationChannel(
                new NotificationChannel(CHANNEL, c.getString(R.string.adb_channel),
                        NotificationManager.IMPORTANCE_HIGH));
    }

    private void notifyNow(Notification n) {
        getSystemService(NotificationManager.class).notify(ID, n);
    }

    private Notification build(String text, boolean withInput) {
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, AdbPairActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        b.setSmallIcon(R.drawable.ic_tile_tethering)
                .setContentTitle(getString(R.string.adb_notif_title))
                .setContentText(text)
                .setContentIntent(open)
                .setOngoing(true)
                .setOnlyAlertOnce(true);
        PendingIntent stop = PendingIntent.getService(this, 1,
                new Intent(this, AdbPairingService.class).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        b.addAction(new Notification.Action.Builder(null, getString(R.string.adb_notif_cancel), stop).build());
        if (withInput) {
            RemoteInput ri = new RemoteInput.Builder(KEY_CODE)
                    .setLabel(getString(R.string.adb_notif_label_code)).build();
            // RemoteInput results are written into the intent: it has to be mutable on Android 12+.
            int flags = PendingIntent.FLAG_UPDATE_CURRENT
                    | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
            PendingIntent reply = PendingIntent.getBroadcast(this, 2,
                    new Intent(this, AdbPairReplyReceiver.class).setPackage(getPackageName()), flags);
            b.addAction(new Notification.Action.Builder(null, getString(R.string.adb_notif_action_code), reply)
                    .addRemoteInput(ri).build());
        }
        return b.build();
    }

    static void postResult(Context c, String text) {
        ensureChannel(c);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(c, CHANNEL) : new Notification.Builder(c);
        PendingIntent open = PendingIntent.getActivity(c, 0, new Intent(c, AdbPairActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        b.setSmallIcon(R.drawable.ic_tile_tethering)
                .setContentTitle(c.getString(R.string.adb_notif_title))
                .setContentText(text)
                .setContentIntent(open)
                .setAutoCancel(true);
        c.getSystemService(NotificationManager.class).notify(ID + 1, b.build());
    }
}
