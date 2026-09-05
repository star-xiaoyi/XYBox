package com.fongmi.android.tv.player.exo;

import android.content.Context;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.accessibility.CaptioningManager;

import androidx.media3.common.MediaItem;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.Tracks;
import androidx.media3.common.util.Util;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.LoadControl;
import androidx.media3.exoplayer.RenderersFactory;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.exoplayer.trackselection.TrackSelector;
import androidx.media3.ui.CaptionStyleCompat;
import androidx.media3.ui.PlayerView;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.BuildConfig;
import com.fongmi.android.tv.Setting;
import com.fongmi.android.tv.bean.Drm;
import com.fongmi.android.tv.bean.Sub;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.NextRenderersFactory;

public class ExoUtil {

    public static String getUa() {
        return Util.getUserAgent(App.get(), BuildConfig.APPLICATION_ID);
    }

    public static LoadControl buildLoadControl() {
        // 原实现把默认 50 秒直接乘 1~10，最高档意图达到 500 秒。若真正按时间执行，
        // 高码率片源可能吃掉数百 MB 内存。这里保留档位含义，但收敛到 50~95 秒。
        int bufferMs = DefaultLoadControl.DEFAULT_MIN_BUFFER_MS + (Setting.getBuffer() - 1) * 5000;
        return new DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                        bufferMs,
                        bufferMs,
                        1500,
                        5000)
                // 保留 Media3 的默认字节上限，避免高码率视频仅为追满时长而占用过多内存。
                .setBackBuffer(15 * 1000, true)
                .build();
    }

    public static TrackSelector buildTrackSelector() {
        DefaultTrackSelector trackSelector = new DefaultTrackSelector(App.get());
        DefaultTrackSelector.Parameters.Builder builder = trackSelector.buildUponParameters();
        if (Setting.isPreferAAC()) builder.setPreferredAudioMimeType(MimeTypes.AUDIO_AAC);
        builder.setPreferredTextLanguage(Locale.getDefault().getISO3Language());
        builder.setTunnelingEnabled(Setting.isTunnel());
        // 不强制最高码率，让 HLS/DASH 的自适应轨道能根据带宽和缓冲量升降档。
        trackSelector.setParameters(builder.build());
        return trackSelector;
    }

    public static RenderersFactory buildRenderersFactory(int renderMode) {
        return new NextRenderersFactory(App.get()).setEnableDecoderFallback(true).setExtensionRendererMode(renderMode);
    }

    public static MediaSource.Factory buildMediaSourceFactory() {
        return new MediaSourceFactory();
    }

    public static CaptionStyleCompat getCaptionStyle() {
        return Setting.isCaption() ? CaptionStyleCompat.createFromCaptionStyle(((CaptioningManager) App.get().getSystemService(Context.CAPTIONING_SERVICE)).getUserStyle()) : new CaptionStyleCompat(Color.WHITE, Color.TRANSPARENT, Color.TRANSPARENT, CaptionStyleCompat.EDGE_TYPE_OUTLINE, Color.BLACK, null);
    }

    public static boolean haveTrack(Tracks tracks, int type) {
        int count = 0;
        for (Tracks.Group trackGroup : tracks.getGroups()) {
            if (trackGroup.getType() != type) continue;
            for (int i = 0; i < trackGroup.length; i++) if (trackGroup.isTrackSupported(i)) count++;
        }
        return count > 0;
    }

    public static void selectTrack(ExoPlayer player, int group, int track) {
        if (!isTrackValid(player, group, track)) return;
        List<Integer> trackIndices = new ArrayList<>();
        selectTrack(player, group, track, trackIndices);
        setTrackParameters(player, group, trackIndices);
    }

    public static void selectVideoQuality(ExoPlayer player, int group, int track) {
        if (!isTrackValid(player, group, track)) return;
        Tracks.Group tracks = player.getCurrentTracks().getGroups().get(group);
        if (tracks.getType() != C.TRACK_TYPE_VIDEO || !tracks.isTrackSupported(track)) return;
        Format format = tracks.getTrackFormat(track);
        // 清晰度作为自适应上限，而不是把播放器锁死到某一条码流。弱网时仍可自动降档，
        // DASH/HLS 的某一路临时不可用时也能选择同上限内的其他视频轨。
        androidx.media3.common.TrackSelectionParameters.Builder builder = player.getTrackSelectionParameters()
                .buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                .clearVideoSizeConstraints()
                .setMaxVideoBitrate(Integer.MAX_VALUE);
        if (format.width > 0 && format.height > 0) builder.setMaxVideoSize(format.width, format.height);
        if (format.bitrate > 0) builder.setMaxVideoBitrate(format.bitrate);
        player.setTrackSelectionParameters(builder.build());
    }

    public static void deselectTrack(ExoPlayer player, int group, int track) {
        if (!isTrackValid(player, group, track)) return;
        List<Integer> trackIndices = new ArrayList<>();
        deselectTrack(player, group, track, trackIndices);
        setTrackParameters(player, group, trackIndices);
    }

    public static void resetTrack(ExoPlayer player) {
        player.setTrackSelectionParameters(player.getTrackSelectionParameters()
                .buildUpon()
                .clearOverrides()
                .clearVideoSizeConstraints()
                .setMaxVideoBitrate(Integer.MAX_VALUE)
                .build());
    }

    public static void resetTrack(ExoPlayer player, int type) {
        androidx.media3.common.TrackSelectionParameters.Builder builder = player.getTrackSelectionParameters()
                .buildUpon()
                .clearOverridesOfType(type);
        if (type == C.TRACK_TYPE_VIDEO) {
            builder.clearVideoSizeConstraints().setMaxVideoBitrate(Integer.MAX_VALUE);
        }
        player.setTrackSelectionParameters(builder.build());
    }

    public static void setSubtitleView(PlayerView exo) {
        exo.getSubtitleView().setStyle(getCaptionStyle());
        exo.getSubtitleView().setApplyEmbeddedFontSizes(false);
        exo.getSubtitleView().setApplyEmbeddedStyles(!Setting.isCaption());
        if (Setting.getSubtitleTextSize() != 0) exo.getSubtitleView().setFractionalTextSize(Setting.getSubtitleTextSize());
    }

    public static String getMimeType(String path) {
        if (TextUtils.isEmpty(path)) return "";
        if (path.endsWith(".vtt")) return MimeTypes.TEXT_VTT;
        if (path.endsWith(".ssa") || path.endsWith(".ass")) return MimeTypes.TEXT_SSA;
        if (path.endsWith(".ttml") || path.endsWith(".xml") || path.endsWith(".dfxp")) return MimeTypes.APPLICATION_TTML;
        return MimeTypes.APPLICATION_SUBRIP;
    }

    public static String getMimeType(int errorCode) {
        // 无扩展名的 HLS 地址会先被当作普通容器探测，失败后用 HLS 再试一次。
        // 普通 IO 错误和已经识别出的 manifest 错误不能硬套成 HLS，否则只会重复报错。
        if (errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED || errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED) return MimeTypes.APPLICATION_M3U8;
        return null;
    }

    public static boolean isMimeType(String value, String mimeType) {
        if (TextUtils.isEmpty(value)) return false;
        if (mimeType.equalsIgnoreCase(value)) return true;
        return MimeTypes.APPLICATION_M3U8.equals(mimeType) && (value.toLowerCase(Locale.US).contains("mpegurl") || value.toLowerCase(Locale.US).contains("m3u8"));
    }

    public static MediaItem getMediaItem(Map<String, String> headers, Uri uri, String mimeType, Drm drm, List<Sub> subs, int decode) {
        MediaItem.Builder builder = new MediaItem.Builder().setUri(uri);
        builder.setRequestMetadata(getRequestMetadata(headers, uri));
        builder.setSubtitleConfigurations(getSubtitleConfigs(subs));
        if (drm != null) builder.setDrmConfiguration(drm.get());
        if (!TextUtils.isEmpty(mimeType)) builder.setMimeType(mimeType);
        builder.setMediaId(uri.toString());
        builder.setImageDurationMs(15000);
        return builder.build();
    }

    private static MediaItem.RequestMetadata getRequestMetadata(Map<String, String> headers, Uri uri) {
        Bundle extras = new Bundle();
        for (Map.Entry<String, String> header : headers.entrySet()) extras.putString(header.getKey(), header.getValue());
        return new MediaItem.RequestMetadata.Builder().setMediaUri(uri).setExtras(extras).build();
    }

    private static List<MediaItem.SubtitleConfiguration> getSubtitleConfigs(List<Sub> subs) {
        List<MediaItem.SubtitleConfiguration> configs = new ArrayList<>();
        if (subs != null) for (Sub sub : subs) configs.add(sub.config());
        return configs;
    }

    private static void selectTrack(ExoPlayer player, int group, int track, List<Integer> trackIndices) {
        if (!isTrackValid(player, group, track)) return;
        Tracks.Group trackGroup = player.getCurrentTracks().getGroups().get(group);
        for (int i = 0; i < trackGroup.length; i++) {
            if (i == track || trackGroup.isTrackSelected(i)) trackIndices.add(i);
        }
    }

    private static void deselectTrack(ExoPlayer player, int group, int track, List<Integer> trackIndices) {
        if (!isTrackValid(player, group, track)) return;
        Tracks.Group trackGroup = player.getCurrentTracks().getGroups().get(group);
        for (int i = 0; i < trackGroup.length; i++) {
            if (i != track && trackGroup.isTrackSelected(i)) trackIndices.add(i);
        }
    }

    private static void setTrackParameters(ExoPlayer player, int group, List<Integer> trackIndices) {
        if (group < 0 || group >= player.getCurrentTracks().getGroups().size()) return;
        player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon().setOverrideForType(new TrackSelectionOverride(player.getCurrentTracks().getGroups().get(group).getMediaTrackGroup(), trackIndices)).build());
    }

    private static boolean isTrackValid(ExoPlayer player, int group, int track) {
        if (group < 0 || group >= player.getCurrentTracks().getGroups().size()) return false;
        return track >= 0 && track < player.getCurrentTracks().getGroups().get(group).length;
    }
}
