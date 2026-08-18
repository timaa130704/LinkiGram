package app.nimarkogram.messenger.badges;

import android.text.TextUtils;

import app.nimarkogram.messenger.api.dto.BadgeDTO;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.BulletinFactory;

public final class BadgeUi {

    private BadgeUi() {}

    public static CharSequence accessibilityLabel(BadgeDTO badge) {
        if (badge == null) return null;
        CharSequence title = LocaleController.getString(R.string.NM_ProfileBadge);
        return TextUtils.isEmpty(badge.getText()) ? title : title + ": " + badge.getText();
    }
    public static void showBulletin(int account, BadgeDTO badge) {
        if (badge == null) return;
        CharSequence text = TextUtils.isEmpty(badge.getText())
                ? LocaleController.getString(R.string.NM_ProfileBadge) : badge.getText();
        org.telegram.messenger.FileLog.d("BadgeUi.showBulletin res=" + badge.getImageRes() + " doc=" + badge.getDocumentId() + " text=" + text);
        if (badge.getImageRes() != 0) {
            try {
                android.graphics.drawable.Drawable d = createBulletinImageDrawable(badge.getImageRes());
                if (d != null) BulletinFactory.global().createSimpleBulletin(d, text).show();
            } catch (Throwable ignored) {}
            return;
        }
        if (badge.getDocumentId() == 0L) return;
        TLRPC.Document cached = AnimatedEmojiDrawable.findDocument(account, badge.getDocumentId());
        if (cached != null) {
            showBulletin(cached, text);
            return;
        }
        AnimatedEmojiDrawable.getDocumentFetcher(account).fetchDocument(badge.getDocumentId(), document -> {
            if (document != null) AndroidUtilities.runOnUIThread(() -> showBulletin(document, text));
        });
    }

    private static void showBulletin(TLRPC.Document document, CharSequence text) {
        if (document == null) return;
        BulletinFactory.global().createEmojiBulletin(document, text).show();
    }

    public static android.graphics.drawable.Drawable createBadgeImageDrawable(int imageRes) {
        try {
            android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeResource(
                    ApplicationLoader.applicationContext.getResources(), imageRes);
            if (bitmap == null) return null;
            int size = AndroidUtilities.dp(24);
            android.graphics.Bitmap scaled = android.graphics.Bitmap.createScaledBitmap(bitmap, size, size, true);
            if (scaled != bitmap) bitmap.recycle();
            return new android.graphics.drawable.BitmapDrawable(
                    ApplicationLoader.applicationContext.getResources(), scaled);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static android.graphics.drawable.Drawable createBulletinImageDrawable(int imageRes) {
        try {
            android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeResource(
                    ApplicationLoader.applicationContext.getResources(), imageRes);
            if (bitmap == null) return null;
            int size = AndroidUtilities.dp(48);
            android.graphics.Bitmap scaled = android.graphics.Bitmap.createScaledBitmap(bitmap, size, size, true);
            if (scaled != bitmap) bitmap.recycle();
            return new android.graphics.drawable.BitmapDrawable(
                    ApplicationLoader.applicationContext.getResources(), scaled);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
