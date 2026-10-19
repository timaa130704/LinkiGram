package app.nimarkogram.messenger.drawer;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.IntPredicate;

import app.nimarkogram.messenger.NimarkoConfig;
import app.nimarkogram.messenger.plugins.ui.PluginsActivity;
import app.nimarkogram.messenger.preferences.AyuMomentsPreferencesActivity;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.ActionIntroActivity;
import org.telegram.ui.CallLogActivity;
import org.telegram.ui.ChannelCreateActivity;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.ContactsActivity;
import org.telegram.ui.DialogsActivity;
import org.telegram.ui.GroupCreateActivity;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.ProfileActivity;
import org.telegram.ui.SettingsActivity;

import org.telegram.ui.WebAppDisclaimerAlert;
import org.telegram.ui.bots.BotWebViewSheet;

/**
 * Резолвер пунктов главного меню: id из {@link MainMenuLayout} → иконка, подпись и действие.
 *
 * exteraGram: {@code com/exteragram/messenger/utils/chats/MainMenuHelper.java} (12.9.0, 819 строк).
 * Из {@code MenuContext} убран {@code pluginContextData}; пункты FEED/BROWSER/QR и
 * инъекция пунктов плагинов убраны — этих зависимостей в LinkiGram нет.
 */
public final class MainMenuHelper {

    private MainMenuHelper() {
    }

    /**
     * Контекст резолва. exteraGram: {@code MainMenuHelper.MenuContext} — record из четырёх полей,
     * из них {@code pluginContextData} выброшен вместе с движком плагинов.
     *
     * @param archiveClick своё действие для «Архива»: в списке чатов архив открывается
     *                     не новым фрагментом, а прокруткой
     */
    public record MenuContext(int currentAccount, BaseFragment fragment, Runnable archiveClick) {
    }

    public record MenuItemInfo(int iconRes, CharSequence text, Runnable onClick, Runnable onLongClick) {
    }

    public record AttachMenuBotInfo(int iconRes, CharSequence text, TLRPC.TL_attachMenuBot bot,
                                    Runnable onClick, Runnable onLongClick) {
    }

    public static MenuContext createMenuContext(int currentAccount, BaseFragment fragment) {
        return new MenuContext(currentAccount, fragment, null);
    }

    public static MenuContext createMenuContext(int currentAccount, BaseFragment fragment, Runnable archiveClick) {
        return new MenuContext(currentAccount, fragment, archiveClick);
    }

    // ---- меню «⋮» ----

    public static void addConfiguredItemOptions(ItemOptions io, MenuContext ctx) {
        addConfiguredItemOptions(io, ctx, id -> false);
    }

    /**
     * Разделитель ставится «отложенно»: висящие в начале и в конце схлопываются.
     *
     * @param skip пункты, которые в этом конкретном меню дублируют что-то другое
     */
    public static void addConfiguredItemOptions(ItemOptions io, MenuContext ctx, IntPredicate skip) {
        boolean hasAnyItem = false;
        boolean dividerPending = false;
        final List<Integer> layout = MainMenuLayout.getLayout();
        for (int i = 0; i < layout.size(); i++) {
            final Integer id = layout.get(i);
            if (id == null) {
                continue;
            }
            if (id == MainMenuItem.DIVIDER.getId()) {
                if (hasAnyItem) {
                    dividerPending = true;
                }
                continue;
            }
            if (skip.test(id)) {
                continue;
            }
            if (dividerPending) {
                io.addGap();
                dividerPending = false;
            }
            if (addConfiguredItemOption(io, ctx, id)) {
                hasAnyItem = true;
            }
        }
    }

    private static boolean addConfiguredItemOption(ItemOptions io, MenuContext ctx, int id) {
        final MainMenuItem item = MainMenuItem.getById(id);
        if (item == null) {
            return false;
        }
        if (item == MainMenuItem.ARCHIVE && !hasArchivedChats(ctx.currentAccount())) {
            return false;
        }
        if (item == MainMenuItem.BOTS) {
            return addAttachMenuBotMenuItems(io, ctx);
        }
        if (item == MainMenuItem.PLUGINS) {
            return addPluginsMenuItem(io, ctx);
        }
        final MenuItemInfo info = resolveMenuItem(id, ctx);
        if (info == null || info.onClick() == null) {
            return false;
        }
        io.add(info.iconRes(), info.text(), info.onClick());
        bindLongClick(io, info.onLongClick());
        return true;
    }

