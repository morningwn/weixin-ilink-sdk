package io.github.morningwn.client;

/**
 * Auth session returned by confirmed QR login status.
 *
 * @param token bot token
 * @param baseUrl business base url
 * @param accountId bot account id
 * @param userId authorized user id
 */
public record ILinkAuthSession(
        String token,
        String baseUrl,
        String accountId,
        String userId
) {
}
