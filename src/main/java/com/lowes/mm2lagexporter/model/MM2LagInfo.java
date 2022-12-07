package com.lowes.mm2lagexporter.model;


import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.stereotype.Component;

import java.util.Map;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Component
public class MM2LagInfo {
    private String name;
    private String sourceClusterBrokerUrl;
    private String targetClusterBrokerUrl;
    private String sourceClusterAlias;
    private String targetClusterAlias;
    private Map<String, ConnectorInfo> connector;
}
