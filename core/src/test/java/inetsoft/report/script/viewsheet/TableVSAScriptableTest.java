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

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.Hyperlink;
import inetsoft.report.composition.*;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.internal.binding.BaseField;
import inetsoft.report.lens.AttributeTableLens;
import inetsoft.report.painter.*;
import inetsoft.report.script.TableArray;
import inetsoft.report.script.TableRow;
import inetsoft.test.*;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.TableVSAssemblyInfo;

import inetsoft.web.viewsheet.event.OpenViewsheetEvent;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;

import java.awt.*;
import java.security.Principal;
import java.util.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.openMocks;

@SreeHome(importResources = "TableVSAScriptableTest.vso")
public class TableVSAScriptableTest {
   private ViewsheetSandbox viewsheetSandbox ;
   private TableVSAScriptable tableVSAScriptable, tableVSAScriptable2;
   private TableVSAssemblyInfo tableVSAssemblyInfo;
   private TableVSAssembly tableVSAssembly, tableVSAssembly2;
   private VSAScriptable vsaScriptable;

   @Mock
   ViewsheetService viewsheetService;

   @BeforeEach
   void setUp() {
      openMocks(this);
      Viewsheet viewsheet = new Viewsheet();
      viewsheet.getVSAssemblyInfo().setName("vs1");

      tableVSAssembly = new TableVSAssembly();
      tableVSAssemblyInfo = (TableVSAssemblyInfo) tableVSAssembly.getVSAssemblyInfo();
      tableVSAssemblyInfo.setName("Table1");
      viewsheet.addAssembly(tableVSAssembly);

      viewsheetSandbox = mock(ViewsheetSandbox.class);
      when(viewsheetSandbox.getID()).thenReturn("vs1");
      when(viewsheetSandbox.getViewsheet()).thenReturn(viewsheet);

      tableVSAScriptable = new TableVSAScriptable(viewsheetSandbox);
      vsaScriptable = new VSAScriptable(viewsheetSandbox);
      tableVSAScriptable.setAssembly("Table1");
      vsaScriptable.setAssembly("Table1");
   }

   @Test
   void testGetClassName() {
      assertEquals("TableVSA", tableVSAScriptable.getClassName());
   }

   @ParameterizedTest
   @ValueSource(strings = {"insert", "del", "edit", "wrapping", "shrink",
                           "titleVisible", "flyOnClick", "keepRowHeightOnPrint"})
   void testAddProperties(String propertyName) {
      tableVSAScriptable.addProperties();
      assert tableVSAScriptable.get(propertyName, tableVSAScriptable) instanceof Boolean;
   }

   @ParameterizedTest
   @CsvSource({
      "insert, true, true",
      "del, true, true",
      "edit, true, true",
      "wrapping, true, true",
      "shrink, true, true",
      "titleVisible, false, false",
      "flyOnClick, true, true",
      "keepRowHeightOnPrint, false, false"
   })
   void testSetProperty(String propertyName, Object propertyValue, Object expectedValue) {
      tableVSAScriptable.setProperty(propertyName, propertyValue);
      assertEquals(expectedValue, tableVSAScriptable.get(propertyName, tableVSAScriptable));
   }

   @Test
   void testGetSetFormValue() {
      //setFrom and isForm
      assertFalse(tableVSAScriptable.isForm());
      tableVSAScriptable.setForm(true);
      assertTrue(tableVSAScriptable.isForm());
      tableVSAScriptable.setForm(false);
      assertFalse(tableVSAScriptable.isForm());

      //test setFormValue
      tableVSAScriptable.setFormValue(true);
      assertTrue(tableVSAssemblyInfo.getFormValue());
      tableVSAScriptable.setFormValue(false);
      assertFalse(tableVSAssemblyInfo.getFormValue());
   }

