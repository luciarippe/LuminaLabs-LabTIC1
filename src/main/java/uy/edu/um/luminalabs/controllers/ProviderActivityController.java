package uy.edu.um.luminalabs.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import uy.edu.um.luminalabs.dto.ActivityForm;
import uy.edu.um.luminalabs.dto.SlotForm;
import uy.edu.um.luminalabs.dto.SlotRepetition;
import uy.edu.um.luminalabs.entities.Activity;
import uy.edu.um.luminalabs.entities.Business;
import uy.edu.um.luminalabs.entities.Slot;
import uy.edu.um.luminalabs.exceptions.FieldValidationException;
import uy.edu.um.luminalabs.repositories.ActivityCategoryRepository;
import uy.edu.um.luminalabs.security.SecurityUser;
import uy.edu.um.luminalabs.services.ImageProcessor;
import uy.edu.um.luminalabs.services.LocationService;
import uy.edu.um.luminalabs.services.ProviderActivityService;
import uy.edu.um.luminalabs.services.ProviderSlotService;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

// Espacio del prestador: publicar y modificar actividades (CU-03) y gestionar sus slots (CU-04).
// Todas las rutas estan bajo /provider, que solo acepta ROLE_PROVIDER (ver SecurityConfig);
// los servicios ademas verifican que la actividad sea de un emprendimiento del prestador.
@Controller
@RequestMapping("/provider/activities")
@RequiredArgsConstructor
public class ProviderActivityController {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy 'a las' HH:mm");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final Locale SPANISH = Locale.forLanguageTag("es-UY");

    // RNF-12: otra transaccion cambio el slot entre que lo leimos y lo guardamos (ver Slot.version)
    private static final String CONCURRENT_CHANGE = "Otra persona modificó este horario al mismo tiempo. "
            + "Revisá los datos actualizados y volvé a intentarlo.";

    private final ProviderActivityService activityService;
    private final ProviderSlotService slotService;
    private final LocationService locationService;
    private final ActivityCategoryRepository categoryRepository;
    private final Clock clock;

    // ---------- Actividades (CU-03) ----------

    // Todas las actividades del prestador, con su proxima fecha y precio "desde". Con runningOut=true (desde el
    // aviso del panel) solo las que tienen los horarios por terminarse, primero las mas urgentes.
    @GetMapping
    public String list(@AuthenticationPrincipal SecurityUser currentUser,
                       @RequestParam(defaultValue = "false") boolean runningOut, Model model) {
        List<Activity> activities = activityService.findActivities(currentUser.getId());
        List<ProviderSlotService.RunningOut> runningOutItems = slotService.findRunningOut(activities);
        model.addAttribute("activities", runningOut
                ? runningOutItems.stream().map(ProviderSlotService.RunningOut::activity).toList()
                : activities);
        model.addAttribute("runningOutFilter", runningOut);
        model.addAttribute("totalActivities", activities.size());
        model.addAttribute("runningOutDays", ProviderSlotService.RUNNING_OUT_DAYS);
        model.addAttribute("summaries", slotService.summarize(activities));
        model.addAttribute("canPublish", !activityService.findPublishableBusinesses(currentUser.getId()).isEmpty());
        // Las que tienen los horarios por terminarse llevan una etiqueta junto a la proxima fecha
        model.addAttribute("runningOutById", runningOutItems.stream()
                .collect(Collectors.toMap(item -> item.activity().getId(), Function.identity())));
        return "provider/activities";
    }

    @GetMapping("/new")
    public String newActivity(@AuthenticationPrincipal SecurityUser currentUser,
                              @RequestParam(required = false) Long businessId, Model model,
                              RedirectAttributes redirectAttributes) {
        List<Business> businesses = activityService.findPublishableBusinesses(currentUser.getId());
        if (businesses.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Necesitás un emprendimiento aprobado para publicar actividades.");
            return "redirect:/provider";
        }
        ActivityForm form = new ActivityForm();
        // 1a: si hay un solo emprendimiento (o se eligio desde el panel) ya queda seleccionado
        if (businessId != null) {
            form.setBusinessId(businessId);
        } else if (businesses.size() == 1) {
            form.setBusinessId(businesses.getFirst().getId());
        }
        // Valores sugeridos para las condiciones de reserva y cancelacion (RF-10)
        form.setBookingCutoffMinutes(60);
        form.setCancellationDeadlineHours(24);
        form.setCancellationFeePercentage(0);
        model.addAttribute("form", form);
        return showActivityForm(currentUser, null, model);
    }

    @PostMapping("/new")
    public String create(@AuthenticationPrincipal SecurityUser currentUser,
                         @Valid @ModelAttribute("form") ActivityForm form, BindingResult bindingResult,
                         @RequestParam(value = "photos", required = false) List<MultipartFile> photos,
                         Model model, RedirectAttributes redirectAttributes) {
        if (!bindingResult.hasErrors()) {
            try {
                Activity activity = activityService.create(currentUser.getId(), form, photos);
                redirectAttributes.addFlashAttribute("successMessage", "La actividad \"" + activity.getName()
                        + "\" quedó publicada. Ahora cargá sus fechas y horarios para que se pueda reservar.");
                return "redirect:/provider/activities/" + activity.getId();
            } catch (FieldValidationException e) {
                reject(bindingResult, e);
            }
        }
        return showActivityForm(currentUser, null, model);
    }

