// Se carga en el <head> sin "defer", antes de pintar: marca la pagina para las animaciones de entrada (ver
// "Movimiento" en styles.css y app.js), asi los elementos no se ven un instante y despues desaparecen para animarse.
// No se activa si la persona pidio menos movimiento en su sistema. Si app.js no llega a cargar, se desactiva sola
// para que nada quede oculto.
(function () {
    if (!("IntersectionObserver" in window) || window.matchMedia("(prefers-reduced-motion: reduce)").matches) {
        return;
    }
    var root = document.documentElement;
    root.classList.add("js-motion");
    setTimeout(function () {
        if (!window.motionReady) root.classList.remove("js-motion");
    }, 2500);
})();
