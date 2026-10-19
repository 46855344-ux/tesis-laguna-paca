// Tests del nucleo portable del nodo sensor ESP32.
// Se ejecutan en el host (sin hardware) con:  pio test -e native
//
// Los asserts de double de Unity estan desactivados por defecto. El flag se
// define tambien en platformio.ini porque unity.c se compila por separado.
#ifndef UNITY_INCLUDE_DOUBLE
#define UNITY_INCLUDE_DOUBLE
#endif

#include <unity.h>

#include <cmath>
#include <cstddef>
#include <limits>
#include <string>
#include <vector>

#include "lagunapaca/DecimalFormat.h"
#include "lagunapaca/DeepSleepPolicy.h"
#include "lagunapaca/MovingAverageFilter.h"
#include "lagunapaca/MqttIdentity.h"
#include "lagunapaca/SensorCalibration.h"
#include "lagunapaca/TelemetryNode.h"
#include "lagunapaca/TelemetryPayload.h"
#include "lagunapaca/TelemetrySample.h"

using namespace laguna;

namespace {

// ---------------------------------------------------------------------------
// Dobles de prueba: puertos simulados, porque no hay hardware conectado.
// ---------------------------------------------------------------------------

class FakeAnalogChannel : public AnalogChannel {
public:
    explicit FakeAnalogChannel(double fixed = 0.0) : fixed_(fixed) {
    }

    /** Fuerza una secuencia de lecturas; al agotarse repite el valor fijo. */
    void setSequence(std::initializer_list<double> values) {
        sequence_.assign(values);
        index_ = 0;
    }

    double readVoltage() override {
        ++reads_;
        if (index_ < sequence_.size()) {
            return sequence_[index_++];
        }
        return fixed_;
    }

    int reads() const { return reads_; }

private:
    double fixed_;
    std::vector<double> sequence_;
    std::size_t index_ = 0;
    int reads_ = 0;
};

class FakeTemperatureSensor : public TemperatureSensor {
public:
    explicit FakeTemperatureSensor(double celsius) : celsius_(celsius) {
    }

    double readCelsius() override {
        ++reads_;
        return celsius_;
    }

    int reads() const { return reads_; }

private:
    double celsius_;
    int reads_ = 0;
};

class FakeTransport : public TelemetryTransport {
public:
    bool send(const std::string& payload) override {
        payloads.push_back(payload);
        return result;
    }

    std::vector<std::string> payloads;
    bool result = true;
};

// Calibraciones lineales triviales: dejan escribir tests con numeros redondos.
PhCalibration identityPh() { return PhCalibration(1.0, 0.0); }
TurbidityCalibration identityTurbidity() { return TurbidityCalibration(0.0, 1.0, 0.0); }
TdsCalibration linearTds() { return TdsCalibration(0.0, 0.0, 1000.0, 0.0); }

TelemetryNode makeNode(FakeAnalogChannel& ph,
                       FakeAnalogChannel& turbidity,
                       FakeAnalogChannel& tds,
                       FakeTemperatureSensor& temperature,
                       FakeTransport& transport,
                       const PhCalibration& phCal = identityPh(),
                       const TurbidityCalibration& turbCal = identityTurbidity(),
                       const TdsCalibration& tdsCal = linearTds(),
                       std::size_t samples = 3) {
    TelemetryNodeConfig config{"N1", samples};
    return TelemetryNode(config, ph, turbidity, tds, temperature, phCal, turbCal, tdsCal, transport);
}

const char* kReferencePayload =
        R"({"node_id": "N1", "ph": 7.2, "turbidity_ntu": 15.4, "temp_c": 18.5, "tds_ppm": 320})";

}  // namespace

void setUp(void) {
}

void tearDown(void) {
}

// ---------------------------------------------------------------------------
// Formato decimal: el payload debe ser compacto y sin ruido de punto flotante.
// ---------------------------------------------------------------------------

void test_formatDecimal_descartaCerosFinales() {
    TEST_ASSERT_EQUAL_STRING("7.2", formatDecimal(7.2, 2).c_str());
    TEST_ASSERT_EQUAL_STRING("15.4", formatDecimal(15.4, 2).c_str());
    TEST_ASSERT_EQUAL_STRING("18.5", formatDecimal(18.5, 1).c_str());
    TEST_ASSERT_EQUAL_STRING("320", formatDecimal(320.0, 1).c_str());
}

