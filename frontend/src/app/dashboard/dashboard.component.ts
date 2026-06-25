import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { AuthService } from '../auth/auth.service';
import { Router } from '@angular/router';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.css'
})
export class DashboardComponent implements OnInit {
  stats: any;
  malePercent = 0;
  femalePercent = 0;
  ageGroups: { label: string; count: number; percent: number }[] = [];
  isPreviewModalOpen = false;

  // Catégories de grades (ضباط سامون، ضباط أعوان، أعوان)
  seniorCount = 0;
  juniorCount = 0;
  agentsCount = 0;
  totalOfficersAndAgents = 0;
  seniorPercent = 0;
  juniorPercent = 0;
  agentsPercent = 0;

  // Prévisualisation d'image
  isImagePreviewModalOpen = false;
  imagePreviewPersonnel: any = null;

  gradeCategories = [
    {
      key: 'civil',
      label: 'السلك الفرعي للزي المدني',
      color: 'bg-blue',
      icon: 'fa-solid fa-user-tie',
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
      key: 'system',
      label: 'السلك الفرعي للزي النظامي',
      color: 'bg-red',
      icon: 'fa-solid fa-user-shield',
      grades: [
        'عميد', 'عقيد', 'مقدم', 'رائد', 'نقيب', 'ملازم أول', 'ملازم',
        'ناظر أمن أول', 'ناظر أمن', 'ناظر أمن مساعد', 'حافظ أمن',
        'رقيب أمن أول', 'رقيب أمن'
      ]
    }
  ];

  constructor(
    private http: HttpClient,
    private authService: AuthService,
    private router: Router
  ) {}

  canViewDashboard(): boolean {
    return this.authService.hasRole('ROLE_SUPER_ADMIN') || 
           this.authService.hasRole('ROLE_ADMIN_DIRECTION') || 
           this.authService.hasRole('ROLE_AGENT_RH');
  }

  ngOnInit(): void {
    if (!this.canViewDashboard()) {
      this.router.navigate(['/personnel']);
      return;
    }
    this.fetchStats();
  }

  fetchStats(): void {
    const headers = this.authService.getAuthHeaders();
    this.http.get<any>('http://localhost:8080/api/dashboard/stats', { headers }).subscribe({
      next: (data) => {
        this.stats = data;
        const totalActive = (data.activeMaleCount || 0) + (data.activeFemaleCount || 0);
        if (totalActive > 0) {
          this.malePercent = Math.round((data.activeMaleCount / totalActive) * 100);
          this.femalePercent = Math.round((data.activeFemaleCount / totalActive) * 100);
        } else {
          this.malePercent = 0;
          this.femalePercent = 0;
        }



        if (data.activePersonnelByAgeGroup) {
          const ageKeys = Object.keys(data.activePersonnelByAgeGroup);
          let totalAgeCount = 0;
          ageKeys.forEach(k => totalAgeCount += data.activePersonnelByAgeGroup[k]);

          this.ageGroups = ageKeys.map(k => {
            const count = data.activePersonnelByAgeGroup[k];
            const percent = totalAgeCount > 0 ? Math.round((count / totalAgeCount) * 100) : 0;
            return { label: k, count, percent };
          });
        }

        // Calcul des catégories (ضباط سامون، ضباط أعوان، أعوان)
        if (data.personnelByGrade) {
          const seniorGrades = ['عميد', 'عقيد', 'مقدم', 'رائد', 'محافظ شرطة عام من الصنف الأول', 'محافظ شرطة عام من الصنف الثاني', 'محافظ شرطة أعلى'];
          const juniorGrades = ['محافظ شرطة أول', 'محافظ شرطة', 'نقيب', 'ملازم أول', 'ملازم'];

          let senior = 0;
          let junior = 0;
          let agents = 0;

          Object.keys(data.personnelByGrade).forEach(grade => {
            const count = data.personnelByGrade[grade] || 0;
            if (seniorGrades.includes(grade)) {
              senior += count;
            } else if (juniorGrades.includes(grade)) {
              junior += count;
            } else {
              agents += count;
            }
          });

          this.seniorCount = senior;
          this.juniorCount = junior;
          this.agentsCount = agents;
          this.totalOfficersAndAgents = senior + junior + agents;

          if (this.totalOfficersAndAgents > 0) {
            this.seniorPercent = Math.round((senior / this.totalOfficersAndAgents) * 100);
            this.juniorPercent = Math.round((junior / this.totalOfficersAndAgents) * 100);
            this.agentsPercent = Math.round((agents / this.totalOfficersAndAgents) * 100);
          } else {
            this.seniorPercent = 0;
            this.juniorPercent = 0;
            this.agentsPercent = 0;
          }
        }
      },
      error: (err) => console.error('Error fetching dashboard stats', err)
    });
  }

