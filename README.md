# FWB_5 — Desktop-mode Android browser

FWB_5 is an Android WebView browser configured to request desktop websites by default. The interface has a compact top control row, a tab strip directly underneath it, the page area, and a bottom address/search row.

## Features
- Desktop user-agent and desktop viewport settings.
- Tabs with a `+` button; tap a tab to switch and long-press a tab to close it. The `×` label is visual; long-press is the reliable close action.
- Back and forward navigation, refresh, address navigation and search.
- File picker with multi-select; websites can use the browser's native multi-file chooser when their upload control is opened.
- DownloadManager-based downloads to the device Downloads folder.
- Copy selected text or text/link content at the last touched position; long-press on a page also copies touched content.
- Send button tries to activate a website send/submit control by common labels; if no matching control is found, it opens Android's share sheet with the current page URL.
- GitHub Actions workflow at `.github/workflows/android.yml`, triggered for pushes to `main`, `master`, or `android`, pull requests to those branches, and manual runs.

## Build on GitHub Actions
The workflow installs Gradle 8.7 and Android SDK 35 directly, so it does not depend on a checked-in `gradlew` or `gradle/wrapper` directory. It uploads the debug APK as the `FWB_5-Desktop-APK` artifact.

## Notes
- The browser is an Android application that renders sites in desktop mode; it is not a Windows executable.
- A site's own message send control can only be activated when it is discoverable in that site's page DOM. Otherwise, Send shares the current page link.
- File selection alone does not bypass a website's upload form or its permissions; actual upload is handled by the website's own file input.
