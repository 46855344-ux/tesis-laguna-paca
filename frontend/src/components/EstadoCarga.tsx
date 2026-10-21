import { Link } from 'react-router-dom';
import { Icon } from './Icon';

export function Cargando({ texto = 'Cargando…' }: { texto?: string }) {
  return (
    <div className="estado-vacio" role="status" aria-live="polite">
      <span className="estado-vacio__icono">
        <Icon name="refrescar" />
      </span>
      <p>{texto}</p>
    </div>
  );
}

export function ErrorPagina({
  mensaje,
  alReintentar
}: {
  mensaje: string;
  alReintentar?: () => void;
}) {
  return (
    <section className="tarjeta" role="alert">
      <div className="estado-vacio">
        <span className="estado-vacio__icono">
          <Icon name="alertaTriangulo" />
        </span>
        <h3>No se pudieron cargar los datos</h3>
        <p>{mensaje}</p>
        <div className="estado-vacio__acciones">
          {alReintentar ? (
            <button type="button" className="boton boton--primario" onClick={alReintentar}>
              <Icon name="refrescar" />
              <span>Reintentar</span>
            </button>
          ) : null}
          <Link className="boton boton--secundario" to="/panel">
            <span>Volver al panel</span>
          </Link>
        </div>
      </div>
    </section>
  );
}
