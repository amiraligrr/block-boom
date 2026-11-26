#!/usr/bin/env python3
"""
Structural PHP syntax checker.

No PHP binary is available in this environment, so this tokenizes PHP the way
the real lexer does (comments, all four string forms, heredoc/nowdoc) and
verifies that delimiters nest correctly and every statement looks terminated.
It is not a full parser, but it reliably catches the structural mistakes that
break a page at runtime: unbalanced braces/parens/brackets, unterminated
strings, unclosed heredocs, and stray `<?php`/close-tag confusion.
"""
import re
import sys
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

PAIRS = {')': '(', ']': '[', '}': '{'}
OPEN = set('([{')


class Err(Exception):
    pass


def check(path):
    src = open(path, 'r', encoding='utf-8', errors='replace').read()
    i, n = 0, len(src)
    line = 1
    stack = []          # (char, line)
    problems = []
    in_php = False
    saw_open = False
    heredocs = []       # active heredoc labels
    prev_significant = ''   # last significant char, for statement-termination hints

    def err(msg, at_line=None):
        problems.append((at_line if at_line is not None else line, msg))

    while i < n:
        c = src[i]

        if c == '\n':
            line += 1
            i += 1
            continue

        if not in_php:
            # Outside PHP: only <?php or <?= can re-enter.
            if src.startswith('<?php', i) or src.startswith('<?=', i):
                in_php = True
                saw_open = True
                i += 5 if src.startswith('<?php', i) else 3
                # require whitespace after <?php
                if i < n and not src[i].isspace() and not src.startswith('?>', i):
                    if src.startswith('<?php', i - 5):
                        err('"<?php" must be followed by whitespace', line)
                continue
            i += 1
            continue

        # Inside an open heredoc: only the terminator matters.
        if heredocs:
            label, indented, started_line = heredocs[-1]
            j = i
            at_line_start = (j == 0 or src[j - 1] == '\n')
            if at_line_start:
                k = j
                if indented:
                    while k < n and src[k] in ' \t':
                        k += 1
                if src.startswith(label, k):
                    after = k + len(label)
                    if after >= n or not (src[after].isalnum() or src[after] == '_'):
                        heredocs.pop()
                        i = after
                        prev_significant = ';'
                        continue
            if c == '\n':
                line += 1
            i += 1
            continue

        # Comments
        if src.startswith('//', i) or (c == '#' and not src.startswith('#[', i)):
            j = src.find('\n', i)
            i = n if j < 0 else j
            continue
        if src.startswith('/*', i):
            j = src.find('*/', i + 2)
            if j < 0:
                err('unterminated /* comment', line)
                break
            line += src.count('\n', i, j)
            i = j + 2
            continue

        # Close tag. `?>` only leaves PHP mode; it does not close any
        # delimiter, so a `function f() { ?> html <?php }` body is legal and
        # must not be reported.
        if src.startswith('?>', i):
            in_php = False
            i += 2
            continue

        # Heredoc / Nowdoc
        if c == '<' and src.startswith('<<<', i):
            m = i + 3
            while m < n and src[m] in ' \t':
                m += 1
            nowdoc = False
            if m < n and src[m] == "'":
                nowdoc = True
                m += 1
            if m < n and src[m] == '"':
                m += 1
            start = m
            while m < n and (src[m].isalnum() or src[m] == '_'):
                m += 1
            label = src[start:m]
            if not label:
                err('malformed heredoc/nowdoc', line)
                i += 3
                continue
            if m < n and src[m] in '\'"':
                m += 1
            # skip to end of line
            eol = src.find('\n', m)
            if eol < 0:
                err('heredoc without body', line)
                break
            indented = bool(re.search(r'[ \t]<<[ \t]*$', src[i:eol]))
            heredocs.append((label, indented, line))
            line += 1
            i = eol + 1
            continue

        # Strings. PHP allows literal newlines inside both quote styles, so a
        # newline is NOT a terminator — only EOF is.
        if c == "'" or c == '"':
            quote = c
            j = i + 1
            closed = False
            while j < n:
                ch = src[j]
                if ch == '\\':
                    if j + 1 < n and src[j + 1] == '\n':
                        line += 1
                    j += 2
                    continue
                if ch == quote:
                    closed = True
                    j += 1
                    break
                if ch == '\n':
                    line += 1
                j += 1
            if not closed:
                err('unterminated %s string' % ('single' if quote == "'" else 'double'), line)
            i = j
            prev_significant = '"'
            continue

        # Delimiters
        if c in OPEN:
            stack.append((c, line))
            i += 1
            continue
        if c in PAIRS:
            if not stack:
                err('unmatched closing %r' % c, line)
            else:
                op, ol = stack.pop()
                if op != PAIRS[c]:
                    err('mismatched %r (line %d) closed by %r' % (op, ol, c), line)
            i += 1
            prev_significant = c
            continue

        if not c.isspace():
            prev_significant = c
        i += 1

    for op, ol in stack:
        err('unclosed %r opened here (EOF reached)' % op, ol)
    for label, ind, hl in heredocs:
        err('unterminated heredoc/nowdoc <<<%s' % label, hl)
    if not saw_open:
        err('no "<?php" opening tag found')

    return problems


def main():
    # Default to the real source trees. Previously this required explicit
    # paths, so running it bare scanned nothing and still exited 0 — a green
    # result that had checked zero files.
    targets = sys.argv[1:] or [os.path.join(ROOT, 'api'), os.path.join(ROOT, 'admin')]
    files = []
    for t in targets:
        if os.path.isdir(t):
            for root, _, names in os.walk(t):
                files += [os.path.join(root, n) for n in sorted(names) if n.endswith('.php')]
        elif os.path.exists(t):
            files.append(t)
        else:
            print('no such path: %s' % t)
            return 1

    if not files:
        print('no PHP files found under: %s' % ', '.join(targets))
        return 1

    bad = 0
    for f in sorted(files):
        probs = check(f)
        if probs:
            bad += 1
            print('FAIL %s' % f)
            for ln, msg in probs:
                print('   line %-5d %s' % (ln, msg))
        else:
            print('ok   %s' % f)
    print('\n%d file(s) checked, %d with problems' % (len(files), bad))
    return 1 if bad else 0


if __name__ == '__main__':
    sys.exit(main())
