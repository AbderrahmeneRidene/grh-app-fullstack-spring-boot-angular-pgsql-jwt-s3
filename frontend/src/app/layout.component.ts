import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterOutlet, RouterLink, RouterLinkActive, Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { AuthService } from './auth/auth.service';

@Component({
  selector: 'app-layout',
  standalone: true,
  imports: [CommonModule, RouterOutlet, RouterLink, RouterLinkActive, FormsModule],
  templateUrl: './layout.component.html',
  styleUrl: './layout.component.css'
})
export class LayoutComponent {
  user: any;
  currentAdminName = 'المدرسة الوطنية لتكوين إطارات الأمن الوطني والشرطة الوطنية بصلامبو';

  isDropdownOpen = false;
  isProfileModalOpen = false;
  isEditingAccount = false;
  activeTab = 'info';
  profileDetails: any = null;
  academicList: any[] = [];
  careerList: any[] = [];

  isCurrentPasswordVerified = false;
  profileFormModel = {
    username: '',
    email: '',
    password: '',
    confirmPassword: '',
    currentPassword: ''
  };
  showCurrentPassword = false;
  showNewPassword = false;
  showConfirmPassword = false;

  constructor(private authService: AuthService, private router: Router, private http: HttpClient) {
    this.authService.currentUser$.subscribe(u => {
      this.user = u;
    });
  }

  canManageOrg(): boolean {
    return this.authService.hasRole('ROLE_SUPER_ADMIN') || this.authService.hasRole('ROLE_ADMIN_DIRECTION');
  }

  canViewDashboard(): boolean {
    return this.authService.hasRole('ROLE_SUPER_ADMIN') || 
           this.authService.hasRole('ROLE_ADMIN_DIRECTION') || 
           this.authService.hasRole('ROLE_AGENT_RH');
  }

  canViewArchive(): boolean {
    return this.authService.hasRole('ROLE_SUPER_ADMIN') || 
           this.authService.hasRole('ROLE_ADMIN_DIRECTION') || 
           this.authService.hasRole('ROLE_AGENT_RH');
  }

  canViewPersonnel(): boolean {
    return this.authService.getRoleHierarchyIndex() >= 1;
  }

  canManageLeaves(): boolean {
    return this.authService.hasRole('ROLE_SUPER_ADMIN') || 
           this.authService.hasRole('ROLE_ADMIN_DIRECTION') || 
           this.authService.hasRole('ROLE_AGENT_RH');
  }

  logout(): void {
    this.isDropdownOpen = false;
    this.authService.logout();
    this.router.navigate(['/login']);
  }

  getAvatar(user: any): string {
    if (!user) return 'assets/avatar_default.png';
    if (user.profilePicture) {
      return user.profilePicture;
    }
    const gender = user.gender;
    if (gender === 'MALE') return 'assets/avatar_male.png';
    if (gender === 'FEMALE') return 'assets/avatar_female.png';
    return 'assets/avatar_default.png';
  }

  // Toggle profile dropdown menu
  toggleDropdown(event: Event): void {
    event.stopPropagation();
    this.isDropdownOpen = !this.isDropdownOpen;
  }

  // Close dropdown globally
  closeDropdown(): void {
    this.isDropdownOpen = false;
  }

  // Open profile modal
  openProfileModal(event: Event): void {
    event.stopPropagation();
    this.isDropdownOpen = false;
    this.isCurrentPasswordVerified = false;
    this.profileFormModel = {
      username: this.user?.username || '',
      email: this.user?.email || '',
      password: '',
      confirmPassword: '',
      currentPassword: ''
    };
    
    if (this.user?.personnelId) {
      this.isEditingAccount = false;
      this.activeTab = 'info';
      const headers = this.authService.getAuthHeaders();
      // Fetch personnel detailed details
      this.http.get<any>(`http://localhost:8080/api/personnel/${this.user.personnelId}`, { headers }).subscribe({
        next: (data) => {
          this.profileDetails = data;
        },
        error: (err) => console.error(err)
      });
      // Fetch academic details
      this.http.get<any[]>(`http://localhost:8080/api/personnel/${this.user.personnelId}/academic`, { headers }).subscribe({
        next: (data) => this.academicList = data,
        error: (err) => console.error(err)
      });
      // Fetch career progression details
      this.http.get<any[]>(`http://localhost:8080/api/personnel/${this.user.personnelId}/career`, { headers }).subscribe({
        next: (data) => this.careerList = data,
        error: (err) => console.error(err)
      });
    } else {
      this.profileDetails = null;
      this.academicList = [];
      this.careerList = [];
      this.isEditingAccount = true;
      this.activeTab = 'account';
    }
    
    this.isProfileModalOpen = true;
  }

