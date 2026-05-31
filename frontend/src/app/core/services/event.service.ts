import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ZoneEventResponse, BulkUploadResponse } from '../models/event.model';

@Injectable({ providedIn: 'root' })
export class EventService {
  constructor(private http: HttpClient) {}

  list(startDate: string, endDate: string, zoneId?: string, eventType?: string): Observable<ZoneEventResponse[]> {
    let params = new HttpParams()
      .set('startDate', startDate)
      .set('endDate', endDate);
    if (zoneId) params = params.set('zoneId', zoneId);
    if (eventType) params = params.set('eventType', eventType);
    return this.http.get<ZoneEventResponse[]>('/api/v1/events', { params });
  }

  uploadCsv(file: File, zoneMapping: Record<string, string>): Observable<BulkUploadResponse> {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('zoneMapping', JSON.stringify(zoneMapping));
    return this.http.post<BulkUploadResponse>('/api/v1/events/upload', formData);
  }
}
