package app.nimarkogram.messenger.nfc;

import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.cardemulation.HostApduService;
import android.os.Bundle;
import android.text.TextUtils;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.UserConfig;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

/**
 * Emulates a read-only NDEF tag over NFC so a second phone can read the
 * Linki-Share payload with a plain NDEF reader.
 */
public class LinkiShareNdefService extends HostApduService {

    private static final byte[] NDEF_AID = {
            (byte) 0xD2, 0x76, 0x00, 0x00, (byte) 0x85, 0x01, 0x01
    };
    private static final byte[] NDEF_FILE_ID = {(byte) 0xE1, 0x04};
    private static final byte[] CC_FILE_ID = {(byte) 0xE1, 0x03};

    private static final byte[] CAPABILITY_CONTAINER = {
            0x00, 0x0F,             // CCLEN
            0x20,                   // mapping version 2.0
            0x00, 0x3B,             // maximum R-APDU data size
            0x00, 0x34,             // maximum C-APDU data size
            0x04, 0x06,             // NDEF File Control TLV: T=04, L=06
            (byte) 0xE1, 0x04,      // NDEF file id
            0x04, (byte) 0x00,      // maximum NDEF file size (1024 bytes)
            0x00,                   // read access: everyone
            (byte) 0xFF            // write access: nobody
    };

    private static final byte[] STATUS_OK = {(byte) 0x90, 0x00};
    private static final byte[] STATUS_NOT_FOUND = {0x6A, (byte) 0x82};
    private static final byte[] STATUS_NOT_ALLOWED = {0x69, (byte) 0x86};
    private static final byte[] STATUS_FAILED = {0x6F, 0x00};

    /** Enables emulation only while the exchange screen is in front. */
    private static volatile boolean sArmed;

    private enum SelectedFile {NONE, CAPABILITY_CONTAINER, NDEF}

    private SelectedFile selected = SelectedFile.NONE;
    private byte[] ndefFile;

    public static void setArmed(boolean armed) {
        sArmed = armed;
    }

    public static boolean isArmed() {
        return sArmed;
    }

    @Override
    public byte[] processCommandApdu(byte[] apdu, Bundle extras) {
        if (apdu == null || apdu.length < 4) {
            return STATUS_FAILED;
        }
        final int ins = apdu[1] & 0xFF;
        if (ins == 0xA4) {
            return handleSelect(apdu);
        }
        if (ins == 0xB0) {
            return handleReadBinary(apdu);
        }
        return STATUS_NOT_ALLOWED;
    }

    private byte[] handleSelect(byte[] apdu) {
        final byte[] data = apduData(apdu);
        if (data == null) {
            return STATUS_FAILED;
        }
        if (Arrays.equals(data, NDEF_AID)) {
            selected = SelectedFile.NONE;
            ndefFile = buildNdefFile();
            return STATUS_OK;
        }
        if (Arrays.equals(data, CC_FILE_ID)) {
            selected = SelectedFile.CAPABILITY_CONTAINER;
            return STATUS_OK;
        }
        if (Arrays.equals(data, NDEF_FILE_ID)) {
            if (ndefFile == null) {
                ndefFile = buildNdefFile();
            }
            selected = SelectedFile.NDEF;
            return STATUS_OK;
        }
        return STATUS_NOT_FOUND;
    }

    private byte[] handleReadBinary(byte[] apdu) {
        if (selected == SelectedFile.NONE) {
            return STATUS_NOT_ALLOWED;
        }
        final byte[] file = selected == SelectedFile.CAPABILITY_CONTAINER
                ? CAPABILITY_CONTAINER
                : ndefFile;
        if (file == null || file.length == 0) {
            return STATUS_NOT_ALLOWED;
        }
        int offset = ((apdu[2] & 0xFF) << 8) | (apdu[3] & 0xFF);
        int length = apdu.length > 4 ? (apdu[4] & 0xFF) : 0;
        if (length == 0) {
            length = file.length;
        }
        if (offset >= file.length) {
            return STATUS_FAILED;
        }
        final int end = Math.min(file.length, offset + length);
        return appendStatus(Arrays.copyOfRange(file, offset, end));
    }

    /** The NDEF file is the 2-byte length prefix followed by the NDEF message. */
    private byte[] buildNdefFile() {
        final LinkiSharePayload payload = payload();
        if (payload == null) {
            // Keep the tag valid but empty until an account is available and armed.
            return new byte[]{0x00, 0x00};
        }
        try {
            final NdefRecord record = NdefRecord.createUri(payload.toUri());
            final byte[] message = new NdefMessage(new NdefRecord[]{record}).toByteArray();
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.write((message.length >> 8) & 0xFF);
            out.write(message.length & 0xFF);
            out.write(message);
            return out.toByteArray();
        } catch (Exception e) {
            FileLog.e(e);
            return new byte[]{0x00, 0x00};
        }
    }

    private LinkiSharePayload payload() {
        if (!sArmed) {
            return null;
        }
        final int account = UserConfig.selectedAccount;
        if (account < 0 || account >= UserConfig.MAX_ACCOUNT_COUNT) {
            return null;
        }
        return LinkiSharePayload.current(account);
    }

    private static byte[] apduData(byte[] apdu) {
        int length = apdu.length > 4 ? (apdu[4] & 0xFF) : 0;
        if (length == 0) {
            length = apdu.length - 5;
        }
        if (length <= 0 || apdu.length < 5 + length) {
            return new byte[0];
        }
        return Arrays.copyOfRange(apdu, 5, 5 + length);
    }

    private static byte[] appendStatus(byte[] body) {
        final byte[] result = Arrays.copyOf(body, body.length + STATUS_OK.length);
        System.arraycopy(STATUS_OK, 0, result, body.length, STATUS_OK.length);
        return result;
    }

    @Override
    public void onDeactivated(int reason) {
        selected = SelectedFile.NONE;
        ndefFile = null;
    }
}