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

import inetsoft.uql.viewsheet.internal.PopVSAssemblyInfo.PopLocation;
import inetsoft.web.composer.model.vs.GaugePropertyDialogModel;
import inetsoft.web.composer.model.vs.GeneralPropPaneModel;
import inetsoft.web.composer.model.vs.SizePositionPaneModel;
import inetsoft.web.composer.model.vs.ViewsheetObjectPropertyDialogModel;
import inetsoft.web.composer.model.vs.ImagePropertyDialogModel;
import inetsoft.web.composer.model.vs.TextPropertyDialogModel;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
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
    * Redmine #76739: the Options dialog's "Customize" parameter list (Prompt for Parameters) had
    * no short name, despite being a plain String[]/String[] pair setViewsheetInfo genuinely
    * applies via ViewsheetSettingsService.setViewsheetParameterInfo.
    */
   @Test
   void exposesTheViewsheetParametersAliases() {
      assertEquals("vsOptionsPane.viewsheetParametersDialogModel.enabledParameters",
                   PropertyAliases.resolveForWrite(PropertyAliases.SHEET, "enabledParameters"));
      assertEquals("vsOptionsPane.viewsheetParametersDialogModel.disabledParameters",
                   PropertyAliases.resolveForWrite(PropertyAliases.SHEET, "disabledParameters"));
   }

   /**
    * Redmine #76739: the Options dialog's Data Source "Select"/"Clear" buttons are deliberately
    * NOT in this vocabulary -- they need a resolved AssetEntry, not a scalar JSON leaf, so they
    * are reached through the dedicated set_viewsheet_data_source tool instead (see
    * SheetPropertyService#setDataSource). This is the negative-space companion to
    * exposesTheViewsheetParametersAliases above: proving the omission is deliberate, not simply
    * untested.
    */
   @Test
   void doesNotAliasTheDataSourceField() {
      assertFalse(
         PropertyAliases.forType(PropertyAliases.SHEET).aliases().containsKey("dataSource"),
         "selectDataSourceDialogModel.dataSource should not be a short-name alias -- it needs " +
         "a resolved AssetEntry, which set_viewsheet_properties' patch contract cannot build " +
         "from a JSON leaf value alone");
   }

   /**
    * Redmine #77077: a raw dotted path into the data source subtree reached the viewsheet's LIVE
    * base AssetEntry (getViewsheetInfo hands it to the model by reference) and mutated it in
    * place -- path changed while the cached identifier/properties did not, and setViewsheetInfo's
    * equals-guarded rebind never ran, all reported as ok. Refused for every spelling that
    * PropertyPath would resolve into the subtree: it accepts a capitalized first letter on any
    * segment, so a case-sensitive prefix match would have been bypassable.
    */
   @Test
   void refusesEveryRawWriteIntoTheDataSourceSubtreeInAnyCase() {
      for(String key : java.util.List.of(
         "vsOptionsPane.selectDataSourceDialogModel",
         "vsOptionsPane.selectDataSourceDialogModel.dataSource",
         "vsOptionsPane.selectDataSourceDialogModel.dataSource.path",
         "vsOptionsPane.selectDataSourceDialogModel.dataSource.alias",
         "vsOptionsPane.selectDataSourceDialogModel.dataSource.Path",
         "vsOptionsPane.selectDataSourceDialogModel.dataSource.createdDate.time",
         "vsOptionsPane.selectDataSourceDialogModel.dataSource.user.name",
         "vsOptionsPane.SelectDataSourceDialogModel.DataSource.path",
         "vsOptionsPane.SelectDataSourceDialogModel",
         "vsOptionsPane.selectDataSourceDialogModel.DataSource",
         "vsOptionsPane.selectDataSourceDialogModel.title",
         "VsOptionsPane.selectDataSourceDialogModel.dataSource.path"))
      {
         Exception thrown = assertThrows(
            IllegalArgumentException.class,
            () -> PropertyAliases.resolveForWrite(PropertyAliases.SHEET, key), key);

         assertTrue(thrown.getMessage().contains("set_viewsheet_data_source"),
                    key + ": " + thrown.getMessage());
         assertTrue(thrown.getMessage().contains("selectDataSourceDialogModel"),
                    key + ": " + thrown.getMessage());
      }
   }

   /** The refusal is a prefix on whole segments, not a substring: siblings stay writable. */
   @Test
   void dataSourceRefusalLeavesSiblingOptionsWritable() {
      assertEquals("vsOptionsPane.selectionAssociation",
                   PropertyAliases.resolveForWrite(PropertyAliases.SHEET,
                                                   "vsOptionsPane.selectionAssociation"));
      assertEquals("vsOptionsPane.alias",
                   PropertyAliases.resolveForWrite(PropertyAliases.SHEET, "vsOptionsPane.alias"));
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
    * <p>Note what is deliberately absent: {@code pointLine} and the word-cloud font scale are
    * {@code PlotDescriptor} fields that the chart property dialog never surfaces, so there is no
    * path to alias. They are not reachable through this engine at all.
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

   // ── 'enabled' points at the live field, not the Editable flag ─────────────

   /**
    * {@code basicGeneralPaneModel.enabled} is the Editable flag wearing the wrong name — its own
    * setter is declared {@code setEnabled(boolean editable)} — and nothing in the tree ever writes
    * it. Aliased there, {@code enabled} reported success, wrote a field no one reads, and always
    * read back false. The live switch is {@code GeneralPropPaneModel.enabled}, one level up.
    *
    * <p>The registry's own invariant test cannot catch this: both paths resolve on the model, so a
    * write to either "works". Only the level is wrong, which is why it needs naming explicitly.
    */
   @Test
   void enabledResolvesToTheGeneralPropPaneFieldNotTheEditableFlagBelowIt() {
      assertEquals("textGeneralPaneModel.outputGeneralPaneModel.generalPropPaneModel.enabled",
                   PropertyAliases.resolve("text", "enabled"),
                   "an output assembly nests generalPropPaneModel one level deeper");
      assertEquals("chartGeneralPaneModel.generalPropPaneModel.enabled",
                   PropertyAliases.resolve("chart", "enabled"),
                   "a data assembly holds generalPropPaneModel directly");
   }

   @Test
   void enabledIsNotTheBasicGeneralPaneEnabled() {
      for(String type : java.util.List.of("text", "chart", "gauge", "image", "table")) {
         assertFalse(PropertyAliases.resolve(type, "enabled").endsWith("basicGeneralPaneModel.enabled"),
                     type + " must not alias enabled onto the Editable flag");
      }
   }

   /** {@code visible} is the sibling that genuinely does live on the basic pane. */
   @Test
   void visibleStillResolvesOntoTheBasicGeneralPane() {
      assertTrue(PropertyAliases.resolve("chart", "visible").endsWith("basicGeneralPaneModel.visible"));
   }

   // ── 'shadow' is only real for the outputGeneral() family ──────────────────

   /**
    * shadow is only ever applied back to the real assembly by gauge/text/image's dialog services
    * (VSFloatable.isShadow() is consulted only for OutputVSAssemblyInfo/ShapeVSAssemblyInfo). The
    * dataGeneral() family (chart, table, crosstab, submit, the selection/input types, ...)
    * inherits an unused field from the class hierarchy that no dialog service or renderer ever
    * reads -- aliasing it there let set_assembly_properties(Submit1, {shadow: true}) return a
    * fake {"ok":true} that always read back false.
    */
   @Test
   void shadowIsOnlyAliasedForTheOutputAssemblyTypesThatApplyIt() {
      for(String type : java.util.List.of("gauge", "text", "image")) {
         assertTrue(PropertyAliases.forType(type).aliases().containsKey("shadow"),
                    type + " should expose 'shadow'");
      }

      for(String type : java.util.List.of("submit", "chart", "table", "crosstab", "selectionlist")) {
         assertFalse(PropertyAliases.forType(type).aliases().containsKey("shadow"),
                     type + " should not expose 'shadow' -- it is never applied to the real assembly");
      }
   }

   /** Guards against over-correcting: the 3 genuinely-working cases must not regress. */
   @Test
   void shadowRoundTripsOnEveryOutputAssembly() {
      assertShadowRoundTrips("gauge", new GaugePropertyDialogModel());
      assertShadowRoundTrips("text", new TextPropertyDialogModel());
      assertShadowRoundTrips("image", ImagePropertyDialogModel.builder().build());
   }

   private static void assertShadowRoundTrips(String type, Object model) {
      String path = PropertyAliases.resolve(type, "shadow");
      model = PropertyPath.set(model, path, true);

      assertEquals(true, PropertyPath.get(model, path), type + "'s shadow should round-trip");
   }

   // ── the chart line pane's read-only capability flags ──────────────────────
   //
   // These report whether the chart TYPE offers a control. ChartLinePaneModel recomputes each one
   // from the chart type on every read and updateChartLinePaneModel writes none of them, so a
   // write could not survive even in principle -- it reported success and changed nothing.

   @Test
   void refusesToWriteEveryChartReadOnlyCapabilityFlag() {
      for(String flag : java.util.List.of("gridLineVisible", "innerLineVisible", "trendLineVisible",
                                          "facetGridVisible", "facetGridEnabled",
                                          "projectForwardEnabled", "lineTabVisible"))
      {
         Exception thrown = assertThrows(
            IllegalArgumentException.class,
            () -> PropertyAliases.resolveForWrite("chart", flag),
            flag + " is recomputed on every read, so a write to it cannot survive");

         assertTrue(thrown.getMessage().contains(flag), "name the flag: " + flag);
         assertTrue(thrown.getMessage().contains("read-only"), "say why, for " + flag);
         assertTrue(thrown.getMessage().contains("set_chart_type"),
                    "say what to do instead, for " + flag);
      }
   }

   /** The raw-path escape hatch must not reach a refused flag under a different spelling. */
   @Test
   void refusesAChartReadOnlyFlagEvenAsARawDottedPath() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> PropertyAliases.resolveForWrite("chart", "chartLinePaneModel.gridLineVisible"));

      assertTrue(thrown.getMessage().contains("gridLineVisible"));
   }

   /**
    * The boundary that matters: {@code facetGrid} is the real setting and applies, so only the
    * flag saying whether its control appears is refused. Refusing both would have removed a
    * working property.
    */
   @Test
   void facetGridItselfStaysWritable() {
      assertEquals("chartLinePaneModel.facetGrid",
                   PropertyAliases.resolveForWrite("chart", "facetGrid"));
   }

   @Test
   void theRealLinePaneSettingsStayWritable() {
      for(String alias : java.util.List.of("trendLineType", "trendLineStyle", "trendLineColor",
                                           "projectForward", "facetGridColor"))
      {
         assertEquals("chartLinePaneModel." + alias,
                      PropertyAliases.resolveForWrite("chart", alias),
                      alias + " is a real setting, not a capability flag");
      }
   }

   /**
    * {@code projectForwardEnabled} is exposed readable on purpose: it is the gate that explains
    * why {@code projectForward} reads back 0. Readable and refused on write is the whole point,
    * so both halves are asserted together.
    */
   @Test
   void projectForwardEnabledIsReadableButNotWritable() {
      assertEquals("chartLinePaneModel.projectForwardEnabled",
                   PropertyAliases.resolve("chart", "projectForwardEnabled"));
      assertThrows(IllegalArgumentException.class,
                   () -> PropertyAliases.resolveForWrite("chart", "projectForwardEnabled"));
   }

   /**
    * The refusal is keyed on the assembly type, not on the leaf name alone. The very leaf that is
    * refused on a chart has to pass on a type that has no chart line pane -- otherwise the check
    * would be vetoing a name it knows nothing about, on models where it means something else.
    */
   @Test
   void theChartRefusalDoesNotReachAnotherAssemblyType() {
      assertEquals("chartLinePaneModel.gridLineVisible",
                   PropertyAliases.resolveForWrite(PropertyAliases.SHEET,
                                                   "chartLinePaneModel.gridLineVisible"),
                   "refused on 'chart', but nothing to refuse on a viewsheet");
   }

   // ── data-binding vocabulary (bug 76322) ────────────────────────────────────
   //
   // Text, gauge, image, slider, spinner, checkbox, combobox and radiobutton each bind via a
   // distinct nested path that was previously never aliased -- only reachable through
   // get_assembly_properties(raw: true), buried in a nested dialog model. These assert the
   // vocabulary is now discoverable via list_assembly_properties for every affected type.

   @Test
   void exposesDataOutputBindingForGaugeTextAndImage() {
      for(String type : java.util.List.of("gauge", "text", "image")) {
         for(String alias : java.util.List.of("table", "column", "aggregate")) {
            assertTrue(PropertyAliases.forType(type).aliases().containsKey(alias),
                       type + " should expose '" + alias + "'");
         }

         assertEquals("dataOutputPaneModel.table", PropertyAliases.resolve(type, "table"));
         assertEquals("dataOutputPaneModel.column", PropertyAliases.resolve(type, "column"));
         assertEquals("dataOutputPaneModel.aggregate", PropertyAliases.resolve(type, "aggregate"));
      }
   }

   @Test
   void exposesDataInputBindingForSliderSpinnerAndTextInput() {
      for(String type : java.util.List.of("slider", "spinner", "textinput")) {
         for(String alias : java.util.List.of("table", "columnValue", "rowValue")) {
            assertTrue(PropertyAliases.forType(type).aliases().containsKey(alias),
                       type + " should expose '" + alias + "'");
         }

         assertEquals("dataInputPaneModel.table", PropertyAliases.resolve(type, "table"));
         assertEquals("dataInputPaneModel.columnValue", PropertyAliases.resolve(type, "columnValue"));
         assertEquals("dataInputPaneModel.rowValue", PropertyAliases.resolve(type, "rowValue"));
      }
   }

   @Test
   void exposesListValuesBindingForCheckboxComboboxAndRadioButton() {
      for(String type : java.util.List.of("checkbox", "combobox", "radiobutton")) {
         assertTrue(PropertyAliases.forType(type).aliases().containsKey("table"),
                    type + " should expose 'table'");
         assertTrue(PropertyAliases.forType(type).aliases().containsKey("column"),
                    type + " should expose 'column'");
      }

      assertEquals("checkboxGeneralPaneModel.listValuesPaneModel.comboBoxEditorModel." +
                   "selectionListDialogModel.selectionListEditorModel.table",
                   PropertyAliases.resolve("checkbox", "table"));
      assertEquals("comboboxGeneralPaneModel.listValuesPaneModel.comboBoxEditorModel." +
                   "selectionListDialogModel.selectionListEditorModel.column",
                   PropertyAliases.resolve("combobox", "column"));
      assertEquals("radioButtonGeneralPaneModel.listValuesPaneModel.comboBoxEditorModel." +
                   "selectionListDialogModel.selectionListEditorModel.table",
                   PropertyAliases.resolve("radiobutton", "table"));
   }

   /**
    * {@code embedded}/{@code query} gate whether the static labels/values are ever used at
    * render/bind time (Redmine #76699/VFO-016) -- previously reachable only via
    * {@code get_assembly_properties(raw: true)}.
    */
   @Test
   void exposesEmbeddedAndQueryForCheckboxComboboxAndRadioButton() {
      for(String type : java.util.List.of("checkbox", "combobox", "radiobutton")) {
         assertTrue(PropertyAliases.forType(type).aliases().containsKey("embedded"),
                    type + " should expose 'embedded'");
         assertTrue(PropertyAliases.forType(type).aliases().containsKey("query"),
                    type + " should expose 'query'");
      }

      assertEquals("checkboxGeneralPaneModel.listValuesPaneModel.comboBoxEditorModel.embedded",
                   PropertyAliases.resolve("checkbox", "embedded"));
      assertEquals("comboboxGeneralPaneModel.listValuesPaneModel.comboBoxEditorModel.query",
                   PropertyAliases.resolve("combobox", "query"));
      assertEquals("radioButtonGeneralPaneModel.listValuesPaneModel.comboBoxEditorModel.embedded",
                   PropertyAliases.resolve("radiobutton", "embedded"));
   }

   /**
    * Combobox alone has two distinct fields literally named {@code table}:
    * {@code selectionListEditorModel.table} (the dropdown's own choices query -- what "table"
    * means for every other list-input type too) and a completely separate top-level
    * {@code dataInputPaneModel.table} (the row/column write-back target). The "table" alias must
    * resolve to the former; aliasing the latter under the same name would silently rebind the
    * wrong StyleBI concept.
    */
   @Test
   void comboboxTableAliasIsTheListValuesQueryNotTheWriteBackTarget() {
      String resolved = PropertyAliases.resolve("combobox", "table");

      assertTrue(resolved.contains("selectionListEditorModel.table"),
                 "combobox's 'table' alias should point at the list-values query");
      assertFalse(resolved.equals("dataInputPaneModel.table"),
                  "combobox's 'table' alias must not point at the row/column write-back target");
   }

   // ── parity audit L7: new aliases round-trip ────────────────────────────────

   @Test
   void newChartAliasesResolveAndRoundTrip() {
      assertEquals("chartAdvancedPaneModel.sortOthersLast",
                   PropertyAliases.resolveForWrite("chart", "sortOthersLast"));
      assertEquals("chartAdvancedPaneModel.chartPlotOptionsPaneModel.showValues",
                   PropertyAliases.resolveForWrite("chart", "showValues"));

      inetsoft.web.composer.model.vs.ChartPropertyDialogModel model =
         new inetsoft.web.composer.model.vs.ChartPropertyDialogModel();
      inetsoft.web.composer.model.vs.ChartAdvancedPaneModel advancedPane =
         new inetsoft.web.composer.model.vs.ChartAdvancedPaneModel();
      advancedPane.setChartPlotOptionsPaneModel(
         new inetsoft.web.graph.model.dialog.ChartPlotOptionsPaneModel());
      model.setChartAdvancedPaneModel(advancedPane);
      String path = PropertyAliases.resolve("chart", "showValues");
      Object result = PropertyPath.set(model, path, true);

      assertEquals(true, PropertyPath.get(result, path));
   }

   @Test
   void newTableAndCrosstabAliasesRoundTrip() {
      assertEquals("tableAdvancedPaneModel.insert",
                   PropertyAliases.resolveForWrite("table", "insert"));
      assertEquals("crosstabAdvancedPaneModel.enableAdhoc",
                   PropertyAliases.resolveForWrite("crosstab", "enableAdhoc"));
      assertEquals("tableViewGeneralPaneModel.tableStylePaneModel.tableStyle",
                   PropertyAliases.resolveForWrite("calctable", "tableStyle"));

      inetsoft.web.composer.model.vs.TableViewPropertyDialogModel model =
         new inetsoft.web.composer.model.vs.TableViewPropertyDialogModel();
      String path = PropertyAliases.resolve("table", "insert");
      Object result = PropertyPath.set(model, path, true);

      assertEquals(true, PropertyPath.get(result, path));
   }

   // ── parity audit L7: dead-field refusals ────────────────────────────────────

   @Test
   void refusesTableDeadFields() {
      for(String field : java.util.List.of("shadow", "editable")) {
         assertThrows(
            IllegalArgumentException.class,
            () -> PropertyAliases.resolveForWrite("table",
               "tableViewGeneralPaneModel.generalPropPaneModel.basicGeneralPaneModel." + field),
            field + " has no effect on write for table");
      }

      // shrinkEnabled/formVisible sit directly on tableAdvancedPaneModel, not the basic pane.
      assertThrows(IllegalArgumentException.class,
                   () -> PropertyAliases.resolveForWrite("table",
                      "tableAdvancedPaneModel.shrinkEnabled"));
      assertThrows(IllegalArgumentException.class,
                   () -> PropertyAliases.resolveForWrite("table",
                      "tableAdvancedPaneModel.formVisible"));
      assertThrows(IllegalArgumentException.class,
                   () -> PropertyAliases.resolveForWrite("table",
                      "tableViewGeneralPaneModel.sizePositionPaneModel.container"));

      // cellHeight (bug #76871): genuinely live for SelectionList/SelectionTree/CheckBox/
      // RadioButton, but a plain Table has no such concept -- neither getTableViewPropertyDialogModel
      // nor setTablePropertyModel ever touches it.
      assertThrows(IllegalArgumentException.class,
                   () -> PropertyAliases.resolveForWrite("table",
                      "tableViewGeneralPaneModel.sizePositionPaneModel.cellHeight"));

      // scaleVertical (bug #76883), same shape as cellHeight -- genuinely live for Text, but
      // neither getTableViewPropertyDialogModel nor setTablePropertyModel ever touches it.
      assertThrows(IllegalArgumentException.class,
                   () -> PropertyAliases.resolveForWrite("table",
                      "tableViewGeneralPaneModel.sizePositionPaneModel.scaleVertical"));
   }

   @Test
   void refusesCrosstabDeadFields() {
      assertThrows(IllegalArgumentException.class,
                   () -> PropertyAliases.resolveForWrite("crosstab",
                      "crosstabAdvancedPaneModel.crosstabInfoNull"));
      assertThrows(IllegalArgumentException.class,
                   () -> PropertyAliases.resolveForWrite("crosstab",
                      "crosstabAdvancedPaneModel.sortOthersLastEnabled"));
      assertThrows(IllegalArgumentException.class,
                   () -> PropertyAliases.resolveForWrite("crosstab",
                      "crosstabAdvancedPaneModel.dateComparisonSupport"));
      assertThrows(IllegalArgumentException.class,
                   () -> PropertyAliases.resolveForWrite("crosstab",
                      "tableViewGeneralPaneModel.sizePositionPaneModel.container"));

      // cellHeight (bug #76871), same shape as table's -- Crosstab has no such concept either.
      assertThrows(IllegalArgumentException.class,
                   () -> PropertyAliases.resolveForWrite("crosstab",
                      "tableViewGeneralPaneModel.sizePositionPaneModel.cellHeight"));

      // scaleVertical (bug #76883), same shape as table's -- Crosstab has no such concept either.
      assertThrows(IllegalArgumentException.class,
                   () -> PropertyAliases.resolveForWrite("crosstab",
                      "tableViewGeneralPaneModel.sizePositionPaneModel.scaleVertical"));
   }

   @Test
   void refusesCalctableDeadFields() {
      // cellHeight (bug #76895), same shape as table/crosstab -- CalcTable has no such concept
      // either: neither getCalcTablePropertyDialogModel nor setCalcTablePropertyModel ever
      // touches it.
      assertThrows(IllegalArgumentException.class,
                   () -> PropertyAliases.resolveForWrite("calctable",
                      "tableViewGeneralPaneModel.sizePositionPaneModel.cellHeight"));

      // scaleVertical (bug #76895), same shape as cellHeight -- CalcTable has no such concept
      // either.
      assertThrows(IllegalArgumentException.class,
                   () -> PropertyAliases.resolveForWrite("calctable",
                      "tableViewGeneralPaneModel.sizePositionPaneModel.scaleVertical"));
   }

   /**
    * {@code refresh} is real only for the seven types that read {@code basicGeneralPaneModel}'s
    * {@code isRefresh()} back on write (the six {@code VSInputService}-routed input types, plus
    * submit via {@code SubmitPropertyDialogService}) -- dead for every other type that reaches
    * the shared {@code basicGeneral()} helper through {@code outputGeneral()}/{@code
    * dataGeneral()}/the direct {@code groupContainer()} call. Bug #77028: before this fix, only
    * four of these fourteen dead types were actually refused; the other ten (chart, gauge, image,
    * selectiontree, timeslider, calendar, tab, calctable, groupcontainer, selectioncontainer)
    * reported {@code ok:true} and silently changed nothing. Independently confirmed by the native
    * Composer UI itself: {@code basic-general-pane.component.html}'s Refresh checkbox is gated on
    * {@code BasicGeneralPaneModel.showRefreshCheckbox}, which none of these fourteen types' own
    * model classes ever set {@code true}.
    */
   @Test
   void refusesRefreshOnlyOnTheTypesWhereItIsDead() {
      for(String type : java.util.List.of("table", "crosstab", "text", "selectionlist", "chart",
                                          "gauge", "image", "selectiontree", "timeslider",
                                          "calendar", "tab", "calctable", "groupcontainer",
                                          "selectioncontainer"))
      {
         assertTrue(PropertyAliases.forType(type).aliases().containsKey("refresh"),
                    type + " should still list 'refresh' as readable");
         assertThrows(IllegalArgumentException.class,
                      () -> PropertyAliases.resolveForWrite(type, "refresh"),
                      "refresh has no effect on write for " + type);
      }

      for(String liveType : java.util.List.of("checkbox", "combobox", "radiobutton", "slider",
                                              "spinner", "textinput", "submit"))
      {
         assertDoesNotThrow(() -> PropertyAliases.resolveForWrite(liveType, "refresh"),
                            "refresh is real for " + liveType + " and must stay writable");
      }
   }

   /**
    * Bug #77028: {@code enabled} on groupcontainer resolves to
    * {@code groupContainerGeneralPane.generalPropPane.enabled} (the live
    * {@code GeneralPropPaneModel.enabled} one level up from {@code basicGeneralPaneModel} --
    * genuinely applied for most {@code dataGeneral()}/{@code outputGeneral()} types) but
    * {@code GroupContainerPropertyDialogService.setGroupContainerPropertyDialogModel} never reads
    * it back, and its own {@code getGroupContainerPropertyDialogModel} explicitly calls
    * {@code generalPropPaneModel.setShowEnabledGroup(false)}, so the native Composer UI never
    * even renders the Enabled checkbox for this type. Before this fix, a write reported
    * {@code ok:true} and silently changed nothing.
    */
   @Test
   void refusesGroupContainerEnabled() {
      assertTrue(PropertyAliases.forType("groupcontainer").aliases().containsKey("enabled"),
                 "groupcontainer should still list 'enabled' as readable");
      assertThrows(IllegalArgumentException.class,
                   () -> PropertyAliases.resolveForWrite("groupcontainer", "enabled"),
                   "'enabled' has no effect on write for groupcontainer");

      // Confirm the same field one level up (generalPropPaneModel.enabled) stays writable for a
      // sibling type reached through the same dataGeneral()/basicGeneral() shape, so this
      // refusal is scoped to groupcontainer specifically rather than accidentally widened.
      assertDoesNotThrow(() -> PropertyAliases.resolveForWrite("chart", "enabled"),
                         "'enabled' must stay writable on chart");
   }

   /**
    * Bug #78901: a group container's size is always the bounding box of its members.
    * {@code setGroupContainerPropertyDialogModel} reads only left/top of its size pane, and the
    * layout pass re-derives the size, so a width/height write reported success and changed
    * nothing. Both the alias and the raw path are refused; left/top (which move the group) and a
    * rectangle's width stay writable.
    */
   @Test
   void refusesGroupContainerWidthAndHeight() {
      for(String field : List.of("width", "height")) {
         assertTrue(PropertyAliases.forType("groupcontainer").aliases().containsKey(field),
                    "groupcontainer should still list '" + field + "' as readable");

         for(String key : List.of(field, sizePositionPath("groupcontainer", field))) {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
               () -> PropertyAliases.resolveForWrite("groupcontainer", key), key);
            assertTrue(e.getMessage().contains("is read-only on groupcontainer") &&
                       e.getMessage().contains("members"), e.getMessage());
         }

         assertDoesNotThrow(() -> PropertyAliases.resolveForWrite("rectangle", field));
      }

      assertDoesNotThrow(() -> PropertyAliases.resolveForWrite("groupcontainer", "left"));
      assertDoesNotThrow(() -> PropertyAliases.resolveForWrite("groupcontainer", "top"));
   }

   @Test
   void refusesTheNewChartDerivedFlags() {
      for(String flag : java.util.List.of("adhocVisible", "glossyEffectSupported",
                                          "sortOthersLastEnabled", "rankPerGroupLabel"))
      {
         assertThrows(IllegalArgumentException.class,
                      () -> PropertyAliases.resolveForWrite("chart",
                         "chartAdvancedPaneModel." + flag),
                      flag + " should be refused if reachable as a raw path");
      }

      for(String flag : java.util.List.of("showValuesVisible", "hasXDimension", "wordCloud")) {
         assertThrows(IllegalArgumentException.class,
                      () -> PropertyAliases.resolveForWrite("chart",
                         "chartAdvancedPaneModel.chartPlotOptionsPaneModel." + flag),
                      flag + " should be refused if reachable as a raw path");
      }

      // rankPerGroupLabel is readable, like projectForwardEnabled.
      assertEquals("chartAdvancedPaneModel.rankPerGroupLabel",
                   PropertyAliases.resolve("chart", "rankPerGroupLabel"));
      assertThrows(IllegalArgumentException.class,
                   () -> PropertyAliases.resolveForWrite("chart", "rankPerGroupLabel"));
   }

   /**
    * Regression: {@code SizePositionPaneModel.isLocked()} has zero consumers anywhere in
    * {@code core/src/main/java} outside test/model code -- no {@code *PropertyDialogService}
    * apply method ever reads it back on write, for any type. Before this fix,
    * {@code set_assembly_properties({locked:true})} on a non-sheet type returned {@code ok:true}
    * and silently changed nothing; it must now be refused like the other dead fields, pointing
    * the caller at {@code edit(op:"set_lock")} instead.
    *
    * <p>Bug #76894: image/line/oval/rectangle used to be carved out here as exceptions, on the
    * mistaken assumption that their apply methods genuinely read this field back. They do not --
    * their apply methods only ever call {@code isLocked()}'s sibling accessors
    * ({@code getWidth()}/{@code getHeight()}/{@code getLeft()}/{@code getTop()}), never
    * {@code isLocked()} itself. Their real, separate lock mechanism lives on
    * {@code LockableVSAssembly}, reached through {@code edit(op:"set_lock")}, not through this
    * dialog-model field -- so they belong in this refusal list along with every other type.
    */
   @Test
   void refusesLockedOnTypesWhereItIsDead() {
      for(String type : java.util.List.of("table", "crosstab", "chart", "gauge", "text",
                                          "selectionlist", "selectiontree", "checkbox",
                                          "combobox", "radiobutton", "slider", "spinner",
                                          "textinput", "timeslider", "calendar", "tab",
                                          "calctable", "groupcontainer", "selectioncontainer",
                                          "submit", "image", "line", "oval", "rectangle"))
      {
         assertTrue(PropertyAliases.forType(type).aliases().containsKey("locked"),
                    type + " should still list 'locked' as readable");
         assertThrows(IllegalArgumentException.class,
                      () -> PropertyAliases.resolveForWrite(type, "locked"),
                      "'locked' has no effect on write for " + type);
      }
   }

   /**
    * Bug #76556 (VFO-016): {@code basicGeneralPaneModel.enabled} has a getter/setter pair on the
    * six input assemblies' dialog models -- populated on every read, one level below the live
    * {@code enabled} alias -- but {@code VSInputService}'s apply methods for all six never read
    * it back. Before this fix, a raw dotted-path write there returned {@code ok:true} and always
    * read back {@code false}. The {@code enabled} alias itself must keep resolving to, and stay
    * writable at, the live {@code generalPropPaneModel.enabled} one level up.
    */
   @Test
   void refusesBasicGeneralEnabledOnTheSixInputTypesWhereItIsDead() {
      assertThrows(IllegalArgumentException.class,
                   () -> PropertyAliases.resolveForWrite("spinner",
                      "spinnerGeneralPaneModel.generalPropPaneModel.basicGeneralPaneModel." +
                      "enabled"));

      java.util.Map<String, String> prefixes = java.util.Map.of(
         "checkbox", "checkboxGeneralPaneModel",
         "textinput", "textInputGeneralPaneModel",
         "slider", "sliderGeneralPaneModel",
         "radiobutton", "radioButtonGeneralPaneModel",
         "combobox", "comboboxGeneralPaneModel",
         "spinner", "spinnerGeneralPaneModel");

      for(var entry : prefixes.entrySet()) {
         String path = entry.getValue() +
            ".generalPropPaneModel.basicGeneralPaneModel.enabled";
         assertThrows(IllegalArgumentException.class,
                      () -> PropertyAliases.resolveForWrite(entry.getKey(), path),
                      "basicGeneralPaneModel.enabled has no effect on write for " +
                      entry.getKey());

         assertEquals(entry.getValue() + ".generalPropPaneModel.enabled",
                      PropertyAliases.resolveForWrite(entry.getKey(), "enabled"),
                      "the 'enabled' alias must still resolve to, and stay writable at, the " +
                      "live field for " + entry.getKey());
      }
   }

   /**
    * Redmine 78271: no assembly type reads {@code basicGeneralPaneModel.enabled} back, so the raw
    * write is refused on every type (not only the six input assemblies), including the embedded
    * viewsheet, and the refusal only points at the {@code enabled} alias where one is live.
    */
   @Test
   void refusesBasicGeneralEnabledOnEveryAssemblyType() {
      java.util.Map<String, String> prefixes = java.util.Map.of(
         "gauge", "gaugeGeneralPaneModel.outputGeneralPaneModel",
         "text", "textGeneralPaneModel.outputGeneralPaneModel",
         "calendar", "calendarGeneralPaneModel.dataGeneralPaneModel",
         "table", "tableGeneralPaneModel.dataGeneralPaneModel",
         "selectionlist", "selectionListGeneralPaneModel",
         "chart", "chartGeneralPaneModel",
         "viewsheet", "viewsheetGeneralPaneModel",
         "groupcontainer", "groupContainerGeneralPane",
         "rectangle", "shapeGeneralPaneModel");

      for(var entry : prefixes.entrySet()) {
         String type = entry.getKey();
         String path = entry.getValue() + ".generalPropPaneModel.basicGeneralPaneModel.enabled";
         IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> PropertyAliases.resolveForWrite(type, path),
            "basicGeneralPaneModel.enabled has no effect on write for " + type);
         boolean pointsAtAlias = ex.getMessage().contains("'enabled' alias");
         boolean hasLiveAlias = !java.util.Set.of("viewsheet", "groupcontainer", "rectangle")
            .contains(type);
         assertEquals(hasLiveAlias, pointsAtAlias, type + ": " + ex.getMessage());
      }

      assertDoesNotThrow(() -> PropertyAliases.resolveForWrite("gauge", "enabled"));
      assertDoesNotThrow(() -> PropertyAliases.resolveForWrite("text", "enabled"));
   }

   /**
    * Bug #76771 (VCG-008): {@code hierarchyPropertyPaneModel.dimensions} is a
    * {@code VSDimensionModel[]} that {@link PropertyPath#coerce} cannot build -- no bean-array
    * branch exists at all -- so the raw dotted path, the only way in (the field is not
    * registered as an alias anywhere), must be refused by name rather than left to the generic
    * coercion error, on BOTH chart and crosstab: the field and the failure are identical on both.
    */
   @Test
   void theHierarchyDimensionsFieldIsRefusedForWriteOnChartAndCrosstab() {
      for(String assemblyType : java.util.List.of("chart", "crosstab")) {
         for(String path : java.util.List.of(
            "hierarchyPropertyPaneModel.dimensions",
            "hierarchyPropertyPaneModel.dimensions.members"))
         {
            IllegalArgumentException thrown = assertThrows(
               IllegalArgumentException.class,
               () -> PropertyAliases.resolveForWrite(assemblyType, path),
               assemblyType + "/" + path + " must be refused");
            assertTrue(thrown.getMessage().contains("add_hierarchy_dimension"),
                       "the refusal must point at the tool that works: " + thrown.getMessage());
         }
      }
   }

   /**
    * Regression for bug #78536: an index-suffixed or bracketed spelling of the dimensions field
    * gets the same add_hierarchy_dimension refusal, not PropertyPath's generic "not a property".
    */
   @Test
   void indexedSpellingsOfTheHierarchyDimensionsFieldGetTheToolRefusal() {
      for(String assemblyType : java.util.List.of("chart", "crosstab")) {
         for(String path : java.util.List.of(
            "hierarchyPropertyPaneModel.dimensions0.members",
            "hierarchyPropertyPaneModel.dimensions[0]",
            "hierarchyPropertyPaneModel.dimensions[0].members",
            "hierarchyPropertyPaneModel.dimensions[]",
            "hierarchyPropertyPaneModel.dimensions12"))
         {
            IllegalArgumentException thrown = assertThrows(
               IllegalArgumentException.class,
               () -> PropertyAliases.resolveForWrite(assemblyType, path),
               assemblyType + "/" + path + " must be refused");
            assertTrue(thrown.getMessage().contains("add_hierarchy_dimension"),
                       assemblyType + "/" + path + ": " + thrown.getMessage());
         }
      }
   }

   /** Bug #78536: the same prefix test guards the sheet refusals, so they catch these too. */
   @Test
   void indexedSpellingsOfRefusedSheetPanesGetTheirSpecificRefusal() {
      Map<String, String> expected = Map.of(
         "filtersPane[0].x", "'filtersPane' is read-only",
         "screensPane0.x", "'screensPane' is not settable",
         "vsScriptPane[0]", "update_script",
         "localizationPane[1]", "'localizationPane' is read-only",
         "vsOptionsPane.selectDataSourceDialogModel[0].dataSource", "set_viewsheet_data_source");

      for(Map.Entry<String, String> entry : expected.entrySet()) {
         IllegalArgumentException thrown = assertThrows(
            IllegalArgumentException.class,
            () -> PropertyAliases.resolveForWrite(PropertyAliases.SHEET, entry.getKey()),
            entry.getKey() + " must be refused");
         assertTrue(thrown.getMessage().contains(entry.getValue()),
                    entry.getKey() + ": " + thrown.getMessage());
      }
   }

   /**
    * Bug #78536: widening the prefix test must not refuse a path that writes today -- a field
    * that only shares leading letters with a guarded prefix stays writable.
    */
   @Test
   void widenedPrefixTestStillLetsCurrentlyValidPathsThrough() {
      assertEquals("hierarchyPropertyPaneModel.columnList",
                   PropertyAliases.resolveForWrite("chart", "hierarchyPropertyPaneModel.columnList"));
      assertEquals("hierarchyPropertyPaneModel.grayedOutFields",
                   PropertyAliases.resolveForWrite("crosstab",
                                                   "hierarchyPropertyPaneModel.grayedOutFields"));
      assertEquals("vsOptionsPane.alias",
                   PropertyAliases.resolveForWrite(PropertyAliases.SHEET, "vsOptionsPane.alias"));
      assertEquals("vsOptionsPane.alias",
                   PropertyAliases.resolveForWrite(PropertyAliases.SHEET, "alias"));
   }

   /** The refusal is scoped to {@code dimensions}; sibling hierarchy-pane fields are untouched. */
   @Test
   void theHierarchyDimensionsRefusalDoesNotReachItsSiblingFields() {
      assertDoesNotThrow(
         () -> PropertyAliases.resolveForWrite("chart", "hierarchyPropertyPaneModel.columnList"));
      assertDoesNotThrow(
         () -> PropertyAliases.resolveForWrite("chart", "hierarchyPropertyPaneModel.cube"));
      assertDoesNotThrow(
         () -> PropertyAliases.resolveForWrite("crosstab",
            "hierarchyPropertyPaneModel.grayedOutFields"));
   }

   // ── bug #76809 (VTB-017): 'primary' is present and writable -- the gap is discoverability ──

   /**
    * Regression guard against the original bug report's own (incorrect) theory: {@code primary}
    * must keep resolving to the live {@code basicGeneralPaneModel.primary} field, on crosstab and
    * on another {@link #basicGeneral} type, for both list()'s alias vocabulary and the raw path.
    */
   @Test
   void primaryStillResolvesForCrosstabAndChart() {
      assertTrue(PropertyAliases.forType("crosstab").aliases().containsKey("primary"),
                 "crosstab should still expose 'primary'");
      assertEquals(
         "tableViewGeneralPaneModel.generalPropPaneModel.basicGeneralPaneModel.primary",
         PropertyAliases.resolve("crosstab", "primary"));
      assertEquals(
         "tableViewGeneralPaneModel.generalPropPaneModel.basicGeneralPaneModel.primary",
         PropertyAliases.resolveForWrite("crosstab", "primary"),
         "primary must stay writable, not just readable");

      assertTrue(PropertyAliases.forType("chart").aliases().containsKey("primary"),
                 "chart should still expose 'primary'");
      assertEquals(
         "chartGeneralPaneModel.generalPropPaneModel.basicGeneralPaneModel.primary",
         PropertyAliases.resolve("chart", "primary"));
   }

   /**
    * The actual fix: {@code primary}'s own name has zero lexical connection to the Composer UI's
    * caption for it ("Visible in External Viewsheets"). {@link PropertyAliases#labelFor} must
    * surface that caption so a caller searching by the user's own words (e.g. "external
    * dashboard") can find it -- and must do so for every {@link #basicGeneral}/{@link
    * #shapeGeneral} type at once, not just crosstab.
    */
   @Test
   void primaryHasTheComposerUiLabel() {
      assertEquals("Visible in External Viewsheets", PropertyAliases.labelFor("primary"));
   }

   /** An alias with no better caption than its own name must not fabricate one. */
   @Test
   void anOrdinaryAliasHasNoLabel() {
      assertNull(PropertyAliases.labelFor("visible"));
      assertNull(PropertyAliases.labelFor("name"));
   }

   /**
    * The inverse of {@link #everyDeclaredAliasResolvesOnItsDialogModel}, and the gap that let
    * VOF-008 ship: that test proves every DECLARED alias resolves, which says nothing about a
    * real, applied property that was never declared at all. {@code submitOnChange} was genuinely
    * written by {@code VSInputService} and genuinely reachable by its raw dotted path on all six
    * input assemblies, but because {@code list_assembly_properties} and
    * {@code get_assembly_properties} iterate the alias map alone, it was invisible — findable
    * only by reverse-engineering {@code get_assembly_properties(raw: true)}.
    *
    * <p>The seven types here are exactly the ones whose dialog service turns the checkbox on
    * ({@code generalPropPaneModel.setShowSubmitCheckbox(true)}, six call sites in
    * {@code VSInputService}, plus the range slider's own copy on
    * {@code RangeSliderSizePaneModel}). Nothing else should gain the alias: on a chart, table or
    * gauge the field is inherited from the shared model, never read and never applied.
    */
   @Test
   void everyInputAssemblyExposesSubmitOnChange() {
      for(String type : java.util.List.of("checkbox", "combobox", "radiobutton", "slider",
                                          "spinner", "textinput", "timeslider"))
      {
         PropertyAliases.TypeAliases entry = PropertyAliases.forType(type);
         String path = entry.aliases().get("submitOnChange");

         assertNotNull(path, type + " applies submitOnChange but does not alias it, so " +
                             "list_assembly_properties cannot show it");
         assertDoesNotThrow(() -> PropertyPath.typeOf(entry.modelClass(), path),
                            "submitOnChange on " + type + " points at '" + path +
                            "', which does not exist on " + entry.modelClass().getSimpleName());
      }
   }

   /**
    * ...and the types that merely inherit the field keep it out of their vocabulary. Aliasing it
    * on an assembly whose dialog never reads it back would report a setting as available and
    * then silently do nothing — the trap {@code shadow} and {@code sliderLabelPaneModel.showLabel}
    * are deliberately excluded for.
    */
   @Test
   void submitOnChangeIsNotExposedOnAssembliesThatIgnoreIt() {
      for(String type : java.util.List.of("chart", "gauge", "text", "image", "crosstab")) {
         assertNull(PropertyAliases.forType(type).aliases().get("submitOnChange"),
                    type + " does not apply submitOnChange and must not advertise it");
      }
   }

   // ── bug #76936 (Track A): SelectionTree/RangeSlider/SelectionContainer alias gaps ────────

   /**
    * Problem A: {@code singleSelectionLevels} is real on SelectionTree
    * (SelectionTreePropertyDialogService reads/writes it via
    * {@code getSingleSelectionLevelNames}/{@code setSingleSelectionLevelNames}), but must NOT be
    * added to the shared {@code selectionGeneral} helper both selectionList() and selectionTree()
    * call -- SelectionListPropertyDialogService never reads it back, so it would be a dead-field
    * trap for SelectionList.
    */
   @Test
   void singleSelectionLevelsIsAliasedOnSelectionTreeOnly() {
      assertEquals("selectionGeneralPaneModel.singleSelectionLevels",
                   PropertyAliases.resolve("selectiontree", "singleSelectionLevels"));
      assertNull(PropertyAliases.forType("selectionlist").aliases().get("singleSelectionLevels"),
                 "singleSelectionLevels is dead on SelectionList and must not be advertised");
   }

   /** Problem C: the plain-scalar RangeSliderSizePaneModel fields. */
   @Test
   void rangeSliderExposesTheSizePaneFields() {
      String prefix = "rangeSliderAdvancedPaneModel.rangeSliderSizePaneModel.";
      assertEquals(prefix + "length", PropertyAliases.resolve("timeslider", "length"));
      assertEquals(prefix + "logScale", PropertyAliases.resolve("timeslider", "logScale"));
      assertEquals(prefix + "upperInclusive",
                   PropertyAliases.resolve("timeslider", "upperInclusive"));
      assertEquals(prefix + "rangeType", PropertyAliases.resolve("timeslider", "rangeType"));
      assertEquals(prefix + "rangeSize", PropertyAliases.resolve("timeslider", "rangeSize"));
      assertEquals(prefix + "maxRangeSize",
                   PropertyAliases.resolve("timeslider", "maxRangeSize"));
   }

   /**
    * Problem D: {@code tick}/{@code currentValue}/{@code showMin}/{@code showMax} are real on
    * RangeSlider's own {@code sliderLabelPaneModel} copy, named {@code showMin}/{@code showMax}
    * to match {@code slider()}'s own naming for the identical {@code minimum}/{@code maximum}
    * visibility-toggle fields. {@code label}/{@code showLabel} are dead on RangeSlider (confirmed
    * by reading the full get/set method bodies of RangeSliderPropertyDialogService) and must NOT
    * be aliased, unlike Slider's own separate {@code sliderLabelPaneModel.label}.
    */
   @Test
   void rangeSliderExposesTheLabelPaneFieldsButNotDeadOnes() {
      String prefix = "rangeSliderAdvancedPaneModel.sliderLabelPaneModel.";
      assertEquals(prefix + "tick", PropertyAliases.resolve("timeslider", "tick"));
      assertEquals(prefix + "currentValue", PropertyAliases.resolve("timeslider", "currentValue"));
      assertEquals(prefix + "minimum", PropertyAliases.resolve("timeslider", "showMin"));
      assertEquals(prefix + "maximum", PropertyAliases.resolve("timeslider", "showMax"));

      assertNull(PropertyAliases.forType("timeslider").aliases().get("label"),
                 "label is dead on RangeSlider and must not be advertised");
      assertNull(PropertyAliases.forType("timeslider").aliases().get("showLabel"),
                 "showLabel is dead on RangeSlider and must not be advertised");
   }

   /** Problem E: the plain-boolean SelectionContainerGeneralPaneModel fields. */
   @Test
   void selectionContainerExposesShowCurrentSelectionAndAdhocEnabled() {
      assertEquals("selectionContainerGeneralPaneModel.showCurrentSelection",
                   PropertyAliases.resolve("selectioncontainer", "showCurrentSelection"));
      assertEquals("selectionContainerGeneralPaneModel.adhocEnabled",
                   PropertyAliases.resolve("selectioncontainer", "adhocEnabled"));
   }

   /**
    * The chart's target-lines pane has no scalar write path at all -- its entries are
    * {@code TargetInfo} beans that {@code PropertyPath} cannot build -- so the raw dotted path,
    * the only way in, is refused rather than left to fail with a generic coercion message. The
    * refusal has to name the tools that do work, or a caller reads "not supported" and stops.
    */
   @Test
   void theChartTargetLinesPaneIsRefusedForWriteAndNamesItsReplacement() {
      for(String path : java.util.List.of(
         "chartAdvancedPaneModel.chartTargetLinesPaneModel",
         "chartAdvancedPaneModel.chartTargetLinesPaneModel.chartTargets",
         "chartAdvancedPaneModel.chartTargetLinesPaneModel.newTargetInfo",
         "chartAdvancedPaneModel.chartTargetLinesPaneModel.deletedIndexList",
         "chartAdvancedPaneModel.chartTargetLinesPaneModel.supportsTarget",
         "chartTargetLinesPaneModel.chartTargets"))
      {
         IllegalArgumentException thrown = assertThrows(
            IllegalArgumentException.class,
            () -> PropertyAliases.resolveForWrite("chart", path), path + " must be refused");
         assertTrue(thrown.getMessage().contains("add_chart_target_line"),
                    "the refusal must point at the tool that works: " + thrown.getMessage());
      }
   }

   /** The refusal is chart-scoped, and must not swallow the plot-options pane beside it. */
   @Test
   void theTargetLinesRefusalDoesNotReachTheRestOfTheAdvancedPane() {
      assertEquals("chartAdvancedPaneModel.chartPlotOptionsPaneModel.showReferenceLine",
                   PropertyAliases.resolveForWrite("chart", "showReferenceLine"));
      assertEquals("chartLinePaneModel.trendLineType",
                   PropertyAliases.resolveForWrite("chart", "trendLineType"));
   }

   // ── bug #76929: Text's under-aliased fields ────────────────────────────────

   /**
    * autoSize/embedUrl/scaleVertical/popLocation/padding were only reachable through
    * {@code get_assembly_properties(raw:true)} despite being genuinely applied by
    * {@code TextPropertyDialogService}.
    */
   @Test
   void exposesThePreviouslyMissingTextAliases() {
      assertEquals("textGeneralPaneModel.textPaneModel.autoSize",
                   PropertyAliases.resolveForWrite("text", "autoSize"));
      assertEquals("textGeneralPaneModel.textPaneModel.url",
                   PropertyAliases.resolveForWrite("text", "embedUrl"));
      assertEquals("textGeneralPaneModel.sizePositionPaneModel.scaleVertical",
                   PropertyAliases.resolveForWrite("text", "scaleVertical"));
      assertEquals("textGeneralPaneModel.popLocation",
                   PropertyAliases.resolveForWrite("text", "popLocation"));
      assertEquals("textGeneralPaneModel.paddingPaneModel.top",
                   PropertyAliases.resolveForWrite("text", "paddingTop"));
      assertEquals("textGeneralPaneModel.paddingPaneModel.left",
                   PropertyAliases.resolveForWrite("text", "paddingLeft"));
      assertEquals("textGeneralPaneModel.paddingPaneModel.bottom",
                   PropertyAliases.resolveForWrite("text", "paddingBottom"));
      assertEquals("textGeneralPaneModel.paddingPaneModel.right",
                   PropertyAliases.resolveForWrite("text", "paddingRight"));
   }

   /**
    * {@code embedUrl}'s own name ("url") has no lexical connection to the Composer's caption for
    * it -- the same category of gap {@code primary} had (bug #76809/VTB-017).
    */
   @Test
   void embedUrlHasTheComposerUiLabel() {
      assertEquals("Embed content from URL", PropertyAliases.labelFor("embedUrl"));
   }

   @Test
   void textAliasesRoundTrip() {
      TextPropertyDialogModel model = new TextPropertyDialogModel();
      String path = PropertyAliases.resolve("text", "autoSize");
      Object result = PropertyPath.set(model, path, true);

      assertEquals(true, PropertyPath.get(result, path));
   }

   // ── bug #76929: Line/Oval/Rectangle's under-aliased fields ─────────────────

   /**
    * The old {@code shape()} shared a single minimal alias set (name/visible/primary/
    * size/position only) across all three types. Confirmed against each type's own
    * {@code *PropertyDialogService} that every field below is genuinely read and written back.
    */
   @Test
   void exposesThePreviouslyMissingLineAliases() {
      assertEquals("shapeGeneralPaneModel.basicGeneralPaneModel.shadow",
                   PropertyAliases.resolveForWrite("line", "shadow"));
      assertEquals("linePropertyPaneModel.linePropPaneModel.color",
                   PropertyAliases.resolveForWrite("line", "lineColor"));
      assertEquals("linePropertyPaneModel.linePropPaneModel.colorValue",
                   PropertyAliases.resolveForWrite("line", "lineColorValue"));
      assertEquals("linePropertyPaneModel.linePropPaneModel.style",
                   PropertyAliases.resolveForWrite("line", "lineStyle"));
      assertEquals("linePropertyPaneModel.begin",
                   PropertyAliases.resolveForWrite("line", "beginArrow"));
      assertEquals("linePropertyPaneModel.end",
                   PropertyAliases.resolveForWrite("line", "endArrow"));
   }

   @Test
   void exposesThePreviouslyMissingOvalAndRectangleAliases() {
      for(String type : java.util.List.of("oval", "rectangle")) {
         String prefix = type + "PropertyPaneModel";
         assertEquals(prefix + ".linePropPaneModel.color",
                      PropertyAliases.resolveForWrite(type, "lineColor"));
         assertEquals(prefix + ".linePropPaneModel.style",
                      PropertyAliases.resolveForWrite(type, "lineStyle"));
         assertEquals(prefix + ".fillPropPaneModel.color",
                      PropertyAliases.resolveForWrite(type, "fillColor"));
         assertEquals(prefix + ".fillPropPaneModel.alpha",
                      PropertyAliases.resolveForWrite(type, "fillAlpha"));
         assertEquals(prefix + ".shadowPropPaneModel.apply",
                      PropertyAliases.resolveForWrite(type, "shadowApply"));
         assertEquals(prefix + ".shadowPropPaneModel.color",
                      PropertyAliases.resolveForWrite(type, "shadowColor"));
         assertEquals(prefix + ".shadowPropPaneModel.alpha",
                      PropertyAliases.resolveForWrite(type, "shadowAlpha"));
         assertEquals(prefix + ".shadowPropPaneModel.direction",
                      PropertyAliases.resolveForWrite(type, "shadowDirection"));
         assertEquals(prefix + ".shadowPropPaneModel.distance",
                      PropertyAliases.resolveForWrite(type, "shadowDistance"));
         assertEquals(prefix + ".shadowPropPaneModel.blur",
                      PropertyAliases.resolveForWrite(type, "shadowBlur"));
      }

      assertEquals("rectanglePropertyPaneModel.radius",
                   PropertyAliases.resolveForWrite("rectangle", "radius"));
   }

   /**
    * {@code shadow} is a plain boolean toggle genuinely applied only by
    * {@code LinePropertyDialogService} -- Oval's and Rectangle's own dialog services never read
    * {@code basicGeneralPaneModel.isShadow()} back at all (they use their own, richer
    * {@code shadowPropPaneModel} instead, exposed above as {@code shadowApply} etc.). Sharing one
    * "shadow" alias across all three types the way the old {@code shape()} would have to would
    * have made it a dead field on two of them.
    */
   @Test
   void shadowIsOnlyAliasedForLineNotOvalOrRectangle() {
      assertTrue(PropertyAliases.forType("line").aliases().containsKey("shadow"),
                 "line should expose 'shadow'");

      for(String type : java.util.List.of("oval", "rectangle")) {
         assertFalse(PropertyAliases.forType(type).aliases().containsKey("shadow"),
                     type + " should not expose 'shadow' -- it uses shadowApply/shadowColor/... " +
                     "instead");
      }
   }

   @Test
   void radiusIsOnlyAliasedForRectangle() {
      assertTrue(PropertyAliases.forType("rectangle").aliases().containsKey("radius"));
      assertFalse(PropertyAliases.forType("line").aliases().containsKey("radius"));
      assertFalse(PropertyAliases.forType("oval").aliases().containsKey("radius"));
   }

   @Test
   void lineOvalRectangleAliasesRoundTrip() {
      inetsoft.web.composer.model.vs.LinePropertyDialogModel lineModel =
         new inetsoft.web.composer.model.vs.LinePropertyDialogModel();
      String linePath = PropertyAliases.resolve("line", "beginArrow");
      Object lineResult = PropertyPath.set(lineModel, linePath, 2);
      assertEquals(2, PropertyPath.get(lineResult, linePath));

      inetsoft.web.composer.model.vs.OvalPropertyDialogModel ovalModel =
         new inetsoft.web.composer.model.vs.OvalPropertyDialogModel();
      String ovalPath = PropertyAliases.resolve("oval", "fillAlpha");
      Object ovalResult = PropertyPath.set(ovalModel, ovalPath, 50);
      assertEquals(50, PropertyPath.get(ovalResult, ovalPath));

      inetsoft.web.composer.model.vs.RectanglePropertyDialogModel rectModel =
         new inetsoft.web.composer.model.vs.RectanglePropertyDialogModel();
      String radiusPath = PropertyAliases.resolve("rectangle", "radius");
      Object rectResult = PropertyPath.set(rectModel, radiusPath, 20);
      assertEquals(20, PropertyPath.get(rectResult, radiusPath));
   }

   // ── bug #76929: gradientColor is now settable, not just refused ────────────

   /**
    * {@code fillPropPaneModel.gradientColor} (Oval/Rectangle's Fill tab gradient) used to fail
    * with a generic "expects GradientColor, which '...' is not" error -- {@link PropertyPath}
    * now builds the bean directly, so the raw dotted path round-trips a full gradient, colors
    * included.
    */
   @Test
   void gradientColorIsSettableOnOvalAndRectangleViaTheRawPath() {
      Map<String, Object> gradient = Map.of(
         "apply", true,
         "direction", "linear",
         "angle", 90,
         "colors", java.util.List.of(
            Map.of("color", "#FF0000", "offset", 0),
            Map.of("color", "#FFFF00", "offset", 100)));

      assertGradientColorRoundTrips(new inetsoft.web.composer.model.vs.OvalPropertyDialogModel(),
                                     "ovalPropertyPaneModel.fillPropPaneModel.gradientColor",
                                     gradient);
      assertGradientColorRoundTrips(
         new inetsoft.web.composer.model.vs.RectanglePropertyDialogModel(),
         "rectanglePropertyPaneModel.fillPropPaneModel.gradientColor", gradient);
   }

   private static void assertGradientColorRoundTrips(Object model, String path,
                                                      Map<String, Object> gradient)
   {
      Object result = PropertyPath.set(model, path, gradient);
      Object readBack = PropertyPath.get(result, path);

      assertInstanceOf(inetsoft.uql.viewsheet.GradientColor.class, readBack);
      inetsoft.uql.viewsheet.GradientColor gradientColor =
         (inetsoft.uql.viewsheet.GradientColor) readBack;
      assertTrue(gradientColor.isApply());
      assertEquals("linear", gradientColor.getDirection());
      assertEquals(90, gradientColor.getAngle());
      assertEquals(2, gradientColor.getColors().length);
      assertEquals("#FF0000", gradientColor.getColors()[0].getColor());
      assertEquals(100, gradientColor.getColors()[1].getOffset());
   }

   // ── #78393: raw dotted paths resolve to one canonical spelling ──

   @Test
   void resolveLowersEverySegmentInitialOfARawPath() {
      assertEquals("textInputColumnOptionPaneModel.dateEditorModel.minimum",
                   PropertyAliases.resolve("textinput",
                                           "TextInputColumnOptionPaneModel.DateEditorModel.minimum"));
      assertEquals("vsOptionsPane.alias", PropertyAliases.resolve(PropertyAliases.SHEET, "alias"));
   }

   /** A capitalised first segment must reach the same refusal as the canonical spelling. */
   @Test
   void refusesSheetScriptAndReadOnlyPanesUnderACapitalizedFirstSegment() {
      Exception script = assertThrows(IllegalArgumentException.class,
         () -> PropertyAliases.resolveForWrite(PropertyAliases.SHEET, "VsScriptPane.onInit"));
      assertTrue(script.getMessage().contains("'vsScriptPane' is not settable"),
                 script.getMessage());

      for(String pane : new String[] { "FiltersPane", "LocalizationPane", "ScreensPane" }) {
         String canonical = Character.toLowerCase(pane.charAt(0)) + pane.substring(1);
         Exception e = assertThrows(IllegalArgumentException.class,
            () -> PropertyAliases.resolveForWrite(PropertyAliases.SHEET, pane + ".x"));
         assertTrue(e.getMessage().contains("'" + canonical + "'"), e.getMessage());
      }
   }

   // ── empty segments and capitalised spellings (bug #78440) ─────────────────

   @Test
   void refusesAnEmptySegmentNamingTheWholePath() {
      for(String bad : new String[] { "chartLinePaneModel.trendLineStyle.",
                                      "ChartLinePaneModel.TrendLineStyle..",
                                      ".chartLinePaneModel.trendLineStyle",
                                      "chartLinePaneModel..trendLineStyle", "." })
      {
         Exception e = assertThrows(IllegalArgumentException.class,
            () -> PropertyAliases.resolveForWrite("chart", bad), bad);
         assertTrue(e.getMessage().contains("'" + bad + "'") &&
                    e.getMessage().contains("empty segment"), e.getMessage());
         assertThrows(IllegalArgumentException.class, () -> PropertyAliases.resolve("chart", bad));
         assertThrows(IllegalArgumentException.class,
                      () -> PropertyAliases.lowerSegmentInitials(bad));
      }
   }

   /** Bug #78440 round 2: the guard canon must fold an initial exactly as the setter resolves it. */
   @Test
   void foldsNonAsciiInitialsTheWayTheSetterResolvesThem() {
      assertEquals("selectionGeneralPaneModel.sortType",
                   PropertyAliases.lowerSegmentInitials("ſelectionGeneralPaneModel.sortType"));
      assertEquals("selectionGeneralPaneModel.sortType",
                   PropertyAliases.lowerSegmentInitials("selectionGeneralPaneModel.ſortType"));
      assertEquals("imagePreviewPaneModel.selectedImage",
                   PropertyAliases.lowerSegmentInitials("ımagePreviewPaneModel.selectedImage"));
      assertEquals("selectionGeneralPaneModel.sortType",
                   PropertyAliases.lowerSegmentInitials("SelectionGeneralPaneModel.SortType"));

      // K (Kelvin) and I-with-dot lower-case to ASCII but the setter does not resolve them;
      // they only need to canonicalize without surviving as a spelling that bypasses a guard.
      assertEquals("kind.x", PropertyAliases.lowerSegmentInitials("Kind.x"));
      assertEquals("image.x", PropertyAliases.lowerSegmentInitials("İmage.x"));
   }

   /** For every char the setter upper-cases to an ASCII letter, the canon is that letter lower-cased. */
   @Test
   void canonicalInitialInvertsTheSetterCapitalization() {
      for(char c = 0; c < Character.MAX_VALUE; c++) {
         char upper = Character.toUpperCase(c);

         if(upper < 128 && Character.isLetter(upper)) {
            assertEquals(Character.toLowerCase(upper), PropertyPath.canonicalInitial(c),
                         "U+" + Integer.toHexString(c));
         }
      }
   }

   @Test
   void refusesNonAsciiInitialVariantsOfGuardedSheetPanes() {
      Exception e = assertThrows(IllegalArgumentException.class,
         () -> PropertyAliases.resolveForWrite(PropertyAliases.SHEET, "ſcreensPane.x"));
      assertTrue(e.getMessage().contains("'screensPane'"), e.getMessage());
   }

   @Test
   void refusesAnEmptySegmentOnTheSheetBeforeAnyGuard() {
      for(String bad : new String[] { ".vsScriptPane.onInit", "." }) {
         Exception e = assertThrows(IllegalArgumentException.class,
            () -> PropertyAliases.resolveForWrite(PropertyAliases.SHEET, bad), bad);
         assertTrue(e.getMessage().contains("empty segment"), e.getMessage());
      }

      assertThrows(IllegalArgumentException.class, () -> PropertyAliases.resolveForWrite(
         PropertyAliases.SHEET, "vsScriptPane..onInit"));
   }

   @Test
   void canonicalizesCapitalisedFirstLettersOfIntEnumAndShapeStylePaths() {
      assertEquals("chartLinePaneModel.trendLineStyle",
                   PropertyAliases.resolveForWrite("chart", "ChartLinePaneModel.TrendLineStyle"));
      assertEquals("selectionTreePaneModel.mode",
                   PropertyAliases.resolveForWrite("selectiontree", "SelectionTreePaneModel.Mode"));
      assertEquals("calendarAdvancedPaneModel.viewMode",
                   PropertyAliases.resolveForWrite("calendar",
                                                   "CalendarAdvancedPaneModel.ViewMode"));
      assertEquals("selectionGeneralPaneModel.sortType",
                   PropertyAliases.resolveForWrite("selectionlist",
                                                   "SelectionGeneralPaneModel.SortType"));
      assertEquals("rectanglePropertyPaneModel.linePropPaneModel.style",
                   PropertyAliases.resolveForWrite(
                      "rectangle", "RectanglePropertyPaneModel.LinePropPaneModel.Style"));
   }

   @Test
   void refusesCapitalisedChartTargetsAndHierarchyDimensions() {
      assertThrows(IllegalArgumentException.class, () -> PropertyAliases.resolveForWrite(
         "chart", "ChartAdvancedPaneModel.ChartTargetLinesPaneModel.chartTargets"));
      assertThrows(IllegalArgumentException.class, () -> PropertyAliases.resolveForWrite(
         "chart", "HierarchyPropertyPaneModel.dimensions"));
      assertThrows(IllegalArgumentException.class, () -> PropertyAliases.resolveForWrite(
         "crosstab", "hierarchyPropertyPaneModel.Dimensions"));
   }

   // ── bug-78566: dead fields on selection list/tree, range slider and slider ────

   /** Reviewed reads of the apply methods, per type: raw paths that are live without an alias. */
   private static final Map<String, List<String>> LIVE_WITHOUT_ALIAS = Map.of(
      "selectionlist", List.of(
         "revision", "sizePositionPaneModel.titleHeight", "sizePositionPaneModel.cellHeight",
         "selectionListPaneModel.additionalTables", "selectionListPaneModel.selectedTable",
         "selectionListPaneModel.selectedColumn"),
      "selectiontree", List.of(
         "revision", "sizePositionPaneModel.titleHeight", "sizePositionPaneModel.cellHeight",
         "selectionTreePaneModel.additionalTables", "selectionTreePaneModel.selectedTable",
         "selectionTreePaneModel.selectedColumns", "selectionTreePaneModel.id",
         "selectionTreePaneModel.parentId", "selectionTreePaneModel.label",
         "selectionTreePaneModel.idRef", "selectionTreePaneModel.parentIdRef",
         "selectionTreePaneModel.labelRef", "selectionTreePaneModel.selectionMeasurePaneModel"),
      "timeslider", List.of(
         "revision", "sizePositionPaneModel.titleHeight",
         "rangeSliderDataPaneModel.additionalTables", "rangeSliderDataPaneModel.selectedTable",
         "rangeSliderDataPaneModel.selectedColumns", "rangeSliderDataPaneModel.composite"),
      "slider", List.of(
         // variable is derived from the table binding on write (VSInputService), and checked by
         // AssemblyPropertyService.requireVariableFlagAchievable -- see PropertyAliases'
         // dataInputPaneModel.variable note
         "dataInputPaneModel.variable"));
   // writeBackDirectly, labelGap and labelPosition were listed live-without-alias here until bug
   // #80464 gave them short names; the classification test accepts an aliased leaf directly.

   private static boolean isOrUnder(String path, String entry) {
      return path.equals(entry) || path.endsWith("." + entry) || path.contains("." + entry + ".") ||
         path.startsWith(entry + ".");
   }

   private static void collectWritableLeaves(Class<?> type, String prefix, List<String> out,
                                             Deque<Class<?>> stack)
   {
      if(stack.contains(type)) {
         return;
      }

      stack.push(type);

      for(String property : PropertyPath.propertiesOf(type)) {
         String capitalized = Character.toUpperCase(property.charAt(0)) + property.substring(1);
         Method getter = null;
         boolean hasSetter = false;

         for(Method method : type.getMethods()) {
            if(method.getParameterCount() == 0 && (method.getName().equals("get" + capitalized) ||
                                                   method.getName().equals("is" + capitalized)))
            {
               getter = method;
            }

            hasSetter |= method.getParameterCount() == 1 &&
               method.getName().equals("set" + capitalized);
         }

         if(getter == null) {
            continue;
         }

         Class<?> returned = getter.getReturnType();

         if(returned.getName().startsWith("inetsoft.") && !returned.isEnum()) {
            collectWritableLeaves(returned, prefix + property + ".", out, stack);
         }
         else if(hasSetter) {
            out.add(prefix + property);
         }
      }

      stack.pop();
   }

   private static boolean refused(String type, String path) {
      try {
         PropertyAliases.resolveForWrite(type, path);
         return false;
      }
      catch(IllegalArgumentException e) {
         // a dotless key that is not an alias is "not a property", which is not a dead field
         return e.getMessage().contains("is read-only");
      }
   }

   /**
    * The axis is per type x writable pane-model leaf. "Not aliased" never meant "refused": any
    * dotted key resolves, so a leaf the apply method ignores must be registered dead, and one it
    * reads must be named live here. A new pane field or a changed apply method fails this test
    * until a human classifies it, instead of being implicitly accepted by omission.
    */
   @Test
   void everyWritableLeafOfSelectionAndSliderTypesIsClassifiedLiveOrDead() {
      for(String type : List.of("selectionlist", "selectiontree", "timeslider", "slider")) {
         List<String> leaves = new ArrayList<>();
         collectWritableLeaves(PropertyAliases.forType(type).modelClass(), "", leaves,
                               new ArrayDeque<>());
         assertFalse(leaves.isEmpty(), type);
         Collection<String> aliasPaths = PropertyAliases.forType(type).aliases().values();
         List<String> live = LIVE_WITHOUT_ALIAS.get(type);
         List<String> unclassified = new ArrayList<>();

         for(String leaf : leaves) {
            boolean liveRaw = live.stream().anyMatch(entry -> isOrUnder(leaf, entry));

            if(!aliasPaths.contains(leaf) && !refused(type, leaf) && !liveRaw) {
               unclassified.add(leaf);
            }
         }

         assertTrue(unclassified.isEmpty(),
                    type + " has writable leaves that are neither aliased, refused as dead, nor " +
                    "reviewed live: " + unclassified);

         for(String entry : live) {
            assertTrue(leaves.stream().anyMatch(leaf -> isOrUnder(leaf, entry)),
                       type + ": live entry '" + entry + "' matches no leaf");
            assertTrue(leaves.stream().filter(leaf -> isOrUnder(leaf, entry))
                          .noneMatch(leaf -> refused(type, leaf)),
                       type + ": live entry '" + entry + "' is also refused as dead");
         }
      }
   }

   @Test
   void deadSelectionFieldsAreRefusedByFullPathAndLiveSiblingsAreNot() {
      IllegalArgumentException list = assertThrows(IllegalArgumentException.class, () ->
         PropertyAliases.resolveForWrite(
            "selectionlist", "selectionGeneralPaneModel.singleSelectionLevels"));
      assertTrue(list.getMessage().contains("selectionGeneralPaneModel.singleSelectionLevels"),
                 list.getMessage());

      IllegalArgumentException label = assertThrows(IllegalArgumentException.class, () ->
         PropertyAliases.resolveForWrite(
            "timeslider", "rangeSliderAdvancedPaneModel.sliderLabelPaneModel.showLabel"));
      assertTrue(label.getMessage().contains(
         "rangeSliderAdvancedPaneModel.sliderLabelPaneModel.showLabel"), label.getMessage());
      assertThrows(IllegalArgumentException.class, () -> PropertyAliases.resolveForWrite(
         "slider", "sliderAdvancedPaneModel.sliderLabelPaneModel.showLabel"));

      assertDoesNotThrow(() -> PropertyAliases.resolveForWrite(
         "timeslider", "rangeSliderAdvancedPaneModel.sliderLabelPaneModel.tick"));
      assertDoesNotThrow(() -> PropertyAliases.resolveForWrite(
         "selectiontree", "selectionGeneralPaneModel.singleSelectionLevels"));
      assertDoesNotThrow(() -> PropertyAliases.resolveForWrite("selectionlist",
         "selectionGeneralPaneModel.submitOnChange"));
      assertDoesNotThrow(() -> PropertyAliases.resolveForWrite("slider", "showLabel"));
      assertDoesNotThrow(() -> PropertyAliases.resolveForWrite(
         "slider", "sliderGeneralPaneModel.generalPropPaneModel.submitOnChange"));
   }

   // ── bug-78573: dead SizePositionPaneModel fields on every sizePosition-registered type ────

   private static final List<String> SIZE_POSITION_TYPES = List.of(
      "gauge", "text", "image", "chart", "table", "crosstab", "calctable", "selectionlist",
      "selectiontree", "checkbox", "radiobutton", "slider", "combobox", "spinner", "textinput",
      "timeslider", "calendar", "tab", "groupcontainer", "line", "oval", "rectangle",
      "selectioncontainer", "submit", "viewsheet");

   private static final Map<String, List<String>> SIZE_POSITION_LIVE = Map.of(
      "scaleVertical", List.of("text"),
      "titleHeight", List.of("calctable", "calendar", "chart", "crosstab", "selectioncontainer",
                             "selectionlist", "selectiontree", "table", "timeslider", "checkbox",
                             "radiobutton"),
      "cellHeight", List.of("selectionlist", "selectiontree", "checkbox", "radiobutton"),
      "container", List.of());

   private static String sizePositionPath(String type, String field) {
      String top = PropertyAliases.forType(type).aliases().get("top");
      return top == null ? null : top.substring(0, top.length() - "top".length()) + field;
   }

   @Test
   void sizePositionFieldsAreRefusedExactlyWhereTheyAreDead() {
      int checked = 0;

      for(String type : SIZE_POSITION_TYPES) {
         for(Map.Entry<String, List<String>> field : SIZE_POSITION_LIVE.entrySet()) {
            String path = sizePositionPath(type, field.getKey());

            if(path == null) {
               continue;
            }

            boolean live = field.getValue().contains(type);
            assertEquals(!live, refused(type, path), type + " " + path);
            checked++;
         }
      }

      assertTrue(checked >= 60, "checked " + checked);
   }

   @Test
   void reportedDeadSizePositionFieldsAreRefusedWithReadOnlyMessage() {
      for(String type : List.of("gauge", "chart", "image", "combobox", "spinner", "calendar")) {
         IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () ->
            PropertyAliases.resolveForWrite(type, sizePositionPath(type, "scaleVertical")));
         assertTrue(e.getMessage().contains("is read-only on " + type) &&
                    e.getMessage().contains("sizePositionPaneModel.scaleVertical"),
                    e.getMessage());
      }

      for(String type : List.of("gauge", "chart", "combobox", "text")) {
         assertTrue(refused(type, sizePositionPath(type, "cellHeight")), type);
      }

      for(String type : List.of("gauge", "calctable")) {
         assertTrue(refused(type, sizePositionPath(type, "container")), type);
      }
   }

   @Test
   void liveSizePositionFieldsStayWritable() {
      assertDoesNotThrow(() -> PropertyAliases.resolveForWrite("text", "scaleVertical"));
      assertDoesNotThrow(() -> PropertyAliases.resolveForWrite(
         "text", sizePositionPath("text", "scaleVertical")));

      for(String type : List.of("selectionlist", "selectiontree", "checkbox", "radiobutton")) {
         assertDoesNotThrow(() -> PropertyAliases.resolveForWrite(
            type, sizePositionPath(type, "cellHeight")), type);
      }
   }

   // ── bug #80150: the embedded viewsheet assembly ──────────────────────────

   private static String refusal(String type, String key) {
      return assertThrows(IllegalArgumentException.class,
                          () -> PropertyAliases.resolveForWrite(type, key), key).getMessage();
   }

   @Test
   void embeddedViewsheetVocabularyIsExactlyTheDialogFieldsPlusReadOnlySize() {
      assertTrue(PropertyAliases.covers("viewsheet"));
      assertTrue(PropertyAliases.covers(PropertyAliases.SHEET));
      assertNotSame(PropertyAliases.forType("viewsheet"), PropertyAliases.forType("sheet"));
      assertSame(ViewsheetObjectPropertyDialogModel.class,
                 PropertyAliases.forType("viewsheet").modelClass());
      assertEquals(List.of("name", "visible", "primary", "top", "left", "width", "height"),
                   new ArrayList<>(PropertyAliases.forType("viewsheet").aliases().keySet()));

      for(String alias : PropertyAliases.forType("viewsheet").aliases().keySet()) {
         assertFalse(alias.toLowerCase().contains("script"), alias);
         assertFalse(PropertyAliases.forType("viewsheet").aliases().get(alias).startsWith("."),
                     alias);
      }

      for(String live : List.of("name", "visible", "primary", "top", "left")) {
         assertDoesNotThrow(() -> PropertyAliases.resolveForWrite("viewsheet", live), live);
      }
   }

   @Test
   void embeddedViewsheetWidthAndHeightAreRefusedWithAnAccurateReason() {
      for(String key : List.of("width", "height", "sizePositionPaneModel.width",
                               "sizePositionPaneModel.height"))
      {
         String message = refusal("viewsheet", key);

         assertTrue(message.contains("embedded viewsheet") && message.contains("laid out") &&
                    message.contains("left/top"), message);
         assertFalse(message.contains("never reads it back"), message);
      }
   }

   @Test
   void embeddedViewsheetRefusesEveryOtherNonDialogPathLoudly() {
      for(String path : List.of(
         "sizePositionPaneModel.locked", "sizePositionPaneModel.titleHeight",
         "sizePositionPaneModel.cellHeight", "sizePositionPaneModel.scaleVertical",
         "sizePositionPaneModel.container", "generalPropPaneModel.enabled",
         "generalPropPaneModel.submitOnChange", "generalPropPaneModel.showEnabledGroup",
         "generalPropPaneModel.showSubmitCheckbox", "generalPropPaneModel.popLocation",
         "generalPropPaneModel.basicGeneralPaneModel.enabled",
         "generalPropPaneModel.basicGeneralPaneModel.refresh",
         "generalPropPaneModel.basicGeneralPaneModel.editable",
         "generalPropPaneModel.basicGeneralPaneModel.nameEditable",
         "generalPropPaneModel.basicGeneralPaneModel.shadow",
         "generalPropPaneModel.basicGeneralPaneModel.objectNames"))
      {
         assertTrue(refusal("viewsheet", path).contains("is read-only"), path);
      }

      // not a read-only message but loud all the same: no alias, so an unknown key
      assertThrows(IllegalArgumentException.class,
                   () -> PropertyAliases.resolveForWrite("viewsheet", "enabled"));
      assertThrows(IllegalArgumentException.class,
                   () -> PropertyAliases.resolveForWrite("viewsheet", "locked"));
   }

   @Test
   void embeddedViewsheetLockedIsNotAdvisedTowardSetLock() {
      for(String key : List.of("sizePositionPaneModel.locked")) {
         assertFalse(refusal("viewsheet", key).contains("set_lock"), key);
      }
   }

   @Test
   void embeddedViewsheetScriptPathsStayWritableAndRevisionIsNotAProperty() {
      assertDoesNotThrow(() -> PropertyAliases.resolveForWrite(
         "viewsheet", "vsAssemblyScriptPaneModel.expression"));
      assertDoesNotThrow(() -> PropertyAliases.resolveForWrite(
         "viewsheet", "vsAssemblyScriptPaneModel.scriptEnabled"));
      assertThrows(IllegalArgumentException.class,
                   () -> PropertyAliases.resolveForWrite("viewsheet", "revision"));
   }

   /**
    * The root of this model is an Immutables class, which {@code collectWritableLeaves} cannot
    * walk (it reads getX/isX only), so the two mutable panes are walked with their prefixes and
    * the script pane and revision are pinned as literals. Every leaf must be aliased or refused.
    */
   @Test
   void everyWritableLeafOfTheEmbeddedViewsheetDialogIsAliasedOrRefused() {
      List<String> leaves = new ArrayList<>();
      collectWritableLeaves(GeneralPropPaneModel.class, "generalPropPaneModel.", leaves,
                            new ArrayDeque<>());
      collectWritableLeaves(SizePositionPaneModel.class, "sizePositionPaneModel.", leaves,
                            new ArrayDeque<>());
      assertTrue(leaves.size() > 10, leaves.toString());
      assertTrue(leaves.contains("generalPropPaneModel.basicGeneralPaneModel.name"), leaves.toString());
      assertTrue(leaves.contains("sizePositionPaneModel.top"), leaves.toString());

      Collection<String> aliasPaths = PropertyAliases.forType("viewsheet").aliases().values();
      List<String> unclassified = new ArrayList<>();

      for(String leaf : leaves) {
         // width/height are aliased AND refused; the rest of the aliased set is live
         if(!aliasPaths.contains(leaf) && !refused("viewsheet", leaf)) {
            unclassified.add(leaf);
         }
      }

      assertTrue(unclassified.isEmpty(),
                 "writable leaves neither aliased nor refused as dead: " + unclassified);

      for(String live : List.of("name", "visible", "primary", "top", "left")) {
         assertFalse(refused("viewsheet", PropertyAliases.forType("viewsheet").aliases().get(live)),
                     live);
      }
   }

   @Test
   void sizePositionPathMatchesBothAPrefixedAndARootPane() {
      assertTrue(PropertyAliases.isSizePositionPath("sizePositionPaneModel.top", "top"));
      assertTrue(PropertyAliases.isSizePositionPath("x.sizePositionPaneModel.top", "top"));
      assertFalse(PropertyAliases.isSizePositionPath("xsizePositionPaneModel.top", "top"));
      assertFalse(PropertyAliases.isSizePositionPath("sizePositionPaneModel.topX", "top"));
      assertFalse(PropertyAliases.isSizePositionPath("sizePositionPaneModel.left", "top"));
   }

   // ── #78480: a source word on a selection type names set_selection_source ──

   @Test
   void aSelectionSourceWordNamesSetSelectionSourceInsteadOfARawPath() {
      for(String[] typeAndKey : new String[][] {
         {"selectionlist", "additionalTables"}, {"selectiontree", "table"},
         {"timeslider", "composite"}, {"calendar", "selectedColumn"}})
      {
         IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
            () -> PropertyAliases.resolveForWrite(typeAndKey[0], typeAndKey[1]));

         assertTrue(thrown.getMessage().contains("set_selection_source"), thrown.getMessage());
         assertFalse(thrown.getMessage().contains("raw model path"), thrown.getMessage());
      }
   }

   @Test
   void otherUnknownKeysStillAdvertiseTheRawPath() {
      IllegalArgumentException list = assertThrows(IllegalArgumentException.class,
         () -> PropertyAliases.resolveForWrite("selectionlist", "noSuchThing"));
      assertTrue(list.getMessage().contains("raw model path"), list.getMessage());

      // "table" is only a source word on the four selection types
      IllegalArgumentException text = assertThrows(IllegalArgumentException.class,
         () -> PropertyAliases.resolveForWrite("text", "additionalTables"));
      assertFalse(text.getMessage().contains("set_selection_source"), text.getMessage());
   }

   // ── Redmine #79233: selection short names that only existed as raw dotted paths ──────────

   /**
    * formula/showText/showBar were aliased on SelectionList only, and cellHeight/titleHeight
    * on neither, although all of them are applied on save and writable through the raw path.
    * Each alias must land on exactly the raw path the Composer dialog model carries.
    */
   @Test
   void selectionAssembliesExposeTheMeasureAndSizeShortNames() {
      String size = "selectionGeneralPaneModel.sizePositionPaneModel.";

      for(String type : List.of("selectionlist", "selectiontree")) {
         assertEquals(size + "cellHeight", PropertyAliases.resolveForWrite(type, "cellHeight"),
                      type);
         assertEquals(size + "titleHeight", PropertyAliases.resolveForWrite(type, "titleHeight"),
                      type);
      }

      String treeMeasure = "selectionTreePaneModel.selectionMeasurePaneModel.";
      String listMeasure = "selectionListPaneModel.selectionMeasurePaneModel.";

      for(String field : List.of("formula", "showText", "showBar")) {
         assertEquals(treeMeasure + field,
                      PropertyAliases.resolveForWrite("selectiontree", field));
         assertEquals(listMeasure + field,
                      PropertyAliases.resolveForWrite("selectionlist", field));
      }

      assertEquals("calendarGeneralPaneModel.sizePositionPaneModel.titleHeight",
                   PropertyAliases.resolveForWrite("calendar", "titleHeight"));
      assertEquals("selectionContainerGeneralPaneModel.sizePositionPaneModel.titleHeight",
                   PropertyAliases.resolveForWrite("selectioncontainer", "titleHeight"));
   }

   /** titleHeight is only offered where it is applied, not on every sizePosition type. */
   @Test
   void titleHeightIsNotAliasedOnTypesThatDoNotApplyIt() {
      for(String type : List.of("gauge", "text", "image", "slider", "spinner", "textinput")) {
         assertFalse(PropertyAliases.forType(type).aliases().containsKey("titleHeight"), type);
      }
   }

   // ── bug #80464: aliases for Form (input) and Output (data output) pane fields ─────────────
   //
   // The cause axis is the assembly TYPE each alias is registered on. Each alias below is
   // registered exactly on the types where the Composer dialog shows the control AND the type's
   // apply method (VSInputService / Text|Gauge|ImagePropertyDialogService) reads the field back,
   // so no DEAD_FIELDS entry is needed. The table is the specification: the test fails both when
   // an alias is missing from a listed type and when it leaks onto a type that is not listed.

   /** The nine input/output types the bug is about -- the coverage assertion's universe. */
   private static final List<String> FORM_OUTPUT_TYPES = List.of(
      "gauge", "text", "image", "checkbox", "combobox", "radiobutton", "slider", "spinner",
      "textinput");

   private record AliasCase(String alias, Object value, Map<String, String> pathByType) {
   }

   private static Map<String, String> paths(String... typeAndPath) {
      Map<String, String> map = new java.util.LinkedHashMap<>();

      for(int i = 0; i < typeAndPath.length; i += 2) {
         map.put(typeAndPath[i], typeAndPath[i + 1]);
      }

      return map;
   }

   private static final String CHECKBOX = "checkboxGeneralPaneModel";
   private static final String COMBOBOX = "comboboxGeneralPaneModel";
   private static final String RADIO = "radioButtonGeneralPaneModel";
   private static final String LIST_EDITOR = ".listValuesPaneModel.comboBoxEditorModel.dataType";

   private static final List<AliasCase> FORM_OUTPUT_ALIASES = List.of(
      // size pane: Title Height / Cell Height render only for the two titled list inputs
      new AliasCase("titleHeight", 37, paths(
         "checkbox", CHECKBOX + ".sizePositionPaneModel.titleHeight",
         "radiobutton", RADIO + ".sizePositionPaneModel.titleHeight")),
      new AliasCase("cellHeight", 31, paths(
         "checkbox", CHECKBOX + ".sizePositionPaneModel.cellHeight",
         "radiobutton", RADIO + ".sizePositionPaneModel.cellHeight")),
      // data output pane: gauge, text, image
      new AliasCase("with", "Col2", paths(
         "gauge", "dataOutputPaneModel.with", "text", "dataOutputPaneModel.with",
         "image", "dataOutputPaneModel.with")),
      new AliasCase("nthOperand", "3", paths(
         "gauge", "dataOutputPaneModel.num", "text", "dataOutputPaneModel.num",
         "image", "dataOutputPaneModel.num")),
      new AliasCase("magnitude", 1000, paths(
         "gauge", "dataOutputPaneModel.magnitude", "text", "dataOutputPaneModel.magnitude",
         "image", "dataOutputPaneModel.magnitude")),
      // pop pane: text already had all three (bug #76929); image gains them
      new AliasCase("popComponent", "Pop1", paths(
         "text", "textGeneralPaneModel.popComponent",
         "image", "imageAdvancedPaneModel.popComponent")),
      new AliasCase("popLocation", PopLocation.CENTER, paths(
         "text", "textGeneralPaneModel.popLocation",
         "image", "imageAdvancedPaneModel.popLocation")),
      new AliasCase("alpha", "40", paths(
         "text", "textGeneralPaneModel.alpha", "image", "imageAdvancedPaneModel.alpha")),
      // input label pane: hosted by combobox, slider, spinner and text input only
      new AliasCase("labelPosition", "top", paths(
         "combobox", "inputLabelPaneModel.labelPosition",
         "slider", "inputLabelPaneModel.labelPosition",
         "spinner", "inputLabelPaneModel.labelPosition",
         "textinput", "inputLabelPaneModel.labelPosition")),
      new AliasCase("labelGap", 12, paths(
         "combobox", "inputLabelPaneModel.labelGap", "slider", "inputLabelPaneModel.labelGap",
         "spinner", "inputLabelPaneModel.labelGap",
         "textinput", "inputLabelPaneModel.labelGap")),
      // data input pane: Write Back to Storage is hidden (and refused, #78579) for the check box
      new AliasCase("writeBackDirectly", true, paths(
         "textinput", "dataInputPaneModel.writeBackDirectly",
         "spinner", "dataInputPaneModel.writeBackDirectly",
         "slider", "dataInputPaneModel.writeBackDirectly",
         "radiobutton", "dataInputPaneModel.writeBackDirectly",
         "combobox", "dataInputPaneModel.writeBackDirectly")),
      // the check box is excluded: its Target tree is variables-only (VSInputService onlyVars),
      // so the alias waits for a variables-only guard
      new AliasCase("writeBackTable", "T1", paths(
         "radiobutton", "dataInputPaneModel.table", "combobox", "dataInputPaneModel.table")),
      new AliasCase("writeBackColumn", "c1", paths(
         "radiobutton", "dataInputPaneModel.columnValue",
         "combobox", "dataInputPaneModel.columnValue")),
      new AliasCase("writeBackRow", "r1", paths(
         "radiobutton", "dataInputPaneModel.rowValue",
         "combobox", "dataInputPaneModel.rowValue")),
      // text input general pane; "Placeholder Text" is bound to the model's toolTip
      new AliasCase("placeholderText", "hint", paths(
         "textinput", "textInputGeneralPaneModel.toolTip")),
      new AliasCase("defaultText", "dflt", paths(
         "textinput", "textInputGeneralPaneModel.defaultText")),
      new AliasCase("insetStyle", true, paths(
         "textinput", "textInputGeneralPaneModel.insetStyle")),
      // list values pane
      new AliasCase("dataType", "integer", paths(
         "checkbox", CHECKBOX + LIST_EDITOR, "combobox", COMBOBOX + LIST_EDITOR,
         "radiobutton", RADIO + LIST_EDITOR)),
      new AliasCase("sortType", 1, paths(
         "checkbox", CHECKBOX + ".listValuesPaneModel.sortType",
         "combobox", COMBOBOX + ".listValuesPaneModel.sortType",
         "radiobutton", RADIO + ".listValuesPaneModel.sortType")),
      new AliasCase("embeddedDataDown", true, paths(
         "checkbox", CHECKBOX + ".listValuesPaneModel.embeddedDataDown",
         "combobox", COMBOBOX + ".listValuesPaneModel.embeddedDataDown",
         "radiobutton", RADIO + ".listValuesPaneModel.embeddedDataDown")),
      // Select First Item: shown by list-values-pane only for the check box, applied only by
      // the check box setter -- never in the shared listInput() set
      new AliasCase("selectFirstItem", true, paths(
         "checkbox", CHECKBOX + ".listValuesPaneModel.selectFirstItem")),
      // ComboBox-only (PR2): the other types refuse the same raw paths, see
      // comboBoxOnlyFieldsAreRefusedOnTheOtherInputAndOutputTypes
      new AliasCase("editable", true, paths(
         "combobox", COMBOBOX + ".generalPropPaneModel.basicGeneralPaneModel.editable")),
      new AliasCase("queryDateFormat", true, paths(
         "combobox", "dataInputPaneModel.queryDateFormat")),
      new AliasCase("dateFormatPattern", "dd.MM.yyyy", paths(
         "combobox", "dataInputPaneModel.dateFormatPattern")),
      new AliasCase("calendar", true, paths(
         "combobox", COMBOBOX + ".listValuesPaneModel.comboBoxEditorModel.calendar")),
      new AliasCase("serverTZ", true, paths(
         "combobox", COMBOBOX + ".listValuesPaneModel.comboBoxEditorModel.serverTZ")),
      new AliasCase("minDate", "2020-01-01", paths(
         "combobox", COMBOBOX + ".listValuesPaneModel.comboBoxEditorModel.minDate")),
      new AliasCase("maxDate", "2021-01-01", paths(
         "combobox", COMBOBOX + ".listValuesPaneModel.comboBoxEditorModel.maxDate")),
      new AliasCase("defaultValue", "2020-06-01", paths(
         "combobox", COMBOBOX + ".listValuesPaneModel.comboBoxEditorModel.defaultValue")));

   /** Alias -> raw-path equality, on every registered type, through both resolvers. */
   @Test
   void formAndOutputAliasesResolveToTheirRawPath() {
      int checked = 0;

      for(AliasCase aliasCase : FORM_OUTPUT_ALIASES) {
         for(Map.Entry<String, String> entry : aliasCase.pathByType().entrySet()) {
            String type = entry.getKey();
            assertEquals(entry.getValue(), PropertyAliases.resolve(type, aliasCase.alias()),
                         type + " " + aliasCase.alias());
            assertEquals(entry.getValue(),
                         PropertyAliases.resolveForWrite(type, aliasCase.alias()),
                         type + " " + aliasCase.alias() + " (write)");
            assertDoesNotThrow(() -> PropertyAliases.resolveForWrite(type, entry.getValue()),
                               type + " raw " + entry.getValue() + " must stay writable");
            assertDoesNotThrow(() -> PropertyPath.typeOf(
               PropertyAliases.forType(type).modelClass(), entry.getValue()), entry.getValue());
            checked++;
         }
      }

      assertEquals(59, checked);
   }

   /**
    * Registered-type coverage: an alias is on exactly the listed types among the nine input and
    * output types -- present where the UI shows the option and Java applies it, absent
    * everywhere else (where a short name would advertise a no-op).
    */
   @Test
   void formAndOutputAliasesAreRegisteredOnExactlyTheListedTypes() {
      for(AliasCase aliasCase : FORM_OUTPUT_ALIASES) {
         for(String type : FORM_OUTPUT_TYPES) {
            assertEquals(aliasCase.pathByType().containsKey(type),
                         PropertyAliases.forType(type).aliases().containsKey(aliasCase.alias()),
                         "'" + aliasCase.alias() + "' registered on " + type);
         }
      }
   }

   /**
    * Each alias path is writable on the real dialog-model shape and reads back what was
    * written, the way {@code set_assembly_properties} followed by {@code
    * get_assembly_properties(raw: true)} sees it. Intermediate panes the composer service
    * populates on every read are populated here the same way.
    */
   @Test
   void formAndOutputAliasesRoundTripOnTheRealModelShapes() throws Exception {
      for(AliasCase aliasCase : FORM_OUTPUT_ALIASES) {
         for(Map.Entry<String, String> entry : aliasCase.pathByType().entrySet()) {
            Class<?> modelClass = PropertyAliases.forType(entry.getKey()).modelClass();
            Object model = modelClass == ImagePropertyDialogModel.class
               ? ImagePropertyDialogModel.builder().build()
               : modelClass.getDeclaredConstructor().newInstance();
            String path = PropertyAliases.resolveForWrite(entry.getKey(), aliasCase.alias());
            populateIntermediatePanes(model, path);
            Object written = PropertyPath.set(model, path, aliasCase.value());

            assertEquals(aliasCase.value(), PropertyPath.get(written, path),
                         entry.getKey() + " " + aliasCase.alias());
         }
      }
   }

   private static void populateIntermediatePanes(Object root, String path) throws Exception {
      String[] segments = path.split("\\.");
      Object current = root;

      for(int i = 0; i < segments.length - 1; i++) {
         Object child = PropertyPath.get(current, segments[i]);

         if(child == null) {
            Class<?> type = PropertyPath.typeOf(current.getClass(), segments[i]);
            child = type.getDeclaredConstructor().newInstance();
            PropertyPath.set(current, segments[i], child);
         }

         current = child;
      }
   }

   /**
    * The check box has no write-back Column, Row or Write Back to Storage (hidden by the dialog,
    * refused by bug #78579's guard), and its Target tree is variables-only
    * (VSInputService.java:592), so a plain table name written through {@code writeBackTable}
    * would be a dead write. None of the four is advertised on it until the variables-only guard
    * lands (bug #80464 PR3).
    */
   @Test
   void checkboxAdvertisesNoWriteBackAlias() {
      Map<String, String> aliases = PropertyAliases.forType("checkbox").aliases();

      for(String dead : List.of("writeBackTable", "writeBackColumn", "writeBackRow",
                                "writeBackDirectly", "columnValue", "rowValue"))
      {
         assertFalse(aliases.containsKey(dead), dead);
      }
   }

   /**
    * Names deliberately NOT registered: {@code noDefault} is never read by VSInputService (the
    * Composer just clears the default value), and the rest belong to later parts of bug #80464
    * (combobox-only fields need DEAD_FIELDS entries; multiLine, inputControl (an unrecognised
    * type is silently dropped) and the static items need new validation). Registering one here would be the "exposes but does nothing" trap or would
    * ship before its guard.
    */
   @Test
   void deferredOrDeadNamesAreNotRegisteredOnAnyInputOrOutputType() {
      for(String type : FORM_OUTPUT_TYPES) {
         for(String name : List.of("noDefault", "multiLine", "inputControl", "staticLabels",
                                   "staticValues"))
         {
            assertFalse(PropertyAliases.forType(type).aliases().containsKey(name),
                        name + " on " + type);
         }
      }
   }

   /** The raw paths the new aliases name are still live where they are registered. */
   @Test
   void theNewAliasPathsAreNotRefusedAsDeadOnTheirRegisteredTypes() {
      for(AliasCase aliasCase : FORM_OUTPUT_ALIASES) {
         for(Map.Entry<String, String> entry : aliasCase.pathByType().entrySet()) {
            assertFalse(refused(entry.getKey(), entry.getValue()),
                        entry.getKey() + " " + entry.getValue());
         }
      }
   }

   /**
    * The new aliases land on paths that existing guards key on, so those guards fire for the
    * short name too (they are judged on the resolved path, not the caller's key). The labelGap
    * range check and the list sortType domain are driven here directly; the Image popComponent
    * reference check is pinned by its registered suffix, because it needs a live viewsheet to
    * run end to end.
    */
   @Test
   void existingGuardsFireThroughTheNewAliases() throws Exception {
      for(String type : List.of("combobox", "slider", "spinner", "textinput")) {
         String path = PropertyAliases.resolveForWrite(type, "labelGap");
         assertThrows(IllegalArgumentException.class,
                      () -> AssemblyPropertyService.requireLabelGapInRange("labelGap", path, 5000),
                      type);
         assertDoesNotThrow(
            () -> AssemblyPropertyService.requireLabelGapInRange("labelGap", path, 12), type);
      }

      Method canonical = AssemblyPropertyService.class.getDeclaredMethod(
         "canonicalIntEnum", String.class, String.class, Object.class);
      canonical.setAccessible(true);

      for(String type : List.of("checkbox", "combobox", "radiobutton")) {
         String path = PropertyAliases.resolveForWrite(type, "sortType");
         assertEquals(2, canonical.invoke(null, type, path, "descending"), type);
         java.lang.reflect.InvocationTargetException thrown = assertThrows(
            java.lang.reflect.InvocationTargetException.class,
            () -> canonical.invoke(null, type, path, "bogus"), type);
         assertInstanceOf(IllegalArgumentException.class, thrown.getCause(), type);
      }

      java.lang.reflect.Field fields =
         AssemblyPropertyService.class.getDeclaredField("REFERENCE_FIELDS");
      fields.setAccessible(true);
      String imagePop = PropertyAliases.resolveForWrite("image", "popComponent");
      boolean covered = false;

      for(Object field : (List<?>) fields.get(null)) {
         Method suffix = field.getClass().getDeclaredMethod("suffix");
         suffix.setAccessible(true);
         covered |= imagePop.endsWith((String) suffix.invoke(field));
      }

      assertTrue(covered, imagePop);
   }

   private static final String CB_EDITOR = ".listValuesPaneModel.comboBoxEditorModel.";

   /** type -> the raw ComboBox-only paths that type does not apply, in that type's own shape. */
   private static Map<String, List<String>> comboBoxOnlyPaths() {
      Map<String, List<String>> map = new java.util.LinkedHashMap<>();
      String[] editor = {"calendar", "serverTZ", "minDate", "maxDate", "defaultValue"};

      for(String type : List.of("checkbox", "radiobutton")) {
         String prefix = type.equals("checkbox") ? CHECKBOX : RADIO;
         List<String> paths = new ArrayList<>(List.of(
            prefix + ".generalPropPaneModel.basicGeneralPaneModel.editable",
            "dataInputPaneModel.queryDateFormat", "dataInputPaneModel.dateFormatPattern"));

         for(String field : editor) {
            paths.add(prefix + CB_EDITOR + field);
         }

         map.put(type, paths);
      }

      for(Map.Entry<String, String> entry : Map.of(
         "spinner", "spinnerGeneralPaneModel", "textinput", "textInputGeneralPaneModel",
         "slider", "sliderGeneralPaneModel").entrySet())
      {
         map.put(entry.getKey(), List.of(
            entry.getValue() + ".generalPropPaneModel.basicGeneralPaneModel.editable",
            "dataInputPaneModel.queryDateFormat", "dataInputPaneModel.dateFormatPattern"));
      }

      for(String type : List.of("gauge", "text", "image")) {
         // derived from the registry (the output family nests outputGeneralPaneModel), not typed
         String enabled = PropertyAliases.resolve(type, "enabled");
         String editable = enabled.substring(0, enabled.length() - "enabled".length()) +
            "basicGeneralPaneModel.editable";
         assertDoesNotThrow(() -> PropertyPath.typeOf(
            PropertyAliases.forType(type).modelClass(), editable), editable);
         map.put(type, List.of(editable));
      }

      return map;
   }

   /**
    * Editable, the date-format pair and the calendar editor fields are applied by the ComboBox
    * setters only, so every other input/output type refuses the raw path with the read-only
    * message (it used to report success and change nothing). The ComboBox accepts them all.
    */
   @Test
   void comboBoxOnlyFieldsAreRefusedOnTheOtherInputAndOutputTypes() {
      int checked = 0;

      for(Map.Entry<String, List<String>> entry : comboBoxOnlyPaths().entrySet()) {
         for(String path : entry.getValue()) {
            String message = refusal(entry.getKey(), path);
            assertTrue(message.contains("is read-only on " + entry.getKey()) &&
                       message.contains("ComboBox only"),
                       entry.getKey() + " " + path + ": " + message);
            checked++;
         }
      }

      assertEquals(2 * 8 + 3 * 3 + 3, checked);

      for(String field : List.of("calendar", "serverTZ", "minDate", "maxDate", "defaultValue")) {
         assertDoesNotThrow(() -> PropertyAliases.resolveForWrite("combobox", field), field);
         assertDoesNotThrow(() -> PropertyAliases.resolveForWrite(
            "combobox", COMBOBOX + CB_EDITOR + field), field);
      }

      assertDoesNotThrow(() -> PropertyAliases.resolveForWrite("combobox", "editable"));
   }

   /** Select First Item is a CheckBox-only write: refused on RadioButton and ComboBox. */
   @Test
   void selectFirstItemIsRefusedOnRadioButtonAndComboBoxButNotCheckBox() {
      for(String type : List.of("radiobutton", "combobox")) {
         String prefix = type.equals("radiobutton") ? RADIO : COMBOBOX;
         String message = refusal(type, prefix + ".listValuesPaneModel.selectFirstItem");
         assertTrue(message.contains("is read-only on " + type) &&
                    message.contains("CheckBox only"), message);
      }

      assertDoesNotThrow(() -> PropertyAliases.resolveForWrite("checkbox", "selectFirstItem"));
   }

   /** Editable resolves to the live editable flag, never to the misnamed ...enabled leftover. */
   @Test
   void editableAliasIsTheEditableFlagNotTheMisnamedEnabledOne() {
      String path = PropertyAliases.resolve("combobox", "editable");
      assertTrue(path.endsWith("basicGeneralPaneModel.editable"), path);
      assertFalse(PropertyAliases.resolve("combobox", "enabled")
                     .contains("basicGeneralPaneModel"));
   }

   /**
    * titleHeight is read back by an explicit set of types; on any other type that registers the
    * size pane a raw write is refused instead of being a silent no-op, over every size-pane type.
    */
   @Test
   void rawTitleHeightIsRefusedExactlyOnTypesThatIgnoreIt() {
      int refusedCount = 0;

      for(String type : SIZE_POSITION_TYPES) {
         String path = sizePositionPath(type, "titleHeight");

         if(path == null) {
            continue;
         }

         boolean live = SIZE_POSITION_LIVE.get("titleHeight").contains(type);
         assertEquals(!live, refused(type, path), type + " " + path);
         refusedCount += live ? 0 : 1;
      }

      assertTrue(refusedCount >= 10, "refused " + refusedCount);

      for(String type : List.of("combobox", "spinner", "textinput", "gauge", "text", "image")) {
         assertTrue(refusal(type, sizePositionPath(type, "titleHeight"))
                       .contains("is read-only on " + type), type);
      }

      for(String type : List.of("checkbox", "radiobutton")) {
         assertDoesNotThrow(() -> PropertyAliases.resolveForWrite(type, "titleHeight"), type);
      }
   }

   /**
    * The plugin pre-validates the bare key {@code labelPosition} (propertyTools.ts), so the
    * server alias must carry exactly that name and the Composer's four positions.
    */
   @Test
   void labelPositionAliasNameMatchesTheKeyThePluginPrevalidates() {
      for(String type : List.of("combobox", "slider", "spinner", "textinput")) {
         assertEquals("inputLabelPaneModel.labelPosition",
                      PropertyAliases.resolve(type, "labelPosition"), type);
      }

      for(String type : List.of("checkbox", "radiobutton", "gauge", "text", "image")) {
         assertFalse(PropertyAliases.forType(type).aliases().containsKey("labelPosition"), type);
      }
   }
}
