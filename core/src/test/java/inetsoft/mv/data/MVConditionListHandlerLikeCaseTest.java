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
import inetsoft.test.*;
import inetsoft.uql.Condition;
import inetsoft.uql.XCondition;
import inetsoft.uql.XNode;
import inetsoft.uql.asset.*;
import inetsoft.uql.jdbc.SQLHelper;
import inetsoft.uql.jdbc.XExpression;
import inetsoft.uql.VariableTable;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #79417. The LIKE case sensitivity of the source database is given to the MV condition
 * only when the table reads a single database whose helper says so.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class MVConditionListHandlerLikeCaseTest {
   @Test
   void singleSourceWithCaseSensitiveHelper() {
      assertEquals(Boolean.TRUE,
                   MVConditionListHandler.getLikeCaseSensitivity(bound("ds"), helper(Boolean.TRUE)));
   }

   @Test
   void singleSourceUnderComposedTable() {
      MirrorTableAssembly mirror = mock(MirrorTableAssembly.class);
      TableAssembly child = bound("ds");
      when(mirror.getTableAssemblies(false)).thenReturn(new TableAssembly[] { child });
      assertEquals(Boolean.TRUE,
                   MVConditionListHandler.getLikeCaseSensitivity(mirror, helper(Boolean.TRUE)));
   }

   @Test
   void severalSourcesGiveNoFlag() {
      MirrorTableAssembly mirror = mock(MirrorTableAssembly.class);
      TableAssembly child1 = bound("ds1");
      TableAssembly child2 = bound("ds2");
      when(mirror.getTableAssemblies(false)).thenReturn(new TableAssembly[] { child1, child2 });
      assertNull(MVConditionListHandler.getLikeCaseSensitivity(mirror, helper(Boolean.TRUE)));
   }

   @Test
   void nonDatabaseLeafGivesNoFlag() {
      MirrorTableAssembly mirror = mock(MirrorTableAssembly.class);
      TableAssembly child1 = bound("ds");
      TableAssembly child2 = mock(EmbeddedTableAssembly.class);
      when(mirror.getTableAssemblies(false)).thenReturn(new TableAssembly[] { child1, child2 });
      assertNull(MVConditionListHandler.getLikeCaseSensitivity(mirror, helper(Boolean.TRUE)));
   }

   @Test
   void nullHelperOrUnknownCapabilityGivesNoFlag() {
      assertNull(MVConditionListHandler.getLikeCaseSensitivity(bound("ds"), null));
      assertNull(MVConditionListHandler.getLikeCaseSensitivity(bound("ds"), helper(null)));
   }

   @Test
   void failingHelperGivesNoFlag() {
      SQLHelper helper = mock(SQLHelper.class);
      when(helper.getLikeCaseSensitivity()).thenThrow(new RuntimeException("boom"));
      assertNull(MVConditionListHandler.getLikeCaseSensitivity(bound("ds"), helper));

      BoundTableAssembly table = mock(BoundTableAssembly.class);
      when(table.getSource()).thenThrow(new RuntimeException("boom"));
      assertNull(MVConditionListHandler.getLikeCaseSensitivity(table, helper(Boolean.TRUE)));
   }

   @Test
   void conditionNodeCarriesTheFlagOnlyWhenSet() {
      MVDef def = mock(MVDef.class);
      BoundTableAssembly table = bound("ds");
      MVConditionListHandler handler =
         new MVConditionListHandler(table, mock(MV.class), new VariableTable(), def);
      Condition cond = new Condition();
      cond.setOperation(XCondition.CONTAINS);
      cond.addValue("a");
      cond.setNegated(true);
      XExpression field = new XExpression("col", XExpression.FIELD);

      XNode node = handler.createCondNode(cond, field, new VariableTable());
      assertNull(node.getAttribute(MVConditionListHandler.LIKE_CASE_SENSITIVE));

      handler.setLikeCaseSensitive(Boolean.TRUE);
      node = handler.createCondNode(cond, field, new VariableTable());
      assertEquals("true", node.getAttribute(MVConditionListHandler.LIKE_CASE_SENSITIVE));
   }

   private static BoundTableAssembly bound(String source) {
      BoundTableAssembly table = mock(BoundTableAssembly.class);
      when(table.getSource()).thenReturn(source);
      return table;
   }

   private static SQLHelper helper(Boolean cs) {
      SQLHelper helper = mock(SQLHelper.class);
      when(helper.getLikeCaseSensitivity()).thenReturn(cs);
      return helper;
   }
}
