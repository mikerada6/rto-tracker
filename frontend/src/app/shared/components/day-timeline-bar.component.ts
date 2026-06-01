import { Component, computed, input } from '@angular/core';
import { JourneyPhase, JourneyPhaseType } from '../../core/utils/timeline.utils';

interface TimelineSegment {
  type: JourneyPhaseType | 'gap';
  widthPercent: number;
  label: string;
}

interface HourTick {
  leftPercent: number;
  label: string;
}

@Component({
  selector: 'app-day-timeline-bar',
  imports: [],
  template: `
    @if (segments().length > 0) {
      <div class="px-5 py-3">
        <!-- Timeline bar -->
        <div class="flex h-3 rounded-full overflow-hidden shadow-inner bg-gray-100">
          @for (seg of segments(); track $index) {
            <div class="h-full transition-all"
                 [style.width.%]="seg.widthPercent"
                 [class]="segmentBgClass(seg.type)"
                 [title]="seg.label">
            </div>
          }
        </div>

        <!-- Hour tick marks -->
        <div class="relative h-4 mt-1">
          @for (tick of hourTicks(); track tick.label) {
            <span class="absolute text-[9px] text-gray-400 font-mono -translate-x-1/2"
                  [style.left.%]="tick.leftPercent">
              {{ tick.label }}
            </span>
          }
        </div>
      </div>
    }
  `
})
export class DayTimelineBarComponent {
  readonly phases = input.required<JourneyPhase[]>();

  readonly segments = computed<TimelineSegment[]>(() => {
    const phases = this.phases();
    if (!phases.length) return [];

    // Find global time bounds
    const startMs = Math.min(...phases.map(p => p.startTime.getTime()));
    const endMs = Math.max(...phases.map(p => {
      if (p.endTime) return p.endTime.getTime();
      // For ongoing phases, use last stop's arrival + duration
      const lastStop = p.stops[p.stops.length - 1];
      if (lastStop?.departureTime) return lastStop.departureTime.getTime();
      return lastStop?.arrivalTime.getTime() ?? p.startTime.getTime();
    }));

    const totalSpan = endMs - startMs;
    if (totalSpan <= 0) return [];

    // Build segments with gaps between phases
    const result: TimelineSegment[] = [];
    const sortedPhases = [...phases].sort((a, b) => a.startTime.getTime() - b.startTime.getTime());

    for (let i = 0; i < sortedPhases.length; i++) {
      const phase = sortedPhases[i];
      const phaseStart = phase.startTime.getTime();
      const phaseEnd = phase.endTime?.getTime() ?? phaseStart + (phase.totalDurationSeconds * 1000);

      // Gap before this phase (from previous phase end)
      if (i > 0) {
        const prevPhase = sortedPhases[i - 1];
        const prevEnd = prevPhase.endTime?.getTime() ?? prevPhase.startTime.getTime() + (prevPhase.totalDurationSeconds * 1000);
        const gapMs = phaseStart - prevEnd;
        if (gapMs > 0) {
          result.push({
            type: 'gap',
            widthPercent: (gapMs / totalSpan) * 100,
            label: this.formatDur(gapMs / 1000),
          });
        }
      }

      // Phase segment
      const phaseDurMs = phaseEnd - phaseStart;
      result.push({
        type: phase.type,
        widthPercent: Math.max(1, (phaseDurMs / totalSpan) * 100),
        label: `${phase.label}: ${this.formatDur(phase.totalDurationSeconds)}`,
      });
    }

    return result;
  });

  readonly hourTicks = computed<HourTick[]>(() => {
    const phases = this.phases();
    if (!phases.length) return [];

    const startMs = Math.min(...phases.map(p => p.startTime.getTime()));
    const endMs = Math.max(...phases.map(p => {
      if (p.endTime) return p.endTime.getTime();
      const lastStop = p.stops[p.stops.length - 1];
      if (lastStop?.departureTime) return lastStop.departureTime.getTime();
      return lastStop?.arrivalTime.getTime() ?? p.startTime.getTime();
    }));

    const totalSpan = endMs - startMs;
    if (totalSpan <= 0) return [];

    // Find first whole hour at or after start
    const startDate = new Date(startMs);
    const firstHour = new Date(startDate);
    firstHour.setMinutes(0, 0, 0);
    if (firstHour.getTime() < startMs) {
      firstHour.setHours(firstHour.getHours() + 1);
    }

    const ticks: HourTick[] = [];
    const current = new Date(firstHour);
    while (current.getTime() <= endMs) {
      const leftPercent = ((current.getTime() - startMs) / totalSpan) * 100;
      if (leftPercent >= 2 && leftPercent <= 98) {
        const hours = current.getHours();
        const ampm = hours >= 12 ? 'p' : 'a';
        const displayHour = hours === 0 ? 12 : hours > 12 ? hours - 12 : hours;
        ticks.push({
          leftPercent,
          label: `${displayHour}${ampm}`,
        });
      }
      current.setHours(current.getHours() + 1);
    }

    return ticks;
  });

  segmentBgClass(type: JourneyPhaseType | 'gap'): string {
    switch (type) {
      case 'morning_commute': return 'bg-blue-400';
      case 'office':          return 'bg-green-500';
      case 'evening_commute': return 'bg-amber-400';
      case 'home':            return 'bg-gray-300';
      case 'gap':             return 'bg-gray-200';
      default:                return 'bg-gray-300';
    }
  }

  private formatDur(seconds: number): string {
    const h = Math.floor(seconds / 3600);
    const m = Math.floor((seconds % 3600) / 60);
    if (h === 0) return `${m}m`;
    if (m === 0) return `${h}h`;
    return `${h}h ${m}m`;
  }
}
