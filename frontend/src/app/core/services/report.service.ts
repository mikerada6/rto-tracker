import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { QuarterReportResponse } from '../models/report.model';

export interface AvailablePeriod { year: number; quarter: number; }

@Injectable({ providedIn: 'root' })
export class ReportService {
  constructor(private http: HttpClient) {}

  getAvailablePeriods(): Observable<AvailablePeriod[]> {
    return this.http.get<AvailablePeriod[]>('/api/v1/reports/available-periods');
  }

  getQuarterReport(year: number, quarter: string): Observable<QuarterReportResponse> {
    return this.http.get<QuarterReportResponse>(`/api/v1/reports/quarter/${year}/${quarter}`);
  }

  getCurrentQuarterReport(): Observable<QuarterReportResponse> {
    return this.http.get<QuarterReportResponse>('/api/v1/reports/quarter/current');
  }
}
