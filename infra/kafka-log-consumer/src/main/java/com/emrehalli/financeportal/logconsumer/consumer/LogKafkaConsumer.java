package com.emrehalli.financeportal.logconsumer.consumer;

import com.emrehalli.financeportal.logconsumer.opensearch.OpenSearchWriter;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class LogKafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(LogKafkaConsumer.class);
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final OpenSearchWriter writer;
    private final ObjectMapper mapper;

    public LogKafkaConsumer(OpenSearchWriter writer) {
        this.writer = writer;
        this.mapper = new ObjectMapper();
    }

    @KafkaListener(topics = "${kafka.topic}")
    public void consume(List<String> messages, Acknowledgment ack) {
        List<Map<String, Object>> docs = new ArrayList<>(messages.size());

        for (String msg : messages) {
            if (msg == null || msg.isBlank()) continue;
            try {
                Map<String, Object> doc = mapper.readValue(msg, MAP_TYPE);
                enrichTimestamp(doc);
                docs.add(doc);
            } catch (Exception e) {
                log.warn("Parse error, skipping message: {}", e.getMessage());
            }
        }

        try {
            if (!docs.isEmpty()) {
                writer.bulkIndex(docs);
            }
        } catch (Exception e) {
            log.error("Unexpected error writing {} docs to OpenSearch: {}", docs.size(), e.getMessage());
        } finally {
            ack.acknowledge();
        }
    }

    // Backend JSON loglarındaki timestamp'ten @timestamp türet.
    // Fluent Bit kayıtlarıyla uyumlu olması için aynı alan adı kullanılır.
    private void enrichTimestamp(Map<String, Object> doc) {
        Object ts = doc.get("timestamp");
        if (ts instanceof String s && !s.isBlank()) {
            doc.put("@timestamp", s);
        } else {
            doc.put("@timestamp", Instant.now().toString());
        }
    }
}
