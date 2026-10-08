package com.fongmi.android.tv.utils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Android 12+ ApplicationExitInfo returns a protobuf, not a text tombstone.
 * Reads the AOSP debuggerd/proto/tombstone.proto diagnostic fields without a new runtime dependency.
 * Keeps every available thread/backtrace frame; skips raw memory, open files and logcat payloads.
 * Unknown fields are skipped by wire type, while corrupt/truncated messages are rejected.
 */
public final class NativeCrashTrace {
    public static final int MAX_BYTES = 4 * 1024 * 1024;

    private NativeCrashTrace() { }

    public static String read(InputStream input) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) {
            if (count == 0) continue;
            if (bytes.size() > MAX_BYTES - count) throw new IOException("原生堆栈超过 4 MiB 安全读取上限");
            bytes.write(buffer, 0, count);
        }
        byte[] data = bytes.toByteArray();
        Message tombstone = new Message(data, 0, data.length);
        if (tombstone.number(5) == 0) throw new IOException("系统未提供可识别的 tombstone protobuf");
        long crashedTid = tombstone.number(6);
        StringBuilder text = new StringBuilder();
        text.append("pid=").append(tombstone.number(5)).append(" crashedTid=").append(crashedTid)
                .append(" uid=").append(tombstone.number(7)).append('\n');
        text.append("time=").append(tombstone.string(4)).append(" build=").append(tombstone.string(2)).append('\n');
        for (Field command : tombstone.fields(9)) text.append("command=").append(command.string()).append('\n');
        Field signal = tombstone.first(10);
        if (signal != null) {
            Message value = signal.message();
            text.append("signal=").append(value.number(1)).append(" (").append(value.string(2))
                    .append(") code=").append(value.number(3)).append(" (").append(value.string(4)).append(')');
            if (value.number(8) != 0) text.append(" faultAddress=0x").append(Long.toHexString(value.number(9)));
            text.append('\n');
        }
        String abort = tombstone.string(14);
        if (!abort.isEmpty()) text.append("Abort message: ").append(abort).append('\n');
        for (Field cause : tombstone.fields(15)) text.append("Cause: ").append(cause.message().string(1)).append('\n');
        List<Message> threads = new ArrayList<>();
        for (Field entry : tombstone.fields(16)) {
            Field thread = entry.message().first(2);
            if (thread != null) threads.add(thread.message());
        }
        threads.sort((left, right) -> Boolean.compare(right.number(1) == crashedTid, left.number(1) == crashedTid));
        for (Message thread : threads) {
            text.append("\nThread tid=").append(thread.number(1)).append(" name=").append(thread.string(2));
            if (thread.number(1) == crashedTid) text.append(" [crashed]");
            text.append('\n');
            for (Field register : thread.fields(3)) {
                Message value = register.message();
                text.append("  ").append(value.string(1)).append("=0x").append(Long.toHexString(value.number(2))).append('\n');
            }
            for (Field note : thread.fields(7)) text.append("  note: ").append(note.string()).append('\n');
            int index = 0;
            for (Field frame : thread.fields(4)) {
                Message value = frame.message();
                text.append(String.format(Locale.US, "  #%02d pc %016x ", index++, value.number(1)))
                        .append(value.string(6));
                String function = value.string(4), build = value.string(8);
                if (!function.isEmpty()) text.append(" (").append(function).append('+').append(value.number(5)).append(')');
                if (value.number(7) != 0) text.append(" (mapOffset=0x").append(Long.toHexString(value.number(7))).append(')');
                if (!build.isEmpty()) text.append(" (BuildId: ").append(build).append(')');
                text.append('\n');
            }
        }
        if (threads.isEmpty()) text.append("系统 tombstone 未包含线程堆栈\n");
        return text.toString();
    }

    private static final class Message {
        private final List<Field> values = new ArrayList<>();

        private Message(byte[] data, int start, int end) throws IOException {
            Cursor cursor = new Cursor(data, start, end);
            while (cursor.position < end) {
                long tag = cursor.varint();
                if (tag <= 0 || tag > 0xffffffffL || tag >>> 3 == 0) throw new IOException("Invalid protobuf field");
                int number = (int) (tag >>> 3), wire = (int) (tag & 7);
                if (wire == 0) values.add(new Field(number, cursor.varint(), data, -1, -1));
                else if (wire == 2) {
                    long length = cursor.varint();
                    if (length < 0 || length > end - cursor.position) throw new IOException("Truncated protobuf field");
                    int next = cursor.position + (int) length;
                    values.add(new Field(number, 0, data, cursor.position, next));
                    cursor.position = next;
                } else if (wire == 1 || wire == 5) {
                    int length = wire == 1 ? 8 : 4;
                    if (length > end - cursor.position) throw new IOException("Truncated fixed protobuf field");
                    cursor.position += length;
                } else throw new IOException("Unsupported protobuf wire type " + wire);
            }
        }

        private Field first(int number) {
            for (Field value : values) if (value.number == number) return value;
            return null;
        }

        private List<Field> fields(int number) {
            List<Field> found = new ArrayList<>();
            for (Field value : values) if (value.number == number) found.add(value);
            return found;
        }

        private long number(int number) {
            Field value = first(number);
            return value == null ? 0 : value.value;
        }

        private String string(int number) throws IOException {
            Field value = first(number);
            return value == null ? "" : value.string();
        }
    }

    private static final class Field {
        private final int number, start, end;
        private final long value;
        private final byte[] data;

        private Field(int number, long value, byte[] data, int start, int end) {
            this.number = number;
            this.value = value;
            this.data = data;
            this.start = start;
            this.end = end;
        }

        private Message message() throws IOException {
            if (start < 0) throw new IOException("Expected protobuf message");
            return new Message(data, start, end);
        }

        private String string() throws IOException {
            if (start < 0) throw new IOException("Expected protobuf string");
            return new String(data, start, end - start, StandardCharsets.UTF_8);
        }
    }

    private static final class Cursor {
        private final byte[] data;
        private final int end;
        private int position;

        private Cursor(byte[] data, int position, int end) { this.data = data; this.position = position; this.end = end; }

        private long varint() throws IOException {
            long result = 0;
            for (int index = 0; index < 10; index++) {
                if (position >= end) throw new IOException("Truncated protobuf varint");
                int value = data[position++] & 0xff;
                if (index == 9 && value > 1) throw new IOException("Overflowing protobuf varint");
                result |= (long) (value & 0x7f) << (index * 7);
                if ((value & 0x80) == 0) return result;
            }
            throw new IOException("Invalid protobuf varint");
        }
    }
}
