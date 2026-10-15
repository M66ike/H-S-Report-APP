# GMES H&S Report App

Android app for completing GMES FM_038 site visit H&S reports offline.

## Current v0.1 scope

- New report workflow based on FM_038-12 `New Report`
- Policy Documents, PPE/Training/Inspections, Equipment, Operational and Client sections
- Yes / Partially / No / N/A scoring using the same report weightings
- Camera or gallery evidence attached directly to individual report questions
- Local saved reports that can be reopened and edited
- Local People & Reviewers list
- On-screen handwritten reviewer signature capture
- Macro-free Excel export using the FM_038 fixed cell positions
- Three-page PDF export following the existing site visit report structure
- Offline SQLite storage

The debug APK is built by GitHub Actions on each push to `main`.
