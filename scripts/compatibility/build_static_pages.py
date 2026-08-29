#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import re
from datetime import datetime, timezone
from html import escape
from pathlib import Path
from urllib.parse import quote

STATUS_LABELS = {
    "playable": "Playable",
    "ingame": "Ingame",
    "menus": "Menus",
    "boots": "Boots",
    "nothing": "Nothing",
    "unknown": "Unknown",
}

STATUS_EXPLANATIONS = {
    "playable": "This report reached normal play without a major blocker in the recorded configuration. It is still a point-in-time result, not a guarantee for every device or game update.",
    "ingame": "This report reached controllable gameplay, but major issues, instability, missing features, or performance problems may still prevent normal completion.",
    "menus": "This report reached a title screen or menu but did not reach controllable gameplay. Treat it as partial progress rather than a playable result.",
    "boots": "This report produced meaningful output during startup but stopped before menus or gameplay. It is useful for tracking emulator progress, not for judging playability.",
    "nothing": "This configuration did not produce a usable result. The title may crash, hang, or remain on a black screen; another release, driver, or device can behave differently.",
    "unknown": "The available report does not map cleanly to a published compatibility status. Check the raw report and evidence before drawing a conclusion.",
}

BASE_STATIC_PAGES = [
    "index.html",
    "compatibility.html",
    "updates.html",
    "methodology.html",
    "guide.html",
    "faq.html",
    "about.html",
    "contact.html",
    "privacy.html",
    "terms.html",
]


def load_json(path: Path) -> dict:
    with path.open("r", encoding="utf-8") as handle:
        return json.load(handle)


