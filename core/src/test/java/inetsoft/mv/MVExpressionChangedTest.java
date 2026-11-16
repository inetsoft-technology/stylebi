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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #81422, an MV kept being used (and reported valid) after the expression of a worksheet
 * expression column it holds the data of was edited.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class MVExpressionChangedTest {
   /**
    * The stored side is the table's selection written into the MV metadata, the current side
    * the same table read back from the saved worksheet. An unchanged worksheet must not read as
    * changed.
    */
   @Test
   void unchangedWorksheetIsNotChangedAfterRoundTrip() throws Exception {
      Worksheet ws = worksheet("13", true);
      MVMetaData stored = roundTrip(metadata(ws));
      Worksheet saved = roundTrip(ws);

      assertFalse(stored.isExpressionChanged(columns(saved)));
   }

   @Test
   void editedSqlExpressionIsChanged() throws Exception {
      MVMetaData stored = roundTrip(metadata(worksheet("13", true)));
      Worksheet edited = roundTrip(worksheet("17", true));

      assertTrue(stored.isExpressionChanged(columns(edited)));
   }

   @Test
   void editedScriptExpressionIsChanged() throws Exception {
      MVMetaData stored = roundTrip(metadata(worksheet("field['A'] * 3", false)));
      Worksheet edited = roundTrip(worksheet("field['A'] * 4", false));

      assertTrue(stored.isExpressionChanged(columns(edited)));
   }

   @Test
   void switchedExpressionModeIsChanged() throws Exception {
      MVMetaData stored = roundTrip(metadata(worksheet("13", false)));

      assertTrue(stored.isExpressionChanged(columns(roundTrip(worksheet("13", true)))));
   }

   @Test
   void missingStoredColumnsIsNotChanged() {
      assertFalse(new MVMetaData("ws", "ORDERS", "ORDERS")
                     .isExpressionChanged(columns(worksheet("17", true))));
   }

   @Test
   void nullCurrentColumnsIsNotChanged() {
      assertFalse(metadata(worksheet("13", true)).isExpressionChanged(null));
   }

   /**
    * An MV saved before the expression text was stored holds a plain column, which can't be
    * told from the expression column and is kept.
    */
   @Test
   void storedPlainColumnIsNotChanged() {
      ColumnSelection plain = new ColumnSelection();
      plain.addAttribute(new ColumnRef(new AttributeRef(null, "Flag")));
      MVMetaData stored = new MVMetaData("ws", "ORDERS", "ORDERS");
      stored.setColumns(plain);

      assertFalse(stored.isExpressionChanged(columns(worksheet("17", true))));
   }

   @Test
   void columnMissingOnEitherSideIsNotChanged() {
      MVMetaData stored = metadata(worksheet("13", true));
      ColumnSelection current = new ColumnSelection();
      current.addAttribute(new ColumnRef(new AttributeRef(null, "OTHER")));

      assertFalse(stored.isExpressionChanged(current));
   }

   @Test
   void calculateFieldIsLeftToItsOwnCheck() {
      ColumnSelection stored = new ColumnSelection();
      stored.addAttribute(calc("1"));
      ColumnSelection current = new ColumnSelection();
      current.addAttribute(calc("2"));
      MVMetaData data = new MVMetaData("ws", "ORDERS", "ORDERS");
      data.setColumns(stored);

      assertFalse(data.isExpressionChanged(current));
   }

   @Test
   void mvDefComparesTheBoundTableOfTheWorksheet() {
      Worksheet ws = worksheet("13", true);
      MVDef def = new MVDef();
      ReflectionTestUtils.setField(def, "otname", "ORDERS");
      ReflectionTestUtils.setField(def, "data", metadata(ws));

      assertFalse(def.isExpressionChanged(ws));
      assertTrue(def.isExpressionChanged(worksheet("17", true)));
      // no worksheet / no such table can't be told
      assertFalse(def.isExpressionChanged((Worksheet) null));
      assertFalse(def.isExpressionChanged(new Worksheet()));
   }

   /**
    * Two MVs built from the same unchanged worksheet still share, an MV built from an edited
    * expression is not shareable by the stored one.
    */
   @Test
   void sharingFollowsTheExpression() {
      MVMetaData stored = shareable(worksheet("13", true));

      assertTrue(stored.isSharedBy(shareable(worksheet("13", true))));
      assertFalse(stored.isSharedBy(shareable(worksheet("17", true))));
      assertNotNull(MVMetaData.SHARE_HINT.get());
   }

   private MVMetaData shareable(Worksheet ws) {
      MVMetaData data = metadata(ws);
      data.setPlan("plan");
      return data;
   }

   private MVMetaData metadata(Worksheet ws) {
      MVMetaData data = new MVMetaData("ws", "ORDERS", "ORDERS");
      data.setColumns(columns(ws));
      return data;
   }

   private ColumnSelection columns(Worksheet ws) {
      return ((TableAssembly) ws.getAssembly("ORDERS")).getColumnSelection(true);
   }

   private Worksheet worksheet(String expression, boolean sql) {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly table = new EmbeddedTableAssembly(ws, "ORDERS");
      ws.addAssembly(table);

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
      return ws;
   }

   private CalculateRef calc(String expression) {
      ExpressionRef eref = new ExpressionRef(null, "Calc");
      eref.setExpression(expression);
      CalculateRef calc = new CalculateRef();
      calc.setDataRef(eref);
      calc.setName("Calc");
      return calc;
   }

   private MVMetaData roundTrip(MVMetaData data) throws Exception {
      StringWriter buf = new StringWriter();
      PrintWriter writer = new PrintWriter(buf);
      data.writeXML(writer);
      writer.flush();

      MVMetaData result = new MVMetaData();
      result.parseXML(parse(buf.toString()).getDocumentElement());
      return result;
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
