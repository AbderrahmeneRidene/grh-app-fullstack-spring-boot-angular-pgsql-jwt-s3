import { Routes } from '@angular/router';
import { LoginComponent } from './auth/login.component';
import { LayoutComponent } from './layout.component';
import { DashboardComponent } from './dashboard/dashboard.component';
import { OrganizationComponent } from './organization/organization.component';
import { PersonnelComponent } from './personnel/personnel.component';
import { ArchivedPersonnelComponent } from './personnel/archived-personnel.component';
import { AnnualLeavesComponent } from './leaves/annual-leaves.component';
import { ExceptionalLeavesComponent } from './leaves/exceptional-leaves.component';
import { MyAnnualLeavesComponent } from './leaves/my-annual-leaves.component';
import { MyExceptionalLeavesComponent } from './leaves/my-exceptional-leaves.component';
import { SickLeavesComponent } from './leaves/sick-leaves.component';
import { AppSettingsComponent } from './settings/app-settings.component';
import { authGuard } from './auth/auth.guard';

export const routes: Routes = [
  { path: 'login', component: LoginComponent },
  {
    path: '',
    component: LayoutComponent,
    canActivate: [authGuard],
    children: [
      { path: '', component: DashboardComponent },
      { path: 'organization', component: OrganizationComponent },
      { path: 'personnel', component: PersonnelComponent },
      { path: 'archive', component: ArchivedPersonnelComponent },
      { path: 'annual-leaves', component: AnnualLeavesComponent },
      { path: 'exceptional-leaves', component: ExceptionalLeavesComponent },
      { path: 'my-annual-leaves', component: MyAnnualLeavesComponent },
      { path: 'my-exceptional-leaves', component: MyExceptionalLeavesComponent },
      { path: 'sick-leaves', component: SickLeavesComponent },
      { path: 'app-settings', component: AppSettingsComponent }
    ]
  },
  { path: '**', redirectTo: 'login' }
];
