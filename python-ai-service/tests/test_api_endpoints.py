from fastapi.testclient import TestClient

from app.main import app
from app.api.v1 import maintenance, replenishment


client = TestClient(app)


def test_health_endpoint_returns_ok() -> None:
    response = client.get("/health")
    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "ok"


def test_rul_predict_endpoint_returns_payload(monkeypatch) -> None:
    def fake_predict_rul(spare_part_id: int) -> dict:
        return {
            "spare_part_id": spare_part_id,
            "predicted_rul": 321.0,
            "confidence_interval": [200.0, 430.0],
            "alert": "WARNING",
            "alert_message": "test",
            "top_features": {},
            "data_quality": "sufficient",
            "timestamp": "2026-03-30T16:00:00",
        }

    monkeypatch.setattr(maintenance, "predict_rul", fake_predict_rul)

    response = client.post("/api/v1/rul/predict", json={"spare_part_id": 1})
    assert response.status_code == 200
    assert response.json()["predicted_rul"] == 321.0


def test_replenishment_endpoint_keeps_suggestion_field(monkeypatch) -> None:
    def fake_suggest(ids: list[int]) -> list[dict]:
        return [
            {
                "spare_part_id": ids[0],
                "spare_part_name": "p1",
                "current_stock": 10,
                "predicted_demand": {"total": 20},
                "suggestion": {
                    "suggested_qty": 15,
                    "suggested_date": "2026-04-01",
                },
                "priority": "HIGH",
                "alert_message": "need purchase",
                "data_quality": "sufficient",
                "n_months_available": 12,
                "timestamp": "2026-03-30T16:00:00",
            }
        ]

    monkeypatch.setattr(replenishment, "suggest_replenishment", fake_suggest)

    response = client.post("/api/v1/replenishment/suggest", json={"spare_part_ids": [1]})
    assert response.status_code == 200
    body = response.json()
    assert body[0]["suggestion"]["suggested_qty"] == 15
    assert body[0]["alert_message"] == "need purchase"
