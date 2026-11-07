// Lista de valores como etiquetas redondeadas (p. ej. los rubros de una empresa), o un texto si no hay ninguno
export function Etiquetas({ valores, vacio }: { valores: string[] | null | undefined; vacio: string }) {
  if (!valores?.length) return <span className="text-zinc-600 dark:text-zinc-400">{vacio}</span>;
  return (
    <ul className="flex flex-wrap gap-2">
      {valores.map((v) => (
        <li key={v} className="rounded-full bg-zinc-100 px-3 py-0.5 text-sm text-zinc-800 dark:bg-zinc-800 dark:text-zinc-200">
          {v}
        </li>
      ))}
    </ul>
  );
}
