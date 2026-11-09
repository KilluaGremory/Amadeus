package com.example.yink.amadeus;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

public class NotificationHelper {

    public static final String CHANNEL_ALARM_ID = "amadeus_alarm_channel";
    public static final String CHANNEL_GENERAL_ID = "amadeus_general_channel";

    public static void createNotificationChannels(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null) {
                // Alarm Channel (High importance)
                NotificationChannel alarmChannel = new NotificationChannel(
                        CHANNEL_ALARM_ID,
                        context.getString(R.string.incoming_call),
                        NotificationManager.IMPORTANCE_HIGH
                );
                alarmChannel.setDescription("Amadeus Incoming Call & Alarm Notifications");
                alarmChannel.enableVibration(true);
                alarmChannel.setLockscreenVisibility(android.app.Notification.VISIBILITY_PUBLIC);
                manager.createNotificationChannel(alarmChannel);

                // General Channel
                NotificationChannel generalChannel = new NotificationChannel(
                        CHANNEL_GENERAL_ID,
                        context.getString(R.string.app_name),
                        NotificationManager.IMPORTANCE_DEFAULT
                );
                generalChannel.setDescription("Amadeus Background Notifications");
                manager.createNotificationChannel(generalChannel);
            }
        }
    }
}
