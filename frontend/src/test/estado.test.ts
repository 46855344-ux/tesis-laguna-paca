import { describe, expect, it } from 'vitest';
import { claseEstado, etiquetaEstado } from '../utils/estado';

describe('estado de una muestra', () => {
  it('traduce el veredicto ECA-Agua a texto visible', () => {
    expect(etiquetaEstado('ALERTA')).toBe('Alerta ECA-Agua');
    expect(etiquetaEstado('DENTRO')).toBe('Dentro del rango');
    expect(etiquetaEstado('SIN_DATOS')).toBe('Sin datos');
  });

  it('asigna la clase visual sin inventar estados intermedios', () => {
    expect(claseEstado('ALERTA')).toBe('peligro');
    expect(claseEstado('DENTRO')).toBe('exito');
    expect(claseEstado('SIN_DATOS')).toBe('neutra');
  });
});
