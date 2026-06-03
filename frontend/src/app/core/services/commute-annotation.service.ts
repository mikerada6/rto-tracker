import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  CommuteAnnotation,
  CreateCommuteAnnotationRequest,
  UpdateCommuteAnnotationRequest,
} from '../models/commute-annotation.model';

@Injectable({ providedIn: 'root' })
export class CommuteAnnotationService {
  private http = inject(HttpClient);

  create(date: string, req: CreateCommuteAnnotationRequest): Observable<CommuteAnnotation> {
    return this.http.post<CommuteAnnotation>(`/api/v1/days/${date}/commute-annotations`, req);
  }

  update(date: string, id: string, req: UpdateCommuteAnnotationRequest): Observable<CommuteAnnotation> {
    return this.http.put<CommuteAnnotation>(`/api/v1/days/${date}/commute-annotations/${id}`, req);
  }

  delete(date: string, id: string): Observable<void> {
    return this.http.delete<void>(`/api/v1/days/${date}/commute-annotations/${id}`);
  }
}