void test_formatDecimal_casosBorde() {
    TEST_ASSERT_EQUAL_STRING("0", formatDecimal(0.0, 2).c_str());
    // El ruido de la aritmetica flotante no debe filtrarse al JSON.
    TEST_ASSERT_EQUAL_STRING("320", formatDecimal(0.32 * 1000.0, 1).c_str());
    TEST_ASSERT_EQUAL_STRING("6.55", formatDecimal(6.549, 2).c_str());
    // Un cero negativo no debe aparecer como "-0".
    TEST_ASSERT_EQUAL_STRING("0", formatDecimal(-0.001, 2).c_str());
    // maxDecimals fuera de rango se acota.
    TEST_ASSERT_EQUAL_STRING("1", formatDecimal(1.4, -3).c_str());
}

// ---------------------------------------------------------------------------
// Filtro de media movil causal (tesis: w = 3)
// ---------------------------------------------------------------------------

void test_movingAverage_promediaLaVentana() {
    MovingAverageFilter filter(3);

    TEST_ASSERT_DOUBLE_WITHIN(1e-9, 1.0, filter.add(1.0));
    TEST_ASSERT_DOUBLE_WITHIN(1e-9, 1.5, filter.add(2.0));
    TEST_ASSERT_DOUBLE_WITHIN(1e-9, 2.0, filter.add(3.0));
    // Al llenarse la ventana descarta la muestra mas antigua.
    TEST_ASSERT_DOUBLE_WITHIN(1e-9, 3.0, filter.add(4.0));
    TEST_ASSERT_EQUAL_UINT32(3u, static_cast<unsigned>(filter.sampleCount()));
}

void test_movingAverage_resetDescartaLasMuestras() {
    MovingAverageFilter filter(3);
    filter.add(10.0);
    filter.reset();

    TEST_ASSERT_EQUAL_UINT32(0u, static_cast<unsigned>(filter.sampleCount()));
    TEST_ASSERT_DOUBLE_WITHIN(1e-9, 0.0, filter.average());
}

void test_movingAverage_acotaLaVentana() {
    TEST_ASSERT_EQUAL_UINT32(1u, static_cast<unsigned>(MovingAverageFilter(0).windowSize()));
    TEST_ASSERT_EQUAL_UINT32(
            static_cast<unsigned>(MovingAverageFilter::kMaxWindow),
            static_cast<unsigned>(MovingAverageFilter(99).windowSize()));
}

// ---------------------------------------------------------------------------
// pH: PH-4502C calibrado con buffers 4.01 / 7.00 / 10.01
// ---------------------------------------------------------------------------

void test_phCalibration_ajustaPorMinimosCuadrados() {
    // Tensiones decrescentes al subir el pH (comportamiento del PH-4502C).
    PhCalibration calibration = PhCalibration::fromBuffers(3.0, 2.5, 2.0);

    TEST_ASSERT_DOUBLE_WITHIN(0.02, PhCalibration::kBuffer4, calibration.toPh(3.0));
    TEST_ASSERT_DOUBLE_WITHIN(0.02, PhCalibration::kBuffer7, calibration.toPh(2.5));
    TEST_ASSERT_DOUBLE_WITHIN(0.02, PhCalibration::kBuffer10, calibration.toPh(2.0));
}

void test_phCalibration_tensionesIguales_devuelveNeutro() {
    PhCalibration calibration = PhCalibration::fromBuffers(2.5, 2.5, 2.5);

    TEST_ASSERT_DOUBLE_WITHIN(1e-9, 0.0, calibration.slope());
    TEST_ASSERT_DOUBLE_WITHIN(1e-9, PhCalibration::kBuffer7, calibration.toPh(2.5));
}

void test_phCalibration_noRecorta_paraQueLaValidacionDecida() {
    PhCalibration calibration(1.0, 0.0);

    TEST_ASSERT_DOUBLE_WITHIN(1e-9, 15.0, calibration.toPh(15.0));
    TEST_ASSERT_DOUBLE_WITHIN(1e-9, -2.0, calibration.toPh(-2.0));
}

// ---------------------------------------------------------------------------
// Turbidez: TSW-20M (polinomio cuadratico)
// ---------------------------------------------------------------------------

