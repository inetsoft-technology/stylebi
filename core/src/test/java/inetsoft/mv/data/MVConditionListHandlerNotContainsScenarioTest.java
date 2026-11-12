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
package inetsoft.mv.data;

import inetsoft.mv.MVDef;
import inetsoft.sree.SreeEnv;
import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.erm.ExpressionRef;
import inetsoft.uql.jdbc.SQLHelper;
import inetsoft.uql.jdbc.XBinaryCondition;
import inetsoft.uql.jdbc.XFilterNodeItem;
import inetsoft.util.Tool;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

/**
 * Bug #80152. "not contains 'a'" on a script expression column and on its physical source
 * column, single DB2-like source: the handler's condition node, rewritten by the MV dictionary,
 * keeps the rows the query would keep without the MV.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class MVConditionListHandlerNotContainsScenarioTest {
   private static final String[] NAMES = {
      "MeToo AppServer", "True Action", "banana", "Kiwi", "Plum" };

   @Test
   void notContainsOnScriptExpressionIsCaseInsensitive() {
      ExpressionRef exp = new ExpressionRef(null, "E");
      exp.setExpression("field['PRODUCT_NAME'] + ''");
      // global setting default: case insensitive, as without the MV
      assertEquals(List.of("Kiwi", "Plum"), keptRows(new ColumnRef(exp)));
   }

   @Test
   void notContainsOnPhysicalColumnFollowsTheDatabaseCase() {
      // DB2 LIKE is case sensitive: only lower case 'a' counts
      assertEquals(List.of("Kiwi", "MeToo AppServer", "Plum", "True Action"),
                   keptRows(new ColumnRef(new AttributeRef("PRODUCT_NAME"))));
   }

   private static List<String> keptRows(DataRef ref) {
      String old = SreeEnv.getProperty("string.compare.casesensitive");
      SreeEnv.setProperty("string.compare.casesensitive", "false");
      Tool.invalidateCaseSensitive();

      try {
         BoundTableAssembly table = mock(BoundTableAssembly.class);
         when(table.getSource()).thenReturn("db2");
         SQLHelper helper = mock(SQLHelper.class);
         when(helper.getLikeCaseSensitivity()).thenReturn(Boolean.TRUE);

         MVConditionListHandler handler = new MVConditionListHandler(
            table, mock(MV.class), new VariableTable(), mock(MVDef.class));
         handler.setLikeCaseSensitive(MVConditionListHandler.getLikeCaseSensitivity(table, helper));

         Condition cond = new Condition();
         cond.setOperation(XCondition.CONTAINS);
         cond.addValue("a");
         cond.setNegated(true);
         XFilterNodeItem item = handler.createItem(new ConditionItem(ref, cond, 0),
                                                   new VariableTable());
         XBinaryCondition node = (XBinaryCondition) item.getNode();

         XDimDictionary dict = new XDimDictionary();
         dict.setDataType(String.class);

         for(String name : NAMES) {
            dict.addValue(name);
         }

         dict.complete();
         dict.fixFilter(node);

         // the node is NOT IN (matching indexes): the kept rows are the non-matching values
         List<String> matching = new ArrayList<>();

         for(Object idx : (Object[]) node.getExpression2().getValue()) {
            matching.add(dict.getValue(Integer.parseInt((String) idx)).toString());
         }

         assertEquals(true, node.isIsNot());
         List<String> kept = new ArrayList<>(List.of(NAMES));
         kept.removeAll(matching);
         kept.sort(null);
         return kept;
      }
      finally {
         if(old == null) {
            SreeEnv.remove("string.compare.casesensitive");
         }
         else {
            SreeEnv.setProperty("string.compare.casesensitive", old);
         }

         Tool.invalidateCaseSensitive();
      }
   }
}
