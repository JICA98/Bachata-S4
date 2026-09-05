#!/usr/bin/env python3
from __future__ import annotations

import argparse
import html
import json
import shutil
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import quote

STATUS_ORDER = {"playable": 0, "ingame": 1, "menus": 2, "boots": 3, "nothing": 4, "unknown": 5}
STATUS_LABEL = {"playable": "Playable", "ingame": "Ingame", "menus": "Menus", "boots": "Boots", "nothing": "Nothing", "unknown": "Unknown"}
RAW_BASE = "https://raw.githubusercontent.com/JICA98/Bachata-S4-Compatibility/main/"


def load_json(path: Path):
    with path.open("r", encoding="utf-8") as fh:
        return json.load(fh)


def write_json(path: Path, value) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def esc(value) -> str:
    return html.escape("" if value is None else str(value), quote=True)


def normalize_status(value) -> str:
    value = str(value or "unknown").lower()
    return value if value in STATUS_ORDER else "unknown"


def driver_display(driver: dict) -> str:
    if not driver:
        return "Not recorded"
    parts = [driver.get("name") or driver.get("type") or driver.get("kind"), driver.get("version"), driver.get("build")]
    return " ".join(str(v) for v in parts if v) or "Not recorded"


def screenshot_output_url(path: str) -> str:
    path = path.lstrip("/")
    return f"/evidence/{path}"


def log_url(item: dict) -> str:
    return str(item.get("externalUrl") or f"{RAW_BASE}{item.get('path', '').lstrip('/')}")


def copy_screenshot(source: Path, output: Path, item: dict) -> dict:
    value = dict(item)
    rel = str(item.get("path") or "").lstrip("/")
    if not rel:
        value["url"] = "/assets/placeholder.svg"
        return value
    src = source / rel
    dst = output / "evidence" / rel
    if src.is_file():
        dst.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(src, dst)
        value["url"] = screenshot_output_url(rel)
    else:
        value["url"] = "/assets/placeholder.svg"
        value["missing"] = True
    return value


def transform_report(source: Path, output: Path, report: dict) -> dict:
    value = json.loads(json.dumps(report))
    value["status"] = normalize_status(value.get("status"))
    evidence = value.setdefault("evidence", {})
    evidence["screenshots"] = [copy_screenshot(source, output, item) for item in evidence.get("screenshots", []) if isinstance(item, dict)]
    evidence["logs"] = [{**item, "url": log_url(item)} for item in evidence.get("logs", []) if isinstance(item, dict)]
    for key in ("issueNumber", "issues", "discussion", "discussions", "canonicalIssue", "legacyIssues"):
        value.pop(key, None)
    return value


def report_summary(report: dict) -> dict:
    screenshots = report.get("evidence", {}).get("screenshots", [])
    driver = report.get("driver", {})
    perf = report.get("performance", {})
    return {
        "reportId": report.get("reportId", ""),
        "status": normalize_status(report.get("status")),
        "testedAt": report.get("testedAt", ""),
        "gameVersion": report.get("gameVersion", ""),
        "releaseTag": report.get("release", {}).get("tag", ""),
        "releaseCommit": report.get("release", {}).get("commit", ""),
        "summary": report.get("summary", ""),
        "device": {
            "label": report.get("device", {}).get("label", "Unknown device"),
            "soc": report.get("device", {}).get("soc", ""),
            "gpu": report.get("device", {}).get("gpu", ""),
            "androidVersion": report.get("device", {}).get("androidVersion", ""),
        },
        "driver": {**driver, "display": driver_display(driver)},
        "performance": {"averageFps": perf.get("averageFps")},
        "thumbnail": screenshots[0].get("url") if screenshots else "/assets/placeholder.svg",
    }


def fmt_date(value: str) -> str:
    try:
        return datetime.fromisoformat(value.replace("Z", "+00:00")).strftime("%d %b %Y")
    except Exception:
        return value or "Unknown date"