void test_turbidity_calibracionCuadratica() {
    TurbidityCalibration calibration(0.0, 10.0, -5.0);

    TEST_ASSERT_DOUBLE_WITHIN(1e-9, 5.0, calibration.toNtu(1.0));
    // Sin recortar: un valor negativo senala una lectura fuera de rango.
    TEST_ASSERT_DOUBLE_WITHIN(1e-9, -4.0, calibration.toNtu(0.1));
}

void test_turbidity_presetTsw20m() {
    TurbidityCalibration calibration = TurbidityCalibration::tsw20m();

    // A 4.2 V el agua esta practicamente limpia; a 3.0 V, muy turbia.
    TEST_ASSERT_TRUE(calibration.toNtu(4.2) >= 0.0 && calibration.toNtu(4.2) < 10.0);
    TEST_ASSERT_TRUE(calibration.toNtu(3.0) > 100.0);
    // Por debajo del rango valido el polinomio da negativo.
    TEST_ASSERT_TRUE(calibration.toNtu(0.0) < 0.0);
}

// ---------------------------------------------------------------------------
// TDS: Gravity V1.0 con compensacion por temperatura
// ---------------------------------------------------------------------------

void test_tds_presetGravityV1() {
    TdsCalibration calibration = TdsCalibration::gravityV1();

    TEST_ASSERT_DOUBLE_WITHIN(0.01, 879.35, calibration.toPpm(2.0, 25.0));
    TEST_ASSERT_DOUBLE_WITHIN(1e-9, 0.0, calibration.toPpm(0.0, 25.0));
}

void test_tds_compensaPorTemperatura() {
    TdsCalibration calibration = TdsCalibration::gravityV1();

    // Agua mas fria -> mayor correccion, por eso el valor compensado sube.
    TEST_ASSERT_TRUE(calibration.toPpm(2.0, 20.0) > calibration.toPpm(2.0, 25.0));
    TEST_ASSERT_TRUE(calibration.toPpm(2.0, 30.0) < calibration.toPpm(2.0, 25.0));
}

// ---------------------------------------------------------------------------
// Validacion de plausibilidad (espeja las restricciones del backend)
// ---------------------------------------------------------------------------

void test_validateSample_aceptaLaMuestraDeReferencia() {
    TelemetrySample sample{7.2, 15.4, 18.5, 320.0};

    TEST_ASSERT_EQUAL_INT(static_cast<int>(SampleValidity::Valid),
                          static_cast<int>(validateSample("N1", sample)));
}

void test_validateSample_rechazaValoresFueraDeRango() {
    TEST_ASSERT_EQUAL_INT(
            static_cast<int>(SampleValidity::PhOutOfRange),
            static_cast<int>(validateSample("N1", TelemetrySample{15.0, 15.4, 18.5, 320.0})));
    TEST_ASSERT_EQUAL_INT(
            static_cast<int>(SampleValidity::TurbidityNegative),
            static_cast<int>(validateSample("N1", TelemetrySample{7.2, -1.0, 18.5, 320.0})));
    TEST_ASSERT_EQUAL_INT(
            static_cast<int>(SampleValidity::TemperatureOutOfRange),
            static_cast<int>(validateSample("N1", TelemetrySample{7.2, 15.4, 80.0, 320.0})));
    TEST_ASSERT_EQUAL_INT(
            static_cast<int>(SampleValidity::TdsNegative),
            static_cast<int>(validateSample("N1", TelemetrySample{7.2, 15.4, 18.5, -5.0})));
}

void test_validateSample_rechazaValoresNoFinitos() {
    const double notANumber = std::nan("");
    const double infinite = std::numeric_limits<double>::infinity();

    // Un artefacto aritmetico (0/0 o /0) no debe llegar a la nube como dato.
    TEST_ASSERT_EQUAL_INT(
            static_cast<int>(SampleValidity::PhOutOfRange),
            static_cast<int>(validateSample("N1", TelemetrySample{notANumber, 15.4, 18.5, 320.0})));
    TEST_ASSERT_EQUAL_INT(
            static_cast<int>(SampleValidity::TdsNegative),
            static_cast<int>(validateSample("N1", TelemetrySample{7.2, 15.4, 18.5, infinite})));
}

void test_validateSample_rechazaNodeIdInvalido() {    TelemetrySample sample{7.2, 15.4, 18.5, 320.0};

    TEST_ASSERT_EQUAL_INT(static_cast<int>(SampleValidity::NodeIdInvalid),
                          static_cast<int>(validateSample("", sample)));
    TEST_ASSERT_EQUAL_INT(static_cast<int>(SampleValidity::NodeIdInvalid),
                          static_cast<int>(validateSample(nullptr, sample)));
}

