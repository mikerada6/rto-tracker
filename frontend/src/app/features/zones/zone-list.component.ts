import { Component, OnInit, signal, HostListener } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ZoneService } from '../../core/services/zone.service';
import { ZoneResponse, ZoneType, CreateZoneRequest, UpdateZoneRequest } from '../../core/models/zone.model';
import { ToastService } from '../../shared/services/toast.service';
import { SkeletonComponent } from '../../shared/components/skeleton.component';

@Component({
  selector: 'app-zone-list',
  imports: [FormsModule, SkeletonComponent],
  template: `
    <div class="space-y-6">
      <div class="flex items-center justify-between">
        <h1 class="text-xl font-semibold text-gray-900">Zones</h1>
        <button
          (click)="openCreate()"
          class="px-4 py-2 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 transition-colors"
        >
          Add Zone
        </button>
      </div>

      @if (loading()) {
        <!-- Skeleton cards -->
        <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
          @for (i of [1,2,3]; track i) {
            <div class="bg-white rounded-xl p-5 shadow-sm border border-gray-100 space-y-3">
              <div class="flex items-start justify-between">
                <div class="flex items-center gap-2">
                  <app-skeleton class="h-6 w-6" shape="circle" />
                  <app-skeleton class="h-4 w-32" />
                </div>
                <app-skeleton class="h-5 w-12" />
              </div>
              <app-skeleton class="h-3 w-20" />
              <app-skeleton class="h-3 w-28" />
            </div>
          }
        </div>
      } @else {
        <!-- Zone cards -->
        <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
          @for (zone of zones(); track zone.id) {
            <div class="bg-white rounded-xl p-5 shadow-sm border border-gray-100">
              <div class="flex items-start justify-between mb-3">
                <div class="flex items-center gap-2">
                  <span class="text-lg">{{ getZoneIcon(zone.type) }}</span>
                  <h3 class="font-medium text-gray-900">{{ zone.name }}</h3>
                </div>
                <span
                  class="px-2 py-0.5 rounded-full text-xs font-medium"
                  [class]="zone.active ? 'bg-green-100 text-green-700' : 'bg-gray-100 text-gray-500'"
                >
                  {{ zone.active ? 'Active' : 'Inactive' }}
                </span>
              </div>
              <div class="space-y-1 text-sm text-gray-500">
                <p>Type: {{ zone.type }}</p>
                <p class="font-mono text-xs">{{ zone.externalId }}</p>
              </div>
              <div class="mt-4 flex gap-3">
                <button
                  (click)="openEdit(zone)"
                  class="text-xs text-blue-600 hover:text-blue-800 transition-colors"
                >
                  Edit
                </button>
                <button
                  (click)="confirmDelete(zone)"
                  class="text-xs text-red-600 hover:text-red-800 transition-colors"
                >
                  Delete
                </button>
              </div>
            </div>
          } @empty {
            <div class="col-span-full bg-white rounded-xl px-6 py-16 text-center border border-gray-100">
              <div class="mx-auto w-20 h-20 rounded-full bg-blue-50 flex items-center justify-center mb-5">
                <svg class="w-10 h-10 text-blue-500" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M17.657 16.657L13.414 20.9a1.998 1.998 0 01-2.827 0l-4.244-4.243a8 8 0 1111.314 0z"/>
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M15 11a3 3 0 11-6 0 3 3 0 016 0z"/>
                </svg>
              </div>
              <h2 class="text-lg font-semibold text-gray-900 mb-2">No zones yet</h2>
              <p class="text-sm text-gray-500 max-w-md mx-auto mb-6">
                Zones are the locations the tracker watches — your home, office, train station.
                Add your first one to start counting office days.
              </p>
              <button
                (click)="openCreate()"
                class="inline-flex items-center gap-2 px-5 py-2.5 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 transition-colors focus-visible:ring-2 focus-visible:ring-blue-500 focus-visible:ring-offset-2"
              >
                <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4"/>
                </svg>
                Add your first zone
              </button>
            </div>
          }
        </div>
      }

      <!-- Add / Edit zone dialog -->
      @if (showForm()) {
        <div
          class="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4"
          (click)="closeForm()"
          role="dialog"
          aria-modal="true"
          [attr.aria-label]="editingZone() ? 'Edit zone' : 'Add zone'"
        >
          <div class="bg-white rounded-2xl p-6 w-full max-w-md" (click)="$event.stopPropagation()">
            <h2 class="text-lg font-semibold text-gray-900 mb-4">{{ editingZone() ? 'Edit Zone' : 'Add Zone' }}</h2>
            <form (ngSubmit)="submitForm()" class="space-y-4">
              <div>
                <label class="block text-sm font-medium text-gray-700 mb-1">Name</label>
                <input [(ngModel)]="formData.name" name="name" required
                  class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-blue-500 outline-none" />
              </div>
              <div>
                <label class="block text-sm font-medium text-gray-700 mb-1">Type</label>
                <select [(ngModel)]="formData.type" name="type"
                  class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-blue-500 outline-none">
                  <option value="HOME">Home</option>
                  <option value="OFFICE">Office</option>
                  <option value="TRAIN_STATION">Train Station</option>
                  <option value="OTHER">Other</option>
                </select>
              </div>
              <div>
                <label class="block text-sm font-medium text-gray-700 mb-1">External ID</label>
                <input [(ngModel)]="formData.externalId" name="externalId" required placeholder="zone.my_zone"
                  class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-blue-500 outline-none" />
              </div>
              <div class="flex justify-end gap-3 pt-2">
                <button type="button" (click)="closeForm()"
                  class="px-4 py-2 text-sm text-gray-700 hover:bg-gray-100 rounded-lg transition-colors">Cancel</button>
                <button type="submit"
                  class="px-4 py-2 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 transition-colors">
                  {{ editingZone() ? 'Save' : 'Create' }}
                </button>
              </div>
            </form>
          </div>
        </div>
      }

      <!-- Styled delete confirmation dialog -->
      @if (zoneToDelete()) {
        <div class="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4">
          <div class="bg-white rounded-2xl p-6 w-full max-w-sm shadow-xl">
            <div class="flex items-center gap-3 mb-4">
              <span class="text-2xl">⚠️</span>
              <h2 class="text-lg font-semibold text-gray-900">Delete Zone?</h2>
            </div>
            <p class="text-sm text-gray-600 mb-6">
              Are you sure you want to delete <span class="font-semibold text-gray-900">{{ zoneToDelete()!.name }}</span>?
              It will be soft-deleted and can be restored later.
            </p>
            <div class="flex justify-end gap-3">
              <button (click)="zoneToDelete.set(null)"
                class="px-4 py-2 text-sm text-gray-700 hover:bg-gray-100 rounded-lg transition-colors">Cancel</button>
              <button (click)="executeDelete()"
                class="px-4 py-2 bg-red-600 text-white text-sm font-medium rounded-lg hover:bg-red-700 transition-colors">Delete</button>
            </div>
          </div>
        </div>
      }
    </div>
  `
})
export class ZoneListComponent implements OnInit {
  readonly zones = signal<ZoneResponse[]>([]);
  readonly loading = signal(true);
  readonly showForm = signal(false);
  readonly editingZone = signal<ZoneResponse | null>(null);
  readonly zoneToDelete = signal<ZoneResponse | null>(null);

