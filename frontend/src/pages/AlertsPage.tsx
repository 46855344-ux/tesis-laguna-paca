import { useCallback, useEffect, useMemo, useState, type FormEvent } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { api } from '../api/client';
import type { AlertasVista, FiltrosAlertas } from '../api/types';
import { AppLayout } from '../components/AppLayout';
import { Cargando, ErrorPagina } from '../components/EstadoCarga';
import { EmptyState } from '../components/EmptyState';
import { Icon } from '../components/Icon';
import { Pagination } from '../components/Pagination';
import { useAuth } from '../hooks/useAuth';
import { useToast } from '../hooks/useToast';

interface FormularioAlertas {
  estacion: string;
  estado: FiltrosAlertas['estado'];
  desde: string;
  hasta: string;
}

function filtrosDesde(params: URLSearchParams): FiltrosAlertas {
  const pagina = Number(params.get('page') ?? '0');
  const estado = params.get('estado');
  return {
    estacion: params.get('estacion') ?? '',
    estado:
      estado === 'atendidas' || estado === 'todas' || estado === 'pendientes'
        ? estado
        : 'pendientes',
    desde: params.get('desde') ?? '',
    hasta: params.get('hasta') ?? '',
    pagina: Number.isInteger(pagina) && pagina >= 0 ? pagina : 0
  };
}

function formularioDesde(filtros: FiltrosAlertas): FormularioAlertas {
  return {
    estacion: filtros.estacion,
    estado: filtros.estado,
    desde: filtros.desde,
    hasta: filtros.hasta
  };
}

