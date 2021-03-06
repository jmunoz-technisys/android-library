/* Copyright 2016 Urban Airship and Contributors */

package com.urbanairship.push;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.urbanairship.AirshipComponent;
import com.urbanairship.AirshipConfigOptions;
import com.urbanairship.Logger;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.R;
import com.urbanairship.UAirship;
import com.urbanairship.json.JsonValue;
import com.urbanairship.push.notifications.DefaultNotificationFactory;
import com.urbanairship.push.notifications.NotificationActionButtonGroup;
import com.urbanairship.push.notifications.NotificationFactory;
import com.urbanairship.util.UAStringUtil;

import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * This class is the primary interface for customizing the display and behavior
 * of incoming push notifications.
 */
public class PushManager extends AirshipComponent {

    /**
     * Action sent as a broadcast when a push message is received.
     * <p/>
     * Extras:
     * {@link #EXTRA_NOTIFICATION_ID},
     * {@link #EXTRA_PUSH_MESSAGE_BUNDLE}
     *
     * @hide
     */
    public static final String ACTION_PUSH_RECEIVED = "com.urbanairship.push.RECEIVED";

    /**
     * Action sent as a broadcast when a notification is opened.
     * <p/>
     * Extras:
     * {@link #EXTRA_NOTIFICATION_ID},
     * {@link #EXTRA_PUSH_MESSAGE_BUNDLE},
     * {@link #EXTRA_NOTIFICATION_BUTTON_ID},
     * {@link #EXTRA_NOTIFICATION_BUTTON_FOREGROUND}
     *
     * @hide
     */
    public static final String ACTION_NOTIFICATION_OPENED = "com.urbanairship.push.OPENED";

    /**
     * Action sent as a broadcast when a notification is dismissed.
     * <p/>
     * Extras:
     * {@link #EXTRA_NOTIFICATION_ID},
     * {@link #EXTRA_PUSH_MESSAGE_BUNDLE}
     *
     * @hide
     */
    public static final String ACTION_NOTIFICATION_DISMISSED = "com.urbanairship.push.DISMISSED";

    /**
     * Action sent as a broadcast when a channel registration succeeds.
     * <p/>
     * Extras:
     * {@link #EXTRA_CHANNEL_ID}
     *
     * @hide
     */
    public static final String ACTION_CHANNEL_UPDATED = "com.urbanairship.push.CHANNEL_UPDATED";

    /**
     * The notification ID extra contains the ID of the notification placed in the
     * <code>NotificationManager</code> by the library.
     * <p/>
     * If a <code>Notification</code> was not created, the extra will not be included.
     */
    public static final String EXTRA_NOTIFICATION_ID = "com.urbanairship.push.NOTIFICATION_ID";

    /**
     * The push message extra bundle.
     *
     * @hide
     */
    public static final String EXTRA_PUSH_MESSAGE_BUNDLE = "com.urbanairship.push.EXTRA_PUSH_MESSAGE_BUNDLE";


    /**
     * The interactive notification action button identifier extra.
     *
     * @hide
     */
    public static final String EXTRA_NOTIFICATION_BUTTON_ID = "com.urbanairship.push.EXTRA_NOTIFICATION_BUTTON_ID";

    /**
     * The flag indicating if the interactive notification action button is background or foreground.
     *
     * @hide
     */
    public static final String EXTRA_NOTIFICATION_BUTTON_FOREGROUND = "com.urbanairship.push.EXTRA_NOTIFICATION_BUTTON_FOREGROUND";

    /**
     * Extra used to indicate an error in channel registration.
     *
     * @hide
     */
    public static final String EXTRA_ERROR = "com.urbanairship.push.EXTRA_ERROR";

    /**
     * The channel ID extra.
     *
     * @hide
     */
    public static final String EXTRA_CHANNEL_ID = "com.urbanairship.push.EXTRA_CHANNEL_ID";

    /**
     * This intent action indicates that a push notification has been opened.
     *
     * @hide
     */
    public static final String ACTION_NOTIFICATION_OPENED_PROXY = "com.urbanairship.ACTION_NOTIFICATION_OPENED_PROXY";

    /**
     * This intent action indicates that a push notification button has been opened.
     *
     * @hide
     */
    public static final String ACTION_NOTIFICATION_BUTTON_OPENED_PROXY = "com.urbanairship.ACTION_NOTIFICATION_BUTTON_OPENED_PROXY";

    /**
     * This intent action indicates that a push notification button has been dismissed.
     *
     * @hide
     */
    public static final String ACTION_NOTIFICATION_DISMISSED_PROXY = "com.urbanairship.ACTION_NOTIFICATION_DISMISSED_PROXY";

