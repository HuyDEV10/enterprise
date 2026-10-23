"""Shared target/context contract for training and serving (no model imports)."""
from __future__ import annotations

import re
import unicodedata
from dataclasses import dataclass

TARGET_START = "<target>"
TARGET_END = "</target>"
LABELS = ["NEGATIVE", "NEUTRAL", "POSITIVE"]
CONTRACT_VERSION = "entity-pair-markers-v2"


class SentimentInputError(ValueError):
    def __init__(self, code: str, message: str):
        self.code = code
        super().__init__(message)


def normalize_text(value: str) -> str:
    return " ".join(unicodedata.normalize("NFC", value).split())


def target_pattern(entity: str) -> re.Pattern:
    # A ticker/name must not match part of an unrelated word (e.g. CAT in education).
    return re.compile(r"(?<!\w)" + re.escape(entity) + r"(?!\w)", re.IGNORECASE)


def infer_language(text: str) -> str:
    """A routing hint for en/vi calibration, not a general language classifier."""
    vietnamese = "ăâđêôơưĂÂĐÊÔƠƯạảấầẩẫậắằẳẵặẹẻẽếềểễệỉĩịọỏốồổỗộớờởỡợụủũứừửữựỳỵỷỹ"
    return "vi" if any(character in vietnamese for character in text) else "en"


@dataclass(frozen=True)
class TargetContext:
    entity: str
    context: str
    truncated: bool


def prepare_target_context(entity: str, text: str, max_words: int = 80) -> TargetContext:
    entity, text = normalize_text(entity), normalize_text(text)
    if not entity or not text:
        raise SentimentInputError("BLANK_INPUT", "entity and text must not be blank")
    if len(entity) > 300 or len(entity.split()) > 32:
        raise SentimentInputError("ENTITY_TOO_LONG", "Use the company name or a short entity mention")
    if TARGET_START in text or TARGET_END in text or TARGET_START in entity or TARGET_END in entity:
        raise SentimentInputError("RESERVED_MARKER", "Input contains reserved target markers")
    pattern = target_pattern(entity)
    first = pattern.search(text)
    if first is None:
        raise SentimentInputError("TARGET_NOT_MENTIONED", "The target entity must be explicitly mentioned in the supplied text")
    words = list(re.finditer(r"\S+", text))
    position = next(index for index, word in enumerate(words) if word.end() > first.start())
    # Keep the target near the beginning so tokenizer truncation preserves it in vi too.
    left = max(0, position - min(20, max_words // 3))
    right = min(len(words), left + max_words)
    while right < len(words) and words[right - 1].end() < first.end():
        right += 1
    context = text[words[left].start():words[right - 1].end()]
    context = pattern.sub(lambda match: f"{TARGET_START} {match.group(0)} {TARGET_END}", context)
    return TargetContext(entity, context, left > 0 or right < len(words))
