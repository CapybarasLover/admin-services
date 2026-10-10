"""Регистрация шрифта с кириллицей.

Встроенные шрифты PDF кириллицу не умеют, поэтому нужен TTF.
В контейнере это DejaVu из пакета fonts-dejavu-core, на macOS — Arial.
Пути можно переопределить через REPORT_FONT_REGULAR / REPORT_FONT_BOLD.
"""

import os

from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont

CANDIDATES = [
    (
        "DejaVuSans",
        "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
    ),
    (
        "Arial",
        "/System/Library/Fonts/Supplemental/Arial.ttf",
        "/System/Library/Fonts/Supplemental/Arial Bold.ttf",
    ),
    (
        "LiberationSans",
        "/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf",
        "/usr/share/fonts/truetype/liberation/LiberationSans-Bold.ttf",
    ),
]


class FontsNotFound(RuntimeError):
    pass


def register_fonts() -> tuple[str, str]:
    """Возвращает пару (обычный, жирный) имён зарегистрированных шрифтов."""
    override = os.getenv("REPORT_FONT_REGULAR")
    if override:
        candidates = [("Custom", override, os.getenv("REPORT_FONT_BOLD", override))]
    else:
        candidates = CANDIDATES

    for name, regular, bold in candidates:
        if not os.path.exists(regular):
            continue
        bold_path = bold if os.path.exists(bold) else regular
        pdfmetrics.registerFont(TTFont(name, regular))
        bold_name = f"{name}-Bold"
        pdfmetrics.registerFont(TTFont(bold_name, bold_path))
        pdfmetrics.registerFontFamily(name, normal=name, bold=bold_name)
        return name, bold_name

    raise FontsNotFound(
        "Не найден TTF с кириллицей. Поставьте fonts-dejavu-core "
        "или задайте REPORT_FONT_REGULAR."
    )


def has_glyph(font_name: str, codepoint: int) -> bool:
    """Есть ли символ в шрифте.

    Знак рубля U+20BD появился только в 2014-м, и в системных шрифтах
    macOS его нет — вместо него в PDF попадал бы пустой квадрат.
    """
    try:
        return codepoint in pdfmetrics.getFont(font_name).face.charToGlyph
    except Exception:
        return False
