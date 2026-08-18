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

import inetsoft.web.binding.model.graph.aesthetic.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@Tag("core")
class VisualFrameAliasesTest {
   private static Map<String, Object> spec(Object... pairs) {
      Map<String, Object> spec = new LinkedHashMap<>();

      for(int i = 0; i < pairs.length; i += 2) {
         spec.put((String) pairs[i], pairs[i + 1]);
      }

      return spec;
   }

   // ── the four Phase 1 colour frames ────────────────────────────────────────

   @Test
   void buildsAStaticColorFrame() {
      VisualFrameModel frame = VisualFrameAliases.create("color", spec("type", "static",
                                                                      "color", "#4e79a7"));

      assertInstanceOf(StaticColorModel.class, frame);
      assertEquals("#4E79A7", ((StaticColorModel) frame).getColor());
   }

   @Test
   void buildsACategoricalColorFrameFromAColourList() {
      VisualFrameModel frame = VisualFrameAliases.create(
         "color", spec("type", "categorical", "colors", java.util.List.of("#4e79a7", "f28e2c")));

      CategoricalColorModel categorical = assertInstanceOf(CategoricalColorModel.class, frame);
      assertArrayEquals(new String[]{ "#4E79A7", "#F28E2C" }, categorical.getColors());
   }

   @Test
   void buildsAGradientColorFrameFromAndTo() {
      VisualFrameModel frame = VisualFrameAliases.create(
         "color", spec("type", "gradient", "from", "#eef", "to", "#059"));

      GradientColorModel gradient = assertInstanceOf(GradientColorModel.class, frame);
      assertEquals("#EEEEFF", gradient.getFromColor());
      assertEquals("#005599", gradient.getToColor());
   }

   @Test
   void buildsANamedPaletteFrame() {
      VisualFrameModel frame = VisualFrameAliases.create(
         "color", spec("type", "palette", "palette", "Blues"));

      assertInstanceOf(BluesColorModel.class, frame);
   }

   @Test
   void matchesAPaletteNameCaseInsensitively() {
      assertInstanceOf(RdYlGnColorModel.class,
                       VisualFrameAliases.create("color", spec("type", "palette",
                                                               "palette", "rdylgn")));
   }

   // ── the vocabulary stays honest ───────────────────────────────────────────

   @Test
   void everyRegisteredPaletteIsInstantiable() {
      assertEquals(27, VisualFrameAliases.PALETTES.size(),
                   "the palette registry should carry every named colour ramp");

      for(String name : VisualFrameAliases.PALETTES.keySet()) {
         assertNotNull(VisualFrameAliases.create("color", spec("type", "palette",
                                                               "palette", name)),
                       "palette '" + name + "' does not resolve");
      }
   }

   /**
    * The test that keeps the vocabulary honest as upstream adds palettes. A new
    * {@code ColorFrameModel} subclass that nothing maps is silently unreachable — nothing
    * errors, the option simply never exists.
    */
   @Test
   void everyColorFrameSubclassIsMappedOrExplicitlyExcluded() {
      for(Class<?> subclass : VisualFrameAliases.colorFrameSubclasses()) {
         assertTrue(VisualFrameAliases.isMapped(subclass) ||
                    VisualFrameAliases.isExcluded(subclass),
                    subclass.getSimpleName() + " is neither mapped to an alias nor explicitly " +
                    "excluded. Add it to VisualFrameAliases, or to its exclusion list with a " +
                    "reason.");
      }
   }

   // ── refusals ──────────────────────────────────────────────────────────────

