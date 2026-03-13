package com.vocawik.service.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vocawik.infrastructure.mail.EmailService;
import com.vocawik.repository.user.UserRepository;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

class VerificationServiceTest {

    private static final Duration EMAIL_VERIFY_TOKEN_TTL = Duration.ofMinutes(10);
    private static final Duration REGISTER_TICKET_TTL = Duration.ofMinutes(15);
    private static final Duration EMAIL_VERIFY_REQUEST_LOCK_TTL = Duration.ofMinutes(16);

    private UserRepository userRepository;
    private StringRedisTemplate stringRedisTemplate;
    private ValueOperations<String, String> valueOperations;
    private EmailService emailService;
    private VerificationService verificationService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        stringRedisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        emailService = mock(EmailService.class);

        verificationService =
                new VerificationService(
                        userRepository,
                        stringRedisTemplate,
                        mock(PasswordEncoder.class),
                        emailService);
        ReflectionTestUtils.setField(
                verificationService,
                "emailVerificationFrontendUrl",
                "https://vocawik.test/register/verify");
        ReflectionTestUtils.setField(
                verificationService, "emailVerificationSubject", "Verify your email");
        ReflectionTestUtils.setField(
                verificationService, "emailVerificationContentTemplate", "{verifyUrl}");
    }

    @Test
    @DisplayName(
            "Requesting registration verification should send register link with email and register ticket")
    void requestEmailVerification_shouldSendRegisterLinkWithEmailAndRegisterTicket() {
        String email = "User@example.com";
        when(userRepository.findByEmailIgnoreCaseAndIsDeletedFalse("user@example.com"))
                .thenReturn(Optional.empty());
        when(valueOperations.setIfAbsent(
                        anyString(), anyString(), eq(EMAIL_VERIFY_REQUEST_LOCK_TTL)))
                .thenReturn(true);

        String requestId = verificationService.requestEmailVerification(email);

        verify(emailService)
                .send(
                        eq("user@example.com"),
                        eq("Verify your email"),
                        argThat(
                                body ->
                                        body.startsWith(
                                                        "https://vocawik.test/register/verify?email=user%40example.com&registerTicket=")
                                                && !body.endsWith("registerTicket=")));
        verify(valueOperations)
                .set(
                        argThat(key -> key.startsWith("auth:register:ticket:")),
                        eq("user@example.com"),
                        eq(REGISTER_TICKET_TTL));
        verify(valueOperations)
                .set(
                        argThat(key -> key.startsWith("auth:email:verify:")),
                        argThat(
                                payload ->
                                        payload.startsWith("register:")
                                                && payload.contains(requestId)
                                                && payload.contains("user@example.com")),
                        eq(EMAIL_VERIFY_TOKEN_TTL));
        assertThat(requestId).isNotBlank();
    }

    @Test
    @DisplayName("Confirming verification token should return the pre-issued register ticket")
    void confirmEmailVerification_shouldReturnPreIssuedRegisterTicket() {
        String email = "user@example.com";
        String requestId = UUID.randomUUID().toString();
        String registerTicket = "register-ticket";
        when(valueOperations.getAndDelete(anyString()))
                .thenReturn("register:" + requestId + "\n" + email + "\n" + registerTicket);

        String result =
                verificationService.confirmEmailVerification("verification-token", requestId);

        assertThat(result).isEqualTo(registerTicket);
        verify(stringRedisTemplate)
                .delete(
                        argThat(
                                (String key) ->
                                        key.startsWith("auth:email:verify:register:email:")));
    }
}
