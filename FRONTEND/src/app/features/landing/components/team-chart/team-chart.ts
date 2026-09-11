import { Component, ElementRef, AfterViewInit, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';

interface ModuleTeam {
  name: string;
  memberCount: number;
  members: string[];
}

@Component({
  selector: 'app-team-chart',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './team-chart.html',
  styleUrl: './team-chart.scss',
})
export class TeamChart implements AfterViewInit {
  @ViewChild('chartRoot', { static: true }) chartRoot!: ElementRef<HTMLDivElement>;

  leader = 'Team Lead - Shaunak Naveen Malpani';

  modules: ModuleTeam[] = [
    {
      name: 'Module 1',
      memberCount: 6,
      members: [
        'Mahir Mittal - Group Lead',
        'Anjana V Acharya - Frontend Developer',
        'Anvi Sharma - Security & Authorization',
        'Tanay Mahesh - Reports & Data Aggregation',
        'Niral Pujeri - FD Calculation logic Bankend',
        'Krishna Kamath - Python Integration & Reports',
      ],
    },
    {
      name: 'Module 2',
      memberCount: 6,
      members: [
        'Vansh Jaju - Group Lead',
        'Asavari Kaushal - Project Management',
        'Krishang Jain - Integration & Quality',
        'Danielle Lynn Gonsalves - Business & Trasaction',
        'J. Sheshanth - Pricing & Rate Management',
        'Sanigarapu Spoorthy - Product Charges & Benifits',
      ],
    },
    {
      name: 'Module 3',
      memberCount: 6,
      members: [
        'Ayush Kapoor - Group Lead',
        'Dev Sharma - Transaction & Lifecycle ',
        'Pranay Yashwin - Frontend Integration & Testing',
        'Sudhiksha Shanbhag - FD Account Creation',
        'Ramnath Pai - Batch Processing & Research',
        'Tanvika Shetty - Events & Notification',
      ],
    },
  ];

  isGlowing = false;

  ngAfterViewInit(): void {
    const observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            this.isGlowing = true;
            observer.disconnect();
          }
        });
      },
      { threshold: 0.3 }
    );
    observer.observe(this.chartRoot.nativeElement);
  }
}
