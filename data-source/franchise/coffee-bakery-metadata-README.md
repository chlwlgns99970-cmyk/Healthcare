# Coffee and bakery menu metadata

Checked on 2026-10-04. Parser: `coffee-bakery-2026-10-04-v2`.

| Official source | Cached parsed menus | Exact existing food IDs | Published allergen labels | Unknown allergen labels | Descriptions naming ingredients |
| --- | ---: | ---: | ---: | ---: | ---: |
| Starbucks | 316 | 73 | 52 | 21 | 70 |
| Tous Les Jours | 442 | 190 | 189 | 1 | 137 |
| Total | 758 | 263 | 241 | 22 | 207 |

The input contains 782 official HTTPS responses. All cached file SHA-256 values,
sizes and retrieval dates were verified. Two Starbucks detail pages returned no
parseable product detail; these failures remain in the audit. No request was
repeated during final generation.

Joins require an exact brand and a normalized menu title. Explicit Starbucks
HOT/ICED variants must also match the official temperature field. The original
K-FIND food ID, source food code and food name remain unchanged. The unmatched
audit retains 441 absent current-menu matches and three ambiguous matches.

All 263 source descriptions are partial ingredient evidence. A fixed dictionary
extracts only literal components from the official description, using word and
Korean particle boundaries and keeping composite names intact. A product name,
flavor-only mention or allergen label does not supply ingredient evidence.
None of these rows is a complete ingredient declaration. For example, `연유`
does not manufacture a `우유` ingredient; `통밀빵` does not manufacture `밀`.

Published allergen cells are parsed separately, including explicit absence
declarations; an empty field stays unknown. Every joined row is marked
`staleCandidate=true` and `CURRENT_OFFICIAL_MENU_RECIPE_VERSION_UNVERIFIED`:
the current official menu declaration does not establish that an older K-FIND
nutrition entry uses the same recipe. No nutrition value was changed, no
publication/recipe date was invented, and no cross-contact statement was
inferred.

Run with the project's Python runtime:

```text
python tools/collect_coffee_bakery_metadata.py --generate --verify-cache
```

This command is offline. Both the output CSV and audit JSON were byte-identical
on a second generation. CSV SHA-256:
`da445e5428ad9086d7aa1d8cd74a0f7b2729939e76fdd8f25fe5acce4d9074ab`.

The focused collector suite has nine tests. The final two tests for literal
component extraction and flavor/name exclusions passed after the earlier seven.
