package com.ogt.gis.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE_NAME = "ogt.gis.events";
    public static final String IMPORT_QUEUE = "gis.import.queue";
    public static final String ROUTING_KEY = "gis.import.queue";

    @Bean
    public TopicExchange gisExchange() {
        return new TopicExchange(EXCHANGE_NAME);
    }

    @Bean
    public Queue importQueue() {
        return QueueBuilder.durable(IMPORT_QUEUE).build();
    }

    @Bean
    public Binding importBinding() {
        return BindingBuilder
                .bind(importQueue())
                .to(gisExchange())
                .with(ROUTING_KEY);
    }

    @Bean
    public Queue exportQueue() {
        return QueueBuilder.durable("gis.export.queue").build();
    }

    @Bean
    public Binding exportBinding(Queue exportQueue, TopicExchange gisExchange) {
        return BindingBuilder.bind(exportQueue)
                .to(gisExchange)
                .with("gis.export.queue");
    }

}
