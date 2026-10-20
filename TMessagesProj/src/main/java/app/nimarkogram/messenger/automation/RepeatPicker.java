package app.nimarkogram.messenger.automation;

import android.content.Context;
import android.text.InputType;
import android.widget.EditText;
import android.widget.FrameLayout;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import java.util.function.IntConsumer;

public final class RepeatPicker {
    private RepeatPicker() {}
    public static String label(int seconds) {
        return LocaleController.formatString(seconds % 3600 == 0 ? R.string.LinkiRepeatHours : R.string.LinkiRepeatMinutes,
                seconds % 3600 == 0 ? seconds / 3600 : seconds / 60);
    }
    public static void show(Context context, boolean hours, IntConsumer selected) {
        EditText input = new EditText(context);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setSingleLine(true); input.setText("1");
        input.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        input.setFilters(new android.text.InputFilter[] { new android.text.InputFilter.LengthFilter(5) });
        FrameLayout container = new FrameLayout(context);
        container.setPadding(AndroidUtilities.dp(22), AndroidUtilities.dp(8), AndroidUtilities.dp(22), AndroidUtilities.dp(8));
        container.addView(input, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(LocaleController.getString(hours ? R.string.LinkiRepeatInHours : R.string.LinkiRepeatInMinutes));
        builder.setMessage(LocaleController.getString(R.string.LinkiRepeatInfo)); builder.setView(container);
        builder.setPositiveButton(LocaleController.getString(R.string.OK), null);
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        android.app.AlarmManager alarms = (android.app.AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (android.os.Build.VERSION.SDK_INT >= 31 && !alarms.canScheduleExactAlarms()) {
            builder.setNeutralButton(LocaleController.getString(R.string.LinkiRepeatAllowExact), (d, w) -> {
                try { context.startActivity(new android.content.Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        android.net.Uri.parse("package:" + context.getPackageName()))); }
                catch (Exception e) { org.telegram.messenger.FileLog.e(e); }
            });
        }
        AlertDialog dialog = builder.create(); dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try {
                int amount = Integer.parseInt(input.getText().toString());
                long seconds = amount * (hours ? 3600L : 60L);
                if (amount <= 0 || seconds > 31 * 86400L) throw new IllegalArgumentException();
                selected.accept(-(int) seconds); dialog.dismiss();
            } catch (Exception e) { input.setError(LocaleController.getString(R.string.LinkiRepeatInvalid)); }
        });
    }
}
