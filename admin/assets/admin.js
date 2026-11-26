/* Block Boom admin — small, dependency-free enhancements. */
(function () {
  "use strict";

  function enhanceTables() {
    document.querySelectorAll(".bb-main table").forEach(function (table) {
      var headers = Array.prototype.map.call(
        table.querySelectorAll("thead th"),
        function (th) { return (th.textContent || "").trim(); }
      );
      if (headers.length < 3) return;
      table.classList.add("bb-mobile-table");
      table.querySelectorAll("tbody tr").forEach(function (tr) {
        Array.prototype.forEach.call(tr.children, function (td, index) {
          if (td.tagName !== "TD") return;
          if (td.hasAttribute("colspan")) return;
          if (headers[index]) td.setAttribute("data-label", headers[index]);
        });
      });
    });
  }

  function chartDefaults() {
    if (typeof window.Chart === "undefined") return;
    window.Chart.defaults.color = "#8bb8a6";
    window.Chart.defaults.borderColor = "rgba(110, 231, 183, 0.1)";
    window.Chart.defaults.font.family = "Vazirmatn, system-ui, sans-serif";
    window.Chart.defaults.font.size = 11;
    window.Chart.defaults.plugins.legend.labels.usePointStyle = true;
    window.Chart.defaults.plugins.legend.labels.boxWidth = 8;
    window.Chart.defaults.plugins.tooltip.backgroundColor = "rgba(3, 16, 11, 0.96)";
    window.Chart.defaults.plugins.tooltip.borderColor = "rgba(110, 231, 183, 0.22)";
    window.Chart.defaults.plugins.tooltip.borderWidth = 1;
    window.Chart.defaults.plugins.tooltip.titleColor = "#ecfff7";
    window.Chart.defaults.plugins.tooltip.bodyColor = "#c4e6d8";
    window.Chart.defaults.plugins.tooltip.padding = 10;
    window.Chart.defaults.plugins.tooltip.cornerRadius = 10;
  }

  function passwordToggles() {
    document.querySelectorAll("[data-password-toggle]").forEach(function (button) {
      button.addEventListener("click", function () {
        var input = document.getElementById(button.getAttribute("data-password-toggle"));
        if (!input) return;
        var showing = input.type === "text";
        input.type = showing ? "password" : "text";
        button.setAttribute("aria-label", showing ? "نمایش رمز" : "پنهان کردن رمز");
        var icon = button.querySelector("i");
        if (icon) {
          icon.className = showing ? "fas fa-eye" : "fas fa-eye-slash";
        }
      });
    });
  }

  function copyCodes() {
    document.querySelectorAll(".bb-main code").forEach(function (code) {
      if (code.closest("pre")) return;
      code.title = "برای کپی کلیک کنید";
      code.style.cursor = "pointer";
      code.addEventListener("click", function () {
        var text = code.textContent || "";
        if (!text) return;
        var done = function () {
          var old = code.textContent;
          code.textContent = "کپی شد ✓";
          window.setTimeout(function () { code.textContent = old; }, 900);
        };
        if (navigator.clipboard && navigator.clipboard.writeText) {
          navigator.clipboard.writeText(text).then(done).catch(function () {});
        } else {
          done();
        }
      });
    });
  }

  function activeMobileNav() {
    var active = document.querySelector(".bb-mobile-nav .bb-nav-link.is-active");
    if (active && active.scrollIntoView) {
      active.scrollIntoView({ inline: "center", block: "nearest" });
    }
  }

  function init() {
    enhanceTables();
    chartDefaults();
    passwordToggles();
    copyCodes();
    activeMobileNav();
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", init);
  } else {
    init();
  }
})();
