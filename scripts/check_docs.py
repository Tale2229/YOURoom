"""Check local documentation links and static HTML/CSS resources without network access.

Run from any working directory: python -X utf8 scripts/check_docs.py
JavaScript-generated URLs and external links are outside this check's scope.
"""
from html import unescape
from html.parser import HTMLParser
from pathlib import Path
import re
from urllib.parse import unquote, urlsplit

ROOT = Path(__file__).resolve().parents[1]


class Resources(HTMLParser):
    def __init__(self):
        super().__init__()
        self.urls = []

    def handle_starttag(self, tag, attrs):
        # HTMLParser treats script contents as data, avoiding partial href/src
        # strings in generated markup (e.g. src="' + expression + '").
        for name, value in attrs:
            if name in ("href", "src", "poster") and value:
                self.urls.append(value)


def references(path):
    text = path.read_text(encoding="utf-8-sig")
    if path.suffix == ".md":
        text = re.sub(r"```.*?```", "", text, flags=re.S)
        for match in re.finditer(r"\]\((<[^>]+>|[^\n)]+)\)", text):
            ref = match[1].strip()
            if ref.startswith("<"):
                ref = ref[1:-1]
            else:
                ref = re.split(r'\s+[\"\x27]', ref, maxsplit=1)[0]
            yield ref
    elif path.suffix == ".html":
        parser = Resources()
        parser.feed(text)
        yield from parser.urls
    if path.suffix in (".html", ".css"):
        for match in re.finditer(r"url\(\s*['\"]?([^'\")]+)['\"]?\s*\)", text):
            yield match[1].strip()


def main():
    paths = [ROOT / "README.md", ROOT / "CHANGELOG.md"]
    paths += sorted(p for p in (ROOT / "docs").rglob("*")
                    if p.suffix in (".md", ".html", ".css") and p.is_file())
    checked = 0
    failures = []
    for path in paths:
        for ref in references(path):
            if any(marker in ref for marker in ("${", "{{")):
                continue
            parts = urlsplit(unescape(ref))
            if parts.scheme or parts.netloc or not parts.path:
                continue
            relative = unquote(parts.path)
            target = ((ROOT if relative.startswith("/") else path.parent)
                      / relative.lstrip("/")).resolve()
            checked += 1
            if not target.is_relative_to(ROOT) or not target.exists():
                failures.append(f"{path.relative_to(ROOT).as_posix()} -> {ref}")
    if failures:
        print("Broken local references:")
        print("\n".join(failures))
        raise SystemExit(1)
    print(f"OK: {checked} local links/resources across {len(paths)} documents.")


if __name__ == "__main__":
    main()
