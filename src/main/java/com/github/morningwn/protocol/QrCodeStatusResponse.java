package com.github.morningwn.protocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response payload for get_qrcode_status.
 *
 * @param status qr status
 * @param redirectHost idc redirect host
 * @param botToken bot token when confirmed
 * @param ilinkBotId bot account id
 * @param ilinkUserId wechat user id
 * @param baseurl business base url
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record QrCodeStatusResponse(
        @JsonProperty("status") String status,
        @JsonProperty("redirect_host") String redirectHost,
        @JsonProperty("bot_token") String botToken,
        @JsonProperty("ilink_bot_id") String ilinkBotId,
        @JsonProperty("ilink_user_id") String ilinkUserId,
        @JsonProperty("baseurl") String baseurl
) {
}
