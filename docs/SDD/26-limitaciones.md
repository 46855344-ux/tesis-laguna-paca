# 26 · Limitaciones

Este documento existe para que nadie tenga que descubrir los huecos leyendo el
código. Se separan las limitaciones **del sistema** de las **de la validación**.

## 26.1 Limitaciones científicas

### L-01 · El modelo se entrenó con datos sintéticos

`dataset.py` genera una serie con memoria temporal (AR(1)), ciclo diario y
correlación espacial. Esas propiedades **las elegimos nosotros**. Un R² de 0.9289
demuestra que la arquitectura aprende una dinámica espacio-temporal; **no**
demuestra que prediga la Laguna de Paca.

**Cómo se resuelve:** campaña de muestreo, reentrenamiento y artefacto con
`data_source = field`. Hasta entonces, toda cifra debe llevar la etiqueta
«sintético».

### L-02 · Coordenadas provisionales

`PROVISIONAL_STATIONS` se derivó del polígono de OpenStreetMap (extremos
desplazados 5 % hacia el centroide y verificados con *point-in-polygon*). No son
puntos medidos con GPS en campo. El grafo es geográficamente plausible, no real.

**Cómo se resuelve:** medir las cuatro estaciones y editar `stations.py`; el
script `fetch_laguna.py` regenera el mapa y el modelo usa las distancias nuevas.

### L-03 · Métricas sin intervalo de confianza

Se reporta un R² puntual sobre una partición cronológica. No hay validación
cruzada temporal ni intervalos de confianza. Con una sola serie sintética, más
estadística no cambiaría la conclusión, pero para la tesis conviene reportar
variabilidad entre semillas.

### L-04 · Un solo horizonte de evaluación principal

El criterio se declara a 6 h; también se reportan 1 y 3 h. No se exploraron
horizontes de 12 o 24 h, ni otras variables distintas del pH.

## 26.2 Limitaciones de validación técnica

### L-05 · PostgreSQL de producción no probado en contenedor

No hay PostgreSQL instalado ni Docker. La migración V1–V5 **sí se ejecutó** en un
PostgreSQL 16.9 embebido (Zonky) y se verificaron columnas, índice parcial,
restricciones y los SQLSTATE 23505/23514 (ver `28-registro-de-evidencias.md`,
28.3). Lo que falta es el arranque desde Spring Boot, el contenedor
`postgres:16-alpine` y una configuración de producción (usuarios, extensiones,
copias de seguridad).

**Riesgo:** una diferencia entre el PostgreSQL embebido y el contenedor podría
aparecer en el primer despliegue. **Acción:** levantar el stack con Docker y
repetir la migración allí antes de la defensa. El IT
`PostgresMigrationIT` ya reproduce estas comprobaciones y el CI tiene un job
`backend-postgres`; falta ejecutarlo en un runner y contra el contenedor real.

### L-06 · Docker Compose no ejecutado

`compose.yaml`, los Dockerfiles y los healthchecks están escritos. El archivo se
validó con **Docker Compose v5.6.0** (`config --quiet`, salida 0, perfiles
`integrated` y `mqtt`) y el YAML con PyYAML. No se construyeron las imágenes ni
se levantó el stack: no hay motor Docker en la máquina.

**Riesgo:** dependencias de build no fijadas, tamaño de imagen, healthcheck mal
calibrado. **Acción:** `docker compose build` y `up` en una máquina con Docker.

### L-07 · SonarQube no ejecutado

`sonar-project.properties`, JaCoCo y el job de CI están configurados. No hay
servidor SonarQube, así que no hay análisis estático real. No se reporta ninguna
métrica de Sonar.

### L-08 · CI no ejecutado

`.github/workflows/ci.yml` define backend, IA y firmware. No se ejecutó en
GitHub Actions porque el repositorio no está conectado a un runner en esta
entrega. Los comandos que ejecuta sí se corrieron localmente.

### L-09 · Sin hardware ESP32

El firmware se compila y se prueba en el host con dobles. No se validó:

- calibración real de pH, turbidez, TDS y temperatura;
- consumo y autonomía;
- comportamiento del ADC con el WiFi encendido en la placa;
- TLS;
- OTA;
- resistencia de campo (humedad, temperatura, corrosión).

### L-10 · Sin app Android

`android-app/` está vacío. El contrato REST está listo, pero no hay cliente
móvil, notificaciones push ni tema Material 3.

## 26.3 Limitaciones de seguridad

- **Sin TLS** en backend ni MQTT: no apto para internet.
- **Un solo usuario** sin roles.
- **Sin rate limiting** en el login.
- **Sin escaneo de secretos** automatizado.
- **Clave de Google compartida en el chat**: debe rotarse.
- **Sin auditoría de seguridad externa** ni prueba de penetración.
- **Sin CSP** ni cabeceras de seguridad adicionales.
- **Broker MQTT anónimo** en la configuración de desarrollo.

## 26.4 Limitaciones de operación

- Sin métricas, trazas ni alertas operativas.
- Sin prueba de carga: no se conoce el límite de la instancia.
- Sin rotación de logs configurada.
- Sin respaldo automatizado (solo el comando propuesto).
- Sin SLO/SLA: no hay operación real que medir.

## 26.5 Limitaciones de interfaz

- El refresco de 20 s y el cajón móvil se validaron a mano, no con un navegador
  automatizado.
- Sin auditoría automática de accesibilidad (solo contrastes medidos).
- El mapa de Google no es accesible por teclado; el SVG sí.
- Sin internacionalización: todo en español.
- Sin modo de alto contraste del sistema operativo (solo tema claro/oscuro).

## 26.6 Limitaciones de alcance

- Solo pH tiene regla ECA-Agua; turbidez, temperatura y TDS se registran pero no
  generan alerta.
- Solo se predicen 1, 3 y 6 h de pH.
- No hay multi-tenancy ni múltiples lagunas.
- No hay notificaciones por correo, SMS o push.
- No hay historial de versiones del modelo en la interfaz (solo la versión en
  cada fila).

## 26.7 Lo que **sí** está demostrado

Para no dejar una impresión solo negativa:

1. La ingesta HTTP/MQTT funciona y es idempotente.
2. La regla ECA-Agua es única, determinista y probada en los bordes.
3. El panel opera sin internet y sin JavaScript para lo esencial.
4. El ST-GNN se entrena, se guarda como artefacto y se sirve por HTTP.
5. El backend integra la IA sin acoplarse: si falla, el monitoreo continúa.
6. Las predicciones se evalúan contra mediciones reales y guardan el error.
7. 327 pruebas automatizadas pasan en esta máquina.

## 26.8 Cómo citar estas limitaciones

En la tesis, junto a los resultados:

> Los resultados del modelo corresponden a una serie sintética generada por el
> propio proyecto y a coordenadas provisionales derivadas de OpenStreetMap. Por
> tanto, validan la arquitectura y la integración del sistema, no la precisión
> sobre la Laguna de Paca. La validación de campo queda como trabajo futuro.
