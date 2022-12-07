package com.lowes.mm2lagexporter.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.lowes.mm2lagexporter.utils.Status;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class ConsumerStatus {

    @JsonProperty("TYPE")
    private String type;
    @JsonProperty("STATUS")
    private Status status;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonProperty("TRACE")
    private String trace;
}
