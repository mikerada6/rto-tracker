# Product Roadmap Ideas

Suggestions for new features and improvements, organized by theme and roughly prioritized by user impact within each section.

---

## 1. Notifications & Alerts

### 1a. Weekly Compliance Digest Email/Push
Right now users have to open the app to check their status. A weekly summary (e.g., every Sunday evening) showing days completed, days remaining, and current pace would keep users informed passively.

### 1b. "Falling Behind" Alerts
Trigger a notification when `requiredAvgForRemainder` exceeds a configurable threshold (e.g., > 4 days/week needed). Delivery options: email, Slack webhook, Home Assistant notification, or push via a companion PWA.

### 1c. Daily Arrival/Departure Confirmation
After an office ENTER event is recorded, send a brief confirmation ("Logged: arrived at Downtown Office at 9:02 AM") so users trust the automation is working without opening the app.

---

## 2. Flexible RTO Policies

### 2a. Holiday & PTO Calendar
Currently the system uses raw calendar averages with no awareness of holidays or time off. Allow users to mark PTO days or import a holiday calendar (iCal feed or manual entry). Those days would be excluded from the denominator, giving a fairer compliance picture.

### 2b. Custom Compliance Periods
Some companies measure RTO on a rolling 4-week window instead of fixed quarters. Allow users to choose between fixed (quarter/month) and rolling-window compliance periods.

### 2c. Variable Weekly Targets
Support policies like "3 days/week minimum, but any week with a company holiday only requires 2." This could be modeled as per-week overrides or rules tied to the holiday calendar.

### 2d. Per-Office Minimum Requirements
Some policies require at least N days at a *specific* office (e.g., your assigned building). Allow users to set per-zone compliance targets in addition to the global one.

---

## 3. Commute Analytics

### 3a. Commute Trends Dashboard
Show historical commute duration trends over weeks/months as a line chart. Highlight averages, best/worst commutes, and day-of-week patterns. Users who commute could optimize their schedule around this data.

### 3b. Departure Time Recommendations
Based on historical commute data, suggest optimal departure times. "Leaving before 7:30 AM saves you ~15 min on average."

### 3c. Commute Cost Estimator
Allow users to input their commute cost (transit pass, gas/mileage, parking, tolls) and show monthly/quarterly spend correlated with office days. Useful for budgeting and expense reports.

---

## 4. Calendar & Scheduling

### 4a. Google/Outlook Calendar Sync
Import calendar events to cross-reference. Show "you had 3 in-person meetings scheduled but only went to the office twice this week." Could also auto-detect office days from calendar location fields as a secondary signal.

### 4b. Plan-Ahead View
Show a forward-looking week/month view: "You need 4 more days this quarter. Here are the remaining work days." Let users tentatively mark which days they plan to go in, and show projected compliance.

### 4c. Team Coordination View (Multi-User)
If multiple people in a team use the app, show which days teammates plan to be in-office. Helps coordinate in-person collaboration. Requires opt-in sharing.

### 4d. Export Calendar (iCal Feed)
Generate an iCal feed of office days so users can subscribe in their calendar app. Each office day appears as an all-day event.

---

## 5. Data & Reporting

### 5a. Year-Over-Year Comparison
Show side-by-side quarter or month comparisons across years. "Q2 2026 vs Q2 2025: +12% office attendance."

### 5b. Export to PDF / CSV
Allow users to download compliance reports as PDF (for HR submissions) or CSV (for personal records). The audit endpoint has the data; this is a presentation layer addition.

### 5c. Streak & Gamification
Show current and longest office-day streaks, badges for milestones ("First full week!", "10 consecutive weeks compliant"). Light gamification can drive habit formation.

### 5d. Monthly Summary Report
Add a dedicated monthly report view alongside the existing quarterly one. Many users think in months, not quarters.

### 5e. Day-of-Week Heatmap
Visualize which days of the week the user tends to go to the office. A small heatmap on the dashboard would surface patterns ("You almost never go in on Fridays").

---

## 6. Multi-Device & Sharing

### 6a. Multiple Tracking Entities
Support tracking more than one Home Assistant `person` entity per user account. Useful for households where both partners track RTO, or for users with multiple phones/devices.

### 6b. Read-Only Sharing Link
Generate a shareable link that shows compliance status without exposing the API key. Useful for showing a manager "I'm at 3.1 days/week this quarter" without giving them account access.

### 6c. Admin Compliance Dashboard
Give admins an aggregate view: team-wide compliance rates, distribution charts, users at risk. Useful for managers responsible for enforcing policy.

---

## 7. User Experience Polish

### 7a. Progressive Web App (PWA)
Add a service worker and manifest so the frontend can be installed on mobile home screens. Enable offline viewing of cached dashboard data and push notifications.

### 7b. Onboarding Wizard
First-time users currently need to: create zones, set up HA automation, configure settings. A step-by-step wizard that walks through each stage (with validation that events are flowing) would reduce setup friction significantly.

### 7c. Quick-Actions / Manual Check-In
Not everyone uses Home Assistant. Add a "I'm at the office" button that manually creates an ENTER event for a selected office zone. Useful as a fallback or for users without HA.

### 7d. Dashboard Widget Customization
Let users choose which stat cards and charts appear on their dashboard and in what order. Not everyone cares about commute data; some just want the compliance number.

### 7e. Dark Mode Schedule
Auto-switch between light and dark mode based on time of day or system preference schedule, rather than just a manual toggle.

---

## 8. Integrations

### 8a. Slack / Teams Bot
A bot that responds to `/rto` with your current compliance status. Could also post weekly summaries to a team channel.

### 8b. Webhook Outbound
Fire a configurable webhook when certain events happen (office day recorded, compliance threshold crossed, weekly summary ready). Enables users to build their own integrations.

### 8c. Apple Shortcuts / Tasker Integration
Provide a simple REST endpoint designed for mobile automation apps. Users without Home Assistant could trigger events from their phone's geofencing.

### 8d. HACS Integration for Home Assistant
Package the HA automation as a proper HACS custom integration with a config flow, rather than requiring manual YAML setup.

---

## 9. Data Integrity & Trust

### 9a. Event Correction / Override
Currently events are immutable. Allow users to mark an event as "incorrect" or add a manual override ("I was in the office on June 1 but HA missed it") with an audit trail showing the correction.

### 9b. Anomaly Detection
Flag suspicious patterns: office ENTER without EXIT, EXIT without ENTER, impossibly short commutes, events at 3 AM. Surface these on the dashboard with a "review needed" indicator.

### 9c. Zone Health Check
Show a status page indicating when each zone last fired an event. If a zone hasn't triggered in an unusual amount of time, warn the user that their automation may be broken.

---

## 10. Performance & Technical

### 10a. Real-Time Event Feed (WebSocket/SSE)
Push new events to the frontend in real-time instead of requiring a page refresh. Show a live "you just arrived at Office" toast when HA fires the webhook.

### 10b. Batch Recomputation Endpoint
If a user corrects historical events or changes zones, provide an admin action to recompute all OfficeDayRecords for a date range rather than waiting for cache misses.

### 10c. Multi-Timezone Support for Travel
Users who travel across time zones currently have a single timezone setting. Support per-event timezone or auto-detect from coordinates so that a business trip to another timezone calculates office hours correctly.

---

*Generated 2026-06-02. Review and prioritize based on user feedback and development capacity.*
