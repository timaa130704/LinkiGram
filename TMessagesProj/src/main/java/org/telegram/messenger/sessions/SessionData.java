package org.telegram.messenger.sessions;

import java.util.Arrays;

/** A production user authorization. Never log this object or its key. */
public final class SessionData {
    public final int dcId;
    public final long userId;
    public final int apiId;
    public final byte[] authKey;

    public SessionData(int dcId, byte[] authKey, long userId, int apiId) {
        if (dcId < 1 || dcId > 5 || authKey == null || authKey.length != 256 || userId < 0) {
            throw new IllegalArgumentException("Invalid session");
        }
        boolean nonzero = false;
        for (byte b : authKey) nonzero |= b != 0;
        if (!nonzero) throw new IllegalArgumentException("Empty auth key");
        this.dcId = dcId;
        this.authKey = authKey.clone();
        this.userId = userId;
        this.apiId = apiId;
    }

    public void clear() { Arrays.fill(authKey, (byte) 0); }
}
