/* Copyright 2016 Urban Airship and Contributors */

package com.urbanairship.push;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationManagerCompat;

import com.urbanairship.BaseIntentService;
import com.urbanairship.CoreReceiver;
import com.urbanairship.Logger;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.UAirship;
import com.urbanairship.actions.Action;
import com.urbanairship.actions.ActionArguments;
import com.urbanairship.actions.ActionService;
import com.urbanairship.analytics.PushArrivedEvent;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonList;
import com.urbanairship.json.JsonValue;
import com.urbanairship.push.iam.InAppMessage;
import com.urbanairship.push.notifications.NotificationFactory;
import com.urbanairship.richpush.RichPushInbox;
import com.urbanairship.util.UAStringUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Service delegate for the {@link PushService} to handle incoming push messages.
 */
class IncomingPushServiceDelegate extends BaseIntentService.Delegate {

    /**
     * Key to store the push canonical IDs for push deduping.
     */
    private static final String LAST_CANONICAL_IDS_KEY = "com.urbanairship.push.LAST_CANONICAL_IDS";

    /**
     * Max amount of canonical IDs to store.
     */
    private static final int MAX_CANONICAL_IDS = 10;

    /**
     * Amount of time in milliseconds to wait for {@link com.urbanairship.richpush.RichPushInbox} to refresh.
     */
    private static final int RICH_PUSH_REFRESH_WAIT_TIME_MS = 60000; // 1 minute

    private final NotificationManagerCompat notificationManager;
    private final PushManager pushManager;
    private final UAirship airship;

    public IncomingPushServiceDelegate(Context context, PreferenceDataStore dataStore) {
        this(context, dataStore, UAirship.shared(), NotificationManagerCompat.from(context));
    }

    public IncomingPushServiceDelegate(Context context, PreferenceDataStore dataStore,
                                       UAirship airship, NotificationManagerCompat notificationManager) {
        super(context, dataStore);

        this.airship = airship;
        this.pushManager = airship.getPushManager();
        this.notificationManager = notificationManager;
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        switch (intent.getAction()) {
            case PushService.ACTION_RECEIVE_ADM_MESSAGE:
                onAdmMessageReceived(intent);
                break;
            case PushService.ACTION_RECEIVE_GCM_MESSAGE:
                onGcmMessageReceived(intent);
                break;
        }
    }

    /**
     * Handles incoming GCM messages.
     *
     * @param intent The received intent.
     */
    private void onGcmMessageReceived(@NonNull Intent intent) {
        if (airship.getPlatformType() != UAirship.ANDROID_PLATFORM) {
            Logger.error("Received intent from invalid transport acting as GCM.");
            return;
        }

        if (!airship.getPushManager().isPushAvailable()) {
            Logger.error("IncomingPushServiceDelegate - Received intent from GCM without registering.");
            return;
        }

        Intent gcmIntent = intent.getParcelableExtra(PushService.EXTRA_INTENT);
        if (gcmIntent == null) {
            Logger.error("IncomingPushServiceDelegate - Received GCM message missing original intent.");
            return;
        }

        String sender = gcmIntent.getStringExtra("from");
        if (sender != null && !sender.equals(airship.getAirshipConfigOptions().gcmSender)) {
            Logger.info("Ignoring GCM message from sender: " + sender);
            return;
        }

        if (GcmConstants.GCM_DELETED_MESSAGES_VALUE.equals(gcmIntent.getStringExtra(GcmConstants.EXTRA_GCM_MESSAGE_TYPE))) {
            Logger.info("GCM deleted " + gcmIntent.getStringExtra(GcmConstants.EXTRA_GCM_TOTAL_DELETED) + " pending messages.");
            return;
        }

        processMessage(new PushMessage(gcmIntent.getExtras()));
    }

