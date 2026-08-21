package com.fongmi.android.tv.download;

/** 下载过程回调：进度上报 + 取消检查。 */
public interface Progress {

    boolean isCancelled();

    /**
     * @param percent    0-100
     * @param doneBytes  已下载字节
     * @param totalBytes 总字节，未知时为 0
     * @param doneSeg    已完成分片数，直链下载为 0
     * @param totalSeg   分片总数，直链下载为 0
     * @param speed      瞬时速度（字节/秒）
     */
    void onProgress(int percent, long doneBytes, long totalBytes, int doneSeg, int totalSeg, long speed);
}
