import { Component, signal, computed, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ReportService, ReportPeriod } from '../../../core/services/report.service';

interface PeriodOption { value: ReportPeriod; label: string; }

@Component({
  selector: 'app-report-export-panel',
  imports: [FormsModule],
  template: `
    <div class="bg-white rounded-2xl shadow-sm border border-gray-100 p-6">
      <div class="flex items-start justify-between mb-4">
        <div>
          <h2 class="text-lg font-semibold text-gray-900">Export Report</h2>
          <p class="text-sm text-gray-500">Download a printable PDF for management.</p>
        </div>
      </div>

      <div class="flex flex-wrap items-center gap-2 mb-4">
        @for (opt of periods; track opt.value) {
          <button
            type="button"
            (click)="selectPeriod(opt.value)"
            class="px-4 py-2 text-sm font-medium rounded-lg border transition-colors"
            [class]="period() === opt.value
              ? 'bg-blue-600 text-white border-blue-600'
              : 'bg-white text-gray-700 border-gray-300 hover:bg-gray-50'"
          >{{ opt.label }}</button>
        }
      </div>

      @if (period() === 'CUSTOM') {
        <div class="flex flex-wrap items-end gap-3 mb-4">
          <div>
            <label class="block text-xs font-medium text-gray-500 mb-1">From</label>
            <input
              type="date"
              [(ngModel)]="from"
              [max]="to() || todayStr"
              class="px-3 py-2 border border-gray-300 rounded-lg text-sm text-gray-900 bg-white focus:ring-2 focus:ring-blue-500 focus:border-blue-500 outline-none"
            />
          </div>
          <div>
            <label class="block text-xs font-medium text-gray-500 mb-1">To</label>
            <input
              type="date"
              [(ngModel)]="to"
              [min]="from()"
              [max]="todayStr"
              class="px-3 py-2 border border-gray-300 rounded-lg text-sm text-gray-900 bg-white focus:ring-2 focus:ring-blue-500 focus:border-blue-500 outline-none"
            />
          </div>
          @if (customRangeError()) {
            <p class="text-sm text-red-600 self-center">{{ customRangeError() }}</p>
          }
        </div>
      }

      <div class="flex items-center gap-3">
        <button
          type="button"
          (click)="exportPdf()"
          [disabled]="!canExport() || exporting()"
          class="inline-flex items-center gap-2 px-4 py-2 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
        >
          @if (exporting()) {
            <span class="inline-block w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin"></span>
            <span>Generating…</span>
          } @else {
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor" class="w-4 h-4">
              <path fill-rule="evenodd" d="M10 3a.75.75 0 0 1 .75.75v8.69l2.97-2.97a.75.75 0 1 1 1.06 1.06l-4.25 4.25a.75.75 0 0 1-1.06 0L5.22 10.53a.75.75 0 1 1 1.06-1.06l2.97 2.97V3.75A.75.75 0 0 1 10 3Zm-6.75 12.5a.75.75 0 0 1 .75.75v.25c0 .414.336.75.75.75h10.5a.75.75 0 0 0 .75-.75v-.25a.75.75 0 0 1 1.5 0v.25A2.25 2.25 0 0 1 15.25 18.5H4.75A2.25 2.25 0 0 1 2.5 16.25v-.25a.75.75 0 0 1 .75-.75Z" clip-rule="evenodd" />
            </svg>
            <span>Export PDF</span>
          }
        </button>
        @if (errorMessage()) {
          <p class="text-sm text-red-600">{{ errorMessage() }}</p>
        }
      </div>
    </div>
  `
})
export class ReportExportPanelComponent {
  private readonly reportService = inject(ReportService);

  readonly periods: PeriodOption[] = [
    { value: 'WEEK', label: 'This week' },
    { value: 'MONTH', label: 'This month' },
    { value: 'QUARTER', label: 'This quarter' },
    { value: 'YEAR', label: 'This year' },
    { value: 'CUSTOM', label: 'Custom' }
  ];

  readonly todayStr = new Date().toISOString().slice(0, 10);

  readonly period = signal<ReportPeriod>('MONTH');
  readonly from = signal('');
  readonly to = signal('');
  readonly exporting = signal(false);
  readonly errorMessage = signal('');

  readonly customRangeError = computed(() => {
    if (this.period() !== 'CUSTOM') return '';
    const f = this.from();
    const t = this.to();
    if (!f || !t) return '';
    if (new Date(t) < new Date(f)) return 'End date is before start date.';
    const diffDays = Math.round((new Date(t).getTime() - new Date(f).getTime()) / 86400000) + 1;
    if (diffDays > 365) return 'Range cannot exceed 365 days.';
    return '';
  });

  readonly canExport = computed(() => {
    if (this.period() !== 'CUSTOM') return true;
    return !!this.from() && !!this.to() && !this.customRangeError();
  });

  selectPeriod(value: ReportPeriod): void {
    this.period.set(value);
    this.errorMessage.set('');
  }

  exportPdf(): void {
    this.errorMessage.set('');
    this.exporting.set(true);
    const from = this.period() === 'CUSTOM' ? this.from() : undefined;
    const to = this.period() === 'CUSTOM' ? this.to() : undefined;

    this.reportService.exportPdf(this.period(), from, to).subscribe({
      next: (response) => {
        const blob = response.body;
        if (!blob) {
          this.errorMessage.set('Empty response from server.');
          this.exporting.set(false);
          return;
        }
        const filename = this.filenameFromHeader(
          response.headers.get('Content-Disposition')
        ) ?? `rto-report-${this.period().toLowerCase()}.pdf`;
        this.triggerDownload(blob, filename);
        this.exporting.set(false);
      },
      error: () => {
        this.errorMessage.set('Failed to generate report. Please try again.');
        this.exporting.set(false);
      }
    });
  }

  private filenameFromHeader(header: string | null): string | null {
    if (!header) return null;
    const match = /filename="?([^";]+)"?/i.exec(header);
    return match ? match[1] : null;
  }

  private triggerDownload(blob: Blob, filename: string): void {
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    window.URL.revokeObjectURL(url);
  }
}
