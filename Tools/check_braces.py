"""Crude brace/paren balance check for Java sources.

Strips comments and string/char literals, then counts delimiters. Catches the
class of mistake that costs a 25-minute release build (see AGENTS.md).
"""

import re
import sys

COMMENT_OR_LITERAL = re.compile(
    r"""
    //[^\n]*                      # line comment
  | /\*.*?\*/                    # block comment
  | "(?:\\.|[^"\\])*"            # string literal
  | '(?:\\.|[^'\\])*'            # char literal
    """,
    re.VERBOSE | re.DOTALL,
)


def strip(src: str) -> str:
    return COMMENT_OR_LITERAL.sub(lambda m: " " * len(m.group(0)), src)


def main(paths):
    bad = False
    for path in paths:
        with open(path, encoding="utf-8") as fh:
            src = strip(fh.read())
        braces = src.count("{") - src.count("}")
        parens = src.count("(") - src.count(")")
        ok = braces == 0 and parens == 0
        bad |= not ok
        print(f"{'OK  ' if ok else 'FAIL'} {path} braces={braces:+d} parens={parens:+d}")
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))