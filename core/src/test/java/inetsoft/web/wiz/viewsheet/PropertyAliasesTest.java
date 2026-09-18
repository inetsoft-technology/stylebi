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

import inetsoft.web.composer.model.vs.GaugePropertyDialogModel;
import inetsoft.web.composer.model.vs.ImagePropertyDialogModel;
import inetsoft.web.composer.model.vs.TextPropertyDialogModel;
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

   /**
    * {@code refresh} is real for the input assemblies/submit (aliased through the same shared
    * {@code basicGeneral()} helper) but dead for these four -- their apply methods never read
    * {@code basicGeneralPaneModel.refresh} back.
    */
   @Test
   void refusesRefreshOnlyOnTheTypesWhereItIsDead() {
      for(String type : java.util.List.of("table", "crosstab", "text", "selectionlist")) {
         assertTrue(PropertyAliases.forType(type).aliases().containsKey("refresh"),
                    type + " should still list 'refresh' as readable");
         assertThrows(IllegalArgumentException.class,
                      () -> PropertyAliases.resolveForWrite(type, "refresh"),
                      "refresh has no effect on write for " + type);
      }

      assertEquals("comboboxGeneralPaneModel.generalPropPaneModel.basicGeneralPaneModel.refresh",
                   PropertyAliases.resolveForWrite("combobox", "refresh"),
                   "refresh is real for combobox and must stay writable");
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
}
