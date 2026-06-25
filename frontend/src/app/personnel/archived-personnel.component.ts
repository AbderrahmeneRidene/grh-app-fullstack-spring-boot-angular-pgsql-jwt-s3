import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { AuthService } from '../auth/auth.service';

@Component({
  selector: 'app-archived-personnel',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './archived-personnel.component.html',
  styleUrl: './archived-personnel.component.css'
})
export class ArchivedPersonnelComponent implements OnInit {
  archivedList: any[] = [];
  selectedPersonnel: any = null;
  activeTab = 'info';

  // Pagination & Sorting properties
  currentPage = 1;
  pageSize = 10;
  pageSizeOptions = [5, 10, 20, 50];
  sortField = 'registrationNumber';
  sortDirection: 'asc' | 'desc' = 'asc';


  constructor(private http: HttpClient, private authService: AuthService) {}

  ngOnInit(): void {
    this.fetchArchived();
  }

  isAgentRh(): boolean {
    return this.authService.hasRole('ROLE_AGENT_RH') || this.authService.hasRole('ROLE_SUPER_ADMIN');
  }

  isDirecteur(): boolean {
    return this.authService.hasRole('ROLE_ADMIN_DIRECTION') || this.authService.hasRole('ROLE_SUPER_ADMIN');
  }

  isSuperAdmin(): boolean {
    return this.authService.hasRole('ROLE_SUPER_ADMIN');
  }

  deletePersonnel(p: any): void {
    if (!confirm(`هل أنت متأكد من حذف الموظف ${p.firstNameAr} ${p.lastNameAr} نهائياً؟`)) return;
    const headers = this.authService.getAuthHeaders();
    this.http.delete(`http://localhost:8080/api/personnel/${p.id}`, { headers }).subscribe({
      next: () => {
        alert('تم حذف الموظف بنجاح.');
        this.fetchArchived();
        if (this.selectedPersonnel && this.selectedPersonnel.id === p.id) {
          this.selectedPersonnel = null;
        }
      },
      error: (err) => alert(err.error || 'حدث خطأ أثناء الحذف')
    });
  }

  fetchArchived(): void {
    const headers = this.authService.getAuthHeaders();
    this.http.get<any[]>('http://localhost:8080/api/personnel/archived', { headers }).subscribe({
      next: (data) => this.archivedList = data,
      error: (err) => console.error(err)
    });
  }

  requestActive(p: any): void {
    if (!confirm(`هل أنت متأكد من طلب إلغاء أرشفة الموظف ${p.firstNameAr} ${p.lastNameAr} وإعادته للخدمة؟`)) return;
    const headers = this.authService.getAuthHeaders();
    this.http.post<any>(`http://localhost:8080/api/personnel/${p.id}/request-active`, {}, { headers }).subscribe({
      next: () => {
        alert('تم تقديم طلب التنشيط بنجاح، بانتظار موافقة المدير.');
        this.fetchArchived();
        if (this.selectedPersonnel && this.selectedPersonnel.id === p.id) {
          this.selectedPersonnel = null;
        }
      },
      error: (err) => alert(err.error || 'حدث خطأ أثناء طلب التنشيط')
    });
  }

  approveActive(p: any): void {
    if (!confirm(`هل أنت متأكد من الموافقة على إلغاء أرشفة الموظف ${p.firstNameAr} ${p.lastNameAr}؟`)) return;
    const headers = this.authService.getAuthHeaders();
    this.http.post<any>(`http://localhost:8080/api/personnel/${p.id}/approve-active`, {}, { headers }).subscribe({
      next: () => {
        alert('تم إلغاء أرشفة الموظف وإعادته إلى قائمة الموظفين النشطين بنجاح.');
        this.fetchArchived();
        if (this.selectedPersonnel && this.selectedPersonnel.id === p.id) {
          this.selectedPersonnel = null;
        }
      },
      error: (err) => alert(err.error || 'حدث خطأ أثناء الموافقة')
    });
  }

