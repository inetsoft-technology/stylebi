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
package inetsoft.test;

import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.web.WebConfig;
import inetsoft.web.adhoc.DecodeArgumentResolver;
import inetsoft.web.factory.RemainingPathResolver;
import inetsoft.web.viewsheet.service.LinkUriArgumentResolver;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class PenetrationTestsExtension implements BeforeEachCallback {
   @Override
   public void beforeEach(ExtensionContext context) throws Exception {
      Class<?> testClass = context.getRequiredTestClass();
      Object testInstance = context.getRequiredTestInstance();
      Field mockMvcField = null;
      WebConfig config = null;
      ObjectMapper objectMapper = null;

      for(Field field : testClass.getDeclaredFields()) {
         if(MockMvc.class.isAssignableFrom(field.getType())) {
            mockMvcField = field;
         }
         else if(ObjectMapper.class.isAssignableFrom(field.getType())) {
            if(config == null) {
               config = new WebConfig();
            }

            if(objectMapper == null) {
               objectMapper = config.objectMapper();
            }

            field.setAccessible(true);
            field.set(testInstance, objectMapper);
         }
      }

      if(mockMvcField != null) {
         if(config == null) {
            config = new WebConfig();
         }

         if(objectMapper == null) {
            objectMapper = config.objectMapper();
         }

         Object[] controllers = { objectMapper };

         for(Method method : testClass.getDeclaredMethods()) {
            if(method.isAnnotationPresent(PenetrationTests.ControllerFactory.class)) {
               if(method.getParameterCount() == 0) {
                  method.setAccessible(true);
                  Object controllerInstance = method.invoke(testInstance);
                  Object[] swap = new Object[controllers.length + 1];
                  swap[0] = controllerInstance;
                  System.arraycopy(controllers, 0, swap, 1, controllers.length);
                  controllers = swap;
               }
               else  {
                  throw new IllegalStateException(
                     "Controller factory methods can have no parameters");
               }
            }
         }

         MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(controllers)
            .setControllerAdvice(new TestExceptionHandler())
            .setMessageConverters(config.jsonMessageConverter())
            .setCustomArgumentResolvers(
               new DecodeArgumentResolver(), new RemainingPathResolver(),
               new LinkUriArgumentResolver())
            .build();
         mockMvcField.setAccessible(true);
         mockMvcField.set(testInstance, mockMvc);
      }
   }

   @ControllerAdvice(basePackages = "inetsoft.web")
   static final class TestExceptionHandler {
      @ExceptionHandler
      @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
      public void handleException(Throwable e) {
         e.printStackTrace();
      }
   }
}
