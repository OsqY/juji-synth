# M15 review — security hardening and final audit gate

Reviewer: `/root/m15_review` (independent context)
Date: 2026-08-02
Security skill: `omk-security-review`
Commit: `229f144`
Verdict: **PASS for the remediation; M15 closure remains BLOCKED by process evidence**

## Remediation reviewed

- `Arrangement` rejects blank and duplicate clip IDs before persisted state can
  reach Compose keys, selection, history, or delete/move operations.
- `ProjectPathPolicy` canonicalizes project names and audio paths, rejects
  traversal, symlink escapes, project-root directories, and cross-project
  absolute paths.
- Repository save/load/delete/rename/import and autosave audio restoration use
  the policy; project-name mismatches are rejected.
- Timeline add, Trash restore, and arrangement reconciliation resolve paths
  through the same policy before calling JNI audio loading.
- Project creation publishes its active name only after a successful save;
  rename and delete keep the app-level active name synchronized.
- Unit tests cover malformed IDs, traversal, root/directory paths, relative and
  absolute selected-project paths, and cross-project rejection.

## Security result

The independent follow-up found no P0, P1, P2, or P3 findings in the
remediation. Critical/high findings from the first audit (clip identity and
uncontained audio/project paths) are resolved. Legacy audio paths outside the
active project are intentionally rejected rather than auto-migrated; the user
workflow documents the manual copy-to-`samples/` recovery.

## Validation

- `git diff --check`: PASS.
- `./gradlew testDebugUnitTest`: PASS.
- `./gradlew compileDebugKotlin`: PASS.
- `./gradlew lintDebug`: PASS.
- `./gradlew assembleDebug`: PASS.
- `./gradlew --no-daemon connectedDebugAndroidTest`: PASS, `21/21` on the
  physical SM-G998W (Android 15, latest source under test).

## Residual M15 blockers

- The required mdpi/xhdpi/xxhdpi/xxxhdpi, tablet, and landscape matrix remains
  unexecuted; unavailable profiles are recorded in
  `docs/timeline-device-validation/`.
- No PR, Linear, Notion, or merge action is authorized by the current scope.
- Oboe and `.commandcode/` remain outside every staged/committed path.

M15 cannot be closed until the required device-density matrix is executed and
the external handoff is explicitly authorized.
