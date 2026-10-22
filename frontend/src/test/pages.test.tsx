import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { AlertasVista, HistorialVista, Tablero } from '../api/types';
import { HistoryPage } from '../pages/HistoryPage';
import { AlertsPage } from '../pages/AlertsPage';
import { DashboardPage } from '../pages/DashboardPage';
import { MetricsPage } from '../pages/MetricsPage';
import { ToastProvider } from '../hooks/useToast';
import { ThemeProvider } from '../hooks/useTheme';

vi.mock('../hooks/useAuth', () => ({
  useAuth: () => ({
    estado: 'autenticado',
    sesion: {
      usuario: 'admin',
      rol: 'Administrador',
      inicial: 'A',
      estaciones: ['N1', 'N2', 'N3', 'N4'],
      googleMapsApiKey: ''
    },
    error: null,
    iniciarSesion: vi.fn(),
    cerrarSesion: vi.fn(),
    recargarSesion: vi.fn()
  })
}));

vi.mock('../api/client', () => {
  class ApiError extends Error {
    status = 500;
  }
  return {
    ApiError,
    api: {
      tablero: vi.fn(),
      historial: vi.fn(),
      alertas: vi.fn(),
      atenderAlerta: vi.fn(),
      reabrirAlerta: vi.fn(),
      predecir: vi.fn()
    }
  };
});

import { api } from '../api/client';

const HISTORIAL: HistorialVista = {
  filas: [
    {
      id: 7,
      fecha: '21/10/2026',
      hora: '07:00',
      estacion: 'N1',
      ph: '7.20',
      turbidez: '15.4',
      temperatura: '18.5',
      tds: '320',
      estado: 'DENTRO',
      motivos: []
    }
  ],
  filtros: {
    estacion: '',
    desde: '',
    hasta: '',
    soloAlertas: false,
    motivo: '',
    pagina: 0
  },
  pagina: 1,
  totalPaginas: 1,
  total: 1,
  desdeFila: 1,
  hastaFila: 1,
  paginas: [1],
  aviso: null,
  hayResultados: true,
  hayAviso: false,
  hayVariasPaginas: false
};

const ALERTAS: AlertasVista = {
  filas: [
    {
      id: 1,
      estacion: 'N2',
      fecha: '21/10/2026',
      hora: '06:55',
      hace: 'hace 5 minutos',
      ph: '9.60',
      turbidez: '15.4',
      temperatura: '18.5',
      tds: '320',
      motivos: ['pH 9.60 supera el límite superior ECA-Agua (9.0)'],
      atendida: false,
      atendidaPor: null,
      atendidaHace: null,
      nota: null
    }
  ],
  filtros: {
    estacion: '',
    estado: 'pendientes',
    desde: '',
    hasta: '',
    pagina: 0
  },
  pagina: 1,
  totalPaginas: 1,
  total: 1,
  desdeFila: 1,
  hastaFila: 1,
  paginas: [1],
  pendientes: 1,
  atendidas24h: 0,
  alertas24h: 1,
  aviso: null,
  hayResultados: true,
  hayAviso: false,
  hayVariasPaginas: false,
  filtroPendientes: true
};

const TABLERO: Tablero = {
  actualizadoEn: '2026-10-21T12:00:00Z',
  horaActualizacion: '07:00:00',
  muestras: 2,
  alertas: 1,
  estaciones: [
    {
      id: 'N1',
      estado: 'DENTRO',
      etiqueta: 'Dentro del rango',
      clase: 'exito',
      ultima: {
        ph: '7.20',
        turbidez: '15.4',
        temperatura: '18.5',
        tds: '320',
        registradaEn: '2026-10-21T11:00:00Z',
        hora: '06:00',
        fecha: '21/10/2026',
        hace: 'hace 1 hora'
      },
      motivos: [],
      atendida: false,
      atendidaPor: null,
      atendidaHace: null,
      conDatos: true
    },
    {
      id: 'N2',
      estado: 'ALERTA',
      etiqueta: 'Alerta ECA-Agua',
      clase: 'peligro',
      ultima: {
        ph: '9.60',
        turbidez: '15.4',
        temperatura: '18.5',
        tds: '320',
        registradaEn: '2026-10-21T11:55:00Z',
        hora: '06:55',
        fecha: '21/10/2026',
        hace: 'hace 5 minutos'
      },
      motivos: ['pH 9.60 supera el límite superior ECA-Agua (9.0)'],
      atendida: false,
      atendidaPor: null,
      atendidaHace: null,
      conDatos: true
    },
    {
      id: 'N3',
      estado: 'SIN_DATOS',
      etiqueta: 'Sin datos',
      clase: 'neutra',
      ultima: null,
      motivos: [],
      atendida: false,
      atendidaPor: null,
      atendidaHace: null,
      conDatos: false
    },
    {
      id: 'N4',
      estado: 'SIN_DATOS',
      etiqueta: 'Sin datos',
      clase: 'neutra',
      ultima: null,
      motivos: [],
      atendida: false,
      atendidaPor: null,
      atendidaHace: null,
      conDatos: false
    }
  ],
  mapa: {
    viewBox: '0 0 720 460',
    camino: 'M 10 10 L 20 20 L 30 10 Z',
    puntos: [
      {
        id: 'N1',
        x: 100,
        y: 80,
        lat: -11.71,
        lon: -75.5,
        clase: 'exito',
        ph: '7.20',
        titulo: 'N1 · pH 7.20 · Dentro del rango'
      },
      {
        id: 'N2',
        x: 200,
        y: 120,
        lat: -11.72,
        lon: -75.51,
        clase: 'peligro',
        ph: '9.60',
        titulo: 'N2 · pH 9.60 · Alerta ECA-Agua'
      }
    ]
  },
  serie: [],
  predicciones: [],
  estacionesConDatos: 2,
  sinDatos: false
};

