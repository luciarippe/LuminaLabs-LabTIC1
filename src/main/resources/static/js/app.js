// Comportamientos chicos de la interfaz. La pagina funciona igual sin JavaScript.
document.addEventListener("DOMContentLoaded", () => {

    // Selects que aplican el cambio al elegir una opcion (ej. "Ordenar por" del catalogo)
    document.querySelectorAll("select[data-auto-submit]").forEach(select => {
        select.addEventListener("change", () => select.form.submit());
    });

    // Filtros del catalogo: si se cambia algo respecto de lo aplicado, se resalta el pie con "Aplicar" para que
    // se note que falta aplicarlos (y se apaga si se vuelve a como estaba)
    document.querySelectorAll("[data-filter-form]").forEach(form => {
        const foot = form.querySelector("[data-filter-foot]");
        const status = form.querySelector("[data-filter-status]");
        const button = foot.querySelector("button");
        const idleText = status.textContent;
        const idleLabel = button.lastChild.textContent;
        const snapshot = () => new URLSearchParams(new FormData(form)).toString();
        const applied = snapshot();
        const update = () => {
            const changed = snapshot() !== applied;
            foot.classList.toggle("has-changes", changed);
            status.textContent = changed ? "Tenés cambios sin aplicar" : idleText;
            button.lastChild.textContent = changed ? " Aplicar cambios" : idleLabel;
        };
        form.addEventListener("input", update);
        form.addEventListener("change", update);
    });

    // Fotos de una actividad (formulario del prestador): miniaturas de las elegidas, con una "x" para descartar
    // alguna antes de subir, y avisos si no son JPG/PNG, si pesan mas del limite o si se pasa la cantidad.
    // El servidor vuelve a controlar todo.
    document.querySelectorAll("[data-photo-upload]").forEach(section => {
        const input = section.querySelector("[data-photo-input]");
        const preview = section.querySelector("[data-photo-preview]");
        const status = section.querySelector("[data-photo-status]");
        const form = input.form;
        const maxPhotos = Number(section.dataset.maxPhotos);
        const maxBytes = Number(section.dataset.maxMb) * 1024 * 1024;
        const current = Number(section.dataset.currentPhotos);
        const allowed = ["image/jpeg", "image/png"];

        const problem = () => {
            const files = [...input.files];
            const removed = section.querySelectorAll("[data-photo-remove]:checked").length;
            const total = current - removed + files.length;
            const wrongType = files.find(file => !allowed.includes(file.type));
            const tooBig = files.find(file => file.size > maxBytes);
            if (wrongType) return `"${wrongType.name}" no es una foto JPG o PNG.`;
            if (tooBig) return `"${tooBig.name}" pesa más de ${section.dataset.maxMb} MB.`;
            if (total > maxPhotos) return `Son ${total} fotos y el máximo es ${maxPhotos}. Quitá algunas.`;
            return null;
        };

        const render = () => {
            preview.replaceChildren(...[...input.files].map((file, index) => {
                const tile = document.createElement("div");
                tile.className = "photo-tile photo-tile--new";
                const image = document.createElement("img");
                image.src = URL.createObjectURL(file);
                image.alt = file.name;
                image.addEventListener("load", () => URL.revokeObjectURL(image.src));
                const remove = document.createElement("button");
                remove.type = "button";
                remove.className = "photo-tile__discard";
                remove.textContent = "×";
                remove.setAttribute("aria-label", "Descartar " + file.name);
                remove.addEventListener("click", () => discard(index));
                const badge = document.createElement("span");
                badge.className = "photo-tile__badge";
                badge.textContent = "Nueva";
                tile.append(image, badge, remove);
                return tile;
            }));
            const message = problem();
            const count = input.files.length;
            status.textContent = message || (count === 0 ? "" : count === 1 ? "1 foto nueva para subir." : `${count} fotos nuevas para subir.`);
            status.classList.toggle("is-error", Boolean(message));
        };

        // Saca un archivo de la seleccion (un FileList no se puede modificar: se arma otro)
        const discard = index => {
            const remaining = new DataTransfer();
            [...input.files].forEach((file, i) => { if (i !== index) remaining.items.add(file); });
            input.files = remaining.files;
            render();
        };

        input.addEventListener("change", render);
        section.querySelectorAll("[data-photo-remove]").forEach(box => box.addEventListener("change", render));
        form.addEventListener("submit", event => {
            if (problem()) {
                event.preventDefault();
                render();
                section.scrollIntoView({behavior: "smooth", block: "center"});
            }
        });
    });

    // Galeria del detalle de una actividad: tocar una miniatura la muestra en grande
    document.querySelectorAll("[data-gallery]").forEach(gallery => {
        const main = gallery.querySelector("[data-gallery-main]");
        gallery.querySelectorAll(".gallery__thumb").forEach(thumb => thumb.addEventListener("click", () => {
            main.src = thumb.dataset.src;
            gallery.querySelectorAll(".gallery__thumb").forEach(other => other.classList.toggle("is-active", other === thumb));
        }));
    });

    // Fotos de localidades (administracion): al elegir el archivo se sube solo
    document.querySelectorAll("[data-auto-upload]").forEach(form => {
        form.querySelector("[data-auto-upload-input]").addEventListener("change", event => {
            if (event.target.files.length > 0) form.submit();
        });
    });

    // Formularios que piden confirmacion antes de enviarse (ej. cancelar un horario)
    document.querySelectorAll("form[data-confirm]").forEach(form => {
        form.addEventListener("submit", event => {
            if (!window.confirm(form.dataset.confirm)) {
                event.preventDefault();
            }
        });
    });

    // Menu de la cuenta en el encabezado. Con mouse se abre al pasar por encima (CSS); esto agrega el clic
    // (celulares), cerrar al tocar afuera o con Escape, y aria-expanded para lectores de pantalla.
    document.querySelectorAll("[data-user-menu]").forEach(menu => {
        const trigger = menu.querySelector(".user-menu__trigger");
        const setOpen = open => {
            menu.classList.toggle("is-open", open);
            menu.classList.remove("is-closed");
            trigger.setAttribute("aria-expanded", String(open));
        };
        trigger.addEventListener("click", () => setOpen(!menu.classList.contains("is-open")));
        menu.addEventListener("mouseenter", () => trigger.setAttribute("aria-expanded", "true"));
        menu.addEventListener("mouseleave", () => {
            menu.classList.remove("is-closed");
            if (!menu.classList.contains("is-open")) trigger.setAttribute("aria-expanded", "false");
        });
        document.addEventListener("click", event => {
            if (!menu.contains(event.target)) setOpen(false);
        });
        menu.addEventListener("keydown", event => {
            if (event.key !== "Escape") return;
            setOpen(false);
            // .is-closed lo cierra aunque el mouse siga encima o el foco siga adentro
            menu.classList.add("is-closed");
            trigger.focus();
        });
    });

    // Filtro por texto de una tabla (ej. "Mis actividades"): oculta las filas que no contienen lo buscado
    document.querySelectorAll("[data-table-filter]").forEach(input => {
        const table = document.getElementById(input.dataset.tableFilter);
        const empty = document.querySelector(`[data-table-empty="${input.dataset.tableFilter}"]`);
        const normalize = text => text.normalize("NFD").replace(/[̀-ͯ]/g, "").toLowerCase();
        input.addEventListener("input", () => {
            const term = normalize(input.value.trim());
            let visible = 0;
            table.querySelectorAll("tbody tr").forEach(row => {
                const show = normalize(row.textContent).includes(term);
                row.hidden = !show;
                if (show) visible++;
            });
            table.hidden = visible === 0;
            if (empty) empty.hidden = visible > 0;
        });
    });

    // Repetir un horario al agregarlo (espacio del prestador): muestra "Dias" y "Hasta" segun la opcion, sugiere
    // una fecha final y calcula cuantos horarios se van a crear, con las mismas reglas que SlotRepetition.
    document.querySelectorAll("[data-slot-repeat]").forEach(box => {
        const form = box.closest("form");
        const select = box.querySelector("[data-repeat-select]");
        const weekdaysField = box.querySelector("[data-repeat-weekdays]");
        const untilField = box.querySelector("[data-repeat-until]");
        const untilInput = untilField.querySelector("input");
        const preview = box.querySelector("[data-repeat-preview]");
        const submit = form.querySelector("[data-repeat-submit]");
        const dateInput = form.querySelector("input[name='date']");
        const maxSlots = Number(box.dataset.maxSlots);
        const stepDays = {DAILY: 1, EVERY_OTHER_DAY: 2, WEEKLY: 7, EVERY_OTHER_WEEK: 14, WEEKDAYS: 1};
        const dayNames = ["SUNDAY", "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY"];
        const parse = text => {
            const [year, month, day] = text.split("-").map(Number);
            return new Date(year, month - 1, day);
        };
        const toInputValue = date => [date.getFullYear(), String(date.getMonth() + 1).padStart(2, "0"),
            String(date.getDate()).padStart(2, "0")].join("-");
        const short = date => date.toLocaleDateString("es-UY", {day: "2-digit", month: "2-digit", year: "numeric"});

        const update = () => {
            const repetition = select.value;
            const repeated = repetition !== "NONE";
            weekdaysField.hidden = repetition !== "WEEKDAYS";
            untilField.hidden = !repeated;
            preview.classList.remove("is-error");
            submit.textContent = "Agregar horario";
            if (!repeated) {
                preview.textContent = "";
                return;
            }
            // Fecha final sugerida (sin pasar el maximo permitido) si todavia no se eligio
            if (!untilInput.value && dateInput.value) {
                const suggested = parse(dateInput.value);
                suggested.setMonth(suggested.getMonth() + Number(untilInput.dataset.suggestedMonths));
                const max = untilInput.max ? parse(untilInput.max) : null;
                untilInput.value = toInputValue(max && suggested > max ? max : suggested);
            }
            const showError = text => {
                preview.textContent = text;
                preview.classList.add("is-error");
            };
            if (!dateInput.value) return showError("Elegí la fecha del primer horario.");
            if (!untilInput.value) return showError("Elegí hasta qué fecha se repite.");
            const first = parse(dateInput.value);
            const until = parse(untilInput.value);
            if (until <= first) return showError("La fecha final tiene que ser posterior a la del primer horario.");
            if (untilInput.max && until > parse(untilInput.max)) return showError("La fecha final pasa el máximo permitido.");
            const chosenDays = [...weekdaysField.querySelectorAll("input:checked")].map(input => input.value);
            if (repetition === "WEEKDAYS" && chosenDays.length === 0) return showError("Elegí al menos un día de la semana.");

            const dates = [];
            for (const date = new Date(first); date <= until; date.setDate(date.getDate() + stepDays[repetition])) {
                if (repetition !== "WEEKDAYS" || chosenDays.includes(dayNames[date.getDay()])) dates.push(new Date(date));
            }
            if (dates.length === 0) return showError("Ningún día elegido cae entre esas fechas.");
            if (dates.length > maxSlots) {
                return showError(`Serían ${dates.length} horarios: el máximo por vez es ${maxSlots}. Elegí una fecha final más cercana.`);
            }
            preview.textContent = dates.length === 1
                ? `Se va a crear 1 horario, el ${short(dates[0])}.`
                : `Se van a crear ${dates.length} horarios, del ${short(dates[0])} al ${short(dates[dates.length - 1])}.`;
            submit.textContent = dates.length === 1 ? "Agregar horario" : `Agregar ${dates.length} horarios`;
        };

        [select, untilInput, dateInput].forEach(input => input.addEventListener("input", update));
        [select, untilInput, dateInput].forEach(input => input.addEventListener("change", update));
        weekdaysField.querySelectorAll("input").forEach(input => input.addEventListener("change", update));
        update();
    });

    // Calendario de horarios (detalle de la actividad). Se arma con la lista de horarios que manda el servidor
    // (cada <li> tiene data-date y data-bookable): los dias con lugares se pueden tocar y al tocar uno se muestran
    // solo sus horarios. Sin JavaScript se ve la lista completa.
    document.querySelectorAll("[data-slot-calendar]").forEach(calendar => {
        const list = calendar.parentElement.querySelector("[data-slot-list]");
        const items = [...list.querySelectorAll("li[data-date]")];
        if (items.length === 0) return;

        const slotsByDate = new Map();
        items.forEach(item => {
            if (!slotsByDate.has(item.dataset.date)) slotsByDate.set(item.dataset.date, []);
            slotsByDate.get(item.dataset.date).push(item);
        });
        const isBookable = item => item.dataset.bookable === "true";
        const parse = text => {
            const [year, month, day] = text.split("-").map(Number);
            return new Date(year, month - 1, day);
        };
        const toKey = date => [date.getFullYear(), String(date.getMonth() + 1).padStart(2, "0"),
            String(date.getDate()).padStart(2, "0")].join("-");
        const capitalize = text => text.charAt(0).toUpperCase() + text.slice(1);
        const monthFormat = new Intl.DateTimeFormat("es-UY", {month: "long", year: "numeric"});
        const dayFormat = new Intl.DateTimeFormat("es-UY", {weekday: "long", day: "numeric", month: "long"});

        // Se puede navegar desde el mes del primer horario hasta el del ultimo; arranca en el primero con lugares
        const dates = [...slotsByDate.keys()];
        const firstBookable = dates.find(date => slotsByDate.get(date).some(isBookable)) || dates[0];
        const monthOf = date => new Date(date.getFullYear(), date.getMonth(), 1);
        const minMonth = monthOf(parse(dates[0]));
        const maxMonth = monthOf(parse(dates[dates.length - 1]));
        let shownMonth = monthOf(parse(firstBookable));
        let selectedDate = null;
        const todayKey = toKey(new Date());

        // Estructura del calendario (textos fijos; los datos se agregan con textContent)
        calendar.innerHTML = `
            <div class="slot-calendar__head">
                <button type="button" class="slot-calendar__nav" data-month-step="-1" aria-label="Mes anterior">‹</button>
                <strong class="slot-calendar__month" aria-live="polite"></strong>
                <button type="button" class="slot-calendar__nav" data-month-step="1" aria-label="Mes siguiente">›</button>
            </div>
            <div class="slot-calendar__weekdays" aria-hidden="true">
                <span>L</span><span>M</span><span>M</span><span>J</span><span>V</span><span>S</span><span>D</span>
            </div>
            <div class="slot-calendar__grid"></div>
            <p class="slot-calendar__legend"><span class="slot-calendar__dot"></span> Días con lugares disponibles</p>
            <p class="slot-calendar__selection" aria-live="polite">Tocá un día marcado para ver sus horarios.</p>`;
        const title = calendar.querySelector(".slot-calendar__month");
        const grid = calendar.querySelector(".slot-calendar__grid");
        const selection = calendar.querySelector(".slot-calendar__selection");
        const navButtons = calendar.querySelectorAll("[data-month-step]");
        const radios = list.querySelectorAll("input[type='radio']");

        // Al empezar no hay dia elegido: ningun horario visible ni marcado
        calendar.hidden = false;
        list.classList.add("slot-list--by-day");
        list.hidden = true;
        radios.forEach(radio => { radio.checked = false; });

        const render = () => {
            title.textContent = capitalize(monthFormat.format(shownMonth));
            navButtons[0].disabled = shownMonth <= minMonth;
            navButtons[1].disabled = shownMonth >= maxMonth;
            grid.replaceChildren();
            // Lunes primero: getDay() da 0 para el domingo
            const blanks = (shownMonth.getDay() + 6) % 7;
            for (let i = 0; i < blanks; i++) grid.append(document.createElement("span"));
            const daysInMonth = new Date(shownMonth.getFullYear(), shownMonth.getMonth() + 1, 0).getDate();
            for (let day = 1; day <= daysInMonth; day++) {
                const date = new Date(shownMonth.getFullYear(), shownMonth.getMonth(), day);
                const key = toKey(date);
                const daySlots = slotsByDate.get(key);
                const available = daySlots ? daySlots.filter(isBookable).length : 0;
                const button = document.createElement("button");
                button.type = "button";
                button.className = "slot-calendar__day";
                button.textContent = day;
                if (key === todayKey) button.classList.add("is-today");
                if (available > 0) {
                    button.classList.add("has-slots");
                    button.setAttribute("aria-label", `${dayFormat.format(date)}: ${available === 1 ? "1 horario" : available + " horarios"}`);
                    button.setAttribute("aria-pressed", String(key === selectedDate));
                    if (key === selectedDate) button.classList.add("is-selected");
                    button.addEventListener("click", () => selectDate(key));
                } else {
                    button.disabled = true;
                    if (daySlots) {
                        // Tiene horarios pero ninguno se puede reservar (agotados o con las reservas cerradas)
                        button.classList.add("is-full");
                        button.title = "Sin lugares disponibles";
                    }
                }
                grid.append(button);
            }
        };

        const selectDate = key => {
            selectedDate = key;
            items.forEach(item => { item.hidden = item.dataset.date !== key; });
            radios.forEach(radio => { radio.checked = false; });
            // Si ese dia hay un solo horario disponible, ya queda elegido
            const available = slotsByDate.get(key).filter(isBookable);
            if (available.length === 1) available[0].querySelector("input").checked = true;
            list.hidden = false;
            selection.textContent = `Horarios del ${dayFormat.format(parse(key)).replace(",", "")}`;
            selection.classList.add("is-active");
            render();
            // Avisa al control de lugares (escucha "change" en los horarios) que cambio la eleccion
            radios[0].dispatchEvent(new Event("change", {bubbles: true}));
        };

        navButtons.forEach(button => button.addEventListener("click", () => {
            shownMonth = new Date(shownMonth.getFullYear(), shownMonth.getMonth() + Number(button.dataset.monthStep), 1);
            render();
        }));
        render();
    });

    // Cantidad de personas al reservar (detalle de la actividad y pagina de reserva): mientras se escribe, avisa
    // debajo del campo si hay lugar y cuantos quedan. Los lugares salen del data-availability del campo o, en el
    // detalle, del horario elegido. Si no hay lugar no deja seguir (el servidor igual lo vuelve a controlar).
    document.querySelectorAll("[data-seat-check]").forEach(input => {
        const form = input.form;
        const status = document.getElementById(input.getAttribute("aria-describedby"));
        const maxPerReservation = Number(input.dataset.maxPerReservation);
        const syncsParticipants = input.hasAttribute("data-participant-count");
        const places = n => n === 1 ? "queda 1 lugar" : `quedan ${n} lugares`;
        const people = n => n === 1 ? "1 persona" : `${n} personas`;

        const availability = () => {
            if (input.dataset.availability) return Number(input.dataset.availability);
            const slot = form.querySelector("input[name='slotId']:checked");
            return slot ? Number(slot.dataset.availability) : null;
        };

        // Devuelve la cantidad si se puede reservar, o null
        const check = () => {
            const raw = input.value.trim();
            const left = availability();
            let message;
            let count = null;
            if (left === null) {
                message = "Elegí un día y un horario para ver si hay lugar.";
            } else if (raw === "") {
                message = `Ingresá cuántas personas van: ${places(left)} en este horario.`;
            } else if (!/^\d+$/.test(raw) || Number(raw) < 1) {
                message = "Ingresá un número de personas mayor a 0.";
            } else if (Number(raw) > left) {
                message = `No hay lugar para ${people(Number(raw))}: ${places(left)} en este horario.`;
            } else if (Number(raw) > maxPerReservation) {
                message = `Podés reservar hasta ${maxPerReservation} personas por reserva.`;
            } else {
                count = Number(raw);
                message = `Hay lugar: ${places(left)} en este horario.`;
            }
            // Sin horario elegido solo se marca en rojo despues de intentar reservar
            const showError = count === null && (left === null ? attempted : raw !== "");
            status.textContent = message;
            status.classList.toggle("is-ok", count !== null);
            status.classList.toggle("is-error", showError);
            input.classList.toggle("is-invalid", showError);
            if (count !== null && syncsParticipants) syncParticipants(count);
            return count;
        };

        let attempted = false;
        input.addEventListener("input", check);
        form.querySelectorAll("input[name='slotId']").forEach(radio => radio.addEventListener("change", check));
        form.addEventListener("submit", event => {
            attempted = true;
            const count = check();
            // En la pagina de reserva los participantes ya se ajustaron: no hace falta recargar (y se perderia lo escrito)
            if (count === null || syncsParticipants) {
                event.preventDefault();
            }
            if (count === null) {
                // Sin horario elegido se lleva al calendario; si no, al campo de personas
                const calendar = availability() === null && form.querySelector("[data-slot-calendar]:not([hidden])");
                if (calendar) calendar.scrollIntoView({behavior: "smooth", block: "center"});
                else input.focus();
            }
        });
        check();
    });

    // Pagina de reserva: agrega o quita formularios de participantes (desde el <template>) y actualiza el resumen
    function syncParticipants(count) {
        const container = document.querySelector("[data-participants]");
        const template = document.getElementById("participant-template");
        if (!container || !template) return;
        const sections = container.querySelectorAll("[data-participant]");
        for (let i = sections.length; i < count; i++) {
            container.insertAdjacentHTML("beforeend",
                template.innerHTML.replaceAll("{i}", i).replaceAll("{n}", i + 1));
        }
        for (let i = sections.length - 1; i >= count; i--) {
            sections[i].remove();
        }
        const summary = document.querySelector("[data-reservation-summary]");
        if (summary) {
            const total = Number(summary.dataset.price) * count;
            summary.querySelector("[data-summary-count]").textContent = count;
            summary.querySelector("[data-summary-total]").textContent =
                "$ " + new Intl.NumberFormat("es-UY", {maximumFractionDigits: 0}).format(total);
        }
    }

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
    const typeInputs = document.querySelectorAll("input[name$='businessType']");
    const identifierTexts = {
        COMPANY: ["RUT", "12 dígitos, solo números (sin espacios, puntos ni guiones)."],
        INDIVIDUAL: ["Cédula de identidad", "7 u 8 dígitos, solo números (sin puntos ni guion)."]
    };
    const updateIdentifier = () => {
        const checked = document.querySelector("input[name$='businessType']:checked");
        if (!checked || !identifierLabel) return;
        [identifierLabel.textContent, identifierHint.textContent] = identifierTexts[checked.value];
    };
    typeInputs.forEach(input => input.addEventListener("change", updateIdentifier));
    updateIdentifier();

    // Texto de ayuda del numero de cuenta segun el banco elegido (formato en el data-hint de cada opcion)
    const bankSelect = document.querySelector("select[name$='.bank']");
    const accountHint = document.getElementById("account-hint");
    if (bankSelect && accountHint) {
        const defaultHint = accountHint.textContent;
        const updateAccountHint = () => {
            accountHint.textContent = bankSelect.selectedOptions[0]?.dataset.hint || defaultHint;
        };
        bankSelect.addEventListener("change", updateAccountHint);
        updateAccountHint();
    }

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
