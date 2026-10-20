package pe.edu.continental.lagunapaca.panel;

import java.util.List;
import java.util.TreeSet;

/**
 * Números de página que se dibujan en la paginación.
 *
 * <p>Siempre la primera, la última y las vecinas de la actual; la plantilla
 * convierte los saltos en «…». Se calcula en el servidor para que la paginación
 * funcione sin JavaScript y para que el historial y las alertas numeren igual.</p>
 */
final class Paginas {

    private Paginas() {
    }

    static List<Integer> visibles(int pagina, int totalPaginas) {
        if (totalPaginas <= 1) {
            return List.of();
        }
        TreeSet<Integer> paginas = new TreeSet<>();
        paginas.add(1);
        paginas.add(totalPaginas);
        for (int candidata = pagina - 1; candidata <= pagina + 1; candidata++) {
            if (candidata >= 1 && candidata <= totalPaginas) {
                paginas.add(candidata);
            }
        }
        return List.copyOf(paginas);
    }
}
