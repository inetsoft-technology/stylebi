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
package inetsoft.report.io.viewsheet;

import inetsoft.report.composition.WorksheetService;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.io.ArabicTextUtil;
import inetsoft.test.*;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.SelectionListVSAssemblyInfo;
import inetsoft.util.script.ScriptException;
import inetsoft.web.viewsheet.event.OpenViewsheetEvent;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #82105: a SelectionList whose script fails on open/refresh must still get its cell
 * format, so the PDF/PNG/HTML export (and "expand selections" in any format) does not fail
 * with a NullPointerException in {@code SelectionListVSAssemblyInfo.getRowHeights}.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class
)
@SreeHome(importResources = "/inetsoft/graph/GraphRenderTest.zip")
@Tag("core")
@Tag("integration")
class SelectionListScriptFailureFormatTest {
   private static final String BAD = "noSuchFunction123();";
   private static final String LIST = "List82105";

   @BeforeEach
   void setUp() throws Exception {
      // ArabicTextUtilImpl is not on core's test classpath; the PDF writer needs it
      Field field = ArabicTextUtil.class.getDeclaredField("instance");
      field.setAccessible(true);
      savedArabic = field.get(null);
      field.set(null, new ArabicTextUtil() {
         @Override
         public boolean containsArabic(String text) {
            return false;
         }

         @Override
         public String createSubstituteString(String text) {
            return text;
         }

         @Override
         public char getMirrorCharacter(char c) {
            return c;
         }
      });

      savedExceptions = WorksheetService.ASSET_EXCEPTIONS.get();
      exceptions = new ArrayList<>();
      WorksheetService.ASSET_EXCEPTIONS.set(exceptions);
   }

   @AfterEach
   void tearDown() throws Exception {
      Field field = ArabicTextUtil.class.getDeclaredField("instance");
      field.setAccessible(true);
      field.set(null, savedArabic);
      WorksheetService.ASSET_EXCEPTIONS.set(savedExceptions);
   }

   /** The refresh that runs the failing script still formats the values. */
   @ParameterizedTest(name = "script=[{0}]")
   @CsvSource(delimiter = '|', value = { BAD, "var x = 1;", "''" })
   void failingScriptStillFormatsTheValues(String script) throws Exception {
      SelectionListVSAssemblyInfo info = addList(script);
      SelectionList slist = info.getSelectionList();
      assertNotNull(slist);
      SelectionValue[] values = slist.getAllSelectionValues();
      assertTrue(values.length > 0, "the list should have values");

      for(SelectionValue value : values) {
         assertNotNull(value.getFormat(), "format of " + value.getLabel());
      }

      List<Double> heights = assertDoesNotThrow(info::getRowHeights);
      assertFalse(heights.isEmpty());

      // the viewer still gets the script error, as before
      boolean scriptError = exceptions.stream().anyMatch(ex -> ex instanceof ScriptException);
      assertEquals(BAD.equals(script), scriptError, "recorded: " + exceptions);
   }

   /** PDF export of a sheet with the list, with and without "expand selections". */
   @ParameterizedTest(name = "expand selections {0}")
   @CsvSource({ "false", "true" })
   void pdfExportWithFailingListScript(boolean expand) throws Exception {
      addList(BAD);
      ViewsheetSandbox box = viewsheetResource.getRuntimeViewsheet().getViewsheetSandbox()
         .orElseThrow();
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      AbstractVSExporter exporter = (AbstractVSExporter)
         AbstractVSExporter.getVSExporter(FileFormatInfo.EXPORT_TYPE_PDF, null, out);
      exporter.setExpandSelections(expand);
      exporter.export(box, "Current View", 0, null);
      exporter.write();
      assertTrue(out.size() > 0, "the PDF should be written");
   }

   private SelectionListVSAssemblyInfo addList(String script) throws Exception {
      ViewsheetSandbox box = viewsheetResource.getRuntimeViewsheet().getViewsheetSandbox()
         .orElseThrow();
      Viewsheet vs = box.getViewsheet();

      // the other assemblies need SVG helpers that are not on core's test classpath
      for(Assembly assembly : vs.getAssemblies()) {
         vs.removeAssembly(assembly.getAbsoluteName());
      }

      SelectionListVSAssembly list = new SelectionListVSAssembly(vs, LIST);
      SelectionListVSAssemblyInfo info = (SelectionListVSAssemblyInfo) list.getInfo();
      info.setTableName("RegionalProductSales");
      ColumnRef ref = new ColumnRef(new AttributeRef(null, "Region"));
      ref.setDataType("string");
      info.setDataRef(ref);
      info.setScript(script);
      info.setScriptEnabled(true);
      // fires ADD_ASSEMBLY -> ViewsheetSandbox.reset0 -> executeView, the viewer refresh path
      vs.addAssembly(list);
      return (SelectionListVSAssemblyInfo) vs.getAssembly(LIST).getInfo();
   }

   private static OpenViewsheetEvent createOpenViewsheetEvent() {
      OpenViewsheetEvent event = new OpenViewsheetEvent();
      event.setEntryId("1^128^__NULL__^TEST_GraphRender");
      event.setViewer(true);
      return event;
   }

   @RegisterExtension
   RuntimeViewsheetExtension viewsheetResource =
      new RuntimeViewsheetExtension(createOpenViewsheetEvent());

   private Object savedArabic;
   private List<Exception> savedExceptions;
   private List<Exception> exceptions;
}
