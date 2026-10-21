import { useEffect, useMemo, useState, type FormEvent } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { api } from '../api/client';
import type { FiltrosHistorial, HistorialVista } from '../api/types';
import { AppLayout } from '../components/AppLayout';
import { Cargando, ErrorPagina } from '../components/EstadoCarga';
import { EmptyState } from '../components/EmptyState';
import { Icon } from '../components/Icon';
import { Pagination } from '../components/Pagination';
import { StatusBadge } from '../components/StatusBadge';
import { useAuth } from '../hooks/useAuth';

interface FormularioHistorial {
  estacion: string;
  desde: string;
  hasta: string;
  soloAlertas: boolean;
  motivo: string;
}

function filtrosDesde(params: URLSearchParams): FiltrosHistorial {
  const pagina = Number(params.get('page') ?? '0');
  return {
    estacion: params.get('estacion') ?? '',
    desde: params.get('desde') ?? '',
    hasta: params.get('hasta') ?? '',
    soloAlertas: params.get('soloAlertas') === 'true',
    motivo: params.get('motivo') ?? '',
    pagina: Number.isInteger(pagina) && pagina >= 0 ? pagina : 0
  };
}

function formularioDesde(filtros: FiltrosHistorial): FormularioHistorial {
  return {
    estacion: filtros.estacion,
    desde: filtros.desde,
    hasta: filtros.hasta,
    soloAlertas: filtros.soloAlertas,
    motivo: filtros.motivo
  };
}

