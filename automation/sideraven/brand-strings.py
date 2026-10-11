#!/usr/bin/env python3
# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this
# file, You can obtain one at http://mozilla.org/MPL/2.0/.
"""Makes Fenix's strings say Sideraven, at release build time (as Iceraven's release does with "Iceraven").

    automation/sideraven/brand-strings.py [repo-root]

Rewrites every Fenix `strings.xml` under app/src/*/res/values*/ in place: "Firefox" becomes "Sideraven",
except in strings that name Mozilla's real services (the account, Sync, Relay, Suggest, Nightly, Mozilla's
terms, Firefox on a computer) or describe Mozilla's data collection and marketing: those keep Fenix's
wording in every language, so Sideraven never claims Mozilla's practices as its own. A few strings that
would otherwise mislead get Sideraven's own (English) wording. Sideraven's strings (sideraven_strings.xml)
and the app names (static_strings.xml) are left alone. The repository keeps Fenix's wording, so upstream
updates merge cleanly; run this only on a checkout that builds a release.
"""
import pathlib
import re
import sys

# A string naming one of these is about Mozilla's service, not this app: it keeps Fenix's wording.
KEPT = [
    "Firefox account",
    "Firefox Account",
    "Firefox accounts",
    "Firefox Accounts",
    "Firefox Sync",
    "Firefox Relay",
    "Firefox Suggest",
    "Firefox Nightly",
    "Firefox Terms",
    "Firefox Focus",
    "Firefox Klar",
    "in to Firefox",  # signing in to Firefox on another device (Send Tab, sync)
    "Firefox on ",    # Firefox on a computer or another device
]
# Mozilla's data collection, marketing and sign-in (on Firefox on a computer): kept as Fenix has them.
KEPT_NAME = re.compile(
    r"^(preferences_usage_data_description_\d+|preferences_marketing_data_description_\d+"
    r"|onboarding_preferences_dialog_usage_data_description_\d+|onboarding_marketing\w*|nova_onboarding_marketing\w*"
    r"|sign_in_instructions|sign_in_create_account_text|privacy_notice_updated_homepage_message)$"
)
# Strings whose meaning changes for a fork: Sideraven's own wording, the same in every language.
OVERRIDES = {
    "about_content": "%1$s is an independent browser built on Firefox for Android. It isn’t made by Mozilla.",
    "onboarding_term_of_service_line_two_2": "Sideraven is built on Firefox. Learn how Firefox handles data in Mozilla’s %1$s.",
    "onboarding_redesign_tou_body_two": "Sideraven is built on Firefox. Learn how Firefox handles data in Mozilla’s %1$s.",
    "nova_onboarding_tou_body_line_2": "Sideraven is built on Firefox. Learn how Firefox handles data in Mozilla’s %1$s.",
    "onboarding_term_of_service_line_three": "Sideraven builds on Iceraven, which removes Firefox’s data collection as far as it can. %1$s",
    "onboarding_redesign_tou_body_three": "Sideraven builds on Iceraven, which removes Firefox’s data collection as far as it can. %1$s",
    "nova_onboarding_tou_body_line_3": "Sideraven builds on Iceraven, which removes Firefox’s data collection as far as it can. %1$s",
}
STRING = re.compile(r'(<string name="(\w+)"[^>]*>)(.*?)(</string>)', re.S)


def kept_names(english: str) -> set:
    names = {m.group(2) for m in STRING.finditer(english) if KEPT_NAME.match(m.group(2))}
    names |= {m.group(2) for m in STRING.finditer(english) if any(p in m.group(3) for p in KEPT)}
    return names


def swap(text: str) -> str:
    held = {f"\x00{i}\x00": phrase for i, phrase in enumerate(KEPT)}
    for key, phrase in held.items():
        text = text.replace(phrase, key)
    text = text.replace("Firefox", "Sideraven")
    for key, phrase in held.items():
        text = text.replace(key, phrase)
    return text


def brand(text: str, keep: set) -> str:
    out, last = [], 0
    for m in STRING.finditer(text):
        out.append(swap(text[last:m.start()]))  # comments and plurals between strings
        open_tag, name, body, close = m.group(1), m.group(2), m.group(3), m.group(4)
        if name in OVERRIDES:
            body = OVERRIDES[name]
        elif name not in keep:
            body = swap(body)
        out.append(open_tag + body + close)
        last = m.end()
    out.append(swap(text[last:]))
    return "".join(out)


def main() -> int:
    root = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else ".")
    english = root / "app/src/main/res/values/strings.xml"
    found = sorted(root.glob("app/src/*/res/values*/strings.xml"))
    if not english.exists() or not found:
        print(f"no Fenix strings.xml under {root}/app/src", file=sys.stderr)
        return 1
    keep = kept_names(english.read_text(encoding="utf-8"))
    changed = 0
    for path in found:
        text = path.read_text(encoding="utf-8")
        branded = brand(text, keep)
        if branded != text:
            path.write_text(branded, encoding="utf-8")
            changed += 1
    print(f"brand-strings: {changed} of {len(found)} string files now say Sideraven ({len(keep)} strings keep Fenix's wording)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