    @GetMapping("/{id}/edit")
    public String edit(@AuthenticationPrincipal SecurityUser currentUser, @PathVariable Long id, Model model) {
        Activity activity = activityService.findOwnActivity(currentUser.getId(), id);
        model.addAttribute("form", activityService.toForm(activity));
        return showActivityForm(currentUser, activity, model);
    }

    @PostMapping("/{id}/edit")
    public String update(@AuthenticationPrincipal SecurityUser currentUser, @PathVariable Long id,
                         @Valid @ModelAttribute("form") ActivityForm form, BindingResult bindingResult,
                         @RequestParam(value = "photos", required = false) List<MultipartFile> photos,
                         Model model, RedirectAttributes redirectAttributes) {
        Activity activity = activityService.findOwnActivity(currentUser.getId(), id);
        if (!bindingResult.hasErrors()) {
            try {
                activityService.update(currentUser.getId(), id, form, photos);
                redirectAttributes.addFlashAttribute("successMessage", "Guardamos los cambios de la actividad.");
                return "redirect:/provider/activities/" + id;
            } catch (FieldValidationException e) {
                reject(bindingResult, e);
            } catch (IllegalStateException e) {
                redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
                return "redirect:/provider/activities/" + id;
            }
        }
        return showActivityForm(currentUser, activity, model);
    }

    // activity == null: alta; si no, edicion (el emprendimiento ya no se puede cambiar)
    private String showActivityForm(SecurityUser currentUser, Activity activity, Model model) {
        model.addAttribute("activity", activity);
        model.addAttribute("businesses", activityService.findPublishableBusinesses(currentUser.getId()));
        model.addAttribute("locationsByDepartment", locationService.findAllGroupedByDepartment());
        model.addAttribute("categories", categoryRepository.findAllByOrderByNameAsc());
        // Limites de las fotos, para los textos y los controles de app.js
        model.addAttribute("maxPhotos", Activity.MAX_PHOTOS);
        model.addAttribute("maxPhotoMb", ImageProcessor.MAX_BYTES / 1024 / 1024);
        return "provider/activity-form";
    }

    // ---------- Slots (CU-04) ----------

    // Pagina de gestion de la actividad: sus datos, sus slots y el formulario para agregar uno
    @GetMapping("/{id}")
    public String manage(@AuthenticationPrincipal SecurityUser currentUser, @PathVariable Long id, Model model) {
        model.addAttribute("slotForm", new SlotForm());
        return showManagePage(currentUser, id, model);
    }

    // Para los formularios de horarios: hasta que fecha se puede cargar y las opciones de repeticion
    @ModelAttribute("lastSlotDate")
    public LocalDate lastSlotDate() {
        return slotService.lastAllowedDate();
    }

    @ModelAttribute("repetitions")
    public SlotRepetition[] repetitions() {
        return SlotRepetition.values();
    }

    // Limites de la repeticion (SlotForm), como variables porque Thymeleaf no deja acceder a clases en th:data-*
    @ModelAttribute("maxMonthsAhead")
    public int maxMonthsAhead() {
        return SlotForm.MAX_MONTHS_AHEAD;
    }

    @ModelAttribute("suggestedRepeatMonths")
    public int suggestedRepeatMonths() {
        return SlotForm.SUGGESTED_REPEAT_MONTHS;
    }

    @ModelAttribute("maxSlotsPerRepetition")
    public int maxSlotsPerRepetition() {
        return SlotForm.MAX_SLOTS_PER_REPETITION;
    }

    @ModelAttribute("weekdayOptions")
    public List<WeekdayOption> weekdayOptions() {
        return Arrays.stream(DayOfWeek.values())
                .map(day -> new WeekdayOption(day, day.getDisplayName(TextStyle.SHORT, SPANISH).replace(".", "")))
                .toList();
    }

    // Un dia de la semana para los botones de "Ciertos dias de la semana" (ej. MONDAY, "lun")
    public record WeekdayOption(DayOfWeek day, String label) {
    }

    @PostMapping("/{id}/slots")
    public String addSlot(@AuthenticationPrincipal SecurityUser currentUser, @PathVariable Long id,
                          @Valid @ModelAttribute("slotForm") SlotForm slotForm, BindingResult bindingResult,
                          Model model, RedirectAttributes redirectAttributes) {
        if (!bindingResult.hasErrors()) {
            try {
                ProviderSlotService.AddedSlots added = slotService.addSlots(currentUser.getId(), id, slotForm);
                redirectAttributes.addFlashAttribute("successMessage", addedMessage(added));
                return "redirect:/provider/activities/" + id;
            } catch (FieldValidationException e) {
                reject(bindingResult, e);
            } catch (IllegalStateException e) {
                redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
                return "redirect:/provider/activities/" + id;
            }
        }
        return showManagePage(currentUser, id, model);
    }

