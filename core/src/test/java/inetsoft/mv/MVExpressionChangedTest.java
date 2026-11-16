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
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.erm.ExpressionRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.CalculateRef;
import inetsoft.util.Tool;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.w3c.dom.Document;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #81422, an MV kept being used (and reported valid) after the expression of a worksheet
 * expression column it holds the data of was edited.
 *
 * <p>The MV built for a viewsheet is built on the mirror table the viewsheet puts over the
 * worksheet table (the worksheet table is renamed T_O under a mirror T). The columns stored in
 * the MV metadata are the mirror's, plain attributes without the expression, so the expression
 * the MV was built with is read from the worksheet snapshot stored with the MV.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class MVExpressionChangedTest {
   /**
    * The shape the MV analysis stores: the metadata columns are plain attributes of the mirror
    * table, with no expression text.
    */
   @Test
   void storedMetadataColumnIsPlainWithoutExpression() {
      ColumnSelection cols = columns(mirrorWorksheet("13", true), "ORDERS");
      DataRef flag = cols.findAttribute(new ColumnRef(new AttributeRef("ORDERS_O", "Flag")));

      assertNotNull(flag);
      assertInstanceOf(AttributeRef.class, ((ColumnRef) flag).getDataRef());
   }

   /** An unchanged worksheet must not read as changed, however the snapshot is stored. */
   @Test
   void unchangedWorksheetIsNotChanged() throws Exception {
      MVDef def = def(roundTrip(mirrorWorksheet("13", true)));

      // the saved worksheet (status), and the viewsheet's worksheet (use time)
      assertFalse(def.isExpressionChanged(roundTrip(worksheet("13", true))));
      assertFalse(def.isExpressionChanged(mirrorWorksheet("13", true)));
   }

   @Test
   void editedSqlExpressionIsChanged() throws Exception {
      MVDef def = def(roundTrip(mirrorWorksheet("13", true)));

      assertTrue(def.isExpressionChanged(roundTrip(worksheet("17", true))));
      assertTrue(def.isExpressionChanged(mirrorWorksheet("17", true)));
   }

   @Test
   void editedScriptExpressionIsChanged() throws Exception {
      MVDef def = def(roundTrip(mirrorWorksheet("field['A'] * 3", false)));

      assertFalse(def.isExpressionChanged(roundTrip(worksheet("field['A'] * 3", false))));
      assertTrue(def.isExpressionChanged(roundTrip(worksheet("field['A'] * 4", false))));
   }

   @Test
   void switchedExpressionModeIsChanged() throws Exception {
      MVDef def = def(roundTrip(mirrorWorksheet("13", false)));

      assertTrue(def.isExpressionChanged(roundTrip(worksheet("13", true))));
   }

   /** The column selection of a table in another worksheet, e.g. a worksheet MV's bound table. */
   @Test
   void currentColumnSelectionIsComparedWithTheSnapshot() throws Exception {
      MVDef def = def(roundTrip(mirrorWorksheet("13", true)));

      assertFalse(def.isExpressionChanged(columns(worksheet("13", true), "ORDERS")));
      assertTrue(def.isExpressionChanged(columns(worksheet("17", true), "ORDERS")));
      assertFalse(def.isExpressionChanged((ColumnSelection) null));
   }

   @Test
   void missingSnapshotOrTableIsNotChanged() {
      assertFalse(def(null).isExpressionChanged(worksheet("17", true)));

      MVDef def = def(mirrorWorksheet("13", true));
      assertFalse(def.isExpressionChanged((Worksheet) null));
      assertFalse(def.isExpressionChanged(new Worksheet()));
   }

   /**
    * A table the viewsheet has no mirror over (already a worksheet mirror/composed table) has
    * only plain columns on both sides, nothing to compare.
    */
   @Test
   void plainColumnsAreNotChanged() {
      Worksheet snapshot = new Worksheet();
      snapshot.addAssembly(plainTable(snapshot, "ORDERS"));
      Worksheet current = new Worksheet();
      current.addAssembly(plainTable(current, "ORDERS"));

      assertFalse(def(snapshot).isExpressionChanged(current));
   }

   @Test
   void sameNamedColumnWithoutExpressionOnOneSideIsNotChanged() {
      ColumnSelection stored = new ColumnSelection();
      stored.addAttribute(new ColumnRef(new AttributeRef(null, "Flag")));

      assertFalse(MVMetaData.isExpressionChanged(stored, columns(worksheet("17", true),
                                                                   "ORDERS")));
      assertFalse(MVMetaData.isExpressionChanged(null, stored));
   }

   @Test
   void calculateFieldIsLeftToItsOwnCheck() {
      ColumnSelection stored = new ColumnSelection();
      stored.addAttribute(calc("1"));
      ColumnSelection current = new ColumnSelection();
      current.addAttribute(calc("2"));

      assertFalse(MVMetaData.isExpressionChanged(stored, current));
   }

   /**
    * A metadata that keeps the expression text (e.g. the columns of a worksheet table that has
    * no mirror) is not shareable by an MV built from another expression.
    */
   @Test
   void sharingFollowsTheExpression() {
      MVMetaData stored = shareable(worksheet("13", true));

      assertTrue(stored.isSharedBy(shareable(worksheet("13", true))));
      assertFalse(stored.isSharedBy(shareable(worksheet("17", true))));
      assertNotNull(MVMetaData.SHARE_HINT.get());
   }

   private MVDef def(Worksheet snapshot) {
      MVDef def = new MVDef();
      ReflectionTestUtils.setField(def, "otname", "ORDERS");
      ReflectionTestUtils.setField(def, "tname", "ORDERS");
      def.container = new MVDef.MVContainer(new ArrayList<>(), snapshot);
      return def;
   }

   private MVMetaData shareable(Worksheet ws) {
      MVMetaData data = new MVMetaData("ws", "ORDERS", "ORDERS");
      data.setColumns(columns(ws, "ORDERS"));
      data.setPlan("plan");
      return data;
   }

   private ColumnSelection columns(Worksheet ws, String table) {
      return ((TableAssembly) ws.getAssembly(table)).getColumnSelection(true);
   }

   /** The worksheet as the author saves it: the table with the expression column. */
   private Worksheet worksheet(String expression, boolean sql) {
      Worksheet ws = new Worksheet();
      ws.addAssembly(table(ws, "ORDERS", expression, sql));
      return ws;
   }

   /** The worksheet of a viewsheet: the table is renamed ORDERS_O under the mirror ORDERS. */
   private Worksheet mirrorWorksheet(String expression, boolean sql) {
      Worksheet ws = new Worksheet();
      TableAssembly table = table(ws, "ORDERS_O", expression, sql);
      ws.addAssembly(table);
      ws.addAssembly(new MirrorTableAssembly(ws, "ORDERS", table));
      return ws;
   }

   private EmbeddedTableAssembly plainTable(Worksheet ws, String name) {
      EmbeddedTableAssembly table = new EmbeddedTableAssembly(ws, name);
      ColumnSelection cols = new ColumnSelection();
      ColumnRef id = new ColumnRef(new AttributeRef(null, "ID"));
      id.setDataType(XSchema.INTEGER);
      cols.addAttribute(id);
      table.setColumnSelection(cols);
      return table;
   }

   private EmbeddedTableAssembly table(Worksheet ws, String name, String expression,
                                       boolean sql)
   {
      EmbeddedTableAssembly table = new EmbeddedTableAssembly(ws, name);

      ColumnSelection cols = new ColumnSelection();
      ColumnRef id = new ColumnRef(new AttributeRef(null, "ID"));
      id.setDataType(XSchema.INTEGER);
      cols.addAttribute(id);

      ExpressionRef eref = new ExpressionRef(null, "Flag");
      eref.setExpression(expression);
      ColumnRef flag = new ColumnRef(eref);
      flag.setSQL(sql);
      flag.setDataType(XSchema.INTEGER);
      cols.addAttribute(flag);

      table.setColumnSelection(cols);
      return table;
   }

   private CalculateRef calc(String expression) {
      ExpressionRef eref = new ExpressionRef(null, "Calc");
      eref.setExpression(expression);
      CalculateRef calc = new CalculateRef();
      calc.setDataRef(eref);
      calc.setName("Calc");
      return calc;
   }

   private Worksheet roundTrip(Worksheet ws) throws Exception {
      StringWriter buf = new StringWriter();
      PrintWriter writer = new PrintWriter(buf);
      ws.writeXML(writer);
      writer.flush();

      Worksheet result = new Worksheet();
      result.parseXML(parse(buf.toString()).getDocumentElement());
      return result;
   }

   private Document parse(String xml) throws Exception {
      return Tool.parseXML(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
   }
}
