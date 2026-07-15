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
package inetsoft.web.wiz;

import inetsoft.web.wiz.service.UnsupportedDatasourceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

/**
 * Verifies that {@link WizControllerErrorHandler} maps a permission-denial {@code SecurityException}
 * thrown from a wiz controller to HTTP 403 — rather than the generic 500 it would produce without a
 * package-scoped advice (no other {@code @ControllerAdvice} covers {@code inetsoft.web.wiz}, and
 * {@code GlobalExceptionHandler} does not handle {@code SecurityException}).
 */
@Tag("core")
class WizControllerErrorHandlerTest {
   private MockMvc mvc;

   @BeforeEach
   void setUp() {
      mvc = standaloneSetup(new ThrowingController())
         .setControllerAdvice(new WizControllerErrorHandler())
         .build();
   }

   @Test
   void sreeSecurityExceptionMapsToForbidden() throws Exception {
      mvc.perform(get("/wiz-test/throw").param("type", "sree"))
         .andExpect(status().isForbidden())
         .andExpect(content().string(containsString("Forbidden")));
   }

   @Test
   void javaLangSecurityExceptionMapsToForbidden() throws Exception {
      mvc.perform(get("/wiz-test/throw").param("type", "lang"))
         .andExpect(status().isForbidden())
         .andExpect(content().string(containsString("Forbidden")));
   }

   /**
    * Confirms the shared advice benefits controllers with NO local catch-all of their own
    * (e.g. WorksheetAgentController, WorksheetGenerateController) — only DatasourceMetaApiController
    * has its own local override of this same handler, needed because a local
    * {@code @ExceptionHandler} always wins over this advice.
    */
   @Test
   void unsupportedDatasourceExceptionMapsToUnprocessableEntityWithDatasourceType() throws Exception {
      // Content negotiation here defaults to XML (no Accept header, no @ResponseBody producer
      // configured on the test double), so assert on substrings rather than exact JSON syntax —
      // mirrors the loose containsString checks the SecurityException cases above already use.
      mvc.perform(get("/wiz-test/throw").param("type", "unsupported"))
         .andExpect(status().isUnprocessableEntity())
         .andExpect(content().string(containsString("not supported")))
         .andExpect(content().string(containsString("datasourceType")))
         .andExpect(content().string(containsString("Mongo")));
   }

   @RestController
   private static class ThrowingController {
      @GetMapping("/wiz-test/throw")
      public String throwIt(@RequestParam("type") String type) throws Exception {
         if("sree".equals(type)) {
            throw new inetsoft.sree.security.SecurityException("denied");
         }

         if("unsupported".equals(type)) {
            throw new UnsupportedDatasourceException("MongoDB REST", "Mongo");
         }

         throw new java.lang.SecurityException("denied");
      }
   }
}
