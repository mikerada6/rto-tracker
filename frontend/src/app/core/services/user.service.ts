import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { UserResponse, UpdateUserRequest, ApiKeyResponse } from '../models/user.model';

@Injectable({ providedIn: 'root' })
export class UserService {
  constructor(private http: HttpClient) {}

  getProfile(): Observable<UserResponse> {
    return this.http.get<UserResponse>('/api/v1/users/me');
  }

  updateProfile(request: UpdateUserRequest): Observable<UserResponse> {
    return this.http.patch<UserResponse>('/api/v1/users/me', request);
  }

  regenerateApiKey(): Observable<ApiKeyResponse> {
    return this.http.post<ApiKeyResponse>('/api/v1/users/me/regenerate-key', {});
  }
}
