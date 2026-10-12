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
package inetsoft.report.composition.execution;

import inetsoft.report.composition.ChangedAssemblyList;
import inetsoft.report.io.viewsheet.excel.CSVVSExporter;
import inetsoft.test.*;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.util.Catalog;
import inetsoft.util.ThreadContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;

import static inetsoft.report.composition.execution.EmbeddedOnLoadFixture.ONLOAD;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #78255: a failing onLoad script of an embedded (or nested embedded) viewsheet never put
 * the "onLoad script failed" warning on the export. {@code prepareForExport()} re-ran only the
 * exported box's own onLoad with the capture handler; the embedded onLoad scripts run only from
 * the host reset, which swallows their failures, so the exporters, which read only the
 * exported box's flag, never saw them.
 *
 * <p>The tests run the real {@code AbstractVSExporter.export} (prepareSheet,
 * addScriptErrorMessage, the warning text write) through a CSV exporter that records the
 * texts it writes. The PDF/Excel/HTML writers need classes of inetsoft-xml-formats, which
 * core's test classpath lacks; every format shares this warning path.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ViewsheetSandboxEmbeddedOnLoadExportWarningTest {
   private static final String CHILD_THROWS = "throw 'CHILDX';";
   private Principal savedPrincipal;
   private EmbeddedOnLoadFixture f;

   @BeforeEach
   void setUp() throws Exception {
      savedPrincipal = ThreadContext.getContextPrincipal();
      f = new EmbeddedOnLoadFixture();
   }

   @AfterEach
   void cleanup() {
      f.dispose();
      ThreadContext.setContextPrincipal(savedPrincipal);
   }

   // the reporter's case: the host has no onLoad, the embedded child's onLoad throws
   @Test
   void childOnLoadFailureShowsWarning() throws Exception {
      ViewsheetSandbox box = f.childOnLoad(CHILD_THROWS).viewer();

      assertEquals(1, countWarnings(export(box)));
      assertNotNull(box.getExportScriptError());
   }

   // the scheduled export opens a fresh box with resetAll
   @Test
   void childOnLoadFailureShowsWarningAfterResetAll() throws Exception {
      f.childOnLoad(CHILD_THROWS);
      ViewsheetSandbox box = f.sandbox();
      box.resetAll(new ChangedAssemblyList());

      assertEquals(1, countWarnings(export(box)));
   }

   @Test
   void nestedGrandchildOnLoadFailureShowsWarning() throws Exception {
      ViewsheetSandbox box = f.nested().grandOnLoad("throw 'GRANDX';").viewer();

      assertEquals(1, countWarnings(export(box)));
      assertTrue(String.valueOf(box.getExportScriptError()).contains("GRANDX"),
                 () -> "flag: " + box.getExportScriptError());
   }

   // the host onLoad changes a variable, so its reset re-runs the child onLoad inside the
   // export with no handler; the failure must still reach the warning
   @Test
   void childOnLoadFailureShowsWarningWhenHostOnLoadChangesVariable() throws Exception {
      ViewsheetSandbox box = f.wrapperOnLoad(ONLOAD).childOnLoad(CHILD_THROWS).viewer();

      assertEquals(1, countWarnings(export(box)));
      assertEquals("P=ONLOADW", f.text(box, "Viewsheet1"),
                   "the child still sees the host onLoad's parameter (#78222)");
   }

   // both fail: one warning, the host's failure wins
   @Test
   void hostAndChildFailureShowOneWarning() throws Exception {
      ViewsheetSandbox box = f.wrapperOnLoad("throw 'HOSTX';").childOnLoad(CHILD_THROWS).viewer();

      assertEquals(1, countWarnings(export(box)));
      assertTrue(String.valueOf(box.getExportScriptError()).contains("HOSTX"),
                 () -> "flag: " + box.getExportScriptError());
   }

   @Test
   void succeedingChildOnLoadShowsNoWarning() throws Exception {
      ViewsheetSandbox box = f.childOnLoad("parameter.c = 1;").viewer();

      assertEquals(0, countWarnings(export(box)));
      assertNull(box.getExportScriptError());
   }

   // the flag is export-scoped: a failure from the viewer open alone is not reported
   @Test
   void childFailureOnlyAtOpenShowsNoWarning() throws Exception {
      ViewsheetSandbox box = f.childOnLoad(
         "if(parameter.ok == null) { parameter.ok = 1; throw 'CHILDX'; }").viewer();

      assertEquals(0, countWarnings(export(box)));
   }

   @Test
   void scriptDisabledChildShowsNoWarning() throws Exception {
      f.child.getViewsheetInfo().setScriptEnabled(false);
      ViewsheetSandbox box = f.childOnLoad(CHILD_THROWS).viewer();

      assertEquals(0, countWarnings(export(box)));
   }

   // a second export of the same box re-evaluates and warns again, once
   @Test
   void secondExportOfSameBoxWarnsAgain() throws Exception {
      ViewsheetSandbox box = f.childOnLoad(CHILD_THROWS).viewer();

      assertEquals(1, countWarnings(export(box)));
      assertEquals(1, countWarnings(export(box)));
   }

   private static List<String> export(ViewsheetSandbox box) throws Exception {
      RecordingExporter exporter = new RecordingExporter();
      exporter.export(box, "Current View", 0, null);
      return exporter.texts;
   }

   private static long countWarnings(List<String> texts) {
      String message = Catalog.getCatalog().getString("vs.export.onLoadScriptFailed");
      return texts.stream().filter(text -> text != null && text.contains(message)).count();
   }

   private static final class RecordingExporter extends CSVVSExporter {
      @Override
      protected void writeText(VSAssembly assembly, String txt) {
         texts.add(txt);
      }

      private final List<String> texts = new ArrayList<>();
   }
}
