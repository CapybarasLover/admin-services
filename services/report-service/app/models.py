"""Модели входящего JSON.

Зеркалят SummaryReportDto бэкенда. Лишние поля Jackson (например
dateRangeValid от @AssertTrue) pydantic молча игнорирует.
"""

from datetime import date, datetime
from decimal import Decimal
from typing import Literal

from pydantic import BaseModel

ItemStatus = Literal["ENOUGH", "FEW", "OUT"]


class StorageItem(BaseModel):
    name: str
    count: int
    status: ItemStatus
    id: int | None = None
    # На master поле cost пока не отдаётся, поэтому оно необязательное.
    cost: Decimal | None = None


class StorageStats(BaseModel):
    admissionsCount: int
    admissionsTotal: int
    sellsCount: int
    sellsTotal: int
    writeOffsCount: int
    writeOffsTotal: int
    spending: Decimal
    revenue: Decimal
    profit: Decimal


class ProductStats(BaseModel):
    admissionsCount: int
    admissionsTotal: int
    sellsCount: int
    sellsTotal: int
    writeOffsCount: int
    writeOffsTotal: int
    productSpending: Decimal
    productRevenue: Decimal
    productProfit: Decimal


class SummaryReport(BaseModel):
    storageName: str
    dateFrom: date
    dateTo: date
    generatedAt: datetime
    currentStock: list[StorageItem] = []
    storageStats: StorageStats
    productStats: dict[str, ProductStats] = {}
