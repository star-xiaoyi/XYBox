package com.fongmi.android.tv.search;

import androidx.media3.common.C;
import androidx.media3.common.DataReader;
import androidx.media3.common.Format;
import androidx.media3.common.util.ParsableByteArray;
import androidx.media3.extractor.*;
import androidx.media3.extractor.ts.TsExtractor;
import androidx.media3.extractor.mp4.Mp4Extractor;
import androidx.media3.extractor.mp4.FragmentedMp4Extractor;
import com.github.catvod.net.OkHttp;
import okhttp3.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.regex.*;

/** Read bounded manifest/container headers, without creating a player or decoder. */
public final class QualityProbe {
    public static final class Size {
        public final int width, height;
        public final long speed;
        public final int bitrate;
        public final String playbackUrl;
        /** HTTP request to first media bytes, including connection and redirects; not ICMP ping. */
        public final long latencyMs;
        public Size(int width, int height) { this(width, height, 0, 0); }
        public Size(int width, int height, long speed, int bitrate) { this(width, height, speed, bitrate, ""); }
        public Size(int width, int height, long speed, int bitrate, String playbackUrl) {
            this(width, height, speed, bitrate, playbackUrl, -1);
        }
        public Size(int width, int height, long speed, int bitrate, String playbackUrl, long latencyMs) {
            this.width = width; this.height = height; this.speed = speed; this.bitrate = bitrate; this.playbackUrl = playbackUrl;
            this.latencyMs = latencyMs;
        }
        public int shortSide() { return Math.min(width, height); }
    }
    /** Shared URL results retain the time of the real request, including after a paused probe resumes. */
    public static final class Measurement {
        public final List<Size> sizes;
        public final long measuredAt;
        public Measurement(List<Size> sizes, long measuredAt) {
            this.sizes = Collections.unmodifiableList(new ArrayList<>(sizes));
            this.measuredAt = measuredAt;
        }
        public boolean fresh(long now) { return SourceSelection.fresh(measuredAt, now); }
    }
    private static final int LIMIT = 192 * 1024;
    private static final int MIN_THROUGHPUT_SAMPLE = 128 * 1024;
    private static final Pattern RESOLUTION = Pattern.compile("RESOLUTION=(\\d+)x(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern MAP = Pattern.compile("#EXT-X-MAP:.*?URI=\"([^\"]+)\"");
    private QualityProbe() { }

    public static List<Size> manifestSizes(String manifest) {
        List<Size> result = new ArrayList<>(); Matcher matcher = RESOLUTION.matcher(manifest);
        while (matcher.find()) {
            int width = Integer.parseInt(matcher.group(1)), height = Integer.parseInt(matcher.group(2));
            if (width > 0 && height > 0 && width < 32768 && height < 32768) {
                boolean duplicate = false; for (Size size : result) duplicate |= size.width == width && size.height == height;
                if (!duplicate) result.add(new Size(width, height));
            }
        }
        return result;
    }

    public static List<Size> inspect(String url, Map<String, String> headers) throws Exception {
        return inspect(url, headers, System.nanoTime() + 35_000_000_000L, 0, 0, url);
    }

    private static List<Size> inspect(String url, Map<String, String> headers, long deadline, int depth, int bandwidth, String playbackUrl) throws Exception {
        if (depth >= 6 || Thread.currentThread().isInterrupted()) return Collections.emptyList();
        long remaining = (deadline - System.nanoTime()) / 1_000_000;
        if (remaining <= 0) return Collections.emptyList();
        Payload payload = fetch(url, headers, deadline, "bytes=0-" + (LIMIT - 1), LIMIT);
        byte[] bytes = payload.bytes; HttpUrl base = payload.base; long speed = payload.speed;
        String text = new String(bytes, java.nio.charset.StandardCharsets.UTF_8).replace("\uFEFF", "").trim();
        // Some source proxies return the final manifest address as a plain single-line body.
        if (text.length() < 4096 && text.matches("https?://[^\\s]+"))
            return inspect(text, headers, deadline, depth + 1, bandwidth, text);
        if (!text.startsWith("#EXTM3U")) {
            List<Size> measured = new ArrayList<>();
            List<Size> dimensions = containerSizes(bytes);
            // Many MP4s store the track metadata after a large mdat atom.
            if (dimensions.isEmpty() && isMp4(bytes) && System.nanoTime() < deadline) {
                Payload tail = fetch(url, headers, deadline, "bytes=-" + LIMIT, LIMIT);
                byte[] moov = moovOnly(tail.bytes);
                if (moov != null) dimensions = containerSizes(moov);
            }
            for (Size size : dimensions) measured.add(new Size(size.width, size.height, speed, size.bitrate > 0 ? size.bitrate : bandwidth, playbackUrl, payload.latencyMs));
            return measured;
        }
        // HLS RESOLUTION is only an advertisement. Inspect real media from each variant.
        List<Size> measured = new ArrayList<>();
        String[] lines = text.split("\n");
        int bitrate = 0, variants = 0;
        boolean nextVariant = false;
        for (String raw : lines) {
            String line = raw.trim();
            if (line.startsWith("#EXT-X-STREAM-INF:")) {
                nextVariant = true;
                Matcher bw = Pattern.compile("(?:^|[, :])BANDWIDTH=(\\d+)").matcher(line);
                bitrate = bw.find() ? (int) Math.min(Integer.MAX_VALUE, Long.parseLong(bw.group(1))) : 0;
            } else if (nextVariant && !line.isEmpty() && !line.startsWith("#")) {
                nextVariant = false;
                HttpUrl target = base.resolve(line);
                if (target != null && variants++ < 12) {
                    try { measured.addAll(inspect(target.toString(), headers, deadline, depth + 1, bitrate, target.toString())); }
                    catch (Exception ignored) { }
                }
            }
        }
        if (variants > 0) return measured;
        Exception failure = null;
        for (Sample sample : samples(lines)) {
            try {
                measured = sampleSizes(sample, base, headers, deadline, depth, bandwidth, playbackUrl);
                if (!measured.isEmpty()) return measured;
            } catch (Exception error) { failure = error; }
            if (System.nanoTime() >= deadline || Thread.currentThread().isInterrupted()) break;
        }
        if (failure != null) throw failure;
        return measured;
    }

    private static List<Size> sampleSizes(Sample sample, HttpUrl base, Map<String, String> headers,
                                          long deadline, int depth, int bandwidth, String playbackUrl) throws Exception {
        List<Size> measured = new ArrayList<>();
        HttpUrl target = base.resolve(sample.uri);
        if (target == null) return measured;
        if (sample.method.equals("NONE")) return inspect(target.toString(), headers, deadline, depth + 1, bandwidth, playbackUrl);
        if (!sample.method.equals("AES-128") || !sample.format.equals("identity") || sample.keyUri.isEmpty()) return measured;
        HttpUrl keyUrl = base.resolve(sample.keyUri);
        if (keyUrl == null || sample.map && sample.iv.isEmpty()) return measured;
        Payload key = fetch(keyUrl.toString(), headers, deadline, "bytes=0-15", 16);
        if (key.bytes.length != 16) return measured;
        Payload encrypted = fetch(target.toString(), headers, deadline, "bytes=0-" + (LIMIT - 1), LIMIT);
        javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/CBC/NoPadding");
        cipher.init(javax.crypto.Cipher.DECRYPT_MODE, new javax.crypto.spec.SecretKeySpec(key.bytes, "AES"),
                new javax.crypto.spec.IvParameterSpec(sample.vector()));
        int blocks = encrypted.bytes.length / 16 * 16;
        if (blocks == 0) return measured;
        byte[] decrypted = cipher.doFinal(encrypted.bytes, 0, blocks);
        for (Size size : containerSizes(decrypted)) measured.add(new Size(size.width, size.height,
                encrypted.speed, size.bitrate > 0 ? size.bitrate : bandwidth, playbackUrl, encrypted.latencyMs));
        return measured;
    }

    private static final class Payload {
        final byte[] bytes; final HttpUrl base; final long speed, latencyMs;
        Payload(SampleBytes sample, HttpUrl base, long elapsed) {
            bytes = sample.bytes; this.base = base; latencyMs = sample.latencyMs;
            // Include first-byte waiting in the average: a short sample must not exaggerate throughput.
            speed = bytes.length >= 64 * 1024 ? bytes.length * 1000L / Math.max(1, elapsed) : 0;
        }
    }
    private static Payload fetch(String url, Map<String, String> headers, long deadline, String range, int limit) throws Exception {
        long remaining = (deadline - System.nanoTime()) / 1_000_000;
        if (remaining <= 0 || Thread.currentThread().isInterrupted()) throw new java.io.InterruptedIOException("核实已结束");
        OkHttpClient client = OkHttp.client(Math.min(remaining, 10000)).newBuilder()
                .callTimeout(Math.min(remaining, 12000), java.util.concurrent.TimeUnit.MILLISECONDS).build();
        Request.Builder request = new Request.Builder().url(url).headers(Headers.of(headers)).header("Range", range);
        long started = System.nanoTime();
        try (Response response = client.newCall(request.build()).execute()) {
            if (response.code() == 416 && !range.startsWith("bytes=-")) {
                try (Response retry = client.newCall(request.removeHeader("Range").build()).execute()) {
                    if (!retry.isSuccessful() || retry.body() == null) throw new java.io.IOException("HTTP " + retry.code());
                    return new Payload(readBounded(retry, deadline, limit, started), retry.request().url(), (System.nanoTime() - started) / 1_000_000);
                }
            }
            if (!response.isSuccessful() || response.body() == null) throw new java.io.IOException("HTTP " + response.code());
            return new Payload(readBounded(response, deadline, limit, started), response.request().url(), (System.nanoTime() - started) / 1_000_000);
        }
    }
    private static final class Sample {
        String uri, method = "NONE", format = "identity", keyUri = "", iv = "";
        java.math.BigInteger sequence = java.math.BigInteger.ZERO; boolean map;
        byte[] vector() {
            java.math.BigInteger value = iv.isEmpty() ? sequence : new java.math.BigInteger(iv.replaceFirst("(?i)^0x", ""), 16);
            if (value.signum() < 0 || value.bitLength() > 128) throw new IllegalArgumentException("IV");
            byte[] number = value.toByteArray(), result = new byte[16];
            int count = Math.min(16, number.length); System.arraycopy(number, number.length-count, result, 16-count, count); return result;
        }
    }
    private static String attribute(String line, String name, String fallback) {
        Matcher match = Pattern.compile("(?:^|,)" + name + "=(?:\\\"([^\\\"]*)\\\"|([^,]*))").matcher(line.substring(line.indexOf(':')+1));
        return match.find() ? match.group(1) != null ? match.group(1) : match.group(2) : fallback;
    }
    private static List<Sample> samples(String[] lines) {
        List<Sample> segments = new ArrayList<>(); Sample map = null;
        String method = "NONE", format = "identity", keyUri = "", iv = "";
        java.math.BigInteger sequence = java.math.BigInteger.ZERO;
        for (String raw : lines) {
            String line = raw.trim();
            if (line.startsWith("#EXT-X-MEDIA-SEQUENCE:")) sequence = new java.math.BigInteger(line.substring(line.indexOf(':')+1).trim());
            else if (line.startsWith("#EXT-X-KEY:")) {
                method = attribute(line, "METHOD", "NONE"); format = attribute(line, "KEYFORMAT", "identity");
                keyUri = attribute(line, "URI", ""); iv = attribute(line, "IV", "");
            } else if (line.startsWith("#EXT-X-MAP:") || !line.isEmpty() && !line.startsWith("#")) {
                Sample item = new Sample(); item.map = line.startsWith("#EXT-X-MAP:");
                // Byte ranges need an offset-specific IV/request; keep unsupported samples unverified.
                if (item.map && !attribute(line, "BYTERANGE", "").isEmpty()) return Collections.emptyList();
                item.uri = item.map ? attribute(line, "URI", "") : line;
                item.method = method; item.format = format; item.keyUri = keyUri; item.iv = iv; item.sequence = sequence;
                if (item.map) map = item; else { segments.add(item); sequence = sequence.add(java.math.BigInteger.ONE); }
            } else if (line.startsWith("#EXT-X-BYTERANGE:")) return Collections.emptyList();
        }
        if (map != null) return Collections.singletonList(map);
        if (segments.isEmpty()) return Collections.emptyList();
        // A middle segment may rely on SPS/PPS from the first segment, so try initialization first.
        List<Sample> result = new ArrayList<>(); result.add(segments.get(0));
        for (int index : new int[]{segments.size()/2, segments.size()-1}) if (!result.contains(segments.get(index))) result.add(segments.get(index));
        return result;
    }
    private static boolean isMp4(byte[] data) {
        return data.length >= 12 && data[4]=='f' && data[5]=='t' && data[6]=='y' && data[7]=='p';
    }
    private static byte[] moovOnly(byte[] data) {
        for (int i=4;i+4<=data.length;i++) if (data[i]=='m'&&data[i+1]=='o'&&data[i+2]=='o'&&data[i+3]=='v') {
            long length = java.nio.ByteBuffer.wrap(data, i-4, 4).getInt() & 0xffffffffL;
            if (length >= 8 && length <= data.length-i+4) {
                byte[] prefix = {0,0,0,16,'f','t','y','p','i','s','o','m',0,0,0,0};
                byte[] result = Arrays.copyOf(prefix, prefix.length+(int)length);
                System.arraycopy(data,i-4,result,prefix.length,(int)length); return result;
            }
        }
        return null;
    }

    private static final class SampleBytes {
        final byte[] bytes; final long latencyMs;
        SampleBytes(byte[] bytes, long latencyMs) { this.bytes = bytes; this.latencyMs = latencyMs; }
    }
    private static SampleBytes readBounded(Response response, long deadline, int limit, long started) throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        long latencyMs = -1;
        byte[] part = new byte[8192];
        java.io.InputStream input = response.body().byteStream();
        if ("gzip".equalsIgnoreCase(response.header("Content-Encoding"))) input = new java.util.zip.GZIPInputStream(input);
        try {
            while (buffer.size() < limit && System.nanoTime() < deadline && !Thread.currentThread().isInterrupted()) {
                int read = input.read(part, 0, Math.min(part.length, limit - buffer.size()));
                if (read <= 0) break;
                if (latencyMs < 0) latencyMs = (System.nanoTime() - started) / 1_000_000;
                buffer.write(part, 0, read);
                // Dimensions often appear in the first packets, but a 32 KiB CDN burst is
                // not a sustainable throughput sample. Keep a bounded 128 KiB window.
                if (buffer.size() >= MIN_THROUGHPUT_SAMPLE && buffer.size() % 32768 < read) {
                    byte[] head = buffer.toByteArray();
                    if (!(head[0] == '#' || head[0] == (byte) 0xef) && !containerSizes(head).isEmpty()) break;
                }
            }
        } catch (java.io.InterruptedIOException error) { if (buffer.size() == 0) throw error; }
        return new SampleBytes(buffer.toByteArray(), latencyMs);
    }