    /**
     * Пункт «Плагины». exteraGram открывал здесь swipe-back подменю с пунктами плагинов
     * ({@code app.exteraless.plugins.menus.MenuInjector}); такой инъекции в LinkiGram нет,
     * поэтому пункт ведёт прямо в {@link PluginsActivity}.
     */
    public static boolean addPluginsMenuItem(ItemOptions io, MenuContext ctx) {
        final MenuItemInfo info = resolveMenuItem(MainMenuItem.PLUGINS.getId(), ctx);
        if (info == null || info.onClick() == null) {
            return false;
        }
        io.add(info.iconRes(), info.text(), info.onClick());
        return true;
    }

    private static boolean addAttachMenuBotMenuItems(ItemOptions io, MenuContext ctx) {
        final List<AttachMenuBotInfo> bots = getAttachMenuBotItems(ctx);
        if (bots.isEmpty()) {
            return false;
        }
        for (AttachMenuBotInfo bot : bots) {
            io.addBot(bot.bot(), bot.onClick(), bot.onLongClick());
        }
        return true;
    }

    /** Длинное нажатие закрывает меню. */
    private static void bindLongClick(ItemOptions io, Runnable onLongClick) {
        if (onLongClick == null) {
            return;
        }
        final ActionBarMenuSubItem last = io.getLast();
        if (last == null) {
            return;
        }
        last.setOnLongClickListener(v -> {
            io.dismiss();
            onLongClick.run();
            return true;
        });
    }

    // ---- шторка ----

    public static List<MenuItemInfo> resolveDrawerMenuItems(int id, MenuContext ctx) {
        final MainMenuItem item = MainMenuItem.getById(id);
        if (item == null) {
            return Collections.emptyList();
        }
        if (item == MainMenuItem.BOTS) {
            return resolveDrawerBotMenuItems(ctx);
        }
        if (item == MainMenuItem.PLUGINS) {
            return resolveDrawerPluginMenuItems(ctx);
        }
        if (item == MainMenuItem.ARCHIVE && !hasArchivedChats(ctx.currentAccount())) {
            // Пустой архив в шторке не показывается.
            return Collections.emptyList();
        }
        final MenuItemInfo info = resolveMenuItem(id, ctx);
        return info == null ? Collections.emptyList() : Collections.singletonList(info);
    }

    private static List<MenuItemInfo> resolveDrawerPluginMenuItems(MenuContext ctx) {
        final MenuItemInfo info = resolveMenuItem(MainMenuItem.PLUGINS.getId(), ctx);
        return info == null ? Collections.emptyList() : Collections.singletonList(info);
    }

    private static List<MenuItemInfo> resolveDrawerBotMenuItems(MenuContext ctx) {
        final List<AttachMenuBotInfo> bots = getAttachMenuBotItems(ctx);
        if (bots.isEmpty()) {
            return Collections.emptyList();
        }
        final ArrayList<MenuItemInfo> result = new ArrayList<>(bots.size());
        for (AttachMenuBotInfo bot : bots) {
            result.add(new MenuItemInfo(bot.iconRes(), bot.text(), bot.onClick(), bot.onLongClick()));
        }
        return result;
    }

    // ---- сам резолвер ----

    public static MenuItemInfo resolveMenuItem(int id, MenuContext ctx) {
        final MainMenuItem item = MainMenuItem.getById(id);
        if (item == null || ctx.fragment() == null) {
            return null;
        }
        final int currentAccount = ctx.currentAccount();
        final BaseFragment fragment = ctx.fragment();
        switch (item) {
            case PROFILE:
                return new MenuItemInfo(R.drawable.left_status_profile, LocaleController.getString(R.string.MyProfile), () -> {
                    final Bundle args = new Bundle();
                    args.putLong("user_id", UserConfig.getInstance(currentAccount).getClientUserId());
                    args.putBoolean("my_profile", true);
                    fragment.presentFragment(new ProfileActivity(args));
                }, null);
            case ARCHIVE:
                return new MenuItemInfo(R.drawable.msg_archive, LocaleController.getString(R.string.ArchivedChats),
                        ctx.archiveClick() != null ? ctx.archiveClick() : () -> {
                            final Bundle args = new Bundle();
                            args.putInt("folderId", 1);
                            fragment.presentFragment(new DialogsActivity(args));
                        }, null);
            case NEW_GROUP:
                return new MenuItemInfo(R.drawable.msg_groups, LocaleController.getString(R.string.NewGroup),
                        () -> fragment.presentFragment(new GroupCreateActivity(new Bundle())), null);
            case CONTACTS:
                return new MenuItemInfo(R.drawable.msg_contacts, LocaleController.getString(R.string.Contacts), () -> {
                    final Bundle args = new Bundle();
                    args.putBoolean("needPhonebook", true);
                    args.putBoolean("needFinishFragment", false);
                    fragment.presentFragment(new ContactsActivity(args));
                }, null);
            case CALLS:
                return new MenuItemInfo(R.drawable.msg_calls, LocaleController.getString(R.string.Calls),
                        () -> fragment.presentFragment(new CallLogActivity()), null);
            case NEW_CHANNEL:
                return new MenuItemInfo(R.drawable.msg_channel, LocaleController.getString(R.string.NewChannel),
                        () -> presentChannelCreate(fragment), null);
            case SAVED:
                return new MenuItemInfo(R.drawable.msg_saved, LocaleController.getString(R.string.SavedMessages), () -> {
                    final Bundle args = new Bundle();
                    args.putLong("user_id", UserConfig.getInstance(currentAccount).getClientUserId());
                    fragment.presentFragment(new ChatActivity(args));
                }, null);
            case SETTINGS:
                return new MenuItemInfo(R.drawable.msg_settings, LocaleController.getString(R.string.Settings),
                        () -> fragment.presentFragment(new SettingsActivity()), null);
            case PLUGINS:
                return new MenuItemInfo(R.drawable.msg_plugins, LocaleController.getString(R.string.NimarkoPlugins),
                        () -> fragment.presentFragment(new PluginsActivity()), null);
            case GHOST_MODE:
                return new MenuItemInfo(R.drawable.ghost_solar, ghostModeTitle(),
                        () -> toggleGhostMode(fragment, currentAccount),
                        () -> fragment.presentFragment(new AyuMomentsPreferencesActivity()));
            case RECENT_CHATS:
                return new MenuItemInfo(R.drawable.msg_recent, LocaleController.getString(R.string.RecentChats),
                        () -> showRecentChats(fragment), null);
            default:
                return null;
        }
    }

