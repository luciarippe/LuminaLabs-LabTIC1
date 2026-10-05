package uy.edu.um.luminalabs.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import uy.edu.um.luminalabs.security.SecurityUser;
import uy.edu.um.luminalabs.services.BusinessService;

// Espacio de gestion del prestador
@Controller
@RequestMapping("/provider")
@RequiredArgsConstructor
public class ProviderController {

    private final BusinessService businessService;

    @GetMapping
    public String dashboard(@AuthenticationPrincipal SecurityUser currentUser, Model model) {
        model.addAttribute("businesses", businessService.findByProvider(currentUser.getId()));
        return "provider/dashboard";
    }
}