void test_isValidNodeId_limiteDe32Caracteres() {
    TEST_ASSERT_TRUE(isValidNodeId("N1"));
    TEST_ASSERT_TRUE(isValidNodeId(std::string(32, 'N').c_str()));
    TEST_ASSERT_FALSE(isValidNodeId(std::string(33, 'N').c_str()));
    TEST_ASSERT_FALSE(isValidNodeId(""));
    TEST_ASSERT_FALSE(isValidNodeId(nullptr));
}

void test_describeValidity_tieneMensajeParaCadaCaso() {
    TEST_ASSERT_TRUE(std::string(describeValidity(SampleValidity::Valid)).size() > 0);
    TEST_ASSERT_TRUE(std::string(describeValidity(SampleValidity::NodeIdInvalid)).size() > 0);
    TEST_ASSERT_TRUE(std::string(describeValidity(SampleValidity::PhOutOfRange)).size() > 0);
    TEST_ASSERT_TRUE(std::string(describeValidity(SampleValidity::TurbidityNegative)).size() > 0);
    TEST_ASSERT_TRUE(std::string(describeValidity(SampleValidity::TemperatureOutOfRange)).size() > 0);
    TEST_ASSERT_TRUE(std::string(describeValidity(SampleValidity::TdsNegative)).size() > 0);
}

// ---------------------------------------------------------------------------
// Payload JSON exacto que espera el backend
// ---------------------------------------------------------------------------

void test_payload_esElContratoExacto() {
    TelemetrySample sample{7.2, 15.4, 18.5, 320.0};

    TEST_ASSERT_EQUAL_STRING(kReferencePayload, buildTelemetryPayload("N1", sample).c_str());
}

void test_payload_usaElNodeIdRecibido() {
    TelemetrySample sample{6.8, 12.0, 17.26, 410.5};
    std::string payload = buildTelemetryPayload("N4", sample);

    TEST_ASSERT_EQUAL_STRING(
            R"({"node_id": "N4", "ph": 6.8, "turbidity_ntu": 12, "temp_c": 17.3, "tds_ppm": 410.5})",
            payload.c_str());
}

// ---------------------------------------------------------------------------
// TelemetryNode: orquesta un ciclo de medicion completo
// ---------------------------------------------------------------------------

void test_node_enviaElPayloadDeReferencia() {
    FakeAnalogChannel ph(7.2);
    FakeAnalogChannel turbidity(15.4);
    FakeAnalogChannel tds(0.32);
    FakeTemperatureSensor temperature(18.5);
    FakeTransport transport;

    TelemetryNode node = makeNode(ph, turbidity, tds, temperature, transport);

    TEST_ASSERT_EQUAL_INT(static_cast<int>(TelemetryNode::Outcome::Sent),
                          static_cast<int>(node.runOnce()));
    TEST_ASSERT_EQUAL_UINT32(1u, static_cast<unsigned>(transport.payloads.size()));
    TEST_ASSERT_EQUAL_STRING(kReferencePayload, transport.payloads.at(0).c_str());
    TEST_ASSERT_EQUAL_STRING(kReferencePayload, node.lastPayload().c_str());
}

void test_node_leeCadaCanalAnalogicoWVeces() {
    FakeAnalogChannel ph(7.2);
    FakeAnalogChannel turbidity(15.4);
    FakeAnalogChannel tds(0.32);
    FakeTemperatureSensor temperature(18.5);
    FakeTransport transport;

    TelemetryNode node = makeNode(ph, turbidity, tds, temperature, transport, identityPh(),
                                  identityTurbidity(), linearTds(), 3);
    node.runOnce();

    TEST_ASSERT_EQUAL_INT(3, ph.reads());
    TEST_ASSERT_EQUAL_INT(3, turbidity.reads());
    TEST_ASSERT_EQUAL_INT(3, tds.reads());
    // El DS18B20 es digital: una sola lectura por ciclo.
    TEST_ASSERT_EQUAL_INT(1, temperature.reads());
}

