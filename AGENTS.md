# PaceFit AI — Project notes

- This directory is now an independent Git repository. Check `git rev-parse --show-toplevel` before Git operations; never stage the parent Windows home directory.
- Current scope is PF-001 only: a local health profile. `TICKETS.md` is the backlog; planned modules must not be described as implemented. Real verification belongs in `docs/VERIFICACION.md`.
- Windows verification: `.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest`. Instrumented execution requires a connected device: `:app:connectedDebugAndroidTest`.
- Use JDK 21, wrapper Gradle 8.13, AGP 8.13.0, Kotlin/Compose plugin 2.2.10; Java source/target is 17. SDK 36 is required, min API 26, target 35. `local.properties` stays machine-local.
- `PaceFitApplication` owns the single Room database. The profile has singleton primary key 1. Preserve exported `app/schemas/`; add migrations for schema changes, not destructive fallback.
- `ProfileViewModel` preserves edits during repository emissions. Do not replace an in-progress draft whenever the database emits. Do not log health values.
- PF-001 has no Internet permission, remote account or sensitive permissions. Health remains local; Android backup and device transfer are excluded in both XML configurations. Do not weaken this when adding remote modules.
- Numeric input accepts Spanish decimal commas and rejects nonfinite values. Input bounds are typo checks, not medical guidance.
- UI copy and ticket state must distinguish implemented, tested and planned. Do not mark device acceptance or Kanban evidence complete without execution.
