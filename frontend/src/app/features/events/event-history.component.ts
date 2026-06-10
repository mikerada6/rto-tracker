import { Component, OnInit, signal, computed, HostListener } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DatePipe } from '@angular/common';
import { EventService } from '../../core/services/event.service';
import { ZoneService } from '../../core/services/zone.service';
import { ZoneEventResponse } from '../../core/models/event.model';
import { ZoneResponse } from '../../core/models/zone.model';
import { format, subDays, startOfMonth, startOfQuarter } from 'date-fns';
import { ToastService } from '../../shared/services/toast.service';
import { SkeletonComponent } from '../../shared/components/skeleton.component';

@Component({
  selector: 'app-event-history',
  imports: [FormsModule, DatePipe, SkeletonComponent],
  template: `
    <div class="space-y-6">
      <div class="flex items-center justify-between">
        <h1 class="text-xl font-semibold text-gray-900">Event History</h1>
        <button
          (click)="openUpload()"
          class="px-4 py-2 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 transition-colors"
        >
          Import CSV
        </button>
      </div>

      <!-- Filters -->
      <div class="bg-white rounded-xl shadow-sm border border-gray-100 p-4 space-y-3">
        <!-- Quick date presets -->
        <div class="flex flex-wrap items-center gap-2">
          <span class="text-xs text-gray-500 mr-1">Quick range:</span>
          @for (preset of datePresets; track preset.key) {
            <button
              type="button"
              (click)="applyDatePreset(preset.key)"
              [attr.aria-pressed]="activePreset() === preset.key"
              class="px-2.5 py-1 text-xs font-medium rounded-full border transition-colors focus-visible:ring-2 focus-visible:ring-blue-500 focus-visible:ring-offset-1"
              [class.bg-blue-600]="activePreset() === preset.key"
              [class.text-white]="activePreset() === preset.key"
              [class.border-blue-600]="activePreset() === preset.key"
              [class.bg-white]="activePreset() !== preset.key"
              [class.text-gray-700]="activePreset() !== preset.key"
              [class.border-gray-300]="activePreset() !== preset.key"
              [class.hover:bg-gray-50]="activePreset() !== preset.key"
            >
              {{ preset.label }}
            </button>
          }
        </div>

        <div class="flex flex-wrap items-end gap-4">
          <div>
            <label class="block text-xs text-gray-500 mb-1">Start Date</label>
            <input type="date" [(ngModel)]="startDate" (change)="onDateRangeChange()"
              class="px-3 py-2 border border-gray-300 rounded-lg text-sm text-gray-900 bg-white focus:ring-2 focus:ring-blue-500 outline-none" />
          </div>
          <div>
            <label class="block text-xs text-gray-500 mb-1">End Date</label>
            <input type="date" [(ngModel)]="endDate" (change)="onDateRangeChange()"
              class="px-3 py-2 border border-gray-300 rounded-lg text-sm text-gray-900 bg-white focus:ring-2 focus:ring-blue-500 outline-none" />
          </div>
          <div>
            <label class="block text-xs text-gray-500 mb-1">Zone</label>
            <select [(ngModel)]="selectedZoneId" (change)="loadEvents()"
              class="px-3 py-2 border border-gray-300 rounded-lg text-sm text-gray-900 bg-white focus:ring-2 focus:ring-blue-500 outline-none">
              <option value="">All Zones</option>
              @for (zone of zones(); track zone.id) {
                <option [value]="zone.id">{{ zone.name }}</option>
              }
            </select>
          </div>
          <div>
            <label class="block text-xs text-gray-500 mb-1">Type</label>
            <select [(ngModel)]="selectedType" (change)="loadEvents()"
              class="px-3 py-2 border border-gray-300 rounded-lg text-sm text-gray-900 bg-white focus:ring-2 focus:ring-blue-500 outline-none">
              <option value="">All</option>
              <option value="ENTER">Enter</option>
              <option value="EXIT">Exit</option>
            </select>
          </div>
        </div>
      </div>

      <!-- Events table -->
      @if (loading()) {
        <!-- Skeleton table rows -->
        <div class="bg-white rounded-xl shadow-sm border border-gray-100 overflow-hidden">
          <div class="space-y-0 divide-y divide-gray-50">
            @for (i of [1,2,3,4,5,6,7,8]; track i) {
              <div class="px-4 py-3 flex gap-4 items-center">
                <app-skeleton class="h-4 w-36" />
                <app-skeleton class="h-4 w-24" />
                <app-skeleton class="h-5 w-12" />
              </div>
            }
          </div>
        </div>
      } @else {
        <div class="bg-white rounded-xl shadow-sm border border-gray-100 overflow-hidden">
          <div class="overflow-x-auto hidden sm:block">
            <table class="w-full text-sm">
              <thead class="bg-gray-50">
                <tr>
                  <th class="text-left px-4 py-3 text-gray-500 font-medium">
                    <button
                      type="button"
                      (click)="toggleSort('timestamp')"
                      [attr.aria-sort]="ariaSort('timestamp')"
                      class="inline-flex items-center gap-1 hover:text-gray-900 transition-colors"
                    >
                      Timestamp
                      <span class="text-xs w-3 inline-block" aria-hidden="true">{{ sortIndicator('timestamp') }}</span>
                    </button>
                  </th>
                  <th class="text-left px-4 py-3 text-gray-500 font-medium">
                    <button
                      type="button"
                      (click)="toggleSort('zone')"
                      [attr.aria-sort]="ariaSort('zone')"
                      class="inline-flex items-center gap-1 hover:text-gray-900 transition-colors"
                    >
                      Zone
                      <span class="text-xs w-3 inline-block" aria-hidden="true">{{ sortIndicator('zone') }}</span>
                    </button>
                  </th>
                  <th class="text-left px-4 py-3 text-gray-500 font-medium">
                    <button
                      type="button"
                      (click)="toggleSort('type')"
                      [attr.aria-sort]="ariaSort('type')"
                      class="inline-flex items-center gap-1 hover:text-gray-900 transition-colors"
                    >
                      Type
                      <span class="text-xs w-3 inline-block" aria-hidden="true">{{ sortIndicator('type') }}</span>
                    </button>
                  </th>
                  <th class="text-right px-4 py-3 text-gray-500 font-medium w-12">
                    <span class="sr-only">Actions</span>
                  </th>
                </tr>
              </thead>
              <tbody>
                @for (event of pagedEvents(); track event.id) {
                  <tr class="border-t border-gray-50 hover:bg-gray-50">
                    <td class="px-4 py-3 font-mono text-xs text-gray-700">{{ event.timestamp | date:'MMM d, yyyy HH:mm:ss' }}</td>
                    <td class="px-4 py-3 text-gray-800">{{ event.zoneName }}</td>
                    <td class="px-4 py-3">
                      <span
                        class="px-2 py-0.5 rounded-full text-xs font-medium"
                        [class]="event.eventType === 'ENTER' ? 'bg-green-100 text-green-700' : 'bg-red-100 text-red-700'"
                      >
                        {{ event.eventType }}
                      </span>
                    </td>
                    <td class="px-4 py-3 text-right">
                      <button
                        type="button"
                        (click)="confirmDelete(event)"
                        [attr.aria-label]="'Delete event ' + event.eventType + ' at ' + event.zoneName"
                        class="p-1.5 rounded-md text-gray-400 hover:text-red-600 hover:bg-red-50 transition-colors"
                      >
                        <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                            d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6M1 7h22M9 7V4a1 1 0 011-1h4a1 1 0 011 1v3" />
                        </svg>
                      </button>
                    </td>
                  </tr>
                } @empty {
                  <tr>
                    <td colspan="4" class="px-4 py-12 text-center text-gray-400">
                      <svg class="w-10 h-10 mx-auto mb-2 text-gray-300" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
                      </svg>
                      No events found for these filters.<br><span class="text-xs">Try adjusting the date range.</span>
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          </div>

          <!-- Card layout for mobile (< sm) -->
          <div class="sm:hidden divide-y divide-gray-100">
            @for (event of pagedEvents(); track event.id) {
              <div class="px-4 py-3 flex items-center justify-between gap-3">
                <div class="min-w-0 flex-1">
                  <p class="text-sm font-medium text-gray-900 truncate">{{ event.zoneName }}</p>
                  <p class="text-xs text-gray-400 font-mono">{{ event.timestamp | date:'MMM d, yyyy HH:mm' }}</p>
                </div>
                <span
                  class="px-2 py-0.5 rounded-full text-xs font-medium"
                  [class]="event.eventType === 'ENTER' ? 'bg-green-100 text-green-700' : 'bg-red-100 text-red-700'"
                >
                  {{ event.eventType }}
                </span>
                <button
                  type="button"
                  (click)="confirmDelete(event)"
                  [attr.aria-label]="'Delete event ' + event.eventType + ' at ' + event.zoneName"
                  class="p-1.5 rounded-md text-gray-400 hover:text-red-600 hover:bg-red-50 transition-colors"
                >
                  <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                      d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6M1 7h22M9 7V4a1 1 0 011-1h4a1 1 0 011 1v3" />
                  </svg>
                </button>
              </div>
            } @empty {
              <div class="px-4 py-12 text-center text-gray-400">
                <svg class="w-10 h-10 mx-auto mb-2 text-gray-300" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
                </svg>
                No events found
              </div>
            }
          </div>

          <!-- Pagination footer -->
          @if (events().length > 0) {
            <div class="px-4 py-3 border-t border-gray-100 flex flex-wrap items-center justify-between gap-3 bg-gray-50 text-sm text-gray-600">
              <div class="flex items-center gap-2">
                <span>Rows per page:</span>
                <select [(ngModel)]="pageSize" (change)="onPageSizeChange()"
                  class="px-2 py-1 border border-gray-300 rounded text-sm text-gray-900 bg-white outline-none">
                  <option [value]="25">25</option>
                  <option [value]="50">50</option>
                  <option [value]="100">100</option>
                </select>
              </div>
              <div class="flex items-center gap-1">
                <span class="mr-2">{{ pageStart() + 1 }}–{{ pageEnd() }} of {{ events().length }}</span>
                <button (click)="prevPage()" [disabled]="currentPage() === 0"
                  class="px-2 py-1 rounded border border-gray-300 hover:bg-gray-100 disabled:opacity-40 transition-colors">‹</button>
                <button (click)="nextPage()" [disabled]="currentPage() >= totalPages() - 1"
                  class="px-2 py-1 rounded border border-gray-300 hover:bg-gray-100 disabled:opacity-40 transition-colors">›</button>
              </div>
            </div>
          }
        </div>
      }

      <!-- Delete confirmation dialog -->
      @if (eventToDelete()) {
        <div class="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4" role="dialog" aria-modal="true" aria-label="Delete event">
          <div class="bg-white rounded-2xl p-6 w-full max-w-sm shadow-xl">
            <div class="flex items-center gap-3 mb-4">
              <span class="text-2xl">⚠️</span>
              <h2 class="text-lg font-semibold text-gray-900">Delete Event?</h2>
            </div>
            <p class="text-sm text-gray-600 mb-2">
              Delete this event? The day's commute and office stats will recompute without it.
            </p>
            <div class="text-sm text-gray-700 bg-gray-50 rounded-lg px-3 py-2 mb-6 space-y-1">
              <p><span class="text-gray-500">When:</span> <span class="font-mono">{{ eventToDelete()!.timestamp | date:'MMM d, yyyy HH:mm:ss' }}</span></p>
              <p><span class="text-gray-500">Zone:</span> {{ eventToDelete()!.zoneName }}</p>
              <p><span class="text-gray-500">Type:</span>
                <span
                  class="ml-1 px-2 py-0.5 rounded-full text-xs font-medium"
                  [class]="eventToDelete()!.eventType === 'ENTER' ? 'bg-green-100 text-green-700' : 'bg-red-100 text-red-700'"
                >{{ eventToDelete()!.eventType }}</span>
              </p>
            </div>
            <div class="flex justify-end gap-3">
              <button (click)="eventToDelete.set(null)"
                class="px-4 py-2 text-sm text-gray-700 hover:bg-gray-100 rounded-lg transition-colors">Cancel</button>
              <button (click)="executeDelete()"
                class="px-4 py-2 bg-red-600 text-white text-sm font-medium rounded-lg hover:bg-red-700 transition-colors">Delete</button>
            </div>
          </div>
        </div>
      }

      <!-- Upload dialog -->
      @if (showUpload()) {
        <div
          class="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4"
          (click)="showUpload.set(false)"
          role="dialog"
          aria-modal="true"
          aria-label="Import CSV"
        >
          <div class="bg-white rounded-2xl p-6 w-full max-w-lg" (click)="$event.stopPropagation()">
            <h2 class="text-lg font-semibold text-gray-900 mb-4">Import CSV</h2>
            <div class="space-y-4">
              <!-- Format documentation -->
              <details class="rounded-lg border border-gray-200 bg-gray-50 text-sm">
                <summary class="px-3 py-2 cursor-pointer font-medium text-gray-700 hover:bg-gray-100 rounded-lg list-none flex items-center justify-between">
                  <span>Expected CSV format</span>
                  <span class="text-xs text-gray-400">click to expand</span>
                </summary>
                <div class="px-3 pb-3 pt-1 text-xs text-gray-600 space-y-2">
                  <p>Header row required. Four comma-separated columns in this order:</p>
                  <pre class="bg-white border border-gray-200 rounded p-2 font-mono text-[11px] overflow-x-auto"><span class="text-gray-400">Date,Time,Zone,Type
</span>6/10/26,9:02:14,Home,Departed
6/10/26,9:47:30,Office,Arrived
6/10/26,17:32:05,Office,Departed</pre>
                  <ul class="list-disc pl-4 space-y-1 text-gray-600">
                    <li><span class="font-medium text-gray-700">Date</span> — <code class="text-[11px]">M/d/yy</code> e.g. <code class="text-[11px]">6/10/26</code></li>
                    <li><span class="font-medium text-gray-700">Time</span> — <code class="text-[11px]">H:mm:ss</code> in your account timezone, 24-hour, e.g. <code class="text-[11px]">17:32:05</code></li>
                    <li><span class="font-medium text-gray-700">Zone</span> — any label; you map each unique value to a real zone below</li>
                    <li><span class="font-medium text-gray-700">Type</span> — must be <code class="text-[11px]">Arrived</code> or <code class="text-[11px]">Departed</code> (case-insensitive)</li>
                  </ul>
                  <p class="text-gray-500">Duplicate events are skipped — re-running the same file is safe.</p>
                  <button
                    type="button"
                    (click)="downloadSampleCsv(); $event.stopPropagation()"
                    class="inline-flex items-center gap-1 text-blue-600 hover:text-blue-800 font-medium"
                  >
                    <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 16v2a2 2 0 002 2h12a2 2 0 002-2v-2M7 10l5 5 5-5M12 15V3"/>
                    </svg>
                    Download sample CSV
                  </button>
                </div>
              </details>

              <!-- File drop -->
              <div
                class="border-2 border-dashed border-gray-300 rounded-lg p-8 text-center"
                (dragover)="$event.preventDefault()"
                (drop)="onFileDrop($event)"
              >
                <input type="file" accept=".csv" (change)="onFileSelect($event)" class="hidden" #fileInput />
                <button (click)="fileInput.click()" class="text-blue-600 hover:text-blue-800 font-medium text-sm">
                  {{ uploadFile() ? uploadFile()!.name : 'Choose a CSV file or drag and drop' }}
                </button>
              </div>

              <!-- Progress bar -->
              @if (uploading()) {
                <div class="w-full bg-gray-200 rounded-full h-1.5">
                  <div class="bg-blue-600 h-1.5 rounded-full animate-pulse w-full"></div>
                </div>
                <p class="text-xs text-gray-500 text-center">Uploading…</p>
              }

              <!-- Zone mapping editor -->
              @if (uploadFile() && csvZoneNames().length > 0 && !uploadResult()) {
                <div>
                  <h3 class="text-sm font-medium text-gray-700 mb-2">Zone Mapping</h3>
                  <p class="text-xs text-gray-500 mb-3">Map CSV zone names to system zones (leave blank to skip).</p>
                  <div class="space-y-2 max-h-48 overflow-y-auto">
                    @for (csvName of csvZoneNames(); track csvName) {
                      <div class="flex items-center gap-3">
                        <span class="text-sm text-gray-700 font-mono flex-1 truncate">{{ csvName }}</span>
                        <span class="text-gray-400">→</span>
                        <select [(ngModel)]="zoneMapping[csvName]" [name]="'map_' + csvName"
                          class="px-2 py-1 border border-gray-300 rounded text-sm text-gray-900 bg-white focus:ring-2 focus:ring-blue-500 outline-none">
                          <option value="">Skip</option>
                          @for (zone of zones(); track zone.id) {
                            <option [value]="zone.externalId">{{ zone.name }}</option>
                          }
                        </select>
                      </div>
                    }
                  </div>
                </div>
              }

              <!-- Result -->
              @if (uploadResult()) {
                <div class="p-3 bg-green-50 border border-green-200 rounded-lg text-sm text-green-700">
                  Imported {{ uploadResult()!.importedCount }} of {{ uploadResult()!.totalRows }} events.
                  @if (uploadResult()!.skippedCount > 0) {
                    {{ uploadResult()!.skippedCount }} skipped.
                  }
                </div>
                @if (uploadResult()!.errors?.length > 0) {
                  <div class="p-3 bg-red-50 border border-red-200 rounded-lg text-xs text-red-700 space-y-1 max-h-36 overflow-y-auto">
                    @for (err of uploadResult()!.errors; track $index) {
                      <p>Row {{ err.row }}: {{ err.reason }}</p>
                    }
                  </div>
                }
              }

              <div class="flex justify-end gap-3">
                <button (click)="showUpload.set(false)"
                  class="px-4 py-2 text-sm text-gray-700 hover:bg-gray-100 rounded-lg transition-colors">Close</button>
                <button
                  (click)="uploadCsv()"
                  [disabled]="!uploadFile() || uploading()"
                  class="px-4 py-2 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 disabled:opacity-50 transition-colors"
                >
                  {{ uploading() ? 'Uploading...' : 'Upload' }}
                </button>
              </div>
            </div>
          </div>
        </div>
      }
    </div>
  `
})
export class EventHistoryComponent implements OnInit {
  readonly events = signal<ZoneEventResponse[]>([]);
  readonly zones = signal<ZoneResponse[]>([]);
  readonly loading = signal(true);
  readonly showUpload = signal(false);
  readonly uploadFile = signal<File | null>(null);
  readonly uploading = signal(false);
  readonly uploadResult = signal<any>(null);
  readonly csvZoneNames = signal<string[]>([]);
  readonly currentPage = signal(0);
  readonly eventToDelete = signal<ZoneEventResponse | null>(null);
  readonly sortColumn = signal<'timestamp' | 'zone' | 'type' | null>(null);
  readonly sortDirection = signal<'asc' | 'desc'>('asc');

