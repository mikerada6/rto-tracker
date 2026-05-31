import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { AuthService } from '../services/auth.service';
import { ToastService } from '../../shared/services/toast.service';

export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const toast = inject(ToastService);

  return next(req).pipe(
    catchError(error => {
      if (error.status === 401 && !req.url.includes('/users/me')) {
        authService.logout();
      } else if (error.status === 429) {
        toast.warning('Too many requests. Please slow down.');
      } else if (error.status === 0) {
        toast.error('Network error. Check your connection.');
      }
      return throwError(() => error);
    })
  );
};
