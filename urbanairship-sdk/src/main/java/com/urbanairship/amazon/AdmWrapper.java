/* Copyright 2016 Urban Airship and Contributors */

package com.urbanairship.amazon;

import android.content.Context;
import android.support.annotation.NonNull;

import com.amazon.device.messaging.ADM;
import com.amazon.device.messaging.development.ADMManifest;
import com.urbanairship.Logger;
import com.urbanairship.UAirship;

/**
 * Wrapper around ADM methods.
 */
class AdmWrapper {

    /**
     * Wraps {@link com.amazon.device.messaging.development.ADMManifest#checkManifestAuthoredProperly(android.content.Context)}.
     */
    public static void validateManifest() {
        try {
            ADMManifest.checkManifestAuthoredProperly(UAirship.getApplicationContext());
        } catch (RuntimeException ex) {
            Logger.error("AndroidManifest invalid ADM setup.", ex);
        }
    }

    /**
     * Wraps {@link com.amazon.device.messaging.ADM#isSupported()}.
     *
     * @return The value returned by {@link com.amazon.device.messaging.ADM#isSupported()}.
     */
    public static boolean isSupported() {
        try {
            return new ADM(UAirship.getApplicationContext()).isSupported();
        } catch (RuntimeException ex) {
            Logger.error("Failed to call ADM. Make sure ADM jar is not bundled with the APK.");
            return false;
        }
    }

    /**
     * Wraps {@link com.amazon.device.messaging.ADM#startRegister()}.
     */
    public static void startRegistration(@NonNull Context context) {
        new ADM(context).startRegister();
    }
}
