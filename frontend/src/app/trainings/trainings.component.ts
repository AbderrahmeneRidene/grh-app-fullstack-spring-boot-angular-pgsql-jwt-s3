import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { AuthService } from '../auth/auth.service';

@Component({
  selector: 'app-trainings',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './trainings.component.html',
  styleUrl: './trainings.component.css'
})
export class TrainingsComponent implements OnInit {
  trainingsList: any[] = [];
  personnelList: any[] = [];
  selectedTraining: any = null;
  enrollmentsList: any[] = [];
  isEnrollmentsModalOpen = false;
  isCreateModalOpen = false;

  selectedPersonnelId = '';
  evalNotes: { [key: number]: string } = {};

  newTraining: any = {
    titleAr: '',
    titleFr: '',
    startDate: '',
    endDate: '',
    institution: ''
  };

  isEditTrainingModalOpen = false;
  editTraining: any = {
    id: null,
    titleAr: '',
    titleFr: '',
    startDate: '',
    endDate: '',
    institution: ''
  };

  constructor(private http: HttpClient, private authService: AuthService) {}

  ngOnInit(): void {
    this.fetchTrainings();
    this.fetchPersonnel();
  }

  canCreate(): boolean {
    const roleIdx = this.authService.getRoleHierarchyIndex();
    return roleIdx >= 3; // Chef de Sous-Direction (3) ou plus
  }

  canEnroll(): boolean {
    return this.authService.hasRole('ROLE_AGENT_RH') || this.authService.hasRole('ROLE_SUPER_ADMIN');
  }

  canEvaluate(): boolean {
    const roleIdx = this.authService.getRoleHierarchyIndex();
    return roleIdx >= 3; // Chef de sous direction ou plus
  }

  fetchTrainings(): void {
    const headers = this.authService.getAuthHeaders();
    this.http.get<any[]>('http://localhost:8080/api/trainings', { headers }).subscribe({
      next: (data) => this.trainingsList = data,
      error: (err) => console.error(err)
    });
  }

  fetchPersonnel(): void {
    const headers = this.authService.getAuthHeaders();
    this.http.get<any[]>('http://localhost:8080/api/personnel', { headers }).subscribe({
      next: (data) => this.personnelList = data,
      error: (err) => console.error(err)
    });
  }

  openEnrollmentsModal(t: any): void {
    this.selectedTraining = t;
    this.selectedPersonnelId = '';
    this.fetchEnrollments();
    this.isEnrollmentsModalOpen = true;
  }
  closeEnrollmentsModal(): void {
    this.isEnrollmentsModalOpen = false;
    this.selectedTraining = null;
  }
  openCreateModal(): void { this.isCreateModalOpen = true; }
  closeCreateModal(): void { this.isCreateModalOpen = false; }

  fetchEnrollments(): void {
    if (!this.selectedTraining) return;
    const headers = this.authService.getAuthHeaders();
    this.http.get<any[]>(`http://localhost:8080/api/trainings/${this.selectedTraining.id}/personnel`, { headers }).subscribe({
      next: (data) => this.enrollmentsList = data,
      error: (err) => console.error(err)
    });
  }

  enrollPersonnel(): void {
    if (!this.selectedTraining || !this.selectedPersonnelId) return;
    const headers = this.authService.getAuthHeaders();
    this.http.post<any>(`http://localhost:8080/api/trainings/${this.selectedTraining.id}/enroll?personnelId=${this.selectedPersonnelId}`, {}, { headers }).subscribe({
      next: () => {
        this.selectedPersonnelId = '';
        this.fetchEnrollments();
      },
      error: (err) => console.error(err)
    });
  }

  evaluateEnrollment(enrollmentId: number, status: string, evaluation: string): void {
    const headers = this.authService.getAuthHeaders();
    const evalText = evaluation ? evaluation : '';
    this.http.put(`http://localhost:8080/api/trainings/enroll/${enrollmentId}/status?status=${status}&evaluation=${evalText}`, {}, { headers }).subscribe({
      next: () => {
        this.fetchEnrollments();
      },
      error: (err) => console.error(err)
    });
  }

  // --- Validation Helpers ---
  getArabicTitleError(value: string): string | null {
    if (!value) return null;
    const clean = value.trim();
    if (clean.length === 0) return null;
    const arabicRegex = /^[\u0600-\u06FF\s0-9\-\(\)\[\]\.\,]+$/;
    if (!arabicRegex.test(clean)) {
      return 'يجب أن يحتوي هذا الحقل على حروف عربية فقط';
    }
    if (clean.length > 100) {
      return 'يجب ألا يتجاوز طول هذا الحقل 100 حرفاً';
    }
    return null;
  }

  getFrenchTitleError(value: string): string | null {
    if (!value) return null;
    const clean = value.trim();
    if (clean.length === 0) return null;
    const frenchRegex = /^[a-zA-Z\s0-9\-\(\)\[\]\.\,\'\u00C0-\u00FF]+$/;
    if (!frenchRegex.test(clean)) {
      return 'يجب أن يحتوي هذا الحقل على حروف لاتينية فقط';
    }
    if (clean.length > 100) {
      return 'يجب ألا يتجاوز طول هذا الحقل 100 حرفاً';
    }
    return null;
  }

  getDateRangeError(start: string, end: string): string | null {
    if (!start || !end) return null;
    const startDate = new Date(start);
    const endDate = new Date(end);
    if (endDate < startDate) {
      return 'تاريخ النهاية لا يمكن أن يكون قبل تاريخ البداية';
    }
    return null;
  }

  isTrainingFormValid(isEdit: boolean): boolean {
    const t = isEdit ? this.editTraining : this.newTraining;
    if (!t) return false;
    if (!t.titleAr || !t.titleAr.trim() || this.getArabicTitleError(t.titleAr)) return false;
    if (t.titleFr && this.getFrenchTitleError(t.titleFr)) return false;
    if (!t.startDate || !t.endDate || this.getDateRangeError(t.startDate, t.endDate)) return false;
    if (!t.institution || !t.institution.trim() || t.institution.length > 100) return false;
    return true;
  }

  onCreateTraining(): void {
    const headers = this.authService.getAuthHeaders();
    this.http.post<any>('http://localhost:8080/api/trainings', this.newTraining, { headers }).subscribe({
      next: () => {
        this.newTraining = { titleAr: '', titleFr: '', startDate: '', endDate: '', institution: '' };
        this.closeCreateModal();
        this.fetchTrainings();
      },
      error: (err) => console.error(err)
    });
  }

  openEditTrainingModal(t: any): void {
    this.editTraining = {
      id: t.id,
      titleAr: t.titleAr,
      titleFr: t.titleFr,
      startDate: t.startDate,
      endDate: t.endDate,
      institution: t.institution
    };
    this.isEditTrainingModalOpen = true;
  }

  closeEditTrainingModal(): void {
    this.isEditTrainingModalOpen = false;
  }

  onUpdateTraining(): void {
    const headers = this.authService.getAuthHeaders();
    this.http.put<any>(`http://localhost:8080/api/trainings/${this.editTraining.id}`, this.editTraining, { headers }).subscribe({
      next: () => {
        this.closeEditTrainingModal();
        this.fetchTrainings();
      },
      error: (err) => console.error('Error updating training', err)
    });
  }

  getStatusAr(status: string): string {
    switch (status) {
      case 'EN_COURS': return 'جارية حالياً';
      case 'TERMINE': return 'تمت بنجاح';
      case 'ECHOUE': return 'رسب / لم يجتز';
      default: return status;
    }
  }
}
