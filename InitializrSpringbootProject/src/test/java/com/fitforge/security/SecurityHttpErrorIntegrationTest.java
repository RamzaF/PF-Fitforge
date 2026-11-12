package com.fitforge.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import static org.assertj.core.api.Assertions.assertThat;

// A real servlet container is needed: MockMvc does not perform Tomcat ERROR dispatches.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SecurityHttpErrorIntegrationTest {
    @Autowired TestRestTemplate http;

    @Test void missingPublicResourceKeeps404InsteadOfRedirectingToLogin() {
        var response = http.getForEntity("/uploads/missing-resource.png", String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(404);
    }
}
