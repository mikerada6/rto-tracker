import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';
import { AppShellComponent } from './shared/components/app-shell.component';

export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () => import('./features/login/login.component').then(m => m.LoginComponent)
  },
  {
    path: '',
    component: AppShellComponent,
    canActivate: [authGuard],
    children: [
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
      {
        path: 'dashboard',
        loadComponent: () => import('./features/dashboard/dashboard.component').then(m => m.DashboardComponent)
      },
      {
        path: 'calendar',
        loadComponent: () => import('./features/calendar/calendar.component').then(m => m.CalendarComponent)
      },
      {
        path: 'calendar/:date',
        loadComponent: () => import('./features/calendar/calendar.component').then(m => m.CalendarComponent)
      },
      {
        path: 'reports',
        loadComponent: () => import('./features/reports/quarter-report.component').then(m => m.QuarterReportComponent)
      },
      {
        path: 'reports/:year/:quarter',
        loadComponent: () => import('./features/reports/quarter-report.component').then(m => m.QuarterReportComponent)
      },
      {
        path: 'zones',
        loadComponent: () => import('./features/zones/zone-list.component').then(m => m.ZoneListComponent)
      },
      {
        path: 'events',
        loadComponent: () => import('./features/events/event-history.component').then(m => m.EventHistoryComponent)
      },
      {
        path: 'settings',
        loadComponent: () => import('./features/settings/settings.component').then(m => m.SettingsComponent)
      }
    ]
  },
  { path: '**', redirectTo: 'dashboard' }
];
