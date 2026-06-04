package it.unicas.cassitrack.config;

import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.integration.mqtt.outbound.MqttPahoMessageHandler;
import org.springframework.integration.mqtt.support.DefaultPahoMessageConverter;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;

import it.unicas.cassitrack.mqtt.MqttMessageHandler;

/**
 * Configures the MQTT client that listens to messages from buses.
 *
 * How it works:
 *   Bus (ESP32) → publishes JSON to MQTT topic → Mosquitto broker
 *   → this adapter receives it → mqttInputChannel
 *   → MqttMessageHandler processes it (validate → store → broadcast)
 */
@Configuration
public class MqttConfig {

    @Value("${mqtt.broker.url}")
    private String brokerUrl;

    @Value("${mqtt.broker.client-id}")
    private String clientId;

    @Value("${mqtt.topics.position}")
    private String positionTopic;

    @Value("${mqtt.topics.ble}")
    private String bleTopic;

    /**
     * Factory that creates MQTT client connections.
     * Sets connection options: timeout, keep-alive, clean session.
     */
    @Bean
    public MqttPahoClientFactory mqttClientFactory() {
        DefaultMqttPahoClientFactory factory = new DefaultMqttPahoClientFactory();
        MqttConnectOptions options = new MqttConnectOptions();
        options.setServerURIs(new String[]{brokerUrl});
        options.setConnectionTimeout(30);
        options.setKeepAliveInterval(60);
        options.setCleanSession(true);
        options.setAutomaticReconnect(true);  // reconnect if broker restarts
        factory.setConnectionOptions(options);
        return factory;
    }

    /**
     * The Spring Integration channel that carries incoming MQTT messages.
     * Think of it as a pipe: messages arrive here from the broker.
     */
    @Bean
    public MessageChannel mqttInputChannel() {
        return new DirectChannel();
    }

    /**
     * Inbound channel adapter — subscribes to MQTT topics and
     * pushes messages into mqttInputChannel.
     *
     * Topics subscribed:
     *   cassitrack/+/position  (+ matches any vehicle_id)
     *   cassitrack/+/ble
     */
    @Bean
    public MqttPahoMessageDrivenChannelAdapter mqttInbound() {
        MqttPahoMessageDrivenChannelAdapter adapter =
            new MqttPahoMessageDrivenChannelAdapter(
                clientId + "-inbound",
                mqttClientFactory(),
                positionTopic,
                bleTopic
            );
        adapter.setCompletionTimeout(5000);
        adapter.setConverter(new DefaultPahoMessageConverter());
        adapter.setQos(1);                 // QoS 1 = at least once delivery
        adapter.setOutputChannel(mqttInputChannel());
        return adapter;
    }

    /**
     * The Spring Integration channel for OUTBOUND messages.
     * Controllers send messages here to be published to the Mosquitto broker.
     */
    @Bean
    public MessageChannel mqttOutboundChannel() {
        return new DirectChannel();
    }

    /**
     * Outbound channel adapter — takes messages from mqttOutboundChannel
     * and publishes them using the existing MqttPahoClientFactory connection.
     */
    @Bean
    @ServiceActivator(inputChannel = "mqttOutboundChannel")
    public MessageHandler mqttOutbound() {
        MqttPahoMessageHandler messageHandler =
                new MqttPahoMessageHandler(clientId + "-outbound-publisher", mqttClientFactory());
        messageHandler.setAsync(true);
        messageHandler.setDefaultQos(1);
        return messageHandler;
    }

}
