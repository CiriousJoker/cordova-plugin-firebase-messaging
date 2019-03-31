package by.chemerisuk.cordova.firebase;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.os.Build;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.content.LocalBroadcastManager;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import org.json.JSONObject;

import java.util.Date;

import de.didactylus.didactduell.R;
import io.paperdb.Paper;


public class FirebaseMessagingPluginService extends FirebaseMessagingService {
    private static final String TAG = "FirebaseMessagingPlugin";

    public static final String ACTION_FCM_MESSAGE = "by.chemerisuk.cordova.firebase.ACTION_FCM_MESSAGE";
    public static final String EXTRA_FCM_MESSAGE = "by.chemerisuk.cordova.firebase.EXTRA_FCM_MESSAGE";
    public static final String ACTION_FCM_TOKEN = "by.chemerisuk.cordova.firebase.ACTION_FCM_TOKEN";
    public static final String EXTRA_FCM_TOKEN = "by.chemerisuk.cordova.firebase.EXTRA_FCM_TOKEN";

    private LocalBroadcastManager broadcastManager;

    @Override
    public void onCreate() {
        this.broadcastManager = LocalBroadcastManager.getInstance(this);
        Paper.init(this);
    }

    @Override
    public void onNewToken(String token) {
        FirebaseMessagingPlugin.sendInstanceId(token);

        Intent intent = new Intent(ACTION_FCM_TOKEN);
        intent.putExtra(EXTRA_FCM_TOKEN, token);
        this.broadcastManager.sendBroadcast(intent);
    }

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        JSONObject notificationData = FirebaseMessagingPlugin.sendNotification(remoteMessage);
        if(notificationData != null) {
            // App was killed, store the notification for delayed delivery
            // TODO: Add book title "fcm_message_queue" as a string resource
            Paper.book().write(Long.toString(new Date().getTime()), notificationData);
        }
        showNotification();
        Intent intent = new Intent(ACTION_FCM_MESSAGE);
        intent.putExtra(EXTRA_FCM_MESSAGE, remoteMessage);
        this.broadcastManager.sendBroadcast(intent);
    }

    private void showNotification() {
        String channelId = createNotificationChannel();
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.drawable.fcm_push_icon)
                .setContentTitle("some title")
                .setContentText("some body")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        notificationManager.notify(19999, builder.build());
    }

    private String createNotificationChannel() {
        String channelId = "channelid";

        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Chat Nachrichten"; // getString(R.string.channel_name);
            String description = "Benachrichtigungen von Freunden"; //getString(R.string.channel_description);
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(channelId, name, importance);
            channel.setDescription(description);
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }

        return channelId;
    }
}