void test_node_aplicaLaMediaMovilCausal() {
    FakeAnalogChannel ph(7.2);
    ph.setSequence({1.0, 2.0, 3.0});
    FakeAnalogChannel turbidity(15.4);
    FakeAnalogChannel tds(0.32);
    FakeTemperatureSensor temperature(18.5);
    FakeTransport transport;

    TelemetryNode node = makeNode(ph, turbidity, tds, temperature, transport);
    node.runOnce();

    TEST_ASSERT_DOUBLE_WITHIN(1e-9, 2.0, node.lastSample().ph);
}

void test_node_descartaUnaMuestraImplausible() {
    FakeAnalogChannel ph(15.0);  // fuera del rango 0-14
    FakeAnalogChannel turbidity(15.4);
    FakeAnalogChannel tds(0.32);
    FakeTemperatureSensor temperature(18.5);
    FakeTransport transport;

    TelemetryNode node = makeNode(ph, turbidity, tds, temperature, transport);

    TEST_ASSERT_EQUAL_INT(static_cast<int>(TelemetryNode::Outcome::InvalidSample),
                          static_cast<int>(node.runOnce()));
    TEST_ASSERT_EQUAL_INT(static_cast<int>(SampleValidity::PhOutOfRange),
                          static_cast<int>(node.lastValidity()));
    // No se debe transmitir basura a la nube.
    TEST_ASSERT_EQUAL_UINT32(0u, static_cast<unsigned>(transport.payloads.size()));
}

void test_node_informaCuandoElTransporteFalla() {
    FakeAnalogChannel ph(7.2);
    FakeAnalogChannel turbidity(15.4);
    FakeAnalogChannel tds(0.32);
    FakeTemperatureSensor temperature(18.5);
    FakeTransport transport;
    transport.result = false;

    TelemetryNode node = makeNode(ph, turbidity, tds, temperature, transport);

    TEST_ASSERT_EQUAL_INT(static_cast<int>(TelemetryNode::Outcome::TransportFailed),
                          static_cast<int>(node.runOnce()));
    TEST_ASSERT_EQUAL_UINT32(1u, static_cast<unsigned>(transport.payloads.size()));
}

// ---------------------------------------------------------------------------
// Politica de Deep Sleep
// ---------------------------------------------------------------------------

void test_deepSleep_descuentaElTiempoActivo() {
    DeepSleepPolicy policy(300, 10);

    TEST_ASSERT_EQUAL_UINT32(299u, policy.sleepSeconds(1200));
    TEST_ASSERT_EQUAL_UINT64(299000000ull, policy.sleepMicros(1200));
}

void test_deepSleep_respetaElMinimoDeSeguridad() {
    // Un ciclo activo mas largo que el intervalo no puede producir 0 segundos.
    DeepSleepPolicy policy(300, 10);
    TEST_ASSERT_EQUAL_UINT32(10u, policy.sleepSeconds(400000));

    // Un intervalo menor al minimo se eleva al minimo.
    DeepSleepPolicy shortInterval(5, 10);
    TEST_ASSERT_EQUAL_UINT32(10u, shortInterval.sleepSeconds(0));
}

// ---------------------------------------------------------------------------
// Identidad MQTT del nodo
// ---------------------------------------------------------------------------
// MQTT 3.1.1 obliga al broker a aceptar client ids de 1 a 23 bytes, y varios
// brokers reales rechazan los mas largos. Ademas el id tiene que ser unico por
// placa: dos nodos con el mismo id se expulsan mutuamente al conectarse.

/** MAC de ejemplo (los 4 bytes bajos son la parte unica de la placa). */
constexpr std::uint64_t kSampleChipId = 0x244CAB123456ull;

void test_mqttClientId_respetaElLimiteDe23Bytes() {
    // El node id mas largo que admite el backend son 32 caracteres.
    const std::string longestNodeId(32, 'N');
    const std::string clientId = buildMqttClientId(longestNodeId.c_str(), kSampleChipId);

    TEST_ASSERT_TRUE(clientId.size() <= kMaxClientIdLength);
    TEST_ASSERT_EQUAL_UINT32(23u, static_cast<unsigned>(clientId.size()));
    TEST_ASSERT_TRUE(clientId.size() > 0);
}

