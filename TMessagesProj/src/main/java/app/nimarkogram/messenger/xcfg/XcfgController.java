package app.nimarkogram.messenger.xcfg;

/*
 * X-CFG: контроллер встроенного способа обхода.
 * Порт XCfgPlugin (автор @x_vare, GPL) из плагина в клиент LinkiGram.
 *
 * Отличие от плагина: коды логина не только дедуплицируются по значению,
 * но и по id сообщения, а история опрашивается только для сообщений,
 * пришедших ПОСЛЕ отправки /start — это лечит вечный цикл на устаревших кодах.
 */
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.browser.Browser;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.RequestDelegate;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.LaunchActivity;
import org.telegram.utils.proxy.ProxySettings;

import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class XcfgController implements NotificationCenter.NotificationCenterDelegate {

    private static final Pattern CODE_RE = Pattern.compile("XCFG-LOGIN:([0-9A-Fa-f]{12})");

    // Логотип X-CFG (белый X на прозрачном, 96x96 PNG) — из плагина, перекрашивается под цвет вкладок
    static final String ICON_B64 = "iVBORw0KGgoAAAANSUhEUgAAAGAAAABgCAYAAADimHc4AAALeElEQVR42u1de7BVZRX/rX3OvVwuAZYKpBYmiZpaQaM1ONpMOY6S"
            + "OUF/OI5DUeCUlg+mh9QdrUxTU8whC8igSSQzZcLpIeKMliY2YyKj4iT2oEsaTYSXh8K95/Hrj7M+WG65l/PY51vnnGnP7Dkwc8/Z"
            + "a33fWr9vvTfw/8v1EpICQJzpoIgw6gMrfPsufmSeW35BvDTgWAC9AAoAEgC17so4ABMa0KIuABtFZAvJnIiUmrzROREpkfwAgAsA"
            + "7FO+Y15lAKMAbMoDmANggRKSq5UfAD0AuhsgpBvAapKfAVAiSREpN2nx8/qMYwD8EMDpAEp18N0QGXrvBjAPJCeRfJCVq8T6rlKd"
            + "d4HkEMnXSc7XRepqFsSRzOvnrUr3XqUh5r1Xn33lftgl+T6SW0kWSQ7WsZDlBu4hJWgzyZOatQnhN0merxte1Lsc8R5UXh8gOZak"
            + "gGS3EnaFElRoQBPqvQJhPybZTbIry0OZZE4lfzLJDfqsQmQew/P+SvKU/UJBMlECe0iu0j8a0h2LdQU42kXyk1lqgYGehOQKwx8j"
            + "8zdEch/JeW/iTw8nkJxK8m9OEhIW5Vm1zPbT1eAGBA2fowtQjKzhFmZXqIbnSSbDYeTF+oUhBygKhN6hRObeRGjtVg9ITiH5vJP0"
            + "F4xgTR5WsAwUjSa53FFVg6UwqxEtUH669F6eOmtiXUHbdpG84JDQaiRmMsmNzhLzPMl3KobnGoCei4256QU93wuLf0jjwkDRLJJ7"
            + "nDFzibXf2wx6wvN+T/JtqpFJtVZDUN1FjlZRSQXg/FqsImU0T3KUI5QGod1GckbNVp3xGI8k+YQzFD1BckI4o6oRHv33fON9lp00"
            + "+IqqoWcEKDqb5Hbd0aIDI2WSN1fDiIGe95D8h363SB+ncjXJt1QjONVI0zeNVJYdVHmA5Ex7uB7CilvtqLVlDauc2rAvY5gaS3Kd"
            + "s4P2OMmJw1lFRlg+r98pOJxbIZY2NzNv3qj1aST/7QRFQa1vVIF4AxQZGk8muUW/U3CicakxBCSrWEqXfn7ZCYrCpu8keU5q0QOz"
            + "vSTvd7LawmZvJHlsMJ2zDOUmeveS/IWzXb1erTMJ2qA0XqqxntgOVzCZd5L8WNNyGobRUzWk6qHmYRP6lJbRBh77Hc4oS9ONdZuc"
            + "dbj3l5jcgYeDZh2ciSR/66yVj5A8QjWyeelNYxV1afLEk+mH1eS8Sv+/z8lE3k7y9HpNTqkTiooApgD4JYBTtKKiC4iW1IYmtdcA"
            + "OAPAcZrgz0WkoQAgD+ArInJbSPjXWusj9UKRiAyRvBDACq2MQOTyDhr6Gbm4LAjcfQDmAXhdi8tqruaod8EKJLtE5F4AP9LfKdZR"
            + "U9RQTZOWlJSdFv9FANeIyO5KkVt9pTTSSKJbF3wCgN8AmK6bkEfnXmW9CwDmi8jPAhrU+4NJA3WNJQA5EdkGoA/ALv29UocuPpW3"
            + "PICfaDFZOA8RfQP0KioUrQVwi/5euUM3oKTQswHAt0VkEEC50Sq+JIPq3rJmepYC+J0SWejAxU8A7ADQJyLbVPAa1vYkgxLrAEXb"
            + "AVyrROY7CIpCLWcCYImIrM0CepCx2VgkmReRxwHcoDBUimwVNesKhsUjAG5V46OcVW1/kmGjAdUZWa5WUXcHQFFRnbtt6nANqMmZ"
            + "mXYnGXZ7lJS4nQCuA7BVN6HYAdBzE4BnFHoyhdYk45ab4KA9rZuw19jP7XYFh+seAMuCiZ11W1EzQgcllZSVGqtpxwO5oNr7HIBv"
            + "iMi+RrzdqBugRFLt5GsBbFZJKraRt5sAGARwvYi8pN5uU+hPmtT9F6yivwBYiEqwqh2giKZlaRmA+9WwaJrwNDN6GaDoVwDuahMo"
            + "Kqq2PqnmNOqNcrZK62mosDua5J+cEji1Jlh2kDy3mf1qsTQAipt5EXkZwNcADJgwciuanAJgUfB2RaTQ1huQgqJHASxRKCq3mJcc"
            + "vN2HASwO3m7HRLJMk9wErXBrJSgKnZL/YqV5G5nW9LTQJoSylrNIvuxQYTdSRVuR5OeaXlbiBEE2YNctIo8B+IEZi8AWcLjuBnCX"
            + "Qk+ppYZoNAGKciTHONbypMsJN5Oc2rHQM0Kx7/tNDX/BoRmwpMXGs2OZnN4QZL3kLhHZqFFGOHjJIdwwAOAFT4FMvOYCmdzBzx28"
            + "5BBemApgAckezWcknQ4/B6vpP4Hkn41JGLvkfR/Jz1rPvVMXP5S495A8Ues6g2l6kelqKTkcxFt5YIBGR/oAYiR+Icl/kjxD/9+j"
            + "tvcyp272YIXdp/0PuY6DIiPpnyC5Wxn+Ncm36vCKhORxJJ9xME2DFVYkeZmHMxYrIvoOExHdo5/X6d/06ufHdYNie8khHPEfkh/y"
            + "NEubNSYsT3KxgRjb1vPRVLfLbU59XkHr1mlXaL7tocjg/ixWBmbYuRPhAPwDK7MUwniESU7d+aHdqEhyYdtDkVn8d5HcdJD+LesB"
            + "36B/O0Y/zzTd+QUHKNpuNLOrXU3OINGrRpDmoBGv8sBQjl7n7nzbGD6p7lEDLWL1zK1iTFhg+Ck9qHOsTDoZ7xSws03X17edg2YW"
            + "/90kX6yiddQyvFglLhzI0zRR4mEVBSPhvLaBIgM9PSRX1iC9Aev3mFFlAYouN1MHPaBoAw9M7sq3i/R/muRrNQ7+Cww/rVUUiUJR"
            + "7yHOEUYYPfN9hcbWNU3N4k810DNUp9TdqR5yj/7mSZo4iZ07sBNQZrUsFBnpGEfyngZiOjYsMCcFRfPNrGmPgN0mklNaLmCXGux0"
            + "qS5eI3hthx5N0d/vUTha4eAlW61bpWdc61hFqRTjKxmNCQtQdLcyPNrEk551OA9s7mDeSJO7PKyekGRfk+HC2AGuc1NQdKEe8EUn"
            + "KLJT3vOtcvB+oQnTUwLDfyf53hCwU03wDtg9YAJ2OW/omaaFVs2YUJhmuFs17nCSTzoG7Eokr3SDIgM9ozKGnuEYHiR5eQqKzjG5"
            + "g9hQVGJlQNQ0Fygy0HOVMRvLTcbefpLTU7mDPueZRQ+pNsYL2JnF/7AWNsUYjjpkhqGON6bgYSTXOkIRzfi05pumIWGtudx1ERm3"
            + "2PvVkMzXzxkRBeFgpul/TYFBPpbD1We83XLkCOWrJD+SgqIvGgew5DQh/ch6oKiWwFJe+4DP1PeOlbXCTCJW8ZVQeencUTjQ/BHG"
            + "x6xB/G7MPCoV1mcBuFp7yZLMocg0WBzGyrA8z/qdpeoPJCmtnM5KgRUd0pghdzAzc9M0NTm3z9kBeoqVt+DBqrrZhHkHSf7Hpu+o"
            + "MM03a4drhiarYzMXXrg2wAOTabuHeWlDjuRPnQJ24Xl3GHqSrKyew0k+5jSZNjzvOybjJiMUgB1P8iUHWm3A7qKGoSgFPYucRtcP"
            + "mmKp8YeyMvjGOqTXHIp9AxQ9R/L4hkxTg6szWWleLka2s0Ngr5/kadVko1KVeLc7pzHvNLGrpF6rZyLJPzrF39MFs9011qIeQ79i"
            + "3+CPXFLPC3wC9ORIftfJ6glStLKeZLiBorON4eBR7LuF5Mk1QZGBnvMcoYckXzA52Fwd51coe/+WsxDdawQ6qVZ93+4IPcGSmN3g"
            + "qwwDL0cYC86j76BMckE1rzEUY0vf4uDt2gq5m+xZlFF3/isOAbuCGYPwwREFyhA729mEe1QP/0xi7Iavq1NSGRtSHzxYC9T+llHt"
            + "3z0alYkhJwAY0uAWI44F3g5gloisz2pcjDKboDKSYDWAc1EZRxbzXQNl5e/rInJzGPwqIrTdi2ONGx9TQuzzvtSMxohU/rqfftdA"
            + "+jW9YqR/JoDbzeCiJOKsnl4AD2mYe6+ZQ5plf3JO+fwUgGuMdCYRB4OMAbAewGUisoNkTkiKiJCVtz2PS70QQSK9jiQHYKsSlTRj"
            + "RpvRKAFwoj4z1ps30jDeLyK7Wq7ngJ3apY4q3qChzHsuAGPN6fEuMW/bKYydeP0PJ7MHLA25pOoAAAAASUVORK5CYII=";

    private static class LoginWait {
        final long startedAt = System.currentTimeMillis();
        final int srvNow;
        final long botId;
        final int uid;
        final Set<String> tried = new HashSet<>();
        final Set<Integer> seenMsgIds = new HashSet<>();
        volatile boolean codeDispatched;

        LoginWait(int srvNow, long botId, int uid) {
            this.srvNow = srvNow;
            this.botId = botId;
            this.uid = uid;
        }
    }

    private static volatile XcfgController instance;
    private static volatile Bitmap tabIcon;

    private int account = UserConfig.selectedAccount;
    private LoginWait wait;
    private AlertDialog busyDialog;
    private AlertDialog cabinetDialog;
    private volatile String watcherGen = "";
    private boolean observerAdded;

    private final ExecutorService pool = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "xcfg");
        t.setDaemon(true);
        return t;
    });

    public static XcfgController getInstance() {
        XcfgController i = instance;
        if (i == null) {
            synchronized (XcfgController.class) {
                if (instance == null) {
                    instance = new XcfgController();
                }
                i = instance;
            }
        }
        return i;
    }

    // ─────────────────────────── лог ───────────────────────────

    private static void fileLog(String msg) {
        try {
            Log.i("xcfg", String.valueOf(msg));
        } catch (Throwable ignore) {
        }
        try {
            File d = ApplicationLoader.applicationContext.getExternalFilesDir(null);
            if (d != null) {
                try (FileWriter w = new FileWriter(new File(d, "xcfg.log"), true)) {
                    w.write(android.text.format.DateFormat.format("kk:mm:ss", System.currentTimeMillis()) + " " + msg + "\n");
                }
            }
        } catch (Throwable ignore) {
        }
    }

    // ─────────────────────────── входная точка ───────────────────────────

    /** Клик по вкладке X-CFG или пункт меню: токен есть — кабинет, нет — вход. */
    public static void openMain(int account) {
        XcfgController c = getInstance();
        c.account = UserConfig.selectedAccount;
        if (!XcfgPrefs.token().isEmpty()) {
            c.showCabinet();
        } else {
            c.startLogin();
        }
    }

    /** Запуск из LaunchActivity: поднимаем VPN, если он был включён. */
    public static void autostart() {
        if (!XcfgPrefs.getBool("tg_vpn", false)) {
            return;
        }
        getInstance().pool.execute(() -> getInstance().autostartInternal());
    }

    public static Bitmap tabIcon(Context ctx) {
        Bitmap b = tabIcon;
        if (b != null) {
            return b;
        }
        try {
            byte[] data = Base64.decode(ICON_B64, Base64.DEFAULT);
            b = BitmapFactory.decodeByteArray(data, 0, data.length);
            tabIcon = b;
        } catch (Throwable t) {
            FileLog.e(t);
        }
        return b;
    }

    // ─────────────────────────── утилиты UI ───────────────────────────

    Activity activity() {
        BaseFragment f = LaunchActivity.getSafeLastFragment();
        if (f != null) {
            Activity a = f.getParentActivity();
            if (a != null) {
                return a;
            }
        }
        return null;
    }

    private void toast(String text) {
        AndroidUtilities.runOnUIThread(() -> {
            try {
                BaseFragment f = LaunchActivity.getSafeLastFragment();
                if (f != null) {
                    BulletinFactory.of(f).createSimpleBulletin(R.drawable.msg_info_filled_solar, text).show();
                }
            } catch (Throwable t) {
                FileLog.e(t);
            }
        });
    }

    private void showBusy(String title) {
        Activity a = activity();
        if (a == null) {
            return;
        }
        closeBusy();
        AlertDialog.Builder b = new AlertDialog.Builder(a, AlertDialog.ALERT_TYPE_SPINNER);
        b.setTitle(title);
        busyDialog = b.show();
    }

    private void closeBusy() {
        AlertDialog d = busyDialog;
        busyDialog = null;
        if (d != null) {
            try {
                d.dismiss();
            } catch (Exception ignore) {
            }
        }
    }

    private void closeCabinet() {
        AlertDialog d = cabinetDialog;
        cabinetDialog = null;
        if (d != null) {
            try {
                d.dismiss();
            } catch (Exception ignore) {
            }
        }
    }

    private void openUrl(String url) {
        Activity a = activity();
        if (a == null || url == null || url.isEmpty()) {
            return;
        }
        try {
            Browser.openUrl(a, url);
        } catch (Exception e) {
            FileLog.e(e);
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                a.startActivity(intent);
            } catch (Exception ignore) {
            }
        }
    }

    // ─────────────────────────── вход через бота ───────────────────────────

    private void startLogin() {
        AndroidUtilities.runOnUIThread(() -> {
            showBusy("Подключаю X-CFG…");
            pool.execute(this::loginWorker);
        });
    }

    private long cachedBotId() {
        try {
            Object o = MessagesController.getInstance(account).getUserOrChat(XcfgPrefs.BOT);
            if (o instanceof TLRPC.User) {
                return ((TLRPC.User) o).id;
            }
        } catch (Exception ignore) {
        }
        return 0;
    }

    private void loginWorker() {
        try {
            long botId = cachedBotId();
            if (botId != 0) {
                sendStart(botId);
                return;
            }
            // бота нет в кэше — спрашиваем Telegram по юзернейму
            TLRPC.TL_contacts_resolveUsername req = new TLRPC.TL_contacts_resolveUsername();
            req.username = XcfgPrefs.BOT;
            ConnectionsManager.getInstance(account).sendRequest(req, (RequestDelegate) (response, error) -> {
                try {
                    if (!(response instanceof TLRPC.TL_contacts_resolvedPeer) || ((TLRPC.TL_contacts_resolvedPeer) response).users.isEmpty()) {
                        throw new RuntimeException("bot not resolved: " + (error != null ? error.text : "empty"));
                    }
                    ArrayList<TLRPC.User> users = ((TLRPC.TL_contacts_resolvedPeer) response).users;
                    MessagesController.getInstance(account).putUsers(users, false);
                    sendStart(users.get(0).id);
                } catch (Exception e) {
                    fileLog("resolve bot: " + e);
                    AndroidUtilities.runOnUIThread(this::closeBusy);
                    toast("Не удалось найти бота @" + XcfgPrefs.BOT);
                }
            });
        } catch (Exception e) {
            fileLog("login worker: " + e);
            AndroidUtilities.runOnUIThread(this::closeBusy);
            toast("Не удалось подключиться к боту");
        }
    }

    private void sendStart(long botId) {
        int srvNow = ConnectionsManager.getInstance(account).getCurrentTime();
        wait = new LoginWait(srvNow, botId, XcfgPrefs.uid());
        addLoginObserver();
        fileLog("v" + XcfgPrefs.PLUGIN_VERSION + ": бот " + botId + ", отправляю /start plg");
        AndroidUtilities.runOnUIThread(() -> {
            try {
                // бот сам удалит это сообщение и пришлёт код
                SendMessagesHelper.getInstance(account).sendMessage(SendMessagesHelper.SendMessageParams.of("/start plg", botId));
            } catch (Exception e) {
                fileLog("send /start: " + e);
            }
        });
        AndroidUtilities.runOnUIThread(this::loginTimeoutCheck, XcfgPrefs.LOGIN_TIMEOUT_S * 1000L);
        AndroidUtilities.runOnUIThread(() -> pool.execute(this::pollHistory), 1200);
    }

    private void addLoginObserver() {
        AndroidUtilities.runOnUIThread(() -> {
            if (!observerAdded) {
                try {
                    NotificationCenter.getInstance(account).addObserver(XcfgController.this, NotificationCenter.didReceiveNewMessages);
                    observerAdded = true;
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        });
    }

    private void removeLoginObserver() {
        AndroidUtilities.runOnUIThread(() -> {
            if (observerAdded) {
                try {
                    NotificationCenter.getInstance(account).removeObserver(XcfgController.this, NotificationCenter.didReceiveNewMessages);
                } catch (Exception ignore) {
                }
                observerAdded = false;
            }
        });
    }

    private void loginTimeoutCheck() {
        LoginWait w = wait;
        if (w != null && System.currentTimeMillis() - w.startedAt >= (XcfgPrefs.LOGIN_TIMEOUT_S - 1) * 1000L) {
            clearWait();
            AndroidUtilities.runOnUIThread(this::closeBusy);
            fileLog("время ожидания вышло");
            showLoginHelp("Код от бота не удалось прочитать за " + XcfgPrefs.LOGIN_TIMEOUT_S
                    + " с. Журнал входа — files/xcfg.log (пришлите его разработчику). Если аккаунт не привязан — откройте @"
                    + XcfgPrefs.BOT + " и нажмите /start.");
        }
    }

    private void clearWait() {
        wait = null;
        removeLoginObserver();
    }

    private void showLoginHelp(String text) {
        AndroidUtilities.runOnUIThread(() -> {
            Activity a = activity();
            if (a == null) {
                return;
            }
            AlertDialog.Builder b = new AlertDialog.Builder(a);
            b.setTitle("X-CFG");
            b.setMessage(text);
            b.setPositiveButton("Открыть бота", (dialog, which) -> openUrl("https://t.me/" + XcfgPrefs.BOT));
            b.setNegativeButton("Закрыть", (dialog, which) -> dialog.dismiss());
            b.show();
        });
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id != NotificationCenter.didReceiveNewMessages || wait == null) {
            return;
        }
        try {
            long did = (Long) args[0];
            ArrayList<?> arr = (ArrayList<?>) args[1];
            LoginWait w = wait;
            if (w == null || did != w.botId) {
                return;
            }
            for (Object o : arr) {
                if (!(o instanceof org.telegram.messenger.MessageObject)) {
                    continue;
                }
                org.telegram.messenger.MessageObject m = (org.telegram.messenger.MessageObject) o;
                if (m.isOut()) {
                    continue;
                }
                TLRPC.Message owner = m.messageOwner;
                if (owner == null || owner.date < w.srvNow - 5) {
                    continue;
                }
                handleBotText(owner.message, owner.id, "хук");
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    /** Читаем последние сообщения чата с ботом, пока не придёт ответ (или не выйдет время). */
    private void pollHistory() {
        LoginWait w = wait;
        if (w == null || w.codeDispatched) {
            return;
        }
        if (System.currentTimeMillis() - w.startedAt > XcfgPrefs.LOGIN_TIMEOUT_S * 1000L) {
            AndroidUtilities.runOnUIThread(this::loginTimeoutCheck);
            return;
        }
        try {
            TLRPC.TL_messages_getHistory req = new TLRPC.TL_messages_getHistory();
            req.peer = MessagesController.getInstance(account).getInputPeer(w.botId);
            req.limit = 6;
            ConnectionsManager.getInstance(account).sendRequest(req, (RequestDelegate) (response, error) -> {
                try {
                    LoginWait lw = wait;
                    if (lw == null || lw != w) {
                        return;
                    }
                    if (!(response instanceof TLRPC.messages_Messages)) {
                        fileLog("история не получена: " + (error != null ? error.text : "empty"));
                        return;
                    }
                    List<TLRPC.Message> msgs = ((TLRPC.messages_Messages) response).messages;
                    // только сообщения, пришедшие после /start: устаревшие коды игнорируем
                    int since = w.srvNow - 5;
                    for (TLRPC.Message m : msgs) {
                        if (m.out || m.date < since || w.seenMsgIds.contains(m.id)) {
                            continue;
                        }
                        if (handleBotText(m.message, m.id, "история")) {
                            return;
                        }
                    }
                } catch (Exception e) {
                    fileLog("ошибка разбора истории: " + e);
                }
            });
        } catch (Exception e) {
            fileLog("опрос истории: " + e);
        }
        AndroidUtilities.runOnUIThread(() -> pool.execute(this::pollHistory), 1500);
    }

    /** Обработка текста бота; true — сообщение было нашим ответом (код или «сначала войдите»). */
    private boolean handleBotText(String text, int msgId, String source) {
        LoginWait w = wait;
        if (w == null) {
            return false;
        }
        if (msgId != 0) {
            if (w.seenMsgIds.contains(msgId)) {
                return false;
            }
            w.seenMsgIds.add(msgId);
        }
        Matcher m = CODE_RE.matcher(text == null ? "" : text);
        if (m.find()) {
            String code = m.group(1).toUpperCase();
            if (w.tried.contains(code)) {
                return false;
            }
            w.tried.add(code);
            w.codeDispatched = true;
            fileLog("код получен (" + source + "), меняю на вход");
            int uid = w.uid;
            pool.execute(() -> exchangeCode(code, uid));
            return true;
        }
        if (text != null && text.contains("Плагин X-CFG") && text.contains("Сначала войдите")) {
            clearWait();
            fileLog("бот: аккаунт не привязан");
            AndroidUtilities.runOnUIThread(this::closeBusy);
            showLoginHelp("Сначала войдите в аккаунт в боте @" + XcfgPrefs.BOT
                    + ": нажмите /start и привяжите аккаунт. Потом откройте вкладку X-CFG ещё раз.");
            return true;
        }
        return false;
    }

    private void exchangeCode(String code, int uid) {
        XcfgApi.ApiResp resp;
        try {
            resp = XcfgApi.pluginLogin(code);
        } catch (Exception e) {
            fileLog("нет связи с сайтом: " + e);
            clearWait();
            AndroidUtilities.runOnUIThread(this::closeBusy);
            toast("Нет связи с X-VPN: " + e.getMessage());
            return;
        }
        boolean success = resp.json.optBoolean("success");
        if (!success) {
            fileLog("сайт отклонил код: " + resp.json.optString("message"));
            // старый или чужой код — ждём свежий, пока не выйдет время
            LoginWait w = wait;
            if (w != null) {
                w.codeDispatched = false;
            }
            return;
        }
        clearWait();
        fileLog("вход выполнен");
        XcfgPrefs.set("token_" + uid, resp.json.optString("session_token", ""));
        showCabinet();
    }

    // ─────────────────────────── кабинет ───────────────────────────

    private XcfgApi.ApiResp api(String path) throws Exception {
        return XcfgApi.post(path, new JSONObject(), XcfgPrefs.token());
    }

    private void showCabinet() {
        AndroidUtilities.runOnUIThread(() -> showBusy("X-CFG"));
        pool.execute(() -> {
            XcfgApi.ApiResp resp;
            try {
                resp = api("/api/app/me");
            } catch (Exception e) {
                fileLog("me: " + e);
                AndroidUtilities.runOnUIThread(this::closeBusy);
                toast("Нет связи с X-VPN");
                return;
            }
            if (resp.code == 401) {
                // сессию завершили — входим заново
                XcfgPrefs.setToken("");
                AndroidUtilities.runOnUIThread(this::closeBusy);
                startLogin();
                return;
            }
            if (resp.code == 403) {
                AndroidUtilities.runOnUIThread(this::closeBusy);
                String msg = resp.json.optString("message");
                toast(msg.isEmpty() ? "Аккаунт недоступен" : msg);
                return;
            }
            AndroidUtilities.runOnUIThread(() -> {
                closeBusy();
                XcfgCabinet.render(this, resp.json);
            });
        });
    }

    // ─────────────────────────── VPN для Telegram ───────────────────────────

    private String hwid() {
        String h = XcfgPrefs.get("tg_hwid", "");
        if (h.isEmpty()) {
            h = "xcfg-tg-" + java.util.UUID.randomUUID().toString().replace("-", "");
            XcfgPrefs.set("tg_hwid", h);
        }
        return h;
    }

    private int vpnPort() {
        int port = XcfgPrefs.getInt("tg_port", 0);
        if (port == 0) {
            port = 20000 + (int) (Math.random() * 20000);
            XcfgPrefs.setInt("tg_port", port);
        }
        return port;
    }

    private String vpnSecret(String key) {
        String v = XcfgPrefs.get(key, "");
        if (v.isEmpty()) {
            byte[] b = new byte[12];
            new java.security.SecureRandom().nextBytes(b);
            StringBuilder sb = new StringBuilder();
            for (byte x : b) {
                sb.append(String.format("%02x", x));
            }
            v = sb.toString();
            XcfgPrefs.set(key, v);
        }
        return v;
    }

    /** Прокси включается только в памяти: без ядра Telegram после перезапуска просто подключится напрямую. */
    private void applyProxy(boolean on) {
        try {
            if (on) {
                ProxySettings ps = ProxySettings.builder()
                        .setType(ProxySettings.Type.SOCKS5)
                        .setAddress("127.0.0.1")
                        .setPort(vpnPort())
                        .setUser(vpnSecret("tg_user"))
                        .setPassword(vpnSecret("tg_pass"))
                        .setSecret("")
                        .build();
                ConnectionsManager.setProxySettings(true, ps);
            } else {
                // возвращаем то, что пользователь сам настроил в Telegram (или прямое подключение)
                android.content.SharedPreferences prefs = MessagesController.getGlobalMainSettings();
                ProxySettings own = ProxySettings.fromSharedPreferences(prefs);
                boolean enabled = prefs.getBoolean("proxy_enabled", false) && own != null && own.isValid();
                ConnectionsManager.setProxySettings(enabled, own);
            }
            AndroidUtilities.runOnUIThread(() -> {
                try {
                    NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxySettingsChanged, new Object[0]);
                } catch (Exception ignore) {
                }
            });
        } catch (Exception e) {
            fileLog("vpn proxy: " + e);
        }
    }

    private void startCore(List<String> links) throws Exception {
        String cfg = XcfgVless.buildConfig(links, vpnPort(), vpnSecret("tg_user"), vpnSecret("tg_pass"));
        XcfgCore.start(cfg);
        watchSystemVpn();
    }

    boolean vpnRunning() {
        return XcfgCore.isRunning();
    }

    private boolean systemVpnOn() {
        try {
            android.net.ConnectivityManager cm =
                    (android.net.ConnectivityManager) ApplicationLoader.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE);
            android.net.NetworkCapabilities caps = cm.getNetworkCapabilities(cm.getActiveNetwork());
            return caps != null && caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Пока работает VPN на весь телефон, Telegram идёт через него напрямую:
     * двойной туннель через тот же сервер не работает. Выключили VPN телефона — снова наш прокси.
     */
    private void watchSystemVpn() {
        final String gen = String.valueOf(System.currentTimeMillis());
        watcherGen = gen; // один наблюдатель на процесс: новый запуск гасит старый
        Thread t = new Thread(() -> {
            Boolean applied = null;
            while (watcherGen.equals(gen) && vpnRunning()) {
                boolean want = !systemVpnOn();
                if (applied == null || want != applied) {
                    try {
                        applyProxy(want);
                        fileLog(want ? "vpn: прокси Telegram включён" : "vpn: на телефоне включён VPN — Telegram идёт через него");
                    } catch (Exception e) {
                        fileLog("vpn watch: " + e);
                    }
                    applied = want;
                }
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }, "xcfg-watch");
        t.setDaemon(true);
        t.start();
    }

    void vpnEnable(String subUrl) {
        pool.execute(() -> {
            try {
                if (!XcfgCore.isReady()) {
                    toast("X-CFG: скачиваю ядро VPN (~12 МБ, один раз)…");
                    XcfgCore.download(percent -> {
                    });
                }
                XcfgApi.Sub sub = XcfgApi.fetchLinks(subUrl, hwid());
                if (sub.links.isEmpty()) {
                    toast("X-CFG: " + (sub.stub.isEmpty() ? "в подписке нет серверов" : sub.stub));
                    return;
                }
                XcfgPrefs.set("tg_links", new JSONArray(sub.links).toString());
                XcfgPrefs.set("tg_sub", subUrl);
                startCore(sub.links);
                XcfgPrefs.setBool("tg_vpn", true);
                fileLog("vpn: включён, серверов " + sub.links.size());
                toast("VPN для Telegram включён (серверов: " + sub.links.size() + ")");
            } catch (Exception e) {
                fileLog("vpn enable: " + e);
                toast("X-CFG: не удалось включить VPN: " + e.getMessage());
            }
        });
    }

    void vpnDisable(boolean notify) {
        XcfgPrefs.setBool("tg_vpn", false);
        watcherGen = "";
        applyProxy(false);
        XcfgCore.stop();
        if (notify) {
            toast("VPN для Telegram выключен");
        }
    }

    private void autostartInternal() {
        try {
            List<String> links = loadLinks();
            if (!links.isEmpty() && XcfgCore.isReady()) {
                startCore(links);
                fileLog("vpn: автозапуск, серверов " + links.size());
            }
            String sub = XcfgPrefs.get("tg_sub", "");
            if (!sub.isEmpty()) {
                XcfgApi.Sub fresh = XcfgApi.fetchLinks(sub, hwid());
                if (!fresh.links.isEmpty() && !fresh.links.equals(links)) {
                    XcfgPrefs.set("tg_links", new JSONArray(fresh.links).toString());
                    if (!XcfgCore.isReady()) {
                        XcfgCore.download(percent -> {
                        });
                    }
                    startCore(fresh.links);
                    fileLog("vpn: серверы обновлены (" + fresh.links.size() + ")");
                } else if (fresh.links.isEmpty()) {
                    fileLog("vpn: подписка без серверов: " + fresh.stub);
                    vpnDisable(false);
                    toast("X-CFG: VPN для Telegram выключен — " + (fresh.stub.isEmpty() ? "подписка недоступна" : fresh.stub));
                }
            }
        } catch (Exception e) {
            fileLog("vpn autostart: " + e);
        }
    }

    private static List<String> loadLinks() {
        List<String> links = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(XcfgPrefs.get("tg_links", "[]"));
            for (int i = 0; i < a.length(); i++) {
                links.add(a.getString(i));
            }
        } catch (Exception ignore) {
        }
        return links;
    }

    // ─────────────────────────── действия кабинета ───────────────────────────

    void trial() {
        pool.execute(() -> {
            try {
                XcfgApi.ApiResp resp = api("/api/app/trial");
                String msg = resp.json.optString("message");
                if (msg.isEmpty()) {
                    msg = resp.json.optBoolean("success") ? "Готово" : "Не удалось";
                }
                toast(msg);
            } catch (Exception e) {
                fileLog("trial: " + e);
                toast("Нет связи с X-VPN");
            }
        });
    }

    void logout() {
        final int tokenUid = XcfgPrefs.uid();
        pool.execute(() -> {
            if (XcfgPrefs.getBool("tg_vpn", false) || vpnRunning()) {
                vpnDisable(false);
            }
            XcfgPrefs.set("tg_links", "[]");
            XcfgPrefs.set("tg_sub", "");
            try {
                api("/api/app/logout");
            } catch (Exception ignore) {
            }
            XcfgPrefs.set("token_" + tokenUid, "");
            toast("Вы вышли из X-CFG. Следующий вход — автоматически через бота.");
        });
    }

    void launchXcfg() {
        Activity a = activity();
        if (a == null) {
            return;
        }
        try {
            Intent intent = new Intent();
            intent.setClassName(XcfgPrefs.XCFG_PACKAGE, XcfgPrefs.XCFG_ACTIVITY);
            // то же, что нажатие плитки X-CFG: приложение само запросит VPN-доступ и подключится
            intent.putExtra("connect", true);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            a.startActivity(intent);
        } catch (Exception e) {
            toast("Приложение X-CFG не установлено — открываю загрузку");
            openUrl(XcfgPrefs.API + "/");
        }
    }

    void openUrlExternal(String url) {
        openUrl(url);
    }

    AlertDialog cabinet() {
        return cabinetDialog;
    }

    void setCabinet(AlertDialog d) {
        cabinetDialog = d;
    }

    void closeCabinetPublic() {
        closeCabinet();
    }

    int themeColor(String key) {
        try {
            switch (key) {
                case "text":
                    return Theme.getColor(Theme.key_dialogTextBlack);
                case "hint":
                    return Theme.getColor(Theme.key_dialogTextGray2);
                case "chip":
                    return Theme.getColor(Theme.key_dialogBackgroundGray);
            }
        } catch (Exception ignore) {
        }
        return 0xFF000000;
    }
}
