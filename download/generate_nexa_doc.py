#!/usr/bin/env python3
"""Generate comprehensive NEXA PRO v5.0 Technical Documentation PDF."""

import os
from reportlab.lib.pagesizes import A4
from reportlab.lib.units import inch, mm
from reportlab.lib.colors import HexColor, white, black
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.lib.enums import TA_CENTER, TA_LEFT, TA_JUSTIFY, TA_RIGHT
from reportlab.platypus import (
    SimpleDocTemplate, Paragraph, Spacer, Table, TableStyle,
    PageBreak, KeepTogether, ListFlowable, ListItem, HRFlowable
)
from reportlab.platypus.doctemplate import PageTemplate, BaseDocTemplate, Frame
from reportlab.platypus.tableofcontents import TableOfContents
from reportlab.pdfgen import canvas
from reportlab.lib.fonts import addMapping
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont

# ─── Color Palette ───────────────────────────────────────────────────
PRIMARY      = HexColor('#4C6EF5')
PRIMARY_DARK = HexColor('#3B5BDB')
TEXT_COLOR    = HexColor('#1F2937')
TEXT_LIGHT    = HexColor('#4B5563')
TEXT_MUTED    = HexColor('#6B7280')
BG_WHITE      = HexColor('#FFFFFF')
TABLE_HEADER  = HexColor('#F5F7FA')
TABLE_ALT     = HexColor('#FAFBFC')
ACCENT_LINE   = HexColor('#E5E7EB')
SUCCESS_GREEN = HexColor('#10B981')
WARNING_AMBER = HexColor('#F59E0B')
NEW_BADGE     = HexColor('#8B5CF6')
COVER_BG      = HexColor('#0F172A')
COVER_SUBTLE  = HexColor('#1E293B')

OUTPUT_PATH = '/home/z/my-project/download/NEXA_PRO_Technical_Documentation.pdf'

# ─── Custom Doc Template with Page Numbers ───────────────────────────
class NexaDocTemplate(BaseDocTemplate):
    def __init__(self, filename, **kwargs):
        super().__init__(filename, **kwargs)
        self.page_width, self.page_height = A4
        self._section_number = 0

        frame = Frame(
            1 * inch, 1 * inch,
            self.page_width - 2 * inch,
            self.page_height - 2.1 * inch,
            id='normal'
        )

        self.addPageTemplates([
            PageTemplate(id='cover', frames=frame, onPage=self._cover_page),
            PageTemplate(id='normal', frames=frame, onPage=self._normal_page),
        ])

    def _cover_page(self, canvas_obj, doc):
        """Draw the cover page background and decorations."""
        canvas_obj.saveState()
        w, h = A4

        # Full page dark background
        canvas_obj.setFillColor(COVER_BG)
        canvas_obj.rect(0, 0, w, h, fill=1, stroke=0)

        # Subtle geometric accent shapes
        canvas_obj.setFillColor(HexColor('#1A2744'))
        canvas_obj.circle(w * 0.85, h * 0.75, 180, fill=1, stroke=0)
        canvas_obj.setFillColor(HexColor('#162036'))
        canvas_obj.circle(w * 0.15, h * 0.25, 140, fill=1, stroke=0)
        canvas_obj.setFillColor(HexColor('#1D2B47'))
        canvas_obj.circle(w * 0.90, h * 0.20, 90, fill=1, stroke=0)

        # Top accent line
        canvas_obj.setStrokeColor(PRIMARY)
        canvas_obj.setLineWidth(3)
        canvas_obj.line(1 * inch, h - 1.2 * inch, w - 1 * inch, h - 1.2 * inch)

        # Bottom accent line
        canvas_obj.setStrokeColor(PRIMARY)
        canvas_obj.setLineWidth(1)
        canvas_obj.line(1 * inch, 1.2 * inch, w - 1 * inch, 1.2 * inch)

        # Left decorative bar
        canvas_obj.setFillColor(PRIMARY)
        canvas_obj.rect(1 * inch, h * 0.32, 4, h * 0.36, fill=1, stroke=0)

        canvas_obj.restoreState()

    def _normal_page(self, canvas_obj, doc):
        """Draw headers, footers, and page numbers on normal pages."""
        canvas_obj.saveState()
        w, h = A4

        # Header line
        canvas_obj.setStrokeColor(ACCENT_LINE)
        canvas_obj.setLineWidth(0.5)
        canvas_obj.line(1 * inch, h - 0.85 * inch, w - 1 * inch, h - 0.85 * inch)

        # Header text
        canvas_obj.setFillColor(TEXT_MUTED)
        canvas_obj.setFont('Helvetica', 8)
        canvas_obj.drawString(1 * inch, h - 0.75 * inch, 'NEXA PRO v5.0 — Technical Documentation')
        canvas_obj.drawRightString(w - 1 * inch, h - 0.75 * inch, 'May 2026')

        # Footer line
        canvas_obj.setStrokeColor(ACCENT_LINE)
        canvas_obj.line(1 * inch, 0.85 * inch, w - 1 * inch, 0.85 * inch)

        # Page number
        canvas_obj.setFillColor(TEXT_MUTED)
        canvas_obj.setFont('Helvetica', 9)
        page_num = doc.page
        canvas_obj.drawCentredString(w / 2, 0.6 * inch, f'— {page_num} —')

        canvas_obj.restoreState()


# ─── Styles ──────────────────────────────────────────────────────────
def build_styles():
    styles = getSampleStyleSheet()

    styles.add(ParagraphStyle(
        name='CoverTitle',
        fontName='Helvetica-Bold',
        fontSize=36,
        leading=44,
        textColor=white,
        alignment=TA_LEFT,
        spaceAfter=6,
    ))
    styles.add(ParagraphStyle(
        name='CoverSubtitle',
        fontName='Helvetica',
        fontSize=16,
        leading=22,
        textColor=HexColor('#94A3B8'),
        alignment=TA_LEFT,
        spaceAfter=4,
    ))
    styles.add(ParagraphStyle(
        name='CoverMeta',
        fontName='Helvetica',
        fontSize=11,
        leading=16,
        textColor=HexColor('#64748B'),
        alignment=TA_LEFT,
        spaceAfter=2,
    ))
    styles.add(ParagraphStyle(
        name='SectionHeading',
        fontName='Helvetica-Bold',
        fontSize=20,
        leading=26,
        textColor=TEXT_COLOR,
        spaceBefore=20,
        spaceAfter=10,
    ))
    styles.add(ParagraphStyle(
        name='SubHeading',
        fontName='Helvetica-Bold',
        fontSize=13,
        leading=18,
        textColor=PRIMARY,
        spaceBefore=14,
        spaceAfter=6,
    ))
    styles.add(ParagraphStyle(
        name='SubHeading2',
        fontName='Helvetica-Bold',
        fontSize=11.5,
        leading=16,
        textColor=TEXT_COLOR,
        spaceBefore=10,
        spaceAfter=4,
    ))
    styles.add(ParagraphStyle(
        name='BodyText2',
        fontName='Helvetica',
        fontSize=10,
        leading=15,
        textColor=TEXT_COLOR,
        alignment=TA_JUSTIFY,
        spaceBefore=2,
        spaceAfter=6,
    ))
    styles.add(ParagraphStyle(
        name='BulletText',
        fontName='Helvetica',
        fontSize=10,
        leading=15,
        textColor=TEXT_COLOR,
        alignment=TA_LEFT,
        leftIndent=18,
        bulletIndent=6,
        spaceBefore=2,
        spaceAfter=3,
    ))
    styles.add(ParagraphStyle(
        name='TableCell',
        fontName='Helvetica',
        fontSize=9,
        leading=12,
        textColor=TEXT_COLOR,
    ))
    styles.add(ParagraphStyle(
        name='TableHeader',
        fontName='Helvetica-Bold',
        fontSize=9,
        leading=12,
        textColor=TEXT_COLOR,
    ))
    styles.add(ParagraphStyle(
        name='TOCEntry',
        fontName='Helvetica',
        fontSize=11,
        leading=20,
        textColor=TEXT_COLOR,
        leftIndent=10,
    ))
    styles.add(ParagraphStyle(
        name='TOCSection',
        fontName='Helvetica-Bold',
        fontSize=12,
        leading=22,
        textColor=TEXT_COLOR,
        spaceBefore=8,
    ))
    styles.add(ParagraphStyle(
        name='Caption',
        fontName='Helvetica-Oblique',
        fontSize=9,
        leading=13,
        textColor=TEXT_MUTED,
        alignment=TA_CENTER,
        spaceBefore=4,
        spaceAfter=10,
    ))
    styles.add(ParagraphStyle(
        name='BadgeNew',
        fontName='Helvetica-Bold',
        fontSize=8,
        leading=10,
        textColor=NEW_BADGE,
    ))
    return styles


