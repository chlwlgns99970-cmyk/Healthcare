"""Summarize saved adb runner output without executing or modifying an app."""
from pathlib import Path
import json
import re
import sys

path = Path(sys.argv[1])
text = path.read_text(encoding="utf-8-sig")
tests = []
pattern = r"INSTRUMENTATION_STATUS: class=([^\r\n]+)(.*?)(?:INSTRUMENTATION_STATUS_CODE: (-?\d+))"
for match in re.finditer(pattern, text, re.S):
    body = match.group(2)
    name = re.search(r"INSTRUMENTATION_STATUS: test=([^\r\n]+)", body)
    code = int(match.group(3))
    if not name or code == 1:
        continue
    stack = body.split("INSTRUMENTATION_STATUS: stack=", 1)
    tests.append(dict(className=match.group(1), name=name.group(1), code=code,
                      trace=stack[1].split("INSTRUMENTATION_STATUS: stream=", 1)[0].strip()
                      if len(stack) == 2 else ""))
expected = max(map(int, re.findall(r"INSTRUMENTATION_STATUS: numtests=(\d+)", text)), default=0)
result = dict(expected=expected, completed=len(tests), passed=sum(x["code"] == 0 for x in tests),
              failed=sum(x["code"] in (-1, -2) for x in tests),
              skipped=sum(x["code"] in (-3, -4) for x in tests), tests=tests)
result["status"] = "PASS" if (expected > 0 and len(tests) == expected and
    all(x["code"] == 0 for x in tests) and re.search(r"OK \(\d+ tests?\)", text)) else "FAIL_OR_INCOMPLETE"
path.with_suffix(".json").write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(json.dumps({k: v for k, v in result.items() if k != "tests"}))
for test in tests:
    if test["code"] != 0:
        print(test["className"].split(".")[-1], test["name"], test["trace"][:400])
