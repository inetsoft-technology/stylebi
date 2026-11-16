/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

import inetsoft.report.composition.FormTableRow;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.report.script.TableArray;
import inetsoft.report.script.formula.AssetQueryScope;
import inetsoft.test.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;
import inetsoft.util.script.JavaScriptEngine;
import inetsoft.web.viewsheet.event.OpenViewsheetEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.Tag;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import javax.imageio.ImageIO;
import java.awt.*;
import java.net.URL;
import java.security.Principal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome(importResources = "ViewsheetScopeTest.vso")
@Tag("core")
@Tag("integration")
public class ViewsheetScopeTest {
   @RegisterExtension
   RuntimeViewsheetExtension viewsheetResource =
      new RuntimeViewsheetExtension(createOpenViewsheetEvent());

   private ViewsheetScope viewsheetScope;
   private ViewsheetSandbox sandbox;

   @BeforeEach
   void setUp() throws Exception {
      openMocks(this);
      RuntimeViewsheet rvs = viewsheetResource.getRuntimeViewsheet();
      sandbox = rvs.getViewsheetSandbox().orElseThrow();
      viewsheetScope = new ViewsheetScope(sandbox, false);
   }

   private XEmbeddedTable getXEmbeddedTable() {
       Worksheet worksheet = sandbox.getViewsheet().getOriginalWorksheet();
       EmbeddedTableAssembly tableAssembly = (EmbeddedTableAssembly) worksheet.getAssembly("Query1");
       if (tableAssembly == null) {
           throw new RuntimeException("EmbeddedTableAssembly 'Query1' not found.");
       }
       viewsheetScope.refreshData();
       return tableAssembly.getEmbeddedData();
   }

   /**
    * test toList with array of objects
    */
   @Test
   void testToListWithObj() {
      Object arrObj = new Object[]{"banana", "apple", "apple", "cherry"};
      Object result = viewsheetScope.toList(arrObj, "sort=asc, distinct=true");
      assertArrayEquals(new Object[]{"apple", "banana", "cherry"}, (Object[]) result);

      Object arrObj1 = new Object[]{"apple", "banana", "cherry"};
      Object result1 = viewsheetScope.toList(arrObj1, "maxrows=2, remainder=other");
      assertArrayEquals(new Object[]{"apple", "banana", "other"}, (Object[]) result1);

      // Test case with null values
      Object arrObj4 = new Object[]{"apple", null, "banana"};
      Object result4 = viewsheetScope.toList(arrObj4, "sort=asc");
      assertArrayEquals(new Object[]{null, "apple", "banana"}, (Object[]) result4);
   }

   /**
    * test toList with array of date objects
    */
   @Test
   void testToListWithDateObj() {
      // Test case with different date format, Assuming the date format is "yyyy-MM-dd"
      Object arrObj1 = new Object[]{"2022-01-01", "2023-02-02", "2022-01-03"};
      Object result1 = viewsheetScope.toList(arrObj1, "date=year, sort=asc");
      assertArrayEquals(new Object[]{"2022-01-01", "2022-01-03", "2023-02-02"}, (Object[]) result1);

      // Test data is date type
      Object[] dateArray1 ={
         new Date(2021 - 1900, 0, 1),
         new Date(2023 - 1900, 5, 15),
         new Date(2025 - 1900, 11, 31)
      };

      Object[] dateArray2 = dateArray1.clone();

      Object result2 = viewsheetScope.toList(dateArray1, "date=month, sort=desc");
      assertArrayEquals(new Object[]{12, 6, 1}, (Object[])result2);

      Object result3 = viewsheetScope.toList(dateArray2, "rounddate=year,interval=2,timeseries=false");
      assertArrayEquals(new Object[]{new Date(2020 - 1900, 0, 1),
                                     new Date(2022 - 1900, 0, 1),
                                     new Date(2024 - 1900, 0, 1)}, (Object[])result3);
   }

