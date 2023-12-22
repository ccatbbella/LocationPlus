package edu.ucsb.ece150.locationplus;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.TaskStackBuilder;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;
import androidx.core.app.JobIntentService;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofenceStatusCodes;
import com.google.android.gms.location.GeofencingEvent;

public class GeofenceTransitionJobIntentService extends JobIntentService {
    private final String CHANNEL_ID = "LocationPlus";
    private final String CHANNEL_NAME = "Geofence Notification Channel";
    private final int NOTIFICATION_ID = 1;
    private final int PENDING_INTENT_REQUEST_CODE = 0;
    private NotificationChannel mNotificationChannel;
    private NotificationManager mNotificationManager;
    private NotificationManagerCompat mNotificationManagerCompat;

    public static void enqueueWork(Context context, Intent intent) {
        enqueueWork(context, GeofenceTransitionJobIntentService.class, 0, intent);
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public void onHandleWork(Intent intent) {
        GeofencingEvent geofencingEvent = GeofencingEvent.fromIntent(intent);
        if(geofencingEvent.hasError()) {
            Log.e("Geofence", GeofenceStatusCodes.getStatusCodeString(geofencingEvent.getErrorCode()));
            return;
        }

        // If the user has arrived at their destination (is within the Geofence)
        // 1. Create a notification and display it
        // 2. Go back to the main activity (via Intent) to handle cleanup (Geofence removal, etc.)
        int geofenceTransition = geofencingEvent.getGeofenceTransition();
        if (geofenceTransition == Geofence.GEOFENCE_TRANSITION_DWELL || geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER) {
            Log.d("Geofence", "Geofence Triggered (ENTER or DWELL)");
            mNotificationChannel = new NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH);
            mNotificationManager = (NotificationManager) getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
            mNotificationManager.createNotificationChannel(mNotificationChannel);

            Intent goToMap = new Intent(getApplicationContext(), MapsActivity.class);
            goToMap.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

            TaskStackBuilder stackBuilder = TaskStackBuilder.create(getApplicationContext());
            stackBuilder.addParentStack(MapsActivity.class);
            stackBuilder.addNextIntent(goToMap);

            PendingIntent pendingIntent = PendingIntent.getActivity(getApplicationContext(), PENDING_INTENT_REQUEST_CODE, goToMap, PendingIntent.FLAG_IMMUTABLE);
            NotificationCompat.Builder notification = new NotificationCompat.Builder(getApplicationContext(), CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notif_icon)
                    .setContentTitle("Arrived at Destination")
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setOnlyAlertOnce(true)
                    .setContentIntent(pendingIntent);
            mNotificationManagerCompat = NotificationManagerCompat.from(getApplicationContext());
            mNotificationManagerCompat.notify(NOTIFICATION_ID, notification.build());

            Intent removeGeofenceIntent = new Intent(getApplicationContext(), MapsActivity.class);
            removeGeofenceIntent.putExtra("GeofenceTriggered", true);
            removeGeofenceIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(removeGeofenceIntent);
        } else {
            Log.e("Geofence", "Invalid Geofence transition type.");
        }
    }
}