  isImagePreviewOpen = false;

  closeProfileModal(): void {
    this.isProfileModalOpen = false;
    this.isImagePreviewOpen = false;
  }

  openImagePreview(event: Event): void {
    if (event) event.stopPropagation();
    this.isImagePreviewOpen = true;
  }

  closeImagePreview(): void {
    this.isImagePreviewOpen = false;
  }

  toggleEditAccount(): void {
    this.isEditingAccount = !this.isEditingAccount;
    this.isCurrentPasswordVerified = false;
    this.profileFormModel = {
      username: this.user?.username || '',
      email: this.user?.email || '',
      password: '',
      confirmPassword: '',
      currentPassword: ''
    };
  }

  verifyCurrentPassword(): void {
    if (!this.profileFormModel.currentPassword) {
      alert('الرجاء إدخال كلمة المرور الحالية');
      return;
    }
    const headers = this.authService.getAuthHeaders();
    this.http.post<any>('http://localhost:8080/api/auth/verify-password', {
      currentPassword: this.profileFormModel.currentPassword
    }, { headers }).subscribe({
      next: () => {
        this.isCurrentPasswordVerified = true;
      },
      error: (err) => {
        alert(err.error || 'كلمة المرور الحالية غير صحيحة');
      }
    });
  }

  // Save account profile credentials updates
  onUpdateProfile(): void {
    if (this.profileFormModel.password && this.profileFormModel.password !== this.profileFormModel.confirmPassword) {
      alert('كلمتا المرور غير متطابقتين!');
      return;
    }

    const headers = this.authService.getAuthHeaders();
    this.http.put<any>('http://localhost:8080/api/auth/update-profile', this.profileFormModel, { headers }).subscribe({
      next: (response) => {
        if (response.token) {
          localStorage.setItem('jwtToken', response.token);
          this.authService.updateCurrentUserField('token', response.token);
        }
        this.authService.updateCurrentUserField('username', response.username);
        this.authService.updateCurrentUserField('email', response.email);
        alert('تم تحديث بيانات الحساب بنجاح!');
        this.closeProfileModal();
      },
      error: (err) => {
        alert(err.error || 'حدث خطأ أثناء تحديث بيانات الحساب');
      }
    });
  }

  getPostLabel(post: string): string {
    switch (post) {
      case 'DIRECTEUR': return 'مدير إدارة';
      case 'SOUS_DIRECTEUR': return 'رئيس إدارة فرعية';
      case 'CHEF_SERVICE': return 'رئيس مصلحة';
      case 'CHEF_SERVICE_ADJ': return 'مساعد رئيس مصلحة';
      case 'CHEF_QISM': return 'رئيس قسم';
      default: return post || 'لا يوجد';
    }
  }

  getGenderLabel(gender: string): string {
    if (gender === 'MALE') return 'ذكر (Homme)';
    if (gender === 'FEMALE') return 'أنثى (Femme)';
    return gender || 'غير محدد';
  }

  getMaritalStatusLabel(status: string): string {
    switch (status) {
      case 'CELIBATAIRE': return 'أعزب / عزباء (Célibataire)';
      case 'MARIE': return 'متزوج / متزوجة (Marié)';
      case 'DIVORCE': return 'مطلق / مطلقة (Divorcé)';
      case 'VEUF': return 'أرمل / أرملة (Veuf)';
      default: return status || 'غير محدد';
    }
  }
}
