"""Tests for Legacy MA Site (Flask)."""
import json


class TestHealthEndpoint:
    def test_health_returns_200(self, client):
        resp = client.get("/api/health")
        assert resp.status_code == 200

    def test_health_returns_correct_site_info(self, client):
        data = client.get("/api/health").get_json()
        assert data["status"] == "healthy"
        assert data["site"] == "MA Site (Acquired - Legacy Monolith)"
        assert data["port"] == 3002
        assert data["auth_type"] == "session-based"

    def test_health_oidc_disabled_when_no_idp(self, client):
        data = client.get("/api/health").get_json()
        assert data["oidc_enabled"] is False


class TestRegistration:
    def test_register_new_user(self, client):
        resp = client.post("/api/auth/register", json={
            "username": "mauser",
            "email": "ma@example.com",
            "password": "mapass123",
            "display_name": "MA User",
            "company_name": "Acquired Corp",
        })
        assert resp.status_code == 201
        data = resp.get_json()
        assert data["email"] == "ma@example.com"
        assert data["source"] == "ma_site"
        assert data["company_name"] == "Acquired Corp"
        assert "id" in data

    def test_register_missing_fields_returns_400(self, client):
        resp = client.post("/api/auth/register", json={"email": "a@b.com"})
        assert resp.status_code == 400

    def test_register_duplicate_email_returns_409(self, client, seed_user):
        resp = client.post("/api/auth/register", json={
            "username": "other",
            "email": "test@example.com",
            "password": "pass",
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
        assert data["source"] == "ma_site"

    def test_login_wrong_password_returns_401(self, client, seed_user):
        resp = client.post("/api/auth/login", json={
            "email": "test@example.com",
            "password": "wrongpass",
        })
        assert resp.status_code == 401

    def test_login_missing_fields_returns_400(self, client):
        resp = client.post("/api/auth/login", json={"email": "x@y.com"})
        assert resp.status_code == 400


class TestDoLogin:
    def test_do_login_success(self, client, seed_user):
        resp = client.post("/do-login", data={
            "email": "test@example.com",
            "password": "testpass123",
        })
        assert resp.status_code == 200
        assert b"Welcome" in resp.data
        assert b"test@example.com" in resp.data

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
        assert data["source"] == "ma_site"


class TestProfileUpdate:
    def test_update_display_name(self, client, seed_user):
        client.post("/api/auth/login", json={
            "email": "test@example.com",
            "password": "testpass123",
        })
        resp = client.put("/api/users/me", json={"display_name": "Updated Name"})
        assert resp.status_code == 200
        assert resp.get_json()["display_name"] == "Updated Name"

    def test_update_company_name(self, client, seed_user):
        client.post("/api/auth/login", json={
            "email": "test@example.com",
            "password": "testpass123",
        })
        resp = client.put("/api/users/me", json={"company_name": "New Corp"})
        assert resp.status_code == 200
        assert resp.get_json()["company_name"] == "New Corp"

    def test_update_not_logged_in_returns_401(self, client):
        resp = client.put("/api/users/me", json={"display_name": "X"})
        assert resp.status_code == 401


class TestDeleteUser:
    def test_delete_user(self, client, seed_user):
        client.post("/api/auth/login", json={
            "email": "test@example.com",
            "password": "testpass123",
        })
        resp = client.delete("/api/users/me")
        assert resp.status_code == 200
        assert "deleted" in resp.get_json()["message"]

    def test_delete_not_logged_in_returns_401(self, client):
        resp = client.delete("/api/users/me")
        assert resp.status_code == 401


class TestUserEndpoints:
    def test_list_all_users(self, client, seed_user):
        resp = client.get("/api/users/all")
        assert resp.status_code == 200
        data = resp.get_json()
        assert len(data) >= 1

    def test_user_count(self, client, seed_user):
        resp = client.get("/api/users/count")
        assert resp.status_code == 200
        data = resp.get_json()
        assert data["count"] >= 1
        assert data["site"] == "ma_site"


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