def fps_value(value) -> str:
    try:
        number = float(value)
    except (TypeError, ValueError):
        return "Not recorded"
    return f"{number:.1f} FPS" if number >= 10 else f"{number:.2f} FPS"


def spec(label: str, value) -> str:
    return f'<div class="spec"><small>{esc(label)}</small><strong>{esc(value or "Not recorded")}</strong></div>'


def render_report(report: dict, idx: int) -> str:
    status = normalize_status(report.get("status"))
    release = report.get("release", {})
    device = report.get("device", {})
    driver = report.get("driver", {})
    perf = report.get("performance", {})
    screenshots = report.get("evidence", {}).get("screenshots", [])
    logs = report.get("evidence", {}).get("logs", [])
    release_tag = release.get("tag") or "Unknown release"
    release_url = release.get("url") or (f"https://github.com/JICA98/Bachata-S4/releases/tag/{quote(str(release_tag))}" if release_tag != "Unknown release" else "")
    release_value = f'<a href="{esc(release_url)}" target="_blank" rel="noreferrer">{esc(release_tag)}</a>' if release_url else esc(release_tag)
    specs = "".join([
        spec("Release", release_tag),
        spec("Game version", report.get("gameVersion")),
        spec("Device", device.get("label")),
        spec("SoC", device.get("soc")),
        spec("GPU", device.get("gpu")),
        spec("Android", device.get("androidVersion")),
        spec("Driver", driver_display(driver)),
        spec("Average FPS", fps_value(perf.get("averageFps"))),
        spec("Minimum FPS", fps_value(perf.get("minimumFps"))),
        spec("Maximum FPS", fps_value(perf.get("maximumFps"))),
        spec("Frame pacing", perf.get("framePacing")),
        spec("Test duration", f"{perf.get('testDurationSeconds')} s" if perf.get("testDurationSeconds") is not None else None),
    ])
    shots = "".join(
        f'<figure class="screenshot"><a href="{esc(item.get("url") or "/assets/placeholder.svg")}"><img loading="lazy" src="{esc(item.get("url") or "/assets/placeholder.svg")}" alt="{esc(item.get("caption") or "Compatibility screenshot")}"></a><figcaption>{esc(item.get("caption") or "Compatibility screenshot")}</figcaption></figure>'
        for item in screenshots
    )
    log_links = "".join(
        f'<a class="button button-quiet" href="{esc(item.get("url"))}" target="_blank" rel="noreferrer">{esc(item.get("label") or "Open log")} ↗</a>'
        for item in logs if item.get("url")
    )
    notes = report.get("notes") or ""
    return f'''
    <article class="report-card" id="report-{esc(report.get('reportId') or idx)}">
      <header class="report-header">
        <div><h2>{esc(release_tag)} · {esc(fmt_date(report.get('testedAt', '')))}</h2><p>{esc(report.get('reportId') or '')}</p></div>
        <span class="status-badge report-status {esc(status)}">{esc(STATUS_LABEL[status])}</span>
      </header>
      <div class="report-body">
        <p class="report-summary">{esc(report.get('summary') or 'No summary recorded.')}</p>
        <div class="spec-grid">{specs}</div>
        {f'<p class="report-notes">{esc(notes)}</p>' if notes else ''}
        {f'<div class="screenshot-grid">{shots}</div>' if shots else ''}
        {f'<div class="log-links">{log_links}</div>' if log_links else ''}
      </div>
    </article>'''


