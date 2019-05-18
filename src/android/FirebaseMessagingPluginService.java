package by.chemerisuk.cordova.firebase;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.content.ContextCompat;
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
import static android.content.ContentResolver.SCHEME_ANDROID_RESOURCE;


public class FirebaseMessagingPluginService extends FirebaseMessagingService {
    private static final String TAG = "FirebaseMessagingPluginService";

    public static final String ACTION_FCM_MESSAGE = "by.chemerisuk.cordova.firebase.ACTION_FCM_MESSAGE";
    public static final String EXTRA_FCM_MESSAGE = "by.chemerisuk.cordova.firebase.EXTRA_FCM_MESSAGE";
    public static final String ACTION_FCM_TOKEN = "by.chemerisuk.cordova.firebase.ACTION_FCM_TOKEN";
    public static final String EXTRA_FCM_TOKEN = "by.chemerisuk.cordova.firebase.EXTRA_FCM_TOKEN";
    public final static String NOTIFICATION_ICON_KEY = "com.google.firebase.messaging.default_notification_icon";
    public final static String NOTIFICATION_COLOR_KEY = "com.google.firebase.messaging.default_notification_color";

    public static final String ACTION_NOTIFICATION_TAP = "by.chemerisuk.cordova.firebase.ACTION_NOTIFICATION_TAP";
    public static final String BOOK_NOTIFICATION_QUEUE = "by.chemerisuk.cordova.firebase.BOOK_NOTIFICATION_QUEUE";
    public static final String BOOK_NOTIFICATION_ACTION_QUEUE = "by.chemerisuk.cordova.firebase.BOOK_NOTIFICATION_ACTION_QUEUE";
    public static final String CHANNEL_CHAT_MESSAGES = "by.chemerisuk.cordova.firebase.CHANNEL_CHAT_MESSAGES";

    private LocalBroadcastManager broadcastManager;
    private NotificationManager notificationManager;
    private int defaultNotificationIcon;
    private int defaultNotificationColor;

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
        this.notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        try {
            ApplicationInfo ai = getPackageManager().getApplicationInfo(getApplicationContext().getPackageName(), PackageManager.GET_META_DATA);
            this.defaultNotificationIcon = ai.metaData.getInt(NOTIFICATION_ICON_KEY, ai.icon);
            this.defaultNotificationColor = ContextCompat.getColor(this, ai.metaData.getInt(NOTIFICATION_COLOR_KEY));
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Failed to load meta-data", e);
        } catch(Resources.NotFoundException e) {
            Log.e(TAG, "Failed to load notification color", e);
        }
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

        if (FirebaseMessagingPlugin.isForceShow()) {
            RemoteMessage.Notification notification = remoteMessage.getNotification();
            if (notification != null) {
                showAlert(notification);
            }
        }
    }

    private void showAlert(RemoteMessage.Notification notification) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, notification.getChannelId());
        builder.setContentTitle(notification.getTitle());
        builder.setContentText(notification.getBody());
        builder.setGroup(notification.getTag());
        builder.setSmallIcon(this.defaultNotificationIcon);
        builder.setColor(this.defaultNotificationColor);
        // must set sound and priority in order to display alert
        builder.setSound(getNotificationSound(notification.getSound()));
        builder.setPriority(1);

        this.notificationManager.notify(0, builder.build());
        // dismiss notification to hide icon from status bar automatically
        new Handler(getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                notificationManager.cancel(0);
            }
        }, 3000);
    }

    private Uri getNotificationSound(String soundName) {
        if (soundName != null && !soundName.equals("default") && !soundName.equals("enabled")) {
            return Uri.parse(SCHEME_ANDROID_RESOURCE + "://" + getApplicationContext().getPackageName() + "/raw/" + soundName);
        } else {
            return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        }
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
