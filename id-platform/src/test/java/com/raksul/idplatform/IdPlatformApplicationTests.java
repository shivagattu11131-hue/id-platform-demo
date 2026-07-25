package com.raksul.idplatform;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class IdPlatformApplicationTests {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void contextLoads() {
    }

    @Test
    void healthEndpointReturnsHealthy() {
        ResponseEntity<Map> resp = restTemplate.getForEntity("/api/health", Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().get("status")).isEqualTo("healthy");
    }

    @Test
    void discoveryDocumentContainsRequiredFields() {
        ResponseEntity<Map> resp = restTemplate.getForEntity("/.well-known/openid-configuration", Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map body = resp.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("issuer")).isNotNull();
        assertThat(body.get("authorization_endpoint")).isNotNull();
        assertThat(body.get("token_endpoint")).isNotNull();
        assertThat(body.get("userinfo_endpoint")).isNotNull();
        assertThat(body.get("jwks_uri")).isNotNull();
    }

    @Test
    void jwksEndpointReturnsKeys() {
        ResponseEntity<Map> resp = restTemplate.getForEntity("/jwks.json", Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().get("keys")).isNotNull();
    }
}
