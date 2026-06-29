import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { AuthService } from '../auth/auth.service';

@Component({
  selector: 'app-annual-leaves',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './annual-leaves.component.html',
  styleUrl: './annual-leaves.component.css'
})
export class AnnualLeavesComponent implements OnInit {
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

  searchQuery = '';
  filterApproved = true;
  filterPending = true;
  filterRejected = true;

  gradeOrder = [
    'محافظ شرطة عام من الصنف الأول',
    'محافظ شرطة عام من الصنف الثاني',
    'محافظ شرطة أعلى',
    'عميد',
    'عقيد',
    'مقدم',
    'رائد',
    'محافظ شرطة أول',
    'محافظ شرطة',
    'نقيب',
    'ملازم أول',
    'ملازم',
    'ضابط شرطة أول',
    'ضابط شرطة',
    'ضابط شرطة مساعد',
    'ناظر أمن أول',
    'ناظر أمن',
    'ناظر أمن مساعد',
    'حافظ أمن',
    'مفتش شرطة أول',
    'مفتش شرطة',
    'رقيب أمن أول',
    'رقيب أمن'
  ];

  personnelList: any[] = [];
  personnelSearchQuery = '';

  get filteredPersonnelList(): any[] {
    if (!this.personnelSearchQuery || this.personnelSearchQuery.trim() === '') {
      return this.personnelList;
    }
    const q = this.personnelSearchQuery.toLowerCase().trim();
    return this.personnelList.filter(p => {
      const reg = (p.registrationNumber || '').toString().toLowerCase();
      const first = (p.firstNameAr || '').toLowerCase();
      const father = (p.fatherNameAr || '').toLowerCase();
      const last = (p.lastNameAr || '').toLowerCase();
      const fullName = `${first} ${father} ${last}`.toLowerCase();
      return reg.includes(q) || fullName.includes(q);
    });
  }

  newRequest: any = {
    personnelId: null,
    startDate: '',
    duration: null,
    endDate: '',
    resumptionDate: '',
    justification: '',
    documentPath: ''
  };

  imagePreviewPersonnel: any = null;

  getAvatar(p: any): string {
    if (!p) return 'assets/avatar_default.png';
    const profilePicture = p.personnelProfilePicture || p.profilePicture;
    const gender = p.personnelGender || p.gender;

    if (profilePicture) {
      let url = profilePicture;
      if (url.includes('minio:9000/')) {
        url = '/' + url.split('minio:9000/')[1];
      } else if (url.includes('localhost:9000/') && window.location.port !== '4200') {
        url = '/' + url.split('localhost:9000/')[1];
      }
      return url;
    }
    if (gender === 'MALE') return 'assets/avatar_male.png';
    if (gender === 'FEMALE') return 'assets/avatar_female.png';
    return 'assets/avatar_default.png';
  }

  openImagePreview(leave: any, event: Event): void {
    if (event) event.stopPropagation();
    this.imagePreviewPersonnel = {
      profilePicture: leave.personnelProfilePicture,
      gender: leave.personnelGender,
      firstNameAr: leave.personnelFirstNameAr,
      lastNameAr: leave.personnelLastNameAr,
      firstNameFr: leave.personnelFirstNameFr,
      lastNameFr: leave.personnelLastNameFr,
      grade: leave.personnelGrade
    };
  }

  closeImagePreview(): void {
    this.imagePreviewPersonnel = null;
  }

  constructor(private http: HttpClient, private authService: AuthService) {}

  get currentUser(): any {
    return this.authService.currentUserValue;
  }

