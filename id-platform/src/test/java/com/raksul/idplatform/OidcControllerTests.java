package com.raksul.idplatform;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OidcControllerTests {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void discoveryDocumentHasAllRequiredFields() {
        ResponseEntity<Map> resp = restTemplate.getForEntity(
                "/.well-known/openid-configuration", Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map body = resp.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("issuer")).isNotNull();
        assertThat(body.get("authorization_endpoint")).isNotNull();
        assertThat(body.get("token_endpoint")).isNotNull();
        assertThat(body.get("userinfo_endpoint")).isNotNull();
        assertThat(body.get("jwks_uri")).isNotNull();
        assertThat(body.get("response_types_supported")).isEqualTo(List.of("code"));
        assertThat((List) body.get("grant_types_supported")).containsExactly("authorization_code", "refresh_token");
    }

    @Test
    void jwksReturnsRsaKeys() {
        ResponseEntity<Map> resp = restTemplate.getForEntity("/jwks.json", Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map body = resp.getBody();
        assertThat(body).isNotNull();
        List keys = (List) body.get("keys");
        assertThat(keys).isNotEmpty();

        Map key = (Map) keys.get(0);
        assertThat(key.get("kty")).isEqualTo("RSA");
        assertThat(key.get("alg")).isEqualTo("RS256");
        assertThat(key.get("use")).isEqualTo("sig");
    }

    @Test
    void tokenEndpointRejectsUnsupportedGrantType() {
        ResponseEntity<Map> resp = restTemplate.postForEntity("/oauth2/token",
                Map.of("grant_type", "implicit"), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().get("error")).isEqualTo("unsupported_grant_type");
    }

    @Test
    void tokenEndpointRejectsMissingCode() {
        ResponseEntity<Map> resp = restTemplate.postForEntity("/oauth2/token",
                Map.of("grant_type", "authorization_code", "client_id", "main-site"),
                Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().get("error")).isEqualTo("invalid_request");
    }

    @Test
    void tokenEndpointRejectsUnknownClient() {
        ResponseEntity<Map> resp = restTemplate.postForEntity("/oauth2/token",
                Map.of("grant_type", "authorization_code", "code", "fake", "client_id", "unknown"),
                Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().get("error")).isEqualTo("invalid_client");
    }

    @Test
    void userinfoRejectsMissingAuthHeader() {
        HttpHeaders headers = new HttpHeaders();
        ResponseEntity<Map> resp = restTemplate.exchange(
                "/oauth2/userinfo", HttpMethod.GET,
                new HttpEntity<>(headers), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void userinfoRejectsInvalidToken() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer invalid.token.here");
        ResponseEntity<Map> resp = restTemplate.exchange(
                "/oauth2/userinfo", HttpMethod.GET,
                new HttpEntity<>(headers), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void authorizeGetReturnsLoginPage() {
        ResponseEntity<String> resp = restTemplate.getForEntity(
                "/oauth2/authorize?response_type=code&client_id=main-site"
                        + "&redirect_uri=http://localhost:3001/callback"
                        + "&scope=openid&state=test123",
                String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).contains("login");
    }

    @Test
    void authorizeRejectsInvalidClient() {
        ResponseEntity<String> resp = restTemplate.getForEntity(
                "/oauth2/authorize?response_type=code&client_id=unknown"
                        + "&redirect_uri=http://localhost:3001/callback"
                        + "&scope=openid",
                String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).contains("Unknown client_id");
    }

    @Test
    void authorizeRejectsInvalidRedirectUri() {
        ResponseEntity<String> resp = restTemplate.getForEntity(
                "/oauth2/authorize?response_type=code&client_id=main-site"
                        + "&redirect_uri=http://evil.com/callback"
                        + "&scope=openid",
                String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).contains("Invalid redirect_uri");
    }

    @Test
    void authorizeRejectsUnsupportedResponseType() {
        ResponseEntity<String> resp = restTemplate.getForEntity(
                "/oauth2/authorize?response_type=token&client_id=main-site"
                        + "&redirect_uri=http://localhost:3001/callback"
                        + "&scope=openid",
                String.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).contains("Unsupported response_type");
    }
}
