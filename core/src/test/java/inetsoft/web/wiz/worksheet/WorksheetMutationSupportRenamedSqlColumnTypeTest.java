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
package inetsoft.web.wiz.worksheet;

import inetsoft.uql.schema.XSchema;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.jdbc.*;
import inetsoft.uql.schema.XTypeNode;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Bug #79176 (item 3), a renamed SQL column must not be reported as untyped, and a column
 * the parser and the result metadata really cannot type must still be.
 */
@Tag("core")
class WorksheetMutationSupportRenamedSqlColumnTypeTest {
   @Test
   void duplicateNamesWithRenamesProduceNoTypeWarning() {
      // JDBC returns four columns, the second order_id is dropped from the selection (n = 3), so
      // the columns are matched by name
      JDBCSelection selection = selection(true);
      ColumnSelection columns = columns(true);
      UniformSQL sql = parsedSql(selection);
      JDBCQuery query = queryWithMetadata("order_id", "order_id", "customer_id", "amount");

      List<String> untyped = WorksheetMutationSupport.applySqlResultTypes(columns, query, sql, null);

      assertEquals(List.of(), untyped);
      assertEquals(List.of(), WorksheetMutationSupport.untypedColumnWarnings(untyped));
      assertEquals("integer", ((ColumnRef) columns.getAttribute(1)).getDataType());
      assertEquals(XSchema.DOUBLE, ((ColumnRef) columns.getAttribute(2)).getDataType());
   }

   @Test
   void duplicateNamesWithoutRenamesProduceNoTypeWarning() {
      ColumnSelection columns = columns(false);
      List<String> untyped = WorksheetMutationSupport.applySqlResultTypes(
         columns, queryWithMetadata("order_id", "order_id", "customer_id", "amount"),
         parsedSql(selection(true)), null);

      assertEquals(List.of(), untyped);
   }

   @Test
   void genuinelyUntypedColumnStillWarns() {
      // "mystery" is neither typed by the parser nor present in the result metadata, and carries
      // a rename alias
      JDBCSelection selection = selection(true);
      selection.addColumn("orders.qty + 1");
      selection.setAlias(selection.getColumnCount() - 1, "mystery");
      ColumnSelection columns = columns(true);
      ColumnRef mystery = new ColumnRef(new AttributeRef("mystery"));
      mystery.setAlias("RENAMED");
      columns.addAttribute(mystery);
      JDBCQuery query = queryWithMetadata("order_id", "customer_id", "amount");

      List<String> untyped = WorksheetMutationSupport.applySqlResultTypes(
         columns, query, parsedSql(selection), null);

      assertEquals(List.of("mystery"), untyped);
      assertEquals(1, WorksheetMutationSupport.untypedColumnWarnings(untyped).size());
   }

   @Test
   void untypedColumnWithoutRenameStillWarns() {
      JDBCSelection selection = new JDBCSelection();
      selection.addColumn("orders.qty + 1");
      selection.setAlias(0, "mystery");
      ColumnSelection columns = new ColumnSelection();
      columns.addAttribute(new ColumnRef(new AttributeRef("mystery")));
      JDBCQuery query = queryWithMetadata();

      List<String> untyped = WorksheetMutationSupport.applySqlResultTypes(
         columns, query, parsedSql(selection), null);

      assertEquals(List.of("mystery"), untyped);
   }

   @Test
   void derivedTableAggregateAndPlainColumnsAreTypedFromMetadata() {
      // the parser fills the string default for every bare derived-table column (Bug #79762)
      JDBCSelection selection = derivedSelection("s.total", "s.qty");
      ColumnSelection columns = new ColumnSelection();
      columns.addAttribute(column("total", "string", null));
      columns.addAttribute(column("qty", "string", null));

      List<String> untyped = WorksheetMutationSupport.applySqlResultTypes(
         columns, queryWithMetadata("total", "qty"), derivedSql(selection), null);

      assertEquals(List.of(), untyped);
      assertEquals(XSchema.INTEGER, ((ColumnRef) columns.getAttribute(0)).getDataType());
      assertEquals(XSchema.INTEGER, ((ColumnRef) columns.getAttribute(1)).getDataType());
   }

   @Test
   void derivedTableAmountColumnGetsDoubleFromMetadata() {
      JDBCSelection selection = derivedSelection("s.amount");
      ColumnSelection columns = new ColumnSelection();
      columns.addAttribute(column("amount", "string", null));

      List<String> untyped = WorksheetMutationSupport.applySqlResultTypes(
         columns, queryWithMetadata("amount"), derivedSql(selection), null);

      assertEquals(List.of(), untyped);
      assertEquals(XSchema.DOUBLE, ((ColumnRef) columns.getAttribute(0)).getDataType());
   }

