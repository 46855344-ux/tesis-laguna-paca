# 27 · Glosario

| Término | Definición |
|---|---|
| **ECA-Agua** | Estándares de Calidad Ambiental para Agua (Perú). Categoría 4-E1: conservación del ambiente acuático. |
| **ST-GNN** | *Spatio-Temporal Graph Neural Network*: red neuronal que combina tiempo y grafo. |
| **TCN** | *Temporal Convolutional Network*: convoluciones causales con dilatación exponencial. |
| **GCN** | *Graph Convolutional Network*: propaga información entre nodos vecinos. |
| **Atención** | Mecanismo que pondera la influencia de cada vecino; aquí enmascarado por la adyacencia. |
| **Adyacencia** | Matriz de pesos entre estaciones; se construye con kernel gaussiano y poda MST ∪ KNN. |
| **Kernel gaussiano** | `exp(-d² / (2σ²))`: convierte distancia en peso de relación. |
| **MST** | Árbol de expansión mínima; garantiza que el grafo nunca quede desconectado. |
| **KNN** | *k* vecinos más cercanos; añade relaciones locales que el MST no incluye. |
| **R²** | Coeficiente de determinación: proporción de varianza explicada. |
| **RMSE** | Raíz del error cuadrático medio, en unidades de la variable. |
| **MAE** | Error absoluto medio, en unidades de la variable. |
| **Persistencia** | Línea base trivial: predecir que el pH será el último observado. |
| **Ventana / `input_len`** | Número de muestras por estación que el modelo recibe (24 h). |
| **Horizonte** | Horas hacia adelante que se predicen (1, 3, 6). |
| **Ventana objetivo** | La que empieza justo después de la de entrada; evita fuga de información. |
| **Partición cronológica con hueco** | Entrenamiento y prueba separados en el tiempo, con un hueco del tamaño de la ventana. |
| **Artefacto** | Archivo `.pt` con pesos, configuración, escalador, grafo y métricas. |
| **`model_run_id`** | UUID que agrupa las cuatro predicciones de una misma inferencia. |
| **`data_source`** | Origen de los datos de entrenamiento: `synthetic` o `field`. |
| **Idempotencia** | Repetir una operación no cambia el resultado: un reintento no duplica la muestra. |
| **`sample_id`** | Identificador único de muestra que envía el nodo. |
| **JWT** | *JSON Web Token*: credencial firmada para la API. |
| **CSRF** | *Cross-Site Request Forgery*: ataque que se mitiga con un token por formulario. |
| **BCrypt** | Función de hash de contraseñas con sal y costo configurable. |
| **Sesión / `JSESSIONID`** | Cookie que identifica la sesión del panel. |
| **QoS** | *Quality of Service* de MQTT; QoS 1 garantiza al menos una entrega. |
| **PUBACK** | Confirmación de MQTT para QoS 1. |
| **Deep Sleep** | Modo de bajo consumo del ESP32 que reinicia la CPU al despertar. |
| **RTC** | Memoria del ESP32 que sobrevive al Deep Sleep. |
| **`bootId`** | Valor aleatorio que identifica un arranque en frío. |
| **Estación provisional** | Coordenada derivada de OSM, no medida en campo. |
| **H2** | Base de datos en memoria usada en tests y en el perfil `demo`. |
| **Flyway** | Herramienta de migraciones versionadas. |
| **JaCoCo** | Herramienta de cobertura de código Java. |
| **SonarQube** | Plataforma de análisis estático; configurada, no ejecutada aquí. |
| **SPA** | Aplicación de una sola página; el panel React carga una vez y navega sin recargar. |
| **Vite** | Empaquetador y servidor de desarrollo del frontend. |
| **Vitest** | Ejecutor de pruebas del frontend, integrado con Vite. |
| **SVG** | Gráfico vectorial; el mapa y el gráfico se dibujan a mano. |
| **ODbL** | Licencia de OpenStreetMap que exige atribución y compartir igual. |
| **CRISP-DM** | Metodología de minería de datos: comprensión, preparación, modelado, evaluación, despliegue. |
