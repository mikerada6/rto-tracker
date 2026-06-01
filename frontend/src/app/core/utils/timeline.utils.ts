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

// ── Journey phase grouping ──────────────────────────────────────────

export type JourneyPhaseType = 'morning_commute' | 'office' | 'evening_commute' | 'home' | 'other';

export interface JourneyStop {
  zone: string;
  zoneType: ZoneType;
  arrivalTime: Date;
  departureTime: Date | null;
  durationSeconds: number;
  /** Travel time from previous stop (gap between prev departure and this arrival) */
  transitFromPrevSeconds: number;
  isImplicitArrival: boolean;
  isMicroVisit: boolean;
}

export interface JourneyPhase {
  type: JourneyPhaseType;
  label: string;
  stops: JourneyStop[];
  startTime: Date;
  endTime: Date | null;
  totalDurationSeconds: number;
  /** Total transit time between stops within this phase */
  totalTransitSeconds: number;
}

/**
 * Groups timeline segments into journey phases:
 *   1. Morning commute — everything from initial home departure to first office entry
 *   2. Office — time spent at office zone(s)
 *   3. Evening commute — everything from last office exit to arriving home
 *   4. Home — remaining time at home (if any trailing segments)
 *
 * Transit gaps (time between exiting one zone and entering the next) are
 * computed and attached to each stop as transitFromPrevSeconds.
 */
export function buildJourneyPhases(segments: TimelineSegment[]): JourneyPhase[] {
  if (!segments.length) return [];

  // Find key transition indices
  const firstOfficeIdx = segments.findIndex(s => s.zoneType === 'OFFICE' && !s.isMicroVisit);
  const lastOfficeIdx = findLastIndex(segments, s => s.zoneType === 'OFFICE' && !s.isMicroVisit);

  // If no office visit, return all segments as a single "other" phase
  if (firstOfficeIdx === -1) {
    return [buildPhase('other', 'Activity', segments)];
  }

  const phases: JourneyPhase[] = [];

  // Morning commute: everything before first office entry
  if (firstOfficeIdx > 0) {
    const morningSegs = segments.slice(0, firstOfficeIdx);
    phases.push(buildPhase('morning_commute', 'Morning Commute', morningSegs));
  }

  // Office: all segments from first office entry to last office exit (inclusive)
  const officeSegs = segments.slice(firstOfficeIdx, lastOfficeIdx + 1);
  phases.push(buildPhase('office', 'At Office', officeSegs));

  // Evening commute: everything after last office exit
  if (lastOfficeIdx < segments.length - 1) {
    const eveningSegs = segments.slice(lastOfficeIdx + 1);
    phases.push(buildPhase('evening_commute', 'Evening Commute', eveningSegs));
  }

  return phases;
}

function buildPhase(type: JourneyPhaseType, label: string, segments: TimelineSegment[]): JourneyPhase {
  const stops: JourneyStop[] = segments.map((seg, i) => {
    let transitFromPrev = 0;
    if (i > 0) {
      const prevSeg = segments[i - 1];
      if (prevSeg.departureTime && seg.arrivalTime) {
        transitFromPrev = Math.max(0, (seg.arrivalTime.getTime() - prevSeg.departureTime.getTime()) / 1000);
      }
    }
    return {
      zone: seg.zone,
      zoneType: seg.zoneType,
      arrivalTime: seg.arrivalTime,
      departureTime: seg.departureTime,
      durationSeconds: seg.durationSeconds,
      transitFromPrevSeconds: transitFromPrev,
      isImplicitArrival: seg.isImplicitArrival,
      isMicroVisit: seg.isMicroVisit,
    };
  });

  const startTime = stops[0].arrivalTime;
  const lastStop = stops[stops.length - 1];
  const endTime = lastStop.departureTime;

  let totalDuration = 0;
  let totalTransit = 0;
  for (const stop of stops) {
    totalDuration += stop.durationSeconds;
    totalTransit += stop.transitFromPrevSeconds;
  }
  totalDuration += totalTransit;

  return { type, label, stops, startTime, endTime, totalDurationSeconds: totalDuration, totalTransitSeconds: totalTransit };
}

function findLastIndex<T>(arr: T[], predicate: (item: T) => boolean): number {
  for (let i = arr.length - 1; i >= 0; i--) {
    if (predicate(arr[i])) return i;
  }
  return -1;
}

