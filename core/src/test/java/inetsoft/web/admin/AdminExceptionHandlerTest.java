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
package inetsoft.web.admin;

import inetsoft.util.MessageException;
import inetsoft.util.log.LogLevel;
import inetsoft.util.log.LogManager;
import inetsoft.web.admin.security.SecurityService.PreMutationRefusalException;
import inetsoft.web.security.auth.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #76855 follow-up: SecurityService.updateX wraps its precondition failures in
 * PreMutationRefusalException; the handler must map the wrapped cause back to the status the
 * unwrapped exception produced instead of reporting a 500.
 */
@Tag("core")
class AdminExceptionHandlerTest {
   @BeforeEach
   void setUp() {
      logManager = mock(LogManager.class);
      handler = new AdminExceptionHandler(logManager);
   }

   @Test
   void unauthorizedCauseMapsTo401() {
      ResponseEntity<Object> response =
         handler.handlePreMutationRefusal(wrap(new UnauthorizedAccessException("denied")));

      assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
      assertInstanceOf(UnauthorizedAccessError.class, response.getBody());
   }

   @Test
   void missingResourceCauseMapsTo404() {
      ResponseEntity<Object> response =
         handler.handlePreMutationRefusal(wrap(new MissingResourceException("user1")));

      assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
      assertInstanceOf(MissingResourceError.class, response.getBody());
   }

   @Test
   void invalidResourceCauseMapsTo405() {
      ResponseEntity<Object> response =
         handler.handlePreMutationRefusal(wrap(new InvalidResourceException()));

      assertEquals(HttpStatus.METHOD_NOT_ALLOWED, response.getStatusCode());
      assertInstanceOf(InvalidResourceError.class, response.getBody());
   }

   @Test
   void resourceExistsCauseMapsTo409() {
      ResponseEntity<Object> response =
         handler.handlePreMutationRefusal(wrap(new ResourceExistsException("user1")));

      assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
      assertInstanceOf(ResourceExistsError.class, response.getBody());
   }

   @Test
   void messageExceptionCauseUsesItsOwnLogLevel() {
      MessageException cause = new MessageException("bad role", LogLevel.WARN);
      ResponseEntity<Object> response = handler.handlePreMutationRefusal(wrap(cause));

      assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
      assertEquals("bad role", ((GenericError) response.getBody()).getMessage());
      verify(logManager).logException(any(Logger.class), eq(LogLevel.WARN), eq("bad role"), any());
   }

   @Test
   void refusalWithoutCauseStays500() {
      ResponseEntity<Object> response =
         handler.handlePreMutationRefusal(new PreMutationRefusalException("no admin left"));

      assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
      assertInstanceOf(GenericError.class, response.getBody());
   }

   private static PreMutationRefusalException wrap(Exception cause) {
      return new PreMutationRefusalException(cause.getMessage(), cause);
   }

   private LogManager logManager;
   private AdminExceptionHandler handler;
}
