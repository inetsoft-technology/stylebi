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
}
