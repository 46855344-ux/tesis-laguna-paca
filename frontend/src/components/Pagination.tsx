import { Fragment } from 'react';
import { Link } from 'react-router-dom';

interface PaginationProps {
  pagina: number;
  totalPaginas: number;
  paginas: number[];
  enlace: (pagina: number) => string;
  etiqueta: string;
}

export function Pagination({ pagina, totalPaginas, paginas, enlace, etiqueta }: PaginationProps) {
  if (totalPaginas <= 1) {
    return null;
  }

  return (
    <nav className="paginacion" aria-label={etiqueta}>
      {pagina > 1 ? (
        <Link
          className="paginacion__numero"
          to={enlace(pagina - 1)}
          aria-label={`Ir a la página ${pagina - 1}`}
        >
          <span aria-hidden="true">‹</span>
          <span className="visualmente-oculto">Anterior</span>
        </Link>
      ) : (
        <span className="paginacion__numero es-inactivo" aria-hidden="true">
          ‹
        </span>
      )}

      {paginas.map((numero, indice) => {
        const anterior = paginas[indice - 1];
        return (
          <Fragment key={numero}>
            {indice > 0 && numero - anterior > 1 ? (
              <span className="paginacion__puntos" aria-hidden="true">
                …
              </span>
            ) : null}
            {numero === pagina ? (
              <span className="paginacion__numero es-activo" aria-current="page">
                {numero}
              </span>
            ) : (
              <Link
                className="paginacion__numero"
                to={enlace(numero)}
                aria-label={`Ir a la página ${numero}`}
              >
                {numero}
              </Link>
            )}
          </Fragment>
        );
      })}

      {pagina < totalPaginas ? (
        <Link
          className="paginacion__numero"
          to={enlace(pagina + 1)}
          aria-label={`Ir a la página ${pagina + 1}`}
        >
          <span aria-hidden="true">›</span>
          <span className="visualmente-oculto">Siguiente</span>
        </Link>
      ) : (
        <span className="paginacion__numero es-inactivo" aria-hidden="true">
          ›
        </span>
      )}
    </nav>
  );
}
