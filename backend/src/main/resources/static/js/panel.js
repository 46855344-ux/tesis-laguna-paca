/* ==========================================================================
   panel.js — Interactividad del panel
   --------------------------------------------------------------------------
   JavaScript vanilla, sin dependencias y sin manejadores inline: todo se
   enlaza con data-accion y addEventListener, de modo que una futura política
   CSP no obligue a reescribir las plantillas.

   Responsabilidades:
     - Tema claro/oscuro (persistido en localStorage).
     - Barra lateral colapsable en escritorio y cajón deslizante en móvil.
     - Menú de usuario accesible (Escape, clic fuera, foco).
     - Toasts para dar respuesta inmediata a cada acción.
     - Validación visual en tiempo real de los formularios.
     - Botón "copiar" del bloque de comandos.
     - Tablero: refresco en vivo cada 20 s y gráfico SVG sin librerías.
   ========================================================================== */

(function () {
  "use strict";

  var CLAVE_TEMA = "laguna-tema";
  var CLAVE_BARRA = "laguna-barra";
  var DURACION_TOAST_MS = 4500;

  var raiz = document.documentElement;
  var cuerpo = document.body;

  /* ------------------------------------------------------------------ Tema */

  function temaActual() {
    return raiz.dataset.tema === "oscuro" ? "oscuro" : "claro";
  }

  function aplicarTema(tema, persistir) {
    raiz.dataset.tema = tema;

    if (persistir) {
      try {
        localStorage.setItem(CLAVE_TEMA, tema);
      } catch (error) {
        /* Modo privado o almacenamiento bloqueado: el tema dura la visita. */
      }
    }

    var oscuro = tema === "oscuro";
    document.querySelectorAll("[data-accion='alternar-tema']").forEach(function (boton) {
      boton.setAttribute("aria-pressed", String(oscuro));
      boton.setAttribute("aria-label", oscuro ? "Cambiar a modo claro" : "Cambiar a modo oscuro");
      boton.setAttribute("title", oscuro ? "Cambiar a modo claro" : "Cambiar a modo oscuro");
    });
  }

  function alternarTema() {
    aplicarTema(temaActual() === "oscuro" ? "claro" : "oscuro", true);
  }

  /* -------------------------------------------------------- Barra lateral */

  function aplicarBarra(colapsada, persistir) {
    cuerpo.dataset.barra = colapsada ? "colapsada" : "expandida";

    if (persistir) {
      try {
        localStorage.setItem(CLAVE_BARRA, colapsada ? "colapsada" : "expandida");
      } catch (error) {
        /* Sin persistencia: el estado vale para la visita actual. */
      }
    }

    var boton = document.getElementById("alternar-barra");
    if (!boton) {
      return;
    }

    boton.setAttribute("aria-expanded", String(!colapsada));
    boton.setAttribute("aria-label", colapsada ? "Expandir el menú lateral" : "Contraer el menú lateral");
    boton.setAttribute("title", colapsada ? "Expandir el menú lateral" : "Contraer el menú lateral");

    var texto = boton.querySelector(".js-texto-barra");
    if (texto) {
      texto.textContent = colapsada ? "Expandir menú" : "Contraer menú";
    }
  }

  function alternarBarra() {
    aplicarBarra(cuerpo.dataset.barra !== "colapsada", true);
  }

  /* ------------------------------------------------------ Cajón en móvil */

  function abrirMenuMovil(abrir) {
    cuerpo.dataset.menu = abrir ? "abierto" : "cerrado";

    var boton = document.getElementById("abrir-menu");
    if (boton) {
      boton.setAttribute("aria-expanded", String(abrir));
    }

    if (abrir) {
      var primerEnlace = document.querySelector("#barra-lateral a, #barra-lateral button");
      if (primerEnlace) {
        primerEnlace.focus();
      }
    } else if (boton) {
      // Devolver el foco al disparador es lo que permite seguir navegando
      // con teclado sin perderse.
      boton.focus();
    }
  }

  /* ------------------------------------------------------- Menú de usuario */

  var disparadorUsuario = null;
  var listaUsuario = null;

  function abrirMenuUsuario(abrir) {
    if (!disparadorUsuario || !listaUsuario) {
      return;
    }

    disparadorUsuario.setAttribute("aria-expanded", String(abrir));
    listaUsuario.hidden = !abrir;

    if (abrir) {
      var primeraOpcion = listaUsuario.querySelector("button");
      if (primeraOpcion) {
        primeraOpcion.focus();
      }
    }
  }

  /* ---------------------------------------------------------------- Toasts */

  var ICONOS = {
    exito:
      '<svg class="icono" viewBox="0 0 24 24" fill="none" stroke="currentColor" ' +
      'stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' +
      '<circle cx="12" cy="12" r="10"/><path d="m9 12 2 2 4-4"/></svg>',
    error:
      '<svg class="icono" viewBox="0 0 24 24" fill="none" stroke="currentColor" ' +
      'stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' +
      '<circle cx="12" cy="12" r="10"/><path d="m15 9-6 6"/><path d="m9 9 6 6"/></svg>',
    alerta:
      '<svg class="icono" viewBox="0 0 24 24" fill="none" stroke="currentColor" ' +
      'stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' +
      '<path d="m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3Z"/>' +
      '<path d="M12 9v4"/><path d="M12 17h.01"/></svg>',
    info:
      '<svg class="icono" viewBox="0 0 24 24" fill="none" stroke="currentColor" ' +
      'stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' +
      '<circle cx="12" cy="12" r="10"/><path d="M12 16v-4"/><path d="M12 8h.01"/></svg>'
  };

  function cerrarToast(toast) {
    if (toast.dataset.saliendo === "true") {
      return;
    }
    toast.dataset.saliendo = "true";
    toast.classList.add("toast--saliendo");
    // Se espera al final de la animación antes de quitarlo del DOM; si la
    // animación está desactivada, el timeout igual lo limpia.
    setTimeout(function () {
      toast.remove();
    }, 200);
  }

  function mostrarToast(mensaje, tipo) {
    var contenedor = document.getElementById("contenedor-toasts");
    if (!contenedor) {
      return;
    }

    var clase = tipo === "exito" || tipo === "error" || tipo === "alerta" ? tipo : "info";

    var toast = document.createElement("div");
    toast.className = "toast toast--" + clase;
    toast.setAttribute("role", clase === "error" ? "alert" : "status");

    var icono = document.createElement("span");
    icono.className = "toast__icono";
    icono.innerHTML = ICONOS[clase];

    var texto = document.createElement("p");
    texto.className = "toast__cuerpo";
    texto.textContent = mensaje;

    var cerrar = document.createElement("button");
    cerrar.type = "button";
    cerrar.className = "toast__cerrar";
    cerrar.setAttribute("aria-label", "Cerrar notificación");
    cerrar.innerHTML =
      '<svg class="icono icono--sm" viewBox="0 0 24 24" fill="none" stroke="currentColor" ' +
      'stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' +
      '<path d="M18 6 6 18"/><path d="m6 6 12 12"/></svg>';
    cerrar.addEventListener("click", function () {
      cerrarToast(toast);
    });

    toast.appendChild(icono);
    toast.appendChild(texto);
    toast.appendChild(cerrar);
    contenedor.appendChild(toast);

    setTimeout(function () {
      cerrarToast(toast);
    }, DURACION_TOAST_MS);
  }

  /* -------------------------------------------------- Validación en vivo */

  function prepararFormularios() {
    document.querySelectorAll("form[data-validar]").forEach(function (formulario) {
      formulario.querySelectorAll("input[required]").forEach(function (campo) {
        var idError = campo.getAttribute("aria-describedby");
        var error = idError ? document.getElementById(idError) : null;

        function validar() {
          var vacio = campo.value.trim() === "";
          campo.setAttribute("aria-invalid", String(vacio));
          if (error) {
            error.dataset.visible = String(vacio);
          }
        }

        campo.addEventListener("blur", validar);
        // Una vez mostrado el error, se corrige mientras se escribe: feedback
        // inmediato, sin regañar antes de que la persona termine.
        campo.addEventListener("input", function () {
          if (campo.getAttribute("aria-invalid") === "true") {
            validar();
          }
        });
      });
    });
  }

  /* ------------------------------------------------------------- Copiar */

  function prepararCopiar() {
    document.querySelectorAll("[data-accion='copiar']").forEach(function (boton) {
      boton.addEventListener("click", function () {
        var destino = boton.getAttribute("data-objetivo");
        var origen = destino ? document.querySelector(destino) : null;
        if (!origen) {
          return;
        }

        var texto = (origen.textContent || "").trim();
        var confirmar = function () {
          mostrarToast("Comando copiado al portapapeles.", "exito");
        };
        var avisar = function () {
          mostrarToast("No se pudo copiar. Selecciona el texto y cópialo a mano.", "error");
        };

        if (navigator.clipboard && navigator.clipboard.writeText) {
          navigator.clipboard.writeText(texto).then(confirmar).catch(avisar);
        } else {
          avisar();
        }
      });
    });
  }

  /* ------------------------------------------------------ Tablero en vivo */

  var INTERVALO_REFRESCO_MS = 20000;
  var ANCHO_GRAFICO = 800;
  var ALTO_GRAFICO = 280;
  var MARGEN_GRAFICO = { arriba: 16, derecha: 16, abajo: 30, izquierda: 52 };
  var ETIQUETAS_VARIABLE = {
    ph: "pH",
    turbidez: "turbidez",
    temperatura: "temperatura",
    tds: "sólidos disueltos"
  };

  // La hora se formatea siempre en la zona de la laguna: si alguien abre el
  // panel desde otra zona, el eje sigue coincidiendo con las tarjetas.
  var HORA_CORTA = new Intl.DateTimeFormat("es-PE", {
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
    timeZone: "America/Lima"
  });

  var tablero = null;
  var variableActual = "ph";
  var cargando = false;

  function texto(tarjeta, selector, valor) {
    var elemento = tarjeta.querySelector(selector);
    if (elemento) {
      elemento.textContent = valor;
    }
  }

  function actualizarKpis(datos) {
    var muestras = document.getElementById("kpi-muestras");
    var alertas = document.getElementById("kpi-alertas");
    var estaciones = document.getElementById("kpi-estaciones");
    var actualizado = document.getElementById("kpi-actualizado");

    if (muestras) {
      muestras.textContent = datos.muestras;
    }
    if (alertas) {
      alertas.textContent = datos.alertas;
      // El rojo solo aparece cuando hay alertas de verdad.
      alertas.classList.toggle("kpi__valor--alerta", datos.alertas > 0);
    }
    if (estaciones) {
      // Se cuenta en el cliente a partir del mismo dato que pinta cada tarjeta:
      // así el KPI no depende de una propiedad calculada aparte en el servidor.
      var conDatos = datos.estaciones.filter(function (estacion) {
        return estacion.conDatos;
      }).length;
      estaciones.textContent = conDatos + " / " + datos.estaciones.length;
    }
    if (actualizado) {
      actualizado.textContent = datos.horaActualizacion;
    }
  }

  function actualizarEstaciones(datos) {
    datos.estaciones.forEach(function (estacion) {
      var tarjeta = document.querySelector('[data-estacion="' + estacion.id + '"]');
      if (!tarjeta) {
        return;
      }

      var insignia = tarjeta.querySelector(".js-estado");
      if (insignia) {
        insignia.className = "etiqueta js-estado etiqueta--" + estacion.clase;
      }
      texto(tarjeta, ".js-estado-texto", estacion.etiqueta);

      var nota = tarjeta.querySelector(".js-nota");
      if (estacion.ultima) {
        if (nota) {
          nota.textContent = "Última muestra " + estacion.ultima.hace +
            " · " + estacion.ultima.fecha;
        }
        texto(tarjeta, ".js-ph", estacion.ultima.ph);
        texto(tarjeta, ".js-turbidez", estacion.ultima.turbidez);
        texto(tarjeta, ".js-temperatura", estacion.ultima.temperatura);
        texto(tarjeta, ".js-tds", estacion.ultima.tds);
      } else {
        if (nota) {
          nota.textContent = "Esperando la primera muestra del nodo.";
        }
        texto(tarjeta, ".js-ph", "—");
        texto(tarjeta, ".js-turbidez", "—");
        texto(tarjeta, ".js-temperatura", "—");
        texto(tarjeta, ".js-tds", "—");
      }

      // La atención de la alerta la registra el panel de alertas; aquí solo se
      // refleja, para que el tablero no muestre como pendiente algo ya resuelto.
      var atencion = tarjeta.querySelector(".js-atendida");
      if (atencion) {
        atencion.hidden = !estacion.atendida;
        atencion.textContent = estacion.atendida
          ? "Atendida por " + estacion.atendidaPor + " · " + estacion.atendidaHace
          : "";
      }

      var motivos = tarjeta.querySelector(".js-motivos");
      if (motivos) {
        motivos.textContent = "";
        estacion.motivos.forEach(function (motivo) {
          var item = document.createElement("li");
          item.textContent = motivo;
          motivos.appendChild(item);
        });
        motivos.hidden = estacion.motivos.length === 0;
      }
    });
  }

  function agruparSeries(datos) {
    var indicePorId = {};
    var series = datos.estaciones.map(function (estacion, indice) {
      indicePorId[estacion.id] = indice;
      // El color depende del índice de la estación, no del orden de llegada:
      // N1 siempre es la misma línea.
      return { id: estacion.id, indice: indice, puntos: [] };
    });

    datos.serie.forEach(function (punto) {
      var indice = indicePorId[punto.estacion];
      var valor = punto[variableActual];
      if (indice === undefined || typeof valor !== "number") {
        return;
      }
      series[indice].puntos.push({
        t: new Date(punto.instante).getTime(),
        v: valor
      });
    });

    series.forEach(function (serie) {
      serie.puntos.sort(function (a, b) {
        return a.t - b.t;
      });
    });

    return series;
  }

  function crearNodoSvg(etiqueta, atributos) {
    var nodo = document.createElementNS("http://www.w3.org/2000/svg", etiqueta);
    Object.keys(atributos).forEach(function (nombre) {
      nodo.setAttribute(nombre, atributos[nombre]);
    });
    return nodo;
  }

  function formatearValor(valor) {
    if (variableActual === "ph") {
      return valor.toFixed(2);
    }
    if (variableActual === "tds") {
      return valor.toFixed(0);
    }
    return valor.toFixed(1);
  }

  function dibujarGrafico() {
    var svg = document.getElementById("grafico-serie");
    var vacio = document.getElementById("grafico-vacio");
    var leyenda = document.getElementById("leyenda-serie");

    if (!svg || !vacio || !leyenda || !tablero) {
      return;
    }

    svg.textContent = "";
    leyenda.textContent = "";

    var series = agruparSeries(tablero).filter(function (serie) {
      return serie.puntos.length > 0;
    });

    if (series.length === 0) {
      svg.hidden = true;
      vacio.hidden = false;
      vacio.textContent = "Todavía no hay muestras en las últimas 24 horas.";
      return;
    }

    var minimo = Infinity;
    var maximo = -Infinity;
    var tMin = Infinity;
    var tMax = -Infinity;

    series.forEach(function (serie) {
      serie.puntos.forEach(function (punto) {
        minimo = Math.min(minimo, punto.v);
        maximo = Math.max(maximo, punto.v);
        tMin = Math.min(tMin, punto.t);
        tMax = Math.max(tMax, punto.t);
      });
    });

    // Un rango plano (todas las lecturas iguales) no debe dividir entre cero.
    var rango = maximo - minimo;
    var aire = rango === 0 ? Math.max(Math.abs(maximo) * 0.05, 0.5) : rango * 0.1;
    var yMin = minimo - aire;
    var yMax = maximo + aire;

    // Una ráfaga de muestras separadas por segundos aplastaría el eje y todas
    // las horas se leerían iguales: se abre la ventana hasta media hora como
    // mínimo. Es un mínimo de legibilidad, no una ventana de datos: las
    // muestras siguen siendo solo las de las últimas 24 h.
    var INTERVALO_MINIMO_MS = 30 * 60000;
    if (tMax - tMin < INTERVALO_MINIMO_MS) {
      var centro = (tMin + tMax) / 2;
      tMin = centro - INTERVALO_MINIMO_MS / 2;
      tMax = centro + INTERVALO_MINIMO_MS / 2;
    }

    var anchoUtil = ANCHO_GRAFICO - MARGEN_GRAFICO.izquierda - MARGEN_GRAFICO.derecha;
    var altoUtil = ALTO_GRAFICO - MARGEN_GRAFICO.arriba - MARGEN_GRAFICO.abajo;

    function x(t) {
      return MARGEN_GRAFICO.izquierda + ((t - tMin) / (tMax - tMin)) * anchoUtil;
    }

    function y(valor) {
      return MARGEN_GRAFICO.arriba + altoUtil -
        ((valor - yMin) / (yMax - yMin)) * altoUtil;
    }

    svg.setAttribute("viewBox", "0 0 " + ANCHO_GRAFICO + " " + ALTO_GRAFICO);
    svg.setAttribute("aria-label", "Serie temporal de " +
      ETIQUETAS_VARIABLE[variableActual] + " de las últimas 24 horas");

    var divisiones = 4;
    for (var i = 0; i <= divisiones; i += 1) {
      var valorLinea = yMin + ((yMax - yMin) * i) / divisiones;
      var yLinea = y(valorLinea);

      svg.appendChild(crearNodoSvg("line", {
        x1: MARGEN_GRAFICO.izquierda,
        x2: ANCHO_GRAFICO - MARGEN_GRAFICO.derecha,
        y1: yLinea,
        y2: yLinea,
        "class": "grafico__rejilla"
      }));

      var etiquetaY = crearNodoSvg("text", {
        x: MARGEN_GRAFICO.izquierda - 8,
        y: yLinea + 4,
        "text-anchor": "end",
        "class": "grafico__eje"
      });
      etiquetaY.textContent = formatearValor(valorLinea);
      svg.appendChild(etiquetaY);
    }

    var marcasX = 4;
    for (var j = 0; j <= marcasX; j += 1) {
      var instante = tMin + ((tMax - tMin) * j) / marcasX;
      var etiquetaX = crearNodoSvg("text", {
        x: x(instante),
        y: ALTO_GRAFICO - 8,
        "text-anchor": j === 0 ? "start" : (j === marcasX ? "end" : "middle"),
        "class": "grafico__eje"
      });
      etiquetaX.textContent = HORA_CORTA.format(new Date(instante));
      svg.appendChild(etiquetaX);
    }

    series.forEach(function (serie) {
      var clase = "serie-" + (serie.indice + 1);
      var titulo = serie.id + " — " + ETIQUETAS_VARIABLE[variableActual];

      if (serie.puntos.length === 1) {
        // Una polilínea de un solo punto no dibuja nada: se usa un círculo.
        var unico = serie.puntos[0];
        var circulo = crearNodoSvg("circle", {
          cx: x(unico.t),
          cy: y(unico.v),
          r: 4,
          "class": "grafico__punto " + clase
        });
        var tituloPunto = crearNodoSvg("title", {});
        tituloPunto.textContent = titulo;
        circulo.appendChild(tituloPunto);
        svg.appendChild(circulo);
      } else {
        var puntos = serie.puntos.map(function (punto) {
          return x(punto.t) + "," + y(punto.v);
        }).join(" ");

        var linea = crearNodoSvg("polyline", {
          points: puntos,
          "class": "grafico__linea " + clase
        });
        var tituloLinea = crearNodoSvg("title", {});
        tituloLinea.textContent = titulo;
        linea.appendChild(tituloLinea);
        svg.appendChild(linea);
      }

      var item = document.createElement("li");
      item.className = "leyenda__item";

      var muestra = document.createElement("span");
      muestra.className = "leyenda__muestra " + clase;
      muestra.setAttribute("aria-hidden", "true");

      item.appendChild(muestra);
      item.appendChild(document.createTextNode(serie.id));
      leyenda.appendChild(item);
    });

    svg.hidden = false;
    vacio.hidden = true;
  }

  function cargarTablero(manual) {
    if (cargando) {
      return Promise.resolve();
    }
    cargando = true;

    return fetch("/panel/datos", {
      headers: { Accept: "application/json" },
      credentials: "same-origin"
    }).then(function (respuesta) {
      if (!respuesta.ok) {
        throw new Error("HTTP " + respuesta.status);
      }
      return respuesta.json();
    }).then(function (datos) {
      tablero = datos;
      actualizarKpis(datos);
      actualizarEstaciones(datos);
      dibujarGrafico();
      if (manual) {
        mostrarToast("Datos actualizados.", "exito");
      }
    }).catch(function () {
      // Un refresco automático que falla no debe interrumpir: solo se avisa
      // cuando la persona pidió actualizar a mano.
      if (manual) {
        mostrarToast("No se pudieron actualizar los datos. Revisa la conexión con el backend.", "error");
      }
    }).finally(function () {
      cargando = false;
    });
  }

  function iniciarTablero() {
    var boton = document.getElementById("boton-actualizar");
    if (!boton) {
      return; // No es la pantalla del tablero.
    }

    boton.addEventListener("click", function () {
      cargarTablero(true);
    });

    var chips = document.querySelectorAll(".chip[data-variable]");
    chips.forEach(function (chip) {
      chip.addEventListener("click", function () {
        variableActual = chip.getAttribute("data-variable");
        chips.forEach(function (otro) {
          var activo = otro === chip;
          otro.classList.toggle("es-activo", activo);
          otro.setAttribute("aria-pressed", String(activo));
        });
        dibujarGrafico();
      });
    });

    cargarTablero(false);

    setInterval(function () {
      // Con la pestaña oculta no se consulta: no gasta red ni batería.
      if (!document.hidden) {
        cargarTablero(false);
      }
    }, INTERVALO_REFRESCO_MS);

    document.addEventListener("visibilitychange", function () {
      if (!document.hidden) {
        cargarTablero(false);
      }
    });
  }

  /* ------------------------------------------------------------ Arranque */

  function iniciar() {
    // Sincroniza los botones con el tema que el script del <head> ya aplicó.
    aplicarTema(temaActual(), false);

    try {
      if (localStorage.getItem(CLAVE_BARRA) === "colapsada") {
        aplicarBarra(true, false);
      }
    } catch (error) {
      /* Sin almacenamiento: se arranca expandida. */
    }

    document.querySelectorAll("[data-accion='alternar-tema']").forEach(function (boton) {
      boton.addEventListener("click", alternarTema);
    });

    var alternar = document.getElementById("alternar-barra");
    if (alternar) {
      alternar.addEventListener("click", alternarBarra);
    }

    var abrir = document.getElementById("abrir-menu");
    if (abrir) {
      abrir.addEventListener("click", function () {
        abrirMenuMovil(cuerpo.dataset.menu !== "abierto");
      });
    }

    var velo = document.getElementById("velo");
    if (velo) {
      velo.addEventListener("click", function () {
        abrirMenuMovil(false);
      });
    }

    disparadorUsuario = document.getElementById("abrir-menu-usuario");
    listaUsuario = document.getElementById("menu-usuario");

    if (disparadorUsuario && listaUsuario) {
      disparadorUsuario.addEventListener("click", function () {
        abrirMenuUsuario(listaUsuario.hidden);
      });

      document.addEventListener("click", function (evento) {
        if (listaUsuario.hidden) {
          return;
        }
        if (!listaUsuario.contains(evento.target) && !disparadorUsuario.contains(evento.target)) {
          abrirMenuUsuario(false);
        }
      });
    }

    // Un solo manejador global para Escape: cierra lo que esté abierto y
    // devuelve el foco a donde estaba, en orden de prioridad.
    document.addEventListener("keydown", function (evento) {
      if (evento.key !== "Escape") {
        return;
      }

      if (listaUsuario && !listaUsuario.hidden) {
        abrirMenuUsuario(false);
        disparadorUsuario.focus();
        return;
      }

      if (cuerpo.dataset.menu === "abierto") {
        abrirMenuMovil(false);
      }
    });

    prepararFormularios();
    prepararCopiar();
    iniciarTablero();
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", iniciar);
  } else {
    iniciar();
  }

  // Punto de entrada para las plantillas que necesiten avisar algo.
  window.LagunaPanel = { toast: mostrarToast };
})();
