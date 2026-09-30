package com.devflow.executor.docker;

import com.devflow.executor.config.ExecutorProperties;
import com.devflow.executor.dto.ExecuteResponseDto;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.StartContainerCmd;
import com.github.dockerjava.api.command.WaitContainerCmd;
import com.github.dockerjava.api.command.WaitContainerResultCallback;
import com.github.dockerjava.api.model.Capability;
import com.github.dockerjava.api.model.HostConfig;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DockerSandboxExecutorSecurityTest {

    private DockerClient dockerClient;
    private ExecutorProperties properties;
    private DockerSandboxExecutor executor;

    @BeforeEach
    void setUp() {
        dockerClient = mock(DockerClient.class);
        properties = new ExecutorProperties();
        executor = new DockerSandboxExecutor(properties, dockerClient);
    }

    @Test
    @DisplayName("HostConfig enforces network=none, cap_drop=ALL, privileged=false, readonly_rootfs=true")
    @SuppressWarnings({"unchecked", "deprecation"})
    void hostConfigEnforcesStrictIsolation() {
        CreateContainerCmd createCmd = mock(CreateContainerCmd.class);
        CreateContainerResponse createResponse = mock(CreateContainerResponse.class);
        StartContainerCmd startCmd = mock(StartContainerCmd.class);
        WaitContainerCmd waitCmd = mock(WaitContainerCmd.class);

        when(dockerClient.createContainerCmd(anyString())).thenReturn(createCmd);
        when(createCmd.withCmd(any(java.util.List.class))).thenReturn(createCmd);
        when(createCmd.withWorkingDir(anyString())).thenReturn(createCmd);
        when(createCmd.withHostConfig(any(HostConfig.class))).thenReturn(createCmd);
        when(createCmd.withEnv(any(String[].class))).thenReturn(createCmd);
        when(createCmd.withUser(anyString())).thenReturn(createCmd);
        when(createCmd.withLabels(any())).thenReturn(createCmd);
        when(createCmd.exec()).thenReturn(createResponse);
        when(createResponse.getId()).thenReturn("container-test-123");

        when(dockerClient.startContainerCmd("container-test-123")).thenReturn(startCmd);
        when(dockerClient.waitContainerCmd("container-test-123")).thenReturn(waitCmd);
        when(waitCmd.exec(any(WaitContainerResultCallback.class))).thenAnswer(invocation -> {
            WaitContainerResultCallback callback = invocation.getArgument(0);
            callback.onNext(new com.github.dockerjava.api.model.WaitResponse());
            callback.onComplete();
            return callback;
        });

        com.github.dockerjava.api.command.LogContainerCmd logCmd = mock(com.github.dockerjava.api.command.LogContainerCmd.class);
        when(dockerClient.logContainerCmd("container-test-123")).thenReturn(logCmd);
        when(logCmd.withStdOut(true)).thenReturn(logCmd);
        when(logCmd.withStdErr(true)).thenReturn(logCmd);
        when(logCmd.withFollowStream(false)).thenReturn(logCmd);
        when(logCmd.exec(any())).thenAnswer(invocation -> {
            com.github.dockerjava.core.command.LogContainerResultCallback cb = invocation.getArgument(0);
            cb.onComplete();
            return cb;
        });

        com.github.dockerjava.api.command.RemoveContainerCmd removeCmd = mock(com.github.dockerjava.api.command.RemoveContainerCmd.class);
        when(dockerClient.removeContainerCmd("container-test-123")).thenReturn(removeCmd);
        when(removeCmd.withForce(true)).thenReturn(removeCmd);

        UUID executionId = UUID.randomUUID();
        UUID proposedChangeId = UUID.randomUUID();
        Path dummyWorkspace = Path.of("target/dummy-workspace");

        ExecuteResponseDto result = executor.run(executionId, proposedChangeId, "JAVA_MAVEN_TEST", dummyWorkspace);
        assertThat(result).isNotNull();
        assertThat(result.status()).isNotBlank();

        ArgumentCaptor<HostConfig> captor = ArgumentCaptor.forClass(HostConfig.class);
        verify(createCmd).withHostConfig(captor.capture());
        HostConfig captured = captor.getValue();

        assertThat(captured.getNetworkMode()).isEqualTo("none");
        assertThat(captured.getPrivileged()).isFalse();
        assertThat(captured.getReadonlyRootfs()).isTrue();
        assertThat(captured.getCapDrop()).containsExactly(Capability.ALL);
        assertThat(captured.getMemory()).isEqualTo(properties.getMemoryBytes());
        assertThat(captured.getCpuPeriod()).isEqualTo(properties.getCpuPeriod());
        assertThat(captured.getCpuQuota()).isEqualTo(properties.getCpuQuota());
        assertThat(captured.getPidsLimit()).isEqualTo(properties.getMaxProcesses());

        verify(createCmd).withUser("1000:1000");
        verify(createCmd).withEnv("CI=true", "LANG=C.UTF-8");
        verify(removeCmd).exec();
    }
}
