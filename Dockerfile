FROM adoptopenjdk:11-jre-hotspot
USER root
WORKDIR /opt/kafka/MM2LagExporter
RUN adduser --uid 10101 -S kafka
RUN chown -R 10101 /opt/kafka/MM2LagExporter
ADD target/mm2-lag-exporter*.jar /opt/kafka/MM2LagExporter/mm2-lag-exporter.jar
USER 10101
EXPOSE 8080
ENTRYPOINT ["java","-jar","/opt/kafka/MM2LagExporter/mm2-lag-exporter.jar"]