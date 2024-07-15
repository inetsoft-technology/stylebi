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
package inetsoft.web.vswizard;

import inetsoft.util.MessageException;
import inetsoft.util.log.LogManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.HashMap;
import java.util.Map;

@ControllerAdvice(basePackages = {
   "inetsoft.web.vswizard", "inetsoft.web.binding", "inetsoft.web.viewsheet"
})
public class VSWizardErrorHandler {

   @ExceptionHandler(MessageException.class)
   public ResponseEntity<Map<String, String>> handleMessageException(MessageException e) {
      MessageException thrown = e.isDumpStack() ? e : null;
      LogManager.getInstance().logException(LOG, e.getLogLevel(), e.getMessage(), thrown);

      Map<String, String> payload = new HashMap<>();
      payload.put("error", "messageException");
      payload.put("message", e.getMessage());
      return new ResponseEntity<>(payload, null, HttpStatus.INTERNAL_SERVER_ERROR);
   }

   private static final Logger LOG = LoggerFactory.getLogger(VSWizardErrorHandler.class);
}
