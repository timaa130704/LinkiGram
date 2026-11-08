package app.exteraless.ai.data;

import java.util.Objects;

public final class Message {

    private final String role;
    private final String content;
    private byte[] imageData;
    private String mimeType;

    public Message(String role, String content) {
        this.role = role;
        this.content = content;
    }

    public Message(String role, String content, byte[] imageData, String mimeType) {
        this.role = role;
        this.content = content;
        this.imageData = imageData;
        this.mimeType = mimeType;
    }

    public String role() {
        return role;
    }

    public String content() {
        return content;
    }

    public byte[] getImageData() {
        return imageData;
    }

    public String getMimeType() {
        return mimeType;
    }

    /** Drops the payload while keeping the text, so old pictures stop being resent. */
    public void clearImage() {
        imageData = null;
        mimeType = null;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || Message.class != other.getClass()) {
            return false;
        }
        return Objects.equals(content, ((Message) other).content);
    }

    @Override
    public int hashCode() {
        return Objects.hash(role, content);
    }
}