   @Test
   void testGetSetQuery() {
      assertNull(tableVSAScriptable.getQuery());
      tableVSAScriptable.setQuery("query1");
      assertEquals("query1", tableVSAScriptable.getQuery());

      //cube source
      tableVSAScriptable.setQuery("cube::cubetest1");
      assertEquals(Assembly.CUBE_VS + "cubetest1", tableVSAScriptable.getQuery());
   }

   @Test
   void testGetSetFields() {
      //set null field
      tableVSAScriptable.setFields(new String[]{});
      assertEquals(new ArrayList<>(), tableVSAScriptable.getFields());

      //set multiple fields
      ColumnRef columnRef1 = new ColumnRef();
      columnRef1.setDataRef(new BaseField("field1"));
      ColumnRef columnRef2 = new ColumnRef();
      columnRef2.setDataRef(new BaseField("field2"));
      List<Object> expectedFields = new ArrayList<>(Arrays.asList(columnRef1, columnRef2));
      tableVSAScriptable.setFields(new String[] {"field1", "field2"});
      assertEquals(expectedFields, tableVSAScriptable.getFields());
   }

   @Test
   void testGetSetTipView() {
      //set null tip view
      tableVSAScriptable.setTipView(null);
      assertNull(tableVSAScriptable.getTipView());

      //set tip view
      tableVSAScriptable.setTipView("view1");
      assertEquals("view1", tableVSAScriptable.getTipView());
      tableVSAScriptable.setTipViewValue("view2");
      assertEquals("view2", tableVSAScriptable.getTipView());

      //form table can't set tip view
      tableVSAScriptable.setForm(true);
      tableVSAScriptable.setTipView("view3");
      tableVSAScriptable.setTipViewValue("view3");
      assertNull(tableVSAScriptable.getTipView());
   }

   @ParameterizedTest
   @CsvSource({
      "setHyperlink, ()",
      "highlighted, ''",
      "data, [][]",
      "cellFormat, [][]",
      "table, [][]",
      "colFormat, []",
      "setColumnWidth, ()",
      "setRowHeight, ()",
      "isActionVisible, ()",
      "title, ''"
   })
   void testGetSuffix(String propertyName, String expectedValue) {
      assertEquals(expectedValue, tableVSAScriptable.getSuffix(propertyName));
   }

   /**
    * Tests table actions, set column width, row height
    * Open a vso file and uses table in the dashboard.
    *
    * @throws Exception if any error occurs during the test execution
    */
   @Test
   void testSetColumnWidthRowHeight() throws Exception {
      processAssembly("TableView1");

      //set column width
      tableVSAScriptable2.setColumnWidth(0, 40);
      assertEquals(40.0, tableVSAssembly2.getTableDataVSAssemblyInfo().getColumnWidth(0));
      tableVSAScriptable2.setColumnWidthAll(1, 30);
      assertEquals(30.0, tableVSAssembly2.getTableDataVSAssemblyInfo().getColumnWidth(1));

      //set row height
      tableVSAScriptable2.setRowHeight(0, 10);
      assertEquals(10.0, tableVSAssembly2.getTableDataVSAssemblyInfo().getRowHeight(0));
      tableVSAScriptable2.setRowHeight(1, 60);
      assertEquals(60.0, tableVSAssembly2.getTableDataVSAssemblyInfo().getRowHeight(1));
   }

   /**
    * Tests setting hyperlinks for specific cells in a table.
    * Verifies that the correct hyperlink type and value are set for the given cells.
    *
    * @throws Exception if any error occurs during the test execution
    */
   @Test
   void testSetHyperlink() throws Exception {
      processAssembly("TableView1");

      //set web link for cell(1,1), set vs link for cell(1,2)
      Hyperlink.Ref vsLink = new Hyperlink.Ref("test", Hyperlink.VIEWSHEET_LINK);
      tableVSAScriptable2.setHyperlink(1, 1, "http://www.inetsoft.com");
      tableVSAScriptable2.setHyperlink(1, 2, vsLink);

      AttributeTableLens tableLens = (AttributeTableLens)tableVSAScriptable2.get("tablelens", tableVSAScriptable2);
      assertEquals("http://www.inetsoft.com", tableLens.getHyperlink(1, 1).getLink());
      assertEquals(Hyperlink.WEB_LINK, tableLens.getHyperlink(1, 1).getLinkType());
      assertEquals("1^128^__NULL__^test", tableLens.getHyperlink(1, 2).getLink());
      assertEquals(Hyperlink.VIEWSHEET_LINK, tableLens.getHyperlink(1, 2).getLinkType());
   }