    @GetMapping("/{id}/slots/{slotId}/edit")
    public String editSlot(@AuthenticationPrincipal SecurityUser currentUser, @PathVariable Long id,
                           @PathVariable Long slotId, Model model) {
        Slot slot = slotService.findOwnSlot(currentUser.getId(), id, slotId);
        SlotForm form = new SlotForm();
        form.setDate(slot.getStartTime().toLocalDate());
        form.setTime(slot.getStartTime().toLocalTime());
        form.setPrice(slot.getPrice());
        form.setMaxCapacity(slot.getMaxCapacity());
        model.addAttribute("slotForm", form);
        model.addAttribute("slot", slot);
        return "provider/slot-edit";
    }

    @PostMapping("/{id}/slots/{slotId}/edit")
    public String updateSlot(@AuthenticationPrincipal SecurityUser currentUser, @PathVariable Long id,
                             @PathVariable Long slotId,
                             @Valid @ModelAttribute("slotForm") SlotForm slotForm, BindingResult bindingResult,
                             Model model, RedirectAttributes redirectAttributes) {
        if (!bindingResult.hasErrors()) {
            try {
                slotService.updateSlot(currentUser.getId(), id, slotId, slotForm);
                redirectAttributes.addFlashAttribute("successMessage", "Guardamos los cambios del horario.");
                return "redirect:/provider/activities/" + id;
            } catch (FieldValidationException e) {
                reject(bindingResult, e);
            } catch (IllegalStateException e) {
                redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
                return "redirect:/provider/activities/" + id;
            } catch (ObjectOptimisticLockingFailureException e) {
                bindingResult.reject("concurrent", CONCURRENT_CHANGE);
            }
        }
        model.addAttribute("slot", slotService.findOwnSlot(currentUser.getId(), id, slotId));
        return "provider/slot-edit";
    }

    @PostMapping("/{id}/slots/{slotId}/cancel")
    public String cancelSlot(@AuthenticationPrincipal SecurityUser currentUser, @PathVariable Long id,
                             @PathVariable Long slotId, RedirectAttributes redirectAttributes) {
        try {
            int cancelledReservations = slotService.cancelSlot(currentUser.getId(), id, slotId);
            redirectAttributes.addFlashAttribute("successMessage", cancelledReservations == 0
                    ? "El horario quedó cancelado y ya no se muestra en el portal."
                    : "El horario quedó cancelado. Se cancelaron " + cancelledReservations
                    + " reservas con devolución total y se avisó a los turistas.");
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        } catch (ObjectOptimisticLockingFailureException e) {
            redirectAttributes.addFlashAttribute("errorMessage", CONCURRENT_CHANGE);
        }
        return "redirect:/provider/activities/" + id;
    }

    // Los slots se muestran en dos tablas: proximos (se pueden modificar o cancelar) y pasados
    private String showManagePage(SecurityUser currentUser, Long id, Model model) {
        LocalDateTime now = LocalDateTime.now(clock);
        Map<Boolean, List<Slot>> slotsByStarted = slotService.findSlots(currentUser.getId(), id).stream()
                .collect(Collectors.partitioningBy(slot -> slot.hasStarted(now)));
        Activity activity = activityService.findOwnActivity(currentUser.getId(), id);
        model.addAttribute("activity", activity);
        // Aviso si a esta actividad se le terminan los horarios (null si no)
        model.addAttribute("runningOut", slotService.findRunningOut(List.of(activity)).stream().findFirst().orElse(null));
        model.addAttribute("upcomingSlots", slotsByStarted.get(false));
        model.addAttribute("pastSlots", slotsByStarted.get(true).reversed());
        return "provider/activity-manage";
    }

    // "Agregamos el horario del ..." o, al repetir, "Agregamos 13 horarios a las 11:00, del 21/10/2026 al 13/01/2027."
    private static String addedMessage(ProviderSlotService.AddedSlots added) {
        List<Slot> created = added.created();
        Slot first = created.getFirst();
        String message = created.size() == 1
                ? "Agregamos el horario del " + first.getStartTime().format(DATE_TIME) + "."
                : "Agregamos " + created.size() + " horarios a las " + first.getStartTime().format(TIME) + ", del "
                + first.getStartTime().format(DATE) + " al " + created.getLast().getStartTime().format(DATE) + ".";
        if (added.skipped() > 0) {
            message += added.skipped() == 1
                    ? " Salteamos 1 fecha que ya tenía un horario a esa hora."
                    : " Salteamos " + added.skipped() + " fechas que ya tenían un horario a esa hora.";
        }
        return message;
    }

    // "photos" no es un campo de ActivityForm (las fotos llegan como archivos aparte): va como error general
    private static void reject(BindingResult bindingResult, FieldValidationException e) {
        e.getFieldErrors().forEach((field, message) -> {
            if ("photos".equals(field)) {
                bindingResult.reject("photos", message);
            } else {
                bindingResult.rejectValue(field, "invalid", message);
            }
        });
    }
}
