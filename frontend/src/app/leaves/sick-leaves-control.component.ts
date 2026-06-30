import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { AuthService } from '../auth/auth.service';

@Component({
  selector: 'app-sick-leaves-control',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './sick-leaves-control.component.html',
  styleUrl: './sick-leaves-control.component.css'
})
export class SickLeavesControlComponent implements OnInit {
  leavesList: any[] = [];
  filteredLeaves: any[] = [];
  
  // Filter checkboxes
  filterDeparture = false; // By default, sick leaves start immediately, so departure is false or not shown
  filterResumption = true;
  searchQuery = '';

  constructor(private http: HttpClient, private authService: AuthService) {}

  ngOnInit(): void {
    this.fetchLeaves();
  }

  fetchLeaves(): void {
    const headers = this.authService.getAuthHeaders();
    this.http.get<any[]>('http://localhost:8080/api/sick-leaves', { headers }).subscribe({
      next: (data) => {
        // Fallback older records where status is null/empty to LEAVE_STARTED
        this.leavesList = data.map(l => ({ ...l, status: l.status || 'LEAVE_STARTED' }))
                              .filter(l => l.status === 'LEAVE_STARTED' || l.status === 'APPROVED');
        this.applyFilters();
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

  confirmResumption(leave: any): void {
    const headers = this.authService.getAuthHeaders();
    this.http.put(`http://localhost:8080/api/sick-leaves/${leave.id}/status`, null, {
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
}
