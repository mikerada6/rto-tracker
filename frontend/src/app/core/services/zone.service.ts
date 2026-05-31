import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ZoneResponse, CreateZoneRequest, UpdateZoneRequest } from '../models/zone.model';

@Injectable({ providedIn: 'root' })
export class ZoneService {
  private readonly baseUrl = '/api/v1/zones';

  constructor(private http: HttpClient) {}

  list(type?: string, active?: boolean): Observable<ZoneResponse[]> {
    let params = new HttpParams();
    if (type) params = params.set('type', type);
    if (active !== undefined) params = params.set('active', active.toString());
    return this.http.get<ZoneResponse[]>(this.baseUrl, { params });
  }

  create(request: CreateZoneRequest): Observable<ZoneResponse> {
    return this.http.post<ZoneResponse>(this.baseUrl, request);
  }

  update(zoneId: string, request: UpdateZoneRequest): Observable<ZoneResponse> {
    return this.http.put<ZoneResponse>(`${this.baseUrl}/${zoneId}`, request);
  }

  delete(zoneId: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${zoneId}`);
  }
}