    /**
     * The CONTENT_INTENT extra is an optional intent that the notification builder can
     * supply on the notification. If set, the intent will be pulled from the notification,
     * stored as part of the supplied UA intent, and then sent inside the UA core receiver.
     *
     * @hide
     */
    public static final String EXTRA_NOTIFICATION_CONTENT_INTENT = "com.urbanairship.push.EXTRA_NOTIFICATION_CONTENT_INTENT";

    /**
     * The DELETE_INTENT extra is an optional intent that the notification builder can
     * supply on the notification. If set, the intent will be pulled from the notification,
     * stored as part of the supplied UA intent, and then sent inside the UA core receiver.
     *
     * @hide
     */
    public static final String EXTRA_NOTIFICATION_DELETE_INTENT = "com.urbanairship.push.EXTRA_NOTIFICATION_DELETE_INTENT";

    /**
     * The description of the notification action button.
     *
     * @hide
     */
    public static final String EXTRA_NOTIFICATION_ACTION_BUTTON_DESCRIPTION = "com.urbanairship.push.EXTRA_NOTIFICATION_ACTION_BUTTON_DESCRIPTION";

    /**
     * The actions payload for the notification action button.
     *
     * @hide
     */
    public static final String EXTRA_NOTIFICATION_BUTTON_ACTIONS_PAYLOAD = "com.urbanairship.push.EXTRA_NOTIFICATION_BUTTON_ACTIONS_PAYLOAD";


    private final String UA_NOTIFICATION_BUTTON_GROUP_PREFIX = "ua_";

    /**
     * The default tag group.
     */
    private final String DEFAULT_TAG_GROUP = "device";


    static final String KEY_PREFIX = "com.urbanairship.push";
    static final String PUSH_ENABLED_KEY = KEY_PREFIX + ".PUSH_ENABLED";
    static final String USER_NOTIFICATIONS_ENABLED_KEY = KEY_PREFIX + ".USER_NOTIFICATIONS_ENABLED";
    static final String PUSH_TOKEN_REGISTRATION_ENABLED_KEY = KEY_PREFIX + ".PUSH_TOKEN_REGISTRATION_ENABLED";

    // As of version 5.0.0
    static final String PUSH_ENABLED_SETTINGS_MIGRATED_KEY = KEY_PREFIX + ".PUSH_ENABLED_SETTINGS_MIGRATED";
    static final String SOUND_ENABLED_KEY = KEY_PREFIX + ".SOUND_ENABLED";
    static final String VIBRATE_ENABLED_KEY = KEY_PREFIX + ".VIBRATE_ENABLED";
    static final String CHANNEL_LOCATION_KEY = KEY_PREFIX + ".CHANNEL_LOCATION";
    static final String CHANNEL_ID_KEY = KEY_PREFIX + ".CHANNEL_ID";
    static final String ALIAS_KEY = KEY_PREFIX + ".ALIAS";
    static final String TAGS_KEY = KEY_PREFIX + ".TAGS";
    static final String LAST_RECEIVED_METADATA = KEY_PREFIX + ".LAST_RECEIVED_METADATA";
    static final String REGISTERED_GCM_SENDER_IDS = KEY_PREFIX + ".REGISTERED_GCM_SENDER_ID";
    static final String QUIET_TIME_ENABLED = KEY_PREFIX + ".QUIET_TIME_ENABLED";

    static final class QuietTime {
        public static final String START_HOUR_KEY = KEY_PREFIX + ".QuietTime.START_HOUR";
        public static final String START_MIN_KEY = KEY_PREFIX + ".QuietTime.START_MINUTE";
        public static final String END_HOUR_KEY = KEY_PREFIX + ".QuietTime.END_HOUR";
        public static final String END_MIN_KEY = KEY_PREFIX + ".QuietTime.END_MINUTE";
        public static final int NOT_SET_VAL = -1;
    }

    static final String QUIET_TIME_INTERVAL = KEY_PREFIX + ".QUIET_TIME_INTERVAL";
    static final String ADM_REGISTRATION_ID_KEY = KEY_PREFIX + ".ADM_REGISTRATION_ID_KEY";
    static final String GCM_INSTANCE_ID_TOKEN_KEY = KEY_PREFIX + ".GCM_INSTANCE_ID_TOKEN_KEY";
    static final String APP_VERSION_KEY = KEY_PREFIX + ".APP_VERSION";
    static final String DEVICE_ID_KEY = KEY_PREFIX + ".DEVICE_ID";
    static final String APID_KEY = KEY_PREFIX + ".APID";

    //singleton stuff
    private NotificationFactory notificationFactory;
    private final Map<String, NotificationActionButtonGroup> actionGroupMap = new HashMap<>();
    private boolean channelTagRegistrationEnabled = true;
    private final NamedUser namedUser;
    private final PreferenceDataStore preferenceDataStore;
    private final AirshipConfigOptions configOptions;
    private boolean channelCreationDelayEnabled;

    private final Object tagLock = new Object();

