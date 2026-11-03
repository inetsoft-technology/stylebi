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

import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.web.wiz.pairing.WizAgentTestSupport;
import inetsoft.web.wiz.worksheet.WorksheetMutationSupport.ConditionNode;
import inetsoft.web.wiz.worksheet.WorksheetMutationSupport.ConditionSpec;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #80075 (1): DATE_IN on a column that is known not to be a date was stored and matched no
 * row. The add_filter path and the set_conditions path must both refuse it.
 */
@Tag("core")
@WizAgentTestSupport
class WorksheetMutationSupportDateInTypeTest {
   private static BoundTableAssembly table() {
      BoundTableAssembly t = new BoundTableAssembly(new Worksheet(), "T");
      ColumnSelection cs = new ColumnSelection();
      cs.addAttribute(column("STATE", XSchema.STRING));
      cs.addAttribute(column("QTY", XSchema.INTEGER));
      cs.addAttribute(column("ORDER_DATE", XSchema.DATE));
      cs.addAttribute(column("SEEN_AT", XSchema.TIME_INSTANT));
      cs.addAttribute(column("UNTYPED", null));
      t.setColumnSelection(cs, false);
      return t;
   }

   private static int stored(BoundTableAssembly t) {
      return t.getPreConditionList() == null ? 0 : t.getPreConditionList().getConditionSize();
   }

   private static ColumnRef column(String name, String type) {
      AttributeRef attr = new AttributeRef(null, name);

      if(type != null) {
         attr.setDataType(type);
      }

      return new ColumnRef(attr);
   }

   private static List<ConditionNode> nodes(String field, String type) {
      return List.of(new ConditionNode(
         new ConditionSpec(field, "DATE_IN", List.of("Last month"), false, type), null, 0));
   }

   @Test
   void addFilterRefusesDateInOnAStringColumn() {
      BoundTableAssembly t = table();

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> WorksheetMutationSupport.addFilter(t, "STATE", "DATE_IN", "Last month"));

      assertTrue(ex.getMessage().contains("DATE_IN"), ex.getMessage());
      assertTrue(ex.getMessage().contains("STATE"), ex.getMessage());
      assertTrue(ex.getMessage().contains("string"), ex.getMessage());
      assertTrue(ex.getMessage().contains("Nothing was changed"), ex.getMessage());
      assertEquals(0, stored(t));
   }

   @Test
   void addFilterRefusesDateInOnANumericColumn() {
      BoundTableAssembly t = table();

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> WorksheetMutationSupport.addFilter(t, "QTY", "DATE_IN", "Last month"));

      assertTrue(ex.getMessage().contains("integer"), ex.getMessage());
      assertEquals(0, stored(t));
   }

   @Test
   void setConditionsRefusesDateInOnAStringColumn() {
      BoundTableAssembly t = table();

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> WorksheetMutationSupport.setConditions(t, nodes("STATE", null), false));

      assertTrue(ex.getMessage().contains("DATE_IN"), ex.getMessage());
      assertTrue(ex.getMessage().contains("STATE"), ex.getMessage());
      assertTrue(ex.getMessage().contains("string"), ex.getMessage());
      assertEquals(0, stored(t));
   }

   @Test
   void aDeclaredDateTypeDoesNotBypassTheGuard() {
      BoundTableAssembly t = table();

      // the declared type disagrees with the column's known type, so it is refused before the
      // DATE_IN check is even reached; either way nothing is stored
      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> WorksheetMutationSupport.setConditions(t, nodes("STATE", "date"), false));

      assertTrue(ex.getMessage().contains("STATE"), ex.getMessage());
      assertEquals(0, stored(t));
   }

   @Test
   void dateInIsAcceptedOnADateColumn() {
      BoundTableAssembly t = table();

      WorksheetMutationSupport.addFilter(t, "ORDER_DATE", "DATE_IN", "Last month");
      assertEquals(1, stored(t));

      BoundTableAssembly t2 = table();
      WorksheetMutationSupport.setConditions(t2, nodes("ORDER_DATE", null), false);
      assertEquals(1, stored(t2));
   }

   @Test
   void dateInIsAcceptedOnATimeInstantColumn() {
      BoundTableAssembly t = table();

      WorksheetMutationSupport.addFilter(t, "SEEN_AT", "DATE_IN", "Last month");
      assertEquals(1, stored(t));

      BoundTableAssembly t2 = table();
      WorksheetMutationSupport.setConditions(t2, nodes("SEEN_AT", null), false);
      assertEquals(1, stored(t2));
   }

   @Test
   void dateInIsStillAcceptedOnAColumnWithNoTypeOfItsOwn() {
      BoundTableAssembly t = table();

      WorksheetMutationSupport.addFilter(t, "UNTYPED", "DATE_IN", "Last month");
      assertEquals(1, stored(t));

      BoundTableAssembly t2 = table();
      WorksheetMutationSupport.setConditions(t2, nodes("UNTYPED", null), false);
      assertEquals(1, stored(t2));
   }
}
