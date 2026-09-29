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
import inetsoft.uql.viewsheet.ViewsheetInfo;
import inetsoft.util.CoreTool;
import inetsoft.util.UserMessage;
import inetsoft.web.viewsheet.event.OpenViewsheetEvent;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77322: a viewsheet onLoad/onInit script error reports the line of the user's
 * script in the message shown to the user, through the real
 * {@link ViewsheetSandbox} and {@link ViewsheetScope#execute} path. It used to report
 * line N + 1, e.g. "(line 2)" for a one-line {@code undefinedFn();}.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome(importResources = "ViewsheetScopeTest.vso")
@Tag("core")
@Tag("integration")
class ViewsheetScriptErrorLineTest {
   @RegisterExtension
   RuntimeViewsheetExtension viewsheetResource =
      new RuntimeViewsheetExtension(createOpenViewsheetEvent());

   private ViewsheetSandbox sandbox;

   @BeforeEach
   void setUp() {
      RuntimeViewsheet rvs = viewsheetResource.getRuntimeViewsheet();
      sandbox = rvs.getViewsheetSandbox().orElseThrow();
      sandbox.getViewsheet().getViewsheetInfo().setScriptEnabled(true);
      CoreTool.clearUserMessage();
   }

   @AfterEach
   void tearDown() {
      CoreTool.clearUserMessage();
   }

   @Test
   void onLoadOneLineErrorReportsLine1() throws Exception {
      String msg = runOnLoad("undefinedFn77322();");
      assertTrue(msg.contains("undefinedFn77322") && msg.endsWith("(line 1)"), msg);
   }

   @Test
   void onLoadMultiLineErrorReportsItsLine() throws Exception {
      String msg = runOnLoad("var a77322 = 1;\n\nundefinedFn77322b();");
      assertTrue(msg.endsWith("(line 3)"), msg);
   }

   @Test
   void onInitOneLineErrorReportsLine1() {
      sandbox.getViewsheet().getViewsheetInfo().setOnInit("undefinedFn77322c();");
      sandbox.processOnInit();
      String msg = userMessage();
      assertTrue(msg.contains("undefinedFn77322c") && msg.endsWith("(line 1)"), msg);
   }

   private String runOnLoad(String script) throws Exception {
      ViewsheetInfo info = sandbox.getViewsheet().getViewsheetInfo();
      info.setOnLoad(script);
      // the viewsheet was already loaded by the extension; run onLoad again
      Field executed = ViewsheetSandbox.class.getDeclaredField("onLoadExeced");
      executed.setAccessible(true);
      executed.setBoolean(sandbox, false);
      sandbox.processOnLoadIf();
      return userMessage();
   }

   private static String userMessage() {
      UserMessage message = CoreTool.getUserMessage();
      assertNotNull(message, "the script error was not reported to the user");
      return message.getMessage();
   }

   private static OpenViewsheetEvent createOpenViewsheetEvent() {
      OpenViewsheetEvent event = new OpenViewsheetEvent();
      event.setEntryId(ViewsheetScopeTest.ASSET_ID);
      event.setViewer(true);
      return event;
   }
}
