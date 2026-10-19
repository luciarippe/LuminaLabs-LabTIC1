// Validacion mientras se completan los formularios de registro (form[data-validate]).
// Las reglas son las mismas que valida el servidor (AccountForm, BusinessForm, ProviderRegistrationService):
// esto solo avisa antes; si JavaScript esta desactivado, el servidor igual rechaza los datos incorrectos.
//
// Cada regla recibe el valor y "live": true mientras se escribe. En ese momento solo se marcan los errores
// que ya no se arreglan escribiendo mas (un caracter no permitido, pasarse del maximo). Los de "falta algo"
// (muy corto, vacio) se muestran al salir del campo o al enviar.
document.addEventListener("DOMContentLoaded", () => {

    const firstInvalidChar = (value, allowed) => [...value].find(char => !allowed.test(char));
    const charMessage = (char, allowedText) =>
        char === " " ? "No puede tener espacios." : `El carácter "${char}" no está permitido: ${allowedText}.`;
    const maxLength = (value, max) => value.length > max ? `Máximo ${max} caracteres (tiene ${value.length}).` : null;
    const digitCount = value => value.replace(/\D/g, "").length;

    const name = (value, live) => {
        const bad = firstInvalidChar(value, /[\p{L} '.-]/u);
        if (bad) return charMessage(bad, "solo letras, espacios, apóstrofos o guiones");
        if (!/^\p{L}/u.test(value)) return "Tiene que empezar con una letra.";
        return maxLength(value, 60);
    };

    const email = (value, live) => {
        if (/\s/.test(value)) return "El correo no puede tener espacios.";
        if (!live && !/^[^@]+@[^@]+\.[^@.]{2,}$/.test(value)) return "El correo no tiene un formato válido (ej.: nombre@correo.com).";
        return maxLength(value, 254);
    };

    // Clave: nombre del campo sin el prefijo "business."
    const rules = {
        firstName: { required: "Ingresá tu nombre.", check: name },
        lastName: { required: "Ingresá tu apellido.", check: name },
        birthDate: {
            required: "Ingresá tu fecha de nacimiento.",
            check: value => {
                const today = new Date().toISOString().slice(0, 10);
                return value >= today ? "La fecha debe ser anterior a hoy." : null;
            }
        },
        username: {
            required: "Elegí un nombre de usuario.",
            check: (value, live) => {
                const bad = firstInvalidChar(value, /[A-Za-z0-9._-]/);
                if (bad) return charMessage(bad, "solo letras sin tilde, números, punto, guion o guion bajo");
                if (value.length > 30) return `Máximo 30 caracteres (tiene ${value.length}).`;
                if (!live && value.length < 3) return "Mínimo 3 caracteres.";
                return null;
            }
        },
        email: { required: "Ingresá tu correo.", check: email },
        password: {
            required: "Elegí una contraseña.",
            check: (value, live) => {
                if (value.length > 72) return `Máximo 72 caracteres (tiene ${value.length}).`;
                if (!live && value.length < 8) return `Mínimo 8 caracteres (tiene ${value.length}).`;
                return null;
            }
        },
        // Mi cuenta: cambio de contrasena
        currentPassword: { required: "Ingresá tu contraseña actual." },
        newPassword: {
            required: "Elegí una contraseña nueva.",
            check: (value, live) => {
                if (value.length > 72) return `Máximo 72 caracteres (tiene ${value.length}).`;
                if (!live && value.length < 8) return `Mínimo 8 caracteres (tiene ${value.length}).`;
                return null;
            }
        },
        confirmPassword: {
            required: "Repetí la contraseña.",
            check: (value, live, form) => {
                // En el registro se compara con "password"; en el cambio de contrasena, con "newPassword"
                const password = form.querySelector("[name='password'], [name='newPassword']").value;
                // Mientras escribe, solo avisa si lo que lleva ya no coincide con el principio de la contraseña
                const matches = live ? password.startsWith(value) : password === value;
                return matches ? null : "Las contraseñas no coinciden.";
            }
        },
        businessType: { required: "Elegí el tipo de emprendimiento." },
        legalName: { required: "Ingresá la razón social o el nombre.", check: value => maxLength(value, 150) },
        identifier: {
            required: "Ingresá el RUT o la cédula.",
            check: (value, live, form) => {
                const bad = firstInvalidChar(value, /[0-9]/);
                if (bad) return bad === " " ? "No puede tener espacios: solo números." : charMessage(bad, "solo números, sin espacios, puntos ni guiones");
                const type = form.querySelector("[name$='businessType']:checked");
                if (!type) return live ? null : "Elegí primero el tipo de emprendimiento (empresa o particular).";
                const digits = digitCount(value);
                if (type.value === "COMPANY") {
                    if (digits > 12) return `El RUT debe tener 12 dígitos y tiene ${digits}.`;
                    if (!live && digits < 12) return `El RUT debe tener 12 dígitos y tiene ${digits}.`;
                } else {
                    if (digits > 8) return `La cédula debe tener 7 u 8 dígitos y tiene ${digits}.`;
                    if (!live && digits < 7) return `La cédula debe tener 7 u 8 dígitos y tiene ${digits}.`;
                }
                return null;
            }
        },
        description: { required: "Contanos qué servicios ofrecés.", check: value => maxLength(value, 2000) },
        locationIds: { required: "Elegí al menos una localidad." },
        contactEmail: { required: "Ingresá un correo de contacto.", check: email },
        contactPhone: {
            required: "Ingresá un teléfono de contacto.",
            check: (value, live) => {
                const bad = firstInvalidChar(value, /[+0-9 ()-]/);
                if (bad && bad !== " ") return charMessage(bad, "solo números, espacios, +, paréntesis o guiones");
                if (value.length > 20) return `Máximo 20 caracteres (tiene ${value.length}).`;
                if (!live && value.length < 6) return "Mínimo 6 caracteres.";
                return null;
            }
        },
        bank: { required: "Elegí el banco o la billetera." },
        // El formato de cada banco viene en los data-* de su <option> (enum Bank, mismos mensajes)
        bankAccountNumber: {
            required: "Ingresá el número de cuenta.",
            check: (value, live, form) => {
                const bad = firstInvalidChar(value, /[0-9]/);
                if (bad) return bad === " " ? "No puede tener espacios: solo números." : charMessage(bad, "solo números, sin espacios, puntos ni guiones");
                const bank = form.querySelector("select[name$='.bank'] option:checked");
                if (!bank || !bank.value) return live ? null : "Elegí primero el banco o la billetera.";
                const min = Number(bank.dataset.min);
                const max = Number(bank.dataset.max);
                const firstDigits = bank.dataset.firstDigits;
                if (value.length > max || (!live && value.length < min)) {
                    const expected = min === max ? max : min === 1 ? `hasta ${max}` : `entre ${min} y ${max}`;
                    return `En ${bank.textContent} la cuenta tiene ${expected} dígitos (ingresaste ${value.length}).`;
                }
                if (firstDigits && !firstDigits.includes(value[0])) {
                    const options = [...firstDigits].join(", ").replace(/, (\d)$/, " o $1");
                    return `En ${bank.textContent} la cuenta empieza con ${options}.`;
                }
                return null;
            }
        }
    };

    const ruleKey = input => input.name.replace(/^business\./, "");

    document.querySelectorAll("form[data-validate]").forEach(form => {
        // Un grupo por campo: los radios y checkbox con el mismo nombre forman uno solo
        const groups = new Map();
        form.querySelectorAll("input[name], textarea[name], select[name]").forEach(input => {
            const key = ruleKey(input);
            if (!rules[key] || input.type === "hidden") return;
            if (!groups.has(key)) {
                const multiple = input.type === "radio" || input.type === "checkbox";
                groups.set(key, {
                    key,
                    inputs: [],
                    multiple,
                    // Donde va el mensaje: el .field del campo, o la seccion para los radios del tipo
                    container: input.closest(".field") || input.closest(".form-section"),
                    dirty: false
                });
            }
            groups.get(key).inputs.push(input);
        });

        const valueOf = group => group.multiple
            ? group.inputs.filter(input => input.checked).map(input => input.value).join(",")
            // La contrasena, el identificador y la cuenta no se recortan: un espacio ahi tambien es un error para el servidor
            : group.inputs[0].type === "password" || group.key === "identifier" || group.key === "bankAccountNumber"
                ? group.inputs[0].value : group.inputs[0].value.trim();

        const errorElement = group => {
            const errors = [...group.container.querySelectorAll(".field-error")]
                .filter(element => element.closest(".field, .form-section") === group.container);
            return errors[errors.length - 1];
        };

        const showError = (group, message) => {
            let element = errorElement(group);
            // Un error que mando el servidor (ej. "ese usuario ya está en uso") se deja hasta que se cambie el valor
            if (!message && element && !element.dataset.client && !group.dirty) return;
            if (message && !element) {
                element = document.createElement("span");
                element.className = "field-error";
                group.container.append(element);
            }
            if (element) {
                element.id ||= "error-" + group.key;
                element.dataset.client = "true";
                element.textContent = message || "";
                element.hidden = !message;
            }
            if (group.multiple) {
                group.container.classList.toggle("is-invalid", Boolean(message) && group.key === "locationIds");
            }
            group.inputs.forEach(input => {
                if (!group.multiple) input.classList.toggle("is-invalid", Boolean(message));
                input.setAttribute("aria-invalid", message ? "true" : "false");
                if (element) input.setAttribute("aria-describedby", element.id);
            });
        };

        // Devuelve el mensaje de error (o null) y lo muestra
        const validate = (group, live) => {
            const rule = rules[group.key];
            const value = valueOf(group);
            let message = null;
            if (!value) {
                message = live ? null : rule.required;
            } else if (rule.check) {
                message = rule.check(value, live, form);
            }
            showError(group, message);
            return message;
        };

        groups.forEach(group => {
            group.inputs.forEach(input => {
                const eventName = group.multiple || input.type === "date" ? "change" : "input";
                input.addEventListener(eventName, () => {
                    group.dirty = true;
                    validate(group, !group.multiple && input.type !== "date");
                });
                if (!group.multiple) {
                    input.addEventListener("blur", () => {
                        if (group.dirty) validate(group, false);
                    });
                }
            });
        });

        // Campos que dependen de otro: el identificador del tipo y la confirmacion de la contrasena
        const revalidateWhen = (sourceKey, targetKey) => {
            const source = groups.get(sourceKey);
            const target = groups.get(targetKey);
            if (!source || !target) return;
            source.inputs.forEach(input => input.addEventListener(source.multiple ? "change" : "input", () => {
                if (target.dirty) validate(target, false);
            }));
        };
        revalidateWhen("businessType", "identifier");
        revalidateWhen("password", "confirmPassword");
        revalidateWhen("newPassword", "confirmPassword");
        revalidateWhen("bank", "bankAccountNumber");

        // Al enviar se revisa todo; si hay errores no se envia y se va al primero
        form.addEventListener("submit", event => {
            const invalid = [...groups.values()].filter(group => validate(group, false));
            if (invalid.length === 0) return;
            event.preventDefault();
            showSummary(form, invalid.length);
            const first = invalid[0].inputs[0];
            first.closest(".field, .form-section").scrollIntoView({ behavior: "smooth", block: "center" });
            first.focus({ preventScroll: true });
        });
    });

    // Mismo aviso que muestra el servidor arriba del formulario
    function showSummary(form, count) {
        // En el panel de administracion el formulario tiene un cuerpo (.panel__body): el aviso va adentro
        const host = form.querySelector(":scope > .panel__body") || form;
        let alert = host.querySelector(":scope > .alert--error");
        if (!alert) {
            alert = document.createElement("div");
            alert.className = "alert alert--error";
            alert.setAttribute("role", "alert");
            host.prepend(alert);
        }
        const title = document.createElement("strong");
        title.textContent = "Revisá los campos marcados. ";
        const detail = document.createElement("span");
        detail.textContent = count === 1 ? "Hay un dato para corregir." : `Hay ${count} datos para corregir.`;
        const wrapper = document.createElement("div");
        wrapper.append(title, detail);
        alert.replaceChildren(wrapper);
    }
});
