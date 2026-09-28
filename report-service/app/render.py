"""Рендер SummaryReport в PDF."""

from decimal import ROUND_HALF_UP, Decimal
from io import BytesIO
from types import SimpleNamespace

from reportlab.lib.enums import TA_RIGHT
from reportlab.lib.pagesizes import A4, landscape
from reportlab.lib.styles import ParagraphStyle
from reportlab.lib.units import mm
from reportlab.platypus import (
    KeepTogether,
    Paragraph,
    SimpleDocTemplate,
    Spacer,
    Table,
    TableStyle,
)

from .fonts import has_glyph, register_fonts
from .models import SummaryReport
from .theme import Palette, palette_for

FONT, FONT_BOLD = register_fonts()

NBSP = " "
MINUS = "−" if has_glyph(FONT, 0x2212) else "-"
# Если шрифт не знает знака рубля, пишем словом, а не квадратом.
CURRENCY = "₽" if has_glyph(FONT, 0x20BD) else "руб."
# Перед каждой подписью в интерфейсе стоит короткая засечка; в PDF её роль
# играет глиф — брусок, если шрифт его знает, иначе длинное тире.
MARK = "▬" if has_glyph(FONT, 0x25AC) else "—"

STATUS_LABEL = {
    "OUT": "Закончился",
    "FEW": "Заканчивается",
    "ENOUGH": "В наличии",
}

MONTHS = [
    "января", "февраля", "марта", "апреля", "мая", "июня",
    "июля", "августа", "сентября", "октября", "ноября", "декабря",
]


def money(value) -> str:
    if value is None:
        return "—"
    amount = Decimal(value).quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)
    body = f"{abs(amount):,.2f}".replace(",", NBSP).replace(".", ",")
    return f"{MINUS if amount < 0 else ''}{body}{NBSP}{CURRENCY}"


def number(value: int) -> str:
    return f"{value:,}".replace(",", NBSP)


def ru_date(value) -> str:
    return f"{value.day} {MONTHS[value.month - 1]} {value.year}"


def ru_datetime(value) -> str:
    return f"{ru_date(value)}, {value:%H:%M}"


def hex_of(color) -> str:
    """reportlab отдаёт hexval() как 0xrrggbb, а разметке абзаца нужен #rrggbb."""
    return "#" + color.hexval()[2:]


def _styles(palette: Palette) -> SimpleNamespace:
    def style(name: str, **kwargs) -> ParagraphStyle:
        base = {"fontName": FONT, "fontSize": 9, "leading": 12, "textColor": palette.ink}
        base.update(kwargs)
        return ParagraphStyle(name, **base)

    return SimpleNamespace(
        title=style("title", fontName=FONT_BOLD, fontSize=18, leading=22),
        subtitle=style("subtitle", fontSize=10, leading=14, textColor=palette.muted),
        section=style("section", fontName=FONT_BOLD, fontSize=8.5, leading=11, textColor=palette.muted, spaceAfter=7),
        cell=style("cell"),
        cell_right=style("cellRight", alignment=TA_RIGHT),
        head=style("head", fontName=FONT_BOLD, fontSize=8, textColor=palette.muted),
        head_right=style("headRight", fontName=FONT_BOLD, fontSize=8, textColor=palette.muted, alignment=TA_RIGHT),
        empty=style("empty", textColor=palette.muted),
        kpi_label=style("kpiLabel", fontName=FONT_BOLD, fontSize=7, textColor=palette.muted),
        kpi_hint=style("kpiHint", fontSize=7, leading=9, textColor=palette.muted),
        counter=style("counter", fontSize=12, leading=15),
    )


def eyebrow(palette: Palette, style: ParagraphStyle, text: str) -> Paragraph:
    """Подпись в верхнем регистре с фирменной засечкой — как на экране."""
    return Paragraph(
        f'<font color="{hex_of(palette.brand)}">{MARK}</font>{NBSP}{NBSP}{text.upper()}',
        style,
    )


def _value_size(text: str) -> float:
    """Сумма — главное число страницы, но в карточку она должна влезать."""
    length = len(text)
    if length <= 13:
        return 27
    if length <= 16:
        return 23
    if length <= 20:
        return 19
    return 16


def _kpi_card(
    palette: Palette,
    s: SimpleNamespace,
    label: str,
    value: str,
    hint: str,
    card_width: float,
    value_color=None,
) -> Table:
    size = _value_size(value)
    value_style = ParagraphStyle(
        "kpiValue",
        fontName=FONT_BOLD,
        fontSize=size,
        leading=size * 1.22,
        textColor=value_color or palette.brand,
    )
    inner = Table(
        [
            [eyebrow(palette, s.kpi_label, label)],
            [Paragraph(value, value_style)],
            [Paragraph(hint, s.kpi_hint)],
        ],
        colWidths=[card_width],
    )
    inner.setStyle(
        TableStyle(
            [
                ("BACKGROUND", (0, 0), (-1, -1), palette.card),
                ("LEFTPADDING", (0, 0), (-1, -1), 10),
                ("RIGHTPADDING", (0, 0), (-1, -1), 10),
                ("TOPPADDING", (0, 0), (0, 0), 10),
                ("BOTTOMPADDING", (0, -1), (0, -1), 10),
                ("TOPPADDING", (0, 1), (0, -1), 3),
                ("BOTTOMPADDING", (0, 0), (0, -2), 3),
                ("BOX", (0, 0), (-1, -1), 0.6, palette.line),
                ("LINEABOVE", (0, 0), (-1, 0), 1.4, palette.brand),
                ("VALIGN", (0, 0), (-1, -1), "TOP"),
            ]
        )
    )
    return inner


