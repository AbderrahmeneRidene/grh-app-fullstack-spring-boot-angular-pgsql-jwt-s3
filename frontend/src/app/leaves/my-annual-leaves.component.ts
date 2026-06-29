import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { AuthService } from '../auth/auth.service';

@Component({
  selector: 'app-my-annual-leaves',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './my-annual-leaves.component.html',
  styleUrl: './my-annual-leaves.component.css'
})
export class MyAnnualLeavesComponent implements OnInit {
  leavesList: any[] = [];
  isLeaveModalOpen = false;
  isEditMode = false;
  editingLeaveId: number | null = null;

  // Pagination & Sorting properties
  currentPage = 1;
  pageSize = 10;
  pageSizeOptions = [5, 10, 20, 50];
  sortField = 'startDate';
  sortDirection: 'asc' | 'desc' = 'desc';

  newRequest: any = {
    personnelId: null,
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
      .filter(l => l.personnelId === userId && l.status === 'APPROVED' && new Date(l.startDate).getFullYear() === currentYear)
      .reduce((sum, l) => {
        const start = new Date(l.startDate);
        const end = new Date(l.endDate);
        const diffTime = Math.abs(end.getTime() - start.getTime());
        const diffDays = Math.ceil(diffTime / (1000 * 60 * 60 * 24)) + 1;
        return sum + diffDays;
      }, 0);

    return Math.max(0, 45 - annualDaysUsed);
  }

  get usedDays(): number {
    return Math.max(0, 45 - this.annualLeaveBalance);
  }

  get balancePercentage(): number {
    return (this.annualLeaveBalance / 45) * 100;
  }

  ngOnInit(): void {
    this.fetchLeaves();
    if (this.currentUser?.personnelId) {
      this.newRequest.personnelId = this.currentUser.personnelId;
    }
  }

  fetchLeaves(): void {
    const headers = this.authService.getAuthHeaders();
    this.http.get<any[]>('http://localhost:8080/api/annual-leaves', { headers }).subscribe({
      next: (data) => {
        const userId = this.currentUser?.personnelId;
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
    if (this.newRequest.duration === null || this.newRequest.duration === undefined) return null;
    const dur = parseInt(this.newRequest.duration, 10);
    if (isNaN(dur) || dur <= 0) {
      return 'يجب أن تكون المدة أكبر من صفر';
    }
    
    const balance = this.annualLeaveBalance;
    if (dur > balance) {
      return `المدة المطلوبة (${dur} يوم) تتجاوز رصيد الإجازة السنوية المتبقي (${balance} يوم)`;
    }
    
    return null;
  }

  isLeaveFormValid(): boolean {
    const r = this.newRequest;
    if (!this.currentUser?.personnelId) {
      return false;
    }
    if (!r.startDate || r.duration === null || r.duration === undefined || r.duration <= 0) {
      return false;
    }
    if (this.getLeaveDurationError()) {
      return false;
    }
    return true;
  }

  onSubmitRequest(): void {
    const headers = this.authService.getAuthHeaders();
    if (this.isEditMode && this.editingLeaveId) {
      this.http.put<any>(`http://localhost:8080/api/annual-leaves/${this.editingLeaveId}`, this.newRequest, { headers }).subscribe({
        next: () => {
          this.closeLeaveModal();
          this.fetchLeaves();
        },
        error: (err) => {
          console.error(err);
          alert(err.error || 'حدث خطأ أثناء تعديل الطلب');
        }
      });
    } else {
      this.http.post<any>('http://localhost:8080/api/annual-leaves', this.newRequest, { headers }).subscribe({
        next: () => {
          this.closeLeaveModal();
          this.fetchLeaves();
        },
        error: (err) => {
          console.error(err);
          alert(err.error || 'حدث خطأ أثناء إرسال الطلب');
        }
      });
    }
  }

  onEditLeave(leave: any): void {
    this.isEditMode = true;
    this.editingLeaveId = leave.id;
    this.newRequest = {
      personnelId: leave.personnelId,
      startDate: leave.startDate,
      duration: leave.duration,
      endDate: leave.endDate,
      resumptionDate: leave.returnDate,
      justification: leave.justification,
      documentPath: leave.documentPath
    };
    this.openLeaveModal();
  }

  openLeaveModal(): void {
    this.isLeaveModalOpen = true;
  }

  closeLeaveModal(): void {
    this.isLeaveModalOpen = false;
    this.isEditMode = false;
    this.editingLeaveId = null;
    this.newRequest = {
      personnelId: this.currentUser?.personnelId || null,
      startDate: '',
      duration: null,
      endDate: '',
      resumptionDate: '',
      justification: '',
      documentPath: ''
    };
  }

  getStatusAr(status: string): string {
    switch (status) {
      case 'PENDING': return 'قيد الانتظار';
      case 'APPROVED_SERVICE': return 'موافقة رئيس المصلحة (مرحلة 1)';
      case 'APPROVED_SD': return 'موافقة رئيس الإدارة الفرعية (مرحلة 2)';
      case 'APPROVED': return 'موافق عليها نهائياً';
      case 'REJECTED': return 'مرفوضة';
      case 'LEAVE_STARTED': return 'بدأت الإجازة';
      case 'WORK_RESUMED': return 'تم استئناف العمل';
      case 'PENDING_MODIFICATION': return 'تعديل معلق';
      case 'PENDING_DELETION': return 'حذف معلق';
      default: return status;
    }
  }

  getMin(a: number, b: number): number {
    return Math.min(a, b);
  }

  setSort(field: string): void {
    if (this.sortField === field) {
      this.sortDirection = this.sortDirection === 'asc' ? 'desc' : 'asc';
    } else {
      this.sortField = field;
      this.sortDirection = 'desc';
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
    const list = this.getSortedLeaves();
    return Math.ceil(list.length / this.pageSize) || 1;
  }

  getPageNumbers(): number[] {
    const total = this.getTotalPages();
    return Array.from({ length: total }, (_, i) => i + 1);
  }

  getSortedLeaves(): any[] {
    const list = [...this.leavesList];

    list.sort((a, b) => {
      let valA = a[this.sortField];
      let valB = b[this.sortField];

      if (valA < valB) return this.sortDirection === 'asc' ? -1 : 1;
      if (valA > valB) return this.sortDirection === 'asc' ? 1 : -1;
      return 0;
    });

    return list;
  }

  getSortedAndPaginatedLeaves(): any[] {
    const list = this.getSortedLeaves();
    const startIndex = (this.currentPage - 1) * this.pageSize;
    return list.slice(startIndex, startIndex + this.pageSize);
  }
}
