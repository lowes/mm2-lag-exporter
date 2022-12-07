package com.lowes.mm2lagexporter.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowes.mm2lagexporter.config.ConnectorConfig;
import com.lowes.mm2lagexporter.config.TargetConsumerConfig;
import com.lowes.mm2lagexporter.model.ConnectorInfo;
import com.lowes.mm2lagexporter.model.ConsumerStatus;
import com.lowes.mm2lagexporter.model.MM2LagInfo;
import com.lowes.mm2lagexporter.utils.Constants;
import com.lowes.mm2lagexporter.utils.Status;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.config.ConfigException;
import org.apache.logging.log4j.util.Strings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.test.context.EmbeddedKafka;

import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.catchThrowable;
import static org.mockito.Mockito.when;

@Profile("test")
@ExtendWith(MockitoExtension.class)
@EmbeddedKafka(partitions = 1, brokerProperties = {"listeners=PLAINTEXT://localhost:9092", "port=9092"})
@Slf4j
class TargetConsumerServiceTest {

    @Mock
    private ConnectorConfig connectorConfig;

    @Mock
    private TargetConsumerConfig targetConsumerConfig;

    @Mock
    private Properties targetConsumerProperties;

    @Mock
    private ConsumerStatus targetConsumerStatus;

    @Mock
    private MM2LagInfo mM2LagInfo;

    @Mock
    private KafkaConsumer<String, String> targetConsumer;

    @Mock
    private ConnectorInfo connectorInfo;

    @InjectMocks
    TargetConsumerService underTest;

    @Mock
    private KafkaProducer testProducer;

    @Test
    void checkTargetConsumerStatus() {
        when(targetConsumerStatus.getStatus()).thenReturn(Status.RUNNING);
        assertThat(underTest.getTargetConsumerStatus().getStatus()).isEqualTo(Status.RUNNING);
    }

    @Test
    void apiShouldRestartTargetConsumerIfFailed() {
        when(targetConsumerStatus.getStatus()).thenReturn(Status.FAILED);
        Throwable thrown = catchThrowable(() -> {
            underTest.restartTargetConsumer();
        });
        assertThat(thrown)
                .isInstanceOf(ConfigException.class)
                .hasMessageContaining("Missing required configuration \"key.deserializer\" which has no default value");
    }

    @Test
    void apiShouldNotRestartTargetConsumerIFRunning() {
        when(targetConsumerStatus.getStatus()).thenReturn(Status.RUNNING);
        Throwable thrown = catchThrowable(() -> {
            underTest.restartTargetConsumer();
        });
        assertThat(thrown).doesNotThrowAnyException();
    }

    @Test
    void schedulerShouldRestartOnlyWhenTargetConsumerFailedAndAutoRestartEnabled() {
        when(targetConsumerStatus.getStatus()).thenReturn(Status.FAILED);
        when(connectorConfig.isAutoRestartConsumerEnabled()).thenReturn(new Boolean("true").booleanValue());
        Throwable thrown = catchThrowable(() -> {
            underTest.schedulerConsumerRestartOnFailure();
        });
        assertThat(thrown)
                .isInstanceOf(ConfigException.class)
                .hasMessageContaining("Missing required configuration \"key.deserializer\" which has no default value");
    }

    @Test
    void schedulerShouldNOTRestartOnlyWhenTargetConsumerRunning() {
        when(targetConsumerStatus.getStatus()).thenReturn(Status.RUNNING);
        Throwable thrown = catchThrowable(() -> {
            underTest.schedulerConsumerRestartOnFailure();
        });
        assertThat(thrown).doesNotThrowAnyException();
    }

    @Test
    void apiShouldNotRestartTargetConsumerIfRunning() {
        when(targetConsumerStatus.getStatus()).thenReturn(Status.RUNNING);
        Throwable thrown = catchThrowable(() -> {
            underTest.restartTargetConsumer();
        });
        assertThat(thrown).doesNotThrowAnyException();
    }

    @Test
    void shouldStartTargetConsumerAtApplicationStartup() {
        String topicName = "test-mm2-connect-offset";
        String mm2ConnectorInfo = "{\"sourcealias-targetalias-mirrorsourceconnector\":[\"test-mm2-connect-offset\"]}";
        ObjectMapper mapper = new ObjectMapper();
        //Test producer
        Properties producerProp = new Properties();
        producerProp.put("bootstrap.servers", "localhost:9092");
        producerProp.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        producerProp.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        //Test consumer
        Properties consumerProp = new Properties();
        consumerProp.put("bootstrap.servers", "localhost:9092");
        consumerProp.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        consumerProp.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        consumerProp.put("group.id", "test-consumer-group-1");

        try {
            testProducer = new KafkaProducer(producerProp);
            //send test messages to internal offset topic
            testProducer.send(new ProducerRecord(topicName, "[\"sourcealias-targetalias-mirrorsourceconnector\",{\"cluster\":\"sourcealias\",\"partition\":0,\"topic\":\"test-mm2-connect-offset\"}]", "100"));
            testProducer.send(new ProducerRecord(topicName, "[\"sourcealias-targetalias-mirrorsourceconnector\",{\"cluster\":\"sourcealias\",\"partition\":0,\"topic\":\"test-mm2-connect-offset\"}]", "102"));
            testProducer.send(new ProducerRecord(topicName, "[\"sourcealias-targetalias-mirrorsourceconnector\",{\"cluster\":\"sourcealias\",\"partition\":0,\"topic\":\"test-mm2-connect-offset\"}]", "105"));
            testProducer.send(new ProducerRecord(topicName, "\"sendingFailureRecord\",{\"cluster\":\"sourcealias\",\"partition\":0,\"topic\":\"test-mm2-connect-offset\"}", "110"));
            testProducer.close();
            targetConsumer = new KafkaConsumer(consumerProp);
            mM2LagInfo = new MM2LagInfo();
            connectorConfig = new ConnectorConfig();
            connectorConfig.setConnectors(mapper.readValue(mm2ConnectorInfo, Map.class));
            targetConsumerStatus = new ConsumerStatus(Constants.TARGET, Status.NOTSTARTED, Strings.EMPTY);
            targetConsumerConfig = new TargetConsumerConfig();
            targetConsumerConfig.setConnectOffsetTopicName(topicName);
        } catch (Exception ex) {
            testProducer.close();
            log.error(" Exception in TargetConsumerServiceTest {}", ex.getMessage());
        }
        TargetConsumerService targetConsumerServiceUnderTest =
                new TargetConsumerService(mM2LagInfo,
                        connectorConfig,
                        targetConsumerStatus,
                        targetConsumerConfig,
                        targetConsumerProperties,
                        targetConsumer);

        targetConsumerServiceUnderTest.runTargetConsumer();
        targetConsumer.close();
        assertThat(mM2LagInfo.getName())
                .isEqualTo("sourcealias-targetalias-mirrorsourceconnector");
        assertThat(mM2LagInfo.getConnector().get("sourcealias-targetalias-mirrorsourceconnector").getTopics().get(topicName).getTopicName())
                .isEqualTo(topicName);
        assertThat(mM2LagInfo.getConnector().get("sourcealias-targetalias-mirrorsourceconnector").getTopics().get(topicName).getPartitions().get(0).getMmOffset())
                .isEqualTo(106);

    }
}