import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { AuthService } from '../auth/auth.service';
import { Router } from '@angular/router';

@Component({
  selector: 'app-personnel',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './personnel.component.html',
  styleUrl: './personnel.component.css'
})
export class PersonnelComponent implements OnInit {
  personnelList: any[] = [];
  unitsList: any[] = [];
  searchQuery = '';
  selectedPersonnel: any = null;
  imagePreviewPersonnel: any = null;

  // Pagination & Sorting properties
  currentPage = 1;
  pageSize = 10;
  pageSizeOptions = [5, 10, 20, 50];
  sortField = 'registrationNumber';
  sortDirection: 'asc' | 'desc' = 'asc';

  isCreateModalOpen = false;
  isEditModalOpen = false;

  newPersonnel: any = {
    registrationNumber: '',
    firstNameAr: '',
    fatherNameAr: '',
    lastNameAr: '',
    firstNameFr: '',
    lastNameFr: '',
    dateOfBirth: '',
    grade: '',
    functionalPost: '',
    gender: 'MALE',
    maritalStatus: 'CELIBATAIRE',
    phoneNumber: '',
    phoneNumber2: '',
    emergencyPhone: '',
    children: [],
    stages: [],
    profilePicture: '',
    organizationalUnitId: '',
    username: '',
    email: ''
  };

  editPersonnel: any = {
    id: null,
    registrationNumber: '',
    firstNameAr: '',
    fatherNameAr: '',
    lastNameAr: '',
    firstNameFr: '',
    lastNameFr: '',
    dateOfBirth: '',
    grade: '',
    functionalPost: '',
    gender: '',
    maritalStatus: '',
    phoneNumber: '',
    phoneNumber2: '',
    emergencyPhone: '',
    children: [],
    stages: [],
    profilePicture: '',
    organizationalUnitId: '',
    username: '',
    email: '',
    roles: [],
    password: ''
  };

  // Properties from child components
  activeTab = 'info';
  academicList: any[] = [];
  newAcademic: any = { degreeName: '', specialty: '', university: '', graduationYear: null };
  newChild: any = { name: '', birthDate: '' };
  newStage: any = { title: '', startDate: '', duration: '', institution: '' };
  careerList: any[] = [];
  newPromotion: any = { newGrade: '', promotionDate: '', decreeReference: '' };
  role = 'ROLE_USER';
  showEditPassword = false;

  gradeCategories = [
    {
      label: 'السلك الفرعي للزي المدني',
      grades: [
        'محافظ شرطة عام من الصنف الأول',
        'محافظ شرطة عام من الصنف الثاني',
        'محافظ شرطة أعلى',
        'محافظ شرطة أول',
        'محافظ شرطة',
        'ضابط شرطة أول',
        'ضابط شرطة',
        'ضابط شرطة مساعد',
        'مفتش شرطة أول',
        'مفتش شرطة'
      ]
    },
    {
      label: 'السلك الفرعي للزي النظامي',
      grades: [
        'عميد', 'عقيد', 'مقدم', 'رائد', 'نقيب', 'ملازم أول', 'ملازم',
        'ناظر أمن أول', 'ناظر أمن', 'ناظر أمن مساعد', 'حافظ أمن',
        'رقيب أمن أول', 'رقيب أمن'
      ]
    }
  ];

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

  get personnelData(): any {
    return this.isEditModalOpen ? this.editPersonnel : this.newPersonnel;
  }

  get isEdit(): boolean {
    return this.isEditModalOpen;
  }

  constructor(private http: HttpClient, public authService: AuthService, private router: Router) {}

  ngOnInit(): void {
    if (!this.canViewPersonnel()) {
      if (this.authService.hasRole('ROLE_SUPER_ADMIN') || 
          this.authService.hasRole('ROLE_ADMIN_DIRECTION') || 
          this.authService.hasRole('ROLE_AGENT_RH')) {
        this.router.navigate(['/']);
      } else {
        this.router.navigate(['/my-annual-leaves']);
      }
      return;
    }
    this.fetchPersonnel();
    this.fetchUnits();
  }

  canViewPersonnel(): boolean {
    return this.authService.getRoleHierarchyIndex() >= 1;
  }

  canEdit(): boolean {
    return this.authService.hasRole('ROLE_AGENT_RH') || this.authService.hasRole('ROLE_SUPER_ADMIN');
  }

