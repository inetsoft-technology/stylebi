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
package inetsoft.report.composition.execution;

import inetsoft.test.*;
import inetsoft.uql.asset.AggregateFormula;
import inetsoft.uql.asset.AggregateRef;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.jdbc.SQLHelper;
import inetsoft.uql.jdbc.UniformSQL;
import inetsoft.uql.schema.XSchema;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

/**
 * Bug #78806, a two-column aggregate pushed to SQL falls back to a generic DECIMAL expression
 * (precision loss / overflow on a chart), while the crosstab aggregates them in memory.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class TwoColumnAggregateMergeableTest {
   private PreAssetQuery query() {
      PreAssetQuery query = Mockito.mock(PreAssetQuery.class, Mockito.CALLS_REAL_METHODS);
      SQLHelper helper = Mockito.mock(SQLHelper.class);
      Mockito.when(helper.supportsOperation(anyString())).thenReturn(true);
      Mockito.when(helper.getSQLHelperType()).thenReturn("postgresql");
      Mockito.doReturn(new UniformSQL()).when(query).getUniformSQL();
      Mockito.doReturn(helper).when(query).getSQLHelper(any());
      Mockito.doReturn(true).when(query).isAttributeMergeable(any());
      return query;
   }

   private AggregateRef ref(AggregateFormula formula, boolean two) {
      ColumnRef a = new ColumnRef(new AttributeRef(null, "a"));
      a.setDataType(XSchema.DOUBLE);
      ColumnRef b = new ColumnRef(new AttributeRef(null, "b"));
      b.setDataType(XSchema.DOUBLE);
      return two ? new AggregateRef(a, b, formula) : new AggregateRef(a, formula);
   }

   @Test
   void twoColumnFormulasAreNotMergeable() {
      PreAssetQuery query = query();

      for(AggregateFormula f : new AggregateFormula[] {
         AggregateFormula.CORRELATION, AggregateFormula.COVARIANCE,
         AggregateFormula.WEIGHTED_AVG })
      {
         assertTrue(f.isTwoColumns());
         assertFalse(query.isAggregateMergeable(ref(f, true)), f.getName());
      }
   }

   @Test
   void singleColumnFormulasStayMergeable() {
      PreAssetQuery query = query();

      for(AggregateFormula f : new AggregateFormula[] {
         AggregateFormula.SUM, AggregateFormula.AVG, AggregateFormula.MAX })
      {
         assertTrue(query.isAggregateMergeable(ref(f, false)), f.getName());
      }
   }
}