  pageSize = 25;
  zoneMapping: Record<string, string> = {};

  startDate = format(subDays(new Date(), 30), 'yyyy-MM-dd');
  endDate = format(new Date(), 'yyyy-MM-dd');
  selectedZoneId = '';
  selectedType = '';

  readonly datePresets = [
    { key: 'today', label: 'Today' },
    { key: 'last7', label: 'Last 7 days' },
    { key: 'last30', label: 'Last 30 days' },
    { key: 'month', label: 'This month' },
    { key: 'quarter', label: 'This quarter' },
  ] as const;

  readonly activePreset = signal<string | null>('last30');

  readonly sortedEvents = computed(() => {
    const col = this.sortColumn();
    if (!col) return this.events();
    const dir = this.sortDirection() === 'asc' ? 1 : -1;
    return [...this.events()].sort((a, b) => {
      let av: string | number;
      let bv: string | number;
      switch (col) {
        case 'timestamp':
          av = new Date(a.timestamp).getTime();
          bv = new Date(b.timestamp).getTime();
          break;
        case 'zone':
          av = (a.zoneName ?? '').toLowerCase();
          bv = (b.zoneName ?? '').toLowerCase();
          break;
        case 'type':
          av = a.eventType;
          bv = b.eventType;
          break;
      }
      if (av < bv) return -1 * dir;
      if (av > bv) return 1 * dir;
      return 0;
    });
  });

