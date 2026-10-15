// SOLO DESARROLLO: el client de testing de ID Uruguay tiene registradas como redirect URI solo
// http://localhost y http://localhost:8080, que ya usan frontofficeciudadanos y WildFly. Para probar
// el frontoffice de funcionarios en local se puede abrir sesion con una cedula, sin gub.uy
// (POST /auth/desarrollo). Exige `next dev` (NODE_ENV=development) y LOGIN_DESARROLLO=true: en el
// build de produccion (Docker) NODE_ENV es siempre production y queda deshabilitado.
// El backend igual controla que la cedula sea de un funcionario registrado.
export function loginDesarrolloHabilitado(): boolean {
  return process.env.NODE_ENV === "development" && process.env.LOGIN_DESARROLLO === "true";
}
