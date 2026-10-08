package com.fongmi.android.tv.api.loader;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/** A bounded control frame followed by an optional streaming body, never a Binder byte array. */
public final class SourceWire {
    public static final int MAX_FRAME = 8 * 1024 * 1024;
    private SourceWire() { }

    public static void write(OutputStream output, String text) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_FRAME) throw new IOException("Source response too large");
        DataOutputStream data = new DataOutputStream(output);
        data.writeInt(bytes.length);
        data.write(bytes);
        data.flush();
    }

    public static String read(InputStream input) throws IOException {
        DataInputStream data = new DataInputStream(input);
        int length = data.readInt();
        if (length < 0 || length > MAX_FRAME) throw new IOException("Invalid source frame length: " + length);
        byte[] bytes = new byte[length];
        data.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
