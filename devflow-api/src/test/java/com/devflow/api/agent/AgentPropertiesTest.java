package com.devflow.api.agent;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.devflow.api.agent.config.AgentProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class AgentPropertiesTest {
    @Test
    void rejectsNonPositiveLimits() {
        AgentProperties properties = new AgentProperties();
        properties.setMaxToolCalls(0);
        assertThrows(IllegalStateException.class, properties::validate);
    }

    @Test
    void rejectsNonPositiveDuration() {
        AgentProperties properties = new AgentProperties();
        properties.setMaxExecutionDuration(Duration.ZERO);
        assertThrows(IllegalStateException.class, properties::validate);
    }
}
