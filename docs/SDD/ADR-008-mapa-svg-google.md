# ADR-008 · Mapa SVG propio y Google Maps opcional

**Estado:** aceptado · **Fecha:** 2026

## Contexto

El tablero debe ubicar cuatro estaciones sobre la laguna. La demo debe funcionar
sin internet; con internet, un mapa interactivo sería una mejora.

## Decisión

- Contorno **real** de OpenStreetMap (`way 26947746`, simplificado a 140 puntos)
  guardado como GeoJSON en el JAR y dibujado como SVG en el servidor.
- Proyección equirectangular corregida por `cos(latitud)`.
- Sin clave de Google **no se pide nada a Google**.
- Con `GOOGLE_MAPS_API_KEY`, se carga Google Maps; si falla, el SVG queda como
  respaldo.
- La clave se lee del entorno, se marca `@JsonIgnore` y no viaja en
  `/panel/datos`.
- Las coordenadas se importan de `ia-model/.../stations.py`, no se copian.

## Alternativas

- **Leaflet/OpenStreetMap tiles**: requiere internet y descarga teselas; rompe la
  demo offline.
- **Solo Google Maps**: idem, y añade dependencia de una clave y de su cuota.
- **Un esquema dibujado a mano**: no es el contorno real; pierde valor
  geográfico.

## Consecuencias

- El mapa funciona sin conexión y sin JavaScript para lo esencial.
- La clave debe restringirse por referente; una clave expuesta se puede usar a
  costa de la cuenta.
- El mapa de Google no es accesible por teclado; las tarjetas siguen siendo la
  vía completa.
- Si se reemplazan las coordenadas provisionales, se reejecuta
  `fetch_laguna.py` y el mapa y el modelo no pueden discrepar.
