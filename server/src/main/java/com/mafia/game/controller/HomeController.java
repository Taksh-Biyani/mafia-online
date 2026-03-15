package com.mafia.game.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Controller for serving the main game interface.
 */
@Controller
public class HomeController {

    /**
     * Serves the main game page.
     *
     * @return the name of the view (index.html)
     */
    @GetMapping("/")
    public String index() {
        return "forward:/index.html";
    }
}
