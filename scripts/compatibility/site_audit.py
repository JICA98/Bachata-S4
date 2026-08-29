#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import re
import sys
import xml.etree.ElementTree as ET
from html.parser import HTMLParser
from pathlib import Path
from urllib.parse import unquote, urlparse

ADS_LINE = "google.com, pub-8704776489115590, DIRECT, f08c47fec0942fa0"
REQUIRED_FILES = [
    "index.html", "compatibility.html", "updates.html", "methodology.html", "guide.html", "faq.html",
    "about.html", "contact.html", "privacy.html", "terms.html", "games/index.html", "ads.txt", "robots.txt", "sitemap.xml"
]
FORBIDDEN_PUBLIC_PHRASES = ["repository skill", "under construction", "coming soon"]


class PageParser(HTMLParser):
    def __init__(self) -> None:
        super().__init__(convert_charrefs=True)
        self.title = ""
        self._in_title = False
        self._hidden_depth = 0
        self.description = ""
        self.canonical = ""
        self.links: list[str] = []
        self.images_missing_alt: list[str] = []
        self.h1_count = 0
        self.visible_text: list[str] = []
        self.json_ld: list[str] = []
        self._in_json_ld = False
        self._json_chunks: list[str] = []
        self.noindex = False

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        values = {k.lower(): (v or "") for k, v in attrs}
        if tag == "title":
            self._in_title = True
        if tag in {"script", "style", "template", "noscript"}:
            self._hidden_depth += 1
        if tag == "script" and values.get("type", "").lower() == "application/ld+json":
            self._in_json_ld = True
            self._json_chunks = []
        if tag == "meta":
            name = values.get("name", "").lower()
            if name == "description":
                self.description = values.get("content", "").strip()
            if name == "robots" and "noindex" in values.get("content", "").lower():
                self.noindex = True
        if tag == "link" and "canonical" in values.get("rel", "").lower().split():
            self.canonical = values.get("href", "").strip()
        if tag == "a" and values.get("href"):
            self.links.append(values["href"].strip())
        if tag == "img" and "alt" not in values:
            self.images_missing_alt.append(values.get("src", "<unknown>"))
        if tag == "h1":
            self.h1_count += 1

    def handle_endtag(self, tag: str) -> None:
        if tag == "title":
            self._in_title = False
        if tag == "script" and self._in_json_ld:
            self.json_ld.append("".join(self._json_chunks).strip())
            self._in_json_ld = False
            self._json_chunks = []
        if tag in {"script", "style", "template", "noscript"} and self._hidden_depth:
            self._hidden_depth -= 1

    def handle_data(self, data: str) -> None:
        if self._in_title:
            self.title += data
        if self._in_json_ld:
            self._json_chunks.append(data)
        if self._hidden_depth == 0:
            stripped = data.strip()
            if stripped:
                self.visible_text.append(stripped)


def internal_target(current: Path, site: Path, href: str) -> Path | None:
    if not href or href.startswith("#"):
        return None
    parsed = urlparse(href)
    if parsed.scheme or parsed.netloc or href.startswith("mailto:") or href.startswith("tel:"):
        return None
    raw_path = unquote(parsed.path)
    if not raw_path:
        return None
    if raw_path.startswith("/"):
        target = site / raw_path.lstrip("/")
    else:
        target = current.parent / raw_path
    if raw_path.endswith("/"):
        target = target / "index.html"
    return target.resolve()


