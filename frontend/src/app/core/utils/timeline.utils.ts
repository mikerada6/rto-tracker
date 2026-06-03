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
  /**
   * True when this stop represents a phase boundary terminus — the office
   * shown as morning destination, or as evening origin. Endpoint stops
   * render as hollow markers, show a single timestamp (arrived/departed),
   * and contribute no duration of their own.
   */
  isPhaseEndpoint?: boolean;
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
  /** Transit gap after the last stop (to the next phase) */
  trailingTransitSeconds: number;
}

/**
 * Groups timeline segments into journey phases:
 *   1. Morning commute — home → train(s) → office (office shown as destination endpoint)
 *   2. At Office — time spent at office zone(s); preserves mid-day breaks (e.g. lunch
 *      out) as middle stops, but the phase total counts only office-typed durations.
 *   3. Evening commute — office (origin endpoint) → train(s) → home
 *
 * Endpoint stops (office at the end of morning, office at the start of evening) are
 * synthesized so the rail visually starts at Home and ends at Home, with the office
 * acting as the connector between phases.
 */
export function buildJourneyPhases(segments: TimelineSegment[]): JourneyPhase[] {
  if (!segments.length) return [];

  const firstOfficeIdx = segments.findIndex(s => s.zoneType === 'OFFICE' && !s.isMicroVisit);
  const lastOfficeIdx = findLastIndex(segments, s => s.zoneType === 'OFFICE' && !s.isMicroVisit);

  if (firstOfficeIdx === -1) {
    return [buildPhase('other', 'Activity', segments)];
  }

  const phases: JourneyPhase[] = [];
  const firstOfficeSeg = segments[firstOfficeIdx];
  const lastOfficeSeg = segments[lastOfficeIdx];

  // ── Morning Commute ──
  if (firstOfficeIdx > 0) {
    const morningSegs = segments.slice(0, firstOfficeIdx);
    const morningPhase = buildPhase('morning_commute', 'Morning Commute', morningSegs);

    const prevSeg = segments[firstOfficeIdx - 1];
    const officeArrivalTransit = prevSeg.departureTime
      ? Math.max(0, (firstOfficeSeg.arrivalTime.getTime() - prevSeg.departureTime.getTime()) / 1000)
      : 0;

    morningPhase.stops.push({
      zone: firstOfficeSeg.zone,
      zoneType: firstOfficeSeg.zoneType,
      arrivalTime: firstOfficeSeg.arrivalTime,
      departureTime: null,
      durationSeconds: 0,
      transitFromPrevSeconds: officeArrivalTransit,
      isImplicitArrival: false,
      isMicroVisit: false,
      isPhaseEndpoint: true,
    });
    morningPhase.totalDurationSeconds += officeArrivalTransit;
    morningPhase.totalTransitSeconds += officeArrivalTransit;
    morningPhase.endTime = firstOfficeSeg.arrivalTime;

    phases.push(morningPhase);
  }

  // ── At Office ──
  // Preserves any mid-day non-office segments (lunch out) as visible stops,
  // but the phase total counts only office-typed time.
  const officeSegs = segments.slice(firstOfficeIdx, lastOfficeIdx + 1);
  const officePhase = buildPhase('office', 'At Office', officeSegs);
  officePhase.totalDurationSeconds = officeSegs
    .filter(s => s.zoneType === 'OFFICE' && !s.isMicroVisit)
    .reduce((sum, s) => sum + s.durationSeconds, 0);
  phases.push(officePhase);

  // ── Evening Commute ──
  if (lastOfficeIdx < segments.length - 1) {
    const eveningSegs = segments.slice(lastOfficeIdx + 1);
    const eveningPhase = buildPhase('evening_commute', 'Evening Commute', eveningSegs);

    const firstEveningSeg = eveningSegs[0];
    const officeDepartureTransit = lastOfficeSeg.departureTime && firstEveningSeg.arrivalTime
      ? Math.max(0, (firstEveningSeg.arrivalTime.getTime() - lastOfficeSeg.departureTime.getTime()) / 1000)
      : 0;

    if (eveningPhase.stops.length > 0) {
      eveningPhase.stops[0].transitFromPrevSeconds = officeDepartureTransit;
    }

    eveningPhase.stops.unshift({
      zone: lastOfficeSeg.zone,
      zoneType: lastOfficeSeg.zoneType,
      arrivalTime: lastOfficeSeg.departureTime ?? lastOfficeSeg.arrivalTime,
      departureTime: lastOfficeSeg.departureTime,
      durationSeconds: 0,
      transitFromPrevSeconds: 0,
      isImplicitArrival: false,
      isMicroVisit: false,
      isPhaseEndpoint: true,
    });
    eveningPhase.totalDurationSeconds += officeDepartureTransit;
    eveningPhase.totalTransitSeconds += officeDepartureTransit;
    if (lastOfficeSeg.departureTime) {
      eveningPhase.startTime = lastOfficeSeg.departureTime;
    }

    phases.push(eveningPhase);
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

  return { type, label, stops, startTime, endTime, totalDurationSeconds: totalDuration, totalTransitSeconds: totalTransit, trailingTransitSeconds: 0 };
}

function findLastIndex<T>(arr: T[], predicate: (item: T) => boolean): number {
  for (let i = arr.length - 1; i >= 0; i--) {
    if (predicate(arr[i])) return i;
  }
  return -1;
}