def render_game_page(base_url: str, game: dict, reports: list[dict]) -> str:
    best = min(reports, key=lambda r: STATUS_ORDER.get(normalize_status(r.get("status")), 5)) if reports else None
    latest = reports[0] if reports else None
    status = normalize_status((best or {}).get("status"))
    screenshots = (latest or {}).get("evidence", {}).get("screenshots", [])
    hero_img = screenshots[0].get("url") if screenshots else "/assets/placeholder.svg"
    cusa = game.get("cusaId") or "Unknown"
    title = game.get("title") or cusa
    canonical = f"{base_url.rstrip('/')}/games/{quote(str(cusa))}/"
    meta = " · ".join(str(v) for v in [cusa, game.get("region"), game.get("publisher")] if v)
    report_html = "".join(render_report(report, idx) for idx, report in enumerate(reports, 1))
    latest_summary = (latest or {}).get("summary") or "No compatibility summary has been recorded yet."
    return f'''<!doctype html><html lang="en" data-theme="dark"><head>
<meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><meta name="theme-color" content="#070910">
<meta name="description" content="{esc(title)} compatibility on Bachata S4: {esc(latest_summary)}"><link rel="canonical" href="{esc(canonical)}">
<meta property="og:title" content="{esc(title)} — Bachata S4 compatibility"><meta property="og:description" content="{esc(latest_summary)}"><meta property="og:url" content="{esc(canonical)}"><meta property="og:image" content="{esc(base_url.rstrip('/') + hero_img)}">
<title>{esc(title)} — Bachata S4 Compatibility</title><link rel="stylesheet" href="/assets/css/styles.css"><script defer src="/assets/js/site.js"></script></head>
<body data-page="compatibility"><div data-site-header></div><main id="main" class="game-page"><div class="container">
<nav class="breadcrumbs" aria-label="Breadcrumb"><a href="/">Home</a><span>›</span><a href="/compatibility.html">Compatibility</a><span>›</span><span>{esc(cusa)}</span></nav>
<section class="game-hero"><div class="game-hero-media"><img src="{esc(hero_img)}" alt="{esc(title)} compatibility screenshot"></div><div class="game-hero-copy"><span class="eyebrow">Game compatibility</span><h1>{esc(title)}</h1><p>{esc(latest_summary)}</p><div class="game-meta-row"><span>{esc(meta)}</span><span>{len(reports)} {'report' if len(reports)==1 else 'reports'}</span><span class="status-badge {esc(status)}" style="position:static">{esc(STATUS_LABEL[status])}</span></div></div></section>
<div class="section-heading"><div><span class="eyebrow">Report history</span><h2>All recorded tests</h2></div><p>Newest test first</p></div>
<section class="report-stack">{report_html or '<div class="empty-state"><h3>No reports recorded</h3></div>'}</section>
</div></main><div data-site-footer></div></body></html>'''


