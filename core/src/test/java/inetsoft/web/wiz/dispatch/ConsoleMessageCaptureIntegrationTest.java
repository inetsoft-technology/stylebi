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
package inetsoft.web.wiz.dispatch;

import inetsoft.report.composition.ChangedAssemblyList;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.test.*;
import inetsoft.uql.viewsheet.*;
import inetsoft.web.viewsheet.event.OpenViewsheetEvent;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #82316 on a real sandbox: an assemblyMain script of a Text and of a Chart that fails during
 * a viewsheet reset (what a render and a refresh both run) lands in the console buffer as INFO,
 * with no refresh warning collector in play.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome(importResources = "/inetsoft/report/composition/EmbeddedVS1.zip")
@Tag("core")
@Tag("integration")
class ConsoleMessageCaptureIntegrationTest {
   @Test
   void aChartScriptFailureDuringAResetIsRecordedUnderTheScope() throws Exception {
      Viewsheet vs = box().getViewsheet();
      addScripted(new ChartVSAssembly(vs, "B5Chart"), vs, "var x = noSuchFunctionB7();");

      ConsoleMessageCapture.scope(runtimeId(), () -> {
         reset();
         return null;
      });

      ConsoleMessageBuffer.Snapshot snapshot =
         ConsoleMessageCapture.buffer(runtimeId()).read(null, 200, 0);
      assertEquals(1, snapshot.entries().size(), snapshot.entries().toString());
      ConsoleMessageBuffer.Entry entry = snapshot.entries().get(0);
      assertEquals("INFO", entry.type());
      assertEquals("B5Chart", entry.assembly());
      assertTrue(entry.message().contains("noSuchFunctionB7 is not defined"), entry.message());
   }

   /** Positive control: the same reset with no scope records nothing, the original symptom. */
   @Test
   void theSameResetWithoutAScopeRecordsNothing() throws Exception {
      Viewsheet vs = box().getViewsheet();
      addScripted(new ChartVSAssembly(vs, "B5Chart"), vs, "var x = noSuchFunctionB7();");

      try {
         reset();
      }
      finally {
         inetsoft.report.composition.WorksheetService.ASSET_EXCEPTIONS.remove();
      }

      assertEquals(0, ConsoleMessageCapture.buffer(runtimeId()).read(null, 200, 0).entries().size());
   }

   @Test
   void aTextScriptFailureAndANestedRefreshCollectorBothFeedTheBuffer() throws Exception {
      Viewsheet vs = box().getViewsheet();
      addScripted(new TextVSAssembly(vs, "T"), vs, "text = missingFn(value);");

      ConsoleMessageCapture.scope(runtimeId(), () ->
         AssetScriptFailureWarnings.collect(() -> {
            reset();
            return new java.util.ArrayList<>();
         }));

      ConsoleMessageBuffer.Snapshot snapshot =
         ConsoleMessageCapture.buffer(runtimeId()).read(null, 200, 0);
      assertEquals(1, snapshot.entries().size(), snapshot.entries().toString());
      assertEquals("T", snapshot.entries().get(0).assembly());
   }

   private void reset() throws Exception {
      ViewsheetSandbox box = box();
      box.reset(null, box.getViewsheet().getAssemblies(), new ChangedAssemblyList(), true, true,
                null);
   }

   private String runtimeId() {
      return vsResource.getRuntimeViewsheet().getID();
   }

   private ViewsheetSandbox box() {
      RuntimeViewsheet rvs = vsResource.getRuntimeViewsheet();
      return rvs.getViewsheetSandbox().orElseThrow();
   }

   private static void addScripted(VSAssembly assembly, Viewsheet vs, String script) {
      assembly.getVSAssemblyInfo().setScript(script);
      assembly.getVSAssemblyInfo().setScriptEnabled(true);
      vs.addAssembly(assembly);
   }

   private static OpenViewsheetEvent openViewsheetEvent() {
      OpenViewsheetEvent event = new OpenViewsheetEvent();
      event.setEntryId("1^128^__NULL__^EmbeddedVS1^host-org");
      event.setViewer(true);
      return event;
   }

   @RegisterExtension
   RuntimeViewsheetExtension vsResource = new RuntimeViewsheetExtension(openViewsheetEvent());
}
