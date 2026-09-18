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
package inetsoft.web.composer.ws.joins;

import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.test.*;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * concatenateTableCross built its Operator with setOperation only, never setLeftTable/
 * setRightTable, so the operator's own fields stayed null even though the (ltable, rtable) map
 * key it was stored under was correct. WorksheetReadService.readJoins and
 * WorksheetEditService.editJoin both read the operator's own fields, not the map key, so a
 * native-Composer-UI cross join reported joins: [] and refused edit_join.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class CrossJoinServiceTest {
   @Test
   void freshTwoTableCrossJoinPopulatesOperatorTableNames() throws Exception {
      Worksheet ws = new Worksheet();
      table(ws, "A", "id");
      table(ws, "B", "id");
      RuntimeWorksheet rws = new RuntimeWorksheet(null, ws, null);

      CrossJoinService.CrossJoinMetaInfo info =
         CrossJoinService.concatenateTableCross("A", "B", rws, false);

      TableAssemblyOperator.Operator op = soleOperator(info.getJoinTable());
      assertEquals("A", op.getLeftTable());
      assertEquals("B", op.getRightTable());
   }

   @Test
   void appendingAThirdTableToAnExistingCrossJoinPopulatesOperatorTableNames() throws Exception {
      Worksheet ws = new Worksheet();
      table(ws, "A", "id");
      table(ws, "B", "id");
      table(ws, "C", "id");
      RuntimeWorksheet rws = new RuntimeWorksheet(null, ws, null);

      CrossJoinService.CrossJoinMetaInfo first =
         CrossJoinService.concatenateTableCross("A", "B", rws, false);
      String joinName = first.getJoinTable().getName();

      CrossJoinService.CrossJoinMetaInfo second =
         CrossJoinService.concatenateTableCross(joinName, "C", rws, false);

      TableAssemblyOperator.Operator op = operatorFor(second.getJoinTable(), "B", "C");
      assertEquals("B", op.getLeftTable());
      assertEquals("C", op.getRightTable());
   }

   private static TableAssemblyOperator.Operator soleOperator(RelationalJoinTableAssembly joinTable) {
      var pairs = joinTable.getOperatorTables();
      assertTrue(pairs.hasMoreElements(), "no operator pair stored");
      String[] pair = (String[]) pairs.nextElement();
      return operatorFor(joinTable, pair[0], pair[1]);
   }

   private static TableAssemblyOperator.Operator operatorFor(
      RelationalJoinTableAssembly joinTable, String ltable, String rtable)
   {
      TableAssemblyOperator top = joinTable.getOperator(ltable, rtable);
      assertNotNull(top, "no operator stored for (" + ltable + "," + rtable + ")");
      assertEquals(1, top.getOperatorCount());
      return top.getOperator(0);
   }

   private static TableAssembly table(Worksheet ws, String name, String... columns) {
      EmbeddedTableAssembly assembly = new EmbeddedTableAssembly(ws, name);
      ColumnSelection cols = new ColumnSelection();

      for(String column : columns) {
         cols.addAttribute(new ColumnRef(new AttributeRef(name, column)));
      }

      assembly.setColumnSelection(cols, false);
      assembly.setColumnSelection(cols, true);
      ws.addAssembly(assembly);
      return assembly;
   }
}
