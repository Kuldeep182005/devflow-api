package com.devflow.api.sandbox;

import com.devflow.api.sandbox.config.SandboxProperties;
import com.devflow.api.sandbox.domain.SandboxExecutionProfile;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNoException;

class SandboxPropertiesTest {

    @Test
    @DisplayName("Default properties are valid")
    void defaultPropertiesAreValid() {
        SandboxProperties properties = new SandboxProperties();
        assertThatNoException().isThrownBy(properties::validate);
        assertThat(properties.getExecutionTimeout()).isEqualTo(Duration.ofMinutes(2));
        assertThat(properties.getAllowedProfiles()).contains(SandboxExecutionProfile.JAVA_MAVEN_TEST);
        assertThat(properties.getMaxOutputCharacters()).isLessThanOrEqualTo(20000);
    }

    @Test
    @DisplayName("Rejects zero or negative timeout")
    void rejectsZeroOrNegativeTimeout() {
        SandboxProperties properties = new SandboxProperties();
        properties.setExecutionTimeout(Duration.ZERO);
        assertThatIllegalStateException().isThrownBy(properties::validate);

        properties.setExecutionTimeout(Duration.ofSeconds(-10));
        assertThatIllegalStateException().isThrownBy(properties::validate);
    }

    @Test
    @DisplayName("Rejects absurdly long timeout")
    void rejectsAbsurdTimeout() {
        SandboxProperties properties = new SandboxProperties();
        properties.setExecutionTimeout(Duration.ofHours(2));
        assertThatIllegalStateException().isThrownBy(properties::validate);
    }

    @Test
    @DisplayName("Rejects negative or zero cpu quota")
    void rejectsInvalidCpuQuota() {
        SandboxProperties properties = new SandboxProperties();
        properties.setCpuQuota(0);
        assertThatIllegalStateException().isThrownBy(properties::validate);

        properties.setCpuQuota(-100);
        assertThatIllegalStateException().isThrownBy(properties::validate);
    }

    @Test
    @DisplayName("Rejects absurdly small or large memory limit")
    void rejectsInvalidMemoryLimit() {
        SandboxProperties properties = new SandboxProperties();
        properties.setMemoryBytes(1024); // 1KB too small
        assertThatIllegalStateException().isThrownBy(properties::validate);

        properties.setMemoryBytes(100L * 1024 * 1024 * 1024L); // 100GB too large
        assertThatIllegalStateException().isThrownBy(properties::validate);
    }

    @Test
    @DisplayName("Rejects output character limit exceeding database constraint")
    void rejectsExcessiveOutputCharacters() {
        SandboxProperties properties = new SandboxProperties();
        properties.setMaxOutputCharacters(50000); // Exceeds DB column limit of 20000
        assertThatIllegalStateException().isThrownBy(properties::validate);
    }

    @Test
    @DisplayName("Rejects empty workspace root or empty allowed profiles")
    void rejectsEmptyWorkspaceRootOrProfiles() {
        SandboxProperties properties = new SandboxProperties();
        properties.setWorkspaceRoot("");
        assertThatIllegalStateException().isThrownBy(properties::validate);

        properties = new SandboxProperties();
        properties.setAllowedProfiles(Set.of());
        assertThatIllegalStateException().isThrownBy(properties::validate);

        properties = new SandboxProperties();
        properties.setExecutorUrl("");
        assertThatIllegalStateException().isThrownBy(properties::validate);

        properties = new SandboxProperties();
        properties.setExecutorToken("");
        assertThatIllegalStateException().isThrownBy(properties::validate);
    }
}
