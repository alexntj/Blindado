package com.passador.replay.overlay;

import android.app.*;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import com.passador.replay.installer.ModuleInstaller;

public final class FloatingService extends Service {
    private static final String CHANNEL = "floating";
    private static final int NOTIF_ID = 0x962;

    public interface ShellProvider { String exec(String cmd) throws Exception; }
    private static ShellProvider shellRef;

    public static void setShell(ShellProvider s) { shellRef = s; }

    private WindowManager wm;
    private View root;
    private WindowManager.LayoutParams lp;
    private TextView status;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override public void onCreate() {
        super.onCreate();
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        startForeground(NOTIF_ID, buildNotification());
    }

    @Override public int onStartCommand(Intent i, int f, int s) {
        if (root == null) showOverlay();
        return START_STICKY;
    }

    private Notification buildNotification() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL,
                "Menu flutuante", NotificationManager.IMPORTANCE_LOW);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
            ? new Notification.Builder(this, CHANNEL)
            : new Notification.Builder(this);
        return builder
            .setContentTitle("Menu ativo")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true).build();
    }

    private void showOverlay() {
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
            stopSelf(); return;
        }
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundColor(0xCC101418);
        box.setPadding(24, 24, 24, 24);

        TextView title = new TextView(this);
        title.setText("Menu");
        title.setTextColor(Color.WHITE);
        title.setTextSize(16f);
        box.addView(title);

        status = new TextView(this);
        status.setText("Pronto");
        status.setTextColor(0xFFAACCFF);
        status.setTextSize(12f);
        box.addView(status);

        Button inject = new Button(this);
        inject.setText("INJECT");
        inject.setOnClickListener(v -> doInject());
        box.addView(inject);

        Button close = new Button(this);
        close.setText("Fechar");
        close.setOnClickListener(v -> stopSelf());
        box.addView(close);

        lp = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = 32; lp.y = 200;

        box.setOnTouchListener(new View.OnTouchListener() {
            int sx, sy; float tx, ty;
            @Override public boolean onTouch(View v, MotionEvent e) {
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        sx = lp.x; sy = lp.y;
                        tx = e.getRawX(); ty = e.getRawY();
                        return false;
                    case MotionEvent.ACTION_MOVE:
                        lp.x = sx + (int)(e.getRawX() - tx);
                        lp.y = sy + (int)(e.getRawY() - ty);
                        wm.updateViewLayout(root, lp);
                        return false;
                }
                return false;
            }
        });

        root = box;
        wm.addView(root, lp);
    }

    private void doInject() {
        status.setText("Injetando...");
        new Thread(() -> {
            try {
                String msg;
                if (shellRef != null) {
                    msg = ModuleInstaller.viaShell(this,
                        shellRef::exec,
                        m -> mainHandler.post(() -> status.setText(m)));
                } else {
                    msg = ModuleInstaller.direct(this,
                        m -> mainHandler.post(() -> status.setText(m)));
                }
                final String resultMessage = msg;
                mainHandler.post(() -> status.setText(resultMessage));
            } catch (Exception e) {
                mainHandler.post(() -> status.setText("Falha: " + e.getMessage()));
            }
        }).start();
    }

    @Override public void onDestroy() {
        super.onDestroy();
        if (root != null && wm != null) {
            try { wm.removeView(root); } catch (Exception ignored) {}
            root = null;
        }
    }

    @Override public IBinder onBind(Intent i) { return null; }
}
