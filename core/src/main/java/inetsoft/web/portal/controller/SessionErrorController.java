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

import inetsoft.sree.portal.CustomThemesManager;
import inetsoft.sree.portal.PortalThemesManager;
import inetsoft.util.Catalog;
import inetsoft.web.factory.RemainingPath;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;

@Controller
public class SessionErrorController {
   /**
    * Shows the login page.
    * @return the error page model and view.
    */
   @RequestMapping(value = "/error/**", method = { RequestMethod.GET, RequestMethod.POST })
   public ModelAndView showErrorPage(@RemainingPath String path) {
      final Catalog catalog = Catalog.getCatalog();
      final ModelAndView model = new ModelAndView("error/error-template");
      final String error;
      String title = catalog.getString("Error");

      switch(path) {
         case NO_COMPOSER_LICENSE:
            error = catalog.getString("composer.license.error");
            break;
         case INVALID_LICENSE:
            error = catalog.getString("dhtmlservice.invalidkey.componentError");
            break;
         case REMOTE_DEVELOPER_LICENSE:
            error = catalog.getString("common.sree.license");
            break;
         case SESSIONS_EXCEEDED:
            error = catalog.getString("common.sessionsExceed");
            title = catalog.getString("common.sessionsExceedTitle");
            break;
         case NAMED_USER_WITHOUT_SECURITY:
            error = catalog.getString("common.namedUserWithoutSecurity");
            break;
         case GOOGLE_USER_SIGN_UP_DENIED:
            error = catalog.getString("common.noPermission");
            break;
         case USER_INACTIVE:
            error = catalog.getString("em.common.security.user.inactive");
            break;
         default:
            error = "";
            break;
      }

      final String contactAdmin = catalog.getString("common.contactAdmin");
      final String errorMsg = String.format("%s %s", error, contactAdmin);
      model.addObject("errorMsg", errorMsg);
      model.addObject("errorTitle", title);
      model.addObject("customTheme",
                      !"default".equals(CustomThemesManager.getManager().getSelectedTheme()));
      return model;
   }

   public static final String NO_COMPOSER_LICENSE = "no-composer-license";
   public static final String INVALID_LICENSE = "invalid-license";
   public static final String REMOTE_DEVELOPER_LICENSE = "remote-developer-license";
   public static final String SESSIONS_EXCEEDED = "sessions-exceeded";
   public static final String NAMED_USER_WITHOUT_SECURITY = "named-user-without-security";
   public static final String GOOGLE_USER_SIGN_UP_DENIED = "google-user-sign-up-denied";
   public static final String USER_INACTIVE = "user-inactive";
}
