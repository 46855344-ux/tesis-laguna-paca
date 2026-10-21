import type { EstadoMuestra } from '../api/types';
import { claseEstado, etiquetaEstado } from '../utils/estado';

interface StatusBadgeProps {
  estado: EstadoMuestra;
  etiqueta?: string;
  clase?: string;
}

export function StatusBadge({ estado, etiqueta, clase }: StatusBadgeProps) {
  const claseFinal = clase ?? claseEstado(estado);
  return (
    <span className={`etiqueta etiqueta--${claseFinal}`}>
      <span className="etiqueta__punto" />
      <span>{etiqueta ?? etiquetaEstado(estado)}</span>
    </span>
  );
}
