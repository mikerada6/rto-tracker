import { Component, OnInit, signal, HostListener } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../core/services/auth.service';
import { UserService } from '../../core/services/user.service';
import { UserResponse } from '../../core/models/user.model';
import { ToastService } from '../../shared/services/toast.service';
import { SkeletonComponent } from '../../shared/components/skeleton.component';

@Component({
  selector: 'app-settings',
  imports: [FormsModule, SkeletonComponent],
  template: `
    <div class="max-w-2xl space-y-6">
      <h1 class="text-xl font-semibold text-gray-900">Settings</h1>

      @if (loading()) {
        <!-- Loading skeleton -->
        <div class="bg-white rounded-2xl shadow-sm border border-gray-100 p-6 space-y-5">
          <app-skeleton class="h-4 w-24" />
          <div class="space-y-2">
            <app-skeleton class="h-3 w-16" />
            <app-skeleton class="h-9 w-full" />
          </div>
          <div class="space-y-2">
            <app-skeleton class="h-3 w-28" />
            <app-skeleton class="h-9 w-full" />
          </div>
          <div class="space-y-2">
            <app-skeleton class="h-3 w-36" />
            <app-skeleton class="h-9 w-24" />
          </div>
        </div>
      } @else if (user()) {
        <!-- Profile -->
        <div class="bg-white rounded-2xl shadow-sm border border-gray-100 p-6 space-y-5">
          <h2 class="text-sm font-medium text-gray-500 uppercase tracking-wide">Profile</h2>

          <div>
            <label id="email-label" class="block text-sm font-medium text-gray-700 mb-1">Email</label>
            <p class="text-gray-900" aria-labelledby="email-label">{{ user()!.email }}</p>
          </div>

          <div>
            <label for="display-name" class="block text-sm font-medium text-gray-700 mb-1">Display Name</label>
            <div class="flex gap-2">
              <input
                id="display-name"
                [(ngModel)]="displayName"
                name="displayName"
                aria-label="Display name"
                class="flex-1 px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-blue-500 outline-none text-sm text-gray-900 bg-white"
              />
              <button
                (click)="updateProfile()"
                [disabled]="saving()"
                aria-label="Save display name"
                class="min-w-[4rem] min-h-[2.75rem] px-4 py-2 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 disabled:opacity-50 transition-colors focus-visible:ring-2 focus-visible:ring-blue-500 focus-visible:ring-offset-2"
              >
                Save
              </button>
            </div>
          </div>

          <div>
            <label for="required-days" class="block text-sm font-medium text-gray-700 mb-1">
              Required Days Per Week
            </label>
            <div class="flex items-center gap-3">
              <input
                id="required-days"
                type="number"
                [(ngModel)]="requiredDays"
                name="requiredDays"
                min="0.5"
                max="5"
                step="0.5"
                aria-label="Required days per week"
                class="w-24 px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-blue-500 outline-none text-sm text-gray-900 bg-white"
              />
              <button
                (click)="updateRequiredDays()"
                [disabled]="saving()"
                aria-label="Save required days"
                class="min-h-[2.75rem] px-4 py-2 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 disabled:opacity-50 transition-colors focus-visible:ring-2 focus-visible:ring-blue-500 focus-visible:ring-offset-2"
              >
                Save
              </button>
            </div>
          </div>

          <div>
            <label for="timezone" class="block text-sm font-medium text-gray-700 mb-1">Timezone</label>
            <div class="flex gap-2">
              <select
                id="timezone"
                [(ngModel)]="timezone"
                name="timezone"
                aria-label="Timezone"
                class="flex-1 px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-blue-500 outline-none text-sm text-gray-900 bg-white"
              >
                @for (tz of timezoneOptions; track tz.value) {
                  <option [value]="tz.value">{{ tz.label }}</option>
                }
              </select>
              <button
                (click)="detectTimezone()"
                title="Auto-detect from browser"
                aria-label="Auto-detect timezone"
                class="px-3 py-2 border border-gray-300 rounded-lg hover:bg-gray-50 text-sm text-gray-600 transition-colors"
              >🌐</button>
              <button
                (click)="updateTimezone()"
                [disabled]="saving()"
                aria-label="Save timezone"
                class="min-h-[2.75rem] px-4 py-2 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 disabled:opacity-50 transition-colors focus-visible:ring-2 focus-visible:ring-blue-500 focus-visible:ring-offset-2"
              >
                Save
              </button>
            </div>
            <p class="text-xs text-gray-400 mt-1">
              Times are stored in UTC and displayed in your local timezone. Click 🌐 to auto-detect.
            </p>
          </div>

          <div>
            <label for="anomalyThreshold" class="block text-sm font-medium text-gray-700 mb-1">Commute anomaly threshold</label>
            <div class="flex gap-2">
              <input
                id="anomalyThreshold"
                type="number"
                min="5"
                max="240"
                step="5"
                [(ngModel)]="anomalyThresholdMinutes"
                name="anomalyThreshold"
                aria-label="Commute anomaly threshold in minutes"
                class="flex-1 px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-blue-500 outline-none text-sm text-gray-900"
              />
              <button
                (click)="updateAnomalyThreshold()"
                [disabled]="saving()"
                aria-label="Save anomaly threshold"
                class="min-h-[2.75rem] px-4 py-2 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 disabled:opacity-50 transition-colors focus-visible:ring-2 focus-visible:ring-blue-500 focus-visible:ring-offset-2"
              >
                Save
              </button>
            </div>
            <p class="text-xs text-gray-400 mt-1">
              Minutes. Commute transit gaps longer than this prompt you to label them (e.g. happy hour).
            </p>
          </div>
        </div>

        <!-- API Key -->
        <div class="bg-white rounded-2xl shadow-sm border border-gray-100 p-6 space-y-4">
          <h2 class="text-sm font-medium text-gray-500 uppercase tracking-wide">API Key</h2>

          @if (newApiKey()) {
            <div class="p-3 bg-amber-50 border border-amber-200 rounded-lg text-sm" role="alert">
              <p class="font-medium text-amber-800 mb-1">New API Key (shown once):</p>
              <div class="flex items-start gap-2">
                <code class="flex-1 text-xs break-all select-all text-gray-900">{{ newApiKey() }}</code>
                <button
                  (click)="copyApiKey()"
                  [attr.aria-label]="apiKeyCopied() ? 'API key copied' : 'Copy API key'"
                  class="flex-shrink-0 min-h-[2rem] px-2.5 py-1 text-xs font-medium rounded-md border transition-colors focus-visible:ring-2 focus-visible:ring-amber-500 focus-visible:ring-offset-1"
                  [class.bg-green-600]="apiKeyCopied()"
                  [class.text-white]="apiKeyCopied()"
                  [class.border-green-600]="apiKeyCopied()"
                  [class.bg-white]="!apiKeyCopied()"
                  [class.text-amber-800]="!apiKeyCopied()"
                  [class.border-amber-300]="!apiKeyCopied()"
                  [class.hover:bg-amber-100]="!apiKeyCopied()"
                >
                  @if (apiKeyCopied()) {
                    ✓ Copied
                  } @else {
                    Copy
                  }
                </button>
              </div>
              <p class="text-amber-600 mt-2 text-xs">Update your Home Assistant configuration with this new key.</p>
            </div>
          }

          <button
            (click)="showRegenConfirm.set(true)"
            [disabled]="regenerating()"
            class="min-h-[2.75rem] px-4 py-2 bg-red-600 text-white text-sm font-medium rounded-lg hover:bg-red-700 disabled:opacity-50 transition-colors focus-visible:ring-2 focus-visible:ring-red-500 focus-visible:ring-offset-2"
          >
            {{ regenerating() ? 'Regenerating...' : 'Regenerate API Key' }}
          </button>
          <p class="text-xs text-gray-500">This will invalidate your current API key immediately.</p>
        </div>

        <!-- Logout -->
        <div class="bg-white rounded-2xl shadow-sm border border-gray-100 p-6">
          <button
            (click)="logout()"
            class="min-h-[2.75rem] px-4 py-2 border border-gray-300 text-gray-700 text-sm font-medium rounded-lg hover:bg-gray-50 transition-colors focus-visible:ring-2 focus-visible:ring-gray-400 focus-visible:ring-offset-2"
          >
            Logout
          </button>
        </div>
      }

      <!-- Styled API key regen confirmation dialog -->
      @if (showRegenConfirm()) {
        <div
          class="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4"
          role="dialog"
          aria-modal="true"
          aria-labelledby="regen-dialog-title"
        >
          <div class="bg-white rounded-2xl p-6 w-full max-w-sm shadow-xl">
            <div class="flex items-center gap-3 mb-4">
              <span class="text-2xl" aria-hidden="true">🔑</span>
              <h2 id="regen-dialog-title" class="text-lg font-semibold text-gray-900">Regenerate API Key?</h2>
            </div>
            <p class="text-sm text-gray-600 mb-6">
              This will <span class="font-semibold text-red-600">immediately invalidate</span> your current API key.
              Any Home Assistant automations using the old key will stop working until updated.
            </p>
            <div class="flex justify-end gap-3">
              <button
                (click)="showRegenConfirm.set(false)"
                class="min-h-[2.75rem] px-4 py-2 text-sm text-gray-700 hover:bg-gray-100 rounded-lg transition-colors focus-visible:ring-2 focus-visible:ring-gray-400 focus-visible:ring-offset-2"
              >Cancel</button>
              <button
                (click)="regenerateKey()"
                class="min-h-[2.75rem] px-4 py-2 bg-red-600 text-white text-sm font-medium rounded-lg hover:bg-red-700 transition-colors focus-visible:ring-2 focus-visible:ring-red-500 focus-visible:ring-offset-2"
              >Regenerate</button>
            </div>
          </div>
        </div>
      }
    </div>
  `
})
export class SettingsComponent implements OnInit {
  readonly user = signal<UserResponse | null>(null);
  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly regenerating = signal(false);
  readonly newApiKey = signal('');
  readonly apiKeyCopied = signal(false);
  readonly showRegenConfirm = signal(false);
  private apiKeyCopiedTimer: ReturnType<typeof setTimeout> | null = null;

