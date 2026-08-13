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
package inetsoft.web.wiz.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.web.wiz.model.WorksheetTable;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for {@link WorksheetTableService#resolveRequestedColumns}.
 *
 * <p>The bug: a {@code physical table} request's explicit {@code columns} list was turned into
 * {@code AttributeRef}s verbatim, with no reconciliation against the source table's real columns.
 * A hallucinated name (ORDER_AMOUNT on a table that has no such column) entered the assembly's
 * stored column selection, and every read-back — {@code /ws/table}'s response and
 * {@code /ws/structure} — then advertised it as real, so the whole downstream chain bound a chart
 * to it. Nothing failed loud: {@code PreAssetQuery.validateColumnSelection} silently removes
 * columns the source does not have, so the generated SQL stayed valid and the execution probe
 * passed. The result was an empty chart with no error, plus a column that vanished from the
 * worksheet the next time Composer refreshed it.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class WorksheetTableServiceColumnValidationTest {
   private static final ObjectMapper MAPPER = new ObjectMapper();

   /** The real Examples/Orders ORDERS columns — note there is no ORDER_AMOUNT. */
   private static final List<String> ORDERS_COLUMNS =
      List.of("ORDER_ID", "CUSTOMER_ID", "EMPLOYEE_ID", "ORDER_DATE", "DISCOUNT", "PAID");

   private static WorksheetTableService service() {
      // resolveRequestedColumns reads only its parameters, never instance state.
      return new WorksheetTableService(null, null, null, null, null, null, null, null, null);
   }

   private static WorksheetTable request(String json) throws Exception {
      return MAPPER.readValue(json, WorksheetTable.class);
   }

   private static List<WorksheetTable.ColumnInfo> columns(String... names) {
      List<WorksheetTable.ColumnInfo> cols = new ArrayList<>();

      for(String name : names) {
         WorksheetTable.ColumnInfo col = new WorksheetTable.ColumnInfo();
         col.setName(name);
         cols.add(col);
      }

      return cols;
   }

   private static List<String> names(List<WorksheetTable.ColumnInfo> cols) {
      return cols.stream().map(WorksheetTable.ColumnInfo::getName).toList();
   }

   @Test
   void hallucinatedColumnFailsLoud() {
      List<WorksheetTable.ColumnInfo> requested = columns("ORDER_ID", "DISCOUNT", "ORDER_AMOUNT");

      IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () ->
         service().resolveRequestedColumns(requested, ORDERS_COLUMNS, Set.of()));

      // The offending column and the real ones are both named, so the caller can self-correct.
      assertTrue(e.getMessage().contains("ORDER_AMOUNT"), e.getMessage());
      assertTrue(e.getMessage().contains("ORDER_DATE"), e.getMessage());
      // A column that DOES exist must not be reported as missing.
      assertFalse(e.getMessage().startsWith("Column(s) not found in source table: ORDER_ID"),
                  e.getMessage());
   }

   @Test
   void everyUnresolvableColumnIsReportedTogether() {
      List<WorksheetTable.ColumnInfo> requested = columns("ORDER_AMOUNT", "DISCOUNT_RATE");

      IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () ->
         service().resolveRequestedColumns(requested, ORDERS_COLUMNS, Set.of()));

      assertTrue(e.getMessage().contains("ORDER_AMOUNT"), e.getMessage());
      assertTrue(e.getMessage().contains("DISCOUNT_RATE"), e.getMessage());
   }

   @Test
   void exactNamesPassThroughUnchanged() {
      List<WorksheetTable.ColumnInfo> requested = columns("ORDER_ID", "DISCOUNT");

      assertEquals(List.of("ORDER_ID", "DISCOUNT"),
                   names(service().resolveRequestedColumns(requested, ORDERS_COLUMNS, Set.of())));
   }

   @Test
   void caseOnlyDifferenceIsCanonicalizedToTheSourceSpelling() {
      List<WorksheetTable.ColumnInfo> requested = columns("order_id", "Discount");

      assertEquals(List.of("ORDER_ID", "DISCOUNT"),
                   names(service().resolveRequestedColumns(requested, ORDERS_COLUMNS, Set.of())));
   }

   @Test
   void tableQualifiedNameIsUnqualified() {
      List<WorksheetTable.ColumnInfo> requested = columns("ORDERS.ORDER_ID", "SA.ORDERS.DISCOUNT");

      assertEquals(List.of("ORDER_ID", "DISCOUNT"),
                   names(service().resolveRequestedColumns(requested, ORDERS_COLUMNS, Set.of())));
   }

   @Test
   void aSourceColumnWhoseOwnNameContainsADotWinsOverUnqualifying() {
      List<String> source = List.of("A.B", "B");
      List<WorksheetTable.ColumnInfo> requested = columns("A.B");

      // "A.B" is itself a column, so it must NOT be unqualified to "B".
      assertEquals(List.of("A.B"),
                   names(service().resolveRequestedColumns(requested, source, Set.of())));
   }

   @Test
   void aColumnTheRequestDerivesItselfIsNeitherRejectedNorKept() throws Exception {
      WorksheetTable req = request("""
         {
           "tableName": "t", "tableType": "physical table",
           "columns": [ { "name": "ORDER_ID" }, { "name": "discount_status" } ],
           "expressionColumns": [
             { "name": "discount_status", "alias": "discount_status",
               "expression": "CASE WHEN DISCOUNT > 0 THEN 'Has Discount' ELSE 'No Discount' END",
               "type": "string", "sql": true }
           ]
         }
         """);

      // discount_status is derived by this same request, so the source's metadata cannot know it —
      // it must not be reported as missing. It must ALSO not be returned: buildColumnSelection would
      // turn it into a plain AttributeRef, and ColumnSelection.addAttribute is exclusive with
      // ColumnRef equality by name, so that attribute would shadow the ExpressionRef that
      // applyExpressionColumns adds next — silently discarding the expression and leaving a phantom
      // column that validateColumnSelection then drops from the query.
      assertEquals(List.of("ORDER_ID"),
                   names(service().resolveRequestedColumns(
                      req.getColumns(), ORDERS_COLUMNS, Set.of("discount_status"))));
   }

   @Test
   void aDerivedColumnIsRecognizedRegardlessOfSpelling() {
      // The caller may spell it differently in columns than in expressionColumns; reporting that as
      // "not found in source table" would misdirect.
      List<WorksheetTable.ColumnInfo> requested = columns("ORDER_ID", "Discount_Status");

      assertEquals(List.of("ORDER_ID"),
                   names(service().resolveRequestedColumns(
                      requested, ORDERS_COLUMNS, Set.of("discount_status"))));
   }

   @Test
   void aDerivedNameThatIsAlsoARealSourceColumnStillResolvesToTheSourceColumn() {
      // The derived branch is reached ONLY when the source has no such column, so an expression
      // named after a real column leaves the existing resolution untouched.
      List<WorksheetTable.ColumnInfo> requested = columns("ORDER_ID", "discount");

      assertEquals(List.of("ORDER_ID", "DISCOUNT"),
                   names(service().resolveRequestedColumns(
                      requested, ORDERS_COLUMNS, Set.of("DISCOUNT"))));
   }

   @Test
   void aHallucinatedColumnIsStillReportedWhenTheRequestDerivesOtherColumns() {
      List<WorksheetTable.ColumnInfo> requested = columns("ORDER_AMOUNT", "discount_status");

      IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () ->
         service().resolveRequestedColumns(requested, ORDERS_COLUMNS, Set.of("discount_status")));

      assertTrue(e.getMessage().contains("ORDER_AMOUNT"), e.getMessage());
      assertFalse(e.getMessage().contains("discount_status"), e.getMessage());
   }

   @Test
   void unavailableSourceMetadataSkipsReconciliationRatherThanFailingTheCreate() {
      List<WorksheetTable.ColumnInfo> requested = columns("ORDER_AMOUNT");

      assertEquals(List.of("ORDER_AMOUNT"),
                   names(service().resolveRequestedColumns(requested, List.of(), Set.of())));
      assertEquals(List.of("ORDER_AMOUNT"),
                   names(service().resolveRequestedColumns(requested, null, Set.of())));
   }

   @Test
   void blankColumnNameIsReported() {
      List<WorksheetTable.ColumnInfo> requested = columns("   ");

      IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () ->
         service().resolveRequestedColumns(requested, ORDERS_COLUMNS, Set.of()));

      assertTrue(e.getMessage().contains("<empty>"), e.getMessage());
   }
}
