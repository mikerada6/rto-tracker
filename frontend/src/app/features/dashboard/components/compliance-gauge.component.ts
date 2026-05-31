import { Component, input, computed } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { PeriodStats } from '../../../core/models/dashboard.model';
import { getComplianceStatus } from '../../../core/utils/compliance-status.util';

@Component({
  selector: 'app-compliance-gauge',
  imports: [DecimalPipe],
  template: `
    <div class="bg-white rounded-2xl p-6 shadow-sm border border-gray-100 h-full flex flex-col items-center justify-center">
      <h3 class="text-sm font-medium text-gray-500 mb-4">Quarter Progress</h3>

      <!-- SVG Gauge -->
      <div class="relative w-48 h-48">
        <svg viewBox="0 0 120 120" class="w-full h-full -rotate-90">
          <!-- Background circle -->
          <circle cx="60" cy="60" r="50" fill="none" stroke="#e5e7eb" stroke-width="10" />
          <!-- Progress arc -->
          <circle
            cx="60" cy="60" r="50"
            fill="none"
            [attr.stroke]="gaugeColor()"
            stroke-width="10"
            stroke-linecap="round"
            [attr.stroke-dasharray]="circumference"
            [attr.stroke-dashoffset]="dashOffset()"
            class="transition-all duration-700 ease-out"
          />
        </svg>
        <!-- Center text -->
        <div class="absolute inset-0 flex flex-col items-center justify-center">
          <span class="text-3xl font-bold" [style.color]="gaugeColor()">
            {{ quarter().daysInOffice }}
          </span>
          <span class="text-xs text-gray-500">of {{ target() }} days</span>
        </div>
      </div>

      <!-- Stats below gauge -->
      <div class="mt-4 text-center">
        <p class="text-sm text-gray-600">
          {{ quarter().averageDaysPerWeek | number:'1.1-1' }} days/week average
        </p>
        <p class="text-xs text-gray-400 mt-1">
          Target: {{ requiredAvg() | number:'1.1-1' }} days/week
        </p>
      </div>
    </div>
  `
})
export class ComplianceGaugeComponent {
  readonly quarter = input.required<PeriodStats>();
  readonly requiredAvg = input.required<number>();

  readonly circumference = 2 * Math.PI * 50; // ~314.16

  readonly target = computed(() => {
    const q = this.quarter();
    const start = new Date(q.periodStart);
    const end = new Date(q.periodEnd);
    const totalDays = (end.getTime() - start.getTime()) / (1000 * 60 * 60 * 24) + 1;
    const totalWeeks = totalDays / 7;
    return Math.ceil(this.requiredAvg() * totalWeeks);
  });

  readonly progress = computed(() => {
    const t = this.target();
    if (t === 0) return 1;
    return Math.min(this.quarter().daysInOffice / t, 1);
  });

  readonly dashOffset = computed(() => {
    return this.circumference * (1 - this.progress());
  });

  readonly gaugeColor = computed(() => {
    const status = getComplianceStatus(this.quarter(), this.requiredAvg());
    switch (status) {
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
