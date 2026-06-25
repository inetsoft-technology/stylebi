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
package inetsoft.web.wiz.service;

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.AggregateFormula;
import inetsoft.uql.asset.AggregateInfo;
import inetsoft.uql.asset.AggregateRef;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.GroupRef;
import inetsoft.uql.asset.PhysicalBoundTableAssembly;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Regression for the silent-0-rows aggregate-typing bug (validate_worksheet / GenerateWsService).
 *
 * A table assembly's PUBLIC column selection — the one the chart recommender (autoBinding) reads to
 * classify dimensions vs. measures — is generated when the (pre-aggregation) private selection is
 * set, BEFORE aggregateInfo exists. So an aggregate output (e.g. Count(id)) keeps the base column's
 * "string" type, is misclassified as a string DIMENSION, the chart binds NO measure, and renders 0
 * rows with no error. The fix regenerates the public selection AFTER aggregateInfo is set
 * (GenerateWsService calls table.resetColumnSelection()), so AbstractTableAssembly types each
 * aggregate column from its formula result type and each group from its own.
 *
 * Needs the full Sree bootstrap because AggregateFormula's static singletons read SreeEnv.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class GenerateWsServiceAggregateTypeTest {
   private static ColumnRef stringCol(String name) {
      ColumnRef c = new ColumnRef(new AttributeRef(null, name));
      c.setDataType(XSchema.STRING);
      return c;
   }

   @Test
   void publicSelectionTypesCountAggregateNumericAfterRegeneration() {
      Worksheet ws = new Worksheet();
      PhysicalBoundTableAssembly t = new PhysicalBoundTableAssembly(ws, "accounts");

      ColumnSelection priv = new ColumnSelection();
      priv.addAttribute(stringCol("billing_country"));
      priv.addAttribute(stringCol("id"));
      // Setting the (pre-aggregation) private selection generates the public selection now, while
      // aggregateInfo is still empty — exactly the order GenerateWsService produces.
      t.setColumnSelection(priv);

      AggregateInfo aggInfo = new AggregateInfo();
      aggInfo.addGroup(new GroupRef(t.getColumnSelection(false).getAttribute("billing_country")));
      aggInfo.addAggregate(
         new AggregateRef(t.getColumnSelection(false).getAttribute("id"), AggregateFormula.COUNT_ALL));
      t.setAggregateInfo(aggInfo);

      // The bug: the public selection was built before aggregateInfo, so Count(id) is still "string".
      assertEquals(XSchema.STRING,
         ((ColumnRef) t.getColumnSelection(true).getAttribute("id")).getDataType(),
         "precondition: aggregate column is untyped until the public selection is regenerated");

      // The fix: regenerate the public selection now that aggregateInfo is set.
      t.resetColumnSelection();

      // Count(id) is now numeric → the recommender classifies it as a measure, not a dimension.
      assertEquals(XSchema.DOUBLE,
         ((ColumnRef) t.getColumnSelection(true).getAttribute("id")).getDataType());
      // The group dimension keeps its (string) type.
      assertEquals(XSchema.STRING,
         ((ColumnRef) t.getColumnSelection(true).getAttribute("billing_country")).getDataType());
   }
}
