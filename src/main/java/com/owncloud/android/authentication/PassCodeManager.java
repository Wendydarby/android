/*
 *   ownCloud Android client application
 *
 *   @author David A. Velasco
 *   Copyright (C) 2015 ownCloud Inc.
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License version 2,
 *   as published by the Free Software Foundation.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package com.owncloud.android.authentication;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.PowerManager;
import android.view.Window;
import android.view.WindowManager;

import com.nextcloud.client.preferences.AppPreferences;
import com.nextcloud.client.preferences.PreferenceManager;
import com.owncloud.android.MainApp;
import com.owncloud.android.ui.activity.PassCodeActivity;
import com.owncloud.android.ui.activity.RequestCredentialsActivity;
import com.owncloud.android.ui.activity.SettingsActivity;
import com.owncloud.android.utils.DeviceCredentialUtils;

import java.util.HashSet;
import java.util.Set;

public final class PassCodeManager {

    private static final Set<Class> exemptOfPasscodeActivities;

    public static final int PASSCODE_ACTIVITY = 9999;

    static {
        exemptOfPasscodeActivities = new HashSet<>();
        exemptOfPasscodeActivities.add(PassCodeActivity.class);
        exemptOfPasscodeActivities.add(RequestCredentialsActivity.class);
        // other activities may be exempted, if needed
    }

    /**
     *  Keeping a "low" positive value is the easiest way to prevent
     *  the pass code being requested on screen rotations.
     */
    private static final int PASS_CODE_TIMEOUT = 5000;

    private AppPreferences preferences;
    private int visibleActivitiesCounter;


    public PassCodeManager(AppPreferences preferences) {
        this.preferences = preferences;
    }

    public void onActivityCreated(Activity activity) {
        Window window = activity.getWindow();
        if (window != null) {
            if (isPassCodeEnabled() || deviceCredentialsAreEnabled(activity)) {
                window.addFlags(WindowManager.LayoutParams.FLAG_SECURE);
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
            }
        }
    }

    public void onActivityStarted(Activity activity) {
        Long timestamp = PreferenceManager.fromContext(activity).getLockTimestamp();
        if (!exemptOfPasscodeActivities.contains(activity.getClass()) && passCodeShouldBeRequested(timestamp)) {

            Intent i = new Intent(MainApp.getAppContext(), PassCodeActivity.class);
            i.setAction(PassCodeActivity.ACTION_CHECK);
            i.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            activity.startActivityForResult(i, PASSCODE_ACTIVITY);
        }

        if (!exemptOfPasscodeActivities.contains(activity.getClass()) &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && deviceCredentialsShouldBeRequested(timestamp, activity)) {
            Intent i = new Intent(MainApp.getAppContext(), RequestCredentialsActivity.class);
            i.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            activity.startActivityForResult(i, PASSCODE_ACTIVITY);
        }

        visibleActivitiesCounter++;    // keep it AFTER passCodeShouldBeRequested was checked
    }

    public void onActivityStopped(Activity activity) {
        if (visibleActivitiesCounter > 0) {
            visibleActivitiesCounter--;
        }
        setUnlockTimestamp(activity);
        PowerManager powerMgr = (PowerManager) activity.getSystemService(Context.POWER_SERVICE);
        if ((isPassCodeEnabled() || deviceCredentialsAreEnabled(activity)) && powerMgr != null
                && !powerMgr.isScreenOn()) {
            activity.moveTaskToBack(true);
        }
    }

    private void setUnlockTimestamp(Activity activity) {
        PreferenceManager.fromContext(activity).setLockTimestamp(System.currentTimeMillis());
    }

    private boolean passCodeShouldBeRequested(Long timestamp) {
        return (System.currentTimeMillis() - timestamp) > PASS_CODE_TIMEOUT &&
            visibleActivitiesCounter <= 0 && isPassCodeEnabled();
    }

    private boolean isPassCodeEnabled() {
        return SettingsActivity.LOCK_PASSCODE.equals(preferences.getLockPreference());
    }

    private boolean deviceCredentialsShouldBeRequested(Long timestamp, Activity activity) {
        return (System.currentTimeMillis() - timestamp) > PASS_CODE_TIMEOUT && visibleActivitiesCounter <= 0 &&
            deviceCredentialsAreEnabled(activity);
    }

    private boolean deviceCredentialsAreEnabled(Activity activity) {
        return SettingsActivity.LOCK_DEVICE_CREDENTIALS.equals(preferences.getLockPreference())
                || Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                        (preferences.isFingerprintUnlockEnabled()
                                && DeviceCredentialUtils.areCredentialsAvailable(activity));
    }
}
