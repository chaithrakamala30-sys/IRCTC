import type { Config } from "tailwindcss";

const config = {
  content: [
    "./app/**/*.{ts,tsx}",
    "./components/**/*.{ts,tsx}",
    "../../packages/ui/src/**/*.{ts,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        rail: {
          ink: "#122443",
          blue: "#1056b5",
          orange: "#d97724",
          canvas: "#f3f6fb",
        },
      },
    },
  },
  plugins: [],
} satisfies Config;

export default config;
