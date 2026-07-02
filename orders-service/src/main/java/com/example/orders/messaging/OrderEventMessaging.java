package com.example.orders.messaging;

import com.example.orders.config.OrdersConfig;
import com.example.orders.model.Order;
import com.example.orders.websocket.OrderSessionRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

import java.time.Duration;
import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

@ApplicationScoped
public class OrderEventMessaging {

    private static final Logger LOG = Logger.getLogger(OrderEventMessaging.class.getName());

    @Inject
    OrdersConfig ordersConfig;

    private String bootstrapServers;
    private String topic;
    private KafkaProducer<String, String> producer;
    private KafkaConsumer<String, String> consumer;
    private ExecutorService consumerExecutor;
    private final AtomicBoolean running = new AtomicBoolean(false);

    @PostConstruct
    void start() {
        try {
            initializeClients();
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Kafka messaging disabled — failed to start producer/consumer", e);
        }
    }

    private synchronized void initializeClients() {
        if (producer != null) {
            return;
        }

        bootstrapServers = ordersConfig.kafkaBootstrapServers();
        topic = ordersConfig.kafkaTopic();

        Properties producerProps = new Properties();
        producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        producerProps.put(ProducerConfig.ACKS_CONFIG, "all");
        producer = new KafkaProducer<>(producerProps);

        Properties consumerProps = new Properties();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "orders-service");
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumer = new KafkaConsumer<>(consumerProps);
        consumer.subscribe(Collections.singletonList(topic));

        running.set(true);
        consumerExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "orders-kafka-consumer");
            t.setDaemon(true);
            return t;
        });
        consumerExecutor.submit(this::pollLoop);
    }

    @PreDestroy
    void stop() {
        running.set(false);
        if (consumerExecutor != null) {
            consumerExecutor.shutdownNow();
        }
        if (consumer != null) {
            consumer.close();
        }
        if (producer != null) {
            producer.close();
        }
    }

    public void publishOrderCreated(Order order) {
        publishEvent(
                "ORDER_CREATED",
                order.getId(),
                order.getCustomerName(),
                order.getProduct(),
                order.getQuantity(),
                order.getStatus()
        );
    }

    public void publishOrderFulfilled(Order order) {
        publishEvent(
                "ORDER_FULFILLED",
                order.getId(),
                order.getCustomerName(),
                order.getProduct(),
                order.getQuantity(),
                order.getStatus()
        );
    }

    public void publishOrderDeleted(Order order) {
        publishEvent(
                "ORDER_DELETED",
                order.getId(),
                order.getCustomerName(),
                order.getProduct(),
                order.getQuantity(),
                order.getStatus()
        );
    }

    private void publishEvent(
            String event,
            long id,
            String customer,
            String product,
            int quantity,
            String status
    ) {
        try {
            if (producer == null) {
                initializeClients();
            }
            if (producer == null) {
                LOG.log(Level.WARNING, "Skipping Kafka publish for order {0} — producer unavailable", id);
                return;
            }
            String payload = """
                {"event":"%s","id":%d,"customer":"%s","product":"%s","quantity":%d,"status":"%s"}
                """.formatted(
                    event,
                    id,
                    escape(customer),
                    escape(product),
                    quantity,
                    escape(status)
            ).trim();
            producer.send(new ProducerRecord<>(topic, String.valueOf(id), payload));
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to publish " + event + " for order " + id, e);
        }
    }

    public boolean isKafkaReachable() {
        try {
            if (producer == null) {
                initializeClients();
            }
            if (producer == null) {
                return false;
            }
            producer.partitionsFor(topic);
            return true;
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Kafka health check failed", e);
            return false;
        }
    }

    private void pollLoop() {
        while (running.get()) {
            try {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
                for (ConsumerRecord<String, String> record : records) {
                    OrderSessionRegistry.broadcast(record.value());
                }
            } catch (Exception e) {
                if (running.get()) {
                    LOG.log(Level.WARNING, "Kafka consumer poll failed", e);
                }
            }
        }
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\"", "\\\"");
    }
}