# ─── Helper functions ────────────────────────────────────────────────
def section_heading(text, styles, number=None):
    if number is not None:
        full = f'<font color="{PRIMARY.hexval()}">{number}.</font>  {text}'
    else:
        full = text
    return Paragraph(full, styles['SectionHeading'])


def sub_heading(text, styles):
    return Paragraph(text, styles['SubHeading'])


def sub_heading2(text, styles):
    return Paragraph(text, styles['SubHeading2'])


def body(text, styles):
    return Paragraph(text, styles['BodyText2'])


def bullet(text, styles):
    return Paragraph(f'<bullet>&bull;</bullet> {text}', styles['BulletText'])


def spacer(h=8):
    return Spacer(1, h)


def hr():
    return HRFlowable(width='100%', thickness=0.5, color=ACCENT_LINE, spaceAfter=8, spaceBefore=4)


def make_table(headers, rows, col_widths=None):
    """Create a professionally styled table."""
    styles_dict = build_styles()

    header_cells = [Paragraph(h, styles_dict['TableHeader']) for h in headers]
    data = [header_cells]

    for row in rows:
        cells = []
        for i, cell in enumerate(row):
            cell_str = str(cell)
            # Style "NEW" badge in purple
            if cell_str.strip().upper() == 'NEW':
                cells.append(Paragraph(f'<font color="{NEW_BADGE.hexval()}"><b>NEW</b></font>', styles_dict['TableCell']))
            elif cell_str.strip().lower() == 'production':
                cells.append(Paragraph(f'<font color="{SUCCESS_GREEN.hexval()}"><b>Production</b></font>', styles_dict['TableCell']))
            else:
                cells.append(Paragraph(cell_str, styles_dict['TableCell']))
        data.append(cells)

    if col_widths is None:
        avail = A4[0] - 2 * inch
        col_widths = [avail / len(headers)] * len(headers)

    t = Table(data, colWidths=col_widths, repeatRows=1)
    style_cmds = [
        ('BACKGROUND', (0, 0), (-1, 0), TABLE_HEADER),
        ('TEXTCOLOR', (0, 0), (-1, 0), TEXT_COLOR),
        ('FONTNAME', (0, 0), (-1, 0), 'Helvetica-Bold'),
        ('FONTSIZE', (0, 0), (-1, 0), 9),
        ('BOTTOMPADDING', (0, 0), (-1, 0), 8),
        ('TOPPADDING', (0, 0), (-1, 0), 8),
        ('GRID', (0, 0), (-1, -1), 0.5, ACCENT_LINE),
        ('VALIGN', (0, 0), (-1, -1), 'TOP'),
        ('LEFTPADDING', (0, 0), (-1, -1), 6),
        ('RIGHTPADDING', (0, 0), (-1, -1), 6),
        ('TOPPADDING', (0, 1), (-1, -1), 5),
        ('BOTTOMPADDING', (0, 1), (-1, -1), 5),
    ]
    # Alternate row colors
    for i in range(1, len(data)):
        if i % 2 == 0:
            style_cmds.append(('BACKGROUND', (0, i), (-1, i), TABLE_ALT))
        else:
            style_cmds.append(('BACKGROUND', (0, i), (-1, i), BG_WHITE))

    t.setStyle(TableStyle(style_cmds))
    return t


