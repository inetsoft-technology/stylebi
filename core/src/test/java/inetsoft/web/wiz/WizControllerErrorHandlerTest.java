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
import static org.junit.jupiter.api.Assertions.assertTrue;
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

   /**
    * The wiz agent services express every input-validation failure as an
    * {@code IllegalArgumentException} carrying a caller-facing message (~75 sites across
    * {@code inetsoft.web.wiz.viewsheet} alone). Without a handler those fall through to a generic
    * 500 and the message is lost, so an agent driving the tools sees only
    * "Request failed with status code 500" for what is really a fixable bad request.
    */
   @Test
   void illegalArgumentExceptionMapsToBadRequestCarryingTheMessage() throws Exception {
      mvc.perform(get("/wiz-test/throw").param("type", "illegal"))
         .andExpect(status().isBadRequest())
         .andExpect(content().string(containsString("requires 'height'")));
   }

   /**
    * A pairing session whose principal no longer owns the runtime sheet. StyleBI throws
    * {@code InvalidUserException} from {@code WorksheetEngine.getSheet}, naming two client session
    * ids — unreadable, and it surfaced as a bare Tomcat 500 HTML page with no message at all.
    *
    * <p>Worth its own status because the remedy is specific and the agent can act on it: re-pair.
    * A 500 tells it to retry, which will fail identically forever. Found live: reads kept working
    * (they resolve the sheet through the pairing lookup) while every write failed, so the session
    * looked healthy right up to the point of the first mutation.
    */
   @Test
   void invalidUserExceptionMapsToConflictTellingTheAgentToRepair() throws Exception {
      mvc.perform(get("/wiz-test/throw").param("type", "invaliduser"))
         .andExpect(status().isConflict())
         .andExpect(content().string(containsString("no longer owns")))
         .andExpect(content().string(containsString("pairing code")));
   }

   /**
    * A capability that is declared but not wired up must say so, not 500. Without this the
    * refusal added to set_column_labels would reach the caller as an opaque server error, which
    * reads as a bug rather than a missing feature — and the whole point of refusing was to let an
    * agent surface the gap.
    */
   @Test
   void unsupportedOperationMapsToNotImplementedCarryingTheMessage() throws Exception {
      mvc.perform(get("/wiz-test/throw").param("type", "unsupported-op"))
         .andExpect(status().isNotImplemented())
         .andExpect(content().string(containsString("not supported yet")));
   }

   /**
    * A body Jackson cannot read at all.
    *
    * <p>Spring's resolver retries an unmatched exception against its <em>cause</em>, so a
    * {@code @JsonCreator} that throws {@code IllegalArgumentException} is already answered with
    * its message by the handler above — the first version of this test asserted exactly that and
    * passed without any new code, which is what gave it away.
    *
    * <p>The real gap is a failure raised by Jackson itself: a field given the wrong shape, whose
    * cause is a {@code JsonProcessingException} and matches nothing. That returned a **bodyless
    * 400**, so the caller saw "Request failed with status code 400" and nothing else —
    * indistinguishable from a bug in the tool. Found live on local-1201, where the documented
    * {@code align: "center"} hit precisely this.
    */
   @Test
   void anUnreadableBodyReportsWhyRatherThanAnEmpty400() throws Exception {
      String content = mvc.perform(get("/wiz-test/throw").param("type", "unreadable"))
         .andExpect(status().isBadRequest())
         .andReturn().getResponse().getContentAsString();

      assertTrue(content.contains("could not be read"),
                 "the 400 must say the body was unreadable, got: [" + content + "]");
      assertTrue(content.contains("align"),
                 "and must name the field Jackson choked on, got: [" + content + "]");
   }

   /**
    * The captured composer error must reach the caller. {@code CommandErrorException} extends
    * {@code Exception} and had no handler, so the mechanism this whole feature exists to add —
    * turning a silently-dispatched composer ERROR into something the caller can see — captured
    * the text and then lost it to a generic 500 reading "Internal Server Error".
    *
    * <p>409 rather than 400: the request was well-formed and the composer refused it, which is a
    * conflict with the sheet's state, not a malformed call. {@code getErrors()} is surfaced as its
    * own field because the composer often reports several and joining them loses the boundaries.
    */
   @Test
   void commandErrorExceptionReportsTheCapturedComposerErrors() throws Exception {
      String content = mvc.perform(get("/wiz-test/throw").param("type", "commanderror"))
         .andExpect(status().isConflict())
         .andReturn().getResponse().getContentAsString();

      assertTrue(content.contains("dependency cycle"),
                 "the captured composer text must reach the caller, got: [" + content + "]");
      assertTrue(content.contains("name is already in use"),
                 "every captured error must survive, got: [" + content + "]");
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

         if("illegal".equals(type)) {
            throw new IllegalArgumentException("Edit op 'resize_title' requires 'height'.");
         }

         if("commanderror".equals(type)) {
            throw new inetsoft.web.wiz.dispatch.CommandErrorException(
               java.util.List.of("dependency cycle", "name is already in use"));
         }

         if("unreadable".equals(type)) {
            // Jackson's own failure, not ours: nothing in the cause chain is an
            // IllegalArgumentException, so no handler matched and the 400 came back empty.
            throw new org.springframework.http.converter.HttpMessageNotReadableException(
               "JSON parse error",
               com.fasterxml.jackson.databind.exc.MismatchedInputException.from(
                  (com.fasterxml.jackson.core.JsonParser) null, String.class,
                  "Cannot construct instance of AlignmentInfo from String value 'center' " +
                  "(field \"align\")"),
               new org.springframework.mock.http.MockHttpInputMessage(new byte[0]));
         }

         if("unsupported-op".equals(type)) {
            throw new UnsupportedOperationException(
               "Renaming column headers is not supported yet.");
         }

         if("invaliduser".equals(type)) {
            throw new inetsoft.util.InvalidUserException(
               "Invalid user found: Principal[Client[admin@172.18.0.1@a2f160d8]] instead of " +
               "Principal[Client[admin@172.18.0.1@ab0096b3]]", () -> "admin");
         }

         throw new java.lang.SecurityException("denied");
      }
   }
}
