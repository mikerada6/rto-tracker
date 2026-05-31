import { Component, inject } from '@angular/core';
import { ToastService, Toast } from '../services/toast.service';

@Component({
  selector: 'app-toast',
  template: `
    <div
      class="fixed bottom-4 right-4 z-[200] flex flex-col gap-2 pointer-events-none"
      aria-live="polite"
      aria-atomic="false"
    >
      @for (toast of toastService.toasts(); track toast.id) {
        <div
          class="flex items-center gap-3 px-4 py-3 rounded-xl shadow-lg text-sm font-medium max-w-sm pointer-events-auto transition-all duration-300"
          [class]="getClasses(toast)"
          role="alert"
        >
          <span class="flex-shrink-0 text-base">{{ getIcon(toast.type) }}</span>
          <span class="flex-1">{{ toast.message }}</span>
          <button
            (click)="toastService.dismiss(toast.id)"
            class="flex-shrink-0 opacity-70 hover:opacity-100 transition-opacity ml-1"
            aria-label="Dismiss"
          >✕</button>
        </div>
      }
    </div>
  `
})
export class ToastComponent {
  readonly toastService = inject(ToastService);

  getClasses(toast: Toast): string {
    switch (toast.type) {
      case 'success': return 'bg-green-600 text-white';
      case 'error':   return 'bg-red-600 text-white';
      case 'warning': return 'bg-amber-500 text-white';
      case 'info':    return 'bg-blue-600 text-white';
    }
  }

  getIcon(type: Toast['type']): string {
    switch (type) {
      case 'success': return '✓';
      case 'error':   return '✕';
      case 'warning': return '⚠';
      case 'info':    return 'ℹ';
    }
  }
}

