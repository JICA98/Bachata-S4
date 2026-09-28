#!/usr/bin/env python3
from __future__ import annotations

import argparse
import html
import json
import shutil
import urllib.request
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import quote

STATUS_ORDER = {"playable": 0, "ingame": 1, "menus": 2, "boots": 3, "nothing": 4, "unknown": 5}
STATUS_LABEL = {"playable": "Playable", "ingame": "Ingame", "menus": "Menus", "boots": "Boots", "nothing": "Nothing", "unknown": "Unknown"}
RAW_BASE = "https://raw.githubusercontent.com/JICA98/Bachata-S4-Compatibility/main/"
# The Android app's compatibility feed. Scores on the site come from the same feed so both show identical numbers.
APP_FEED_BASE = "https://raw.githubusercontent.com/JICA98/Bachata-S4-Compatibility/app-feed/"
AD_NATIVE = '<div class="ad-native-section container"><script async="async" data-cfasync="false" src="https://pl31216953.profitableratecpmnetwork.com/7f163c7ce69fdf82476c891df00fdf82/invoke.js"></script><div id="container-7f163c7ce69fdf82476c891df00fdf82"></div></div>'


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


def load_app_feed(base: str | None) -> tuple[dict, dict]:
    """Returns ({cusa: game feed}, {socId: soc entry}); empty when the feed is unavailable."""
    if not base or base == "none":
        return {}, {}

    def fetch(rel: str):
        if base.startswith("http"):
            with urllib.request.urlopen(base.rstrip("/") + "/" + rel, timeout=15) as response:
                return json.load(response)
        return load_json(Path(base) / rel)

    try:
        index = fetch("index.json")
        socs = fetch("socs.json").get("socs", {})
        games = {}
        for entry in index.get("games", []):
            cusa = str(entry.get("cusaId", ""))
            if cusa.startswith("CUSA"):
                games[cusa] = fetch(f"games/{cusa}.json")
        return games, socs
    except Exception as exc:
        print(f"App feed unavailable, building without scores: {exc}")
        return {}, {}


def release_label(tag) -> str:
    tag = str(tag or "")
    return f"v{tag}" if tag[:1].isdigit() else tag


def view(report: dict) -> dict:
    """One shape for legacy (v1) and app-captured (v2) reports."""
    device = report.get("device", {}) or {}
    perf = report.get("performance", {}) or {}
    result = report.get("result", {}) or {}
    game = report.get("game", {}) or {}
    provenance = report.get("provenance", {}) or {}
    model = " ".join(str(v) for v in [device.get("manufacturer"), device.get("model")] if v)
    tag = (report.get("release", {}) or {}).get("tag") or ""
    if tag in ("", "unreleased") and provenance.get("appBuild"):
        tag_label = f"build {provenance.get('appBuild')}"
    else:
        tag_label = release_label(tag) or "Unknown release"
    return {
        "device": device.get("label") or model or "Unknown device",
        "soc": device.get("socName") or device.get("soc") or device.get("socId") or "",
        "gpu": device.get("gpu", ""),
        "android": device.get("androidVersion", ""),
        "summary": result.get("summary") or report.get("summary") or "",
        "notes": result.get("notes") if "notes" in result else report.get("notes", ""),
        "fps": perf.get("nativeAverageFps", perf.get("averageFps")),
        "low": perf.get("nativeOnePercentLowFps", perf.get("minimumFps")),
        "duration": perf.get("testDurationSeconds"),
        "pacing": perf.get("framePacing"),
        "gameVersion": game.get("version") or report.get("gameVersion") or "",
        "tag": tag,
        "tagLabel": tag_label,
        "contributor": (report.get("contributor", {}) or {}).get("displayName") or (report.get("contributor", {}) or {}).get("publicId") or report.get("tester") or "",
    }


