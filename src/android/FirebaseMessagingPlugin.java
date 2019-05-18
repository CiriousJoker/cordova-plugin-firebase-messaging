package by.chemerisuk.cordova.firebase;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.content.SharedPreferences;
import android.support.v4.app.NotificationManagerCompat;

import by.chemerisuk.cordova.support.CordovaMethod;
import by.chemerisuk.cordova.support.ReflectiveCordovaPlugin;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.RemoteMessage;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.InstanceIdResult;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.PluginResult;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.FutureTask;
import io.paperdb.Book;
import io.paperdb.Paper;
import me.leolin.shortcutbadger.ShortcutBadger;

public class FirebaseMessagingPlugin extends ReflectiveCordovaPlugin {
    private static final String TAG = "FirebaseMessagingPlugin";
    private JSONObject lastBundle;
    private boolean isBackground = false;
    private boolean forceShow = false;
    private CallbackContext tokenRefreshCallback;
    private CallbackContext foregroundCallback;
    private CallbackContext backgroundCallback;
    private CallbackContext callbackNotificationAction;

    private static FirebaseMessagingPlugin instance;

    @Override
    protected void pluginInitialize() {
        FirebaseMessagingPlugin.instance = this;

        lastBundle = getNotificationData(cordova.getActivity().getIntent());

        Context context = cordova.getActivity().getApplicationContext();
        // cleanup badge value initially
        ShortcutBadger.applyCount(context, 0);
    }

