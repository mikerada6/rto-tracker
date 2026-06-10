import { Injectable } from '@angular/core';
import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
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

  exportPdf(period: ReportPeriod, from?: string, to?: string): Observable<HttpResponse<Blob>> {
    let params = new HttpParams().set('period', period);
    if (from) params = params.set('from', from);
    if (to) params = params.set('to', to);
    return this.http.get('/api/v1/reports/export/pdf', {
      params,
      responseType: 'blob',
      observe: 'response'
    });
  }
}

export type ReportPeriod = 'WEEK' | 'MONTH' | 'QUARTER' | 'YEAR' | 'CUSTOM';
