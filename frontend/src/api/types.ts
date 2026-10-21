export type EstadoMuestra = 'SIN_DATOS' | 'DENTRO' | 'ALERTA';

export interface SesionPanel {
  usuario: string;
  rol: string;
  inicial: string;
  estaciones: string[];
  googleMapsApiKey: string;
}

export interface MuestraPanel {
  ph: string;
  turbidez: string;
  temperatura: string;
  tds: string;
  registradaEn: string;
  hora: string;
  fecha: string;
  hace: string;
}

export interface EstacionPanel {
  id: string;
  estado: EstadoMuestra;
  etiqueta: string;
  clase: 'exito' | 'peligro' | 'neutra';
  ultima: MuestraPanel | null;
  motivos: string[];
  atendida: boolean;
  atendidaPor: string | null;
  atendidaHace: string | null;
  conDatos: boolean;
}

export interface PuntoMapa {
  id: string;
  x: number;
  y: number;
  lat: number;
  lon: number;
  clase: 'exito' | 'peligro' | 'neutra';
  ph: string | null;
  titulo: string;
}

export interface MapaLaguna {
  viewBox: string;
  camino: string;
  puntos: PuntoMapa[];
}

export interface PuntoSerie {
  estacion: string;
  instante: string;
  ph: number;
  turbidez: number;
  temperatura: number;
  tds: number;
}

export interface PrediccionPanel {
  estacion: string;
  horizonte: number;
  phPrevisto: string;
  objetivo: string;
  generadoHace: string;
  modelo: string;
  origen: string;
  estado: string;
  phObservado: string | null;
  error: string | null;
  evaluada: boolean;
}

export interface Tablero {
  actualizadoEn: string;
  horaActualizacion: string;
  muestras: number;
  alertas: number;
  estaciones: EstacionPanel[];
  mapa: MapaLaguna;
  serie: PuntoSerie[];
  predicciones: PrediccionPanel[];
  estacionesConDatos: number;
  sinDatos: boolean;
}

export interface FiltrosHistorial {
  estacion: string;
  desde: string;
  hasta: string;
  soloAlertas: boolean;
  motivo: string;
  pagina: number;
}

export interface FilaHistorial {
  id: number;
  fecha: string;
  hora: string;
  estacion: string;
  ph: string;
  turbidez: string;
  temperatura: string;
  tds: string;
  estado: EstadoMuestra;
  motivos: string[];
}

export interface HistorialVista {
  filas: FilaHistorial[];
  filtros: FiltrosHistorial;
  pagina: number;
  totalPaginas: number;
  total: number;
  desdeFila: number;
  hastaFila: number;
  paginas: number[];
  aviso: string | null;
  hayResultados: boolean;
  hayAviso: boolean;
  hayVariasPaginas: boolean;
}

export interface FiltrosAlertas {
  estacion: string;
  estado: 'pendientes' | 'atendidas' | 'todas';
  desde: string;
  hasta: string;
  pagina: number;
}

export interface FilaAlerta {
  id: number;
  estacion: string;
  fecha: string;
  hora: string;
  hace: string;
  ph: string;
  turbidez: string;
  temperatura: string;
  tds: string;
  motivos: string[];
  atendida: boolean;
  atendidaPor: string | null;
  atendidaHace: string | null;
  nota: string | null;
}

export interface AlertasVista {
  filas: FilaAlerta[];
  filtros: FiltrosAlertas;
  pagina: number;
  totalPaginas: number;
  total: number;
  desdeFila: number;
  hastaFila: number;
  paginas: number[];
  pendientes: number;
  atendidas24h: number;
  alertas24h: number;
  aviso: string | null;
  hayResultados: boolean;
  hayAviso: boolean;
  hayVariasPaginas: boolean;
  filtroPendientes: boolean;
}

export type VariableSerie = 'ph' | 'turbidez' | 'temperatura' | 'tds';

export interface MensajeApi {
  mensaje: string;
}

export interface ErrorApi {
  error: string;
}
