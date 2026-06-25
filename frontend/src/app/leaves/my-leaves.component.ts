import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { AuthService } from '../auth/auth.service';

@Component({
  selector: 'app-my-leaves',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './my-leaves.component.html',
  styleUrl: './my-leaves.component.css'
})
export class MyLeavesComponent implements OnInit {
  leavesList: any[] = [];
  isLeaveModalOpen = false;

  // Pagination & Sorting properties
  currentPage = 1;
  pageSize = 10;
  pageSizeOptions = [5, 10, 20, 50];
  sortField = 'startDate';
  sortDirection: 'asc' | 'desc' = 'desc';

  newRequest: any = {
    personnelId: null,
    leaveType: '',
    startDate: '',
    duration: null,
    endDate: '',
    resumptionDate: '',
    justification: '',
    documentPath: ''
  };

  constructor(private http: HttpClient, private authService: AuthService) {}

  get currentUser(): any {
    return this.authService.currentUserValue;
  }

  get annualLeaveBalance(): number {
    const currentYear = new Date().getFullYear();
    const userId = this.currentUser?.personnelId;
    if (!userId) return 0;

    const annualDaysUsed = this.leavesList
      .filter(l => l.personnelId === userId && l.leaveType === 'ANNUEL' && l.status === 'APPROVED' && new Date(l.startDate).getFullYear() === currentYear)
      .reduce((sum, l) => {
        const start = new Date(l.startDate);
        const end = new Date(l.endDate);
        const diffTime = Math.abs(end.getTime() - start.getTime());
        const diffDays = Math.ceil(diffTime / (1000 * 60 * 60 * 24)) + 1;
        return sum + diffDays;
      }, 0);

    return Math.max(0, 45 - annualDaysUsed);
  }

  get exceptionalLeaveBalance(): number {
    const currentYear = new Date().getFullYear();
    const userId = this.currentUser?.personnelId;
    if (!userId) return 0;

    const exceptionalDaysUsed = this.leavesList
      .filter(l => l.personnelId === userId && l.leaveType === 'EXCEPTIONNEL' && l.status === 'APPROVED' && new Date(l.startDate).getFullYear() === currentYear)
      .reduce((sum, l) => {
        const start = new Date(l.startDate);
        const end = new Date(l.endDate);
        const diffTime = Math.abs(end.getTime() - start.getTime());
        const diffDays = Math.ceil(diffTime / (1000 * 60 * 60 * 24)) + 1;
        return sum + diffDays;
      }, 0);

    return Math.max(0, 6 - exceptionalDaysUsed);
  }

  ngOnInit(): void {
    this.fetchLeaves();
    if (this.currentUser?.personnelId) {
      this.newRequest.personnelId = this.currentUser.personnelId;
    }
  }

  fetchLeaves(): void {
    const headers = this.authService.getAuthHeaders();
    this.http.get<any[]>('http://localhost:8080/api/leaves', { headers }).subscribe({
      next: (data) => {
        const userId = this.currentUser?.personnelId;
        // Filter list so only user's own requests are visible here
        this.leavesList = data.filter(l => l.personnelId === userId);
      },
      error: (err) => console.error(err)
    });
  }

  calculateDates(): void {
    if (this.newRequest.startDate && this.newRequest.duration) {
      const start = new Date(this.newRequest.startDate);
      const duration = parseInt(this.newRequest.duration, 10);
      if (!isNaN(start.getTime()) && duration > 0) {
        const end = new Date(start);
        end.setDate(start.getDate() + duration - 1);
        this.newRequest.endDate = end.toISOString().split('T')[0];

        const resume = new Date(start);
        resume.setDate(start.getDate() + duration);
        this.newRequest.resumptionDate = resume.toISOString().split('T')[0];
      } else {
        this.newRequest.endDate = '';
        this.newRequest.resumptionDate = '';
      }
    } else {
      this.newRequest.endDate = '';
    }
  }

  getLeaveDurationError(): string | null {
    if (!this.newRequest.leaveType || this.newRequest.duration === null || this.newRequest.duration === undefined) return null;
    const dur = parseInt(this.newRequest.duration, 10);
    if (isNaN(dur) || dur <= 0) {
      return 'يجب أن تكون المدة أكبر من صفر';
    }
    
    if (this.newRequest.leaveType === 'ANNUEL') {
      const balance = this.annualLeaveBalance;
      if (dur > balance) {
        return `المدة المطلوبة (${dur} يوم) تتجاوز رصيد الإجازة السنوية المتبقي (${balance} يوم)`;
      }
    }
    
    if (this.newRequest.leaveType === 'EXCEPTIONNEL') {
      const balance = this.exceptionalLeaveBalance;
      if (dur > balance) {
        return `المدة المطلوبة (${dur} يوم) تتجاوز رصيد الإجازة الاستثنائية المتبقي (${balance} يوم)`;
      }
    }
    
    return null;
  }

  isLeaveFormValid(): boolean {
    const r = this.newRequest;
    if (!this.currentUser?.personnelId) {
      return false;
    }
    if (!r.leaveType || !r.startDate || r.duration === null || r.duration === undefined || r.duration <= 0) {
      return false;
    }
    if (this.getLeaveDurationError()) {
      return false;
    }
    return true;
  }

  onSubmitRequest(): void {
    const headers = this.authService.getAuthHeaders();
    this.newRequest.personnelId = this.currentUser?.personnelId;
    this.http.post<any>('http://localhost:8080/api/leaves', this.newRequest, { headers }).subscribe({
      next: () => {
        this.newRequest = {
          personnelId: this.currentUser?.personnelId,
          leaveType: '',
          startDate: '',
          duration: null,
          endDate: '',
          resumptionDate: '',
          justification: '',
          documentPath: ''
        };
        this.closeLeaveModal();
        this.fetchLeaves();
      },
      error: (err) => {
        console.error(err);
        alert(err.error || 'حدث خطأ أثناء تقديم الطلب');
      }
    });
  }

  openLeaveModal(): void {
    this.isLeaveModalOpen = true;
  }

  closeLeaveModal(): void {
    this.isLeaveModalOpen = false;
  }

  getLeaveTypeAr(type: string): string {
    switch (type) {
      case 'ANNUEL': return 'سنوية';
      case 'MALADIE': return 'مرضية';
      case 'EXCEPTIONNEL': return 'استثنائية';
      default: return type;
    }
  }

  getStatusAr(status: string): string {
    switch (status) {
      case 'PENDING': return 'قيد الانتظار';
      case 'APPROVED_SERVICE': return 'موافقة المصلحة';
      case 'APPROVED_SD': return 'موافقة الإدارة الفرعية';
      case 'APPROVED': return 'موافق عليها نهائياً';
      case 'REJECTED': return 'مرفوضة';
      default: return status;
    }
  }

  getSortedAndPaginatedLeaves(): any[] {
    let list = [...this.leavesList];

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

    const maxPages = Math.ceil(list.length / this.pageSize) || 1;
    if (this.currentPage > maxPages) {
      this.currentPage = maxPages;
    }

    const startIndex = (this.currentPage - 1) * Number(this.pageSize);
    return list.slice(startIndex, startIndex + Number(this.pageSize));
  }

  getSortValue(item: any, field: string): any {
    switch (field) {
      case 'leaveType':
        return item.leaveType || '';
      case 'startDate':
        return item.startDate || '';
      case 'endDate':
        return item.endDate || '';
      case 'status':
        return item.status || '';
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
    this.currentPage = 1;
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
    return Math.ceil(this.leavesList.length / this.pageSize) || 1;
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
