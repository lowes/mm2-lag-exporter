package com.lowes.mm2lagexporter.controller;

import com.lowes.mm2lagexporter.exception.MMLagExporterException;
import com.lowes.mm2lagexporter.model.ConsumerStatus;
import com.lowes.mm2lagexporter.model.MM2LagInfo;
import com.lowes.mm2lagexporter.service.LagExporterService;
import com.lowes.mm2lagexporter.service.SourceConsumerService;
import com.lowes.mm2lagexporter.service.TargetConsumerService;
import com.lowes.mm2lagexporter.utils.Constants;
import com.lowes.mm2lagexporter.utils.Status;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MirrormakerLagControllerTest {

    @Mock
    private LagExporterService lagExporterService;

    @Mock
    private SourceConsumerService sourceConsumerService;

    @Mock
    private TargetConsumerService targetConsumerService;

    @InjectMocks
    private MirrormakerLagController mirrormakerLagController;

    @Test
    void getLagInfoWithOutTopic() throws MMLagExporterException {
        MM2LagInfo mm2LagInfo = new MM2LagInfo();
        mm2LagInfo.setName("test-topic");
        when(lagExporterService.getCurrentLagInfo()).thenReturn(mm2LagInfo);

        MM2LagInfo response = mirrormakerLagController.getLagInfo(null);
        assertThat(response.getName()).isEqualTo("test-topic");
    }

    @Test
    void getLagInfoWithTopic() throws MMLagExporterException {
        MM2LagInfo mm2LagInfo = new MM2LagInfo();
        mm2LagInfo.setName("test-wo-topic");
        when(lagExporterService.getTopicLagInfo("topic")).thenReturn(mm2LagInfo);

        MM2LagInfo response = mirrormakerLagController.getLagInfo("topic");
        assertThat(response.getName()).isEqualTo("test-wo-topic");
    }

    @Test
    void getLagInfoWithInvalidTopic() throws MMLagExporterException {
        MM2LagInfo mm2LagInfo = new MM2LagInfo();
        mm2LagInfo.setName("test-topic");

        when(lagExporterService.getTopicLagInfo("topic")).thenReturn(mm2LagInfo);
        MM2LagInfo response = mirrormakerLagController.getLagInfo("topic");
        assertThat(response.getName()).isNotEqualTo("test-wo-topic");
    }

    @Test
    void getLagInfoWithInvalidTopicException() throws MMLagExporterException {
        MMLagExporterException thrown = Assertions.assertThrows(MMLagExporterException.class, () -> {
            when(lagExporterService.getTopicLagInfo("topic"))
                    .thenThrow(new MMLagExporterException(Constants.ERROR_MIRRORING_NOT_EXIST, HttpStatus.BAD_REQUEST));
            MM2LagInfo response = mirrormakerLagController.getLagInfo("topic");
        });
        Assertions.assertEquals(MMLagExporterException.class, thrown.getClass());
    }

    @Test
    void getSourceConsumerStatus() {
        ConsumerStatus consumerStatus = new ConsumerStatus();
        consumerStatus.setStatus(Status.RUNNING);
        when(mirrormakerLagController.getSourceConsumerStatus()).thenReturn(consumerStatus);

        ConsumerStatus response = mirrormakerLagController.getSourceConsumerStatus();
        assertThat(response.getStatus()).isEqualTo(Status.RUNNING);
    }

    @Test
    void getTargetConsumerStatus() {
        ConsumerStatus consumerStatus = new ConsumerStatus();
        consumerStatus.setStatus(Status.RUNNING);
        when(mirrormakerLagController.getTargetConsumerStatus()).thenReturn(consumerStatus);

        ConsumerStatus response = mirrormakerLagController.getTargetConsumerStatus();
        assertThat(response.getStatus()).isEqualTo(Status.RUNNING);
    }

    @Test
    void restartTargetConsumer() {
        mirrormakerLagController.restartTargetConsumer();
    }

    @Test
    void restartSourceConsumer() {
        mirrormakerLagController.restartSourceConsumer();
    }

}