    private static List<Size> containerSizes(byte[] bytes) {
        List<Size> sizes = new ArrayList<>();
        for (Extractor extractor : new Extractor[]{
                // The one-int constructor takes payload flags, NOT a mode. That triggers duration seeks
                // before any video format is read and makes valid bounded HLS samples appear unknown.
                new TsExtractor(TsExtractor.MODE_HLS, new androidx.media3.common.util.TimestampAdjuster(0),
                        new androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory()),
                new FragmentedMp4Extractor(), new Mp4Extractor()}) {
            try {
                ByteArrayInputStream stream = new ByteArrayInputStream(bytes);
                DefaultExtractorInput input = new DefaultExtractorInput(stream::read, 0, bytes.length);
                if (!extractor.sniff(input)) continue;
                input.resetPeekPosition();
                extractor.init(new ExtractorOutput() {
                    @Override public TrackOutput track(int id, int type) {
                        return new TrackOutput() {
                            @Override public void format(Format format) {
                                if (type == C.TRACK_TYPE_VIDEO && format.width > 0 && format.height > 0) sizes.add(new Size(format.width, format.height, 0, Math.max(0, format.bitrate)));
                            }
                            @Override public int sampleData(DataReader reader, int length, boolean allowEnd, int part) throws java.io.IOException {
                                return reader.read(new byte[Math.min(length, 4096)], 0, Math.min(length, 4096));
                            }
                            @Override public void sampleData(ParsableByteArray data, int length, int part) { data.skipBytes(length); }
                            @Override public void sampleMetadata(long time, int flags, int size, int offset, CryptoData crypto) { }
                        };
                    }
                    @Override public void endTracks() { }
                    @Override public void seekMap(SeekMap map) { }
                });
                PositionHolder position = new PositionHolder();
                for (int steps = 0; steps < 4096 && sizes.isEmpty(); steps++) {
                    int status = extractor.read(input, position);
                    if (status == Extractor.RESULT_END_OF_INPUT || status == Extractor.RESULT_SEEK) break;
                }
                if (!sizes.isEmpty()) return sizes;
            } catch (Throwable ignored) { }
            finally { extractor.release(); }
        }
        return sizes;
    }
}
