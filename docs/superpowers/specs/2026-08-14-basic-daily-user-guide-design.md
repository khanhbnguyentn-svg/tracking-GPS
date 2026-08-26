# Basic Daily User Guide Design

## Purpose

Provide a short English guide that can be pasted directly into a conversation and followed by a non-technical daily user of the GPS Tracking app.

## Audience and Scope

The reader already has the app installed and configured by an administrator. The guide covers only normal daily operation:

- opening the app and reading the tracking status;
- starting tracking and confirming that it remains active;
- stopping tracking with the required PIN;
- viewing, filtering, and sharing location history;
- responding to simple GPS, permission, notification, and network warnings.

The guide excludes APK installation, updates, Gmail/App Password configuration, report-system internals, build instructions, and source-code administration. Data deletion is mentioned only as an administrator-only action because it requires confirmation and PIN verification.

## Format and Language

The final response is plain English text with short headings and numbered steps. It uses the visible app labels `Status`, `History`, `Settings`, `Start Tracking`, `Stop Tracking`, and `Share CSV` where applicable. Sentences are short, direct, and free of developer terminology.

## Safety and Accuracy

The guide tells users not to uninstall the app, clear app data, or disable required permissions as a troubleshooting shortcut. It explains that the foreground tracking notification should remain visible while tracking is active and that queued records are retained when the network is unavailable. It does not promise an exact email delivery time because Android may delay background work.

## Success Criteria

A daily user can start a trip, recognize that tracking is active, stop it securely, review or share recorded history, and know when to contact an administrator without needing additional technical documentation.
