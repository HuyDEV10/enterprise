from fastapi import APIRouter, HTTPException, Query

from app.schemas.m5_dataset import M5DemandSeries
from app.services.m5_dataset_service import m5_dataset_service

router = APIRouter(prefix="/api/v1/datasets/m5", tags=["m5-dataset"])


@router.get("/series/{item_id}", response_model=M5DemandSeries)
def get_m5_series(
    item_id: str,
    store_id: str = Query(default="CA_1", alias="storeId"),
) -> M5DemandSeries:
    try:
        return m5_dataset_service.load_series(item_id=item_id, store_id=store_id)
    except FileNotFoundError as exc:
        raise HTTPException(status_code=503, detail={"code": "M5_DATASET_UNAVAILABLE", "message": str(exc)}) from exc
    except LookupError as exc:
        raise HTTPException(status_code=404, detail={"code": "M5_SERIES_NOT_FOUND", "message": str(exc)}) from exc
    except ValueError as exc:
        raise HTTPException(status_code=422, detail={"code": "INVALID_M5_DATASET", "message": str(exc)}) from exc
