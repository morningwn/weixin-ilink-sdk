package io.github.morningwn.protocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response payload for get_bot_qrcode.
 *
 * @param qrcode qr polling token
 * @param qrcodeImgContent qr image content URL
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record QrCodeResponse(
        @JsonProperty("qrcode") String qrcode,
        @JsonProperty("qrcode_img_content") String qrcodeImgContent
) {
}
