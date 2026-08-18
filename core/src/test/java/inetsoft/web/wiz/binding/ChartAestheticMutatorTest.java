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

import inetsoft.web.binding.model.ChartBindingModel;
import inetsoft.web.binding.model.graph.*;
import inetsoft.web.binding.model.graph.aesthetic.*;
import inetsoft.web.wiz.binding.model.FieldRef;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@Tag("core")
class ChartAestheticMutatorTest {
   private static FieldRef dimension(String column) {
      return new FieldRef(column, "dimension", null, null, null);
   }

   private static FieldRef measure(String column, String aggregate) {
      return new FieldRef(column, "measure", aggregate, null, null);
   }

   private static Map<String, Object> spec(Object... pairs) {
      Map<String, Object> spec = new LinkedHashMap<>();

      for(int i = 0; i < pairs.length; i += 2) {
         spec.put((String) pairs[i], pairs[i + 1]);
      }

      return spec;
   }

   /**
    * The mirror of {@link ChartBindingMutatorTest}'s preservation test. 2b must not disturb
    * the aesthetics; 2c must not disturb the shelves, chart type or options. Two specs write
    * the same model through the same event, so both directions need the assertion.
    */
   private static Map<String, Object> snapshotNonAesthetics(ChartBindingModel model) {
      Map<String, Object> snapshot = new LinkedHashMap<>();
      snapshot.put("xFields", model.getXFields());
      snapshot.put("yFields", model.getYFields());
      snapshot.put("groupFields", model.getGroupFields());
      snapshot.put("chartType", model.getChartType());
      snapshot.put("multiStyles", model.isMultiStyles());
      snapshot.put("separated", model.isSeparated());
      return snapshot;
   }

   // ── field channels ────────────────────────────────────────────────────────

   @Test
   void bindsADimensionToTheColourChannel() {
      ChartBindingModel model = new ChartBindingModel();

      ChartAestheticMutator.setField(model, "color", dimension("Region"));

      AestheticInfo info = model.getColorField();
      assertNotNull(info);
      assertEquals("Region", info.getFullName());
      assertInstanceOf(ChartDimensionRefModel.class, info.getDataInfo());
   }

   @Test
   void bindsAMeasureToTheSizeChannelCarryingItsAggregate() {
      ChartBindingModel model = new ChartBindingModel();

      ChartAestheticMutator.setField(model, "size", measure("Sales", "Sum"));

      AestheticInfo info = model.getSizeField();
      assertNotNull(info);
      ChartAggregateRefModel ref =
         assertInstanceOf(ChartAggregateRefModel.class, info.getDataInfo());
      assertEquals("Sum", ref.getFormula());
   }

   @Test
   void bindsToTheShapeAndTextChannels() {
      ChartBindingModel model = new ChartBindingModel();

      ChartAestheticMutator.setField(model, "shape", dimension("Category"));
      ChartAestheticMutator.setField(model, "text", dimension("Label"));

      assertNotNull(model.getShapeField());
      assertNotNull(model.getTextField());
   }

   @Test
   void clearingAChannelUnbindsIt() {
      ChartBindingModel model = new ChartBindingModel();
      ChartAestheticMutator.setField(model, "color", dimension("Region"));

      ChartAestheticMutator.clearField(model, "color");

      assertNull(model.getColorField());
   }

   @Test
   void matchesAChannelNameCaseInsensitively() {
      ChartBindingModel model = new ChartBindingModel();

      ChartAestheticMutator.setField(model, "  Color  ", dimension("Region"));

      assertNotNull(model.getColorField());
   }

   // ── frame channels ────────────────────────────────────────────────────────

   @Test
   void setsAStaticColourFrame() {
      ChartBindingModel model = new ChartBindingModel();

      ChartAestheticMutator.setFrame(model, "color", spec("type", "static", "color", "#4e79a7"));

      StaticColorModel frame = assertInstanceOf(StaticColorModel.class, model.getColorFrame());
      assertEquals("#4E79A7", frame.getColor());
   }

   @Test
   void setsANamedPaletteFrame() {
      ChartBindingModel model = new ChartBindingModel();

      ChartAestheticMutator.setFrame(model, "color", spec("type", "palette", "palette", "Blues"));

      assertInstanceOf(BluesColorModel.class, model.getColorFrame());
   }

   // ── preservation, both ways ───────────────────────────────────────────────

   @Test
   void aFieldBindingLeavesTheNonAestheticModelUntouched() {
      ChartBindingModel model = new ChartBindingModel();
      ChartBindingMutator.setShelf(model, "x", List.of(dimension("Region")));
      Map<String, Object> before = snapshotNonAesthetics(model);

      ChartAestheticMutator.setField(model, "color", dimension("Category"));

      assertEquals(before, snapshotNonAesthetics(model),
                   "an aesthetic write must not disturb the shelves spec 2b owns");
   }

