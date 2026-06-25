import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { AuthService } from '../auth/auth.service';

@Component({
  selector: 'app-organization',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './organization.component.html',
  styleUrl: './organization.component.css'
})
export class OrganizationComponent implements OnInit {
  treeData: any[] = [];
  allUnitsList: any[] = [];

  // --- Add modal ---
  isAddUnitModalOpen = false;
  newUnit: any = { nameAr: '', nameFr: '', type: '', parentId: null };

  // --- Edit modal ---
  isEditUnitModalOpen = false;
  editUnit: any = { id: null, nameAr: '', nameFr: '', type: '', parentId: null };

  // --- Error modal (deletion blocked) ---
  isErrorModalOpen = false;
  errorModalMessage = '';

  notificationMessage = '';
  notificationType: 'success' | 'danger' | '' = '';

  showNotification(message: string, type: 'success' | 'danger'): void {
    this.notificationMessage = message;
    this.notificationType = type;
    setTimeout(() => {
      this.notificationMessage = '';
      this.notificationType = '';
    }, 5000);
  }

  constructor(private http: HttpClient, private authService: AuthService) {}

  ngOnInit(): void {
    this.refreshTree();
    this.fetchUnitsList();
  }

  // --- Computed: filtered parents based on type ---
  get filteredParentList(): any[] {
    const t = this.newUnit.type;
    if (t === 'SOUS_DIRECTION') {
      return this.allUnitsList.filter(u => u.type === 'DIRECTION');
    }
    if (t === 'SERVICE') {
      return this.allUnitsList.filter(u => u.type === 'DIRECTION' || u.type === 'SOUS_DIRECTION');
    }
    if (t === 'QISM') {
      return this.allUnitsList.filter(u => u.type === 'DIRECTION' || u.type === 'SOUS_DIRECTION' || u.type === 'SERVICE');
    }
    return this.allUnitsList;
  }

  get filteredEditParentList(): any[] {
    const t = this.editUnit.type;
    if (t === 'SOUS_DIRECTION') {
      return this.allUnitsList.filter(u => u.type === 'DIRECTION' && u.id !== this.editUnit.id);
    }
    if (t === 'SERVICE') {
      return this.allUnitsList.filter(u => (u.type === 'DIRECTION' || u.type === 'SOUS_DIRECTION') && u.id !== this.editUnit.id);
    }
    if (t === 'QISM') {
      return this.allUnitsList.filter(u => (u.type === 'DIRECTION' || u.type === 'SOUS_DIRECTION' || u.type === 'SERVICE') && u.id !== this.editUnit.id);
    }
    return this.allUnitsList.filter(u => u.id !== this.editUnit.id);
  }

  onTypeChange(): void {
    // Reset parent when type changes to avoid invalid combinations
    this.newUnit.parentId = null;
  }

  onEditTypeChange(): void {
    this.editUnit.parentId = null;
  }

  // --- API calls ---
  refreshTree(): void {
    const headers = this.authService.getAuthHeaders();
    this.http.get<any[]>('http://localhost:8080/api/admin/org-units/tree', { headers }).subscribe({
      next: (data) => { this.treeData = data; },
      error: (err) => console.error('Error loading org tree', err)
    });
  }

  fetchUnitsList(): void {
    const headers = this.authService.getAuthHeaders();
    this.http.get<any[]>('http://localhost:8080/api/admin/org-units', { headers }).subscribe({
      next: (data) => { this.allUnitsList = data; },
      error: (err) => console.error('Error loading units list', err)
    });
  }

  // --- Validation Helpers ---
  getArabicNameError(value: string): string | null {
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

  getFrenchNameError(value: string): string | null {
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

  getParentUnitError(type: string, parentId: any): string | null {
    if (!type) return null;
    if (type !== 'DIRECTION' && (parentId === null || parentId === undefined || parentId === '')) {
      return 'يجب اختيار الهيكل الأعلى المسؤول';
    }
    if (type === 'DIRECTION' && parentId !== null && parentId !== undefined && parentId !== '') {
      return 'الهيكل ذو صنف إدارة لا يمكن أن يملك هيكل أعلى مسؤول';
    }
    return null;
  }

  isUnitFormValid(isEdit: boolean): boolean {
    const u = isEdit ? this.editUnit : this.newUnit;
    if (!u) return false;
    if (!u.nameAr || !u.nameAr.trim() || this.getArabicNameError(u.nameAr)) return false;
    if (!u.nameFr || !u.nameFr.trim() || this.getFrenchNameError(u.nameFr)) return false;
    if (!u.type) return false;
    if (this.getParentUnitError(u.type, u.parentId)) return false;
    return true;
  }

  // --- Add ---
  openAddModal(): void {
    this.newUnit = { nameAr: '', nameFr: '', type: '', parentId: null };
    this.isAddUnitModalOpen = true;
  }
  closeAddModal(): void { this.isAddUnitModalOpen = false; }

  onCreateUnit(): void {
    const headers = this.authService.getAuthHeaders();
    this.http.post<any>('http://localhost:8080/api/admin/org-units', this.newUnit, { headers }).subscribe({
      next: () => {
        this.closeAddModal();
        this.refreshTree();
        this.fetchUnitsList();
      },
      error: (err) => console.error('Error creating unit', err)
    });
  }

  // --- Edit ---
  openEditModal(unit: any): void {
    this.editUnit = {
      id: unit.id,
      nameAr: unit.nameAr,
      nameFr: unit.nameFr,
      type: unit.type,
      parentId: unit.parentId ?? null
    };
    this.isEditUnitModalOpen = true;
  }
  closeEditModal(): void { this.isEditUnitModalOpen = false; }

  onUpdateUnit(): void {
    const headers = this.authService.getAuthHeaders();
    this.http.put<any>(`http://localhost:8080/api/admin/org-units/${this.editUnit.id}`, this.editUnit, { headers }).subscribe({
      next: () => {
        this.closeEditModal();
        this.refreshTree();
        this.fetchUnitsList();
      },
      error: (err) => console.error('Error updating unit', err)
    });
  }

  // --- Delete ---
  deleteUnit(id: number): void {
    const headers = this.authService.getAuthHeaders();
    this.http.delete(`http://localhost:8080/api/admin/org-units/${id}`, { headers, responseType: 'text' }).subscribe({
      next: () => {
        this.showNotification('تم حذف الهيكل بنجاح', 'success');
        this.refreshTree();
        this.fetchUnitsList();
      },
      error: (err) => {
        if (err.status === 400) {
          this.showNotification('لا يمكن حذف الهيكل لأنه يحتوي على أعوان', 'danger');
        } else if (err.status === 403) {
          this.showNotification('ليس لديك الصلاحية الكافية لحذف هذا الهيكل التنظيمي.', 'danger');
        } else {
          this.showNotification('حدث خطأ غير متوقع عند محاولة الحذف. يرجى المحاولة مجدداً.', 'danger');
        }
      }
    });
  }

  closeErrorModal(): void { this.isErrorModalOpen = false; }

  // --- Helpers ---
  getTypeLabel(type: string): string {
    switch (type) {
      case 'DIRECTION': return 'إدارة';
      case 'SOUS_DIRECTION': return 'إدارة فرعية';
      case 'QISM': return 'قسم';
      case 'SERVICE': return 'مصلحة';
      default: return type;
    }
  }

  canManage(): boolean {
    return this.authService.hasRole('ROLE_SUPER_ADMIN');
  }
}