# ─── Build the document ──────────────────────────────────────────────
def build_pdf():
    styles = build_styles()

    doc = NexaDocTemplate(
        OUTPUT_PATH,
        pagesize=A4,
        leftMargin=1 * inch,
        rightMargin=1 * inch,
        topMargin=1 * inch,
        bottomMargin=1 * inch,
        title='NEXA PRO v5.0 — Technical Documentation',
        author='AI Assistant Analysis',
        subject='Architecture and Module Analysis',
    )

    story = []
    avail_width = A4[0] - 2 * inch

    # ══════════════════════════════════════════════════════════════════
    # COVER PAGE
    # ══════════════════════════════════════════════════════════════════
    story.append(Spacer(1, 2.0 * inch))

    story.append(Paragraph(
        '<font color="#4C6EF5">NEXA PRO v5.0</font>',
        styles['CoverTitle']
    ))
    story.append(Spacer(1, 12))
    story.append(Paragraph(
        'Technical Documentation &amp; Architecture Analysis',
        styles['CoverSubtitle']
    ))
    story.append(Spacer(1, 8))

    # Accent line under subtitle
    cover_line_data = [['']]
    cover_line = Table(cover_line_data, colWidths=[3.5 * inch])
    cover_line.setStyle(TableStyle([
        ('LINEBELOW', (0, 0), (-1, -1), 2, PRIMARY),
        ('TOPPADDING', (0, 0), (-1, -1), 0),
        ('BOTTOMPADDING', (0, 0), (-1, -1), 0),
    ]))
    story.append(cover_line)
    story.append(Spacer(1, 30))

    story.append(Paragraph('Prepared by: AI Assistant Analysis', styles['CoverMeta']))
    story.append(Paragraph('Date: May 2026', styles['CoverMeta']))
    story.append(Paragraph('Version: 5.0  |  Commit: fb55ad1', styles['CoverMeta']))
    story.append(Paragraph('Repository: github.com/angelpipo1968/nexa-ai-android', styles['CoverMeta']))

    story.append(Spacer(1, 1.2 * inch))

    # Key stats in a mini grid on the cover
    stat_labels = ['Language', 'Architecture', 'Min SDK', 'Modules']
    stat_values = ['Kotlin 100%', 'MVVM + Compose', 'API 26+', '18 Components']
    stats_data = []
    for i in range(4):
        stats_data.append([
            Paragraph(f'<font color="#64748B" size="8">{stat_labels[i]}</font>', styles['CoverMeta']),
            Paragraph(f'<font color="#E2E8F0" size="9"><b>{stat_values[i]}</b></font>', styles['CoverMeta']),
        ])

    stats_table = Table(stats_data, colWidths=[1.6 * inch, 1.8 * inch])
    stats_table.setStyle(TableStyle([
        ('VALIGN', (0, 0), (-1, -1), 'TOP'),
        ('TOPPADDING', (0, 0), (-1, -1), 2),
        ('BOTTOMPADDING', (0, 0), (-1, -1), 2),
        ('LEFTPADDING', (0, 0), (0, -1), 0),
        ('LINEBELOW', (0, 0), (-1, -2), 0.25, HexColor('#334155')),
    ]))
    story.append(stats_table)

    # Switch to normal template for all following pages
    from reportlab.platypus import NextPageTemplate
    story.append(NextPageTemplate('normal'))
    story.append(PageBreak())

    # ══════════════════════════════════════════════════════════════════
    # TABLE OF CONTENTS
    # ══════════════════════════════════════════════════════════════════
    story.append(Paragraph('Table of Contents', styles['SectionHeading']))
    story.append(hr())
    story.append(spacer(6))

    toc_entries = [
        ('1', 'Executive Summary', '3'),
        ('2', 'Project Overview', '4'),
        ('3', 'Architecture Analysis', '5'),
        ('4', 'Module Inventory', '7'),
        ('5', 'AI &amp; NLP Capabilities', '9'),
        ('6', 'Voice System', '10'),
        ('7', 'IoT &amp; Sensor Integration', '12'),
        ('8', 'Recent Improvements', '13'),
        ('9', 'Recommendations &amp; Roadmap', '15'),
        ('10', 'Dependencies Summary', '17'),
        ('11', 'Security Considerations', '18'),
    ]

    for num, title, page in toc_entries:
        toc_line = f'<b><font color="{PRIMARY.hexval()}">{num}.</font></b>&nbsp;&nbsp;&nbsp;{title}'
        story.append(Paragraph(toc_line, styles['TOCEntry']))

    story.append(PageBreak())

    # ══════════════════════════════════════════════════════════════════
    # 1. EXECUTIVE SUMMARY
    # ══════════════════════════════════════════════════════════════════
    story.append(section_heading('Executive Summary', styles, '1'))
    story.append(hr())

    story.append(body(
        'NEXA PRO v5.0 is a feature-rich Android AI assistant application built entirely with Kotlin and the '
        'Jetpack Compose declarative UI framework. The project represents an ambitious effort to combine '
        'cloud-based large language model (LLM) capabilities with extensive on-device machine learning, '
        'voice interaction, IoT management, and contextual sensor fusion into a single, cohesive mobile '
        'experience. The application targets a broad range of Android devices running API level 26 and above, '
        'leveraging modern Android development practices including coroutines, StateFlow, and the Model-View-ViewModel '
        '(MVVM) architectural pattern.',
        styles
    ))
    story.append(body(
        'At its core, NEXA PRO integrates with two cloud LLM providers: Groq, which delivers high-performance '
        'inference using the Llama 3.3 70B model, and Pollinations AI, which offers a free, API-key-free '
        'alternative for cost-effective AI responses. Both providers communicate through Server-Sent Events (SSE) '
        'streaming, enabling real-time token-by-token response delivery that provides a natural conversational feel. '
        'This dual-provider approach ensures resilience—if one service becomes unavailable, the application can '
        'seamlessly fall back to the alternate provider, maintaining uninterrupted user interaction.',
        styles
    ))
    story.append(body(
        'Beyond cloud AI capabilities, NEXA PRO incorporates a substantial on-device ML layer powered by '
        'Google ML Kit and TensorFlow Lite. This layer handles intent classification across 15 categories, '
        'sentiment analysis with nuanced emotion detection, multilingual language identification, smart reply '
        'generation, and entity extraction. The application also features a sophisticated voice system with '
        'wake-word detection ("Hey NEXA" / "Oye NEXA"), speech-to-text with partial results, text-to-speech '
        'with six configurable voice types, and a hands-free continuous conversation mode with barge-in support.',
        styles
    ))
    story.append(body(
        'Recent development activity has significantly expanded the project\'s capabilities. The latest commit '
        '(fb55ad1) introduced 1,716 lines of new code across five entirely new modules: WebSearchManager for '
        'DuckDuckGo integration with HTML scraping and fact-checking, EpisodicMemoryManager for cross-session '
        'memory with consent management, EnhancedEmotionAnalyzer supporting 20 distinct emotion types with '
        'Voice Activity Detection scoring, UserProfileManager for deep user profiling and personalization, '
        'and WebResultProcessor for intelligent search result summarization. This documentation provides a '
        'comprehensive analysis of the project\'s architecture, module inventory, capabilities, and strategic '
        'recommendations for future development.',
        styles
    ))
    story.append(PageBreak())

    # ══════════════════════════════════════════════════════════════════
    # 2. PROJECT OVERVIEW
    # ══════════════════════════════════════════════════════════════════
    story.append(section_heading('Project Overview', styles, '2'))
    story.append(hr())

    story.append(sub_heading('Application Identity', styles))
    story.append(body(
        'NEXA PRO is the fifth major iteration of an AI assistant platform designed specifically for the Android '
        'ecosystem. The application serves as a general-purpose intelligent assistant capable of natural language '
        'conversation, voice interaction, web search, IoT device management, environmental sensing, and personalized '
        'user profiling. Unlike many AI chat applications that rely solely on cloud services, NEXA PRO maintains a '
        'rich on-device processing layer that enables offline-capable features such as intent classification, '
        'sentiment analysis, and wake-word detection without requiring network connectivity.',
        styles
    ))

    # Key facts table
    key_facts = [
        ['Application', 'NEXA PRO v5.0'],
        ['Platform', 'Android (API 26+ / Android 8.0+)'],
        ['Target SDK', 'API 35 (Android 15)'],
        ['Language', '100% Kotlin with Jetpack Compose'],
        ['Architecture', 'MVVM with StateFlow (unidirectional data flow)'],
        ['Build System', 'Gradle 8.12 + KSP 2.0.21 + Java 17'],
        ['Repository', 'github.com/angelpipo1968/nexa-ai-android'],
        ['Latest Commit', 'fb55ad1 — Full English translation + 5 new modules'],
        ['Lines Added', '1,716 lines (latest commit)'],
    ]
    story.append(spacer(6))
    story.append(make_table(
        ['Property', 'Value'],
        key_facts,
        col_widths=[avail_width * 0.30, avail_width * 0.70]
    ))
    story.append(Paragraph('<i>Table 1: Project key facts and configuration</i>', styles['Caption']))

    story.append(sub_heading('Multi-Module Structure', styles))
    story.append(body(
        'The project is organized as a multi-module repository with three primary components. The native Android '
        'application resides in the <font face="Courier" size="9">app/</font> directory and contains all Kotlin source code, '
        'Compose UI layouts, and on-device ML models. The <font face="Courier" size="9">android/</font> directory houses '
        'a Capacitor-based wrapper that enables the web application to run within a native Android shell, providing '
        'access to native device APIs through a bridge layer. The <font face="Courier" size="9">src/</font> directory '
        'contains a Next.js web backend that serves as the foundation for the Capacitor wrapper, implementing shared '
        'UI components and business logic in TypeScript and React.',
        styles
    ))
    story.append(body(
        'This hybrid approach offers significant flexibility: core AI and ML functionality runs natively on the device '
        'for maximum performance and offline capability, while the web layer enables rapid cross-platform iteration '
        'and easier deployment of UI updates without requiring full application releases through the Google Play Store. '
        'The native module contains the most sophisticated components including the NexaViewModel orchestrator, '
        'real-time streaming network layer, Room database for persistent storage, and all sensor/IoT management '
        'subsystems. The project currently targets Android 8.0 (API 26) as its minimum platform, ensuring broad '
        'device compatibility while leveraging modern Android APIs up to version 15 (API 35) for newer features.',
        styles
    ))
    story.append(body(
        'The build configuration uses Kotlin Symbol Processing (KSP) version 2.0.21 for annotation processing, '
        'which provides significantly faster compilation times compared to the legacy kapt processor. Java 17 '
        'serves as the compilation and runtime JDK, aligned with current Gradle and Android Gradle Plugin requirements. '
        'Version catalog dependencies are managed centrally, though the project currently does not employ a formal '
        'dependency injection framework—components are instantiated manually through constructor parameters and '
        'application-level singletons.',
        styles
    ))
    story.append(PageBreak())

    # ══════════════════════════════════════════════════════════════════
    # 3. ARCHITECTURE ANALYSIS
    # ══════════════════════════════════════════════════════════════════
    story.append(section_heading('Architecture Analysis', styles, '3'))
    story.append(hr())

    story.append(sub_heading('MVVM Pattern with Centralized ViewModel', styles))
    story.append(body(
        'NEXA PRO follows the Model-View-ViewModel (MVVM) architectural pattern, which is the recommended approach '
        'for Android applications using Jetpack Compose. In this pattern, the View layer is composed entirely of '
        'Jetpack Compose composables that observe state emitted by the ViewModel, the Model layer encapsulates data '
        'sources and business logic through repository classes, and the ViewModel serves as the intermediary that '
        'transforms model data into UI-compatible state objects. The application employs a single, centralized '
        'NexaViewModel that acts as the primary orchestrator for all application logic.',
        styles
    ))
    story.append(body(
        'The NexaViewModel exposes application state through a <font face="Courier" size="9">StateFlow&lt;NexaUiState&gt;</font> '
        'property, enabling the Compose UI to reactively observe and re-render when state changes occur. This implements '
        'a unidirectional data flow architecture where user actions in the View dispatch intents to the ViewModel, '
        'the ViewModel processes those intents by interacting with repositories and services, and the resulting state '
        'changes flow back to the View through the StateFlow. This pattern eliminates the common issues of bidirectional '
        'data binding and makes state transitions predictable and testable.',
        styles
    ))

    story.append(sub_heading('NexaUiState — Single Source of Truth', styles))
    story.append(body(
        'The NexaUiState data class serves as the single source of truth for the entire application\'s UI state. '
        'It encapsulates all display-relevant data including the current list of chat messages, loading indicators, '
        'error states, voice interaction status, IoT device listings, sensor readings, user profile information, '
        'and memory items. By consolidating all UI state into a single immutable data class, the application ensures '
        'consistency: each render cycle receives a complete snapshot of the application state, preventing partial or '
        'inconsistent UI updates that can occur with fragmented state management approaches.',
        styles
    ))
    story.append(body(
        'State transitions within NexaViewModel are performed using <font face="Courier" size="9">MutableStateFlow</font> '
        'with Kotlin coroutines, ensuring thread safety and compliance with Android\'s main-safety requirements. All '
        'long-running operations—including network calls, ML inference, sensor polling, and database operations—execute '
        'on background dispatchers (typically <font face="Courier" size="9">Dispatchers.IO</font>), with results '
        'collected and merged into the UI state on the main dispatcher. This approach maintains a responsive UI even '
        'during computationally intensive operations such as real-time speech recognition or streaming AI responses.',
        styles
    ))

    story.append(sub_heading('Repository Pattern and Data Layer', styles))
    story.append(body(
        'The data layer follows the Repository pattern, with NexaRepository serving as the primary abstraction over '
        'network operations. The repository encapsulates all communication with external AI providers (Groq and Pollinations AI) '
        'through OkHttp-based SSE streaming connections. Network responses are received as a stream of tokens that are '
        'accumulated and emitted to the ViewModel for real-time display. This abstraction cleanly separates the networking '
        'implementation from the business logic, allowing the AI provider implementation to be swapped or extended without '
        'modifying the ViewModel.',
        styles
    ))
    story.append(body(
        'Local persistence is handled through Room Database, which provides a structured SQLite abstraction for storing '
        'chat sessions, individual messages, and user preferences. The database schema supports conversation history '
        'retrieval, message search, and session management. User preferences that don\'t require relational queries—such as '
        'theme selection, voice configuration, and API keys—are stored in DataStore Preferences, which offers a coroutine-friendly '
        'alternative to SharedPreferences with type-safe key-value storage. The LocationStore provides fused location '
        'updates combining GPS, cellular, and WiFi positioning with geocoding capabilities, while the UserStore manages '
        'local authentication through password hashing without requiring server-side credentials.',
        styles
    ))

    story.append(sub_heading('Architectural Observations', styles))
    story.append(body(
        'One notable aspect of the current architecture is the absence of a formal dependency injection framework. '
        'All components are instantiated manually through constructor parameters, with the Application class serving '
        'as a de facto service locator. While this approach reduces framework overhead and simplifies the build '
        'configuration, it introduces tight coupling between components and makes unit testing more challenging, as '
        'mocking dependencies requires careful constructor management. The centralized NexaViewModel, while providing '
        'a simple mental model for state management, has grown to over 2,000 lines of code, suggesting that a '
        'decomposition into specialized sub-ViewModels or use-case interactors would improve maintainability.',
        styles
    ))
    story.append(PageBreak())

    # ══════════════════════════════════════════════════════════════════
    # 4. MODULE INVENTORY
    # ══════════════════════════════════════════════════════════════════
    story.append(section_heading('Module Inventory', styles, '4'))
    story.append(hr())

    story.append(body(
        'The following table provides a comprehensive inventory of all modules within the NEXA PRO native Android '
        'application. Each module is categorized by its functional domain, primary file location, purpose, and current '
        'development status. Modules marked as <b><font color="#8B5CF6">NEW</font></b> were introduced in the latest '
        'commit (fb55ad1) and represent recently added capabilities that may still require integration work and '
        'production hardening.',
        styles
    ))
    story.append(spacer(8))

    modules = [
        ['NexaViewModel', 'viewmodel/', 'Central orchestrator managing all application state, user intents, and inter-module coordination', 'Production'],
        ['NexaRepository', 'data/', 'Network layer handling SSE streaming connections to Groq and Pollinations AI providers', 'Production'],
        ['SpeechManager', 'voice/', 'TTS + STT engine with audio focus management, Bluetooth SCO support, and 6 voice types', 'Production'],
        ['VoiceEnhancer', 'voice/', 'Wake word detection ("Hey NEXA"), emotion analysis, and real-time language detection', 'Production'],
        ['NaturalConversationEngine', 'voice/', 'Turn-taking logic, backchanneling, topic tracking, and continuous conversation flow', 'Production'],
        ['OnDeviceMLEngine', 'ml/', 'Intent classification (15 categories), sentiment analysis, and preference learning', 'Production'],
        ['EnhancedEmotionAnalyzer', 'ml/', 'Advanced emotion detection with 20 types, VAD scoring, and bilingual lexicon', 'NEW'],
        ['UserProfileManager', 'ml/', 'Deep user profiling, vocabulary analysis, interaction style detection, and personalization', 'NEW'],
        ['EpisodicMemoryManager', 'memory/', 'Cross-session memory with consent management, relevance scoring, and auto-summarization', 'NEW'],
        ['IoTManager', 'iot/', 'BLE scanning, WiFi Direct, device rooms, scenes, automation routines, and energy monitoring', 'Production'],
        ['SensorManager', 'sensors/', '12 sensor types, GPS positioning, driving detection, sleep detection, and NFC integration', 'Production'],
        ['VideoGenerator', 'media/', 'Multi-provider video generation with progress tracking and result handling', 'Production'],
        ['WebSearchManager', 'web/', 'DuckDuckGo API integration, HTML scraping, fact-checking, and response caching', 'NEW'],
        ['WebResultProcessor', 'web/', 'Search result summarization, chat formatting, and voice-optimized output generation', 'NEW'],
        ['NexaDatabase', 'data/', 'Room database providing persistent storage for sessions, messages, and structured data', 'Production'],
        ['SettingsStore', 'data/', 'DataStore Preferences wrapper for theme, voice, language, and API key configuration', 'Production'],
        ['LocationStore', 'data/', 'FusedLocationProvider integration with geocoding, reverse geocoding, and location caching', 'Production'],
        ['UserStore', 'data/', 'Local authentication system with password hashing and credential persistence', 'Production'],
    ]

    story.append(make_table(
        ['Module', 'Location', 'Purpose', 'Status'],
        modules,
        col_widths=[avail_width * 0.18, avail_width * 0.10, avail_width * 0.58, avail_width * 0.14]
    ))
    story.append(Paragraph('<i>Table 2: Complete module inventory with status indicators</i>', styles['Caption']))

    story.append(spacer(6))
    story.append(sub_heading('Module Distribution', styles))
    story.append(body(
        'The 18 modules are distributed across seven functional packages: <b>data/</b> contains five modules handling '
        'persistence, networking, and configuration; <b>voice/</b> includes three modules dedicated to speech processing '
        'and natural conversation; <b>ml/</b> houses three machine learning modules for on-device intelligence; '
        '<b>memory/</b> provides the new episodic memory system; <b>iot/</b> and <b>sensors/</b> each contain a single '
        'manager for their respective domains; and <b>web/</b> includes the two new web search modules. The viewmodel '
        'package contains the central NexaViewModel which coordinates all other modules. This distribution reflects the '
        'application\'s emphasis on voice interaction and on-device intelligence, which together account for six of the '
        'eighteen modules.',
        styles
    ))
    story.append(body(
        'Of the 18 modules, five are newly introduced in the latest commit and carry a "NEW" status. These modules '
        'require integration into the main NexaViewModel orchestration flow—in particular, WebSearchManager and '
        'WebResultProcessor are not yet wired into the ViewModel\'s intent processing pipeline, meaning their '
        'functionality is implemented but not currently accessible through the user interface. The remaining 13 '
        'modules are marked as "Production" status, indicating they are actively used in the application\'s main '
        'flow and have been exercised through development and testing cycles.',
        styles
    ))
    story.append(PageBreak())

    # ══════════════════════════════════════════════════════════════════
    # 5. AI & NLP CAPABILITIES
    # ══════════════════════════════════════════════════════════════════
    story.append(section_heading('AI &amp; NLP Capabilities', styles, '5'))
    story.append(hr())

    story.append(sub_heading('Cloud AI Providers', styles))
    story.append(body(
        'NEXA PRO leverages a dual-provider cloud AI strategy to ensure both quality and availability of AI responses. '
        'The primary provider is Groq, which delivers inference on Meta\'s Llama 3.3 70B model with exceptionally low '
        'latency through its custom LPU (Language Processing Unit) hardware. Groq provides state-of-the-art response '
        'quality with typical first-token latencies under 200 milliseconds, making it ideal for conversational AI '
        'applications where responsiveness directly impacts user experience. The secondary provider, Pollinations AI, '
        'offers a cost-free alternative that requires no API key for access, serving as both a fallback mechanism and '
        'an option for users who prefer not to configure API credentials.',
        styles
    ))
    story.append(body(
        'Communication with both providers uses Server-Sent Events (SSE) over HTTPS, implemented through OkHttp\'s '
        'event source listener. This streaming approach delivers AI responses token-by-token, enabling the UI to '
        'display partial responses in real-time as they are generated. The NexaRepository manages the SSE connection '
        'lifecycle, handling connection establishment, token accumulation, error recovery, and graceful disconnection. '
        'When the Groq service is unavailable or returns an error, the repository automatically falls back to '
        'Pollinations AI, ensuring that the user always receives a response regardless of individual provider status.',
        styles
    ))

    story.append(sub_heading('On-Device Machine Learning', styles))
    story.append(body(
        'The on-device ML layer, primarily implemented in OnDeviceMLEngine and EnhancedEmotionAnalyzer, provides '
        'a comprehensive suite of local intelligence capabilities. Google ML Kit serves as the foundation for several '
        'core features: Language Identification automatically detects the user\'s language from spoken or typed input, '
        'supporting over 50 languages with real-time classification; Entity Extraction identifies structured data such '
        'as dates, addresses, phone numbers, and tracking numbers within natural text; Smart Reply generates contextual '
        'response suggestions based on conversation history; and on-device Translation provides multilingual text '
        'conversion without requiring network connectivity.',
        styles
    ))
    story.append(body(
        'Beyond ML Kit integrations, the application implements custom NLP pipelines in pure Kotlin. The intent '
        'classification system categorizes user input into 15 distinct intent categories including greeting, '
        'farewell, question, command, emotional expression, and domain-specific requests. Sentiment analysis '
        'provides continuous valence scoring across the conversation, tracking emotional trends and triggering '
        'appropriate response strategies. Topic tracking monitors 12 conversation topic categories, enabling '
        'contextual awareness across multi-turn dialogues. The newly introduced EnhancedEmotionAnalyzer extends '
        'this capability with 20 granular emotion types, Voice Activity Detection (VAD) scoring for speech segments, '
        'and a bilingual lexicon supporting both English and Spanish emotion vocabulary.',
        styles
    ))

    story.append(sub_heading('New AI Capabilities', styles))
    story.append(body(
        'The latest development cycle introduced four significant enhancements to the AI and NLP stack. '
        'WebSearchManager brings web search capability through DuckDuckGo\'s API, supplemented by HTML content '
        'scraping via Jsoup for extracting information from retrieved pages. The module includes a fact-checking '
        'subsystem that cross-references AI-generated claims against web sources, and a response cache to minimize '
        'redundant network requests. WebResultProcessor transforms raw search results into conversational summaries, '
        'optimizing them for both chat display and voice output through natural language generation techniques.',
        styles
    ))
    story.append(body(
        'EpisodicMemoryManager introduces a cross-session memory system that allows NEXA PRO to retain and recall '
        'information from previous conversations. The module implements relevance scoring to prioritize the most '
        'salient memories, automatic summarization to compress lengthy interaction histories, and a consent management '
        'system that gives users full control over what information is retained. UserProfileManager complements this '
        'with deep user profiling capabilities, analyzing vocabulary choices, interaction patterns, topic preferences, '
        'and communication style to deliver increasingly personalized responses over time.',
        styles
    ))
    story.append(PageBreak())

    # ══════════════════════════════════════════════════════════════════
    # 6. VOICE SYSTEM
    # ══════════════════════════════════════════════════════════════════
    story.append(section_heading('Voice System', styles, '6'))
    story.append(hr())

    story.append(sub_heading('Speech Recognition and Synthesis', styles))
    story.append(body(
        'The voice system is one of the most sophisticated subsystems within NEXA PRO, implemented across three '
        'dedicated modules: SpeechManager, VoiceEnhancer, and NaturalConversationEngine. Speech recognition leverages '
        'Android\'s built-in SpeechRecognizer API, configured for continuous listening with partial result delivery. '
        'This enables real-time display of recognized text as the user speaks, providing immediate visual feedback that '
        'confirms the system is actively listening and processing input. The recognizer supports both English and '
        'Spanish, with automatic language detection handled by VoiceEnhancer\'s language identification module.',
        styles
    ))
    story.append(body(
        'Text-to-speech synthesis uses Android\'s TTS engine with six configurable voice types: three male and three '
        'female voices. Users can adjust speech rate, pitch, and voice selection through the settings interface. The '
        'SpeechManager implements comprehensive audio focus management, ensuring that NEXA PRO\'s speech output properly '
        'ducks or pauses other audio streams (such as music playback) and restores them when the TTS output completes. '
        'Bluetooth SCO (Synchronous Connection-Oriented) mode is supported for hands-free calling scenarios, routing '
        'both recognition and synthesis audio through Bluetooth headsets when available.',
        styles
    ))

    story.append(sub_heading('Wake Word Detection', styles))
    story.append(body(
        'The wake word system enables hands-free activation through the phrases "Hey NEXA" (English) and "Oye NEXA" '
        '(Spanish). Unlike cloud-based wake word services, NEXA PRO\'s implementation performs detection entirely '
        'on-device using audio spectral analysis. The VoiceEnhancer module continuously monitors the microphone input '
        'through a dedicated audio recording session, applying Fast Fourier Transform (FFT) analysis to the incoming '
        'audio stream and comparing spectral features against pre-computed wake word templates. When a match is detected '
        'with sufficient confidence, the system transitions from passive monitoring to active listening mode, triggering '
        'the SpeechRecognizer to begin full intent recognition.',
        styles
    ))
    story.append(body(
        'The wake word detection system is designed for minimal battery impact, using low-power audio recording with '
        'periodic buffering rather than continuous high-fidelity capture. The proximity sensor is integrated to '
        'automatically disable wake word detection when the device is in a pocket or face-down, preventing false '
        'activations and conserving battery life. Audio visualization provides real-time feedback of the microphone '
        'input level, allowing users to visually confirm that the system is monitoring for the wake word.',
        styles
    ))

    story.append(sub_heading('Hands-Free Conversation Mode', styles))
    story.append(body(
        'The NaturalConversationEngine implements a sophisticated hands-free mode that enables continuous voice '
        'interaction without requiring screen touch. The engine manages a conversation loop that alternates between '
        'listening and speaking phases, with automatic turn-taking based on speech activity detection. Backchanneling '
        'capabilities—such as brief affirmations ("I see," "Go on")—are generated during listening phases to maintain '
        'conversational engagement. The barge-in feature allows users to interrupt the TTS output by speaking during '
        'synthesis, immediately switching the system back to listening mode for a natural conversational flow.',
        styles
    ))

    story.append(sub_heading('Voice Commands', styles))
    story.append(body(
        'Beyond natural language conversation, NEXA PRO supports over 20 explicit voice commands that enable users '
        'to control the application through speech alone. These commands cover essential operations such as clearing '
        'the chat history, creating a new conversation, exporting the conversation as a PDF document, switching '
        'between English and Spanish languages, changing the active voice type, toggling between light and dark '
        'themes, activating and deactivating hands-free mode, adjusting speech rate, and controlling IoT devices. '
        'Command recognition uses a combination of keyword matching and intent classification, providing robust '
        'detection even with variations in phrasing and accent.',
        styles
    ))
    story.append(PageBreak())

    # ══════════════════════════════════════════════════════════════════
    # 7. IoT & SENSOR INTEGRATION
    # ══════════════════════════════════════════════════════════════════
    story.append(section_heading('IoT &amp; Sensor Integration', styles, '7'))
    story.append(hr())

    story.append(sub_heading('IoT Device Management', styles))
    story.append(body(
        'The IoTManager module provides comprehensive device management capabilities through both BLE (Bluetooth Low '
        'Energy) and WiFi Direct communication protocols. BLE scanning discovers nearby smart devices and peripherals, '
        'while WiFi Direct enables high-bandwidth peer-to-peer connections for devices that require greater throughput. '
        'Discovered devices can be organized into logical rooms (e.g., "Living Room," "Bedroom," "Kitchen"), enabling '
        'spatial grouping that simplifies multi-device control and automation. Scenes allow users to define and execute '
        'device state configurations across multiple devices simultaneously—a single "Movie Night" scene, for example, '
        'could dim lights, close blinds, and activate a smart TV with one command.',
        styles
    ))
    story.append(body(
        'Automation routines provide time-based and event-based triggers for device control. Users can create schedules '
        'that automatically adjust device states at specific times or days, and define conditional rules that respond to '
        'sensor readings or other events. Energy monitoring tracks power consumption across connected devices, providing '
        'usage reports and efficiency recommendations. The IoT system integrates with the voice command framework, '
        'allowing users to control devices through natural language such as "Turn off the living room lights" or '
        '"Set the thermostat to 72 degrees."',
        styles
    ))

    story.append(sub_heading('Sensor Fusion System', styles))
    story.append(body(
        'The SensorManager module aggregates data from 12 distinct sensor types available on Android devices, '
        'transforming raw hardware readings into contextual information that enhances the AI assistant\'s awareness '
        'and responsiveness. Supported sensors include the accelerometer, gyroscope, magnetometer, ambient light '
        'sensor, proximity sensor, barometric pressure sensor, step counter, heart rate monitor (where available), '
        'and GPS receiver. The module implements specialized detection algorithms for two high-value use cases: '
        'driving detection uses accelerometer and GPS data patterns to determine when the user is operating a vehicle, '
        'enabling automatic mode switches to hands-free interaction and suppressing non-essential notifications; sleep '
        'detection uses movement patterns and ambient light levels to identify sleep onset, allowing the application '
        'to reduce notification frequency and prepare morning briefings.',
        styles
    ))
    story.append(body(
        'NFC integration enables the application to interact with NFC tags for quick actions and device pairing. '
        'The contextual suggestion engine combines sensor data with user behavior patterns to generate proactive '
        'suggestions across eight categories including time-based actions ("Good morning—here\'s your daily brief"), '
        'location-based recommendations, activity-appropriate responses, weather-aware suggestions, battery '
        'conservation alerts, and IoT automation triggers. This sensor fusion approach transforms the smartphone '
        'from a passive communication tool into an active contextual intelligence platform.',
        styles
    ))
    story.append(PageBreak())

    # ══════════════════════════════════════════════════════════════════
    # 8. RECENT IMPROVEMENTS
    # ══════════════════════════════════════════════════════════════════
    story.append(section_heading('Recent Improvements', styles, '8'))
    story.append(hr())

    story.append(sub_heading('Commit fb55ad1 — Full English Translation &amp; New Modules', styles))
    story.append(body(
        'The most recent commit (fb55ad1) represents the largest single development effort documented in the '
        'project\'s recent history, encompassing a complete English translation of the codebase alongside the '
        'introduction of five entirely new modules. The translation effort spanned 12 source files, converting '
        'all user-facing strings, code comments, documentation, and internal identifiers from Spanish to English. '
        'This国际化 (internationalization) effort improves the project\'s accessibility to the global developer '
        'community and establishes English as the primary language for all future development. The commit added '
        '1,716 lines of new Kotlin code, demonstrating significant feature expansion beyond the translation work.',
        styles
    ))

    story.append(sub_heading('New Module Details', styles))

    story.append(sub_heading2('WebSearchManager — Web Search &amp; Fact-Checking', styles))
    story.append(body(
        'The WebSearchManager module introduces DuckDuckGo search integration through its Instant Answer API, '
        'providing NEXA PRO with the ability to retrieve current information from the web. The module implements '
        'a multi-layered search strategy: initial queries go through the DuckDuckGo API for quick answers, and '
        'when more detailed information is needed, HTML content scraping via Jsoup extracts structured data from '
        'retrieved web pages. A fact-checking subsystem cross-references AI-generated statements against web sources, '
        'providing confidence scores and source citations for factual claims. Response caching reduces latency for '
        'frequently asked questions and minimizes unnecessary network requests, with configurable cache TTL and '
        'storage limits.',
        styles
    ))

    story.append(sub_heading2('WebResultProcessor — Intelligent Result Formatting', styles))
    story.append(body(
        'Complementing the WebSearchManager, the WebResultProcessor transforms raw search results into formats '
        'suitable for different output channels. For chat display, it generates concise summaries with key '
        'highlights and source attribution. For voice output, it produces natural language summaries optimized '
        'for TTS delivery, avoiding complex formatting or URL references that don\'t translate well to spoken '
        'output. The processor implements content deduplication, relevance ranking, and length adaptation based '
        'on the target output channel and user preferences.',
        styles
    ))

    story.append(sub_heading2('EpisodicMemoryManager — Cross-Session Memory', styles))
    story.append(body(
        'The EpisodicMemoryManager implements a biologically-inspired memory system that allows NEXA PRO to '
        'retain and recall information from previous conversation sessions. Unlike simple chat history storage, '
        'this module applies relevance scoring to prioritize the most significant memories, automatic summarization '
        'to compress lengthy interactions into compact semantic representations, and temporal decay functions that '
        'gradually reduce the influence of older memories unless they are reinforced through recall. A consent '
        'management system gives users granular control over memory retention, with options to selectively delete '
        'individual memories, clear all memories from specific time periods, or disable memory functionality entirely.',
        styles
    ))

    story.append(sub_heading2('EnhancedEmotionAnalyzer — Advanced Emotion Detection', styles))
    story.append(body(
        'The EnhancedEmotionAnalyzer extends the existing sentiment analysis capabilities with fine-grained emotion '
        'detection across 20 distinct emotion categories including joy, sadness, anger, surprise, fear, disgust, '
        'trust, anticipation, confusion, frustration, excitement, contentment, anxiety, gratitude, disappointment, '
        'pride, embarrassment, relief, boredom, and neutrality. Voice Activity Detection (VAD) scoring provides '
        'per-segment emotion analysis for speech input, enabling detection of emotional transitions within a single '
        'utterance. A bilingual lexicon supports emotion detection in both English and Spanish, matching the '
        'application\'s bilingual voice recognition capabilities.',
        styles
    ))

    story.append(sub_heading2('UserProfileManager — Deep User Profiling', styles))
    story.append(body(
        'The UserProfileManager builds and maintains a comprehensive user profile through ongoing analysis of '
        'interaction patterns. The module tracks vocabulary complexity and preferred terminology, topic interests '
        'and expertise levels, communication style (formal vs. casual, verbose vs. concise), interaction frequency '
        'and preferred times, and response satisfaction indicators. This profile data is used to personalize AI '
        'responses—adjusting language complexity to match the user\'s level, prioritizing topics of interest, and '
        'modifying response style to align with user preferences. All profiling is performed on-device, with no '
        'user data transmitted to external services.',
        styles
    ))
    story.append(PageBreak())

    # ══════════════════════════════════════════════════════════════════
    # 9. RECOMMENDATIONS & ROADMAP
    # ══════════════════════════════════════════════════════════════════
    story.append(section_heading('Recommendations &amp; Roadmap', styles, '9'))
    story.append(hr())

    story.append(body(
        'Based on the comprehensive analysis of NEXA PRO\'s architecture, module inventory, and recent development '
        'activity, the following recommendations are organized by priority level. High-priority items address '
        'structural risks that could impede future development velocity or production reliability. Medium-priority '
        'items represent important improvements that enhance functionality and user experience. Low-priority items '
        'are aspirational features that would differentiate the application in the market but are not critical '
        'for current stability or maintainability.',
        styles
    ))

    story.append(sub_heading('High Priority', styles))

    story.append(sub_heading2('1. Dependency Injection Framework', styles))
    story.append(body(
        'The current manual instantiation pattern introduces tight coupling between components, making it difficult '
        'to swap implementations for testing or to manage complex dependency graphs as the codebase grows. '
        'Introducing Hilt (Google\'s recommended DI framework for Android) or Koin (a lightweight Kotlin-native '
        'alternative) would provide automatic dependency resolution, scoped instances (application, activity, '
        'ViewModel), and straightforward mocking for unit tests. Given the project\'s use of Jetpack components, '
        'Hilt is the recommended choice for seamless integration with ViewModel, WorkManager, and Compose navigation.',
        styles
    ))

    story.append(sub_heading2('2. ViewModel Decomposition', styles))
    story.append(body(
        'The NexaViewModel has grown to over 2,000 lines of code, handling responsibilities that span chat management, '
        'voice control, IoT operations, sensor processing, web search, memory management, and user profiling. This '
        'violates the Single Responsibility Principle and makes the ViewModel difficult to test, maintain, and extend. '
        'A recommended refactoring approach would introduce use-case or interactor classes that encapsulate specific '
        'business logic (e.g., ChatUseCase, VoiceUseCase, IoTUseCase), with the ViewModel serving as a thin '
        'coordinator that delegates to these interactors and merges their state outputs into the unified NexaUiState.',
        styles
    ))

    story.append(sub_heading2('3. Wire New Modules into ViewModel', styles))
    story.append(body(
        'The five newly created modules (WebSearchManager, WebResultProcessor, EpisodicMemoryManager, '
        'EnhancedEmotionAnalyzer, UserProfileManager) are implemented but not yet integrated into the NexaViewModel\'s '
        'intent processing pipeline. Without this integration, the significant development effort invested in these '
        'modules delivers no user-facing value. A phased integration approach is recommended, starting with '
        'WebSearchManager (highest user value) and EnhancedEmotionAnalyzer (lowest integration complexity), followed '
        'by EpisodicMemoryManager and UserProfileManager, which require more extensive state management changes.',
        styles
    ))

    story.append(sub_heading2('4. Unit Testing Infrastructure', styles))
    story.append(body(
        'The project currently includes test dependencies (JUnit, Mockito) in the build configuration but contains '
        'no actual test code. Establishing a testing foundation is critical for maintaining code quality as the '
        'project scales. Priority areas for initial test coverage include the intent classification logic in '
        'OnDeviceMLEngine, the sentiment analysis pipeline, the NexaRepository SSE handling, and the NexaViewModel '
        'state transitions. A minimum target of 60% code coverage on the ML and data layers would provide '
        'meaningful regression protection without requiring disproportionate effort.',
        styles
    ))

    story.append(sub_heading2('5. CI/CD Pipeline', styles))
    story.append(body(
        'The absence of automated build, test, and deployment pipelines increases the risk of integration failures '
        'and slows the release cycle. A GitHub Actions workflow should be established to perform lint checks, '
        'compile the project, run unit tests, and generate debug APKs on every pull request. Release builds should '
        'include ProGuard/R8 optimization, APK signing, and automatic upload to a distribution channel (Google Play '
        'Internal Testing, Firebase App Distribution, or GitHub Releases).',
        styles
    ))

    story.append(sub_heading('Medium Priority', styles))

    med_items = [
        ('6. ProGuard Rules for Jsoup', 'The newly added Jsoup dependency for web scraping requires proper ProGuard/R8 rules to prevent class stripping during release builds. Without these rules, the WebSearchManager will fail with ClassNotFoundException in production builds.'),
        ('7. Offline-First Architecture', 'While on-device ML provides some offline capability, the core AI conversation feature requires network connectivity. Implementing a local LLM through TensorFlow Lite or ML Kit\'s on-device text generation would enable offline conversation with reduced response quality.'),
        ('8. End-to-End Encryption', 'Chat history stored in Room Database is currently unencrypted. Implementing SQLCipher for encrypted database storage would protect sensitive conversation data from unauthorized access through device theft or forensic extraction.'),
        ('9. Multimodal Input', 'Supporting simultaneous image and voice input would enable use cases such as identifying objects through the camera while asking questions verbally, leveraging both the camera API and speech recognition in parallel.'),
        ('10. Accessibility', 'Comprehensive accessibility improvements including TalkBack support for all UI elements, content descriptions for images and charts, scalable typography, and high-contrast mode support.'),
    ]
    for title, desc in med_items:
        story.append(sub_heading2(title, styles))
        story.append(body(desc, styles))

    story.append(sub_heading('Low Priority', styles))

    low_items = [
        ('11. Internationalization Framework', 'The current bilingual support (English/Spanish) is implemented through manual string placement. Adopting Android\'s standard strings.xml resource system with a proper i18n framework would enable community-contributed translations and simplify adding new languages.'),
        ('12. Performance Profiling', 'Systematic performance profiling using Android Studio Profiler and Jetpack Benchmark to identify memory leaks, excessive allocations, UI jank, and battery drain. Establishing performance budgets for startup time, message send latency, and memory usage.'),
        ('13. Tablet Layouts', 'Optimizing the Compose UI for tablet form factors with multi-pane layouts, drag-and-drop support, and adaptive navigation that takes advantage of larger screen real estate.'),
        ('14. Home Screen Widgets', 'Implementing Android AppWidgets for quick access to common actions (new conversation, voice activation, IoT control) directly from the home screen.'),
        ('15. Wear OS Companion', 'A Wear OS application for voice-first interactions on smartwatches, leveraging the existing voice system for quick queries and IoT control without requiring phone access.'),
    ]
    for title, desc in low_items:
        story.append(sub_heading2(title, styles))
        story.append(body(desc, styles))

    story.append(PageBreak())

    # ══════════════════════════════════════════════════════════════════
    # 10. DEPENDENCIES SUMMARY
    # ══════════════════════════════════════════════════════════════════
    story.append(section_heading('Dependencies Summary', styles, '10'))
    story.append(hr())

    story.append(body(
        'NEXA PRO maintains a curated set of dependencies managed through Gradle version catalogs. The following '
        'table enumerates all major dependencies, their versions, and their roles within the application architecture. '
        'Version alignment follows Android\'s recommended BOM (Bill of Materials) approach where applicable, ensuring '
        'compatibility between related libraries.',
        styles
    ))
    story.append(spacer(6))

    deps = [
        ['AndroidX Core KTX', '1.15.0', 'Core Kotlin extensions for Android APIs'],
        ['AndroidX Activity Compose', '1.9.3', 'Compose integration with Activity lifecycle'],
        ['AndroidX Compose BOM', '2024.12.01', 'Aligned Compose UI, Foundation, Material'],
        ['AndroidX Compose Material 3', 'BOM-managed', 'Material Design 3 components and theming'],
        ['Androidx Lifecycle', '2.8.7', 'ViewModel, LiveData, and lifecycle-aware components'],
        ['AndroidX Navigation Compose', '2.8.5', 'Type-safe navigation between Compose screens'],
        ['AndroidX Room', '2.6.1', 'SQLite abstraction for structured data persistence'],
        ['AndroidX DataStore', '1.1.1', 'Coroutine-based preference and proto storage'],
        ['OkHttp', '4.12.0', 'HTTP client for SSE streaming and API calls'],
        ['Google ML Kit Language ID', '17.0.5', 'On-device language identification'],
        ['Google ML Kit Entity Extraction', '2.0.0', 'Structured entity recognition in text'],
        ['Google ML Kit Smart Reply', '0.1.0', 'Contextual reply suggestions'],
        ['Google ML Kit Translate', '17.0.3', 'On-device text translation'],
        ['Google Play Services Location', '21.3.0', 'Fused location provider and geocoding'],
        ['Jsoup', '1.18.3', 'HTML parsing and web content scraping'],
        ['Coil Compose', '2.7.0', 'Image loading with Compose integration'],
        ['Kotlin Coroutines', '1.9.0', 'Asynchronous programming primitives'],
        ['Kotlin Serialization', '1.7.3', 'JSON serialization for API data models'],
        ['KSP', '2.0.21-1.0.28', 'Annotation processing for Room and other KSP plugins'],
        ['JUnit', '4.13.2', 'Unit testing framework (declared, no tests written)'],
        ['Mockito Kotlin', '5.4.0', 'Mocking framework for Kotlin unit tests'],
    ]

    story.append(make_table(
        ['Dependency', 'Version', 'Purpose'],
        deps,
        col_widths=[avail_width * 0.32, avail_width * 0.16, avail_width * 0.52]
    ))
    story.append(Paragraph('<i>Table 3: Complete dependency inventory with versions and purposes</i>', styles['Caption']))

    story.append(spacer(8))
    story.append(body(
        'The dependency set is relatively lean for an application of NEXA PRO\'s scope, reflecting the project\'s '
        'emphasis on Android platform APIs and on-device processing over third-party libraries. The most notable '
        'dependency is Google ML Kit, which provides multiple on-device ML capabilities without requiring custom '
        'model training or deployment. The recent addition of Jsoup for web scraping introduces a well-maintained '
        'library with minimal transitive dependencies, keeping the dependency footprint manageable. The project does '
        'not currently use any dependency injection, image processing (beyond Coil for loading), or networking '
        'abstraction libraries (OkHttp is used directly without Retrofit), which contributes to build speed and '
        'reduces potential version conflict issues.',
        styles
    ))
    story.append(PageBreak())

    # ══════════════════════════════════════════════════════════════════
    # 11. SECURITY CONSIDERATIONS
    # ══════════════════════════════════════════════════════════════════
    story.append(section_heading('Security Considerations', styles, '11'))
    story.append(hr())

    story.append(sub_heading('Current Security Measures', styles))
    story.append(body(
        'NEXA PRO implements several security measures appropriate for a mobile AI assistant application. '
        'Authentication is handled locally through the UserStore module, which stores user credentials using '
        'password hashing (likely bcrypt or PBKDF2-based) without transmitting passwords to external servers. '
        'This approach eliminates the risk of credential interception during transmission and reduces the attack '
        'surface by removing the need for a server-side authentication endpoint. All network communication uses '
        'HTTPS exclusively, ensuring that data transmitted to Groq, Pollinations AI, and DuckDuckGo is encrypted '
        'in transit using TLS. The OkHttp client is configured to enforce certificate validation, preventing '
        'man-in-the-middle attacks.',
        styles
    ))
    story.append(body(
        'API keys for the Groq service are stored in DataStore Preferences, which provides encrypted file storage '
        'on devices running Android 10 (API 29) and above through Android\'s EncryptedFile mechanism. On older '
        'devices, DataStore files are stored with standard file permissions, which provides protection against '
        'unprivileged applications but may be accessible to rooted devices or through ADB backup extraction. The '
        'application implements privacy controls for the episodic memory system, requiring explicit user consent '
        'before retaining information across sessions and providing granular deletion capabilities.',
        styles
    ))

    story.append(sub_heading('Security Recommendations', styles))

    sec_recs = [
        ['Encrypted Database', 'Migrate Room Database to use SQLCipher, ensuring that chat history and user profile data are encrypted at rest. Currently, database files are accessible to root users and through backup extraction, potentially exposing sensitive conversation content.'],
        ['Android Keystore for API Keys', 'Store API keys using the Android Keystore System rather than DataStore Preferences. The Keystore provides hardware-backed key storage that is resistant to extraction even from rooted devices, offering significantly stronger protection for sensitive credentials.'],
        ['Certificate Pinning', 'Implement OkHttp certificate pinning for Groq and Pollinations AI endpoints to prevent man-in-the-middle attacks, particularly important given that API keys are transmitted in request headers.'],
        ['Secure Memory Storage', 'Ensure that episodic memory data stored by EpisodicMemoryManager is encrypted and that the consent management system prevents memory access without proper authentication verification.'],
        ['Input Sanitization', 'Implement comprehensive input sanitization for web search queries and HTML scraping results to prevent XSS-style injection if scraped content is ever rendered in WebView components.'],
        ['Security Audit', 'Conduct a formal security audit focusing on data flow analysis, credential storage, network communication, and content provider exposure before any public release.'],
    ]

    story.append(spacer(4))
    story.append(make_table(
        ['Recommendation', 'Description'],
        sec_recs,
        col_widths=[avail_width * 0.22, avail_width * 0.78]
    ))
    story.append(Paragraph('<i>Table 4: Security improvement recommendations</i>', styles['Caption']))

    story.append(spacer(8))
    story.append(body(
        'The application\'s security posture is adequate for a development-stage project but requires hardening '
        'before production release. The most critical gap is the lack of database encryption, as the Room database '
        'contains the entirety of the user\'s conversation history and could be extracted through device backup or '
        'root access. Implementing SQLCipher would address this vulnerability with minimal performance overhead, '
        'as encrypted databases typically add less than 10ms to query latency on modern devices. Additionally, '
        'the consent management system for episodic memory should be validated through penetration testing to ensure '
        'that memory data cannot be accessed without proper authentication, even by other applications on the same device.',
        styles
    ))

    # ── End of document ──────────────────────────────────────────────
    story.append(spacer(30))
    story.append(hr())
    story.append(Paragraph(
        '<i>This document was prepared as a technical analysis of the NEXA PRO v5.0 project. '
        'All information is based on source code review of commit fb55ad1. '
        'Recommendations reflect best practices for Android development as of May 2026.</i>',
        styles['Caption']
    ))

    # ══════════════════════════════════════════════════════════════════
    # BUILD
    # ══════════════════════════════════════════════════════════════════
    doc.multiBuild(story)
    print(f'PDF generated successfully: {OUTPUT_PATH}')
    return OUTPUT_PATH


if __name__ == '__main__':
    path = build_pdf()
    file_size = os.path.getsize(path)
    print(f'File size: {file_size:,} bytes ({file_size / 1024:.1f} KB)')
