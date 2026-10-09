package io.github.morningwn.protocol.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Request body for get_bot_qrcode.
 *
 * @param localTokenList most recently persisted local bot tokens, at most ten
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GetBotQrcodeRequest(
        @JsonProperty("local_token_list") List<String> localTokenList
) {
}
