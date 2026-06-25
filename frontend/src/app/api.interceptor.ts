import { HttpInterceptorFn } from '@angular/common/http';

export const apiInterceptor: HttpInterceptorFn = (req, next) => {
  // Intercept requests directed to the hardcoded local backend URL
  if (req.url.startsWith('http://localhost:8080/api')) {
    // If we are not running in development (typically port 4200 for Angular CLI),
    // rewrite the request URL to a relative '/api' path.
    if (window.location.port !== '4200') {
      const newUrl = req.url.replace('http://localhost:8080/api', '/api');
      const cloned = req.clone({ url: newUrl });
      return next(cloned);
    }
  }
  return next(req);
};
