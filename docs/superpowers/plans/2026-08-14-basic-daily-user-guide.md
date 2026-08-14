# Basic Daily User Guide Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver a concise English daily-use guide directly in the conversation for non-technical GPS Tracking app users.

**Architecture:** Produce one self-contained plain-text guide organized in the order a user performs daily actions. Verify every visible label and security requirement against the current app source and operator documentation before sending the response.

**Tech Stack:** Plain English text, Android app UI labels, repository documentation.

## Global Constraints

- Output appears directly in the conversation; do not create a user-guide artifact file.
- Cover daily operation only: status, start, active-state confirmation, stop, history, filtering, sharing, and simple warnings.
- Exclude installation, APK updates, Gmail/App Password setup, build instructions, and source-code administration.
- Mention data deletion only as an administrator-only PIN-protected action.
- Use short sentences and non-technical vocabulary.
- Do not promise an exact email-delivery time.

---

### Task 1: Compose and Verify the Daily-Use Guide

**Files:**
- Read: `app/src/main/java/com/internal/tracker/ui/TrackerApp.kt`
- Read: `docs/periodic-gmail-pilot-handover.md`
- Output: conversation response only

**Interfaces:**
- Consumes: current visible labels and documented PIN, tracking, history, offline, and notification behavior.
- Produces: one English text titled `GPS Tracking App — Basic Daily User Guide`.

- [ ] **Step 1: Build the guide in the user's daily sequence**

Use these sections in order:

```text
GPS Tracking App — Basic Daily User Guide
1. Open the app
2. Start tracking
3. While tracking is active
4. Stop tracking
5. View and share history
6. If something is not working
Important notes
```

- [ ] **Step 2: Include the required operating facts**

State all of the following in plain English:

```text
Status and History can be viewed without a PIN.
Starting tracking requires the required Android permissions and GPS to be enabled.
The foreground notification should remain visible while tracking is active.
Stopping tracking always requires the administrator PIN.
History can be filtered by year and month and shared as CSV.
Deleting data requires confirmation and the administrator PIN.
Records remain stored when the network is unavailable and are sent later.
Scheduled email delivery can be delayed by Android.
Users must not uninstall the app or clear app data as a troubleshooting step.
```

- [ ] **Step 3: Exclude administrator setup content**

Confirm the response contains none of these topics:

```text
APK installation or update commands
Gmail address entry
App Password creation or entry
Build tools, Gradle, signing, Git, or source code
Exact SMTP or database implementation details
```

- [ ] **Step 4: Verify labels and safety statements**

Compare the final draft with `TrackerApp.kt` and `periodic-gmail-pilot-handover.md`. Require these checks:

```text
[ ] Button/tab names match the current English-facing guide terminology.
[ ] PIN is required for stopping and deletion, not for viewing Status or History.
[ ] Offline wording says records are retained, not that email is sent immediately.
[ ] Troubleshooting never tells the user to uninstall or clear app data.
[ ] The response is understandable without technical documentation.
```

- [ ] **Step 5: Send the final guide in the conversation**

Use minimal Markdown headings and numbered steps. Do not mention the planning process, repository, tests, or internal implementation in the guide itself.
