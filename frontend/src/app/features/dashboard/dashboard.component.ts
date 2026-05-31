import { Component, OnInit, OnDestroy, signal, computed } from '@angular/core';
import { DashboardService } from '../../core/services/dashboard.service';
import { DashboardSummary, PeriodStats } from '../../core/models/dashboard.model';
import { getComplianceStatus, getStatusLabel, ComplianceStatus } from '../../core/utils/compliance-status.util';
import { DaysNeededBannerComponent } from './components/days-needed-banner.component';
import { PeriodCardComponent } from './components/period-card.component';
import { RecentCommutesComponent } from './components/recent-commutes.component';
import { ComplianceGaugeComponent } from './components/compliance-gauge.component';
import { WeeklyBarChartComponent } from './components/weekly-bar-chart.component';
import { SkeletonComponent } from '../../shared/components/skeleton.component';

@Component({
  selector: 'app-dashboard',
  imports: [
    DaysNeededBannerComponent,
    PeriodCardComponent,
    RecentCommutesComponent,
    ComplianceGaugeComponent,
    WeeklyBarChartComponent,
    SkeletonComponent
  ],
  template: `
    @if (loading()) {
      <!-- Dashboard skeleton -->
      <div class="space-y-6">
        <!-- Banner skeleton -->
        <div class="bg-white rounded-2xl shadow-sm border border-gray-100 p-6">
          <app-skeleton class="h-5 w-64 mb-2" />
          <app-skeleton class="h-4 w-48" />
        </div>
        <!-- Gauge + Cards skeleton -->
        <div class="grid grid-cols-1 lg:grid-cols-3 gap-6">
          <div class="bg-white rounded-2xl shadow-sm border border-gray-100 p-6 flex items-center justify-center">
            <app-skeleton shape="circle" class="h-40 w-40" />
          </div>
          <div class="lg:col-span-2 grid grid-cols-1 sm:grid-cols-2 gap-4">
            @for (i of [1,2,3,4]; track i) {
              <div class="bg-white rounded-2xl shadow-sm border border-gray-100 p-5 space-y-3">
                <app-skeleton class="h-4 w-24" />
                <app-skeleton class="h-8 w-16" />
                <app-skeleton class="h-3 w-32" />
              </div>
            }
          </div>
        </div>
        <!-- Chart skeleton -->
        <div class="bg-white rounded-2xl shadow-sm border border-gray-100 p-6">
          <app-skeleton class="h-40 w-full" />
        </div>
      </div>
    } @else if (error()) {
      <div class="bg-red-50 border border-red-200 rounded-lg p-6 text-center">
        <p class="text-red-700">{{ error() }}</p>
        <button (click)="refresh()" class="mt-3 text-sm text-red-600 underline hover:text-red-800">Retry</button>
      </div>
    } @else if (!summary()?.recentCommutes?.length && summary()) {
      <!-- Empty state — no events yet -->
      <div class="flex flex-col items-center justify-center py-20 text-center text-gray-400 space-y-3">
        <svg class="w-16 h-16 text-gray-300" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M3 12l2-2m0 0l7-7 7 7M5 10v10a1 1 0 001 1h3m10-11l2 2m-2-2v10a1 1 0 01-1 1h-3m-6 0a1 1 0 001-1v-4a1 1 0 011-1h2a1 1 0 011 1v4a1 1 0 001 1m-6 0h6" />
        </svg>
        <p class="text-lg font-medium text-gray-500">No events recorded yet</p>
        <p class="text-sm max-w-xs">Set up Home Assistant to start tracking your commutes and office days.</p>
      </div>
    } @else if (summary()) {
      <div class="space-y-6">
        <!-- Header with refresh -->
        <div class="flex items-center justify-between">
          <div class="text-xs text-gray-500">
            @if (lastUpdated()) {
              Last updated: {{ lastUpdated() }}
            }
          </div>
          <button
            (click)="refresh()"
            [disabled]="refreshing()"
            class="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium text-gray-600 bg-white border border-gray-300 rounded-md hover:bg-gray-50 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
          >
            <svg
              class="w-3.5 h-3.5"
              [class.animate-spin]="refreshing()"
              fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2"
            >
              <path stroke-linecap="round" stroke-linejoin="round" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
            </svg>
            {{ refreshing() ? 'Refreshing...' : 'Refresh' }}
          </button>
        </div>

        <!-- Hero Banner -->
        <app-days-needed-banner
          [quarter]="summary()!.quarter"
          [requiredAvg]="summary()!.requiredAveragePerWeek"
        />

        <!-- Gauge + Period Cards -->
        <div class="grid grid-cols-1 lg:grid-cols-3 gap-6">
          <!-- Compliance Gauge -->
          <div class="lg:col-span-1">
            <app-compliance-gauge
              [quarter]="summary()!.quarter"
              [requiredAvg]="summary()!.requiredAveragePerWeek"
            />
          </div>

          <!-- Period Cards -->
          <div class="lg:col-span-2 grid grid-cols-1 sm:grid-cols-2 gap-4">
            @for (period of periods(); track period.label) {
              <app-period-card
                [label]="period.label"
                [stats]="period.stats"
                [requiredAvg]="summary()!.requiredAveragePerWeek"
              />
            }
          </div>
        </div>

        <!-- Weekly Bar Chart -->
        <app-weekly-bar-chart
          [quarter]="summary()!.quarter"
          [requiredAvg]="summary()!.requiredAveragePerWeek"
        />

        <!-- Recent Commutes -->
        @if (summary()!.recentCommutes.length > 0) {
          <app-recent-commutes [commutes]="summary()!.recentCommutes" />
        }
      </div>
    }
  `
})
export class DashboardComponent implements OnInit, OnDestroy {
  readonly loading = signal(true);
  readonly refreshing = signal(false);
  readonly error = signal('');
  readonly summary = signal<DashboardSummary | null>(null);
  readonly lastUpdated = signal('');

  private autoRefreshTimer: ReturnType<typeof setInterval> | null = null;

  readonly periods = computed(() => {
    const s = this.summary();
    if (!s) return [];
    return [
      { label: 'This Week', stats: s.week },
      { label: 'This Month', stats: s.month },
      { label: 'This Quarter', stats: s.quarter },
      { label: 'This Year', stats: s.year }
    ];
  });

  constructor(private dashboardService: DashboardService) {}

  ngOnInit(): void {
    this.loadDashboard();
    this.autoRefreshTimer = setInterval(() => this.refresh(), 5 * 60 * 1000);
  }

  ngOnDestroy(): void {
    if (this.autoRefreshTimer) {
      clearInterval(this.autoRefreshTimer);
    }
  }

  loadDashboard(): void {
    this.loading.set(true);
    this.error.set('');
    this.dashboardService.getSummary().subscribe({
      next: (data) => {
        this.summary.set(data);
        this.loading.set(false);
        this.updateTimestamp();
      },
      error: () => {
        this.error.set('Failed to load dashboard data. Please try again.');
        this.loading.set(false);
      }
    });
  }

  refresh(): void {
    this.refreshing.set(true);
    this.dashboardService.getSummary().subscribe({
      next: (data) => {
        this.summary.set(data);
        this.refreshing.set(false);
        this.updateTimestamp();
      },
      error: () => {
        this.refreshing.set(false);
      }
    });
  }

  private updateTimestamp(): void {
    const now = new Date();
    this.lastUpdated.set(now.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }));
  }
}
