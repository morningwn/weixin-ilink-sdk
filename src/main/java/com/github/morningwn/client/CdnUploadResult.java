package com.github.morningwn.client;

/**
 * Upload response details from CDN.
 *
 * @param statusCode HTTP status code
 * @param encryptedParam x-encrypted-param header
 */
public record CdnUploadResult(int statusCode, String encryptedParam) {
}
