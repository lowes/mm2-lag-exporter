package com.lowes.mm2lagexporter.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class ConnectorInfo {
    private String connectorName;
    private Map<String, TopicInfo> topics;
}
