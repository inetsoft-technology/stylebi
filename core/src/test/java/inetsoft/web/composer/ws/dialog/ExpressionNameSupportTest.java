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
package inetsoft.web.composer.ws.dialog;

import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.web.wiz.pairing.WizAgentTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #80462: the name the Create Expression dialog pre-assigns, and the pairing mint check, use
 * the same case-insensitive alias/attribute rule the OK path applies.
 */
@WizAgentTestSupport
class ExpressionNameSupportTest {
   private static ColumnSelection columns(String... names) {
      ColumnSelection cs = new ColumnSelection();

      for(String name : names) {
         cs.addAttribute(new ColumnRef(new AttributeRef(null, name)));
      }

      return cs;
   }

   @Test
   void suggestsExpression1WhenNothingCollides() {
      assertEquals("Expression1", ExpressionNameSupport.suggest(columns("price"), null));
   }

   @Test
   void skipsTakenNamesCaseInsensitively() {
      assertEquals("Expression3", ExpressionNameSupport.suggest(
         columns("price", "expression1", "EXPRESSION2"), null));
   }

   @Test
   void alsoSkipsNamesTakenByTheBoundSource() {
      assertEquals("Expression2", ExpressionNameSupport.suggest(
         columns("price"), columns("Expression1")));
   }

   @Test
   void isTakenMatchesAnAliasInsteadOfTheAttribute() {
      ColumnSelection cs = new ColumnSelection();
      ColumnRef ref = new ColumnRef(new AttributeRef(null, "raw"));
      ref.setAlias("Expression1");
      cs.addAttribute(ref);

      assertTrue(ExpressionNameSupport.isTaken("expression1", cs, null));
      assertFalse(ExpressionNameSupport.isTaken("Expression2", cs, null));
   }
}
