# 오늘 뭐먹지 1.0.7 release validation

Version code8/name1.0.7; Room8, migration additions0. Food67,357; Recipe426/516 complete, known residual90.

Prior audit reused: Unit593 PASS/Samsung365 PASS/23 of23 readiness conditions. All649 audited source files matched before icons; application logic/data/tests unchanged by release preparation. Existing QA Lint0errors/68warnings, Debug0/61.

New icon derived from user original, preserved with master in artifacts/app-icon. Main adaptive+legacy icons and web icon updated. Samsung SM-S948N Android16 home/drawer/app-info PASS, white ring0/black corners0, face/question/three foods visible.

Targeted Unit38/38 and Samsung8/8 PASS: updater, spaghetti detail/amount/save/edit, save acknowledgement, weight popup, search state. Recommendation selector entry smoke PASS. QA/test APK/Release builds PASS. APK applicationId com.example.healthcare, label 오늘 뭐먹지, min24/target37; QA label 오늘 뭐먹지 QA. Existing signer matches.

APK dist/v1.0.7/today-mwo-meokji-v1.0.7.apk:134168293 bytes; SHA256 C6B2919EE5B01DEBCE21B18AEE65EE8612D25BC8A9CA5805C0857E80384F5DA7. Signer385693830FF4C9F9122A9DC5D992646A871AC82764439AEC1C1078BF55496CA8.

QA original17 private files restored byte exactly; DB tables unchanged; firstInstallTime retained. Product untouched during QA. Local detailed evidence app/build/release-1.0.7 is gitignored.

This is pre-publication validation; public release/metadata/download/production update are subsequent gates, not yet claimed complete. Large raw downloads and local audit ledgers retained locally and excluded from staging.
