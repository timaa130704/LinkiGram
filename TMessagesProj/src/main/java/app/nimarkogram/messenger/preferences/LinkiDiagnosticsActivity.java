package app.nimarkogram.messenger.preferences;

import android.content.SharedPreferences;
import android.view.View;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;

import java.util.ArrayList;

import app.exteraless.player.LinerApi;
import app.nimarkogram.messenger.NimarkoConfig;

/** Read-only, local connection diagnostics. No addresses or credentials are displayed. */
public final class LinkiDiagnosticsActivity extends BasePreferencesActivity {
    private static final int ID_REFRESH = 1;

    @Override public String getTitle() { return LocaleController.getString(R.string.NM_Diagnostics); }

    @Override
    public void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        int account = getCurrentAccount();
        int state = ConnectionsManager.getInstance(account).getConnectionState();
        String connection = state == ConnectionsManager.ConnectionStateConnected ? "Подключено"
                : state == ConnectionsManager.ConnectionStateUpdating ? "Обновление соединения"
                : state == ConnectionsManager.ConnectionStateConnecting ? "Подключение…" : "Нет соединения";
        SharedPreferences main = ApplicationLoader.applicationContext
                .getSharedPreferences("mainconfig", android.content.Context.MODE_PRIVATE);
        boolean proxy = main.getBoolean("proxy_enabled", false);
        items.add(UItem.asHeader("Telegram"));
        items.add(UItem.asShadow("Соединение: " + connection));
        items.add(UItem.asShadow("Прокси: " + (proxy ? "включён" : "выключен")));
        items.add(UItem.asHeader("Тексты песен"));
        items.add(UItem.asShadow("Онлайн-поиск: " + (NimarkoConfig.lrclibAllowed() ? "включён" : "выключен")));
        items.add(UItem.asShadow("Аккаунт Liner: " + (LinerApi.signedIn() ? "подключён" : "не подключён")));
        items.add(UItem.asShadow("Эта проверка показывает локальное состояние. Она не отправляет диагностические данные."));
        items.add(UItem.asButton(ID_REFRESH, R.drawable.msg_retry_solar, "Обновить"));
    }

    @Override public void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_REFRESH && listView != null && listView.adapter != null) listView.adapter.update(true);
    }
}
