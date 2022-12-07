package com.lowes.mm2lagexporter.utils;

public class Constants {
    public static final String SOURCE = "Source";
    public static final String TARGET = "Target";
    public static final String TRUE = "true";
    public static final String FALSE = "false";
    public static final String TOPIC = "topic";
    public static final String PARTITION = "partition";
    public static final String SOURCE_CLUSTER = "sourcecluster";
    public static final String TARGET_CLUSTER = "targetcluster";
    public static final String CONNECTOR = "connector";
    public static final String LOGENDOFFSET_METRICS_NAME = "mirrormaker_source_topics_logendoffset";
    public static final String LOGENDOFFSET_METRICS_DOC = "The Log end offset of source cluster Topics";
    public static final String MMOFFSET_METRICS_NAME = "mirrormaker_currentoffset";
    public static final String MMOFFSET_METRICS_DOC = "The current offset of mirrormaker connector process";
    public static final long POLL_TIMEOUT = 1000;
    public static final String SCHEDULER_INITIAL_DELAY = "20000";
    public static final String SCHEDULER_FREQUENCY = "1000";
    public static final String CONSUMER_RESTART_FREQUENCY = "60000";
    public static final String ERROR_TOPIC_NOT_EXIST = "topic not exist to mirroring direction";
    public static final String ERROR_MIRRORING_NOT_EXIST = "Mirroring direction details not exist";

    private Constants() {
        throw new IllegalStateException("Constants Illegal state exception");
    }
}
