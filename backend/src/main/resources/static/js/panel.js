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
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", iniciar);
  } else {
    iniciar();
  }

  // Punto de entrada para las plantillas que necesiten avisar algo.
  window.LagunaPanel = { toast: mostrarToast };
})();
