package com.lowes.mm2lagexporter.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@Configuration
public class ConnectorConfig {

    @Value("#{${connectors}}")
    private Map<String, List<String>> connectors;

    @Value("${source.cluster.bootstrap.servers}")
    private String sourceBrokerUrl;

    @Value("${source.cluster.alias}")
    private String sourceClusterAlias;

    @Value("${target.cluster.bootstrap.servers}")
    private String targetBrokerUrl;

    @Value("${target.cluster.alias}")
    private String targetClusterAlias;

    @Value("${auto.restart.onfailure.enabled:false}")
    private boolean autoRestartConsumerEnabled;

}
