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
package inetsoft.web;

import inetsoft.sree.portal.CustomThemesManager;
import inetsoft.util.Catalog;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.ModelAndView;

import java.security.Principal;

@Controller
public class ErrorController {
   @RequestMapping(
      value = "errors",
      method = RequestMethod.GET,
      produces = MediaType.TEXT_HTML_VALUE)
   public ModelAndView renderErrorPage(HttpServletRequest httpRequest, Principal principal) {
      Catalog catalog = Catalog.getCatalog(principal);
      ModelAndView errorPage = new ModelAndView("error/error-template");
      String errorTitle = "";
      String errorMsg = "";
      int httpErrorCode = getErrorCode(httpRequest);

      switch(httpErrorCode) {
      case 400:
         errorTitle = catalog.getString("Bad Request");
         errorMsg = catalog.getString("http.error.badRequest");
         break;
      case 401:
         errorTitle = catalog.getString("Unauthorized");
         errorMsg = catalog.getString("http.error.unauthorized");
         break;
      case 404:
         errorTitle = catalog.getString("Not Found");
         errorMsg = catalog.getString("http.error.notFound");
         break;
      case 500:
         errorTitle = catalog.getString("Server Error");
         errorMsg = catalog.getString("http.error.serverError");
         break;
      }

      errorPage.addObject("errorTitle", errorTitle);
      errorPage.addObject("errorMsg", errorMsg);
      errorPage.addObject("customTheme",
                          !"default".equals(CustomThemesManager.getManager().getSelectedTheme()));
      return errorPage;
   }

   private int getErrorCode(HttpServletRequest httpRequest) {
      return (Integer) httpRequest
         .getAttribute("jakarta.servlet.error.status_code");
   }

   /**
    * Shows the login page.
    * @return the error page model and view.
    */
   @RequestMapping(value = "common/error", method = { RequestMethod.GET, RequestMethod.POST })
   public ModelAndView showErrorPage(HttpServletRequest request) {
      final Catalog catalog = Catalog.getCatalog();
      final ModelAndView model = new ModelAndView("error/error-template");
      final String error = WebUtils.getErrorMessage(request);
      model.addObject("errorMsg", error);
      model.addObject("errorTitle", catalog.getString("Error"));
      model.addObject("customTheme",
                      !"default".equals(CustomThemesManager.getManager().getSelectedTheme()));
      return model;
   }
}