   @Test
   void aFrameWriteLeavesTheNonAestheticModelUntouched() {
      ChartBindingModel model = new ChartBindingModel();
      ChartBindingMutator.setShelf(model, "y", List.of(measure("Sales", "Sum")));
      Map<String, Object> before = snapshotNonAesthetics(model);

      ChartAestheticMutator.setFrame(model, "color", spec("type", "static", "color", "#000"));

      assertEquals(before, snapshotNonAesthetics(model));
   }

   @Test
   void writingOneAestheticChannelLeavesTheOthersUntouched() {
      ChartBindingModel model = new ChartBindingModel();
      ChartAestheticMutator.setField(model, "shape", dimension("Category"));
      AestheticInfo shapeBefore = model.getShapeField();

      ChartAestheticMutator.setField(model, "color", dimension("Region"));

      assertSame(shapeBefore, model.getShapeField(),
                 "writing the colour channel must not replace the shape channel");
   }

   // ── refusals ──────────────────────────────────────────────────────────────

   @Test
   void refusesAFieldOnAChannelThatOnlyTakesAFrame() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> ChartAestheticMutator.setField(new ChartBindingModel(), "texture",
                                              dimension("Region")));

      assertTrue(thrown.getMessage().contains("texture"));
      assertTrue(thrown.getMessage().contains("set_visual_frame"));
   }

   @Test
   void refusesAnUnknownChannel() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> ChartAestheticMutator.setField(new ChartBindingModel(), "colour",
                                              dimension("Region")));

      assertTrue(thrown.getMessage().contains("colour"));
   }

   @Test
   void refusesAFieldWithNoType() {
      assertThrows(IllegalArgumentException.class,
                   () -> ChartAestheticMutator.setField(
                      new ChartBindingModel(), "color",
                      new FieldRef("Region", null, null, null, null)));
   }

   @Test
   void refusesANullField() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> ChartAestheticMutator.setField(new ChartBindingModel(), "color", null));

      assertTrue(thrown.getMessage().contains("field"));
   }

   // ── the read direction ────────────────────────────────────────────────────

   @Test
   void describesTheBoundChannelsInTheAgentVocabulary() {
      ChartBindingModel model = new ChartBindingModel();
      ChartAestheticMutator.setField(model, "color", dimension("Region"));
      ChartAestheticMutator.setFrame(model, "color", spec("type", "palette", "palette", "Reds"));

      Map<String, Object> described = ChartAestheticMutator.describe(model);
      @SuppressWarnings("unchecked")
      Map<String, Object> color = (Map<String, Object>) described.get("color");

      assertEquals("Region", color.get("field"));
      @SuppressWarnings("unchecked")
      Map<String, Object> frame = (Map<String, Object>) color.get("frame");
      assertEquals("palette", frame.get("type"));
      assertEquals("Reds", frame.get("palette"));
   }

   /**
    * The read path builds its own {@link AestheticInfo} and never sets {@code fullName} —
    * {@code AestheticRefModelFactory.createAestheticInfo} sets only {@code dataInfo} and
    * {@code frame}. So reading the channel name off {@code AestheticInfo.getFullName()} reported
    * {@code field: null} for every channel of every chart, even while the aesthetic was visibly
    * rendering its legend.
    *
    * <p>The test above this one could not catch it: it builds the model with
    * {@code ChartAestheticMutator.setField}, the one place that <em>does</em> set {@code fullName},
    * so it round-trips through our own writer and never exercises a real read. This one builds the
    * model the way the read path does — {@code dataInfo} populated, {@code fullName} absent.
    */
   @Test
   void describesAChannelBuiltTheWayTheReadPathBuildsIt() {
      ChartDimensionRefModel dataInfo = new ChartDimensionRefModel();
      dataInfo.setFullName("Region");

      AestheticInfo info = new AestheticInfo();
      info.setDataInfo(dataInfo);   // exactly what createAestheticInfo does — no setFullName

      ChartBindingModel model = new ChartBindingModel();
      model.setColorField(info);

      @SuppressWarnings("unchecked")
      Map<String, Object> color =
         (Map<String, Object>) ChartAestheticMutator.describe(model).get("color");

      assertEquals("Region", color.get("field"),
                   "a bound channel must report its field, not null");
   }

   /** A bound channel whose name cannot be resolved must not be indistinguishable from an empty one. */
   @Test
   void prefersTheDataInfoNameOverAStaleFullName() {
      ChartDimensionRefModel dataInfo = new ChartDimensionRefModel();
      dataInfo.setFullName("Region");

      AestheticInfo info = new AestheticInfo();
      info.setFullName("Stale");
      info.setDataInfo(dataInfo);

      ChartBindingModel model = new ChartBindingModel();
      model.setColorField(info);

      @SuppressWarnings("unchecked")
      Map<String, Object> color =
         (Map<String, Object>) ChartAestheticMutator.describe(model).get("color");

      assertEquals("Region", color.get("field"));
   }

   @Test
   void describesAnUnboundChannelWithNulls() {
      Map<String, Object> described = ChartAestheticMutator.describe(new ChartBindingModel());
      @SuppressWarnings("unchecked")
      Map<String, Object> shape = (Map<String, Object>) described.get("shape");

      assertNull(shape.get("field"));
      assertNull(shape.get("frame"));
   }

   @Test
   void describesEveryChannelSoAnAgentSeesWhatIsAvailable() {
      Map<String, Object> described = ChartAestheticMutator.describe(new ChartBindingModel());

      for(String channel : AestheticChannels.FIELD_CHANNELS) {
         assertTrue(described.containsKey(channel), "missing channel " + channel);
      }

      for(String channel : AestheticChannels.FRAME_CHANNELS) {
         assertTrue(described.containsKey(channel), "missing channel " + channel);
      }
   }

   // ── node channels (spec 2c Phase 3) ───────────────────────────────────────

   @Test
   void bindsAFieldToTheNodeColorChannelOnARelationChart() {
      ChartBindingModel model = new ChartBindingModel();

      ChartAestheticMutator.setField(model, "node-color", dimension("Region"), true);

      AestheticInfo info = model.getNodeColorField();
      assertNotNull(info);
      assertEquals("Region", info.getFullName());
      // The regular color channel is a separate property -- binding the node channel must not
      // also populate it, or the two would be indistinguishable to a later read.
      assertNull(model.getColorField());
   }

   @Test
   void bindsAFieldToTheNodeSizeChannelOnARelationChart() {
      ChartBindingModel model = new ChartBindingModel();

      ChartAestheticMutator.setField(model, "node-size", measure("Weight", "Sum"), true);

      assertNotNull(model.getNodeSizeField());
      assertNull(model.getSizeField());
   }

   @Test
   void refusesTheNodeColorChannelWhenTheChartIsNotARelationChart() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> ChartAestheticMutator.setField(new ChartBindingModel(), "node-color",
                                              dimension("Region"), false));

      assertTrue(thrown.getMessage().contains("relation"));
   }

   /** The 3-arg overload every existing caller uses defaults to non-relation. */
   @Test
   void theThreeArgOverloadRefusesNodeChannelsByDefault() {
      assertThrows(
         IllegalArgumentException.class,
         () -> ChartAestheticMutator.setField(new ChartBindingModel(), "node-color",
                                              dimension("Region")));
   }

   @Test
   void clearingTheNodeColorChannelUnbindsItWithoutTouchingColor() {
      ChartBindingModel model = new ChartBindingModel();
      ChartAestheticMutator.setField(model, "color", dimension("Region"));
      ChartAestheticMutator.setField(model, "node-color", dimension("Category"), true);

      ChartAestheticMutator.clearField(model, "node-color", true);

      assertNull(model.getNodeColorField());
      assertNotNull(model.getColorField(), "clearing node-color must not clear color");
   }

   @Test
   void setsANodeColorFrameOnARelationChart() {
      ChartBindingModel model = new ChartBindingModel();

      ChartAestheticMutator.setFrame(model, "node-color",
                                     spec("type", "static", "color", "#4e79a7"), true);

      assertNotNull(model.getNodeColorFrame());
      assertNull(model.getColorFrame());
   }

   @Test
   void describesNodeChannelsOnlyWhenAskedToOnARelationChart() {
      ChartBindingModel model = new ChartBindingModel();
      ChartAestheticMutator.setField(model, "node-color", dimension("Region"), true);

      Map<String, Object> notRelation = ChartAestheticMutator.describe(model);
      Map<String, Object> relation = ChartAestheticMutator.describe(model, true);

      assertFalse(notRelation.containsKey("node-color"),
                  "a non-relation read must not advertise a channel this chart cannot render");
      assertTrue(relation.containsKey("node-color"));
      @SuppressWarnings("unchecked")
      Map<String, Object> nodeColor = (Map<String, Object>) relation.get("node-color");
      assertEquals("Region", nodeColor.get("field"));
      assertEquals(true, nodeColor.get("acceptsField"));
      assertEquals(true, nodeColor.get("acceptsFrame"));
   }
}
