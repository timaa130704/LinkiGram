package app.nimarkogram.messenger.plugins.models;

import com.chaquo.python.PyObject;

public final class SliderSetting extends SettingItem {
    public final String key;
    public final String text;
    public final double defaultValue;
    public final double min;
    public final double max;
    public final double step;
    public volatile PyObject onChangeCallback;

    public SliderSetting(String key, String text, double defaultValue, double min,
                         double max, double step, String icon, PyObject onChange,
                         PyObject onLongClick, String linkAlias) {
        super("slider", icon, onLongClick, linkAlias);
        this.key = key;
        this.text = text;
        this.defaultValue = defaultValue;
        this.min = min;
        this.max = max;
        this.step = step;
        this.onChangeCallback = onChange;
    }

    @Override public void clearPythonReferences() {
        onChangeCallback = null;
        super.clearPythonReferences();
    }
}