    /**
     * Handles incoming ADM messages.
     *
     * @param intent The received intent.
     */
    private void onAdmMessageReceived(@NonNull Intent intent) {
        if (airship.getPlatformType() != UAirship.AMAZON_PLATFORM) {
            Logger.error("Received intent from invalid transport acting as ADM.");
            return;
        }

        if (!airship.getPushManager().isPushAvailable()) {
            Logger.error("IncomingPushServiceDelegate - Received intent from ADM without registering.");
            return;
        }

        Intent admIntent = intent.getParcelableExtra(PushService.EXTRA_INTENT);
        if (admIntent == null) {
            Logger.error("IncomingPushServiceDelegate - Received ADM message missing original intent.");
            return;
        }

        processMessage(new PushMessage(admIntent.getExtras()));
    }

    /**
     * Processes the received message.
     *
     * @param message The push message.
     */
    private void processMessage(@NonNull PushMessage message) {
        if (!pushManager.isPushEnabled()) {
            Logger.info("Received a push when push is disabled! Ignoring.");
            return;
        }

        if (!isUniqueCanonicalId(message.getCanonicalPushId())) {
            Logger.info("Received a duplicate push with canonical ID: " + message.getCanonicalPushId());
            return;
        }

        pushManager.setLastReceivedMetadata(message.getMetadata());
        airship.getAnalytics().addEvent(new PushArrivedEvent(message));

        if (message.isExpired()) {
            Logger.debug("Received expired push message, ignoring.");
            return;
        }

        if (message.isPing()) {
            Logger.verbose("IncomingPushServiceDelegate - Received UA Ping");
            return;
        }

        // Run any actions for the push
        Bundle metadata = new Bundle();
        metadata.putParcelable(ActionArguments.PUSH_MESSAGE_METADATA, message);
        ActionService.runActions(UAirship.getApplicationContext(), message.getActions(), Action.SITUATION_PUSH_RECEIVED, metadata);

        // Store any pending in-app messages
        InAppMessage inAppMessage = message.getInAppMessage();
        if (inAppMessage != null) {
            Logger.debug("IncomingPushServiceDelegate - Received a Push with an in-app message.");
            airship.getInAppMessageManager().setPendingMessage(inAppMessage);
        }

        if (!UAStringUtil.isEmpty(message.getRichPushMessageId())) {
            Logger.debug("IncomingPushServiceDelegate - Received a Rich Push.");
            refreshRichPushMessages();
        }

        Integer notificationId = null;
        if (!pushManager.getUserNotificationsEnabled()) {
            Logger.info("User notifications disabled. Unable to display notification for message: " + message);
        } else {
            notificationId = showNotification(message, pushManager.getNotificationFactory());
        }

        sendPushReceivedBroadcast(message, notificationId);
    }

    /**
     * Builds and displays the notification.
     *
     * @param message The push message.
     * @param factory The notification factory.
     */
    private Integer showNotification(@NonNull PushMessage message, @Nullable NotificationFactory factory) {
        int notificationId;
        Notification notification;

        if (factory == null) {
            Logger.info("NotificationFactory is null. Unable to display notification for message: " + message);
            return null;
        }

        try {
            notificationId = factory.getNextId(message);
            notification = factory.createNotification(message, notificationId);
        } catch (Exception e) {
            Logger.error("Unable to create and display notification.", e);
            return null;
        }

        if (notification == null) {
            return null;
        }

        if (!pushManager.isVibrateEnabled() || pushManager.isInQuietTime()) {
            // Remove both the vibrate and the DEFAULT_VIBRATE flag
            notification.vibrate = null;
            notification.defaults &= ~Notification.DEFAULT_VIBRATE;
        }

        if (!pushManager.isSoundEnabled() || pushManager.isInQuietTime()) {
            // Remove both the sound and the DEFAULT_SOUND flag
            notification.sound = null;
            notification.defaults &= ~Notification.DEFAULT_SOUND;
        }

        Intent contentIntent = new Intent(getContext(), CoreReceiver.class)
                .setAction(PushManager.ACTION_NOTIFICATION_OPENED_PROXY)
                .addCategory(UUID.randomUUID().toString())
                .putExtra(PushManager.EXTRA_PUSH_MESSAGE_BUNDLE, message.getPushBundle())
                .putExtra(PushManager.EXTRA_NOTIFICATION_ID, notificationId);

        // If the notification already has an intent, add it to the extras to be sent later
        if (notification.contentIntent != null) {
            contentIntent.putExtra(PushManager.EXTRA_NOTIFICATION_CONTENT_INTENT, notification.contentIntent);
        }

        Intent deleteIntent = new Intent(getContext(), CoreReceiver.class)
                .setAction(PushManager.ACTION_NOTIFICATION_DISMISSED_PROXY)
                .addCategory(UUID.randomUUID().toString())
                .putExtra(PushManager.EXTRA_PUSH_MESSAGE_BUNDLE, message.getPushBundle())
                .putExtra(PushManager.EXTRA_NOTIFICATION_ID, notificationId);

        if (notification.deleteIntent != null) {
            deleteIntent.putExtra(PushManager.EXTRA_NOTIFICATION_DELETE_INTENT, notification.deleteIntent);
        }

        notification.contentIntent = PendingIntent.getBroadcast(getContext(), 0, contentIntent, 0);
        notification.deleteIntent = PendingIntent.getBroadcast(getContext(), 0, deleteIntent, 0);

        Logger.info("Posting notification " + notification + " with ID " + notificationId);
        notificationManager.notify(notificationId, notification);

        return notificationId;
    }