   @Test
   void refusesAnUnknownFrameTypeAndSuggestsNearMatches() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> VisualFrameAliases.create("color", spec("type", "categorial")));

      assertTrue(thrown.getMessage().contains("categorial"));
      assertTrue(thrown.getMessage().contains("categorical"),
                 "an unknown type should point at the near match, not just list everything");
   }

   @Test
   void refusesAnUnknownPaletteListingTheAvailableOnes() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> VisualFrameAliases.create("color", spec("type", "palette", "palette", "Viridis")));

      assertTrue(thrown.getMessage().contains("Viridis"));
      assertTrue(thrown.getMessage().contains("Blues"));
   }

   /**
    * Every channel now accepts a {@code categorical} and a {@code static}, so a spec written for
    * one channel is structurally valid on another and its value keys would simply be ignored —
    * a categorical size frame with no colours, reported as success. The unused-key guard is what
    * catches that.
    */
   @Test
   void refusesAColourSpecOnTheSizeChannelNamingBoth() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> VisualFrameAliases.create("size", spec("type", "categorical",
                                                      "colors", java.util.List.of("#fff"))));

      assertTrue(thrown.getMessage().contains("size"));
      assertTrue(thrown.getMessage().contains("colors"));
   }

   @Test
   void refusesAKeyTheFrameTypeDoesNotRead() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> VisualFrameAliases.create("color", spec("type", "static", "color", "#fff",
                                                       "palette", "Blues")));

      assertTrue(thrown.getMessage().contains("palette"));
   }

   // ── the rest of the taxonomy (2c Phase 2) ─────────────────────────────────

   @Test
   void buildsTheBehaviouralColourFrames() {
      assertInstanceOf(BipolarColorModel.class,
                       VisualFrameAliases.create("color", spec("type", "bipolar")));
      assertInstanceOf(RainbowColorModel.class,
                       VisualFrameAliases.create("color", spec("type", "rainbow")));
      assertInstanceOf(HeatColorModel.class,
                       VisualFrameAliases.create("color", spec("type", "heat")));
      assertInstanceOf(CircularColorModel.class,
                       VisualFrameAliases.create("color", spec("type", "circular")));
   }

   @Test
   void buildsBrightnessAndSaturationFromABaseColour() {
      BrightnessColorModel brightness = assertInstanceOf(
         BrightnessColorModel.class,
         VisualFrameAliases.create("color", spec("type", "brightness", "color", "#4e79a7")));
      assertEquals("#4E79A7", brightness.getColor());
      assertInstanceOf(SaturationColorModel.class,
                       VisualFrameAliases.create("color", spec("type", "saturation",
                                                               "color", "#abc")));
   }

   @Test
   void refusesBrightnessWithNoBaseColour() {
      assertThrows(IllegalArgumentException.class,
                   () -> VisualFrameAliases.create("color", spec("type", "brightness")));
   }

   @Test
   void buildsShapeFrames() {
      StaticShapeModel shape = assertInstanceOf(
         StaticShapeModel.class,
         VisualFrameAliases.create("shape", spec("type", "static", "shape", "circle")));
      assertEquals("circle", shape.getShape());

      CategoricalShapeModel categorical = assertInstanceOf(
         CategoricalShapeModel.class,
         VisualFrameAliases.create("shape", spec("type", "categorical", "shapes",
                                                 java.util.List.of("circle", "square"))));
      assertArrayEquals(new String[]{ "circle", "square" }, categorical.getShapes());

      assertInstanceOf(TriangleShapeModel.class,
                       VisualFrameAliases.create("shape", spec("type", "triangle")));
   }

   @Test
   void buildsSizeFrames() {
      StaticSizeModel size = assertInstanceOf(
         StaticSizeModel.class,
         VisualFrameAliases.create("size", spec("type", "static", "size", 8)));
      assertEquals(8d, size.getSize());
      assertInstanceOf(LinearSizeModel.class,
                       VisualFrameAliases.create("size", spec("type", "linear")));
   }

   @Test
   void buildsLineAndTextureFrames() {
      assertInstanceOf(StaticLineModel.class,
                       VisualFrameAliases.create("line", spec("type", "static", "line", 1)));
      assertInstanceOf(LinearLineModel.class,
                       VisualFrameAliases.create("line", spec("type", "linear")));
      assertInstanceOf(StaticTextureModel.class,
                       VisualFrameAliases.create("texture", spec("type", "static",
                                                                 "texture", 3)));
      assertInstanceOf(GridTextureModel.class,
                       VisualFrameAliases.create("texture", spec("type", "grid")));
   }

   @Test
   void refusesANonNumericSize() {
      assertThrows(IllegalArgumentException.class,
                   () -> VisualFrameAliases.create("size", spec("type", "static",
                                                                "size", "large")));
   }

   @Test
   void refusesAFrameTypeThatBelongsToAnotherChannel() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> VisualFrameAliases.create("size", spec("type", "gradient", "from", "#eee",
                                                      "to", "#059")));

      assertTrue(thrown.getMessage().contains("size"));
   }

   @Test
   void describesEveryFamilyBackInTokens() {
      assertEquals("bipolar",
                   VisualFrameAliases.describe(new BipolarColorModel()).get("type"));
      assertEquals("grid", VisualFrameAliases.describe(new GridTextureModel()).get("type"));
      assertEquals("linear", VisualFrameAliases.describe(new LinearSizeModel()).get("type"));
      assertEquals("triangle",
                   VisualFrameAliases.describe(new TriangleShapeModel()).get("type"));
   }

   @Test
   void listsTypeNamesPerChannel() {
      assertTrue(VisualFrameAliases.typeNames("color").contains("rainbow"));
      assertTrue(VisualFrameAliases.typeNames("texture").contains("left_tilt"));
      assertFalse(VisualFrameAliases.typeNames("size").contains("gradient"),
                  "a channel must not advertise another channel's types");
   }

   @Test
   void refusesAStaticFrameWithNoColour() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> VisualFrameAliases.create("color", spec("type", "static")));

      assertTrue(thrown.getMessage().contains("color"));
   }

   @Test
   void refusesACategoricalFrameWithNoColours() {
      assertThrows(IllegalArgumentException.class,
                   () -> VisualFrameAliases.create("color", spec("type", "categorical",
                                                                 "colors", java.util.List.of())));
   }

   @Test
   void refusesAMissingType() {
      Exception thrown = assertThrows(IllegalArgumentException.class,
                                      () -> VisualFrameAliases.create("color", spec()));

      assertTrue(thrown.getMessage().contains("type"));
   }

   // ── colour normalization ──────────────────────────────────────────────────

   @Test
   void normalizesTheAcceptedColourSpellings() {
      assertEquals("#4E79A7", VisualFrameAliases.normalizeColor("#4e79a7"));
      assertEquals("#4E79A7", VisualFrameAliases.normalizeColor("4e79a7"));
      assertEquals("#4E79A7", VisualFrameAliases.normalizeColor("  #4E79A7  "));
      assertEquals("#AABBCC", VisualFrameAliases.normalizeColor("#abc"));
      assertEquals("#AABBCC", VisualFrameAliases.normalizeColor("abc"));
   }

   @Test
   void rejectsANamedCssColourRatherThanHalfSupportingIt() {
      Exception thrown = assertThrows(IllegalArgumentException.class,
                                      () -> VisualFrameAliases.normalizeColor("rebeccapurple"));

      assertTrue(thrown.getMessage().contains("rebeccapurple"));
      assertTrue(thrown.getMessage().contains("#RRGGBB"));
   }

   @Test
   void rejectsAColourOfTheWrongLength() {
      assertThrows(IllegalArgumentException.class,
                   () -> VisualFrameAliases.normalizeColor("#12345"));
   }

   // ── the read direction ────────────────────────────────────────────────────

   @Test
   void describesAStaticFrameBackInTheAgentVocabulary() {
      StaticColorModel model = new StaticColorModel();
      model.setColor("#4E79A7");

      Map<String, Object> described = VisualFrameAliases.describe(model);

      assertEquals("static", described.get("type"));
      assertEquals("#4E79A7", described.get("color"));
      assertFalse(described.containsKey("clazz"), "an FQCN must never reach the agent");
   }

   @Test
   void describesAPaletteFrameByItsName() {
      Map<String, Object> described = VisualFrameAliases.describe(new BluesColorModel());

      assertEquals("palette", described.get("type"));
      assertEquals("Blues", described.get("palette"));
   }

   @Test
   void describesNullAsNull() {
      assertNull(VisualFrameAliases.describe(null));
   }

   // ── per-value colour mapping, and the useGlobal footgun (2c Phase 3) ──────

   /**
    * The recorded defect: while {@code useGlobal} is set the automatic palette wins, so a mapped
    * colour is stored and never rendered — the model round-trips perfectly and the chart shows
    * something else. Supplying a mapping must clear the flag.
    */
   @Test
   void aColourMappingClearsUseGlobalSoItActuallyRenders() {
      CategoricalColorModel frame = assertInstanceOf(
         CategoricalColorModel.class,
         VisualFrameAliases.create("color", spec("type", "categorical", "mapping",
                                                 Map.of("East", "#4e79a7"))));

      assertFalse(frame.isUseGlobal(),
                  "leaving useGlobal set is what made the mapped colour never render");
      assertFalse(frame.isShareColors());
      assertEquals(1, frame.getColorMaps().length);
      assertEquals("East", frame.getColorMaps()[0].getOption());
      assertEquals("#4E79A7", frame.getColorMaps()[0].getColor());
   }

   @Test
   void refusesUseGlobalTogetherWithAMapping() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> VisualFrameAliases.create("color", spec("type", "categorical", "useGlobal", true,
                                                       "mapping", Map.of("East", "#fff"))));

      assertTrue(thrown.getMessage().contains("never rendered"),
                 "the refusal has to say what would have happened, or it reads as arbitrary");
   }

   @Test
   void acceptsUseGlobalOnItsOwn() {
      CategoricalColorModel frame = assertInstanceOf(
         CategoricalColorModel.class,
         VisualFrameAliases.create("color", spec("type", "categorical", "useGlobal", true,
                                                 "colors", java.util.List.of("#fff"))));

      assertTrue(frame.isUseGlobal());
   }

   @Test
   void acceptsAMappingWithNoExplicitColourList() {
      assertDoesNotThrow(() -> VisualFrameAliases.create(
         "color", spec("type", "categorical", "mapping", Map.of("East", "abc"))));
   }

   @Test
   void refusesAMappingThatIsNotAnObject() {
      assertThrows(IllegalArgumentException.class,
                   () -> VisualFrameAliases.create("color", spec("type", "categorical",
                                                                 "mapping", "East")));
   }

   @Test
   void refusesAnEmptyMappingRatherThanIgnoringIt() {
      assertThrows(IllegalArgumentException.class,
                   () -> VisualFrameAliases.create("color", spec("type", "categorical",
                                                                 "mapping", Map.of())));
   }

   @Test
   void refusesACategoricalFrameWithNeitherColoursNorMapping() {
      assertThrows(IllegalArgumentException.class,
                   () -> VisualFrameAliases.create("color", spec("type", "categorical")));
   }

   /** useGlobal is reported on read, because a true there means any mapping is inert. */
   @Test
   void reportsUseGlobalAndTheMappingOnRead() {
      VisualFrameModel frame = VisualFrameAliases.create(
         "color", spec("type", "categorical", "mapping", Map.of("East", "#4e79a7")));

      Map<String, Object> described = VisualFrameAliases.describe(frame);

      assertEquals(false, described.get("useGlobal"));
      assertEquals(Map.of("East", "#4E79A7"), described.get("mapping"));
   }

   // ── node channels (spec 2c Phase 3) ───────────────────────────────────────

   /**
    * {@code node-color}/{@code node-size} reuse {@code color}/{@code size}'s frame-type
    * taxonomy verbatim — the model holds the identical {@code ColorFrameModel}/
    * {@code SizeFrameModel} types, just on a second property pair.
    */
   @Test
   void buildsANodeColorFrameUsingTheColorTaxonomy() {
      VisualFrameModel frame = VisualFrameAliases.create(
         "node-color", spec("type", "static", "color", "#4e79a7"), true);

      assertInstanceOf(StaticColorModel.class, frame);
      assertEquals("#4E79A7", ((StaticColorModel) frame).getColor());
   }

   @Test
   void buildsANodeSizeFrameUsingTheSizeTaxonomy() {
      VisualFrameModel frame = VisualFrameAliases.create(
         "node-size", spec("type", "static", "size", 5.0), true);

      assertInstanceOf(StaticSizeModel.class, frame);
   }

   @Test
   void refusesANodeChannelWhenTheChartIsNotARelationChart() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> VisualFrameAliases.create("node-color", spec("type", "static", "color", "#000"),
                                         false));

      assertTrue(thrown.getMessage().contains("relation"));
   }

   @Test
   void refusesANodeSizeKeyMeantForColor() {
      assertThrows(
         IllegalArgumentException.class,
         () -> VisualFrameAliases.create("node-size", spec("type", "static", "color", "#000"),
                                         true),
         "'color' is not a key node-size's static frame reads -- it reads 'size'");
   }
}
