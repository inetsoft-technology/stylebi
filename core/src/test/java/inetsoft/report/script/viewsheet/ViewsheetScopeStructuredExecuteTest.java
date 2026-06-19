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
package inetsoft.report.script.viewsheet;

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.test.*;
import inetsoft.web.viewsheet.event.OpenViewsheetEvent;
import inetsoft.web.wiz.script.model.ScriptExecResult;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ViewsheetScope#executeStructured}.
 *
 * <p>Reuses the {@code ViewsheetScopeTest.vso} resource (same assembly library).</p>
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome(importResources = "ViewsheetScopeTest.vso")
@Tag("core")
@Tag("integration")
class ViewsheetScopeStructuredExecuteTest {

   @RegisterExtension
   RuntimeViewsheetExtension viewsheetResource =
      new RuntimeViewsheetExtension(createOpenViewsheetEvent());

   private ViewsheetScope viewsheetScope;

   @BeforeEach
   void setUp() throws Exception {
      RuntimeViewsheet rvs = viewsheetResource.getRuntimeViewsheet();
      ViewsheetSandbox sandbox = rvs.getViewsheetSandbox().orElseThrow();
      viewsheetScope = new ViewsheetScope(sandbox, false);
   }

   // -------------------------------------------------------------------------
   // Success path
   // -------------------------------------------------------------------------

   @Test
   void nullStatementReturnsSuccess() {
      ScriptExecResult result = viewsheetScope.executeStructured(null, null);
      assertTrue(result.ok());
      assertNull(result.value());
      assertNull(result.error());
   }

   @Test
   void emptyStatementReturnsSuccess() {
      ScriptExecResult result = viewsheetScope.executeStructured("", null);
      assertTrue(result.ok());
   }

   @Test
   void validArithmeticReturnsResult() {
      ScriptExecResult result = viewsheetScope.executeStructured("1 + 1", null);
      assertTrue(result.ok(), "expected ok but got error: " + result.error());
      assertNull(result.error());
   }

   // -------------------------------------------------------------------------
   // Error path
   // -------------------------------------------------------------------------

   @Test
   void syntaxErrorReturnsFailureWithMessage() {
      // intentionally broken JS
      ScriptExecResult result = viewsheetScope.executeStructured("function (", null);
      assertFalse(result.ok(), "expected failure for broken script");
      assertNotNull(result.error());
      assertNotNull(result.error().message());
      // message should describe a compilation or execution error
      String msg = result.error().message().toLowerCase();
      assertTrue(msg.contains("error"), "message should contain 'error': " + msg);
   }

   @Test
   void runtimeErrorReturnsFailureWithMessage() {
      // triggers a runtime error (access undefined.property throws TypeError)
      ScriptExecResult result = viewsheetScope.executeStructured(
         "var x = null; x.undefinedProperty;", null);
      assertFalse(result.ok());
      assertNotNull(result.error());
      assertNotNull(result.error().message());
   }

   @Test
   void errorDoesNotAffectSubsequentNormalExecute() throws Exception {
      // Run a broken script via structured (should NOT poison the scriptCache)
      viewsheetScope.executeStructured("function (", null);

      // The same broken script via normal execute() should still throw ScriptException
      // (not silently return null from a poisoned cache)
      assertThrows(Exception.class,
         () -> viewsheetScope.execute("function (", (String) null, false));
   }

   // -------------------------------------------------------------------------
   // Helper
   // -------------------------------------------------------------------------

   private static OpenViewsheetEvent createOpenViewsheetEvent() {
      OpenViewsheetEvent event = new OpenViewsheetEvent();
      event.setEntryId("1^128^__NULL__^ViewsheetScopeTest");
      event.setViewer(true);
      return event;
   }
}
