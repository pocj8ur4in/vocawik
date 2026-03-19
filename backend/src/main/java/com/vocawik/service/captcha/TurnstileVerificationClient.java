package com.vocawik.service.captcha;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** HTTP client for Cloudflare Turnstile siteverify requests. */
@Slf4j
@Component
public class TurnstileVerificationClient {

    private final RestClient restClient = RestClient.create();

    /** Verifies a Turnstile response token against the provider API. */
    public boolean verify(String siteverifyUrl, String secretKey, String token, String remoteIp) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("secret", secretKey);
        form.add("response", token);
        if (remoteIp != null && !remoteIp.isBlank()) {
            form.add("remoteip", remoteIp);
        }

        try {
            TurnstileSiteverifyResponse response =
                    restClient
                            .post()
                            .uri(siteverifyUrl)
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .body(form)
                            .retrieve()
                            .body(TurnstileSiteverifyResponse.class);
            return response != null && response.success();
        } catch (RestClientException ex) {
            logger.warn("Turnstile verification request failed", ex);
            return false;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TurnstileSiteverifyResponse(boolean success, List<String> errorCodes) {}
}
