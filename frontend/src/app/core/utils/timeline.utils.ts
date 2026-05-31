import { DayEventEntry } from '../models/day.model';

export type ZoneType = 'HOME' | 'TRAIN_STATION' | 'OFFICE' | 'OTHER';

export interface TimelineSegment {
  zone: string;
  zoneType: ZoneType;
  arrivalTime: Date;
  departureTime: Date | null;
  durationSeconds: number;
  /** True when enter+exit gap is < 90 seconds */
  isMicroVisit: boolean;
  /**
   * True when a bare EXIT had no matching ENTER — the person was already there
   * (e.g. at home overnight). Arrival time shown as "overnight".
   */
  isImplicitArrival: boolean;
  /** Events that make up this segment (for raw toggle) */
  rawEvents: DayEventEntry[];
}

/**
 * Converts a flat list of ENTER/EXIT events into presence segments.
 *
 * Algorithm:
 * 1. Walk events in timestamp order.
 * 2. On ENTER: open a pending segment for that zone.
 * 3. On EXIT for a zone that has a pending segment: close it.
 * 4. On EXIT with no pending segment (bare exit): create a zero-duration segment.
 * 5. Any still-open segment at the end stays open (departureTime = null).
 * 6. Segments with durationSeconds < 90 are flagged as micro-visits.
 */
export function buildSegments(events: DayEventEntry[]): TimelineSegment[] {
  const sorted = [...events].sort(
    (a, b) => new Date(a.timestamp).getTime() - new Date(b.timestamp).getTime()
  );

  // Map from zone name → open segment
  const open = new Map<string, {
    zone: string;
    zoneType: ZoneType;
    arrivalTime: Date;
    rawEvents: DayEventEntry[];
  }>();

  const closed: TimelineSegment[] = [];
  let anyEnterSeen = false;

  for (const ev of sorted) {
    const ts = new Date(ev.timestamp);
    const zoneType: ZoneType = (ev.zoneType as ZoneType) ?? 'OTHER';

    if (ev.type === 'ENTER') {
      anyEnterSeen = true;
      // If already open (missed exit), close it now
      if (open.has(ev.zone)) {
        const pending = open.get(ev.zone)!;
        const dur = (ts.getTime() - pending.arrivalTime.getTime()) / 1000;
        closed.push({
          zone: pending.zone,
          zoneType: pending.zoneType,
          arrivalTime: pending.arrivalTime,
          departureTime: ts,
          durationSeconds: dur,
          isMicroVisit: dur < 90,
          isImplicitArrival: false,
          rawEvents: [...pending.rawEvents, ev],
        });
      }
      open.set(ev.zone, {
        zone: ev.zone,
        zoneType,
        arrivalTime: ts,
        rawEvents: [ev],
      });
    } else {
      // EXIT
      if (open.has(ev.zone)) {
        const pending = open.get(ev.zone)!;
        open.delete(ev.zone);
        const dur = (ts.getTime() - pending.arrivalTime.getTime()) / 1000;
        closed.push({
          zone: pending.zone,
          zoneType: pending.zoneType,
          arrivalTime: pending.arrivalTime,
          departureTime: ts,
          durationSeconds: dur,
          isMicroVisit: dur < 90,
          isImplicitArrival: false,
          rawEvents: [...pending.rawEvents, ev],
        });
      } else {
        // Bare exit — only treat as implicit overnight arrival if it's HOME
        // and we haven't seen any ENTER events yet (morning departure from home).
        // All other bare exits are likely missed events — dim them as micro-visits.
        const isOvernightHomeExit = zoneType === 'HOME' && !anyEnterSeen;
        closed.push({
          zone: ev.zone,
          zoneType,
          arrivalTime: ts,
          departureTime: ts,
          durationSeconds: 0,
          isMicroVisit: !isOvernightHomeExit,
          isImplicitArrival: isOvernightHomeExit,
          rawEvents: [ev],
        });
      }
    }
  }

  // Close any segments still open at end of day
  for (const pending of open.values()) {
    closed.push({
      zone: pending.zone,
      zoneType: pending.zoneType,
      arrivalTime: pending.arrivalTime,
      departureTime: null,
      durationSeconds: 0,
      isMicroVisit: false,
      isImplicitArrival: false,
      rawEvents: pending.rawEvents,
    });
  }

  // Sort by arrival time
  closed.sort((a, b) => a.arrivalTime.getTime() - b.arrivalTime.getTime());
  return closed;
}

export function formatSegmentDuration(seconds: number): string {
  if (seconds < 60) return `${Math.round(seconds)}s`;
  const h = Math.floor(seconds / 3600);
  const m = Math.floor((seconds % 3600) / 60);
  if (h === 0) return `${m}m`;
  if (m === 0) return `${h}h`;
  return `${h}h ${m}m`;
}

