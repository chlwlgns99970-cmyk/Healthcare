"""Resolve contradictory cooking-method titles only with original instructions.

Parenthetical species, regions, and filling names never become dish aliases.
"""
import re


def supported_title_keys(name, context):
    match = re.fullmatch(r'([^()]+)\(([^()]+)\)', name)
    if not match:
        return set()
    leading, alternate = match.groups()
    # Both labels must identify the same named primary ingredient. This rule
    # addresses method conflicts, not taxonomic or ingredient substitutions.
    leading_base = re.sub(r'(볶음|전골)$', '', leading)
    alternate_base = re.sub(r'(볶음|전골)$', '', alternate)
    if not leading_base or leading_base != alternate_base:
        return set()
    stir_fried = bool(re.search(r'팬[^.]*볶', context))
    simmered = bool(re.search(r'전골냄비|육수[^.]*끓|국물[^.]*끓', context))
    if stir_fried and not simmered:
        return {label for label in (leading, alternate) if label.endswith('볶음')}
    return set()
