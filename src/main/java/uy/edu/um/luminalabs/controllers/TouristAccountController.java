package uy.edu.um.luminalabs.controllers;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import uy.edu.um.luminalabs.security.SessionUserRefresher;
import uy.edu.um.luminalabs.services.AccountService;

// "Mi cuenta" del turista (solo ROLE_TOURIST, ver SecurityConfig)
@Controller
@RequestMapping(TouristAccountController.PATH)
public class TouristAccountController extends AccountPageController {

    static final String PATH = "/account";

    public TouristAccountController(AccountService accountService, SessionUserRefresher sessionUserRefresher) {
        super(accountService, sessionUserRefresher);
    }

    @Override
    protected String basePath() {
        return PATH;
    }

    @Override
    protected String viewName() {
        return "account/index";
    }

    @Override
    protected String emailHint() {
        return "Acá te llegan las confirmaciones de tus reservas.";
    }
}