def _counters_strip(palette: Palette, s: SimpleNamespace, stats, width: float) -> Table:
    """Количество операций — подпись под суммами, а не третья таблица."""

    def cell(label: str, count: int, total: int, verb: str) -> Paragraph:
        return Paragraph(
            f'<font size="7" color="{hex_of(palette.brand)}">{MARK}</font>'
            f'<font size="7" color="{hex_of(palette.muted)}">'
            f"{NBSP}{NBSP}{label.upper()}</font><br/>"
            f'<font color="{hex_of(palette.brand)}">{number(count)}</font>'
            f'<font size="9" color="{hex_of(palette.muted)}">'
            f"{NBSP}{NBSP}·{NBSP}{NBSP}{number(total)}{NBSP}шт. {verb}</font>",
            s.counter,
        )

    strip = Table(
        [[
            cell("Поступления", stats.admissionsCount, stats.admissionsTotal, "принято"),
            cell("Продажи", stats.sellsCount, stats.sellsTotal, "продано"),
            cell("Списания", stats.writeOffsCount, stats.writeOffsTotal, "списано"),
        ]],
        colWidths=[width / 3] * 3,
    )
    strip.setStyle(
        TableStyle(
            [
                ("BACKGROUND", (0, 0), (-1, -1), palette.card),
                ("BOX", (0, 0), (-1, -1), 0.6, palette.line),
                ("LINEAFTER", (0, 0), (-2, -1), 0.6, palette.line),
                ("LEFTPADDING", (0, 0), (-1, -1), 10),
                ("RIGHTPADDING", (0, 0), (-1, -1), 10),
                ("TOPPADDING", (0, 0), (-1, -1), 8),
                ("BOTTOMPADDING", (0, 0), (-1, -1), 8),
                ("VALIGN", (0, 0), (-1, -1), "MIDDLE"),
            ]
        )
    )
    return strip


def _cards_row(cards: list[Table], width: float) -> Table:
    row = Table([cards], colWidths=[width / len(cards)] * len(cards))
    row.setStyle(
        TableStyle(
            [
                ("LEFTPADDING", (0, 0), (-1, -1), 0),
                ("RIGHTPADDING", (0, 0), (-1, -1), 4),
                ("TOPPADDING", (0, 0), (-1, -1), 0),
                ("BOTTOMPADDING", (0, 0), (-1, -1), 0),
                ("VALIGN", (0, 0), (-1, -1), "TOP"),
            ]
        )
    )
    return row


def _data_table(palette: Palette, header: list, rows: list[list], col_widths: list[float], right_from: int) -> Table:
    table = Table([header] + rows, colWidths=col_widths, repeatRows=1)
    style = [
        ("FONTNAME", (0, 0), (-1, -1), FONT),
        ("FONTSIZE", (0, 0), (-1, -1), 9),
        ("VALIGN", (0, 0), (-1, -1), "MIDDLE"),
        ("ALIGN", (right_from, 0), (-1, -1), "RIGHT"),
        ("LINEBELOW", (0, 0), (-1, 0), 0.6, palette.line_2),
        ("LINEBELOW", (0, 1), (-1, -2), 0.3, palette.line),
        ("TOPPADDING", (0, 0), (-1, -1), 5),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 5),
        ("LEFTPADDING", (0, 0), (-1, -1), 4),
        ("RIGHTPADDING", (0, 0), (-1, -1), 4),
    ]
    for index in range(1, len(rows) + 1):
        if index % 2 == 0:
            style.append(("BACKGROUND", (0, index), (-1, index), palette.zebra))
    table.setStyle(TableStyle(style))
    return table


def _head(s: SimpleNamespace, text: str, right: bool = False) -> Paragraph:
    return Paragraph(text.upper(), s.head_right if right else s.head)


def _ops_cell(palette: Palette, s: SimpleNamespace, ops: int, total: int) -> Paragraph:
    if not ops:
        return Paragraph("—", s.cell_right)
    return Paragraph(
        f'{number(ops)} <font color="{hex_of(palette.muted)}">/ {number(total)}{NBSP}шт.</font>',
        s.cell_right,
    )


