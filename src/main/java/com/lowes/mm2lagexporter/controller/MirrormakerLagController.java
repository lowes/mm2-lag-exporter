package com.lowes.mm2lagexporter.controller;

import com.lowes.mm2lagexporter.exception.MMLagExporterException;
import com.lowes.mm2lagexporter.model.ConsumerStatus;
import com.lowes.mm2lagexporter.model.MM2LagInfo;
import com.lowes.mm2lagexporter.service.LagExporterService;
import com.lowes.mm2lagexporter.service.SourceConsumerService;
import com.lowes.mm2lagexporter.service.TargetConsumerService;
import io.swagger.annotations.ApiOperation;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Class to expose endpoint for below operations:
 * 1. Finds the mirroring lag for all or any specific topic.
 * 2. Finds the Status of source and target internal kafka consumer client.
 * 3. Restarts the source/target internal kafka consumer client incase of any failures.
 */
@RestController
public class MirrormakerLagController {

    private final LagExporterService lagExporterService;
    private final SourceConsumerService sourceConsumerService;
    private final TargetConsumerService targetConsumerService;

    public MirrormakerLagController(LagExporterService lagExporterService,
                                    SourceConsumerService sourceConsumerService,
                                    TargetConsumerService targetConsumerService) {
        this.lagExporterService = lagExporterService;
        this.sourceConsumerService = sourceConsumerService;
        this.targetConsumerService = targetConsumerService;
    }

    /**
     * @param topic Topic name to find the lag on specific topic
     * @return Mirroring Lag with details of mirroring direction and cluster info.
     * @throws MMLagExporterException ex: topic not exist
     */
    @GetMapping(value = "/lag", produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiOperation(value = "Provides the mirroring lag information of all the configured topics", response = MM2LagInfo.class)
    public MM2LagInfo getLagInfo(String topic) throws MMLagExporterException {
        if (StringUtils.isNotBlank(topic)) {
            return lagExporterService.getTopicLagInfo(topic);
        }
        return lagExporterService.getCurrentLagInfo();
    }

    /**
     * Get the status of source kafka consumer client.
     *
     * @return ConsumerStatus
     */
    @GetMapping(value = "/source-consumer/status", produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiOperation(value = "Status of source consumer")
    public ConsumerStatus getSourceConsumerStatus() {
        return sourceConsumerService.getSourceConsumerStatus();
    }

    /**
     * Restarts the Source kafka consumer client.
     */
    @PostMapping(value = "/source-consumer/restart", produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiOperation(value = "Restarts the source cluster consumer")
    public void restartSourceConsumer() {
        sourceConsumerService.restartSourceConsumer();
    }

    /**
     * Get the status of target kafka consumer client.
     *
     * @return ConsumerStatus
     */
    @GetMapping(value = "/target-consumer/status", produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiOperation(value = "Status of target consumer")
    public ConsumerStatus getTargetConsumerStatus() {
        return targetConsumerService.getTargetConsumerStatus();
    }

    /**
     * Restarts the Source kafka consumer client.
     */
    @PostMapping(value = "/target-consumer/restart", produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiOperation(value = "Restarts the target cluster consumer")
    public void restartTargetConsumer() {
        targetConsumerService.restartTargetConsumer();
    }
}

