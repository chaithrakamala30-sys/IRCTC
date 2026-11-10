import type { NextConfig } from "next";

const isVercel = process.env.VERCEL === "1";
const defaultApiOrigin = "https://irctc-wb68.onrender.com";

function getApiOrigin(): string {
  const configuredOrigin =
    process.env.RAILCONNECT_API_ORIGIN || defaultApiOrigin;

  let url: URL;
  try {
    url = new URL(configuredOrigin);
  } catch {
    throw new Error("RAILCONNECT_API_ORIGIN must be a valid HTTPS origin.");
  }

  if (
    url.protocol !== "https:" ||
    url.pathname !== "/" ||
    url.search ||
    url.hash
  ) {
    throw new Error("RAILCONNECT_API_ORIGIN must be a valid HTTPS origin.");
  }

  return url.origin;
}

const nextConfig: NextConfig = {
  ...(!isVercel ? { output: "export" as const } : {}),
  poweredByHeader: false,
  transpilePackages: ["@railconnect/api", "@railconnect/ui"],
  ...(isVercel
    ? {
        async rewrites() {
          return [
            {
              source: "/api/:path*",
              destination: `${getApiOrigin()}/api/:path*`,
            },
          ];
        },
      }
    : {}),
};

export default nextConfig;
