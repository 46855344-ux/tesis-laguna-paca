import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api/client';
import type { AlertasVista, EstadoMuestra, EstacionPanel, Tablero } from '../api/types';
import { AppLayout } from '../components/AppLayout';
import { Cargando, ErrorPagina } from '../components/EstadoCarga';
import { EmptyState } from '../components/EmptyState';
import { Icon } from '../components/Icon';
import { LineChart } from '../components/LineChart';
import { StatusBadge } from '../components/StatusBadge';
import { useToast } from '../hooks/useToast';

const CIRCUNFERENCIA = 2 * Math.PI * 54;

const ESTADOS: Array<{
  estado: EstadoMuestra;
  etiqueta: string;
  clase: 'exito' | 'peligro' | 'neutra';
}> = [
  { estado: 'DENTRO', etiqueta: 'Dentro del rango', clase: 'exito' },
  { estado: 'ALERTA', etiqueta: 'Alerta ECA-Agua', clase: 'peligro' },
  { estado: 'SIN_DATOS', etiqueta: 'Sin datos', clase: 'neutra' }
];

interface ConteoEstados {
  DENTRO: number;
  ALERTA: number;
  SIN_DATOS: number;
}

type EstacionConMuestras = EstacionPanel & { muestras: number };

function DonutEstados({
  conteos,
  total
}: {
  conteos: ConteoEstados;
  total: number;
}) {
  let acumulado = 0;

  return (
    <div className="dona">
      <svg
        className="dona__lienzo"
        viewBox="0 0 160 160"
        role="img"
        aria-label={`Distribución de las ${total} estaciones por estado ECA-Agua`}
      >
        <circle className="dona__fondo" cx="80" cy="80" r="54" />
        <g transform="rotate(-90 80 80)">
          {ESTADOS.map((segmento) => {
            const cantidad = conteos[segmento.estado];
            if (cantidad === 0 || total === 0) {
              return null;
            }
            const fraccion = cantidad / total;
            const trazo = fraccion * CIRCUNFERENCIA;
            const desplazamiento = acumulado * CIRCUNFERENCIA;
            acumulado += fraccion;
            return (
              <circle
                key={segmento.estado}
                className={`dona__segmento dona__segmento--${segmento.clase}`}
                cx="80"
                cy="80"
                r="54"
                strokeDasharray={`${trazo} ${CIRCUNFERENCIA - trazo}`}
                strokeDashoffset={-desplazamiento}
              />
            );
          })}
        </g>
        <text className="dona__total" x="80" y="78" textAnchor="middle">
          {total}
        </text>
        <text className="dona__unidad" x="80" y="98" textAnchor="middle">
          estaciones
        </text>
      </svg>

      <ul className="dona__leyenda">
        {ESTADOS.map((segmento) => {
          const cantidad = conteos[segmento.estado];
          const porcentaje = total === 0 ? 0 : Math.round((cantidad / total) * 100);
          return (
            <li key={segmento.estado}>
              <span
                className={`dona__punto dona__punto--${segmento.clase}`}
                aria-hidden="true"
              />
              <span>{segmento.etiqueta}</span>
              <strong>{cantidad}</strong>
              <span className="dona__porcentaje">{porcentaje} %</span>
            </li>
          );
        })}
      </ul>
    </div>
  );
}

function MuestrasPorEstacion({ estaciones }: { estaciones: EstacionConMuestras[] }) {
  const maximo = Math.max(1, ...estaciones.map((estacion) => estacion.muestras));

  return (
    <ul className="ranking">
      {estaciones.map((estacion) => (
        <li className="ranking__item" key={estacion.id}>
          <div className="ranking__cabecera">
            <span className="ranking__nombre">{estacion.id}</span>
            <StatusBadge estado={estacion.estado} />
          </div>
          <div
            className="ranking__barra"
            role="img"
            aria-label={`${estacion.id}: ${estacion.muestras} muestras en las últimas 24 horas`}
          >
            <span
              className="ranking__relleno"
              style={{ width: `${(estacion.muestras / maximo) * 100}%` }}
            />
          </div>
          <div className="ranking__pie">
            <span>{estacion.muestras} muestras</span>
            <span>{estacion.ultima ? `pH ${estacion.ultima.ph}` : 'Sin lectura'}</span>
          </div>
        </li>
      ))}
    </ul>
  );
}

