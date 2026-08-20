import io

# ---------------- Download: 进度节流 + 重试不回退到 0 + 用 body 的长度校验 ----------------
p = 'app/src/main/java/com/fongmi/android/tv/utils/Download.java'
s = io.open(p, encoding='utf-8').read()


def rep(old, new, cnt=1):
    global s
    assert old in s, old[:140]
    s = s.replace(old, new, cnt)


rep("""            try {
                if (callback != null) {
                    App.post(() -> callback.progress(0));
                }

                boolean success = downloadWithUrl(downloadUrl, source, attempt);""",
    """            try {
                // 这里原本会把进度打回 0，重试一次进度条就退回原点，看起来像"下到 1% 又归零"
                if (callback != null && attempt > 1) {
                    App.post(() -> callback.retry());
                }

                boolean success = downloadWithUrl(downloadUrl, source, attempt);""")

rep("""            // 获取文件大小，如果无法获取则使用-1表示未知大小
            String contentLengthStr = res.header(HttpHeaders.CONTENT_LENGTH);
            long expectedLength = -1;
            if (contentLengthStr != null && !contentLengthStr.isEmpty()) {
                try {
                    expectedLength = Long.parseLong(contentLengthStr);
                    if (expectedLength < 0) {
                        expectedLength = -1;
                    }
                } catch (NumberFormatException e) {
                    Logger.w("Download: 无法解析Content-Length: " + contentLengthStr);
                    expectedLength = -1;
                }
            }""",
    """            // 用 body 的长度而不是原始 Content-Length 头：响应被 gzip 压缩时头里是压缩后的字节数，
            // 拿它去校验解压后的文件必然对不上，会误判成"文件损坏"然后一直重试。
            long expectedLength = res.body().contentLength();
            if (expectedLength <= 0) expectedLength = -1;""")

rep("""            byte[] buffer = new byte[4096];
            int readBytes;
            long totalBytes = 0;
            while ((readBytes = input.read(buffer)) != -1) {
                totalBytes += readBytes;
                os.write(buffer, 0, readBytes);

                // 只有当知道文件大小时才计算进度
                if (length > 0 && callback != null) {
                    int progress = (int) (totalBytes * 100.0 / length);
                    final int finalProgress = Math.min(progress, 100); // 确保不超过100%，并设为final
                    App.post(() -> callback.progress(finalProgress));
                } else if (callback != null) {
                    // 不知道文件大小时，显示不确定进度
                    App.post(() -> callback.progress(-1));
                }
            }

            // 下载完成后，如果不知道文件大小，显示100%
            if (length <= 0 && callback != null) {
                App.post(() -> callback.progress(100));
            }""",
    """            byte[] buffer = new byte[8192];
            int readBytes;
            long totalBytes = 0;
            int lastPercent = -1;
            while ((readBytes = input.read(buffer)) != -1) {
                totalBytes += readBytes;
                os.write(buffer, 0, readBytes);
                if (length <= 0 || callback == null) continue;
                // 按整数百分比节流：一个 37MB 的包按 8KB 回调会往主线程丢四千多条消息，
                // 进度条光排队就跟不上，看着像卡住甚至往回跳。
                int percent = Math.min((int) (totalBytes * 100 / length), 100);
                if (percent == lastPercent) continue;
                lastPercent = percent;
                App.post(() -> callback.progress(percent));
            }

            // 不知道文件大小时全程走不确定态，结束时补一个 100
            if (length <= 0 && callback != null) {
                App.post(() -> callback.progress(100));
            }""")

rep("""    public interface Callback {

        void progress(int progress);""",
    """    public interface Callback {

        void progress(int progress);

        /** 一次尝试失败、即将重试。进度条保持原样，只提示状态 */
        default void retry() {
        }""")

rep("import com.google.common.net.HttpHeaders;\n", "")

io.open(p, 'w', encoding='utf-8').write(s)
print('download ok')
