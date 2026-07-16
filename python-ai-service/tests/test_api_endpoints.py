from fastapi.testclient import TestClient

from app.main import app
from app.api.v1 import maintenance, replenishment, jobs


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


def test_submit_replenishment_job(monkeypatch) -> None:
    class FakeTask:
        id = "task-123"

    class FakeDelay:
        @staticmethod
        def delay(spare_part_ids: list[int]):
            assert spare_part_ids == [1, 2]
            return FakeTask()

    monkeypatch.setattr(jobs, "run_replenishment_job", FakeDelay)

    response = client.post("/api/v1/jobs/replenishment", json={"spare_part_ids": [1, 2]})
    assert response.status_code == 200
    assert response.json()["task_id"] == "task-123"


def test_get_job_status_success(monkeypatch) -> None:
    class FakeResult:
        status = "SUCCESS"
        result = {"task_id": "task-123", "status": "SUCCESS", "result": []}

    def fake_async_result(task_id: str, app=None):
        assert task_id == "task-123"
        return FakeResult()

    monkeypatch.setattr(jobs, "has_task", lambda task_id: True)
    monkeypatch.setattr(jobs, "AsyncResult", fake_async_result)

    response = client.get("/api/v1/jobs/task-123")
    assert response.status_code == 200
    assert response.json()["status"] == "SUCCESS"


def test_algorithm_endpoints() -> None:
    # 构造一些模拟数据进行训练
    X_train = [
        [1.0, 2.0, 0.5],
        [1.5, 2.5, 0.6],
        [0.0, 0.0, 0.1],
        [2.0, 3.0, 0.7],
        [0.0, 0.0, 0.0]
    ]
    y_train = [10.0, 15.0, 0.0, 20.0, 0.0]
    xyz_train = ["X", "X", "Y", "Z", "Y"]

    # 1. 测试训练接口
    train_payload = {
        "X": X_train,
        "y": y_train,
        "xyz_groups": xyz_train
    }
    response = client.post("/api/algorithm/train", json=train_payload)
    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "success"
    assert "k_params" in data["metrics"]
    
    # 2. 测试预测接口
    predict_payload = {
        "X": [
            [1.2, 2.2, 0.55],
            [0.1, 0.1, 0.1]
        ],
        "xyz_groups": ["X", "Y"]
    }
    response = client.post("/api/algorithm/predict", json=predict_payload)
    assert response.status_code == 200
    data = response.json()
    assert "predictions" in data
    assert len(data["predictions"]) == 2
    
    # 校验结果字段
    first_pred = data["predictions"][0]
    assert "p_t" in first_pred
    assert "mu_t" in first_pred
    assert "k" in first_pred
    assert "lower_bound" in first_pred
    assert "upper_bound" in first_pred
    assert 0.0 <= first_pred["p_t"] <= 1.0
    assert first_pred["mu_t"] > 0.0


def test_inventory_calc_endpoint() -> None:
    payload = {
        "p_t": 0.8,
        "mu_t": 15.0,
        "k": 2.5,
        "L": 10.0,
        "W": 22,
        "M": 5000,
        "alpha": 0.95
    }
    response = client.post("/api/algorithm/inventory-calc", json=payload)
    assert response.status_code == 200
    data = response.json()
    assert "rop" in data
    assert "ss" in data
    assert "mean_demand" in data
    assert isinstance(data["rop"], int)
    assert isinstance(data["ss"], int)
    assert isinstance(data["mean_demand"], float)
    assert data["rop"] >= 0
    import math
    expected_ss = data["rop"] - int(math.ceil(data["mean_demand"]))
    assert data["ss"] == expected_ss