function conProveedores(ui: React.ReactNode) {
  return (
    <ThemeProvider>
      <ToastProvider>
        <MemoryRouter>{ui}</MemoryRouter>
      </ToastProvider>
    </ThemeProvider>
  );
}

describe('páginas del panel', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    cleanup();
  });

  it('el historial pinta la tabla y envía los filtros a la API', async () => {
    vi.mocked(api.historial).mockResolvedValue(HISTORIAL);

    const { container } = render(conProveedores(<HistoryPage />));

    expect(await screen.findByText('7.20')).toBeInTheDocument();
    expect(screen.getByText(/Mostrando/)).toHaveTextContent('1');
    expect(container.querySelector('[data-muestra="7"]')).toBeInTheDocument();
    expect(api.historial).toHaveBeenCalledWith(
      expect.objectContaining({ estacion: '', page: 0 })
    );

    fireEvent.change(screen.getByLabelText('Estación'), { target: { value: 'N2' } });
    fireEvent.click(screen.getByRole('button', { name: /Filtrar/ }));

    await waitFor(() =>
      expect(api.historial).toHaveBeenLastCalledWith(
        expect.objectContaining({ estacion: 'N2' })
      )
    );
  });

  it('la gestión de alertas registra la nota y recarga la lista', async () => {
    vi.mocked(api.alertas).mockResolvedValue(ALERTAS);
    vi.mocked(api.atenderAlerta).mockResolvedValue({
      mensaje: 'Alerta atendida. Queda registrada con tu usuario y la hora.'
    });

    render(conProveedores(<AlertsPage />));

    expect(await screen.findByText('9.60')).toBeInTheDocument();
    expect(screen.getByText('pH 9.60 supera el límite superior ECA-Agua (9.0)')).toBeInTheDocument();

    fireEvent.change(screen.getByLabelText('Nota (opcional)'), {
      target: { value: 'Se avisó al guardaparque' }
    });
    fireEvent.click(screen.getByRole('button', { name: /Marcar como atendida/ }));

    await waitFor(() =>
      expect(api.atenderAlerta).toHaveBeenCalledWith(1, 'Se avisó al guardaparque')
    );
    expect(await screen.findByText(/Alerta atendida/)).toBeInTheDocument();
    expect(api.alertas).toHaveBeenCalled();
  });

  it('el tablero muestra las cuatro estaciones y el motivo ECA-Agua persistido', async () => {
    vi.mocked(api.tablero).mockResolvedValue(TABLERO);

    const { container } = render(conProveedores(<DashboardPage />));

    expect(await screen.findByText('Estado de la laguna')).toBeInTheDocument();

    const n1 = container.querySelector('[data-estacion="N1"]') as HTMLElement;
    const n2 = container.querySelector('[data-estacion="N2"]') as HTMLElement;
    expect(within(n1).getByText('7.20')).toBeInTheDocument();
    expect(within(n2).getByText(/supera el límite superior/)).toBeInTheDocument();
    expect(screen.getByText('2 / 4')).toBeInTheDocument();
    expect(screen.getByText(/Todavía no hay predicciones/)).toBeInTheDocument();
  });

  it('las métricas resumen el estado, la tendencia y las alertas recientes', async () => {
    const tableroConSerie: Tablero = {
      ...TABLERO,
      serie: [
        {
          estacion: 'N1',
          instante: '2026-10-21T11:00:00Z',
          ph: 7.2,
          turbidez: 15.4,
          temperatura: 18.5,
          tds: 320
        },
        {
          estacion: 'N2',
          instante: '2026-10-21T11:55:00Z',
          ph: 9.6,
          turbidez: 15.4,
          temperatura: 18.5,
          tds: 320
        }
      ]
    };
    vi.mocked(api.tablero).mockResolvedValue(tableroConSerie);
    vi.mocked(api.alertas).mockResolvedValue(ALERTAS);

    render(conProveedores(<MetricsPage />));

    expect(await screen.findByText('Métricas de la laguna')).toBeInTheDocument();
    expect(screen.getAllByText('50 %').length).toBeGreaterThan(0);
    expect(screen.getByText('2 / 4')).toBeInTheDocument();
    expect(
      screen.getByRole('img', { name: /Distribución de las 4 estaciones/ })
    ).toBeInTheDocument();
    expect(screen.getByText(/supera el límite superior/)).toBeInTheDocument();
    expect(screen.getByText('Pendiente')).toBeInTheDocument();
    expect(api.alertas).toHaveBeenCalledWith({ estado: 'todas', page: 0 });
  });
});
