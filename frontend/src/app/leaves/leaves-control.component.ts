import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { AuthService } from '../auth/auth.service';

@Component({
  selector: 'app-leaves-control',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './leaves-control.component.html',
  styleUrl: './leaves-control.component.css'
})
export class LeavesControlComponent implements OnInit {
  leavesList: any[] = [];
  filteredLeaves: any[] = [];
  
  // Filter checkboxes
  filterDeparture = true;
  filterResumption = true;
  searchQuery = '';

  constructor(private http: HttpClient, private authService: AuthService) {}

  ngOnInit(): void {
    this.fetchLeaves();
  }

  fetchLeaves(): void {
    const headers = this.authService.getAuthHeaders();
    this.leavesList = [];

    // Fetch Annual Leaves
    this.http.get<any[]>('http://localhost:8080/api/annual-leaves', { headers }).subscribe({
      next: (annualData) => {
        const annual = annualData
          .filter(l => l.status === 'APPROVED' || l.status === 'LEAVE_STARTED')
          .map(l => ({ ...l, leaveType: 'ANNUEL' }));

        // Fetch Exceptional Leaves
        this.http.get<any[]>('http://localhost:8080/api/exceptional-leaves', { headers }).subscribe({
          next: (exceptionalData) => {
            // Rh agent only validates leaves of duration 1 or 2 days (excluding HALF_DAY, TWO_HOURS)
            const exceptional = exceptionalData
              .filter(l => (l.status === 'APPROVED' || l.status === 'LEAVE_STARTED') && l.duration >= 1.0)
              .map(l => ({ ...l, leaveType: 'EXCEPTIONNEL' }));

            this.leavesList = [...annual, ...exceptional].sort(
              (a, b) => new Date(b.startDate).getTime() - new Date(a.startDate).getTime()
            );
            this.applyFilters();
          },
          error: (err) => console.error(err)
        });
      },
      error: (err) => console.error(err)
    });
  }

  applyFilters(): void {
    this.filteredLeaves = this.leavesList.filter(l => {
      // Filter by status checkboxes
      const matchesDeparture = this.filterDeparture && l.status === 'APPROVED';
      const matchesResumption = this.filterResumption && l.status === 'LEAVE_STARTED';
      if (!matchesDeparture && !matchesResumption) return false;

      // Filter by search query
      if (this.searchQuery.trim() !== '') {
        const query = this.searchQuery.toLowerCase();
        const regMatch = l.personnelRegistrationNumber?.toLowerCase().includes(query);
        const nameMatch = l.personnelFullNameAr?.toLowerCase().includes(query) || 
                          l.personnelFullNameFr?.toLowerCase().includes(query);
        return regMatch || nameMatch;
      }

      return true;
    });
  }

  confirmDeparture(leave: any): void {
    const headers = this.authService.getAuthHeaders();
    const endpoint = leave.leaveType === 'ANNUEL' 
      ? `http://localhost:8080/api/annual-leaves/${leave.id}/status`
      : `http://localhost:8080/api/exceptional-leaves/${leave.id}/status`;

    this.http.put(endpoint, null, {
      headers,
      params: { status: 'LEAVE_STARTED' }
    }).subscribe({
      next: () => {
        this.fetchLeaves();
      },
      error: (err) => console.error(err)
    });
  }

  confirmResumption(leave: any): void {
    const headers = this.authService.getAuthHeaders();
    const endpoint = leave.leaveType === 'ANNUEL' 
      ? `http://localhost:8080/api/annual-leaves/${leave.id}/status`
      : `http://localhost:8080/api/exceptional-leaves/${leave.id}/status`;

    this.http.put(endpoint, null, {
      headers,
      params: { status: 'WORK_RESUMED' }
    }).subscribe({
      next: () => {
        this.fetchLeaves();
      },
      error: (err) => console.error(err)
    });
  }

  getAvatar(p: any): string {
    if (!p) return 'assets/avatar_default.png';
    let pic = p.personnelProfilePicture || p.profilePicture;
    if (pic) {
      if (pic.includes('minio:9000/')) {
        pic = '/' + pic.split('minio:9000/')[1];
      } else if (pic.includes('localhost:9000/') && window.location.port !== '4200') {
        pic = '/' + pic.split('localhost:9000/')[1];
      }
      return pic;
    }
    const gender = p.personnelGender || p.gender;
    if (gender === 'MALE') return 'assets/avatar_male.png';
    if (gender === 'FEMALE') return 'assets/avatar_female.png';
    return 'assets/avatar_default.png';
  }

  getLeaveTypeAr(type: string): string {
    return type === 'ANNUEL' ? 'سنوية' : 'استثنائية';
  }
}
