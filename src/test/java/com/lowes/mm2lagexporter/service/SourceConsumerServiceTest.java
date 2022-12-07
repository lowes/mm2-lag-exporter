package com.lowes.mm2lagexporter.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowes.mm2lagexporter.config.ConnectorConfig;
import com.lowes.mm2lagexporter.config.SourceConsumerConfig;
import com.lowes.mm2lagexporter.model.ConsumerStatus;
import com.lowes.mm2lagexporter.model.MM2LagInfo;
import com.lowes.mm2lagexporter.utils.Constants;
import com.lowes.mm2lagexporter.utils.Status;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.config.ConfigException;
import org.apache.logging.log4j.util.Strings;
import org.assertj.core.api.AssertionsForClassTypes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opentest4j.AssertionFailedError;
import org.springframework.kafka.test.context.EmbeddedKafka;

import java.time.Duration;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.catchThrowable;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@EmbeddedKafka(partitions = 1, brokerProperties = {"listeners=PLAINTEXT://localhost:9092", "port=9092"})
class SourceConsumerServiceTest {

    @Mock
    SourceConsumerConfig sourceConsumerConfig;
    @InjectMocks
    private SourceConsumerService sourceConsumerService;
    @Mock
    private MM2LagInfo mM2LagInfo;
    @Mock
    private ConnectorConfig connectorConfig;
    @Mock
    private ConsumerStatus sourceConsumerStatus;
    @Mock
    private Properties sourceConsumerProperties;
    @Mock
    private KafkaConsumer<String, String> sourceConsumer;
    @Mock
    private KafkaProducer testProducer;

    @BeforeEach
    public void setUp() throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        connectorConfig = new ConnectorConfig();
        String connectorJson = "{\"test-mirrorsourceconnector\":[\"test-topic\"]}";
        connectorConfig.setConnectors(mapper.readValue(connectorJson, Map.class));
        sourceConsumerService = new SourceConsumerService(mM2LagInfo, connectorConfig, sourceConsumerStatus, sourceConsumerProperties, sourceConsumer);
    }

    @Test
    void getSourceConsumerStatusTest() {
        when(sourceConsumerStatus.getStatus()).thenReturn(Status.RUNNING);
        assertThat(sourceConsumerService.getSourceConsumerStatus().getStatus()).isEqualTo(Status.RUNNING);
    }

    @Test
    void restartSourceConsumerFailedTest() {
        when(sourceConsumerStatus.getStatus()).thenReturn(Status.FAILED);
        Throwable thrown = catchThrowable(() -> {
            sourceConsumerService.restartSourceConsumer();
        });
        AssertionsForClassTypes.assertThat(thrown)
                .isInstanceOf(ConfigException.class)
                .hasMessageContaining("Missing required configuration \"key.deserializer\" which has no default value");
    }

    @Test
    void restartSourceConsumerRunningTest() {
        when(sourceConsumerStatus.getStatus()).thenReturn(Status.RUNNING);
        Throwable thrown = catchThrowable(() -> {
            sourceConsumerService.restartSourceConsumer();
        });
        AssertionsForClassTypes.assertThat(thrown).doesNotThrowAnyException();
    }

    @Test
    void restartSourceConsumerRunningStatusTest() {
        connectorConfig.setAutoRestartConsumerEnabled(true);
        when(sourceConsumerStatus.getStatus()).thenReturn(Status.RUNNING);
        Throwable thrown = catchThrowable(() -> {
            sourceConsumerService.restartSourceConsumer();
        });
        AssertionsForClassTypes.assertThat(thrown).doesNotThrowAnyException();
    }

    @Test
    void schedulerConsumerRestartOnFailureNullTest() {
        when(sourceConsumerStatus.getStatus()).thenReturn(Status.FAILED);
        Throwable thrown = catchThrowable(() -> {
            sourceConsumerService.schedulerConsumerRestartOnFailure();
        });
        AssertionsForClassTypes.assertThat(thrown).isNull();
    }

    @Test
    void schedulerConsumerRestartOnFailureTest() {
        connectorConfig.setAutoRestartConsumerEnabled(true);
        when(sourceConsumerStatus.getStatus()).thenReturn(Status.FAILED);
        Throwable thrown = catchThrowable(() -> {
            sourceConsumerService.schedulerConsumerRestartOnFailure();
        });
        AssertionsForClassTypes.assertThat(thrown)
                .isInstanceOf(ConfigException.class)
                .hasMessageContaining("Missing required configuration \"key.deserializer\" which has no default value");
    }

    @Test
    void runSourceConsumerTest() {
        Throwable thrown = catchThrowable(() -> {
            assertTimeoutPreemptively(Duration.ofSeconds(15), () -> {
                sourceConsumerService.runSourceConsumer();
            });
        });
        AssertionsForClassTypes.assertThat(thrown).isInstanceOf(AssertionFailedError.class);
    }

    @Test
    void runSourceConsumerFailTest() {
        when(mM2LagInfo.getConnector()).thenReturn(null);
        sourceConsumerService = new SourceConsumerService(mM2LagInfo, connectorConfig, sourceConsumerStatus, sourceConsumerProperties, sourceConsumer);
        Throwable thrown = catchThrowable(() -> {
            assertTimeoutPreemptively(Duration.ofSeconds(15), () -> {
                sourceConsumerService.runSourceConsumer();
            });
        });
        AssertionsForClassTypes.assertThat(thrown).isNull();
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
            sourceConsumer = new KafkaConsumer(consumerProp);
            mM2LagInfo = new MM2LagInfo();
            connectorConfig = new ConnectorConfig();
            connectorConfig.setConnectors(mapper.readValue(mm2ConnectorInfo, Map.class));
            sourceConsumerStatus = new ConsumerStatus(Constants.TARGET, Status.NOTSTARTED, Strings.EMPTY);
            sourceConsumerConfig = new SourceConsumerConfig();
        } catch (Exception e) {
            testProducer.close();
        }
        SourceConsumerService sourceConsumerService =
                new SourceConsumerService(mM2LagInfo, connectorConfig, sourceConsumerStatus, sourceConsumerProperties, sourceConsumer);
        Throwable thrown = catchThrowable(() -> {
            assertTimeoutPreemptively(Duration.ofSeconds(15), () -> {
                sourceConsumerService.runSourceConsumer();
            });
        });
        AssertionsForClassTypes.assertThat(thrown).isInstanceOf(AssertionFailedError.class);
    }


}