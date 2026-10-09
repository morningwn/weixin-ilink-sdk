package io.github.morningwn.protocol.message;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.JsonNode;

import java.util.LinkedHashMap;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class UnknownMessageItem implements MessageItem {

    private Integer typeCode;
    private final Map<String, JsonNode> fields = new LinkedHashMap<>();

    public UnknownMessageItem() {
    }

    public UnknownMessageItem(Integer typeCode) {
        this.typeCode = typeCode;
    }

    @Override
    @JsonProperty("type")
    public Integer typeCode() {
        return typeCode;
    }

    @JsonProperty("type")
    public void setTypeCode(Integer typeCode) {
        this.typeCode = typeCode;
    }

    @JsonAnySetter
    public void put(String name, JsonNode value) {
        if (!"type".equals(name)) {
            fields.put(name, value);
        }
    }

    @JsonAnyGetter
    public Map<String, JsonNode> fields() {
        return Map.copyOf(fields);
    }
}