   /**
    * test toList with DefaultTableLens
    */
   @Test
   void testToListWithXTable() {
      DefaultTableLens table1 = new DefaultTableLens(new Object[][]{
         {"col1", "col2", "date"},
         {"banana", "1", new Date(2021 - 1900, 0, 1)},
         {"apple", "2",  new Date(2023 - 1900, 5, 15)},
         {"banana", "3", new Date(2025 - 1900, 11, 31)},
         {"peach", "2",  new Date(2021 - 1900, 2, 1)},
         });
      DefaultTableLens table2 = table1.clone();

      Object result = viewsheetScope.toList(table1, "field=col1, sort=asc, sorton=col1, distinct=true");
      assertArrayEquals(new Object[]{"apple", "banana", "peach"}, (Object[]) result);

      // Test topN and timeSeries
      Object result2 = viewsheetScope.toList(table2, "field=date, sort=desc,sort2=desc, rounddate=quarter, sorton=col2,timeseries=true,maxrows=2, remainder=other");
      assertArrayEquals(new Object[]{new Date(2023 - 1900, 3, 1),
                                     new Date(2025 - 1900, 9, 1),
                                     "other"}, (Object[]) result2);

      // Test sort by options
      Object result3 = viewsheetScope.toList(table2, "field=date,sort=asc,rounddate=year,sorton2=col2,interval=2.0,timeseries=false");
      assertArrayEquals(new Object[]{new Date(2020 - 1900, 0, 1),
                                     new Date(2022 - 1900, 0, 1),
                                     new Date(2024 - 1900, 0, 1)}, (Object[]) result3);
   }

   @Test
   void testAppendRowWithTypeMismatchThrowsException() {
      RuntimeException exception = assertThrows(RuntimeException.class, () ->
         viewsheetScope.appendRow("Query1", new Object[]{2, true, null, null}));
      assertEquals("Failed to append row. Check order and data type!", exception.getMessage());
   }

   @Test
   void testAppendRowWithColCountMismatchThrowsException() {
      RuntimeException exception = assertThrows(RuntimeException.class, () ->
         viewsheetScope.appendRow("Query1", new Object[]{2, true, null}));
      assertEquals("Failed to append row. Check order and data type!", exception.getMessage());
   }