export function AlertsPage() {
  const { sesion } = useAuth();
  const { mostrarToast } = useToast();
  const [params, setParams] = useSearchParams();
  const filtros = useMemo(() => filtrosDesde(params), [params]);
  const [formulario, setFormulario] = useState<FormularioAlertas>(() =>
    formularioDesde(filtros)
  );
  const [vista, setVista] = useState<AlertasVista | null>(null);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [notas, setNotas] = useState<Record<number, string>>({});
  const [procesando, setProcesando] = useState<Record<number, boolean>>({});

  useEffect(() => {
    document.title = 'Gestión de alertas · Laguna Paca';
  }, []);

  useEffect(() => {
    setFormulario(formularioDesde(filtros));
  }, [filtros]);

  const cargar = useCallback(async () => {
    setCargando(true);
    setError(null);
    try {
      const datos = await api.alertas({
        estacion: filtros.estacion,
        estado: filtros.estado,
        desde: filtros.desde,
        hasta: filtros.hasta,
        page: filtros.pagina
      });
      setVista(datos);
    } catch (fallo) {
      setError(fallo instanceof Error ? fallo.message : 'No se pudieron cargar las alertas.');
    } finally {
      setCargando(false);
    }
  }, [filtros.estacion, filtros.estado, filtros.desde, filtros.hasta, filtros.pagina]);

  useEffect(() => {
    void cargar();
  }, [cargar]);

  const aplicar = (evento: FormEvent) => {
    evento.preventDefault();
    const nuevos = new URLSearchParams();
    if (formulario.estacion) {
      nuevos.set('estacion', formulario.estacion);
    }
    if (formulario.estado !== 'pendientes') {
      nuevos.set('estado', formulario.estado);
    }
    if (formulario.desde) {
      nuevos.set('desde', formulario.desde);
    }
    if (formulario.hasta) {
      nuevos.set('hasta', formulario.hasta);
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
    return `/panel/alertas${texto ? `?${texto}` : ''}`;
  };

  const atender = async (id: number) => {
    setProcesando((actual) => ({ ...actual, [id]: true }));
    try {
      const respuesta = await api.atenderAlerta(id, notas[id] ?? '');
      mostrarToast(respuesta.mensaje, 'exito');
      setNotas((actual) => ({ ...actual, [id]: '' }));
      await cargar();
    } catch (fallo) {
      mostrarToast(
        fallo instanceof Error ? fallo.message : 'No se pudo atender la alerta.',
        'error'
      );
    } finally {
      setProcesando((actual) => ({ ...actual, [id]: false }));
    }
  };

  const reabrir = async (id: number) => {
    setProcesando((actual) => ({ ...actual, [id]: true }));
    try {
      const respuesta = await api.reabrirAlerta(id);
      mostrarToast(respuesta.mensaje, 'info');
      await cargar();
    } catch (fallo) {
      mostrarToast(
        fallo instanceof Error ? fallo.message : 'No se pudo reabrir la alerta.',
        'error'
      );
    } finally {
      setProcesando((actual) => ({ ...actual, [id]: false }));
    }
  };

  const tituloVacio = vista?.filtroPendientes
    ? 'No hay alertas pendientes'
    : 'Sin alertas con estos filtros';
  const textoVacio = vista?.filtroPendientes
    ? 'Todas las alertas registradas ya fueron atendidas. El historial conserva las mediciones completas.'
    : 'Prueba con otro rango de fechas, otra estación o cambia el estado.';

  return (
    <AppLayout titulo="Gestión de alertas" seccion="alertas">
      <div className="encabezado-pagina">
        <div>
          <h2>Gestión de alertas</h2>
          <p>
            Alertas que decidió el backend con la regla ECA-Agua. Atender una alerta no
            cambia el veredicto: deja constancia de que alguien ya se ocupó de esa
            muestra.
          </p>
        </div>
        <div className="encabezado-pagina__acciones">
          <Link className="boton boton--secundario" to="/panel/historial">
            <Icon name="historial" />
            <span>Ver historial</span>
          </Link>
        </div>
      </div>

      {vista?.aviso ? (
        <p className="aviso aviso--alerta" role="status">
          <Icon name="alertaTriangulo" />
          <span>{vista.aviso}</span>
        </p>
      ) : null}

      <section className="rejilla-kpi rejilla-kpi--tres" aria-label="Resumen de alertas">
        <article className="tarjeta kpi">
          <span className="kpi__etiqueta">Pendientes</span>
          <strong
            className={`kpi__valor${
              (vista?.pendientes ?? 0) > 0 ? ' kpi__valor--alerta' : ''
            }`}
            id="kpi-pendientes"
          >
            {vista?.pendientes ?? 0}
          </strong>
          <span className="kpi__nota">Sin atender, sin límite de antigüedad</span>
        </article>
        <article className="tarjeta kpi">
          <span className="kpi__etiqueta">Atendidas (24 h)</span>
          <strong className="kpi__valor" id="kpi-atendidas">
            {vista?.atendidas24h ?? 0}
          </strong>
          <span className="kpi__nota">Según la hora en que se atendieron</span>
        </article>
        <article className="tarjeta kpi">
          <span className="kpi__etiqueta">Alertas (24 h)</span>
          <strong className="kpi__valor" id="kpi-alertas">
            {vista?.alertas24h ?? 0}
          </strong>
          <span className="kpi__nota">Según la hora en que se midieron</span>
        </article>
      </section>

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
            <label className="campo__etiqueta" htmlFor="estado">
              Estado
            </label>
            <select
              className="campo__control"
              id="estado"
              name="estado"
              value={formulario.estado}
              onChange={(evento) =>
                setFormulario((actual) => ({
                  ...actual,
                  estado: evento.target.value as FormularioAlertas['estado']
                }))
              }
            >
              <option value="pendientes">Pendientes</option>
              <option value="atendidas">Atendidas</option>
              <option value="todas">Todas</option>
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
        </div>

        <div className="filtros__acciones">
          <button type="submit" className="boton boton--primario">
            <Icon name="filtro" />
            <span>Filtrar</span>
          </button>
          <Link className="boton boton--secundario" to="/panel/alertas">
            <span>Quitar los filtros</span>
          </Link>
        </div>
      </form>

      {error ? <ErrorPagina mensaje={error} alReintentar={() => void cargar()} /> : null}

      {!error && cargando && !vista ? <Cargando texto="Cargando las alertas…" /> : null}

      {!error && vista && !vista.hayResultados ? (
        <EmptyState titulo={tituloVacio} descripcion={textoVacio}>
          <Link className="boton boton--secundario" to="/panel/alertas?estado=todas">
            <span>Ver todas las alertas</span>
          </Link>
          <Link className="boton boton--secundario" to="/panel/historial">
            <span>Ir al historial</span>
          </Link>
        </EmptyState>
      ) : null}

      {!error && vista?.hayResultados ? (
        <section aria-labelledby="titulo-resultados" className="seccion-separada">
          <div className="tabla__cabecera">
            <h3 id="titulo-resultados" className="titulo-seccion">
              Alertas registradas
            </h3>
            <p className="tabla__resumen">
              Mostrando{' '}
              <strong>
                {vista.desdeFila}–{vista.hastaFila}
              </strong>{' '}
              de <strong>{vista.total} alertas</strong>
            </p>
          </div>

          <div className="lista-alertas">
            {vista.filas.map((fila) => (
              <article
                className={`tarjeta alerta ${
                  fila.atendida ? 'alerta--atendida' : 'alerta--pendiente'
                }`}
                key={fila.id}
                data-alerta={fila.id}
                data-estacion={fila.estacion}
              >
                <div className="alerta__cabecera">
                  <div className="alerta__identidad">
                    <span
                      className={`etiqueta ${
                        fila.atendida ? 'etiqueta--exito' : 'etiqueta--peligro'
                      }`}
                    >
                      <span className="etiqueta__punto" />
                      <span>{fila.atendida ? 'Atendida' : 'Alerta ECA-Agua'}</span>
                    </span>
                    <h4 className="alerta__titulo">
                      <span>{fila.estacion}</span>
                      <span className="alerta__hace">{fila.hace}</span>
                    </h4>
                  </div>
                  <p className="alerta__fecha">
                    <span>{fila.fecha}</span>
                    <span>{fila.hora}</span>
                  </p>
                </div>

                {fila.motivos.length > 0 ? (
                  <ul className="motivos">
                    {fila.motivos.map((motivo) => (
                      <li key={motivo}>{motivo}</li>
                    ))}
                  </ul>
                ) : null}

                <dl className="datos-alerta">
                  <div>
                    <dt>pH</dt>
                    <dd>{fila.ph}</dd>
                  </div>
                  <div>
                    <dt>Turbidez</dt>
                    <dd>
                      <span>{fila.turbidez}</span>{' '}
                      <span className="mini-datos__unidad">NTU</span>
                    </dd>
                  </div>
                  <div>
                    <dt>Temperatura</dt>
                    <dd>
                      <span>{fila.temperatura}</span>{' '}
                      <span className="mini-datos__unidad">°C</span>
                    </dd>
                  </div>
                  <div>
                    <dt>Sólidos disueltos</dt>
                    <dd>
                      <span>{fila.tds}</span>{' '}
                      <span className="mini-datos__unidad">ppm</span>
                    </dd>
                  </div>
                </dl>

                <div className="alerta__pie">
                  {!fila.atendida ? (
                    <form
                      className="formulario-atender"
                      onSubmit={(evento) => {
                        evento.preventDefault();
                        void atender(fila.id);
                      }}
                    >
                      <div className="campo campo--nota">
                        <label className="campo__etiqueta" htmlFor={`nota-${fila.id}`}>
                          Nota (opcional)
                        </label>
                        <input
                          className="campo__control"
                          type="text"
                          name="nota"
                          maxLength={500}
                          id={`nota-${fila.id}`}
                          placeholder="Qué se hizo o con quién se coordinó"
                          value={notas[fila.id] ?? ''}
                          onChange={(evento) =>
                            setNotas((actual) => ({
                              ...actual,
                              [fila.id]: evento.target.value
                            }))
                          }
                        />
                      </div>
                      <button
                        type="submit"
                        className="boton boton--primario"
                        disabled={procesando[fila.id]}
                      >
                        <Icon name="check" />
                        <span>
                          {procesando[fila.id] ? 'Guardando…' : 'Marcar como atendida'}
                        </span>
                      </button>
                    </form>
                  ) : (
                    <div className="alerta__atencion">
                      <p>
                        Atendida por <strong>{fila.atendidaPor}</strong>{' '}
                        <span>{fila.atendidaHace}</span>
                      </p>
                      {fila.nota ? <p className="alerta__nota">{fila.nota}</p> : null}
                      <button
                        type="button"
                        className="boton boton--secundario"
                        disabled={procesando[fila.id]}
                        onClick={() => void reabrir(fila.id)}
                      >
                        <span>{procesando[fila.id] ? 'Reabriendo…' : 'Reabrir'}</span>
                      </button>
                    </div>
                  )}
                </div>
              </article>
            ))}
          </div>

          <Pagination
            pagina={vista.pagina}
            totalPaginas={vista.totalPaginas}
            paginas={vista.paginas}
            enlace={enlacePagina}
            etiqueta="Paginación de alertas"
          />
        </section>
      ) : null}
    </AppLayout>
  );
}
