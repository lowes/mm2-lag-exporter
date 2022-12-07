package com.lowes.mm2lagexporter.service;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.lowes.mm2lagexporter.config.ConnectorConfig;
import com.lowes.mm2lagexporter.model.*;
import com.lowes.mm2lagexporter.utils.Constants;
import com.lowes.mm2lagexporter.utils.Status;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Creates a new Consumer instance to read the logend offset of the topics in the source cluster.
 */
@Slf4j
@Service
public class SourceConsumerService {
    private final MM2LagInfo mM2LagInfo;
    private final ConnectorConfig connectorConfig;
    private final ConsumerStatus sourceConsumerStatus;
    private final Properties sourceConsumerProperties;
    private ConnectorInfo connectorInfo;
    private KafkaConsumer<String, String> sourceConsumer;

    public SourceConsumerService(MM2LagInfo mM2LagInfo,
                                 ConnectorConfig connectorConfig,
                                 ConsumerStatus sourceConsumerStatus,
                                 @Qualifier("sourceConsumerProperties") Properties sourceConsumerProperties,
                                 @Qualifier("sourceConsumer") KafkaConsumer<String, String> sourceConsumer) {
        this.mM2LagInfo = mM2LagInfo;
        this.connectorConfig = connectorConfig;
        this.sourceConsumerStatus = sourceConsumerStatus;
        this.sourceConsumerProperties = sourceConsumerProperties;
        this.sourceConsumer = sourceConsumer;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Async("sourceTaskExecutor")
    public void runSourceConsumer() {
        log.info("mm2-lag-exporter::Starting New Source Kafka Consumer");
        Map<String, ConnectorInfo> connectorMap;
        List<String> topicsList;
        sourceConsumerStatus.setStatus(Status.RUNNING);
        sourceConsumerStatus.setTrace("");
        mM2LagInfo.setSourceClusterBrokerUrl(connectorConfig.getSourceBrokerUrl());
        mM2LagInfo.setSourceClusterAlias(connectorConfig.getSourceClusterAlias());
        try {
            while (true) {
                for (Map.Entry<String, List<String>> connectors : connectorConfig.getConnectors().entrySet()) {
                    String connector = connectors.getKey();
                    topicsList = connectors.getValue();
                    Preconditions.checkState(!Strings.isNullOrEmpty(topicsList.toString()), "Topic Name is Empty or Null");

                    if (mM2LagInfo.getConnector() == null) {
                        connectorMap = createConnectorMapFromSource(connector);
                        mM2LagInfo.setConnector(connectorMap);
                        connectorInfo = mM2LagInfo.getConnector().get(connector);
                    } else if (mM2LagInfo.getConnector().containsKey(connector)) {
                        connectorInfo = mM2LagInfo.getConnector().get(connector);
                    } else if (!mM2LagInfo.getConnector().containsKey(connector)) {
                        connectorInfo = new ConnectorInfo(connector, null);
                        mM2LagInfo.getConnector().put(connector, connectorInfo);
                    }
                    Iterator<String> topicIterator = topicsList.iterator();
                    while (topicIterator.hasNext()) {
                        String topicName = topicIterator.next();
                        processTopicDetail(topicName, topicIterator);
                    }
                }
            }
        } catch (Exception ex) {
            log.error("mm2-lag-exporter::Error in source consumer instance", ex);
            sourceConsumerStatus.setStatus(Status.FAILED);
            sourceConsumerStatus.setTrace(ExceptionUtils.getStackTrace(ex));
        } finally {
            log.info("mm2-lag-exporter::Closing source cluster consumer");
            sourceConsumer.close();
        }
    }

    private void processTopicDetail(String topicName, Iterator<String> topicIterator) {

        //calculate log end offset of topic only if it's exists in the source cluster.
        List<PartitionInfo> topicPartitionInfo = sourceConsumer.partitionsFor(topicName);
        if (topicPartitionInfo != null) {

            // Getting partition information for each topic in the white listed topics.
            Collection<TopicPartition> topicPartitions =
                    topicPartitionInfo.stream()
                            .map(partitionInfo -> new TopicPartition(partitionInfo.topic(), partitionInfo.partition()))
                            .collect(Collectors.toList());

            // Fetching Log end offsets for each Partitions in the topic.
            sourceConsumer.endOffsets(topicPartitions).forEach((topic, offset) -> {
                        String topicValue = topic.topic();
                        int partitionValue = topic.partition();
                        long offsetValue = offset;

                        if (connectorInfo.getTopics() == null) {
                            // If Map is null
                            connectorInfo.setTopics(createTopicInfoMap(topicValue, partitionValue, offsetValue));
                        } else if (connectorInfo.getTopics().containsKey(topicValue)) {

                            // If the topic is available in the Map
                            if (connectorInfo.getTopics().get(topicValue).getPartitions().containsKey(partitionValue)) {
                                connectorInfo.getTopics().get(topicValue).getPartitions().get(partitionValue).setLogEndOffset(offsetValue);
                                connectorInfo.getTopics().get(topicValue).getPartitions().get(partitionValue).setLogEndOffsetUpdatedAt(Timestamp.valueOf(LocalDateTime.now()));
                            } else {

                                //if new Partition is added to the topics
                                PartitionOffsetInfo partitionOffsetInfo = createPartitionOffsetInfo(topicValue, partitionValue, offsetValue);
                                connectorInfo.getTopics().get(topicValue).getPartitions().put(partitionValue, partitionOffsetInfo);
                            }
                        } else {

                            // If no topic is available in the Map
                            connectorInfo.getTopics().put(topicValue, new TopicInfo(topicValue, createPartitionOffsetInfoMap(topicValue, partitionValue, offsetValue)));
                        }
                    }
            );
        } else {
            // Topic doesn't exists in source kafka cluster.Removing from the list.
            topicIterator.remove();
        }
    }

    private Map<String, ConnectorInfo> createConnectorMapFromSource(String connectorName) {
        Map<String, ConnectorInfo> connectorMapSource = new HashMap<>();
        connectorMapSource.put(connectorName, new ConnectorInfo(connectorName, null));
        return connectorMapSource;
    }

    private Map<String, TopicInfo> createTopicInfoMap(String topicName, int partition, long offset) {
        Map<String, TopicInfo> topicMap = new HashMap<>();
        Map<Integer, PartitionOffsetInfo> partitionInfo = createPartitionOffsetInfoMap(topicName, partition, offset);
        topicMap.put(topicName, new TopicInfo(topicName, partitionInfo));
        return topicMap;
    }

    private Map<Integer, PartitionOffsetInfo> createPartitionOffsetInfoMap(String topicName, int partition, long offset) {
        Map<Integer, PartitionOffsetInfo> partitionInfo = new HashMap<>();
        PartitionOffsetInfo partitionOffsetInfo = createPartitionOffsetInfo(topicName, partition, offset);
        partitionInfo.put(partition, partitionOffsetInfo);
        return partitionInfo;
    }

    private PartitionOffsetInfo createPartitionOffsetInfo(String topicName, int partition, long offset) {
        PartitionOffsetInfo partitionOffsetInfo = new PartitionOffsetInfo();
        partitionOffsetInfo.setTopicName(topicName);
        partitionOffsetInfo.setPartition(partition);
        partitionOffsetInfo.setLogEndOffset(offset);
        partitionOffsetInfo.setLogEndOffsetUpdatedAt(Timestamp.valueOf(LocalDateTime.now()));
        return partitionOffsetInfo;
    }

    /**
     * @return source consumer status whether it is in running or failed state.Also provides Error
     * details if it's in Failed state.
     */
    public ConsumerStatus getSourceConsumerStatus() {
        return sourceConsumerStatus;
    }

    /**
     * To manually restart the source consumer instance.
     */
    @Async("sourceTaskExecutor")
    public void restartSourceConsumer() {
        if (sourceConsumerStatus.getStatus() == Status.FAILED) {
            log.info("mm2-lag-exporter::Restarting Source consumer");
            sourceConsumer = new KafkaConsumer<>(sourceConsumerProperties);
            runSourceConsumer();
        }
    }

    /**
     * In Specific intervals scheduler will be called and will restart the failed internal source consumer.
     * This configuration is defaulted to false.This config can be modified to enable auto restart incase
     * any internal consumer failure.
     */
    @Async("sourceTaskExecutor")
    @Scheduled(fixedRateString = Constants.CONSUMER_RESTART_FREQUENCY, initialDelayString = Constants.SCHEDULER_INITIAL_DELAY)
    public void schedulerConsumerRestartOnFailure() {
        if (sourceConsumerStatus.getStatus() == Status.FAILED && connectorConfig.isAutoRestartConsumerEnabled()) {
            log.info("mm2-lag-exporter::Restarting Failed Source consumer from scheduler");
            restartSourceConsumer();
        }
    }
}