  isAgentRh(): boolean {
    return this.authService.hasRole('ROLE_AGENT_RH') || this.authService.hasRole('ROLE_SUPER_ADMIN');
  }

  isDirecteur(): boolean {
    return this.authService.hasRole('ROLE_ADMIN_DIRECTION') || this.authService.hasRole('ROLE_SUPER_ADMIN');
  }

  requestArchive(p: any): void {
    if (!confirm(`هل أنت متأكد من طلب أرشفة الموظف ${p.firstNameAr} ${p.lastNameAr}؟`)) return;
    const headers = this.authService.getAuthHeaders();
    this.http.post<any>(`http://localhost:8080/api/personnel/${p.id}/request-archive`, {}, { headers }).subscribe({
      next: () => {
        alert('تم تقديم طلب الأرشفة بنجاح، بانتظار موافقة المدير.');
        this.fetchPersonnel();
        if (this.selectedPersonnel && this.selectedPersonnel.id === p.id) {
          this.selectedPersonnel = null;
        }
      },
      error: (err) => alert(err.error || 'حدث خطأ أثناء طلب الأرشفة')
    });
  }

  approveArchive(p: any): void {
    if (!confirm(`هل أنت متأكد من الموافقة على أرشفة الموظف ${p.firstNameAr} ${p.lastNameAr}؟`)) return;
    const headers = this.authService.getAuthHeaders();
    this.http.post<any>(`http://localhost:8080/api/personnel/${p.id}/approve-archive`, {}, { headers }).subscribe({
      next: () => {
        alert('تمت أرشفة الموظف بنجاح.');
        this.fetchPersonnel();
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
        alert('تم رفض الطلب وإلغاء الإجراء.');
        this.fetchPersonnel();
        if (this.selectedPersonnel && this.selectedPersonnel.id === p.id) {
          this.selectedPersonnel = null;
        }
      },
      error: (err) => alert(err.error || 'حدث خطأ أثناء رفض الطلب')
    });
  }

  fetchPersonnel(): void {
    const headers = this.authService.getAuthHeaders();
    this.http.get<any[]>('http://localhost:8080/api/personnel', { headers }).subscribe({
      next: (data) => this.personnelList = data,
      error: (err) => console.error(err)
    });
  }

  fetchUnits(): void {
    const headers = this.authService.getAuthHeaders();
    this.http.get<any[]>('http://localhost:8080/api/admin/org-units', { headers }).subscribe({
      next: (data) => this.unitsList = data,
      error: (err) => console.error(err)
    });
  }

  onSearch(): void {
    if (this.searchQuery.trim() === '') {
      this.fetchPersonnel();
      return;
    }
    const headers = this.authService.getAuthHeaders();
    this.http.get<any[]>(`http://localhost:8080/api/personnel/search?query=${this.searchQuery}`, { headers }).subscribe({
      next: (data) => this.personnelList = data,
      error: (err) => console.error(err)
    });
  }

  selectedPersonnelLeaves: any[] = [];

  get selectedPersonnelAnnualBalance(): number {
    if (!this.selectedPersonnel) return 45;
    const currentYear = new Date().getFullYear();
    const annualDaysUsed = this.selectedPersonnelLeaves
      .filter(l => l.leaveType === 'ANNUEL' && l.status === 'APPROVED' && new Date(l.startDate).getFullYear() === currentYear)
      .reduce((sum, l) => {
        return sum + (l.duration !== null && l.duration !== undefined ? Number(l.duration) : 0);
      }, 0);
    return Math.max(0, 45 - annualDaysUsed);
  }

