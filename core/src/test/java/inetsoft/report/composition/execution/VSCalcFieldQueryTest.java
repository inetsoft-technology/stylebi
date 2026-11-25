/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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

import inetsoft.graph.data.DataSet;
import inetsoft.mv.MVManager;
import inetsoft.report.*;
import inetsoft.report.composition.ChangedAssemblyList;
import inetsoft.test.*;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.ConditionItem;
import inetsoft.uql.ConditionList;
import inetsoft.uql.XCondition;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.erm.ExpressionRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.util.Tool;
import inetsoft.util.UserMessage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Method;
import java.sql.Timestamp;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * How the viewsheet calculated fields reach the queries (Test Case #80957):
 * {@code VSAQuery.appendCalcFields} and the removal of the unused ones,
 * a detail or aggregate calculated field in a freehand (calc) table, and the declared type of
 * an aggregate calculated field in a crosstab. The viewsheet, its sandbox and the queries are
 * real; only the data of the base worksheet table is embedded. Expected values are computed by
 * hand from the data of each test.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  LibManagerTestConfiguration.class,
                                  PluginsTestConfiguration.class,
                                  VSCalcFieldQueryTest.TestConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class VSCalcFieldQueryTest {
   @Configuration
   static class TestConfig {
      @Bean
      public MVManager mvManager() {
         return mock(MVManager.class);
      }

      @Bean
      @SuppressWarnings("unchecked")
      public AssetDataCache assetDataCache() {
         ObjectProvider<DistributedTableCacheStore> provider = mock(ObjectProvider.class);
         when(provider.getObject()).thenReturn(mock(DistributedTableCacheStore.class));
         return new AssetDataCache(mock(DataSourceRegistry.class), provider);
      }
   }

   private Worksheet ws;
   private Viewsheet vs;
   private ViewsheetSandbox box;
   private AssetQuerySandbox wbox;
   private final List<String> aggInfo = Collections.synchronizedList(new ArrayList<>());

   @AfterEach
   void tearDown() {
      if(box != null) {
         box.dispose();
         box = null;
      }

      if(wbox != null) {
         wbox.dispose();
         wbox = null;
      }

      Tool.clearUserMessage();
   }

   // -----------------------------------------------------------------------
   // setup
   // -----------------------------------------------------------------------

   /** A viewsheet over the embedded worksheet table D. */
   private void init(String[] types, Object[][] data) throws Exception {
      ws = new Worksheet();
      EmbeddedTableAssembly table = new EmbeddedTableAssembly(ws, "D");
      table.setEmbeddedData(new XEmbeddedTable(types, data));
      ws.addAssembly(table);

      vs = new Viewsheet();
      setBaseWorksheet(vs, ws);
   }

   /** Viewsheet.setBaseWorksheet is not public; it is reached by reflection. */
   private static void setBaseWorksheet(Viewsheet viewsheet, Worksheet worksheet)
      throws Exception
   {
      Method setBase = Viewsheet.class.getDeclaredMethod("setBaseWorksheet", Worksheet.class);
      setBase.setAccessible(true);
      setBase.invoke(viewsheet, worksheet);
   }

   /** D(city, a, b): A (3, 4), A (6, 2), B (1, 1). */
   private void initCityAb() throws Exception {
      init(new String[] { XSchema.STRING, XSchema.INTEGER, XSchema.INTEGER },
           new Object[][] { { "city", "a", "b" }, { "A", 3, 4 }, { "A", 6, 2 }, { "B", 1, 1 } });
   }

   private CalculateRef addCalc(String name, boolean detail, String expression, String type) {
      CalculateRef calc = new CalculateRef(detail);
      ExpressionRef expr = new ExpressionRef(null, name);
      expr.setExpression(expression);
      calc.setDataRef(expr);
      calc.setDataType(type);
      vs.addCalcField("D", calc);
      return calc;
   }

   private void start() throws Exception {
      AssetEntry entry = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
                                        AssetEntry.Type.VIEWSHEET, "test/VSCalcField", null);
      vs.setEntry(entry);
      box = new ViewsheetSandbox(vs, AbstractSheet.SHEET_RUNTIME_MODE, null, false, entry) {
         @Override
         public TableAssembly getBoundTable(TableAssembly assembly, String vassembly,
                                            boolean detail)
            throws Exception
         {
            Assembly temp = vs.getAssembly(vassembly);

            if(temp instanceof CrosstabVSAssembly &&
               vassembly.startsWith(CalcTableVSAQuery.TEMP_ASSEMBLY_PREFIX))
            {
               // the aggregates CalcTableVSAQuery adds for the calc fields of the table
               StringBuilder sb = new StringBuilder();
               AggregateInfo ainfo = ((CrosstabVSAssembly) temp).getVSCrosstabInfo()
                  .getAggregateInfo();

               for(AggregateRef aggregate : ainfo.getAggregates()) {
                  if(sb.length() > 0) {
                     sb.append(' ');
                  }

                  sb.append(aggregate.getDataRef().getName()).append('/')
                     .append(aggregate.getFormula().getFormulaName()).append('/')
                     .append(aggregate.getDataRef().getClass().getSimpleName());
               }

               aggInfo.add(sb.toString().trim());
            }

            return super.getBoundTable(assembly, vassembly, detail);
         }
      };
      box.reset(null, vs.getAssemblies(), new ChangedAssemblyList(), true, true, null);
   }

   private static TableCellBinding detailCell(String column) {
      TableCellBinding cell = new TableCellBinding(CellBinding.BIND_COLUMN, column);
      cell.setBType(CellBinding.DETAIL);
      cell.setExpansion(GroupableCellBinding.EXPAND_V);
      return cell;
   }

   private static TableCellBinding groupCell(String column) {
      TableCellBinding cell = TableCellBinding.getGroupBinding(column);
      cell.setExpansion(GroupableCellBinding.EXPAND_V);
      cell.setCellName("g1");
      return cell;
   }

   private static TableCellBinding summaryCell(String column, String formula) {
      TableCellBinding cell = new TableCellBinding(CellBinding.BIND_COLUMN, column);
      cell.setBType(CellBinding.SUMMARY);
      cell.setFormula(formula);
      cell.setRowGroup("g1");
      return cell;
   }

   /** A freehand table with the given cells in row 1, column by column. */
   private TableLens freehand(TableCellBinding... cells) throws Exception {
      CalcTableVSAssembly calc = new CalcTableVSAssembly(vs, "Calc1");
      calc.setSourceInfo(new SourceInfo(SourceInfo.ASSET, null, "D"));
      TableLayout layout = calc.getTableLayout();

      if(cells.length > layout.getColCount()) {
         layout.setColCount(cells.length);
      }

      for(int i = 0; i < cells.length; i++) {
         layout.setCellBinding(1, i, cells[i]);
      }

      vs.addAssembly(calc);
      start();
      TableLens lens = new CalcTableVSAQuery(box, "Calc1", false).getTableLens();
      assertNotNull(lens);
      lens.moreRows(TableLens.EOT);
      return lens;
   }

   /** The cells of the rows below the header, row by row. */
   private static List<List<Object>> body(TableLens lens, int cols) {
      List<List<Object>> rows = new ArrayList<>();

      for(int r = 1; lens.moreRows(r); r++) {
         List<Object> row = new ArrayList<>();

         for(int c = 0; c < cols; c++) {
            row.add(lens.getObject(r, c));
         }

         rows.add(row);
      }

      return rows;
   }

   private static List<Double> numbers(List<Object> values) {
      List<Double> list = new ArrayList<>();

      for(Object value : values) {
         list.add(value == null ? null : Double.valueOf(String.valueOf(value)));
      }

      return list;
   }

   private static List<Object> column(List<List<Object>> rows, int col) {
      List<Object> list = new ArrayList<>();

      for(List<Object> row : rows) {
         list.add(row.get(col));
      }

      return list;
   }

   // -----------------------------------------------------------------------
   // V rows: appendCalcFields
   // -----------------------------------------------------------------------

   private static TableAssembly baseTable(String name) {
      Worksheet w = new Worksheet();
      EmbeddedTableAssembly base = new EmbeddedTableAssembly(w, name);
      ColumnSelection columns = new ColumnSelection();

      for(String col : new String[] { "a", "b", "c", "d" }) {
         ColumnRef column = new ColumnRef(new AttributeRef(null, col));
         column.setDataType(XSchema.INTEGER);
         columns.addAttribute(column);
      }

      base.setColumnSelection(columns, false);
      w.addAssembly(base);
      return base;
   }

   private static boolean has(TableAssembly table, String column) {
      return table.getColumnSelection(false).getAttribute(column) != null;
   }

   @Test
   @DisplayName("Only the detail or only the aggregate calc fields are appended")
   void detailVersusAggregate() throws Exception {
      initCityAb();
      addCalc("d1", true, "field['a'] + 1", XSchema.INTEGER);
      addCalc("a1", false, "field['Sum([a])']", XSchema.DOUBLE);

      TableAssembly detail = baseTable("D");
      VSAQuery.appendCalcFieldWithType(detail, "D", true, false, vs);
      assertTrue(has(detail, "d1"));
      assertFalse(has(detail, "a1"));

      TableAssembly aggregate = baseTable("D");
      VSAQuery.appendCalcFieldWithType(aggregate, "D", false, false, vs);
      assertTrue(has(aggregate, "a1"));
      assertFalse(has(aggregate, "d1"));
   }

   @Test
   @DisplayName("A calc field replaces a column of the same name")
   void sameNameColumnIsReplaced() throws Exception {
      initCityAb();
      addCalc("calc", true, "field['a'] + 1", XSchema.INTEGER);
      TableAssembly table = baseTable("D");
      ColumnSelection columns = table.getColumnSelection(false);
      columns.addAttribute(new ColumnRef(new AttributeRef(null, "calc")));
      table.setColumnSelection(columns, false);

      VSAQuery.appendCalcFieldWithType(table, "D", true, false, vs);
      int count = 0;

      for(int i = 0; i < table.getColumnSelection(false).getAttributeCount(); i++) {
         if("calc".equals(table.getColumnSelection(false).getAttribute(i).getName())) {
            count++;
            assertInstanceOf(CalculateRef.class, table.getColumnSelection(false).getAttribute(i));
         }
      }

      assertEquals(1, count);
   }

   @Test
   @DisplayName("Every calc field is appended to a cube table")
   void cubeTable() throws Exception {
      initCityAb();
      addCalc("d1", true, "field['a'] + 1", XSchema.INTEGER);
      TableAssembly cube = baseTable(Assembly.CUBE_VS + "D");
      VSAQuery.appendCalcFieldWithType(cube, "D", false, false, vs);
      assertTrue(has(cube, "d1"));
   }

   @Test
   @DisplayName("RangeOnly appends the Range@ calc fields only")
   void rangeOnly() throws Exception {
      initCityAb();
      addCalc("plain", true, "field['a'] + 1", XSchema.INTEGER);
      addCalc("Range@b", true, "field['b'] + 1", XSchema.INTEGER);
      TableAssembly table = baseTable("D");
      VSAQuery.appendCalcFieldWithType(table, "D", true, true, vs);
      assertTrue(has(table, "Range@b"));
      assertFalse(has(table, "plain"));
   }

   @Test
   @DisplayName("PIN(current behavior, deliberate, Bug #81207 not a bug): the clone added to " +
      "the column selection must be visible, so the viewsheet's own ref becomes visible")
   void pin_appendMakesTheViewsheetRefVisible() throws Exception {
      initCityAb();
      CalculateRef calc = addCalc("d1", true, "field['a'] + 1", XSchema.INTEGER);
      calc.setVisible(false);
      VSAQuery.appendCalcFieldWithType(baseTable("D"), "D", true, false, vs);
      // same idiom at VSAQuery:1583, VSUtil:4427, VSUtil:4450, TableConditionUtil:1343
      assertTrue(vs.getCalcField("D", "d1").isVisible());
   }

   /** D(a, b, c, d) with two rows, and the viewsheet calc fields of the given kind. */
   private TableAssembly prunedTable(String output, String[]... calcs) throws Exception {
      init(new String[] { XSchema.INTEGER, XSchema.INTEGER, XSchema.INTEGER, XSchema.INTEGER },
           new Object[][] { { "a", "b", "c", "d" }, { 1, 2, 3, 4 }, { 5, 6, 7, 8 } });

      for(String[] calc : calcs) {
         addCalc(calc[0], true, calc[1], calc[2]);
      }

      TableAssembly vtable = VSAQuery.getVSTableAssembly("D", false, vs, ws);
      TableAssembly table = ViewsheetSandbox.copyBoundTable(vtable, "V_MT_0");
      ColumnSelection selected = new ColumnSelection();

      for(int i = 0; i < table.getColumnSelection(false).getAttributeCount(); i++) {
         DataRef ref = table.getColumnSelection(false).getAttribute(i);

         if(output.equals(ref.getAttribute())) {
            selected.addAttribute(ref);
         }
      }

      assertEquals(1, selected.getAttributeCount(), output);
      table.setColumnSelection(selected, false);
      ws.addAssembly(table);

      ViewsheetSandbox vbox = mock(ViewsheetSandbox.class,
                                   org.mockito.Mockito.withSettings().stubOnly());
      org.mockito.Mockito.doReturn(vs).when(vbox).getViewsheet();
      VSAQuery query = new OutputVSAQuery(vbox, "Text1");
      // VSAQuery.removeUnusedCalcFields is private; it is reached by reflection
      Method prune = VSAQuery.class.getDeclaredMethod("removeUnusedCalcFields",
                                                      TableAssembly.class);
      prune.setAccessible(true);
      prune.invoke(query, table);
      return table;
   }

   private static List<String> childColumns(TableAssembly table) {
      ColumnSelection columns =
         ((MirrorTableAssembly) table).getTableAssembly().getColumnSelection(false);
      List<String> names = new ArrayList<>();

      for(int i = 0; i < columns.getAttributeCount(); i++) {
         names.add(columns.getAttribute(i).getName());
      }

      return names;
   }

   private List<Object> run(TableAssembly table) throws Exception {
      wbox = new AssetQuerySandbox(table.getWorksheet());
      TableLens lens = wbox.getTableLens(table.getName(), AssetQuerySandbox.RUNTIME_MODE);
      lens.moreRows(TableLens.EOT);
      List<Object> values = new ArrayList<>();

      for(int r = 1; lens.moreRows(r); r++) {
         values.add(lens.getObject(r, 0));
      }

      return values;
   }

   private static final String[] BAD = { "bad", "field['a'].foo()", XSchema.INTEGER };
   private static final String[] OK = { "ok", "field['a'] + 1", XSchema.INTEGER };

   @Test
   @DisplayName("An unused failing detail calc field is removed, a used one fails the query")
   void unusedFailingCalcIsNotExecuted() throws Exception {
      TableAssembly unused = prunedTable("a", BAD, OK);
      assertEquals(Arrays.asList("a", "b", "c", "d"), childColumns(unused));
      Tool.clearUserMessage();
      assertEquals(Arrays.asList(1, 5), run(unused));
      assertNull(Tool.getUserMessage());
      tearDown();

      TableAssembly other = prunedTable("ok", BAD, OK);
      assertEquals(Arrays.asList(2, 6), numbers(run(other)).stream()
         .map(Double::intValue).collect(java.util.stream.Collectors.toList()));
   }

   @Test
   @DisplayName("A used failing calc field gives the design sample row (999) and an " +
      "'expression failed' message (Bug #71861)")
   void usedFailingCalcGivesTheSampleRow() throws Exception {
      TableAssembly used = prunedTable("bad", BAD, OK);
      Tool.clearUserMessage();
      assertEquals(Arrays.asList(999), run(used));
      UserMessage message = Tool.getUserMessage();
      assertNotNull(message);
      assertTrue(message.getMessage().contains("expression failed"), message.getMessage());
   }

   @Test
   @DisplayName("A chain of unused calc fields is removed together, a used end keeps the chain")
   void chainRemoval() throws Exception {
      String[] a = { "A", "field['a'].foo()", XSchema.INTEGER };
      String[] b = { "B", "field['A'] + 1", XSchema.INTEGER };
      TableAssembly unused = prunedTable("a", a, b);
      assertEquals(Arrays.asList("a", "b", "c", "d"), childColumns(unused));
      tearDown();

      TableAssembly used = prunedTable("B", a, b);
      assertEquals(Arrays.asList("a", "b", "c", "d", "A", "B"), childColumns(used));
   }

   // -----------------------------------------------------------------------
   // K rows: a calc field in a freehand table
   // -----------------------------------------------------------------------

   private void xmlRoundTrip() throws Exception {
      java.io.StringWriter sw = new java.io.StringWriter();
      java.io.PrintWriter pw = new java.io.PrintWriter(sw);
      // writeXML shrinks the calc fields and aggregate fields of tables that are not in the
      // base worksheet of the viewsheet
      vs.setBaseEntry(new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET,
                                     "test/BaseWs", null));
      vs.writeXML(pw);
      vs.setBaseEntry(null);
      pw.flush();
      org.w3c.dom.Document doc = Tool.parseXML(new java.io.ByteArrayInputStream(
         sw.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
      Viewsheet vs2 = new Viewsheet();
      vs2.parseXML(doc.getDocumentElement());
      setBaseWorksheet(vs2, ws);
      vs = vs2;
   }

   @Test
   @DisplayName("A detail calc field in a freehand detail cell")
   void detailCalcInFreehandCell() throws Exception {
      initCityAb();
      addCalc("d1", true, "field['a'] * 2", XSchema.INTEGER);
      TableLens lens = freehand(detailCell("d1"));
      // a = 3, 6, 1
      assertEquals(Arrays.asList(6.0, 12.0, 2.0), numbers(column(body(lens, 1), 0)));
   }

   private void ratioFreehand(String ratioType) throws Exception {
      addCalc("ratio", false, "field['Sum([a])'] / field['Sum([b])']", ratioType);
   }

   @Test
   @DisplayName("An aggregate calc field in a freehand summary cell")
   void aggregateCalcInFreehandCell() throws Exception {
      initCityAb();
      ratioFreehand(XSchema.DOUBLE);
      TableLens lens = freehand(groupCell("city"), summaryCell("a", "Sum"),
                                summaryCell("b", "Sum"), summaryCell("ratio", "None"));
      List<List<Object>> rows = body(lens, 4);
      assertEquals(Arrays.asList("A", "B"), column(rows, 0));
      // A: 9 / 6, B: 1 / 1
      assertEquals(Arrays.asList(9.0, 1.0), numbers(column(rows, 1)));
      assertEquals(Arrays.asList(6.0, 1.0), numbers(column(rows, 2)));
      assertEquals(Arrays.asList(1.5, 1.0), numbers(column(rows, 3)));
   }

   @Test
   @DisplayName("The temporary crosstab takes only the aggregate calc fields, with formula None")
   void temporaryCrosstabAggregates() throws Exception {
      initCityAb();
      addCalc("d1", true, "field['a'] * 2", XSchema.INTEGER);
      ratioFreehand(XSchema.DOUBLE);
      freehand(groupCell("city"), summaryCell("a", "Sum"), summaryCell("b", "Sum"),
               summaryCell("ratio", "None"));
      assertFalse(aggInfo.isEmpty(), "the temporary crosstab did not run");

      // the aggregate calc field is the only aggregate: formula None over the calc ref; the
      // detail calc field d1 is not an aggregate of the temporary crosstab
      for(String info : aggInfo) {
         assertEquals("ratio/none/CalculateRef", info);
      }
   }

   @Test
   @DisplayName("A zero or 0 / 0 ratio is empty (#80953 Q3c)")
   void divideByZeroIsEmpty() throws Exception {
      init(new String[] { XSchema.STRING, XSchema.INTEGER, XSchema.INTEGER },
           new Object[][] { { "city", "a", "b" }, { "A", 3, 0 }, { "A", 6, 0 }, { "B", 1, 1 },
                            { "C", 0, 0 } });
      ratioFreehand(XSchema.DOUBLE);
      TableLens lens = freehand(groupCell("city"), summaryCell("a", "Sum"),
                                summaryCell("b", "Sum"), summaryCell("ratio", "None"));
      List<List<Object>> rows = body(lens, 4);
      assertEquals(Arrays.asList("A", "B", "C"), column(rows, 0));
      assertEquals(Arrays.asList(null, 1.0, null), numbers(column(rows, 3)));
   }

   @Test
   @DisplayName("A detail calc field that refers to a hidden one")
   void chainOfDetailCalcs() throws Exception {
      init(new String[] { XSchema.INTEGER, XSchema.TIME_INSTANT },
           new Object[][] { { "customer_id", "orderdate" },
                            { 3, Timestamp.valueOf("2001-08-15 00:00:00") },
                            { 10, Timestamp.valueOf("2001-02-01 00:00:00") },
                            { 1, Timestamp.valueOf("2001-12-25 00:00:00") } });
      CalculateRef h1 = addCalc("h1", true, "CALC.quarter(field['orderdate'])",
                                XSchema.INTEGER);
      h1.setVisible(false);
      CalculateRef h2 = addCalc("h2", true, "field['h1'] * 2", XSchema.INTEGER);
      h2.setVisible(false);
      addCalc("v", true, "field['h2'] + field['customer_id']", XSchema.INTEGER);
      TableLens lens = freehand(detailCell("v"));
      // quarters 3, 1, 4: 3 * 2 + 3, 1 * 2 + 10, 4 * 2 + 1
      assertEquals(Arrays.asList(9.0, 12.0, 9.0), numbers(column(body(lens, 1), 0)));
   }

   @Test
   @DisplayName("Multiplying the chain only passes because the declared Integer " +
      "calc returns a String in a freehand cell, so `+` would concatenate; not full coverage of the chain")
   void chainOfDetailCalcs_multiplied() throws Exception {
      init(new String[] { XSchema.INTEGER, XSchema.TIME_INSTANT },
           new Object[][] { { "customer_id", "orderdate" },
                            { 3, Timestamp.valueOf("2001-08-15 00:00:00") },
                            { 10, Timestamp.valueOf("2001-02-01 00:00:00") },
                            { 1, Timestamp.valueOf("2001-12-25 00:00:00") } });
      CalculateRef h1 = addCalc("h1", true, "CALC.quarter(field['orderdate'])",
                                XSchema.INTEGER);
      h1.setVisible(false);
      CalculateRef h2 = addCalc("h2", true, "field['h1'] * 2", XSchema.INTEGER);
      h2.setVisible(false);
      addCalc("v", true, "field['h2'] * field['customer_id']", XSchema.INTEGER);
      TableLens lens = freehand(detailCell("v"));
      // quarters 3, 1, 4: (3 * 2) * 3, (1 * 2) * 10, (4 * 2) * 1
      assertEquals(Arrays.asList(18.0, 20.0, 8.0), numbers(column(body(lens, 1), 0)));
   }

   @Test
   @DisplayName("An unused failing calc field is not executed in a freehand table")
   void unusedFailingCalcInFreehand() throws Exception {
      init(new String[] { XSchema.INTEGER, XSchema.INTEGER },
           new Object[][] { { "a", "b" }, { 1, 2 }, { 5, 6 } });
      addCalc("bad", true, "field['a'].foo()", XSchema.INTEGER);
      Tool.clearUserMessage();
      TableLens lens = freehand(detailCell("a"));
      assertEquals(Arrays.asList(1.0, 5.0), numbers(column(body(lens, 1), 0)));
      assertNull(Tool.getUserMessage());
   }

   @Test
   @DisplayName("Bug #81627: an unused failing calc field is not executed when another " +
      "calc field reads a field by a name built at run time")
   void unusedFailingCalcWithDynamicFieldName() throws Exception {
      init(new String[] { XSchema.INTEGER, XSchema.INTEGER },
           new Object[][] { { "a", "b" }, { 1, 2 }, { 5, 6 } });
      addCalc("bad", true, "field['a'].foo()", XSchema.INTEGER);
      addCalc("ok", true, "5", XSchema.INTEGER);
      addCalc("dyn", true, "field['o' + 'k'] + 1", XSchema.INTEGER);
      Tool.clearUserMessage();
      TableLens lens = freehand(detailCell("dyn"));
      assertEquals(Arrays.asList(6.0, 6.0), numbers(column(body(lens, 1), 0)));
      assertNull(Tool.getUserMessage());
   }

   @Test
   @DisplayName("Bug #81627: an unused failing calc field is not executed when the freehand " +
      "table groups a column and summarizes another")
   void unusedFailingCalcWithGroupAndSummary() throws Exception {
      initCityAb();
      addCalc("bad", true, "field['city'].foo()", XSchema.INTEGER);
      Tool.clearUserMessage();
      TableLens lens = freehand(groupCell("city"), summaryCell("a", "Sum"));
      List<List<Object>> rows = body(lens, 2);
      assertEquals(Arrays.asList("A", "B"), column(rows, 0));
      assertEquals(Arrays.asList(9.0, 1.0), numbers(column(rows, 1)));
      assertNull(Tool.getUserMessage());
   }

   @Test
   @DisplayName("Bug #81627: a detail calc field used by a group or summary cell is kept " +
      "next to an unused failing one")
   void usedCalcKeptWithGroupAndSummary() throws Exception {
      initCityAb();
      addCalc("bad", true, "field['city'].foo()", XSchema.INTEGER);
      addCalc("g", true, "field['city'] + 'x'", XSchema.STRING);
      addCalc("d", true, "field['a'] * 2", XSchema.INTEGER);
      Tool.clearUserMessage();
      TableLens lens = freehand(groupCell("g"), summaryCell("d", "Sum"));
      List<List<Object>> rows = body(lens, 2);
      assertEquals(Arrays.asList("Ax", "Bx"), column(rows, 0));
      assertEquals(Arrays.asList(18.0, 2.0), numbers(column(rows, 1)));
      assertNull(Tool.getUserMessage());
   }

   @Test
   @DisplayName("Bug #81627: a detail calc field used only by the table condition is kept " +
      "next to an unused failing one, so the filter still applies")
   void calcUsedOnlyByConditionIsKept() throws Exception {
      initCityAb();
      addCalc("d1", true, "field['a'] * 2", XSchema.INTEGER);
      addCalc("bad", true, "field['city'].foo()", XSchema.INTEGER);
      AssetCondition condition = new AssetCondition();
      condition.setOperation(XCondition.EQUAL_TO);
      condition.setType(XSchema.INTEGER);
      condition.addValue(2);
      ConditionList list = new ConditionList();
      list.append(new ConditionItem(new ColumnRef(new AttributeRef(null, "d1")), condition, 0));
      CalcTableVSAssembly calc = new CalcTableVSAssembly(vs, "Calc1");
      calc.setSourceInfo(new SourceInfo(SourceInfo.ASSET, null, "D"));
      calc.setPreConditionList(list);
      calc.getTableLayout().setCellBinding(1, 0, detailCell("city"));
      vs.addAssembly(calc);
      start();
      Tool.clearUserMessage();
      TableLens lens = new CalcTableVSAQuery(box, "Calc1", false).getTableLens();
      lens.moreRows(TableLens.EOT);
      List<List<Object>> rows = body(lens, 1);
      assertEquals(1, rows.size(), String.valueOf(rows));
      assertNull(Tool.getUserMessage());
   }

   @Test
   @DisplayName("A detail calc field declared Integer is an Integer in a freehand cell")
   void declaredTypeOfDetailCalc() throws Exception {
      initCityAb();
      addCalc("d1", true, "field['a'] * 2", XSchema.INTEGER);
      TableLens lens = freehand(detailCell("d1"));
      assertEquals(Arrays.asList(6, 12, 2), column(body(lens, 1), 0));
   }

   @Test
   @DisplayName("Bug #81668: the declared type of an " +
      "aggregate calc field is applied in a freehand cell")
   void aggregateCalcDeclaredType() throws Exception {
      for(String type : new String[] { XSchema.INTEGER, XSchema.STRING }) {
         tearDown();
         initCityAb();
         ratioFreehand(type);
         TableLens lens = freehand(groupCell("city"), summaryCell("a", "Sum"),
                                   summaryCell("b", "Sum"), summaryCell("ratio", "None"));
         List<Object> ratios = column(body(lens, 4), 3);
         // Integer cuts the fraction, String keeps the text of the number
         List<Object> expected = XSchema.INTEGER.equals(type) ?
            Arrays.asList(1.0, 1.0) : Arrays.asList("1.5", "1");
         assertEquals(expected, ratios, type);
      }
   }

   // -----------------------------------------------------------------------
   // the declared type of an aggregate calc field in a crosstab
   // -----------------------------------------------------------------------

   private static VSAggregateRef aggregate(String column, String formula, String type) {
      VSAggregateRef ref = new VSAggregateRef();
      ref.setDataRef(new AttributeRef(null, column));
      ref.setColumnValue(column);
      ref.setFormulaValue(formula);
      ref.setOriginalDataType(type);
      return ref;
   }

   /** The values (last column) of a crosstab of city / the given aggregates. */
   private List<Object> crosstab(VSAggregateRef... aggregates) throws Exception {
      CrosstabVSAssembly crosstab = new CrosstabVSAssembly(vs, "X1");
      crosstab.setSourceInfo(new SourceInfo(SourceInfo.ASSET, null, "D"));
      VSCrosstabInfo info = new VSCrosstabInfo();
      VSDimensionRef dim = new VSDimensionRef(new AttributeRef(null, "city"));
      dim.setGroupColumnValue("city");
      dim.setDataType(XSchema.STRING);
      info.setDesignRowHeaders(new DataRef[] { dim });
      info.setDesignAggregates(aggregates);
      crosstab.setVSCrosstabInfo(info);
      vs.addAssembly(crosstab);
      start();
      TableLens lens = new CrosstabVSAQuery(box, "X1", false).getTableLens();
      lens.moreRows(TableLens.EOT);
      return column(body(lens, lens.getColCount()), lens.getColCount() - 1);
   }

   @Test
   @DisplayName("Bug #81668: an aggregate calc field with no declared type keeps its Double result")
   void undeclaredTypeKeepsDouble() throws Exception {
      initCityAb();
      CalculateRef calc = new CalculateRef(false);
      ExpressionRef expr = new ExpressionRef(null, "ratio");
      expr.setExpression("field['Sum([a])'] / field['Sum([b])']");
      calc.setDataRef(expr);
      vs.addCalcField("D", calc);
      List<Object> values = crosstab(aggregate("a", "Sum", XSchema.INTEGER),
                                     aggregate("b", "Sum", XSchema.INTEGER),
                                     aggregate("ratio", "None", XSchema.DOUBLE));
      assertEquals(Arrays.asList(9.0, 6.0, 1.5, 1.0, 1.0, 1.0), values);
   }

   @Test
   @DisplayName("Bug #81668: the declared type of an " +
      "aggregate calc field is applied in a crosstab")
   void declaredTypeAppliedInCrosstab() throws Exception {
      for(String type : new String[] { XSchema.INTEGER, XSchema.DOUBLE, XSchema.STRING }) {
         tearDown();
         initCityAb();
         ratioFreehand(type);
         List<Object> values = crosstab(aggregate("a", "Sum", XSchema.INTEGER),
                                        aggregate("b", "Sum", XSchema.INTEGER),
                                        aggregate("ratio", "None", type));
         // A: Sum(a) 9, Sum(b) 6, ratio 1.5; B: 1, 1, 1
         List<Object> expected = XSchema.INTEGER.equals(type) ?
            Arrays.asList(9.0, 6.0, 1, 1.0, 1.0, 1) :
            XSchema.DOUBLE.equals(type) ? Arrays.asList(9.0, 6.0, 1.5, 1.0, 1.0, 1.0) :
            Arrays.asList(9.0, 6.0, "1.5", 1.0, 1.0, "1");
         assertEquals(expected, values, type);
      }
   }

   @Test
   @DisplayName("A text result of an aggregate calc field stays text")
   void textResult() throws Exception {
      for(String type : new String[] { XSchema.STRING, XSchema.DOUBLE }) {
         tearDown();
         initCityAb();
         vs.addAggrField("D", new AggregateRef(new ColumnRef(new AttributeRef(null, "a")),
                                               AggregateFormula.COUNT_ALL));
         addCalc("lvl", false, "if(field['Count([a])'] > 1) { 'High' } else { 'Low' }", type);
         List<Object> values = crosstab(aggregate("a", "Sum", XSchema.INTEGER),
                                        aggregate("lvl", "None", type));
         // A has two rows, B one
         assertEquals(Arrays.asList(9.0, "High", 1.0, "Low"), values, type);
      }
   }

   @Test
   @DisplayName("Bug #81668: a date aggregate calc field " +
      "and a comparison follow the declared type")
   void dateAndBooleanDeclaredTypes() throws Exception {
      for(String type : new String[] { XSchema.DATE, XSchema.TIME_INSTANT, XSchema.STRING }) {
         tearDown();
         init(new String[] { XSchema.STRING, XSchema.TIME_INSTANT },
              new Object[][] { { "city", "d" },
                               { "A", Timestamp.valueOf("2020-01-01 00:00:00") },
                               { "A", Timestamp.valueOf("2020-12-01 00:00:00") },
                               { "B", Timestamp.valueOf("2021-05-05 00:00:00") } });
         addCalc("mx", false, "field['Max([d])']", type);
         VSAggregateRef max = aggregate("d", "Max", XSchema.TIME_INSTANT);
         List<Object> values = crosstab(max, aggregate("mx", "None", type));
         Timestamp t1 = Timestamp.valueOf("2020-12-01 00:00:00");
         Timestamp t2 = Timestamp.valueOf("2021-05-05 00:00:00");
         List<Object> expected = XSchema.DATE.equals(type) ?
            Arrays.asList(t1, java.sql.Date.valueOf("2020-12-01"), t2,
                          java.sql.Date.valueOf("2021-05-05")) :
            XSchema.STRING.equals(type) ?
            Arrays.asList(t1, t1.toString(), t2, t2.toString()) :
            Arrays.asList(t1, t1, t2, t2);
         assertEquals(expected, values, type);
      }

      for(String type : new String[] { XSchema.BOOLEAN, XSchema.STRING, XSchema.INTEGER }) {
         tearDown();
         initCityAb();
         addCalc("gt", false, "field['Sum([a])'] > 5", type);
         List<Object> values = crosstab(aggregate("a", "Sum", XSchema.INTEGER),
                                        aggregate("gt", "None", type));
         // a Boolean that the declared type cannot hold stays a Boolean
         List<Object> expected = XSchema.STRING.equals(type) ?
            Arrays.asList(9.0, "true", 1.0, "false") : Arrays.asList(9.0, true, 1.0, false);
         assertEquals(expected, values, type);
      }
   }

   /** D(city, a, b) with Sum(a) as a viewsheet aggregate field and the aggregate calc big. */
   private void initBig(String expression, String type) throws Exception {
      initCityAb();
      vs.addAggrField("D", new AggregateRef(new ColumnRef(new AttributeRef(null, "a")),
                                            AggregateFormula.SUM));
      addCalc("big", false, expression, type);
   }

   @Test
   @DisplayName("Bug #82308: an aggregate calc value out of the range of " +
      "the declared integer type is kept in a crosstab")
   void outOfRangeDeclaredTypeKeptInCrosstab() throws Exception {
      // Sum(a): A 9, B 1. Each case: expression, type, expected A, expected B
      Object[][] cases = {
         { "field['Sum([a])'] * 10000000000", XSchema.INTEGER, 9.0E10, 1.0E10 },
         { "field['Sum([a])'] * -10000000000", XSchema.INTEGER, -9.0E10, -1.0E10 },
         { "field['Sum([a])'] * 1e19", XSchema.LONG, 9.0E19, 1.0E19 },
         // A is out of range and kept, B is in range and converted
         { "field['Sum([a])'] * 10000", XSchema.SHORT, 90000.0, (short) 10000 },
         { "field['Sum([a])'] * 100", XSchema.BYTE, 900.0, (byte) 100 },
         // in range: converted, the fraction cut
         { "field['Sum([a])'] * 2.5", XSchema.INTEGER, 22, 2 },
      };

      for(Object[] test : cases) {
         tearDown();
         initBig((String) test[0], (String) test[1]);
         List<Object> values = crosstab(aggregate("a", "Sum", XSchema.INTEGER),
                                        aggregate("big", "None", (String) test[1]));
         String label = test[1] + " " + test[0];
         assertEquals(Arrays.asList(9.0, test[2], 1.0, test[3]), values, label);
      }
   }

   @Test
   @DisplayName("Bug #82308: a text aggregate calc value declared Boolean " +
      "that is not a boolean is kept in a crosstab")
   void unconvertibleBooleanKeptInCrosstab() throws Exception {
      initBig("field['Sum([a])'] > 5 ? 'abc' : 'true'", XSchema.BOOLEAN);
      List<Object> values = crosstab(aggregate("a", "Sum", XSchema.INTEGER),
                                     aggregate("big", "None", XSchema.BOOLEAN));
      assertEquals(Arrays.asList(9.0, "abc", 1.0, true), values);
   }

   @Test
   @DisplayName("Bug #82308: an aggregate calc value out of the range of " +
      "the declared Integer type is kept in a chart")
   void outOfRangeDeclaredTypeKeptInChart() throws Exception {
      initBig("field['Sum([a])'] * 10000000000", XSchema.INTEGER);
      ChartVSAssembly chart = new ChartVSAssembly(vs, "C1");
      chart.setSourceInfo(new SourceInfo(SourceInfo.ASSET, null, "D"));
      VSChartInfo info = chart.getVSChartInfo();
      VSChartDimensionRef dim =
         new VSChartDimensionRef(new ColumnRef(new AttributeRef(null, "city")));
      dim.setGroupColumnValue("city");
      info.addXField(dim);
      VSChartAggregateRef agg = new VSChartAggregateRef();
      agg.setColumnValue("big");
      agg.setFormulaValue("None");
      info.addYField(agg);
      vs.addAssembly(chart);
      start();

      Object data = box.getData("C1");
      assertInstanceOf(DataSet.class, data, "chart data");
      DataSet set = (DataSet) data;
      int col = -1;

      for(int c = 0; c < set.getColCount(); c++) {
         if(set.getHeader(c).contains("big")) {
            col = c;
         }
      }

      assertTrue(col >= 0, "column big");
      Map<Object, Object> byCity = new HashMap<>();

      for(int r = 0; r < set.getRowCount(); r++) {
         byCity.put(set.getData("city", r), set.getData(col, r));
      }

      assertEquals(Map.of("A", 9.0E10, "B", 1.0E10), byCity);
   }

   /** D(city, a): the data set {2, 4, 4, 4, 5, 5, 7, 9} of one group. */
   private void initGroupOfEight() throws Exception {
      init(new String[] { XSchema.STRING, XSchema.INTEGER },
           new Object[][] { { "city", "a" }, { "A", 2 }, { "A", 4 }, { "A", 4 }, { "A", 4 },
                            { "A", 5 }, { "A", 5 }, { "A", 7 }, { "A", 9 } });
   }

   static java.util.stream.Stream<Arguments> aggregateCalcFormulas() {
      return java.util.stream.Stream.of(
         Arguments.of("Average", 5.0), Arguments.of("Count", 8.0),
         Arguments.of("DistinctCount", 5.0), Arguments.of("Mode", 4.0),
         Arguments.of("Median", 4.5), Arguments.of("Product", 201600.0),
         Arguments.of("Variance", 32.0 / 7), Arguments.of("StandardDeviation", Math.sqrt(32.0 / 7)),
         Arguments.of("PopulationVariance", 4.0), Arguments.of("PopulationStandardDeviation", 2.0));
   }

   @ParameterizedTest(name = "aggregate child: {0}")
   @MethodSource("aggregateCalcFormulas")
   void aggregateCalcOverEveryFormula_inACrosstab(String formula, double expected)
      throws Exception
   {
      initGroupOfEight();
      // the aggregate field of the table, as the aggregate dialog of a viewsheet stores it
      vs.addAggrField("D", new AggregateRef(new ColumnRef(new AttributeRef(null, "a")),
                                            AggregateFormula.getFormula(formula)));
      addCalc("c1", false, "field['" + formula + "([a])'] * 1", XSchema.DOUBLE);
      // calc fields and aggregate fields come from the viewsheet XML
      xmlRoundTrip();
      List<Object> values = crosstab(aggregate("a", "Sum", XSchema.INTEGER),
                                     aggregate("c1", "None", XSchema.DOUBLE));
      assertCells(expected, values, formula);
   }

   /** The Sum cell is 40; the calc cell is the expected number within 1e-9. */
   private static void assertCells(double expected, List<Object> values, String label) {
      assertEquals(2, values.size(), label);
      assertEquals(40.0, ((Number) values.get(0)).doubleValue(), 1e-9, label);
      assertEquals(expected, ((Number) values.get(1)).doubleValue(), 1e-9, label);
   }

   @ParameterizedTest(name = "aggregate name in lower case: {0}")
   @MethodSource("aggregateCalcFormulas")
   void aggregateNameInOtherCase_matchesLikeTheNameWrittenByTheDialog(String formula,
                                                                          double expected)
      throws Exception
   {
      initGroupOfEight();
      vs.addAggrField("D", new AggregateRef(new ColumnRef(new AttributeRef(null, "a")),
                                            AggregateFormula.getFormula(formula)));
      // the aggregate name is matched in any case (VSUtil.addUsedAggregateRef)
      addCalc("c1", false,
              "field['" + formula.toLowerCase(java.util.Locale.ROOT) + "([a])'] * 1",
              XSchema.DOUBLE);
      // the same path as the aggregate-child test: calc fields and aggregate fields come from the viewsheet XML
      xmlRoundTrip();
      List<Object> values = crosstab(aggregate("a", "Sum", XSchema.INTEGER),
                                     aggregate("c1", "None", XSchema.DOUBLE));
      assertCells(expected, values, formula);
   }

   /** D(city, c1, c2): one group A with the given int rows. */
   private void initTwoColumns(String c1, String c2, Object[][] rows) throws Exception {
      Object[][] data = new Object[rows.length + 1][];
      data[0] = new Object[] { "city", c1, c2 };

      for(int i = 0; i < rows.length; i++) {
         data[i + 1] = new Object[] { "A", rows[i][0], rows[i][1] };
      }

      init(new String[] { XSchema.STRING, XSchema.INTEGER, XSchema.INTEGER }, data);
   }

   @Test
   @DisplayName("Bug #81202: column names that differ only by a space or an underscore stay separate")
   void aggregateNames_spaceAndUnderscore() throws Exception {
      initTwoColumns("a b", "a_b", new Object[][] { { 5, 2 }, { 1, 1 } });
      addCalc("c1", false, "field['Sum([a b])'] - field['Sum([a_b])']", XSchema.DOUBLE);
      List<Object> values = crosstab(aggregate("a b", "Sum", XSchema.INTEGER),
                                     aggregate("a_b", "Sum", XSchema.INTEGER),
                                     aggregate("c1", "None", XSchema.DOUBLE));
      // Sum(a b) 6, Sum(a_b) 3
      assertEquals(Arrays.asList(6.0, 3.0, 3.0), values);
   }

   @Test
   @DisplayName("Bug #81202: a sharp-s column name does not throw")
   void aggregateNames_sharpS() throws Exception {
      initTwoColumns("straße", "b", new Object[][] { { 1, 1 }, { 2, 1 }, { 3, 1 } });
      addCalc("c1", false, "field['Sum([straße])'] * 1", XSchema.DOUBLE);
      List<Object> values = crosstab(aggregate("straße", "Sum", XSchema.INTEGER),
                                     aggregate("c1", "None", XSchema.DOUBLE));
      assertEquals(Arrays.asList(6.0, 6.0), values);
   }

   @Test
   @DisplayName("Bug #81202: a sharp-s column written in upper case (STRASSE) matches and is rewritten")
   void aggregateNames_sharpSWrittenAsSS() throws Exception {
      initTwoColumns("straße", "b", new Object[][] { { 1, 1 }, { 2, 1 }, { 3, 1 } });
      vs.addAggrField("D", new AggregateRef(new ColumnRef(new AttributeRef(null, "straße")),
                                            AggregateFormula.COUNT_ALL));
      addCalc("c1", false, "field['Count([STRASSE])'] * 1", XSchema.DOUBLE);
      xmlRoundTrip();
      List<Object> values = crosstab(aggregate("straße", "Sum", XSchema.INTEGER),
                                     aggregate("c1", "None", XSchema.DOUBLE));
      assertEquals(Arrays.asList(6.0, 3.0), values);
   }

   @Test
   @DisplayName("Bug #81202: a text output over an aggregate calc field written in another " +
      "case counts like the one written by the dialog")
   void outputAggregateNameInOtherCase() throws Exception {
      for(String spelling : new String[] { "Count([a])", "count([a])", "COUNT([A])" }) {
         tearDown();
         initGroupOfEight();
         vs.addAggrField("D", new AggregateRef(new ColumnRef(new AttributeRef(null, "a")),
                                               AggregateFormula.COUNT_ALL));
         addCalc("c1", false, "field['" + spelling + "'] * 1", XSchema.DOUBLE);
         xmlRoundTrip();
         TextVSAssembly text = new TextVSAssembly(vs, "T1");
         ScalarBindingInfo binding = new ScalarBindingInfo();
         binding.setTableName("D");
         binding.setColumnValue("c1");
         binding.setAggregateValue("None");
         text.setScalarBindingInfo(binding);
         vs.addAssembly(text);
         start();
         Object value = box.getData("T1");
         assertNotNull(value, spelling);
         assertEquals(8.0, ((Number) value).doubleValue(), 1e-9, spelling);
      }
   }
}
