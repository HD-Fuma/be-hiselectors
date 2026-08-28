package com.fuma.hiselectors.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.context.LifecycleProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.health.autoconfigure.actuate.endpoint.HealthEndpointProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.web.server.Shutdown;
import org.springframework.boot.web.server.autoconfigure.ServerProperties;

class DeploymentHealthConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(PropertiesConfiguration.class);

    @Test
    void deploymentHealthGroupsAndShutdownAreConfigured() {
        contextRunner.run(context -> {
            HealthEndpointProperties health = context.getBean(HealthEndpointProperties.class);

            assertThat(health.getGroup().get("liveness").getInclude())
                    .containsExactly("livenessState");
            assertThat(health.getGroup().get("readiness").getInclude())
                    .containsExactlyInAnyOrder("readinessState", "db");
            assertThat(context.getBean(ServerProperties.class).getShutdown())
                    .isEqualTo(Shutdown.GRACEFUL);
            assertThat(context.getBean(LifecycleProperties.class).getTimeoutPerShutdownPhase())
                    .isEqualTo(Duration.ofSeconds(25));
        });
    }

    @EnableConfigurationProperties({
            HealthEndpointProperties.class,
            ServerProperties.class,
            LifecycleProperties.class
    })
    static class PropertiesConfiguration {
    }
}
