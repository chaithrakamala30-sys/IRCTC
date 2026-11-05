import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  output: "export",
  poweredByHeader: false,
  transpilePackages: ["@railconnect/ui"],
};

export default nextConfig;
