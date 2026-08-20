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
    * The viewsheet aliases that legitimately map to a bare, dot-free path because the field really
    * does sit at the top of {@code ViewsheetPropertyDialogModel}. Kept as an explicit list so the
    * nesting invariant stays live for every other alias of that type.
    */
   private static final java.util.Set<String> SHEET_TOP_LEVEL = java.util.Set.of();

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

            // Skip only the aliases that are genuinely top-level accessors on the viewsheet's own
            // dialog model, not every alias of that type. Blanket-skipping the type would let a
            // future "desc -> desc" typo through, which is exactly what this test exists to catch.
            if(type.equals(PropertyAliases.SHEET) && SHEET_TOP_LEVEL.contains(alias.getKey())) {
               continue;
            }

            assertTrue(alias.getValue().contains("."),
                       alias.getKey() + " maps to '" + alias.getValue() + "', which is not a " +
                       "model path — an alias must name a real nested field");
         }
      }
   }

   // ── viewsheet vocabulary (vs-level properties) ────────────────────────────

   @Test
   void exposesTheViewsheetVocabulary() {
      for(String alias : java.util.List.of("alias", "desc", "maxRows", "snapGrid",
                                           "useMetaData", "promptForParams"))
      {
         assertTrue(PropertyAliases.forType(PropertyAliases.SHEET).aliases().containsKey(alias),
                    "viewsheet should expose '" + alias + "'");
      }
   }

   /**
    * {@code vsScriptPane} is deliberately not part of the enumerated vocabulary at all --
    * reading it is still possible through a raw model dump, but it is never offered as a named
    * "property" a caller might reasonably try to set.
    */
   @Test
   void theViewsheetVocabularyNamesNoScriptKey() {
      for(String alias : PropertyAliases.forType(PropertyAliases.SHEET).aliases().keySet()) {
         assertFalse(alias.toLowerCase().contains("script"),
                     "'" + alias + "' should not be part of the enumerated vocabulary");
      }
   }

   @Test
   void aliasIsWritableOnAViewsheet() {
      assertEquals("vsOptionsPane.alias", PropertyAliases.resolveForWrite(PropertyAliases.SHEET, "alias"));
   }

   /**
    * {@code vsScriptPane} carries onInit/onLoad script. Writing it through a properties patch
    * would be a second, ungoverned path to authoring viewsheet script that routes around the
    * (unbuilt) script-kind taxonomy. The refusal names the field and points at the tool that
    * does own writing script.
    */
   @Test
   void refusesToWriteTheScriptPaneAndPointsAtUpdateScript() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> PropertyAliases.resolveForWrite(PropertyAliases.SHEET, "vsScriptPane"));

      assertTrue(thrown.getMessage().contains("vsScriptPane"));
      assertTrue(thrown.getMessage().contains("update_script"));
   }

   /** The raw-path escape hatch must not reach the script pane under a different spelling. */
   @Test
   void refusesTheScriptPaneEvenAsARawDottedPath() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> PropertyAliases.resolveForWrite(PropertyAliases.SHEET, "vsScriptPane.onInit"));

      assertTrue(thrown.getMessage().contains("update_script"));
   }

   /**
    * {@code filtersPane} and {@code localizationPane} are deliberately absent from the vocabulary
    * and still refused on write.
    *
    * <p>They are read-only -- their entries are relational to other assemblies (filter ids keyed to
    * selection assemblies, localization keyed to the component tree), not a simple scalar
    * {@code info.setX}. They were briefly aliased so they could be read by name, which made every
    * list/get carry the whole localization component tree: ~350 lines on a small sheet, growing
    * with assembly count. Reading them is what {@code raw: true} is for.
    *
    * <p>These two tests matter more now, not less: the refusal has to keep working for a key that
    * is no longer in the map, which it does because {@code resolveForWrite} matches on the path.
    */
   @Test
   void refusesToWriteFiltersPane() {
      assertThrows(IllegalArgumentException.class,
                   () -> PropertyAliases.resolveForWrite(PropertyAliases.SHEET, "filtersPane"));
   }

   @Test
   void refusesToWriteLocalizationPane() {
      assertThrows(IllegalArgumentException.class,
                   () -> PropertyAliases.resolveForWrite(PropertyAliases.SHEET, "localizationPane"));
   }

   /** Excluded with the layout/{@code refLayoutName} path -- its own capability, not v1's. */
   @Test
   void refusesToWriteScreensPaneEvenAsARawPath() {
      assertThrows(IllegalArgumentException.class,
                   () -> PropertyAliases.resolveForWrite(PropertyAliases.SHEET, "screensPane.targetScreen"));
   }

   @Test
   void resolvesAnOrdinaryScalarForWrite() {
      assertEquals("vsOptionsPane.maxRows",
                   PropertyAliases.resolveForWrite(PropertyAliases.SHEET, "maxRows"));
      assertEquals("vsOptionsPane.snapGrid",
                   PropertyAliases.resolveForWrite(PropertyAliases.SHEET, "snapGrid"));
   }

   @Test
   void coversTheTypesItClaimsTo() {
      assertTrue(PropertyAliases.covers("gauge"));
      assertTrue(PropertyAliases.covers("Gauge"), "type matching is case-insensitive");
      assertTrue(PropertyAliases.covers("text"));
   }

   /**
    * The chart's line pane — trend lines, grid lines, facet grid. These are the properties a
    * user means by "add a trend line", and they live one pane below the general/advanced panes
    * the first pass covered.
    *
    * <p>Note what is deliberately absent: the word-cloud font scale is a {@code PlotDescriptor}
    * field that the chart property dialog never surfaces, so there is no path to alias. It is not
    * reachable through this engine at all. {@code pointLine} used to be listed here too — see
    * {@link #resolvesThePointLineAliasThreeLevelsDeep()} for why that was wrong.
    */
   @Test
   void coversTheChartLinePaneProperties() {
      for(String alias : java.util.List.of("gridLineVisible", "innerLineVisible",
                                           "trendLineType", "trendLineStyle", "trendLineColor",
                                           "trendLineVisible", "projectForward",
                                           "facetGrid", "facetGridColor", "facetGridVisible"))
      {
         assertTrue(PropertyAliases.forType("chart").aliases().containsKey(alias),
                    "chart should expose '" + alias + "'");
      }
   }

   /**
    * {@code pointLine} was flagged unreachable ("ChartPropertyDialogModel never surfaces this")
    * without checking that {@link PropertyPath} already resolves a dotted path of ANY depth, not
    * just the two segments every other chart alias happens to use. It is a real, three-segment
    * path, and {@link #everyDeclaredAliasResolvesOnItsDialogModel()} already proves it resolves
    * on the model — this pins the exact path so a future refactor of the alias notices if it
    * silently starts pointing somewhere else.
    */
   @Test
   void resolvesThePointLineAliasThreeLevelsDeep() {
      assertEquals("chartAdvancedPaneModel.chartPlotOptionsPaneModel.showPoints",
                   PropertyAliases.resolve("chart", "pointLine"));
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

   /**
    * An uncovered type must still fail loudly, naming what is covered.
    *
    * <p>This used to assert that `image` was uncovered — it was the standing example, because
    * {@code ImagePropertyDialogModel} is an Immutables class with no setters and the path engine
    * could not write it. That is no longer true: {@code PropertyPath} now reads bare Immutables
    * accessors and rebuilds immutable levels through {@code withX}, so image is covered and this
    * test needed a genuinely unsupported type instead.
    */
   @Test
   void refusesAnUncoveredAssemblyTypeListingWhatIsCovered() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class, () -> PropertyAliases.forType("nosuchassembly"));

      assertTrue(thrown.getMessage().contains("nosuchassembly"));
      assertTrue(thrown.getMessage().contains("gauge"));
   }

   /** The Immutables model that motivated the builder write path. */
   @Test
   void coversTheImageAssemblyNowThatImmutablesCanBeWritten() {
      assertTrue(PropertyAliases.covers("image"));
      assertEquals("imageGeneralPaneModel.outputGeneralPaneModel.generalPropPaneModel." +
                   "basicGeneralPaneModel.visible",
                   PropertyAliases.resolve("image", "visible"));
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
