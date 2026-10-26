package uy.edu.um.luminalabs.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import uy.edu.um.luminalabs.services.BusinessService;
import uy.edu.um.luminalabs.services.BusinessStatusCounts;

// Datos que necesita el menu lateral (y los indicadores del panel) en todas las paginas de administracion
@ControllerAdvice(assignableTypes = {AdminController.class, AdminUserController.class, AdminRatingController.class})
@RequiredArgsConstructor
public class AdminModelAttributes {

    private final BusinessService businessService;

    @ModelAttribute("statusCounts")
    public BusinessStatusCounts statusCounts() {
        return businessService.countByStatus();
    }
}
