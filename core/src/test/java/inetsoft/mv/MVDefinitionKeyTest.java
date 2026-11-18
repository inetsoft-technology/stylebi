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
package inetsoft.mv;

import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.ExpressionRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.uql.viewsheet.CalculateRef;
import inetsoft.util.Tool;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.awt.Point;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #81366, what the fingerprint of the definition of a worksheet table varies with: whatever
 * defines the data of the table and of the tables it is composed of, and nothing else.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class MVDefinitionKeyTest {
   @Test
   void sameDefinitionHasTheSameKey() {
      assertEquals(key(bound(new Worksheet(), "T", null)), key(bound(new Worksheet(), "T", null)));
      assertEquals(key(joined(null).getAssembly("J")), key(joined(null).getAssembly("J")));
      assertNull(MVDefinitionKey.create(null));
   }

   @Test
   void keyIsStableAcrossTheStorageOfTheWorksheet() throws Exception {
      Worksheet ws = new Worksheet();
      TableAssembly t = bound(ws, "T", "12100");
      t.setAggregateInfo(aggregate(t, AggregateFormula.SUM));
      t.setMaxRows(50);

      assertEquals(key(t), key(roundTrip(ws).getAssembly("T")));

      Worksheet jws = joined("12100");
      assertEquals(key(jws.getAssembly("J")), key(roundTrip(jws).getAssembly("J")));
   }

   @Test
   void addedOrEditedConditionChangesTheKey() {
      String none = key(bound(new Worksheet(), "T", null));
      String one = key(bound(new Worksheet(), "T", "12100"));

      assertNotEquals(none, one);
      assertNotEquals(one, key(bound(new Worksheet(), "T", "12200")));

      TableAssembly post = bound(new Worksheet(), "T", null);
      post.setPostConditionList(conditions("12100"));
      assertNotEquals(none, key(post));
      assertNotEquals(one, key(post));

      TableAssembly mv = bound(new Worksheet(), "T", null);
      mv.setMVUpdatePreConditionList(conditions("12100"));
      assertNotEquals(none, key(mv));
   }

   @Test
   void aggregateDistinctAndMaxRowsChangeTheKey() {
      String plain = key(bound(new Worksheet(), "T", null));

      TableAssembly sum = bound(new Worksheet(), "T", null);
      sum.setAggregateInfo(aggregate(sum, AggregateFormula.SUM));
      TableAssembly max = bound(new Worksheet(), "T", null);
      max.setAggregateInfo(aggregate(max, AggregateFormula.MAX));
      TableAssembly distinct = bound(new Worksheet(), "T", null);
      distinct.setDistinct(true);
      TableAssembly rows = bound(new Worksheet(), "T", null);
      rows.setMaxRows(10);

      assertNotEquals(plain, key(sum));
      assertNotEquals(key(sum), key(max));
      assertNotEquals(plain, key(distinct));
      assertNotEquals(plain, key(rows));
   }

   @Test
   void sourceChangesTheKey() {
      BoundTableAssembly other = bound(new Worksheet(), "T", null);
      other.setSourceInfo(new SourceInfo(SourceInfo.PHYSICAL_TABLE, "ds1", "CUSTOMERS"));
      BoundTableAssembly otherSource = bound(new Worksheet(), "T", null);
      otherSource.setSourceInfo(new SourceInfo(SourceInfo.PHYSICAL_TABLE, "ds2", "ORDERS"));
      String key = key(bound(new Worksheet(), "T", null));

      assertNotEquals(key, key(other));
      assertNotEquals(key, key(otherSource));
   }

   @Test
   void expressionColumnChangesTheKey() {
      String one = key(withExpression("1", false));

      assertEquals(one, key(withExpression("1", false)));
      assertNotEquals(one, key(withExpression("2", false)));
      assertNotEquals(one, key(withExpression("1", true)));
      assertNotEquals(one, key(bound(new Worksheet(), "T", null)));
   }

   @Test
   void embeddedDataChangesTheKey() {
      assertNotEquals(key(embedded("a")), key(embedded("b")));
      assertEquals(key(embedded("a")), key(embedded("a")));
   }

   @Test
   void joinChangesTheKey() {
      String join = key(joined(null).getAssembly("J"));
      Worksheet left = joined(null);
      ((CompositeTableAssembly) left.getAssembly("J")).setOperator(
         "T1", "T2", operator(TableAssemblyOperator.LEFT_JOIN, "STATE"));
      Worksheet other = joined(null);
      ((CompositeTableAssembly) other.getAssembly("J")).setOperator(
         "T1", "T2", operator(TableAssemblyOperator.INNER_JOIN, "ORDER_ID"));

      assertNotEquals(join, key(left.getAssembly("J")));
      assertNotEquals(join, key(other.getAssembly("J")));
      assertNotEquals(key(bound(new Worksheet(), "T1", null)), join);
   }

   @Test
   void conditionOfASubTableChangesTheKeyOfTheTableComposedOfIt() {
      String join = key(joined(null).getAssembly("J"));
      Worksheet filtered = joined(null);
      ((TableAssembly) filtered.getAssembly("T2")).setPreConditionList(conditions("12100"));
      assertNotEquals(join, key(filtered.getAssembly("J")));

      // under a mirror, the table of the mirror is a table of its own
      Worksheet mirrored = joined(null);
      mirrored.addAssembly(new MirrorTableAssembly(
         mirrored, "M", (TableAssembly) mirrored.getAssembly("J")));
      String mirror = key(mirrored.getAssembly("M"));
      ((TableAssembly) mirrored.getAssembly("T1")).setPreConditionList(conditions("1"));
      assertNotEquals(mirror, key(mirrored.getAssembly("M")));
   }

   @Test
   void whatDoesNotDefineTheDataKeepsTheKey() {
      Worksheet ws = new Worksheet();
      TableAssembly table = bound(ws, "T", "12100");
      String key = key(table);

      ((WSAssembly) table).setPixelOffset(new Point(500, 400));
      ((WSAssembly) table).setDescription("moved, described");
      assertEquals(key, key(table));

      // visibility and order of the columns
      ColumnSelection cols = table.getColumnSelection(false);
      ((ColumnRef) cols.getAttribute(0)).setVisible(false);
      assertEquals(key, key(table));
      ColumnSelection reversed = new ColumnSelection();

      for(int i = cols.getAttributeCount() - 1; i >= 0; i--) {
         reversed.addAttribute(cols.getAttribute(i));
      }

      table.setColumnSelection(reversed, false);
      assertEquals(key, key(table));

      // the calculate field of a viewsheet
      ExpressionRef eref = new ExpressionRef(null, "Calc");
      eref.setExpression("1");
      CalculateRef calc = new CalculateRef();
      calc.setDataRef(eref);
      calc.setName("Calc");
      ColumnSelection withCalc = (ColumnSelection) table.getColumnSelection(false).clone();
      withCalc.addAttribute(calc);
      table.setColumnSelection(withCalc, false);
      assertEquals(key, key(table));
   }

   @Test
   void sortDecidesTheRowsOnlyIfTheRowsAreLimited() {
      TableAssembly table = bound(new Worksheet(), "T", null);
      String key = key(table);
      SortInfo sort = new SortInfo();
      sort.addSort(new SortRef(column("STATE")));
      table.setSortInfo(sort);
      assertEquals(key, key(table));

      table.setMaxRows(10);
      String limited = key(table);
      table.setSortInfo(new SortInfo());
      assertNotEquals(limited, key(table));
   }

   /**
    * The viewsheet puts a mirror T over the worksheet table, renamed T_O. The worksheet as saved
    * has no mirror.
    */
   @Test
   void mirrorOfTheViewsheetIsLookedThrough() {
      String key = key(bound(new Worksheet(), "ORDERS", "12100"));

      Worksheet ws = new Worksheet();
      TableAssembly inner = bound(ws, "ORDERS_O", "12100");
      ws.addAssembly(new MirrorTableAssembly(ws, "ORDERS", inner));

      assertEquals(key, key(ws.getAssembly("ORDERS")));
      assertEquals(key, key(inner));

      // a mirror of another table is a table of its own
      Worksheet other = new Worksheet();
      TableAssembly t = bound(other, "OTHER", "12100");
      other.addAssembly(new MirrorTableAssembly(other, "ORDERS", t));
      assertNotEquals(key, key(other.getAssembly("ORDERS")));
   }

   @Test
   void keyIsKeptWhereverTheMetadataGoes() throws Exception {
      MVMetaData data = new MVMetaData("ws", "ORDERS", "ORDERS");
      data.setDefinitionKey("ORDERS", "abc");

      assertEquals("abc", data.getDefinitionKey());
      assertEquals("abc", data.deepClone().getDefinitionKey());
      assertEquals("abc", ((MVMetaData) data.clone()).getDefinitionKey());

      MVMetaData parsed = roundTrip(data);
      assertEquals("abc", parsed.getDefinitionKey());
      assertEquals("ORDERS", parsed.getDefinitionTable());

      // an MV stored before the key was recorded
      parsed = roundTrip(new MVMetaData("ws", "ORDERS", "ORDERS"));
      assertNull(parsed.getDefinitionKey());
      assertNull(parsed.getDefinitionTable());
   }

   @Test
   void unrecordedKeyReadsUnchanged() {
      MVDef def = new MVDef();
      Worksheet ws = new Worksheet();
      TableAssembly table = bound(ws, "ORDERS", "12100");

      assertFalse(def.isDefinitionChanged(table));
      assertFalse(def.isDefinitionChanged(ws));
      assertFalse(def.isDefinitionChanged((Worksheet) null));
      assertFalse(def.isStale((Worksheet) null));
   }

   @Test
   void recordedKeyIsComparedWithTheTable() {
      Worksheet ws = new Worksheet();
      TableAssembly table = bound(ws, "ORDERS", "12100");
      MVDef mv = def(table, table.getName());

      assertFalse(mv.isDefinitionChanged(table));
      assertFalse(mv.isDefinitionChanged(ws));
      assertFalse(mv.isStale(table));

      table.setPreConditionList(conditions("12200"));
      assertTrue(mv.isDefinitionChanged(table));
      assertTrue(mv.isDefinitionChanged(ws));
      assertTrue(mv.isStale(table));
      assertTrue(mv.isStale(ws));

      // a table that is not in the worksheet can't be told
      assertFalse(mv.isDefinitionChanged(new Worksheet()));
      assertFalse(mv.isDefinitionChanged((TableAssembly) null));
   }

   /** The association MV is of a derived table, its key is of the bound table. */
   @Test
   void keyIsReadFromTheTableItWasRecordedFor() {
      Worksheet ws = new Worksheet();
      TableAssembly table = bound(ws, "ORDERS", "12100");
      MVDef mv = def(table, "ASSOCIATION_ORDERS");
      mv.setDefinitionKey("ORDERS", key(table));

      assertFalse(mv.isDefinitionChanged(ws));
      table.setPreConditionList(conditions("12200"));
      assertTrue(mv.isDefinitionChanged(ws));
   }

   private static MVDef def(TableAssembly table, String otname) {
      MVDef mv = new MVDef();
      ReflectionTestUtils.setField(mv, "otname", otname);
      ReflectionTestUtils.setField(mv, "data", new MVMetaData("ws", otname, otname));
      mv.setDefinitionKey(table.getName(), key(table));
      // the snapshot the expression check reads, which holds the table as it was built
      mv.container = new MVDef.MVContainer(new ArrayList<>(),
                                           (Worksheet) table.getWorksheet().clone());
      return mv;
   }

   private static String key(Assembly table) {
      return key((TableAssembly) table);
   }

   private static String key(TableAssembly table) {
      String key = MVDefinitionKey.create(table);
      assertNotNull(key);
      return key;
   }

   private static ColumnRef column(String name) {
      ColumnRef col = new ColumnRef(new AttributeRef("ORDERS", name));
      col.setDataType("DISCOUNT".equals(name) ? XSchema.DOUBLE : XSchema.STRING);
      return col;
   }

   private static BoundTableAssembly bound(Worksheet ws, String name, String filter) {
      PhysicalBoundTableAssembly table = new PhysicalBoundTableAssembly(ws, name);
      table.setSourceInfo(new SourceInfo(SourceInfo.PHYSICAL_TABLE, "ds1", "ORDERS"));
      ColumnSelection cols = new ColumnSelection();

      for(String col : new String[] { "STATE", "ORDER_ID", "DISCOUNT" }) {
         cols.addAttribute(column(col));
      }

      table.setColumnSelection(cols, false);
      table.setColumnSelection(cols, true);

      if(filter != null) {
         table.setPreConditionList(conditions(filter));
      }

      ws.addAssembly(table);
      return table;
   }

   private static ConditionList conditions(String value) {
      AssetCondition cond = new AssetCondition();
      cond.setOperation(XCondition.EQUAL_TO);
      cond.addValue(value);
      ConditionList list = new ConditionList();
      list.append(new ConditionItem(column("ORDER_ID"), cond, 0));
      return list;
   }

   private static AggregateInfo aggregate(TableAssembly table, AggregateFormula formula) {
      AggregateInfo info = new AggregateInfo();
      ColumnSelection cols = table.getColumnSelection(false);
      info.addGroup(new GroupRef(cols.getAttribute(0)));
      info.addAggregate(new AggregateRef(cols.getAttribute(2), formula));
      return info;
   }

   private static TableAssembly withExpression(String expression, boolean sql) {
      TableAssembly table = bound(new Worksheet(), "T", null);
      ExpressionRef eref = new ExpressionRef(null, "Flag");
      eref.setExpression(expression);
      ColumnRef col = new ColumnRef(eref);
      col.setSQL(sql);
      col.setDataType(XSchema.STRING);
      ColumnSelection cols = (ColumnSelection) table.getColumnSelection(false).clone();
      cols.addAttribute(col);
      table.setColumnSelection(cols, false);
      table.setColumnSelection(cols, true);
      return table;
   }

   private static TableAssembly embedded(String value) {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly table = new EmbeddedTableAssembly(ws, "E");
      ColumnSelection cols = new ColumnSelection();
      cols.addAttribute(column("STATE"));
      table.setColumnSelection(cols, false);
      table.setColumnSelection(cols, true);
      table.setEmbeddedData(new XEmbeddedTable(new String[] { "string" },
                                               new Object[][] { { "STATE" }, { value } }));
      ws.addAssembly(table);
      return table;
   }

   private static TableAssemblyOperator operator(int operation, String column) {
      return new TableAssemblyOperator("T1", column(column), "T2", column(column), operation);
   }

   /** J: T1 inner join T2 on STATE. */
   private static Worksheet joined(String filter) {
      Worksheet ws = new Worksheet();
      TableAssembly t1 = bound(ws, "T1", filter);
      TableAssembly t2 = bound(ws, "T2", null);
      RelationalJoinTableAssembly join = new RelationalJoinTableAssembly(
         ws, "J", new TableAssembly[] { t1, t2 },
         new TableAssemblyOperator[] { operator(TableAssemblyOperator.INNER_JOIN, "STATE") });
      ws.addAssembly(join);
      return ws;
   }

   private static Worksheet roundTrip(Worksheet ws) throws Exception {
      StringWriter buf = new StringWriter();
      PrintWriter writer = new PrintWriter(buf);
      ws.writeXML(writer);
      writer.flush();

      Worksheet result = new Worksheet();
      result.parseXML(Tool.parseXML(new ByteArrayInputStream(
         buf.toString().getBytes(StandardCharsets.UTF_8))).getDocumentElement());
      return result;
   }

   private static MVMetaData roundTrip(MVMetaData data) throws Exception {
      StringWriter buf = new StringWriter();
      PrintWriter writer = new PrintWriter(buf);
      data.writeXML(writer);
      writer.flush();

      MVMetaData result = new MVMetaData();
      result.parseXML(Tool.parseXML(new ByteArrayInputStream(
         buf.toString().getBytes(StandardCharsets.UTF_8))).getDocumentElement());
      return result;
   }
}
