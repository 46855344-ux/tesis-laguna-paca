import { useMemo } from 'react';
import type { PuntoSerie, VariableSerie } from '../api/types';

interface LineChartProps {
  puntos: PuntoSerie[];
  estaciones: string[];
  variable: VariableSerie;
}

const ANCHO = 800;
const ALTO = 280;
const MARGEN = { arriba: 16, derecha: 16, abajo: 30, izquierda: 52 };
const INTERVALO_MINIMO_MS = 30 * 60 * 1000;

const ETIQUETAS: Record<VariableSerie, string> = {
  ph: 'pH',
  turbidez: 'turbidez',
  temperatura: 'temperatura',
  tds: 'sólidos disueltos'
};

const HORA_CORTA = new Intl.DateTimeFormat('es-PE', {
  hour: '2-digit',
  minute: '2-digit',
  hour12: false,
  timeZone: 'America/Lima'
});

function formatearValor(valor: number, variable: VariableSerie): string {
  if (variable === 'ph') {
    return valor.toFixed(2);
  }
  if (variable === 'tds') {
    return valor.toFixed(0);
  }
  return valor.toFixed(1);
}

export function LineChart({ puntos, estaciones, variable }: LineChartProps) {
  const series = useMemo(() => {
    const indicePorId = new Map(estaciones.map((id, indice) => [id, indice]));
    const agrupadas = estaciones.map((id, indice) => ({
      id,
      indice,
      puntos: [] as Array<{ t: number; v: number }>
    }));

    puntos.forEach((punto) => {
      const indice = indicePorId.get(punto.estacion);
      const valor = punto[variable];
      if (indice === undefined || typeof valor !== 'number' || !Number.isFinite(valor)) {
        return;
      }
      agrupadas[indice].puntos.push({ t: new Date(punto.instante).getTime(), v: valor });
    });

    agrupadas.forEach((serie) => serie.puntos.sort((a, b) => a.t - b.t));
    return agrupadas.filter((serie) => serie.puntos.length > 0);
  }, [puntos, estaciones, variable]);

  const geometria = useMemo(() => {
    if (series.length === 0) {
      return null;
    }

    let minimo = Infinity;
    let maximo = -Infinity;
    let tMin = Infinity;
    let tMax = -Infinity;

    series.forEach((serie) => {
      serie.puntos.forEach((punto) => {
        minimo = Math.min(minimo, punto.v);
        maximo = Math.max(maximo, punto.v);
        tMin = Math.min(tMin, punto.t);
        tMax = Math.max(tMax, punto.t);
      });
    });

    const rango = maximo - minimo;
    const aire = rango === 0 ? Math.max(Math.abs(maximo) * 0.05, 0.5) : rango * 0.1;
    const yMin = minimo - aire;
    const yMax = maximo + aire;

    if (tMax - tMin < INTERVALO_MINIMO_MS) {
      const centro = (tMin + tMax) / 2;
      tMin = centro - INTERVALO_MINIMO_MS / 2;
      tMax = centro + INTERVALO_MINIMO_MS / 2;
    }

    const anchoUtil = ANCHO - MARGEN.izquierda - MARGEN.derecha;
    const altoUtil = ALTO - MARGEN.arriba - MARGEN.abajo;

    return {
      yMin,
      yMax,
      tMin,
      tMax,
      x: (t: number) => MARGEN.izquierda + ((t - tMin) / (tMax - tMin)) * anchoUtil,
      y: (valor: number) =>
        MARGEN.arriba + altoUtil - ((valor - yMin) / (yMax - yMin)) * altoUtil
    };
  }, [series]);

  if (!geometria) {
    return <p className="grafico__vacio">Todavía no hay muestras en las últimas 24 horas.</p>;
  }

  return (
    <div className="grafico">
      <svg
        className="grafico__lienzo"
        viewBox={`0 0 ${ANCHO} ${ALTO}`}
        role="img"
        aria-label={`Serie temporal de ${ETIQUETAS[variable]} de las últimas 24 horas`}
      >
        {Array.from({ length: 5 }, (_, indice) => {
          const valor = geometria.yMin + ((geometria.yMax - geometria.yMin) * indice) / 4;
          const y = geometria.y(valor);
          return (
            <g key={`division-${indice}`}>
              <line
                className="grafico__rejilla"
                x1={MARGEN.izquierda}
                x2={ANCHO - MARGEN.derecha}
                y1={y}
                y2={y}
              />
              <text
                className="grafico__eje"
                x={MARGEN.izquierda - 8}
                y={y + 4}
                textAnchor="end"
              >
                {formatearValor(valor, variable)}
              </text>
            </g>
          );
        })}

        {Array.from({ length: 5 }, (_, indice) => {
          const instante = geometria.tMin + ((geometria.tMax - geometria.tMin) * indice) / 4;
          return (
            <text
              key={`marca-${indice}`}
              className="grafico__eje"
              x={geometria.x(instante)}
              y={ALTO - 8}
              textAnchor={indice === 0 ? 'start' : indice === 4 ? 'end' : 'middle'}
            >
              {HORA_CORTA.format(new Date(instante))}
            </text>
          );
        })}

        {series.map((serie) => {
          const clase = `serie-${serie.indice + 1}`;
          const titulo = `${serie.id} — ${ETIQUETAS[variable]}`;
          if (serie.puntos.length === 1) {
            const unico = serie.puntos[0];
            return (
              <circle
                key={serie.id}
                className={`grafico__punto ${clase}`}
                cx={geometria.x(unico.t)}
                cy={geometria.y(unico.v)}
                r={4}
              >
                <title>{titulo}</title>
              </circle>
            );
          }
          const puntosTexto = serie.puntos
            .map((punto) => `${geometria.x(punto.t)},${geometria.y(punto.v)}`)
            .join(' ');
          return (
            <polyline key={serie.id} className={`grafico__linea ${clase}`} points={puntosTexto}>
              <title>{titulo}</title>
            </polyline>
          );
        })}
      </svg>

      <ul className="leyenda" aria-label="Estaciones representadas">
        {series.map((serie) => (
          <li className="leyenda__item" key={serie.id}>
            <span
              className={`leyenda__muestra serie-${serie.indice + 1}`}
              aria-hidden="true"
            />
            {serie.id}
          </li>
        ))}
      </ul>
    </div>
  );
}
