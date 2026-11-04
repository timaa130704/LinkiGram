package app.nimarkogram.messenger.plugins.utils;

import android.os.Handler;
import android.os.Looper;
import com.chaquo.python.PyObject;
import java.util.concurrent.atomic.AtomicReference;
import app.nimarkogram.messenger.plugins.PluginsController;
import app.nimarkogram.messenger.plugins.ui.PluginUiRegistry;
import org.telegram.messenger.DispatchQueue;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.Utilities;

/** A reusable Android Runnable tied to one generation of a plugin. */
public final class PythonReusableRunnable implements Runnable, PluginsController.RuntimeCallbackHolder {
    private interface Dispatcher { boolean post(Runnable runnable); }

    private final AtomicReference<PyObject> callback;
    private final AtomicReference<Dispatcher> deferred = new AtomicReference<>();
    private final PluginsController.PluginRuntimeToken token;

    public PythonReusableRunnable(PyObject callback, PluginsController.PluginRuntimeToken token) {
        if (callback == null || token == null) {
            throw new IllegalArgumentException("Runnable requires a callback and exact plugin runtime");
        }
        this.callback = new AtomicReference<>(callback);
        this.token = token;
        if (!PluginsController.getInstance().registerRuntimeCallbackHolder(token, this)) {
            this.callback.set(null);
            throw new IllegalArgumentException("Runnable runtime is not current");
        }
    }

    @Override public void run() {
        PluginsController controller = PluginsController.getInstance();
        if (callback.get() == null) return;
        int decision = controller.getPluginRuntimeTaskDecision(token);
        if (decision == PluginsController.RUNTIME_TASK_WAIT) {
            deferred.compareAndSet(null, captureDispatcher());
            decision = controller.deferRuntimeCallback(token, this);
            if (decision == PluginsController.RUNTIME_TASK_RUN) onPluginRuntimeActive();
            else if (decision == PluginsController.RUNTIME_TASK_DROP) revokeAndUnregister();
            return;
        }
        if (decision != PluginsController.RUNTIME_TASK_RUN || !controller.enterPluginRuntime(token)) {
            revokeAndUnregister();
            return;
        }
        PyObject callable = callback.get();
        if (callable == null) {
            controller.exitPluginRuntime(token);
            return;
        }
        String pluginId = token.getPluginId();
        PluginUiRegistry.DecorChildrenSnapshot snapshot = Looper.myLooper() == Looper.getMainLooper()
                ? PluginUiRegistry.captureDecorChildren() : null;
        controller.getWatchdog().onPluginExecutionStarted(pluginId);
        try {
            callable.call();
        } catch (Throwable error) {
            controller.getWatchdog().onPluginExecutionFailed(pluginId, error);
            FileLog.e("LinkiGram: reusable Python callback failed", error);
            if (error instanceof VirtualMachineError) throw (VirtualMachineError) error;
            if (error instanceof ThreadDeath) throw (ThreadDeath) error;
            if (error instanceof LinkageError) throw (LinkageError) error;
        } finally {
            controller.getWatchdog().onPluginExecutionFinished(pluginId);
            if (snapshot != null) {
                try { PluginUiRegistry.adoptNewDecorChildren(token, snapshot); }
                catch (Throwable failure) { FileLog.e("LinkiGram: unable to isolate plugin overlay", failure); }
            }
            controller.exitPluginRuntime(token);
        }
    }

    @Override public void onPluginRuntimeActive() {
        Dispatcher dispatcher = deferred.getAndSet(null);
        if (dispatcher != null && callback.get() != null) {
            try {
                if (!dispatcher.post(this)) revokeAndUnregister();
            } catch (Throwable error) {
                revokeAndUnregister();
                if (error instanceof VirtualMachineError) throw (VirtualMachineError) error;
                if (error instanceof ThreadDeath) throw (ThreadDeath) error;
                if (error instanceof LinkageError) throw (LinkageError) error;
                FileLog.e("LinkiGram: unable to resume reusable callback", error);
            }
        }
    }

    @Override public void revokePluginRuntime() {
        callback.set(null);
        deferred.set(null);
    }

    private void revokeAndUnregister() {
        revokePluginRuntime();
        PluginsController.getInstance().unregisterRuntimeCallbackHolder(token, this);
    }

    private static Dispatcher captureDispatcher() {
        Thread current = Thread.currentThread();
        if (current instanceof DispatchQueue) return ((DispatchQueue) current)::postRunnable;
        Looper looper = Looper.myLooper();
        if (looper != null) return new Handler(looper)::post;
        return Utilities.pluginsQueue::postRunnable;
    }
}
