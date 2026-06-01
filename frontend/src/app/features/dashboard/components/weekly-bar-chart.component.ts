import { Component, Input, OnInit, OnChanges, signal, ViewChild, inject, effect } from '@angular/core';
import { BaseChartDirective } from 'ng2-charts';
import { ChartConfiguration, ChartData } from 'chart.js';
import { CalendarService } from '../../../core/services/calendar.service';
import { AuditDayEntry } from '../../../core/models/audit.model';
import { PeriodStats } from '../../../core/models/dashboard.model';
import { ThemeService } from '../../../core/services/theme.service';
import { startOfISOWeek, getISOWeek, format, parseISO } from 'date-fns';

interface WeekBucket {
  weekLabel: string;
  daysInOffice: number;
}

@Component({
  selector: 'app-weekly-bar-chart',
  imports: [BaseChartDirective],
  template: `
    @if (loading()) {
      <div class="bg-white rounded-lg shadow p-6">
        <div class="flex items-center justify-center py-10">
          <div class="animate-spin rounded-full h-6 w-6 border-b-2 border-blue-600"></div>
        </div>
      </div>
    } @else if (errorMsg()) {
      <div class="bg-white rounded-lg shadow p-6">
        <p class="text-sm text-gray-500 text-center">{{ errorMsg() }}</p>
      </div>
    } @else {
      <div class="bg-white rounded-lg shadow p-6">
        <h3 class="text-sm font-medium text-gray-700 mb-4">Days in Office per Week (This Quarter)</h3>
        <div class="relative" style="height: 250px;">
          <canvas baseChart
            [data]="chartData"
            [options]="chartOptions"
            [type]="'bar'">
          </canvas>
        </div>
      </div>
    }
  `
})
export class WeeklyBarChartComponent implements OnInit, OnChanges {
  @Input({ required: true }) quarter!: PeriodStats;
  @Input({ required: true }) requiredAvg!: number;

  @ViewChild(BaseChartDirective) chart?: BaseChartDirective;

  readonly loading = signal(true);
  readonly errorMsg = signal('');

  chartData: ChartData<'bar'> = { labels: [], datasets: [] };
  chartOptions: ChartConfiguration<'bar'>['options'] = {};

  private readonly themeService = inject(ThemeService);

  constructor(private calendarService: CalendarService) {
    effect(() => {
      // Re-render chart when theme changes
      this.themeService.isDark();
      if (!this.loading() && this.chartData.labels?.length) {
        this.loadData();
      }
    });
  }

  ngOnInit(): void {
    this.loadData();
  }

  ngOnChanges(): void {
    this.loadData();
  }

  private loadData(): void {
    if (!this.quarter) return;

    this.loading.set(true);
    this.errorMsg.set('');

    this.calendarService.getAudit(this.quarter.periodStart, this.quarter.periodEnd).subscribe({
      next: (res) => {
        const buckets = this.groupByWeek(res.days);
        this.buildChart(buckets);
        this.loading.set(false);
      },
      error: () => {
        this.errorMsg.set('Unable to load weekly data.');
        this.loading.set(false);
      }
    });
  }

  private groupByWeek(days: AuditDayEntry[]): WeekBucket[] {
    const weekMap = new Map<string, WeekBucket>();

    for (const day of days) {
      const date = parseISO(day.date);
      const weekStart = startOfISOWeek(date);
      const key = format(weekStart, 'yyyy-MM-dd');

      if (!weekMap.has(key)) {
        const weekNum = getISOWeek(date);
        weekMap.set(key, {
          weekLabel: `W${weekNum}`,
          daysInOffice: 0
        });
      }

      if (day.totalOfficeTimeSeconds > 0) {
        weekMap.get(key)!.daysInOffice++;
      }
    }

    // Sort by week start date
    return Array.from(weekMap.entries())
      .sort(([a], [b]) => a.localeCompare(b))
      .map(([, bucket]) => bucket);
  }

  private buildChart(buckets: WeekBucket[]): void {
    const target = this.requiredAvg;
    const dark = this.themeService.isDark();
    const textColor = dark ? 'rgba(255,255,255,0.7)' : undefined;
    const gridColor = dark ? 'rgba(255,255,255,0.1)' : 'rgba(0,0,0,0.1)';

    const labels = buckets.map(b => b.weekLabel);
    const data = buckets.map(b => b.daysInOffice);
    const colors = data.map(d => d >= target ? '#22c55e' : '#f59e0b');

    this.chartData = {
      labels,
      datasets: [{
        data,
        backgroundColor: colors,
        borderRadius: 4,
        barPercentage: 0.7
      }]
    };

    this.chartOptions = {
      responsive: true,
      maintainAspectRatio: false,
      plugins: {
        legend: { display: false },
        annotation: undefined as any
      },
      scales: {
        y: {
          min: 0,
          max: 5,
          ticks: { stepSize: 1, color: textColor },
          title: { display: true, text: 'Days', color: textColor },
          grid: { color: gridColor }
        },
        x: {
          title: { display: false },
          ticks: { color: textColor },
          grid: { color: gridColor }
        }
      }
    };

    // Add annotation plugin reference line if available
    (this.chartOptions as any).plugins = {
      ...(this.chartOptions as any).plugins,
      annotation: {
        annotations: {
          targetLine: {
            type: 'line',
            yMin: target,
            yMax: target,
            borderColor: '#6366f1',
            borderWidth: 2,
            borderDash: [6, 4],
            label: {
              display: true,
              content: `Target (${target})`,
              position: 'end',
              backgroundColor: '#6366f1',
              color: '#fff',
              font: { size: 10 }
            }
          }
        }
      }
    };

    // Trigger chart update
    setTimeout(() => this.chart?.update(), 0);
  }
}
