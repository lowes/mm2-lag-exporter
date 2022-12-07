package com.lowes.mm2lagexporter.config;

import com.lowes.mm2lagexporter.model.ConsumerStatus;
import com.lowes.mm2lagexporter.utils.Constants;
import com.lowes.mm2lagexporter.utils.Status;
import lombok.Data;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.config.SslConfigs;
import org.apache.kafka.common.security.auth.SecurityProtocol;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Properties;

@Data
@Configuration
public class TargetConsumerConfig {

    @Value("${target.cluster.bootstrap.servers}")
    private String targetBrokerUrl;

    @Value("${target.cluster.alias}")
    private String targetClusterAlias;

    @Value("${connect.offset.topic}")
    private String connectOffsetTopicName;

    @Value("${target.consumer.groupid}")
    private String consumerGroupId;

    @Value("${consumer.ssl.enabled:false}")
    private boolean consumerSslEnabled;

    @Value("${target.security.protocol}")
    private String securityProtocol;

    @Value("${target.sasl.mechanism:#{null}}")
    private String saslMechanism;

    @Value("${target.sasl.Jaas.config:#{null}}")
    private String saslJaasConfig;

    @Value("${target.ssl.truststore.type:JKS}")
    private String sslTruststoreType;

    @Value("${target.truststore.path:#{null}}")
    private String truststorePath;

    @Value("${target.truststore.password:#{null}}")
    private String truststorePassword;

    @Value("${target.ssl.keystore.type:JKS}")
    private String sslKeystoreType;

    @Value("${target.ssl.keystore.location:#{null}}")
    private String sslKeystoreLocation;

    @Value("${target.ssl.keystore.password:#{null}}")
    private String sslKeystorePassword;

    @Value("${target.ssl.key.password:#{null}}")
    private String sslKeyPassword;


    @Value("${target.ssl.endpoint.identification.algorithm}")
    private String sslEndpointIdentificationAlgorithm;

    private String keyDeserializerClassConfig = "org.apache.kafka.common.serialization.StringDeserializer";
    private String valueDeserializerClassConfig = "org.apache.kafka.common.serialization.StringDeserializer";
    private String autoOffsetResetConfig = "earliest";

    @Bean(name = "targetConsumerProperties")
    public Properties getProperties() {
        Properties targetConsumerProperties = new Properties();
        targetConsumerProperties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, targetBrokerUrl);
        targetConsumerProperties.put(ConsumerConfig.GROUP_ID_CONFIG, consumerGroupId);
        targetConsumerProperties.setProperty(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, keyDeserializerClassConfig);
        targetConsumerProperties.setProperty(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, valueDeserializerClassConfig);
        targetConsumerProperties.setProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, autoOffsetResetConfig);
        if (consumerSslEnabled) {
            if (securityProtocol.equals(SecurityProtocol.SSL.name)) {
                targetConsumerProperties.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, securityProtocol);
                targetConsumerProperties.put(SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG, sslTruststoreType);
                putIfNotNull(targetConsumerProperties, SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, truststorePath);
                putIfNotNull(targetConsumerProperties, SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, truststorePassword);
                targetConsumerProperties.put(SslConfigs.SSL_KEYSTORE_TYPE_CONFIG, sslKeystoreType);
                putIfNotNull(targetConsumerProperties, SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG, sslKeystoreLocation);
                putIfNotNull(targetConsumerProperties, SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG, sslKeystorePassword);
                putIfNotNull(targetConsumerProperties, SslConfigs.SSL_KEY_PASSWORD_CONFIG, sslKeyPassword);
            } else if (securityProtocol.equals(SecurityProtocol.SASL_SSL.name)) {
                targetConsumerProperties.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, securityProtocol);
                targetConsumerProperties.put(SaslConfigs.SASL_MECHANISM, saslMechanism);
                targetConsumerProperties.put(SaslConfigs.SASL_JAAS_CONFIG, saslJaasConfig);
                targetConsumerProperties.put(SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG, sslTruststoreType);
                putIfNotNull(targetConsumerProperties, SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, truststorePath);
                putIfNotNull(targetConsumerProperties, SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, truststorePassword);
            } else if (securityProtocol.equals(SecurityProtocol.SASL_PLAINTEXT.name)) {
                targetConsumerProperties.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, securityProtocol);
                targetConsumerProperties.put(SaslConfigs.SASL_MECHANISM, saslMechanism);
                targetConsumerProperties.put(SaslConfigs.SASL_JAAS_CONFIG, saslJaasConfig);
            }
            targetConsumerProperties.put(SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG, sslEndpointIdentificationAlgorithm);
        }
        return targetConsumerProperties;
    }

    private void putIfNotNull(Properties p, String key, String value) {
        if (value != null) {
            p.put(key, value);
        }
    }

    @Bean
    public ConsumerStatus targetConsumerStatus() {
        return new ConsumerStatus(Constants.TARGET, Status.NOTSTARTED, Strings.EMPTY);
    }

    @Bean(name = "targetConsumer")
    public KafkaConsumer<String, String> targetConsumer(@Qualifier("targetConsumerProperties") Properties properties) {
        return new KafkaConsumer<>(properties);
    }

    @Bean("targetTaskExecutor")
    public TaskExecutor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setThreadNamePrefix("target-consumer-thread-");
        return executor;
    }
}
