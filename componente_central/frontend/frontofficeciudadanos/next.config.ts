import type { NextConfig } from "next";

// basePath: /ciudadanos en Kubernetes (ingress por prefijo). En el docker-compose local va vacio
// (NEXT_BASE_PATH=""): la redirect URI de gub.uy registrada es http://localhost y los links a
// /auth/login no llevan el prefijo. Se fija al construir la imagen (build arg del Dockerfile).
const nextConfig: NextConfig = {
  /* config options here */
  reactCompiler: true,
  output: "standalone",
  basePath: process.env.NEXT_BASE_PATH ?? "/ciudadanos",
};

export default nextConfig;
