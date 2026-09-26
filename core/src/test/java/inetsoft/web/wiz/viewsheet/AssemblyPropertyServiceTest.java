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

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.test.*;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.DefaultVariableAssembly;
import inetsoft.uql.asset.EmbeddedTableAssembly;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.uql.viewsheet.*;
import inetsoft.web.composer.model.TreeNodeModel;
import inetsoft.web.composer.model.vs.CalcTablePropertyDialogModel;
import inetsoft.web.composer.model.vs.CalendarPropertyDialogModel;
import inetsoft.web.composer.model.vs.CheckboxPropertyDialogModel;
import inetsoft.web.composer.model.vs.ChartPropertyDialogModel;
import inetsoft.web.composer.model.vs.ComboboxPropertyDialogModel;
import inetsoft.web.composer.model.vs.DynamicValueModel;
import inetsoft.web.composer.model.vs.GaugePropertyDialogModel;
import inetsoft.web.composer.model.vs.GroupContainerPropertyDialogModel;
import inetsoft.web.composer.model.vs.ImageGeneralPaneModel;
import inetsoft.web.composer.model.vs.ImagePreviewPaneModel;
import inetsoft.web.composer.model.vs.ImagePropertyDialogModel;
import inetsoft.web.composer.model.vs.StaticImagePaneModel;
import inetsoft.web.composer.model.vs.RadioButtonPropertyDialogModel;
import inetsoft.web.composer.model.vs.RangeSliderPropertyDialogModel;
import inetsoft.web.composer.model.vs.SelectionListPropertyDialogModel;
import inetsoft.web.composer.model.vs.SelectionTreePropertyDialogModel;
import inetsoft.web.composer.model.vs.TableViewPropertyDialogModel;
import inetsoft.web.composer.model.vs.TextInputPropertyDialogModel;
import inetsoft.web.composer.model.vs.TipCustomizeDialogModel;
import inetsoft.web.composer.vs.dialog.*;
import inetsoft.uql.viewsheet.internal.ImageVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.SelectionTreeVSAssemblyInfo;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// gauge's "face" alias (parity audit L7) reads GaugeGeneralPaneModel.facePaneModel, whose lazy
// construction runs VSGauge.getPrefixIDs() -> VSFaceUtil's static init -> a Spring-bean lookup.
// listsTheAliasVocabularyWithCurrentValues below reads every declared alias's current value, so
// this class needs the same running context TableViewPropertyDialogServiceTest and friends set
// up for the same reason -- a bare Mockito-only test does not have a Spring context for that
// static init to find.
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome()
@Tag("core")
class AssemblyPropertyServiceTest {
   /**
    * <b>The binding guard.</b> Every binding names its two methods explicitly, because the
    * convention they look like they follow does not hold — {@code setChartPropertyModel} has no
    * "Dialog", {@code getTableViewPropertyDialogModel} has a "View" its setter does not, and the
    * selection services drop "Dialog" from both. This resolves every declared name reflectively,
    * so a composer rename fails the build rather than the first live call.
    */
   @Test
   void everyDeclaredMethodNameResolvesOnItsService() {
      AssemblyPropertyService service = serviceWith(mock(GaugeVSAssembly.class), null);

      for(Map.Entry<String, AssemblyPropertyService.Binding> wired :
          service.wiredBindings().entrySet())
      {
         AssemblyPropertyService.Binding binding = wired.getValue();

         assertNotNull(
            AssemblyPropertyService.method(binding.service(), binding.getter(),
                                           binding.getterArity()),
            wired.getKey() + "'s service has no " + binding.getterArity() + "-argument " +
            binding.getter());
         assertNotNull(
            AssemblyPropertyService.method(binding.service(), binding.setter(), 6),
            wired.getKey() + "'s service has no 6-argument " + binding.setter());
      }
   }

   /**
    * The names really are irregular. If someone later "tidies" the bindings by deriving them
    * from the type, this fails and says why.
    */
   @Test
   void theMethodNamesAreNotDerivableFromTheAssemblyType() {
      AssemblyPropertyService service = serviceWith(mock(GaugeVSAssembly.class), null);

      assertEquals("setChartPropertyModel", service.bindingFor("chart").setter(),
                   "chart's setter has no 'Dialog' — do not derive these names");
      assertEquals("getTableViewPropertyDialogModel", service.bindingFor("table").getter(),
                   "table's getter says TableView while its setter says Table");
      assertEquals("getSelectionListPropertyModel",
                   service.bindingFor("selectionlist").getter(),
                   "the selection services drop 'Dialog' from both names");
   }

   /** Calc table's getter takes a scroll offset, so the arity is not uniform either. */
   @Test
   void carriesTheExtraGetterArgumentCalcTableNeeds() {
      AssemblyPropertyService service = serviceWith(mock(GaugeVSAssembly.class), null);

      assertEquals(4, service.bindingFor("calctable").getterArity());
      assertEquals(3, service.bindingFor("gauge").getterArity());
   }

   /**
    * Every <b>assembly</b> type in the registry must resolve to a wired dialog service here, so
    * a covered-but-unwired type never fails at runtime instead of at the build.
    *
    * <p>{@code sheet} is deliberately excluded: it is the one covered type that names no
    * assembly at all. {@code ViewsheetPropertyDialogService.getViewsheetInfo}/
    * {@code setViewsheetInfo} take no assembly name and order their arguments differently from
    * every assembly dialog service's shared {@code (runtimeId, objectId, ...)} shape, so it is
    * not — and should never become — one more entry in this reflective dispatch table. It is
    * wired directly into {@link SheetPropertyService} instead.
    */
   @Test
   void everyCoveredAssemblyTypeHasAWiredService() {
      AssemblyPropertyService service = serviceWith(mock(GaugeVSAssembly.class), null);

      for(String type : PropertyAliases.coveredTypes()) {
         if(type.equals(PropertyAliases.SHEET)) {
            continue;
         }

         assertDoesNotThrow(() -> service.bindingFor(type),
                            "'" + type + "' has aliases but no property service wired, so " +
                            "every call for it would fail at runtime");
      }
   }

   /** The flip side of the exclusion above: the sheet must NOT be reachable this way. */
   @Test
   void viewsheetIsNotWiredIntoTheAssemblyDispatchTable() {
      AssemblyPropertyService service = serviceWith(mock(GaugeVSAssembly.class), null);

      assertThrows(Exception.class, () -> service.bindingFor("viewsheet"));
   }

   @Test
   void listsTheAliasVocabularyWithCurrentValues() throws Exception {
      GaugePropertyDialogModel model = new GaugePropertyDialogModel();
      AssemblyPropertyService service = serviceWith(mock(GaugeVSAssembly.class), model);

      Map<String, Object> listed = service.list("tok", principal(), "Gauge1");

      assertEquals("gauge", listed.get("assemblyType"));
      assertNotNull(listed.get("properties"));
   }

   /**
    * Bug #76809 (VTB-017): {@code primary} was already present, unfiltered, and settable in
    * {@code list_assembly_properties}'s output the whole time -- the actual defect was that no
    * entry told a caller that this is the Composer UI's "Visible in External Viewsheets" checkbox.
    * Every entry now carries a {@code label} field, null for the ordinary aliases that have no
    * better caption than their own name, and the Composer's own caption for {@code primary}.
    */
   @Test
   @SuppressWarnings("unchecked")
   void listsThePrimaryLabelAlongsideItsValue() throws Exception {
      GaugePropertyDialogModel model = new GaugePropertyDialogModel();
      AssemblyPropertyService service = serviceWith(mock(GaugeVSAssembly.class), model);

      Map<String, Object> listed = service.list("tok", principal(), "Gauge1");
      java.util.List<Map<String, Object>> properties =
         (java.util.List<Map<String, Object>>) listed.get("properties");

      Map<String, Object> primary = properties.stream()
         .filter(p -> "primary".equals(p.get("name")))
         .findFirst()
         .orElseThrow(() -> new AssertionError("gauge should list a 'primary' entry"));
      assertEquals("Visible in External Viewsheets", primary.get("label"));

      Map<String, Object> visible = properties.stream()
         .filter(p -> "visible".equals(p.get("name")))
         .findFirst()
         .orElseThrow(() -> new AssertionError("gauge should list a 'visible' entry"));
      assertNull(visible.get("label"),
                 "an ordinary alias with no better caption than its own name has no label");
   }

   @Test
   void refusesAnUnknownAssembly() {
      AssemblyPropertyService service = serviceWith(null, null);

      Exception thrown = assertThrows(
         Exception.class, () -> service.list("tok", principal(), "Nope"));

      assertTrue(thrown.getMessage().contains("Nope"));
   }

   /**
    * An uncovered type must say so, not fail obscurely.
    *
    * <p>This used to use <b>image</b>, which was uncovered "by necessity rather than backlog"
    * because its dialog model is Immutables. That necessity is gone: PropertyPath now reads bare
    * Immutables accessors and rebuilds immutable levels through {@code withX}, so image is wired
    * and covered. An assembly type genuinely outside the registry is used instead.
    */
   @Test
   void refusesAnUncoveredAssemblyTypeNamingWhatIsCovered() {
      AssemblyPropertyService service = serviceWith(mock(AnnotationVSAssembly.class), null);

      Exception thrown = assertThrows(
         Exception.class, () -> service.list("tok", principal(), "Annotation1"));

      assertTrue(thrown.getMessage().contains("gauge"), "name what is covered");
   }

   /** Image is covered now — the Immutables write path is what made it reachable. */
   @Test
   void coversTheImageAssembly() {
      assertTrue(PropertyAliases.covers("image"));
   }

   @Test
   void refusesAnEmptyPatchRatherThanOpeningACheckpointForNothing() {
      AssemblyPropertyService service = serviceWith(mock(GaugeVSAssembly.class), null);

      assertThrows(Exception.class,
                   () -> service.set("tok", principal(), "Gauge1", Map.of(), ""));
   }

   /**
    * A typo in the fourth key must not leave the first three applied — a partial edit the
    * caller cannot detect from the error alone.
    */
   @Test
   void appliesNothingWhenAnyKeyInThePatchIsBad() throws Exception {
      GaugePropertyDialogModel model = new GaugePropertyDialogModel();
      AssemblyPropertyService service = serviceWith(mock(GaugeVSAssembly.class), model);
      Map<String, Object> patch = new LinkedHashMap<>();
      patch.put("max", "100");
      patch.put("nonsense", "x");

      assertThrows(Exception.class,
                   () -> service.set("tok", principal(), "Gauge1", patch, ""));

      assertNull(model.getGaugeGeneralPaneModel() == null
                    ? null : model.getGaugeGeneralPaneModel().getNumberRangePaneModel().getMax(),
                 "the valid key must not have been written before the bad one was found");
   }

   /**
    * The direct regression test for the reported defect: set_assembly_properties(Submit1,
    * {shadow: true}) used to return a fake {@code {"ok":true}} and bump the write revision, but
    * shadow was never applied -- SubmitPropertyDialogService never reads or writes it, so it
    * always read back false. Now that PropertyAliases no longer registers shadow for submit,
    * resolveForWrite's existing unknown-key refusal fires before anything is read or written.
    */
   @Test
   void refusesShadowOnAnAssemblyTypeThatDoesNotApplyIt() {
      AssemblyPropertyService service = serviceWith(mock(SubmitVSAssembly.class), null);

      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> service.set("tok", principal(), "Submit1", Map.of("shadow", true), ""));

