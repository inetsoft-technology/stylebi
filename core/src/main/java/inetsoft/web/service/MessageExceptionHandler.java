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
package inetsoft.web.service;

import inetsoft.util.MessageException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@ControllerAdvice(basePackages = {
   "inetsoft.web.adhoc", "inetsoft.web.binding", "inetsoft.web.composer"
})
public class MessageExceptionHandler {
   @ExceptionHandler(MessageException.class)
   @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
   @ResponseBody
   public ErrorInfo handleBadRequest(HttpServletRequest req, MessageException ex) {
      return new ErrorInfo(req.getRequestURL().toString(), ex.getMessage());
   }

   public static final class ErrorInfo {
      public ErrorInfo(String url, String message) {
         this.url = url;
         this.message = message;
      }

      public String getUrl() {
         return url;
      }

      public void setUrl(String url) {
         this.url = url;
      }

      public String getMessage() {
         return message;
      }

      public void setMessage(String message) {
         this.message = message;
      }

      private String url;
      private String message;
   }
}