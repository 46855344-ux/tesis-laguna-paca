import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode
} from 'react';
import { api, ApiError, setUnauthorizedHandler } from '../api/client';
import type { SesionPanel } from '../api/types';

type EstadoSesion = 'cargando' | 'autenticado' | 'anonimo' | 'error';

interface AuthContextValue {
  estado: EstadoSesion;
  sesion: SesionPanel | null;
  error: string | null;
  iniciarSesion: (usuario: string, contrasena: string) => Promise<void>;
  cerrarSesion: () => Promise<void>;
  recargarSesion: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [estado, setEstado] = useState<EstadoSesion>('cargando');
  const [sesion, setSesion] = useState<SesionPanel | null>(null);
  const [error, setError] = useState<string | null>(null);

  const cargarSesion = useCallback(async () => {
    setEstado('cargando');
    setError(null);
    try {
      const datos = await api.sesion();
      setSesion(datos);
      setEstado('autenticado');
    } catch (fallo) {
      setSesion(null);
      if (fallo instanceof ApiError && fallo.status === 401) {
        setEstado('anonimo');
      } else {
        setError(fallo instanceof Error ? fallo.message : 'No se pudo comprobar la sesión.');
        setEstado('error');
      }
    }
  }, []);

  useEffect(() => {
    setUnauthorizedHandler(() => {
      setSesion(null);
      setEstado('anonimo');
    });
    void cargarSesion();
    return () => setUnauthorizedHandler(null);
  }, [cargarSesion]);

  const iniciarSesion = useCallback(async (usuario: string, contrasena: string) => {
    await api.iniciarSesion(usuario, contrasena);
    const datos = await api.sesion();
    setSesion(datos);
    setEstado('autenticado');
    setError(null);
  }, []);

  const cerrarSesion = useCallback(async () => {
    try {
      await api.cerrarSesion();
    } finally {
      setSesion(null);
      setEstado('anonimo');
    }
  }, []);

  const valor = useMemo(
    () => ({
      estado,
      sesion,
      error,
      iniciarSesion,
      cerrarSesion,
      recargarSesion: cargarSesion
    }),
    [estado, sesion, error, iniciarSesion, cerrarSesion, cargarSesion]
  );

  return <AuthContext.Provider value={valor}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const contexto = useContext(AuthContext);
  if (!contexto) {
    throw new Error('useAuth debe usarse dentro de AuthProvider');
  }
  return contexto;
}