  displayName = '';
  requiredDays = 3;
  timezone = 'America/New_York';
  anomalyThresholdMinutes = 45;

  readonly timezoneOptions = [
    { value: 'America/New_York',    label: 'Eastern Time (ET) — New York' },
    { value: 'America/Chicago',     label: 'Central Time (CT) — Chicago' },
    { value: 'America/Denver',      label: 'Mountain Time (MT) — Denver' },
    { value: 'America/Los_Angeles', label: 'Pacific Time (PT) — Los Angeles' },
    { value: 'America/Phoenix',     label: 'Mountain Time no DST — Phoenix' },
    { value: 'America/Anchorage',   label: 'Alaska Time (AKT)' },
    { value: 'Pacific/Honolulu',    label: 'Hawaii Time (HT)' },
    { value: 'Europe/London',       label: 'GMT/BST — London' },
    { value: 'Europe/Paris',        label: 'CET/CEST — Paris / Berlin' },
    { value: 'Europe/Helsinki',     label: 'EET/EEST — Helsinki' },
    { value: 'Asia/Kolkata',        label: 'IST — India' },
    { value: 'Asia/Shanghai',       label: 'CST — China' },
    { value: 'Asia/Tokyo',          label: 'JST — Japan' },
    { value: 'Australia/Sydney',    label: 'AEST/AEDT — Sydney' },
    { value: 'UTC',                 label: 'UTC' },
  ];

