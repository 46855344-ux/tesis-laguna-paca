import { useCallback, useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { api, ApiError } from '../api/client';
import type { Tablero, VariableSerie } from '../api/types';
import { AppLayout } from '../components/AppLayout';
import { Cargando, ErrorPagina } from '../components/EstadoCarga';
import { EmptyState } from '../components/EmptyState';
import { Icon } from '../components/Icon';
import { LagoonMap } from '../components/LagoonMap';
import { LineChart } from '../components/LineChart';
import { StatusBadge } from '../components/StatusBadge';
import { useAuth } from '../hooks/useAuth';
import { useToast } from '../hooks/useToast';

const VARIABLES: Array<{ id: VariableSerie; etiqueta: string }> = [
  { id: 'ph', etiqueta: 'pH' },
  { id: 'turbidez', etiqueta: 'Turbidez' },
  { id: 'temperatura', etiqueta: 'Temperatura' },
  { id: 'tds', etiqueta: 'Sólidos disueltos' }
];

const COMANDO = `curl -X POST http://localhost:8080/api/v1/telemetry \\
  -H "Authorization: Bearer $TOKEN" \\
  -H "Content-Type: application/json" \\
  -d '{"node_id":"N1","ph":7.2,"turbidity_ntu":15.4,"temp_c":18.5,"tds_ppm":320}'`;

export function DashboardPage() {
  const { sesion } = useAuth();
  const { mostrarToast } = useToast();
  const [tablero, setTablero] = useState<Tablero | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [variable, setVariable] = useState<VariableSerie>('ph');
  const [horizonte, setHorizonte] = useState(6);
  const [generando, setGenerando] = useState(false);
  const cargando = useRef(false);
  const tableroRef = useRef<Tablero | null>(null);

  useEffect(() => {
    document.title = 'Panel de monitoreo · Laguna Paca';
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
        if (manual) {
          mostrarToast('Datos actualizados.', 'exito');
        }
      } catch (fallo) {
        if (manual) {
          mostrarToast(
            fallo instanceof Error
              ? fallo.message
              : 'No se pudieron actualizar los datos.',
            'error'
          );
        }
        if (!tableroRef.current) {
          setError(
            fallo instanceof Error ? fallo.message : 'No se pudieron cargar los datos.'
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

  const generar = async () => {
    setGenerando(true);
    try {
      const respuesta = await api.predecir(horizonte);
      mostrarToast(respuesta.mensaje, 'exito');
      await cargar(false);
    } catch (fallo) {
      mostrarToast(
        fallo instanceof ApiError || fallo instanceof Error
          ? fallo.message
          : 'No se pudo generar la predicción.',
        'error'
      );
    } finally {
      setGenerando(false);
    }
  };

  const copiar = async () => {
    try {
      await navigator.clipboard.writeText(COMANDO);
      mostrarToast('Comando copiado al portapapeles.', 'exito');
    } catch {
      mostrarToast('No se pudo copiar. Selecciona el texto y cópialo a mano.', 'error');
    }
  };

  return (
    <AppLayout titulo="Panel de monitoreo" seccion="panel">
      <div className="encabezado-pagina">
        <div>
          <h2>Estado de la laguna</h2>
          <p>
            Resumen de las cuatro estaciones de muestreo. Los datos llegan desde los
            nodos ESP32 y las alertas las decide el backend con la regla ECA-Agua.
          </p>
        </div>
        <div className="encabezado-pagina__acciones">
          <button
            type="button"
            className="boton boton--secundario"
            onClick={() => void cargar(true)}
          >
            <Icon name="refrescar" />
            <span>Actualizar</span>
          </button>
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

      {error && !tablero ? (
        <ErrorPagina mensaje={error} alReintentar={() => void cargar(false)} />
      ) : null}

      {!tablero && !error ? <Cargando texto="Cargando el tablero…" /> : null}

      {tablero ? (
        <>
          {tablero.sinDatos ? (
            <EmptyState
              titulo="Aún no hay datos de telemetría"
              descripcion="Cuando el primer nodo envíe una muestra, aquí aparecerán el pH, la turbidez, la temperatura y los sólidos disueltos de cada estación."
            >
              <a className="boton boton--primario" href="#como-enviar">
                <Icon name="gota" />
                <span>Cómo enviar una muestra</span>
              </a>
              <a
                className="boton boton--secundario"
                href="/swagger-ui.html"
                target="_blank"
                rel="noopener noreferrer"
              >
                <Icon name="historial" />
                <span>Probar la API</span>
              </a>
            </EmptyState>
          ) : null}

          <section className="rejilla-kpi" aria-label="Resumen de las últimas 24 horas">
            <article className="tarjeta kpi">
              <span className="kpi__etiqueta">Muestras (24 h)</span>
              <strong className="kpi__valor">{tablero.muestras}</strong>
            </article>
            <article className="tarjeta kpi">
              <span className="kpi__etiqueta">Alertas (24 h)</span>
              <strong
                className={`kpi__valor${tablero.alertas > 0 ? ' kpi__valor--alerta' : ''}`}
              >
                {tablero.alertas}
              </strong>
            </article>
            <article className="tarjeta kpi">
              <span className="kpi__etiqueta">Estaciones con datos</span>
              <strong className="kpi__valor">
                {tablero.estacionesConDatos} / {tablero.estaciones.length}
              </strong>
            </article>
            <article className="tarjeta kpi">
              <span className="kpi__etiqueta">Actualizado a las</span>
              <strong className="kpi__valor kpi__valor--hora">
                {tablero.horaActualizacion}
              </strong>
            </article>
          </section>

          <LagoonMap mapa={tablero.mapa} apiKey={sesion?.googleMapsApiKey ?? ''} />

          <section className="seccion-separada" aria-labelledby="titulo-estaciones">
            <h3 id="titulo-estaciones" className="titulo-seccion">
              Estaciones de muestreo
            </h3>
            <div className="rejilla">
              {tablero.estaciones.map((estacion) => (
                <article
                  key={estacion.id}
                  className="tarjeta tarjeta--estacion"
                  data-estacion={estacion.id}
                >
                  <div className="tarjeta__cabecera">
                    <div className="estacion__identidad">
                      <Icon name="estaciones" />
                      <h4 className="tarjeta__titulo">{estacion.id}</h4>
                    </div>
                    <StatusBadge
                      estado={estacion.estado}
                      etiqueta={estacion.etiqueta}
                      clase={estacion.clase}
                    />
                  </div>

                  <p className="tarjeta__nota">
                    {estacion.conDatos && estacion.ultima
                      ? `Última muestra ${estacion.ultima.hace} · ${estacion.ultima.fecha}`
                      : 'Esperando la primera muestra del nodo.'}
                  </p>

                  {estacion.atendida ? (
                    <p className="tarjeta__nota tarjeta__nota--atencion">
                      Atendida por {estacion.atendidaPor} · {estacion.atendidaHace}
                    </p>
                  ) : null}

                  <dl className="mini-datos">
                    <div>
                      <dt>pH</dt>
                      <dd>{estacion.ultima?.ph ?? '—'}</dd>
                    </div>
                    <div>
                      <dt>Turbidez</dt>
                      <dd>
                        <span>{estacion.ultima?.turbidez ?? '—'}</span>{' '}
                        <span className="mini-datos__unidad">NTU</span>
                      </dd>
                    </div>
                    <div>
                      <dt>Temperatura</dt>
                      <dd>
                        <span>{estacion.ultima?.temperatura ?? '—'}</span>{' '}
                        <span className="mini-datos__unidad">°C</span>
                      </dd>
                    </div>
                    <div>
                      <dt>Sólidos disueltos</dt>
                      <dd>
                        <span>{estacion.ultima?.tds ?? '—'}</span>{' '}
                        <span className="mini-datos__unidad">ppm</span>
                      </dd>
                    </div>
                  </dl>

                  {estacion.motivos.length > 0 ? (
                    <ul className="motivos">
                      {estacion.motivos.map((motivo) => (
                        <li key={motivo}>{motivo}</li>
                      ))}
                    </ul>
                  ) : null}
                </article>
              ))}
            </div>
          </section>

          <section className="tarjeta seccion-separada" aria-labelledby="titulo-serie">
            <div className="tarjeta__cabecera tarjeta__cabecera--envolvente">
              <div>
                <h3 id="titulo-serie" className="tarjeta__titulo">
                  Serie de las últimas 24 horas
                </h3>
                <p className="tarjeta__nota">
                  Una línea por estación. La regla ECA-Agua se evalúa sobre el pH.
                </p>
              </div>
              <div className="selector-variable" role="group" aria-label="Variable a graficar">
                {VARIABLES.map((opcion) => (
                  <button
                    key={opcion.id}
                    type="button"
                    className={`chip${variable === opcion.id ? ' es-activo' : ''}`}
                    aria-pressed={variable === opcion.id}
                    onClick={() => setVariable(opcion.id)}
                  >
                    {opcion.etiqueta}
                  </button>
                ))}
              </div>
            </div>

            <LineChart
              puntos={tablero.serie}
              estaciones={tablero.estaciones.map((estacion) => estacion.id)}
              variable={variable}
            />
          </section>

          <section className="tarjeta seccion-separada" aria-labelledby="titulo-predicciones">
            <div className="tarjeta__cabecera tarjeta__cabecera--envolvente">
              <div>
                <h3 id="titulo-predicciones" className="tarjeta__titulo">
                  Predicciones del ST-GNN
                </h3>
                <p className="tarjeta__nota">
                  pH previsto a 1, 3 y 6 horas para las cuatro estaciones. Son
                  predicciones, no mediciones: no cambian el veredicto ECA-Agua.
                </p>
              </div>
              <form
                className="formulario-prediccion"
                onSubmit={(evento) => {
                  evento.preventDefault();
                  void generar();
                }}
              >
                <label className="campo__etiqueta" htmlFor="horizonte">
                  Horizonte
                </label>
                <select
                  className="campo__control"
                  id="horizonte"
                  value={horizonte}
                  onChange={(evento) => setHorizonte(Number(evento.target.value))}
                >
                  <option value={1}>1 hora</option>
                  <option value={3}>3 horas</option>
                  <option value={6}>6 horas</option>
                </select>
                <button type="submit" className="boton boton--primario" disabled={generando}>
                  <Icon name="refrescar" />
                  <span>{generando ? 'Generando…' : 'Generar predicción'}</span>
                </button>
              </form>
            </div>

            <p className="tarjeta__nota">
              El modelo se entrenó con datos <strong>sintéticos</strong>: sirve para
              validar la arquitectura, no para afirmar precisión en la laguna real. Si
              el servicio de IA no está disponible, el tablero y las alertas siguen
              funcionando.
            </p>

            {tablero.predicciones.length === 0 ? (
              <div className="estado-vacio">
                <p>
                  Todavía no hay predicciones. Con 24 muestras por estación puedes
                  generar la primera.
                </p>
              </div>
            ) : (
              <div className="rejilla-predicciones">
                {tablero.predicciones.map((prediccion) => (
                  <article
                    key={`${prediccion.estacion}-${prediccion.horizonte}-${prediccion.objetivo}`}
                    className={`prediccion ${
                      prediccion.evaluada ? 'prediccion--evaluada' : 'prediccion--pendiente'
                    }`}
                  >
                    <header className="prediccion__cabecera">
                      <h4 className="prediccion__titulo">
                        {prediccion.estacion} · {prediccion.horizonte} h
                      </h4>
                      <span className="etiqueta">{prediccion.estado}</span>
                    </header>
                    <p className="prediccion__valor">
                      <strong>{prediccion.phPrevisto}</strong>
                      <span>pH previsto</span>
                    </p>
                    <p className="tarjeta__nota">
                      Objetivo: {prediccion.objetivo} · generada {prediccion.generadoHace}
                    </p>
                    {prediccion.evaluada ? (
                      <p className="tarjeta__nota">
                        Observado: {prediccion.phObservado} · error {prediccion.error}
                      </p>
                    ) : null}
                    <p className="prediccion__origen">
                      {prediccion.modelo} · {prediccion.origen}
                    </p>
                  </article>
                ))}
              </div>
            )}
          </section>

          <div className="rejilla rejilla--ancha seccion-separada">
            <section className="tarjeta" id="como-enviar" aria-labelledby="titulo-comando">
              <div className="tarjeta__cabecera">
                <h3 id="titulo-comando" className="tarjeta__titulo">
                  Enviar una muestra
                </h3>
                <button type="button" className="boton boton--secundario" onClick={() => void copiar()}>
                  <Icon name="copiar" size={16} />
                  <span>Copiar</span>
                </button>
              </div>
              <p className="tarjeta__nota">
                La ingesta exige el JWT que entrega <code>/api/v1/auth/token</code>. El
                nodo ESP32 lo hace solo; aquí se muestra el mismo camino.
              </p>
              <pre className="bloque-codigo" id="comando-ejemplo">
                {COMANDO}
              </pre>
            </section>

            <section className="tarjeta" aria-labelledby="titulo-reglas">
              <div className="tarjeta__cabecera">
                <h3 id="titulo-reglas" className="tarjeta__titulo">
                  Reglas de alerta
                </h3>
                <Icon name="escudo" />
              </div>
              <p className="tarjeta__nota">
                Las decide el backend, nunca el nodo: el firmware solo mide y envía.
              </p>
              <dl className="mini-datos">
                <div>
                  <dt>Acidez</dt>
                  <dd>
                    <span className="etiqueta etiqueta--peligro">pH &lt; 6.5</span>
                  </dd>
                </div>
                <div>
                  <dt>Alcalinidad</dt>
                  <dd>
                    <span className="etiqueta etiqueta--peligro">pH &gt; 9.0</span>
                  </dd>
                </div>
                <div>
                  <dt>Dentro del rango</dt>
                  <dd>
                    <span className="etiqueta etiqueta--exito">6.5 – 9.0</span>
                  </dd>
                </div>
                <div>
                  <dt>Límites exactos</dt>
                  <dd>
                    <span className="etiqueta etiqueta--info">Sin alerta</span>
                  </dd>
                </div>
              </dl>
            </section>
          </div>

          <p className="tarjeta__nota seccion-separada">
            <Link to="/panel/historial">Ver el historial completo</Link> ·{' '}
            <Link to="/panel/alertas">Gestionar las alertas</Link>
          </p>
        </>
      ) : null}
    </AppLayout>
  );
}
