import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode
} from 'react';
import { Icon } from '../components/Icon';

export type ToastType = 'exito' | 'error' | 'alerta' | 'info';

interface Toast {
  id: number;
  mensaje: string;
  tipo: ToastType;
}

interface ToastContextValue {
  mostrarToast: (mensaje: string, tipo?: ToastType) => void;
}

const ToastContext = createContext<ToastContextValue | null>(null);
const DURACION_MS = 4500;

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([]);
  const siguienteId = useRef(1);
  const temporizadores = useRef(new Map<number, ReturnType<typeof setTimeout>>());

  const cerrar = useCallback((id: number) => {
    setToasts((actuales) => actuales.filter((toast) => toast.id !== id));
    const temporizador = temporizadores.current.get(id);
    if (temporizador) {
      clearTimeout(temporizador);
      temporizadores.current.delete(id);
    }
  }, []);

  const mostrarToast = useCallback(
    (mensaje: string, tipo: ToastType = 'info') => {
      const id = siguienteId.current++;
      setToasts((actuales) => [...actuales, { id, mensaje, tipo }]);
      const temporizador = setTimeout(() => cerrar(id), DURACION_MS);
      temporizadores.current.set(id, temporizador);
    },
    [cerrar]
  );

  useEffect(() => {
    const pendientes = temporizadores.current;
    return () => {
      pendientes.forEach((temporizador) => clearTimeout(temporizador));
      pendientes.clear();
    };
  }, []);

  const valor = useMemo(() => ({ mostrarToast }), [mostrarToast]);
  const icono: Record<ToastType, 'check' | 'error' | 'alertaTriangulo' | 'info'> = {
    exito: 'check',
    error: 'error',
    alerta: 'alertaTriangulo',
    info: 'info'
  };

  return (
    <ToastContext.Provider value={valor}>
      {children}
      <div
        className="contenedor-toasts"
        role="region"
        aria-label="Notificaciones"
        aria-live="polite"
      >
        {toasts.map((toast) => (
          <div
            key={toast.id}
            className={`toast toast--${toast.tipo}`}
            role={toast.tipo === 'error' ? 'alert' : 'status'}
          >
            <span className="toast__icono">
              <Icon name={icono[toast.tipo]} />
            </span>
            <p className="toast__cuerpo">{toast.mensaje}</p>
            <button
              type="button"
              className="toast__cerrar"
              aria-label="Cerrar notificación"
              onClick={() => cerrar(toast.id)}
            >
              <Icon name="cerrar" size={16} />
            </button>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}

export function useToast(): ToastContextValue {
  const contexto = useContext(ToastContext);
  if (!contexto) {
    throw new Error('useToast debe usarse dentro de ToastProvider');
  }
  return contexto;
}