    /**
     * exteraGram показывал список недавно открытых чатов ({@code BackButtonMenuRecent}).
     * В LinkiGram накопителя недавних чатов нет, поэтому пункт сообщает, что список пуст —
     * тот же результат, что даёт exteraGram при {@code !hasRecentDialogs()}.
     */
    private static void showRecentChats(BaseFragment fragment) {
        BulletinFactory.of(fragment).createErrorBulletin(LocaleController.getString(R.string.NoRecent)).show();
    }

    /** Заголовок зависит от состояния: пункт и показывает его, и переключает. */
    private static CharSequence ghostModeTitle() {
        return LocaleController.getString(NimarkoConfig.isGhostModeActive()
                ? R.string.DisableGhostMode
                : R.string.EnableGhostMode);
    }

    private static void toggleGhostMode(BaseFragment fragment, int currentAccount) {
        final boolean wasActive = NimarkoConfig.isGhostModeActive();
        NimarkoConfig.setGhostMode(!wasActive);
        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.mainUserInfoChanged);
        BulletinFactory.of(fragment)
                .createSuccessBulletin(LocaleController.getString(
                        wasActive ? R.string.GhostModeDisabled : R.string.GhostModeEnabled))
                .show();
    }

    private static void presentChannelCreate(BaseFragment fragment) {
        final SharedPreferences prefs = MessagesController.getGlobalMainSettings();
        if (BuildVars.DEBUG_VERSION || !prefs.getBoolean("channel_intro", false)) {
            fragment.presentFragment(new ActionIntroActivity(ActionIntroActivity.ACTION_TYPE_CHANNEL_CREATE));
            prefs.edit().putBoolean("channel_intro", true).apply();
        } else {
            final Bundle args = new Bundle();
            args.putInt("step", 0);
            fragment.presentFragment(new ChannelCreateActivity(args));
        }
    }

    /**
     * Иконка и подпись пункта без действия — для экрана-редактора раскладки,
     * где фрагмента-получателя ещё нет. {@link MainMenuItem#DIVIDER} возвращает
     * {@code null}: подпись разделителя — дело редактора (строки {@code MainMenuDivider}
     * в ресурсах форка пока нет).
     */
    public static MenuItemInfo describeItem(int id) {
        final MainMenuItem item = MainMenuItem.getById(id);
        if (item == null) {
            return null;
        }
        return switch (item) {
            case PROFILE -> new MenuItemInfo(R.drawable.left_status_profile, LocaleController.getString(R.string.MyProfile), null, null);
            case ARCHIVE -> new MenuItemInfo(R.drawable.msg_archive, LocaleController.getString(R.string.ArchivedChats), null, null);
            case BOTS -> new MenuItemInfo(R.drawable.msg_bot, LocaleController.getString(R.string.SearchApps), null, null);
            case NEW_GROUP -> new MenuItemInfo(R.drawable.msg_groups, LocaleController.getString(R.string.NewGroup), null, null);
            case CONTACTS -> new MenuItemInfo(R.drawable.msg_contacts, LocaleController.getString(R.string.Contacts), null, null);
            case NEW_CHANNEL -> new MenuItemInfo(R.drawable.msg_channel, LocaleController.getString(R.string.NewChannel), null, null);
            case CALLS -> new MenuItemInfo(R.drawable.msg_calls, LocaleController.getString(R.string.Calls), null, null);
            case SAVED -> new MenuItemInfo(R.drawable.msg_saved, LocaleController.getString(R.string.SavedMessages), null, null);
            case SETTINGS -> new MenuItemInfo(R.drawable.msg_settings, LocaleController.getString(R.string.Settings), null, null);
            case PLUGINS -> new MenuItemInfo(R.drawable.msg_plugins, LocaleController.getString(R.string.NimarkoPlugins), null, null);
            case GHOST_MODE -> new MenuItemInfo(R.drawable.ghost_solar, ghostModeTitle(), null, null);
            case RECENT_CHATS -> new MenuItemInfo(R.drawable.msg_recent, LocaleController.getString(R.string.RecentChats), null, null);
            default -> null;
        };
    }

    // ---- attach-menu-боты ----

    public static List<AttachMenuBotInfo> getAttachMenuBotItems(MenuContext ctx) {
        final BaseFragment fragment = ctx.fragment();
        final LaunchActivity launchActivity = findLaunchActivity(fragment);
        final TLRPC.TL_attachMenuBots attachMenuBots =
                MediaDataController.getInstance(ctx.currentAccount()).getAttachMenuBots();
        if (fragment == null || launchActivity == null || attachMenuBots == null
                || attachMenuBots.bots == null || attachMenuBots.bots.isEmpty()) {
            return Collections.emptyList();
        }
        final ArrayList<AttachMenuBotInfo> result = new ArrayList<>();
        for (TLRPC.TL_attachMenuBot bot : attachMenuBots.bots) {
            if (!bot.show_in_side_menu) {
                continue;
            }
            result.add(new AttachMenuBotInfo(
                    R.drawable.msg_bot,
                    bot.short_name,
                    bot,
                    createAttachMenuBotClickAction(ctx, bot, launchActivity),
                    () -> BotWebViewSheet.deleteBot(ctx.currentAccount(), bot.bot_id, null)));
        }
        return result;
    }

    private static Runnable createAttachMenuBotClickAction(MenuContext ctx, TLRPC.TL_attachMenuBot bot, LaunchActivity launchActivity) {
        return () -> {
            if (bot.inactive || bot.side_menu_disclaimer_needed) {
                final android.content.Context context = ctx.fragment() != null && ctx.fragment().getContext() != null
                        ? ctx.fragment().getContext() : launchActivity;
                WebAppDisclaimerAlert.show(context, (Utilities.Callback<Boolean>) allowSendMessage -> {
                    final TLRPC.TL_messages_toggleBotInAttachMenu req = new TLRPC.TL_messages_toggleBotInAttachMenu();
                    req.bot = MessagesController.getInstance(ctx.currentAccount()).getInputUser(bot.bot_id);
                    req.enabled = true;
                    req.write_allowed = true;
                    ConnectionsManager.getInstance(ctx.currentAccount()).sendRequest(req, (response, error) ->
                            AndroidUtilities.runOnUIThread(() -> {
                                bot.side_menu_disclaimer_needed = false;
                                bot.inactive = false;
                                LaunchActivity.showAttachMenuBot(launchActivity, ctx.currentAccount(), bot, null, true);
                                MediaDataController.getInstance(ctx.currentAccount()).updateAttachMenuBotsInCache();
                            }), ConnectionsManager.RequestFlagInvokeAfter | ConnectionsManager.RequestFlagFailOnServerErrors);
                }, null, null);
            } else {
                LaunchActivity.showAttachMenuBot(launchActivity, ctx.currentAccount(), bot, null, true);
            }
        };
    }

    private static LaunchActivity findLaunchActivity(BaseFragment fragment) {
        if (fragment == null) {
            return LaunchActivity.instance;
        }
        final Activity activity = AndroidUtilities.findActivity(
                fragment.getContext() != null ? fragment.getContext() : fragment.getParentActivity());
        return activity instanceof LaunchActivity ? (LaunchActivity) activity : LaunchActivity.instance;
    }

    /**
     * exteraGram: {@code ChatUtils.hasArchivedChats()} → {@code MessagesController.hasArchivedChatsActual()}.
     * У нас поле {@code hasArchivedChats} приватное, поэтому смотрим сам список папки 1.
     */
    public static boolean hasArchivedChats(int currentAccount) {
        try {
            final List<TLRPC.Dialog> archived = MessagesController.getInstance(currentAccount).getDialogs(1);
            return archived != null && !archived.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }
}