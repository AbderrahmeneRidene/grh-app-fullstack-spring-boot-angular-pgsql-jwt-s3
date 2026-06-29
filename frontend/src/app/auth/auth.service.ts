import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable, BehaviorSubject, tap } from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private apiUrl = 'http://localhost:8080/api/auth';
  private currentUserSubject = new BehaviorSubject<any>(null);
  public currentUser$ = this.currentUserSubject.asObservable();
  private appSettingsSubject = new BehaviorSubject<any>(null);
  public appSettings$ = this.appSettingsSubject.asObservable();

  constructor(private http: HttpClient) {
    const storedUser = localStorage.getItem('currentUser');
    if (storedUser) {
      this.currentUserSubject.next(JSON.parse(storedUser));
    }
    this.loadAppSettings().subscribe({
      error: err => console.error('Error loading app settings', err)
    });
  }

  public get currentUserValue(): any {
    return this.currentUserSubject.value;
  }

  public get appSettingsValue(): any {
    return this.appSettingsSubject.value;
  }

  loadAppSettings(): Observable<any> {
    return this.http.get<any>(`${this.apiUrl}/settings`).pipe(
      tap(settings => {
        if (settings) {
          this.appSettingsSubject.next(settings);
        }
      })
    );
  }

  getAppNameAr(): string {
    const settings = this.appSettingsValue;
    return settings && settings.nameAr ? settings.nameAr : 'المدرسة الوطنية لتكوين إطارات الأمن الوطني والشرطة الوطنية بصلامبو';
  }

  getAppNameFr(): string {
    const settings = this.appSettingsValue;
    return settings && settings.nameFr ? settings.nameFr : 'Ecole Nationale de Formation des Cadres de la Surete Nationale et de la Police Nationale de Carthage Salambo';
  }

  getAppLogo(): string {
    const settings = this.appSettingsValue;
    return settings && settings.logoUrl ? settings.logoUrl : 'assets/logo.png';
  }

  login(credentials: any): Observable<any> {
    return this.http.post<any>(`${this.apiUrl}/login`, credentials).pipe(
      tap(user => {
        if (user && user.token) {
          localStorage.setItem('currentUser', JSON.stringify(user));
          localStorage.setItem('jwtToken', user.token);
          this.currentUserSubject.next(user);
        }
      })
    );
  }

  logout(): void {
    localStorage.removeItem('currentUser');
    localStorage.removeItem('jwtToken');
    this.currentUserSubject.next(null);
  }

  getToken(): string | null {
    return localStorage.getItem('jwtToken');
  }

  getAuthHeaders(): HttpHeaders {
    const token = this.getToken();
    return new HttpHeaders({
      'Content-Type': 'application/json',
      'Authorization': token ? `Bearer ${token}` : ''
    });
  }

  isLoggedIn(): boolean {
    return !!this.getToken();
  }

  hasRole(roleName: string): boolean {
    const user = this.currentUserValue;
    if (!user || !user.roles) return false;
    return user.roles.includes(roleName);
  }

  getRoleHierarchyIndex(): number {
    const user = this.currentUserValue;
    if (!user || !user.roles) return -1;
    const roles = user.roles;
    if (roles.includes('ROLE_SUPER_ADMIN')) return 5;
    if (roles.includes('ROLE_ADMIN_DIRECTION')) return 4;
    if (roles.includes('ROLE_CHEF_SOUS_DIRECTION')) return 3;
    if (roles.includes('ROLE_CHEF_SERVICE')) return 2;
    if (roles.includes('ROLE_AGENT_RH')) return 1;
    return 0;
  }

  updateCurrentUserField(key: string, value: any): void {
    const user = this.currentUserValue;
    if (user) {
      user[key] = value;
      localStorage.setItem('currentUser', JSON.stringify(user));
      this.currentUserSubject.next({ ...user });
    }
  }
}
