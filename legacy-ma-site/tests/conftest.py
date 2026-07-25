import os
import tempfile
import pytest

os.environ.setdefault("ID_PLATFORM_URL", "http://localhost:3000")

from app import app as flask_app, init_db


@pytest.fixture
def app(tmp_path):
    flask_app.config["TESTING"] = True

    import app as app_module
    original_db = app_module.DB_PATH
    app_module.DB_PATH = str(tmp_path / "test_ma_site.db")

    init_db()
    yield flask_app
    app_module.DB_PATH = original_db


@pytest.fixture
def client(app):
    return app.test_client()


@pytest.fixture
def seed_user(client):
    """Register a test user and return the response data."""
    resp = client.post("/api/auth/register", json={
        "username": "testuser",
        "email": "test@example.com",
        "password": "testpass123",
        "display_name": "Test User",
        "company_name": "Test Corp",
    })
    return resp.get_json()
