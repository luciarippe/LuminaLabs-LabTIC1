// Comportamientos chicos de la interfaz. La pagina funciona igual sin JavaScript.
document.addEventListener("DOMContentLoaded", () => {

    // Selects que aplican el cambio al elegir una opcion (ej. "Ordenar por" del catalogo)
    document.querySelectorAll("select[data-auto-submit]").forEach(select => {
        select.addEventListener("change", () => select.form.submit());
    });

    // Mostrar / ocultar contrasena
    document.querySelectorAll("[data-toggle-password]").forEach(button => {
        const input = document.getElementById(button.dataset.togglePassword);
        button.addEventListener("click", () => {
            const hidden = input.type === "password";
            input.type = hidden ? "text" : "password";
            button.textContent = hidden ? "Ocultar" : "Mostrar";
        });
    });

    // Texto de ayuda del identificador segun el tipo de emprendimiento (RUT o cedula)
    const identifierLabel = document.getElementById("identifier-label");
    const identifierHint = document.getElementById("identifier-hint");
    const typeInputs = document.querySelectorAll("input[name='businessType']");
    const identifierTexts = {
        COMPANY: ["RUT", "12 dígitos, con o sin espacios."],
        INDIVIDUAL: ["Cédula de identidad", "7 u 8 dígitos, con o sin puntos y guion."]
    };
    const updateIdentifier = () => {
        const checked = document.querySelector("input[name='businessType']:checked");
        if (!checked || !identifierLabel) return;
        [identifierLabel.textContent, identifierHint.textContent] = identifierTexts[checked.value];
    };
    typeInputs.forEach(input => input.addEventListener("change", updateIdentifier));
    updateIdentifier();

    // Selector de localidades: lista de seleccionadas arriba y filtro por texto
    document.querySelectorAll("[data-locality-picker]").forEach(picker => {
        const search = picker.querySelector("[data-locality-search]");
        const count = picker.querySelector("[data-locality-count]");
        const selectedList = picker.querySelector("[data-locality-selected]");
        const emptyMessage = picker.querySelector("[data-locality-empty]");
        const checkboxes = picker.querySelectorAll("input[type='checkbox']");
        const normalize = text => text.normalize("NFD").replace(/[̀-ͯ]/g, "").toLowerCase();

        // Vuelve a dibujar las seleccionadas; cada una tiene una "x" que desmarca su checkbox
        const updateSelected = () => {
            const checked = [...checkboxes].filter(checkbox => checkbox.checked);
            count.textContent = checked.length;
            emptyMessage.hidden = checked.length > 0;
            selectedList.hidden = checked.length === 0;
            selectedList.replaceChildren(...checked.map(checkbox => {
                const chip = document.createElement("span");
                chip.className = "selected-chip";
                const name = document.createElement("span");
                name.textContent = checkbox.dataset.name + " ";
                const department = document.createElement("small");
                department.textContent = checkbox.dataset.department;
                name.append(department);
                const remove = document.createElement("button");
                remove.type = "button";
                remove.textContent = "×";
                remove.setAttribute("aria-label", "Quitar " + checkbox.dataset.name);
                remove.addEventListener("click", () => {
                    checkbox.checked = false;
                    updateSelected();
                });
                chip.append(name, remove);
                return chip;
            }));
        };

        search.addEventListener("input", () => {
            const term = normalize(search.value.trim());
            picker.querySelectorAll("[data-locality-group]").forEach(group => {
                const groupMatches = normalize(group.dataset.localityGroup).includes(term);
                let visible = 0;
                group.querySelectorAll(".check-chip").forEach(chip => {
                    const show = groupMatches || normalize(chip.textContent).includes(term);
                    chip.hidden = !show;
                    if (show) visible++;
                });
                group.hidden = visible === 0;
            });
        });

        // Al cargar tambien se dibujan las que ya vienen marcadas (ej. el formulario volvio con errores)
        checkboxes.forEach(checkbox => checkbox.addEventListener("change", updateSelected));
        updateSelected();
    });
});
