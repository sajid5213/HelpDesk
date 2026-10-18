package com.helpdesk.common;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves the React app for its own routes, so reloading /tickets/11 works. The routes are listed
 * rather than caught all, so unknown paths stay 404 and /api is never shadowed.
 */
@Controller
class SpaController {

	@GetMapping({ "/login", "/tickets", "/tickets/{id:\\d+}" })
	String app() {
		return "forward:/index.html";
	}

}
