import { Component, input, computed } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { PeriodStats } from '../../../core/models/dashboard.model';
import { getComplianceStatus, getStatusLabel, ComplianceStatus } from '../../../core/utils/compliance-status.util';

@Component({
  selector: 'app-period-card',
  imports: [DecimalPipe],
  template: `
    <div class="bg-white rounded-xl p-5 shadow-sm border border-gray-100">
      <!-- Header -->
      <div class="flex items-center justify-between mb-3">
        <h3 class="text-sm font-medium text-gray-500">{{ label() }}</h3>
        <span
          class="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium"
          [class]="badgeClasses()"
        >
          {{ statusLabel() }}
        </span>
      </div>

      <!-- Main stat -->
      <div class="flex items-baseline gap-1 mb-1">
        <span class="text-3xl font-bold text-gray-900">{{ stats().daysInOffice }}</span>
        <span class="text-sm text-gray-500">days in office</span>
      </div>

      <!-- Average -->
      <p class="text-sm text-gray-600 mb-3">
        {{ stats().averageDaysPerWeek | number:'1.1-1' }} days/week avg
      </p>

      <!-- Progress bar -->
      <div class="w-full bg-gray-100 rounded-full h-2 mb-3">
        <div
          class="h-2 rounded-full transition-all duration-500"
          [style.width.%]="progressPercent()"
          [style.background-color]="progressColor()"
        ></div>
      </div>

      <!-- Footer stats -->
      @if (stats().weeksRemaining > 0) {
        <div class="flex justify-between text-xs text-gray-500">
          <span>{{ stats().daysStillNeeded }} days needed</span>
          <span>{{ stats().weeksRemaining | number:'1.1-1' }} weeks left</span>
        </div>
      } @else {
        <div class="text-xs text-gray-500">
          Period complete
        </div>
      }
    </div>
  `
})
export class PeriodCardComponent {
  readonly label = input.required<string>();
  readonly stats = input.required<PeriodStats>();
  readonly requiredAvg = input.required<number>();

  readonly status = computed(() => getComplianceStatus(this.stats(), this.requiredAvg()));
  readonly statusLabel = computed(() => getStatusLabel(this.status()));

  readonly badgeClasses = computed(() => {
    switch (this.status()) {
      case 'on-track':
      case 'compliant':
        return 'bg-green-100 text-green-700';
      case 'behind':
        return 'bg-amber-100 text-amber-700';
      case 'critical':
      case 'non-compliant':
        return 'bg-red-100 text-red-700';
    }
  });

  readonly progressPercent = computed(() => {
    const s = this.stats();
    const start = new Date(s.periodStart);
    const end = new Date(s.periodEnd);
    const totalDays = (end.getTime() - start.getTime()) / (1000 * 60 * 60 * 24) + 1;
    const totalWeeks = totalDays / 7;
    const target = Math.ceil(this.requiredAvg() * totalWeeks);
    if (target === 0) return 100;
    return Math.min((s.daysInOffice / target) * 100, 100);
  });

  readonly progressColor = computed(() => {
    switch (this.status()) {
      case 'on-track':
      case 'compliant':
        return '#16a34a';
      case 'behind':
        return '#d97706';
      case 'critical':
      case 'non-compliant':
        return '#dc2626';
    }
  });
}
