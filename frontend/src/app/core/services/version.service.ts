import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';

@Injectable({ providedIn: 'root' })
export class VersionService {
  readonly backendCommit = signal('');
  readonly frontendCommit = signal('');

  constructor(private http: HttpClient) {
    this.loadBackendVersion();
    this.loadFrontendVersion();
  }

  private loadBackendVersion(): void {
    this.http.get<{ commit: string }>('/api/v1/version').subscribe({
      next: (res) => this.backendCommit.set(res.commit),
      error: () => this.backendCommit.set('unknown'),
    });
  }

  private loadFrontendVersion(): void {
    this.http.get<{ commit: string }>('/version.json').subscribe({
      next: (res) => this.frontendCommit.set(res.commit),
      error: () => this.frontendCommit.set('dev'),
    });
  }

  abbreviate(commit: string): string {
    if (!commit || commit === 'dev' || commit === 'unknown') return commit;
    return commit.substring(0, 7);
  }
}