  get annualLeaveBalance(): number {
    const currentYear = new Date().getFullYear();
    const userId = this.currentUser?.personnelId;
    if (!userId) return 45;

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

  ngOnInit(): void {
    this.fetchLeaves();
    if (this.isAgentRhOrAdmin()) {
      this.fetchPersonnel();
    }
  }

  isAgentRhOrAdmin(): boolean {
    return this.authService.hasRole('ROLE_AGENT_RH') || 
           this.authService.hasRole('ROLE_ADMIN_DIRECTION') || 
           this.authService.hasRole('ROLE_SUPER_ADMIN');
  }

  fetchPersonnel(): void {
    const headers = this.authService.getAuthHeaders();
    this.http.get<any[]>('http://localhost:8080/api/personnel', { headers }).subscribe({
      next: (data) => {
        this.personnelList = data.filter(p => !p.archiveStatus || p.archiveStatus === 'ACTIVE');
      },
      error: (err) => console.error(err)
    });
  }

  canApprove(): boolean {
    const roleIdx = this.authService.getRoleHierarchyIndex();
    return roleIdx >= 2; // Chef de Service (2) ou plus
  }

  isDirector(): boolean {
    return this.authService.hasRole('ROLE_ADMIN_DIRECTION') || this.authService.hasRole('ROLE_SUPER_ADMIN');
  }

  isAgentRh(): boolean {
    return this.authService.hasRole('ROLE_AGENT_RH');
  }

  getApprovalStatusForUser(): string {
    const roles = this.authService.currentUserValue?.roles || [];
    if (roles.includes('ROLE_ADMIN_DIRECTION') || roles.includes('ROLE_SUPER_ADMIN')) {
      return 'APPROVED'; // Directeur valide définitivement
    }
    if (roles.includes('ROLE_CHEF_SOUS_DIRECTION')) {
      return 'APPROVED_SD'; // Validation 2ème niveau
    }
    return 'APPROVED_SERVICE'; // Chef de service valide 1er niveau
  }

  fetchLeaves(): void {
    const headers = this.authService.getAuthHeaders();
    this.http.get<any[]>('http://localhost:8080/api/annual-leaves', { headers }).subscribe({
      next: (data) => {
        this.leavesList = data;
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
    if (this.isAgentRhOrAdmin() && !r.personnelId) {
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

  updateStatus(id: number, status: string, justification?: string): void {
    const headers = this.authService.getAuthHeaders();
    this.http.put<any>(`http://localhost:8080/api/annual-leaves/${id}/status?status=${status}&justification=${justification || ''}`, {}, { headers }).subscribe({
      next: () => {
        this.fetchLeaves();
      },
      error: (err) => {
        console.error(err);
        alert(err.error || 'حدث خطأ أثناء تحديث حالة الطلب');
      }
    });
  }

  isSuperAdmin(): boolean {
    return this.authService.hasRole('ROLE_SUPER_ADMIN');
  }

  onDeleteLeave(leaveId: number): void {
    if (confirm('هل أنت متأكد من حذف هذه الإجازة السنوية؟')) {
      const headers = this.authService.getAuthHeaders();
      this.http.delete(`http://localhost:8080/api/annual-leaves/${leaveId}`, { headers }).subscribe({
        next: () => this.fetchLeaves(),
        error: (err) => {
          console.error(err);
          alert(err.error || 'حدث خطأ أثناء حذف الإجازة');
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
    this.personnelSearchQuery = '';
    this.newRequest = {
      personnelId: null,
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
      case 'APPROVED_SERVICE': return 'موافقة رئيس المصلحة';
      case 'APPROVED_SD': return 'موافقة رئيس الإدارة الفرعية';
      case 'APPROVED': return 'موافق عليها';
      case 'REJECTED': return 'مرفوضة';
      case 'LEAVE_STARTED': return 'بدأت الإجازة';
      case 'WORK_RESUMED': return 'تم استئناف العمل';
      case 'PENDING_MODIFICATION': return 'تعديل معلق';
      case 'PENDING_DELETION': return 'حذف معلق';
      default: return status;
    }
  }

  // Helper pagination / sort methods
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
    const list = this.getFilteredLeaves();
    return Math.ceil(list.length / this.pageSize) || 1;
  }

  getPageNumbers(): number[] {
    const total = this.getTotalPages();
    return Array.from({ length: total }, (_, i) => i + 1);
  }

  getFilteredLeaves(): any[] {
    return this.leavesList.filter(l => {
      // 1. Text Search
      if (this.searchQuery) {
        const q = this.searchQuery.toLowerCase().trim();
        const code = (l.leaveCode || '').toString().toLowerCase();
        const name = (l.personnelFullNameAr || '').toLowerCase();
        const unit = (l.personnelOrgUnitNameAr || '').toLowerCase();
        const grade = (l.personnelGrade || '').toLowerCase();
        if (!code.includes(q) && !name.includes(q) && !unit.includes(q) && !grade.includes(q)) {
          return false;
        }
      }

      // 2. Status Filter
      const isApproved = l.status === 'APPROVED' || l.status === 'LEAVE_STARTED' || l.status === 'WORK_RESUMED';
      const isPending = l.status === 'PENDING' || l.status.startsWith('APPROVED_') || l.status.includes('PENDING_');
      const isRejected = l.status === 'REJECTED';

      if (!this.filterApproved && isApproved) return false;
      if (!this.filterPending && isPending) return false;
      if (!this.filterRejected && isRejected) return false;

      return true;
    });
  }

  getSortedAndPaginatedLeaves(): any[] {
    const list = this.getFilteredLeaves();

    // Sorting
    list.sort((a, b) => {
      let valA = a[this.sortField];
      let valB = b[this.sortField];

      if (this.sortField === 'name') {
        valA = a.personnelFullNameAr || '';
        valB = b.personnelFullNameAr || '';
      } else if (this.sortField === 'grade') {
        const idxA = this.gradeOrder.indexOf(a.personnelGrade);
        const idxB = this.gradeOrder.indexOf(b.personnelGrade);
        valA = idxA === -1 ? 999 : idxA;
        valB = idxB === -1 ? 999 : idxB;
        return this.sortDirection === 'asc' ? valB - valA : valA - idxB;
      }

      if (valA < valB) return this.sortDirection === 'asc' ? -1 : 1;
      if (valA > valB) return this.sortDirection === 'asc' ? 1 : -1;
      return 0;
    });

    // Pagination
    const startIndex = (this.currentPage - 1) * this.pageSize;
    return list.slice(startIndex, startIndex + this.pageSize);
  }
}
