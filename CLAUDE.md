# VeloSonic Android — Project Instructions

## Localization

This app ships in **23 languages**, matching the iOS client exactly: English (`en`,
default — source of truth), German (`de`), Russian (`ru`), Simplified Chinese
(`zh-Hans`), Japanese (`ja`), French (`fr`), Vietnamese (`vi`), Romanian (`ro`), Arabic
(`ar`, right-to-left — Compose mirrors layout automatically from the locale, no code
changes needed), Dutch (`nl`), Spanish (`es`), Italian (`it`), Polish (`pl`), Czech
(`cs`), Hungarian (`hu`), Turkish (`tr`), Croatian (`hr`), Serbian (`sr`, Cyrillic script
— Croatian and Serbian are separate locales despite being mutually intelligible; never
merge or reuse one file for the other), Korean (`ko`), Brazilian Portuguese (`pt-BR`),
European Portuguese (`pt-PT`, a separate locale from `pt-BR` — distinct vocabulary/
grammar, e.g. "ecrã" vs "tela", "ficheiro" vs "arquivo"; never merge or reuse one file
for the other), Greek (`el`), and Yiddish (`yi`, right-to-left, Hebrew script — same
automatic layout mirroring as Arabic, no code changes needed).

`LOCALES` for scripting purposes: `de ru zh-Hans ja fr vi ro ar nl es it pl cs hu tr hr sr ko pt-BR pt-PT el yi` (22 — everything except the `en` default).

### Resource-qualifier folder names

Every locale is a directory under `app/src/main/res/`. Most are a plain two-letter
suffix, but **three are not obvious and getting them wrong means the translation
silently never loads at runtime** (Android falls back to the default `values/` with no
error):

| Locale | Folder |
|---|---|
| Simplified Chinese (`zh-Hans`) | `values-b+zh+Hans` — BCP-47 script qualifier; plain `zh` can't express script |
| Brazilian Portuguese (`pt-BR`) | `values-pt-rBR` — legacy qualifier needs the `r` prefix; a folder literally named `values-pt-BR` is invalid and silently ignored |
| European Portuguese (`pt-PT`) | `values-pt-rPT` — same `r`-prefix requirement |
| Everything else | `values-<code>` (e.g. `values-de`, `values-zh-Hans` is wrong, `values-sr` for Serbian Cyrillic since that's Android/CLDR's default script for `sr`) |

Each locale's file lives at `values-<qualifier>/strings.xml`.

### The hard rule

**Whenever you add, remove, or change the value of a user-facing string, apply the same
change to all 23 `values*/strings.xml` files** — not just the default `values/
strings.xml`. This is a hard requirement, not a nice-to-have: a shipped feature with
English-only strings is an incomplete change. Never leave a non-default file missing a
key or holding a stale value. If a request only mentions "add a setting" or "add a
string" without mentioning localization, still translate it into all 22 other
languages — localization is not optional follow-up work.

This applies to every user-facing string literal in Compose UI code, not just entries
already in `strings.xml` — a hardcoded `Text("Some Label")` is exactly the kind of thing
that needs to become `Text(stringResource(R.string.some_label))` plus 23 `strings.xml`
entries, not a string that's fine to leave as-is because it was already there.

### Translation rules

- Translate the **value** only — never translate or alter the resource **name**
  (`android:name="..."` attribute).
- Translate for natural meaning, not word-for-word. Match how a native speaker would
  phrase the same UI microcopy in a real app — prefer the phrasing a well-localized
  Android app would use over a literal translation.
- Preserve every format specifier (`%s`, `%d`, `%1$s`, `%2$d`, ...) in a value, and keep
  them in the same relative order as the English source when a string has more than
  one — the code substitutes arguments positionally, so reordering them silently swaps
  values (e.g. a song count ending up where a playlist count belongs). Prefer positional
  specifiers (`%1$s`, `%2$d`) over bare ones (`%s`, `%d`) for any string with more than
  one argument, since a translation may need to reorder the words around the values even
  though the argument order itself must stay fixed.
- Keep XML escapes intact (`\'`, `\"`, `\n`, `&amp;`, `&#8230;`) — translate the
  surrounding text, not the escape sequence itself.
- **Never use `<plurals>` resources** — every entry in every `values*/strings.xml` file
  in this project is a plain `<string name="...">`, including count-based strings. Android
  requires a resource name to have the *same* type (`<string>` vs `<plurals>`) across
  every locale variant of a file, with the default `values/strings.xml`'s type as
  authoritative; since the default file only ever declares plain `<string>`, introducing
  a `<plurals>` for the same name in one locale file causes AAPT2 to silently drop that
  resource at build time (a warning, not a hard error — easy to miss) rather than use the
  translation. This was hit for real during the initial 22-language translation pass and
  cost a wasted redo, so it's a hard rule, not a style preference. For a count-based
  string where the target language's plural rules genuinely don't map cleanly onto the
  English "1 item" / "N items" split, rephrase the sentence to sidestep the mismatch
  (e.g. lead with the number rather than inflecting the noun) instead of reaching for
  `<plurals>`.
- XML comments can stay in English — they're for developers navigating the file, not
  shown to users.

### Verification

Before considering a localization change done, sanity-check that every locale file has
the exact same set of `name="..."` attributes as `values/strings.xml` — a stray missing
or misspelled key falls back to a blank/raw string at runtime, not English text. Run
this after any string change; it must print 0 diff lines for every language:

```bash
for f in de ru "b+zh+Hans" ja fr vi ro ar nl es it pl cs hu tr hr sr ko "pt-rBR" "pt-rPT" el yi; do
  d=$(diff <(grep -oE 'name="[^"]+"' app/src/main/res/values/strings.xml | sort) \
           <(grep -oE 'name="[^"]+"' "app/src/main/res/values-$f/strings.xml" | sort) | wc -l)
  [ "$d" -ne 0 ] && echo "values-$f: $d diff lines — FIX BEFORE COMMITTING"
done
```

New `values-*` folders are picked up automatically by the Android resource system as
soon as they exist on disk with a matching name — no manifest/build-config changes
needed, and no in-app locale-switching code is needed either: Compose's
`stringResource()` already resolves the correct folder from the device/app locale
automatically.
