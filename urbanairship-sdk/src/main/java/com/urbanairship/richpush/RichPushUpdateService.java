/* Copyright 2016 Urban Airship and Contributors */

package com.urbanairship.richpush;

import android.content.Intent;
import android.os.Bundle;
import android.os.ResultReceiver;
import android.support.annotation.NonNull;

import com.urbanairship.BaseIntentService;
import com.urbanairship.Logger;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.UAirship;

import java.net.MalformedURLException;
import java.net.URL;

/**
 * Service for updating the {@link RichPushUser} and their messages.
 *
 * @hide
 */
public class RichPushUpdateService extends BaseIntentService {

    /**
     * Starts the service in order to update just the {@link RichPushMessage}'s messages.
     */
    public static final String ACTION_RICH_PUSH_MESSAGES_UPDATE = "com.urbanairship.richpush.MESSAGES_UPDATE";

    /**
     * Starts the service to sync message state.
     */
    public static final String ACTION_SYNC_MESSAGE_STATE = "com.urbanairship.richpush.SYNC_MESSAGE_STATE";

    /**
     * Starts the service in order to update just the {@link RichPushUser} itself.
     */
    public static final String ACTION_RICH_PUSH_USER_UPDATE = "com.urbanairship.richpush.USER_UPDATE";

    /**
     * Extra key for a result receiver passed in with the intent.
     */
    public static final String EXTRA_RICH_PUSH_RESULT_RECEIVER = "com.urbanairship.richpush.RESULT_RECEIVER";

    /**
     * Extra key to indicate if the rich push user needs to be updated forcefully.
     */
    public static final String EXTRA_FORCEFULLY = "com.urbanairship.richpush.EXTRA_FORCEFULLY";

    /**
     * Status code indicating an update complete successfully.
     */
    public static final int STATUS_RICH_PUSH_UPDATE_SUCCESS = 0;

    /**
     * Status code indicating an update did not complete successfully.
     */
    public static final int STATUS_RICH_PUSH_UPDATE_ERROR = 1;

    static final String LAST_MESSAGE_REFRESH_TIME = "com.urbanairship.user.LAST_MESSAGE_REFRESH_TIME";

    /**
     * RichPushUpdateService constructor.
     */
    public RichPushUpdateService() {
        super("RichPushUpdateService");
    }

    @Override
    protected Delegate getServiceDelegate(@NonNull String intentAction, @NonNull PreferenceDataStore dataStore) {
        Logger.verbose("RichPushUpdateService - Service delegate for intent: " + intentAction);

        switch(intentAction) {
            case ACTION_RICH_PUSH_USER_UPDATE:
                return new UserServiceDelegate(getApplicationContext(), dataStore);

            case ACTION_SYNC_MESSAGE_STATE:
            case ACTION_RICH_PUSH_MESSAGES_UPDATE:
                return new InboxServiceDelegate(getApplicationContext(), dataStore);
        }
        return  null;
    }

    /**
     * Helper method to respond to result receiver.
     *
     * @param intent The received intent.
     * @param status If the intent was successful or not.
     */
    static void respond(Intent intent, boolean status) {
        ResultReceiver receiver = intent.getParcelableExtra(EXTRA_RICH_PUSH_RESULT_RECEIVER);
        if (receiver != null) {
            if (status) {
                receiver.send(RichPushUpdateService.STATUS_RICH_PUSH_UPDATE_SUCCESS, new Bundle());
            } else {
                receiver.send(RichPushUpdateService.STATUS_RICH_PUSH_UPDATE_ERROR, new Bundle());
            }
        }
    }

    /**
     * Gets the URL for inbox/user api calls
     *
     * @param path The url path.
     * @param args Url arguments.
     * @return The URL or null if an error occurred.
     */
    static URL getUserURL(String path, Object... args) {
        String hostURL = UAirship.shared().getAirshipConfigOptions().hostURL;
        String urlString = String.format(hostURL + path, args);
        try {
            return new URL(urlString);
        } catch (MalformedURLException e) {
            Logger.error("Invalid userURL", e);
        }
        return null;
    }
}
