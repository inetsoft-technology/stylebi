/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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

import inetsoft.util.log.LogManager;
import inetsoft.web.security.auth.ResourceExistsException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

/**
 * Bug #75342, the portal schedule save answers a duplicate task name with HTTP 409 and the
 * message in the body (read by the portal editor as error.error.message), not the generic 500
 * handler that logs an error with a stack trace.
 */
@Tag("core")
class ControllerErrorHandlerResourceExistsTest {
   @Test
   void resourceExistsIsConflictWithMessage() {
      ControllerErrorHandler handler = new ControllerErrorHandler(mock(LogManager.class));
      ResponseEntity<Map<String, String>> response = handler.handleResourceExistsException(
         new ResourceExistsException("The specified task name exists."));

      assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
      assertEquals("The specified task name exists.", response.getBody().get("message"));
      assertEquals("resourceExists", response.getBody().get("error"));
   }
}
