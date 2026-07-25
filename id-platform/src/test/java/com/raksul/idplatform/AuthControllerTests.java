package com.raksul.idplatform;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthControllerTests {

    @Autowired
    private TestRestTemplate restTemplate;

    private void registerUser(String email, String password, String displayName) {
        Map<String, String> body = Map.of(
                "email", email,
                "password", password,
                "displayName", displayName
        );
        restTemplate.postForEntity("/api/auth/register", body, Map.class);
    }

    @Test
    void registerNewUser() {
        ResponseEntity<Map> resp = restTemplate.postForEntity("/api/auth/register", Map.of(
                "email", "newuser@test.com",
                "password", "password123",
                "displayName", "New User"
        ), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map body = resp.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("accessToken")).isNotNull();
        assertThat(body.get("refreshToken")).isNotNull();
        assertThat(body.get("user")).isNotNull();
    }

    @Test
    void registerDuplicateEmailFails() {
        registerUser("dup@test.com", "password123", "Dup User");

        ResponseEntity<Map> resp = restTemplate.postForEntity("/api/auth/register", Map.of(
                "email", "dup@test.com",
                "password", "password123",
                "displayName", "Dup User 2"
        ), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().get("error")).isNotNull();
    }

    @Test
    void loginWithValidCredentials() {
        registerUser("login@test.com", "password123", "Login User");

        ResponseEntity<Map> resp = restTemplate.postForEntity("/api/auth/login", Map.of(
                "email", "login@test.com",
                "password", "password123"
        ), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map body = resp.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("accessToken")).isNotNull();
    }

    @Test
    void loginWithInvalidPasswordFails() {
        registerUser("wrong@test.com", "password123", "Wrong User");

        ResponseEntity<Map> resp = restTemplate.postForEntity("/api/auth/login", Map.of(
                "email", "wrong@test.com",
                "password", "wrongpassword"
        ), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void loginWithNonexistentUserFails() {
        ResponseEntity<Map> resp = restTemplate.postForEntity("/api/auth/login", Map.of(
                "email", "ghost@test.com",
                "password", "password123"
        ), Map.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
