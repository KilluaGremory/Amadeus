package com.example.yink.amadeus;

import android.Manifest;
import android.app.KeyguardManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.SpeechRecognizer;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.TaskStackBuilder;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.preference.PreferenceManager;

public class LaunchActivity extends AppCompatActivity {

    private ImageView connect, cancel, logo;
    private TextView status;
    private Boolean isPressed = false;
    private MediaPlayer m;
    private Handler aniHandle = new Handler(Looper.getMainLooper());

    private int i = 0;

    Runnable aniRunnable = new Runnable() {
        public void run() {
            final int DURATION = 20;
            if (i < 39) {
                i++;
                String imgName = "logo" + Integer.toString(i);
                int id = getResources().getIdentifier(imgName, "drawable", getPackageName());
                if (id != 0) {
                    logo.setImageDrawable((ContextCompat.getDrawable(LaunchActivity.this, id)));
                }
                aniHandle.postDelayed(this, DURATION);
            }
        }
    };

    private boolean isGoogleAppAvailable(Context context) {
        try {
            context.getPackageManager().getPackageInfo("com.google.android.googlequicksearchbox", 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return SpeechRecognizer.isRecognitionAvailable(context);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_launch);

        NotificationHelper.createNotificationChannels(this);

        // Edge-to-edge system window insets support
        View rootView = findViewById(R.id.activity_launch);
        if (rootView != null) {
            ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, windowInsets) -> {
                Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(insets.left, insets.top, insets.right, insets.bottom);
                return WindowInsetsCompat.CONSUMED;
            });
        }

        connect = (ImageView) findViewById(R.id.imageView_connect);
        cancel = (ImageView) findViewById(R.id.imageView_cancel);
        status = (TextView) findViewById(R.id.textView_call);
        logo = (ImageView) findViewById(R.id.imageView_logo);
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        final Window win = getWindow();

        aniHandle.post(aniRunnable);

        if (!isGoogleAppAvailable(LaunchActivity.this)) {
            status.setText(R.string.google_app_error);
        }

        if (Alarm.isPlaying()) {
            status.setText(R.string.incoming_call);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                setShowWhenLocked(true);
                setTurnScreenOn(true);
                KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
                if (km != null) {
                    km.requestDismissKeyguard(this, null);
                }
            } else {
                win.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
                win.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
            }
        }

        if (settings.getBoolean("show_notification", false)) {
            showNotification();
        }

        connect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!isPressed) {
                    if (!isGoogleAppAvailable(LaunchActivity.this)) {
                        Toast.makeText(LaunchActivity.this, R.string.google_app_error, Toast.LENGTH_LONG).show();
                    }

                    isPressed = true;
                    connect.setImageResource(R.drawable.connect_select);

                    if (!Alarm.isPlaying()) {
                        m = MediaPlayer.create(LaunchActivity.this, R.raw.tone);
                        if (m != null) {
                            m.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                                @Override
                                public void onPrepared(MediaPlayer mp) {
                                    mp.start();
                                    status.setText(R.string.connecting);
                                }
                            });

                            m.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                                @Override
                                public void onCompletion(MediaPlayer mp) {
                                    mp.release();
                                    m = null;
                                    Intent intent = new Intent(LaunchActivity.this, MainActivity.class);
                                    startActivity(intent);
                                }
                            });
                        } else {
                            Intent intent = new Intent(LaunchActivity.this, MainActivity.class);
                            startActivity(intent);
                        }
                    } else {
                        Alarm.cancel(LaunchActivity.this);
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                            setShowWhenLocked(false);
                            setTurnScreenOn(false);
                        } else {
                            win.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
                            win.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
                        }

                        Intent intent = new Intent(LaunchActivity.this, MainActivity.class);
                        startActivity(intent);
                    }
                }
            }
        });

        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                cancel.setImageResource(R.drawable.cancel_select);
                Alarm.cancel(getApplicationContext());
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    setShowWhenLocked(false);
                    setTurnScreenOn(false);
                } else {
                    win.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
                    win.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
                }

                Intent intent = new Intent(Intent.ACTION_MAIN);
                intent.addCategory(Intent.CATEGORY_HOME);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            }
        });

        logo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent settingIntent = new Intent(LaunchActivity.this, SettingsActivity.class);
                startActivity(settingIntent);
            }
        });
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LangContext.wrap(newBase));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        final Window win = getWindow();

        if (m != null) {
            try {
                if (m.isPlaying()) m.stop();
            } catch (Exception ignored) {
            }
            m.release();
            m = null;
        }

        Alarm.cancel(LaunchActivity.this);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(false);
            setTurnScreenOn(false);
        } else {
            win.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
            win.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }
        aniHandle.removeCallbacks(aniRunnable);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (isPressed) {
            status.setText(R.string.disconnected);
        } else if (!isGoogleAppAvailable(LaunchActivity.this)) {
            status.setText(R.string.google_app_error);
        } else if (Alarm.isPlaying()) {
            status.setText(R.string.incoming_call);
        } else {
            status.setText(R.string.call);
        }

        isPressed = false;
        connect.setImageResource(R.drawable.connect_unselect);
        cancel.setImageResource(R.drawable.cancel_unselect);
    }

    private void showNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
                return;
            }
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(LaunchActivity.this, NotificationHelper.CHANNEL_GENERAL_ID)
                .setSmallIcon(R.drawable.xp2)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.notification_text))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        Intent resultIntent = new Intent(LaunchActivity.this, MainActivity.class);
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(LaunchActivity.this);
        stackBuilder.addParentStack(MainActivity.class);
        stackBuilder.addNextIntent(resultIntent);

        int piFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            piFlags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(0, piFlags);
        builder.setContentIntent(resultPendingIntent);
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.notify(0, builder.build());
        }
    }
}