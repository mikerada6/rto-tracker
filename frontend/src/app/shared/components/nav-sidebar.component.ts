import { Component, inject, input, output } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { ThemeService } from '../../core/services/theme.service';
import { VersionService } from '../../core/services/version.service';

@Component({
  selector: 'app-nav-sidebar',
  imports: [RouterLink, RouterLinkActive],
  template: `
    <aside
      class="h-screen bg-white border-r border-gray-200 flex flex-col transition-all duration-200"
      [class]="collapsed() ? 'w-16' : 'w-60'"
    >
      <!-- Header -->
      <div class="p-4 border-b border-gray-200 flex items-center gap-3">
        <div class="flex-shrink-0 w-8 h-8 rounded-lg overflow-hidden">
          <img src="rto_icon.png" alt="RTO Tracker" class="w-full h-full object-cover" />
        </div>
        @if (!collapsed()) {
          <span class="font-bold text-gray-900">RTO Tracker</span>
        }
      </div>

      <!-- Navigation -->
      <nav class="flex-1 p-2 space-y-1">
        @for (item of navItems; track item.path) {
          <a
            [routerLink]="item.path"
            routerLinkActive="bg-blue-50 text-blue-700 dark:bg-blue-950 dark:text-blue-300"
            [routerLinkActiveOptions]="{ exact: item.path === '/dashboard' }"
            class="flex items-center gap-3 px-3 py-2.5 rounded-lg text-gray-600 hover:bg-gray-100 hover:text-gray-900 transition-colors"
            [title]="item.label"
          >
            <span class="flex-shrink-0" [innerHTML]="item.icon"></span>
            @if (!collapsed()) {
              <span class="text-sm font-medium">{{ item.label }}</span>
            }
          </a>
        }
      </nav>

      <!-- Footer -->
      <div class="p-2 border-t border-gray-200 space-y-1">
        <!-- Version badge -->
        @if (!collapsed()) {
          <div class="px-3 py-1.5 group relative">
            <div class="flex items-center gap-2 text-[10px] text-gray-400 font-mono cursor-default"
                 [title]="'FE: ' + versionService.frontendCommit() + '\nBE: ' + versionService.backendCommit()">
              <svg class="w-3.5 h-3.5 flex-shrink-0 opacity-50" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 10V3L4 14h7v7l9-11h-7z" />
              </svg>
              @if (versionService.frontendCommit() === versionService.backendCommit()) {
                <a [href]="'https://github.com/mikerada6/rto-tracker/commit/' + versionService.frontendCommit()"
                   target="_blank" rel="noopener"
                   class="hover:text-blue-500 transition-colors">
                  {{ versionService.abbreviate(versionService.frontendCommit()) }}
                </a>
              } @else {
                <span>
                  fe:<a [href]="'https://github.com/mikerada6/rto-tracker/commit/' + versionService.frontendCommit()"
                        target="_blank" rel="noopener"
                        class="hover:text-blue-500 transition-colors">{{ versionService.abbreviate(versionService.frontendCommit()) }}</a>
                  be:<a [href]="'https://github.com/mikerada6/rto-tracker/commit/' + versionService.backendCommit()"
                        target="_blank" rel="noopener"
                        class="hover:text-blue-500 transition-colors">{{ versionService.abbreviate(versionService.backendCommit()) }}</a>
                </span>
              }
            </div>
          </div>
        }
        <!-- Dark mode toggle -->
        <button
          (click)="themeService.toggle()"
          class="flex items-center gap-3 w-full px-3 py-2.5 rounded-lg text-gray-600 hover:bg-gray-100 transition-colors"
          [title]="themeService.isDark() ? 'Switch to light mode' : 'Switch to dark mode'"
        >
          @if (themeService.isDark()) {
            <svg class="w-5 h-5 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 3v1m0 16v1m9-9h-1M4 12H3m15.364-6.364l-.707.707M6.343 17.657l-.707.707M17.657 17.657l-.707-.707M6.343 6.343l-.707-.707M16 12a4 4 0 11-8 0 4 4 0 018 0z" />
            </svg>
            @if (!collapsed()) {
              <span class="text-sm font-medium">Light Mode</span>
            }
          } @else {
            <svg class="w-5 h-5 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M20.354 15.354A9 9 0 018.646 3.646 9.003 9.003 0 0012 21a9.003 9.003 0 008.354-5.646z" />
            </svg>
            @if (!collapsed()) {
              <span class="text-sm font-medium">Dark Mode</span>
            }
          }
        </button>
        <!-- Collapse toggle -->
        <button
          (click)="toggleCollapse.emit()"
          class="flex items-center gap-3 w-full px-3 py-2.5 rounded-lg text-gray-600 hover:bg-gray-100 transition-colors"
        >
          <svg class="w-5 h-5 transition-transform" [class.rotate-180]="collapsed()" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M11 19l-7-7 7-7m8 14l-7-7 7-7" />
          </svg>
          @if (!collapsed()) {
            <span class="text-sm font-medium">Collapse</span>
          }
        </button>
        <!-- Logout -->
        <button
          (click)="onLogout()"
          class="flex items-center gap-3 w-full px-3 py-2.5 rounded-lg text-gray-600 hover:bg-red-50 hover:text-red-600 transition-colors"
          title="Logout"
        >
          <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M17 16l4-4m0 0l-4-4m4 4H7m6 4v1a3 3 0 01-3 3H6a3 3 0 01-3-3V7a3 3 0 013-3h4a3 3 0 013 3v1" />
          </svg>
          @if (!collapsed()) {
            <span class="text-sm font-medium">Logout</span>
          }
        </button>
      </div>
    </aside>
  `
})
export class NavSidebarComponent {
  readonly collapsed = input(false);
  readonly toggleCollapse = output();

  readonly navItems = [
    {
      path: '/dashboard',
      label: 'Dashboard',
      icon: '<svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 6a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2H6a2 2 0 01-2-2V6zm10 0a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2h-2a2 2 0 01-2-2V6zM4 16a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2H6a2 2 0 01-2-2v-2zm10 0a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2h-2a2 2 0 01-2-2v-2z"/></svg>'
    },
    {
      path: '/calendar',
      label: 'Calendar',
      icon: '<svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z"/></svg>'
    },
    {
      path: '/reports',
      label: 'Reports',
      icon: '<svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z"/></svg>'
    },
    {
      path: '/zones',
      label: 'Zones',
      icon: '<svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M17.657 16.657L13.414 20.9a1.998 1.998 0 01-2.827 0l-4.244-4.243a8 8 0 1111.314 0z"/><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 11a3 3 0 11-6 0 3 3 0 016 0z"/></svg>'
    },
    {
      path: '/events',
      label: 'Events',
      icon: '<svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z"/></svg>'
    },
    {
      path: '/settings',
      label: 'Settings',
      icon: '<svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.066 2.573c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.573 1.066c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.066-2.573c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z"/><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z"/></svg>'
    }
  ];

  constructor(private authService: AuthService) {}

  readonly themeService = inject(ThemeService);
  readonly versionService = inject(VersionService);

  onLogout(): void {
    this.authService.logout();
  }
}
