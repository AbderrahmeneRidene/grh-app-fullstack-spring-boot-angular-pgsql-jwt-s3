import { Routes } from '@angular/router';
import { LoginComponent } from './auth/login.component';
import { LayoutComponent } from './layout.component';
import { DashboardComponent } from './dashboard/dashboard.component';
import { OrganizationComponent } from './organization/organization.component';
import { PersonnelComponent } from './personnel/personnel.component';
import { ArchivedPersonnelComponent } from './personnel/archived-personnel.component';
import { LeavesComponent } from './leaves/leaves.component';
import { MyLeavesComponent } from './leaves/my-leaves.component';
import { TrainingsComponent } from './trainings/trainings.component';
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
      { path: 'leaves', component: LeavesComponent },
      { path: 'my-leaves', component: MyLeavesComponent },
      { path: 'trainings', component: TrainingsComponent }
    ]
  },
  { path: '**', redirectTo: 'login' }
];
