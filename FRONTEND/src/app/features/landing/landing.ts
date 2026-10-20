import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { GlobeHero } from './components/globe-hero/globe-hero';
import { TeamChart } from './components/team-chart/team-chart';

interface Product {
  title: string;
  description: string;
  icon: string;
}

@Component({
  selector: 'app-landing',
  standalone: true,
  imports: [TranslatePipe, CommonModule, RouterLink, GlobeHero, TeamChart],
  templateUrl: './landing.html',
  styleUrl: './landing.scss',
})
export class Landing {
  products: Product[] = [
    {
      title: 'Fixed Deposits',
      description: 'Open, track, and calculate returns on FDs with real-time interest projections.',
      icon: '◆',
    },
    {
      title: 'Forex',
      description: 'Live-tracked currency preferences and cross-border transaction support.',
      icon: '⇄',
    },
    {
      title: 'AI Chatbot',
      description: 'Conversational assistant for account queries, FD guidance, and support.',
      icon: '◐',
    },
    {
      title: 'Reports & Analytics',
      description: 'Transaction history, spend patterns, and account summaries at a glance.',
      icon: '▤',
    },
    {
      title: 'Audit & Compliance',
      description: 'Every action traced — channel, timestamp, and request ID on every transaction.',
      icon: '◈',
    },
    {
      title: 'Secure by Design',
      description: 'Role-based access, encrypted sessions, and full audit trails throughout.',
      icon: '◉',
    },
  ];
}