  constructor(
    private userService: UserService,
    private authService: AuthService,
    private toast: ToastService
  ) {}

  ngOnInit(): void {
    this.userService.getProfile().subscribe({
      next: (user) => {
          this.user.set(user);
          this.displayName = user.displayName;
          this.requiredDays = user.requiredDaysPerWeek;
          this.timezone = user.timezone || 'America/New_York';
          this.anomalyThresholdMinutes = user.commuteAnomalyThresholdMinutes ?? 45;
          this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.toast.error('Failed to load profile.');
      }
    });
  }

  @HostListener('window:keydown', ['$event'])
  onKeydown(e: KeyboardEvent): void {
    if (e.key === 'Escape') this.showRegenConfirm.set(false);
  }

  updateProfile(): void {
    this.saving.set(true);
    this.userService.updateProfile({ displayName: this.displayName }).subscribe({
      next: (user) => {
        this.user.set(user);
        this.authService.setUser(user);
        this.saving.set(false);
        this.toast.success('Profile updated');
      },
      error: () => {
        this.saving.set(false);
        this.toast.error('Failed to update profile.');
      }
    });
  }

  updateRequiredDays(): void {
    this.saving.set(true);
    this.userService.updateProfile({ requiredDaysPerWeek: this.requiredDays }).subscribe({
      next: (user) => {
        this.user.set(user);
        this.authService.setUser(user);
        this.saving.set(false);
        this.toast.success('Required days updated');
      },
      error: () => {
        this.saving.set(false);
        this.toast.error('Failed to update required days.');
      }
    });
  }

