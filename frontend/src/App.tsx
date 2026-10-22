import {
  createBrowserRouter,
  Navigate,
  Outlet,
  RouterProvider,
  useLocation
} from 'react-router-dom';
import { Cargando, ErrorPagina } from './components/EstadoCarga';
import { useAuth } from './hooks/useAuth';
import { AlertsPage } from './pages/AlertsPage';
import { DashboardPage } from './pages/DashboardPage';
import { HistoryPage } from './pages/HistoryPage';
import { LoginPage } from './pages/LoginPage';
import { MetricsPage } from './pages/MetricsPage';

function RutaProtegida() {
  const { estado, error, recargarSesion } = useAuth();
  const ubicacion = useLocation();

  if (estado === 'cargando') {
    return (
      <div className="pantalla-cargando" role="status" aria-live="polite">
        <div className="pantalla-cargando__caja">
          <Cargando texto="Comprobando la sesión…" />
        </div>
      </div>
    );
  }

  if (estado === 'anonimo') {
    return <Navigate to="/login" replace state={{ desde: ubicacion.pathname }} />;
  }

  if (estado === 'error') {
    return (
      <div className="pantalla-cargando">
        <ErrorPagina
          mensaje={error ?? 'No se pudo comprobar la sesión.'}
          alReintentar={() => void recargarSesion()}
        />
      </div>
    );
  }

  return <Outlet />;
}

const router = createBrowserRouter([
  { path: '/login', element: <LoginPage /> },
  {
    element: <RutaProtegida />,
    children: [
      { path: '/panel', element: <DashboardPage /> },
      { path: '/panel/metricas', element: <MetricsPage /> },
      { path: '/panel/historial', element: <HistoryPage /> },
      { path: '/panel/alertas', element: <AlertsPage /> }
    ]
  },
  { path: '/', element: <Navigate to="/panel" replace /> },
  { path: '*', element: <Navigate to="/panel" replace /> }
]);

export function App() {
  return <RouterProvider router={router} />;
}
