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

   @Test
   void refusesAColourFrameOnTheSizeChannelNamingBoth() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> VisualFrameAliases.create("size", spec("type", "categorical",
                                                      "colors", java.util.List.of("#fff"))));

      assertTrue(thrown.getMessage().contains("size"));
      assertTrue(thrown.getMessage().contains("categorical"));
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
}
