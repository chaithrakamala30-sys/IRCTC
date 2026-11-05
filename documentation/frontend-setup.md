# Frontend setup

## Workspaces and apps

- `apps/passenger-web`: Next.js passenger portal.
- `apps/admin-portal`: separate administration foundation.
- `apps/operations-portal`: separate railway operations foundation.
- `packages/ui`: shared brand, buttons, cards, input, dialog, loading indicator, navigation, and design tokens.

All apps use strict TypeScript, React 19, Tailwind CSS, static export, responsive layout, local fonts/system stacks, and no externally hosted assets.

## Passenger mobile and laptop webpages

- `/mobile` provides a compact, touch-oriented single-column webpage with a bottom navigation bar.
- `/laptop` provides a wider webpage with full navigation and a desktop content hierarchy.
- `/` uses a client viewport breakpoint (760 px) to route users to the appropriate webpage. Visible manual links remain available if JavaScript is unavailable or the user wants the other view.
- Users can open either route from any device. There is no user-agent sniffing and the chosen route is not overridden once entered.
- Shared components, design tokens, API contract, and security behavior prevent the two views from drifting.

Both routes state that search, availability, reservations, and payment are not enabled. No synthetic train results or booking controls are shown.

## Run and build

```powershell
npm ci
npm run dev:passenger
npm run dev:admin
npm run dev:operations
npm run lint
npm run typecheck
npm test
npm run build
```

Each `dev:*` command starts its Next.js app on ports 3001–3003; run in separate terminals. Static export output is created in each app's `out/` directory by the build and served by the Docker static server.

## Shared UI and accessibility

Use the shared `@railconnect/ui` package for navigation, buttons, form labels/errors, cards, dialog, and loading state. Extend design tokens in shared styles instead of duplicating portal colors. Focus indicators, reduced-motion handling, live loading status, semantic landmarks, and accessible names are included as foundations; WCAG 2.2 AA remains a target requiring manual verification.

## API utility and environment

`apps/passenger-web/lib/api.ts` validates the public API base URL and response envelope, and throws an explicit `ApiRequestError` for HTTP or contract failures. Configure `NEXT_PUBLIC_API_BASE_URL` at build time for static output. Do not place secrets in `NEXT_PUBLIC_*` variables. The foundation pages do not call the API yet.

## Error handling

Each app includes loading, not-found, and route-level error states. Errors show a safe message; browser logging records only error name/digest, not request payloads or personal data.
