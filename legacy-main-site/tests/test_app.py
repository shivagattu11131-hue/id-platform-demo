"""Tests for Legacy Main Site (Flask)."""
import json


class TestHealthEndpoint:
    def test_health_returns_200(self, client):
        resp = client.get("/api/health")
        assert resp.status_code == 200

    def test_health_returns_correct_site_info(self, client):
        data = client.get("/api/health").get_json()
        assert data["status"] == "healthy"
        assert data["site"] == "Main Site (Legacy Rails Monolith)"
        assert data["port"] == 3001
        assert data["auth_type"] == "session-based"

    def test_health_oidc_disabled_when_no_idp(self, client):
        data = client.get("/api/health").get_json()
        assert data["oidc_enabled"] is False


class TestRegistration:
    def test_register_new_user(self, client):
        resp = client.post("/api/auth/register", json={
            "username": "john",
            "email": "john@example.com",
            "password": "pass123",
            "display_name": "John",
        })
        assert resp.status_code == 201
        data = resp.get_json()
        assert data["email"] == "john@example.com"
        assert data["source"] == "main_site"
        assert "id" in data

    def test_register_missing_fields_returns_400(self, client):
        resp = client.post("/api/auth/register", json={
            "email": "a@b.com",
        })
        assert resp.status_code == 400
        assert "error" in resp.get_json()

    def test_register_duplicate_email_returns_409(self, client, seed_user):
        resp = client.post("/api/auth/register", json={
            "username": "john2",
            "email": "test@example.com",
            "password": "pass123",
        })
        assert resp.status_code == 409

    def test_register_duplicate_username_returns_409(self, client, seed_user):
        resp = client.post("/api/auth/register", json={
            "username": "testuser",
            "email": "other@example.com",
            "password": "pass123",
        })
        assert resp.status_code == 409


class TestLogin:
    def test_login_success(self, client, seed_user):
        resp = client.post("/api/auth/login", json={
            "email": "test@example.com",
            "password": "testpass123",
        })
        assert resp.status_code == 200
        data = resp.get_json()
        assert data["session_active"] is True
        assert data["source"] == "main_site"
        assert data["email"] == "test@example.com"

    def test_login_wrong_password_returns_401(self, client, seed_user):
        resp = client.post("/api/auth/login", json={
            "email": "test@example.com",
            "password": "wrongpassword",
        })
        assert resp.status_code == 401

    def test_login_nonexistent_user_returns_401(self, client):
        resp = client.post("/api/auth/login", json={
            "email": "nobody@example.com",
            "password": "pass",
        })
        assert resp.status_code == 401

    def test_login_missing_fields_returns_400(self, client):
        resp = client.post("/api/auth/login", json={"email": "a@b.com"})
        assert resp.status_code == 400


class TestDoLogin:
    def test_do_login_success(self, client, seed_user):
        resp = client.post("/do-login", data={
            "email": "test@example.com",
            "password": "testpass123",
        })
        assert resp.status_code == 200
        assert b"Welcome" in resp.data

    def test_do_login_invalid_credentials(self, client, seed_user):
        resp = client.post("/do-login", data={
            "email": "test@example.com",
            "password": "wrongpass",
        })
        assert resp.status_code == 200
        assert b"Legacy Authentication" in resp.data


class TestProfile:
    def test_get_profile_when_not_logged_in(self, client):
        resp = client.get("/api/users/me")
        assert resp.status_code == 401

    def test_get_profile_when_logged_in(self, client, seed_user):
        client.post("/api/auth/login", json={
            "email": "test@example.com",
            "password": "testpass123",
        })
        resp = client.get("/api/users/me")
        assert resp.status_code == 200
        data = resp.get_json()
        assert data["email"] == "test@example.com"
        assert data["source"] == "main_site"


class TestUserEndpoints:
    def test_list_all_users(self, client, seed_user):
        resp = client.get("/api/users/all")
        assert resp.status_code == 200
        data = resp.get_json()
        assert len(data) >= 1
        assert data[0]["email"] == "test@example.com"

    def test_user_count(self, client, seed_user):
        resp = client.get("/api/users/count")
        assert resp.status_code == 200
        data = resp.get_json()
        assert data["count"] >= 1
        assert data["site"] == "main_site"


class TestPages:
    def test_index_renders_legacy_home(self, client):
        resp = client.get("/")
        assert resp.status_code == 200
        assert b"Legacy Authentication" in resp.data

    def test_do_logout_renders_home(self, client):
        resp = client.get("/do-logout")
        assert resp.status_code == 200
        assert b"Legacy Authentication" in resp.data

    def test_login_oidc_redirects_when_oidc_disabled(self, client):
        resp = client.get("/login")
        assert resp.status_code == 302
        assert resp.headers["Location"].endswith("/")


class TestIndexPostLogin:
    def test_index_shows_legacy_dashboard_after_login(self, client, seed_user):
        client.post("/do-login", data={
            "email": "test@example.com",
            "password": "testpass123",
        })
        resp = client.get("/")
        assert resp.status_code == 200
        assert b"Legacy Session Auth" in resp.data
        assert b"Pre-Migration" in resp.data
        assert b"test@example.com" in resp.data
