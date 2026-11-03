#!/usr/bin/env bash
# 개인 정보(이름·개인 메일)가 커밋 작성자나 파일에 들어가지 않게 막는다. 금지 패턴은 저장소에 글자로 남지 않게 base64.
# 사용: pre-commit 훅(스테이지된 파일 + 작성자) · CI(트리 전체 + 범위 안 커밋 작성자)
set -euo pipefail
PAT=$(echo '7KCV7Zi47KSAfO2YuOykgHxXb25IZWV8a2ltd2h8bXlteWlvdmU4ODJ8aG9qdW4oPyFcLmFwcCk=' | base64 -d)
# 예외 하나: 공모전 규칙(APK 파일명 = 팀명_앱이름_release.apk, 개인은 성명)으로 정한 APK 이름만 허용(PM 2026-11-03)
OK=$(echo '7KCV7Zi47KSAX+yPmV9yZWxlYXNlLmFwaw==' | base64 -d)
strip() { sed "s/$OK//g"; }
ALLOW_EMAIL='^(124661562\+mymyiove@users\.noreply\.github\.com|noreply@anthropic\.com|noreply@github\.com)$'
fail=0
mode="${1:-staged}"
if [ "$mode" = "staged" ]; then
  for who in "$(git var GIT_AUTHOR_IDENT)" "$(git var GIT_COMMITTER_IDENT)"; do
    email=$(echo "$who" | sed -E 's/.*<([^>]*)>.*/\1/')
    if ! echo "$email" | grep -Eq "$ALLOW_EMAIL"; then echo "✗ 커밋 작성자 메일이 허용 목록에 없습니다: $email"; echo "  git config user.name mymyiove && git config user.email 124661562+mymyiove@users.noreply.github.com"; fail=1; fi
    if echo "$who" | grep -Pq "$PAT"; then echo "✗ 커밋 작성자 이름에 금지된 이름이 있습니다"; fail=1; fi
  done
  files=$(git diff --cached --name-only --diff-filter=ACMR)
  [ -n "$files" ] && if git diff --cached -U0 | grep '^+' | strip | grep -Pq "$PAT"; then echo "✗ 추가한 내용에 금지된 이름이 있습니다:"; git diff --cached -U0 | grep '^+' | strip | grep -Pn "$PAT" | head -5; fail=1; fi
  for f in $files; do case "$f" in *.apk|*.docx|*.pdf|*.mp4|*.mp3|*.m4a|*.png|*.jpg) if LC_ALL=C grep -aPq "$PAT" "$f" 2>/dev/null; then echo "✗ $f 안에 금지된 이름"; fail=1; fi;; esac; done
  if echo "$files" | strip | grep -Pq "$PAT"; then echo "✗ 파일 이름에 금지된 이름"; fail=1; fi
else
  range="${2:-}"
  if [ -n "$range" ]; then
    git log --format='%an <%ae>%n%cn <%ce>' "$range" | sort -u | while read -r line; do
      email=$(echo "$line" | sed -E 's/.*<([^>]*)>.*/\1/')
      echo "$email" | grep -Eq "$ALLOW_EMAIL" || { echo "✗ 허용되지 않은 커밋 작성자 메일: $email"; exit 1; }
      echo "$line" | grep -Pq "$PAT" && { echo "✗ 커밋 작성자 이름에 금지된 이름"; exit 1; } || true
    done || fail=1
  fi
  if git grep -IPn "$PAT" -- . ':!.githooks/identity-check.sh' 2>/dev/null | strip | grep -P "$PAT" >/tmp/idhits; then echo "✗ 파일 안에 금지된 이름:"; head -20 /tmp/idhits; fail=1; fi
  if git ls-files | strip | grep -Pq "$PAT"; then echo "✗ 파일 이름에 금지된 이름"; fail=1; fi
fi
[ $fail -eq 0 ] && echo "✓ 이름·메일 검사 통과"
exit $fail