def aggregate_view(agg: dict | None) -> dict | None:
    if not agg or agg.get("score") is None:
        return None
    return {
        "score": agg.get("score"),
        "status": normalize_status(agg.get("status")),
        "confidence": agg.get("confidence", ""),
        "reportCount": agg.get("reportCount", 0),
        "testerCount": agg.get("testerCount", 0),
        "deviceCount": agg.get("deviceCount", 0),
        "release": release_label(agg.get("releaseTag")),
        "current": bool(agg.get("hasCurrentReports")),
        "fps": (agg.get("performance") or {}).get("average"),
    }


def report_summary(report: dict) -> dict:
    screenshots = report.get("evidence", {}).get("screenshots", [])
    driver = report.get("driver", {})
    v = view(report)
    return {
        "reportId": report.get("reportId", ""),
        "status": normalize_status(report.get("status")),
        "testedAt": report.get("testedAt", ""),
        "gameVersion": v["gameVersion"],
        "releaseTag": v["tagLabel"],
        "releaseCommit": report.get("release", {}).get("commit", ""),
        "summary": v["summary"],
        "device": {
            "label": v["device"],
            "soc": v["soc"],
            "gpu": v["gpu"],
            "androidVersion": v["android"],
        },
        "driver": {**driver, "display": driver_display(driver)},
        "performance": {"averageFps": v["fps"]},
        # The last screenshot is normally gameplay (the first is often a splash), as in the app feed.
        "thumbnail": screenshots[-1].get("url") if screenshots else "/assets/placeholder.svg",
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


def fps_short(value) -> str | None:
    try:
        number = float(value)
    except (TypeError, ValueError):
        return None
    return f"{number:.1f}" if number >= 10 else f"{number:.2f}"


def duration_short(seconds) -> str | None:
    try:
        seconds = int(seconds)
    except (TypeError, ValueError):
        return None
    return f"{seconds // 60}m {seconds % 60}s" if seconds >= 60 else f"{seconds}s"


def status_pill(status: str) -> str:
    status = normalize_status(status)
    return f'<span class="status-pill {esc(status)}">{esc(STATUS_LABEL[status])}</span>'


def score_ring(score, status: str, caption: str = "", size: str = "") -> str:
    """Circular gauge matching the app; the arc is animated by site.js from --score."""
    status = normalize_status(status)
    value = "—" if score is None else str(int(score))
    pct = 0 if score is None else max(0, min(100, int(score)))
    cap = f'<small>{esc(caption)}</small>' if caption else ""
    return (f'<div class="score-ring {esc(size)} {esc(status)}" style="--score:{pct}" role="img" '
            f'aria-label="Compatibility score {esc(value)} of 100"><svg viewBox="0 0 36 36" aria-hidden="true">'
            f'<circle class="track" cx="18" cy="18" r="15.9"/><circle class="arc" cx="18" cy="18" r="15.9" pathLength="100"/></svg>'
            f'<span><strong>{esc(value)}</strong>{cap}</span></div>')


def chip(value, label) -> str:
    return f'<div class="stat-chip"><strong>{esc(value)}</strong><small>{esc(label)}</small></div>' if value else ""


def render_report(report: dict, idx: int) -> str:
    status = normalize_status(report.get("status"))
    v = view(report)
    driver = report.get("driver", {})
    screenshots = report.get("evidence", {}).get("screenshots", [])
    logs = report.get("evidence", {}).get("logs", [])
    release = report.get("release", {}) or {}
    shots = "".join(
        f'<a class="shot" href="{esc(item.get("url") or "/assets/placeholder.svg")}" data-caption="{esc(item.get("caption") or "")}">'
        f'<img loading="lazy" src="{esc(item.get("url") or "/assets/placeholder.svg")}" alt="{esc(item.get("caption") or "Compatibility screenshot")}"></a>'
        for item in screenshots
    )
    log_links = "".join(
        f'<a class="pill-button" href="{esc(item.get("url"))}" target="_blank" rel="noreferrer">{esc(item.get("label") or "Open log")} ↗</a>'
        for item in logs if item.get("url")
    )
    chips = "".join([
        chip(fps_short(v["fps"]), "avg FPS"),
        chip(fps_short(v["low"]), "1% low"),
        chip(duration_short(v["duration"]), "tested"),
    ])
    details = "".join([
        spec("Game version", v["gameVersion"]),
        spec("GPU", v["gpu"]),
        spec("Android", v["android"]),
        spec("Frame pacing", (v["pacing"] or "").replace("-", " ") or None),
        spec("Build", release.get("commit")),
        spec("Report", report.get("reportId")),
    ])
    meta = " · ".join(x for x in [v["tagLabel"], fmt_date(report.get("testedAt", "")), v["contributor"]] if x)
    notes = v["notes"] or ""
    return f'''
    <article class="report-card {esc(status)}" id="report-{esc(report.get('reportId') or idx)}">
      <header class="report-head">
        <div><h3>{esc(v["device"])}</h3><p>{esc(" · ".join(x for x in [v["soc"], driver_display(driver)] if x))}</p></div>
        {status_pill(status)}
      </header>
      {f'<div class="shot-strip">{shots}</div>' if shots else ''}
      {f'<div class="chip-row">{chips}</div>' if chips else ''}
      <p class="report-summary">{esc(v["summary"] or 'No summary recorded.')}</p>
      <p class="report-meta">{esc(meta)}</p>
      <details class="report-details"><summary>Report details</summary>
        <div class="spec-grid">{details}</div>
        {f'<p class="report-notes">{esc(notes)}</p>' if notes else ''}
        {f'<div class="log-links">{log_links}</div>' if log_links else ''}
      </details>
    </article>'''


def score_card(title: str, agg: dict | None, empty: str) -> str:
    if not agg:
        return f'<article class="score-card glass">{score_ring(None, "unknown", size="md")}<div><h3>{esc(title)}</h3><p class="muted">{esc(empty)}</p></div></article>'
    lines = [f'<p class="status-text {esc(agg["status"])}">{esc(STATUS_LABEL[agg["status"]])} · {esc(agg["confidence"])} confidence</p>',
             f'<p class="muted small">{agg["reportCount"]} reports · {agg["testerCount"]} testers · {agg["deviceCount"]} devices</p>']
    if agg.get("fps") is not None:
        lines.append(f'<p class="muted small">Native FPS avg {esc(fps_short(agg["fps"]))}</p>')
    if not agg["current"]:
        lines.append(f'<p class="warn small">Scored on {esc(agg["release"])}; no reports for the current release yet.</p>')
    return f'<article class="score-card glass">{score_ring(agg["score"], agg["status"], size="md")}<div><h3>{esc(title)}</h3>{"".join(lines)}</div></article>'


def render_game_page(base_url: str, game: dict, reports: list[dict], feed: dict | None = None, soc_names: dict | None = None) -> str:
    feed = feed or {}
    soc_names = soc_names or {}
    general = aggregate_view(feed.get("general"))
    latest = reports[0] if reports else None
    status = general["status"] if general else normalize_status((latest or {}).get("status"))
    shots_latest = next((r.get("evidence", {}).get("screenshots", []) for r in reports if r.get("evidence", {}).get("screenshots")), [])
    hero_img = shots_latest[-1].get("url") if shots_latest else "/assets/placeholder.svg"
    # First screenshots are often splash screens; prefer later (gameplay) shots for the cover too.
    cover_img = shots_latest[-2 if len(shots_latest) > 1 else -1].get("url") if shots_latest else "/assets/placeholder.svg"
    cusa = game.get("cusaId") or "Unknown"
    title = game.get("title") or cusa
    canonical = f"{base_url.rstrip('/')}/games/{quote(str(cusa))}/"
    meta = " · ".join(str(v) for v in [cusa, game.get("publisher"), game.get("region")] if v)
    report_html = "".join(render_report(report, idx) for idx, report in enumerate(reports, 1))
    latest_summary = view(latest)["summary"] if latest else ""
    latest_summary = latest_summary or "No compatibility summary has been recorded yet."
    release_note = ""
    if general:
        release_note = f'<span class="muted small">{"current release" if general["current"] else "last scored on"} {esc(general["release"])}</span>'
    soc_cards = "".join(
        score_card(soc_names.get(soc_id, {}).get("name") or soc_id, aggregate_view(agg), "No scored reports")
        for soc_id, agg in sorted((feed.get("socs") or {}).items())
        if aggregate_view(agg)
    )
    return f'''<!doctype html><html lang="en" data-theme="dark"><head>
<meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><meta name="theme-color" content="#04060b">
<meta name="description" content="{esc(title)} compatibility on Bachata S4: {esc(latest_summary)}"><link rel="canonical" href="{esc(canonical)}">
<meta property="og:title" content="{esc(title)} — Bachata S4 compatibility"><meta property="og:description" content="{esc(latest_summary)}"><meta property="og:url" content="{esc(canonical)}"><meta property="og:image" content="{esc(base_url.rstrip('/') + hero_img)}">
<title>{esc(title)} — Bachata S4 Compatibility</title><link rel="icon" type="image/png" href="/assets/bachata-s4-logo.png"><link rel="apple-touch-icon" href="/assets/bachata-s4-logo.png"><link rel="stylesheet" href="/assets/css/styles.css"><script defer src="/assets/js/site.js"></script></head>
<body data-page="compatibility"><div data-site-header></div><main id="main" class="game-page"><div class="container">
<nav class="breadcrumbs" aria-label="Breadcrumb"><a href="/">Home</a><span>›</span><a href="/compatibility.html">Compatibility</a><span>›</span><span>{esc(cusa)}</span></nav>
<section class="game-hero">
  <img class="game-hero-bg" src="{esc(hero_img)}" alt="">
  <div class="game-hero-row">
    <img class="game-cover" src="{esc(cover_img)}" alt="{esc(title)} screenshot">
    <div class="game-hero-copy"><h1>{esc(title)}</h1><p class="muted">{esc(meta)}</p><div class="pill-row">{status_pill(status)}{release_note}</div></div>
    {score_ring(general["score"] if general else None, status, "overall", "lg")}
  </div>
</section>
<section class="score-grid">{score_card("All devices", general, "No scored reports yet.")}{soc_cards}</section>
<div class="section-heading"><h2>Reports <span class="muted">({len(reports)})</span></h2><p>Newest first</p></div>
<section class="report-stack">{report_html or '<div class="empty-state"><h3>No reports recorded</h3></div>'}</section>
</div></main>{AD_NATIVE}<div data-site-footer></div></body></html>'''


def build(source: Path, site: Path, output: Path, base_url: str, app_feed: str | None = APP_FEED_BASE) -> None:
    if not source.is_dir():
        raise SystemExit(f"Compatibility source directory does not exist: {source}")
    if not site.is_dir():
        raise SystemExit(f"Site source directory does not exist: {site}")
    if output.exists():
        shutil.rmtree(output)
    shutil.copytree(site, output)
    (output / "data" / "games").mkdir(parents=True, exist_ok=True)
    (output / "games").mkdir(parents=True, exist_ok=True)

    feeds, soc_names = load_app_feed(app_feed)
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
        game_devices = {view(r)["device"] for r in reports if view(r)["device"] != "Unknown device"}
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
            "compatibility": aggregate_view((feeds.get(cusa) or {}).get("general")),
            "socCount": len((feeds.get(cusa) or {}).get("socs") or {}),
            "reports": summaries,
        }
        games_index.append(entry)
        write_json(output / "data" / "games" / f"{cusa}.json", {"schemaVersion": 1, "game": game, "reports": reports})
        game_dir = output / "games" / cusa
        game_dir.mkdir(parents=True, exist_ok=True)
        (game_dir / "index.html").write_text(render_game_page(base_url, game, reports, feeds.get(cusa), soc_names), encoding="utf-8")

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
    parser.add_argument("--app-feed", default=APP_FEED_BASE, help="App compatibility feed URL or directory for scores; 'none' disables")
    args = parser.parse_args()
    build(args.source.resolve(), args.site.resolve(), args.output.resolve(), args.base_url, args.app_feed)


if __name__ == "__main__":
    main()
