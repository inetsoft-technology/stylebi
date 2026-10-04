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
package inetsoft.uql.asset;

import inetsoft.test.*;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.erm.ExpressionRef;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77695, the variables of a sql expression column were read with the SQLIterator, whose
 * errors (a tag left open, a tag name that isn't a number) escaped from the worksheet's
 * dependency check.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class AbstractTableAssemblySQLExpressionTest {
   @ParameterizedTest
   @ValueSource(strings = { "$(v) /*<1>*/", "$(v) /*<note>*/", "$(v) /*<note>*/\n+ 1",
                            "$(v) + 1 /*/ c */ /*<where>*/1/*</where>*/" })
   void variableOfUnreadableSqlIsFound(String exp) {
      TableAssembly table = createTable(exp);

      assertEquals(List.of("v"), Arrays.stream(table.getAllVariables())
         .map(var -> var.getName()).toList());

      Set<AssemblyRef> deps = new HashSet<>();
      table.getDependeds(deps);
      assertTrue(deps.stream().anyMatch(ref -> "V".equals(ref.getEntry().getName())),
                 deps.toString());
   }

   // the variables in a -- comment line are not used, also when the sql can't be read
   @Test
   void variableInCommentLineIsIgnored() {
      TableAssembly table = createTable("/*<1>*/ x\n-- $(v)");

      assertEquals(0, table.getAllVariables().length);
   }

   private TableAssembly createTable(String exp) {
      Worksheet ws = new Worksheet();
      DefaultVariableAssembly variable = new DefaultVariableAssembly(ws, "V");
      variable.setVariable(new AssetVariable("v"));
      ws.addAssembly(variable);

      EmbeddedTableAssembly table = new EmbeddedTableAssembly(ws, "T1");
      ExpressionRef ref = new ExpressionRef(null, "E1");
      ref.setExpression(exp);
      ColumnRef column = new ColumnRef(ref);
      column.setSQL(true);
      ColumnSelection columns = new ColumnSelection();
      columns.addAttribute(column);
      table.setColumnSelection(columns);
      ws.addAssembly(table);
      return table;
   }
}
