package by.chemerisuk.cordova.firebase;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.os.Build;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.text.format.DateUtils;
import android.util.Log;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;

import de.didactylus.didactduell.R;
import io.paperdb.Paper;

public class FirebaseMessagingPluginService extends FirebaseMessagingService {
    private static final String TAG = "FirebaseMessagingPlugin";

    public static final String ACTION_FCM_MESSAGE = "by.chemerisuk.cordova.firebase.ACTION_FCM_MESSAGE";
    public static final String EXTRA_FCM_MESSAGE = "by.chemerisuk.cordova.firebase.EXTRA_FCM_MESSAGE";
    public static final String ACTION_FCM_TOKEN = "by.chemerisuk.cordova.firebase.ACTION_FCM_TOKEN";
    public static final String EXTRA_FCM_TOKEN = "by.chemerisuk.cordova.firebase.EXTRA_FCM_TOKEN";

    private LocalBroadcastManager broadcastManager;
    private int notificationCounter = 0;
    private HashMap<String, Integer> mapNotificationIds = new HashMap<>();
    // private HashMap<String, NotificationCompat.MessagingStyle>
    // mapNotificationStyle = new HashMap<>();
    private HashMap<String, NotificationCompat.Builder> mapNotificationBuilder = new HashMap<>();

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
        JSONObject notificationData = FirebaseMessagingPlugin.convertToNotificationData(remoteMessage);
        if (notificationData != null) {
            if (!FirebaseMessagingPlugin.sendNotification(remoteMessage)) {
                // App was killed, store the notification for delayed delivery
                Paper.book(getString(R.string.FCM_QUEUE_NAME)).write(Long.toString(new Date().getTime()),
                        notificationData);
            }
            showNotification(notificationData);
        }

        Intent intent = new Intent(ACTION_FCM_MESSAGE);
        intent.putExtra(EXTRA_FCM_MESSAGE, remoteMessage);
        this.broadcastManager.sendBroadcast(intent);
    }

    private void showNotification(JSONObject data) {
        String channelId = createNotificationChannel();
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);

        try {
            String title = data.getString("_title");
            String body = data.getString("_body");
            String conversationId = data.getString("conversation");

            NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channelId)
                    .setSmallIcon(R.drawable.fcm_push_icon)
                    // .setContentTitle(title)
                    // .setContentText(body)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT);
            if (mapNotificationBuilder.containsKey(conversationId)) {
                builder = mapNotificationBuilder.get(conversationId);
            } else {
                mapNotificationBuilder.put(conversationId, builder);

            }
            assert builder != null;

            if (!FirebaseMessagingPlugin.currentUrlContains(conversationId)) {
                // Get notification id
                int notificationId = notificationCounter;
                if (mapNotificationIds.containsKey(conversationId)) {
                    Integer value = mapNotificationIds.get(conversationId);
                    assert value != null;
                    notificationId = value;
                } else {
                    mapNotificationIds.put(conversationId, notificationId);
                    notificationCounter++;
                }

                // NotificationCompat.MessagingStyle style = new
                // NotificationCompat.MessagingStyle("Some title");
                // if (mapNotificationStyle.containsKey(conversationId)) {
                // style = mapNotificationStyle.get(conversationId);
                // } else {
                // mapNotificationStyle.put(conversationId, style);
                // }
                // assert style != null;

                // Add messsages & set style
                // style.addMessage(body, System.currentTimeMillis(), title);
                // builder.setStyle(style);
                builder.setContentTitle(title);
                builder.setContentText(body);

                notificationManager.notify(notificationId, builder.build());
                // mapNotificationStyle.put(conversationId, style);
            } else {
                // TODO:
                // Remove map entries
                // mapNotificationStyle.remove(conversationId);
                mapNotificationIds.remove(conversationId);
                mapNotificationBuilder.remove(conversationId);
            }

            // TODO: Add update functionality for new messages in the same conversation
        } catch (JSONException e) {
            Log.e(TAG, "showNotification", e);
        }

    }

    private String createNotificationChannel() {
        String channelId = "channelid";

        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Chat Nachrichten"; // getString(R.string.channel_name);
            String description = "Benachrichtigungen von Freunden"; // getString(R.string.channel_description);
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
