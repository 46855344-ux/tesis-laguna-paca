"""Tests del simulador de nodo sensor. Solo biblioteca estándar.

Se ejecutan con:  python -m unittest   (desde backend/tools)
"""

import json
import pathlib
import sys
import unittest

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))

import simulate_node as simulator  # noqa: E402  (va después de ajustar sys.path)


class FormatoDecimalTest(unittest.TestCase):
    """El simulador debe hablar el mismo 'dialecto' numérico que el ESP32."""

    def test_descarta_ceros_finales(self):
        self.assertEqual("320", simulator.format_decimal(320.0, 1))
        self.assertEqual("7.2", simulator.format_decimal(7.20, 2))
        self.assertEqual("18", simulator.format_decimal(18.0, 1))
        self.assertEqual("15.4", simulator.format_decimal(15.4, 2))

    def test_redondea_a_los_decimales_pedidos(self):
        self.assertEqual("6.26", simulator.format_decimal(6.256, 2))
        self.assertEqual("6.25", simulator.format_decimal(6.25, 2))
        self.assertEqual("18.5", simulator.format_decimal(18.549, 1))


class BuildPayloadTest(unittest.TestCase):

    def test_reproduce_byte_a_byte_el_payload_del_esp32(self):
        payload = simulator.build_payload("N1", 7.2, 15.4, 18.5, 320.0)

        self.assertEqual(
            '{"node_id":"N1","ph":7.2,"turbidity_ntu":15.4,"temp_c":18.5,"tds_ppm":320}',
            payload)

    def test_es_json_valido_con_las_cinco_claves_del_contrato(self):
        payload = simulator.build_payload("N2", 8.1, 12.0, 17.0, 410.0)
        parsed = json.loads(payload)

        self.assertEqual(
            ["node_id", "ph", "turbidity_ntu", "temp_c", "tds_ppm"],
            list(parsed.keys()))
        self.assertEqual("N2", parsed["node_id"])

    def test_la_precision_coincide_con_el_firmware(self):
        # pH y turbidez a 2 decimales; temperatura y TDS a 1.
        payload = simulator.build_payload("N1", 7.156, 15.449, 18.549, 320.44)
        parsed = json.loads(payload)

        self.assertEqual(7.16, parsed["ph"])
        self.assertEqual(15.45, parsed["turbidity_ntu"])
        self.assertEqual(18.5, parsed["temp_c"])
        self.assertEqual(320.4, parsed["tds_ppm"])


class EscenariosTest(unittest.TestCase):
    """Los escenarios existen para ejercitar las dos ramas de la regla ECA-Agua.

    Ojo: esto NO es una segunda fuente de verdad. Quien decide si hay alerta es
    el backend; aquí solo se generan valores deliberadamente dentro o fuera del
    rango para que la demostración muestre ambos casos.
    """

    def test_normal_se_mantiene_dentro_del_rango(self):
        for reading in simulator.readings("normal", 50, seed=7):
            self.assertGreaterEqual(reading["ph"], 6.5)
            self.assertLessEqual(reading["ph"], 9.0)

    def test_alert_siempre_sale_del_rango(self):
        lecturas = simulator.readings("alert", 20, seed=7)

        altos = [r for r in lecturas if r["ph"] > 9.0]
        bajos = [r for r in lecturas if r["ph"] < 6.5]
        self.assertEqual(20, len(altos) + len(bajos))

    def test_limit_se_queda_exactamente_en_los_bordes(self):
        # 6.5 y 9.0 NO son alerta: es el caso que conviene mostrar en la defensa.
        valores = {r["ph"] for r in simulator.readings("limit", 4)}

        self.assertEqual({6.5, 9.0}, valores)

    def test_escenario_desconocido_falla_claro(self):
        with self.assertRaises(ValueError):
            simulator.readings("inventado", 1)

    def test_es_determinista_para_la_misma_semilla(self):
        self.assertEqual(
            simulator.readings("normal", 5, seed=99),
            simulator.readings("normal", 5, seed=99))


class FlujoHttpTest(unittest.TestCase):
    """El flujo de red se prueba sin abrir sockets reales."""

    def test_concatena_la_url_sin_barras_dobles(self):
        self.assertEqual("http://localhost:8080/api/v1/telemetry",
                         simulator.endpoint("http://localhost:8080/", "telemetry"))
        self.assertEqual("http://localhost:8080/api/v1/auth/token",
                         simulator.endpoint("http://localhost:8080", "auth/token"))


if __name__ == "__main__":
    unittest.main()
