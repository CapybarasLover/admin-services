"""Палитры отчёта.

Повторяют цветовой язык интерфейса: светофор остатков и знак результата
означают в PDF то же самое, что на экране. Цвета сняты с токенов фронтенда
(frontend/src/index.css), а не подобраны отдельно: фирменный фиолетовый
#8D67E3 работает двумя значениями — заливки и линии отдельно от акцента в
тексте, потому что одно значение не проходит по контрасту в обеих ролях.
"""

from dataclasses import dataclass
from typing import Literal

from reportlab.lib import colors
from reportlab.lib.colors import Color

Theme = Literal["light", "dark"]


@dataclass(frozen=True)
class Palette:
    page: Color
    card: Color
    ink: Color
    muted: Color
    line: Color
    line_2: Color
    zebra: Color
    brand: Color
    brand_ink: Color
    positive: Color
    negative: Color
    status_out: Color
    status_few: Color
    status_enough: Color

    def status(self, code: str) -> Color:
        return {
            "OUT": self.status_out,
            "FEW": self.status_few,
            "ENOUGH": self.status_enough,
        }[code]

    def sign(self, value) -> Color:
        if value > 0:
            return self.positive
        if value < 0:
            return self.negative
        return self.ink


LIGHT = Palette(
    page=colors.HexColor("#FAFAFD"),
    card=colors.HexColor("#FFFFFF"),
    ink=colors.HexColor("#14121B"),
    muted=colors.HexColor("#6A6676"),
    line=colors.HexColor("#E2E1E7"),
    line_2=colors.HexColor("#CECCD7"),
    zebra=colors.HexColor("#F3F1F8"),
    brand=colors.HexColor("#7F58D8"),
    brand_ink=colors.HexColor("#6338B9"),
    positive=colors.HexColor("#007746"),
    negative=colors.HexColor("#C22630"),
    status_out=colors.HexColor("#C22630"),
    status_few=colors.HexColor("#A16100"),
    status_enough=colors.HexColor("#007746"),
)

# Границы интерфейса на тёмном полупрозрачные; в PDF прозрачности не место,
# поэтому те же 10% и 20% белого уже смешаны с подложкой карточки.
DARK = Palette(
    page=colors.HexColor("#0D0C10"),
    card=colors.HexColor("#16151B"),
    ink=colors.HexColor("#F2F0F7"),
    muted=colors.HexColor("#8B8799"),
    line=colors.HexColor("#2C2B31"),
    line_2=colors.HexColor("#3B3A3E"),
    zebra=colors.HexColor("#16151B"),
    brand=colors.HexColor("#8D67E3"),
    brand_ink=colors.HexColor("#A98CEC"),
    positive=colors.HexColor("#55D391"),
    negative=colors.HexColor("#EF6B6B"),
    status_out=colors.HexColor("#EF6B6B"),
    status_few=colors.HexColor("#F0B23E"),
    status_enough=colors.HexColor("#55D391"),
)

PALETTES: dict[str, Palette] = {"light": LIGHT, "dark": DARK}


def palette_for(theme: str | None) -> Palette:
    return PALETTES.get((theme or "light").lower(), LIGHT)
