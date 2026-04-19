package com.yoyuzh.infra.broker;

import java.util.Map;
import java.util.Optional;

public interface LightweightBrokerGateway {

    void publish(String topic, Map<String, Object> payload);

    Optional<Map<String, Object>> poll(String topic);

    void requeue(String topic, Map<String, Object> payload);
}
