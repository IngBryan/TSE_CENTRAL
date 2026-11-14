// Clases de los botones comunes (las usan tambien los componentes de cliente). Mismo aspecto que el
// Button del backoffice: esquinas rounded-lg, azul primario con sombra suave y secundario con borde.
export const linkBoton =
  "flex h-10 items-center justify-center gap-2 rounded-lg px-4 text-sm font-medium whitespace-nowrap transition-colors disabled:pointer-events-none disabled:opacity-50 [&_svg]:size-4";
export const botonGubUy = `${linkBoton} bg-primary text-primary-foreground shadow-sm shadow-primary/30 hover:bg-primary/90`;
export const botonSecundario = `${linkBoton} border bg-card text-foreground shadow-xs hover:bg-accent hover:text-accent-foreground`;
