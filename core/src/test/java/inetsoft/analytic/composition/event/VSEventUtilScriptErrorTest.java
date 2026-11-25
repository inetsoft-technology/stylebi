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
package inetsoft.analytic.composition.event;

import inetsoft.report.composition.WorksheetService;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.test.*;
import inetsoft.uql.util.TableLoadException;
import inetsoft.uql.viewsheet.*;
import inetsoft.util.script.ScriptException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;

/**
 * Bug #82107: a component script that fails while a viewsheet is refreshed (a selection tree's
 * view refresh in {@code CoreLifecycleService.addDeleteVSObject}, the Scale to Screen pre-pass
 * in {@code CoreLifecycleService.refreshViewsheet}, or a text's script in
 * {@code VSEventUtil.applyScale}) must not abort the refresh. The failure is reported as a
 * non-blocking message through the request's asset exceptions, as {@code executeView} does,
 * while a stopped script and a table load failure still propagate.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class VSEventUtilScriptErrorTest {
   @BeforeEach
   void saveExceptions() {
      oldExceptions = WorksheetService.ASSET_EXCEPTIONS.get();
   }

   @AfterEach
   void restoreExceptions() {
      if(oldExceptions == null) {
         WorksheetService.ASSET_EXCEPTIONS.remove();
      }
      else {
         WorksheetService.ASSET_EXCEPTIONS.set(oldExceptions);
      }
   }

   @Test
   void scriptFailureIsRecordedNotThrown() {
      List<Exception> exs = new ArrayList<>();
      WorksheetService.ASSET_EXCEPTIONS.set(exs);
      ScriptException ex = new ScriptException(
         "Script execution error in assembly: SelectionTree1");

      assertDoesNotThrow(() -> VSEventUtil.tolerateScriptError(ex));
      assertEquals(List.of(ex), exs);
   }

   @Test
   void scriptFailureWithoutExceptionListIsIgnored() {
      WorksheetService.ASSET_EXCEPTIONS.remove();

      assertDoesNotThrow(() -> VSEventUtil.tolerateScriptError(
         new ScriptException("Script execution error in assembly: Text1")));
      assertNull(WorksheetService.ASSET_EXCEPTIONS.get());
   }

   @Test
   void stoppedScriptPropagates() {
      List<Exception> exs = new ArrayList<>();
      WorksheetService.ASSET_EXCEPTIONS.set(exs);
      ScriptException ex = new ScriptException("Script timed out");
      ex.setStopped(true);

      assertSame(ex, assertThrows(ScriptException.class,
                                  () -> VSEventUtil.tolerateScriptError(ex)));
      assertTrue(exs.isEmpty());
   }

   @Test
   void tableLoadFailurePropagates() {
      List<Exception> exs = new ArrayList<>();
      WorksheetService.ASSET_EXCEPTIONS.set(exs);
      TableLoadException load = new TableLoadException("Orders failed to load", null);
      ScriptException ex = new ScriptException("Script failed", load);

      assertSame(load, assertThrows(TableLoadException.class,
                                    () -> VSEventUtil.tolerateScriptError(ex)));
      assertTrue(exs.isEmpty());
   }

   /**
    * Scale to Screen runs every text's script; one failing script used to abort the scaling
    * and with it the whole viewsheet open.
    */
   @Test
   void failingTextScriptDoesNotAbortScaling() throws Exception {
      List<Exception> exs = new ArrayList<>();
      WorksheetService.ASSET_EXCEPTIONS.set(exs);

      Viewsheet vs = new Viewsheet();
      vs.getViewsheetInfo().setScaleToScreen(true);
      TextVSAssembly text1 = new TextVSAssembly(vs, "Text1");
      text1.setPixelOffset(new Point(10, 10));
      text1.setPixelSize(new Dimension(100, 20));
      TextVSAssembly text2 = new TextVSAssembly(vs, "Text2");
      text2.setPixelOffset(new Point(10, 100));
      text2.setPixelSize(new Dimension(100, 20));
      vs.addAssembly(text1);
      vs.addAssembly(text2);

      ScriptException ex = new ScriptException("Script execution error in assembly: Text1");
      ViewsheetSandbox box = Mockito.mock(ViewsheetSandbox.class);
      doThrow(ex).when(box).executeScript(
         argThat((VSAssembly assembly) -> assembly != null && "Text1".equals(assembly.getName())));

      assertDoesNotThrow(() -> VSEventUtil.applyScale(
         vs, new Point2D.Double(2, 2), false, null, 1000, 1000, box));

      assertEquals(List.of(ex), exs);
      assertEquals(200, text1.getVSAssemblyInfo().getLayoutSize(true).width,
                   "the text whose script failed is still scaled");
      assertEquals(200, text2.getVSAssemblyInfo().getLayoutSize(true).width,
                   "the other text is still scaled");
   }

   private List<Exception> oldExceptions;
}