  readonly totalPages = computed(() => Math.max(1, Math.ceil(this.sortedEvents().length / this.pageSize)));
  readonly pageStart = computed(() => this.currentPage() * this.pageSize);
  readonly pageEnd = computed(() => Math.min(this.pageStart() + this.pageSize, this.sortedEvents().length));
  readonly pagedEvents = computed(() => this.sortedEvents().slice(this.pageStart(), this.pageEnd()));

  constructor(
    private eventService: EventService,
    private zoneService: ZoneService,
    private toast: ToastService
  ) {}

  ngOnInit(): void {
    this.zoneService.list().subscribe({ next: (z) => this.zones.set(z) });
    this.loadEvents();
  }

  @HostListener('window:keydown', ['$event'])
  onKeydown(e: KeyboardEvent): void {
    if (e.key === 'Escape') {
      this.showUpload.set(false);
      this.eventToDelete.set(null);
    }
  }

  confirmDelete(event: ZoneEventResponse): void {
    this.eventToDelete.set(event);
  }

  executeDelete(): void {
    const event = this.eventToDelete();
    if (!event) return;
    this.eventToDelete.set(null);
    this.eventService.delete(event.id).subscribe({
      next: () => {
        this.loadEvents();
        this.toast.success('Event deleted');
      },
      error: () => this.toast.error('Failed to delete event.')
    });
  }