    /**
     * Creates a PushManager. Normally only one push manager instance should exist, and
     * can be accessed from {@link com.urbanairship.UAirship#getPushManager()}.
     *
     * @param context Application context
     * @param preferenceDataStore The preferences data store.
     * @param configOptions The airship config options.
     * @hide
     */
    public PushManager(Context context, PreferenceDataStore preferenceDataStore, AirshipConfigOptions configOptions) {
        this(context, preferenceDataStore, new NamedUser(preferenceDataStore), configOptions);
    }

    PushManager(Context context, PreferenceDataStore preferenceDataStore, NamedUser namedUser, AirshipConfigOptions configOptions) {
        this.preferenceDataStore = preferenceDataStore;

        DefaultNotificationFactory factory = new DefaultNotificationFactory(context);
        factory.setColor(configOptions.notificationAccentColor);
        if (configOptions.notificationIcon != 0) {
            factory.setSmallIconId(configOptions.notificationIcon);
        }

        this.notificationFactory = factory;

        this.namedUser = namedUser;
        this.configOptions = configOptions;

        actionGroupMap.putAll(ActionButtonGroupsParser.fromXml(context, R.xml.ua_notification_buttons));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            actionGroupMap.putAll(ActionButtonGroupsParser.fromXml(context, R.xml.ua_notification_button_overrides));
        }
    }

    @Override
    protected void init() {
        if (Logger.logLevel < Log.ASSERT && !UAStringUtil.isEmpty(getChannelId())) {
            Log.d(UAirship.getAppName() + " Channel ID", getChannelId());
        }

        this.migratePushEnabledSettings();
        this.migrateQuietTimeInterval();

        channelCreationDelayEnabled = getChannelId() == null && configOptions.channelCreationDelayEnabled;

        // Start registration
        Intent registrationIntent = new Intent(UAirship.getApplicationContext(), PushService.class)
                .setAction(PushService.ACTION_START_REGISTRATION);

        UAirship.getApplicationContext().startService(registrationIntent);

        // If we have a channel already check for pending tags
        if (getChannelId() != null) {
            startUpdateTagsService();
        }


        // Start named user update
        this.namedUser.startUpdateService();

        // Update named user tags if we have a named user
        if (namedUser.getId() != null) {
            this.namedUser.startUpdateTagsService();
        }

    }

    /**
     * Enables channel creation if channel creation has been delayed.
     * <p/>
     * This setting is persisted between application starts, so there is no need to call this
     * repeatedly. It is only necessary to call this when channelCreationDelayEnabled has been
     * set to <code>true</code> in the airship config.
     */
    public void enableChannelCreation() {
        if (isChannelCreationDelayEnabled()) {
            channelCreationDelayEnabled = false;
            updateRegistration();
        }
    }

    /**
     * Enables or disables push notifications.
     * <p/>
     * This setting is persisted between application starts, so there is no need to call this
     * repeatedly. It is only necessary to call this when a user preference has changed.
     *
     * @param enabled A boolean indicating whether push is enabled.
     */
    public void setPushEnabled(boolean enabled) {
        preferenceDataStore.put(PUSH_ENABLED_KEY, enabled);
        updateRegistration();
    }

    /**
     * Determines whether push is enabled.
     *
     * @return <code>true</code> if push is enabled, <code>false</code> otherwise.
     * This defaults to true, and must be explicitly set by the app.
     */
    public boolean isPushEnabled() {
        return preferenceDataStore.getBoolean(PUSH_ENABLED_KEY, true);
    }

    /**
     * Enables or disables user notifications.
     * <p/>
     * User notifications are push notifications that contain an alert message and are
     * intended to be shown to the user.
     * <p/>
     * This setting is persisted between application starts, so there is no need to call this
     * repeatedly. It is only necessary to call this when a user preference has changed.
     *
     * @param enabled A boolean indicating whether user push is enabled.
     */
    public void setUserNotificationsEnabled(boolean enabled) {
        preferenceDataStore.put(USER_NOTIFICATIONS_ENABLED_KEY, enabled);
        updateRegistration();
    }

    /**
     * Determines whether user-facing push notifications are enabled.
     *
     * @return <code>true</code> if user push is enabled, <code>false</code> otherwise.
     */
    public boolean getUserNotificationsEnabled() {
        return preferenceDataStore.getBoolean(USER_NOTIFICATIONS_ENABLED_KEY, false);
    }

    /**
     * Sets the notification factory used when push notifications are received.
     * <p/>
     * Specify a notification factory here to customize the display
     * of a push notification's Custom Expanded Views in the
     * Android Notification Manager.
     * <p/>
     * If <code>null</code>, push notifications will not be displayed by the
     * library.
     *
     * @param factory The notification factory
     * @see com.urbanairship.push.notifications.NotificationFactory
     * @see com.urbanairship.push.notifications.DefaultNotificationFactory
     * @see com.urbanairship.push.notifications.SystemNotificationFactory
     * @see com.urbanairship.push.notifications.CustomLayoutNotificationFactory
     */
    public void setNotificationFactory(@NonNull NotificationFactory factory) {
        notificationFactory = factory;
    }

    /**
     * Returns the current notification factory.
     *
     * @return The current notification factory.
     */
    public NotificationFactory getNotificationFactory() {
        return notificationFactory;
    }

    /**
     * Returns the <code>PreferenceDataStore</code> singleton for this application.
     *
     * @return The PreferenceDataStore
     */
    PreferenceDataStore getPreferenceDataStore() {
        return preferenceDataStore;
    }

    /**
     * Sets both the alias and tags for this channel and updates the server.
     * <p/>
     * Tags should be URL-safe with a length greater than 0 and less than 127 characters. If your
     * tag includes whitespace or special characters, we recommend URL encoding the string.
     * <p/>
     *
     * @param alias The desired alias, <code>null</code> to remove
     * @param tags The desired set of tags, must be non-null
     * @see #setAlias(String)
     * @see #setTags(Set)
     */
    public void setAliasAndTags(@Nullable String alias, @NonNull Set<String> tags) {
        //noinspection ConstantConditions
        if (tags == null) {
            throw new IllegalArgumentException("Tags must be non-null.");
        }


        // only update server w/ registration call if
        // at least one of the values has changed
        boolean updateServer = false;

        if (!UAStringUtil.equals(alias, getAlias())) {
            setAlias(alias);
            updateServer = true;
        }

        if (!storeTags(tags)) {
            updateServer = true;
        }

        if (updateServer) {
            updateRegistration();
        }
    }

    /**
     * Determines whether the app is capable of receiving push,
     * meaning whether a GCM or ADM registration ID is present.
     *
     * @return <code>true</code> if push is available, <code>false</code> otherwise.
     */
    public boolean isPushAvailable() {
        if (getPushTokenRegistrationEnabled()) {
            switch (UAirship.shared().getPlatformType()) {
                case UAirship.AMAZON_PLATFORM:
                    return !UAStringUtil.isEmpty(getAdmId());
                case UAirship.ANDROID_PLATFORM:
                    return !UAStringUtil.isEmpty(getGcmToken());
            }
        }

        return false;
    }

    /**
     * Returns if the application is currently opted in for push.
     *
     * @return <code>true</code> if opted in for push.
     */
    public boolean isOptIn() {
        return isPushEnabled() && isPushAvailable() && getUserNotificationsEnabled();
    }

    /**
     * Returns the next channel registration payload
     *
     * @return The ChannelRegistrationPayload payload
     */
    ChannelRegistrationPayload getNextChannelRegistrationPayload() {
        ChannelRegistrationPayload.Builder builder = new ChannelRegistrationPayload.Builder()
                .setAlias(getAlias())
                .setTags(getChannelTagRegistrationEnabled(), getTags())
                .setOptIn(isOptIn())
                .setBackgroundEnabled(isPushEnabled() && isPushAvailable())
                .setUserId(UAirship.shared().getInbox().getUser().getId())
                .setApid(getApid());

        switch (UAirship.shared().getPlatformType()) {
            case UAirship.ANDROID_PLATFORM:
                builder.setDeviceType("android");
                if (getPushTokenRegistrationEnabled()) {
                    builder.setPushAddress(getGcmToken());
                }
                break;
            case UAirship.AMAZON_PLATFORM:
                builder.setDeviceType("amazon");
                if (getPushTokenRegistrationEnabled()) {
                    builder.setPushAddress(getAdmId());
                }
                break;
        }

        return builder.build();
    }


    /**
     * Update registration.
     */
    public void updateRegistration() {
        Context ctx = UAirship.getApplicationContext();
        Intent i = new Intent(ctx, PushService.class);
        i.setAction(PushService.ACTION_UPDATE_CHANNEL_REGISTRATION);
        ctx.startService(i);
    }

    /**
     * Set the alias for the channel and update the server.
     * <p/>
     * If you are setting both the alias and tags at the same time, you should
     * use {@link #setAliasAndTags(String, Set)} to avoid making an extra
     * network call.
     * <p/>
     * Refer to the
     * <a href="https://docs.urbanairship.com/build/android_features.html#aliases">Alias</a>
     * for more information on the use of aliases.
     *
     * @param alias The alias, <code>null</code> to remove
     */
    public void setAlias(@Nullable String alias) {
        if (alias != null) {
            alias = alias.trim();
        }

        if (!UAStringUtil.equals(alias, getAlias())) {
            preferenceDataStore.put(ALIAS_KEY, alias);
            updateRegistration();
        }
    }

    /**
     * Set tags for the channel and update the server.
     * <p/>
     * Tags should be URL-safe with a length greater than 0 and less than 127 characters. If your
     * tag includes whitespace or special characters, we recommend URL encoding the string.
     * <p/>
     * To clear the current set of tags, pass an empty set to this method.
     * <p/>
     * If you are setting both the alias and tags at the same time, you should
     * use {@link #setAliasAndTags(String, Set)} to avoid making an extra
     * network call.
     * <p/>
     * Refer to the <a href="https://docs.urbanairship.com/build/android_features.html#tags">Tag API</a> for
     * more information.
     *
     * @param tags A set of tag strings.
     */
    public void setTags(@NonNull Set<String> tags) {
        //noinspection ConstantConditions
        if (tags == null) {
            throw new IllegalArgumentException("Tags must be non-null.");
        }

        if (storeTags(tags)) {
            updateRegistration();
        }
    }

    /**
     * Stores tags in the preference data store.
     *
     * @param tags Tags to store.
     * @return {code true} if tags changed, otherwise {@code false}.
     */
    private boolean storeTags(@NonNull Set<String> tags) {
        synchronized (tagLock) {
            Set<String> normalizedTags = TagUtils.normalizeTags(tags);
            if (!normalizedTags.equals(getTags())) {
                if (normalizedTags.isEmpty()) {
                    preferenceDataStore.remove(TAGS_KEY);
                } else {
                    preferenceDataStore.put(TAGS_KEY, JsonValue.wrapOpt(normalizedTags));
                }

                return true;
            }

            return false;
        }
    }


    /**
     * Returns the current alias for this application's channel.
     *
     * @return The string alias, or null if one is not set.
     */
    public String getAlias() {
        return preferenceDataStore.getString(ALIAS_KEY, null);
    }

    /**
     * Returns the current named user.
     *
     * @return The named user.
     */
    @NonNull
    public NamedUser getNamedUser() {
        return namedUser;
    }

    /**
     * Returns the current set of tags.
     * <p/>
     * An empty set indicates that no tags are set on this channel.
     *
     * @return The current set of tags.
     */
    @NonNull
    public Set<String> getTags() {
        synchronized (tagLock) {
            Set<String> tags = new HashSet<>();

            JsonValue jsonValue = preferenceDataStore.getJsonValue(TAGS_KEY);

            if (jsonValue.isJsonList()) {
                for (JsonValue tag : jsonValue.getList()) {
                    if (tag.isString()) {
                        tags.add(tag.getString());
                    }
                }
            }

            Set<String> normalizedTags = TagUtils.normalizeTags(tags);

            //to prevent the getTags call from constantly logging tag set failures, sync tags
            if (tags.size() != normalizedTags.size()) {
                this.setTags(normalizedTags);
            }

            return normalizedTags;
        }
    }

    /**
     * Returns the currently registered ADM ID.
     *
     * @return An ADM identifier string, or null if not present.
     */
    @Nullable
    public String getAdmId() {
        return preferenceDataStore.getString(ADM_REGISTRATION_ID_KEY, null);
    }

    /**
     * Determines whether tags are enabled on the device.
     * If <code>false</code>, no locally specified tags will be sent to the server during registration.
     * The default value is <code>true</code>.
     *
     * @return <code>true</code> if tags are enabled on the device, <code>false</code> otherwise.
     */
    public boolean getChannelTagRegistrationEnabled() {
        return channelTagRegistrationEnabled;
    }

    /**
     * Sets whether tags are enabled on the device. The default value is <code>true</code>.
     * If <code>false</code>, no locally specified tags will be sent to the server during registration.
     *
     * @param enabled A boolean indicating whether tags are enabled on the device.
     */
    public void setChannelTagRegistrationEnabled(boolean enabled) {
        channelTagRegistrationEnabled = enabled;
    }

    /**
     * Determines whether the GCM token or ADM ID is sent during channel registration.
     * If {@code false}, the app will not be able to receive push notifications.
     * The default value is {@code true}.
     *
     * @return {@code true} if the GCM token or ADM ID is sent during channel registration,
     * {@code false} otherwise.
     */
    public boolean getPushTokenRegistrationEnabled() {
        return preferenceDataStore.getBoolean(PUSH_TOKEN_REGISTRATION_ENABLED_KEY, true);
    }

    /**
     * Sets whether the GCM token or ADM ID is sent during channel registration.
     * If {@code false}, the app will not be able to receive push notifications.
     *
     * @param enabled A boolean indicating whether the GCM token or ADM ID is sent during
     * channel registration.
     */
    public void setPushTokenRegistrationEnabled(boolean enabled) {
        preferenceDataStore.put(PUSH_TOKEN_REGISTRATION_ENABLED_KEY, enabled);
        updateRegistration();
    }

    /**
     * Determines whether sound is enabled.
     *
     * @return A boolean indicated whether sound is enabled.
     */
    public boolean isSoundEnabled() {
        return preferenceDataStore.getBoolean(SOUND_ENABLED_KEY, true);
    }

    /**
     * Enables or disables sound.
     *
     * @param enabled A boolean indicating whether sound is enabled.
     */
    public void setSoundEnabled(boolean enabled) {
        preferenceDataStore.put(SOUND_ENABLED_KEY, enabled);
    }

    /**
     * Determines whether vibration is enabled.
     *
     * @return A boolean indicating whether vibration is enabled.
     */
    public boolean isVibrateEnabled() {
        return preferenceDataStore.getBoolean(VIBRATE_ENABLED_KEY, true);
    }

    /**
     * Enables or disables vibration.
     *
     * @param enabled A boolean indicating whether vibration is enabled.
     */
    public void setVibrateEnabled(boolean enabled) {
        preferenceDataStore.put(VIBRATE_ENABLED_KEY, enabled);
    }

    /**
     * Determines whether "Quiet Time" is enabled.
     *
     * @return A boolean indicating whether Quiet Time is enabled.
     */
    public boolean isQuietTimeEnabled() {
        return preferenceDataStore.getBoolean(QUIET_TIME_ENABLED, false);
    }

    /**
     * Sets the quiet time enabled.
     *
     * @param enabled A boolean indicating whether quiet time is enabled.
     */
    public void setQuietTimeEnabled(boolean enabled) {
        preferenceDataStore.put(QUIET_TIME_ENABLED, enabled);
    }

    /**
     * Determines whether we are currently in the middle of "Quiet Time".  Returns false if Quiet Time is disabled,
     * and evaluates whether or not the current date/time falls within the Quiet Time interval set by the user.
     *
     * @return A boolean indicating whether it is currently "Quiet Time".
     */
    public boolean isInQuietTime() {
        if (!this.isQuietTimeEnabled()) {
            return false;
        }

        QuietTimeInterval quietTimeInterval = QuietTimeInterval.parseJson(preferenceDataStore.getString(QUIET_TIME_INTERVAL, null));
        return quietTimeInterval != null && quietTimeInterval.isInQuietTime();
    }

    /**
     * Determines whether channel creation is initially disabled, to be enabled later
     * by enableChannelCreation.
     *
     * @return <code>true</code> if channel creation is initially disabled, <code>false</code> otherwise.
     */
    boolean isChannelCreationDelayEnabled() {
        return channelCreationDelayEnabled;
    }

    /**
     * Returns the Quiet Time interval currently set by the user.
     *
     * @return An array of two Date instances, representing the start and end of Quiet Time.
     */
    public Date[] getQuietTimeInterval() {
        QuietTimeInterval quietTimeInterval = QuietTimeInterval.parseJson(preferenceDataStore.getString(QUIET_TIME_INTERVAL, null));
        if (quietTimeInterval != null) {
            return quietTimeInterval.getQuietTimeIntervalDateArray();
        } else {
            return null;
        }
    }

    /**
     * Returns the send metadata of the last received push.
     *
     * @return The send metadata from the last received push, or null if not found.
     */
    @Nullable
    public String getLastReceivedMetadata() {
        return preferenceDataStore.getString(LAST_RECEIVED_METADATA, null);
    }

    /**
     * Store the send metadata from the last received push.
     *
     * @param sendMetadata The send metadata string.
     */
    void setLastReceivedMetadata(String sendMetadata) {
        preferenceDataStore.put(LAST_RECEIVED_METADATA, sendMetadata);
    }

    /**
     * Sets the Quiet Time interval.
     *
     * @param startTime A Date instance indicating when Quiet Time should start.
     * @param endTime A Date instance indicating when Quiet Time should end.
     */
    public void setQuietTimeInterval(@NonNull Date startTime, @NonNull Date endTime) {
        QuietTimeInterval quietTimeInterval = new QuietTimeInterval.Builder()
                .setQuietTimeInterval(startTime, endTime)
                .build();
        preferenceDataStore.put(QUIET_TIME_INTERVAL, quietTimeInterval.toJsonValue());
    }

    /**
     * Edit the channel tag groups.
     *
     * @return A {@link TagGroupsEditor}.
     */
    public TagGroupsEditor editTagGroups() {
        return new TagGroupsEditor(PushService.ACTION_UPDATE_CHANNEL_TAG_GROUPS) {
            @Override
            public TagGroupsEditor addTag(@NonNull String tagGroup, @NonNull String tag) {
                if (channelTagRegistrationEnabled && DEFAULT_TAG_GROUP.equals(tagGroup)) {
                    Logger.error("Unable to add tag " + tag + " to device tag group when channelTagRegistrationEnabled is true.");
                    return this;
                }
                return super.addTag(tagGroup, tag);
            }

            @Override
            public TagGroupsEditor addTags(@NonNull String tagGroup, @NonNull Set<String> tags) {
                if (channelTagRegistrationEnabled && DEFAULT_TAG_GROUP.equals(tagGroup)) {
                    Logger.error("Unable to add tags { " + tags + " } to device tag group when channelTagRegistrationEnabled is true.");
                    return this;
                }

                return super.addTags(tagGroup, tags);
            }

            @Override
            public TagGroupsEditor removeTag(@NonNull String tagGroup, @NonNull String tag) {
                if (channelTagRegistrationEnabled && DEFAULT_TAG_GROUP.equals(tagGroup)) {
                    Logger.error("Unable to remove tag " + tag + " from device tag group when channelTagRegistrationEnabled is true.");
                    return this;
                }
                return super.removeTag(tagGroup, tag);
            }

            @Override
            public TagGroupsEditor removeTags(@NonNull String tagGroup, @NonNull Set<String> tags) {
                if (channelTagRegistrationEnabled && DEFAULT_TAG_GROUP.equals(tagGroup)) {
                    Logger.error("Unable to remove tags { " + tags + " } from device tag group when channelTagRegistrationEnabled is true.");
                    return this;
                }

                return super.removeTags(tagGroup, tags);
            }
        };
    }

    /**
     * Edits channel Tags.
     *
     * @return A {@link TagEditor}
     */
    public TagEditor editTags() {
        return new TagEditor() {
            @Override
            void onApply(boolean clear, Set<String> tagsToAdd, Set<String> tagsToRemove) {
                synchronized (tagLock) {
                    Set<String> tags = clear ? new HashSet<String>() : getTags();

                    tags.addAll(tagsToAdd);
                    tags.removeAll(tagsToRemove);

                    if (storeTags(tags)) {
                        updateRegistration();
                    }
                }
            }
        };
    }


    /**
     * Register a notification action group under the given name.
     * <p/>
     * The provided notification builders will automatically add the actions to the
     * notification when a message is received with a group specified under the
     * {@link com.urbanairship.push.PushMessage#EXTRA_INTERACTIVE_TYPE}
     * key.
     *
     * @param id The id of the action group.
     * @param group The notification action group.
     */
    public void addNotificationActionButtonGroup(@NonNull String id, @NonNull NotificationActionButtonGroup group) {
        if (id.startsWith(UA_NOTIFICATION_BUTTON_GROUP_PREFIX)) {
            Logger.warn("Unable to add any notification button groups that starts with the reserved Urban Airship prefix " + UA_NOTIFICATION_BUTTON_GROUP_PREFIX);
            return;
        }

        actionGroupMap.put(id, group);
    }

    /**
     * Removes the notification button group under the given name.
     *
     * @param id The id of the button group to remove.
     */
    public void removeNotificationActionButtonGroup(@NonNull String id) {
        if (id.startsWith(UA_NOTIFICATION_BUTTON_GROUP_PREFIX)) {
            Logger.error("Unable to remove any reserved Urban Airship actions groups that begin with " + UA_NOTIFICATION_BUTTON_GROUP_PREFIX);
            return;
        }

        actionGroupMap.remove(id);
    }

    /**
     * Returns the notification action group that is registered under the given name.
     *
     * @param id The id of the action group.
     * @return The notification action group.
     */
    public NotificationActionButtonGroup getNotificationActionGroup(String id) {
        return actionGroupMap.get(id);
    }

    /**
     * Get the Channel ID
     *
     * @return A Channel ID string
     */
    @Nullable
    public String getChannelId() {
        return preferenceDataStore.getString(CHANNEL_ID_KEY, null);
    }

    /**
     * Gets the channel location.
     *
     * @return The channel location.
     */
    @Nullable
    String getChannelLocation() {
        return preferenceDataStore.getString(CHANNEL_LOCATION_KEY, null);
    }

    /**
     * Sets the Channel ID and channel location.
     * Also update the user.
     *
     * @param channelId The channel ID as a string.
     * @param channelLocation The channel location as a URL.
     */
    void setChannel(String channelId, String channelLocation) {
        preferenceDataStore.put(CHANNEL_ID_KEY, channelId);
        preferenceDataStore.put(CHANNEL_LOCATION_KEY, channelLocation);
    }

    /**
     * Sets the registered ADM ID.
     *
     * @param admId An ADM identifier string.
     */
    void setAdmId(String admId) {
        preferenceDataStore.put(APP_VERSION_KEY, UAirship.getPackageInfo().versionCode);
        preferenceDataStore.put(ADM_REGISTRATION_ID_KEY, admId);
        preferenceDataStore.put(DEVICE_ID_KEY, getSecureId(UAirship.getApplicationContext()));
    }

    /**
     * Gets the Android secure ID.
     *
     * @param context The application context.
     * @return The Android secure ID.
     * @hide
     */
    static String getSecureId(@NonNull Context context) {
        return Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
    }

    /**
     * Starts the push service to update tag groups.
     */
    void startUpdateTagsService() {
        Intent tagUpdateIntent = new Intent(UAirship.getApplicationContext(), PushService.class)
                .setAction(PushService.ACTION_UPDATE_CHANNEL_TAG_GROUPS);
        UAirship.getApplicationContext().startService(tagUpdateIntent);
    }

    /**
     * Sets the GCM Instance ID token.
     *
     * @param token The Instance ID token.
     */
    void setGcmToken(String token) {
        preferenceDataStore.put(GCM_INSTANCE_ID_TOKEN_KEY, token);
        preferenceDataStore.put(APP_VERSION_KEY, UAirship.getPackageInfo().versionCode);
        preferenceDataStore.put(DEVICE_ID_KEY, getSecureId(UAirship.getApplicationContext()));
    }

    /**
     * Gets the GCM Instance ID token.
     *
     * @return The GCM token.
     */
    @Nullable
    public String getGcmToken() {
        return preferenceDataStore.getString(GCM_INSTANCE_ID_TOKEN_KEY, null);
    }

    /**
     * Sets the registered sender ID.
     *
     * @param senderId The registered sender ID.
     */
    void setRegisteredGcmSenderId(String senderId) {
        preferenceDataStore.put(REGISTERED_GCM_SENDER_IDS, senderId);
    }

    /**
     * Gets the registered sender ID.
     *
     * @return The registered sender ID.
     */
    String getRegisteredGcmSenderId() {
        return preferenceDataStore.getString(REGISTERED_GCM_SENDER_IDS, null);
    }

    /**
     * Returns the app version associated with the current Registration ID.
     *
     * @return The app version string, or -1 if not found.
     */
    int getAppVersionCode() {
        return preferenceDataStore.getInt(APP_VERSION_KEY, -1);
    }

    /**
     * Returns the device ID associated with the current Registration ID.
     *
     * @return The device ID string, or <code>null</code> if not found.
     */
    String getDeviceId() {
        return preferenceDataStore.getString(DEVICE_ID_KEY, null);
    }

    /**
     * Return the device's existing APID
     *
     * @return an APID string or null if it doesn't exist.
     */
    String getApid() {
        return preferenceDataStore.getString(APID_KEY, null);
    }

    /**
     * Migrates the old push enabled setting to the new user notifications enabled
     * setting, and enables push by default. This was introduced in version 5.0.0.
     */
    void migratePushEnabledSettings() {

        if (preferenceDataStore.getBoolean(PUSH_ENABLED_SETTINGS_MIGRATED_KEY, false)) {
            return;
        }

        Logger.info("Migrating push enabled preferences");

        // get old push enabled value, defaulting to false as before
        boolean oldPushEnabled = this.preferenceDataStore.getBoolean(PUSH_ENABLED_KEY, false);

        // copy old push enabled value to user notifications enabled slot
        Logger.info("Setting user notifications enabled to " + Boolean.toString(oldPushEnabled));
        preferenceDataStore.put(USER_NOTIFICATIONS_ENABLED_KEY, oldPushEnabled);

        if (!oldPushEnabled) {
            Logger.info("Push is now enabled. You can continue to toggle the opt-in state by " +
                    "enabling or disabling user notifications");
        }

        // set push enabled to true
        preferenceDataStore.put(PUSH_ENABLED_KEY, true);
        preferenceDataStore.put(PUSH_ENABLED_SETTINGS_MIGRATED_KEY, true);
    }

    /**
     * Migrates the old quiet time interval to use QuietTimeInterval.
     */
    void migrateQuietTimeInterval() {

        // Attempt to extract an old quiet time interval
        int startHr = preferenceDataStore.getInt(QuietTime.START_HOUR_KEY, QuietTime.NOT_SET_VAL);
        int startMin = preferenceDataStore.getInt(QuietTime.START_MIN_KEY, QuietTime.NOT_SET_VAL);
        int endHr = preferenceDataStore.getInt(QuietTime.END_HOUR_KEY, QuietTime.NOT_SET_VAL);
        int endMin = preferenceDataStore.getInt(QuietTime.END_MIN_KEY, QuietTime.NOT_SET_VAL);

        if (startHr == QuietTime.NOT_SET_VAL || startMin == QuietTime.NOT_SET_VAL ||
                endHr == QuietTime.NOT_SET_VAL || endMin == QuietTime.NOT_SET_VAL) {
            return;
        }

        Logger.info("Migrating quiet time interval");

        QuietTimeInterval quietTimeInterval = new QuietTimeInterval.Builder()
                .setStartHour(startHr)
                .setStartMin(startMin)
                .setEndHour(endHr)
                .setEndMin(endMin)
                .build();

        preferenceDataStore.put(QUIET_TIME_INTERVAL, quietTimeInterval.toJsonValue());
        preferenceDataStore.remove(QuietTime.START_HOUR_KEY);
        preferenceDataStore.remove(QuietTime.START_MIN_KEY);
        preferenceDataStore.remove(QuietTime.END_HOUR_KEY);
        preferenceDataStore.remove(QuietTime.END_MIN_KEY);
    }
}