def _page_decorator(palette: Palette):
    def decorate(canvas, doc):
        width, height = doc.pagesize
        # Фон рисуется в начале страницы, поэтому содержимое ложится поверх.
        canvas.saveState()
        canvas.setFillColor(palette.page)
        canvas.rect(0, 0, width, height, stroke=0, fill=1)
        canvas.setFont(FONT, 8)
        canvas.setFillColor(palette.muted)
        canvas.drawRightString(width - 15 * mm, 10 * mm, f"стр. {doc.page}")
        canvas.drawString(15 * mm, 10 * mm, "Складской учёт — сводный отчёт")
        canvas.restoreState()

    return decorate


def render_report(report: SummaryReport, theme: str = "light") -> bytes:
    palette = palette_for(theme)
    s = _styles(palette)

    buffer = BytesIO()
    doc = SimpleDocTemplate(
        buffer,
        pagesize=landscape(A4),
        leftMargin=15 * mm,
        rightMargin=15 * mm,
        topMargin=14 * mm,
        bottomMargin=16 * mm,
        title=f"Отчёт — {report.storageName}",
        author="Складской учёт",
    )
    width = doc.width
    stats = report.storageStats
    story = []

    story.append(Paragraph(f"Отчёт по складу «{report.storageName}»", s.title))
    story.append(
        Paragraph(
            f"Период: {ru_date(report.dateFrom)} — {ru_date(report.dateTo)}"
            f"{NBSP}·{NBSP}сформирован {ru_datetime(report.generatedAt)}",
            s.subtitle,
        )
    )
    story.append(Spacer(1, 8 * mm))

    card_width = width / 3 - 4
    story.append(
        _cards_row(
            [
                _kpi_card(palette, s, "Закупки", money(stats.spending), "Сумма поступлений за период", card_width),
                _kpi_card(palette, s, "Выручка", money(stats.revenue), "Сумма продаж за период", card_width),
                _kpi_card(
                    palette, s, "Прибыль", money(stats.profit),
                    "Продажи − Закупки за период", card_width, palette.sign(stats.profit),
                ),
            ],
            width,
        )
    )
    story.append(Spacer(1, 3 * mm))
    story.append(_counters_strip(palette, s, stats, width))
    story.append(Spacer(1, 9 * mm))

    story.append(eyebrow(palette, s.section, "По товарам"))
    if report.productStats:
        header = [
            _head(s, "Товар"),
            _head(s, "Поступления", right=True),
            _head(s, "Продажи", right=True),
            _head(s, "Списания", right=True),
            _head(s, "Затраты", right=True),
            _head(s, "Выручка", right=True),
            _head(s, "Прибыль", right=True),
        ]
        rows = []
        ordered = sorted(report.productStats.items(), key=lambda kv: kv[1].productRevenue, reverse=True)
        for name, product in ordered:
            rows.append(
                [
                    Paragraph(name, s.cell),
                    _ops_cell(palette, s, product.admissionsCount, product.admissionsTotal),
                    _ops_cell(palette, s, product.sellsCount, product.sellsTotal),
                    _ops_cell(palette, s, product.writeOffsCount, product.writeOffsTotal),
                    Paragraph(money(product.productSpending), s.cell_right),
                    Paragraph(money(product.productRevenue), s.cell_right),
                    Paragraph(
                        f'<font color="{hex_of(palette.sign(product.productProfit))}">'
                        f"{money(product.productProfit)}</font>",
                        s.cell_right,
                    ),
                ]
            )
        shares = [0.22, 0.13, 0.13, 0.13, 0.13, 0.13, 0.13]
        story.append(_data_table(palette, header, rows, [width * share for share in shares], 1))
    else:
        story.append(Paragraph("За период операций не было.", s.empty))

    story.append(Spacer(1, 9 * mm))

    stock_block = [eyebrow(palette, s.section, "Остатки на момент формирования")]
    if report.currentStock:
        header = [
            _head(s, "Товар"),
            _head(s, "Остаток", right=True),
            _head(s, "Статус"),
            _head(s, "Цена за ед.", right=True),
            _head(s, "Стоимость остатка", right=True),
        ]
        rows = []
        for item in sorted(report.currentStock, key=lambda i: i.name):
            total = None if item.cost is None else item.cost * item.count
            rows.append(
                [
                    Paragraph(item.name, s.cell),
                    Paragraph(number(item.count), s.cell_right),
                    Paragraph(
                        f'<font color="{hex_of(palette.status(item.status))}">'
                        f"{STATUS_LABEL[item.status]}</font>",
                        s.cell,
                    ),
                    Paragraph(money(item.cost), s.cell_right),
                    Paragraph(money(total), s.cell_right),
                ]
            )
        shares = [0.34, 0.14, 0.18, 0.17, 0.17]
        stock_block.append(_data_table(palette, header, rows, [width * share for share in shares], 1))
    else:
        stock_block.append(Paragraph("На складе нет позиций.", s.empty))
    story.append(KeepTogether(stock_block))

    decorate = _page_decorator(palette)
    doc.build(story, onFirstPage=decorate, onLaterPages=decorate)
    return buffer.getvalue()
