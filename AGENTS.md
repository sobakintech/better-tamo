# Better TAMO

Native Android TAMO school diary client (Kotlin, Jetpack Compose, Material 3). Protocol reference: [tamo-dienynas-api](https://github.com/sobakintech/tamo-dienynas-api), usually checked out next to this repo at `../tamo-dienynas-api`.

## Working rules

- The emulator runs the official TAMO app and Better TAMO signed into the user's real account. Install with `adb install -r` only. Never uninstall, clear data or use `connectedDebugAndroidTest`; run UI tests with `am instrument` (see README).
- Never write to the real account without asking first. This includes homework completion sync (`POST core/app/darbai/namu/atlikimas`), sending or deleting messages, and the test notification (it can notify the user's other devices).
- Don't print or extract the session token or official app credentials. Remove any temporary response dumps and their files before finishing.
- Unit tests use fictional fixtures only. UI tests use an isolated Compose activity with synthetic data, never `MainActivity` or the live API.
- Versions come from the build time (`yyyy.MM.dd.HHmm`); every push to `main` (except Markdown-only changes) publishes a release through the Release workflow, which can also be run manually.
- In-app updates read GitHub's latest release (`v<versionName>` tag with an `.apk` asset) and install it through a `PackageInstaller` session. Release builds check on open (at most hourly); debug builds only check from Daugiau and can't install release APKs (different signing key).

## Design direction

Follow the official TAMO school diary's structure while using native Compose/Material components and retaining local personalization.

- Five bottom tabs: Tvarkaraštis, Namų darbai, Įvykiai, Pranešimai, Pažymiai. Messages and Įvykiai stay separate (never merged into one inbox). Every tab header ends with a profile (initials) button that opens Daugiau: profile/role switch, Artimiausi įvykiai, Pamokų istorija, Savaitės tvarkaraštis, TAMO's own extra menu (Analitika web pages), Mano pamokos, phone notifications, appearance, version, Atnaujinimai, a GitHub repository link and sign-out. Nested pages keep the bottom bar and use a back-arrow header.
- Pranešimai is native: folder pill (Gauti, Pažymėti, Išsiųsti, Grupių, Ištrinti), search, paging, unread count badge on the tab, message cards (avatar, sender, role, date, subject, attachment/important marks, star), and a message page with HTML body, attachments and Atsakyti. Composing and replying use TAMO's own web composer inside the app (it closes itself after sending).
- All lists use the same card language: light day headings with relative-day pills and rounded cards (homework, Dienynas, Dalykai, Įvykiai, upcoming events, lesson history, messages). No full-width date bars or divider rows.
- Colored full-width headers with compact titles. The timetable header contains month/year, week navigation and a seven-day strip; its month calendar expands over a dimmed timetable from the same surface.
- Refresh the timetable by pulling down. Keep the date in the existing header; no duplicate date row or standalone refresh button.
- Red weekends. Lesson time, ordinal number and subject share one card. No detached time gutter, monospace clock text, room numbers, or room editor.
- Tapping a lesson opens a bottom sheet with only that lesson: a roomy header card (time column, lesson type, subject, date, teacher, full topic), then marks/attendance, remarks, homework due for it (filled ND) and assigned in it (outlined ND, with its deadline). Its only button, "Visa dalyko informacija" (text only, no icon), opens the subject view. There is no separate full lesson page.
- The subject view (from the sheet or Pažymiai → Dalykai) is the one place for everything about a subject: period average with pažymiai/kaupiamieji/missed counts and final grade, upcoming tests for it (tap jumps to the day), unused cumulative grades from the calendar's `formatives` group with their average and count, every grade and cumulative grade this school year with dates, "Savaitės pamokos" (weekday, lesson number, time in the week nearest today), a "Pamokų istorija" list of every lesson this school year (`GetLessons` in two-month chunks; latest five with "Rodyti visas pamokas"; tap opens that day), praise/remarks, attendance, and "Pervadinti pamokas" at the bottom. No grade calculator.
- Homework matches the timetable: each task is its own rounded card (checkbox, subject, text clamped to three lines, "Užduota MM.dd · teacher"), tapping a card expands the full selectable text, done tasks fade with a struck-through subject. Days are light headings with a relative-day pill on every day (Šiandien/Rytoj/Poryt/Po N d., Vakar/Užvakar/Prieš N d.; today and tomorrow highlighted). The header uses filter chips with counts and a period pill (Artimiausi/Praėję).
- The timetable month/year control is a rounded pill with the calendar icon and chevron inside. Lesson cards match the official layout: fixed height, lesson type as a small uppercase overline above the title (every labelled type, and anything TAMO marks important, in red with a red left edge), one-line title and description with ND chips underneath, content vertically centred. Marks, remark icon and running average/trend sit in the right-hand column behind a divider. Remark icons are speech bubbles (Pagyrimas green check, Pastaba red "!", Komentaras grey), never warning triangles. No subject colour bar. Filled ND = homework due for this lesson; outlined ND = homework assigned in this lesson.
- Pažymiai has Dienynas and Dalykai. Cumulative grades are circled; attendance absences use the error colour. Dalykai shows the overall average first, then subjects with period averages; subject pages list the school year's records.
- Timetable cards show only the lesson's own mark, cumulative circles, remark icon and the lesson-type label (tests highlighted). Missing slots between lessons show a tappable gap row (start time, plus icon, end time; one per missing slot) that opens the event editor prefilled as a numbered lesson. Gap lessons hide on days when TAMO has a real lesson in that slot.
- Error banners sit above lists (never inside them) so they stay visible with cached content.
- Subject and semester pages preserve official aggregate meaning. Do not attribute a combined subject's aggregate grades to an individual override without an actual record link.
- Custom events use normal lesson-card styling plus a small Mano įvykis label. A weekly event hides on days when TAMO has an overlapping lesson with the same name (gap lessons: any lesson in that slot/time). Their editor and recurring rename rules live under Daugiau → Mano pamokos. No general timetable add-event button; adding happens from gap rows or Mano pamokos.
- Rename by recurring weekday/slot or subject plus teacher; no one-time-date rules. Resolve homework using its originating lesson, never its due date.
- Keep light, dark and system themes. Retain native touch targets and readable text scaling. The palette stays green.
- No temporary/sample notices in product screens. Sign-in has no decorative icon, and both headings are centered.

## Protocol notes

These differ from, or aren't covered by, the extracted models in the API reference:

- Calendar `references.grades` points to a subject/period group, not a lesson. Each lesson uses its own `eventDetails` grade and `rightIcons*`; cumulative grades (`formatives`, `dienynas.formativeGrades`) join by `lessonId`.
- `rightIconsTop/Middle/Bottom` arrive as single objects, not arrays. Event-detail fields are `icon/title/body/label`, not the models' `*Content` names.
- Calendar events expose `schoolSubjectId` (scopes rename rules) and `eventIcon.content` (lesson ordinal). There is no teacher ID; teacher rules match on the returned name.
- `GetLessons` has no `id` field; don't treat the legacy `subjectId` as a stable subject ID.
- Feed items are classified by `eventId` (4 homework, 1 grade, 10 cumulative, 8 praise/remark/comment); unknown entries without text are skipped.
- Semester data: `GetWindowFilters(windowName=periods,userType=2)` gives `[personId, periodId]` pairs for `GetPeriodAssessments` (annual period ID is 0). Modern `periodsummary` can return empty periods.
- Some all-day (holiday) events only have a Lithuanian date-range `eventSubtitle`; keep the text when it can't be parsed and never extrapolate holidays into other years.
- Messaging uses `api.tamo.lt/messaging/...` with the same Bearer token and `x-selected-role`. Composer/reply and extra menu pages go through `MobileServiceV3/NavigateDirect`; the WebView needs match-parent layout params or 100%-height pages render blank.
- Push: `res/values/tamo_firebase.xml` holds the official app's Firebase client config, so Firebase initializes without the google-services plugin. Registration is `core/app/devices/installation` (POST, DELETE `/{fid}` on disable/sign-out). Actual push delivery hasn't been confirmed yet.
