import { Component, input, output, signal, computed, effect } from '@angular/core';
import { DatePipe, NgClass } from '@angular/common';
import { FormsModule } from '@angular/forms';
import {
  CommuteAnnotation,
  CommuteAnnotationCategory,
  COMMUTE_ANNOTATION_CATEGORIES,
} from '../../../core/models/commute-annotation.model';

export interface CommuteAnnotationDialogResult {
  category: CommuteAnnotationCategory;
  note: string | null;
}

@Component({
  selector: 'app-commute-annotation-dialog',
  imports: [DatePipe, FormsModule, NgClass],
  template: `
    @if (open()) {
      <div class="fixed inset-0 bg-black/40 z-50 flex items-end sm:items-center justify-center p-4"
           (click)="cancel.emit()">
        <div class="bg-white rounded-2xl shadow-xl w-full max-w-sm overflow-hidden"
             (click)="$event.stopPropagation()">

          <div class="px-5 py-4 border-b border-gray-100">
            <p class="text-xs text-gray-400 font-medium uppercase tracking-wide">
              {{ editing() ? 'Edit gap label' : 'Label this gap' }}
            </p>
            <h3 class="font-bold text-gray-900 text-base mt-0.5">
              {{ start() | date:'HH:mm' }} → {{ end() | date:'HH:mm' }}
              <span class="text-gray-400 text-sm font-normal">· {{ durationLabel() }}</span>
            </h3>
          </div>

          <div class="px-5 py-4 space-y-3">
            <div>
              <p class="text-[11px] font-semibold uppercase tracking-wide text-gray-500 mb-2">Category</p>
              <div class="grid grid-cols-2 gap-2">
                @for (c of categories; track c.value) {
                  <button type="button"
                          (click)="selected.set(c.value)"
                          [ngClass]="selected() === c.value
                            ? 'bg-blue-600 text-white border-blue-600'
                            : 'bg-white text-gray-700 border-gray-200 hover:border-blue-300'"
                          class="flex items-center gap-2 px-3 py-2 rounded-lg border text-sm font-medium transition-all">
                    <span>{{ c.icon }}</span>
                    <span>{{ c.label }}</span>
                  </button>
                }
              </div>
            </div>

            <div>
              <label class="text-[11px] font-semibold uppercase tracking-wide text-gray-500 block mb-1">
                Note <span class="text-gray-300 font-normal">(optional)</span>
              </label>
              <textarea [ngModel]="noteText()" (ngModelChange)="noteText.set($event)"
                        rows="2"
                        maxlength="1000"
                        placeholder="e.g. team happy hour at Frankford Hall"
                        class="w-full px-3 py-2 rounded-lg border border-gray-200 text-sm focus:outline-none focus:border-blue-400"></textarea>
            </div>
          </div>

          <div class="px-5 py-3 bg-gray-50 flex items-center justify-between gap-2">
            @if (editing()) {
              <button type="button"
                      (click)="delete.emit()"
                      class="text-xs text-rose-600 hover:text-rose-800 font-medium">
                Remove
              </button>
            } @else {
              <span></span>
            }
            <div class="flex items-center gap-2">
              <button type="button"
                      (click)="cancel.emit()"
                      class="px-3 py-1.5 text-sm font-medium text-gray-600 hover:text-gray-800">
                Cancel
              </button>
              <button type="button"
                      (click)="submit()"
                      class="px-3 py-1.5 text-sm font-semibold rounded-lg bg-blue-600 text-white hover:bg-blue-700">
                {{ editing() ? 'Save' : 'Label' }}
              </button>
            </div>
          </div>
        </div>
      </div>
    }
  `,
})
export class CommuteAnnotationDialogComponent {
  open = input.required<boolean>();
  start = input.required<Date>();
  end = input.required<Date>();
  existing = input<CommuteAnnotation | null>(null);

  save = output<CommuteAnnotationDialogResult>();
  delete = output<void>();
  cancel = output<void>();

  readonly categories = COMMUTE_ANNOTATION_CATEGORIES;
  readonly selected = signal<CommuteAnnotationCategory>('SOCIAL');
  readonly noteText = signal('');

  readonly editing = computed(() => this.existing() !== null);
  readonly durationLabel = computed(() => {
    const secs = Math.max(0, (this.end().getTime() - this.start().getTime()) / 1000);
    const h = Math.floor(secs / 3600);
    const m = Math.floor((secs % 3600) / 60);
    if (h === 0) return `${m}m`;
    if (m === 0) return `${h}h`;
    return `${h}h ${m}m`;
  });

  constructor() {
    effect(() => {
      // Re-seed form state whenever the dialog opens (or its inputs change).
      if (this.open()) {
        const ex = this.existing();
        this.selected.set(ex?.category ?? 'SOCIAL');
        this.noteText.set(ex?.note ?? '');
      }
    });
  }

  submit(): void {
    this.save.emit({
      category: this.selected(),
      note: this.noteText().trim() ? this.noteText().trim() : null,
    });
  }
}
