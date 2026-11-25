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

import inetsoft.report.TableLens;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.test.*;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.*;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.SelectionListVSAssemblyInfo;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #82321: a selection list bound to a column that its table does not have ran a distinct
 * query with no bound column. Without MV it gave no values, with MV it gave one value (a sum of
 * another column), which the list showed. The selection query must give no data, before any
 * engine runs, when none of the bound columns can be found in the table.
 *
 * <p>Runs the real {@link SelectionListVSAQuery} / {@link SelectionTreeVSAQuery} over a real
 * worksheet, so the table the query binds is the {@code V_} mirror the server builds; only the
 * sandbox and the final query run ({@code getTableLens}) are stubbed.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class SelectionMissingBoundColumnTest {
   @BeforeEach
   void setUp() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly table = new EmbeddedTableAssembly(ws, TABLE);
      table.setEmbeddedData(new XEmbeddedTable(
         new String[] { XSchema.STRING, XSchema.INTEGER },
         new Object[][] { { REGION, AMOUNT }, { "East", 1 }, { "West", 2 } }));
      ws.addAssembly(table);

      // what Viewsheet.update() does with the worksheet it loads
      viewsheet = new Viewsheet();
      viewsheet.getVSAssemblyInfo().setName("vs1");
      Method setBase = Viewsheet.class.getDeclaredMethod("setBaseWorksheet", Worksheet.class);
      setBase.setAccessible(true);
      setBase.invoke(viewsheet, ws);

      box = mock(ViewsheetSandbox.class);
      when(box.getID()).thenReturn("vs1");
      when(box.getViewsheet()).thenReturn(viewsheet);
      when(box.getWorksheet()).thenAnswer(inv -> viewsheet.getBaseWorksheet());
      when(box.getMode()).thenReturn(RuntimeViewsheet.VIEWSHEET_RUNTIME_MODE);
      // the query binds a private copy of the viewsheet table, as getBoundTable makes
      when(box.getBoundTable(any(), anyString(), anyBoolean())).thenAnswer(inv -> {
         TableAssembly vtable = inv.getArgument(0);
         return ViewsheetSandbox.copyBoundTable(vtable, vtable.getName() + "_bound");
      });
   }

   /** Add the assembly and create its selection table the way the viewsheet does. */
   private void addAssembly(VSAssembly assembly) {
      viewsheet.addAssembly(assembly);
      viewsheet.resetWS();
   }

   @Test
   void listBoundToMissingColumnRunsNoQuery() throws Exception {
      AbstractSelectionVSAQuery query = listQuery(ref("CUSTOMER_ID"));

      assertNull(query.getData(), "a list whose column is not in the table has no data");
      verify(query, never()).getTableLens(any());
   }

   @Test
   void listBoundToExistingColumnStillQueriesIt() throws Exception {
      AbstractSelectionVSAQuery query = listQuery(ref(REGION));

      assertSame(RESULT, query.getData());
      assertEquals(List.of(REGION), visibleColumns(queriedTable(query)));
   }

   @Test
   void listResolvesColumnByBoundAttributeName() throws Exception {
      // the viewsheet name (an alias) is not in the table, the bound attribute is
      ColumnRef ref = ref(REGION);
      ref.setAlias("RegionAlias");
      AbstractSelectionVSAQuery query = listQuery(ref);

      assertSame(RESULT, query.getData());
      assertEquals(List.of(REGION), visibleColumns(queriedTable(query)));
   }

   @Test
   void listResolvesColumnByBoundAttributeCaption() throws Exception {
      AttributeRef attr = new AttributeRef(null, "RegionKey");
      attr.setCaption(REGION);
      ColumnRef ref = new ColumnRef(attr);
      ref.setDataType("string");
      AbstractSelectionVSAQuery query = listQuery(ref);

      assertSame(RESULT, query.getData());
      assertEquals(List.of(REGION), visibleColumns(queriedTable(query)));
   }

   @Test
   void listBoundToDetailCalcFieldResolves() throws Exception {
      ExpressionRef exp = new ExpressionRef(null, "calc1");
      exp.setExpression("field['" + AMOUNT + "'] * 2");
      CalculateRef calc = new CalculateRef(true);
      calc.setDataRef(exp);
      viewsheet.addCalcField(TABLE, calc);

      AbstractSelectionVSAQuery query = listQuery((CalculateRef) calc.clone());

      assertSame(RESULT, query.getData());
      assertEquals(List.of("calc1"), visibleColumns(queriedTable(query)));
   }

   @Test
   void treeWithOneLevelMissingIsUnchanged() throws Exception {
      SelectionTreeVSAssembly tree = new SelectionTreeVSAssembly(viewsheet, NAME);
      tree.setTableName(TABLE);
      tree.setDataRefs(new DataRef[] { ref(REGION), ref("CUSTOMER_ID") });
      addAssembly(tree);
      AbstractSelectionVSAQuery query = spyQuery(SelectionTreeVSAQuery.createVSAQuery(box, NAME));

      assertSame(RESULT, query.getData(), "a tree with one level found still runs its query");
      assertEquals(List.of(REGION), visibleColumns(queriedTable(query)));
   }

   @Test
   void treeWithAllLevelsMissingRunsNoQuery() throws Exception {
      SelectionTreeVSAssembly tree = new SelectionTreeVSAssembly(viewsheet, NAME);
      tree.setTableName(TABLE);
      tree.setDataRefs(new DataRef[] { ref("STATE"), ref("CUSTOMER_ID") });
      addAssembly(tree);
      AbstractSelectionVSAQuery query = spyQuery(SelectionTreeVSAQuery.createVSAQuery(box, NAME));

      assertNull(query.getData());
      verify(query, never()).getTableLens(any());
   }

   private AbstractSelectionVSAQuery listQuery(ColumnRef ref) throws Exception {
      SelectionListVSAssembly list = new SelectionListVSAssembly(viewsheet, NAME);
      SelectionListVSAssemblyInfo info = (SelectionListVSAssemblyInfo) list.getInfo();
      info.setTableName(TABLE);
      info.setDataRef(ref);
      addAssembly(list);
      return spyQuery(new SelectionListVSAQuery(box, NAME));
   }

   private static AbstractSelectionVSAQuery spyQuery(AbstractSelectionVSAQuery query)
      throws Exception
   {
      AbstractSelectionVSAQuery spy = spy(query);
      doReturn(RESULT).when(spy).getTableLens(any());
      return spy;
   }

   private static TableAssembly queriedTable(AbstractSelectionVSAQuery query) throws Exception {
      ArgumentCaptor<TableAssembly> table = ArgumentCaptor.forClass(TableAssembly.class);
      verify(query).getTableLens(table.capture());
      assertTrue(table.getValue().isDistinct());
      return table.getValue();
   }

   private static List<String> visibleColumns(TableAssembly table) {
      ColumnSelection columns = table.getColumnSelection(true);
      List<String> names = new ArrayList<>();

      for(int i = 0; i < columns.getAttributeCount(); i++) {
         names.add(columns.getAttribute(i).getAttribute());
      }

      return names;
   }

   private static ColumnRef column(String name, String type) {
      ColumnRef column = new ColumnRef(new AttributeRef(null, name));
      column.setDataType(type);
      return column;
   }

   private static ColumnRef ref(String name) {
      return column(name, "string");
   }

   private static final String TABLE = "Query1";
   private static final String NAME = "SelectionList1";
   private static final String REGION = "Region";
   private static final String AMOUNT = "Amount";
   private static final TableLens RESULT = new DefaultTableLens(new Object[][] { { REGION } });

   private Viewsheet viewsheet;
   private ViewsheetSandbox box;
}
