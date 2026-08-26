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

import inetsoft.web.binding.model.ColorMapModel;
import inetsoft.web.binding.model.graph.aesthetic.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
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
    * The recorded defect: while {@code useGlobal} is set, the model-to-wrapper conversion reads
    * {@code globalColorMaps} in place of {@code colorMaps}, so a mapped colour is stored and never
    * rendered — the model round-trips perfectly and the chart shows something else. Supplying a
    * mapping must clear the flag.
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

   /**
    * The same defect, hit through the tool's own primary documented example: a plain
    * {@code colors} list with no mapping and no explicit {@code useGlobal}. Both flags default
    * to {@code true} and neither was previously cleared for this shape, so the colours were
    * accepted, stored, and never rendered.
    */
   @Test
   void aPlainColoursListClearsUseGlobalAndShareColorsSoItActuallyRenders() {
      CategoricalColorModel frame = assertInstanceOf(
         CategoricalColorModel.class,
         VisualFrameAliases.create("color", spec("type", "categorical",
            "colors", java.util.List.of("#FF0000", "#00FF00", "#0000FF", "#FFFF00"))));

      assertFalse(frame.isUseGlobal(),
                  "leaving useGlobal set is what made the explicit colours never render");
      assertFalse(frame.isShareColors(),
                  "shareColors independently pulls in another chart's cached frame for the " +
                  "same column even when useGlobal is cleared");
   }

   /**
    * "Assign Fixed Mapping" with "Share Colors" checked is a supported and useful combination —
    * pinning a value's colour across the whole viewsheet. The flag picks which array the pins go
    * in, the way {@code openColorMappingDialog}'s callback does; it does not make them illegal.
    */
   @Test
   void sharedPinsGoInTheViewsheetLevelArray() {
      CategoricalColorModel frame = assertInstanceOf(
         CategoricalColorModel.class,
         VisualFrameAliases.create("color", spec("type", "categorical", "shareColors", true,
                                                 "mapping", Map.of("2022", "#000000"))));

      assertEquals(1, frame.getGlobalColorMaps().length,
                   "the factory reads globalColorMaps while sharing is on");
      assertEquals("2022", frame.getGlobalColorMaps()[0].getOption());
      assertEquals("#000000", frame.getGlobalColorMaps()[0].getColor());
      assertEquals(0, frame.getColorMaps().length,
                   "the array the factory is not reading must stay empty, not hold a stale copy");
   }

   @Test
   void unsharedPinsGoInTheFramesOwnArray() {
      CategoricalColorModel frame = assertInstanceOf(
         CategoricalColorModel.class,
         VisualFrameAliases.create("color", spec("type", "categorical", "shareColors", false,
                                                 "mapping", Map.of("2022", "#000000"))));

      assertEquals(1, frame.getColorMaps().length);
      assertEquals(0, frame.getGlobalColorMaps().length);
   }

   /**
    * The frame carries {@code useGlobal} and {@code shareColors} separately for historical
    * reasons, but the Composer has driven them from one checkbox ever since {@code shareColors}
    * was added, and the dialog's own {@code toggleGlobal()} is wired to nothing. Offering the
    * agent both names would advertise a distinction the product does not have, so the second one
    * is refused by name — and the refusal names the one that works.
    */
   @Test
   void refusesUseGlobalAndNamesShareColorsInstead() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> VisualFrameAliases.create("color", spec("type", "categorical", "useGlobal", true,
                                                       "colors", java.util.List.of("#fff"))));

      assertTrue(thrown.getMessage().contains("useGlobal"), thrown.getMessage());
      assertTrue(thrown.getMessage().contains("shareColors"),
                 "a refusal that does not name the working key just stalls the caller");
   }

   /**
    * One key drives both flags, the way the checkbox does. Either one left set alone renders
    * differently from what was asked for.
    */
   @Test
   void shareColorsDrivesBothOfTheFramesFlags() {
      CategoricalColorModel frame = assertInstanceOf(
         CategoricalColorModel.class,
         VisualFrameAliases.create("color", spec("type", "categorical", "shareColors", true,
                                                 "colors", java.util.List.of("#fff"))));

      assertTrue(frame.isShareColors());
      assertTrue(frame.isUseGlobal(), "the checkbox drives both flags, so the alias must too");
   }

   @Test
   void shareColorsFalseIsTheDefaultSoAnExplicitPaletteRendersAsGiven() {
      CategoricalColorModel frame = assertInstanceOf(
         CategoricalColorModel.class,
         VisualFrameAliases.create("color", spec("type", "categorical", "shareColors", false,
                                                 "colors", java.util.List.of("#fff"))));

      assertFalse(frame.isShareColors());
      assertFalse(frame.isUseGlobal());
   }

   /** Omitted means "leave the checkbox alone"; the resolution needs the frame, so it is not here. */
   @Test
   void anOmittedShareColorsIsReportedAsUnasked() {
      assertNull(VisualFrameAliases.shareColors(spec("type", "categorical")));
      assertEquals(Boolean.TRUE,
                   VisualFrameAliases.shareColors(spec("type", "categorical",
                                                       "shareColors", true)));
      assertEquals(Boolean.FALSE,
                   VisualFrameAliases.shareColors(spec("type", "categorical",
                                                       "shareColors", false)));
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

   /** Share Colors is reported on read, because a true there changes what the chart draws. */
   @Test
   void reportsShareColorsAndTheMappingOnRead() {
      VisualFrameModel frame = VisualFrameAliases.create(
         "color", spec("type", "categorical", "mapping", Map.of("East", "#4e79a7")));

      Map<String, Object> described = VisualFrameAliases.describe(frame);

      assertEquals(false, described.get("shareColors"));
      assertEquals(Map.of("East", "#4E79A7"), described.get("mapping"));
      assertFalse(described.containsKey("useGlobal"),
                  "the two flags agree here, so one key reports both");
   }

   /**
    * With sharing on the pins live in {@code globalColorMaps}. Reading {@code colorMaps} here
    * would report {@code mapping: {}} for a chart that visibly has pinned colours — the same
    * read-the-dead-slot failure the write side is careful to avoid.
    */
   @Test
   void reportsSharedPinsFromTheArrayTheRendererActuallyReads() {
      VisualFrameModel frame = VisualFrameAliases.create(
         "color", spec("type", "categorical", "shareColors", true,
                       "mapping", Map.of("2022", "#000000")));

      Map<String, Object> described = VisualFrameAliases.describe(frame);

      assertEquals(true, described.get("shareColors"));
      assertEquals(Map.of("2022", "#000000"), described.get("mapping"));
   }

   /**
    * Live repro: get_chart_aesthetics read a shared pin back as {@code "000000"} while the
    * {@code colors} beside it were {@code "#000000"}, because
    * {@code VSChartBindingFactory.applyColorsToFrame} refills globalColorMaps from the viewsheet
    * with {@code Tool.colorToHTMLString} (six hex digits, no '#') while everything else uses
    * {@code Tool.toString(Color)}. set_visual_frame refuses a bare six digits, so the read could
    * not be fed back to the write.
    */
   @Test
   void reportsAPinInTheSpellingTheWriteSideAccepts() {
      CategoricalColorModel frame = new CategoricalColorModel();
      frame.setColors(new String[]{ "#4E79A7" });
      frame.setUseGlobal(true);
      frame.setGlobalColorMaps(new ColorMapModel[]{ new ColorMapModel("2022", "000000") });

      Map<String, Object> described = VisualFrameAliases.describe(frame);

      assertEquals(Map.of("2022", "#000000"), described.get("mapping"));
   }

   /** A diagnostic must not become a second failure when something upstream stored junk. */
   @Test
   void reportsAnUnparseablePinVerbatimRatherThanThrowing() {
      CategoricalColorModel frame = new CategoricalColorModel();
      frame.setColors(new String[]{ "#4E79A7" });
      frame.setUseGlobal(false);
      frame.setColorMaps(new ColorMapModel[]{ new ColorMapModel("2022", "chartreuse") });

      Map<String, Object> described = VisualFrameAliases.describe(frame);

      assertEquals(Map.of("2022", "chartreuse"), described.get("mapping"));
   }

   /**
    * A viewsheet saved before the Share Colors feature existed parses back with {@code useGlobal}
    * true and {@code shareColors} false ({@code CategoricalColorFrameWrapper.parseContents}
    * defaults the missing element to false while the field default is true). Reported through the
    * flag the render path consults for the pins, since that is the one whose effect the caller can
    * see on the chart. The pair is left untouched by writes rather than surfaced — the Composer
    * can neither produce nor repair the divergence, so naming it would offer a distinction the
    * agent has no way to act on.
    */
   @Test
   void reportsTheRenderRelevantFlagForALegacyFrame() {
      CategoricalColorModel frame = new CategoricalColorModel();
      frame.setColors(new String[]{ "#4E79A7" });
      frame.setUseGlobal(true);
      frame.setShareColors(false);

      Map<String, Object> described = VisualFrameAliases.describe(frame);

      assertEquals(true, described.get("shareColors"));
      assertFalse(described.containsKey("useGlobal"));
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

   // ── the graduated and per-category frames carry their configuration ───────
   //
   // create() used to build these with `new CategoricalLineModel()` and friends and nothing
   // else, so the only reachable spec was the bare `{type: "categorical"}`. The per-category
   // lists the Composer's own categorical pane edits -- lines, textures, shapes -- and the
   // smallest/largest range its binding-size pane edits had no way in at all.

   @Test
   void buildsACategoricalLineFrameFromTheLineList() {
      CategoricalLineModel frame = assertInstanceOf(
         CategoricalLineModel.class,
         VisualFrameAliases.create("line", spec("type", "categorical", "lines",
                                                List.of(4097, 4113, 4145))));

      assertArrayEquals(new int[]{ 4097, 4113, 4145 }, frame.getLines());
      assertTrue(frame.isChanged(),
                 "CategoricalLineFrameModelFactory ends with setChanged(model.isChanged()), so " +
                 "a model left unchanged stores the lines and reports the frame as untouched");
   }

   @Test
   void buildsACategoricalTextureFrameFromTheTextureList() {
      CategoricalTextureModel frame = assertInstanceOf(
         CategoricalTextureModel.class,
         VisualFrameAliases.create("texture", spec("type", "categorical", "textures",
                                                   List.of(-1, 3, 7))));

      assertArrayEquals(new int[]{ -1, 3, 7 }, frame.getTextures());
      assertTrue(frame.isChanged());
   }

   /**
    * The bare form still means something -- "vary by category, using the defaults" -- and is
    * what binding a field produces before anything is picked, so it must stay buildable.
    */
   @Test
   void stillBuildsABareCategoricalLineOrTextureFrame() {
      assertArrayEquals(new int[0], ((CategoricalLineModel) VisualFrameAliases.create(
         "line", spec("type", "categorical"))).getLines());
      assertArrayEquals(new int[0], ((CategoricalTextureModel) VisualFrameAliases.create(
         "texture", spec("type", "categorical"))).getTextures());
   }

   @Test
   void refusesASingleCodeWhereAListOfCodesIsRead() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> VisualFrameAliases.create("line", spec("type", "categorical", "lines", 4097)));

      assertTrue(thrown.getMessage().contains("list"), thrown.getMessage());
   }

   @Test
   void refusesANonNumericCodeInTheList() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> VisualFrameAliases.create("texture", spec("type", "categorical", "textures",
                                                         List.of(1, "solid"))));

      assertTrue(thrown.getMessage().contains("textures[1]"), thrown.getMessage());
   }

   @Test
   void buildsAGraduatedSizeFrameFromSmallestAndLargest() {
      LinearSizeModel linear = assertInstanceOf(
         LinearSizeModel.class,
         VisualFrameAliases.create("size", spec("type", "linear", "smallest", 4, "largest", 22)));

      assertEquals(4d, linear.getSmallest());
      assertEquals(22d, linear.getLargest());
      assertTrue(linear.isChanged(),
                 "SizeFrameModelFactory.updateVisualFrameWrapper0 returns null for an unchanged " +
                 "model, discarding the whole wrapper before it reaches the chart");

      CategoricalSizeModel categorical = assertInstanceOf(
         CategoricalSizeModel.class,
         VisualFrameAliases.create("size", spec("type", "categorical", "largest", 12)));

      assertEquals(1d, categorical.getSmallest(), "SizeFrameModel's own default");
      assertEquals(12d, categorical.getLargest());
      assertTrue(categorical.isChanged());
   }

   @Test
   void refusesASizeRangeThatIsInverted() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> VisualFrameAliases.create("size", spec("type", "linear",
                                                      "smallest", 20, "largest", 5)));

      assertTrue(thrown.getMessage().contains("largest"), thrown.getMessage());
   }

   @Test
   void marksACategoricalShapeFrameAsChanged() {
      CategoricalShapeModel frame = assertInstanceOf(
         CategoricalShapeModel.class,
         VisualFrameAliases.create("shape", spec("type", "categorical", "shapes",
                                                 List.of("900", "901"))));

      assertTrue(frame.isChanged());
   }

   @Test
   void refusesTheNewKeysOnAChannelThatDoesNotReadThem() {
      assertThrows(IllegalArgumentException.class,
                   () -> VisualFrameAliases.create("texture", spec("type", "categorical",
                                                                   "lines", List.of(4097))));
      assertThrows(IllegalArgumentException.class,
                   () -> VisualFrameAliases.create("color", spec("type", "categorical",
                                                                 "colors", List.of("#000"),
                                                                 "largest", 12)));
      assertThrows(IllegalArgumentException.class,
                   () -> VisualFrameAliases.create("size", spec("type", "static", "size", 8,
                                                                "largest", 12)));
   }

   // ── describe() reports what create() now stores ───────────────────────────
   //
   // These three fell through to the BEHAVIOURAL table, which knows only a type name. That was
   // right while they were built empty; now it would report {type: "categorical"} whatever the
   // frame holds, so a write could not be read back.

   @Test
   void describesTheValuesAPerCategoryFrameHolds() {
      Map<String, Object> line = VisualFrameAliases.describe(
         VisualFrameAliases.create("line", spec("type", "categorical", "lines",
                                                List.of(4097, 4241))));

      assertEquals("categorical", line.get("type"));
      assertEquals(List.of(4097, 4241), line.get("lines"));

      Map<String, Object> texture = VisualFrameAliases.describe(
         VisualFrameAliases.create("texture", spec("type", "categorical", "textures",
                                                   List.of(0, 5))));

      assertEquals("categorical", texture.get("type"));
      assertEquals(List.of(0, 5), texture.get("textures"));
   }

   @Test
   void describesAGraduatedSizeFramesRange() {
      Map<String, Object> linear = VisualFrameAliases.describe(
         VisualFrameAliases.create("size", spec("type", "linear", "smallest", 2, "largest", 18)));

      assertEquals("linear", linear.get("type"));
      assertEquals(2d, linear.get("smallest"));
      assertEquals(18d, linear.get("largest"));

      assertEquals("categorical", VisualFrameAliases.describe(
         VisualFrameAliases.create("size", spec("type", "categorical"))).get("type"));
   }

   @Test
   void aStaticSizeFrameIsStillDescribedAsStatic() {
      Map<String, Object> described = VisualFrameAliases.describe(
         VisualFrameAliases.create("size", spec("type", "static", "size", 8)));

      assertEquals("static", described.get("type"),
                   "the new SizeFrameModel branch must not shadow the StaticSizeModel one");
      assertEquals(8d, described.get("size"));
   }

   /**
    * "Use Column Values as Colors" -- the categorical pane's other checkbox. The flag stands on
    * its own: it replaces what drives the chart rather than adding to the palette, so it satisfies
    * the "needs colours or a mapping" precondition by itself.
    */
   @Test
   void carriesTheColorValueFrameFlag() {
      CategoricalColorModel frame = assertInstanceOf(
         CategoricalColorModel.class,
         VisualFrameAliases.create("color", spec("type", "categorical",
                                                 "colorValueFrame", true)));

      assertTrue(frame.isColorValueFrame());
   }

   @Test
   void carriesTheColorValueFrameFlagAlongsideAPalette() {
      CategoricalColorModel frame = assertInstanceOf(
         CategoricalColorModel.class,
         VisualFrameAliases.create("color", spec("type", "categorical",
                                                 "colors", java.util.List.of("#fff"),
                                                 "colorValueFrame", true)));

      assertTrue(frame.isColorValueFrame());
   }

   @Test
   void reportsTheColorValueFrameFlagOnRead() {
      VisualFrameModel frame = VisualFrameAliases.create(
         "color", spec("type", "categorical", "colorValueFrame", true));

      assertEquals(true, VisualFrameAliases.describe(frame).get("colorValueFrame"));
   }

   /**
    * The precondition is satisfied by switching the checkbox ON, because that is what replaces the
    * thing a palette would otherwise have to supply. An explicit {@code false} says "read the
    * palette", which leaves the frame with no palette to read — the same half-specified shape the
    * check exists to refuse, arriving through a key that looks like it answers it.
    */
   @Test
   void refusesAnExplicitlyFalseColorValueFrameStandingInForAPalette() {
      assertThrows(IllegalArgumentException.class,
                   () -> VisualFrameAliases.create("color", spec("type", "categorical",
                                                                 "colorValueFrame", false)));
   }

   @Test
   void acceptsAnExplicitlyFalseColorValueFrameAlongsideAPalette() {
      CategoricalColorModel frame = assertInstanceOf(
         CategoricalColorModel.class,
         VisualFrameAliases.create("color", spec("type", "categorical",
                                                 "colors", java.util.List.of("#fff"),
                                                 "colorValueFrame", false)));

      assertFalse(frame.isColorValueFrame());
   }
}
