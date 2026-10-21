import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import type { MapaLaguna } from '../api/types';
import { LagoonMap } from '../components/LagoonMap';
import { LineChart } from '../components/LineChart';
import { Pagination } from '../components/Pagination';
import { StatusBadge } from '../components/StatusBadge';
import { ThemeProvider } from '../hooks/useTheme';

const MAPA: MapaLaguna = {
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
      titulo: 'N1 · pH 7.20 · Dentro del rango · hace 2 minutos'
    },
    {
      id: 'N2',
      x: 200,
      y: 120,
      lat: -11.72,
      lon: -75.51,
      clase: 'peligro',
      ph: '9.60',
      titulo: 'N2 · pH 9.60 · Alerta ECA-Agua · hace 1 minuto'
    }
  ]
};

describe('componentes de presentación', () => {
  it('el badge usa el veredicto persistido, no recalcula el pH', () => {
    const { container } = render(<StatusBadge estado="ALERTA" />);
    expect(screen.getByText('Alerta ECA-Agua')).toBeInTheDocument();
    expect(container.querySelector('.etiqueta--peligro')).toBeInTheDocument();
  });

  it('dibuja el contorno y enlaza cada estación a su historial', () => {
    render(
      <ThemeProvider>
        <MemoryRouter>
          <LagoonMap mapa={MAPA} apiKey="" />
        </MemoryRouter>
      </ThemeProvider>
    );

    expect(
      screen.getByRole('img', { name: 'Mapa de la Laguna de Paca con las estaciones N1 a N4' })
    ).toBeInTheDocument();
    expect(screen.getByText('N1')).toBeInTheDocument();
    expect(screen.getByText('N2')).toBeInTheDocument();

    const enlaces = screen.getAllByRole('link');
    expect(enlaces[0]).toHaveAttribute('href', '/panel/historial?estacion=N1');
    expect(enlaces[1]).toHaveAttribute('href', '/panel/historial?estacion=N2');
  });

  it('sin clave no ofrece el contenedor de Google', () => {
    render(
      <ThemeProvider>
        <MemoryRouter>
          <LagoonMap mapa={MAPA} apiKey="" />
        </MemoryRouter>
      </ThemeProvider>
    );

    expect(document.querySelector('#mapa-google')).not.toBeInTheDocument();
    expect(screen.queryByText(/Con conexión se muestra el mapa de Google/)).not.toBeInTheDocument();
  });

  it('la paginación marca la página actual y conserva el enlace recibido', () => {
    const { container } = render(
      <MemoryRouter>
        <Pagination
          pagina={2}
          totalPaginas={3}
          paginas={[1, 2, 3]}
          enlace={(numero) => `/panel/historial?page=${numero - 1}`}
          etiqueta="Paginación del historial"
        />
      </MemoryRouter>
    );

    expect(screen.getByText('2')).toHaveAttribute('aria-current', 'page');
    expect(container.querySelector('a[href="/panel/historial?page=0"]')).toBeInTheDocument();
    expect(container.querySelector('a[href="/panel/historial?page=2"]')).toBeInTheDocument();
  });

  it('el gráfico dibuja una línea por estación y no inventa puntos', () => {
    render(
      <LineChart
        puntos={[
          {
            estacion: 'N1',
            instante: '2026-10-21T10:00:00Z',
            ph: 7.2,
            turbidez: 15,
            temperatura: 18,
            tds: 320
          },
          {
            estacion: 'N1',
            instante: '2026-10-21T11:00:00Z',
            ph: 7.4,
            turbidez: 16,
            temperatura: 19,
            tds: 322
          }
        ]}
        estaciones={['N1']}
        variable="ph"
      />
    );

    expect(screen.getByRole('img', { name: /Serie temporal de pH/ })).toBeInTheDocument();
    expect(document.querySelectorAll('polyline')).toHaveLength(1);
    expect(screen.getByText('N1')).toBeInTheDocument();
  });

  it('sin muestras el gráfico muestra un estado vacío honesto', () => {
    render(<LineChart puntos={[]} estaciones={['N1']} variable="ph" />);
    expect(
      screen.getByText('Todavía no hay muestras en las últimas 24 horas.')
    ).toBeInTheDocument();
  });
});
