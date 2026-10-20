package pe.edu.continental.lagunapaca.panel;

import java.util.List;

/**
 * Modelo de vista de la gestión de alertas: una página de alertas ya formateada,
 * los contadores que resumen la situación y los filtros aplicados.
 *
 * <p>El veredicto ECA-Agua y los motivos vienen tal como los guardó la ingesta.
 * El panel no redecide si hay alerta: solo muestra si alguien ya la atendió.</p>
 */
public record AlertasVista(

        /** Alertas de esta página, de la más reciente a la más antigua. */
        List<Fila> filas,

        FiltrosAlertas filtros,

        /** Página actual, base 1 (lo que ve la persona). */
        int pagina,

        int totalPaginas,

        /** Alertas que cumplen los filtros, no solo las de esta página. */
        long total,

        int desdeFila,

        int hastaFila,

        List<Integer> paginas,

        /** Alertas sin atender, sin límite de antigüedad: una pendiente no caduca. */
        long pendientes,

        /** Alertas atendidas en las últimas 24 h (cuándo se atendieron, no cuándo se midieron). */
        long atendidas24h,

        /** Alertas registradas en las últimas 24 h, el mismo número que muestra el tablero. */
        long alertas24h,

        /** Aviso sobre filtros ignorados o inválidos, o {@code null}. */
        String aviso
) {

    public List<Fila> getFilas() {
        return filas;
    }

    public FiltrosAlertas getFiltros() {
        return filtros;
    }

    public int getPagina() {
        return pagina;
    }

    public int getTotalPaginas() {
        return totalPaginas;
    }

    public long getTotal() {
        return total;
    }

    public int getDesdeFila() {
        return desdeFila;
    }

    public int getHastaFila() {
        return hastaFila;
    }

    public List<Integer> getPaginas() {
        return paginas;
    }

    public long getPendientes() {
        return pendientes;
    }

    public long getAtendidas24h() {
        return atendidas24h;
    }

    public long getAlertas24h() {
        return alertas24h;
    }

    public String getAviso() {
        return aviso;
    }

    public boolean isHayResultados() {
        return total > 0;
    }

    public boolean isHayAviso() {
        return aviso != null && !aviso.isBlank();
    }

    public boolean isHayVariasPaginas() {
        return totalPaginas > 1;
    }

    /** ¿Los filtros esconden algo? Se usa para redactar el estado vacío. */
    public boolean isFiltroPendientes() {
        return FiltrosAlertas.PENDIENTES.equals(filtros.getEstado());
    }

    /**
     * Una alerta con su estado de atención.
     *
     * @param atendida     si alguien ya se ocupó de ella
     * @param atendidaPor  usuario del panel que la atendió, o {@code null}
     * @param atendidaHace antigüedad de la atención en palabras, o {@code null}
     * @param nota         nota libre de quien atendió, o {@code null}
     */
    public record Fila(
            long id,
            String estacion,
            String fecha,
            String hora,
            String hace,
            String ph,
            String turbidez,
            String temperatura,
            String tds,
            List<String> motivos,
            boolean atendida,
            String atendidaPor,
            String atendidaHace,
            String nota
    ) {

        public long getId() {
            return id;
        }

        public String getEstacion() {
            return estacion;
        }

        public String getFecha() {
            return fecha;
        }

        public String getHora() {
            return hora;
        }

        public String getHace() {
            return hace;
        }

        public String getPh() {
            return ph;
        }

        public String getTurbidez() {
            return turbidez;
        }

        public String getTemperatura() {
            return temperatura;
        }

        public String getTds() {
            return tds;
        }

        public List<String> getMotivos() {
            return motivos;
        }

        public boolean isAtendida() {
            return atendida;
        }

        public String getAtendidaPor() {
            return atendidaPor;
        }

        public String getAtendidaHace() {
            return atendidaHace;
        }

        public String getNota() {
            return nota;
        }
    }
}
