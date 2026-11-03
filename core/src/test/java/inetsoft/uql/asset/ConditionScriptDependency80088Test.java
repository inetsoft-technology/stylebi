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
import inetsoft.uql.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.web.composer.model.ws.DependencyType;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #80088, a table read by the JavaScript value of another table's condition wasn't a
 * dependency, so it could be deleted and the condition then failed with "X is not defined".
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ConditionScriptDependency80088Test {
   @Test
   void preConditionScriptTableIsDepended() {
      Worksheet ws = worksheet();
      EmbeddedTableAssembly products = (EmbeddedTableAssembly) ws.getAssembly("PRODUCTS");
      products.setPreConditionList(conditions("CATEGORIES['CATEGORY_ID'][1]"));

      assertDependings(ws, "PRODUCTS");
   }

   @Test
   void mvDeleteConditionScriptTableIsDepended() {
      Worksheet ws = worksheet();
      EmbeddedTableAssembly products = (EmbeddedTableAssembly) ws.getAssembly("PRODUCTS");
      products.setMVDeletePreConditionList(conditions("CATEGORIES['CATEGORY_ID'][1]"));

      assertDependings(ws, "PRODUCTS");
   }

   @Test
   void augmentedDependencyIsAnExpression() {
      Worksheet ws = worksheet();
      EmbeddedTableAssembly products = (EmbeddedTableAssembly) ws.getAssembly("PRODUCTS");
      products.setPreConditionList(conditions("CATEGORIES['CATEGORY_ID'][1]"));

      Map<String, Set<DependencyType>> map = new HashMap<>();
      products.getAugmentedDependeds(map);

      assertEquals(Set.of(DependencyType.EXPRESSION), map.get("CATEGORIES"));
   }

   @Test
   void selfReferenceIsNotACycle() {
      Worksheet ws = worksheet();
      EmbeddedTableAssembly products = (EmbeddedTableAssembly) ws.getAssembly("PRODUCTS");
      products.setPreConditionList(conditions("PRODUCTS['CATEGORY_ID'][1]"));

      assertDoesNotThrow(ws::checkDependencies);
      assertEquals(0, ws.getDependings(ws.getAssembly("PRODUCTS").getAssemblyEntry()).length);
   }

   @Test
   void nonTableIdentifierIsIgnored() {
      Worksheet ws = worksheet();
      EmbeddedTableAssembly products = (EmbeddedTableAssembly) ws.getAssembly("PRODUCTS");
      products.setPreConditionList(conditions("somethingElse + 1"));

      assertEquals(0, ws.getDependings(ws.getAssembly("CATEGORIES").getAssemblyEntry()).length);
   }

   private void assertDependings(Worksheet ws, String expected) {
      AssemblyRef[] arr = ws.getDependings(ws.getAssembly("CATEGORIES").getAssemblyEntry());
      assertEquals(List.of(expected), Arrays.stream(arr).map(r -> r.getEntry().getName()).toList());
   }

   private Worksheet worksheet() {
      Worksheet ws = new Worksheet();
      ws.addAssembly(new EmbeddedTableAssembly(ws, "CATEGORIES"));
      ws.addAssembly(new EmbeddedTableAssembly(ws, "PRODUCTS"));
      return ws;
   }

   private ConditionList conditions(String script) {
      ExpressionValue value = new ExpressionValue();
      value.setType(ExpressionValue.JAVASCRIPT);
      value.setExpression(script);
      AssetCondition cond = new AssetCondition();
      cond.setOperation(XCondition.EQUAL_TO);
      cond.setType(XSchema.INTEGER);
      cond.addValue(value);
      ConditionList list = new ConditionList();
      list.append(new ConditionItem(new AttributeRef(null, "CATEGORY_ID"), cond, 0));
      return list;
   }
}
