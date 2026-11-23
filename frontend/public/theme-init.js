// Aplica o tema antes do primeiro desenho para evitar piscar em claro.
(function () {
  try {
    var t = localStorage.getItem("theme");
    var dark = t === "dark" || (t !== "light" && window.matchMedia("(prefers-color-scheme: dark)").matches);
    document.documentElement.classList.toggle("dark", dark);
  } catch (e) {}
})();
