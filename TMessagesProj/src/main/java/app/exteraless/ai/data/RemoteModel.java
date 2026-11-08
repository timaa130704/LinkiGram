package app.exteraless.ai.data;

import android.text.TextUtils;

/**
 * One entry returned by an OpenAI-compatible {@code /models} list.
 *
 * <p>Keyless gateways such as OpenCode Zen publish several free models next to
 * paid ones, so the picker needs the price information to mark them.
 */
public final class RemoteModel {

    private final String id;
    private final String name;
    private final boolean free;
    private final boolean reasoning;
    private final long context;

    public RemoteModel(String id, String name, boolean free, boolean reasoning, long context) {
        this.id = id;
        this.name = TextUtils.isEmpty(name) ? id : name;
        this.free = free;
        this.reasoning = reasoning;
        this.context = context;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public boolean isFree() {
        return free;
    }

    public boolean isReasoning() {
        return reasoning;
    }

    public long getContext() {
        return context;
    }
}