   /**
    * test appendRow with a normal value
    */
   @Test
   void testAppendValidRow() {
      String dateStr = "2025-04-24 01:01:01";
      LocalDateTime dateTime = LocalDateTime.parse(dateStr, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
      viewsheetScope.appendRow("Query1", new Object[]{false, dateTime, 2, "b"});

      assertEquals(3, getXEmbeddedTable().getRowCount());
      assertEquals(4, getXEmbeddedTable().getColCount());
   }

   /**
    * test setCellValue with a normal value
    */
   @Test
   void testSetCellValue() {
      // when row <= rowCount, it will update the row cell
      viewsheetScope.setCellValue("Query1", 2, 0, true);
      assertEquals(true, getXEmbeddedTable().getObject(2,0));

      // when row > rowCount, it will append a new row
      viewsheetScope.setCellValue("Query1", 3, 2, 3);
      assertEquals(4, getXEmbeddedTable().getRowCount());

      viewsheetScope.refreshData();
      assertEquals(4, getXEmbeddedTable().getRowCount());
      assertEquals(4, getXEmbeddedTable().getColCount());

      //test save worksheet
     /* viewsheetScope.saveWorksheet();
      System.err.println("====" + getXEmbeddedTable().getRowCount() + ", " + getXEmbeddedTable().getColCount());
      assertEquals(4, getXEmbeddedTable().getRowCount());
      assertEquals(4, getXEmbeddedTable().getColCount());*/
   }

   /**
    * test execute script with a normal value
    */
   @Test
   void testExecuteScript() throws Exception {
      Object result = viewsheetScope.execute("fields","TableView1", false);

      if (result instanceof List<?> resultList) {
         List<String> actual = resultList.stream()
            .map(ref -> ((ColumnRef) ref).getName())
            .collect(Collectors.toList());

         List<String> expected = List.of("int", "string", "boolean", "datetime");

         assertEquals(expected, actual);
      }
      else {
         System.err.println("result not List is: " + result.getClass().getName());
         System.out.println("value: " + result);
      }
   }

   @Test
   void testExecuteThrowRuntimeException() {
      // Assert that a RuntimeException is thrown when executing the script
      RuntimeException exception = assertThrows(RuntimeException.class, () ->
         viewsheetScope.execute("test","TableView1", true));
      // Assert that the exception message contains the expected error message
      assert exception.getMessage().replace("\n", " ")
         .contains("Script execution error in assembly: TableView1");
   }

   /**
    * test runQuery with a worksheet table
    */
   @Test
   void testRunQuery() {
      Object result = viewsheetScope.runQuery("ws:global:emwsTest", null);
      assertNotNull(result);
   }

   /**
    * test addImage with a url image
    * @throws Exception
    */
   @Test
   void testAddImage() throws Exception {
      URL url = new URL("https://www.inetsoft.com/images/website/homepage/dataPipeline-1.png");
      Image image = ImageIO.read(url);

      assertEquals(0, sandbox.getViewsheet().getUploadedImageNames().length);
      viewsheetScope.refreshScriptable();
      viewsheetScope.addImage("Image1", image);
      assertEquals(1, sandbox.getViewsheet().getUploadedImageNames().length);
   }

   /**
    * test some functions, void functions didn't check.
    */
   @Test
   void testOtherFunctions() throws Exception {
      viewsheetScope.saveWorksheet();
      viewsheetScope.addVariable("testVar", "testValue");
      viewsheetScope.removeVariable("testVar");
      viewsheetScope.addAction("icon1", "showMessage","click");
      viewsheetScope.isCancelled();

      //test delayVisibility
      VSAScriptable mockScriptable1 = mock(VSAScriptable.class);
      when(mockScriptable1.getAssembly()).thenReturn("TableView1");
      viewsheetScope.delayVisibility(1000, new Object[] { mockScriptable1});

      //test resetChartScriptable
      ChartVSAssembly mockChartAssembly = mock(ChartVSAssembly.class);
      when(mockChartAssembly.getName()).thenReturn("chart1");
      viewsheetScope.resetChartScriptable(mockChartAssembly);

      // test getVSAScriptable
      assertInstanceOf(TableVSAScriptable.class, viewsheetScope.getVSAScriptable("TableView1"));

      // test prepareVariables and getvariableScriptable
      Principal principal = (Principal)viewsheetScope.getVariableScriptable().getMember("__principal__");
      assertEquals("INETSOFT_SYSTEM~;~host-org", principal.getName());
      viewsheetScope.prepareVariables(null);
      Object[] paras = (Object[])viewsheetScope.getVariableScriptable().getMember("parameterNames");
      assertArrayEquals(new Object[] {"__principal__", "_GROUPS_", "_USER_", "_ROLES_"},  paras);

      //test execute with a scriptable
      viewsheetScope.execute("visible=false", viewsheetScope.getVSAScriptable("TableView1"), false);
   }

   @Test
   void testSomeGet() {
      assertNull(viewsheetScope.getMember("event"));
      assertEquals(FormTableRow.OLD, viewsheetScope.getMember("OLD"));
      assertEquals(FormTableRow.CHANGED, viewsheetScope.getMember("CHANGED"));
      assertEquals(FormTableRow.ADDED, viewsheetScope.getMember("ADDED"));
      assertEquals(FormTableRow.DELETED, viewsheetScope.getMember("DELETED"));

      assertEquals(19, viewsheetScope.getMemberKeys().length);

      ViewsheetScope viewsheetScope1 = (ViewsheetScope)viewsheetScope.clone();
      assertEquals("ViewsheetScope", viewsheetScope1.getClassName());
   }

   /**
    * Bug #75807, a qualified read of a base-worksheet table name -- e.g.
    * viewsheet['Query1'] -- is dispatched straight at the viewsheet scope, so it
    * has to resolve through the chain that addToPrototype() links to the
    * worksheet's AssetQueryScope.
    */
   @Test
   void testWorksheetTableResolvedThroughChain() {
      AssetQuerySandbox wbox = sandbox.getAssetQuerySandbox();
      String tname = Arrays.stream(wbox.getWorksheet().getAssemblies())
         .filter(a -> a instanceof TableAssembly)
         .map(Assembly::getName)
         .filter(name -> !viewsheetScope.hasMember(name))
         .findFirst()
         .orElseThrow();

      // not resolvable until the worksheet scope is chained onto this scope
      assertFalse(viewsheetScope.hasMember(tname));
      assertNull(viewsheetScope.getMember(tname));

      AssetQueryScope wscope = new AssetQueryScope(wbox);
      wscope.setMode(viewsheetScope.getMode());
      JavaScriptEngine.addToPrototype(viewsheetScope, wscope);

      assertTrue(viewsheetScope.hasMember(tname));
      assertInstanceOf(TableArray.class, viewsheetScope.getMember(tname));

      // a name owned by neither scope stays absent
      assertFalse(viewsheetScope.hasMember("NoSuchTable75807"));
      assertNull(viewsheetScope.getMember("NoSuchTable75807"));
   }

   private static final LocalDateTime SAMPLE_TIME = LocalDateTime.of(2025, 4, 24, 1, 1, 1);

   private Object[] validRow() {
      return new Object[] { false, SAMPLE_TIME, 2, "b" };
   }

   @Test
   void appendRowIgnoresNonArrayData() {
      int before = getXEmbeddedTable().getRowCount();
      viewsheetScope.appendRow("Query1", "x");
      viewsheetScope.appendRow("Query1", null);
      assertEquals(before, getXEmbeddedTable().getRowCount());
   }

   @Test
   void appendRowOfNullsAddsOneRowWithNullCells() {
      int before = getXEmbeddedTable().getRowCount();
      viewsheetScope.appendRow("Query1", new Object[] { false, null, null, null });

      XEmbeddedTable table = getXEmbeddedTable();
      assertEquals(before + 1, table.getRowCount());

      for(int c = 1; c < 4; c++) {
         assertNull(table.getObject(before, c), "column " + c);
      }
   }

   @Test
   void unknownTableNameIsReportedByAppendRowAndSetCellValue() {
      RuntimeException append = assertThrows(RuntimeException.class,
         () -> viewsheetScope.appendRow("NoSuch", validRow()));
      assertTrue(append.getMessage().contains("NoSuch"), append.getMessage());

      RuntimeException set = assertThrows(RuntimeException.class,
         () -> viewsheetScope.setCellValue("NoSuch", 1, 0, true));
      assertTrue(set.getMessage().contains("NoSuch"), set.getMessage());
   }

   static Stream<Arguments> booleanInputs() {
      return Stream.of(Arguments.of("TRUE", true), Arguments.of(1, true), Arguments.of(0, false));
   }

   @ParameterizedTest
   @MethodSource("booleanInputs")
   void appendRowConvertsBooleanColumnInput(Object input, boolean expected) {
      int before = getXEmbeddedTable().getRowCount();
      viewsheetScope.appendRow("Query1", new Object[] { input, SAMPLE_TIME, 2, "b" });
      assertEquals(expected, getXEmbeddedTable().getObject(before, 0));
   }

   @Test
   void appendRowPinsCurrentTruncationOfFractionalValueInIntColumn() {
      // pins current behavior: 2.7 is accepted and truncated to 2
      int before = getXEmbeddedTable().getRowCount();
      viewsheetScope.appendRow("Query1", new Object[] { false, SAMPLE_TIME, 2.7, "b" });
      assertEquals(2, ((Number) getXEmbeddedTable().getObject(before, 2)).intValue());
   }

   @Test
   void setCellValueIgnoresColumnBeyondLastAndSetsLastColumn() {
      XEmbeddedTable table = getXEmbeddedTable();
      int rows = table.getRowCount();
      int cols = table.getColCount();
      assertEquals(4, cols);

      viewsheetScope.setCellValue("Query1", 1, cols, "x");
      assertEquals(rows, getXEmbeddedTable().getRowCount());

      viewsheetScope.setCellValue("Query1", 1, cols - 1, "z");
      assertEquals("z", getXEmbeddedTable().getObject(1, cols - 1));
   }

   @Test
   void setCellValueRejectsInvalidValueAndKeepsTheCell() {
      Object old = getXEmbeddedTable().getObject(1, 2);

      assertThrows(RuntimeException.class,
                   () -> viewsheetScope.setCellValue("Query1", 1, 2, "abc"));
      assertEquals(old, getXEmbeddedTable().getObject(1, 2));
   }

   @Test
   void setCellValueWithNullClearsTheCell() {
      viewsheetScope.setCellValue("Query1", 1, 3, "text");
      assertEquals("text", getXEmbeddedTable().getObject(1, 3));

      viewsheetScope.setCellValue("Query1", 1, 3, null);
      assertNull(getXEmbeddedTable().getObject(1, 3));
   }

   @Test
   void saveWorksheetPersistsRowAppendedToEmbeddedTable() throws Exception {
      AssetRepository rep = inetsoft.report.composition.WorksheetEngine
         .getWorksheetService().getAssetRepository();
      AssetEntry entry = sandbox.getViewsheet().getBaseEntry();
      // the saved worksheet is shared by the other tests of this class, put it back at the end
      Worksheet original = (Worksheet) rep.getSheet(entry, null, false, AssetContent.ALL, false)
         .clone();

      try {
         int before = getXEmbeddedTable().getRowCount();
         viewsheetScope.appendRow("Query1", validRow());
         viewsheetScope.saveWorksheet();

         Worksheet saved = (Worksheet) rep.getSheet(entry, null, false, AssetContent.ALL, false);
         EmbeddedTableAssembly table = (EmbeddedTableAssembly) saved.getAssembly("Query1");
         assertEquals(before + 1, table.getEmbeddedData().getRowCount());
      }
      finally {
         rep.setSheet(entry, original, sandbox.getUser(), true);
      }
   }

   @Test
   void refreshDataSetsTouchTimestampToNow() throws Exception {
      Thread.sleep(2);
      long t0 = System.currentTimeMillis();
      viewsheetScope.refreshData();
      assertTrue(sandbox.getTouchTimestamp() >= t0);
   }

   @Test
   void addImageStoresPngBytesUnderThePath() throws Exception {
      java.awt.image.BufferedImage image =
         new java.awt.image.BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_INT_RGB);
      viewsheetScope.addImage("p", image);

      assertTrue(Arrays.asList(sandbox.getViewsheet().getUploadedImageNames()).contains("p"));
      java.awt.image.BufferedImage decoded = ImageIO.read(new java.io.ByteArrayInputStream(
         sandbox.getViewsheet().getUploadedImageBytes("p")));
      assertEquals(2, decoded.getWidth());
      assertEquals(2, decoded.getHeight());
   }

