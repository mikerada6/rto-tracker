import { Injectable, signal, computed } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, tap, catchError, throwError } from 'rxjs';
import { UserResponse } from '../models/user.model';

const API_KEY_STORAGE_KEY = 'rto_api_key';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly _apiKey = signal<string | null>(localStorage.getItem(API_KEY_STORAGE_KEY));
  private readonly _user = signal<UserResponse | null>(null);

  readonly apiKey = this._apiKey.asReadonly();
  readonly user = this._user.asReadonly();
  readonly isAuthenticated = computed(() => !!this._apiKey());

  constructor(private http: HttpClient, private router: Router) {}

  login(apiKey: string): Observable<UserResponse> {
    return this.http.get<UserResponse>('/api/v1/users/me', {
      headers: { 'X-API-Key': apiKey }
    }).pipe(
      tap(user => {
        localStorage.setItem(API_KEY_STORAGE_KEY, apiKey);
        this._apiKey.set(apiKey);
        this._user.set(user);
      })
    );
  }

  logout(): void {
    localStorage.removeItem(API_KEY_STORAGE_KEY);
    this._apiKey.set(null);
    this._user.set(null);
    this.router.navigate(['/login']);
  }

  loadUser(): Observable<UserResponse> {
    return this.http.get<UserResponse>('/api/v1/users/me').pipe(
      tap(user => this._user.set(user)),
      catchError(err => {
        if (err.status === 401) {
          this.logout();
        }
        return throwError(() => err);
      })
    );
  }

  setUser(user: UserResponse): void {
    this._user.set(user);
  }
}
