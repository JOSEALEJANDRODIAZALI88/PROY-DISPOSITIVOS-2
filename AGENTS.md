# PaceFit AI — Project notes

## Source and ticket scope
- The requirements source is `recursos/PaceFit_AI_informe_final_Kanban.docx`. `TICKETS.md` maps implementation tickets to the report's RF/B/T identifiers; PF numbering is operational, not original report numbering.
- Work only within the agreed scope; never automatically advance through the backlog. After `0.2.0-local.1`, the user authorized the original Integrante 4 requirements (PF-001/007/011/015), a full UI redesign and only necessary PF-003/010/012 dependencies (`0.3.0-beta.1`).
- Four main sections: Inicio/Actividad/Ciudad/Perfil; account/settings/SQLite are secondary destinations. Preserve the distinction between private local health/routes and explicitly published city data.
- FGS recording, Auth/RLS/Storage/account deletion and city publishing are implemented. The authorized exclusive Supabase project `ymfypllkcrqwfwihqzpf` is deployed on Free; HTTPS backend and 24 Android tests on each API 26/35 passed, including wrong/valid email OTP. Actual Gmail receipt, public OTP, session refresh/logout and disposable-account cleanup passed in the private desktop helper. Email UI acceptance on a physical Android, Gemini inference and GPS field acceptance remain pending. PF-012 implements only photo and its quota, not the other five tasks/advisor. BLE, health calculations, meals, chat/zones, PDF and optional modules remain backlog.
- Cloud authorization is Free-only. Preserve the other existing projects; never enable billing, resize to paid compute or create another project automatically. `scripts/cloud-admin.py` checks the specific project and organization plan, reads the authorized CLI keyring and never prints secrets. Missing Gemini/SMTP credentials require private user entry, not chat.
- Acceptance scripts include a DPAPI application-data backup and isolated PostgreSQL/photo restore rehearsal, not Auth/PITR or full production recovery. No daily purge task is installed; provider retention remains pending. The user currently has no physical phone: synthetic GPS or desktop helpers never close field/phone acceptance. Temporary acceptance accounts use durable owned-run journals; recover only when no acceptance tool is active.

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
- Internet is for configured HTTPS services and OSM maps, not health upload. GPS/steps/notifications are requested only for their features, never health/manual coordinates. Location FGS starts while UI is visible; no automatic GPS restart after process death, background-location, camera or storage permission. Preserve backup/transfer exclusions.
- Room v3 uses `MIGRATION_1_2` and `MIGRATION_2_3`, never destructive fallback. Preserve schemas 1/2/3. Photos live in `files/pacefit/photos/{plogging,reports}` with UUID names and fresh JPEG encoding without source EXIF. Serialize DB/file mutations through `LocalRepository`; preserve `erase.pending`.
- Supabase URL/public anon key come from local.properties/environment; Gemini/service_role secrets NEVER enter APK/Git/logs. Session tokens use Android Keystore encryption/noBackup files. Preserve account deletion intent/ack/receipt retry, including after Auth/JWT deletion.
- Email confirmation uses an OTP entered in Cuenta, not session tokens in a URI. The hosted project currently uses eight digits; the client supports six/eight without lowering server settings. Never log/save OTP or passwords in SavedState. Admin-generated test OTPs are not evidence of actual email delivery.
- Supabase SQL must maintain RLS/minimal grants, no direct equivalent of sensitive RPC/Edge mutations, per-user private Storage and server-calculated points. AI payload excludes health/identity/GPS; manual classification remains available and real inference is never fabricated.
- Instrumentation uses PaceFitTestRunner with isolated database/photo/session namespaces. Do not let UI/service tests modify production records. Backend tests target localhost and clean only their own users/objects.
- Numeric input accepts Spanish decimal commas and rejects nonfinite values. Input bounds are typo checks, not medical guidance.
- UI copy and ticket state must distinguish implemented, tested and planned. Do not mark device acceptance or Kanban evidence complete without execution.
