import { Component, input, computed } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { PeriodStats } from '../../../core/models/dashboard.model';
import { getComplianceStatus, getStatusLabel, ComplianceStatus } from '../../../core/utils/compliance-status.util';

@Component({
  selector: 'app-days-needed-banner',
  imports: [DecimalPipe],
  template: `
    <div
      class="rounded-2xl p-6 text-white"
      [class]="bannerClasses()"
    >
      <div class="flex items-start justify-between">
        <div>
          <h2 class="text-2xl sm:text-3xl font-bold">
            @if (quarter().daysStillNeeded === 0 && quarter().weeksRemaining <= 0) {
              Quarter Complete
            } @else if (quarter().daysStillNeeded === 0) {
              You're on track!
            } @else {
              You need {{ quarter().daysStillNeeded }} more
              {{ quarter().daysStillNeeded === 1 ? 'day' : 'days' }}
              this quarter
            }
          </h2>
          <p class="mt-2 text-white/80 text-sm sm:text-base">
            @if (quarter().weeksRemaining > 0) {
              Averaging {{ quarter().averageDaysPerWeek | number:'1.1-1' }} days/week
              @if (quarter().requiredAvgForRemainder !== null) {
                — need {{ quarter().requiredAvgForRemainder! | number:'1.1-1' }}/week
                for the remaining {{ quarter().weeksRemaining | number:'1.1-1' }} weeks
              }
            } @else {
              Final average: {{ quarter().averageDaysPerWeek | number:'1.1-1' }} days/week
            }
          </p>
        </div>
        <div class="flex-shrink-0 ml-4">
          <span
            class="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full text-sm font-medium bg-white/20"
          >
            @switch (status()) {
              @case ('on-track') { ✓ On Track }
              @case ('behind') { ⚠ Behind }
              @case ('critical') { ✕ At Risk }
              @case ('compliant') { ✓ Compliant }
              @case ('non-compliant') { ✕ Non-Compliant }
            }
          </span>
        </div>
      </div>

      @if (status() === 'critical' && quarter().requiredAvgForRemainder !== null && quarter().requiredAvgForRemainder! > 5) {
        <div class="mt-3 p-3 bg-white/10 rounded-lg text-sm">
          Reaching the target requires {{ quarter().requiredAvgForRemainder! | number:'1.1-1' }} days/week,
          which exceeds the maximum possible (5 days/week).
        </div>
      }
    </div>
  `
})
export class DaysNeededBannerComponent {
  readonly quarter = input.required<PeriodStats>();
  readonly requiredAvg = input.required<number>();

  readonly status = computed(() => getComplianceStatus(this.quarter(), this.requiredAvg()));

  readonly bannerClasses = computed(() => {
    switch (this.status()) {
      case 'on-track':
      case 'compliant':
        return 'bg-gradient-to-r from-green-600 to-green-500';
      case 'behind':
        return 'bg-gradient-to-r from-amber-600 to-amber-500';
      case 'critical':
      case 'non-compliant':
        return 'bg-gradient-to-r from-red-600 to-red-500';
    }
  });
}
