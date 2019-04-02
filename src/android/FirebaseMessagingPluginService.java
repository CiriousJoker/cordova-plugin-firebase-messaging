package by.chemerisuk.cordova.firebase;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import org.json.JSONException;
import org.json.JSONObject;
import java.util.Date;
import java.util.HashMap;

import de.didactylus.didactduell.MainActivity;
import de.didactylus.didactduell.R;
import io.paperdb.Paper;

public class FirebaseMessagingPluginService extends FirebaseMessagingService {
    private static final String TAG = "FirebaseMessagingPlugin";

    public static final String ACTION_FCM_MESSAGE = "by.chemerisuk.cordova.firebase.ACTION_FCM_MESSAGE";
    public static final String EXTRA_FCM_MESSAGE = "by.chemerisuk.cordova.firebase.EXTRA_FCM_MESSAGE";
    public static final String ACTION_FCM_TOKEN = "by.chemerisuk.cordova.firebase.ACTION_FCM_TOKEN";
    public static final String EXTRA_FCM_TOKEN = "by.chemerisuk.cordova.firebase.EXTRA_FCM_TOKEN";

    public static final String ACTION_NOTIFICATION_TAP = "by.chemerisuk.cordova.firebase.ACTION_NOTIFICATION_TAP";
    public static final String BOOK_NOTIFICATION_QUEUE = "by.chemerisuk.cordova.firebase.BOOK_NOTIFICATION_QUEUE";
    public static final String BOOK_NOTIFICATION_ACTION_QUEUE = "by.chemerisuk.cordova.firebase.BOOK_NOTIFICATION_ACTION_QUEUE";
    public static final String CHANNEL_CHAT_MESSAGES = "by.chemerisuk.cordova.firebase.CHANNEL_CHAT_MESSAGES";

    private LocalBroadcastManager broadcastManager;

    BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "onReceive()");
            try {
                JSONObject action = new JSONObject();
                action.put("action", intent.getStringExtra("action"));
                action.put("notification", new JSONObject(intent.getStringExtra("notification")));
                FirebaseMessagingPlugin.openUrl(action);

                Intent intentStartMainActivity = new Intent(context, MainActivity.class);
                intentStartMainActivity.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intentStartMainActivity);

            } catch (JSONException e) {
                Log.e(TAG, "onReceive(): Notification action intent is invalid.", e);
            }
        }
    };

    private int notificationCounter = 0;
    private HashMap<String, Integer> mapNotificationIds = new HashMap<>();

    private HashMap<String, NotificationCompat.Builder> mapNotificationBuilder = new HashMap<>();

    @Override
    public void onCreate() {
        this.broadcastManager = LocalBroadcastManager.getInstance(this);

        IntentFilter filter = new IntentFilter(ACTION_NOTIFICATION_TAP);

        registerReceiver(mReceiver, filter);
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
                Paper.book(BOOK_NOTIFICATION_QUEUE).write(Long.toString(new Date().getTime()),
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
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setAutoCancel(true);


            if (mapNotificationBuilder.containsKey(conversationId)) {
                builder = mapNotificationBuilder.get(conversationId);
            } else {
                mapNotificationBuilder.put(conversationId, builder);
            }
            assert builder != null;

            // Set intents
            Intent intent = new Intent(ACTION_NOTIFICATION_TAP);
            intent.putExtra("action", "tap");
            intent.putExtra("notification", data.toString());
            PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT);
            builder.setContentIntent(pendingIntent);

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

                builder.setContentTitle(title);
                builder.setContentText(body);

                notificationManager.notify(notificationId, builder.build());
            } else {
                // Remove map entries
                mapNotificationIds.remove(conversationId);
                mapNotificationBuilder.remove(conversationId);
            }
        } catch (JSONException e) {
            Log.e(TAG, "showNotification", e);
        }

    }

    private String createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Chat Nachrichten"; // getString(R.string.channel_name);
            String description = "Benachrichtigungen von Freunden"; // getString(R.string.channel_description);
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(CHANNEL_CHAT_MESSAGES, name, importance);
            channel.setDescription(description);

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }

        return CHANNEL_CHAT_MESSAGES;
    }
}