def audit(site: Path) -> list[str]:
    errors: list[str] = []
    for name in REQUIRED_FILES:
        if not (site / name).exists():
            errors.append(f"Missing required deployment file: {name}")

    ads = site / "ads.txt"
    if ads.exists():
        actual = "\n".join(line.strip() for line in ads.read_text(encoding="utf-8").splitlines() if line.strip())
        if actual != ADS_LINE:
            errors.append(f"ads.txt must contain exactly: {ADS_LINE}")

    titles: dict[str, Path] = {}
    html_paths = sorted(site.rglob("*.html"))
    for path in html_paths:
        source = path.read_text(encoding="utf-8")
        parser = PageParser()
        try:
            parser.feed(source)
        except Exception as exc:  # HTMLParser is tolerant, but fail closed if something unexpected occurs.
            errors.append(f"{path.relative_to(site)}: parser error: {exc}")
            continue
        rel = path.relative_to(site)
        title = parser.title.strip()
        if not title:
            errors.append(f"{rel}: missing <title>")
        elif title in titles:
            errors.append(f"{rel}: duplicate title also used by {titles[title].relative_to(site)}: {title}")
        else:
            titles[title] = path
        if not parser.description:
            errors.append(f"{rel}: missing meta description")
        if path.name != "404.html" and not parser.canonical:
            errors.append(f"{rel}: missing canonical URL")
        if parser.canonical and not parser.canonical.startswith("https://bachatas4.games/"):
            errors.append(f"{rel}: canonical must use https://bachatas4.games/")
        if parser.noindex and path.name != "404.html":
            errors.append(f"{rel}: contains noindex")
        if parser.h1_count != 1:
            errors.append(f"{rel}: expected exactly one h1, found {parser.h1_count}")
        if parser.images_missing_alt:
            errors.append(f"{rel}: image(s) missing alt attribute: {', '.join(parser.images_missing_alt[:4])}")
        lowered = source.lower()
        for phrase in FORBIDDEN_PUBLIC_PHRASES:
            if phrase in lowered:
                errors.append(f"{rel}: public-facing placeholder/internal phrase found: {phrase!r}")
        words = re.findall(r"[A-Za-z0-9][A-Za-z0-9'’-]*", " ".join(parser.visible_text))
        minimum = 160 if rel.parts and rel.parts[0] == "games" and path.name != "index.html" else 70
        if path.name != "404.html" and len(words) < minimum:
            errors.append(f"{rel}: thin visible content ({len(words)} words; expected at least {minimum})")
        for block in parser.json_ld:
            if not block:
                continue
            try:
                json.loads(block)
            except json.JSONDecodeError as exc:
                errors.append(f"{rel}: invalid JSON-LD: {exc}")
        for href in parser.links:
            target = internal_target(path, site, href)
            if target is None:
                continue
            if site.resolve() not in target.parents and target != site.resolve():
                errors.append(f"{rel}: internal link escapes deployment root: {href}")
                continue
            if not target.exists():
                errors.append(f"{rel}: broken internal link: {href}")

    sitemap = site / "sitemap.xml"
    if sitemap.exists():
        try:
            root = ET.parse(sitemap).getroot()
            ns = {"sm": "http://www.sitemaps.org/schemas/sitemap/0.9"}
            locs = {node.text.strip() for node in root.findall("sm:url/sm:loc", ns) if node.text}
            for path in sorted((site / "games").glob("CUSA*.html")):
                expected = f"https://bachatas4.games/games/{path.name}"
                if expected not in locs:
                    errors.append(f"sitemap.xml: missing generated game URL {expected}")
        except ET.ParseError as exc:
            errors.append(f"sitemap.xml: invalid XML: {exc}")

    robots = site / "robots.txt"
    if robots.exists() and "https://bachatas4.games/sitemap.xml" not in robots.read_text(encoding="utf-8"):
        errors.append("robots.txt: missing canonical sitemap URL")
    return errors


def main() -> int:
    parser = argparse.ArgumentParser(description="Audit the assembled Bachata S4 compatibility site")
    parser.add_argument("--site", type=Path, required=True)
    args = parser.parse_args()
    site = args.site.resolve()
    errors = audit(site)
    if errors:
        print("Site audit failed:", file=sys.stderr)
        for error in errors:
            print(f"- {error}", file=sys.stderr)
        return 1
    print(f"Site audit passed: {len(list(site.rglob('*.html')))} HTML page(s) checked.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
