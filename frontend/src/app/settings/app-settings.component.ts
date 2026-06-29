import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { AuthService } from '../auth/auth.service';

@Component({
  selector: 'app-settings',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './app-settings.component.html',
  styleUrl: './app-settings.component.css'
})
export class AppSettingsComponent implements OnInit {
  settings = {
    id: 1,
    nameAr: '',
    nameFr: '',
    code: 'ENFCSPN',
    logoUrl: ''
  };
  isLoading = false;
  successMessage = '';
  errorMessage = '';

  constructor(private http: HttpClient, public authService: AuthService) {}

  ngOnInit(): void {
    this.fetchSettings();
  }

  fetchSettings(): void {
    this.isLoading = true;
    this.authService.loadAppSettings().subscribe({
      next: (data) => {
        if (data) {
          this.settings = { ...data };
        }
        this.isLoading = false;
      },
      error: (err) => {
        console.error(err);
        this.errorMessage = 'حدث خطأ أثناء تحميل الإعدادات';
        this.isLoading = false;
      }
    });
  }

  onFileSelected(event: any): void {
    const file = event.target.files[0];
    if (file) {
      if (file.size > 2 * 1024 * 1024) {
        alert('حجم الصورة يجب أن لا يتجاوز 2 ميغابايت');
        return;
      }
      const reader = new FileReader();
      reader.onload = (e: any) => {
        this.settings.logoUrl = e.target.result;
      };
      reader.readAsDataURL(file);
    }
  }

  removeLogo(): void {
    this.settings.logoUrl = '';
  }

  onSubmit(): void {
    this.successMessage = '';
    this.errorMessage = '';
    this.isLoading = true;

    const headers = this.authService.getAuthHeaders();
    this.http.put('http://localhost:8080/api/admin/settings', this.settings, { headers }).subscribe({
      next: () => {
        this.successMessage = 'تم حفظ الإعدادات وتحديث مظهر المنظومة بنجاح!';
        this.isLoading = false;
        this.authService.loadAppSettings().subscribe();
      },
      error: (err) => {
        console.error(err);
        this.errorMessage = err.error || 'حدث خطأ أثناء حفظ الإعدادات';
        this.isLoading = false;
      }
    });
  }
}
