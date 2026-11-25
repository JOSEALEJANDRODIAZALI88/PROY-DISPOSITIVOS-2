# PaceFit AI — Project notes

## Source and ticket scope
- The requirements source is `recursos/PaceFit_AI_informe_final_Kanban.docx`. `TICKETS.md` maps implementation tickets to the report's RF/B/T identifiers; PF numbering is operational, not original report numbering.
- Work proceeds only within the agreed scope; never automatically advance through the backlog. The user authorized Integrante 4: PF-001, PF-007, PF-011 and PF-015 adapted to persistent SQLite and private directories (`0.2.0-local.1`).
- PF-001 retains the local profile. PF-007 has foreground-visible GPS/optional steps and a conservative detector; PF-003 background recording remains pending. PF-011 only adapts local deletion, NOT remote account security. PF-015 has local photos/plogging/reports, NOT shared maps, AI or trusted server points. BLE, health calculations, meals, Supabase/Gemini, social features and PDF remain planned.
- The current UI is an initial implementation, not a verified reproduction of the report's screenshots. Informational roadmap cards must not appear to be working features.

## Repository and verification
- This directory is now an independent Git repository. Check `git rev-parse --show-toplevel` before Git operations; never stage the parent Windows home directory.
- The project remote is `https://github.com/JOSEALEJANDRODIAZALI88/PROY-DISPOSITIVOS-2.git`; the first delivery is published on `primera-version`. Do not replace it with the parent repository's remote or overwrite `main`.
- PF-001 remains in Testing until device acceptance. Real verification belongs in `docs/VERIFICACION.md`; the original 18-test evidence is historical, not verification of later edits.
- Windows verification: `.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest`. Instrumented execution requires a connected device: `:app:connectedDebugAndroidTest`.
- APK: `app/build/outputs/apk/debug/app-debug.apk`. Build success alone does not verify persistence across app restarts, rotation, offline use or deletion on a device.
- Use JDK 21, wrapper Gradle 8.13, AGP 8.13.0, Kotlin/Compose plugin 2.2.10; Java source/target is 17. SDK 36 is required, min API 26, target 35. `local.properties` stays machine-local.

## State and privacy contracts
- Kotlin sources are under `app/src/main/java/com/pacefit/ai/`: `domain/` holds the profile and validation, `data/` Room/repository code, and `ui/` Compose and ViewModel state.
- `PaceFitApplication` owns the single Room database. The profile has singleton primary key 1. Preserve exported `app/schemas/`; add migrations for schema changes, not destructive fallback.
- `ProfileViewModel` preserves edits during repository emissions. Do not replace an in-progress draft whenever the database emits. Do not log health values.
- There is no Internet permission or remote account. GPS/ACTIVITY_RECOGNITION permissions are optional for the local extensions, never required for the health profile or manual coordinates. There is no background-location, camera or storage permission. Health remains local; preserve both backup/transfer exclusions.
- Room v2 uses `MIGRATION_1_2`, never destructive fallback. Photos live in `files/pacefit/photos/{plogging,reports}` with UUID names and fresh JPEG encoding without source EXIF. Serialize DB/file mutations through `LocalRepository`; preserve interrupted-deletion recovery via `erase.pending`.
- Numeric input accepts Spanish decimal commas and rejects nonfinite values. Input bounds are typo checks, not medical guidance.
- UI copy and ticket state must distinguish implemented, tested and planned. Do not mark device acceptance or Kanban evidence complete without execution.