export function MetricsPage() {
  const { mostrarToast } = useToast();
  const [tablero, setTablero] = useState<Tablero | null>(null);
  const [alertas, setAlertas] = useState<AlertasVista | null>(null);
  const [error, setError] = useState<string | null>(null);
  const cargando = useRef(false);
  const tableroRef = useRef<Tablero | null>(null);

  useEffect(() => {
    document.title = 'Métricas · Laguna Paca';
  }, []);

  useEffect(() => {
    tableroRef.current = tablero;
  }, [tablero]);

  const cargar = useCallback(
    async (manual = false) => {
      if (cargando.current) {
        return;
      }
      cargando.current = true;
      try {
        const datos = await api.tablero();
        setTablero(datos);
        setError(null);

        try {
          const lista = await api.alertas({ estado: 'todas', page: 0 });
          setAlertas(lista);
        } catch {
          // Las métricas del tablero siguen siendo útiles aunque falle la
          // tabla de alertas; no se inventa una lista vacía.
          setAlertas(null);
        }

        if (manual) {
          mostrarToast('Métricas actualizadas.', 'exito');
        }
      } catch (fallo) {
        if (manual) {
          mostrarToast(
            fallo instanceof Error
              ? fallo.message
              : 'No se pudieron actualizar las métricas.',
            'error'
          );
        }
        if (!tableroRef.current) {
          setError(
            fallo instanceof Error
              ? fallo.message
              : 'No se pudieron cargar las métricas.'
          );
        }
      } finally {
        cargando.current = false;
      }
    },
    [mostrarToast]
  );

  useEffect(() => {
    void cargar(false);
    const intervalo = window.setInterval(() => {
      if (!document.hidden) {
        void cargar(false);
      }
    }, 20000);
    const alVolver = () => {
      if (!document.hidden) {
        void cargar(false);
      }
    };
    document.addEventListener('visibilitychange', alVolver);
    return () => {
      window.clearInterval(intervalo);
      document.removeEventListener('visibilitychange', alVolver);
    };
  }, [cargar]);

  const conteos = useMemo<ConteoEstados>(() => {
    const resultado: ConteoEstados = { DENTRO: 0, ALERTA: 0, SIN_DATOS: 0 };
    tablero?.estaciones.forEach((estacion) => {
      resultado[estacion.estado] += 1;
    });
    return resultado;
  }, [tablero]);

  const estacionesConMuestras = useMemo<EstacionConMuestras[]>(() => {
    if (!tablero) {
      return [];
    }
    const porEstacion = new Map<string, number>();
    tablero.serie.forEach((punto) => {
      porEstacion.set(punto.estacion, (porEstacion.get(punto.estacion) ?? 0) + 1);
    });
    return tablero.estaciones.map((estacion) => ({
      ...estacion,
      muestras: porEstacion.get(estacion.id) ?? 0
    }));
  }, [tablero]);

  if (error && !tablero) {
    return (
      <AppLayout titulo="Métricas" seccion="metricas">
        <ErrorPagina mensaje={error} alReintentar={() => void cargar(false)} />
      </AppLayout>
    );
  }

  if (!tablero) {
    return (
      <AppLayout titulo="Métricas" seccion="metricas">
        <Cargando texto="Cargando las métricas…" />
      </AppLayout>
    );
  }

  const totalMuestras = tablero.muestras;
  const totalAlertas = tablero.alertas;
  const cumplimiento =
    totalMuestras === 0
      ? null
      : Math.round(((totalMuestras - totalAlertas) / totalMuestras) * 100);
  const porcentajeAlertas =
    totalMuestras === 0 ? 0 : Math.round((totalAlertas / totalMuestras) * 100);
  const claseCumplimiento =
    cumplimiento === null
      ? 'neutra'
      : cumplimiento >= 90
        ? 'exito'
        : cumplimiento >= 70
          ? 'aviso'
          : 'peligro';
  const alertasRecientes = alertas?.filas.slice(0, 5) ?? [];

  return (
    <AppLayout titulo="Métricas" seccion="metricas">
      <div className="encabezado-pagina">
        <div>
          <h2>Métricas de la laguna</h2>
          <p>
            Indicadores de las últimas 24 horas para las cuatro estaciones. Los
            valores provienen de la telemetría persistida y del veredicto ECA-Agua
            que decide el backend.
          </p>
        </div>
        <div className="encabezado-pagina__acciones">
          <span className="pildora-periodo">
            <Icon name="historial" size={16} />
            Últimas 24 h
          </span>
          <button
            type="button"
            className="boton boton--secundario"
            onClick={() => void cargar(true)}
          >
            <Icon name="refrescar" />
            <span>Actualizar</span>
          </button>
        </div>
      </div>

      {tablero.sinDatos ? (
        <EmptyState
          titulo="Aún no hay datos para las métricas"
          descripcion="Cuando los nodos envíen muestras, aquí aparecerán los indicadores, la distribución de estados y las alertas recientes."
        >
          <Link className="boton boton--primario" to="/panel">
            <Icon name="panel" />
            <span>Volver al panel</span>
          </Link>
        </EmptyState>
      ) : null}

      <section className="rejilla-kpi metricas-kpi" aria-label="Indicadores de las últimas 24 horas">
        <article className="tarjeta kpi kpi--con-icono">
          <span className="kpi__icono kpi__icono--principal" aria-hidden="true">
            <Icon name="gota" />
          </span>
          <div className="kpi__cuerpo">
            <span className="kpi__etiqueta">Muestras (24 h)</span>
            <strong className="kpi__valor">{totalMuestras}</strong>
            <span className="kpi__nota">Recibidas de las cuatro estaciones.</span>
          </div>
        </article>

        <article className="tarjeta kpi kpi--con-icono">
          <span className="kpi__icono kpi__icono--peligro" aria-hidden="true">
            <Icon name="alertas" />
          </span>
          <div className="kpi__cuerpo">
            <span className="kpi__etiqueta">Alertas (24 h)</span>
            <strong
              className={`kpi__valor${totalAlertas > 0 ? ' kpi__valor--alerta' : ''}`}
            >
              {totalAlertas}
            </strong>
            <span className="kpi__nota">{porcentajeAlertas} % de las muestras.</span>
          </div>
        </article>

        <article className="tarjeta kpi kpi--con-icono">
          <span
            className={`kpi__icono kpi__icono--${claseCumplimiento}`}
            aria-hidden="true"
          >
            <Icon name="escudo" />
          </span>
          <div className="kpi__cuerpo">
            <span className="kpi__etiqueta">Cumplimiento ECA-Agua</span>
            <strong className="kpi__valor">
              {cumplimiento === null ? '—' : `${cumplimiento} %`}
            </strong>
            <span className="kpi__nota">pH dentro del rango 6.5 – 9.0.</span>
          </div>
        </article>

        <article className="tarjeta kpi kpi--con-icono">
          <span className="kpi__icono kpi__icono--info" aria-hidden="true">
            <Icon name="estaciones" />
          </span>
          <div className="kpi__cuerpo">
            <span className="kpi__etiqueta">Estaciones con datos</span>
            <strong className="kpi__valor">
              {tablero.estacionesConDatos} / {tablero.estaciones.length}
            </strong>
            <span className="kpi__nota">Con al menos una lectura histórica.</span>
          </div>
        </article>
      </section>

      <div className="metricas-fila">
        <section
          className="tarjeta metricas-panel metricas-panel--principal"
          aria-labelledby="titulo-tendencia"
        >
          <div className="tarjeta__cabecera">
            <div>
              <h3 id="titulo-tendencia" className="tarjeta__titulo">
                Tendencia de pH
              </h3>
              <p className="tarjeta__nota">
                Una línea por estación. La regla ECA-Agua se evalúa sobre el pH.
              </p>
            </div>
            <span className="etiqueta etiqueta--info">24 h</span>
          </div>
          <LineChart
            puntos={tablero.serie}
            estaciones={tablero.estaciones.map((estacion) => estacion.id)}
            variable="ph"
          />
        </section>

        <section
          className="tarjeta metricas-panel metricas-panel--dona"
          aria-labelledby="titulo-estados"
        >
          <div className="tarjeta__cabecera">
            <div>
              <h3 id="titulo-estados" className="tarjeta__titulo">
                Estado de las estaciones
              </h3>
              <p className="tarjeta__nota">
                Veredicto persistido por la ingesta, sin recalcular el pH.
              </p>
            </div>
          </div>
          <DonutEstados conteos={conteos} total={tablero.estaciones.length} />
        </section>
      </div>

      <div className="metricas-fila metricas-fila--tablas">
        <section
          className="tarjeta tarjeta--tabla metricas-panel metricas-panel--tabla"
          aria-labelledby="titulo-alertas-recientes"
        >
          <div className="tabla__cabecera metricas-tabla__cabecera">
            <div>
              <h3 id="titulo-alertas-recientes" className="tarjeta__titulo">
                Alertas recientes
              </h3>
              <p className="tarjeta__nota">
                Últimas alertas ECA-Agua registradas, pendientes o atendidas.
              </p>
            </div>
            <Link to="/panel/alertas">Ver todas</Link>
          </div>

          {alertas === null ? (
            <p className="metricas-tabla__aviso">
              No se pudieron cargar las alertas recientes. El resto de las métricas
              sigue disponible.
            </p>
          ) : (
            <div className="tabla__contenedor">
              <table className="tabla tabla--metricas">
                <caption className="visualmente-oculto">
                  Últimas cinco alertas ECA-Agua
                </caption>
                <thead>
                  <tr>
                    <th scope="col">Estación</th>
                    <th scope="col">Fecha y hora</th>
                    <th scope="col">pH</th>
                    <th scope="col">Motivo</th>
                    <th scope="col">Estado</th>
                  </tr>
                </thead>
                <tbody>
                  {alertasRecientes.length === 0 ? (
                    <tr>
                      <td colSpan={5} className="metricas-tabla__vacia">
                        No hay alertas registradas en el periodo.
                      </td>
                    </tr>
                  ) : (
                    alertasRecientes.map((alerta) => (
                      <tr key={alerta.id}>
                        <td className="tabla__estacion">{alerta.estacion}</td>
                        <td>
                          <span className="tabla__fecha">{alerta.fecha}</span>{' '}
                          <span className="tabla__hora">{alerta.hora}</span>
                        </td>
                        <td className="tabla__numero">{alerta.ph}</td>
                        <td>{alerta.motivos[0] ?? 'Sin motivo registrado.'}</td>
                        <td>
                          <span
                            className={`etiqueta ${
                              alerta.atendida ? 'etiqueta--exito' : 'etiqueta--alerta'
                            }`}
                          >
                            <span className="etiqueta__punto" />
                            <span>{alerta.atendida ? 'Atendida' : 'Pendiente'}</span>
                          </span>
                        </td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>
          )}
        </section>

        <section
          className="tarjeta metricas-panel"
          aria-labelledby="titulo-muestras-estacion"
        >
          <div className="tarjeta__cabecera">
            <div>
              <h3 id="titulo-muestras-estacion" className="tarjeta__titulo">
                Muestras por estación
              </h3>
              <p className="tarjeta__nota">Volumen recibido en las últimas 24 horas.</p>
            </div>
            <Link to="/panel/historial">Historial</Link>
          </div>
          <MuestrasPorEstacion estaciones={estacionesConMuestras} />
        </section>
      </div>

      <p className="metricas-pie">
        Actualizado a las {tablero.horaActualizacion} · Fuente: telemetría del
        backend · El veredicto ECA-Agua no se recalcula en el navegador.
      </p>
    </AppLayout>
  );
}
