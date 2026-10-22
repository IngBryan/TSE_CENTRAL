import type { NextConfig } from "next";

// basePath: /ciudadanos en Kubernetes (ingress por prefijo). En el docker-compose local va vacio
// (NEXT_BASE_PATH=""): la redirect URI de gub.uy registrada es http://localhost y los links a
// /auth/login no llevan el prefijo. Se fija al construir la imagen (build arg del Dockerfile).
const basePath = process.env.NEXT_BASE_PATH ?? "/ciudadanos";

const nextConfig: NextConfig = {
  /* config options here */
  reactCompiler: true,
  output: "standalone",
  basePath,
  // El mismo valor para los <a href> y <form action> a route handlers (src/lib/base-path.ts)
  env: { NEXT_PUBLIC_BASE_PATH: basePath },
};

export default nextConfig;