  get selectedPersonnelExceptionalBalance(): number {
    if (!this.selectedPersonnel) return 6;
    const currentYear = new Date().getFullYear();
    const exceptionalDaysUsed = this.selectedPersonnelLeaves
      .filter(l => l.leaveType === 'EXCEPTIONNEL' && l.status === 'APPROVED' && new Date(l.startDate).getFullYear() === currentYear)
      .reduce((sum, l) => {
        return sum + (l.duration !== null && l.duration !== undefined ? Number(l.duration) : 1.0);
      }, 0);
    return Math.max(0, 6 - exceptionalDaysUsed);
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

  fetchSelectedPersonnelLeaves(): void {
    if (!this.selectedPersonnel) return;
    const headers = this.authService.getAuthHeaders();
    this.selectedPersonnelLeaves = [];

    // Fetch Annual Leaves
    this.http.get<any[]>('http://localhost:8080/api/annual-leaves', { headers }).subscribe({
      next: (data) => {
        const filtered = data
          .filter(l => l.personnelId === this.selectedPersonnel.id)
          .map(l => ({ ...l, leaveType: 'ANNUEL' }));
        this.selectedPersonnelLeaves = [...this.selectedPersonnelLeaves, ...filtered].sort(
          (a, b) => new Date(b.startDate).getTime() - new Date(a.startDate).getTime()
        );
      },
      error: (err) => console.error(err)
    });

    // Fetch Exceptional Leaves
    this.http.get<any[]>('http://localhost:8080/api/exceptional-leaves', { headers }).subscribe({
      next: (data) => {
        const filtered = data
          .filter(l => l.personnelId === this.selectedPersonnel.id)
          .map(l => ({ ...l, leaveType: 'EXCEPTIONNEL' }));
        this.selectedPersonnelLeaves = [...this.selectedPersonnelLeaves, ...filtered].sort(
          (a, b) => new Date(b.startDate).getTime() - new Date(a.startDate).getTime()
        );
      },
      error: (err) => console.error(err)
    });

    // Fetch Sick Leaves
    this.http.get<any[]>('http://localhost:8080/api/sick-leaves', { headers }).subscribe({
      next: (data) => {
        const filtered = data
          .filter(l => l.personnelId === this.selectedPersonnel.id)
          .map(l => ({ ...l, leaveType: 'MALADIE' }));
        this.selectedPersonnelLeaves = [...this.selectedPersonnelLeaves, ...filtered].sort(
          (a, b) => new Date(b.startDate).getTime() - new Date(a.startDate).getTime()
        );
      },
      error: (err) => console.error(err)
    });
  }

  get todayDate(): string {
    const today = new Date();
    const yyyy = today.getFullYear();
    const mm = String(today.getMonth() + 1).padStart(2, '0');
    const dd = String(today.getDate()).padStart(2, '0');
    return `${dd}-${mm}-${yyyy}`;
  }

  isPreviewModalOpen = false;

  openPreview(): void {
    this.isPreviewModalOpen = true;
  }

  closePreview(): void {
    this.isPreviewModalOpen = false;
  }

  printDocument(): void {
    window.print();
  }

  downloadPDF(): void {
    if (!this.selectedPersonnel) return;
    const element = document.getElementById('personnel-pdf-content');
    if (!element) return;
    if ((window as any).html2pdf) {
      this.runHtml2Pdf(element);
    } else {
      const script = document.createElement('script');
      script.src = 'https://cdnjs.cloudflare.com/ajax/libs/html2pdf.js/0.10.1/html2pdf.bundle.min.js';
      script.onload = () => {
        this.runHtml2Pdf(element);
      };
      document.body.appendChild(script);
    }
  }

  private runHtml2Pdf(element: HTMLElement): void {
    const regNum = this.selectedPersonnel?.registrationNumber || 'agent';
    const opt = {
      margin:       [12, 12, 12, 12],
      filename:     `fiche_personnel_${regNum}.pdf`,
      image:        { type: 'jpeg', quality: 0.98 },
      html2canvas:  { scale: 2, useCORS: true, letterRendering: true },
      jsPDF:        { unit: 'mm', format: 'a4', orientation: 'portrait' }
    };
    (window as any).html2pdf().set(opt).from(element).save();
  }

  viewProfile(p: any): void {
    this.selectedPersonnel = p;
    this.activeTab = 'info';
    this.selectedPersonnelLeaves = [];
    this.fetchAcademic();
    this.fetchCareer();
    this.fetchSelectedPersonnelLeaves();
  }

  onDetailClose(): void {
    this.selectedPersonnel = null;
    this.selectedPersonnelLeaves = [];
    this.fetchPersonnel();
  }

  openCreateModal(): void {
    this.role = 'ROLE_USER';
    this.isCreateModalOpen = true;
  }

  openEditModal(p: any): void {
    this.editPersonnel = {
      id: p.id,
      registrationNumber: p.registrationNumber,
      firstNameAr: p.firstNameAr,
      fatherNameAr: p.fatherNameAr || '',
      lastNameAr: p.lastNameAr,
      firstNameFr: p.firstNameFr || '',
      lastNameFr: p.lastNameFr || '',
      dateOfBirth: p.dateOfBirth,
      grade: p.grade,
      functionalPost: p.functionalPost || '',
      gender: p.gender || 'MALE',
      maritalStatus: p.maritalStatus || 'CELIBATAIRE',
      phoneNumber: p.phoneNumber || '',
      phoneNumber2: p.phoneNumber2 || '',
      emergencyPhone: p.emergencyPhone || '',
      children: p.children ? JSON.parse(JSON.stringify(p.children)) : [],
      stages: p.stages ? JSON.parse(JSON.stringify(p.stages)) : [],
      profilePicture: p.profilePicture || '',
      organizationalUnitId: p.organizationalUnitId,
      username: p.username || '',
      email: p.email || '',
      roles: p.roles ? [...p.roles] : [],
      password: ''
    };
    this.role = this.editPersonnel.roles && this.editPersonnel.roles.length > 0 ? this.editPersonnel.roles[0] : 'ROLE_USER';
    this.isEditModalOpen = true;
  }

  closeFormModal(): void {
    this.isCreateModalOpen = false;
    this.isEditModalOpen = false;
    this.newPersonnel = {
      registrationNumber: '',
      firstNameAr: '',
      fatherNameAr: '',
      lastNameAr: '',
      firstNameFr: '',
      lastNameFr: '',
      dateOfBirth: '',
      grade: '',
      functionalPost: '',
      gender: 'MALE',
      maritalStatus: 'CELIBATAIRE',
      phoneNumber: '',
      phoneNumber2: '',
      emergencyPhone: '',
      children: [],
      stages: [],
      profilePicture: '',
      organizationalUnitId: '',
      username: '',
      email: ''
    };
  }

  submitForm(): void {
    const headers = this.authService.getAuthHeaders();
    if (this.isEditModalOpen) {
      const payload = {
        ...this.editPersonnel,
        roles: [this.role]
      };
      this.http.put<any>(`http://localhost:8080/api/personnel/${payload.id}`, payload, { headers }).subscribe({
        next: (updated) => {
          this.closeFormModal();
          this.selectedPersonnel = updated;
          if (this.authService.currentUserValue?.personnelId === updated.id) {
            this.authService.updateCurrentUserField('profilePicture', updated.profilePicture);
            if (updated.username) {
              this.authService.updateCurrentUserField('username', updated.username);
            }
          }
          this.fetchPersonnel();
        },
        error: (err) => alert(err.error || 'حدث خطأ أثناء التحديث')
      });
    } else {
      if (!this.newPersonnel.email || !this.newPersonnel.email.trim()) {
        const regNum = this.newPersonnel.registrationNumber ? this.newPersonnel.registrationNumber.trim() : '';
        this.newPersonnel.email = `ag${regNum}@interior.gov.tn`;
      }
      const payload = {
        ...this.newPersonnel,
        roles: [this.role]
      };
      this.http.post<any>('http://localhost:8080/api/personnel', payload, { headers }).subscribe({
        next: () => {
          this.closeFormModal();
          this.fetchPersonnel();
        },
        error: (err) => alert(err.error || 'حدث خطأ أثناء التسجيل')
      });
    }
  }

  // Academic and Career methods (from details component)
  fetchAcademic(): void {
    if (!this.selectedPersonnel) return;
    const headers = this.authService.getAuthHeaders();
    this.http.get<any[]>(`http://localhost:8080/api/personnel/${this.selectedPersonnel.id}/academic`, { headers }).subscribe({
      next: (data) => this.academicList = data,
      error: (err) => console.error(err)
    });
  }

  addAcademic(): void {
    if (!this.selectedPersonnel) return;
    const headers = this.authService.getAuthHeaders();
    this.http.post<any>(`http://localhost:8080/api/personnel/${this.selectedPersonnel.id}/academic`, this.newAcademic, { headers }).subscribe({
      next: () => {
        this.newAcademic = { degreeName: '', specialty: '', university: '', graduationYear: null };
        this.fetchAcademic();
      },
      error: (err) => console.error(err)
    });
  }

  fetchCareer(): void {
    if (!this.selectedPersonnel) return;
    const headers = this.authService.getAuthHeaders();
    this.http.get<any[]>(`http://localhost:8080/api/personnel/${this.selectedPersonnel.id}/career`, { headers }).subscribe({
      next: (data) => this.careerList = data,
      error: (err) => console.error(err)
    });
  }

  promotePersonnel(): void {
    if (!this.selectedPersonnel) return;
    const headers = this.authService.getAuthHeaders();
    this.http.post<any>(`http://localhost:8080/api/personnel/${this.selectedPersonnel.id}/career`, this.newPromotion, { headers }).subscribe({
      next: () => {
        this.selectedPersonnel.grade = this.newPromotion.newGrade;
        this.newPromotion = { newGrade: '', promotionDate: '', decreeReference: '' };
        this.fetchCareer();
      },
      error: (err) => console.error(err)
    });
  }

  addChildToDetail(): void {
    if (!this.selectedPersonnel) return;
    const headers = this.authService.getAuthHeaders();
    this.http.post<any>(`http://localhost:8080/api/personnel/${this.selectedPersonnel.id}/children`, this.newChild, { headers }).subscribe({
      next: (updatedPersonnel) => {
        this.selectedPersonnel.children = updatedPersonnel.children;
        this.newChild = { name: '', birthDate: '' };
      },
      error: (err) => console.error(err)
    });
  }

  addStageToDetail(): void {
    if (!this.selectedPersonnel) return;
    const headers = this.authService.getAuthHeaders();
    this.http.post<any>(`http://localhost:8080/api/personnel/${this.selectedPersonnel.id}/stages`, this.newStage, { headers }).subscribe({
      next: (updatedPersonnel) => {
        this.selectedPersonnel.stages = updatedPersonnel.stages;
        this.newStage = { title: '', startDate: '', duration: '', institution: '' };
      },
      error: (err) => console.error(err)
    });
  }

  // Avatar upload and dynamic lists methods (from form component)
  onFileSelected(event: any): void {
    const file = event.target.files[0];
    if (file) {
      const reader = new FileReader();
      reader.onload = (e: any) => {
        this.personnelData.profilePicture = e.target.result;
      };
      reader.readAsDataURL(file);
    }
  }

  deleteAvatar(): void {
    this.personnelData.profilePicture = '';
  }

  addChild(): void {
    this.personnelData.children.push({ name: '', birthDate: '' });
  }

  removeChild(index: number): void {
    this.personnelData.children.splice(index, 1);
  }

  addStage(): void {
    this.personnelData.stages.push({ title: '', startDate: '', duration: '', institution: '' });
  }

  removeStage(index: number): void {
    this.personnelData.stages.splice(index, 1);
  }

  getAvatar(p: any): string {
    if (!p) return 'assets/avatar_default.png';
    if (p.profilePicture) {
      let url = p.profilePicture;
      if (url.includes('minio:9000/')) {
        url = '/' + url.split('minio:9000/')[1];
      } else if (url.includes('localhost:9000/') && window.location.port !== '4200') {
        url = '/' + url.split('localhost:9000/')[1];
      }
      return url;
    }
    const gender = p.gender;
    if (gender === 'MALE') return 'assets/avatar_male.png';
    if (gender === 'FEMALE') return 'assets/avatar_female.png';
    return 'assets/avatar_default.png';
  }

  openImagePreview(p: any, event: Event): void {
    event.stopPropagation();
    this.imagePreviewPersonnel = p;
  }

  closeImagePreview(): void {
    this.imagePreviewPersonnel = null;
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

  // Validation functions (from form component)
  getArabicNameError(value: string): string | null {
    if (!value) return null;
    const clean = value.trim();
    if (clean.length === 0) return null;
    const arabicRegex = /^[\u0600-\u06FF\s]+$/;
    if (!arabicRegex.test(clean)) {
      return 'يجب أن يحتوي هذا الحقل على حروف عربية فقط';
    }
    if (clean.length > 20) {
      return 'يجب ألا يتجاوز طول هذا الحقل 20 حرفاً';
    }
    return null;
  }

  getFrenchNameError(value: string): string | null {
    if (!value) return null;
    const clean = value.trim();
    if (clean.length === 0) return null;
    const frenchRegex = /^[a-zA-Z\s\-]+$/;
    if (!frenchRegex.test(clean)) {
      return 'يجب أن يحتوي هذا الحقل على حروف لاتينية فقط';
    }
    if (clean.length > 30) {
      return 'يجب ألا يتجاوز طول هذا الحقل 30 حرفاً';
    }
    return null;
  }

  calculateAge(birthDateStr: string): number {
    if (!birthDateStr) return 0;
    const today = new Date();
    const birthDate = new Date(birthDateStr);
    let age = today.getFullYear() - birthDate.getFullYear();
    const m = today.getMonth() - birthDate.getMonth();
    if (m < 0 || (m === 0 && today.getDate() < birthDate.getDate())) {
      age--;
    }
    return age;
  }

  getAgeError(dob: string): string | null {
    if (!dob) return null;
    const age = this.calculateAge(dob);
    if (age < 0) {
      return 'تاريخ الولادة غير صالح';
    }
    if (age < 18 || age > 60) {
      return 'يجب أن يكون السن بين 18 و 60 سنة';
    }
    return null;
  }

  getPhoneError(value: string): string | null {
    if (!value) return null;
    const clean = value.trim();
    if (clean.length === 0) return null;
    const phoneRegex = /^\d{8}$/;
    if (!phoneRegex.test(clean)) {
      return 'يجب أن يتكون رقم الهاتف من 8 أرقام بالضبط';
    }
    return null;
  }

  getEmailError(value: string): string | null {
    if (!value) return null;
    const clean = value.trim();
    if (clean.length === 0) return null;
    const emailRegex = /^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$/;
    if (!emailRegex.test(clean)) {
      return 'البريد الإلكتروني غير صالح';
    }
    return null;
  }

  getRegNumError(value: string): string | null {
    if (!value) return null;
    const clean = value.toString().trim();
    if (clean.length === 0) return null;
    const regRegex = /^[a-zA-Z0-9]{4,10}$/;
    if (!regRegex.test(clean)) {
      return 'يجب أن يتكون المعرف من 4 إلى 10 رموز وأرقام وبدون فراغات';
    }
    return null;
  }

  isFormValid(): boolean {
    const p = this.personnelData;
    if (!p) return false;
    
    if (!p.registrationNumber || !p.registrationNumber.toString().trim()) return false;
    if (!this.isEdit && this.getRegNumError(p.registrationNumber)) return false;

    if (!p.firstNameAr || !p.firstNameAr.trim()) return false;
    if (this.getArabicNameError(p.firstNameAr)) return false;

    if (!p.fatherNameAr || !p.fatherNameAr.trim()) return false;
    if (this.getArabicNameError(p.fatherNameAr)) return false;

    if (!p.lastNameAr || !p.lastNameAr.trim()) return false;
    if (this.getArabicNameError(p.lastNameAr)) return false;

    if (!p.dateOfBirth) return false;
    if (this.getAgeError(p.dateOfBirth)) return false;

    if (!p.grade) return false;
    if (!p.organizationalUnitId) return false;
    if (!p.gender) return false;
    if (!p.maritalStatus) return false;

    if (p.firstNameFr && this.getFrenchNameError(p.firstNameFr)) return false;
    if (p.lastNameFr && this.getFrenchNameError(p.lastNameFr)) return false;
    if (p.phoneNumber && this.getPhoneError(p.phoneNumber)) return false;
    if (p.phoneNumber2 && this.getPhoneError(p.phoneNumber2)) return false;
    if (p.emergencyPhone && this.getPhoneError(p.emergencyPhone)) return false;
    if (p.email && this.getEmailError(p.email)) return false;

    if (p.children) {
      for (const child of p.children) {
        if (!child.name || !child.name.trim() || !child.birthDate) {
          return false;
        }
        const nameError = this.getArabicNameError(child.name) && this.getFrenchNameError(child.name);
        if (nameError) return false;
        const childAge = this.calculateAge(child.birthDate);
        if (childAge < 0) return false;
      }
    }

    if (p.stages) {
      for (const stage of p.stages) {
        if (!stage.title || !stage.title.trim() || !stage.startDate || !stage.duration || !stage.duration.trim() || !stage.institution || !stage.institution.trim()) {
          return false;
        }
        const stageAge = this.calculateAge(stage.startDate);
        if (stageAge < 0) return false;
      }
    }

    return true;
  }

  // Sorting & pagination helpers
  getSortedAndPaginatedPersonnel(): any[] {
    let list = [...this.personnelList];

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

    const maxPages = Math.ceil(list.length / this.pageSize) || 1;
    if (this.currentPage > maxPages) {
      this.currentPage = maxPages;
    }

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
      case 'orgUnit':
        return item.organizationalUnitNameAr || '';
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
    return Math.ceil(this.personnelList.length / this.pageSize) || 1;
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
