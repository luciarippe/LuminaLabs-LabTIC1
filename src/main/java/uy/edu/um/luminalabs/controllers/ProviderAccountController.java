package uy.edu.um.luminalabs.controllers;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import uy.edu.um.luminalabs.security.SessionUserRefresher;
import uy.edu.um.luminalabs.services.AccountService;

// "Mi cuenta" del prestador (dentro de /provider/**, solo ROLE_PROVIDER, ver SecurityConfig)
@Controller
@RequestMapping(ProviderAccountController.PATH)
public class ProviderAccountController extends AccountPageController {

    static final String PATH = "/provider/account";

    public ProviderAccountController(AccountService accountService, SessionUserRefresher sessionUserRefresher) {
        super(accountService, sessionUserRefresher);
    }

    @Override
    protected String basePath() {
        return PATH;
    }

    @Override
    protected String viewName() {
        return "provider/account";
    }

    @Override
    protected String emailHint() {
        return "Acá te avisamos sobre tus emprendimientos y las reservas de tus actividades.";
    }
}
