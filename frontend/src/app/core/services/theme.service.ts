import { Injectable, signal, effect } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class ThemeService {
  readonly isDark = signal<boolean>(false);

  constructor() {
    const stored = localStorage.getItem('theme');
    const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
    const dark = stored ? stored === 'dark' : prefersDark;
    this.isDark.set(dark);
    this.applyTheme(dark);

    effect(() => {
      const d = this.isDark();
      this.applyTheme(d);
      localStorage.setItem('theme', d ? 'dark' : 'light');
    });
  }

  toggle(): void {
    this.isDark.set(!this.isDark());
  }

  private applyTheme(dark: boolean): void {
    document.documentElement.classList.toggle('dark', dark);
  }
}

