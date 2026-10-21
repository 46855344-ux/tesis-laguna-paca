# 23 · Manual de usuario (panel)

## 23.1 Acceso

1. Abrir `http://localhost:8080`.
2. El sistema redirige a `/login`.
3. Introducir usuario y contraseña (`admin` / `admin123` en desarrollo).
4. Si son incorrectos, vuelve al formulario con un aviso; no dice cuál de los dos
   falló.

La sesión se cierra con «Salir» (POST con CSRF). Cerrar la pestaña no la cierra.

## 23.2 Tablero

- **Indicadores superiores**: muestras de las últimas 24 h, alertas de 24 h,
  estaciones y hora de la última actualización.
- **Tarjetas N1–N4**: última muestra de cada estación, pH, turbidez, temperatura,
  TDS, antigüedad en palabras y badge:
  - verde **Dentro**: el pH está entre 6.5 y 9.0;
  - rojo **Alerta**: fuera de ese rango (se muestra el motivo);
  - gris **Sin datos**: la estación nunca midió.
- **Gráfico**: una línea por estación con los puntos de 24 h. Si hay menos de
  30 minutos de datos, el eje se abre a 30 min para que las horas se lean.
- **Mapa**: contorno real de la laguna; cada punto se colorea con el veredicto y
  enlaza al historial filtrado de esa estación.
- **Predicciones**: ver 23.5.
- El tablero se actualiza cada 20 s. Si la pestaña está oculta, se pausa. El botón
  «Actualizar» fuerza la lectura y confirma con un aviso.

## 23.3 Historial

1. Ir a «Historial».
2. Elegir estación, desde, hasta, «solo alertas» o escribir en el motivo.
3. Pulsar «Buscar».

- Se muestran 20 filas por página, de la más reciente a la más antigua.
- Las fechas se interpretan en **hora de Lima**.
- Si una fecha está mal escrita, el sistema avisa y sigue mostrando el historial.
- Si el rango está invertido, no se aplica y se avisa.
- Los filtros quedan en la URL: se puede guardar o compartir el enlace.

## 23.4 Alertas

1. Ir a «Alertas». Por defecto se listan las **pendientes**.
2. Filtrar por estación, estado o fechas.
3. Pulsar «Atender», escribir una nota (opcional) y confirmar.
4. La alerta pasa a «Atendida» con el usuario y la fecha.
5. «Reabrir» borra la auditoría y la devuelve a pendientes.

**Importante:** atender **no** cambia el veredicto ECA-Agua. Una muestra fuera de
rango sigue estando fuera de rango; la atención solo registra que alguien la
revisó.

## 23.5 Predicciones

1. En el tablero, sección «Predicciones del ST-GNN».
2. Elegir horizonte: 1, 3 o 6 horas.
3. Pulsar «Generar predicción».
4. Aparecen cuatro tarjetas (una por estación) con:
   - pH previsto;
   - horizonte y hora objetivo;
   - origen: «Datos sintéticos» o «Datos de campo»;
   - estado: «Pendiente» o «Evaluada».
5. Cuando llega una medición real cerca de la hora objetivo, la tarjeta pasa a
   «Evaluada» y muestra el valor observado y el error (`predicho − observado`).

Si el servicio de IA no está disponible, aparece un aviso y **no** se crea
ninguna predicción. El resto del panel sigue funcionando.

## 23.6 Mapa

- Sin clave de Google, el mapa es un SVG con el contorno real y funciona sin
  internet.
- Con `GOOGLE_MAPS_API_KEY`, se muestra Google Maps; si la API no carga, queda el
  SVG como respaldo.
- El mapa de Google no es accesible por teclado; las tarjetas y el historial son
  la vía completa.

## 23.7 Tema y accesibilidad

- Botón de tema: claro por defecto, oscuro opcional; se recuerda en el
  navegador.
- Todo se puede usar con teclado: `Tab`, `Enter`, `Escape` para cerrar el menú
  móvil.
- Los iconos siempre van acompañados de texto.
- Si el sistema pide reducir movimiento, las animaciones se desactivan.

## 23.8 Preguntas frecuentes

**¿Por qué una estación dice «Sin datos»?**
Porque nunca llegó una muestra. No se inventa un valor.

**¿Por qué la tarjeta muestra un dato de hace días?**
Porque es la última muestra real. La antigüedad se escribe en palabras para no
confundirla con un dato actual.

**¿Puedo corregir una alerta?**
No se corrige el veredicto. Se puede atender con una nota; el veredicto queda como
evidencia.

**¿Por qué no hay un estado «cerca del límite»?**
Porque el backend no tiene esa regla. Añadirla en el panel crearía una segunda
regla que podría divergir.

**¿La predicción cambia el color de la tarjeta?**
No. El color es el veredicto de la medición.
