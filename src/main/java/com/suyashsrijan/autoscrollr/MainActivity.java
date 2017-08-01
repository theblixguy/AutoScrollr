package com.suyashsrijan.autoscrollr;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.HandlerThread;
import android.preference.PreferenceManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.SwitchCompat;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.ToggleButton;

import com.afollestad.materialdialogs.MaterialDialog;
import com.nanotasks.BackgroundWork;
import com.nanotasks.Completion;
import com.nanotasks.Tasks;

import java.util.List;

import eu.chainfire.libsuperuser.Shell;

public class MainActivity extends AppCompatActivity implements CompoundButton.OnCheckedChangeListener {

    private static String TAG = "AutoScrollr-App";

    private SharedPreferences settings;
    private SharedPreferences.Editor editor;
    private boolean isServiceEnabled = false;
    private boolean isSuAvailable = false;
    private boolean showDonateDevDialog = true;
    private TextView textViewStatus;
    private int mLastExitCode = -1;
    private boolean mCommandRunning = false;
    private HandlerThread mCallbackThread = null;
    private static Shell.Interactive rootSession;
    private MaterialDialog progressDialog = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setElevation(0.0f);
        }

        CustomTabs.with(getApplicationContext()).warm();
        settings = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        final SwitchCompat toggleButtonService = findViewById(R.id.switch1);
        textViewStatus = findViewById(R.id.textView2);

        showDonateDevDialog = settings.getBoolean("showDonateDevDialog", true);
        isServiceEnabled = settings.getBoolean("isServiceEnabled", false);
        isSuAvailable = settings.getBoolean("isSuAvailable", false);

        toggleButtonService.setOnCheckedChangeListener(null);

        if (Utils.isMyServiceRunning(ScrollrService.class, this)) {
            textViewStatus.setText(R.string.autoscrollr_active_status_text);
            toggleButtonService.setChecked(true);
        } else {
            textViewStatus.setText(R.string.autoscrollr_inactive_status_text);
            toggleButtonService.setChecked(false);
        }

        toggleButtonService.setOnCheckedChangeListener(this);

        if (!isSuAvailable) {

            Tasks.executeInBackground(MainActivity.this, new BackgroundWork<Boolean>() {
                @Override
                public Boolean doInBackground() throws Exception {
                    if (rootSession != null) {
                        if (rootSession.isRunning()) {
                            return true;
                        } else {
                            dispose();
                        }
                    }

                    mCallbackThread = new HandlerThread("SU callback");
                    mCallbackThread.start();

                    mCommandRunning = true;
                    rootSession = new Shell.Builder().useSU()
                            .setHandler(new Handler(mCallbackThread.getLooper()))
                            .setOnSTDERRLineListener(mStderrListener)
                            .open(mOpenListener);

                    waitForCommandFinished();

                    if (mLastExitCode != Shell.OnCommandResultListener.SHELL_RUNNING) {
                        dispose();
                        return false;
                    }
                    return true;
                }
            }, new Completion<Boolean>() {
                @Override
                public void onSuccess(Context context, Boolean result) {
                    if (progressDialog != null) {
                        progressDialog.dismiss();
                    }
                    isSuAvailable = result;
                    Log.i(TAG, "SU available: " + Boolean.toString(result));
                    if (isSuAvailable) {
                        Log.i(TAG, "Phone is rooted and SU permission granted");
                        editor = settings.edit();
                        editor.putBoolean("isSuAvailable", true);
                        editor.apply();

                        if (isServiceEnabled) {
                            toggleButtonService.setChecked(true);
                            if (!Utils.isMyServiceRunning(ScrollrService.class, MainActivity.this)) {
                                Log.i(TAG, "Starting AutoScrollr service");
                                startService(new Intent(context, ScrollrService.class));
                            } else {
                                Log.i(TAG, "Service already running");
                            }
                        } else {
                            Log.i(TAG, "Service not enabled");
                        }

                    } else {
                        Log.i(TAG, "SU permission denied or not available");
                        toggleButtonService.setChecked(false);
                        toggleButtonService.setEnabled(false);
                        textViewStatus.setText(R.string.autoscrollr_disabled_status_text);
                        editor = settings.edit();
                        editor.putBoolean("isSuAvailable", false);
                        editor.apply();
                        AlertDialog.Builder builder = new AlertDialog.Builder(context, R.style.AppCompatAlertDialogStyle);
                        builder.setTitle(getString(R.string.dialog_error_title));
                        builder.setMessage(getString(R.string.dialog_error_message));
                        builder.setPositiveButton(getString(R.string.dialog_error_close_button_text), null);
                        builder.show();
                    }
                }

                @Override
                public void onError(Context context, Exception e) {
                    Log.e(TAG, "Error querying SU: " + e.getMessage());
                    Log.i(TAG, "SU permission denied or not available");
                    toggleButtonService.setChecked(false);
                    toggleButtonService.setEnabled(false);
                    textViewStatus.setText(R.string.autoscrollr_disabled_status_text);
                    editor = settings.edit();
                    editor.putBoolean("isSuAvailable", false);
                    editor.apply();
                    AlertDialog.Builder builder = new AlertDialog.Builder(context, R.style.AppCompatAlertDialogStyle);
                    builder.setTitle(getString(R.string.dialog_error_title));
                    builder.setMessage(getString(R.string.dialog_error_message));
                    builder.setPositiveButton(getString(R.string.dialog_error_close_button_text), null);
                    builder.show();
                }
            });
        } else {
            if (showDonateDevDialog) {
                showDonateDevDialog();
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        switch (id) {
            case R.id.action_app_settings:
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
                break;
            case R.id.action_donate_dev:
                openDonatePage();
                break;
            case R.id.action_donate_about:
                startActivity(new Intent(MainActivity.this, AboutActivity.class));
                break;

        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
        if (b) {
            editor = settings.edit();
            editor.putBoolean("isServiceEnabled", true);
            editor.apply();
            isServiceEnabled = true;
            textViewStatus.setText(R.string.autoscrollr_active_status_text);
            if (!Utils.isMyServiceRunning(ScrollrService.class, MainActivity.this)) {
                Log.i(TAG, "Enabling AutoScrollr");
                startService(new Intent(MainActivity.this, ScrollrService.class));
            }
            showScrollrActiveDialog();
        } else {
            editor = settings.edit();
            editor.putBoolean("isServiceEnabled", false);
            editor.apply();
            isServiceEnabled = false;
            textViewStatus.setText(R.string.autoscrollr_inactive_status_text);
            if (Utils.isMyServiceRunning(ScrollrService.class, MainActivity.this)) {
                Log.i(TAG, "Disabling AutoScrollr");
                stopService(new Intent(MainActivity.this, ScrollrService.class));
            }
        }
    }

    private final Shell.OnCommandResultListener mOpenListener = new Shell.OnCommandResultListener() {
        @Override
        public void onCommandResult(int commandCode, int exitCode, List<String> output) {
            mStdoutListener.onCommandResult(commandCode, exitCode);
        }
    };

    private final Shell.OnCommandLineListener mStdoutListener = new Shell.OnCommandLineListener() {
        public void onLine(String line) {
            Log.i(TAG, line);
        }

        @Override
        public void onCommandResult(int commandCode, int exitCode) {
            mLastExitCode = exitCode;
            synchronized (mCallbackThread) {
                mCommandRunning = false;
                mCallbackThread.notifyAll();
            }
        }
    };

    private final Shell.OnCommandLineListener mStderrListener = new Shell.OnCommandLineListener() {
        @Override
        public void onLine(String line) {
            Log.i(TAG, line);
        }

        @Override
        public void onCommandResult(int commandCode, int exitCode) {

        }
    };

    private void waitForCommandFinished() {
        synchronized (mCallbackThread) {
            while (mCommandRunning) {
                try {
                    mCallbackThread.wait();
                } catch (Exception e)  {
                    if (e instanceof InterruptedException) {
                        Log.i(TAG, "InterruptedException occurred while waiting for command to finish");
                        e.printStackTrace();
                    } else if (e instanceof NullPointerException) {
                        Log.i(TAG, "NPE occurred while waiting for command to finish");
                        e.printStackTrace();
                    }
                }
            }
        }

        if (mLastExitCode == Shell.OnCommandResultListener.WATCHDOG_EXIT || mLastExitCode == Shell.OnCommandResultListener.SHELL_DIED) {
            dispose();
        }
    }

    public synchronized void dispose() {
        if (rootSession == null) {
            return;
        }

        try {
            rootSession.close();
        } catch (Exception ignored) {
        }
        rootSession = null;

        mCallbackThread.quit();
        mCallbackThread = null;
    }

    public void openDonatePage() {
        CustomTabs.with(getApplicationContext())
                .setStyle(new CustomTabs.Style(getApplicationContext())
                        .setShowTitle(true)
                        .setExitAnimation(android.R.anim.slide_in_left, android.R.anim.slide_out_right)
                        .setToolbarColor(R.color.colorPrimary))
                .openUrl("https://www.paypal.me/suyashsrijan", this);
    }

    public void showScrollrActiveDialog() {
            AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AppCompatAlertDialogStyle);
            builder.setTitle(getString(R.string.autoscrollr_active_dialog_title));
            builder.setMessage(getString(R.string.autoscrollr_active_dialog_message_text));
            builder.setPositiveButton(getString(R.string.autoscrollr_active_dialog_positive_button_text), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    dialogInterface.dismiss();
                }
            });
            builder.show();
    }

    public void showDonateDevDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AppCompatAlertDialogStyle);
        builder.setTitle(getString(R.string.donate_dialog_title_text));
        builder.setMessage(getString(R.string.donate_dialog_message_text));
        builder.setPositiveButton(getString(R.string.donate_dialog_positive_button_text), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                editor = settings.edit();
                editor.putBoolean("showDonateDevDialog", false);
                editor.apply();
                dialogInterface.dismiss();
                openDonatePage();
            }
        });
        builder.setNegativeButton(getString(R.string.donate_dialog_close_button_text), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                editor = settings.edit();
                editor.putBoolean("showDonateDevDialog", false);
                editor.apply();
                dialogInterface.dismiss();
            }
        });
        builder.show();
    }
}
