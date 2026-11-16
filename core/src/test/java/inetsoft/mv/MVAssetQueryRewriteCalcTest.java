/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.mv;

import inetsoft.test.*;
import inetsoft.uql.asset.AggregateFormula;
import inetsoft.uql.asset.AggregateRef;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.ExpressionRef;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Bug #81202: {@code MVAssetQuery.rewriteCalc} found the aggregates of a calc field in any case
 * but replaced only the canonical spelling, so an aggregate written in another case stayed an
 * aggregate expression inside the MV query.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  LibManagerTestConfiguration.class, PluginsTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class MVAssetQueryRewriteCalcTest {
   private static String rewrite(String expression, AggregateRef... arefs) throws Exception {
      ExpressionRef eref = new ExpressionRef(null, "c1");
      eref.setExpression(expression);
      Method method = MVAssetQuery.class.getDeclaredMethod(
         "rewriteCalc", ExpressionRef.class, AggregateRef[].class);
      method.setAccessible(true);
      method.invoke(null, eref, arefs);
      return eref.getExpression();
   }

   private static AggregateRef agg(String column, AggregateFormula formula) {
      return new AggregateRef(new ColumnRef(new AttributeRef(null, column)), formula);
   }

   @Test
   void aggregateNameInOtherCaseIsRewrittenLikeTheCanonicalOne() throws Exception {
      AggregateRef a = agg("a", AggregateFormula.COUNT_ALL);
      String proper = rewrite("field['Count([a])'] * 1", a);
      assertEquals(proper.indexOf("Count([a])"), -1, proper);
      assertEquals(proper, rewrite("field['count([a])'] * 1", a));
      assertEquals(proper, rewrite("field['COUNT([A])'] * 1", a));
   }

   @Test
   void sharpSAggregateNameWrittenAsSSIsRewritten() throws Exception {
      AggregateRef a = agg("straße", AggregateFormula.COUNT_ALL);
      assertEquals(rewrite("field['Count([straße])'] + 1", a),
                   rewrite("field['Count([STRASSE])'] + 1", a));
   }
}
