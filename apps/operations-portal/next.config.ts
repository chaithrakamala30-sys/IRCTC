import type { NextConfig } from "next";

const isVercel = process.env.VERCEL === "1";

function getApiOrigin(): string {
  const configuredOrigin = process.env.RAILCONNECT_API_ORIGIN;
  if (!configuredOrigin) {
    throw new Error("RAILCONNECT_API_ORIGIN must be configured for Vercel deployments.");
  }

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
  transpilePackages: ["@railconnect/ui"],
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
