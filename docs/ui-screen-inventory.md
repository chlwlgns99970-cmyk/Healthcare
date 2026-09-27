# Healthcare UI screen inventory

This inventory reflects the routes and user-visible states present in the project before the redesign. It deliberately does not introduce screens or health metrics that were not already implemented.

| Route / state | Screen implementation | Issue found | Redesign applied | Status |
| --- | --- | --- | --- | --- |
| `DashboardRoute` | `DashboardScreen.kt` | Weak numeric hierarchy, default cards and spacing, plain empty state | Daily calorie hero, existing progress/goal data, quick record action, readable meal cards, designed empty state | Complete |
| `HistoryRoute` list | `HistoryScreen.kt` / `HistoryListPane` | Summary and records had weak hierarchy; empty state was plain text | Daily summary card, consistent date navigation, compact record cards, long-name handling, designed empty state | Complete |
| `HistoryRoute` detail | `HistoryScreen.kt` / `HistoryDetailPane` | Detail was a flat text column and could clip on short screens | Scrollable calorie hero, grouped record metadata, memo card, consistent top bar and destructive action color | Complete |
| `HistoryRoute` edit | `HistoryScreen.kt` / `HistoryEditPane` | The release plan required record editing, but the adaptive detail flow exposed only delete | Edit action and a scrollable sectioned form for food name, calories, meal type, date/time, serving and memo; saves retain the record ID and original source/photo metadata | Complete |
| `HistoryRoute` empty detail | `HistoryScreen.kt` / `EmptyDetailPane` | Unstructured centered text | Icon, title and supporting message using the shared empty-state component | Complete |
| `AddRecordRoute` manual entry | `AddRecordScreen.kt` + `WellnessManualRecordScreen.kt` | Long undifferentiated field list; saved/recent/favorite choices and final calories lacked hierarchy | Section-card form, searchable food choices, accessible serving selectors, emphasized editable calories, meal/date/time/memo grouping, persistent scroll flow | Complete |
| `AddRecordRoute` photo permission | `AddRecordScreen.kt` / permission dialogs | Default presentation | Kept behavior and errors intact; dialogs now inherit the unified Material 3 theme | Complete |
| `AddRecordRoute` camera | `FoodCameraScreen.kt` | Basic overlay; insets and shutter hierarchy were weak | Safe-area controls, immersive preview, clear guidance and large circular shutter while preserving CameraX capture | Complete |
| `AddRecordRoute` photo preview | `WellnessManualRecordScreen.kt` / `WellnessPhotoPreviewScreen` | Unshaped preview and weak primary/secondary hierarchy | Large rounded preview, privacy/manual-flow explanation, secondary retake and primary use-photo actions | Complete |
| `AddRecordRoute` photo-reference manual entry | `WellnessManualRecordScreen.kt` | Photo and form felt disconnected | Rounded photo reference card followed by the same manual section flow | Complete |
| Disabled legacy photo-analysis progress/result/error | `AddRecordScreen.kt` | Backend-only legacy states remain in source | Retained but not reconnected or exposed; no active route or general-user trigger exists | Preserved, inactive |
| `SettingsRoute` | `SettingsScreen.kt` | Two no-op items looked interactive; real goal setting lacked focus | Removed misleading no-op rows, emphasized the existing calorie goal, retained its dialog and added a concise app-info section | Complete |
| Adaptive bottom navigation / rail | `HealthcareApp.kt`, `Navigation.kt` | Selected and unselected icons were identical; labels were less concise | Distinct filled/outlined states, concise labels, theme-driven Material 3 colors; routes and tab count unchanged | Complete |

## Structures checked

- Navigation3 back stack and all four real top-level routes
- All screen composables, alert dialogs, top bars, adaptive navigation suite, cards, buttons, text fields, chips, list items and empty/error/loading states
- Room-backed dashboard/history/goal data and ViewModel interactions
- CameraX capture, temporary photo preview and manual photo-reference recording
- Date and time pickers
- Material theme, light/dark colors, typography, shapes and spacing

There is no statistics route, standalone goal route, bottom sheet, or separate record-detail/edit route in this project. Goal editing is part of Settings, and record detail/edit are states of the adaptive History detail pane; no speculative screens were added.
