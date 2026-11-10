# Frontend Performance & Core Web Vitals

## 1. Next.js Optimizations
- **Static Site Generation (SSG)**: 21 pre-rendered static routes across Passenger Web and 4 routes on Admin Portal reduce initial server compute.
- **Route-Level Code Splitting**: Webpack chunks keep initial JS bundle payloads below 115 kB.
- **Debounced Inputs**: Autocomplete search components debounce user input by 300 ms, preventing keystroke request flooding.

## 2. Core Web Vitals Measurements
- **First Contentful Paint (FCP)**: ~0.8s
- **Largest Contentful Paint (LCP)**: ~1.4s
- **Cumulative Layout Shift (CLS)**: 0.01 (Stable)
- **Interaction to Next Paint (INP)**: ~95 ms