    @CordovaMethod
    private void subscribe(String topic, final CallbackContext callbackContext) {
        FirebaseMessaging.getInstance().subscribeToTopic(topic).addOnCompleteListener(cordova.getActivity(),
                new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(Task<Void> task) {
                        if (task.isSuccessful()) {
                            callbackContext.success();
                        } else {
                            callbackContext.error(task.getException().getMessage());
                        }
                    }
                });
    }

    @CordovaMethod
    private void unsubscribe(String topic, final CallbackContext callbackContext) {
        FirebaseMessaging.getInstance().unsubscribeFromTopic(topic).addOnCompleteListener(cordova.getActivity(),
                new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(Task<Void> task) {
                        if (task.isSuccessful()) {
                            callbackContext.success();
                        } else {
                            callbackContext.error(task.getException().getMessage());
                        }
                    }
                });
    }

    @CordovaMethod
    private void revokeToken(CallbackContext callbackContext) throws IOException {
        FirebaseInstanceId.getInstance().deleteInstanceId();

        callbackContext.success();
    }

    @CordovaMethod
    private void getToken(String type, final CallbackContext callbackContext) {
        if (type != null) {
            callbackContext.sendPluginResult(
                new PluginResult(PluginResult.Status.OK, (String)null));
        } else {
            FirebaseInstanceId.getInstance().getInstanceId()
                .addOnCompleteListener(cordova.getActivity(), new OnCompleteListener<InstanceIdResult>() {
                    @Override
                    public void onComplete(Task<InstanceIdResult> task) {
                        if (task.isSuccessful()) {
                            callbackContext.success(task.getResult().getToken());
                        } else {
                            callbackContext.error(task.getException().getMessage());
                        }
                    }
                });
        }
    }

    @CordovaMethod
    private void onTokenRefresh(CallbackContext callbackContext) {
        instance.tokenRefreshCallback = callbackContext;
    }

    @CordovaMethod
    private void onNotificationAction(CallbackContext callbackContext) {
        instance.callbackNotificationAction = callbackContext;
        handleQueuedNotificationActions();
    }

    @CordovaMethod
    private void onMessage(CallbackContext callbackContext) {
        instance.foregroundCallback = callbackContext;
        handleQueuedNotifications();
    }

    @CordovaMethod
    private void onBackgroundMessage(CallbackContext callbackContext) {
        instance.backgroundCallback = callbackContext;

        if (lastBundle != null) {
            sendNotification(lastBundle, callbackContext);
            lastBundle = null;
        }
    }

    @CordovaMethod
    private void setBadge(int value, CallbackContext callbackContext) {
        if (value >= 0) {
            Context context = cordova.getActivity().getApplicationContext();
            ShortcutBadger.applyCount(context, value);

            callbackContext.success();
        } else {
            callbackContext.error("Badge value can't be negative");
        }
    }

    @CordovaMethod
    private void getBadge(CallbackContext callbackContext) {
        Context context = cordova.getActivity();
        SharedPreferences settings = context.getSharedPreferences("badge", Context.MODE_PRIVATE);
        callbackContext.success(settings.getInt("badge", 0));
    }

    @CordovaMethod
    private void requestPermission(JSONObject options, CallbackContext callbackContext) throws JSONException {
        Context context = cordova.getActivity().getApplicationContext();

        this.forceShow = options.optBoolean("forceShow");

        if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            callbackContext.success();
        } else {
            callbackContext.error("Notifications permission is not granted");
        }
    }

    @Override
    public void onNewIntent(Intent intent) {
        JSONObject notificationData = getNotificationData(intent);
        if (instance != null && notificationData != null) {
            sendNotification(notificationData, instance.backgroundCallback);
        }
    }

    @Override
    public void onPause(boolean multitasking) {
        this.isBackground = true;
    }

    @Override
    public void onResume(boolean multitasking) {
        this.isBackground = false;
        this.clearNotifications();
        handleQueuedNotificationActions();
    }


    private void handleQueuedNotificationActions() {
        Paper.init(cordova.getActivity().getApplicationContext());
        Book queue = Paper.book(FirebaseMessagingPluginService.BOOK_NOTIFICATION_ACTION_QUEUE);
        List<String> allKeys = queue.getAllKeys();
        for (String key : allKeys) {
            JSONObject notificationAction = queue.read(key);

            if (callbackNotificationAction != null) {
                PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, notificationAction);
                pluginResult.setKeepCallback(true);
                callbackNotificationAction.sendPluginResult(pluginResult);
                queue.delete(key);
            }
        }
    }

    private void handleQueuedNotifications() {
        Paper.init(cordova.getActivity().getApplicationContext());
        Book queue = Paper.book(FirebaseMessagingPluginService.BOOK_NOTIFICATION_QUEUE);
        List<String> allKeys = queue.getAllKeys();
        for (String key : allKeys) {
            JSONObject notificationData = queue.read(key);

            if (instance != null) {
                CallbackContext callbackContext = instance.isBackground ? instance.backgroundCallback
                        : instance.foregroundCallback;
                instance.sendNotification(notificationData, callbackContext);
                queue.delete(key);
            }
        }
    }

    static void openUrl(JSONObject action) {
        Paper.book(FirebaseMessagingPluginService.BOOK_NOTIFICATION_ACTION_QUEUE).write(Long.toString(System.currentTimeMillis()), action);
        if (instance != null) {
            instance.handleQueuedNotificationActions();
        }
    }

    static boolean sendNotification(RemoteMessage remoteMessage) {
        JSONObject notificationData = convertToNotificationData(remoteMessage);
        if (notificationData == null) {
            return false;
        }

        if (instance != null) {
            CallbackContext callbackContext = instance.isBackground ? instance.backgroundCallback
                    : instance.foregroundCallback;
            instance.sendNotification(notificationData, callbackContext);
            return true;
        } else {
            Log.e(TAG, "sendNotification() | App is currently killed. Notification was queued instead.");
        }
        return false;
    }

    @CordovaMethod
    private void clearNotifications() {
        NotificationManager notificationManager = (NotificationManager) instance.cordova.getContext()
                .getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancelAll();
    }

    static boolean currentUrlContains(String part) {
        if (instance != null && !instance.isBackground) {
            FutureTask<String> futureResult = new FutureTask<>(() -> instance.webView.getUrl());

            instance.cordova.getActivity().runOnUiThread(futureResult);
            try {
                return futureResult.get().contains(part);
            } catch (Exception e) {
                return false;
            }
        }
        return false;
    }

    static JSONObject convertToNotificationData(RemoteMessage remoteMessage) {
        JSONObject notificationData = new JSONObject(remoteMessage.getData());
        RemoteMessage.Notification notification = remoteMessage.getNotification();

        try {
            if (notification != null) {
                JSONObject jsonNotification = new JSONObject();
                jsonNotification.put("body", notification.getBody());
                jsonNotification.put("title", notification.getTitle());
                jsonNotification.put("sound", notification.getSound());
                jsonNotification.put("icon", notification.getIcon());
                jsonNotification.put("tag", notification.getTag());
                jsonNotification.put("color", notification.getColor());
                jsonNotification.put("clickAction", notification.getClickAction());

                notificationData.put("gcm", jsonNotification);
            }
            notificationData.put("google.message_id", remoteMessage.getMessageId());
            notificationData.put("google.sent_time", remoteMessage.getSentTime());
            return notificationData;
        } catch (JSONException e) {
            Log.e(TAG, "convertToNotificationData", e);
            return null;
        }
    }

    static void sendInstanceId(String instanceId) {
        if (instance != null) {
            if (instance.tokenRefreshCallback != null && instanceId != null) {
                PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, instanceId);
                pluginResult.setKeepCallback(true);
                instance.tokenRefreshCallback.sendPluginResult(pluginResult);
            }
        }
    }

    static boolean isForceShow() {
        return instance != null && instance.forceShow;
    }

    private void sendNotification(JSONObject notificationData, CallbackContext callbackContext) {
        if (callbackContext != null) {
            PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, notificationData);
            pluginResult.setKeepCallback(true);
            callbackContext.sendPluginResult(pluginResult);
        }
    }

    private JSONObject getNotificationData(Intent intent) {
        Bundle bundle = intent.getExtras();

        if (bundle == null) {
            return null;
        }

        if (!bundle.containsKey("google.message_id") && !bundle.containsKey("google.sent_time")) {
            return null;
        }

        try {
            JSONObject notificationData = new JSONObject();
            Set<String> keys = bundle.keySet();
            for (String key : keys) {
                notificationData.put(key, bundle.get(key));
            }
            return notificationData;
        } catch (JSONException e) {
            Log.e(TAG, "getNotificationData", e);
            return null;
        }
    }
}
