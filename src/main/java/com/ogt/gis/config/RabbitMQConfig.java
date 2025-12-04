package com.ogt.gis.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    // ========== GIS SERVICE EXCHANGES ==========
    public static final String GIS_EXCHANGE_NAME = "ogt.gis.events";
    public static final String IMPORT_QUEUE = "gis.import.queue";
    public static final String ROUTING_KEY = "gis.import.queue";

    @Bean
    public TopicExchange gisExchange() {
        return new TopicExchange(GIS_EXCHANGE_NAME);
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

    // ========== LIGHT POINT SERVICE EXCHANGE (PARA ENVIAR MENSAJES) ==========
    @Bean
    public TopicExchange lightPointExchange() {
        return ExchangeBuilder.topicExchange("ogt.lightpoint.events")
                .durable(true)
                .build();
    }

    // ========== JSON MESSAGE CONVERTER ==========
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    // ========== RABBIT TEMPLATE CON JSON CONVERTER ==========
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());
        return template;
    }
}