      assertTrue(thrown.getMessage().contains("shadow"), "must name the refused key: " + thrown.getMessage());
   }

   /**
    * The reported defect: {@code rangeValues}/{@code rangeColorValues} are {@code String[]}-typed
    * and unaliased, so the raw dotted path is the only way to set them, and PropertyPath.coerce
    * had no branch for an array-typed target -- every input shape failed identically regardless
    * of value. This exercises the real nested GaugeAdvancedPaneModel/RangePaneModel path, not a
    * synthetic PropertyPathTest fixture.
    */
   @Test
   void setsGaugeRangeValuesViaRawPath() throws Exception {
      GaugePropertyDialogModel model = new GaugePropertyDialogModel();
      AssemblyPropertyService service = serviceWith(mock(GaugeVSAssembly.class), model);

      service.set("tok", principal(), "Gauge1",
                  Map.of("gaugeAdvancedPaneModel.rangePaneModel.rangeValues",
                         java.util.List.of("60", "90", "100")),
                  "");

      assertArrayEquals(
         new String[]{ "60", "90", "100" },
         model.getGaugeAdvancedPaneModel().getRangePaneModel().getRangeValues());
   }

   /**
    * VBM-006: a trailing blank {@code rangeValues} entry (e.g. {@code ["60","90","",""]}) is an
    * unambiguous "extend the last color band to the gauge's own max" request that the renderer
    * ({@code DefaultVSGauge.fillRanges0}) now resolves correctly on its own, so it must be
    * allowed to pass through silently -- no normalization needed here, and no error either.
    */
   @Test
   void allowsATrailingBlankGaugeRangeValue() throws Exception {
      GaugePropertyDialogModel model = new GaugePropertyDialogModel();
      AssemblyPropertyService service = serviceWith(mock(GaugeVSAssembly.class), model);

      assertDoesNotThrow(() -> service.set("tok", principal(), "Gauge1",
         Map.of("gaugeAdvancedPaneModel.rangePaneModel.rangeValues",
                java.util.List.of("60", "90", "", "")),
         ""));
   }

   /**
    * VBM-006: unlike a trailing blank, a blank entry followed by a later populated entry is
    * genuinely ambiguous -- the renderer has no principled way to resolve it and would silently
    * collapse that band to nothing. This must be refused loudly instead, naming the field.
    */
   @Test
   void refusesAnInteriorGapInGaugeRangeValues() {
      GaugePropertyDialogModel model = new GaugePropertyDialogModel();
      AssemblyPropertyService service = serviceWith(mock(GaugeVSAssembly.class), model);

      Exception thrown = assertThrows(IllegalArgumentException.class,
         () -> service.set("tok", principal(), "Gauge1",
            Map.of("gaugeAdvancedPaneModel.rangePaneModel.rangeValues",
                   java.util.List.of("60", "", "150")),
            ""));

      assertTrue(thrown.getMessage().contains("rangeValues[1]"),
                 "must name the blank index: " + thrown.getMessage());
   }

   /**
    * VBM-006 review round 1: the interior-gap guard must only fire for a patch that actually
    * touches {@code gaugeAdvancedPaneModel.rangePaneModel}. A gauge can already have an interior
    * {@code rangeValues} gap saved from a source the guard does not cover (the human Composer
    * GUI, or a call made before this guard existed); an unrelated later patch (here, {@code max})
    * must still succeed instead of being blocked by state it never touched.
    */
   @Test
   void ignoresAPreExistingInteriorGapWhenThePatchDoesNotTouchRanges() throws Exception {
      GaugePropertyDialogModel model = new GaugePropertyDialogModel();
      model.getGaugeAdvancedPaneModel().getRangePaneModel()
         .setRangeValues(new String[]{ "60", "", "150" });
      AssemblyPropertyService service = serviceWith(mock(GaugeVSAssembly.class), model);

      assertDoesNotThrow(() -> service.set("tok", principal(), "Gauge1",
         Map.of("max", "999"), ""));

      assertEquals("999", model.getGaugeGeneralPaneModel().getNumberRangePaneModel().getMax(),
                   "the unrelated property must still have been written");
   }

   // ── monotonicity/min/color-count (Redmine #76717, VOF-001) ────────────────
   //
   // requireNoInteriorGapInGaugeRangeValues only ever caught an interior blank gap; none of
   // these three shapes reached DefaultVSGauge.fillRanges0 rejected, they just rendered
   // plausible-but-wrong (a band silently skipped, or falling through to a default color).

   /**
    * fillRanges0's own band-skip loop compares each boundary against every earlier one
    * ({@code ranges[i] < ranges[k]}), so a later boundary lower than an earlier one silently
    * drops that band instead of erroring.
    */
   @Test
   void refusesANonMonotonicGaugeRangeValue() {
      GaugePropertyDialogModel model = new GaugePropertyDialogModel();
      AssemblyPropertyService service = serviceWith(mock(GaugeVSAssembly.class), model);

      Exception thrown = assertThrows(IllegalArgumentException.class,
         () -> service.set("tok", principal(), "Gauge1",
            Map.of("gaugeAdvancedPaneModel.rangePaneModel.rangeValues",
                   java.util.List.of("90", "60", "150")),
            ""));

      assertTrue(thrown.getMessage().contains("rangeValues[1]"),
                 "must name the offending index: " + thrown.getMessage());
   }

   @Test
   void allowsMonotonicNonDecreasingGaugeRangeValues() throws Exception {
      GaugePropertyDialogModel model = new GaugePropertyDialogModel();
      AssemblyPropertyService service = serviceWith(mock(GaugeVSAssembly.class), model);
      Map<String, Object> patch = new LinkedHashMap<>();
      patch.put("gaugeAdvancedPaneModel.rangePaneModel.rangeValues",
                java.util.List.of("20", "40", "60"));
      patch.put("gaugeAdvancedPaneModel.rangePaneModel.rangeColorValues",
                java.util.List.of("red", "yellow", "green"));

      assertDoesNotThrow(() -> service.set("tok", principal(), "Gauge1", patch, ""));
   }

   /**
    * fillRanges0 skips any boundary {@code <= info.getMin()}, so a boundary at or below the
    * gauge's own min (default 0 when unset, matching {@code RangeOutputVSAssemblyInfo.getMin()})
    * silently drops that band.
    */
   @Test
   void refusesAGaugeRangeValueAtOrBelowTheGaugesMin() {
      GaugePropertyDialogModel model = new GaugePropertyDialogModel();
      AssemblyPropertyService service = serviceWith(mock(GaugeVSAssembly.class), model);
      Map<String, Object> patch = new LinkedHashMap<>();
      patch.put("gaugeAdvancedPaneModel.rangePaneModel.rangeValues",
                java.util.List.of("-10", "60", "90"));
      patch.put("gaugeAdvancedPaneModel.rangePaneModel.rangeColorValues",
                java.util.List.of("red", "yellow", "green"));

      Exception thrown = assertThrows(IllegalArgumentException.class,
         () -> service.set("tok", principal(), "Gauge1", patch, ""));

      assertTrue(thrown.getMessage().contains("rangeValues[0]"),
                 "must name the offending index: " + thrown.getMessage());
   }

   /**
    * Confirms the check reads the gauge's own {@code min} rather than hard-coding 0 -- a
    * boundary that would fail against the default is allowed once a lower min is set in the
    * same patch.
    */
   @Test
   void allowsAGaugeRangeValueAboveACustomMin() throws Exception {
      GaugePropertyDialogModel model = new GaugePropertyDialogModel();
      AssemblyPropertyService service = serviceWith(mock(GaugeVSAssembly.class), model);
      Map<String, Object> patch = new LinkedHashMap<>();
      patch.put("min", "-20");
      patch.put("gaugeAdvancedPaneModel.rangePaneModel.rangeValues",
                java.util.List.of("-10", "10", "30"));
      patch.put("gaugeAdvancedPaneModel.rangePaneModel.rangeColorValues",
                java.util.List.of("red", "yellow", "green"));

      assertDoesNotThrow(() -> service.set("tok", principal(), "Gauge1", patch, ""));
   }

   /**
    * Scenario 3 from the report: 4 populated boundaries but only 3 populated colors.
    * RangePaneModel does not pad either array to a fixed length -- {@code set_assembly_properties}
    * writes them at exactly the caller's length -- so this must be a bounds check
    * ({@code i >= rangeColorValues.length}), not an index into an array assumed to already be
    * length 5/6; a naturally-sized 3-element array is the realistic shape, not a padded one.
    */
   @Test
   void refusesAGaugeRangeColorCountMismatch() {
      GaugePropertyDialogModel model = new GaugePropertyDialogModel();
      AssemblyPropertyService service = serviceWith(mock(GaugeVSAssembly.class), model);
      Map<String, Object> patch = new LinkedHashMap<>();
      patch.put("gaugeAdvancedPaneModel.rangePaneModel.rangeValues",
                java.util.List.of("20", "40", "60", "80"));
      patch.put("gaugeAdvancedPaneModel.rangePaneModel.rangeColorValues",
                java.util.List.of("red", "yellow", "green"));

      Exception thrown = assertThrows(IllegalArgumentException.class,
         () -> service.set("tok", principal(), "Gauge1", patch, ""));

      assertTrue(thrown.getMessage().contains("rangeColorValues[3]"),
                 "must name the missing color index: " + thrown.getMessage());
   }

   /**
    * Leaving {@code rangeColorValues} entirely unset is its own legitimate state -- every band
    * paints with {@code fillRanges0}'s default color -- not the reported defect, which is a
    * caller who has started coloring some boundaries and silently not others. The count-parity
    * check must not turn "no colors configured at all" into a forced requirement.
    */
   @Test
   void allowsGaugeRangeValuesWithNoColorsConfiguredAtAll() throws Exception {
      GaugePropertyDialogModel model = new GaugePropertyDialogModel();
      AssemblyPropertyService service = serviceWith(mock(GaugeVSAssembly.class), model);

      assertDoesNotThrow(() -> service.set("tok", principal(), "Gauge1",
         Map.of("gaugeAdvancedPaneModel.rangePaneModel.rangeValues",
                java.util.List.of("20", "40", "60")),
         ""));
   }

   @Test
   void allowsMatchingGaugeRangeValueAndColorCounts() throws Exception {
      GaugePropertyDialogModel model = new GaugePropertyDialogModel();
      AssemblyPropertyService service = serviceWith(mock(GaugeVSAssembly.class), model);
      Map<String, Object> patch = new LinkedHashMap<>();
      patch.put("gaugeAdvancedPaneModel.rangePaneModel.rangeValues",
                java.util.List.of("20", "40", "60"));
      patch.put("gaugeAdvancedPaneModel.rangePaneModel.rangeColorValues",
                java.util.List.of("red", "yellow", "green"));

      assertDoesNotThrow(() -> service.set("tok", principal(), "Gauge1", patch, ""));
   }

   /**
    * A trailing blank {@code rangeValues} entry (the VBM-006 "extend to max" shape) is one
    * boundary short of the array's own length -- the color-count check must not demand a color
    * for that implicit slot, only for the populated boundaries before it.
    */
   @Test
   void allowsATrailingAutoExtendBandWithOneFewerColorThanRangeValuesSlots() throws Exception {
      GaugePropertyDialogModel model = new GaugePropertyDialogModel();
      AssemblyPropertyService service = serviceWith(mock(GaugeVSAssembly.class), model);
      Map<String, Object> patch = new LinkedHashMap<>();
      patch.put("gaugeAdvancedPaneModel.rangePaneModel.rangeValues",
                java.util.List.of("60", "90", "100", ""));
      patch.put("gaugeAdvancedPaneModel.rangePaneModel.rangeColorValues",
                java.util.List.of("red", "yellow", "green"));

      assertDoesNotThrow(() -> service.set("tok", principal(), "Gauge1", patch, ""));
   }

   /**
    * Same guard as {@code ignoresAPreExistingInteriorGapWhenThePatchDoesNotTouchRanges}, for each
    * new check: a gauge can already have a non-monotonic/negative/count-mismatched config saved
    * from a source these checks don't cover (the human Composer GUI, or a call made before this
    * guard existed); an unrelated later patch must not be blocked by state it never touched.
    */
   @Test
   void ignoresAPreExistingNonMonotonicRangeWhenThePatchDoesNotTouchRanges() throws Exception {
      GaugePropertyDialogModel model = new GaugePropertyDialogModel();
      model.getGaugeAdvancedPaneModel().getRangePaneModel()
         .setRangeValues(new String[]{ "90", "60", "150" });
      AssemblyPropertyService service = serviceWith(mock(GaugeVSAssembly.class), model);

      assertDoesNotThrow(() -> service.set("tok", principal(), "Gauge1",
         Map.of("max", "999"), ""));
   }

   @Test
   void ignoresAPreExistingBelowMinRangeWhenThePatchDoesNotTouchRanges() throws Exception {
      GaugePropertyDialogModel model = new GaugePropertyDialogModel();
      model.getGaugeAdvancedPaneModel().getRangePaneModel()
         .setRangeValues(new String[]{ "-10", "60", "90" });
      AssemblyPropertyService service = serviceWith(mock(GaugeVSAssembly.class), model);

      assertDoesNotThrow(() -> service.set("tok", principal(), "Gauge1",
         Map.of("max", "999"), ""));
   }

   @Test
   void ignoresAPreExistingColorCountMismatchWhenThePatchDoesNotTouchRanges() throws Exception {
      GaugePropertyDialogModel model = new GaugePropertyDialogModel();
      model.getGaugeAdvancedPaneModel().getRangePaneModel()
         .setRangeValues(new String[]{ "20", "40", "60", "80" });
      model.getGaugeAdvancedPaneModel().getRangePaneModel()
         .setRangeColorValues(new String[]{ "red", "yellow", "green" });
      AssemblyPropertyService service = serviceWith(mock(GaugeVSAssembly.class), model);

      assertDoesNotThrow(() -> service.set("tok", principal(), "Gauge1",
         Map.of("max", "999"), ""));
   }

   // ── dynamic ("$(...)") rangeValues entries (Redmine #76886) ───────────────
   //
   // requireNoInteriorGapInGaugeRangeValues's numeric/monotonic/min checks unconditionally
   // called Double.parseDouble on every populated rangeValues[i], with no exemption for an
   // unresolved dynamic reference -- unlike columnValue/rowValue's own VSUtil.isDynamicValue
   // guards in the same file, and unlike the sibling targetValue property in the same
   // rangePaneModel, which has no such validator at all. The interior-gap and color-count
   // checks must keep firing regardless.

   /**
    * The exact reported repro: a dynamic reference at index 0 must be accepted, and must read
    * back unchanged rather than being resolved/mangled.
    */
   @Test
   void allowsADynamicValueGaugeRangeValue() throws Exception {
      GaugePropertyDialogModel model = new GaugePropertyDialogModel();
      AssemblyPropertyService service = serviceWith(mock(GaugeVSAssembly.class), model);

      assertDoesNotThrow(() -> service.set("tok", principal(), "Gauge1",
         Map.of("gaugeAdvancedPaneModel.rangePaneModel.rangeValues",
                java.util.List.of("$(Spinner1)", "20000000", "30000000", "", "")),
         ""));

      assertEquals("$(Spinner1)",
                   model.getGaugeAdvancedPaneModel().getRangePaneModel().getRangeValues()[0],
                   "the dynamic reference must read back unchanged");
   }

   /**
    * The monotonicity check must not attempt to compare a dynamic slot against its numeric
    * neighbors in either direction.
    */
   @Test
   void allowsADynamicValueInTheMiddleOfGaugeRangeValues() throws Exception {
      GaugePropertyDialogModel model = new GaugePropertyDialogModel();
      AssemblyPropertyService service = serviceWith(mock(GaugeVSAssembly.class), model);

      assertDoesNotThrow(() -> service.set("tok", principal(), "Gauge1",
         Map.of("gaugeAdvancedPaneModel.rangePaneModel.rangeValues",
                java.util.List.of("20", "$(Spinner1)", "60")),
         ""));
   }

   /**
    * The min-bound check must also skip a dynamic slot. With no {@code min} set (default 0,
    * same as {@code refusesAGaugeRangeValueAtOrBelowTheGaugesMin}), a naive fix that left the
    * dynamic slot's "parsed" value at Java's default {@code 0.0} would wrongly trip
    * {@code 0.0 <= 0} and throw; the exemption must skip the comparison for that slot entirely.
    */
   @Test
   void allowsADynamicValueGaugeRangeValueToSkipTheMinCheck() throws Exception {
      GaugePropertyDialogModel model = new GaugePropertyDialogModel();
      AssemblyPropertyService service = serviceWith(mock(GaugeVSAssembly.class), model);

      assertDoesNotThrow(() -> service.set("tok", principal(), "Gauge1",
         Map.of("gaugeAdvancedPaneModel.rangePaneModel.rangeValues",
                java.util.List.of("$(Spinner1)", "60", "90")),
         ""));
   }

   /**
    * The dynamic-value exemption must not weaken the structural interior-gap check, which it
    * does not own: a dynamic populated entry followed by a blank, followed by a populated one,
    * must still be refused exactly as {@code refusesAnInteriorGapInGaugeRangeValues}'s all-
    * literal case is.
    */
   @Test
   void refusesAnInteriorGapEvenWithADynamicGaugeRangeValue() {
      GaugePropertyDialogModel model = new GaugePropertyDialogModel();
      AssemblyPropertyService service = serviceWith(mock(GaugeVSAssembly.class), model);

      Exception thrown = assertThrows(IllegalArgumentException.class,
         () -> service.set("tok", principal(), "Gauge1",
            Map.of("gaugeAdvancedPaneModel.rangePaneModel.rangeValues",
                   java.util.List.of("$(Spinner1)", "", "90")),
            ""));

      assertTrue(thrown.getMessage().contains("rangeValues[1]"),
                 "must name the blank index: " + thrown.getMessage());
   }

   /**
    * The dynamic-value exemption must not weaken the color-count check either: a dynamic
    * {@code rangeValues[0]} still needs a matching {@code rangeColorValues[0]} once any color is
    * populated.
    */
   @Test
   void refusesAGaugeRangeColorCountMismatchEvenWithADynamicRangeValue() {
      GaugePropertyDialogModel model = new GaugePropertyDialogModel();
      AssemblyPropertyService service = serviceWith(mock(GaugeVSAssembly.class), model);
      Map<String, Object> patch = new LinkedHashMap<>();
      patch.put("gaugeAdvancedPaneModel.rangePaneModel.rangeValues",
                java.util.List.of("$(Spinner1)", "40", "60"));
      patch.put("gaugeAdvancedPaneModel.rangePaneModel.rangeColorValues",
                java.util.List.of("", "yellow", "green"));

      Exception thrown = assertThrows(IllegalArgumentException.class,
         () -> service.set("tok", principal(), "Gauge1", patch, ""));

      assertTrue(thrown.getMessage().contains("rangeColorValues[0]"),
                 "must name the missing color index: " + thrown.getMessage());
   }

   /**
    * A genuinely invalid, non-numeric, non-dynamic-value string must still be rejected -- the
    * fix must not widen the check to accept arbitrary garbage, only real "$(...)"/"=..."
    * references.
    */
   @Test
   void refusesAGenuinelyInvalidNonNumericGaugeRangeValue() {
      GaugePropertyDialogModel model = new GaugePropertyDialogModel();
      AssemblyPropertyService service = serviceWith(mock(GaugeVSAssembly.class), model);

      Exception thrown = assertThrows(IllegalArgumentException.class,
         () -> service.set("tok", principal(), "Gauge1",
            Map.of("gaugeAdvancedPaneModel.rangePaneModel.rangeValues",
                   java.util.List.of("abc", "60", "90")),
            ""));

      assertTrue(thrown.getMessage().contains("rangeValues[0]") &&
                 thrown.getMessage().contains("is not a number"),
                 "must still reject genuine garbage: " + thrown.getMessage());
   }

   /**
    * The exact reported repro (bug #76530): {@code columnValue} carries the reference,
    * {@code table} never set. Must resolve and go through, not be refused.
    */
   @Test
   void allowsTheExactReportedReproNowThatColumnValueAloneResolvesToAKnownVariable()
      throws Exception
   {
      Worksheet ws = new Worksheet();
      ws.addAssembly(new DefaultVariableAssembly(ws, "StartDate"));
      TextInputPropertyDialogModel model = new TextInputPropertyDialogModel();
      AssemblyPropertyService service =
         serviceWithTextInput(mock(TextInputVSAssembly.class), model, ws);

      Map<String, Object> patch = new LinkedHashMap<>();
      patch.put("dataInputPaneModel.columnValue", "$(StartDate)");
      patch.put("dataInputPaneModel.variable", true);

      assertDoesNotThrow(
         () -> service.set("tok", principal(), "StartDateInput", patch, ""));
   }

   /** Proves the normalization actually mutates {@code table}, not just avoids throwing. */
   @Test
   void normalizesColumnValueOnlyIntoTableBeforeTheAchievabilityCheck() throws Exception {
      Worksheet ws = new Worksheet();
      ws.addAssembly(new DefaultVariableAssembly(ws, "StartDate"));
      TextInputPropertyDialogModel model = new TextInputPropertyDialogModel();
      AssemblyPropertyService service =
         serviceWithTextInput(mock(TextInputVSAssembly.class), model, ws);

      Map<String, Object> patch = new LinkedHashMap<>();
      patch.put("dataInputPaneModel.columnValue", "$(StartDate)");
      patch.put("dataInputPaneModel.variable", true);

      service.set("tok", principal(), "StartDateInput", patch, "");

      assertEquals("$(StartDate)", model.getDataInputPaneModel().getTable(),
                   "normalizeVariableTableBinding must write 'table', not just avoid throwing");
   }

   /**
    * Bug #76530 part (b): a patch that explicitly touches {@code dataInputPaneModel.variable}
    * but leaves both {@code table} and {@code columnValue} unset cannot possibly resolve to a
    * variable reference -- this write must be refused, by field name, instead of reporting
    * {@code {"ok":true}} while silently changing nothing (the originally-reported symptom).
    */
   @Test
   void refusesAnExplicitVariableWriteWhenNeitherTableNorColumnValueResolveToAVariable() {
      TextInputPropertyDialogModel model = new TextInputPropertyDialogModel();
      AssemblyPropertyService service =
         serviceWithTextInput(mock(TextInputVSAssembly.class), model, new Worksheet());

      Exception thrown = assertThrows(IllegalArgumentException.class,
         () -> service.set("tok", principal(), "StartDateInput",
            Map.of("dataInputPaneModel.variable", true), ""));

      assertTrue(thrown.getMessage().contains("dataInputPaneModel.variable"),
                 "must name the refused field: " + thrown.getMessage());
   }

   /**
    * The achievability check must only fire when the patch actually touches {@code variable} --
    * an unrelated field write on a TextInput whose current binding is not a variable (the common
    * case: most TextInputs write back to a literal cell, not a variable) must not be blocked by
    * state the patch never touched.
    */
   @Test
   void doesNotCheckVariableAchievabilityWhenThePatchDoesNotTouchVariable() throws Exception {
      TextInputPropertyDialogModel model = new TextInputPropertyDialogModel();
      AssemblyPropertyService service =
         serviceWithTextInput(mock(TextInputVSAssembly.class), model, new Worksheet());

      assertDoesNotThrow(() -> service.set("tok", principal(), "StartDateInput",
         Map.of("dataInputPaneModel.rowValue", "0"), ""));
   }

   /**
    * Bug #76552 (follow-up to #76530/PR #5100): the achievability check must compare the
    * <em>requested</em> value, not merely whether a variable binding is possible. A patch
    * requesting {@code variable:false} while {@code columnValue} still resolves to a known
    * variable must be refused -- the real setter would still derive {@code variable=true} from
    * the table's shape regardless of this patch, so the write would report success and silently
    * leave {@code variable} at {@code true}. This is the exact gap #5100's own 3 tests left
    * uncovered: they only ever requested {@code variable:true}.
    */
   @Test
   void refusesAnExplicitVariableFalseWriteWhenTheBindingStillResolvesToAVariable() {
      Worksheet ws = new Worksheet();
      ws.addAssembly(new DefaultVariableAssembly(ws, "StartDate"));
      TextInputPropertyDialogModel model = new TextInputPropertyDialogModel();
      AssemblyPropertyService service =
         serviceWithTextInput(mock(TextInputVSAssembly.class), model, ws);

      Map<String, Object> patch = new LinkedHashMap<>();
      patch.put("dataInputPaneModel.columnValue", "$(StartDate)");
      patch.put("dataInputPaneModel.variable", false);

      Exception thrown = assertThrows(IllegalArgumentException.class,
         () -> service.set("tok", principal(), "StartDateInput", patch, ""));

      assertTrue(thrown.getMessage().contains("dataInputPaneModel.variable"),
                 "must name the refused field: " + thrown.getMessage());
   }

   /**
    * Bug #76552 (follow-up to #76530/PR #5100): the mirror image of the case above -- requesting
    * {@code variable:false} when the table was never a variable to begin with is a harmless
    * no-op and must be allowed, not refused. Before this fix, the achievability check only asked
    * "can this resolve to a variable" (no), which wrongly refused every {@code false} request
    * regardless of whether it matched the current binding.
    */
   @Test
   void allowsAnExplicitVariableFalseWriteWhenTheBindingAlreadyDoesNotResolveToAVariable()
      throws Exception
   {
      TextInputPropertyDialogModel model = new TextInputPropertyDialogModel();
      AssemblyPropertyService service =
         serviceWithTextInput(mock(TextInputVSAssembly.class), model, new Worksheet());

      assertDoesNotThrow(() -> service.set("tok", principal(), "StartDateInput",
         Map.of("dataInputPaneModel.variable", false), ""));
   }

   // ── checkbox/radiobutton (bug #76551) ────────────────────────────────────
   //
   // Same four cases as TextInput above, now that checkbox/radiobutton are in
   // PropertyAliases.VARIABLE_FLAG_DERIVED_TYPES too. This only exercises the AI/wiz-layer
   // achievability check (requireVariableFlagAchievable) -- VSInputService is mocked here, so it
   // never runs the real setCheckboxPropertyModel/setRadioButtonPropertyModel body. The setter's
   // own derive-not-trust behavior (the interactive Composer UI's own protection) is covered
   // separately by CheckBoxRadioButtonTableBindingSetterTest.

   @Test
   void allowsTheExactReportedReproNowThatColumnValueAloneResolvesToAKnownVariableForCheckbox()
      throws Exception
   {
      Worksheet ws = new Worksheet();
      ws.addAssembly(new DefaultVariableAssembly(ws, "StartDate"));
      CheckboxPropertyDialogModel model = new CheckboxPropertyDialogModel();
      AssemblyPropertyService service =
         serviceWithCheckbox(mock(CheckBoxVSAssembly.class), model, ws);

      Map<String, Object> patch = new LinkedHashMap<>();
      patch.put("dataInputPaneModel.columnValue", "$(StartDate)");
      patch.put("dataInputPaneModel.variable", true);

      assertDoesNotThrow(
         () -> service.set("tok", principal(), "StartDateCheckbox", patch, ""));
   }

   @Test
   void refusesAnExplicitVariableWriteWhenNeitherTableNorColumnValueResolveToAVariableForCheckbox() {
      CheckboxPropertyDialogModel model = new CheckboxPropertyDialogModel();
      AssemblyPropertyService service =
         serviceWithCheckbox(mock(CheckBoxVSAssembly.class), model, new Worksheet());

      Exception thrown = assertThrows(IllegalArgumentException.class,
         () -> service.set("tok", principal(), "StartDateCheckbox",
            Map.of("dataInputPaneModel.variable", true), ""));

      assertTrue(thrown.getMessage().contains("dataInputPaneModel.variable"),
                 "must name the refused field: " + thrown.getMessage());
   }

   @Test
   void refusesAnExplicitVariableFalseWriteWhenTheBindingStillResolvesToAVariableForCheckbox() {
      Worksheet ws = new Worksheet();
      ws.addAssembly(new DefaultVariableAssembly(ws, "StartDate"));
      CheckboxPropertyDialogModel model = new CheckboxPropertyDialogModel();
      AssemblyPropertyService service =
         serviceWithCheckbox(mock(CheckBoxVSAssembly.class), model, ws);

      Map<String, Object> patch = new LinkedHashMap<>();
      patch.put("dataInputPaneModel.columnValue", "$(StartDate)");
      patch.put("dataInputPaneModel.variable", false);

      Exception thrown = assertThrows(IllegalArgumentException.class,
         () -> service.set("tok", principal(), "StartDateCheckbox", patch, ""));

      assertTrue(thrown.getMessage().contains("dataInputPaneModel.variable"),
                 "must name the refused field: " + thrown.getMessage());
   }

   @Test
   void allowsAnExplicitVariableFalseWriteWhenTheBindingAlreadyDoesNotResolveToAVariableForCheckbox()
      throws Exception
   {
      CheckboxPropertyDialogModel model = new CheckboxPropertyDialogModel();
      AssemblyPropertyService service =
         serviceWithCheckbox(mock(CheckBoxVSAssembly.class), model, new Worksheet());

      assertDoesNotThrow(() -> service.set("tok", principal(), "StartDateCheckbox",
         Map.of("dataInputPaneModel.variable", false), ""));
   }

   @Test
   void allowsTheExactReportedReproNowThatColumnValueAloneResolvesToAKnownVariableForRadioButton()
      throws Exception
   {
      Worksheet ws = new Worksheet();
      ws.addAssembly(new DefaultVariableAssembly(ws, "StartDate"));
      RadioButtonPropertyDialogModel model = new RadioButtonPropertyDialogModel();
      AssemblyPropertyService service =
         serviceWithRadioButton(mock(RadioButtonVSAssembly.class), model, ws);

      Map<String, Object> patch = new LinkedHashMap<>();
      patch.put("dataInputPaneModel.columnValue", "$(StartDate)");
      patch.put("dataInputPaneModel.variable", true);

      assertDoesNotThrow(
         () -> service.set("tok", principal(), "StartDateRadio", patch, ""));
   }

   @Test
   void refusesAnExplicitVariableWriteWhenNeitherTableNorColumnValueResolveToAVariableForRadioButton() {
      RadioButtonPropertyDialogModel model = new RadioButtonPropertyDialogModel();
      AssemblyPropertyService service =
         serviceWithRadioButton(mock(RadioButtonVSAssembly.class), model, new Worksheet());

      Exception thrown = assertThrows(IllegalArgumentException.class,
         () -> service.set("tok", principal(), "StartDateRadio",
            Map.of("dataInputPaneModel.variable", true), ""));

      assertTrue(thrown.getMessage().contains("dataInputPaneModel.variable"),
                 "must name the refused field: " + thrown.getMessage());
   }

   @Test
   void refusesAnExplicitVariableFalseWriteWhenTheBindingStillResolvesToAVariableForRadioButton() {
      Worksheet ws = new Worksheet();
      ws.addAssembly(new DefaultVariableAssembly(ws, "StartDate"));
      RadioButtonPropertyDialogModel model = new RadioButtonPropertyDialogModel();
      AssemblyPropertyService service =
         serviceWithRadioButton(mock(RadioButtonVSAssembly.class), model, ws);

      Map<String, Object> patch = new LinkedHashMap<>();
      patch.put("dataInputPaneModel.columnValue", "$(StartDate)");
      patch.put("dataInputPaneModel.variable", false);

      Exception thrown = assertThrows(IllegalArgumentException.class,
         () -> service.set("tok", principal(), "StartDateRadio", patch, ""));

      assertTrue(thrown.getMessage().contains("dataInputPaneModel.variable"),
                 "must name the refused field: " + thrown.getMessage());
   }

   @Test
   void allowsAnExplicitVariableFalseWriteWhenTheBindingAlreadyDoesNotResolveToAVariableForRadioButton()
      throws Exception
   {
      RadioButtonPropertyDialogModel model = new RadioButtonPropertyDialogModel();
      AssemblyPropertyService service =
         serviceWithRadioButton(mock(RadioButtonVSAssembly.class), model, new Worksheet());

      assertDoesNotThrow(() -> service.set("tok", principal(), "StartDateRadio",
         Map.of("dataInputPaneModel.variable", false), ""));
   }

   // ── row/column value validation (bug #76803/VOF-013/VOF-014) ──────────────
   //
   // dataInputPaneModel.rowValue/columnValue are only ever checked against the bound embedded
   // table lazily, inside InputVSAssemblyInfo.update() -- never at set_assembly_properties write
   // time. An out-of-range rowValue was a permanent, silent no-op; a nonexistent columnValue was
   // accepted silently and crashed the next unrelated set_input_value call with an opaque 500.
   // requireRowColumnValueValid() mirrors update()'s own checks so both are refused loud, here.

   private static final String EMBEDDED_TABLE = "Query1";

   private static Worksheet embeddedTableWorksheet() {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly embedded = new EmbeddedTableAssembly(ws, EMBEDDED_TABLE);
      // row 0 is the header row, so the addressable data rows are 1 and 2.
      embedded.setEmbeddedData(new XEmbeddedTable(
         new String[]{ XSchema.STRING, XSchema.STRING },
         new Object[][] {
            { "state", "product_name" },
            { "NY", "InsideView" },
            { "CA", "Fast Mail" }
         }));
      ws.addAssembly(embedded);

      return ws;
   }

   @Test
   void vof013OutOfRangeRowValueIsRefused() {
      TextInputPropertyDialogModel model = new TextInputPropertyDialogModel();
      model.getDataInputPaneModel().setTable(EMBEDDED_TABLE);
      AssemblyPropertyService service =
         serviceWithTextInput(mock(TextInputVSAssembly.class), model, embeddedTableWorksheet());

      Map<String, Object> patch = new LinkedHashMap<>();
      patch.put("dataInputPaneModel.columnValue", "state");
      patch.put("dataInputPaneModel.rowValue", "10");

      Exception thrown = assertThrows(IllegalArgumentException.class,
         () -> service.set("tok", principal(), "StartDateInput", patch, ""));

      assertTrue(thrown.getMessage().contains("dataInputPaneModel.rowValue"),
                 "must name the refused field: " + thrown.getMessage());
   }

   @Test
   void vof014NonexistentColumnValueIsRefused() {
      TextInputPropertyDialogModel model = new TextInputPropertyDialogModel();
      model.getDataInputPaneModel().setTable(EMBEDDED_TABLE);
      AssemblyPropertyService service =
         serviceWithTextInput(mock(TextInputVSAssembly.class), model, embeddedTableWorksheet());

      Map<String, Object> patch = new LinkedHashMap<>();
      patch.put("dataInputPaneModel.columnValue", "not_a_real_column");
      patch.put("dataInputPaneModel.rowValue", "1");

      Exception thrown = assertThrows(IllegalArgumentException.class,
         () -> service.set("tok", principal(), "StartDateInput", patch, ""));

      assertTrue(thrown.getMessage().contains("dataInputPaneModel.columnValue"),
                 "must name the refused field: " + thrown.getMessage());
   }

   @Test
   void validRowAndColumnValueStillSucceeds() throws Exception {
      TextInputPropertyDialogModel model = new TextInputPropertyDialogModel();
      model.getDataInputPaneModel().setTable(EMBEDDED_TABLE);
      AssemblyPropertyService service =
         serviceWithTextInput(mock(TextInputVSAssembly.class), model, embeddedTableWorksheet());

      Map<String, Object> patch = new LinkedHashMap<>();
      patch.put("dataInputPaneModel.columnValue", "state");
      patch.put("dataInputPaneModel.rowValue", "1");

      assertDoesNotThrow(() -> service.set("tok", principal(), "StartDateInput", patch, ""));
   }

   /**
    * VOF-014's real repro shape: the table binding is already set from a prior call, and this
    * patch touches only {@code columnValue}. The gating condition ORs on either field, so
    * {@code PropertyPath.get(model, "dataInputPaneModel.table")} must still read the model's
    * pre-existing state and catch this, not just a patch that names all three fields at once.
    */
   @Test
   void vof014ColumnOnlyPatchAgainstAPreExistingTableBindingIsStillCaught() {
      TextInputPropertyDialogModel model = new TextInputPropertyDialogModel();
      model.getDataInputPaneModel().setTable(EMBEDDED_TABLE);
      model.getDataInputPaneModel().setRowValue("1");
      AssemblyPropertyService service =
         serviceWithTextInput(mock(TextInputVSAssembly.class), model, embeddedTableWorksheet());

      Exception thrown = assertThrows(IllegalArgumentException.class,
         () -> service.set("tok", principal(), "StartDateInput",
            Map.of("dataInputPaneModel.columnValue", "not_a_real_column"), ""));

      assertTrue(thrown.getMessage().contains("dataInputPaneModel.columnValue"),
                 "must name the refused field: " + thrown.getMessage());
   }

   /**
    * A {@code "="}-prefixed value is a script expression (bug #76803 revision), resolved only at
    * runtime by a {@code ViewsheetSandbox} this check does not have -- it must be left alone
    * (fails open) rather than statically checked against the real column list and wrongly
    * refused.
    */
   @Test
   void scriptExpressionColumnValueIsNotRefused() throws Exception {
      TextInputPropertyDialogModel model = new TextInputPropertyDialogModel();
      model.getDataInputPaneModel().setTable(EMBEDDED_TABLE);
      AssemblyPropertyService service =
         serviceWithTextInput(mock(TextInputVSAssembly.class), model, embeddedTableWorksheet());

      Map<String, Object> patch = new LinkedHashMap<>();
      patch.put("dataInputPaneModel.columnValue", "=getColumnName()");
      patch.put("dataInputPaneModel.rowValue", "1");

      assertDoesNotThrow(() -> service.set("tok", principal(), "StartDateInput", patch, ""));
   }

   /** Mirrors {@link #scriptExpressionColumnValueIsNotRefused} for {@code rowValue}. */
   @Test
   void scriptExpressionRowValueIsNotRefused() throws Exception {
      TextInputPropertyDialogModel model = new TextInputPropertyDialogModel();
      model.getDataInputPaneModel().setTable(EMBEDDED_TABLE);
      AssemblyPropertyService service =
         serviceWithTextInput(mock(TextInputVSAssembly.class), model, embeddedTableWorksheet());

      Map<String, Object> patch = new LinkedHashMap<>();
      patch.put("dataInputPaneModel.columnValue", "state");
      patch.put("dataInputPaneModel.rowValue", "=getRowIndex()");

      assertDoesNotThrow(() -> service.set("tok", principal(), "StartDateInput", patch, ""));
   }

   // ── static list "embedded" auto-derivation (Redmine #76699/VFO-016) ───────
   //
   // labels/values have no short name (still raw-path-only), but VSInputService.setListValues
   // always writes them while sourceType (which gates whether they are ever read back) is
   // derived from embedded/query alone, defaulting to NONE_SOURCE. Writing labels/values with no
   // query/table/column in the same patch has unambiguous static-list intent, so embedded is
   // implied true; a caller who also sets query/table/column, or embedded itself, is left alone.

   private static final String CHECKBOX_LABELS =
      "checkboxGeneralPaneModel.listValuesPaneModel.comboBoxEditorModel." +
      "variableListDialogModel.labels";
   private static final String CHECKBOX_VALUES =
      "checkboxGeneralPaneModel.listValuesPaneModel.comboBoxEditorModel." +
      "variableListDialogModel.values";
   private static final String COMBOBOX_LABELS =
      "comboboxGeneralPaneModel.listValuesPaneModel.comboBoxEditorModel." +
      "variableListDialogModel.labels";
   private static final String COMBOBOX_VALUES =
      "comboboxGeneralPaneModel.listValuesPaneModel.comboBoxEditorModel." +
      "variableListDialogModel.values";

   @Test
   void impliesEmbeddedWhenLabelsAndValuesAreSetAloneForCheckbox() throws Exception {
      CheckboxPropertyDialogModel model = new CheckboxPropertyDialogModel();
      AssemblyPropertyService service =
         serviceWithCheckbox(mock(CheckBoxVSAssembly.class), model, new Worksheet());
      Map<String, Object> patch = new LinkedHashMap<>();
      patch.put(CHECKBOX_LABELS, java.util.List.of("Show Sales Chart"));
      patch.put(CHECKBOX_VALUES, java.util.List.of("show"));

      service.set("tok", principal(), "ShowSalesChartToggle", patch, "");

      assertTrue(model.getCheckboxGeneralPaneModel().getListValuesPaneModel()
                    .getComboBoxEditorModel().isEmbedded(),
                 "embedded must be implied true so labels/values are not silently inert");
   }

   /**
    * A patch that ALSO sets {@code table}/{@code column} alongside the static labels/values
    * intends a real, distinct configuration ({@code embedded=false, query=true} ==
    * {@code BOUND_SOURCE}) -- embedded must not be silently forced true, which would reclassify
    * it into {@code MERGE_SOURCE} instead.
    */
   @Test
   void leavesEmbeddedAloneWhenTableAndColumnAreAlsoSetForCheckbox() throws Exception {
      CheckboxPropertyDialogModel model = new CheckboxPropertyDialogModel();
      AssemblyPropertyService service =
         serviceWithCheckbox(mock(CheckBoxVSAssembly.class), model, new Worksheet());
      Map<String, Object> patch = new LinkedHashMap<>();
      patch.put(CHECKBOX_LABELS, java.util.List.of("Show Sales Chart"));
      patch.put(CHECKBOX_VALUES, java.util.List.of("show"));
      patch.put("table", "SalesTable");
      patch.put("column", "SalesColumn");

      service.set("tok", principal(), "ShowSalesChartToggle", patch, "");

      assertFalse(model.getCheckboxGeneralPaneModel().getListValuesPaneModel()
                     .getComboBoxEditorModel().isEmbedded(),
                  "a query/table/column binding in the same patch must not be reclassified " +
                  "into MERGE_SOURCE by forcing embedded true");
   }

   /**
    * Same guard, exercised via {@code query} alone (no {@code table}/{@code column}) --
    * {@code embedded=false, query=true} is itself a complete, real {@code BOUND_SOURCE}
    * configuration, so this disjunct of the guard must trip on {@code query} by itself, not only
    * when {@code table}/{@code column} are also present.
    */
   @Test
   void leavesEmbeddedAloneWhenQueryAloneIsAlsoSetForCheckbox() throws Exception {
      CheckboxPropertyDialogModel model = new CheckboxPropertyDialogModel();
      AssemblyPropertyService service =
         serviceWithCheckbox(mock(CheckBoxVSAssembly.class), model, new Worksheet());
      Map<String, Object> patch = new LinkedHashMap<>();
      patch.put(CHECKBOX_LABELS, java.util.List.of("Show Sales Chart"));
      patch.put(CHECKBOX_VALUES, java.util.List.of("show"));
      patch.put("query", true);

      service.set("tok", principal(), "ShowSalesChartToggle", patch, "");

      assertFalse(model.getCheckboxGeneralPaneModel().getListValuesPaneModel()
                     .getComboBoxEditorModel().isEmbedded(),
                  "query set alone in the same patch must not be reclassified into " +
                  "MERGE_SOURCE by forcing embedded true");
   }

   /** A caller who sets {@code embedded} explicitly is never overridden, even to {@code false}. */
   @Test
   void leavesEmbeddedAloneWhenCallerSetsItExplicitlyForCheckbox() throws Exception {
      CheckboxPropertyDialogModel model = new CheckboxPropertyDialogModel();
      AssemblyPropertyService service =
         serviceWithCheckbox(mock(CheckBoxVSAssembly.class), model, new Worksheet());
      Map<String, Object> patch = new LinkedHashMap<>();
      patch.put(CHECKBOX_LABELS, java.util.List.of("Show Sales Chart"));
      patch.put(CHECKBOX_VALUES, java.util.List.of("show"));
      patch.put("embedded", false);

      service.set("tok", principal(), "ShowSalesChartToggle", patch, "");

      assertFalse(model.getCheckboxGeneralPaneModel().getListValuesPaneModel()
                     .getComboBoxEditorModel().isEmbedded(),
                  "an explicit embedded must not be overridden by the labels/values implication");
   }

   /** Same shape, RadioButton -- confirms the derivation is not CheckBox-specific. */
   @Test
   void impliesEmbeddedWhenLabelsAndValuesAreSetAloneForRadioButton() throws Exception {
      RadioButtonPropertyDialogModel model = new RadioButtonPropertyDialogModel();
      AssemblyPropertyService service =
         serviceWithRadioButton(mock(RadioButtonVSAssembly.class), model, new Worksheet());
      Map<String, Object> patch = new LinkedHashMap<>();
      patch.put("radioButtonGeneralPaneModel.listValuesPaneModel.comboBoxEditorModel." +
                "variableListDialogModel.labels", java.util.List.of("Show Sales Chart"));
      patch.put("radioButtonGeneralPaneModel.listValuesPaneModel.comboBoxEditorModel." +
                "variableListDialogModel.values", java.util.List.of("show"));

      service.set("tok", principal(), "StartDateRadio", patch, "");

      assertTrue(model.getRadioButtonGeneralPaneModel().getListValuesPaneModel()
                    .getComboBoxEditorModel().isEmbedded(),
                 "embedded must be implied true for RadioButton too, same shared listInput() " +
                 "gap as CheckBox");
   }

   /**
    * Same shape, ComboBox -- confirms the derivation is not CheckBox/RadioButton-only either.
    * ComboBox's registration ({@code register(registry, "combobox", ..., listInput(...))}) goes
    * through the identical {@code listInput()} helper as the other two, with no separate
    * {@code dataInput()} merge -- {@code comboboxTableAliasIsTheListValuesQueryNotTheWriteBackTarget}
    * in {@code PropertyAliasesTest} already pins that ComboBox's short {@code table} alias
    * resolves to the list-values query path, not the unrelated {@code dataInputPaneModel.table}
    * row/column write-back target -- so the guard's {@code table}/{@code column} disjunct is
    * reachable via ComboBox's own short aliases too, not raw-path-only.
    */
   @Test
   void impliesEmbeddedWhenLabelsAndValuesAreSetAloneForCombobox() throws Exception {
      ComboboxPropertyDialogModel model = new ComboboxPropertyDialogModel();
      AssemblyPropertyService service =
         serviceWithCombobox(mock(ComboBoxVSAssembly.class), model, new Worksheet());
      Map<String, Object> patch = new LinkedHashMap<>();
      patch.put(COMBOBOX_LABELS, java.util.List.of("Show Sales Chart"));
      patch.put(COMBOBOX_VALUES, java.util.List.of("show"));

      service.set("tok", principal(), "ShowSalesChartDropdown", patch, "");

      assertTrue(model.getComboboxGeneralPaneModel().getListValuesPaneModel()
                    .getComboBoxEditorModel().isEmbedded(),
                 "embedded must be implied true for ComboBox too, same shared listInput() gap " +
                 "as CheckBox/RadioButton");
   }

   /**
    * The guard case for ComboBox, using its own short {@code table}/{@code column} aliases (not
    * a raw path) -- proves the guard's table/column disjunct is actually reachable for ComboBox
    * through the vocabulary a caller would really use, not just in theory.
    */
   @Test
   void leavesEmbeddedAloneWhenTableAndColumnAreAlsoSetForCombobox() throws Exception {
      ComboboxPropertyDialogModel model = new ComboboxPropertyDialogModel();
      AssemblyPropertyService service =
         serviceWithCombobox(mock(ComboBoxVSAssembly.class), model, new Worksheet());
      Map<String, Object> patch = new LinkedHashMap<>();
      patch.put(COMBOBOX_LABELS, java.util.List.of("Show Sales Chart"));
      patch.put(COMBOBOX_VALUES, java.util.List.of("show"));
      patch.put("table", "SalesTable");
      patch.put("column", "SalesColumn");

      service.set("tok", principal(), "ShowSalesChartDropdown", patch, "");

      assertFalse(model.getComboboxGeneralPaneModel().getListValuesPaneModel()
                     .getComboBoxEditorModel().isEmbedded(),
                  "ComboBox's own short table/column aliases must trip the guard just like the " +
                  "raw path would, not be silently unreachable");
   }

   /**
    * {@code ChartPropertyDialogService.setChartPropertyModel} only calls {@code setAlphaValue}
    * inside the {@code tipOption == true} branch, so {@code tipAlpha} set alone -- without
    * {@code tipOption}/{@code tipView} in the same patch -- would otherwise be silently dropped
    * (Redmine #76516). {@code impliedSibling} fills in {@code tipOption: true} for this case.
    */
   @Test
   void impliesTipOptionWhenAlphaIsSetAlone() throws Exception {
      ChartPropertyDialogModel model = new ChartPropertyDialogModel();
      AssemblyPropertyService service = serviceWith(mock(ChartVSAssembly.class), model);

      service.set("tok", principal(), "Chart1", Map.of("tipAlpha", "50"), "");

      assertTrue(model.getChartGeneralPaneModel().getTipPaneModel().isTipOption(),
                 "tipOption must be implied true so alpha is not silently dropped");
      assertEquals("50", model.getChartGeneralPaneModel().getTipPaneModel().getAlpha());
   }

   /** A caller who sets {@code tipOption} explicitly is never overridden, even to {@code false}. */
   @Test
   void leavesTipOptionAloneWhenAlphaAndTipOptionAreBothSet() throws Exception {
      ChartPropertyDialogModel model = new ChartPropertyDialogModel();
      AssemblyPropertyService service = serviceWith(mock(ChartVSAssembly.class), model);

      service.set("tok", principal(), "Chart1", Map.of("tipAlpha", "50", "tipOption", false), "");

      assertFalse(model.getChartGeneralPaneModel().getTipPaneModel().isTipOption(),
                  "an explicit tipOption must not be overridden by the alpha implication");
   }

   /**
    * {@code customTip} is only applied when {@code customRB == TipFormat.CUSTOM}; otherwise it is
    * actively nulled out. So {@code tooltip} set alone -- without {@code tooltipMode: "CUSTOM"}
    * in the same patch -- would otherwise wipe any existing custom tooltip (Redmine #76516).
    * {@code impliedSibling} fills in {@code customRB: CUSTOM} for this case.
    */
   @Test
   void impliesCustomModeWhenTooltipIsSetAlone() throws Exception {
      GaugePropertyDialogModel model = new GaugePropertyDialogModel();
      AssemblyPropertyService service = serviceWith(mock(GaugeVSAssembly.class), model);

      service.set("tok", principal(), "Gauge1", Map.of("tooltip", "hello"), "");

      assertEquals(TipCustomizeDialogModel.TipFormat.CUSTOM,
                   model.getGaugeGeneralPaneModel().getTipPaneModel()
                      .getTipCustomizeDialogModel().getCustomRB(),
                   "customRB must be implied CUSTOM so tooltip is not silently wiped");
      assertEquals("hello", model.getGaugeGeneralPaneModel().getTipPaneModel()
         .getTipCustomizeDialogModel().getCustomTip());
   }

   /** A caller who sets {@code tooltipMode} explicitly is never overridden. */
   @Test
   void leavesCustomModeAloneWhenTooltipAndTooltipModeAreBothSet() throws Exception {
      GaugePropertyDialogModel model = new GaugePropertyDialogModel();
      AssemblyPropertyService service = serviceWith(mock(GaugeVSAssembly.class), model);

      service.set("tok", principal(), "Gauge1",
                  Map.of("tooltip", "hello", "tooltipMode", "none"), "");

      assertEquals(TipCustomizeDialogModel.TipFormat.NONE,
                   model.getGaugeGeneralPaneModel().getTipPaneModel()
                      .getTipCustomizeDialogModel().getCustomRB(),
                   "an explicit tooltipMode must not be overridden by the tooltip implication");
   }

   // ── showType/mode/sortType int-enum alias/domain (bugs #76542, #76925) ───
   //
   // selectionGeneralPaneModel.showType and calendarAdvancedPaneModel.showType share the short
   // alias "showType" but have different, overlapping int domains (Selection: list=0/dropdown=1;
   // Calendar: calendar=1/dropdown=2) -- a leaf-name-only canonicalization would silently misapply
   // "dropdown" on whichever collides. canonicalIntEnum is keyed by the resolved full path
   // instead, so these tests exercise both paths explicitly. selectionTreePaneModel.mode and
   // selectionGeneralPaneModel.sortType (shared by SelectionList and SelectionTree) are the same
   // mechanism, added for bug #76925.

   @Test
   void canonicalizesDropdownTokenOnASelectionListShowType() throws Exception {
      SelectionListPropertyDialogModel model = new SelectionListPropertyDialogModel();
      AssemblyPropertyService service =
         serviceWithSelectionList(mock(SelectionListVSAssembly.class), model);

      service.set("tok", principal(), "Selection1", Map.of("showType", "dropdown"), "");

      assertEquals(1, model.getSelectionGeneralPaneModel().getShowType());
   }

   @Test
   void canonicalizesDropdownTokenCaseInsensitivelyOnASelectionListShowType() throws Exception {
      SelectionListPropertyDialogModel model = new SelectionListPropertyDialogModel();
      AssemblyPropertyService service =
         serviceWithSelectionList(mock(SelectionListVSAssembly.class), model);

      service.set("tok", principal(), "Selection1", Map.of("showType", "Dropdown"), "");

      assertEquals(1, model.getSelectionGeneralPaneModel().getShowType());
   }

   @Test
   void canonicalizesListTokenOnASelectionListShowType() throws Exception {
      SelectionListPropertyDialogModel model = new SelectionListPropertyDialogModel();
      AssemblyPropertyService service =
         serviceWithSelectionList(mock(SelectionListVSAssembly.class), model);

      service.set("tok", principal(), "Selection1", Map.of("showType", "list"), "");

      assertEquals(0, model.getSelectionGeneralPaneModel().getShowType());
   }

   /**
    * The regression test that would catch a wrongly-leaf-name-keyed implementation: Calendar's
    * "dropdown" is a different int (2) from Selection's (1), because Calendar's own showType
    * domain also has a "calendar" mode value that collides with Selection's dropdown value (1).
    */
   @Test
   void canonicalizesDropdownTokenOnACalendarShowTypeToItsOwnDomain() throws Exception {
      CalendarPropertyDialogModel model = new CalendarPropertyDialogModel();
      AssemblyPropertyService service =
         serviceWithCalendar(mock(CalendarVSAssembly.class), model);

      service.set("tok", principal(), "Calendar1", Map.of("showType", "dropdown"), "");

      assertEquals(2, model.getCalendarAdvancedPaneModel().getShowType());
   }

   @Test
   void canonicalizesCalendarTokenOnACalendarShowType() throws Exception {
      CalendarPropertyDialogModel model = new CalendarPropertyDialogModel();
      AssemblyPropertyService service =
         serviceWithCalendar(mock(CalendarVSAssembly.class), model);

      service.set("tok", principal(), "Calendar1", Map.of("showType", "calendar"), "");

      assertEquals(1, model.getCalendarAdvancedPaneModel().getShowType());
   }

   // ── selectionTreePaneModel.mode alias/domain (bug #76925) ────────────────
   //
   // mode is SelectionTree-only, a closed 2-value enum (column=1, id=2) -- see
   // SelectionTreeVSAssemblyInfo.COLUMN/ID and selection-tree-pane.component.html's two radio
   // buttons, the only real producer of this value.

   @Test
   void canonicalizesColumnTokenOnASelectionTreeMode() throws Exception {
      SelectionTreePropertyDialogModel model = new SelectionTreePropertyDialogModel();
      AssemblyPropertyService service =
         serviceWithSelectionTree(mock(SelectionTreeVSAssembly.class), model);

      service.set("tok", principal(), "SelectionTree1", Map.of("mode", "column"), "");

      assertEquals(SelectionTreeVSAssemblyInfo.COLUMN, model.getSelectionTreePaneModel().getMode());
   }

   @Test
   void canonicalizesColumnsPluralTokenOnASelectionTreeMode() throws Exception {
      SelectionTreePropertyDialogModel model = new SelectionTreePropertyDialogModel();
      AssemblyPropertyService service =
         serviceWithSelectionTree(mock(SelectionTreeVSAssembly.class), model);

      service.set("tok", principal(), "SelectionTree1", Map.of("mode", "columns"), "");

      assertEquals(SelectionTreeVSAssemblyInfo.COLUMN, model.getSelectionTreePaneModel().getMode());
   }

   @Test
   void canonicalizesIdTokenCaseInsensitivelyOnASelectionTreeMode() throws Exception {
      SelectionTreePropertyDialogModel model = new SelectionTreePropertyDialogModel();
      AssemblyPropertyService service =
         serviceWithSelectionTree(mock(SelectionTreeVSAssembly.class), model);

      service.set("tok", principal(), "SelectionTree1", Map.of("mode", "ID"), "");

      assertEquals(SelectionTreeVSAssemblyInfo.ID, model.getSelectionTreePaneModel().getMode());
   }

   @Test
   void refusesAnUnrecognizedModeTokenNamingTheValidValues() {
      SelectionTreePropertyDialogModel model = new SelectionTreePropertyDialogModel();
      AssemblyPropertyService service =
         serviceWithSelectionTree(mock(SelectionTreeVSAssembly.class), model);

      Exception thrown = assertThrows(IllegalArgumentException.class,
         () -> service.set("tok", principal(), "SelectionTree1", Map.of("mode", "bogus"), ""));

      assertTrue(thrown.getMessage().contains("bogus"));
      assertTrue(thrown.getMessage().contains("column"), "must name the valid tokens: " +
                 thrown.getMessage());
      assertTrue(thrown.getMessage().contains("id"), "must name the valid tokens: " +
                 thrown.getMessage());
   }

   /** An out-of-domain numeric mode must be rejected too, not just silently stored. */
   @Test
   void refusesAnOutOfDomainNumericMode() {
      SelectionTreePropertyDialogModel model = new SelectionTreePropertyDialogModel();
      AssemblyPropertyService service =
         serviceWithSelectionTree(mock(SelectionTreeVSAssembly.class), model);

      Exception thrown = assertThrows(IllegalArgumentException.class,
         () -> service.set("tok", principal(), "SelectionTree1", Map.of("mode", 999), ""));

      assertTrue(thrown.getMessage().contains("999"));
   }

   /** An already-numeric, in-domain mode must still work exactly as before -- no regression. */
   @Test
   void stillAcceptsAnAlreadyNumericInDomainMode() throws Exception {
      SelectionTreePropertyDialogModel model = new SelectionTreePropertyDialogModel();
      AssemblyPropertyService service =
         serviceWithSelectionTree(mock(SelectionTreeVSAssembly.class), model);

      service.set("tok", principal(), "SelectionTree1", Map.of("mode", 2), "");

      assertEquals(SelectionTreeVSAssemblyInfo.ID, model.getSelectionTreePaneModel().getMode());
   }

   // ── selectionGeneralPaneModel.sortType alias/domain (bug #76925) ──────────
   //
   // sortType is shared by SelectionList and SelectionTree (one resolved path, one domain entry
   // covers both -- see AssemblyPropertyService.INT_ENUM_DOMAINS's own javadoc). Its domain is
   // four values (none=0, asc=1, desc=2, specific=8): the three the Selection dialog's own "Sort"
   // radios can produce, plus SORT_NONE, which SelectionBaseVSAssemblyInfo#setSortType's own
   // javadoc documents as legal and which the assembly's script API
   // (SelectionListVSAScriptable/SelectionTreeVSAScriptable) can write under this identical name
   // -- refuting it would be a regression, not a fix (see 02-refute.md).

   @Test
   void canonicalizesAscTokenOnASelectionListSortType() throws Exception {
      SelectionListPropertyDialogModel model = new SelectionListPropertyDialogModel();
      AssemblyPropertyService service =
         serviceWithSelectionList(mock(SelectionListVSAssembly.class), model);

      service.set("tok", principal(), "Selection1", Map.of("sortType", "asc"), "");

      assertEquals(XConstants.SORT_ASC, model.getSelectionGeneralPaneModel().getSortType());
   }

   @Test
   void canonicalizesDescendingTokenCaseInsensitivelyOnASelectionListSortType() throws Exception {
      SelectionListPropertyDialogModel model = new SelectionListPropertyDialogModel();
      AssemblyPropertyService service =
         serviceWithSelectionList(mock(SelectionListVSAssembly.class), model);

      service.set("tok", principal(), "Selection1", Map.of("sortType", "Descending"), "");

      assertEquals(XConstants.SORT_DESC, model.getSelectionGeneralPaneModel().getSortType());
   }

   @Test
   void canonicalizesHideOthersTokenOnASelectionListSortType() throws Exception {
      SelectionListPropertyDialogModel model = new SelectionListPropertyDialogModel();
      AssemblyPropertyService service =
         serviceWithSelectionList(mock(SelectionListVSAssembly.class), model);

      service.set("tok", principal(), "Selection1", Map.of("sortType", "hideothers"), "");

      assertEquals(XConstants.SORT_SPECIFIC, model.getSelectionGeneralPaneModel().getSortType());
   }

   /**
    * SORT_NONE (0) is the amendment 02-refute.md required: it is not one of the dialog's own
    * three radios, but it is a documented-legal, script-API-reachable value that must not be
    * refused by this fix.
    */
   @Test
   void canonicalizesNoneTokenOnASelectionListSortType() throws Exception {
      SelectionListPropertyDialogModel model = new SelectionListPropertyDialogModel();
      AssemblyPropertyService service =
         serviceWithSelectionList(mock(SelectionListVSAssembly.class), model);

      service.set("tok", principal(), "Selection1", Map.of("sortType", "none"), "");

      assertEquals(XConstants.SORT_NONE, model.getSelectionGeneralPaneModel().getSortType());
   }

   @Test
   void canonicalizesAscTokenOnASelectionTreeSortType() throws Exception {
      SelectionTreePropertyDialogModel model = new SelectionTreePropertyDialogModel();
      AssemblyPropertyService service =
         serviceWithSelectionTree(mock(SelectionTreeVSAssembly.class), model);

      service.set("tok", principal(), "SelectionTree1", Map.of("sortType", "asc"), "");

      assertEquals(XConstants.SORT_ASC, model.getSelectionGeneralPaneModel().getSortType());
   }

   @Test
   void refusesAnUnrecognizedSortTypeTokenNamingTheValidValues() {
      SelectionListPropertyDialogModel model = new SelectionListPropertyDialogModel();
      AssemblyPropertyService service =
         serviceWithSelectionList(mock(SelectionListVSAssembly.class), model);

      Exception thrown = assertThrows(IllegalArgumentException.class,
         () -> service.set("tok", principal(), "Selection1", Map.of("sortType", "bogus"), ""));

      assertTrue(thrown.getMessage().contains("bogus"));
      assertTrue(thrown.getMessage().contains("asc"), "must name the valid tokens: " +
                 thrown.getMessage());
      assertTrue(thrown.getMessage().contains("desc"), "must name the valid tokens: " +
                 thrown.getMessage());
      assertTrue(thrown.getMessage().contains("specific"), "must name the valid tokens: " +
                 thrown.getMessage());
      assertTrue(thrown.getMessage().contains("none"), "must name the valid tokens: " +
                 thrown.getMessage());
   }

   /**
    * An out-of-domain numeric sortType must be rejected too, not just silently stored -- {@code
    * 999} is syntactically a valid int but not one this dialog/script surface ever produces.
    */
   @Test
   void refusesAnOutOfDomainNumericSortType() {
      SelectionListPropertyDialogModel model = new SelectionListPropertyDialogModel();
      AssemblyPropertyService service =
         serviceWithSelectionList(mock(SelectionListVSAssembly.class), model);

      Exception thrown = assertThrows(IllegalArgumentException.class,
         () -> service.set("tok", principal(), "Selection1", Map.of("sortType", 999), ""));

      assertTrue(thrown.getMessage().contains("999"));
   }

   /**
    * {@code SORT_ASC|SORT_DESC} combined (3) is syntactically a valid int but is not a value
    * this dialog/script surface ever produces either -- it must be refused the same as 999, not
    * accepted just because it happens to be a combination of two in-domain bits.
    */
   @Test
   void refusesABitmaskCombinationOfTwoInDomainSortTypeValues() {
      SelectionListPropertyDialogModel model = new SelectionListPropertyDialogModel();
      AssemblyPropertyService service =
         serviceWithSelectionList(mock(SelectionListVSAssembly.class), model);

      Exception thrown = assertThrows(IllegalArgumentException.class,
         () -> service.set("tok", principal(), "Selection1", Map.of("sortType", 3), ""));

      assertTrue(thrown.getMessage().contains("3"));
   }

   /** An already-numeric, in-domain sortType must still work exactly as before -- no regression. */
   @Test
   void stillAcceptsAnAlreadyNumericInDomainSortType() throws Exception {
      SelectionListPropertyDialogModel model = new SelectionListPropertyDialogModel();
      AssemblyPropertyService service =
         serviceWithSelectionList(mock(SelectionListVSAssembly.class), model);

      service.set("tok", principal(), "Selection1", Map.of("sortType", 8), "");

      assertEquals(XConstants.SORT_SPECIFIC, model.getSelectionGeneralPaneModel().getSortType());
   }

   // ── rangeSliderAdvancedPaneModel.rangeSliderSizePaneModel.rangeType (bug #76936) ─────────
   //
   // rangeType is a closed int-enum too (TimeInfo.YEAR/MONTH/NUMBER/MEMBER/DAY/HOUR/MINUTE/
   // HOUR_OF_DAY/MINUTE_OF_DAY, non-sequential -- gaps at 5-15 and 19), same mechanism as
   // showType/mode/sortType above.

   @Test
   void canonicalizesDayTokenOnARangeSliderRangeType() throws Exception {
      RangeSliderPropertyDialogModel model = new RangeSliderPropertyDialogModel();
      AssemblyPropertyService service =
         serviceWithRangeSlider(mock(TimeSliderVSAssembly.class), model);

      service.set("tok", principal(), "RangeSlider1", Map.of("rangeType", "day"), "");

      assertEquals(TimeInfo.DAY, model.getRangeSliderAdvancedPaneModel()
         .getRangeSliderSizePaneModel().getRangeType());
   }

   @Test
   void canonicalizesHourOfDayTokenCaseInsensitivelyOnARangeSliderRangeType() throws Exception {
      RangeSliderPropertyDialogModel model = new RangeSliderPropertyDialogModel();
      AssemblyPropertyService service =
         serviceWithRangeSlider(mock(TimeSliderVSAssembly.class), model);

      service.set("tok", principal(), "RangeSlider1", Map.of("rangeType", "Hour_Of_Day"), "");

      assertEquals(TimeInfo.HOUR_OF_DAY, model.getRangeSliderAdvancedPaneModel()
         .getRangeSliderSizePaneModel().getRangeType());
   }

   @Test
   void refusesAnUnrecognizedRangeTypeTokenNamingTheValidValues() {
      RangeSliderPropertyDialogModel model = new RangeSliderPropertyDialogModel();
      AssemblyPropertyService service =
         serviceWithRangeSlider(mock(TimeSliderVSAssembly.class), model);

      Exception thrown = assertThrows(IllegalArgumentException.class,
         () -> service.set("tok", principal(), "RangeSlider1", Map.of("rangeType", "bogus"), ""));

      assertTrue(thrown.getMessage().contains("bogus"));
      assertTrue(thrown.getMessage().contains("day"), "must name the valid tokens: " +
                 thrown.getMessage());
      assertTrue(thrown.getMessage().contains("year"), "must name the valid tokens: " +
                 thrown.getMessage());
   }

   /**
    * An out-of-domain numeric rangeType must be rejected too, not just silently stored -- {@code
    * 999} is syntactically a valid int but not one of TimeInfo's own non-sequential constants.
    */
   @Test
   void refusesAnOutOfDomainNumericRangeType() {
      RangeSliderPropertyDialogModel model = new RangeSliderPropertyDialogModel();
      AssemblyPropertyService service =
         serviceWithRangeSlider(mock(TimeSliderVSAssembly.class), model);

      Exception thrown = assertThrows(IllegalArgumentException.class,
         () -> service.set("tok", principal(), "RangeSlider1", Map.of("rangeType", 999), ""));

      assertTrue(thrown.getMessage().contains("999"));
   }

   /** An already-numeric, in-domain rangeType must still work exactly as before -- no regression. */
   @Test
   void stillAcceptsAnAlreadyNumericInDomainRangeType() throws Exception {
      RangeSliderPropertyDialogModel model = new RangeSliderPropertyDialogModel();
      AssemblyPropertyService service =
         serviceWithRangeSlider(mock(TimeSliderVSAssembly.class), model);

      service.set("tok", principal(), "RangeSlider1", Map.of("rangeType", TimeInfo.MINUTE), "");

      assertEquals(TimeInfo.MINUTE, model.getRangeSliderAdvancedPaneModel()
         .getRangeSliderSizePaneModel().getRangeType());
   }

   // ── calendarAdvancedPaneModel.min/max: DynamicValueModel coercion (bug #76888) ────────────
   //
   // min/max are DynamicValueModel-typed (value/type/dataType), not a plain scalar, so there was
   // previously no shape of raw dotted-path value coerce() could turn into one -- every write hit
   // the same unconditional throw regardless of content (PropertyPath.coerce()'s missing bean/Map
   // case). These exercise the fix through the real AssemblyPropertyService.set() entry point,
   // the same one set_assembly_properties calls.

   @Test
   void writesCalendarMinFromAStructuredJsonObjectWithAnExplicitType() throws Exception {
      CalendarPropertyDialogModel model = new CalendarPropertyDialogModel();
      AssemblyPropertyService service =
         serviceWithCalendar(mock(CalendarVSAssembly.class), model);

      service.set("tok", principal(), "Calendar1", Map.of(
         "calendarAdvancedPaneModel.min",
         Map.of("value", "2026-01-01", "type", "VALUE", "dataType", "date")), "");

      DynamicValueModel min = model.getCalendarAdvancedPaneModel().getMin();
      assertEquals("2026-01-01", min.getValue());
      assertEquals("VALUE", min.getType());
      assertEquals("date", min.getDataType());
   }

   /**
    * No explicit {@code type} in the JSON object -- must auto-detect {@code VARIABLE} from the
    * {@code value} entry's own shape, the same way a bare {@code "$(Foo)"} string does, rather
    * than silently defaulting to {@code VALUE} and storing an unresolved literal.
    */
   @Test
   void writesCalendarMinFromAJsonObjectWithNoExplicitTypeAutoDetectingAVariableReference()
      throws Exception
   {
      CalendarPropertyDialogModel model = new CalendarPropertyDialogModel();
      AssemblyPropertyService service =
         serviceWithCalendar(mock(CalendarVSAssembly.class), model);

      service.set("tok", principal(), "Calendar1",
                  Map.of("calendarAdvancedPaneModel.min", Map.of("value", "$(Foo)")), "");

      DynamicValueModel min = model.getCalendarAdvancedPaneModel().getMin();
      assertEquals("$(Foo)", min.getValue());
      assertEquals(DynamicValueModel.VARIABLE, min.getType(),
                   "a missing type must not silently default to VALUE for a variable reference");
   }

   @Test
   void writesCalendarMinFromABareLiteralString() throws Exception {
      CalendarPropertyDialogModel model = new CalendarPropertyDialogModel();
      AssemblyPropertyService service =
         serviceWithCalendar(mock(CalendarVSAssembly.class), model);

      service.set("tok", principal(), "Calendar1",
                  Map.of("calendarAdvancedPaneModel.min", "2026-01-01"), "");

      DynamicValueModel min = model.getCalendarAdvancedPaneModel().getMin();
      assertEquals("2026-01-01", min.getValue());
      assertEquals(DynamicValueModel.VALUE, min.getType());
   }

   @Test
   void writesCalendarMinFromABareComponentReferenceString() throws Exception {
      CalendarPropertyDialogModel model = new CalendarPropertyDialogModel();
      AssemblyPropertyService service =
         serviceWithCalendar(mock(CalendarVSAssembly.class), model);

      service.set("tok", principal(), "Calendar1",
                  Map.of("calendarAdvancedPaneModel.min", "$(Spinner1)"), "");

      DynamicValueModel min = model.getCalendarAdvancedPaneModel().getMin();
      assertEquals("$(Spinner1)", min.getValue());
      assertEquals(DynamicValueModel.VARIABLE, min.getType());
   }

   @Test
   void refusesAnUnrecognizedShowTypeTokenNamingTheValidValues() {
      SelectionListPropertyDialogModel model = new SelectionListPropertyDialogModel();
      AssemblyPropertyService service =
         serviceWithSelectionList(mock(SelectionListVSAssembly.class), model);

      Exception thrown = assertThrows(IllegalArgumentException.class,
         () -> service.set("tok", principal(), "Selection1", Map.of("showType", "combobox"), ""));

      assertTrue(thrown.getMessage().contains("combobox"));
      assertTrue(thrown.getMessage().contains("list"), "must name the valid tokens: " +
                 thrown.getMessage());
      assertTrue(thrown.getMessage().contains("dropdown"), "must name the valid tokens: " +
                 thrown.getMessage());
   }

   /**
    * An out-of-domain numeric value must be rejected too, not just silently stored -- {@code 2}
    * is Calendar's dropdown, not a valid SelectionList showType (0 or 1).
    */
   @Test
   void refusesAnOutOfDomainNumericShowTypeOnASelectionList() {
      SelectionListPropertyDialogModel model = new SelectionListPropertyDialogModel();
      AssemblyPropertyService service =
         serviceWithSelectionList(mock(SelectionListVSAssembly.class), model);

      Exception thrown = assertThrows(IllegalArgumentException.class,
         () -> service.set("tok", principal(), "Selection1", Map.of("showType", 2), ""));

      assertTrue(thrown.getMessage().contains("2"));
   }

   /** An already-numeric, in-domain value must still work exactly as before -- no regression. */
   @Test
   void stillAcceptsAnAlreadyNumericInDomainShowTypeOnASelectionList() throws Exception {
      SelectionListPropertyDialogModel model = new SelectionListPropertyDialogModel();
      AssemblyPropertyService service =
         serviceWithSelectionList(mock(SelectionListVSAssembly.class), model);

      service.set("tok", principal(), "Selection1", Map.of("showType", 1), "");

      assertEquals(1, model.getSelectionGeneralPaneModel().getShowType());
   }

   // ── tableStyle write-time validation (bug #76764/VTS-003) ────────────────
   //
   // PropertyPath's CONSTRAINED_STRINGS gate doesn't cover tableStyle (a dynamic, per-library
   // domain), so a raw-path write of a name that matches no real style was silently accepted,
   // stored verbatim, and resolved to null at render time (DataVSAQuery/VSUtil.getTableStyle),
   // falling back to CSS-only formatting with no error anywhere. These exercise both leaf
   // fields a real style tree node carries -- data() (the ID, what the interactive Composer UI
   // itself writes/matches) and label() (the folder-stripped name, what this plugin's own
   // tableStyleTools.ts tells callers to write) -- since a validator that accepted only one
   // would falsely refuse the other's real, currently-legitimate calling convention.

   private static final String TABLE_STYLE_PATH =
      "tableViewGeneralPaneModel.tableStylePaneModel.tableStyle";

   private static TreeNodeModel sampleStyleTree() {
      TreeNodeModel style = TreeNodeModel.builder()
         .label("Default Style")
         .data("4611686018437387905")
         .type("style")
         .leaf(true)
         .build();
      TreeNodeModel folder = TreeNodeModel.builder()
         .label("Styles")
         .type("folder")
         .leaf(false)
         .children(java.util.List.of(style))
         .build();

      return TreeNodeModel.builder().children(java.util.List.of(folder)).build();
   }

   @Test
   void refusesADanglingTableStyleNameOnATable() {
      TableViewPropertyDialogModel model = new TableViewPropertyDialogModel();
      model.getTableViewGeneralPaneModel().getTableStylePaneModel()
         .setStyleTree(sampleStyleTree());
      AssemblyPropertyService service = serviceWithTable(mock(TableVSAssembly.class), model);

      Exception thrown = assertThrows(IllegalArgumentException.class,
         () -> service.set("tok", principal(), "Table1",
            Map.of(TABLE_STYLE_PATH, "No Such Style"), ""));

      assertTrue(thrown.getMessage().contains("No Such Style"),
                 "must name the bad value: " + thrown.getMessage());
   }

   @Test
   void refusesADanglingTableStyleNameOnACalcTable() {
      CalcTablePropertyDialogModel model = new CalcTablePropertyDialogModel();
      model.getTableViewGeneralPaneModel().getTableStylePaneModel()
         .setStyleTree(sampleStyleTree());
      AssemblyPropertyService service =
         serviceWithCalcTable(mock(CalcTableVSAssembly.class), model);

      Exception thrown = assertThrows(IllegalArgumentException.class,
         () -> service.set("tok", principal(), "CalcTable1",
            Map.of(TABLE_STYLE_PATH, "No Such Style"), ""));

      assertTrue(thrown.getMessage().contains("No Such Style"),
                 "must name the bad value: " + thrown.getMessage());
   }

   /** The plugin's own documented convention (tableStyleTools.ts): write the style's name. */
   @Test
   void allowsATableStyleWriteMatchingARealStylesLabel() throws Exception {
      TableViewPropertyDialogModel model = new TableViewPropertyDialogModel();
      model.getTableViewGeneralPaneModel().getTableStylePaneModel()
         .setStyleTree(sampleStyleTree());
      AssemblyPropertyService service = serviceWithTable(mock(TableVSAssembly.class), model);

      service.set("tok", principal(), "Table1", Map.of(TABLE_STYLE_PATH, "Default Style"), "");

      assertEquals("Default Style", model.getTableViewGeneralPaneModel()
         .getTableStylePaneModel().getTableStyle());
   }

   /**
    * The interactive Composer UI's own convention (table-style-pane.component.ts): write the
    * style's ID, not its name. A validator that only matched label() would wrongly refuse this.
    */
   @Test
   void allowsATableStyleWriteMatchingARealStylesId() throws Exception {
      TableViewPropertyDialogModel model = new TableViewPropertyDialogModel();
      model.getTableViewGeneralPaneModel().getTableStylePaneModel()
         .setStyleTree(sampleStyleTree());
      AssemblyPropertyService service = serviceWithTable(mock(TableVSAssembly.class), model);

      service.set("tok", principal(), "Table1",
                  Map.of(TABLE_STYLE_PATH, "4611686018437387905"), "");

      assertEquals("4611686018437387905", model.getTableViewGeneralPaneModel()
         .getTableStylePaneModel().getTableStyle());
   }

   /** Clearing the style (empty string) is a legitimate "no style" request, not a dangling name. */
   @Test
   void allowsClearingTableStyleToEmpty() throws Exception {
      TableViewPropertyDialogModel model = new TableViewPropertyDialogModel();
      model.getTableViewGeneralPaneModel().getTableStylePaneModel()
         .setStyleTree(sampleStyleTree());
      AssemblyPropertyService service = serviceWithTable(mock(TableVSAssembly.class), model);

      assertDoesNotThrow(() -> service.set("tok", principal(), "Table1",
         Map.of(TABLE_STYLE_PATH, ""), ""));
   }


   // ── selectedImage write-time validation (VOF-011) ─────────────────────────
   //
   // ImagePropertyDialogService stores whatever string it is handed, and nothing downstream
   // complains: VSUtil.getVSImage returns null without logging for an unprefixed path, and
   // VSImageModel's noImageFlag stays false because the value is non-blank, so the client asks
   // for an image and gets none -- an empty box with ok:true and no signal. The encoding is not
   // guessable from the tree either: a leaf's value is its type marker joined directly to its
   // data ("^UPLOADED^2.png"), while the "Skin"/"Uploaded" nodes above it are untyped display
   // folders that never appear in the value at all. The slash-joined spelling those folders
   // suggest is accepted as an alias rather than merely refused.

   private static final String SELECTED_IMAGE_PATH =
      "imageGeneralPaneModel.staticImagePaneModel.imagePreviewPaneModel.selectedImage";

   private static TreeNodeModel sampleImageTree() {
      TreeNodeModel current = TreeNodeModel.builder()
         .label("Current Image")
         .data("_CURRENT_IMAGE_")
         .type("current")
         .leaf(true)
         .build();
      TreeNodeModel skin = TreeNodeModel.builder()
         .label("Background1")
         .data("background1.png")
         .type(ImageVSAssemblyInfo.SKIN_IMAGE)
         .leaf(true)
         .build();
      TreeNodeModel skinFolder = TreeNodeModel.builder()
         .label("Skin")
         .data("Skin")
         .leaf(false)
         .children(java.util.List.of(skin))
         .build();
      TreeNodeModel upload = TreeNodeModel.builder()
         .label("2.png")
         .data("2.png")
         .type(ImageVSAssemblyInfo.UPLOADED_IMAGE)
         .leaf(true)
         .build();
      TreeNodeModel uploadFolder = TreeNodeModel.builder()
         .label("Uploaded")
         .data("Uploaded")
         .leaf(false)
         .children(java.util.List.of(upload))
         .build();

      return TreeNodeModel.builder()
         .children(java.util.List.of(current, skinFolder, uploadFolder))
         .build();
   }

   private static ImagePropertyDialogModel imageModelWithTree() {
      return imageModelWithTree(sampleImageTree());
   }

   private static ImagePropertyDialogModel imageModelWithTree(TreeNodeModel tree) {
      return ImagePropertyDialogModel.builder()
         .imageGeneralPaneModel(
            ImageGeneralPaneModel.builder()
               .staticImagePaneModel(
                  StaticImagePaneModel.builder()
                     .imagePreviewPaneModel(
                        ImagePreviewPaneModel.builder()
                           .imageTree(tree)
                           .build())
                     .build())
               .build())
         .build();
   }

   private static String writtenSelectedImage(ImagePropertyDialogService service)
      throws Exception
   {
      ArgumentCaptor<ImagePropertyDialogModel> captor =
         ArgumentCaptor.forClass(ImagePropertyDialogModel.class);
      verify(service).setImagePropertyDialogModel(anyString(), anyString(), captor.capture(),
                                                  any(), any(), any());

      return captor.getValue().imageGeneralPaneModel().staticImagePaneModel()
         .imagePreviewPaneModel().selectedImage();
   }

   @Test
   void allowsAnUploadedImageWrittenInItsCanonicalForm() throws Exception {
      ImagePropertyDialogService imageService = mock(ImagePropertyDialogService.class);
      AssemblyPropertyService service =
         serviceWithImage(mock(ImageVSAssembly.class), imageModelWithTree(), imageService);

      service.set("tok", principal(), "Image1", Map.of(SELECTED_IMAGE_PATH, "^UPLOADED^2.png"), "");

      assertEquals("^UPLOADED^2.png", writtenSelectedImage(imageService));
   }

   @Test
   void allowsASkinImageWrittenInItsCanonicalForm() throws Exception {
      ImagePropertyDialogService imageService = mock(ImagePropertyDialogService.class);
      AssemblyPropertyService service =
         serviceWithImage(mock(ImageVSAssembly.class), imageModelWithTree(), imageService);

      service.set("tok", principal(), "Image1",
                  Map.of(SELECTED_IMAGE_PATH, "^SKIN^background1.png"), "");

      assertEquals("^SKIN^background1.png", writtenSelectedImage(imageService));
   }

   /**
    * The spelling the tree's own shape suggests -- folder label, slash, leaf name. It is not a
    * real value, and writing it verbatim is what produced the blank box; normalizing it costs
    * less than expecting every caller to learn the prefix convention.
    */
   @Test
   void normalizesTheFolderSlashNameSpellingOntoTheCanonicalValue() throws Exception {
      ImagePropertyDialogService imageService = mock(ImagePropertyDialogService.class);
      AssemblyPropertyService service =
         serviceWithImage(mock(ImageVSAssembly.class), imageModelWithTree(), imageService);

      service.set("tok", principal(), "Image1", Map.of(SELECTED_IMAGE_PATH, "Uploaded/2.png"), "");

      assertEquals("^UPLOADED^2.png", writtenSelectedImage(imageService));
   }

   @Test
   void refusesASelectedImageThatNamesNothingInTheTree() throws Exception {
      ImagePropertyDialogService imageService = mock(ImagePropertyDialogService.class);
      AssemblyPropertyService service =
         serviceWithImage(mock(ImageVSAssembly.class), imageModelWithTree(), imageService);

      Exception thrown = assertThrows(IllegalArgumentException.class,
         () -> service.set("tok", principal(), "Image1",
                           Map.of(SELECTED_IMAGE_PATH, "^UPLOADED^nope.png"), ""));

      assertTrue(thrown.getMessage().contains("nope.png"),
                 "must name the bad value: " + thrown.getMessage());
      assertTrue(thrown.getMessage().contains("^UPLOADED^2.png"),
                 "must list what does work: " + thrown.getMessage());
      // The model is still READ before the patch loop; what must not happen is the write.
      verify(imageService, never()).setImagePropertyDialogModel(
         anyString(), anyString(), any(), any(), any(), any());
   }

   /**
    * The alias only ever renames a real image. "Uploaded/nope.png" is as dead as any other
    * unknown name and must not be waved through on the strength of its shape alone.
    */
   @Test
   void refusesTheFolderSlashNameSpellingForAnImageThatDoesNotExist() {
      ImagePropertyDialogService imageService = mock(ImagePropertyDialogService.class);
      AssemblyPropertyService service =
         serviceWithImage(mock(ImageVSAssembly.class), imageModelWithTree(), imageService);

      assertThrows(IllegalArgumentException.class,
         () -> service.set("tok", principal(), "Image1",
                           Map.of(SELECTED_IMAGE_PATH, "Uploaded/nope.png"), ""));
   }

   /**
    * "Current Image" is the preview pane's placeholder for the image the assembly already has,
    * not an image anything can be set to.
    */
   @Test
   void refusesTheCurrentImagePlaceholderNode() {
      ImagePropertyDialogService imageService = mock(ImagePropertyDialogService.class);
      AssemblyPropertyService service =
         serviceWithImage(mock(ImageVSAssembly.class), imageModelWithTree(), imageService);

      assertThrows(IllegalArgumentException.class,
         () -> service.set("tok", principal(), "Image1",
                           Map.of(SELECTED_IMAGE_PATH, "_CURRENT_IMAGE_"), ""));
   }

   /** Clearing the image is a legitimate request, not a dangling name. */
   @Test
   void allowsClearingSelectedImageToEmpty() {
      ImagePropertyDialogService imageService = mock(ImagePropertyDialogService.class);
      AssemblyPropertyService service =
         serviceWithImage(mock(ImageVSAssembly.class), imageModelWithTree(), imageService);

      assertDoesNotThrow(() -> service.set("tok", principal(), "Image1",
                                           Map.of(SELECTED_IMAGE_PATH, ""), ""));
   }

   /**
    * A dynamic reference names a variable or expression resolved at render time, so there is no
    * tree node for it to match and the gate must let it through unresolved.
    */
   @Test
   void allowsADynamicSelectedImageReference() {
      ImagePropertyDialogService imageService = mock(ImagePropertyDialogService.class);
      AssemblyPropertyService service =
         serviceWithImage(mock(ImageVSAssembly.class), imageModelWithTree(), imageService);

      assertDoesNotThrow(() -> service.set("tok", principal(), "Image1",
                                           Map.of(SELECTED_IMAGE_PATH, "$(ImageName)"), ""));
   }

   /** A script expression is the runtime's other dynamic form and passes through the same way. */
   @Test
   void allowsAScriptSelectedImageExpression() {
      ImagePropertyDialogService imageService = mock(ImagePropertyDialogService.class);
      AssemblyPropertyService service =
         serviceWithImage(mock(ImageVSAssembly.class), imageModelWithTree(), imageService);

      assertDoesNotThrow(() -> service.set("tok", principal(), "Image1",
                                           Map.of(SELECTED_IMAGE_PATH, "=\"logo.png\""), ""));
   }

   // Bug #76994: "dynamic" is VSUtil.isDynamicValue -- a whole-value $(...) or =... -- not
   // "starts with $". Each of these is a literal path to DynamicValue.getRValue, renders the
   // same empty box VOF-011 closed, and must be refused like any other unknown name.

   @Test
   void refusesADollarPrefixedLiteralSelectedImage() {
      assertRefusedAsUnknownImage("$logo.png");
   }

   @Test
   void refusesAVariableWithTrailingTextAsSelectedImage() {
      assertRefusedAsUnknownImage("$(X).png");
   }

   @Test
   void refusesAnUnclosedVariableAsSelectedImage() {
      assertRefusedAsUnknownImage("$(X");
   }

   /**
    * An upload may legitimately be named with a leading "$"; its tree value still carries the
    * type marker, so the canonical form is accepted while the bare name is refused and the
    * message points at the canonical one.
    */
   @Test
   void distinguishesADollarNamedUploadFromItsBareName() throws Exception {
      TreeNodeModel upload = TreeNodeModel.builder()
         .label("$logo.png")
         .data("$logo.png")
         .type(ImageVSAssemblyInfo.UPLOADED_IMAGE)
         .leaf(true)
         .build();
      TreeNodeModel uploadFolder = TreeNodeModel.builder()
         .label("Uploaded")
         .data("Uploaded")
         .leaf(false)
         .children(java.util.List.of(upload))
         .build();
      TreeNodeModel tree = TreeNodeModel.builder()
         .children(java.util.List.of(uploadFolder))
         .build();

      ImagePropertyDialogService imageService = mock(ImagePropertyDialogService.class);
      AssemblyPropertyService service =
         serviceWithImage(mock(ImageVSAssembly.class), imageModelWithTree(tree), imageService);

      service.set("tok", principal(), "Image1",
                  Map.of(SELECTED_IMAGE_PATH, "^UPLOADED^$logo.png"), "");
      assertEquals("^UPLOADED^$logo.png", writtenSelectedImage(imageService));

      ImagePropertyDialogService refusingService = mock(ImagePropertyDialogService.class);
      AssemblyPropertyService refusing =
         serviceWithImage(mock(ImageVSAssembly.class), imageModelWithTree(tree), refusingService);

      Exception thrown = assertThrows(IllegalArgumentException.class,
         () -> refusing.set("tok", principal(), "Image1",
                            Map.of(SELECTED_IMAGE_PATH, "$logo.png"), ""));

      assertTrue(thrown.getMessage().contains("^UPLOADED^$logo.png"),
                 "must list the canonical value: " + thrown.getMessage());
   }

   // A group container's background image is a plain String (GroupContainerVSAssemblyInfo
   // .setBackgroundImage), never a DynamicValue, so nothing resolves "$(X)" or "=..." there:
   // stored verbatim it is the same empty box, and must meet the tree check like any literal.

   private static final String GROUP_SELECTED_IMAGE_PATH =
      "groupContainerGeneralPane.staticImagePane.imagePreviewPaneModel.selectedImage";

   private static GroupContainerPropertyDialogModel groupContainerModelWithTree() {
      GroupContainerPropertyDialogModel model = new GroupContainerPropertyDialogModel();
      model.getGroupContainerGeneralPane().setStaticImagePane(
         StaticImagePaneModel.builder()
            .imagePreviewPaneModel(
               ImagePreviewPaneModel.builder()
                  .imageTree(sampleImageTree())
                  .build())
            .build());
      return model;
   }

   @Test
   void refusesAVariableAsAGroupContainerBackgroundImage() {
      assertRefusedAsUnknownGroupContainerImage("$(X)");
   }

   @Test
   void refusesAScriptAsAGroupContainerBackgroundImage() {
      assertRefusedAsUnknownGroupContainerImage("=\"a.png\"");
   }

   @Test
   void allowsACanonicalGroupContainerBackgroundImage() throws Exception {
      GroupContainerPropertyDialogModel model = groupContainerModelWithTree();
      AssemblyPropertyService service =
         serviceWith(mock(GroupContainerVSAssembly.class), model);

      service.set("tok", principal(), "Group1",
                  Map.of(GROUP_SELECTED_IMAGE_PATH, "^UPLOADED^2.png"), "");

      assertEquals("^UPLOADED^2.png", model.getGroupContainerGeneralPane().getStaticImagePane()
         .imagePreviewPaneModel().selectedImage());
   }

   @Test
   void allowsClearingAGroupContainerBackgroundImage() {
      AssemblyPropertyService service =
         serviceWith(mock(GroupContainerVSAssembly.class), groupContainerModelWithTree());

      assertDoesNotThrow(() -> service.set("tok", principal(), "Group1",
                                           Map.of(GROUP_SELECTED_IMAGE_PATH, ""), ""));
   }

   private void assertRefusedAsUnknownGroupContainerImage(String value) {
      AssemblyPropertyService service =
         serviceWith(mock(GroupContainerVSAssembly.class), groupContainerModelWithTree());

      Exception thrown = assertThrows(IllegalArgumentException.class,
         () -> service.set("tok", principal(), "Group1",
                           Map.of(GROUP_SELECTED_IMAGE_PATH, value), ""));

      assertTrue(thrown.getMessage().contains(value),
                 "must name the bad value: " + thrown.getMessage());
      assertTrue(thrown.getMessage().contains("^UPLOADED^2.png"),
                 "must list what does work: " + thrown.getMessage());
   }

   private void assertRefusedAsUnknownImage(String value) {
      ImagePropertyDialogService imageService = mock(ImagePropertyDialogService.class);
      AssemblyPropertyService service =
         serviceWithImage(mock(ImageVSAssembly.class), imageModelWithTree(), imageService);

      Exception thrown = assertThrows(IllegalArgumentException.class,
         () -> service.set("tok", principal(), "Image1",
                           Map.of(SELECTED_IMAGE_PATH, value), ""));

      assertTrue(thrown.getMessage().contains(value),
                 "must name the bad value: " + thrown.getMessage());
      assertTrue(thrown.getMessage().contains("^UPLOADED^2.png"),
                 "must list what does work: " + thrown.getMessage());
   }

   // ── harness ───────────────────────────────────────────────────────────────

   private static AssemblyPropertyService serviceWith(VSAssembly assembly, Object model) {
      return serviceWith(assembly, model, null, null);
   }

   private static AssemblyPropertyService serviceWithTextInput(
      VSAssembly assembly, TextInputPropertyDialogModel model, Worksheet baseWorksheet)
   {
      return serviceWith(assembly, model, model, baseWorksheet);
   }

   private static AssemblyPropertyService serviceWithCheckbox(
      VSAssembly assembly, CheckboxPropertyDialogModel model, Worksheet baseWorksheet)
   {
      return serviceWith(assembly, model, model, baseWorksheet);
   }

   private static AssemblyPropertyService serviceWithRadioButton(
      VSAssembly assembly, RadioButtonPropertyDialogModel model, Worksheet baseWorksheet)
   {
      return serviceWith(assembly, model, model, baseWorksheet);
   }

   private static AssemblyPropertyService serviceWithCombobox(
      VSAssembly assembly, ComboboxPropertyDialogModel model, Worksheet baseWorksheet)
   {
      return serviceWith(assembly, model, model, baseWorksheet);
   }

   private static AssemblyPropertyService serviceWithSelectionList(
      VSAssembly assembly, SelectionListPropertyDialogModel model)
   {
      return serviceWith(assembly, model, null, null);
   }

   private static AssemblyPropertyService serviceWithSelectionTree(
      VSAssembly assembly, SelectionTreePropertyDialogModel model)
   {
      return serviceWith(assembly, model, null, null);
   }

   private static AssemblyPropertyService serviceWithCalendar(
      VSAssembly assembly, CalendarPropertyDialogModel model)
   {
      return serviceWith(assembly, model, null, null);
   }

   private static AssemblyPropertyService serviceWithRangeSlider(
      VSAssembly assembly, RangeSliderPropertyDialogModel model)
   {
      return serviceWith(assembly, model, null, null);
   }

   private static AssemblyPropertyService serviceWithTable(
      VSAssembly assembly, TableViewPropertyDialogModel model)
   {
      return serviceWith(assembly, model, null, null);
   }

   private static AssemblyPropertyService serviceWithImage(
      VSAssembly assembly, ImagePropertyDialogModel model, ImagePropertyDialogService imageService)
   {
      try {
         when(imageService.getImagePropertyDialogModel(anyString(), anyString(),
                                                       any(Principal.class)))
            .thenReturn(model);
      }
      catch(Exception e) {
         throw new IllegalStateException(e);
      }

      return serviceWith(assembly, model, null, null, imageService);
   }

   private static AssemblyPropertyService serviceWithCalcTable(
      VSAssembly assembly, CalcTablePropertyDialogModel model)
   {
      return serviceWith(assembly, model, null, null);
   }

   private static AssemblyPropertyService serviceWith(
      VSAssembly assembly, Object model, Object inputModel,
      Worksheet baseWorksheet)
   {
      return serviceWith(assembly, model, inputModel, baseWorksheet,
                         mock(ImagePropertyDialogService.class));
   }

   private static AssemblyPropertyService serviceWith(
      VSAssembly assembly, Object model, Object inputModel,
      Worksheet baseWorksheet, ImagePropertyDialogService imageService)
   {
      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getAssembly(anyString())).thenReturn(assembly);
      when(vs.getBaseWorksheet()).thenReturn(baseWorksheet);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(rvs.getID()).thenReturn("rt1");

      ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);

      try {
         when(sessions.resolve(anyString(), any(Principal.class))).thenReturn(rvs);
         doAnswer(invocation -> {
            ViewsheetSessionService.Mutation mutation = invocation.getArgument(2);
            mutation.run(rvs, "rt1", null);
            return null;
         }).when(sessions).mutate(anyString(), any(Principal.class), any());
      }
      catch(Exception e) {
         throw new IllegalStateException(e);
      }

      GaugePropertyDialogService gauge = mock(GaugePropertyDialogService.class);

      if(model instanceof GaugePropertyDialogModel gaugeModel) {
         try {
            when(gauge.getGaugePropertyDialogModel(anyString(), anyString(),
                                                   any(Principal.class)))
               .thenReturn(gaugeModel);
         }
         catch(Exception e) {
            throw new IllegalStateException(e);
         }
      }

      SelectionListPropertyDialogService selectionList =
         mock(SelectionListPropertyDialogService.class);

      if(model instanceof SelectionListPropertyDialogModel selectionListModel) {
         try {
            when(selectionList.getSelectionListPropertyModel(anyString(), anyString(),
                                                              any(Principal.class)))
               .thenReturn(selectionListModel);
         }
         catch(Exception e) {
            throw new IllegalStateException(e);
         }
      }

      SelectionTreePropertyDialogService selectionTree =
         mock(SelectionTreePropertyDialogService.class);

      if(model instanceof SelectionTreePropertyDialogModel selectionTreeModel) {
         try {
            when(selectionTree.getSelectionTreePropertyModel(anyString(), anyString(),
                                                              any(Principal.class)))
               .thenReturn(selectionTreeModel);
         }
         catch(Exception e) {
            throw new IllegalStateException(e);
         }
      }

      CalendarPropertyDialogService calendar = mock(CalendarPropertyDialogService.class);

      if(model instanceof CalendarPropertyDialogModel calendarModel) {
         try {
            when(calendar.getCalendarPropertyModel(anyString(), anyString(),
                                                   any(Principal.class)))
               .thenReturn(calendarModel);
         }
         catch(Exception e) {
            throw new IllegalStateException(e);
         }
      }

      inetsoft.web.viewsheet.service.VSInputService inputService =
         mock(inetsoft.web.viewsheet.service.VSInputService.class);

      try {
         if(inputModel instanceof TextInputPropertyDialogModel textInputModel) {
            when(inputService.getTextInputPropertyDialogModel(anyString(), anyString(),
                                                               any(Principal.class)))
               .thenReturn(textInputModel);
         }
         else if(inputModel instanceof CheckboxPropertyDialogModel checkboxModel) {
            when(inputService.getCheckBoxPropertyModel(anyString(), anyString(),
                                                        any(Principal.class)))
               .thenReturn(checkboxModel);
         }
         else if(inputModel instanceof RadioButtonPropertyDialogModel radioButtonModel) {
            when(inputService.getRadioButtonPropertyModel(anyString(), anyString(),
                                                           any(Principal.class)))
               .thenReturn(radioButtonModel);
         }
         else if(inputModel instanceof ComboboxPropertyDialogModel comboboxModel) {
            when(inputService.getComboboxPropertyDialogModel(anyString(), anyString(),
                                                              any(Principal.class)))
               .thenReturn(comboboxModel);
         }
      }
      catch(Exception e) {
         throw new IllegalStateException(e);
      }

      ChartPropertyDialogService chart = mock(ChartPropertyDialogService.class);

      if(model instanceof ChartPropertyDialogModel chartModel) {
         try {
            when(chart.getChartPropertyDialogModel(anyString(), anyString(),
                                                   any(Principal.class)))
               .thenReturn(chartModel);
         }
         catch(Exception e) {
            throw new IllegalStateException(e);
         }
      }

      TableViewPropertyDialogService table = mock(TableViewPropertyDialogService.class);

      if(model instanceof TableViewPropertyDialogModel tableModel) {
         try {
            when(table.getTableViewPropertyDialogModel(anyString(), anyString(),
                                                        any(Principal.class)))
               .thenReturn(tableModel);
         }
         catch(Exception e) {
            throw new IllegalStateException(e);
         }
      }

      CalcTablePropertyDialogService calcTable = mock(CalcTablePropertyDialogService.class);

      if(model instanceof CalcTablePropertyDialogModel calcTableModel) {
         try {
            when(calcTable.getCalcTablePropertyDialogModel(anyString(), anyString(), anyDouble(),
                                                            any(Principal.class)))
               .thenReturn(calcTableModel);
         }
         catch(Exception e) {
            throw new IllegalStateException(e);
         }
      }

      GroupContainerPropertyDialogService groupContainer =
         mock(GroupContainerPropertyDialogService.class);

      if(model instanceof GroupContainerPropertyDialogModel groupContainerModel) {
         try {
            when(groupContainer.getGroupContainerPropertyDialogModel(anyString(), anyString(),
                                                                     any(Principal.class)))
               .thenReturn(groupContainerModel);
         }
         catch(Exception e) {
            throw new IllegalStateException(e);
         }
      }

      RangeSliderPropertyDialogService rangeSlider =
         mock(RangeSliderPropertyDialogService.class);

      if(model instanceof RangeSliderPropertyDialogModel rangeSliderModel) {
         try {
            when(rangeSlider.getRangeSliderPropertyModel(anyString(), anyString(),
                                                          any(Principal.class)))
               .thenReturn(rangeSliderModel);
         }
         catch(Exception e) {
            throw new IllegalStateException(e);
         }
      }

      return new AssemblyPropertyService(
         sessions, gauge, imageService,
         mock(TextPropertyDialogService.class),
         chart, table,
         mock(CrosstabPropertyDialogService.class),
         selectionList,
         selectionTree,
         inputService,
         rangeSlider,
         calendar, mock(TabPropertyDialogService.class),
         calcTable,
         groupContainer,
         mock(LinePropertyDialogService.class), mock(OvalPropertyDialogService.class),
         mock(RectanglePropertyDialogService.class),
         mock(SelectionContainerPropertyDialogService.class),
         mock(SubmitPropertyDialogService.class));
   }

   private static Principal principal() {
      return () -> "admin";
   }
}
