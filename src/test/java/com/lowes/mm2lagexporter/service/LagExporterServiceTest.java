package com.lowes.mm2lagexporter.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowes.mm2lagexporter.config.ConnectorConfig;
import com.lowes.mm2lagexporter.exception.MMLagExporterException;
import com.lowes.mm2lagexporter.model.MM2LagInfo;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sends the Lag information to REST API call and
 * created a scheduler to get the lag information from
 * the topics and to update the metrics.
 */
@Slf4j
@ExtendWith(MockitoExtension.class)
class LagExporterServiceTest {

    @InjectMocks
    private LagExporterService lagExporterService;

    @Mock
    private MM2LagInfo mM2LagInfo;

    @Mock
    private ConnectorConfig connectorConfig;

    @Mock
    private MeterRegistry meterRegistry;

    @BeforeEach
    public void setUp() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            String mmLagDetails = "{\"name\":\"test-mirrorsourceconnector\",\"sourceClusterBrokerUrl\":\"source_broker:9092\",\"targetClusterBrokerUrl\":\"target_broker:9092\",\"sourceClusterAlias\":\"srecluster\",\"targetClusterAlias\":\"tgcluster\",\"connector\":{\"test-mirrorsourceconnector\":{\"connectorName\":\"test-mirrorsourceconnector\",\"topics\":{\"test-topic\":{\"topicName\":\"test-topic\",\"partitions\":{\"0\":{\"topicName\":\"test-topic\",\"partition\":0,\"logEndOffset\":110,\"logEndOffsetUpdatedAt\":\"2022-05-24T09:34:27.181+00:00\",\"mmOffset\":100,\"mmOffsetUpdatedAt\":\"2022-05-24T09:33:21.129+00:00\",\"lag\":10},\"1\":{\"topicName\":\"test-topic\",\"partition\":1,\"logEndOffset\":100,\"logEndOffsetUpdatedAt\":\"2022-05-24T09:34:27.181+00:00\",\"mmOffset\":100,\"mmOffsetUpdatedAt\":\"2022-05-24T09:33:16.818+00:00\",\"lag\":0},\"2\":{\"topicName\":\"test-topic\",\"partition\":2,\"logEndOffset\":100,\"logEndOffsetUpdatedAt\":\"2022-05-24T09:34:27.181+00:00\",\"mmOffset\":100,\"mmOffsetUpdatedAt\":\"2022-05-24T09:33:21.079+00:00\",\"lag\":0}}}}}}}";
            mM2LagInfo = mapper.readValue(mmLagDetails, MM2LagInfo.class);
            connectorConfig = new ConnectorConfig();
            String connectorJson = "{\"test-mirrorsourceconnector\":[\"test-topic\"]}";
            connectorConfig.setConnectors(mapper.readValue(connectorJson, Map.class));
        } catch (Exception ex) {
            log.error(" Exception in LagExporterServiceTest {}", ex.getMessage());
        }
        lagExporterService = new LagExporterService(mM2LagInfo, connectorConfig, meterRegistry);
    }

    @Test
    void getCurrentLagInfoNoInfo() {
        lagExporterService = new LagExporterService(null, connectorConfig, meterRegistry);
        MM2LagInfo response = lagExporterService.getCurrentLagInfo();
        assertThat(response).isNull();
    }

    @Test
    void getCurrentLagInfo() {
        MM2LagInfo mM2LagInfo = new MM2LagInfo();
        mM2LagInfo.setName("test-topic");
        lagExporterService = new LagExporterService(mM2LagInfo, connectorConfig, meterRegistry);
        MM2LagInfo response = lagExporterService.getCurrentLagInfo();
        assertThat(response.getName()).isEqualTo("test-topic");
    }

    @Test
    void getTopicLagInfoNullLagInfo() throws MMLagExporterException {
        MMLagExporterException thrown = Assertions.assertThrows(MMLagExporterException.class, () -> {
            lagExporterService.getTopicLagInfo("topic");
        });
        Assertions.assertEquals(MMLagExporterException.class, thrown.getClass());
    }

    @Test
    void getTopicLagInfoNoConnectorData() throws MMLagExporterException {
        MMLagExporterException thrown = Assertions.assertThrows(MMLagExporterException.class, () -> {
            MM2LagInfo mM2LagInfo = new MM2LagInfo();
            mM2LagInfo.setName("test-topic");
            mM2LagInfo.setConnector(new HashMap<>());
            lagExporterService = new LagExporterService(mM2LagInfo, connectorConfig, meterRegistry);
            lagExporterService.getTopicLagInfo("topic");
        });
        Assertions.assertEquals(MMLagExporterException.class, thrown.getClass());
    }

    @Test
    void getTopicLagInfoNoTopicInfo() throws MMLagExporterException {
        MMLagExporterException thrown = Assertions.assertThrows(MMLagExporterException.class, () -> {
            lagExporterService.getTopicLagInfo("topic");
        });
        Assertions.assertEquals(MMLagExporterException.class, thrown.getClass());
    }

    @Test()
    void getTopicLagInfo() throws MMLagExporterException {
        MM2LagInfo response = lagExporterService.getTopicLagInfo("test-topic");
        Assertions.assertNotNull(response);
    }

    @Test()
    void getLagForMetrics() throws MMLagExporterException {
        lagExporterService.getLagForMetrics();
    }

}
