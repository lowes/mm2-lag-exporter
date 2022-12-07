package com.lowes.mm2lagexporter.model;


import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class MMPartitionInfo {
    private String cluster;
    private int partition;
    private String topic;
}