def build(source: Path, site: Path, output: Path, base_url: str) -> None:
    if not source.is_dir():
        raise SystemExit(f"Compatibility source directory does not exist: {source}")
    if not site.is_dir():
        raise SystemExit(f"Site source directory does not exist: {site}")
    if output.exists():
        shutil.rmtree(output)
    shutil.copytree(site, output)
    (output / "data" / "games").mkdir(parents=True, exist_ok=True)
    (output / "games").mkdir(parents=True, exist_ok=True)

    games_index = []
    status_counts = Counter()
    devices = set()
    report_count = 0
    warnings = []

    for game_path in sorted((source / "games").glob("CUSA*/game.json")):
        try:
            raw_game = load_json(game_path)
        except Exception as exc:
            warnings.append(f"Skipping {game_path}: {exc}")
            continue
        cusa = str(raw_game.get("cusaId") or game_path.parent.name).upper()
        game = {
            "schemaVersion": raw_game.get("schemaVersion", 1),
            "cusaId": cusa,
            "title": raw_game.get("title") or cusa,
            "region": raw_game.get("region", ""),
            "publisher": raw_game.get("publisher", ""),
        }
        reports = []
        for report_path in sorted((game_path.parent / "reports").glob("*.json")):
            try:
                raw_report = load_json(report_path)
                if str(raw_report.get("cusaId") or cusa).upper() != cusa:
                    warnings.append(f"Skipping mismatched report {report_path}")
                    continue
                reports.append(transform_report(source, output, raw_report))
            except Exception as exc:
                warnings.append(f"Skipping {report_path}: {exc}")
        reports.sort(key=lambda r: str(r.get("testedAt") or ""), reverse=True)
        summaries = [report_summary(r) for r in reports]
        best = min(reports, key=lambda r: STATUS_ORDER.get(normalize_status(r.get("status")), 5)) if reports else None
        latest = reports[0] if reports else None
        game_devices = {r.get("device", {}).get("label") for r in reports if r.get("device", {}).get("label")}
        devices.update(game_devices)
        report_count += len(reports)
        if best:
            status_counts[normalize_status(best.get("status"))] += 1
        entry = {
            **game,
            "reportCount": len(reports),
            "deviceCount": len(game_devices),
            "bestStatus": normalize_status((best or {}).get("status")),
            "latestStatus": normalize_status((latest or {}).get("status")),
            "latestTestedAt": (latest or {}).get("testedAt", ""),
            "latestRelease": (latest or {}).get("release", {}).get("tag", ""),
            "thumbnail": summaries[0]["thumbnail"] if summaries else "/assets/placeholder.svg",
            "reports": summaries,
        }
        games_index.append(entry)
        write_json(output / "data" / "games" / f"{cusa}.json", {"schemaVersion": 1, "game": game, "reports": reports})
        game_dir = output / "games" / cusa
        game_dir.mkdir(parents=True, exist_ok=True)
        (game_dir / "index.html").write_text(render_game_page(base_url, game, reports), encoding="utf-8")

    games_index.sort(key=lambda g: (g.get("title") or g["cusaId"]).lower())
    now = datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")
    index = {
        "schemaVersion": 4,
        "generatedAt": now,
        "project": {"name": "Bachata S4", "repository": "https://github.com/JICA98/Bachata-S4", "dataRepository": "https://github.com/JICA98/Bachata-S4-Compatibility", "platform": "Android"},
        "stats": {"games": len(games_index), "reports": report_count, "devices": len(devices), **{s: status_counts[s] for s in STATUS_ORDER if s != "unknown"}},
        "games": games_index,
    }
    write_json(output / "data" / "site-index.json", index)

    static_paths = ["/", "/compatibility.html", "/updates.html", "/methodology.html", "/about.html", "/guide.html", "/faq.html", "/contact.html", "/privacy.html", "/terms.html"]
    urls = [base_url.rstrip("/") + p for p in static_paths]
    urls += [f"{base_url.rstrip('/')}/games/{quote(g['cusaId'])}/" for g in games_index]
    sitemap = '<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n' + "\n".join(f"  <url><loc>{esc(url)}</loc></url>" for url in urls) + "\n</urlset>\n"
    (output / "sitemap.xml").write_text(sitemap, encoding="utf-8")
    (output / "robots.txt").write_text(f"User-agent: *\nAllow: /\nSitemap: {base_url.rstrip('/')}/sitemap.xml\n", encoding="utf-8")
    (output / "build-info.json").write_text(json.dumps({"generatedAt": now, "games": len(games_index), "reports": report_count, "warnings": warnings}, indent=2) + "\n", encoding="utf-8")
    print(f"Built {len(games_index)} game pages and {report_count} reports into {output}")
    if warnings:
        print(f"Warnings: {len(warnings)}")
        for warning in warnings[:20]:
            print(f"- {warning}")


def main() -> None:
    parser = argparse.ArgumentParser(description="Build the Bachata S4 static compatibility website without release-index or discussion dependencies.")
    parser.add_argument("--source", type=Path, required=True, help="Path to Bachata-S4-Compatibility checkout")
    parser.add_argument("--site", type=Path, default=Path("compatibility-site"), help="Static frontend source directory")
    parser.add_argument("--output", type=Path, required=True, help="Generated site output directory")
    parser.add_argument("--base-url", default="https://bachatas4.games")
    args = parser.parse_args()
    build(args.source.resolve(), args.site.resolve(), args.output.resolve(), args.base_url)


if __name__ == "__main__":
    main()