   @Test
   void testSetSize() throws Exception {
      processAssembly("TableView1");

      //set size, table default size is 400*120
      Dimension defaultSize = new Dimension(400, 120);
      Dimension smallSize = new Dimension(100, 100);
      Dimension largeSize = new Dimension(450, 200);

      assertEquals(defaultSize, tableVSAScriptable2.getSize());
      tableVSAScriptable2.setSize(smallSize);
      assertEquals(smallSize, tableVSAScriptable2.getSize());
      tableVSAScriptable2.setSize(largeSize);
      assertEquals(largeSize, tableVSAScriptable2.getSize());
   }

   @Test
   void testGetAndFormAction() throws Exception {
      processAssembly("TableView1");

      //test get actions
      assertNull(tableVSAScriptable2.get("value", tableVSAScriptable2));
      assertEquals("CONTACT_ID",
                   ((TableRow) tableVSAScriptable2.get("field", tableVSAScriptable2)).get(0, null));
      assertEquals(36, tableVSAScriptable2.get("row", tableVSAScriptable2));
      assertEquals(4, tableVSAScriptable2.get("col", tableVSAScriptable2));
      assertEquals(1,
                   ((TableArray)tableVSAScriptable2.get("data", tableVSAScriptable2)).getTable().getObject(1, 1));
      assertEquals(1,
                   ((TableArray)tableVSAScriptable2.get("table", tableVSAScriptable2)).getTable().getObject(1, 1));
      assertEquals(36, tableVSAScriptable2.get("data.length", tableVSAScriptable2));
      assertEquals(4, tableVSAScriptable2.get("data.size", tableVSAScriptable2));
      assertNull(tableVSAScriptable2.get("dataConditions", tableVSAScriptable2));

      //for non-form table, can't append/insert/delete row, keep original row count
      tableVSAScriptable2.appendRow(1);
      tableVSAScriptable2.insertRow(3);
      tableVSAScriptable2.deleteRow(30);
      AttributeTableLens tableLens1 = (AttributeTableLens)tableVSAScriptable2.get(
         "tablelens", tableVSAScriptable2);
      assertEquals(36, tableLens1.getRowCount());
   }

   /**
    * Tests setting presenters for specific cells, columns index, and column headers in a table.
    * Verifies that the correct presenter is applied and handles invalid parameters.
    *
    * @throws Exception if any error occurs during the test execution
    */
   @Test
   void testSetPresenter() throws Exception {
      processAssembly("TableView1");

      QRCodePresenter qrCodePresenter = new QRCodePresenter();
      Bar2Presenter bar2Presenter = new Bar2Presenter();
      BarPresenter barPresenter = new BarPresenter();
      //set presenter for cell
      tableVSAScriptable2.setPresenter(2, 2, qrCodePresenter);
      //set presenter for column 1
      tableVSAScriptable2.setPresenter(1, bar2Presenter, null);
      //set presenter by column header
      tableVSAScriptable2.setPresenter("CONTACT_ID", barPresenter, null);

      AttributeTableLens tableLens1 = (AttributeTableLens)tableVSAScriptable2.get(
         "tablelens", tableVSAScriptable2);
      assertEquals(qrCodePresenter.getDisplayName(),
                   tableLens1.getPresenter(2, 2).getDisplayName());
      assertEquals(bar2Presenter.getDisplayName(), tableLens1.getPresenter(1).getDisplayName());
      assertEquals(barPresenter.getDisplayName(),
                   tableLens1.getPresenter("CONTACT_ID", 0).getDisplayName());

      //invalid value for set presenter
      RuntimeException runtimeException = assertThrows(RuntimeException.class, () -> {
         tableVSAScriptable2.setPresenter(true, "test", "test");
      });
      assertEquals("Invalid parameters for setPresenter: true, test, test",
                   runtimeException.getMessage());
   }

