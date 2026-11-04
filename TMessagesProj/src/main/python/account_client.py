"""Account-bound facade compatible with the exteraless client SDK."""

import threading
from contextlib import contextmanager

_state = threading.local()
_missing = object()


def get_hook_account():
    return getattr(_state, 'account', None)


def _enter_hook_account(account):
    previous = getattr(_state, 'account', _missing)
    _state.account = int(account)
    return previous


def _exit_hook_account(previous):
    if previous is _missing:
        try:
            del _state.account
        except AttributeError:
            pass
    else:
        _state.account = previous


@contextmanager
def hook_scope(account):
    previous = _enter_hook_account(account)
    try:
        yield account
    finally:
        _exit_hook_account(previous)


class AccountClient:
    def __init__(self, account=None):
        self._account = None if account is None else int(account)

    def __call__(self, account=None):
        return AccountClient(account)

    @property
    def account(self):
        if self._account is not None:
            return self._account
        account = get_hook_account()
        if account is not None:
            return account
        import client_utils
        return client_utils.get_selected_account()

    def send_request(self, request, fn):
        import client_utils
        return client_utils.send_request(request, _scope_callback(fn, self.account), account=self.account)

    def send_message(self, params, parse_mode=None):
        import client_utils
        return client_utils.send_message(params, account=self.account, parse_mode=parse_mode)

    def send_text(self, peer_id, text, replyToMsg=None, parse_mode=None):
        import client_utils
        return client_utils.send_text(peer_id, text, account=self.account,
                                      replyToMsg=replyToMsg, parse_mode=parse_mode)

    def send_photo(self, peer_id, path, caption=None, high_quality=False,
                   parse_mode=None, replyToMsg=None):
        import client_utils
        return client_utils.send_photo(peer_id, path, caption=caption, high_quality=high_quality,
                                       account=self.account, parse_mode=parse_mode, replyToMsg=replyToMsg)

    def send_video(self, peer_id, path, caption=None, parse_mode=None, replyToMsg=None):
        import client_utils
        return client_utils.send_video(peer_id, path, caption=caption, account=self.account,
                                       parse_mode=parse_mode, replyToMsg=replyToMsg)

    def send_document(self, peer_id, path, caption=None, parse_mode=None, replyToMsg=None):
        import client_utils
        return client_utils.send_document(peer_id, path, caption=caption, account=self.account,
                                          parse_mode=parse_mode, replyToMsg=replyToMsg)

    def send_audio(self, peer_id, path, caption=None, parse_mode=None, replyToMsg=None):
        import client_utils
        return client_utils.send_audio(peer_id, path, caption=caption, account=self.account,
                                       parse_mode=parse_mode, replyToMsg=replyToMsg)

    def edit_message(self, message_obj, text=None, file_path=None, with_spoiler=False, parse_mode=None):
        import client_utils
        return client_utils.edit_message(message_obj, text=text, file_path=file_path,
                                         with_spoiler=with_spoiler, account=self.account, parse_mode=parse_mode)

    def run_on_queue(self, fn, queue=None, delay=0, delay_ms=None):
        import client_utils
        if queue is None:
            queue = client_utils.PLUGINS_QUEUE
        if delay_ms is not None:
            delay = delay_ms
        return client_utils.run_on_queue(_scope_callback(fn, self.account), queue, delay)

    def __getattr__(self, name):
        # All account-aware helpers already enforce host permissions and use
        # runtime-owned Java callbacks; don't bypass them in this facade.
        import client_utils
        if name in _ACCOUNT_HELPERS:
            function = getattr(client_utils, name)
            return lambda *args, **kwargs: function(*args, account=self.account, **kwargs)
        if name in ('get_media_controller', 'get_last_fragment'):
            return getattr(client_utils, name)
        raise AttributeError(name)


_ACCOUNT_HELPERS = frozenset({
    'get_account_instance', 'get_messages_controller', 'get_contacts_controller',
    'get_media_data_controller', 'get_connections_manager', 'get_location_controller',
    'get_notifications_controller', 'get_messages_storage', 'get_send_messages_helper',
    'get_file_loader', 'get_secret_chat_helper', 'get_download_controller',
    'get_notifications_settings', 'get_notification_center', 'get_user_config',
    'send_request', 'send_text', 'send_message', 'send_photo', 'send_video',
    'send_document', 'send_audio', 'edit_message',
})


def _scope_callback(callback, account):
    from plugin_runtime import capture_callback_owner
    token = capture_callback_owner(callback)
    def wrapped(*args, **kwargs):
        with hook_scope(account):
            return callback(*args, **kwargs)
    wrapped._nimarko_runtime_token = token
    return wrapped


def get_client(account=None):
    return AccountClient(account)
