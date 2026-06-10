# Home Assistant Integration Guide

This guide explains how to connect Home Assistant to the RTO Tracker so that zone enter/exit events are automatically recorded when your phone detects you entering or leaving a location.

---

## How It Works

1. Home Assistant tracks your phone's location using the **HA Companion App**
2. You define **Zones** in Home Assistant (home, office, train station, etc.)
3. When your phone enters or leaves a zone, a UI automation fires
4. The automation calls a `rest_command` that POSTs the event to the RTO Tracker API
5. The backend records the event and updates your compliance metrics

---

## Prerequisites

- A running RTO Tracker instance (backend accessible from your HA server)
- Home Assistant with the [Companion App](https://companion.home-assistant.io/) installed on your phone
- An RTO Tracker account with an API key (find yours on the **Settings** page)
- Zones created in both Home Assistant and the RTO Tracker UI

---

## Step 1: Create Zones in RTO Tracker

In the RTO Tracker web UI, go to **Zones** and create entries for each location you want to track. For each zone, set:

- **Name** — a human-readable label (e.g. "Home", "Office")
- **Type** — `HOME`, `OFFICE`, `TRAIN_STATION`, or `OTHER`
- **External ID** — must match the Home Assistant zone entity ID exactly (e.g. `zone.home`, `zone.my_office`)

To find your HA zone entity IDs, go to **Settings > Areas & Zones > Zones** in Home Assistant. The entity ID appears below the zone name (e.g. `zone.home`).

---

## Step 2: Add Secrets to `secrets.yaml`

Add these entries to your Home Assistant `secrets.yaml` file (usually at `/config/secrets.yaml`):

```yaml
# Full URL to the RTO Tracker events endpoint.
# !secret only works as a complete YAML value — it CANNOT be embedded
# inside a string like "http://!secret host:8080/path". Store the
# entire URL here instead.
rto_events_url: "http://192.168.1.100:8080/api/v1/events"

# Your API key — copy from the RTO Tracker Settings page
rto_api_key: YOUR_API_KEY_HERE
```

> **Important `!secret` gotcha:** A common mistake is writing `url: "http://!secret rto_host:8080/..."`. This does **not** work — `!secret` is a YAML tag that must be the entire value. Always store the full URL as the secret.

> **Tip:** Each user needs their own API key. If you're tracking multiple people, add a separate secret for each (e.g. `rto_api_key_alice`, `rto_api_key_bob`).

---

## Step 3: Define the REST Command (YAML Required)

`rest_command` has no UI equivalent in Home Assistant — this is the one part that must be done in YAML.

Create a file called `rto_rest_commands.yaml` in your HA config directory:

```yaml
rto_event:
  url: !secret rto_events_url
  method: POST
  content_type: "application/json; charset=utf-8"
  headers:
    X-API-Key: !secret rto_api_key
  payload: >-
    {
      "externalId": "{{ externalId }}",
      "eventType":  "{{ eventType }}",
      "timestamp":  "{{ timestamp }}"
    }
```

Then include it in your `configuration.yaml`:

```yaml
rest_command: !include rto_rest_commands.yaml
```

Restart Home Assistant after making this change (the `rest_command` integration requires a restart).

### Payload Fields

| Field | Required | Description |
|-------|----------|-------------|
| `externalId` | Yes* | The HA zone entity ID (e.g. `zone.home`). Must match a zone's External ID in RTO Tracker. |
| `eventType` | Yes | Either `ENTER` or `EXIT` |
| `timestamp` | Yes | ISO-8601 UTC timestamp (e.g. `2026-05-31T14:32:00Z`). Must not be in the future. |
| `zoneId` | Yes* | Alternative to `externalId` — the zone's UUID from RTO Tracker. Use one or the other. |
| `latitude` | No | GPS latitude as a decimal number |
| `longitude` | No | GPS longitude as a decimal number |

\* Provide either `externalId` or `zoneId`, not both. `externalId` is recommended for Home Assistant since it matches HA zone entity IDs directly.

---

## Step 4: Create the Automation (via the UI)

Modern Home Assistant recommends creating automations through the UI rather than editing YAML files directly. The UI automation editor gives you visual traces, easier debugging, and avoids YAML syntax issues.

### Using the UI Automation Editor

1. Go to **Settings > Automations & Scenes > Create Automation**
2. Set **Mode** to `Parallel` (so overlapping zone events don't queue)

**Add triggers** — for each zone you want to track, add two "Zone" triggers:

| Trigger name | Entity | Zone | Event |
|--------------|--------|------|-------|
| `leave_home` | `person.your_name` | `zone.home` | Leave |
| `arrive_home` | `person.your_name` | `zone.home` | Enter |
| `arrive_office` | `person.your_name` | `zone.my_office` | Enter |
| `leave_office` | `person.your_name` | `zone.my_office` | Leave |

Give each trigger an **ID** (e.g. `leave_home`) — you'll reference these in the actions.

**Add actions** — use "Choose" (formerly "If/Then") to branch on which trigger fired. For each branch:

1. **Condition:** Triggered by → select the trigger ID
2. **Action:** Call service → `rest_command.rto_event` with this data:

   | Field | Value |
   |-------|-------|
   | `externalId` | The zone entity ID, e.g. `zone.home` |
   | `eventType` | `EXIT` for leave triggers, `ENTER` for enter triggers |
   | `timestamp` | `{{ utcnow().isoformat() ~ 'Z' }}` |

> **Important:** Use `utcnow().isoformat()` for the timestamp — this produces a valid ISO-8601 string with a `+00:00` UTC offset. Do **not** append `~ 'Z'` (that creates an invalid `+00:00Z` suffix). Do **not** use `now()` — the backend expects UTC. Do **not** use `now().utc` — that attribute does not exist in HA's Jinja2.

### YAML Equivalent (for reference)

If you prefer YAML or want to paste into the UI's YAML editor tab, here's the equivalent:

```yaml
alias: RTO Zone Tracking
description: Posts zone enter/exit events to the RTO Tracker API

triggers:
  - trigger: zone
    entity_id: person.your_name
    zone: zone.home
    event: leave
    id: leave_home

  - trigger: zone
    entity_id: person.your_name
    zone: zone.home
    event: enter
    id: arrive_home

  - trigger: zone
    entity_id: person.your_name
    zone: zone.my_office
    event: enter
    id: arrive_office

  - trigger: zone
    entity_id: person.your_name
    zone: zone.my_office
    event: leave
    id: leave_office

actions:
  - choose:
      - conditions:
          - condition: trigger
            id: leave_home
        sequence:
          - action: rest_command.rto_event
            data:
              externalId: zone.home
              eventType: EXIT
              timestamp: "{{ utcnow().isoformat() }}"

      - conditions:
          - condition: trigger
            id: arrive_home
        sequence:
          - action: rest_command.rto_event
            data:
              externalId: zone.home
              eventType: ENTER
              timestamp: "{{ utcnow().isoformat() }}"

      - conditions:
          - condition: trigger
            id: arrive_office
        sequence:
          - action: rest_command.rto_event
            data:
              externalId: zone.my_office
              eventType: ENTER
              timestamp: "{{ utcnow().isoformat() }}"

      - conditions:
          - condition: trigger
            id: leave_office
        sequence:
          - action: rest_command.rto_event
            data:
              externalId: zone.my_office
              eventType: EXIT
              timestamp: "{{ utcnow().isoformat() }}"

mode: parallel
```

> **Tip:** You can paste YAML directly into the automation editor. Open your automation in the UI, click the three-dot menu, and select **Edit in YAML**. Paste the YAML above, adjust entity names, and save.

### Adding More Zones

To track additional locations (train stations, secondary offices, etc.):

1. Create the zone in Home Assistant and in the RTO Tracker UI (with matching External IDs)
2. Add an `enter` and `leave` trigger for the zone in the automation
3. Add corresponding `choose` branches in the actions

---

## Step 5: Test It

1. **Manual test with curl** — verify the backend is reachable before setting up HA:

   ```bash
   curl -X POST http://YOUR_SERVER:8080/api/v1/events \
     -H "Content-Type: application/json" \
     -H "X-API-Key: YOUR_API_KEY" \
     -d '{
       "externalId": "zone.home",
       "eventType": "ENTER",
       "timestamp": "2026-05-31T12:00:00Z"
     }'
   ```

   A successful response returns `201 Created` with the event details.

2. **Test from HA** — go to **Developer Tools > Actions** (formerly Services), search for `rest_command.rto_event`, and call it with test data:

   ```yaml
   externalId: zone.home
   eventType: ENTER
   timestamp: "2026-05-31T12:00:00Z"
   ```

3. **Check the automation trace** — after a live zone event fires, go to **Settings > Automations**, click your automation, and open the **Traces** tab to see exactly what happened (which trigger fired, what data was sent, whether the REST call succeeded).

4. **Live test** — physically leave and re-enter a zone, then check the **Events** page in the RTO Tracker UI to confirm the events were recorded.

---

## Troubleshooting

| Problem | Solution |
|---------|----------|
| `401 Unauthorized` | Check that your API key in `secrets.yaml` matches the one on the Settings page. Keys are case-sensitive. |
| `404 Not Found` on event POST | The `externalId` doesn't match any zone in your RTO Tracker account. Verify the External ID field on the Zones page matches the HA zone entity ID exactly. |
| `422 Unprocessable Entity` | The timestamp is in the future. Make sure you're using `utcnow()`, not `now()`. |
| REST command not found | You need to restart HA after adding `rest_command` to `configuration.yaml`. A "Reload Automations" is not enough. |
| Events not firing | Open the automation's **Traces** tab in the UI to see if the trigger fired. Also verify the Companion App has location permissions enabled and is not in battery-saver mode. |
| Duplicate events | The backend deduplicates events within a 5-minute window automatically. If you see duplicates further apart, check for GPS bounce — increase your zone radius in HA (150m+ recommended for GPS zones). |
| `!secret` not resolving in URL | `!secret` must be the entire YAML value. `url: "http://!secret host:8080/path"` does **not** work. Store the full URL as the secret value instead. |

---

## API Response Reference

**Success (new event)** — `201 Created`:

```json
{
  "id": "a1b2c3d4-...",
  "zoneId": "e5f6g7h8-...",
  "zoneName": "Home",
  "eventType": "ENTER",
  "timestamp": "2026-05-31T12:00:00Z",
  "latitude": null,
  "longitude": null,
  "createdAt": "2026-05-31T12:00:01Z"
}
```

**Deduplicated (existing event returned)** — `200 OK`: same format as above.

---

## Multiple Users

If you're tracking more than one person, create a separate `rest_command` for each user with their own API key:

```yaml
rto_event_alice:
  url: !secret rto_events_url
  method: POST
  content_type: "application/json; charset=utf-8"
  headers:
    X-API-Key: !secret rto_api_key_alice
  payload: >-
    {
      "externalId": "{{ externalId }}",
      "eventType":  "{{ eventType }}",
      "timestamp":  "{{ timestamp }}"
    }

rto_event_bob:
  url: !secret rto_events_url
  method: POST
  content_type: "application/json; charset=utf-8"
  headers:
    X-API-Key: !secret rto_api_key_bob
  payload: >-
    {
      "externalId": "{{ externalId }}",
      "eventType":  "{{ eventType }}",
      "timestamp":  "{{ timestamp }}"
    }
```

Then use `rest_command.rto_event_alice` or `rest_command.rto_event_bob` in each trigger's action based on which `person.*` entity fired.

See the example files in this directory for a complete multi-user setup:
- `rest-commands.yaml` — REST command definitions
- `automation.yaml` — full automation with triggers for multiple people and zones
- `secrets-snippet.example.yaml` — secrets template
- `midnight-reset.yaml` — daily helper reset automation
