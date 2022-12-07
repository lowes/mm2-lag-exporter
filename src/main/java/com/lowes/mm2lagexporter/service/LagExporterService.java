package com.lowes.mm2lagexporter.service;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.lowes.mm2lagexporter.config.ConnectorConfig;
import com.lowes.mm2lagexporter.exception.MMLagExporterException;
import com.lowes.mm2lagexporter.model.ConnectorInfo;
import com.lowes.mm2lagexporter.model.MM2LagInfo;
import com.lowes.mm2lagexporter.model.TopicInfo;
import com.lowes.mm2lagexporter.utils.Constants;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * This class Sends the mirroring lag information to REST API call and
 * also scheduler is available to get the lag information from
 * the topics and to update the metrics.
 */
@Service
public class LagExporterService {
    private final MM2LagInfo mM2LagInfo;
    private final ConnectorConfig connectorConfig;
    private final MeterRegistry meterRegistry;
    private final Map<String, AtomicLong> logEndOffset;
    private final Map<String, AtomicLong> mmOffset;

    public LagExporterService(MM2LagInfo mM2LagInfo,
                              ConnectorConfig connectorConfig,
                              MeterRegistry meterRegistry) {
        this.mM2LagInfo = mM2LagInfo;
        this.connectorConfig = connectorConfig;
        this.meterRegistry = meterRegistry;
        logEndOffset = new HashMap<>();
        mmOffset = new HashMap<>();
    }

    /**
     * @return returns the mirrormaker lag information to the REST API call.
     */
    public MM2LagInfo getCurrentLagInfo() {
        return mM2LagInfo;
    }

    /**
     * the mirror maker lag information for the specific topic to the REST API call.
     *
     * @param topicName TopicName
     * @return lag information
     * @throws MMLagExporterException MMLagExporterException
     */
    public MM2LagInfo getTopicLagInfo(String topicName) throws MMLagExporterException {

        // JVM cache is exist validation
        if (mM2LagInfo == null || mM2LagInfo.getConnector().size() == 0) {
            throw new MMLagExporterException(Constants.ERROR_MIRRORING_NOT_EXIST, HttpStatus.BAD_REQUEST);
        }

        // Validate / filter the topic exist and get topic directions details from mirroring directions
        Map<String, ConnectorInfo> connectorInfo = mM2LagInfo.getConnector().entrySet().stream()
                .filter(e -> !CollectionUtils.isEmpty(e.getValue().getTopics()) && e.getValue().getTopics().containsKey(topicName))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        if (CollectionUtils.isEmpty(connectorInfo)) {
            // Exception as topic not exit in mirroring direction
            throw new MMLagExporterException(Constants.ERROR_TOPIC_NOT_EXIST, HttpStatus.BAD_REQUEST);
        }

        // filter topic details for directions
        connectorInfo.entrySet().stream().forEach(cont -> {
            Map<String, TopicInfo> topicInfo = cont.getValue().getTopics().entrySet().stream()
                    .filter(e -> e.getValue() != null && e.getValue().getTopicName().equals(topicName))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            cont.getValue().setTopics(topicInfo);
        });

        // Build response with direction and topic details
        MM2LagInfo topicLagInfo = new MM2LagInfo();
        topicLagInfo.setName(mM2LagInfo.getName());
        topicLagInfo.setSourceClusterAlias(mM2LagInfo.getSourceClusterAlias());
        topicLagInfo.setSourceClusterBrokerUrl(mM2LagInfo.getSourceClusterBrokerUrl());
        topicLagInfo.setTargetClusterAlias(mM2LagInfo.getTargetClusterAlias());
        topicLagInfo.setTargetClusterBrokerUrl(mM2LagInfo.getTargetClusterBrokerUrl());
        topicLagInfo.setConnector(connectorInfo);

        return topicLagInfo;
    }

    /**
     * push the logend offset and mirrormaker2 offset metrics to prometheus
     * and this method will get called by a scheduler to update the latest metrics.
     */
    private void getCurrentLagForMetrics() {
        List<String> topicsList;

        for (Map.Entry<String, List<String>> connectors : connectorConfig.getConnectors().entrySet()) {
            String connector = connectors.getKey();
            topicsList = connectors.getValue();

            // Validate the topic exist or not
            Preconditions.checkState(!Strings.isNullOrEmpty(topicsList.toString()), "Topic Name is Empty or Null");
            ConnectorInfo connectorInfo;
            if (mM2LagInfo.getConnector() != null) {
                connectorInfo = mM2LagInfo.getConnector().get(connector);
                if (connectorInfo != null) {
                    updateConnectorLagMetrics(connector, connectorInfo);
                }
            }
        }
    }

    private void updateConnectorLagMetrics(String connector, ConnectorInfo connectorInfo) {
        for (String topic : connectorInfo.getTopics().keySet()) {
            if (connectorInfo.getTopics().get(topic).getPartitions() != null) {
                updateTopicPartitionLagMetrics(connector, connectorInfo, topic);
            }
        }
    }

    private void updateTopicPartitionLagMetrics(String connector, ConnectorInfo connectorInfo, String topic) {
        String keyString;
        Set<Integer> partitions = connectorInfo.getTopics().get(topic).getPartitions().keySet();
        for (Integer partition : partitions) {
            keyString = String.format("[connector-%s,topic-%s,partition-%s]", connector, topic, partition);
            if (!logEndOffset.containsKey(keyString)) {
                logEndOffset.put(keyString, new AtomicLong(0));

                // Push the logend offset metrics of the topics to prometheus
                Gauge.builder(Constants.LOGENDOFFSET_METRICS_NAME, logEndOffset.get(keyString), Number::doubleValue)
                        .tag(Constants.CONNECTOR, connectorInfo.getConnectorName())
                        .tag(Constants.SOURCE_CLUSTER, mM2LagInfo.getSourceClusterAlias())
                        .tag(Constants.TARGET_CLUSTER, mM2LagInfo.getTargetClusterAlias())
                        .tag(Constants.TOPIC, topic)
                        .tag(Constants.PARTITION, String.valueOf(partition))
                        .description(Constants.LOGENDOFFSET_METRICS_DOC)
                        .register(meterRegistry);

                mmOffset.put(keyString, new AtomicLong(0));

                // Push the mirrored offset metrics of the topics to prometheus
                Gauge.builder(Constants.MMOFFSET_METRICS_NAME, mmOffset.get(keyString), Number::doubleValue)
                        .tag(Constants.CONNECTOR, connectorInfo.getConnectorName())
                        .tag(Constants.SOURCE_CLUSTER, mM2LagInfo.getSourceClusterAlias())
                        .tag(Constants.TARGET_CLUSTER, mM2LagInfo.getTargetClusterAlias())
                        .tag(Constants.TOPIC, topic)
                        .tag(Constants.PARTITION, String.valueOf(partition))
                        .description(Constants.MMOFFSET_METRICS_DOC)
                        .register(meterRegistry);
            }
            // Update logend offset and mirrored offset with latest offset values
            logEndOffset.get(keyString).set(connectorInfo.getTopics().get(topic).getPartitions().get(partition).getLogEndOffset());
            mmOffset.get(keyString).set(connectorInfo.getTopics().get(topic).getPartitions().get(partition).getMmOffset());
        }
    }

    /**
     * Scheduler to push the lag metrics in specific interval.Interval time is defaulted to 1 second with initial delay of 20 seconds.
     */
    @Scheduled(fixedRateString = Constants.SCHEDULER_FREQUENCY, initialDelayString = Constants.SCHEDULER_INITIAL_DELAY)
    public void getLagForMetrics() {
        getCurrentLagForMetrics();
    }
}
