import { Injectable, computed, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';

@Injectable({ providedIn: 'root' })
export class VersionService {
  readonly backendVersion = signal('');
  readonly backendCommit = signal('');
  readonly frontendVersion = signal('');
  readonly frontendCommit = signal('');

  readonly versionsMatch = computed(
    () => this.backendVersion() === this.frontendVersion(),
  );

  constructor(private http: HttpClient) {
    this.loadBackendVersion();
    this.loadFrontendVersion();
  }

  private loadBackendVersion(): void {
    this.http.get<{ version?: string; commit: string }>('/api/v1/version').subscribe({
      next: (res) => {
        this.backendVersion.set(res.version ?? 'unknown');
        this.backendCommit.set(res.commit);
      },
      error: () => {
        this.backendVersion.set('unknown');
        this.backendCommit.set('unknown');
      },
    });
  }

  private loadFrontendVersion(): void {
    this.http.get<{ version?: string; commit: string }>('/version.json').subscribe({
      next: (res) => {
        this.frontendVersion.set(res.version ?? 'dev');
        this.frontendCommit.set(res.commit);
      },
      error: () => {
        this.frontendVersion.set('dev');
        this.frontendCommit.set('dev');
      },
    });
  }

  abbreviate(commit: string): string {
    if (!commit || commit === 'dev' || commit === 'unknown') return commit;
    return commit.substring(0, 7);
  }
}
