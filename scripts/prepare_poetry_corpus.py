"""Build the offline quote corpus from a pinned, attributed Jingmo data release.

Download the three files listed in SOURCES into --source-dir first. This script
does not crawl a website or query a public random-sentence service.
"""
from __future__ import annotations

import argparse
from collections import Counter, deque
import gzip
import hashlib
import json
from pathlib import Path
import re
import unicodedata

REVISION = "dc3ed0bc8877512d8ac0b2b93cf6b2420237ec72"
BASE = f"https://raw.githubusercontent.com/hefengbao/jingmo-data/{REVISION}"
QUOTES = "classicalliterature_sentence_v3_1.json"
POEMS = "classicalliterature_classicpoem_v3_1.json"
SOURCES = {QUOTES: f"{BASE}/api/{QUOTES}", POEMS: f"{BASE}/api/{POEMS}", "LICENSE.txt": f"{BASE}/LICENSE"}
INPUT_SHA256 = {
    QUOTES: "5944c8b68b799b04dbad53f92bc8a129d9b332c8150cd1d33b8c35b341a5ee7b",
    POEMS: "d0300c9268b8387298ef4b3f98fda4ac2b148a393a0e6f0b39ed4e81692bc4db",
    "LICENSE.txt": "a19557c715bfbec568bde1f1f7c5cc1e9e5828650d1a4e8534220abccefd811a",
}
ROOT = Path(__file__).resolve().parents[1]


def normalized(text: str) -> str:
    return "".join(c for c in unicodedata.normalize("NFKC", text) if c.isalnum())


def clean(text: str) -> str:
    return re.sub(r"\s+", " ", text).strip()


def build(source_dir: Path, root: Path = ROOT) -> dict:
    for name, expected in INPUT_SHA256.items():
        if hashlib.sha256((source_dir / name).read_bytes()).hexdigest() != expected:
            raise ValueError(f"Unexpected source checksum: {name}")
    quotes = json.loads((source_dir / QUOTES).read_text(encoding="utf-8"))["data"]
    poems = json.loads((source_dir / POEMS).read_text(encoding="utf-8"))["data"]
    license_text = (source_dir / "LICENSE.txt").read_text(encoding="utf-8")
    if "MIT License" not in license_text or "贺丰宝" not in license_text:
        raise ValueError("Unexpected upstream license")
    selected, seen, rejected = [], set(), Counter()

    def add(body: str, source: str, reference: str) -> bool:
        body, source = clean(body), clean(source)
        # Two source quotes contain embedded numbered footnote markers. They are
        # editorial annotations, not part of the quoted verse or a distinct quote.
        body = re.sub(r"[\u2460-\u24ff\u3251-\u325f\u32b1-\u32bf]", "", body)
        key = normalized(body)
        if "毛泽东" in source:
            rejected["outside_classical_scope"] += 1
            return False
        if not key or not re.fullmatch(r"[^《》]*《[^《》]+》", source) or "\ufffd" in body + source:
            rejected["invalid"] += 1
            return False
        if key in seen:
            rejected["duplicate"] += 1
            return False
        seen.add(key)
        identifier = "verse-" + hashlib.sha256(key.encode("utf-8")).hexdigest()[:16]
        selected.append((identifier, body, source, reference))
        return True

    for quote in quotes:
        add(quote["content"], quote["from"], f"sentence:{quote['id']}")
    quote_count = len(selected)
    quote_rejections = dict(rejected)
    rejected.clear()

    # Supplement after deduplication with intact sentences from the Tang/Song
    # classics. Preserve wording and punctuation; do not generate paraphrases,
    # author names, interpretations, or claim every excerpt is a famous quote.
    candidates = []
    for poem in poems:
        if poem["dynasty"] not in ("唐", "宋"):
            continue
        title = poem["title"] + ("·" + poem["subtitle"] if poem.get("subtitle") else "")
        source = f"{poem['writer']}《{title}》"
        text = poem["content"].replace("\\n", "\n")
        sentences = []
        for index, match in enumerate(re.finditer(r"[^。！？!?]+[。！？!?]", text)):
            sentence = match.group().strip()
            if (8 <= len(normalized(sentence)) <= 40 and "，" in sentence
                    and not re.search(r"[\n\r（）()\[\]0-9a-zA-Z]", sentence)):
                sentences.append((sentence, source, f"poem:{poem['id']}:{index}"))
        if sentences:
            candidates.append(deque(sentences))
    # Round-robin works so one long poem cannot fill the supplement by itself.
    while len(selected) < 10_000 and candidates:
        remaining = []
        for queue in candidates:
            if len(selected) >= 10_000:
                break
            add(*queue.popleft())
            if queue:
                remaining.append(queue)
        candidates = remaining
    if len(selected) != 10_000 or len({row[0] for row in selected}) != 10_000:
        raise ValueError(f"Expected exactly 10000 distinct quotes, got {len(selected)}")
    asset = root / "app/src/main/assets/poetry/quotes.tsv"
    notices = root / "app/src/main/assets/licenses/jingmo_poetry"
    docs = root / "docs/content/poetry-corpus"
    for directory in (asset.parent, notices, docs):
        directory.mkdir(parents=True, exist_ok=True)
    raw = "".join("\t".join(row[:3]) + "\n" for row in selected).encode("utf-8")
    asset.write_bytes(raw)
    (docs / "provenance.tsv").write_text("id\tupstream_reference\n" + "".join(
        f"{row[0]}\t{row[3]}\n" for row in selected), encoding="utf-8", newline="\n")
    (notices / "LICENSE.txt").write_text(license_text, encoding="utf-8", newline="\n")
    (notices / "NOTICE.txt").write_text(
        "YOURoom offline classical quotations\n"
        "Source: hefengbao/jingmo-data (MIT), Copyright (c) 2024 贺丰宝\n"
        f"Revision: {REVISION}\n"
        f"Quotes: {SOURCES[QUOTES]}\nClassics: {SOURCES[POEMS]}\n"
        "Only quotation text and attribution are included. Translations, modern\n"
        "commentaries, author biographies, audio and images are not included.\n"
        "YOURoom normalizes whitespace, removes duplicate text and supplements\n"
        "the quote list with intact sentences from the included Tang/Song classics.\n"
        "These are sourced excerpts, not AI-generated poems. Original data may\n"
        "contain errors; an entry is not certified as manually proofread.\n",
        encoding="utf-8", newline="\n")
    report = {
        "version": 1, "revision": REVISION, "count": len(selected),
        "from_quote_list": quote_count, "from_classics": len(selected) - quote_count,
        "rejected_quote_rows": quote_rejections, "rejected_supplement_rows": dict(rejected),
        "utf8_bytes": len(raw), "gzip_estimate_bytes": len(gzip.compress(raw, mtime=0)),
        "sha256": hashlib.sha256(raw).hexdigest(),
        "inputs": [{"file": name, "url": url, "sha256": hashlib.sha256((source_dir / name).read_bytes()).hexdigest()}
                   for name, url in SOURCES.items()],
    }
    (docs / "manifest.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8", newline="\n")
    return report


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-dir", type=Path, default=ROOT / "artifacts/poetry-source")
    args = parser.parse_args()
    print(json.dumps(build(args.source_dir), ensure_ascii=False, indent=2))