  rejectArchiveAction(p: any): void {
    if (!confirm(`هل أنت متأكد من رفض الطلب الخاص بالموظف ${p.firstNameAr} ${p.lastNameAr}؟`)) return;
    const headers = this.authService.getAuthHeaders();
    this.http.post<any>(`http://localhost:8080/api/personnel/${p.id}/reject-archive-action`, {}, { headers }).subscribe({
      next: () => {
        alert('تم رفض الطلب وإبقاء الموظف مؤرشفاً.');
        this.fetchArchived();
        if (this.selectedPersonnel && this.selectedPersonnel.id === p.id) {
          this.selectedPersonnel = null;
        }
      },
      error: (err) => alert(err.error || 'حدث خطأ أثناء رفض الطلب')
    });
  }

  viewProfile(p: any): void {
    this.selectedPersonnel = p;
    this.activeTab = 'info';
  }

  getAvatar(p: any): string {
    if (!p) return 'assets/avatar_default.png';
    if (p.profilePicture) {
      return p.profilePicture;
    }
    const gender = p.gender;
    if (gender === 'MALE') return 'assets/avatar_male.png';
    if (gender === 'FEMALE') return 'assets/avatar_female.png';
    return 'assets/avatar_default.png';
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

  getGenderLabel(g: string): string {
    return g === 'MALE' ? 'ذكر' : 'أنثى';
  }

  getMaritalStatusLabel(s: string): string {
    switch (s) {
      case 'CELIBATAIRE': return 'عازب(ة)';
      case 'MARIE': return 'متزوج(ة)';
      case 'DIVORCE': return 'مطلق(ة)';
      case 'VEUF': return 'أرمل(ة)';
      default: return s;
    }
  }

  // Sorting and Pagination helpers
  getSortedAndPaginatedArchived(): any[] {
    let list = [...this.archivedList];

    // Sorting logic
    list.sort((a, b) => {
      let valA = this.getSortValue(a, this.sortField);
      let valB = this.getSortValue(b, this.sortField);

      if (valA === valB) return 0;
      
      const modifier = this.sortDirection === 'asc' ? 1 : -1;
      
      if (typeof valA === 'string' && typeof valB === 'string') {
        return valA.localeCompare(valB, 'ar-TN') * modifier;
      }
      
      return (valA < valB ? -1 : 1) * modifier;
    });

    // Reset pagination if total items change
    const maxPages = Math.ceil(list.length / this.pageSize) || 1;
    if (this.currentPage > maxPages) {
      this.currentPage = maxPages;
    }

    // Paginate
    const startIndex = (this.currentPage - 1) * Number(this.pageSize);
    return list.slice(startIndex, startIndex + Number(this.pageSize));
  }

  getSortValue(item: any, field: string): any {
    switch (field) {
      case 'registrationNumber':
        return item.registrationNumber || '';
      case 'name':
        return `${item.firstNameAr || ''} ${item.fatherNameAr || ''} ${item.lastNameAr || ''}`.trim();
      case 'grade':
        return item.grade || '';
      default:
        return '';
    }
  }

  setSort(field: string): void {
    if (this.sortField === field) {
      this.sortDirection = this.sortDirection === 'asc' ? 'desc' : 'asc';
    } else {
      this.sortField = field;
      this.sortDirection = 'asc';
    }
    this.currentPage = 1; // reset to first page on sort change
  }

  getSortIcon(field: string): string {
    if (this.sortField !== field) return 'fa-solid fa-sort';
    return this.sortDirection === 'asc' ? 'fa-solid fa-sort-up' : 'fa-solid fa-sort-down';
  }

  changePage(page: number): void {
    if (page >= 1 && page <= this.getTotalPages()) {
      this.currentPage = page;
    }
  }

  getTotalPages(): number {
    return Math.ceil(this.archivedList.length / this.pageSize) || 1;
  }

  getMin(a: number, b: number): number {
    return Math.min(a, b);
  }

  getPageNumbers(): number[] {
    const total = this.getTotalPages();
    const pages = [];
    for (let i = 1; i <= total; i++) {
      pages.push(i);
    }
    return pages;
  }
}
