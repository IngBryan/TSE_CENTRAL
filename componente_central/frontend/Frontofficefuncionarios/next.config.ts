import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  /* config options here */
  reactCompiler: true,
  output: "standalone",
  basePath: "/funcionarios",
  // Login con gub.uy en local: las URIs registradas para desarrollo son http://localhost y
  // http://localhost/logout, fuera del basePath. Estas redirecciones llevan la vuelta de ID Uruguay a la
  // app (el query ?code=...&state=... se conserva). En Kubernetes el ingress solo manda /funcionarios a
  // este front, asi que ahi no aplican.
  async redirects() {
    return [
      {
        source: "/",
        has: [{ type: "query", key: "state" }],
        destination: "/funcionarios",
        basePath: false,
        permanent: false,
      },
      { source: "/logout", destination: "/funcionarios/logout", basePath: false, permanent: false },
    ];
  },
};

export default nextConfig;
