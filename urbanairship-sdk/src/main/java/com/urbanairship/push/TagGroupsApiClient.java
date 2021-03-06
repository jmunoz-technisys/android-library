/* Copyright 2016 Urban Airship and Contributors */

package com.urbanairship.push;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;

import com.urbanairship.AirshipConfigOptions;
import com.urbanairship.Logger;
import com.urbanairship.UAirship;
import com.urbanairship.http.RequestFactory;
import com.urbanairship.http.Response;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonValue;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * A high level abstraction for performing tag groups operations.
 */
class TagGroupsApiClient {

    private static final String CHANNEL_TAGS_PATH = "api/channels/tags/";
    private static final String NAMED_USER_TAGS_PATH = "api/named_users/tags/";
    private static final String AUDIENCE_KEY = "audience";
    private static final String ANDROID_CHANNEL_KEY = "android_channel";
    private static final String AMAZON_CHANNEL_KEY = "amazon_channel";
    private static final String NAMED_USER_ID_KEY = "named_user_id";
    private static final String ADD_KEY = "add";
    private static final String REMOVE_KEY = "remove";

    private final String urlString;
    private final String appKey;
    private final String appSecret;
    private final RequestFactory requestFactory;

    TagGroupsApiClient(@NonNull AirshipConfigOptions configOptions) {
        this(configOptions, new RequestFactory());
    }

    @VisibleForTesting
    public TagGroupsApiClient(@NonNull AirshipConfigOptions configOptions, @NonNull RequestFactory requestFactory) {
        this.requestFactory = requestFactory;
        this.urlString = configOptions.hostURL;
        this.appKey = configOptions.getAppKey();
        this.appSecret = configOptions.getAppSecret();
    }

    /**
     * Update the named user tags.
     *
     * @param namedUserId The named user ID string.
     * @param addTags The map of tags to add.
     * @param removeTags The map of tags to remove.
     * @return The response or null if an error occurred.
     */
    Response updateNamedUserTags(@NonNull String namedUserId,
                                 @NonNull Map<String, Set<String>> addTags,
                                 @NonNull Map<String, Set<String>> removeTags) {

        URL namedUserTagsUrl = getTagURL(NAMED_USER_TAGS_PATH);
        if (namedUserTagsUrl == null) {
            Logger.error("The named user tags URL cannot be null.");
            return null;
        }

        if (addTags.isEmpty() && removeTags.isEmpty()) {
            Logger.error("Both addTags and removeTags cannot be empty.");
            return null;
        }

        return request(namedUserTagsUrl, NAMED_USER_ID_KEY, namedUserId, addTags, removeTags);
    }

    /**
     * Update the channel tag group.
     *
     * @param channelId The channel ID string.
     * @param addTags The map of tags to add.
     * @param removeTags The map of tags to remove.
     * @return The response or null if an error occurred.
     */
    Response updateChannelTags(@NonNull String channelId,
                               @NonNull Map<String, Set<String>> addTags,
                               @NonNull Map<String, Set<String>> removeTags) {

        URL channelTagsUrl = getTagURL(CHANNEL_TAGS_PATH);
        if (channelTagsUrl == null) {
            Logger.error("The channel tags URL cannot be null.");
            return null;
        }

        if (addTags.isEmpty() && removeTags.isEmpty()) {
            Logger.error("Both addTags and removeTags cannot be empty.");
            return null;
        }

        return request(channelTagsUrl, getChannelType(), channelId, addTags, removeTags);
    }

    /**
     * Sends the tag groups request.
     *
     * @param url The specified URL to send the request to.
     * @param audienceSelector The audience selector string.
     * @param audienceId The audience ID string.
     * @param addTags The map of tags to add.
     * @param removeTags The map of tags to remove.
     * @return The response or null if an error occurred.
     */
    private Response request(@NonNull URL url, @NonNull String audienceSelector, @NonNull String audienceId,
                             @NonNull Map<String, Set<String>> addTags,  @NonNull Map<String, Set<String>> removeTags) {

        Map<String, Object> payload = new HashMap<>();
        Map<String, Object> audience = new HashMap<>();


        audience.put(audienceSelector, audienceId);
        payload.put(AUDIENCE_KEY, audience);
        if (!addTags.isEmpty()) {
            payload.put(ADD_KEY, addTags);
        }
        if (!removeTags.isEmpty()) {
            payload.put(REMOVE_KEY, removeTags);
        }

        String tagPayload;
        try {
            tagPayload = JsonValue.wrap(payload).toString();
        } catch (JsonException e) {
            Logger.error("Failed to create channel tag groups payload as json.", e);
            return null;
        }

        Logger.info("Updating tag groups with payload: " + tagPayload);

        Response response = requestFactory.createRequest("POST", url)
                                          .setCredentials(appKey, appSecret)
                                          .setRequestBody(tagPayload, "application/json")
                                          .setHeader("Accept", "application/vnd.urbanairship+json; version=3;")
                                          .execute();

        if (response == null) {
            Logger.error("Failed to receive a response for tag groups.");
        } else {
            Logger.debug("Received a response for tag groups: " + response);
        }

        return response;
    }

    /**
     * Returns the channel type based on the platform.
     *
     * @return The channel type string.
     */
    String getChannelType() {
        String channelType = null;
        switch (UAirship.shared().getPlatformType()) {
            case UAirship.ANDROID_PLATFORM:
                channelType = ANDROID_CHANNEL_KEY;
                break;
            case UAirship.AMAZON_PLATFORM:
                channelType = AMAZON_CHANNEL_KEY;
                break;
        }
        return channelType;
    }

    /**
     * Returns the tag URL.
     *
     * @param urlString The URL string.
     *
     * @return The tag URL.
     */
    @Nullable
    URL getTagURL(String urlString) {
        URL tagUrl = null;
        try {
            tagUrl = new URL(this.urlString + urlString);
        } catch (MalformedURLException e) {
            Logger.error("Invalid tag URL.", e);
        }

        return tagUrl;
    }
}
