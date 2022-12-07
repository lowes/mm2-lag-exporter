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
public class SourceConsumerConfig {

    @Value("${source.cluster.bootstrap.servers}")
    private String sourceBrokerUrl;

    @Value("${source.consumer.groupid}")
    private String consumerGroupId;

    @Value("${consumer.ssl.enabled:false}")
    private boolean consumerSslEnabled;

    @Value("${source.security.protocol}")
    private String securityProtocol;

    @Value("${source.sasl.mechanism:#{null}}")
    private String saslMechanism;

    @Value("${source.sasl.Jaas.config:#{null}}")
    private String saslJaasConfig;

    @Value("${source.truststore.path:#{null}}")
    private String truststorePath;

    @Value("${source.ssl.truststore.type:JKS}")
    private String sslTruststoreType;

    @Value("${source.truststore.password:#{null}}")
    private String truststorePassword;

    @Value("${source.ssl.keystore.type:JKS}")
    private String sslKeystoreType;

    @Value("${source.ssl.keystore.location:#{null}}")
    private String sslKeystoreLocation;

    @Value("${source.ssl.keystore.password:#{null}}")
    private String sslKeystorePassword;

    @Value("${source.ssl.key.password:#{null}}")
    private String sslKeyPassword;

    @Value("${source.ssl.endpoint.identification.algorithm}")
    private String sslEndpointIdentificationAlgorithm;

    private String enableAutoCommitConfig = "true";
    private String autoCommitIntervalMsConfig = "1000";
    private String keyDeserializerClassConfig = "org.apache.kafka.common.serialization.StringDeserializer";
    private String valueDeserializerClassConfig = "org.apache.kafka.common.serialization.StringDeserializer";

    @Bean(name = "sourceConsumerProperties")
    public Properties getProperties() {
        Properties sourceConsumerProperties = new Properties();
        sourceConsumerProperties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, sourceBrokerUrl);
        sourceConsumerProperties.put(ConsumerConfig.GROUP_ID_CONFIG, consumerGroupId);
        sourceConsumerProperties.setProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, enableAutoCommitConfig);
        sourceConsumerProperties.setProperty(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, autoCommitIntervalMsConfig);
        sourceConsumerProperties.setProperty(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, keyDeserializerClassConfig);
        sourceConsumerProperties.setProperty(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, valueDeserializerClassConfig);
        if (consumerSslEnabled) {
            if (securityProtocol.equals(SecurityProtocol.SSL.name)) {
                sourceConsumerProperties.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, securityProtocol);
                sourceConsumerProperties.put(SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG, sslTruststoreType);
                putIfNotNull(sourceConsumerProperties, SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, truststorePath);
                putIfNotNull(sourceConsumerProperties, SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, truststorePassword);
                sourceConsumerProperties.put(SslConfigs.SSL_KEYSTORE_TYPE_CONFIG, sslKeystoreType);
                putIfNotNull(sourceConsumerProperties, SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG, sslKeystoreLocation);
                putIfNotNull(sourceConsumerProperties, SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG, sslKeystorePassword);
                putIfNotNull(sourceConsumerProperties, SslConfigs.SSL_KEY_PASSWORD_CONFIG, sslKeyPassword);
            } else if (securityProtocol.equals(SecurityProtocol.SASL_SSL.name)) {
                sourceConsumerProperties.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, securityProtocol);
                sourceConsumerProperties.put(SaslConfigs.SASL_MECHANISM, saslMechanism);
                sourceConsumerProperties.put(SaslConfigs.SASL_JAAS_CONFIG, saslJaasConfig);
                sourceConsumerProperties.put(SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG, sslTruststoreType);
                putIfNotNull(sourceConsumerProperties, SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, truststorePath);
                putIfNotNull(sourceConsumerProperties, SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, truststorePassword);
            } else if (securityProtocol.equals(SecurityProtocol.SASL_PLAINTEXT.name)) {
                sourceConsumerProperties.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, securityProtocol);
                sourceConsumerProperties.put(SaslConfigs.SASL_MECHANISM, saslMechanism);
                sourceConsumerProperties.put(SaslConfigs.SASL_JAAS_CONFIG, saslJaasConfig);
            }
            sourceConsumerProperties.put(SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG, sslEndpointIdentificationAlgorithm);
        }
        return sourceConsumerProperties;
    }

    private void putIfNotNull(Properties p, String key, String value) {
        if (value != null) {
            p.put(key, value);
        }
    }

    @Bean
    public ConsumerStatus sourceConsumerStatus() {
        return new ConsumerStatus(Constants.SOURCE, Status.NOTSTARTED, Strings.EMPTY);
    }

    @Bean(name = "sourceConsumer")
    public KafkaConsumer<String, String> sourceConsumer(@Qualifier("sourceConsumerProperties") Properties properties) {
        return new KafkaConsumer<>(properties);
    }

    @Bean("sourceTaskExecutor")
    public TaskExecutor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setThreadNamePrefix("source-consumer-thread-");
        return executor;
    }
}
