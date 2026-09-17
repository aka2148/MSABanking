# BankingTechnology Project Context

## Project Overview
**Project Name:** Banking Lab  
**Location:** `c:\bankingtech\BankingTechnology`  
**Purpose:** A full-stack banking application with frontend and backend components.

---

## Architecture Overview

### Directory Structure
```
BankingTechnology/
├── BACKEND/                    # Backend services (currently minimal)
│   └── README.MD              # (empty)
├── FRONTEND/                   # Angular frontend application
│   └── app/                    # Main Angular app
│       ├── src/                # Source code
│       ├── public/             # Static assets
│       ├── angular.json        # Angular CLI configuration
│       ├── package.json        # NPM dependencies
│       ├── tsconfig.json       # TypeScript configuration
│       └── .env                # Environment variables
├── .git/                       # Git repository
└── README.MD                   # Project root documentation
```

---

## Tech Stack

### Frontend
- **Framework:** Angular 21.2.0 (latest)
- **Language:** TypeScript 5.9.2
- **Styling:** 
  - Tailwind CSS 4.1.12 (with PostCSS)
  - Custom CSS with design tokens (OKLch colors)
- **Backend Integration:** Supabase (via @supabase/supabase-js 2.109.0)
- **Server-Side Rendering:** Angular SSR (@angular/ssr 21.2.8) with Express 5.1.0
- **State Management:** RxJS 7.8.0
- **Testing:** 
  - Vitest 4.0.8 (unit tests)
  - jsdom 28.0.0 (DOM testing environment)
- **Development Tools:**
  - Angular CLI 21.2.8
  - Prettier 3.8.1 (code formatting)
  - Node.js 20.17.19+

### Backend
- **Status:** Placeholder structure only
- **Runtime:** Node.js based (Express configured for frontend SSR)

### Database & Services
- **Backend-as-a-Service:** Supabase
  - URL: `https://pssucganiyfzalolmeav.supabase.co`
  - Authentication: Anon key based (safe for frontend)

---

## Frontend Application Details

### Project Setup
- **Angular Version:** 21.2.0 with Server-Side Rendering
- **Build System:** @angular/build
- **Package Manager:** npm 10.8.2
- **Output Mode:** Server-side rendering enabled

### File Structure (src/)
```
src/
├── index.html              # Entry HTML template
├── main.ts                 # Client bootstrap
├── main.server.ts          # Server bootstrap
├── server.ts               # Express SSR configuration
├── styles.css              # Global styles
├── env.d.ts                # Environment type definitions
├── app/
│   ├── app.ts             # Root component
│   ├── app.html           # Root template (placeholder)
│   ├── app.css            # Root styles
│   ├── app.routes.ts      # Route definitions (currently empty)
│   ├── app.routes.server.ts # SSR routes
│   ├── app.config.ts      # Angular configuration
│   ├── app.config.server.ts # SSR configuration
│   ├── app.spec.ts        # Component tests
│   └── services/
│       └── supabase.service.ts # Supabase client service
└── environments/
    └── environment.ts      # Environment configuration
```

### Root Component (App)
- **Selector:** `app-root`
- **Features:**
  - Signal-based state (Angular 21 standalone)
  - Router integration with RouterOutlet
  - Responsive design with custom OKLch color system

### Environment Configuration
```typescript
// Reads from .env variables (NG_APP_* prefix)
{
  production: boolean,
  supabase: {
    url: string,
    anonKey: string
  }
}
```

### Supabase Service
- **Location:** `src/app/services/supabase.service.ts`
- **Initialization:** Validates required environment variables at construction
- **Exports:** `getClient()` method for Supabase API access
- **Error Handling:** Throws if `NG_APP_SUPABASE_URL` or `NG_APP_SUPABASE_ANON_KEY` missing

### Design System
Uses OKLch color space with predefined tokens:
- Primary Colors: Bright Blue, Electric Violet, French Violet
- Accent Colors: Vivid Pink, Hot Red, Orange Red
- Neutral: Gray 900, 700, 400
- Gradients: Red-to-Pink-to-Purple (vertical/horizontal)
- Typography: Inter font with antialiasing

### Current State
- **Routes:** Empty (no routes defined yet)
- **UI:** Placeholder template in place
- **Features:** Foundation laid for Supabase integration

---

## Environment Variables

### Required (.env file)
```
NG_APP_SUPABASE_URL=https://your-project-ref.supabase.co
NG_APP_SUPABASE_ANON_KEY=<your-anon-key>
NG_APP_ENV=development|production
```

**Current Configuration:**
- Supabase URL: https://pssucganiyfzalolmeav.supabase.co
- Environment: development

---

## Build & Run Configuration

### Available Scripts
- **`npm start`** - Start development server (`ng serve`)
- **`npm build`** - Production build (`ng build`)
- **`npm watch`** - Development watch mode
- **`npm test`** - Run tests via Vitest
- **`npm run serve:ssr:app`** - Run SSR server (`node dist/app/server/server.mjs`)

### Build Budgets
- Initial bundle: 500kB warning / 1MB error
- Component styles: 4kB warning / 8kB error

### Assets & Styles
- Public assets from `public/` folder
- Global styles: `src/styles.css`
- Tailwind CSS with custom design tokens

---

## Backend Status

### Current State
- Minimal placeholder structure
- No implementation details available
- Likely intended for backend API services

### Integration Points
- Frontend will consume backend APIs via Supabase or direct endpoints
- Express server used for frontend SSR only (not primary backend runtime)

---

## Development Workflow

### First-Time Setup
```bash
cd FRONTEND/app
npm install
# Configure .env with Supabase credentials
npm start
```

### Build for Production
```bash
npm run build
npm run serve:ssr:app  # Serve SSR version
```

### Testing
```bash
npm test
```

---

## Key Integration Points

1. **Supabase Service**: Central point for all backend communication
2. **Environment Loading**: Uses Vite's `import.meta.env` for environment variables
3. **Routing**: Currently empty but Router is imported and ready
4. **SSR Compatibility**: All components designed for server-side rendering

---

## Dependencies at a Glance

### Core Angular (21.2.0)
- @angular/core, @angular/common, @angular/forms
- @angular/router, @angular/platform-browser, @angular/platform-server

### Database & Auth
- @supabase/supabase-js (v2.109.0) - Real-time database, auth, storage

### Styling
- tailwindcss (v4.1.12)
- @tailwindcss/postcss
- postcss

### Runtime
- express (v5.1.0)
- rxjs (v7.8.0)

### Development
- typescript (v5.9.2)
- vitest (v4.0.8)
- prettier (v3.8.1)

---

## Known Constraints & Notes

1. **Supabase Anon Key Exposure**: The .env contains an anon key visible in git - should be rotated in production
2. **No Routes Defined**: Application structure is ready but no actual routes are configured
3. **Empty README Files**: BACKEND and FRONTEND documentation is empty
4. **Placeholder UI**: App template is still in place and needs replacement with actual components
5. **SSR Configured**: Application is set up for server-side rendering but may need additional configuration for deployment

---

## Next Steps for Development

- [ ] Define application routes in `app.routes.ts`
- [ ] Create feature components and pages
- [ ] Implement Supabase data access layer
- [ ] Configure authentication flow
- [ ] Build backend API or define Supabase policies
- [ ] Add tests for components and services
- [ ] Replace placeholder UI with actual banking interface
- [ ] Configure production environment variables
- [ ] Set up CI/CD pipeline

---

*Last Updated: 2026-09-04*  
*Project Location: c:\bankingtech\BankingTechnology*
