package com.yoyuzh.common.broker;

import java.util.Map;
import java.util.Optional;

public interface LightweightBrokerService {

    void publish(String topic, Map<String, Object> payload);

    Optional<Map<String, Object>> poll(String topic);

    void requeue(String topic, Map<String, Object> payload);
}