void test_mqttClientId_esDeterministaYUnicoPorPlaca() {
    const std::string clientId = buildMqttClientId("N1", kSampleChipId);
    const std::string repeated = buildMqttClientId("N1", kSampleChipId);
    const std::string otherChip = buildMqttClientId("N1", 0x244CAB999999ull);
    const std::string otherNode = buildMqttClientId("N2", kSampleChipId);

    TEST_ASSERT_EQUAL_STRING(clientId.c_str(), repeated.c_str());
    TEST_ASSERT_TRUE(clientId != otherChip);
    TEST_ASSERT_TRUE(clientId != otherNode);
}

void test_mqttClientId_incluyeElNodeIdCuandoCabe() {
    const std::string clientId = buildMqttClientId("N1", kSampleChipId);

    // 0x244CAB123456 -> los 4 bytes bajos son ab123456.
    TEST_ASSERT_EQUAL_STRING("lp-ab123456-N1", clientId.c_str());
}

void test_mqttClientId_soloUsaCaracteresSeguros() {
    const std::string clientId = buildMqttClientId("N 1/#", kSampleChipId);

    TEST_ASSERT_TRUE(clientId.size() > 0);
    for (char character : clientId) {
        const bool safe = (character >= 'a' && character <= 'z')
                || (character >= 'A' && character <= 'Z')
                || (character >= '0' && character <= '9')
                || character == '-';
        TEST_ASSERT_TRUE(safe);
    }
}

void test_mqttClientId_sinNodeIdNoQuedaVacio() {
    const std::string fromEmpty = buildMqttClientId("", kSampleChipId);
    const std::string fromNull = buildMqttClientId(nullptr, kSampleChipId);

    // Un client id vacio solo vale con clean session; no queremos depender de eso.
    TEST_ASSERT_TRUE(fromEmpty.size() > 0);
    TEST_ASSERT_TRUE(fromNull.size() > 0);
    TEST_ASSERT_TRUE(fromEmpty.back() != '-');
    TEST_ASSERT_TRUE(fromNull.back() != '-');
}

// ---------------------------------------------------------------------------

int main(int, char**) {
    UNITY_BEGIN();

    RUN_TEST(test_formatDecimal_descartaCerosFinales);
    RUN_TEST(test_formatDecimal_casosBorde);

    RUN_TEST(test_movingAverage_promediaLaVentana);
    RUN_TEST(test_movingAverage_resetDescartaLasMuestras);
    RUN_TEST(test_movingAverage_acotaLaVentana);

    RUN_TEST(test_phCalibration_ajustaPorMinimosCuadrados);
    RUN_TEST(test_phCalibration_tensionesIguales_devuelveNeutro);
    RUN_TEST(test_phCalibration_noRecorta_paraQueLaValidacionDecida);

    RUN_TEST(test_turbidity_calibracionCuadratica);
    RUN_TEST(test_turbidity_presetTsw20m);

    RUN_TEST(test_tds_presetGravityV1);
    RUN_TEST(test_tds_compensaPorTemperatura);

    RUN_TEST(test_validateSample_aceptaLaMuestraDeReferencia);
    RUN_TEST(test_validateSample_rechazaValoresFueraDeRango);
    RUN_TEST(test_validateSample_rechazaValoresNoFinitos);
    RUN_TEST(test_validateSample_rechazaNodeIdInvalido);
    RUN_TEST(test_isValidNodeId_limiteDe32Caracteres);
    RUN_TEST(test_describeValidity_tieneMensajeParaCadaCaso);

    RUN_TEST(test_payload_esElContratoExacto);
    RUN_TEST(test_payload_usaElNodeIdRecibido);

    RUN_TEST(test_node_enviaElPayloadDeReferencia);
    RUN_TEST(test_node_leeCadaCanalAnalogicoWVeces);
    RUN_TEST(test_node_aplicaLaMediaMovilCausal);
    RUN_TEST(test_node_descartaUnaMuestraImplausible);
    RUN_TEST(test_node_informaCuandoElTransporteFalla);

    RUN_TEST(test_deepSleep_descuentaElTiempoActivo);
    RUN_TEST(test_deepSleep_respetaElMinimoDeSeguridad);

    RUN_TEST(test_mqttClientId_respetaElLimiteDe23Bytes);
    RUN_TEST(test_mqttClientId_esDeterministaYUnicoPorPlaca);
    RUN_TEST(test_mqttClientId_incluyeElNodeIdCuandoCabe);
    RUN_TEST(test_mqttClientId_soloUsaCaracteresSeguros);
    RUN_TEST(test_mqttClientId_sinNodeIdNoQuedaVacio);

    return UNITY_END();
}
