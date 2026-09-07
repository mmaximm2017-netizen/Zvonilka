# Zvonilka UI refinement task

Implement the approved UI refinement pass for the four main screens shown on Samsung A15. Keep all call, Telecom, SIM, missed-call, contact persistence, PhoneBlock and signing behavior unchanged.

## 1. Global density
- Reduce visual density/spacing about 10–15% without making touch targets unsafe.
- Main page titles should be smaller and less dominant than current oversized headings.
- Reduce excessive card/list vertical padding and bottom navigation height/spacing while keeping good accessibility.
- Preserve current dark blue / light blue visual identity and Material 3.

## 2. Recent screen
- Keep only the two filters `Все` and `Пропущенные` inside a compact segmented control that does NOT consume almost the whole width.
- Keep `Править` / `Готово` as a clearly separate action aligned right, not visually a third segment.
- Make call rows about 10–15% more compact.
- Keep name as primary information; make SIM/operator/duration secondary and visually quieter.
- Make the repeated info affordance visually quieter/smaller while keeping its function and touch target.
- Preserve all edit/delete/expand/swipe-call behavior.

## 3. Contacts
- Reduce search field and row vertical size about 10–15%.
- Do NOT render SIM source as a separate line above the contact name. Render a small compact SIM badge/chip inline with the name or otherwise on the same row, while preserving source information.
- Make alphabet rail narrower/quieter while still tappable and usable.
- Preserve swipe-to-call, contact opening, search, alphabet navigation and photos.

## 4. Keypad
- Remove the impression of a huge empty area above the dial pad.
- Treat the number as a proper primary input/display: placeholder should be visually lighter/smaller, a typed number should be clearly prominent.
- Show T9/contact matches directly under/near the number area in a bounded compact result area instead of letting an empty LazyColumn consume most of the screen.
- Slightly reduce dial-key circles; improve legibility of Latin/Cyrillic letter labels.
- Keep green call button as the main accent.
- Preserve long-0 `+`, backspace long-clear, paste, haptics, T9 matching, contact creation/add, SIM selection, call placement.

## 5. Settings redesign
- Replace the collection of equally prominent blue buttons and status paragraphs with normal compact settings rows/cards and clear hierarchy.
- In `Звонки и доступ`, render status rows like `Телефон по умолчанию`, `Контакты`, `История вызовов`, `Входящие на экране блокировки` with compact success/problem indicators.
- Show the large `Настроить разрешения` button/action only when something actually needs setup; otherwise do not make it dominant.
- Render `SIM по умолчанию`, `Экспорт контактов`, `Импорт контактов` as standard setting rows with value/chevron/action semantics rather than large capsule buttons.
- Keep appearance and PhoneBlock controls understandable and compact.

## 6. Advanced / technical settings
- Add a collapsed `Дополнительно` section (or sub-screen if simpler and consistent) and move technical items there: call diagnostics, SIM-book technical status, and other debugging/technical information.
- Keep import/export accessible in normal settings (do not hide basic user backup actions in technical diagnostics).

## Constraints
- No feature removals.
- No changes to call answer/reject UX, lock-screen incoming UI, Telecom lifecycle, missed-call logic, PhoneBlock behavior, permissions policy, contact storage, SIM mutation rules, VCF semantics or release signing.
- Do not add new dependencies unless truly necessary (prefer none).
- Keep code focused; avoid broad architecture refactor.
- Bump app to versionName `0.11.0`, versionCode `18` because this is a visible multi-screen UI release.
- Update README version/current UI notes if needed.
- Remove this `UI_REDESIGN_TASK.md` file before finalizing the implementation.
- Run/ensure `:app:testDebugUnitTest :app:assembleDebug :app:lintDebug` remains green.
