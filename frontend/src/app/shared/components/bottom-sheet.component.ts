import { Component, input, output } from '@angular/core';

@Component({
  selector: 'app-bottom-sheet',
  imports: [],
  template: `
    @if (open()) {
      <!-- Backdrop -->
      <div
        class="fixed inset-0 bg-black/40 z-40 lg:hidden"
        (click)="close.emit()"
      ></div>

      <!-- Sheet -->
      <div
        class="fixed bottom-0 left-0 right-0 z-50 lg:hidden bg-white rounded-t-2xl shadow-xl
               max-h-[85vh] overflow-y-auto
               animate-slide-up"
        (touchstart)="onTouchStart($event)"
        (touchmove)="onTouchMove($event)"
        (touchend)="onTouchEnd($event)"
      >
        <!-- Drag handle -->
        <div class="flex justify-center pt-3 pb-2">
          <div class="w-10 h-1 rounded-full bg-gray-300"></div>
        </div>

        <!-- Close button -->
        <button
          (click)="close.emit()"
          class="absolute top-3 right-4 p-1 text-gray-400 hover:text-gray-600"
          aria-label="Close"
        >
          <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
          </svg>
        </button>

        <!-- Content -->
        <div class="px-4 pb-6">
          <ng-content />
        </div>
      </div>
    }
  `,
  styles: [`
    .animate-slide-up {
      animation: slideUp 250ms ease-out;
    }
    @keyframes slideUp {
      from { transform: translateY(100%); }
      to { transform: translateY(0); }
    }
  `]
})
export class BottomSheetComponent {
  open = input.required<boolean>();
  close = output<void>();

  private touchStartY = 0;
  private currentTranslateY = 0;

  onTouchStart(e: TouchEvent): void {
    this.touchStartY = e.touches[0].clientY;
  }

  onTouchMove(e: TouchEvent): void {
    const deltaY = e.touches[0].clientY - this.touchStartY;
    if (deltaY > 0) {
      this.currentTranslateY = deltaY;
      const sheet = (e.currentTarget as HTMLElement);
      sheet.style.transform = `translateY(${deltaY}px)`;
    }
  }

  onTouchEnd(e: TouchEvent): void {
    const sheet = (e.currentTarget as HTMLElement);
    if (this.currentTranslateY > 150) {
      this.close.emit();
    }
    sheet.style.transform = '';
    this.currentTranslateY = 0;
  }
}

