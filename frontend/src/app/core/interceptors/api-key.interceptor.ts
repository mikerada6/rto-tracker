import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { AuthService } from '../services/auth.service';

export const apiKeyInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const apiKey = authService.apiKey();

  if (apiKey && !req.headers.has('X-API-Key')) {
    req = req.clone({
      setHeaders: { 'X-API-Key': apiKey }
    });
  }

  return next(req);
};