  formData: { name: string; type: ZoneType; externalId: string } = { name: '', type: 'OFFICE', externalId: '' };

  constructor(private zoneService: ZoneService, private toast: ToastService) {}

  ngOnInit(): void {
    this.loadZones();
  }

  @HostListener('window:keydown', ['$event'])
  onKeydown(e: KeyboardEvent): void {
    if (e.key === 'Escape') {
      this.closeForm();
      this.zoneToDelete.set(null);
    }
  }

  loadZones(): void {
    this.loading.set(true);
    this.zoneService.list().subscribe({
      next: (zones) => {
        this.zones.set(zones);
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });
  }

  openCreate(): void {
    this.editingZone.set(null);
    this.formData = { name: '', type: 'OFFICE', externalId: '' };
    this.showForm.set(true);
  }

  openEdit(zone: ZoneResponse): void {
    this.editingZone.set(zone);
    this.formData = { name: zone.name, type: zone.type, externalId: zone.externalId };
    this.showForm.set(true);
  }

  closeForm(): void {
    this.showForm.set(false);
    this.editingZone.set(null);
  }

  submitForm(): void {
    const editing = this.editingZone();
    if (editing) {
      const req: UpdateZoneRequest = { name: this.formData.name, type: this.formData.type, externalId: this.formData.externalId };
      this.zoneService.update(editing.id, req).subscribe({
        next: () => { this.closeForm(); this.loadZones(); this.toast.success('Zone updated'); },
        error: () => this.toast.error('Failed to update zone.')
      });
    } else {
      const req: CreateZoneRequest = { name: this.formData.name, type: this.formData.type, externalId: this.formData.externalId };
      this.zoneService.create(req).subscribe({
        next: () => { this.closeForm(); this.loadZones(); this.toast.success('Zone created'); },
        error: () => this.toast.error('Failed to create zone.')
      });
    }
  }

  confirmDelete(zone: ZoneResponse): void {
    this.zoneToDelete.set(zone);
  }

  executeDelete(): void {
    const zone = this.zoneToDelete();
    if (!zone) return;
    this.zoneToDelete.set(null);
    this.zoneService.delete(zone.id).subscribe({
      next: () => { this.loadZones(); this.toast.success(`"${zone.name}" deleted`); },
      error: () => this.toast.error('Failed to delete zone.')
    });
  }

  getZoneIcon(type: ZoneType): string {
    switch (type) {
      case 'HOME': return '🏠';
      case 'OFFICE': return '🏢';
      case 'TRAIN_STATION': return '🚉';
      case 'OTHER': return '📍';
    }
  }
}
