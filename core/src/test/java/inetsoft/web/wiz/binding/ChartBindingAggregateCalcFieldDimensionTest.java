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

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.test.SwapperTestConfiguration;
import inetsoft.uql.asset.SourceInfo;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.viewsheet.CalculateRef;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.web.binding.model.ChartBindingModel;
import inetsoft.web.binding.service.DataRefModelFactoryService;
import inetsoft.web.wiz.binding.model.FieldRef;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #78756 (p1): an aggregate calc field ({@code baseOnDetail} false) put on a chart dimension
 * slot -- x/y/group shelf, path/source/target single shelf, or a color/shape/size/text channel --
 * used to be accepted and then fail at render. {@code FieldRefFactory.toChartRef} is the one
 * funnel all three write paths go through, so it refuses, naming the field, before anything is
 * assigned.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ChartBindingAggregateCalcFieldDimensionTest {
   private static final String TABLE = "Orders";
   private static final SourceInfo ORDERS = new SourceInfo(SourceInfo.ASSET, null, TABLE);

   @Test
   void refusesAnAggregateCalcFieldOnXYAndGroupNamingTheFieldAndWritesNothing() throws Exception {
      RuntimeViewsheet rvs = rvsWithCalc(TABLE, "Total Short", false);

      for(String shelf : new String[] {"x", "y", "group"}) {
         ChartBindingModel model = new ChartBindingModel();

         IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () ->
            ChartBindingMutator.setShelf(model, shelf, List.of(dim("Region"), dim("Total Short")),
                                         rvs, ORDERS, refModelService()));

         assertTrue(e.getMessage().contains("'Total Short' is an aggregate calc field"),
                    e.getMessage());
         assertTrue(e.getMessage().contains("measure"), e.getMessage());
         assertTrue(model.getXFields().isEmpty() && model.getYFields().isEmpty() &&
                    model.getGroupFields().isEmpty(), shelf);
      }
   }

   @Test
   void refusesAnAggregateCalcFieldOnASingleShelf() throws Exception {
      ChartBindingModel model = new ChartBindingModel();

      IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () ->
         ChartBindingMutator.setSingleShelf(model, "path", dim("Total Short"),
                                            rvsWithCalc(TABLE, "Total Short", false), ORDERS,
                                            refModelService()));

      assertTrue(e.getMessage().contains("'Total Short'"), e.getMessage());
      assertNull(model.getPathField());
   }

   @Test
   void refusesAnAggregateCalcFieldOnAnAestheticChannelAndWritesNothing() throws Exception {
      ChartBindingModel model = new ChartBindingModel();

      IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () ->
         ChartAestheticMutator.setField(model, "color", dim("Total Short"), false,
                                        rvsWithCalc(TABLE, "Total Short", false), ORDERS,
                                        refModelService()));

      assertTrue(e.getMessage().contains("'Total Short'"), e.getMessage());
      assertNull(model.getColorField());
   }

   @Test
   void refusesWhenTheChartSourceIsOnlyOnTheModel() {
      ChartBindingModel model = new ChartBindingModel();
      model.setSource(BindingSources.assetSource(TABLE));

      assertThrows(IllegalArgumentException.class, () ->
         ChartBindingMutator.setShelf(model, "x", List.of(dim("Total Short")),
                                      rvsWithCalc(TABLE, "Total Short", false), null,
                                      refModelService()));
   }

   /** Control: a detail calc field is an ordinary column. */
   @Test
   void acceptsADetailCalcFieldAsADimension() throws Exception {
      ChartBindingModel model = new ChartBindingModel();

      ChartBindingMutator.setShelf(model, "x", List.of(dim("Net Sales")),
                                   rvsWithCalc(TABLE, "Net Sales", true), ORDERS,
                                   refModelService());

      assertEquals(1, model.getXFields().size());
   }

   /** Control: an aggregate calc field is exactly what a measure slot wants. */
   @Test
   void acceptsAnAggregateCalcFieldAsAMeasure() throws Exception {
      ChartBindingModel model = new ChartBindingModel();

      ChartBindingMutator.setShelf(model, "y", List.of(measure("Total Short")),
                                   rvsWithCalc(TABLE, "Total Short", false), ORDERS,
                                   refModelService());

      assertEquals(1, model.getYFields().size());
   }

   @Test
   void aSameNamedAggregateCalcFieldOnAnotherTableDoesNotRefuseARealColumn() throws Exception {
      RuntimeViewsheet rvs = rvsWithCalc("Other", "Total Short", false);
      when(rvs.getViewsheet().getCalcFieldSources()).thenReturn(List.of("Other"));
      ChartBindingModel model = new ChartBindingModel();

      ChartBindingMutator.setShelf(model, "x", List.of(dim("Total Short")), rvs, ORDERS,
                                   refModelService());

      assertEquals(1, model.getXFields().size());
   }

   @Test
   void theContextFreeOverloadIsNotGuarded() {
      ChartBindingModel model = new ChartBindingModel();

      assertDoesNotThrow(() -> ChartBindingMutator.setShelf(
         model, "x", List.of(dim("Total Short"))));
   }

   private static FieldRef dim(String column) {
      return new FieldRef(column, "dimension", null, null, null);
   }

   private static FieldRef measure(String column) {
      return new FieldRef(column, "measure", null, null, null);
   }

   private static RuntimeViewsheet rvsWithCalc(String table, String column, boolean baseOnDetail) {
      CalculateRef calc = new CalculateRef(baseOnDetail);
      calc.setDataRef(new AttributeRef(column));
      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getCalcField(table, column)).thenReturn(calc);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      return rvs;
   }

   private static DataRefModelFactoryService refModelService() {
      return mock(DataRefModelFactoryService.class);
   }
}
