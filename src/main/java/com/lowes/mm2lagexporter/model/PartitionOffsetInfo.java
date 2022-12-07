package com.lowes.mm2lagexporter.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.sql.Timestamp;


@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class PartitionOffsetInfo {
    private String topicName;
    private int partition;
    private long logEndOffset;
    private Timestamp logEndOffsetUpdatedAt;
    private long mmOffset;
    private Timestamp mmOffsetUpdatedAt;
    private long lag;

    public void setLogEndOffset(long logEndOffset) {
        this.logEndOffset = logEndOffset;
        if (this.logEndOffset - this.mmOffset >= 0) {
            this.lag = this.logEndOffset - this.mmOffset;
        }
    }

    public void setMmOffset(long mmOffset) {
        this.mmOffset = mmOffset + 1;
        if (this.logEndOffset - this.mmOffset >= 0) {
            this.lag = this.logEndOffset - this.mmOffset;
        }
    }
}
