package com.devflow.api.github.webhook.api;

import com.devflow.api.github.webhook.service.GithubWebhookService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GithubWebhookController.class)
@AutoConfigureMockMvc(addFilters = false)
class GithubWebhookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GithubWebhookService webhookService;

    @Test
    void acceptsVerifiedWebhookAndPassesRawBodyToService() throws Exception {
        when(webhookService.maxBodyBytes()).thenReturn(1024);
        doNothing().when(webhookService).ingest(eq("signature"), eq("issues"), eq("delivery-1"), any(byte[].class));

        mockMvc.perform(post("/api/v1/github/webhooks")
                        .header("X-Hub-Signature-256", "signature")
                        .header("X-GitHub-Event", "issues")
                        .header("X-GitHub-Delivery", "delivery-1")
                        .contentType("application/json")
                        .content("{\"action\":\"opened\"}"))
                .andExpect(status().isAccepted());

        verify(webhookService).ingest(eq("signature"), eq("issues"), eq("delivery-1"),
                argThat(body -> java.util.Arrays.equals(body,
                        "{\"action\":\"opened\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8))));
    }

    @Test
    void rejectsBodyAboveConfiguredLimit() throws Exception {
        when(webhookService.maxBodyBytes()).thenReturn(2);

        mockMvc.perform(post("/api/v1/github/webhooks")
                        .contentType("application/json")
                        .content("{}x"))
                .andExpect(status().isPayloadTooLarge());
    }
}