   @Test
   void addImageIgnoresNullAndNonImageValues() {
      int count = sandbox.getViewsheet().getUploadedImageNames().length;
      viewsheetScope.addImage("n", null);
      viewsheetScope.addImage("s", "not an image");
      assertEquals(count, sandbox.getViewsheet().getUploadedImageNames().length);
   }

   private boolean isHiddenAndScriptControlled(String name) {
      VSAssemblyInfo info = sandbox.getViewsheet().getAssembly(name).getVSAssemblyInfo();
      return !info.isVisible() && info.isControlByScript();
   }

   static Stream<Arguments> ignoredDelayVisibilityCalls() {
      return Stream.of(Arguments.of(0, "array"), Arguments.of(-5, "array"),
                       Arguments.of(1000, null), Arguments.of(1000, "str"));
   }

   @ParameterizedTest
   @MethodSource("ignoredDelayVisibilityCalls")
   void delayVisibilityIgnoresNonPositiveTimeAndNonArrayArgument(int time, Object arg) {
      VSAScriptable scriptable = viewsheetScope.getVSAScriptable("TableView1");
      Object array = "array".equals(arg) ? new Object[] { scriptable } : arg;
      viewsheetScope.delayVisibility(time, array);

      VSAssemblyInfo info = sandbox.getViewsheet().getAssembly("TableView1").getVSAssemblyInfo();
      assertTrue(info.isVisible());
      assertFalse(info.isControlByScript());
   }

