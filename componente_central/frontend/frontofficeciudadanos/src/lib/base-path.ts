// basePath de la app, el mismo de next.config.ts (que lo inyecta al construir). <Link> y redirect() lo
// agregan solos; hace falta a mano en los <a href> y <form action> que apuntan a route handlers (login,
// logout, ingreso de testing). Vacio en el docker-compose local, /ciudadanos en Kubernetes.
export const BASE_PATH = process.env.NEXT_PUBLIC_BASE_PATH ?? "";
