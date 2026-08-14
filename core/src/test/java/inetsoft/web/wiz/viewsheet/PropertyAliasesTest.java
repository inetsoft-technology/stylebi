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
package inetsoft.web.wiz.viewsheet;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@Tag("core")
class PropertyAliasesTest {
   /**
    * <b>The highest-value test in this band.</b> It reflects over every declared alias and
    * asserts the path exists on its dialog model. When the composer renames a pane field, this
    * breaks the build instead of letting the plugin ship an alias that silently resolves to
    * nothing in production.
    */
   @Test
   void everyDeclaredAliasResolvesOnItsDialogModel() {
      for(String type : PropertyAliases.coveredTypes()) {
         PropertyAliases.TypeAliases entry = PropertyAliases.forType(type);

         for(Map.Entry<String, String> alias : entry.aliases().entrySet()) {
            assertDoesNotThrow(
               () -> PropertyPath.typeOf(entry.modelClass(), alias.getValue()),
               "alias '" + alias.getKey() + "' on " + type + " points at '" +
               alias.getValue() + "', which does not exist on " +
               entry.modelClass().getSimpleName());
         }
      }
   }

   /**
    * An alias is only ever a shorter name for a path that already exists. If one ever mapped
    * to something computed, property semantics would live in two places — the drift this
    * design exists to prevent.
    */
   @Test
   void noAliasIsAnEmptyOrSelfReferentialPath() {
      for(String type : PropertyAliases.coveredTypes()) {
         PropertyAliases.TypeAliases entry = PropertyAliases.forType(type);

         for(Map.Entry<String, String> alias : entry.aliases().entrySet()) {
            assertFalse(alias.getValue().isBlank(), alias.getKey() + " maps to nothing");
            assertTrue(alias.getValue().contains("."),
                       alias.getKey() + " maps to '" + alias.getValue() + "', which is not a " +
                       "model path — an alias must name a real nested field");
         }
      }
   }

   @Test
   void coversTheTypesItClaimsTo() {
      assertTrue(PropertyAliases.covers("gauge"));
      assertTrue(PropertyAliases.covers("Gauge"), "type matching is case-insensitive");
      assertTrue(PropertyAliases.covers("text"));
   }

   @Test
   void resolvesAnAliasToItsPath() {
      assertEquals(
         "gaugeGeneralPaneModel.numberRangePaneModel.max",
         PropertyAliases.resolve("gauge", "max"));
   }

   @Test
   void resolvesTheDeepVisibilityPathThatMotivatesTheWholeLayer() {
      assertEquals(
         "gaugeGeneralPaneModel.outputGeneralPaneModel.generalPropPaneModel." +
         "basicGeneralPaneModel.visible",
         PropertyAliases.resolve("gauge", "visible"));
   }

   /** The documented raw escape hatch: a dotted key passes through for PropertyPath to check. */
   @Test
   void passesADottedRawPathThrough() {
      assertEquals("gaugeAdvancedPaneModel.rangePaneModel.rangeGradient",
                   PropertyAliases.resolve("gauge", "gaugeAdvancedPaneModel.rangePaneModel." +
                                                    "rangeGradient"));
   }

   @Test
   void refusesAnUnknownKeyAndSuggestsTheNearMatch() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class, () -> PropertyAliases.resolve("gauge", "maxx"));

      assertTrue(thrown.getMessage().contains("maxx"));
      assertTrue(thrown.getMessage().contains("'max'"), "a near miss should be offered");
   }

   @Test
   void refusesAnUnknownKeyByListingTheKnownOnes() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class, () -> PropertyAliases.resolve("gauge", "wobble"));

      assertTrue(thrown.getMessage().contains("visible"));
   }

   @Test
   void refusesAnUncoveredAssemblyTypeListingWhatIsCovered() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class, () -> PropertyAliases.forType("submit"));

      assertTrue(thrown.getMessage().contains("submit"));
      assertTrue(thrown.getMessage().contains("gauge"));
   }

   /**
    * `min` on a Text is the spec's own example of a property that belongs to another type.
    * It has to fail, not land somewhere harmless.
    */
   @Test
   void refusesAPropertyThatBelongsToADifferentType() {
      assertThrows(IllegalArgumentException.class, () -> PropertyAliases.resolve("text", "min"));
   }
}
