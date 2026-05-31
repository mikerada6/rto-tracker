import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { NavSidebarComponent } from './nav-sidebar.component';
import { MobileNavComponent } from './mobile-nav.component';
import { ToastComponent } from './toast.component';
import { ThemeService } from '../../core/services/theme.service';

@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, NavSidebarComponent, MobileNavComponent, ToastComponent],
  template: `
    <!-- Offline banner -->
    @if (offline()) {
      <div class="fixed top-0 left-0 right-0 z-[300] bg-gray-800 text-white text-center text-sm py-2 px-4 flex items-center justify-center gap-2" role="alert">
        <svg class="w-4 h-4 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M18.364 5.636a9 9 0 010 12.728M15.536 8.464a5 5 0 010 7.072M12 12h.01M8.464 15.536a5 5 0 010-7.072M5.636 18.364a9 9 0 010-12.728" />
        </svg>
        Unable to connect. Check your connection.
      </div>
    }

    <div class="flex h-screen" [class.pt-8]="offline()">
      <!-- Desktop sidebar -->
      <app-nav-sidebar class="hidden lg:flex" [collapsed]="sidebarCollapsed()" (toggleCollapse)="sidebarCollapsed.set(!sidebarCollapsed())" />

      <!-- Main content -->
      <main class="flex-1 overflow-y-auto bg-gray-50">
        <div class="max-w-7xl mx-auto px-4 lg:px-6 py-4 lg:py-6 pb-20 lg:pb-6">
          <router-outlet />
        </div>
      </main>

      <!-- Mobile bottom nav -->
      <app-mobile-nav class="lg:hidden" />
    </div>

    <!-- Global toast container -->
    <app-toast />
  `
})
export class AppShellComponent implements OnInit, OnDestroy {
  readonly sidebarCollapsed = signal(false);
  readonly offline = signal(false);

  // Inject to ensure ThemeService initialises and applies the stored preference
  private readonly themeService = inject(ThemeService);

  private onOnline = () => this.offline.set(false);
  private onOffline = () => this.offline.set(true);

  ngOnInit(): void {
    this.offline.set(!navigator.onLine);
    window.addEventListener('online', this.onOnline);
    window.addEventListener('offline', this.onOffline);
  }

  ngOnDestroy(): void {
    window.removeEventListener('online', this.onOnline);
    window.removeEventListener('offline', this.onOffline);
  }
}
