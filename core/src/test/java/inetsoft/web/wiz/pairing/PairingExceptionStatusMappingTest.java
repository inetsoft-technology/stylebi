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
package inetsoft.web.wiz.pairing;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;

/**
 * N3 trap: every {@code @ExceptionHandler(PairingException.class)} in the wiz controllers switches
 * on {@link PairingException.Kind} with {@code default -> BAD_REQUEST}, so a new Kind that one of
 * them misses silently answers 400. This finds every such handler by scanning, so a new handler is
 * covered the day it is added, and asserts every Kind maps to its intended status in each.
 */
@Tag("core")
class PairingExceptionStatusMappingTest {
   private static final Map<PairingException.Kind, HttpStatus> EXPECTED =
      new EnumMap<>(PairingException.Kind.class);

   static {
      EXPECTED.put(PairingException.Kind.INVALID_ARGUMENT, HttpStatus.BAD_REQUEST);
      EXPECTED.put(PairingException.Kind.SESSION_EXPIRED, HttpStatus.NOT_FOUND);
      EXPECTED.put(PairingException.Kind.USER_MISMATCH, HttpStatus.FORBIDDEN);
      EXPECTED.put(PairingException.Kind.FEATURE_DISABLED, HttpStatus.FORBIDDEN);
      EXPECTED.put(PairingException.Kind.VIEWER_MODE, HttpStatus.FORBIDDEN);
      EXPECTED.put(PairingException.Kind.RATE_LIMITED, HttpStatus.TOO_MANY_REQUESTS);
      EXPECTED.put(PairingException.Kind.INTERNAL, HttpStatus.INTERNAL_SERVER_ERROR);
   }

   @Test
   void theExpectedTableCoversEveryKind() {
      for(PairingException.Kind kind : PairingException.Kind.values()) {
         assertTrue(EXPECTED.containsKey(kind),
                    "add " + kind + " to EXPECTED and to every PairingException handler");
      }
   }

   @Test
   void everyHandlerMapsEveryKindToItsIntendedStatus() throws Exception {
      Map<String, Method> handlers = findHandlers();

      // the ten known today; a new one is picked up by the scan and checked like the rest
      assertTrue(handlers.size() >= 10, "found only " + handlers.keySet());

      for(Map.Entry<String, Method> e : handlers.entrySet()) {
         Class<?> type = e.getValue().getDeclaringClass();
         Object target = mock(type, withSettings().defaultAnswer(CALLS_REAL_METHODS));
         e.getValue().setAccessible(true);

         for(PairingException.Kind kind : PairingException.Kind.values()) {
            PairingException ex = new PairingException(kind, "msg " + kind);
            ResponseEntity<?> response = (ResponseEntity<?>) e.getValue().invoke(target, ex);
            assertEquals(EXPECTED.get(kind), HttpStatus.valueOf(response.getStatusCode().value()),
                         e.getKey() + " maps " + kind + " wrongly");
            Map<?, ?> body = (Map<?, ?>) response.getBody();
            assertEquals(kind.name(), body.get("errorCode"), e.getKey());
            assertEquals("msg " + kind, body.get("error"), e.getKey());
         }
      }
   }

   private static Map<String, Method> findHandlers() throws ClassNotFoundException {
      ClassPathScanningCandidateComponentProvider scanner =
         new ClassPathScanningCandidateComponentProvider(false);
      scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
      Map<String, Method> found = new TreeMap<>();

      for(BeanDefinition def : scanner.findCandidateComponents("inetsoft.web.wiz")) {
         Class<?> type = Class.forName(def.getBeanClassName());

         for(Method m : type.getDeclaredMethods()) {
            ExceptionHandler h = m.getAnnotation(ExceptionHandler.class);

            if(h != null && Arrays.asList(h.value()).contains(PairingException.class)) {
               found.put(type.getSimpleName() + "#" + m.getName(), m);
            }
         }
      }

      return found;
   }
}
