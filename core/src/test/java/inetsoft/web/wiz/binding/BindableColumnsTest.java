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

import inetsoft.web.wiz.binding.model.BindableField;
import inetsoft.web.wiz.binding.model.BindableTable;
import inetsoft.web.wiz.binding.model.FieldRef;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A column the source does not have used to bind cleanly. The shelf stored it, the call reported
 * success, and the crosstab rendered with no rows at all — which reads as "the data is empty"
 * rather than "that column does not exist". Found live on local-1200 by binding a column from a
 * different worksheet than the one the assembly pointed at, and confirmed with a nonsense name.
 */
@Tag("core")
class BindableColumnsTest {
   private static final List<BindableTable> TABLES = List.of(
      new BindableTable("Query1", List.of(new BindableField("PRICE", null),
                                          new BindableField("QUANTITY", null))),
      new BindableTable("Products", List.of(new BindableField("PRODUCT_NAME", null))));

   private static FieldRef dim(String column) {
      return new FieldRef(column, "dimension", null, null, null);
   }

   @Test
   void refusesAColumnThatAppearsInNoBindableTable() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> BindableColumns.require(TABLES, "Crosstab1", dim("NO_SUCH_COLUMN_XYZ")));

      assertTrue(thrown.getMessage().contains("NO_SUCH_COLUMN_XYZ"));
      assertTrue(thrown.getMessage().contains("PRICE"), "the message must list what is available");
   }

   /** The listing groups by table, and a column only has to exist in one of them. */
   @Test
   void acceptsAColumnFromAnyBindableTable() {
      assertDoesNotThrow(() -> BindableColumns.require(TABLES, "Crosstab1", dim("PRODUCT_NAME")));
      assertDoesNotThrow(() -> BindableColumns.require(TABLES, "Crosstab1", dim("PRICE")));
   }

   @Test
   void isCaseInsensitiveRatherThanRejectingAKnownColumnOnItsSpelling() {
      assertDoesNotThrow(() -> BindableColumns.require(TABLES, "Crosstab1", dim("price")));
   }

   /**
    * An empty listing means the binding tree could not be read — a different failure. Refusing
    * every column on the strength of it would turn a read problem into a write problem.
    */
   @Test
   void staysOutOfTheWayWhenNothingCouldBeListed() {
      assertDoesNotThrow(() -> BindableColumns.require(List.of(), "Crosstab1", dim("ANYTHING")));
      assertDoesNotThrow(() -> BindableColumns.require(null, "Crosstab1", dim("ANYTHING")));
   }

   @Test
   void ignoresAnAbsentOrBlankColumn() {
      assertDoesNotThrow(() -> BindableColumns.require(TABLES, "Crosstab1", dim(null)));
      assertDoesNotThrow(() -> BindableColumns.require(TABLES, "Crosstab1", dim("  ")));
      assertDoesNotThrow(() -> BindableColumns.require(TABLES, "Crosstab1", List.of()));
   }

   @Test
   void checksEveryFieldNotJustTheFirst() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> BindableColumns.require(TABLES, "Crosstab1", dim("PRICE"), dim("MADE_UP")));

      assertTrue(thrown.getMessage().contains("MADE_UP"));
   }
}