def write_text(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8", newline="\n")


def normalize_status(value: object) -> str:
    status = str(value or "unknown").lower()
    return status if status in STATUS_LABELS else "unknown"


def text(value: object, fallback: str = "Not recorded") -> str:
    normalized = "" if value is None else str(value).strip()
    return normalized or fallback


def format_date(value: object) -> str:
    raw = str(value or "").strip()
    if not raw:
        return "Unknown date"
    try:
        parsed = datetime.fromisoformat(raw.replace("Z", "+00:00"))
        return parsed.strftime("%d %B %Y")
    except ValueError:
        return raw


def driver_label(report: dict) -> str:
    driver = report.get("driver") or {}
    display = text(driver.get("display"), "")
    if display:
        return display
    parts = [driver.get("name") or driver.get("type"), driver.get("version"), driver.get("build")]
    return " ".join(str(part).strip() for part in parts if str(part or "").strip()) or "Unknown driver"


def device_label(report: dict) -> str:
    device = report.get("device") or {}
    label = text(device.get("label"), "")
    if label:
        return label
    return " ".join(filter(None, [text(device.get("manufacturer"), ""), text(device.get("model"), "")])) or "Unknown device"


def fps_label(report: dict) -> str:
    value = (report.get("performance") or {}).get("averageFps")
    try:
        number = float(value)
    except (TypeError, ValueError):
        return "Not measured"
    return f"{number:.1f} FPS"


def rel_prefix(depth: int) -> str:
    return "../" * depth


def nav(prefix: str, active: str = "") -> str:
    items = [
        ("Home", f"{prefix}index.html", "home"),
        ("Compatibility", f"{prefix}compatibility.html", "compatibility"),
        ("All games", f"{prefix}games/index.html", "games"),
        ("Updates", f"{prefix}updates.html", "updates"),
        ("How Testing Works", f"{prefix}methodology.html", "methodology"),
        ("Guide", f"{prefix}guide.html", "guide"),
        ("FAQ", f"{prefix}faq.html", "faq"),
        ("About", f"{prefix}about.html", "about"),
        ("Contact", f"{prefix}contact.html", "contact"),
    ]
    links = []
    for label, href, key in items:
        current = ' aria-current="page"' if key == active else ""
        links.append(f'<a class="nav-link" href="{href}"{current}>{label}</a>')
    links.append('<a class="nav-link nav-external" href="https://github.com/JICA98/Bachata-S4" target="_blank" rel="noreferrer">GitHub</a>')
    return "\n        ".join(links)


def header(prefix: str, active: str = "") -> str:
    return f'''<a class="skip-link" href="#content">Skip to content</a>
<header class="site-header">
  <div class="container header-inner">
    <a class="brand" href="{prefix}index.html" aria-label="Bachata S4 home">
      <img src="{prefix}assets/bachata-s4-logo.png" alt="" width="48" height="48">
      <span><strong>Bachata S4</strong><small>PS4 emulation on Android</small></span>
    </a>
    <nav class="site-nav" id="site-nav" aria-label="Primary">
        {nav(prefix, active)}
    </nav>
    <div class="header-actions">
      <button class="icon-button" id="theme-toggle" type="button" aria-label="Switch color theme" title="Switch color theme"><span aria-hidden="true">◐</span></button>
      <button class="icon-button nav-toggle" id="nav-toggle" type="button" aria-expanded="false" aria-controls="site-nav" aria-label="Open menu" title="Menu"><span class="nav-toggle-bars" aria-hidden="true"></span></button>
    </div>
  </div>
</header>'''


def footer(prefix: str) -> str:
    return f'''<footer class="site-footer">
  <div class="container footer-inner">
    <div class="brand footer-brand">
      <img src="{prefix}assets/bachata-s4-logo.png" alt="" width="38" height="38">
      <span><strong>Bachata S4</strong><small>Evidence-first Android PS4 emulation compatibility</small></span>
    </div>
    <div class="footer-col">
      <nav class="footer-nav" aria-label="Site">
        <a href="{prefix}index.html">Home</a>
        <a href="{prefix}compatibility.html">Compatibility</a>
        <a href="{prefix}games/index.html">All games</a>
        <a href="{prefix}guide.html">Compatibility guide</a>
        <a href="{prefix}faq.html">FAQ</a>
        <a href="{prefix}methodology.html">How Testing Works</a>
        <a href="{prefix}about.html">About</a>
        <a href="{prefix}contact.html">Contact</a>
        <a href="{prefix}privacy.html">Privacy</a>
        <a href="{prefix}terms.html">Terms</a>
      </nav>
      <p>Bachata S4 is an independent project, not affiliated with Sony Interactive Entertainment. Compatibility reports describe emulator behavior on specific tested configurations and do not provide copyrighted game content.</p>
    </div>
  </div>
</footer>'''


def page_shell(*, title: str, description: str, canonical: str, body: str, prefix: str, active: str, schema: dict | list | None = None) -> str:
    schema_tag = ""
    if schema is not None:
        schema_tag = f'\n  <script type="application/ld+json">{json.dumps(schema, ensure_ascii=False, separators=(",", ":"))}</script>'
    return f'''<!doctype html>
<html lang="en" data-theme="dark">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <meta name="description" content="{escape(description, quote=True)}">
  <meta name="theme-color" content="#0a0d16">
  <meta name="google-adsense-account" content="ca-pub-8704776489115590">
  <link rel="canonical" href="{escape(canonical, quote=True)}">
  <meta property="og:title" content="{escape(title, quote=True)}">
  <meta property="og:description" content="{escape(description, quote=True)}">
  <meta property="og:type" content="website">
  <meta property="og:url" content="{escape(canonical, quote=True)}">
  <meta property="og:image" content="https://bachatas4.games/assets/bachata-s4-logo.png">
  <meta name="twitter:card" content="summary_large_image">
  <title>{escape(title)}</title>
  <link rel="icon" href="{prefix}assets/bachata-s4-logo.png">
  <link rel="stylesheet" href="{prefix}assets/css/styles.css">
  <link rel="stylesheet" href="{prefix}assets/css/content-pages.css">{schema_tag}
</head>
<body>
{header(prefix, active)}
<main id="content">
{body}
</main>
{footer(prefix)}
<script defer src="{prefix}assets/js/site.js"></script>
</body>
</html>
'''


def report_table(report: dict) -> str:
    device = report.get("device") or {}
    driver = report.get("driver") or {}
    performance = report.get("performance") or {}
    release = report.get("release") or {}
    settings = report.get("settings") or {}
    rows = [
        ("Status", STATUS_LABELS[normalize_status(report.get("status"))]),
        ("Tested", format_date(report.get("testedAt"))),
        ("Bachata S4 release", text(release.get("tag"), "Unknown release")),
        ("Game version", text(report.get("gameVersion"), "Not recorded")),
        ("Device", device_label(report)),
        ("SoC", text(device.get("soc"))),
        ("GPU", text(device.get("gpu"))),
        ("Android", text(device.get("androidVersion"))),
        ("Graphics driver", driver_label(report)),
        ("Average FPS", fps_label(report)),
        ("Frame pacing", text(performance.get("framePacing"))),
        ("Resolution scale", f"{settings.get('resolutionScale')}×" if settings.get("resolutionScale") else "Not recorded"),
    ]
    return '<dl class="facts-grid">' + "".join(
        f'<div><dt>{escape(label)}</dt><dd>{escape(str(value))}</dd></div>' for label, value in rows
    ) + "</dl>"


def evidence_block(report: dict, prefix: str) -> str:
    evidence = report.get("evidence") or {}
    screenshots = evidence.get("screenshots") or []
    logs = evidence.get("logs") or []
    parts: list[str] = []
    if screenshots:
        gallery = []
        for idx, shot in enumerate(screenshots, 1):
            url = text(shot.get("url"), "")
            if not url:
                continue
            href = url if re.match(r"^https?://", url) else prefix + url
            caption = text(shot.get("caption"), f"Compatibility screenshot {idx}")
            gallery.append(
                f'<a class="evidence-shot" href="{escape(href, quote=True)}" target="_blank" rel="noreferrer"><img loading="lazy" src="{escape(href, quote=True)}" alt="{escape(caption, quote=True)}"><span>{escape(caption)}</span></a>'
            )
        if gallery:
            parts.append('<h3>Screenshots</h3><div class="evidence-grid">' + "".join(gallery) + "</div>")
    if logs:
        links = []
        for log in logs:
            url = text(log.get("url"), "")
            if not url:
                continue
            label = text(log.get("label"), "Session log")
            links.append(f'<li><a href="{escape(url, quote=True)}" target="_blank" rel="noreferrer">{escape(label)} ↗</a></li>')
        if links:
            parts.append('<h3>Logs</h3><ul class="evidence-links">' + "".join(links) + "</ul>")
    return "".join(parts) or "<p>No public screenshots or logs were attached to this report.</p>"


def render_game_page(game: dict, detail: dict, base_url: str) -> str:
    cusa = text(game.get("cusaId"), "UNKNOWN")
    title = text(game.get("title"), cusa)
    reports = detail.get("reports") or []
    latest = reports[0] if reports else {}
    status = normalize_status(latest.get("status") or game.get("latestStatus") or game.get("bestStatus"))
    latest_summary = text(latest.get("summary") or latest.get("notes"), "No summary was recorded for the latest test.")
    canonical = f"{base_url}/games/{quote(cusa)}.html"
    description = f"{title} ({cusa}) compatibility on Bachata S4 for Android: {STATUS_LABELS[status]}. See tested release, device, graphics driver, performance, screenshots, logs, and report history."
    issue_url = text(game.get("issueUrl"), "")
    issue_link = f'<a class="button button-secondary" href="{escape(issue_url, quote=True)}" target="_blank" rel="noreferrer">Open game discussion ↗</a>' if issue_url else ""

    history_cards = []
    for report in reports:
        rstatus = normalize_status(report.get("status"))
        notes = text(report.get("notes") or report.get("summary"), "No detailed notes were recorded.")
        issues = report.get("issues") or []
        issue_html = ""
        if issues:
            issue_html = '<h4>Known issues</h4><ul>' + "".join(f'<li>{escape(str(item))}</li>' for item in issues) + "</ul>"
        history_cards.append(f'''<article class="report-card" id="{escape(text(report.get('reportId'), ''), quote=True)}">
  <div class="report-card-head"><span class="status-badge {rstatus}">{STATUS_LABELS[rstatus]}</span><strong>{escape(text((report.get('release') or {}).get('tag'), 'Unknown release'))}</strong><span>{escape(format_date(report.get('testedAt')))}</span></div>
  <p>{escape(notes)}</p>
  {report_table(report)}
  {issue_html}
  <div class="evidence-block">{evidence_block(report, '../')}</div>
</article>''')

    report_count = len(reports)
    compare_copy = (
        f"This page preserves {report_count} published {'report' if report_count == 1 else 'reports'} for {title}. "
        "When two reports disagree, compare the emulator release, device SoC, GPU, Android version, selected graphics driver, game version, and test date before treating the difference as a regression. "
        "Mobile thermals can also change sustained performance, so a short FPS measurement should not be read as a guaranteed long-session frame rate."
    )
    schema = {
        "@context": "https://schema.org",
        "@graph": [
            {
                "@type": "WebPage",
                "@id": canonical,
                "url": canonical,
                "name": f"{title} ({cusa}) compatibility · Bachata S4",
                "description": description,
                "isPartOf": {"@id": f"{base_url}/#website"},
                "dateModified": latest.get("testedAt") or None,
            },
            {
                "@type": "VideoGame",
                "name": title,
                "gamePlatform": "PlayStation 4",
                "identifier": cusa,
                "publisher": text(game.get("publisher"), "Unknown publisher"),
            },
            {
                "@type": "BreadcrumbList",
                "itemListElement": [
                    {"@type": "ListItem", "position": 1, "name": "Home", "item": f"{base_url}/"},
                    {"@type": "ListItem", "position": 2, "name": "Compatibility", "item": f"{base_url}/compatibility.html"},
                    {"@type": "ListItem", "position": 3, "name": title, "item": canonical},
                ],
            },
        ],
    }

    body = f'''<section class="page-head game-page-head">
  <div class="container">
    <nav class="breadcrumbs" aria-label="Breadcrumb"><a href="../index.html">Home</a><span>›</span><a href="../compatibility.html">Compatibility</a><span>›</span><span>{escape(cusa)}</span></nav>
    <span class="eyebrow">Game compatibility report</span>
    <h1>{escape(title)}</h1>
    <p class="lede">{escape(cusa)}{(' · ' + escape(text(game.get('region'), ''))) if text(game.get('region'), '') else ''}{(' · ' + escape(text(game.get('publisher'), ''))) if text(game.get('publisher'), '') else ''}</p>
    <div class="hero-actions"><span class="status-badge {status}">{STATUS_LABELS[status]}</span>{issue_link}</div>
  </div>
</section>
<section class="page-body">
  <div class="container prose wide-prose">
    <h2>Latest tested result</h2>
    <p>{escape(latest_summary)}</p>
    <p>{escape(STATUS_EXPLANATIONS[status])}</p>
    {report_table(latest) if latest else '<p>No report data is currently available.</p>'}

    <h2>How to interpret this result</h2>
    <p>{escape(compare_copy)}</p>
    <p>A compatibility label answers only “what happened in this recorded environment?” It does not prove that every phone with the same chipset will behave identically. Firmware, cooling, driver revisions, emulator settings, and background system load can all change the outcome.</p>

    <h2>Report history and evidence</h2>
    <p>Each report below is kept separately rather than overwritten. This makes improvements and regressions visible over time and lets you compare evidence instead of relying on a single aggregate badge.</p>
    <div class="report-stack">{''.join(history_cards) if history_cards else '<p>No reports have been published for this title yet.</p>'}</div>

    <h2>Before comparing with your device</h2>
    <ul>
      <li>Match the Bachata S4 release and commit first.</li>
      <li>Compare the exact GPU driver, including Turnip version/build when used.</li>
      <li>Check game version, Android version, resolution scale, and recorded settings.</li>
      <li>Use screenshots and logs to distinguish rendering problems from guest-code or runtime failures.</li>
      <li>For sustained performance, consider thermal throttling and test duration rather than average FPS alone.</li>
    </ul>
    <p>For the status definitions and review process, read <a href="../methodology.html">How Testing Works</a>. For a practical walkthrough of the database, read the <a href="../guide.html">compatibility guide</a>.</p>
  </div>
</section>'''
    return page_shell(
        title=f"{title} ({cusa}) compatibility · Bachata S4",
        description=description,
        canonical=canonical,
        body=body,
        prefix="../",
        active="games",
        schema=schema,
    )


def render_games_index(games: list[dict], generated_at: str, base_url: str) -> str:
    rows = []
    for game in sorted(games, key=lambda item: text(item.get("title"), "").lower()):
        cusa = text(game.get("cusaId"), "")
        if not cusa:
            continue
        status = normalize_status(game.get("bestStatus"))
        rows.append(f'''<article class="directory-card">
  <div><span class="status-badge {status}">{STATUS_LABELS[status]}</span><h2><a href="{escape(cusa, quote=True)}.html">{escape(text(game.get('title'), cusa))}</a></h2><p>{escape(cusa)} · {int(game.get('reportCount') or 0)} reports · {int(game.get('deviceCount') or 0)} devices</p></div>
  <a class="button button-secondary" href="{escape(cusa, quote=True)}.html">View evidence</a>
</article>''')
    body = f'''<section class="page-head"><div class="container"><span class="eyebrow">Crawlable compatibility archive</span><h1>All tested games</h1><p class="lede">One permanent page per CUSA serial, with report history, environment details, performance data, screenshots, and logs.</p></div></section>
<section class="page-body"><div class="container prose wide-prose"><p>Generated {escape(format_date(generated_at))} from the public Bachata-S4-Compatibility repository. This directory is intentionally plain HTML so game reports remain useful and discoverable even when JavaScript is disabled.</p><div class="directory-grid">{''.join(rows)}</div></div></section>'''
    schema = {
        "@context": "https://schema.org",
        "@type": "CollectionPage",
        "name": "Bachata S4 game compatibility archive",
        "url": f"{base_url}/games/",
        "description": "Static index of Bachata S4 game compatibility pages for Android PS4 emulation testing.",
    }
    return page_shell(
        title="All tested games · Bachata S4",
        description="Browse permanent Bachata S4 compatibility pages for every tested PS4 CUSA serial, including release, device, driver, performance, screenshots, logs, and report history.",
        canonical=f"{base_url}/games/",
        body=body,
        prefix="../",
        active="games",
        schema=schema,
    )


def inject_stylesheet(html: str) -> str:
    marker = 'assets/css/content-pages.css'
    if marker in html:
        return html
    return html.replace('</head>', '  <link rel="stylesheet" href="assets/css/content-pages.css">\n</head>', 1)


def replace_generated_block(html: str, start: str, end: str, block: str) -> str:
    pattern = re.compile(re.escape(start) + r".*?" + re.escape(end), re.S)
    replacement = f"{start}\n{block}\n{end}"
    if pattern.search(html):
        return pattern.sub(replacement, html, count=1)
    if "</main>" not in html:
        raise ValueError("Could not inject generated block: </main> missing")
    return html.replace("</main>", replacement + "\n</main>", 1)


def augment_top_level_navigation(html: str) -> str:
    def add_primary(match: re.Match[str]) -> str:
        block = match.group(0)
        additions = []
        if 'href="games/index.html"' not in block:
            additions.append('<a class="nav-link" href="games/index.html">All games</a>')
        if 'href="guide.html"' not in block:
            additions.append('<a class="nav-link" href="guide.html">Guide</a>')
        if 'href="faq.html"' not in block:
            additions.append('<a class="nav-link" href="faq.html">FAQ</a>')
        if not additions:
            return block
        return block.replace('</nav>', '        ' + ''.join(additions) + '\n      </nav>', 1)

    def add_footer(match: re.Match[str]) -> str:
        block = match.group(0)
        additions = []
        if 'href="games/index.html"' not in block:
            additions.append('<a href="games/index.html">All games</a>')
        if 'href="guide.html"' not in block:
            additions.append('<a href="guide.html">Guide</a>')
        if 'href="faq.html"' not in block:
            additions.append('<a href="faq.html">FAQ</a>')
        if not additions:
            return block
        return block.replace('</nav>', ''.join(additions) + '\n        </nav>', 1)

    html = re.sub(r'<nav class="site-nav".*?</nav>', add_primary, html, count=1, flags=re.S)
    html = re.sub(r'<nav class="footer-nav".*?</nav>', add_footer, html, count=1, flags=re.S)
    return html


def patch_top_level_pages(site: Path, games: list[dict]) -> None:
    for path in site.glob("*.html"):
        html = path.read_text(encoding="utf-8")
        html = inject_stylesheet(html)
        html = augment_top_level_navigation(html)
        html = html.replace("using the repository skill.", "through the public compatibility repository.")
        path.write_text(html, encoding="utf-8", newline="\n")

    compatibility = site / "compatibility.html"
    if compatibility.exists():
        links = []
        for game in sorted(games, key=lambda item: text(item.get("title"), "").lower()):
            cusa = text(game.get("cusaId"), "")
            if not cusa:
                continue
            status = normalize_status(game.get("bestStatus"))
            links.append(f'<li><a href="games/{escape(cusa, quote=True)}.html"><strong>{escape(text(game.get("title"), cusa))}</strong><span>{escape(cusa)} · {STATUS_LABELS[status]} · {int(game.get("reportCount") or 0)} reports</span></a></li>')
        block = f'''<section class="page-body generated-game-directory" aria-labelledby="static-game-pages-title">
  <div class="container prose wide-prose">
    <span class="eyebrow">Permanent game pages</span>
    <h2 id="static-game-pages-title">Browse every tested CUSA without JavaScript</h2>
    <p>The interactive filters above are convenient, but every tested game also has a permanent HTML page with its report history and evidence. These links work with JavaScript disabled and give search engines a direct path to the actual compatibility content.</p>
    <ul class="static-game-list">{''.join(links)}</ul>
    <p><a class="button button-secondary" href="games/index.html">Open the complete game archive</a></p>
  </div>
</section>'''
        html = compatibility.read_text(encoding="utf-8")
        html = replace_generated_block(html, "<!-- GENERATED_STATIC_GAME_DIRECTORY_START -->", "<!-- GENERATED_STATIC_GAME_DIRECTORY_END -->", block)
        compatibility.write_text(html, encoding="utf-8", newline="\n")

    home = site / "index.html"
    if home.exists():
        block = '''<section class="about-section" aria-labelledby="knowledge-title">
  <div class="container">
    <div class="section-heading"><div><span class="eyebrow">Learn before comparing</span><h2 id="knowledge-title">Compatibility knowledge base</h2></div></div>
    <div class="about-grid">
      <article><h3>Read reports correctly</h3><p>Learn why the same title can behave differently across Bachata S4 releases, Android devices, GPU drivers, thermals, and game updates.</p><a class="button button-secondary" href="guide.html">Compatibility guide</a></article>
      <article><h3>Understand the evidence</h3><p>See how statuses are assigned, what testers record, and why screenshots and logs are preserved with every report.</p><a class="button button-secondary" href="methodology.html">How testing works</a></article>
      <article><h3>Common questions</h3><p>Get answers about supported devices, Turnip drivers, FPS numbers, regressions, legal game ownership, and reporting a result.</p><a class="button button-secondary" href="faq.html">Read the FAQ</a></article>
      <article><h3>Permanent game pages</h3><p>Open one crawlable page per CUSA serial with report history, device details, driver details, performance, screenshots, and logs.</p><a class="button button-secondary" href="games/index.html">Browse all games</a></article>
    </div>
  </div>
</section>'''
        html = home.read_text(encoding="utf-8")
        html = replace_generated_block(html, "<!-- GENERATED_KNOWLEDGE_BASE_START -->", "<!-- GENERATED_KNOWLEDGE_BASE_END -->", block)
        home.write_text(html, encoding="utf-8", newline="\n")


def build_sitemap(site: Path, games: list[dict], base_url: str, generated_at: str) -> None:
    urls: list[tuple[str, str]] = []
    for name in BASE_STATIC_PAGES:
        if (site / name).exists():
            loc = f"{base_url}/" if name == "index.html" else f"{base_url}/{name}"
            urls.append((loc, generated_at))
    urls.append((f"{base_url}/games/", generated_at))
    for game in games:
        cusa = text(game.get("cusaId"), "")
        if not cusa:
            continue
        lastmod = text(game.get("latestTestedAt"), generated_at)
        urls.append((f"{base_url}/games/{quote(cusa)}.html", lastmod))

    items = []
    for loc, lastmod in urls:
        date = str(lastmod or "").split("T", 1)[0] or datetime.now(timezone.utc).date().isoformat()
        items.append(f"  <url><loc>{escape(loc)}</loc><lastmod>{escape(date)}</lastmod></url>")
    sitemap = '<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n' + "\n".join(items) + "\n</urlset>\n"
    write_text(site / "sitemap.xml", sitemap)
    write_text(site / "robots.txt", f"User-agent: *\nAllow: /\n\nSitemap: {base_url}/sitemap.xml\n")


def render_404(base_url: str) -> str:
    body = '''<section class="page-head"><div class="container"><span class="eyebrow">404</span><h1>Page not found</h1><p class="lede">The page may have moved, or the link may be outdated.</p><div class="hero-actions"><a class="button button-primary" href="/">Go home</a><a class="button button-secondary" href="/compatibility.html">Browse compatibility</a></div></div></section>
<section class="page-body"><div class="container prose"><h2>Looking for a game?</h2><p>Use the compatibility database or the permanent all-games archive. Every published CUSA report has a stable page under <code>/games/</code>.</p></div></section>'''
    return page_shell(
        title="Page not found · Bachata S4",
        description="The requested Bachata S4 compatibility page could not be found. Browse the compatibility database or permanent game archive.",
        canonical=f"{base_url}/404.html",
        body=body,
        prefix="",
        active="",
    )


def build(site: Path, data: Path, base_url: str) -> None:
    base_url = base_url.rstrip("/")
    index_path = data / "site-index.json"
    if not index_path.exists():
        raise SystemExit(f"Missing generated compatibility index: {index_path}")
    index = load_json(index_path)
    games = index.get("games") or []
    generated_at = text(index.get("generatedAt"), datetime.now(timezone.utc).isoformat())

    games_dir = site / "games"
    games_dir.mkdir(parents=True, exist_ok=True)
    for game in games:
        cusa = text(game.get("cusaId"), "")
        if not cusa:
            continue
        detail_path = data / "games" / f"{cusa}.json"
        if not detail_path.exists():
            raise SystemExit(f"Missing per-game detail JSON: {detail_path}")
        detail = load_json(detail_path)
        write_text(games_dir / f"{cusa}.html", render_game_page(game, detail, base_url))

    write_text(games_dir / "index.html", render_games_index(games, generated_at, base_url))
    patch_top_level_pages(site, games)
    build_sitemap(site, games, base_url, generated_at)
    write_text(site / "404.html", render_404(base_url))
    print(f"Generated {len(games)} permanent game page(s), sitemap.xml, robots.txt, and static navigation blocks.")


def main() -> int:
    parser = argparse.ArgumentParser(description="Generate crawlable HTML compatibility pages for the Bachata S4 static site")
    parser.add_argument("--site", type=Path, required=True, help="Assembled site directory, e.g. _site")
    parser.add_argument("--data", type=Path, required=True, help="Generated data directory, e.g. _site/data")
    parser.add_argument("--base-url", default="https://bachatas4.games")
    args = parser.parse_args()
    build(args.site.resolve(), args.data.resolve(), args.base_url)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
