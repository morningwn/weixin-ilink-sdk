package io.github.morningwn.protocol.response;

/**
 * 下载媒体的内容与类型信息。
 *
 * @param content     媒体字节内容
 * @param contentType 响应 Content-Type，可能为空
 */
public record DownloadedMedia(byte[] content, String contentType) {
}
