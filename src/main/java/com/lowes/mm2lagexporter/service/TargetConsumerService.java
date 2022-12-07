package com.lowes.mm2lagexporter.service;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowes.mm2lagexporter.config.ConnectorConfig;
import com.lowes.mm2lagexporter.config.TargetConsumerConfig;
import com.lowes.mm2lagexporter.model.*;
import com.lowes.mm2lagexporter.utils.Constants;
import com.lowes.mm2lagexporter.utils.Status;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Creates a new Consumer instance to read the current offset of mirror maker connector offset topic in the destination cluster.
 */
@Slf4j
@Service
public class TargetConsumerService {
    private final ConnectorConfig connectorConfig;
    private final TargetConsumerConfig targetConsumerConfig;
    private final Properties targetConsumerProperties;
    private final ConsumerStatus targetConsumerStatus;
    private final MM2LagInfo mM2LagInfo;
    private ConnectorInfo connectorInfo;
    private KafkaConsumer<String, String> targetConsumer;
    private long offsetValue;
    private String topicValue;
    private Integer partitionValue;
    private String connectorValue;

    public TargetConsumerService(MM2LagInfo mM2LagInfo,
                                 ConnectorConfig connectorConfig,
                                 ConsumerStatus targetConsumerStatus,
                                 TargetConsumerConfig targetConsumerConfig,
                                 @Qualifier("targetConsumerProperties") Properties targetConsumerProperties,
                                 @Qualifier("targetConsumer") KafkaConsumer<String, String> targetConsumer) {
        this.mM2LagInfo = mM2LagInfo;
        this.connectorConfig = connectorConfig;
        this.targetConsumerStatus = targetConsumerStatus;
        this.targetConsumerConfig = targetConsumerConfig;
        this.targetConsumerProperties = targetConsumerProperties;
        this.targetConsumer = targetConsumer;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Async("targetTaskExecutor")
    public void runTargetConsumer() {
        log.info("mm2-lag-exporter::Starting New Target Kafka Consumer");
        targetConsumerStatus.setStatus(Status.RUNNING);
        targetConsumerStatus.setTrace("");

        ObjectMapper jsonConvertor = new ObjectMapper();
        List<String> connectorNameList = new ArrayList<>();
        String topicName = targetConsumerConfig.getConnectOffsetTopicName();

        //Creating connector List
        for (Map.Entry<String, List<String>> connectors : connectorConfig.getConnectors().entrySet()) {
            String connector = connectors.getKey();
            connectorNameList.add(connector);
        }
        mM2LagInfo.setName(String.join(",", connectorNameList));
        mM2LagInfo.setTargetClusterBrokerUrl(connectorConfig.getTargetBrokerUrl());
        mM2LagInfo.setTargetClusterAlias(connectorConfig.getTargetClusterAlias());

        log.debug("mm2-lag-exporter::target consumer subscribed to connect offset topic: {}" + topicName);

        // In case of application restart seek the internal offset topic from beginning to get the mirror maker2 current offset
        Collection<TopicPartition> topicPartitions =
                targetConsumer.partitionsFor(topicName)
                        .stream()
                        .map(partitions -> new TopicPartition(partitions.topic(), partitions.partition()))
                        .collect(Collectors.toList());

        targetConsumer.assign(topicPartitions);
        targetConsumer.seekToBeginning(targetConsumer.assignment());

        //Poll the offset information from the connect-offsets internal topic updated by mirror maker source connector
        try {
            updateTopicLagDetails(jsonConvertor, connectorNameList);
        } catch (Exception ex) {
            log.error("mm2-lag-exporter::Error in target consumer instance", ex);
            targetConsumerStatus.setStatus(Status.FAILED);
            targetConsumerStatus.setTrace(ExceptionUtils.getStackTrace(ex));
        } finally {
            log.info("mm2-lag-exporter::Closing Target cluster consumer");
            targetConsumer.close();
        }
    }

    private void updateTopicLagDetails(ObjectMapper jsonConvertor, List<String> connectorNameList) throws JsonProcessingException {
        String keyString;
        String valueString;
        Map<String, ConnectorInfo> connectorMap;
        ConsumerRecords<String, String> consumerRecords;
        while (true) {
            consumerRecords = targetConsumer.poll(Duration.ofMillis(Constants.POLL_TIMEOUT));
            for (ConsumerRecord<String, String> consumerRecord : consumerRecords) {
                try {
                    String connectorsTemp = jsonConvertor.treeToValue(jsonConvertor.readTree(consumerRecord.key()).get(0), MMConnectorInfo.class).getConnectorName();

                    //Filtering only the required connectors from the kafka message key.Key will contain connector name,source cluster, topics and
                    //partition information. and the message value field will contain the offset details.
                    if (connectorNameList.stream().anyMatch(connectorsTemp::equalsIgnoreCase)) {
                        keyString = consumerRecord.key();
                        valueString = consumerRecord.value();
                        MMConnectorInfo mmConnectorInfo = jsonConvertor.treeToValue(jsonConvertor.readTree(keyString).get(0), MMConnectorInfo.class);
                        MMPartitionInfo mmPartitionInfo = jsonConvertor.treeToValue(jsonConvertor.readTree(keyString).get(1), MMPartitionInfo.class);

                        connectorValue = mmConnectorInfo.getConnectorName();
                        topicValue = mmPartitionInfo.getTopic();
                        partitionValue = mmPartitionInfo.getPartition();
                        offsetValue = jsonConvertor.readValue(valueString, MMOffsetInfo.class).getOffset();

                        if (mM2LagInfo.getConnector() == null) {
                            connectorMap = createConnectorMapFromTarget(connectorValue);
                            mM2LagInfo.setConnector(connectorMap);
                            connectorInfo = mM2LagInfo.getConnector().get(connectorValue);
                        } else if (mM2LagInfo.getConnector().containsKey(connectorValue)) {
                            connectorInfo = mM2LagInfo.getConnector().get(connectorValue);
                        } else if (!mM2LagInfo.getConnector().containsKey(connectorValue)) {
                            connectorInfo = new ConnectorInfo(connectorValue, null);
                            mM2LagInfo.getConnector().put(connectorValue, connectorInfo);
                        }
                        filterAndUpdateLagDetails();
                    }
                } catch (JsonParseException jsonParseException) {
                    log.error("mm2-lag-exporter::Error in Parsing Json values {} Failed value {}", jsonParseException, consumerRecord.key());
                }
            }
        }
    }

    //Filtering only the requested topics from the config.
    private void filterAndUpdateLagDetails() {
        if (connectorConfig.getConnectors().get(connectorValue).contains(topicValue)) {
            if (connectorInfo.getTopics() == null) {
                connectorInfo.setTopics(createTopicInfo(topicValue, getPartitionOffsetInfoMap(topicValue, partitionValue, offsetValue)));
            } else if (connectorInfo.getTopics().containsKey(topicValue)) {
                if (connectorInfo.getTopics().get(topicValue).getPartitions().containsKey(partitionValue)) {
                    connectorInfo.getTopics().get(topicValue).getPartitions().get(partitionValue).setMmOffset(offsetValue);
                    connectorInfo.getTopics().get(topicValue).getPartitions().get(partitionValue).setMmOffsetUpdatedAt(Timestamp.valueOf(LocalDateTime.now()));
                } else {
                    PartitionOffsetInfo partitionOffsetInfo = getPartitionOffsetInfo(topicValue, partitionValue, offsetValue);
                    connectorInfo.getTopics().get(topicValue).getPartitions().put(partitionValue, partitionOffsetInfo);
                }
            } else {
                connectorInfo.getTopics().put(topicValue, new TopicInfo(topicValue, getPartitionOffsetInfoMap(topicValue, partitionValue, offsetValue)));
            }
        }
    }

    private Map<String, ConnectorInfo> createConnectorMapFromTarget(String connectorName) {
        Map<String, ConnectorInfo> connectorMapTarget = new HashMap<>();
        connectorMapTarget.put(connectorName, new ConnectorInfo(connectorName, null));
        return connectorMapTarget;
    }

    private Map<String, TopicInfo> createTopicInfo(String topicName, Map<Integer, PartitionOffsetInfo> partitionInfo) {
        Map<String, TopicInfo> topicMap = new HashMap<>();
        topicMap.put(topicName, new TopicInfo(topicName, partitionInfo));
        return topicMap;
    }

    private Map<Integer, PartitionOffsetInfo> getPartitionOffsetInfoMap(String topicName, int partition, long offset) {
        Map<Integer, PartitionOffsetInfo> targetPartitionMap = new HashMap<>();
        PartitionOffsetInfo partitionInfo = getPartitionOffsetInfo(topicName, partition, offset);
        targetPartitionMap.put(partition, partitionInfo);
        return targetPartitionMap;
    }

    private PartitionOffsetInfo getPartitionOffsetInfo(String topicName, int partition, long offset) {
        PartitionOffsetInfo targetPartitionInfo = new PartitionOffsetInfo();
        targetPartitionInfo.setTopicName(topicName);
        targetPartitionInfo.setPartition(partition);
        targetPartitionInfo.setMmOffset(offset);
        targetPartitionInfo.setMmOffsetUpdatedAt(Timestamp.valueOf(LocalDateTime.now()));
        return targetPartitionInfo;
    }

    /**
     * @return source consumer status whether it is in running or failed state.Also provides Error
     * details if its in Failed state.
     */
    public ConsumerStatus getTargetConsumerStatus() {
        return targetConsumerStatus;
    }

    /**
     * To manually restart the target consumer instance.
     */
    @Async("targetTaskExecutor")
    public void restartTargetConsumer() {
        if (targetConsumerStatus.getStatus() == Status.FAILED) {
            log.info("mm2-lag-exporter::Restarting Target consumer");
            targetConsumer = new KafkaConsumer<>(targetConsumerProperties);
            runTargetConsumer();
        }
    }

    /**
     * In Specific intervals scheduler will be called and will restart the failed internal target consumer.
     * This configuration is defaulted to false.This config can be modified to enable auto restart incase
     * any internal consumer failure.
     */
    @Async("targetTaskExecutor")
    @Scheduled(fixedRateString = Constants.CONSUMER_RESTART_FREQUENCY, initialDelayString = Constants.SCHEDULER_INITIAL_DELAY)
    public void schedulerConsumerRestartOnFailure() {
        if (targetConsumerStatus.getStatus() == Status.FAILED && connectorConfig.isAutoRestartConsumerEnabled()) {
            log.info("mm2-lag-exporter::Restarting Failed target consumer from scheduler");
            restartTargetConsumer();
        }
    }
}
