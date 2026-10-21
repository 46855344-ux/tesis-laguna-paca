import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import type { MapaLaguna } from '../api/types';
import { useTheme } from '../hooks/useTheme';

interface GoogleMapsApi {
  maps: {
    Map: new (contenedor: HTMLElement, opciones?: Record<string, unknown>) => GoogleMap;
    Marker: new (opciones?: Record<string, unknown>) => GoogleMarker;
    LatLngBounds: new () => GoogleBounds;
    SymbolPath: { CIRCLE: number };
    event: { trigger: (objeto: unknown, evento: string) => void };
  };
}

interface GoogleMap {
  fitBounds: (limites: GoogleBounds, margen?: number) => void;
  setOptions: (opciones: Record<string, unknown>) => void;
}

interface GoogleBounds {
  extend: (punto: { lat: number; lng: number }) => void;
}

interface GoogleMarker {
  setIcon: (icono: Record<string, unknown>) => void;
  setTitle: (titulo: string) => void;
  addListener: (evento: string, callback: () => void) => void;
}

declare global {
  interface Window {
    google?: GoogleMapsApi;
  }
}

let promesaGoogle: Promise<void> | null = null;
let claveCargada = '';

function cargarGoogle(apiKey: string): Promise<void> {
  if (window.google?.maps) {
    return Promise.resolve();
  }
  if (promesaGoogle && claveCargada === apiKey) {
    return promesaGoogle;
  }
  claveCargada = apiKey;
  promesaGoogle = new Promise<void>((resolve, reject) => {
    const script = document.createElement('script');
    script.src = `https://maps.googleapis.com/maps/api/js?key=${encodeURIComponent(
      apiKey
    )}&v=weekly&loading=async`;
    script.async = true;
    script.addEventListener('load', () => resolve());
    script.addEventListener('error', () =>
      reject(new Error('No se pudo cargar Google Maps.'))
    );
    document.head.appendChild(script);
  });
  return promesaGoogle;
}

function token(nombre: string): string {
  return getComputedStyle(document.documentElement).getPropertyValue(nombre).trim();
}

function iconoDePunto(clase: string): Record<string, unknown> {
  const colores: Record<string, string> = {
    exito: '--color-success',
    peligro: '--color-danger',
    neutra: '--color-text-muted'
  };
  return {
    path: window.google?.maps.SymbolPath.CIRCLE ?? 0,
    scale: 9,
    fillColor: token(colores[clase] ?? colores.neutra),
    fillOpacity: 1,
    strokeColor: token('--color-surface'),
    strokeWeight: 3
  };
}

interface LagoonMapProps {
  mapa: MapaLaguna;
  apiKey: string;
}

