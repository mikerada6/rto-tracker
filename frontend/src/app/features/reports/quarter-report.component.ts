import { Component, OnInit, signal, computed, ViewChild, ElementRef, inject, effect } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { Chart, BarController, BarElement, CategoryScale, LinearScale, Tooltip, Legend } from 'chart.js';
import annotationPlugin from 'chartjs-plugin-annotation';
import { ReportService, AvailablePeriod } from '../../core/services/report.service';
import { QuarterReportResponse } from '../../core/models/report.model';
import { SkeletonComponent } from '../../shared/components/skeleton.component';
import { ThemeService } from '../../core/services/theme.service';

Chart.register(BarController, BarElement, CategoryScale, LinearScale, Tooltip, Legend, annotationPlugin);

@Component({
  selector: 'app-quarter-report',
  imports: [DecimalPipe, SkeletonComponent],
  template: `
    <div class="space-y-6">
      <div class="flex items-center justify-between">
        <h1 class="text-xl font-semibold text-gray-900">Quarterly Report</h1>
      </div>

      <!-- Quarter selector -->
      <div class="bg-white rounded-2xl shadow-sm border border-gray-100 p-4">
        <div class="flex items-center gap-4">
          <select
            [value]="selectedYear()"
            (change)="onYearChange($any($event.target).value)"
            class="px-3 py-2 border border-gray-300 rounded-lg text-sm text-gray-900 bg-white focus:ring-2 focus:ring-blue-500 focus:border-blue-500 outline-none"
          >
            @for (y of availableYears(); track y) {
              <option [value]="y">{{ y }}</option>
            }
          </select>
          <div class="flex rounded-lg border border-gray-300 overflow-hidden">
            @for (q of availableQuartersForYear(); track q) {
              <button
                (click)="onQuarterChange(q)"
                class="px-4 py-2 text-sm font-medium transition-colors"
                [class]="selectedQuarter() === q ? 'bg-blue-600 text-white' : 'bg-white text-gray-700 hover:bg-gray-50'"
              >
                {{ q }}
              </button>
            }
          </div>
        </div>
      </div>

      @if (loading()) {
        <!-- Skeleton -->
        <div class="bg-white rounded-2xl shadow-sm border border-gray-100 p-6 space-y-6">
          <div class="flex items-center justify-between">
            <div class="space-y-2">
              <app-skeleton class="h-5 w-40" />
              <app-skeleton class="h-3 w-56" />
            </div>
            <app-skeleton class="h-7 w-24" />
          </div>
          <div class="grid grid-cols-2 gap-6">
            <div class="space-y-2"><app-skeleton class="h-3 w-28" /><app-skeleton class="h-8 w-16" /></div>
            <div class="space-y-2"><app-skeleton class="h-3 w-28" /><app-skeleton class="h-8 w-16" /></div>
          </div>
          <app-skeleton class="h-48 w-full" />
          <div class="space-y-3">
            @for (i of [1,2,3]; track i) {
              <div class="flex justify-between">
                <app-skeleton class="h-4 w-24" />
                <app-skeleton class="h-4 w-10" />
                <app-skeleton class="h-4 w-10" />
              </div>
            }
          </div>
        </div>
      } @else if (report()) {
        <!-- Summary card -->
        <div class="bg-white rounded-2xl shadow-sm border border-gray-100 p-6">
          <div class="flex items-center justify-between mb-6">
            <div>
              <h2 class="text-lg font-semibold text-gray-900">{{ report()!.period }}</h2>
              <p class="text-sm text-gray-500">{{ report()!.periodStart }} to {{ report()!.periodEnd }}</p>
            </div>
            <span
              class="px-3 py-1.5 rounded-full text-sm font-medium"
              [class]="report()!.isCompliant ? 'bg-green-100 text-green-700' : 'bg-red-100 text-red-700'"
            >
              {{ report()!.isCompliant ? '✓ Compliant' : '✕ Non-Compliant' }}
            </span>
          </div>

          <div class="grid grid-cols-2 gap-6 mb-6">
            <div>
              <p class="text-sm text-gray-500">Total Office Days</p>
              <p class="text-3xl font-bold text-gray-900">{{ report()!.daysInOffice }}</p>
            </div>
            <div>
              <p class="text-sm text-gray-500">Average Days/Week</p>
              <p class="text-3xl font-bold text-gray-900">{{ report()!.averageDaysPerWeek | number:'1.1-1' }}</p>
            </div>
          </div>

          <!-- Bar chart -->
          <h3 class="text-sm font-medium text-gray-500 mb-3">Monthly Office Days</h3>
          <div class="relative h-48 mb-6">
            <canvas #barChart></canvas>
          </div>

          <!-- Monthly breakdown -->
          <h3 class="text-sm font-medium text-gray-500 mb-3">Monthly Breakdown</h3>
          <div class="overflow-x-auto">
            <table class="w-full text-sm">
              <thead>
                <tr class="border-b border-gray-200">
                  <th class="text-left py-2 text-gray-500 font-medium">Month</th>
                  <th class="text-right py-2 text-gray-500 font-medium">Days in Office</th>
                  <th class="text-right py-2 text-gray-500 font-medium">Avg Days/Week</th>
                </tr>
              </thead>
              <tbody>
                @for (month of report()!.monthlyBreakdown; track month.month) {
                  <tr class="border-b border-gray-50">
                    <td class="py-3 font-medium text-gray-900">{{ month.month }}</td>
                    <td class="py-3 text-right text-gray-700">{{ month.daysInOffice }}</td>
                    <td class="py-3 text-right text-gray-700">{{ month.averageDaysPerWeek | number:'1.1-1' }}</td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        </div>
      } @else if (error()) {
        <div class="bg-red-50 border border-red-200 rounded-lg p-6 text-center">
          <p class="text-red-700">{{ error() }}</p>
          <button (click)="loadReport()" class="mt-3 text-sm text-red-600 underline">Retry</button>
        </div>
      } @else if (!loading() && availableYears().length === 0) {
        <div class="bg-gray-50 border border-gray-200 rounded-lg p-6 text-center">
          <p class="text-gray-500">No report data available yet. Events will appear here once recorded.</p>
        </div>
      }
    </div>
  `
})
export class QuarterReportComponent implements OnInit {
  @ViewChild('barChart') barChartRef!: ElementRef<HTMLCanvasElement>;