   /**
    * Tests various actions on a form table, such as appending, inserting, deleting rows,
    * setting cell values, and committing changes. Verifies the row count and data integrity
    * after each operation.
    * Open a vso file and uses form table in the dashboard.
    * @throws Exception if any error occurs during the test execution
    */
   @Test
   void testFormTableActions() throws Exception {
      processAssembly("TableView2");

      //form table has 36 rows, append 1 row and insert 1 row
      tableVSAScriptable2.appendRow(1);
      tableVSAScriptable2.insertRow(3);
      AttributeTableLens tableLens2 = (AttributeTableLens)tableVSAScriptable2.get(
         "tablelens", tableVSAScriptable2);
      assertEquals(38, tableLens2.getRowCount());
      //delete row 35
      tableVSAScriptable2.deleteRow(35);
      assertEquals(37, tableLens2.getRowCount());
      //set cell value
      tableVSAScriptable2.setObject(1, 1, "999");
      assertEquals("999", tableLens2.getObject(1, 1));

      assertEquals(1, Arrays.stream(tableVSAScriptable2.getFormRows("changed")).count());
      assertEquals(1, Arrays.stream(tableVSAScriptable2.getFormRows("deleted")).count());
      assertEquals(2, Arrays.stream(tableVSAScriptable2.getFormRows("added")).count());
      assertEquals(34, Arrays.stream(tableVSAScriptable2.getFormRows(FormTableRow.OLD)).count());

      //commit changed and appended row
      tableVSAScriptable2.commit("changed");
      assertEquals(0, Arrays.stream(tableVSAScriptable2.getFormRows("changed")).count());
      tableVSAScriptable2.commit(2);//index 2 is appended row
      assertEquals(1, Arrays.stream(tableVSAScriptable2.getFormRows("added")).count());
      assertEquals(36, Arrays.stream(tableVSAScriptable2.getFormRows(FormTableRow.OLD)).count());
   }

   private static OpenViewsheetEvent createOpenViewsheetEvent() {
      OpenViewsheetEvent event = new OpenViewsheetEvent();
      event.setEntryId(ASSET_ID);
      event.setViewer(true);

      return event;
   }

   /**
    * Processes the specified assembly by retrieving it from the runtime viewsheet
    * and initializing the `TableVSAScriptable` instance with the assembly name.
    *
    * @param assemblyName the name of the assembly to process
    * @throws Exception if an error occurs during the processing of the assembly
    */
   private void processAssembly(String assemblyName) throws Exception {
      RuntimeViewsheet rvs = viewsheetResource.getRuntimeViewsheet();
      ViewsheetSandbox sandbox = rvs.getViewsheetSandbox();
      Principal principal = mock(Principal.class);
      when(viewsheetService.getViewsheet(viewsheetResource.getRuntimeId(), principal))
         .thenReturn(viewsheetResource.getRuntimeViewsheet());

      tableVSAssembly2 = (TableVSAssembly) viewsheetResource
         .getRuntimeViewsheet().getViewsheet().getAssembly(assemblyName);
      tableVSAScriptable2 = new TableVSAScriptable(sandbox);
      tableVSAScriptable2.setAssembly(tableVSAssembly2.getName());
   }

   public static final String ASSET_ID = "1^128^__NULL__^TableVSAScriptableTest";

   @RegisterExtension
   @Order(1)
   ControllersExtension controllers = new ControllersExtension();

   @RegisterExtension
   @Order(2)
   RuntimeViewsheetExtension viewsheetResource =
      new RuntimeViewsheetExtension(createOpenViewsheetEvent(), controllers);
}