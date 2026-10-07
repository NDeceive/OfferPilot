# Face expression models

Official weights from https://github.com/justadudewhohacks/face-api.js/tree/master/weights.
The upstream MIT license is preserved in `LICENSE`.

The browser loads `tiny_face_detector_model-weights_manifest.json` and
`face_expression_model-weights_manifest.json`, plus the binary shards listed in
those manifests. All four files must stay together. No CDN is required at runtime.

The interview uses face-api.js 0.22.2, TinyFaceDetector (input size 224, threshold
0.5), and `detectSingleFace(video, options).withFaceExpressions()`. It samples
on an approximately two-second schedule without overlapping inferences, while a question is
active and the interview is not paused/submitting. Only probabilities and timing
are uploaded; camera images stay in the browser.

Pending samples are cached by user and session in localStorage, uploaded every ten
seconds in batches of at most 100, and flushed in the background when finishing. Failed uploads never block finishing; the report page can retry cached samples. Permanent invalid samples are quarantined with explicit reasons.
The backend validates session ownership, question/round membership, timestamps,
and all seven probabilities. A session/sample ID unique key makes retries
idempotent. The additive `db/migration_expressions.sql` migration runs on backend
startup. Report details expose the `expressions` timeline and per-round summaries.

Verification: `npm test`, `npm run build`, and backend `mvnw.cmd test`.
Set `EXPRESSIONS_DB_TEST=true` to include the rollback-only local MySQL check.
`scripts/check-face-expressions.cjs` checks responsive charts and browser inference
using a fake camera and the existing generated character illustration.
