import type { EstadoMuestra } from '../api/types';

export function etiquetaEstado(estado: EstadoMuestra): string {
  switch (estado) {
    case 'ALERTA':
      return 'Alerta ECA-Agua';
    case 'DENTRO':
      return 'Dentro del rango';
    default:
      return 'Sin datos';
  }
}

export function claseEstado(estado: EstadoMuestra): 'exito' | 'peligro' | 'neutra' {
  switch (estado) {
    case 'ALERTA':
      return 'peligro';
    case 'DENTRO':
      return 'exito';
    default:
      return 'neutra';
  }
}
