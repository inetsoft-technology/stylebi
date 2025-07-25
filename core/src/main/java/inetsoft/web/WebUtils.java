/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

public final class WebUtils {
   private WebUtils() {
   }

   /**
    * Redirects to the error page with the specified message.
    *
    * @param response the HTTP response.
    * @param request  the HTTP request.
    * @param message  the error message to display.
    *
    * @throws IOException if an I/O error occurs.
    */
   public static void redirectToErrorPage(HttpServletResponse response, HttpServletRequest request,
                                          String message)
      throws IOException, ServletException
   {
      request.setAttribute("error", message);
      request.getRequestDispatcher("/common/error")
         .forward(request, response);
   }

   /**
    * Get the error message from the request.
    *
    * @param request the HTTP request.
    *
    * @return error message, or a default error message if not found.
    */
   public static String getErrorMessage(HttpServletRequest request) {
      String error = Catalog.getCatalog().getString("http.error.serverError");
      Object errorObj = request.getAttribute("error");

      if(errorObj instanceof String errorMessage && !Tool.isEmptyString(errorMessage)) {
         error = errorMessage;
      }

      return error;
   }
}