  readonly loading = signal(true);
  readonly error = signal('');
  readonly report = signal<QuarterReportResponse | null>(null);

  readonly selectedYear = signal(new Date().getFullYear());
  readonly selectedQuarter = signal(this.getCurrentQuarter());

  readonly availablePeriods = signal<AvailablePeriod[]>([]);

  readonly availableYears = computed(() =>
    [...new Set(this.availablePeriods().map(p => p.year))].sort((a, b) => a - b)
  );

  readonly availableQuartersForYear = computed(() => {
    const year = this.selectedYear();
    return this.availablePeriods()
      .filter(p => p.year === year)
      .map(p => `Q${p.quarter}`)
      .sort();
  });

  private chart: Chart | null = null;
  private readonly themeService = inject(ThemeService);

  constructor(
    private reportService: ReportService,
    private route: ActivatedRoute,
    private router: Router
  ) {
    effect(() => {
      this.themeService.isDark();
      if (this.report()) {
        setTimeout(() => this.renderChart(), 0);
      }
    });
  }

  ngOnInit(): void {
    const yearParam = this.route.snapshot.paramMap.get('year');
    const quarterParam = this.route.snapshot.paramMap.get('quarter');
    if (yearParam && quarterParam) {
      this.selectedYear.set(parseInt(yearParam));
      this.selectedQuarter.set(quarterParam);
    }

    this.reportService.getAvailablePeriods().subscribe({
      next: (periods) => {
        this.availablePeriods.set(periods);
        if (periods.length === 0) {
          this.loading.set(false);
          return;
        }
        // If the initially selected year/quarter isn't in the available list,
        // default to the last available period.
        const quarters = this.availableQuartersForYear();
        if (quarters.length === 0 || !quarters.includes(this.selectedQuarter())) {
          const last = periods[periods.length - 1];
          this.selectedYear.set(last.year);
          this.selectedQuarter.set(`Q${last.quarter}`);
        }
        this.loadReport();
      },
      error: () => {
        // Fall back to just loading the report anyway
        this.loadReport();
      }
    });
  }

