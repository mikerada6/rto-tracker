import { Component, OnInit, signal, computed, HostListener } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DatePipe } from '@angular/common';
import { EventService } from '../../core/services/event.service';
import { ZoneService } from '../../core/services/zone.service';
import { ZoneEventResponse } from '../../core/models/event.model';
import { ZoneResponse } from '../../core/models/zone.model';
import { format, subDays } from 'date-fns';
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
      <div class="bg-white rounded-xl shadow-sm border border-gray-100 p-4">
        <div class="flex flex-wrap items-end gap-4">
          <div>
            <label class="block text-xs text-gray-500 mb-1">Start Date</label>
            <input type="date" [(ngModel)]="startDate" (change)="loadEvents()"
              class="px-3 py-2 border border-gray-300 rounded-lg text-sm text-gray-900 bg-white focus:ring-2 focus:ring-blue-500 outline-none" />
          </div>
          <div>
            <label class="block text-xs text-gray-500 mb-1">End Date</label>
            <input type="date" [(ngModel)]="endDate" (change)="loadEvents()"
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
                  <th class="text-left px-4 py-3 text-gray-500 font-medium">Timestamp</th>
                  <th class="text-left px-4 py-3 text-gray-500 font-medium">Zone</th>
                  <th class="text-left px-4 py-3 text-gray-500 font-medium">Type</th>
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
                  </tr>
                } @empty {
                  <tr>
                    <td colspan="3" class="px-4 py-12 text-center text-gray-400">
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
              <div class="px-4 py-3 flex items-center justify-between">
                <div>
                  <p class="text-sm font-medium text-gray-900">{{ event.zoneName }}</p>
                  <p class="text-xs text-gray-400 font-mono">{{ event.timestamp | date:'MMM d, yyyy HH:mm' }}</p>
                </div>
                <span
                  class="px-2 py-0.5 rounded-full text-xs font-medium"
                  [class]="event.eventType === 'ENTER' ? 'bg-green-100 text-green-700' : 'bg-red-100 text-red-700'"
                >
                  {{ event.eventType }}
                </span>
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

  pageSize = 25;
  zoneMapping: Record<string, string> = {};

  startDate = format(subDays(new Date(), 30), 'yyyy-MM-dd');
  endDate = format(new Date(), 'yyyy-MM-dd');
  selectedZoneId = '';
  selectedType = '';

  readonly totalPages = computed(() => Math.max(1, Math.ceil(this.events().length / this.pageSize)));
  readonly pageStart = computed(() => this.currentPage() * this.pageSize);
  readonly pageEnd = computed(() => Math.min(this.pageStart() + this.pageSize, this.events().length));
  readonly pagedEvents = computed(() => this.events().slice(this.pageStart(), this.pageEnd()));

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
    if (e.key === 'Escape') this.showUpload.set(false);
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
        this.toast.success(`Imported ${result.importedCount} events`);
      },
      error: () => {
        this.uploading.set(false);
        this.toast.error('CSV upload failed.');
      }
    });
  }
}
