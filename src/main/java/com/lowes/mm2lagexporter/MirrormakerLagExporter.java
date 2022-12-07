package com.lowes.mm2lagexporter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableAsync
public class MirrormakerLagExporter {

    public static void main(String[] args) {
        SpringApplication.run(MirrormakerLagExporter.class, args);
    }

}
