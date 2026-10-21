import type {
  AlertasVista,
  HistorialVista,
  MensajeApi,
  SesionPanel,
  Tablero
} from './types';

export class ApiError extends Error {
  readonly status: number;

  constructor(status: number, message: string) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
  }
}

interface CsrfToken {
  token: string;
  headerName: string;
  parameterName: string;
}

let csrfCache: CsrfToken | null = null;
let unauthorizedHandler: (() => void) | null = null;

export function setUnauthorizedHandler(handler: (() => void) | null): void {
  unauthorizedHandler = handler;
}

/** Solo para pruebas: cada caso debe empezar sin un token CSRF heredado. */
export function clearCsrfCache(): void {
  csrfCache = null;
}

async function csrfToken(force = false): Promise<CsrfToken> {
  if (!force && csrfCache) {
    return csrfCache;
  }
  const response = await fetch('/panel/csrf', {
    headers: { Accept: 'application/json' },
    credentials: 'same-origin'
  });
  if (!response.ok) {
    throw new ApiError(response.status, 'No se pudo preparar la protección CSRF.');
  }
  csrfCache = (await response.json()) as CsrfToken;
  return csrfCache;
}

async function errorMessage(response: Response): Promise<string> {
  try {
    const cuerpo = (await response.json()) as { error?: string; message?: string; detail?: string };
    return cuerpo.error || cuerpo.message || cuerpo.detail || `Error HTTP ${response.status}`;
  } catch {
    return `Error HTTP ${response.status}`;
  }
}

async function request<T>(
  url: string,
  init: RequestInit = {},
  reintentar = true
): Promise<T> {
  const csrf = await csrfToken();
  const headers = new Headers(init.headers);
  headers.set('Accept', 'application/json');
  if (init.body && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json');
  }
  headers.set(csrf.headerName, csrf.token);

  const response = await fetch(url, {
    ...init,
    headers,
    credentials: 'same-origin'
  });

  if (response.status === 401) {
    unauthorizedHandler?.();
    throw new ApiError(401, 'La sesión expiró. Vuelve a iniciar sesión.');
  }

  // El token de sesión puede rotar: un 403 por CSRF se reintenta una vez.
  if (response.status === 403 && reintentar) {
    csrfCache = null;
    return request<T>(url, init, false);
  }

  if (!response.ok) {
    throw new ApiError(response.status, await errorMessage(response));
  }

  if (response.status === 204) {
    return undefined as T;
  }
  return (await response.json()) as T;
}

function consulta(parametros: Record<string, string | number | boolean | undefined | null>): string {
  const query = new URLSearchParams();
  Object.entries(parametros).forEach(([clave, valor]) => {
    if (valor !== undefined && valor !== null && valor !== '') {
      query.set(clave, String(valor));
    }
  });
  const texto = query.toString();
  return texto ? `?${texto}` : '';
}

export const api = {
  async sesion(): Promise<SesionPanel> {
    return request<SesionPanel>('/panel/api/sesion');
  },

  async iniciarSesion(usuario: string, contrasena: string): Promise<void> {
    const csrf = await csrfToken(true);
    const cuerpo = new URLSearchParams({
      username: usuario,
      password: contrasena,
      [csrf.parameterName]: csrf.token
    });

    const response = await fetch('/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: cuerpo,
      credentials: 'same-origin',
      redirect: 'follow'
    });

    // El éxito termina en /panel; el fallo, en /login?error.
    const destino = new URL(response.url, window.location.origin);
    csrfCache = null;

    if (!response.ok) {
      throw new ApiError(response.status, 'No se pudo iniciar sesión.');
    }
    if (destino.pathname !== '/panel') {
      throw new ApiError(401, 'Usuario o contraseña incorrectos. Inténtalo de nuevo.');
    }
  },

  async cerrarSesion(): Promise<void> {
    const enviar = async (csrf: CsrfToken) =>
      fetch('/logout', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams({ [csrf.parameterName]: csrf.token }),
        credentials: 'same-origin',
        redirect: 'follow'
      });

    // Se fuerza un token fresco: si la sesión rotó, el cacheado ya no sirve.
    let csrf = await csrfToken(true);
    let response = await enviar(csrf);
    if (response.status === 403) {
      csrf = await csrfToken(true);
      response = await enviar(csrf);
    }
    csrfCache = null;
    if (!response.ok) {
      throw new ApiError(response.status, 'No se pudo cerrar la sesión.');
    }
  },

  async tablero(): Promise<Tablero> {
    return request<Tablero>('/panel/api/datos');
  },

  async historial(
    filtros: {
      estacion?: string;
      desde?: string;
      hasta?: string;
      soloAlertas?: boolean;
      motivo?: string;
      page?: number;
    }
  ): Promise<HistorialVista> {
    return request<HistorialVista>(
      `/panel/api/historial${consulta({
        estacion: filtros.estacion,
        desde: filtros.desde,
        hasta: filtros.hasta,
        soloAlertas: filtros.soloAlertas ? 'true' : undefined,
        motivo: filtros.motivo,
        page: filtros.page
      })}`
    );
  },

  async alertas(
    filtros: {
      estacion?: string;
      estado?: string;
      desde?: string;
      hasta?: string;
      page?: number;
    }
  ): Promise<AlertasVista> {
    return request<AlertasVista>(
      `/panel/api/alertas${consulta({
        estacion: filtros.estacion,
        estado: filtros.estado,
        desde: filtros.desde,
        hasta: filtros.hasta,
        page: filtros.page
      })}`
    );
  },

  async atenderAlerta(id: number, nota: string): Promise<MensajeApi> {
    return request<MensajeApi>(`/panel/api/alertas/${id}/atender`, {
      method: 'POST',
      body: JSON.stringify({ nota })
    });
  },

  async reabrirAlerta(id: number): Promise<MensajeApi> {
    return request<MensajeApi>(`/panel/api/alertas/${id}/reabrir`, {
      method: 'POST'
    });
  },

  async predecir(horizonte: number): Promise<MensajeApi> {
    return request<MensajeApi>('/panel/api/predicciones', {
      method: 'POST',
      body: JSON.stringify({ horizonte })
    });
  }
};
