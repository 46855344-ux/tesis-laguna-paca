import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  api,
  ApiError,
  clearCsrfCache,
  setUnauthorizedHandler
} from '../api/client';
import type { SesionPanel, Tablero } from '../api/types';

const SESION: SesionPanel = {
  usuario: 'admin',
  rol: 'Administrador',
  inicial: 'A',
  estaciones: ['N1', 'N2', 'N3', 'N4'],
  googleMapsApiKey: ''
};

const TABLERO: Tablero = {
  actualizadoEn: '2026-10-21T12:00:00Z',
  horaActualizacion: '07:00:00',
  muestras: 0,
  alertas: 0,
  estaciones: [],
  mapa: { viewBox: '0 0 720 460', camino: 'M 0 0 Z', puntos: [] },
  serie: [],
  predicciones: [],
  estacionesConDatos: 0,
  sinDatos: true
};

function respuesta(cuerpo: unknown, status = 200, url = 'http://localhost/'): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    url,
    json: async () => cuerpo
  } as Response;
}

function token(): Response {
  return respuesta({ token: 't1', headerName: 'X-CSRF-TOKEN', parameterName: '_csrf' });
}

function cabecera(init: RequestInit | undefined, nombre: string): string | null {
  return new Headers(init?.headers).get(nombre);
}

describe('cliente de la API del panel', () => {
  beforeEach(() => {
    clearCsrfCache();
    setUnauthorizedHandler(null);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    setUnauthorizedHandler(null);
  });

  it('pide el token CSRF y lo envía en cada petición', async () => {
    const fetchMock = vi.fn(async (entrada: RequestInfo | URL, _init?: RequestInit) => {
      const url = String(entrada);
      if (url === '/panel/csrf') {
        return token();
      }
      if (url === '/panel/api/sesion') {
        return respuesta(SESION);
      }
      throw new Error(`URL inesperada: ${url}`);
    });
    vi.stubGlobal('fetch', fetchMock);

    await expect(api.sesion()).resolves.toEqual(SESION);

    const llamada = fetchMock.mock.calls.find(([url]) => String(url) === '/panel/api/sesion');
    expect(llamada).toBeDefined();
    expect(cabecera(llamada?.[1], 'X-CSRF-TOKEN')).toBe('t1');
    expect(cabecera(llamada?.[1], 'Accept')).toBe('application/json');
  });

  it('avisa al proveedor de sesión cuando el backend responde 401', async () => {
    let sesionExpirada = false;
    setUnauthorizedHandler(() => {
      sesionExpirada = true;
    });

    vi.stubGlobal(
      'fetch',
      vi.fn(async (entrada: RequestInfo | URL, _init?: RequestInit) => {
        const url = String(entrada);
        if (url === '/panel/csrf') {
          return token();
        }
        return respuesta({}, 401);
      })
    );

    await expect(api.sesion()).rejects.toMatchObject({ status: 401 });
    expect(sesionExpirada).toBe(true);
  });

  it('reintenta una vez un 403 por token CSRF rotado', async () => {
    let csrf = 0;
    let datos = 0;
    const fetchMock = vi.fn(async (entrada: RequestInfo | URL, _init?: RequestInit) => {
      const url = String(entrada);
      if (url === '/panel/csrf') {
        csrf += 1;
        return respuesta({
          token: `t${csrf}`,
          headerName: 'X-CSRF-TOKEN',
          parameterName: '_csrf'
        });
      }
      datos += 1;
      return datos === 1 ? respuesta({}, 403) : respuesta(TABLERO);
    });
    vi.stubGlobal('fetch', fetchMock);

    await expect(api.tablero()).resolves.toEqual(TABLERO);

    const llamadasDatos = fetchMock.mock.calls.filter(
      ([url]) => String(url) === '/panel/api/datos'
    );
    expect(llamadasDatos).toHaveLength(2);
    expect(cabecera(llamadasDatos[0][1], 'X-CSRF-TOKEN')).toBe('t1');
    expect(cabecera(llamadasDatos[1][1], 'X-CSRF-TOKEN')).toBe('t2');
  });

  it('envía el formulario de acceso con CSRF y detecta el éxito por la URL final', async () => {
    const fetchMock = vi.fn(async (entrada: RequestInfo | URL, _init?: RequestInit) => {
      const url = String(entrada);
      if (url === '/panel/csrf') {
        return token();
      }
      return respuesta({}, 200, 'http://localhost/panel');
    });
    vi.stubGlobal('fetch', fetchMock);

    await expect(api.iniciarSesion('admin', 'admin123')).resolves.toBeUndefined();

    const acceso = fetchMock.mock.calls.find(([url]) => String(url) === '/login');
    expect(acceso).toBeDefined();
    const cuerpo = acceso?.[1]?.body as URLSearchParams;
    expect(cuerpo.get('username')).toBe('admin');
    expect(cuerpo.get('password')).toBe('admin123');
    expect(cuerpo.get('_csrf')).toBe('t1');
  });

  it('considera fallido el acceso si el backend devuelve al login', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (entrada: RequestInfo | URL, _init?: RequestInit) => {
        const url = String(entrada);
        if (url === '/panel/csrf') {
          return token();
        }
        return respuesta({}, 200, 'http://localhost/login?error');
      })
    );

    await expect(api.iniciarSesion('admin', 'incorrecta')).rejects.toBeInstanceOf(
      ApiError
    );
  });
});
