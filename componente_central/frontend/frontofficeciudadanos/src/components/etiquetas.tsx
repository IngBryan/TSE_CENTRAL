// Lista de valores como etiquetas redondeadas (p. ej. los rubros de una empresa), o un texto si no hay ninguno
export function Etiquetas({ valores, vacio }: { valores: string[] | null | undefined; vacio: string }) {
  if (!valores?.length) return <span className="text-muted-foreground">{vacio}</span>;
  return (
    <ul className="flex flex-wrap gap-2">
      {valores.map((v) => (
        <li key={v} className="rounded-full bg-muted px-3 py-0.5 text-sm text-foreground">
          {v}
        </li>
      ))}
    </ul>
  );
}
