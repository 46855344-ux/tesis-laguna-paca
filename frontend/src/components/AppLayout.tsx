import {
  useEffect,
  useRef,
  useState,
  type ReactNode
} from 'react';
import { NavLink, useNavigate } from 'react-router-dom';
import { Icon } from './Icon';
import { useAuth } from '../hooks/useAuth';
import { useTheme } from '../hooks/useTheme';
import { useToast } from '../hooks/useToast';

type Seccion = 'panel' | 'metricas' | 'historial' | 'alertas';

interface AppLayoutProps {
  titulo: string;
  seccion: Seccion;
  children: ReactNode;
}

const CLAVE_BARRA = 'laguna-barra';

export function AppLayout({ titulo, seccion, children }: AppLayoutProps) {
  const { sesion, cerrarSesion } = useAuth();
  const { tema, alternarTema } = useTheme();
  const { mostrarToast } = useToast();
  const navigate = useNavigate();

  const [colapsada, setColapsada] = useState(() => {
    try {
      return localStorage.getItem(CLAVE_BARRA) === 'colapsada';
    } catch {
      return false;
    }
  });
  const [menuMovil, setMenuMovil] = useState(false);
  const [menuUsuario, setMenuUsuario] = useState(false);

  const botonMenu = useRef<HTMLButtonElement>(null);
  const barraLateral = useRef<HTMLElement>(null);
  const botonUsuario = useRef<HTMLButtonElement>(null);
  const listaUsuario = useRef<HTMLDivElement>(null);

  useEffect(() => {
    document.body.dataset.barra = colapsada ? 'colapsada' : 'expandida';
    try {
      localStorage.setItem(CLAVE_BARRA, colapsada ? 'colapsada' : 'expandida');
    } catch {
      // Sin persistencia: el estado vale para la visita actual.
    }
  }, [colapsada]);

  useEffect(() => {
    document.body.dataset.menu = menuMovil ? 'abierto' : 'cerrado';
    if (menuMovil) {
      const primero = barraLateral.current?.querySelector<HTMLElement>('a, button');
      primero?.focus();
    }
    return () => {
      document.body.dataset.menu = 'cerrado';
    };
  }, [menuMovil]);

  useEffect(() => {
    if (menuUsuario) {
      listaUsuario.current?.querySelector<HTMLButtonElement>('button')?.focus();
    }
  }, [menuUsuario]);

  useEffect(() => {
    const alHacerClic = (evento: MouseEvent) => {
      if (!menuUsuario) {
        return;
      }
      const objetivo = evento.target as Node;
      if (
        !listaUsuario.current?.contains(objetivo) &&
        !botonUsuario.current?.contains(objetivo)
      ) {
        setMenuUsuario(false);
      }
    };
    document.addEventListener('click', alHacerClic);
    return () => document.removeEventListener('click', alHacerClic);
  }, [menuUsuario]);

  useEffect(() => {
    const alPulsar = (evento: KeyboardEvent) => {
      if (evento.key !== 'Escape') {
        return;
      }
      if (menuUsuario) {
        setMenuUsuario(false);
        botonUsuario.current?.focus();
        return;
      }
      if (menuMovil) {
        setMenuMovil(false);
        botonMenu.current?.focus();
      }
    };
    document.addEventListener('keydown', alPulsar);
    return () => document.removeEventListener('keydown', alPulsar);
  }, [menuUsuario, menuMovil]);

  const salir = async () => {
    try {
      await cerrarSesion();
      navigate('/login?logout', { replace: true });
    } catch (fallo) {
      mostrarToast(
        fallo instanceof Error ? fallo.message : 'No se pudo cerrar la sesión.',
        'error'
      );
    }
  };

  const cerrarMenuMovil = () => {
    setMenuMovil(false);
    botonMenu.current?.focus();
  };

  return (
    <>
      <a className="saltar-contenido" href="#contenido">
        Saltar al contenido
      </a>

      <aside
        ref={barraLateral}
        id="barra-lateral"
        className="barra-lateral"
        aria-label="Menú principal"
      >
        <NavLink className="marca" to="/panel" aria-label="Laguna Paca, ir al panel de monitoreo">
          <span className="marca__icono">
            <Icon name="gota" />
          </span>
          <span className="marca__texto">
            <strong>Laguna Paca</strong>
            <small>Monitoreo ambiental</small>
          </span>
        </NavLink>

        <nav className="navegacion" aria-label="Secciones del panel">
          <p className="navegacion__titulo" id="grupo-monitoreo">
            Monitoreo
          </p>
          <ul aria-labelledby="grupo-monitoreo">
            <li>
              <NavLink
                to="/panel"
                end
                className={({ isActive }) =>
                  `nav-item${isActive ? ' nav-item--activo' : ''}`
                }
                aria-label="Panel de monitoreo"
              >
                <Icon name="panel" />
                <span className="nav-item__texto">Panel</span>
              </NavLink>
            </li>
            <li>
              <NavLink
                to="/panel/metricas"
                className={({ isActive }) =>
                  `nav-item${isActive ? ' nav-item--activo' : ''}`
                }
                aria-label="Métricas de la laguna"
              >
                <Icon name="metricas" />
                <span className="nav-item__texto">Métricas</span>
              </NavLink>
            </li>
            <li>
              <NavLink
                to="/panel/historial"
                className={({ isActive }) =>
                  `nav-item${isActive ? ' nav-item--activo' : ''}`
                }
                aria-label="Historial de muestras"
              >
                <Icon name="historial" />
                <span className="nav-item__texto">Historial</span>
              </NavLink>
            </li>
            <li>
              <NavLink
                to="/panel/alertas"
                className={({ isActive }) =>
                  `nav-item${isActive ? ' nav-item--activo' : ''}`
                }
                aria-label="Gestión de alertas"
              >
                <Icon name="alertas" />
                <span className="nav-item__texto">Alertas</span>
              </NavLink>
            </li>
          </ul>

          <p className="navegacion__titulo" id="grupo-sistema">
            Sistema
          </p>
          <ul aria-labelledby="grupo-sistema">
            <li>
              <span
                className="nav-item nav-item--pronto"
                aria-label="Estaciones de muestreo, disponible próximamente"
              >
                <Icon name="estaciones" />
                <span className="nav-item__texto">Estaciones</span>
                <span className="etiqueta etiqueta--neutra">Pronto</span>
              </span>
            </li>
            <li>
              <span
                className="nav-item nav-item--pronto"
                aria-label="Configuración del sistema, disponible próximamente"
              >
                <Icon name="configuracion" />
                <span className="nav-item__texto">Configuración</span>
                <span className="etiqueta etiqueta--neutra">Pronto</span>
              </span>
            </li>
          </ul>
        </nav>

        <div className="barra-lateral__pie solo-escritorio">
          <button
            type="button"
            className="boton boton--fantasma boton--ancho"
            id="alternar-barra"
            aria-controls="barra-lateral"
            aria-expanded={!colapsada}
            aria-label={colapsada ? 'Expandir el menú lateral' : 'Contraer el menú lateral'}
            title={colapsada ? 'Expandir el menú lateral' : 'Contraer el menú lateral'}
            onClick={() => setColapsada((valor) => !valor)}
          >
            <Icon name="panelIzquierdo" />
            <span className="nav-item__texto">
              {colapsada ? 'Expandir menú' : 'Contraer menú'}
            </span>
          </button>
        </div>
      </aside>

      <div
        className="velo"
        id="velo"
        aria-hidden="true"
        onClick={cerrarMenuMovil}
      />

      <div className="app">
        <header className="barra-superior">
          <button
            ref={botonMenu}
            type="button"
            id="abrir-menu"
            className="boton boton--icono solo-movil"
            aria-controls="barra-lateral"
            aria-expanded={menuMovil}
            aria-label="Abrir el menú de navegación"
            onClick={() => setMenuMovil(true)}
          >
            <Icon name="menu" />
          </button>

          <div className="barra-superior__titulo">
            <h1>{titulo}</h1>
            <p>Laguna de Paca · Junín</p>
          </div>

          <div className="barra-superior__acciones">
            <button
              type="button"
              className="boton boton--icono"
              aria-pressed={tema === 'oscuro'}
              aria-label={tema === 'oscuro' ? 'Cambiar a modo claro' : 'Cambiar a modo oscuro'}
              title={tema === 'oscuro' ? 'Cambiar a modo claro' : 'Cambiar a modo oscuro'}
              onClick={alternarTema}
            >
              <span className="js-icono-sol">
                <Icon name="sol" />
              </span>
              <span className="js-icono-luna">
                <Icon name="luna" />
              </span>
            </button>

            <div className="menu-usuario">
              <button
                ref={botonUsuario}
                type="button"
                id="abrir-menu-usuario"
                className="menu-usuario__disparador"
                aria-haspopup="true"
                aria-expanded={menuUsuario}
                aria-controls="menu-usuario"
                onClick={() => setMenuUsuario((abierto) => !abierto)}
              >
                <span className="avatar" aria-hidden="true">
                  {sesion?.inicial ?? 'A'}
                </span>
                <span className="menu-usuario__datos">
                  <strong>{sesion?.usuario ?? 'admin'}</strong>
                  <small>{sesion?.rol ?? 'Administrador'}</small>
                </span>
                <Icon name="chevron" size={16} />
                <span className="visualmente-oculto">Abrir el menú de usuario</span>
              </button>

              <div className="menu-usuario__lista" id="menu-usuario" hidden={!menuUsuario} ref={listaUsuario}>
                <p className="menu-usuario__encabezado">
                  Sesión iniciada como <strong>{sesion?.usuario ?? 'admin'}</strong>
                </p>
                <button type="button" className="menu-usuario__opcion" onClick={() => void salir()}>
                  <Icon name="salir" size={16} />
                  <span>Cerrar sesión</span>
                </button>
              </div>
            </div>
          </div>
        </header>

        <main id="contenido" className="contenido">
          {children}
        </main>
      </div>
    </>
  );
}
