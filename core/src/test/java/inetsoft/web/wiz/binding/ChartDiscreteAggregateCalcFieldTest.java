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
import inetsoft.uql.erm.ExpressionRef;
import inetsoft.uql.viewsheet.CalculateRef;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.web.binding.model.ChartBindingModel;
import inetsoft.web.binding.model.graph.AestheticInfo;
import inetsoft.web.binding.model.graph.ChartAggregateRefModel;
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
 * Bug #80504 (item 3): {@code discrete:true} on an aggregate calc field ({@code baseOnDetail}
 * false) is refused whatever its aggregate -- the Composer disables Discrete for it -- not only
 * when the aggregate resolves to none. Built on a real {@link Viewsheet} holding a real
 * {@link CalculateRef}; a chart that already holds such a measure stays editable.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ChartDiscreteAggregateCalcFieldTest {
   private static final String TABLE = "Orders";
   private static final SourceInfo ORDERS = new SourceInfo(SourceInfo.ASSET, null, TABLE);

   @Test
   void refusesExplicitSumDiscreteOnAnAggregateCalcFieldOnTheShelf() {
      ChartBindingModel model = new ChartBindingModel();

      IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () ->
         ChartBindingMutator.setShelf(
            model, "y", List.of(measure("Sales"), field("AggX", "Sum", true)),
            rvsWithCalc("AggX", false), ORDERS, service()));

      assertTrue(e.getMessage().contains("'AggX'"), e.getMessage());
      assertTrue(e.getMessage().contains("discrete"), e.getMessage());
      assertTrue(e.getMessage().contains("aggregate calc field"), e.getMessage());
      assertTrue(model.getYFields().isEmpty(), "nothing is written");
   }

   @Test
   void refusesOnTheXShelfAndOnAnAestheticChannel() {
      ChartBindingModel xModel = new ChartBindingModel();
      assertThrows(IllegalArgumentException.class, () -> ChartBindingMutator.setShelf(
         xModel, "x", List.of(field("AggX", "Sum", true)), rvsWithCalc("AggX", false),
         ORDERS, service()));

      for(String channel : new String[] {"color", "size", "text"}) {
         ChartBindingModel model = new ChartBindingModel();
         IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () ->
            ChartAestheticMutator.setField(model, channel, field("AggX", "Sum", true), false,
                                           rvsWithCalc("AggX", false), ORDERS, service()));
         assertTrue(e.getMessage().contains("'AggX'") && e.getMessage().contains("discrete"),
                    e.getMessage());
         assertNull(model.getColorField(), channel);
      }
   }

   @Test
   void omittedAggregateKeepsTheExistingNoneMessage() {
      ChartBindingModel model = new ChartBindingModel();

      IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () ->
         ChartBindingMutator.setShelf(model, "y", List.of(field("AggX", null, true)),
                                      rvsWithCalc("AggX", false), ORDERS, service()));

      assertTrue(e.getMessage().contains("resolves to 'none'"), e.getMessage());
   }

   @Test
   void aDetailCalcFieldAndAPlainColumnStillAcceptDiscrete() throws Exception {
      ChartBindingModel detail = new ChartBindingModel();
      ChartBindingMutator.setShelf(detail, "y", List.of(field("NetX", "Sum", true)),
                                   rvsWithCalc("NetX", true), ORDERS, service());
      assertTrue(((ChartAggregateRefModel) detail.getYFields().get(0)).isDiscrete());

      ChartBindingModel plain = new ChartBindingModel();
      ChartBindingMutator.setShelf(plain, "y", List.of(field("Sales", "Sum", true)),
                                   rvsWithCalc("AggX", false), ORDERS, service());
      assertTrue(((ChartAggregateRefModel) plain.getYFields().get(0)).isDiscrete());

      ChartBindingModel color = new ChartBindingModel();
      ChartAestheticMutator.setField(color, "color", field("NetX", "Sum", true), false,
                                     rvsWithCalc("NetX", true), ORDERS, service());
      assertNotNull(color.getColorField());
   }

   @Test
   void aNonDiscreteAggregateCalcFieldIsStillAccepted() throws Exception {
      ChartBindingModel model = new ChartBindingModel();
      ChartBindingMutator.setShelf(model, "y", List.of(field("AggX", "Sum", null)),
                                   rvsWithCalc("AggX", false), ORDERS, service());
      assertFalse(((ChartAggregateRefModel) model.getYFields().get(0)).isDiscrete());
   }

   @Test
   void withoutAViewsheetContextTheGuardIsSkipped() {
      ChartBindingModel model = new ChartBindingModel();
      assertDoesNotThrow(() -> ChartBindingMutator.setShelf(
         model, "y", List.of(field("AggX", "Sum", true)), null, ORDERS, service()));
   }

   /** The risk: a chart already holding a discrete aggregate calc field must stay editable. */
   @Test
   void aPreExistingDiscreteAggregateCalcFieldSurvivesAnUnrelatedShelfEdit() throws Exception {
      ChartBindingModel model = new ChartBindingModel();
      // seeded without a viewsheet, as it could be written before the guard existed
      ChartBindingMutator.setShelf(model, "y", List.of(field("AggX", "Sum", true)), null, ORDERS,
                                   service());
      assertTrue(((ChartAggregateRefModel) model.getYFields().get(0)).isDiscrete());

      RuntimeViewsheet rvs = rvsWithCalc("AggX", false);
      // the read-modify-write resend: AggX comes back with discrete:true, plus an unrelated field
      ChartBindingMutator.setShelf(model, "y",
                                   List.of(field("AggX", "Sum", true), measure("Sales")), rvs,
                                   ORDERS, service());

      assertEquals(2, model.getYFields().size());
      assertTrue(((ChartAggregateRefModel) model.getYFields().get(0)).isDiscrete());

      // ... but turning it on for a measure that was not discrete is still refused
      ChartBindingModel fresh = new ChartBindingModel();
      ChartBindingMutator.setShelf(fresh, "y", List.of(field("AggX", "Sum", null)), rvs, ORDERS,
                                   service());
      assertThrows(IllegalArgumentException.class, () -> ChartBindingMutator.setShelf(
         fresh, "y", List.of(field("AggX", "Sum", true)), rvs, ORDERS, service()));
   }

   @Test
   void aPreExistingDiscreteAestheticAggregateCalcFieldCanBeResent() throws Exception {
      ChartBindingModel model = new ChartBindingModel();
      ChartAestheticMutator.setField(model, "color", field("AggX", "Sum", true), false, null,
                                     ORDERS, service());
      AestheticInfo before = model.getColorField();
      assertNotNull(before);

      ChartAestheticMutator.setField(model, "color", field("AggX", "Sum", true), false,
                                     rvsWithCalc("AggX", false), ORDERS, service());

      assertTrue(((ChartAggregateRefModel) model.getColorField().getDataInfo()).isDiscrete());
   }

   private static FieldRef measure(String column) {
      return field(column, "Sum", null);
   }

   private static FieldRef field(String column, String aggregate, Boolean discrete) {
      return new FieldRef(column, "measure", aggregate, null, null, null, null, null, null, null,
                          null, null, null, null, null, discrete);
   }

   /** A real viewsheet holding a real calc field on {@code TABLE}. */
   private static RuntimeViewsheet rvsWithCalc(String column, boolean baseOnDetail) {
      ExpressionRef eref = new ExpressionRef(null, column);
      eref.setExpression("1");
      CalculateRef calc = new CalculateRef(baseOnDetail);
      calc.setDataRef(eref);
      Viewsheet vs = new Viewsheet();
      vs.addCalcField(TABLE, calc);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      return rvs;
   }

   private static DataRefModelFactoryService service() {
      return mock(DataRefModelFactoryService.class);
   }
}
