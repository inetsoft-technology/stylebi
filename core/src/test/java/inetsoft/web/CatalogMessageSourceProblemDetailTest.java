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
package inetsoft.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.test.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.method.annotation.ExceptionHandlerExceptionResolver;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77315: a {@link ResponseStatusException} handled by {@link GlobalExceptionHandler} must
 * keep its reason in the ProblemDetail {@code detail}. {@link CatalogMessageSource} used to echo
 * the unknown {@code problemDetail.*} message codes, so Spring replaced the detail, title and type
 * with the raw codes.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class CatalogMessageSourceProblemDetailTest {
   @BeforeEach
   void setUp() {
      context = new AnnotationConfigApplicationContext();
      // same bean name as WebConfig, so the advice gets it through MessageSourceAware
      context.registerBean("messageSource", CatalogMessageSource.class);
      context.registerBean(GlobalExceptionHandler.class);
      context.refresh();

      resolver = new ExceptionHandlerExceptionResolver();
      resolver.setApplicationContext(context);
      resolver.setMessageConverters(List.of(new MappingJackson2HttpMessageConverter()));
      resolver.afterPropertiesSet();
   }

   @AfterEach
   void tearDown() {
      context.close();
   }

   @Test
   void responseStatusExceptionKeepsReasonInDetail() throws Exception {
      String reason = "A theme that is not visible to all organizations can't be the default " +
         "for all organizations: aa";
      MockHttpServletRequest request =
         new MockHttpServletRequest("PUT", "/api/em/settings/presentation/themes/aa");
      MockHttpServletResponse response = new MockHttpServletResponse();
      HandlerMethod handler =
         new HandlerMethod(new TestController(), TestController.class.getMethod("save"));

      ModelAndView mav = resolver.resolveException(
         request, response, handler,
         new ResponseStatusException(HttpStatus.BAD_REQUEST, reason));

      assertNotNull(mav, "GlobalExceptionHandler should handle the exception");
      assertEquals(400, response.getStatus());

      JsonNode body = new ObjectMapper().readTree(response.getContentAsString());
      assertEquals(reason, body.path("detail").asText());
      assertEquals("Bad Request", body.path("title").asText());
      assertEquals("about:blank", body.path("type").asText());
   }

   @Test
   void unknownProblemDetailCodeResolvesToNull() {
      CatalogMessageSource source = context.getBean(CatalogMessageSource.class);

      assertNull(source.getMessage(
         "problemDetail.org.springframework.web.server.ResponseStatusException", null, null,
         Locale.getDefault()));
      assertNull(source.getMessage(
         "problemDetail.title.org.springframework.web.server.ResponseStatusException",
         new Object[] { "x" }, null, Locale.getDefault()));
   }

   @Test
   void otherUnknownCodeStillEchoesCode() {
      CatalogMessageSource source = context.getBean(CatalogMessageSource.class);

      assertEquals("bug77315.unknown.key",
                   source.getMessage("bug77315.unknown.key", null, Locale.getDefault()));
      assertEquals("bug77315.unknown.key",
                   source.getMessage("bug77315.unknown.key", new Object[] { "x" },
                                     Locale.getDefault()));
   }

   @Controller
   static class TestController {
      public void save() {
      }
   }

   private AnnotationConfigApplicationContext context;
   private ExceptionHandlerExceptionResolver resolver;
}
