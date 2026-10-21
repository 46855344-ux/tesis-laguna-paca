# Artefacto del modelo

`stgnn.pt` es el artefacto de inferencia que carga el servicio FastAPI. Se
generó con:

```powershell
ia-model\.venv\Scripts\python.exe train_stgnn.py --horizons 1 3 6 --save artifacts\stgnn.pt
```

Contenido: configuración del modelo, pesos, escalador ajustado **solo con
entrenamiento**, adyacencia del grafo y métricas, para los horizontes 1, 3 y 6 h.

## Advertencia sobre los datos

El artefacto se entrenó con la serie **sintética** de `dataset.py`, no con
mediciones de la laguna. Su `data_source` es `synthetic` y el panel lo muestra
así. Sirve para demostrar que la arquitectura aprende una dinámica
espacio-temporal y que el servicio de inferencia funciona de extremo a extremo;
**no** es evidencia de precisión sobre la Laguna de Paca.

Métricas del artefacto (semilla 42, ventana 24 h, 150 épocas):

| Horizonte | R² ST-GNN | R² persistencia | RMSE (pH) | MAE (pH) |
|---|---|---|---|---|
| 1 h | 0.9817 | 0.9675 | 0.0296 | 0.0235 |
| 3 h | 0.9681 | 0.8986 | 0.0391 | 0.0308 |
| 6 h | 0.9289 | 0.7403 | 0.0584 | 0.0445 |

Son las métricas del artefacto versionado (registradas en
`artifacts-train.log`). Una ejecución anterior de un solo horizonte dio cifras
ligeramente distintas (0.9819 y 0.9628) porque cada corrida reentrena; las de la
tabla son las que corresponden a este archivo.

El artefacto ocupa ~100 KB. Se versiona para que `docker compose` y la demo
funcionen sin entrenar; si se cambia el modelo o los datos, se regenera con el
comando de arriba y se actualiza este archivo.
