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
package inetsoft.web.wiz.binding;

import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.uql.viewsheet.graph.GraphTypes;
import inetsoft.web.binding.model.ChartBindingModel;
import inetsoft.web.binding.model.graph.AestheticInfo;
import inetsoft.web.binding.model.graph.ChartAggregateRefModel;
import inetsoft.web.binding.model.graph.ChartDimensionRefModel;
import inetsoft.web.binding.model.graph.ChartRefModel;
import inetsoft.web.wiz.binding.model.FieldRef;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The Composer "Discrete" flag on a chart measure (bug #80330, stage 1): accepted on measures,
 * read back, preserved across shelf rewrites, mutually exclusive with secondaryY, and refused by
 * name where the Composer disables the checkbox. Fields are built from the real wire shape (JSON
 * through Jackson) so the {@code n}-keyed record binds exactly as a request does.
 */
@Tag("core")
class ChartDiscreteMeasureTest {
   private final ObjectMapper mapper = new ObjectMapper();

   private FieldRef json(String body) throws Exception {
      return mapper.readValue(body, FieldRef.class);
   }

   private FieldRef measure(String discrete, String secondaryY) throws Exception {
      StringBuilder sb = new StringBuilder(
         "{\"column\":\"Sales\",\"type\":\"measure\",\"aggregate\":\"Sum\"");

      if(discrete != null) {
         sb.append(",\"discrete\":").append(discrete);
      }

      if(secondaryY != null) {
         sb.append(",\"secondaryY\":").append(secondaryY);
      }

      return json(sb.append("}").toString());
   }

   private static ChartAggregateRefModel y(ChartBindingModel model) {
      return (ChartAggregateRefModel) model.getYFields().get(0);
   }

   // ── wire binding and factory ──────────────────────────────────────────────────────────────

   @Test
   void bindsDiscreteFromTheWireAndLeavesItNullWhenAbsent() throws Exception {
      assertEquals(Boolean.TRUE, measure("true", null).discrete());
      assertEquals(Boolean.FALSE, measure("false", null).discrete());
      assertNull(measure(null, null).discrete());
      assertNull(json("{\"column\":\"Region\",\"type\":\"dimension\",\"discrete\":null}")
                    .discrete(), "an echoed null reads as not specified");
   }

   @Test
   void toChartRefSetsDiscreteOnTheMeasure() throws Exception {
      ChartRefModel ref = FieldRefFactory.toChartRef(measure("true", null));

      assertTrue(((ChartAggregateRefModel) ref).isDiscrete());
      assertFalse(((ChartAggregateRefModel) FieldRefFactory.toChartRef(measure("false", null)))
                     .isDiscrete());
      assertFalse(((ChartAggregateRefModel) FieldRefFactory.toChartRef(measure(null, null)))
                     .isDiscrete());
   }

   @Test
   void fromReportsDiscreteForAChartMeasureAndNullForADimension() {
      ChartAggregateRefModel aggregate = new ChartAggregateRefModel();
      aggregate.setColumnValue("Sales");
      aggregate.setFormula("Sum");
      aggregate.setDiscrete(true);

      assertEquals(Boolean.TRUE, FieldRefFactory.from(aggregate).discrete());

      aggregate.setDiscrete(false);
      assertEquals(Boolean.FALSE, FieldRefFactory.from(aggregate).discrete());

      ChartDimensionRefModel dimension = new ChartDimensionRefModel();
      dimension.setColumnValue("Region");
      assertNull(FieldRefFactory.from(dimension).discrete());
   }

   @Test
   void discreteOnADimensionIsRefusedNamingDiscrete() throws Exception {
      FieldRef field = json("{\"column\":\"Region\",\"type\":\"dimension\",\"discrete\":true}");
      IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                                                () -> FieldRefFactory.toChartRef(field));

      assertTrue(e.getMessage().contains("discrete"), e.getMessage());
      assertTrue(e.getMessage().contains("Region"), e.getMessage());
      assertDoesNotThrow(() -> FieldRefFactory.toChartRef(
         json("{\"column\":\"Region\",\"type\":\"dimension\",\"discrete\":false}")));
      assertDoesNotThrow(() -> FieldRefFactory.toChartRef(
         json("{\"column\":\"Region\",\"type\":\"dimension\",\"discrete\":null}")));
   }

   @Test
   void bothDiscreteAndSecondaryYTrueIsRefusedNamingBoth() throws Exception {
      FieldRef field = measure("true", "true");
      IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                                                () -> FieldRefFactory.toChartRef(field));

      assertTrue(e.getMessage().contains("discrete"), e.getMessage());
      assertTrue(e.getMessage().contains("secondaryY"), e.getMessage());
   }

   @Test
   void aFormulaNoneMeasureCannotBeDiscreteJudgedOnTheResolvedFormula() throws Exception {
      FieldRef none = json(
         "{\"column\":\"Sales\",\"type\":\"measure\",\"aggregate\":\"none\",\"discrete\":true}");
      IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                                                () -> FieldRefFactory.toChartRef(none));

      assertTrue(e.getMessage().contains("discrete"), e.getMessage());
   }

   @Test
   void anOmittedAggregateIsJudgedOnTheDefaultedFormulaNotRefused() throws Exception {
      FieldRef omitted = json("{\"column\":\"Sales\",\"type\":\"measure\",\"discrete\":true}");

      assertTrue(((ChartAggregateRefModel) FieldRefFactory.toChartRef(omitted)).isDiscrete());
   }

   // ── shelf writes, preserve-on-omit and exclusivity ────────────────────────────────────────

   @Test
   void setShelfAppliesDiscreteToTheYMeasure() throws Exception {
      ChartBindingModel model = new ChartBindingModel();
      ChartBindingMutator.setShelf(model, "y", List.of(measure("true", null)));

      assertTrue(y(model).isDiscrete());
   }

   @Test
   void anOmittedDiscreteKeepsTheFlagAcrossAShelfRewriteAndAutoBoundMeasuresSurvive()
      throws Exception
   {
      ChartBindingModel model = new ChartBindingModel();
      // An auto-bound discrete measure: the flag is set on the model, not through a FieldRef.
      ChartAggregateRefModel autoBound = new ChartAggregateRefModel();
      autoBound.setColumnValue("Sales");
      autoBound.setFormula("Sum");
      autoBound.setDiscrete(true);
      model.setYFields(List.of(autoBound));

      ChartBindingMutator.setShelf(model, "y", List.of(measure(null, null)));

      assertTrue(y(model).isDiscrete(), "an unrelated rewrite must not revert it to continuous");
   }

   @Test
   void anExplicitFalseClearsDiscrete() throws Exception {
      ChartBindingModel model = new ChartBindingModel();
      ChartBindingMutator.setShelf(model, "y", List.of(measure("true", null)));
      ChartBindingMutator.setShelf(model, "y", List.of(measure("false", null)));

      assertFalse(y(model).isDiscrete());
   }

   @Test
   void turningSecondaryYOnDropsAPreviousDiscreteWhenDiscreteIsOmitted() throws Exception {
      ChartBindingModel model = new ChartBindingModel();
      ChartBindingMutator.setShelf(model, "y", List.of(measure("true", null)));
      ChartBindingMutator.setShelf(model, "y", List.of(measure(null, "true")));

      assertTrue(y(model).isSecondaryY());
      assertFalse(y(model).isDiscrete());
   }

   @Test
   void turningDiscreteOnDropsAPreviousSecondaryYWhenSecondaryYIsOmitted() throws Exception {
      ChartBindingModel model = new ChartBindingModel();
      ChartBindingMutator.setShelf(model, "y", List.of(measure(null, "true")));
      ChartBindingMutator.setShelf(model, "y", List.of(measure("true", null)));

      assertTrue(y(model).isDiscrete());
      assertFalse(y(model).isSecondaryY());
   }

   @Test
   void omittingBothKeepsWhicheverWasSet() throws Exception {
      ChartBindingModel secondary = new ChartBindingModel();
      ChartBindingMutator.setShelf(secondary, "y", List.of(measure(null, "true")));
      ChartBindingMutator.setShelf(secondary, "y", List.of(measure(null, null)));
      assertTrue(y(secondary).isSecondaryY());
      assertFalse(y(secondary).isDiscrete());

      ChartBindingModel discrete = new ChartBindingModel();
      ChartBindingMutator.setShelf(discrete, "y", List.of(measure("true", null)));
      ChartBindingMutator.setShelf(discrete, "y", List.of(measure(null, null)));
      assertTrue(y(discrete).isDiscrete());
      assertFalse(y(discrete).isSecondaryY());
   }

   @Test
   void explicitFalseSecondaryYKeepsAPreviousDiscrete() throws Exception {
      ChartBindingModel model = new ChartBindingModel();
      ChartBindingMutator.setShelf(model, "y", List.of(measure("true", null)));
      ChartBindingMutator.setShelf(model, "y", List.of(measure(null, "false")));

      assertTrue(y(model).isDiscrete());
   }

   @Test
   void aPerMeasureChartTypeRestoresOntoTheMeasureWithTheSameDiscreteFlag() throws Exception {
      ChartBindingModel model = new ChartBindingModel();
      ChartBindingMutator.setShelf(model, "y", List.of(measure("true", null)));
      y(model).setChartType(GraphTypes.CHART_LINE);

      ChartBindingMutator.setShelf(model, "y", List.of(measure(null, null)));

      assertTrue(y(model).isDiscrete());
      assertEquals(GraphTypes.CHART_LINE, y(model).getChartType());
   }

   // ── refusals by chart type, shelf and channel ─────────────────────────────────────────────

   @Test
   void refusesDiscreteOnChartTypesWithoutADiscreteMeasure() throws Exception {
      int[] types = {GraphTypes.CHART_CANDLE, GraphTypes.CHART_STOCK, GraphTypes.CHART_MEKKO,
                     GraphTypes.CHART_BOXPLOT, GraphTypes.CHART_GANTT};

      for(int type : types) {
         ChartBindingModel model = new ChartBindingModel();
         model.setChartType(type);
         FieldRef field = measure("true", null);
         IllegalArgumentException e = assertThrows(
            IllegalArgumentException.class,
            () -> ChartBindingMutator.setShelf(model, "y", List.of(field)),
            "chart type 0x" + Integer.toHexString(type));

         assertTrue(e.getMessage().contains("discrete"), e.getMessage());
         assertTrue(model.getYFields().isEmpty(), "a refusal leaves the shelf untouched");
      }
   }

   @Test
   void acceptsDiscreteFalseOnThoseChartTypes() throws Exception {
      ChartBindingModel model = new ChartBindingModel();
      model.setChartType(GraphTypes.CHART_BOXPLOT);

      assertDoesNotThrow(
         () -> ChartBindingMutator.setShelf(model, "y", List.of(measure("false", null))));
   }

   @Test
   void refusesDiscreteOnTheHighLowOpenCloseShelves() throws Exception {
      for(String shelf : List.of("high", "low", "open", "close")) {
         ChartBindingModel model = new ChartBindingModel();
         FieldRef field = measure("true", null);
         IllegalArgumentException e = assertThrows(
            IllegalArgumentException.class,
            () -> ChartBindingMutator.setSingleShelf(model, shelf, field), shelf);

         assertTrue(e.getMessage().contains("discrete"), e.getMessage());
      }
   }

   @Test
   void acceptsDiscreteOnThePathShelf() throws Exception {
      ChartBindingModel model = new ChartBindingModel();

      assertDoesNotThrow(
         () -> ChartBindingMutator.setSingleShelf(model, "path", measure("true", null)));
   }

   @Test
   void aestheticColorTakesDiscreteAndReadsItBack() throws Exception {
      ChartBindingModel model = new ChartBindingModel();
      ChartAestheticMutator.setField(model, "color", measure("true", null));

      AestheticInfo info = model.getColorField();
      assertTrue(((ChartAggregateRefModel) info.getDataInfo()).isDiscrete());
      assertEquals(Boolean.TRUE, FieldRefFactory.from(info.getDataInfo()).discrete());
   }

   @Test
   void aestheticRebindWithDiscreteOmittedKeepsTheFlag() throws Exception {
      ChartBindingModel model = new ChartBindingModel();
      ChartAestheticMutator.setField(model, "color", measure("true", null));
      ChartAestheticMutator.setField(model, "color", measure(null, null));

      assertTrue(((ChartAggregateRefModel) model.getColorField().getDataInfo()).isDiscrete());
   }

   @Test
   void sizeChannelOnAnIntervalChartRefusesDiscreteButOtherChannelsDoNot() throws Exception {
      ChartBindingModel model = new ChartBindingModel();
      model.setChartType(GraphTypes.CHART_INTERVAL);
      FieldRef field = measure("true", null);

      IllegalArgumentException e = assertThrows(
         IllegalArgumentException.class,
         () -> ChartAestheticMutator.setField(model, "size", field));
      assertTrue(e.getMessage().contains("discrete"), e.getMessage());

      assertDoesNotThrow(() -> ChartAestheticMutator.setField(model, "color", field));
   }

   @Test
   void aestheticDimensionWithDiscreteTrueIsRefused() throws Exception {
      ChartBindingModel model = new ChartBindingModel();
      FieldRef field = json("{\"column\":\"Region\",\"type\":\"dimension\",\"discrete\":true}");

      IllegalArgumentException e = assertThrows(
         IllegalArgumentException.class,
         () -> ChartAestheticMutator.setField(model, "color", field));
      assertTrue(e.getMessage().contains("discrete"), e.getMessage());
   }
}
