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
package inetsoft.web.wiz.binding;

import inetsoft.web.binding.model.table.CrosstabBindingModel;
import inetsoft.web.wiz.binding.model.FieldRef;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The "Include all rows" option of a table or crosstab field (Bug #82313): per field on the wire,
 * per table in the binding model.
 */
@Tag("core")
class TableAllRowsTest {
   private static FieldRef dim(String column, Boolean allRows) {
      return new FieldRef(column, "dimension", null, null, null, null, null, null, null, null,
                          null, null, null, null, null, null, allRows);
   }

   @Test
   void entityIsTheTablePartOfTheColumn() {
      assertEquals("Product", TableAllRows.entityOf("Product:Category"));
      assertNull(TableAllRows.entityOf("Category"));
      assertNull(TableAllRows.entityOf(null));
   }

   @Test
   void readReportsTrueOnlyForFieldsOfAnIncludedTable() {
      CrosstabBindingModel model = new CrosstabBindingModel();
      model.setAllRows(List.of("Product"));

      List<FieldRef> out = TableAllRows.report(
         model, List.of(dim("Product:Category", null), dim("Order:Paid", null)));

      assertEquals(Boolean.TRUE, out.get(0).allRows());
      assertNull(out.get(1).allRows(), "a table that does not include all rows reports nothing");
   }

   @Test
   void readWithNoIncludedTableReturnsTheFieldsUntouched() {
      List<FieldRef> fields = List.of(dim("Product:Category", null));

      assertSame(fields, TableAllRows.report(new CrosstabBindingModel(), fields));
   }

   @Test
   void echoOfWhatWasReadIsANoOpEvenWithoutASource() {
      CrosstabBindingModel model = new CrosstabBindingModel();
      model.setAllRows(List.of("Product"));

      // a context-free rewrite of the shelf (rvs and source null) re-sends what the read reported
      TableAllRows.apply(model, TableAllRows.report(model, List.of(dim("Product:Category", null))),
                         null, null);

      assertEquals(List.of("Product"), model.getAllRows());
   }

   @Test
   void unspecifiedLeavesTheTableSettingAlone() {
      CrosstabBindingModel model = new CrosstabBindingModel();
      model.setAllRows(List.of("Product"));

      TableAllRows.apply(model, List.of(dim("Product:Category", null)), null, null);

      assertEquals(List.of("Product"), model.getAllRows());
   }

   @Test
   void settingTheOptionWithoutASourceToCheckItAgainstIsRefusedNamingTheField() {
      CrosstabBindingModel model = new CrosstabBindingModel();

      IllegalArgumentException e = assertThrows(
         IllegalArgumentException.class,
         () -> TableAllRows.apply(model, List.of(dim("Product:Category", true)), null, null));

      assertTrue(e.getMessage().contains("Product:Category"), e.getMessage());
      assertTrue(e.getMessage().contains("allRows"), e.getMessage());
      assertNull(model.getAllRows(), "a refused write must not change the model");
   }

   @Test
   void fieldsOfOneTableThatDisagreeAreRefused() {
      CrosstabBindingModel model = new CrosstabBindingModel();

      IllegalArgumentException e = assertThrows(
         IllegalArgumentException.class,
         () -> TableAllRows.apply(
            model, List.of(dim("Product:Category", true), dim("Product:Name", false)), null,
            null));

      assertTrue(e.getMessage().contains("Product"), e.getMessage());
      assertTrue(e.getMessage().contains("disagree"), e.getMessage());
   }

   @Test
   void turningTheOptionOffForATableThatDoesNotHaveItIsANoOp() {
      CrosstabBindingModel model = new CrosstabBindingModel();

      TableAllRows.apply(model, List.of(dim("Product:Category", false)), null, null);

      assertNull(model.getAllRows());
   }
}