   @Test
   void derivedTableVarcharColumnStaysStringWithoutWarning() throws Exception {
      JDBCSelection selection = derivedSelection("s.name");
      ColumnSelection columns = new ColumnSelection();
      columns.addAttribute(column("name", "string", null));
      JDBCQuery query = mock(JDBCQuery.class);
      XTypeNode meta = new XTypeNode("table");
      XTypeNode node = XSchema.createPrimitiveType(XSchema.STRING);
      node.setName("name");
      meta.addChild(node);
      when(query.getOutputTypeForNonParseableSQL(any(), any(), any())).thenReturn(meta);

      List<String> untyped = WorksheetMutationSupport.applySqlResultTypes(
         columns, query, derivedSql(selection), null);

      assertEquals(List.of(), untyped);
      assertEquals(List.of(), WorksheetMutationSupport.untypedColumnWarnings(untyped));
      assertEquals(XSchema.STRING, ((ColumnRef) columns.getAttribute(0)).getDataType());
   }

   @Test
   void derivedTableColumnWarnsWhenMetadataCannotTypeIt() {
      JDBCSelection selection = derivedSelection("s.total");
      ColumnSelection columns = new ColumnSelection();
      columns.addAttribute(column("total", "string", null));

      List<String> untyped = WorksheetMutationSupport.applySqlResultTypes(
         columns, queryWithMetadata(), derivedSql(selection), null);

      assertEquals(List.of("total"), untyped);
   }

   @Test
   void baseTableStringColumnDoesNotFetchMetadata() {
      JDBCSelection selection = new JDBCSelection();
      selection.addColumn("orders.name");
      selection.setType("orders.name", XSchema.STRING);
      selection.setTable("orders.name", "orders");
      ColumnSelection columns = new ColumnSelection();
      columns.addAttribute(column("name", "string", null));
      JDBCQuery query = mock(JDBCQuery.class);
      UniformSQL sql = parsedSql(selection);
      when(sql.getTableName("orders")).thenReturn("ORDERS");

      assertEquals(List.of(),
                   WorksheetMutationSupport.applySqlResultTypes(columns, query, sql, null));
      verifyNoInteractions(query);
   }

   /** Columns of a derived table s, each typed with the string default as the parser does. */
   private static JDBCSelection derivedSelection(String... paths) {
      JDBCSelection selection = new JDBCSelection();

      for(String path : paths) {
         selection.addColumn(path);
         selection.setType(path, XSchema.STRING);
         selection.setTable(path, "s");
      }

      return selection;
   }

   private static UniformSQL derivedSql(JDBCSelection selection) {
      UniformSQL sql = parsedSql(selection);
      when(sql.getTableName("s")).thenReturn(new UniformSQL());
      return sql;
   }

   /** order_id, order_id (dropped from the columns), customer_id typed, amount untyped. */
   private static JDBCSelection selection(boolean duplicate) {
      JDBCSelection selection = new JDBCSelection();
      selection.addColumn("orders.order_id");
      selection.setType("orders.order_id", "integer");

      if(duplicate) {
         selection.addColumn("items.order_id");
         selection.setType("items.order_id", "integer");
      }

      selection.addColumn("orders.customer_id");
      selection.setType("orders.customer_id", "integer");
      selection.addColumn("orders.qty * orders.price");
      selection.setAlias(selection.getColumnCount() - 1, "amount");
      return selection;
   }

   /** The edited table's columns after the rename aliases were carried over. */
   private static ColumnSelection columns(boolean renamed) {
      ColumnSelection columns = new ColumnSelection();
      columns.addAttribute(column("order_id", "integer", null));
      columns.addAttribute(column("customer_id", "integer", renamed ? "CUST" : null));
      columns.addAttribute(column("amount", "string", renamed ? "REVENUE" : null));
      return columns;
   }

   private static ColumnRef column(String attribute, String type, String alias) {
      ColumnRef ref = new ColumnRef(new AttributeRef(attribute));
      ref.setDataType(type);

      if(alias != null) {
         ref.setAlias(alias);
      }

      return ref;
   }

   private static UniformSQL parsedSql(JDBCSelection selection) {
      UniformSQL sql = mock(UniformSQL.class);
      when(sql.getParseResult()).thenReturn(UniformSQL.PARSE_SUCCESS);
      when(sql.getSelection()).thenReturn(selection);
      return sql;
   }

   private static JDBCQuery queryWithMetadata(String... names) {
      JDBCQuery query = mock(JDBCQuery.class);
      XTypeNode meta = new XTypeNode("table");

      for(String name : names) {
         XTypeNode node = XSchema.createPrimitiveType(
            name.equals("amount") ? XSchema.DOUBLE : XSchema.INTEGER);
         node.setName(name);
         meta.addChild(node);
      }

      try {
         when(query.getOutputTypeForNonParseableSQL(any(), any(), any())).thenReturn(meta);
      }
      catch(Exception ex) {
         throw new IllegalStateException(ex);
      }

      return query;
   }
}