    /**
     * Broadcasts an intent to notify the host application of a push message received, but
     * only if a receiver is set to get the user-defined intent receiver.
     *
     * @param message The message that created the notification
     * @param notificationId The ID of the messages created notification
     */
    private void sendPushReceivedBroadcast(@NonNull PushMessage message, @Nullable Integer notificationId) {
        Intent intent = new Intent(PushManager.ACTION_PUSH_RECEIVED)
                .putExtra(PushManager.EXTRA_PUSH_MESSAGE_BUNDLE, message.getPushBundle())
                .addCategory(UAirship.getPackageName())
                .setPackage(UAirship.getPackageName());

        if (notificationId != null) {
            intent.putExtra(PushManager.EXTRA_NOTIFICATION_ID, notificationId.intValue());
        }

        getContext().sendBroadcast(intent, UAirship.getUrbanAirshipPermission());
    }

    /**
     * Helper method that blocks while the rich push messages are refreshing
     */
    private void refreshRichPushMessages() {
        final Semaphore semaphore = new Semaphore(0);
        airship.getInbox().fetchMessages(new RichPushInbox.FetchMessagesCallback() {
            @Override
            public void onFinished(boolean success) {
                semaphore.release();
            }
        }, Looper.getMainLooper());

        try {
            semaphore.tryAcquire(RICH_PUSH_REFRESH_WAIT_TIME_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Logger.warn("Interrupted while waiting for rich push messages to refresh");
        }
    }

    /**
     * Check to see if we've seen this ID before. If we have,
     * return false. If not, add the ID to our history and return true.
     *
     * @param canonicalId The canonical push ID for an incoming notification.
     * @return <code>false</code> if the ID exists in the history, otherwise <code>true</code>.
     */
    private boolean isUniqueCanonicalId(@Nullable String canonicalId) {
        if (UAStringUtil.isEmpty(canonicalId)) {
            return true;
        }

        JsonList jsonList = null;
        try {
            jsonList = JsonValue.parseString(getDataStore().getString(LAST_CANONICAL_IDS_KEY, null)).getList();
        } catch (JsonException e) {
            Logger.debug("IncomingPushServiceDelegate - Unable to parse canonical Ids.", e);
        }

        List<JsonValue> canonicalIds = jsonList == null ? new ArrayList<JsonValue>() : jsonList.getList();

        // Wrap the canonicalId
        JsonValue id = JsonValue.wrap(canonicalId);

        // Check if the list contains the canonicalId
        if (canonicalIds.contains(id)) {
            return false;
        }

        // Add it
        canonicalIds.add(id);
        if (canonicalIds.size() > MAX_CANONICAL_IDS) {
            canonicalIds = canonicalIds.subList(canonicalIds.size() - MAX_CANONICAL_IDS, canonicalIds.size());
        }

        // Store the new list
        getDataStore().put(LAST_CANONICAL_IDS_KEY, JsonValue.wrapOpt(canonicalIds).toString());

        return true;
    }

}
