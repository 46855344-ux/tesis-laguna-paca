import { Icon } from './Icon';

interface EmptyStateProps {
  titulo: string;
  descripcion: string;
  children?: React.ReactNode;
}

export function EmptyState({ titulo, descripcion, children }: EmptyStateProps) {
  return (
    <section className="tarjeta" aria-labelledby="titulo-vacio">
      <div className="estado-vacio">
        <span className="estado-vacio__icono">
          <Icon name="bandeja" />
        </span>
        <h3 id="titulo-vacio">{titulo}</h3>
        <p>{descripcion}</p>
      </div>
      {children ? <div className="estado-vacio__acciones">{children}</div> : null}
    </section>
  );
}
