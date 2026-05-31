import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { DayResponse } from '../models/day.model';
import { AuditResponse } from '../models/audit.model';

@Injectable({ providedIn: 'root' })
export class CalendarService {
  constructor(private http: HttpClient) {}

  getDayDetail(date: string): Observable<DayResponse> {
    return this.http.get<DayResponse>(`/api/v1/days/${date}`);
  }

  getAudit(startDate: string, endDate: string): Observable<AuditResponse> {
    const params = new HttpParams()
      .set('startDate', startDate)
      .set('endDate', endDate);
    return this.http.get<AuditResponse>('/api/v1/audit/office-days', { params });
  }
}
