import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { AuthService } from '../auth/auth.service';

@Component({
  selector: 'app-sick-leaves',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './sick-leaves.component.html',
  styleUrl: './sick-leaves.component.css'
})
export class SickLeavesComponent implements OnInit {
  sickLeavesList: any[] = [];
  isSickLeaveModalOpen = false;
  isExtendMode = false;
  editingLeaveId: number | null = null;

  // Pagination & Sorting
  currentPage = 1;
  pageSize = 10;
  pageSizeOptions = [5, 10, 20, 50];
  sortField = 'startDate';
  sortDirection: 'asc' | 'desc' = 'desc';
  searchQuery = '';

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

  newSickLeave: any = {
    personnelId: null,
    startDate: '',
    duration: null,
    endDate: '',
    justification: '',
    documentPath: '',
    extensionNotes: ''
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

  ngOnInit(): void {
    this.fetchSickLeaves();
    if (this.isAgentRhOrAdmin()) {
      this.fetchPersonnel();
    }
  }

  isAgentRhOrAdmin(): boolean {
    return this.authService.hasRole('ROLE_AGENT_RH') || 
           this.authService.hasRole('ROLE_ADMIN_DIRECTION') || 
           this.authService.hasRole('ROLE_SUPER_ADMIN');
  }

  isAgentRh(): boolean {
    return this.authService.hasRole('ROLE_AGENT_RH');
  }

  isDirector(): boolean {
    return this.authService.hasRole('ROLE_ADMIN_DIRECTION') || this.authService.hasRole('ROLE_SUPER_ADMIN');
  }

  getExtendingPersonnelName(): string {
    if (!this.editingLeaveId) return '';
    const leave = this.sickLeavesList.find(l => l.id === this.editingLeaveId);
    return leave ? leave.personnelFullNameAr : '';
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

  fetchSickLeaves(): void {
    const headers = this.authService.getAuthHeaders();
    this.http.get<any[]>('http://localhost:8080/api/sick-leaves', { headers }).subscribe({
      next: (data) => this.sickLeavesList = data,
      error: (err) => console.error(err)
    });
  }

  calculateDates(): void {
    if (this.newSickLeave.startDate && this.newSickLeave.duration) {
      const start = new Date(this.newSickLeave.startDate);
      const duration = parseInt(this.newSickLeave.duration, 10);
      if (!isNaN(start.getTime()) && duration > 0) {
        const end = new Date(start);
        end.setDate(start.getDate() + duration - 1);
        this.newSickLeave.endDate = end.toISOString().split('T')[0];
      } else {
        this.newSickLeave.endDate = '';
      }
    } else {
      this.newSickLeave.endDate = '';
    }
  }

  isFormValid(): boolean {
    if (this.isExtendMode) {
      return this.newSickLeave.endDate && this.newSickLeave.duration > 0;
    }
    const r = this.newSickLeave;
    if (!r.personnelId || !r.startDate || r.duration === null || r.duration === undefined || r.duration <= 0) {
      return false;
    }
    return true;
  }

  openCreateModal(): void {
    this.personnelSearchQuery = '';
    this.isExtendMode = false;
    this.editingLeaveId = null;
    this.newSickLeave = {
      personnelId: null,
      startDate: '',
      duration: null,
      endDate: '',
      justification: '',
      documentPath: '',
      extensionNotes: ''
    };
    this.isSickLeaveModalOpen = true;
  }

  openExtendModal(leave: any): void {
    this.isExtendMode = true;
    this.editingLeaveId = leave.id;
    
    // Calculate current duration
    let currentDuration = leave.duration;
    if (!currentDuration && leave.startDate && leave.endDate) {
      const start = new Date(leave.startDate);
      const end = new Date(leave.endDate);
      const diffTime = Math.abs(end.getTime() - start.getTime());
      currentDuration = Math.ceil(diffTime / (1000 * 60 * 60 * 24)) + 1;
    }

    this.newSickLeave = {
      personnelId: leave.personnelId,
      startDate: leave.startDate,
      duration: currentDuration,
      endDate: leave.endDate,
      justification: leave.justification || '',
      documentPath: leave.documentPath || '',
      extensionNotes: ''
    };
    this.isSickLeaveModalOpen = true;
  }

  closeSickLeaveModal(): void {
    this.personnelSearchQuery = '';
    this.isSickLeaveModalOpen = false;
  }

  onSubmit(): void {
    const headers = this.authService.getAuthHeaders();

    if (this.isExtendMode && this.editingLeaveId) {
      // Extend: only update endDate and extensionNotes
      const payload = {
        endDate: this.newSickLeave.endDate,
        extensionNotes: this.newSickLeave.extensionNotes,
        justification: this.newSickLeave.justification,
        documentPath: this.newSickLeave.documentPath
      };
      this.http.put<any>(`http://localhost:8080/api/sick-leaves/${this.editingLeaveId}`, payload, { headers }).subscribe({
        next: () => {
          this.closeSickLeaveModal();
          this.fetchSickLeaves();
        },
        error: (err) => {
          console.error(err);
          alert(err.error || 'حدث خطأ أثناء تمديد الإجازة');
        }
      });
    } else {
      // Create new sick leave
      const payload = {
        personnelId: this.newSickLeave.personnelId,
        startDate: this.newSickLeave.startDate,
        endDate: this.newSickLeave.endDate,
        justification: this.newSickLeave.justification,
        documentPath: this.newSickLeave.documentPath
      };
      this.http.post<any>('http://localhost:8080/api/sick-leaves', payload, { headers }).subscribe({
        next: () => {
          this.closeSickLeaveModal();
          this.fetchSickLeaves();
        },
        error: (err) => {
          console.error(err);
          alert(err.error || 'حدث خطأ أثناء إنشاء الإجازة المرضية');
        }
      });
    }
  }

  isSuperAdmin(): boolean {
    return this.authService.hasRole('ROLE_SUPER_ADMIN');
  }

  onDeleteSickLeave(leave: any): void {
    if (confirm('هل أنت متأكد من حذف هذه الإجازة المرضية؟')) {
      const headers = this.authService.getAuthHeaders();
      this.http.delete(`http://localhost:8080/api/sick-leaves/${leave.id}`, { headers }).subscribe({
        next: () => this.fetchSickLeaves(),
        error: (err) => console.error(err)
      });
    }
  }

  // Sorting and Pagination
  getSortedAndPaginatedLeaves(): any[] {
    let list = [...this.sickLeavesList];

    if (this.searchQuery && this.searchQuery.trim() !== '') {
      const q = this.searchQuery.toLowerCase().trim();
      list = list.filter(l => {
        const reg = (l.personnelRegistrationNumber || '').toString().toLowerCase();
        const fullName = (l.personnelFullNameAr || '').toLowerCase();
        const code = (l.leaveCode || '').toLowerCase();
        return reg.includes(q) || fullName.includes(q) || code.includes(q);
      });
    }

    list.sort((a, b) => {
      let valA = this.getSortValue(a, this.sortField);
      let valB = this.getSortValue(b, this.sortField);
      if (valA === valB) return 0;
      const modifier = this.sortDirection === 'asc' ? 1 : -1;

      if (this.sortField === 'grade') {
        const idxA = this.gradeOrder.indexOf(valA);
        const idxB = this.gradeOrder.indexOf(valB);
        const posA = idxA !== -1 ? idxA : 999;
        const posB = idxB !== -1 ? idxB : 999;
        return (posA - posB) * modifier;
      }

      if (typeof valA === 'string' && typeof valB === 'string') {
        return valA.localeCompare(valB, 'ar-TN') * modifier;
      }
      return (valA < valB ? -1 : 1) * modifier;
    });

    const maxPages = Math.ceil(list.length / Number(this.pageSize)) || 1;
    if (this.currentPage > maxPages) {
      this.currentPage = maxPages;
    }
    const startIndex = (this.currentPage - 1) * Number(this.pageSize);
    return list.slice(startIndex, startIndex + Number(this.pageSize));
  }

  getSortValue(item: any, field: string): any {
    switch (field) {
      case 'leaveCode': return item.leaveCode || '';
      case 'name': return item.personnelFullNameAr || '';
      case 'grade': return item.personnelGrade || '';
      case 'startDate': return item.startDate || '';
      case 'endDate': return item.endDate || '';
      default: return '';
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
    return Math.ceil(this.sickLeavesList.length / this.pageSize) || 1;
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
