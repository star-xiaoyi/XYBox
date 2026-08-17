package com.fongmi.android.tv.player.exo;

import androidx.media3.common.PlaybackException;

public class ErrorMsgProvider {

    public String get(PlaybackException e) {
        return switch (e.errorCode) {
            case PlaybackException.ERROR_CODE_TIMEOUT -> "连接超时";
            case PlaybackException.ERROR_CODE_UNSPECIFIED -> "未知错误";
            case PlaybackException.ERROR_CODE_IO_UNSPECIFIED -> "IO错误";
            case PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "HTTP状态错误";
            case PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE -> "无效的HTTP内容类型";
            case PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> "网络连接失败";
            case PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "网络连接超时";
            case PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE -> "读取位置超出范围";
            case PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED -> "清单格式错误";
            case PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED -> "容器格式错误";
            case PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED -> "不支持的清单格式";
            case PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED -> "不支持的容器格式";
            case PlaybackException.ERROR_CODE_DECODER_INIT_FAILED -> "解码器初始化失败";
            case PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED -> "解码器查询失败";
            case PlaybackException.ERROR_CODE_DECODING_FAILED -> "解码失败";
            case PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED -> "不支持的解码格式";
            case PlaybackException.ERROR_CODE_DECODING_RESOURCES_RECLAIMED -> "解码资源被回收";
            case PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES -> "解码格式超出设备能力";
            case PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED -> "音频轨道初始化失败";
            case PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED -> "音频轨道写入失败";
            case PlaybackException.ERROR_CODE_DRM_UNSPECIFIED -> "DRM错误";
            case PlaybackException.ERROR_CODE_DRM_SYSTEM_ERROR -> "DRM系统错误";
            case PlaybackException.ERROR_CODE_DRM_CONTENT_ERROR -> "DRM内容错误";
            case PlaybackException.ERROR_CODE_DRM_DEVICE_REVOKED -> "DRM设备已吊销";
            case PlaybackException.ERROR_CODE_DRM_LICENSE_EXPIRED -> "DRM许可证已过期";
            case PlaybackException.ERROR_CODE_DRM_PROVISIONING_FAILED -> "DRM配置失败";
            case PlaybackException.ERROR_CODE_DRM_DISALLOWED_OPERATION -> "DRM不允许的操作";
            case PlaybackException.ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED -> "DRM许可证获取失败";
            default -> "播放错误: " + e.getErrorCodeName();
        };
    }
}