export function LagoonMap({ mapa, apiKey }: LagoonMapProps) {
  const { tema } = useTheme();
  const navigate = useNavigate();
  const [googleListo, setGoogleListo] = useState(false);
  const contenedorGoogle = useRef<HTMLDivElement>(null);
  const mapaGoogle = useRef<{
    mapa: GoogleMap;
    marcadores: Record<string, GoogleMarker>;
  } | null>(null);

  useEffect(() => {
    if (!apiKey) {
      return;
    }
    let cancelado = false;
    cargarGoogle(apiKey)
      .then(() => {
        if (!cancelado) {
          setGoogleListo(true);
        }
      })
      .catch(() => {
        // Sin internet queda el contorno propio; la tarjeta no se rompe.
        if (!cancelado) {
          setGoogleListo(false);
        }
      });
    return () => {
      cancelado = true;
    };
  }, [apiKey]);

  useEffect(() => {
    if (!googleListo || !window.google?.maps || !contenedorGoogle.current) {
      return;
    }
    const google = window.google;

    try {
      if (!mapaGoogle.current) {
        const limites = new google.maps.LatLngBounds();
        mapa.puntos.forEach((punto) => limites.extend({ lat: punto.lat, lng: punto.lon }));

        // Google mide el contenedor al crearlo: debe estar visible antes.
        contenedorGoogle.current.hidden = false;
        void contenedorGoogle.current.offsetWidth;

        const mapaNuevo = new google.maps.Map(contenedorGoogle.current, {
          mapTypeControl: false,
          streetViewControl: false,
          fullscreenControl: false,
          colorScheme: tema === 'oscuro' ? 'DARK' : 'LIGHT'
        });
        const marcadores: Record<string, GoogleMarker> = {};

        mapa.puntos.forEach((punto) => {
          const marcador = new google.maps.Marker({
            map: mapaNuevo,
            position: { lat: punto.lat, lng: punto.lon },
            title: punto.titulo,
            icon: iconoDePunto(punto.clase)
          });
          marcador.addListener('click', () => {
            navigate(`/panel/historial?estacion=${encodeURIComponent(punto.id)}`);
          });
          marcadores[punto.id] = marcador;
        });

        mapaNuevo.fitBounds(limites, 48);
        google.maps.event.trigger(mapaNuevo, 'resize');
        mapaGoogle.current = { mapa: mapaNuevo, marcadores };
      } else {
        mapa.puntos.forEach((punto) => {
          const marcador = mapaGoogle.current?.marcadores[punto.id];
          if (marcador) {
            marcador.setIcon(iconoDePunto(punto.clase));
            marcador.setTitle(punto.titulo);
          }
        });
      }
    } catch {
      setGoogleListo(false);
      if (contenedorGoogle.current) {
        contenedorGoogle.current.hidden = true;
      }
    }
  }, [googleListo, mapa.puntos, navigate, tema]);

  useEffect(() => {
    if (mapaGoogle.current) {
      mapaGoogle.current.mapa.setOptions({
        colorScheme: tema === 'oscuro' ? 'DARK' : 'LIGHT'
      });
      mapa.puntos.forEach((punto) => {
        mapaGoogle.current?.marcadores[punto.id]?.setIcon(iconoDePunto(punto.clase));
      });
    }
  }, [tema, mapa.puntos]);

  return (
    <section className="tarjeta seccion-separada" aria-labelledby="titulo-mapa">
      <div className="tarjeta__cabecera tarjeta__cabecera--envolvente">
        <div>
          <h3 id="titulo-mapa" className="tarjeta__titulo">
            Mapa de la laguna
          </h3>
          <p className="tarjeta__nota">
            Contorno de la Laguna de Paca con la última lectura de cada estación.
            Selecciona un punto para ver su historial.
          </p>
        </div>
      </div>

      <div className="mapa-laguna">
        <svg
          id="mapa-laguna-svg"
          className="mapa-laguna__lienzo"
          viewBox={mapa.viewBox}
          role="img"
          aria-label="Mapa de la Laguna de Paca con las estaciones N1 a N4"
          style={{ display: googleListo ? 'none' : undefined }}
        >
          <path className="mapa-laguna__agua" d={mapa.camino} />
          {mapa.puntos.map((punto) => (
            <g
              key={punto.id}
              className={`estacion-mapa estacion-mapa--${punto.clase}`}
              data-punto={punto.id}
            >
              <a
                className="estacion-mapa__enlace"
                href={`/panel/historial?estacion=${encodeURIComponent(punto.id)}`}
                aria-label={punto.titulo}
                onClick={(evento) => {
                  evento.preventDefault();
                  navigate(`/panel/historial?estacion=${encodeURIComponent(punto.id)}`);
                }}
              >
                <title>{punto.titulo}</title>
                <circle cx={punto.x} cy={punto.y} r={9} />
                <text className="estacion-mapa__id" x={punto.x + 16} y={punto.y - 2}>
                  {punto.id}
                </text>
                <text className="estacion-mapa__ph" x={punto.x + 16} y={punto.y + 13}>
                  {punto.ph ?? ''}
                </text>
              </a>
            </g>
          ))}
        </svg>

        {apiKey ? (
          <div
            ref={contenedorGoogle}
            id="mapa-google"
            className="mapa-laguna__google"
            hidden={!googleListo}
          />
        ) : null}
      </div>

      <ul className="leyenda" aria-label="Estados posibles de una estación">
        <li className="leyenda__item">
          <span className="leyenda__punto leyenda__punto--exito" />
          <span>Dentro del rango</span>
        </li>
        <li className="leyenda__item">
          <span className="leyenda__punto leyenda__punto--peligro" />
          <span>Alerta ECA-Agua</span>
        </li>
        <li className="leyenda__item">
          <span className="leyenda__punto leyenda__punto--neutra" />
          <span>Sin datos</span>
        </li>
      </ul>

      {apiKey ? (
        <p className="tarjeta__nota">
          Con conexión se muestra el mapa de Google; sin ella queda el contorno de
          OpenStreetMap que dibuja el servidor.
        </p>
      ) : null}
    </section>
  );
}
