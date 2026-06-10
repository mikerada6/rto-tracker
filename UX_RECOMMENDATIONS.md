# UX Improvement Recommendations

A senior UI/UX audit of the RTO Tracker frontend. Organized by impact and effort.

---

## Bug Fix: Mobile Calendar Grid Misalignment (FIXED)

The mobile calendar grid had a critical layout bug — an empty "Week office-day counter badge" `<div>` at the end of each week row was adding a 9th cell to an 8-column CSS grid. Because it was `lg:hidden`, it only appeared on mobile, causing each row's overflow to cascade into the next. Week numbers (W18, W19, etc.) appeared shifted mid-row and days did not align under their column headers.

**Root cause:** `calendar.component.ts` line 177 — empty div removed.

---

## High Impact, Low Effort

### 1. Add Copy-to-Clipboard for API Key

**Where:** Settings page, API key display & post-regeneration alert

Right now users have to manually select the monospace key text and copy it. A single "Copy" button with a checkmark confirmation is table-stakes for any secret/token display. This is especially important here because the key must be pasted into Home Assistant config — a clunky copy experience means users might partially copy or miss characters.

### 2. Add Success Toast on Settings Save

**Where:** Settings page (profile name, required days, timezone)

Each "Save" button fires an API call and disables during save, but there's no visible confirmation when it succeeds. The toast service exists and works — it's just not wired up for profile saves. Users are left wondering "did that actually save?"

### 3. CSV Import Format Documentation

**Where:** Events page, CSV upload modal

The upload modal asks users to drag a CSV but never explains what columns are expected, what timestamp format to use, or provides an example row. Add a collapsible "Expected format" section or a download link for a sample CSV file. This is the #1 blocker for any user trying to backfill historical data.

### 4. Empty States for Zones

**Where:** Zones page when no zones are configured

With zero zones, the page shows a title, an "Add Zone" button, and an empty grid. Add a centered illustration/icon with a message like "No zones yet — add your first zone to start tracking" and a prominent CTA. First-time users land here disoriented.

### 5. Date Filter Presets for Events

**Where:** Events page filter bar

Users manually pick start/end dates to filter events. Add quick-select chips: "Today", "Last 7 days", "Last 30 days", "This month", "This quarter". These cover 90% of use cases and eliminate two clicks + two date pickers.

---

## High Impact, Medium Effort

### 6. Improve Onboarding / First-Run Experience

**Where:** Dashboard empty state, global

The current empty dashboard says "Set up Home Assistant automations to start tracking" with a link. For a first-time user, the gap between "I logged in" and "I see data" is enormous. Consider:

- A step-by-step setup checklist (create zones, configure HA, verify first event)
- Inline progress indicator ("Step 2 of 4: Add your office zone")
- A "Send test event" button that creates a sample event to prove the pipeline works

This would dramatically reduce time-to-value.

### 7. Colorblind-Safe Calendar Palette

**Where:** Calendar day cells

The calendar uses green-100 through green-700 to indicate office hours duration. This is a single-hue gradient — difficult for anyone with deuteranopia or protanopia (8% of men). Options:

- Use a blue-to-purple gradient instead (perceptually distinct across color vision types)
- Add pattern fills or subtle icons inside cells alongside color
- Show the hour count as text on all screen sizes (currently hidden on mobile)

### 8. Keyboard Shortcut Discoverability

**Where:** Calendar, global navigation

The calendar supports arrow keys and Escape, but users have no way to discover this. Add:

- A small "Keyboard shortcuts" link or `?` hotkey that opens a shortcut reference panel
- Tooltip hints on the nav buttons ("← Previous month (Left arrow)")

### 9. Smarter Error Messages

**Where:** All error states across features

Current errors are generic ("Failed to load dashboard data", "Unable to connect"). Replace with actionable messages:

- Network error: "Can't reach the server — check your connection and try again"
- 401: "Your API key may have expired — go to Settings to regenerate"
- 500: "Something went wrong on our end — try again in a moment"
- Timeout: "This is taking longer than usual — the server may be busy"

Each error state already has a retry button, which is good — but the message should help users decide whether retrying will actually help.

### 10. Unsaved Changes Warning

**Where:** Settings page, Zone edit modal

If a user edits their display name and navigates away without saving, the change is silently lost. Add a `CanDeactivate` guard that prompts "You have unsaved changes — discard?" This is standard for any form-heavy page.

---

## Medium Impact, Medium Effort

### 11. Restore Soft-Deleted Zones

**Where:** Zones page

The delete confirmation says "It will be soft-deleted and can be restored later" — but there's no restore UI. This is a broken promise. Add either:

- A "Show deleted" toggle that reveals inactive zones with a "Restore" button
- A trash/archive section at the bottom of the zones list

### 12. Searchable Timezone Select

**Where:** Settings page timezone dropdown