  applyDatePreset(key: string): void {
    const now = new Date();
    let start: Date;
    switch (key) {
      case 'today':   start = now; break;
      case 'last7':   start = subDays(now, 6); break;
      case 'last30':  start = subDays(now, 29); break;
      case 'month':   start = startOfMonth(now); break;
      case 'quarter': start = startOfQuarter(now); break;
      default: return;
    }
    this.startDate = format(start, 'yyyy-MM-dd');
    this.endDate = format(now, 'yyyy-MM-dd');
    this.activePreset.set(key);
    this.loadEvents();
  }

  onDateRangeChange(): void {
    this.activePreset.set(null);
    this.loadEvents();
  }

  downloadSampleCsv(): void {
    const sample =
      'Date,Time,Zone,Type\n' +
      '6/10/26,9:02:14,Home,Departed\n' +
      '6/10/26,9:47:30,Office,Arrived\n' +
      '6/10/26,17:32:05,Office,Departed\n';
    const blob = new Blob([sample], { type: 'text/csv;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'rto-tracker-sample.csv';
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
  }

  loadEvents(): void {
    this.loading.set(true);
    this.currentPage.set(0);
    this.eventService.list(
      this.startDate,
      this.endDate,
      this.selectedZoneId || undefined,
      this.selectedType || undefined
    ).subscribe({
      next: (events) => {
        this.events.set(events);
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });
  }

  prevPage(): void {
    if (this.currentPage() > 0) this.currentPage.update(p => p - 1);
  }

  nextPage(): void {
    if (this.currentPage() < this.totalPages() - 1) this.currentPage.update(p => p + 1);
  }

  onPageSizeChange(): void {
    this.currentPage.set(0);
  }

  toggleSort(column: 'timestamp' | 'zone' | 'type'): void {
    if (this.sortColumn() !== column) {
      this.sortColumn.set(column);
      this.sortDirection.set('asc');
    } else if (this.sortDirection() === 'asc') {
      this.sortDirection.set('desc');
    } else {
      this.sortColumn.set(null);
      this.sortDirection.set('asc');
    }
    this.currentPage.set(0);
  }

  sortIndicator(column: 'timestamp' | 'zone' | 'type'): string {
    if (this.sortColumn() !== column) return '';
    return this.sortDirection() === 'asc' ? '▲' : '▼';
  }

  ariaSort(column: 'timestamp' | 'zone' | 'type'): 'ascending' | 'descending' | 'none' {
    if (this.sortColumn() !== column) return 'none';
    return this.sortDirection() === 'asc' ? 'ascending' : 'descending';
  }

  openUpload(): void {
    this.uploadFile.set(null);
    this.uploadResult.set(null);
    this.csvZoneNames.set([]);
    this.zoneMapping = {};
    this.showUpload.set(true);
  }

  onFileSelect(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files?.length) {
      this.setFile(input.files[0]);
    }
  }

  onFileDrop(event: DragEvent): void {
    event.preventDefault();
    if (event.dataTransfer?.files.length) {
      this.setFile(event.dataTransfer.files[0]);
    }
  }

  private setFile(file: File): void {
    this.uploadFile.set(file);
    this.uploadResult.set(null);
    this.zoneMapping = {};
    // Parse CSV header to extract zone names for mapping
    const reader = new FileReader();
    reader.onload = (e) => {
      const text = e.target?.result as string;
      const lines = text.split('\n').filter(l => l.trim());
      const zoneSet = new Set<string>();
      // assume header row; look for a "zone" or "externalId" column
      if (lines.length > 1) {
        const headers = lines[0].split(',').map(h => h.trim().toLowerCase());
        const zoneIdx = headers.findIndex(h => h.includes('zone') || h.includes('external'));
        if (zoneIdx >= 0) {
          for (let i = 1; i < lines.length; i++) {
            const cols = lines[i].split(',');
            if (cols[zoneIdx]?.trim()) zoneSet.add(cols[zoneIdx].trim());
          }
        }
      }
      this.csvZoneNames.set([...zoneSet]);
    };
    reader.readAsText(file);
  }

  uploadCsv(): void {
    const file = this.uploadFile();
    if (!file) return;
    this.uploading.set(true);
    // Build mapping: only include non-empty mappings
    const mapping: Record<string, string> = {};
    for (const [k, v] of Object.entries(this.zoneMapping)) {
      if (v) mapping[k] = v;
    }
    this.eventService.uploadCsv(file, mapping).subscribe({
      next: (result) => {
        this.uploadResult.set(result);
        this.uploading.set(false);
        this.loadEvents();
        if (result.errors?.length > 0) {
          this.toast.error(`Import had ${result.errors.length} row error(s). See details below.`);
        } else {
          this.toast.success(`Imported ${result.importedCount} events`);
        }
      },
      error: (err) => {
        this.uploading.set(false);
        const message = err.error?.message || err.message || 'Unknown error';
        this.toast.error(`CSV upload failed: ${message}`);
      }
    });
  }
}