  getMapKeys(map: any): string[] {
    if (!map) return [];
    return Object.keys(map);
  }

  getProgressPercent(value: number, total: number): number {
    if (!total || total === 0) return 0;
    return Math.round((value / total) * 100);
  }

  getGradesForCategory(category: any): { name: string; count: number }[] {
    if (!this.stats || !this.stats.personnelByGrade) return [];
    return category.grades
      .filter((gradeName: string) => this.stats.personnelByGrade[gradeName] !== undefined && this.stats.personnelByGrade[gradeName] > 0)
      .map((gradeName: string) => ({
        name: gradeName,
        count: this.stats.personnelByGrade[gradeName]
      }));
  }

  getGradeCountLabel(gradeName: string, count: number): string {
    const isItar = [
      'عميد', 'عقيد', 'مقدم', 'رائد', 'نقيب', 'ملازم أول', 'ملازم',
      'محافظ شرطة عام من الصنف الأول',
      'محافظ شرطة عام من الصنف الثاني',
      'محافظ شرطة أعلى',
      'محافظ شرطة أول',
      'محافظ شرطة'
    ].includes(gradeName);


    if (isItar) {
      if (count === 1) return 'إطار (1)';
      if (count === 2) return 'إطاران (2)';
      if (count >= 3 && count <= 10) return `${count} إطارات`;
      return `${count} إطار`;
    } else {
      if (count === 1) return 'عون (1)';
      if (count === 2) return 'عونان (2)';
      if (count >= 3 && count <= 10) return `${count} أعوان`;
      return `${count} عون`;
    }
  }

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
    const element = document.getElementById('print-preview-document');
    if (!element) return;
    
    if ((window as any).html2pdf) {
      this.runHtml2Pdf(element);
    } else {
      const script = document.createElement('script');
      script.src = 'https://cdnjs.cloudflare.com/ajax/libs/html2pdf.js/0.10.1/html2pdf.bundle.min.js';
      script.onload = () => {
        this.runHtml2Pdf(element);
      };
      document.head.appendChild(script);
    }
  }

  private runHtml2Pdf(element: HTMLElement): void {
    const opt = {
      margin:       10,
      filename:     'rapport_dashboard.pdf',
      image:        { type: 'jpeg', quality: 0.98 },
      html2canvas:  { scale: 2, useCORS: true },
      jsPDF:        { unit: 'mm', format: 'a4', orientation: 'portrait' }
    };
    (window as any).html2pdf().set(opt).from(element).save();
  }

  getFormattedDate(): string {
    const today = new Date();
    return today.toLocaleDateString('ar-TN', {
      weekday: 'long',
      year: 'numeric',
      month: 'long',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    });
  }

  getAvatar(p: any): string {
    if (!p) return 'assets/avatar_default.png';
    // Si c'est un leave, p peut avoir personnelProfilePicture
    if (p.personnelProfilePicture) return p.personnelProfilePicture;
    if (p.profilePicture) return p.profilePicture;
    const gender = p.personnelGender || p.gender;
    if (gender === 'MALE') return 'assets/avatar_male.png';
    if (gender === 'FEMALE') return 'assets/avatar_female.png';
    return 'assets/avatar_default.png';
  }

  openImagePreview(p: any, event: Event): void {
    if (event) event.stopPropagation();
    // Créer un format uniforme
    this.imagePreviewPersonnel = {
      personnelFirstNameAr: p.personnelFirstNameAr || p.firstNameAr,
      personnelFatherNameAr: p.personnelFatherNameAr || p.fatherNameAr,
      personnelLastNameAr: p.personnelLastNameAr || p.lastNameAr,
      personnelRegistrationNumber: p.personnelRegistrationNumber || p.registrationNumber,
      personnelGrade: p.personnelGrade || p.grade,
      personnelProfilePicture: p.personnelProfilePicture || p.profilePicture,
      personnelGender: p.personnelGender || p.gender
    };
    this.isImagePreviewModalOpen = true;
  }

  closeImagePreview(): void {
    this.imagePreviewPersonnel = null;
    this.isImagePreviewModalOpen = false;
  }
}
