package com.suyashsrijan.autoscrollr;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.os.AsyncTask;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import eu.chainfire.libsuperuser.Shell;


public class ScrollrService extends Service {
    public ScrollrService() {
    }

    private static String TAG = "AutoScrollr-Service";
    private static Shell.Interactive rootSession;
    private NotificationCompat.Builder mBuilder;
    private StartScrollrBroadcast startScrollrBroadcast;
    private WindowManager mWindowManager;
    private View mFloatingView;
    private Point centerPoint;
    private boolean isAutoScrolling = false;
    private int scrollSpeed = 3000;
    private boolean scrollDown = true;
    private Timer scrollTimer;

    @Override
    public void onCreate() {
        super.onCreate();
        mBuilder = new NotificationCompat.Builder(this);
        startScrollrBroadcast = new StartScrollrBroadcast();
        LocalBroadcastManager.getInstance(this).registerReceiver(startScrollrBroadcast, new IntentFilter("start-scrollr"));
        executeCommandWithRoot("whoami"); // Init interactive root shell
        if (!Utils.isSystemAlertWindowPermissionGranted(getApplicationContext())) {
            executeCommandWithRoot("pm grant com.suyashsrijan.autoscrollr android.permission.SYSTEM_ALERT_WINDOW");
        }
        registerReceiver(startScrollrBroadcast, new IntentFilter("com.suyashsrijan.autoscrollr.OPEN_WIDGET"));
        centerPoint = Utils.getDisplayCenterCoordinates(getApplicationContext());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        Log.i(TAG, "Service has now started");
        showNotification();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "Stopping service");
        if (rootSession != null) {
            rootSession.close();
            rootSession = null;
        }
        if (mFloatingView != null || mFloatingView.getWindowToken() != null) {
            try {
                mWindowManager.removeView(mFloatingView);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (scrollTimer != null) {
            scrollTimer.cancel();
            scrollTimer.purge();
        }
        unregisterReceiver(startScrollrBroadcast);
    }

    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    class StartScrollrBroadcast extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (mFloatingView == null || mFloatingView.getWindowToken() == null) {
                Log.i(TAG, "Starting Scrollr");
                mFloatingView = LayoutInflater.from(context).inflate(R.layout.layout_floating_scrollr_widget, null);
                final WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.TYPE_PHONE,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                        PixelFormat.TRANSLUCENT);
                params.gravity = Gravity.TOP | Gravity.LEFT;
                params.x = 0;
                params.y = 100;
                mWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

                final View collapsedView = mFloatingView.findViewById(R.id.collapse_view);
                final View expandedView = mFloatingView.findViewById(R.id.expanded_container);

                mFloatingView.findViewById(R.id.root_container).setOnTouchListener(new View.OnTouchListener() {
                    private int initialX;
                    private int initialY;
                    private float initialTouchX;
                    private float initialTouchY;


                    @Override
                    public boolean onTouch(View v, MotionEvent event) {
                        switch (event.getAction()) {
                            case MotionEvent.ACTION_DOWN:
                                initialX = params.x;
                                initialY = params.y;
                                initialTouchX = event.getRawX();
                                initialTouchY = event.getRawY();
                                return true;
                            case MotionEvent.ACTION_UP:
                                int Xdiff = (int) (event.getRawX() - initialTouchX);
                                int Ydiff = (int) (event.getRawY() - initialTouchY);
                                if (Xdiff < 10 && Ydiff < 10) {
                                    if (mFloatingView == null || mFloatingView.findViewById(R.id.collapse_view).getVisibility() == View.VISIBLE) {
                                        collapsedView.setVisibility(View.GONE);
                                        expandedView.setVisibility(View.VISIBLE);
                                    }
                                }
                                return true;
                            case MotionEvent.ACTION_MOVE:
                                params.x = initialX + (int) (event.getRawX() - initialTouchX);
                                params.y = initialY + (int) (event.getRawY() - initialTouchY);
                                mWindowManager.updateViewLayout(mFloatingView, params);
                                return true;
                        }
                        return false;
                    }
                });

                ImageView closeButtonCollapsed = mFloatingView.findViewById(R.id.close_btn);
                closeButtonCollapsed.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if (mFloatingView != null) mWindowManager.removeView(mFloatingView);
                        scrollDown = true;
                    }
                });

                final ImageView playButton = mFloatingView.findViewById(R.id.play_btn);
                playButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (isAutoScrolling) {
                            Log.i(TAG, "Stopping Scrollr timer");
                            playButton.setImageResource(R.drawable.ic_play_arrow_white_24dp);
                            isAutoScrolling = false;
                            scrollTimer.cancel();
                            scrollTimer.purge();
                            scrollTimer = null;
                        } else {
                            Log.i(TAG, "Starting Scrollr timer");
                            playButton.setImageResource(R.drawable.ic_pause_white_24dp);
                            isAutoScrolling = true;
                            scrollTimer = new Timer();
                            scrollTimer.scheduleAtFixedRate(new ScrollrTask(), 0, scrollSpeed);
                        }
                    }
                });

                ImageView nextButton = mFloatingView.findViewById(R.id.next_btn);
                nextButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (scrollSpeed > 1500 && scrollTimer != null) {
                            Log.i(TAG, "Decreasing Scrollr timer interval by 500ms");
                            scrollSpeed -= 500;
                            scrollTimer.cancel();
                            scrollTimer.purge();
                            scrollTimer = null;
                            scrollTimer = new Timer();
                            scrollTimer.scheduleAtFixedRate(new ScrollrTask(), 0, scrollSpeed);
                        }
                    }
                });

                ImageView prevButton = mFloatingView.findViewById(R.id.prev_btn);
                prevButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (scrollSpeed < 25000 && scrollTimer != null) {
                            Log.i(TAG, "Increasing Scrollr timer interval by 500ms");
                            scrollSpeed += 500;
                            scrollTimer.cancel();
                            scrollTimer.purge();
                            scrollTimer = null;
                            scrollTimer = new Timer();
                            scrollTimer.scheduleAtFixedRate(new ScrollrTask(), 0, scrollSpeed);
                        }
                    }
                });

                ImageView closeButton = mFloatingView.findViewById(R.id.close_button);
                closeButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        collapsedView.setVisibility(View.VISIBLE);
                        expandedView.setVisibility(View.GONE);
                    }
                });

                ImageView changeDirectionButton = mFloatingView.findViewById(R.id.change_direction_button);
                changeDirectionButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if (scrollTimer != null) {
                            Log.i(TAG, "Changing Scrollr direction");
                            scrollDown ^= true;
                        }
                    }
                });
                mWindowManager.addView(mFloatingView, params);
            } else {
                Log.i(TAG, "Scrollr already open");
            }
        }
    }

    private class ScrollrTask extends TimerTask {
        @Override
        public void run() {
            executeCommandWithRoot("input swipe "
                    + centerPoint.x + " "
                    + centerPoint.y + " "
                    + centerPoint.x + " "
                    + (scrollDown ? Integer.toString(centerPoint.y - 150) : Integer.toString(centerPoint.y + 150)));
        }
    }

    public void showNotification() {
        Intent notificationIntent = new Intent("com.suyashsrijan.autoscrollr.OPEN_WIDGET");
        PendingIntent intent = PendingIntent.getBroadcast(getApplicationContext(), 0,
                notificationIntent, 0);
        Notification n = mBuilder
                .setContentTitle("AutoScrollr")
                .setStyle(new NotificationCompat.BigTextStyle().bigText("Tap to open AutoScrollr"))
                .setSmallIcon(R.mipmap.ic_launcher)
                .setPriority(-2)
                .setContentIntent(intent)
                .setOngoing(true).build();
        startForeground(3107, n);
    }

    public void executeCommandWithRoot(final String command) {
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                if (rootSession != null) {
                    rootSession.addCommand(command, 0, new Shell.OnCommandResultListener() {
                        @Override
                        public void onCommandResult(int commandCode, int exitCode, List<String> output) {
                            printShellOutput(output);
                        }
                    });
                } else {
                    rootSession = new Shell.Builder().
                            useSU().
                            setWantSTDERR(true).
                            setWatchdogTimeout(5).
                            setMinimalLogging(true).
                            open(new Shell.OnCommandResultListener() {
                                @Override
                                public void onCommandResult(int commandCode, int exitCode, List<String> output) {
                                    if (exitCode != Shell.OnCommandResultListener.SHELL_RUNNING) {
                                        Log.i(TAG, "Error opening root shell: exitCode " + exitCode);
                                    } else {
                                        rootSession.addCommand(command, 0, new Shell.OnCommandResultListener() {
                                            @Override
                                            public void onCommandResult(int commandCode, int exitCode, List<String> output) {
                                                printShellOutput(output);
                                            }
                                        });
                                    }
                                }
                            });
                }
            }
        });
    }

    public void printShellOutput(List<String> output) {
        if (output != null && !output.isEmpty()) {
            for (String s : output) {
                Log.i(TAG, s);
            }
        }
    }
}
