import { useEffect, useState, type FormEvent } from 'react';
import { Navigate, useNavigate, useSearchParams } from 'react-router-dom';
import { Icon } from '../components/Icon';
import { useAuth } from '../hooks/useAuth';
import { useTheme } from '../hooks/useTheme';

export function LoginPage() {
  const { estado, iniciarSesion } = useAuth();
  const { tema, alternarTema } = useTheme();
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const [usuario, setUsuario] = useState('');
  const [contrasena, setContrasena] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [errores, setErrores] = useState({ usuario: false, contrasena: false });
  const [enviando, setEnviando] = useState(false);

  useEffect(() => {
    document.title = 'Iniciar sesión · Laguna Paca';
  }, []);

  if (estado === 'autenticado') {
    return <Navigate to="/panel" replace />;
  }

  const enviar = async (evento: FormEvent) => {
    evento.preventDefault();
    const vacioUsuario = usuario.trim() === '';
    const vacioContrasena = contrasena.trim() === '';
    setErrores({ usuario: vacioUsuario, contrasena: vacioContrasena });
    if (vacioUsuario || vacioContrasena) {
      return;
    }

    setEnviando(true);
    setError(null);
    try {
      await iniciarSesion(usuario.trim(), contrasena);
      navigate('/panel', { replace: true });
    } catch (fallo) {
      setError(
        fallo instanceof Error
          ? fallo.message
          : 'No se pudo iniciar sesión. Inténtalo de nuevo.'
      );
    } finally {
      setEnviando(false);
    }
  };

  const avisoExterno =
    params.get('error') !== null
      ? 'Usuario o contraseña incorrectos. Inténtalo de nuevo.'
      : params.get('logout') !== null
        ? 'Cerraste sesión correctamente.'
        : params.get('sesion') === 'expirada'
          ? 'La sesión expiró. Vuelve a iniciar sesión.'
          : null;

  return (
    <main className="pagina-login">
      <button
        type="button"
        className="boton boton--icono tema-flotante"
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

      <section className="tarjeta-login" aria-labelledby="titulo-login">
        <div className="tarjeta-login__marca">
          <span className="marca__icono">
            <Icon name="gota" />
          </span>
          <h1 id="titulo-login">Laguna Paca</h1>
          <p>Panel de monitoreo de la calidad del agua</p>
        </div>

        {avisoExterno && params.get('error') !== null ? (
          <p className="aviso aviso--error" role="alert">
            <Icon name="candado" />
            <span>{avisoExterno}</span>
          </p>
        ) : null}
        {avisoExterno && params.get('error') === null ? (
          <p className="aviso aviso--info" role="status">
            <Icon name="escudo" />
            <span>{avisoExterno}</span>
          </p>
        ) : null}
        {error ? (
          <p className="aviso aviso--error" role="alert">
            <Icon name="candado" />
            <span>{error}</span>
          </p>
        ) : null}

        <form onSubmit={(evento) => void enviar(evento)} noValidate>
          <div className="campo">
            <label className="campo__etiqueta" htmlFor="usuario">
              Usuario
            </label>
            <input
              className="campo__control"
              id="usuario"
              name="username"
              type="text"
              required
              autoFocus
              autoComplete="username"
              placeholder="admin"
              aria-invalid={errores.usuario}
              aria-describedby="error-usuario"
              value={usuario}
              onChange={(evento) => setUsuario(evento.target.value)}
            />
            <p
              className="campo__error"
              id="error-usuario"
              data-visible={errores.usuario}
            >
              Escribe tu usuario.
            </p>
          </div>

          <div className="campo">
            <label className="campo__etiqueta" htmlFor="contrasena">
              Contraseña
            </label>
            <input
              className="campo__control"
              id="contrasena"
              name="password"
              type="password"
              required
              autoComplete="current-password"
              placeholder="Tu contraseña"
              aria-invalid={errores.contrasena}
              aria-describedby="error-contrasena"
              value={contrasena}
              onChange={(evento) => setContrasena(evento.target.value)}
            />
            <p
              className="campo__error"
              id="error-contrasena"
              data-visible={errores.contrasena}
            >
              Escribe tu contraseña.
            </p>
          </div>

          <button
            type="submit"
            className="boton boton--primario boton--bloque"
            disabled={enviando}
          >
            <Icon name="candado" />
            <span>{enviando ? 'Entrando…' : 'Iniciar sesión'}</span>
          </button>
        </form>

        <p className="tarjeta-login__pie">
          Acceso restringido al personal autorizado del monitoreo.
        </p>
      </section>
    </main>
  );
}
