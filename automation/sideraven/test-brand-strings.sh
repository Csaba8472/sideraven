#!/usr/bin/env bash
# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this
# file, You can obtain one at http://mozilla.org/MPL/2.0/.
#
# Checks brand-strings.py on the committed string files (git HEAD, never a working tree that may already be
# branded): the release says Sideraven, keeps Mozilla's service names and data wording in every language,
# rewrites the strings a fork must not inherit, and leaves Sideraven's own strings alone. Run from the repo root.
set -euo pipefail
here=$(cd "$(dirname "$0")" && pwd)
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

git ls-files 'app/src/*/res/values*/*strings.xml' | while read -r f; do
  mkdir -p "$work/$(dirname "$f")"
  git show "HEAD:$f" > "$work/$f"
done
english="$work/app/src/main/res/values/strings.xml"
french="$work/app/src/main/res/values-fr/strings.xml"
ours="$work/app/src/main/res/values/sideraven_strings.xml"
ours_before=$(sha1sum < "$ours")
grep -q "Firefox" "$english" || { echo "FAIL: the copy is already branded" >&2; exit 1; }

python3 "$here/brand-strings.py" "$work"

fail() { echo "FAIL: $1" >&2; exit 1; }
value() { grep -o "<string name=\"$2\"[^>]*>[^<]*" "$1" | sed 's/.*>//'; }

grep -q "Sideraven Labs" "$english" || fail "Firefox Labs was not renamed"
# Mozilla's services keep their names, in every language.
value "$english" "sync_signed_as" >/dev/null || true
grep -q "Firefox Sync" "$english" || fail "Firefox Sync was renamed"
fr_account=$(git show HEAD:app/src/main/res/values-fr/strings.xml | grep -o '<string name="[a-z_0-9]*"[^>]*>[^<]*compte Firefox' | head -1 | sed -E 's/<string name="([a-z_0-9]+)".*/\1/' || true)
[ -z "$fr_account" ] || value "$french" "$fr_account" | grep -q "compte Firefox" || fail "the French Firefox account string ($fr_account) was renamed"
# Mozilla's data collection and marketing wording isn't claimed as Sideraven's.
grep -q "Sideraven sends" "$english" && fail "a string says Sideraven sends data"
grep -iq "Sideraven, and that you use it, with Mozilla" "$english" && fail "a marketing string claims Sideraven shares data"
value "$english" "onboarding_term_of_service_line_three" | grep -q "Sideraven builds on Iceraven" || fail "the terms card's data line wasn't rewritten"
value "$french" "onboarding_term_of_service_line_three" | grep -q "Sideraven builds on Iceraven" || fail "the French terms card's data line wasn't rewritten"
value "$english" "about_content" | grep -q "It isn’t made by Mozilla" || fail "the About line still credits Mozilla as the maker"
# Otherwise no "Firefox" left in strings Sideraven speaks in.
left=$(grep -o '<string name="[a-z_0-9]*"[^>]*>[^<]*Firefox' "$english" | sed -E 's/<string name="([a-z_0-9]+)".*/\1/' \
  | grep -vE '^(preferences_(usage|marketing)_data_description_[0-9]+|onboarding_preferences_dialog_usage_data_description_[0-9]+|onboarding_marketing.*|nova_onboarding_marketing.*|sign_in_instructions|sign_in_create_account_text|privacy_notice_updated_homepage_message|about_content|onboarding_term_of_service_line_two_2|onboarding_term_of_service_line_three|onboarding_redesign_tou_body_(two|three)|nova_onboarding_tou_body_line_[23])$' || true)
for name in $left; do
  value "$english" "$name" | grep -qE "Firefox (account|Account|accounts|Accounts|Sync|Relay|Suggest|Nightly|Terms|Focus|Klar|on )|in to Firefox" \
    || fail "$name still says Firefox: $(value "$english" "$name" | head -c 120)"
done
[ "$(sha1sum < "$ours")" = "$ours_before" ] || fail "Sideraven's own strings were changed"
echo "brand-strings: OK"