  updateTimezone(): void {
    this.saving.set(true);
    this.userService.updateProfile({ timezone: this.timezone }).subscribe({
      next: (user) => {
        this.user.set(user);
        this.authService.setUser(user);
        this.saving.set(false);
        this.toast.success('Timezone updated');
      },
      error: () => {
        this.saving.set(false);
        this.toast.error('Failed to update timezone.');
      }
    });
  }

  updateAnomalyThreshold(): void {
    this.saving.set(true);
    this.userService.updateProfile({ commuteAnomalyThresholdMinutes: this.anomalyThresholdMinutes }).subscribe({
      next: (user) => {
        this.user.set(user);
        this.authService.setUser(user);
        this.saving.set(false);
        this.toast.success('Anomaly threshold updated');
      },
      error: () => {
        this.saving.set(false);
        this.toast.error('Failed to update threshold.');
      }
    });
  }

  detectTimezone(): void {
    const detected = Intl.DateTimeFormat().resolvedOptions().timeZone;
    // Try to match against our list, otherwise add as custom option
    const known = this.timezoneOptions.find(t => t.value === detected);
    if (!known) {
      // Add it dynamically so the select can show it
      this.timezoneOptions.push({ value: detected, label: `${detected} (detected)` });
    }
    this.timezone = detected;
    this.toast.success(`Detected: ${detected}`);
  }

  regenerateKey(): void {
    this.showRegenConfirm.set(false);
    this.regenerating.set(true);
    this.userService.regenerateApiKey().subscribe({
      next: (res) => {
        this.newApiKey.set(res.apiKey);
        this.apiKeyCopied.set(false);
        this.regenerating.set(false);
        this.toast.success('API key regenerated — copy it now!');
      },
      error: () => {
        this.regenerating.set(false);
        this.toast.error('Failed to regenerate API key.');
      }
    });
  }

  async copyApiKey(): Promise<void> {
    const key = this.newApiKey();
    if (!key) return;
    try {
      await navigator.clipboard.writeText(key);
      this.apiKeyCopied.set(true);
      if (this.apiKeyCopiedTimer) clearTimeout(this.apiKeyCopiedTimer);
      this.apiKeyCopiedTimer = setTimeout(() => this.apiKeyCopied.set(false), 2000);
    } catch {
      this.toast.error('Copy failed — select and copy manually.');
    }
  }

  logout(): void {
    this.authService.logout();
  }
}
