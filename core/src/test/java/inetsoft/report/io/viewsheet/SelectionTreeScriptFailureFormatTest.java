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
import inetsoft.test.*;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.SelectionTreeVSAssemblyInfo;
import inetsoft.util.script.ScriptException;
import inetsoft.web.viewsheet.event.OpenViewsheetEvent;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #82107: a SelectionTree whose script fails on open/refresh must still get its values
 * sorted and formatted. The viewer renders a tree cell only when it has a format, so without
 * it the tree showed no values at all. ID mode is covered by
 * {@code SelectionVSAQueryScriptFailureTest}.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class
)
@SreeHome(importResources = "/inetsoft/graph/GraphRenderTest.zip")
@Tag("core")
@Tag("integration")
class SelectionTreeScriptFailureFormatTest {
   private static final String BAD = "noSuchFunction123();";
   private static final String TREE = "Tree82107";

   @BeforeEach
   void setUp() {
      savedExceptions = WorksheetService.ASSET_EXCEPTIONS.get();
      exceptions = new ArrayList<>();
      WorksheetService.ASSET_EXCEPTIONS.set(exceptions);
   }

   @AfterEach
   void tearDown() {
      WorksheetService.ASSET_EXCEPTIONS.set(savedExceptions);
   }

   /** The refresh that runs the failing script still formats the values (column mode). */
   @ParameterizedTest(name = "script=[{0}]")
   @CsvSource(delimiter = '|', value = { BAD, "var x = 1;", "''" })
   void failingScriptStillFormatsTheValues(String script) throws Exception {
      SelectionTreeVSAssembly tree = addTree(script);
      assertFalse(tree.isIDMode());
      assertAllFormatted(tree, script);
   }

   private void assertAllFormatted(SelectionTreeVSAssembly tree, String script) {
      CompositeSelectionValue root = tree.getCompositeSelectionValue();
      assertNotNull(root, "the tree should have values");
      List<SelectionValue> values = new ArrayList<>();
      collect(root.getSelectionList(), values);
      assertFalse(values.isEmpty(), "the tree should have values");

      for(SelectionValue value : values) {
         assertNotNull(value.getFormat(), "format of " + value.getLabel());
      }

      // the viewer still gets the script error, as before
      boolean scriptError = exceptions.stream().anyMatch(ex -> ex instanceof ScriptException);
      assertEquals(BAD.equals(script), scriptError, "recorded: " + exceptions);
   }

   private static void collect(SelectionList list, List<SelectionValue> values) {
      if(list == null) {
         return;
      }

      for(SelectionValue value : list.getAllSelectionValues()) {
         values.add(value);

         if(value instanceof CompositeSelectionValue) {
            collect(((CompositeSelectionValue) value).getSelectionList(), values);
         }
      }
   }

   private SelectionTreeVSAssembly addTree(String script) throws Exception {
      ViewsheetSandbox box = viewsheetResource.getRuntimeViewsheet().getViewsheetSandbox()
         .orElseThrow();
      Viewsheet vs = box.getViewsheet();

      // the other assemblies need SVG helpers that are not on core's test classpath
      for(Assembly assembly : vs.getAssemblies()) {
         vs.removeAssembly(assembly.getAbsoluteName());
      }

      SelectionTreeVSAssembly tree = new SelectionTreeVSAssembly(vs, TREE);
      SelectionTreeVSAssemblyInfo info = (SelectionTreeVSAssemblyInfo) tree.getInfo();
      info.setTableName("RegionalProductSales");
      info.setDataRefs(new DataRef[] { col("Region"), col("Category") });

      info.setScript(script);
      info.setScriptEnabled(true);
      // fires ADD_ASSEMBLY -> ViewsheetSandbox.reset0 -> executeView, the viewer refresh path
      vs.addAssembly(tree);
      return (SelectionTreeVSAssembly) vs.getAssembly(TREE);
   }

   private static ColumnRef col(String name) {
      ColumnRef ref = new ColumnRef(new AttributeRef(null, name));
      ref.setDataType("string");
      return ref;
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

   private List<Exception> savedExceptions;
   private List<Exception> exceptions;
}
