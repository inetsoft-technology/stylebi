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
package inetsoft.web.wiz.binding;

import inetsoft.report.composition.graph.GraphUtil;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.graph.VSChartAggregateRef;
import inetsoft.web.binding.model.BindingModel;
import inetsoft.web.binding.model.ChartBindingModel;
import inetsoft.web.binding.model.graph.ChartAggregateRefModel;
import inetsoft.web.wiz.binding.model.FieldRef;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bug #78884: a dynamic ($(Pick)) aggregate on a numeric column must not be judged categorical,
 * or GraphUtil.fixVisualFrame swaps a categorical frame onto the color/shape/size channel.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ChartMeasureOriginalDataTypeFrameTest {
   @Test
   void dynamicAggregateOnANumericColumnIsNotCategorical() throws Exception {
      assertFalse(GraphUtil.isCategorical(aggregateFor("Total", "double")));
   }

   @Test
   void dynamicAggregateOnAStringColumnStaysCategorical() throws Exception {
      assertTrue(GraphUtil.isCategorical(aggregateFor("Region", "string")));
   }

   private static VSChartAggregateRef aggregateFor(String column, String type) throws Exception {
      ChartBindingModel model = new ChartBindingModel();
      BindingModel.SourceTable table = new BindingModel.SourceTable();
      table.setName("Orders");
      table.setColumns(List.of(new BindingModel.SourceTableColumn(column, type)));
      model.setTables(List.of(table));
      ChartAggregateRefModel ref = (ChartAggregateRefModel) FieldRefFactory.toChartRef(
         new FieldRef(column, "measure", "$(Pick)", null, null), model, null, null, null);

      VSChartAggregateRef agg = new VSChartAggregateRef();
      agg.setColumnValue(ref.getColumnValue());
      agg.setFormulaValue("$(Pick)");
      agg.setOriginalDataType(ref.getOriginalDataType());
      return agg;
   }
}