The timezone dropdown has 15 options in a flat list. It's manageable now, but it's still a scan-heavy interaction. Replace with a combobox/autocomplete that filters as you type. The auto-detect button is smart — make it more prominent (currently a small globe icon).

### 13. Pagination for Recent Commutes

**Where:** Dashboard recent commutes section

This section loads all recent commutes with no pagination or "show more" control. For active users this list will grow indefinitely. Add a "Show more" button or limit to the last 5 with a "View all in Calendar" link.

### 14. Request Cancellation on Navigation

**Where:** All services / HTTP calls

If a user navigates from Dashboard to Calendar while the dashboard is still loading, those HTTP requests continue in the background. Use `takeUntilDestroyed()` or the `AbortSignal` pattern to cancel in-flight requests on component teardown. This prevents stale responses from arriving and causing unexpected state updates.

### 15. Mobile Calendar Cell Information

**Where:** Calendar grid on mobile

On desktop, each calendar cell shows the office duration (e.g., "7h 30m"). On mobile this is hidden entirely — cells only show a small green dot. The dot communicates "office day" but loses the duration dimension. Consider showing abbreviated text ("7h") or using the green intensity more aggressively to encode duration on mobile.

---

## Medium Impact, Higher Effort

### 16. Data Export

**Where:** Reports page, Events page

There's no way to export data. Users who need to share compliance reports with managers or HR have to screenshot. Add:

- "Export CSV" on the events list (with current filters applied)
- "Export PDF" or "Print" on quarterly reports
- Dashboard summary as a shareable link or downloadable image

### 17. Offline Resilience

**Where:** Global

The app shows an offline banner, which is good, but all features become completely non-functional offline. Consider caching the last-fetched dashboard and calendar data in localStorage/IndexedDB so users can at least view recent data while offline.

### 18. Break Down Calendar Component

**Where:** `calendar.component.ts` (861 lines)

The calendar is a single 860+ line component handling: month grid computation, touch gestures, keyboard events, day detail loading, journey visualization, and raw event display. Extract into sub-components:

- `CalendarGrid` — month rendering + cell interaction
- `CalendarHeader` — navigation controls + month stats
- `DayDetailPanel` — day breakdown (already partially done for mobile)
- `JourneyTimeline` — subway-map phase visualization

This improves maintainability and enables lazy-loading the detail panel.

---

## Lower Priority Polish

### 19. Focus Management in Modals

Modals (zone edit, delete confirm, CSV upload) don't trap focus — a keyboard user can Tab past the modal into the background page. Implement focus trapping (first/last element cycling) and restore focus to the triggering element on close.

### 20. Loading States for Zone Operations

Zone create/edit/delete operations have no loading indicator. The button click fires the API call but gives no visual feedback until the toast appears. Add a spinner or disable state during the operation.

### 21. Transition to Reactive Forms

The app uses `ngModel` for form binding. Reactive forms (`FormBuilder`, `FormGroup`) provide better validation control, easier testing, and cleaner error message display. This is a larger refactor but would unlock proper inline validation across settings, zone editing, and CSV mapping.

### 22. Login Page Help Text

The login page has an API key input and that's it. First-time users who received an invite code have no idea where to get an API key or what it looks like. Add:

- "Don't have an API key? Register with an invite code" link
- Brief help text: "Your API key was provided when you registered"

### 23. Relative Timestamps

Event timestamps are shown in absolute format. For recent events, relative time ("2 hours ago", "yesterday at 3:15 PM") is faster to parse. Use relative for events within 48 hours, absolute beyond that.

### 24. Drag-and-Drop Visual Feedback on CSV Upload

The file drop zone has a dashed border but doesn't change appearance on dragover. Add a visual state change (border color, background highlight, "Drop file here" text swap) when a file is hovering over the zone.

---

## Accessibility Checklist

These are WCAG 2.1 AA compliance gaps worth addressing:

| Item | Status | Fix |
|------|--------|-----|
| Skip-to-main-content link | Missing | Add hidden skip link before nav |
| Focus trapping in modals | Missing | Implement for all dialog components |
| Emoji icons lack accessible names | Partial | Add `aria-hidden="true"` + visually hidden text |
| Calendar color contrast | Needs audit | Validate green shades meet 4.5:1 ratio |
| Touch targets ≥ 44px | Mostly good | Audit small icon buttons on mobile |
| `[innerHTML]` for icons | Security risk | Switch to Angular `DomSanitizer` or SVG components |
| `aria-live` on dynamic content | Partial (toast only) | Add to dashboard stats on refresh |

---

## Summary Priority Matrix

| Priority | Items | Theme |
|----------|-------|-------|
| **Do first** | #1-5 | Quick wins that immediately improve daily UX |
| **Do next** | #6-10 | Onboarding, accessibility, and error handling |
| **Plan for** | #11-15 | Feature gaps and mobile polish |
| **Backlog** | #16-24 | Architecture, export, offline, and compliance |
