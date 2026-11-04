"""Elyx settings backed by the same persistent store as BasePlugin."""


def _store():
    # Import lazily: plugin_settings needs Android helpers during startup.
    import plugin_settings
    return plugin_settings


def _reload(plugin_id):
    from java import jclass
    controller = jclass('app.nimarkogram.messenger.plugins.PluginsController')
    controller.getInstance().loadPluginSettings(plugin_id)


class SettingsController:
    def __init__(self, plugin_id):
        self._plugin_id = str(plugin_id)

    @property
    def plugin_id(self):
        return self._plugin_id

    def get_settings(self):
        return _store().get_all_settings(self._plugin_id)

    def get_setting(self, key, default=None):
        return _store().get_setting(self._plugin_id, key, default)

    def get(self, key, default=None):
        return self.get_setting(key, default)

    def __call__(self, key, default=None):
        return self.get_setting(key, default)

    def __getitem__(self, key):
        return self.get_setting(key)

    def set_setting(self, key, value, reload_settings=False):
        completion = _store().set_setting(self._plugin_id, key, value)
        if completion and reload_settings:
            _reload(self._plugin_id)

    def set(self, key, value, reload_settings=False):
        self.set_setting(key, value, reload_settings)

    def __setitem__(self, key, value):
        self.set_setting(key, value)

    def clear_settings(self):
        completion = _store().clear_settings(self._plugin_id)
        if completion:
            _reload(self._plugin_id)