export function HistoryPage() {
  const { sesion } = useAuth();
  const [params, setParams] = useSearchParams();
  const filtros = useMemo(() => filtrosDesde(params), [params]);
  const [formulario, setFormulario] = useState<FormularioHistorial>(() =>
    formularioDesde(filtros)
  );
  const [vista, setVista] = useState<HistorialVista | null>(null);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    document.title = 'Historial de muestras · Laguna Paca';
  }, []);

  useEffect(() => {
    setFormulario(formularioDesde(filtros));
  }, [filtros]);

  useEffect(() => {
    let activo = true;
    setCargando(true);
    setError(null);
    api
      .historial({
        estacion: filtros.estacion,
        desde: filtros.desde,
        hasta: filtros.hasta,
        soloAlertas: filtros.soloAlertas,
        motivo: filtros.motivo,
        page: filtros.pagina
      })
      .then((datos) => {
        if (activo) {
          setVista(datos);
        }
      })
      .catch((fallo) => {
        if (activo) {
          setError(
            fallo instanceof Error ? fallo.message : 'No se pudo cargar el historial.'
          );
        }
      })
      .finally(() => {
        if (activo) {
          setCargando(false);
        }
      });
    return () => {
      activo = false;
    };
  }, [
    filtros.estacion,
    filtros.desde,
    filtros.hasta,
    filtros.soloAlertas,
    filtros.motivo,
    filtros.pagina
  ]);

  const aplicar = (evento: FormEvent) => {
    evento.preventDefault();
    const nuevos = new URLSearchParams();
    if (formulario.estacion) {
      nuevos.set('estacion', formulario.estacion);
    }
    if (formulario.desde) {
      nuevos.set('desde', formulario.desde);
    }
    if (formulario.hasta) {
      nuevos.set('hasta', formulario.hasta);
    }
    if (formulario.soloAlertas) {
      nuevos.set('soloAlertas', 'true');
    }
    if (formulario.motivo) {
      nuevos.set('motivo', formulario.motivo);
    }
    setParams(nuevos);
  };

  const enlacePagina = (pagina: number) => {
    const nuevos = new URLSearchParams(params);
    if (pagina <= 1) {
      nuevos.delete('page');
    } else {
      nuevos.set('page', String(pagina - 1));
    }
    const texto = nuevos.toString();
    return `/panel/historial${texto ? `?${texto}` : ''}`;
  };

  const hayFiltros =
    filtros.estacion !== '' ||
    filtros.desde !== '' ||
    filtros.hasta !== '' ||
    filtros.soloAlertas ||
    filtros.motivo !== '';

  return (
    <AppLayout titulo="Historial de muestras" seccion="historial">
      <div className="encabezado-pagina">
        <div>
          <h2>Historial de muestras</h2>
          <p>
            Todas las lecturas que enviaron los nodos, de la más reciente a la más
            antigua. Los filtros quedan en la dirección: puedes guardar o compartir el
            enlace de una búsqueda.
          </p>
        </div>
        <div className="encabezado-pagina__acciones">
          <a
            className="boton boton--secundario"
            href="/swagger-ui.html"
            target="_blank"
            rel="noopener noreferrer"
          >
            <Icon name="escudo" />
            <span>Documentación de la API</span>
          </a>
        </div>
      </div>

      {vista?.aviso ? (
        <p className="aviso aviso--alerta" role="status">
          <Icon name="alertaTriangulo" />
          <span>{vista.aviso}</span>
        </p>
      ) : null}

      <form
        className="tarjeta filtros"
        role="search"
        aria-labelledby="titulo-filtros"
        onSubmit={aplicar}
      >
        <h3 id="titulo-filtros" className="titulo-seccion">
          Filtros
        </h3>

        <div className="filtros__rejilla">
          <div className="campo">
            <label className="campo__etiqueta" htmlFor="estacion">
              Estación
            </label>
            <select
              className="campo__control"
              id="estacion"
              name="estacion"
              value={formulario.estacion}
              onChange={(evento) =>
                setFormulario((actual) => ({ ...actual, estacion: evento.target.value }))
              }
            >
              <option value="">Todas</option>
              {(sesion?.estaciones ?? ['N1', 'N2', 'N3', 'N4']).map((id) => (
                <option key={id} value={id}>
                  {id}
                </option>
              ))}
            </select>
          </div>

          <div className="campo">
            <label className="campo__etiqueta" htmlFor="desde">
              Desde
            </label>
            <input
              className="campo__control"
              id="desde"
              name="desde"
              type="date"
              value={formulario.desde}
              onChange={(evento) =>
                setFormulario((actual) => ({ ...actual, desde: evento.target.value }))
              }
            />
          </div>

          <div className="campo">
            <label className="campo__etiqueta" htmlFor="hasta">
              Hasta
            </label>
            <input
              className="campo__control"
              id="hasta"
              name="hasta"
              type="date"
              value={formulario.hasta}
              onChange={(evento) =>
                setFormulario((actual) => ({ ...actual, hasta: evento.target.value }))
              }
            />
          </div>

          <div className="campo">
            <label className="campo__etiqueta" htmlFor="motivo">
              Buscar en el motivo
            </label>
            <input
              className="campo__control"
              id="motivo"
              name="motivo"
              type="search"
              placeholder="pH, límite, …"
              value={formulario.motivo}
              onChange={(evento) =>
                setFormulario((actual) => ({ ...actual, motivo: evento.target.value }))
              }
            />
          </div>

          <div className="campo campo--casilla">
            <input
              type="checkbox"
              id="soloAlertas"
              name="soloAlertas"
              checked={formulario.soloAlertas}
              onChange={(evento) =>
                setFormulario((actual) => ({
                  ...actual,
                  soloAlertas: evento.target.checked
                }))
              }
            />
            <label className="campo__etiqueta" htmlFor="soloAlertas">
              Solo alertas ECA-Agua
            </label>
          </div>
        </div>

        <div className="filtros__acciones">
          <button type="submit" className="boton boton--primario">
            <Icon name="filtro" />
            <span>Filtrar</span>
          </button>
          <Link className="boton boton--secundario" to="/panel/historial">
            <span>Quitar los filtros</span>
          </Link>
        </div>
      </form>

      {error ? <ErrorPagina mensaje={error} alReintentar={() => window.location.reload()} /> : null}

      {!error && cargando && !vista ? <Cargando texto="Cargando el historial…" /> : null}

      {!error && vista && !vista.hayResultados ? (
        <EmptyState
          titulo="Sin resultados"
          descripcion="Ninguna muestra coincide con estos filtros. Prueba con otro rango de fechas o quita la búsqueda."
        >
          <Link className="boton boton--secundario" to="/panel/historial">
            <span>Quitar los filtros</span>
          </Link>
        </EmptyState>
      ) : null}

      {!error && vista?.hayResultados ? (
        <section aria-labelledby="titulo-resultados" className="seccion-separada">
          <div className="tabla__cabecera">
            <h3 id="titulo-resultados" className="titulo-seccion">
              Resultados
            </h3>
            <p className="tabla__resumen">
              Mostrando{' '}
              <strong>
                {vista.desdeFila}–{vista.hastaFila}
              </strong>{' '}
              de <strong>{vista.total} muestras</strong>
            </p>
          </div>

          <div className="tarjeta tarjeta--tabla">
            <div className="tabla__contenedor">
              <table className="tabla">
                <caption className="visualmente-oculto">
                  Muestras de calidad de agua, de la más reciente a la más antigua.
                </caption>
                <thead>
                  <tr>
                    <th scope="col">Fecha y hora</th>
                    <th scope="col">Estación</th>
                    <th scope="col" className="tabla__numero">
                      pH
                    </th>
                    <th scope="col" className="tabla__numero">
                      Turbidez (NTU)
                    </th>
                    <th scope="col" className="tabla__numero">
                      Temp. (°C)
                    </th>
                    <th scope="col" className="tabla__numero">
                      TDS (ppm)
                    </th>
                    <th scope="col">Estado</th>
                  </tr>
                </thead>
                <tbody>
                  {vista.filas.map((fila) => (
                    <tr key={fila.id} data-muestra={fila.id} data-estacion={fila.estacion}>
                      <td>
                        <span className="tabla__fecha">{fila.fecha}</span>
                        <span className="tabla__hora">{fila.hora}</span>
                      </td>
                      <td>
                        <span className="tabla__estacion">{fila.estacion}</span>
                      </td>
                      <td className="tabla__numero">{fila.ph}</td>
                      <td className="tabla__numero">{fila.turbidez}</td>
                      <td className="tabla__numero">{fila.temperatura}</td>
                      <td className="tabla__numero">{fila.tds}</td>
                      <td>
                        <StatusBadge estado={fila.estado} />
                        {fila.motivos.length > 0 ? (
                          <ul className="motivos motivos--tabla">
                            {fila.motivos.map((motivo) => (
                              <li key={motivo}>{motivo}</li>
                            ))}
                          </ul>
                        ) : null}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>

          <Pagination
            pagina={vista.pagina}
            totalPaginas={vista.totalPaginas}
            paginas={vista.paginas}
            enlace={enlacePagina}
            etiqueta="Paginación del historial"
          />
        </section>
      ) : null}

      {hayFiltros ? (
        <p className="tarjeta__nota seccion-separada">
          <Link to="/panel/historial">Quitar todos los filtros</Link>
        </p>
      ) : null}
    </AppLayout>
  );
}
