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

import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.web.wiz.pairing.WizAgentTestSupport;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.List;

import static inetsoft.web.wiz.worksheet.WorksheetMutationSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #80069: a worksheet condition's field-typed value is refused with a text match or DATE_IN
 * and against a column of a different type family, as the viewsheet side does (#79570). The MV
 * lists apply the type rule only, since the Composer's MV append dialog offers a Field value for
 * the text matches.
 */
@Tag("core")
@WizAgentTestSupport
class FieldConditionOperand80069Test {
   private static ColumnRef column(String name, String type) {
      ColumnRef col = new ColumnRef(new AttributeRef(null, name));

      if(type != null) {
         col.setDataType(type);
      }

      return col;
   }

   /** NAME/DESC string, PRICE/COST double, QTY integer, UNTYPED has no type of its own. */
   private static BoundTableAssembly table(Worksheet ws) {
      BoundTableAssembly t = new BoundTableAssembly(ws, "P");
      ColumnSelection cs = new ColumnSelection();
      cs.addAttribute(column("NAME", XSchema.STRING));
      cs.addAttribute(column("DESC", XSchema.STRING));
      cs.addAttribute(column("PRICE", XSchema.DOUBLE));
      cs.addAttribute(column("COST", XSchema.DOUBLE));
      cs.addAttribute(column("QTY", XSchema.INTEGER));
      cs.addAttribute(column("UNTYPED", null));
      t.setColumnSelection(cs, false);
      ws.addAssembly(t);
      return t;
   }

   private static List<ConditionNode> cond(String field, String op, String operand) {
      return List.of(new ConditionNode(
         new ConditionSpec(field, op, List.of(), false, null,
            List.of(new ConditionValueSpec("field", operand, null, null, null))), null, 0));
   }

   private static void refused(Executable call, String... fragments) {
      Throwable e = assertThrows(IllegalArgumentException.class, call);

      for(String fragment : fragments) {
         assertTrue(e.getMessage().contains(fragment), e.getMessage() + " / " + fragment);
      }
   }

   private static boolean empty(ConditionListWrapper list) {
      return list == null || list.isEmpty();
   }

   @Test
   void textMatchAndDateInAgainstAFieldRefusedNamingOperatorAndField() {
      BoundTableAssembly t = table(new Worksheet());

      for(String op : new String[] { "STARTING_WITH", "CONTAINS", "LIKE" }) {
         refused(() -> setConditions(t, cond("NAME", op, "DESC"), false),
                 "\"NAME\"", op, "field-typed");
      }

      refused(() -> setConditions(t, cond("QTY", "DATE_IN", "QTY"), false), "DATE_IN");
      assertTrue(empty(t.getPreConditionList()));
   }

   @Test
   void textVersusNumericFieldRefusedNamingOperandAndFamilies() {
      BoundTableAssembly t = table(new Worksheet());
      refused(() -> setConditions(t, cond("NAME", ">", "PRICE"), false),
              "\"NAME\"", "\"PRICE\"", "text vs numeric");
      refused(() -> setConditions(t, cond("PRICE", "=", "NAME"), false), "numeric vs text");
      assertTrue(empty(t.getPreConditionList()));
   }

   @Test
   void sameFamilyFieldAccepted() {
      BoundTableAssembly t = table(new Worksheet());
      setConditions(t, cond("PRICE", ">", "COST"), false);
      setConditions(t, cond("QTY", "=", "PRICE"), false);
      setConditions(t, cond("NAME", "=", "DESC"), false);
      assertFalse(empty(t.getPreConditionList()));
   }

   @Test
   void unknownTypeOnEitherSideAccepted() {
      BoundTableAssembly t = table(new Worksheet());
      setConditions(t, cond("NAME", ">", "UNTYPED"), false);
      setConditions(t, cond("UNTYPED", ">", "PRICE"), false);
      assertFalse(empty(t.getPreConditionList()));
   }

   @Test
   void postConditionsAreGuardedToo() {
      BoundTableAssembly t = table(new Worksheet());
      AggregateInfo ainfo = new AggregateInfo();
      ainfo.addGroup(new GroupRef(column("NAME", XSchema.STRING)));
      ainfo.addAggregate(
         new AggregateRef(column("PRICE", XSchema.DOUBLE), AggregateFormula.SUM));
      t.setAggregateInfo(ainfo);
      String sum = t.getAggregateInfo().getAggregate(0).toView();
      refused(() -> setConditions(t, cond("NAME", "STARTING_WITH", "NAME"), true),
              "STARTING_WITH", "field-typed");
      refused(() -> setConditions(t, cond(sum, ">", "NAME"), true), "numeric vs text");
      assertTrue(empty(t.getPostConditionList()));
   }

   @Test
   void mvListsRefuseTypeMismatchButAllowTextMatchWithAField() {
      BoundTableAssembly t = table(new Worksheet());
      refused(() -> setMVConditions(t, cond("NAME", ">", "PRICE"), null, null, null, null),
              "text vs numeric");
      refused(() -> setMVConditions(t, null, null, cond("NAME", ">", "PRICE"), null, null),
              "text vs numeric");
      assertTrue(empty(t.getMVUpdatePreConditionList()));
      assertTrue(empty(t.getMVDeletePreConditionList()));

      setMVConditions(t, cond("NAME", "STARTING_WITH", "DESC"), null, null, null, null);
      assertFalse(empty(t.getMVUpdatePreConditionList()));
   }
}
