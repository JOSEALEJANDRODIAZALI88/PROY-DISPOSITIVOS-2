# PaceFit AI — Project notes

## Source and ticket scope
- The requirements source is `recursos/PaceFit_AI_informe_final_Kanban.docx`. `TICKETS.md` maps implementation tickets to the report's RF/B/T identifiers; PF numbering is operational, not original report numbering.
- Work proceeds ticket by ticket. Implement only the agreed ticket; do not start the entire backlog or automatically advance to the next ticket. The current delivery is PF-001, mapped to RF01, B01 and part of B02.
- PF-001 implements the Android base and local profile creation, reading, editing and deletion. GPS, BLE, health calculations, meals, Supabase/Gemini, social features and PDF remain planned; optional report modules remain in the backlog too.
- The current UI is an initial implementation, not a verified reproduction of the report's screenshots. Informational roadmap cards must not appear to be working features.

## Repository and verification
- This directory is now an independent Git repository. Check `git rev-parse --show-toplevel` before Git operations; never stage the parent Windows home directory.
- The project remote is `https://github.com/JOSEALEJANDRODIAZALI88/PROY-DISPOSITIVOS-2.git`; the first delivery is published on `primera-version`. Do not replace it with the parent repository's remote or overwrite `main`.
- PF-001 remains in Testing: APK compilation, 18 unit tests and lint passed; instrumented tests were compiled but not executed, and installation/UI acceptance is pending. Real verification belongs in `docs/VERIFICACION.md`; do not treat earlier evidence as verification of later edits.
- Windows verification: `.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest`. Instrumented execution requires a connected device: `:app:connectedDebugAndroidTest`.
- APK: `app/build/outputs/apk/debug/app-debug.apk`. Build success alone does not verify persistence across app restarts, rotation, offline use or deletion on a device.
- Use JDK 21, wrapper Gradle 8.13, AGP 8.13.0, Kotlin/Compose plugin 2.2.10; Java source/target is 17. SDK 36 is required, min API 26, target 35. `local.properties` stays machine-local.

## State and privacy contracts
- Kotlin sources are under `app/src/main/java/com/pacefit/ai/`: `domain/` holds the profile and validation, `data/` Room/repository code, and `ui/` Compose and ViewModel state.
- `PaceFitApplication` owns the single Room database. The profile has singleton primary key 1. Preserve exported `app/schemas/`; add migrations for schema changes, not destructive fallback.
- `ProfileViewModel` preserves edits during repository emissions. Do not replace an in-progress draft whenever the database emits. Do not log health values.
- PF-001 has no Internet permission, remote account or sensitive permissions. Health remains local; Android backup and device transfer are excluded in both XML configurations. Do not weaken this when adding remote modules.
- Numeric input accepts Spanish decimal commas and rejects nonfinite values. Input bounds are typo checks, not medical guidance.
- UI copy and ticket state must distinguish implemented, tested and planned. Do not mark device acceptance or Kanban evidence complete without execution.