   @Test
   void delayVisibilityHidesScriptablesInArrayAndSkipsOtherEntries() {
      VSAScriptable scriptable = viewsheetScope.getVSAScriptable("TableView1");
      viewsheetScope.delayVisibility(1000, new Object[] { scriptable, "x", null });
      assertTrue(isHiddenAndScriptControlled("TableView1"));
   }

   @Test
   void isCancelledFollowsSandboxCancelAndResetStartTime() throws Exception {
      assertFalse(viewsheetScope.isCancelled());

      Thread.sleep(2);
      sandbox.cancel();
      assertTrue(viewsheetScope.isCancelled());

      Thread.sleep(2);
      viewsheetScope.resetStartTime();
      assertFalse(viewsheetScope.isCancelled());
   }

   private VSAScriptable scriptableWithScope() {
      VSAScriptable scriptable = new VSAScriptable(sandbox);
      scriptable.setAssembly("TableView1");
      scriptable.setScope(viewsheetScope);
      return scriptable;
   }

   @Test
   void addActionRecordsOneUserActionForRepeatedIdenticalTriple() {
      VSAScriptable scriptable = scriptableWithScope();
      int before = sandbox.getViewsheet().getViewsheetInfo().getUserActions().size();
      scriptable.addAction("icon1", "label1", "event1");
      scriptable.addAction("icon1", "label1", "event1");
      assertEquals(before + 1, sandbox.getViewsheet().getViewsheetInfo().getUserActions().size());
   }

   static Stream<Arguments> incompleteActions() {
      return Stream.of(Arguments.of(null, "label", "event"), Arguments.of("icon", "", "event"),
                       Arguments.of("icon", "label", null));
   }

   @ParameterizedTest
   @MethodSource("incompleteActions")
   void addActionRequiresIconLabelAndEvent(String icon, String label, String event) {
      VSAScriptable scriptable = scriptableWithScope();
      int before = sandbox.getViewsheet().getViewsheetInfo().getUserActions().size();
      scriptable.addAction(icon, label, event);
      assertEquals(before, sandbox.getViewsheet().getViewsheetInfo().getUserActions().size());
   }

   private static OpenViewsheetEvent createOpenViewsheetEvent() {
      OpenViewsheetEvent event = new OpenViewsheetEvent();
      event.setEntryId(ASSET_ID);
      event.setViewer(true);
      return event;
   }

   public static final String ASSET_ID = "1^128^__NULL__^ViewsheetScopeTest";
}
