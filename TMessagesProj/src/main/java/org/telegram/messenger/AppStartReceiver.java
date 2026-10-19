/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.messenger;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class AppStartReceiver extends BroadcastReceiver {

    public void onReceive(Context context, Intent intent) {
        // A removed launcher variant may have been the only enabled alias.
        if (intent != null && Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) {
            org.telegram.ui.LauncherIconController.tryFixLauncherIconIfNeeded();
            return;
        }
        if (intent != null && Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            AndroidUtilities.runOnUIThread(() -> {
                SharedConfig.loadConfig();
                if (SharedConfig.passcodeHash.length() > 0) {
                    SharedConfig.appLocked = true;
                    SharedConfig.saveConfig();
                }
                ApplicationLoader.startPushService();
            });
        }
    }
}
