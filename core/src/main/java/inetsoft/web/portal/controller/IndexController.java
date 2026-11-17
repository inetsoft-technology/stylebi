/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package inetsoft.web.portal.controller;

import inetsoft.sree.internal.SUtil;
import inetsoft.sree.portal.CustomThemesManager;
import inetsoft.sree.security.Organization;
import inetsoft.web.portal.ShellDarkMode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class IndexController {
   @GetMapping({"/", "/index.html"})
   public String home(HttpServletRequest request, Model model) {
      // The workspace chooser is reached from the portal's Home link and before signing in, so it
      // resolves the shell mode the way the login page does: the mode the portal last resolved
      // for this browser, otherwise the organization default.
      boolean customTheme = LoginController.isCustomTheme(request, CustomThemesManager.getManager());
      String orgID = SUtil.isMultiTenant() ? SUtil.getLoginOrganization(request) :
         Organization.getDefaultOrganizationID();
      model.addAttribute("shellDark", ShellDarkMode.isLoginDark(request, orgID, customTheme));
      return "index";
   }
}
