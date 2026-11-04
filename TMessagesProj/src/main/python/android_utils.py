from typing import Any
from android.os import Looper
from org.telegram.messenger import AndroidUtilities
from app.nimarkogram.messenger.utils import AppUtils
from app.nimarkogram.messenger.plugins.ui import PluginViewListener
from plugin_runtime import (
    capture_callback_owner,
    make_interface_proxy,
    make_runnable,
    make_reusable_runnable,
    _run_owned_callback,
)

R = make_reusable_runnable

def safe_call(fn, *args, **kwargs):
    """Call an SDK callback without allowing an exception to escape into Java."""
    if fn is None:
        return None
    try:
        owner = capture_callback_owner(fn)
        if owner is not None:
            from app.nimarkogram.messenger.plugins import PluginsController
            if PluginsController.getInstance().isPluginRuntimeExecuting(owner):
                return fn(*args, **kwargs)
            return _run_owned_callback(owner, fn, args, kwargs, None)
        return fn(*args, **kwargs)
    except Exception:
        import traceback
        log('callback failed:\n' + traceback.format_exc())
        return None

def get_context():
    from org.telegram.messenger import ApplicationLoader
    return ApplicationLoader.applicationContext

def get_activity():
    from org.telegram.ui import LaunchActivity
    try:
        activity = LaunchActivity.instance
        if activity is not None:
            return activity
    except Exception:
        pass
    try:
        from client_utils import get_last_fragment
        fragment = get_last_fragment()
        return fragment.getParentActivity() if fragment is not None else None
    except Exception:
        return None

def OnClickListener(fn: callable):
    """Return a Java listener which is cleared with its exact plugin runtime."""
    token = capture_callback_owner(fn)
    if token is None:
        raise RuntimeError('OnClickListener requires a plugin runtime')
    return PluginViewListener(
        PluginViewListener.TYPE_CLICK, fn, token)

def OnLongClickListener(fn: callable):
    """Return a stale-safe Java long-click listener."""
    token = capture_callback_owner(fn)
    if token is None:
        raise RuntimeError('OnLongClickListener requires a plugin runtime')
    return PluginViewListener(
        PluginViewListener.TYPE_LONG_CLICK, fn, token)

def OnTouchListener(fn):
    from java import jclass
    class Listener:
        def onTouch(self, view, event):
            return bool(safe_call(fn, view, event))
    return make_interface_proxy(Listener(),
            [jclass('android.view.View$OnTouchListener')],
            owner=capture_callback_owner(fn))

def OnKeyListener(fn):
    from java import jclass
    class Listener:
        def onKey(self, view, key_code, event):
            return bool(safe_call(fn, view, key_code, event))
    return make_interface_proxy(Listener(),
            [jclass('android.view.View$OnKeyListener')],
            owner=capture_callback_owner(fn))

def OnSeekBarChangeListener(on_progress_changed, on_start=None, on_stop=None):
    from java import jclass
    class Listener:
        def onProgressChanged(self, seek_bar, progress, from_user):
            safe_call(on_progress_changed, seek_bar, progress, from_user)

        def onStartTrackingTouch(self, seek_bar):
            safe_call(on_start, seek_bar)

        def onStopTrackingTouch(self, seek_bar):
            safe_call(on_stop, seek_bar)

    return make_interface_proxy(Listener(),
            [jclass('android.widget.SeekBar$OnSeekBarChangeListener')],
            owner=capture_callback_owner(on_progress_changed))

def run_on_ui_thread(func: callable, delay=0):
    AndroidUtilities.runOnUIThread(make_runnable(func), delay)

def is_on_ui_thread() -> bool:
    return Looper.myLooper() == Looper.getMainLooper()

def log(data: Any):
    msg = str(data) if isinstance(data, (str, int, float, bool)) or data is None else None
    try:
        if msg is not None:
            AppUtils.log(msg)
        else:
            AppUtils.printObjectDetails(data)
    except Exception:
        pass
    try:
        print(msg if msg is not None else repr(data))
    except Exception:
        pass

def copy_to_clipboard(text: str):
    if AndroidUtilities.addToClipboard(text):
        from ui.bulletin import BulletinHelper
        BulletinHelper.show_copied_to_clipboard()