  loadReport(): void {
    this.loading.set(true);
    this.error.set('');
    this.chart?.destroy();
    this.chart = null;
    this.reportService.getQuarterReport(this.selectedYear(), this.selectedQuarter()).subscribe({
      next: (data) => {
        this.report.set(data);
        this.loading.set(false);
        // Defer chart render to next tick so Angular has time to stamp the
        // @if(report()) block into the DOM before we look for the canvas.
        setTimeout(() => this.renderChart(), 0);
      },
      error: () => {
        this.error.set('Failed to load report.');
        this.loading.set(false);
      }
    });
  }

  private renderChart(): void {
    const r = this.report();
    if (!r || !this.barChartRef) return;
    this.chart?.destroy();
    const dark = this.themeService.isDark();
    const textColor = dark ? 'rgba(255,255,255,0.7)' : undefined;
    const gridColor = dark ? 'rgba(255,255,255,0.08)' : 'rgba(0,0,0,0.05)';
    const labels = r.monthlyBreakdown.map(m => m.month);
    const data = r.monthlyBreakdown.map(m => m.daysInOffice);
    const targetAvg = r.averageDaysPerWeek;
    // Compute per-month target days: avg * weeks in month (approx monthDays/7 * required)
    // We'll just draw a flat reference line at the quarter avg days/week * ~4.3 weeks
    const targetDays = parseFloat((targetAvg * 4.33).toFixed(1));

    this.chart = new Chart(this.barChartRef.nativeElement, {
      type: 'bar',
      data: {
        labels,
        datasets: [{
          label: 'Days in Office',
          data,
          backgroundColor: data.map(d => d >= targetDays ? 'rgba(34,197,94,0.7)' : 'rgba(239,68,68,0.7)'),
          borderRadius: 4,
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
          legend: { display: false },
          annotation: {
            annotations: {
              targetLine: {
                type: 'line',
                yMin: targetDays,
                yMax: targetDays,
                borderColor: 'rgba(59,130,246,0.8)',
                borderWidth: 2,
                borderDash: [6, 4],
                label: {
                  content: `Target ~${targetDays}d`,
                  display: true,
                  position: 'end',
                  backgroundColor: 'rgba(59,130,246,0.1)',
                  color: 'rgb(59,130,246)',
                  font: { size: 11 }
                }
              }
            }
          }
        },
        scales: {
          y: { beginAtZero: true, ticks: { stepSize: 1, color: textColor }, grid: { color: gridColor } },
          x: { grid: { display: false }, ticks: { color: textColor } }
        }
      }
    });
  }

  onYearChange(year: string): void {
    this.selectedYear.set(parseInt(year));
    // Ensure selected quarter is valid for the new year
    const quarters = this.availableQuartersForYear();
    if (quarters.length > 0 && !quarters.includes(this.selectedQuarter())) {
      this.selectedQuarter.set(quarters[quarters.length - 1]);
    }
    this.updateUrl();
    this.loadReport();
  }

  onQuarterChange(quarter: string): void {
    this.selectedQuarter.set(quarter);
    this.updateUrl();
    this.loadReport();
  }

  private updateUrl(): void {
    this.router.navigate(
      ['/reports', this.selectedYear(), this.selectedQuarter()],
      { replaceUrl: true }
    );
  }

  private getCurrentQuarter(): string {
    const month = new Date().getMonth();
    if (month < 3) return 'Q1';
    if (month < 6) return 'Q2';
    if (month < 9) return 'Q3';
    return 'Q4';
  }
}
