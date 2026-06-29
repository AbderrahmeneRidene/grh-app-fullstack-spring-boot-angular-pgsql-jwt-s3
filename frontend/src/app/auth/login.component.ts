import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from './auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './login.component.html',
  styleUrl: './login.component.css'
})
export class LoginComponent {
  credentials = { username: '', password: '' };
  errorMessage = '';
  showPassword = false;

  constructor(public authService: AuthService, private router: Router) {
    if (this.authService.isLoggedIn()) {
      this.redirectUser();
    }
  }

  onSubmit(): void {
    this.errorMessage = '';
    this.authService.login(this.credentials).subscribe({
      next: () => {
        this.redirectUser();
      },
      error: (err) => {
        this.errorMessage = 'اسم المستخدم أو كلمة المرور غير صحيحة';
        console.error(err);
      }
    });
  }

  private redirectUser(): void {
    if (this.authService.hasRole('ROLE_SUPER_ADMIN') || 
        this.authService.hasRole('ROLE_ADMIN_DIRECTION') || 
        this.authService.hasRole('ROLE_AGENT_RH')) {
      this.router.navigate(['/']);
    } else {
      this.router.navigate(['/my-annual-leaves']);
    }
  }
}
