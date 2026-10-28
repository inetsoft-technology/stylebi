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

import inetsoft.graph.GraphConstants;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.test.*;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.DefaultVariableAssembly;
import inetsoft.uql.asset.EmbeddedTableAssembly;
import inetsoft.uql.asset.TableAssembly;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.uql.viewsheet.*;
import inetsoft.web.binding.handler.VSColumnHandler;
import inetsoft.web.composer.model.TreeNodeModel;
import inetsoft.web.composer.model.vs.CalcTablePropertyDialogModel;
import inetsoft.web.composer.model.vs.CalendarPropertyDialogModel;
import inetsoft.web.composer.model.vs.CheckboxPropertyDialogModel;
import inetsoft.web.composer.model.vs.ChartLinePaneModel;
import inetsoft.web.composer.model.vs.ChartPropertyDialogModel;
import inetsoft.web.composer.model.vs.ComboboxPropertyDialogModel;
import inetsoft.web.composer.model.vs.CrosstabPropertyDialogModel;
import inetsoft.web.composer.model.vs.DynamicValueModel;
import inetsoft.web.composer.model.vs.GaugePropertyDialogModel;
import inetsoft.web.composer.model.vs.SizePositionPaneModel;
import inetsoft.web.composer.model.vs.RectanglePropertyDialogModel;
import inetsoft.web.composer.model.vs.OvalPropertyDialogModel;
import inetsoft.web.composer.model.vs.LinePropertyDialogModel;
import inetsoft.web.composer.model.vs.GroupContainerPropertyDialogModel;
import inetsoft.web.composer.model.vs.ImageAdvancedPaneModel;
import inetsoft.web.composer.model.vs.ImageGeneralPaneModel;
import inetsoft.web.composer.model.vs.ImagePreviewPaneModel;
import inetsoft.web.composer.model.vs.ImagePropertyDialogModel;
import inetsoft.web.composer.model.vs.ImageScalePaneModel;
import inetsoft.web.composer.model.vs.StaticImagePaneModel;
import inetsoft.web.composer.model.vs.NumericRangePaneModel;
import inetsoft.web.composer.model.vs.OutputColumnRefModel;
import inetsoft.web.composer.model.vs.RadioButtonPropertyDialogModel;
import inetsoft.web.composer.model.vs.RangeSliderPropertyDialogModel;
import inetsoft.web.composer.model.vs.SliderPropertyDialogModel;
import inetsoft.web.composer.model.vs.SpinnerPropertyDialogModel;
import inetsoft.web.composer.model.vs.SelectionContainerPropertyDialogModel;
import inetsoft.web.composer.model.vs.SelectionListPropertyDialogModel;
import inetsoft.web.composer.model.vs.SelectionTreePropertyDialogModel;
import inetsoft.web.composer.model.vs.TableViewPropertyDialogModel;
import inetsoft.web.composer.model.vs.TextInputPropertyDialogModel;
import inetsoft.web.composer.model.vs.TextPropertyDialogModel;
import inetsoft.web.composer.model.vs.TipCustomizeDialogModel;
import inetsoft.web.composer.model.vs.TipPaneModel;
import inetsoft.web.composer.model.vs.VariableListDialogModel;
import inetsoft.web.composer.vs.dialog.*;
import inetsoft.uql.viewsheet.internal.ImageVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.SelectionTreeVSAssemblyInfo;
import inetsoft.web.viewsheet.service.VSOutputService;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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

   /**
    * The Selection Container's class is CurrentSelectionVSAssembly, which strips to
    * "currentselection" -- a key nothing registers. It must resolve to "selectioncontainer", the
    * key PropertyAliases and the bindings use, or none of its aliases are reachable.
    */
   @Test
   @SuppressWarnings("unchecked")
   void resolvesACurrentSelectionAssemblyAsSelectionContainer() throws Exception {
      SelectionContainerPropertyDialogModel model = new SelectionContainerPropertyDialogModel();
      AssemblyPropertyService service =
         serviceWith(mock(CurrentSelectionVSAssembly.class), model);

      Map<String, Object> listed = service.list("tok", principal(), "SelectionContainer1");

      assertEquals("selectioncontainer", listed.get("assemblyType"));
      java.util.List<String> names =
         ((java.util.List<Map<String, Object>>) listed.get("properties")).stream()
            .map(p -> (String) p.get("name"))
            .toList();
      assertTrue(names.contains("showCurrentSelection"), "listed: " + names);
      assertTrue(names.contains("adhocEnabled"), "listed: " + names);
   }

   @Test
   @SuppressWarnings("unchecked")
   void getsSelectionContainerValues() throws Exception {
      SelectionContainerPropertyDialogModel model = new SelectionContainerPropertyDialogModel();
      model.getSelectionContainerGeneralPaneModel().setShowCurrentSelection(true);
      AssemblyPropertyService service =
         serviceWith(mock(CurrentSelectionVSAssembly.class), model);

      Map<String, Object> values =
         (Map<String, Object>) service.get("tok", principal(), "SelectionContainer1", false);

      assertEquals(true, values.get("showCurrentSelection"));
      assertEquals(false, values.get("adhocEnabled"));
   }

   @Test
   void setsASelectionContainerAlias() throws Exception {
      SelectionContainerPropertyDialogModel model = new SelectionContainerPropertyDialogModel();
      AssemblyPropertyService service =
         serviceWith(mock(CurrentSelectionVSAssembly.class), model);

      service.set("tok", principal(), "SelectionContainer1", Map.of("adhocEnabled", true), "");

      assertTrue(model.getSelectionContainerGeneralPaneModel().isAdhocEnabled());
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

   // ── gauge face write-time validation (bug #79090) ────────────────────────
   //
   // A face VSGauge.getGauge cannot resolve used to be stored verbatim and then broke every
   // render/export of the viewsheet. Valid = what the engine can render: the advertised
   // prefixes plus legacy ids that still resolve.

   private static int gaugeFace(GaugePropertyDialogModel model) {
      return model.getGaugeGeneralPaneModel().getFacePaneModel().getFace();
   }

   private void assertGaugeFaceRefused(Object bad) {
      GaugePropertyDialogModel model = new GaugePropertyDialogModel();
      model.getGaugeGeneralPaneModel().getFacePaneModel().setFace(10120);
      AssemblyPropertyService service = serviceWith(mock(GaugeVSAssembly.class), model);

      Exception thrown = assertThrows(IllegalArgumentException.class,
         () -> service.set("tok", principal(), "Gauge1", Map.of("face", bad), ""));

      assertTrue(thrown.getMessage().contains("face"), thrown.getMessage());
      assertTrue(thrown.getMessage().contains("10120"),
                 "must list the valid ids: " + thrown.getMessage());
      assertEquals(10120, gaugeFace(model), "a refused patch must not change the face");
   }

   @Test
   void refusesAnUnknownGaugeFace() {
      assertGaugeFaceRefused(99999);
   }

   @Test
   void refusesAGaugeFaceThatIsAThemeSuffixedMapKeyNotAStoredPrefix() {
      assertGaugeFaceRefused(10125);
      assertGaugeFaceRefused(10126);
   }

   @Test
   void refusesAFractionalOrNonNumericGaugeFace() {
      assertGaugeFaceRefused(10120.5);
      assertGaugeFaceRefused("abc");
   }

   @Test
   void refusesAnOutOfRangeOrNonFiniteGaugeFace() {
      assertGaugeFaceRefused(1.0E12);
      assertGaugeFaceRefused(Double.NaN);
      assertGaugeFaceRefused(Double.POSITIVE_INFINITY);
   }

   @Test
   void allowsAWholeValuedDoubleGaugeFace() throws Exception {
      GaugePropertyDialogModel model = new GaugePropertyDialogModel();
      AssemblyPropertyService service = serviceWith(mock(GaugeVSAssembly.class), model);

      service.set("tok", principal(), "Gauge1", Map.of("face", 90820.0), "");
      assertEquals(90820, gaugeFace(model));

      service.set("tok", principal(), "Gauge1", Map.of("face", "10120.0"), "");
      assertEquals(10120, gaugeFace(model));
   }

   @Test
   void allowsAnAdvertisedGaugeFaceAsNumberOrNumericString() throws Exception {
      GaugePropertyDialogModel model = new GaugePropertyDialogModel();
      AssemblyPropertyService service = serviceWith(mock(GaugeVSAssembly.class), model);

      service.set("tok", principal(), "Gauge1", Map.of("face", 90820), "");
      assertEquals(90820, gaugeFace(model));

      service.set("tok", principal(), "Gauge1", Map.of("face", "10120"), "");
      assertEquals(10120, gaugeFace(model));
   }

   @Test
   void allowsAGaugeFaceViaTheRawPath() throws Exception {
      GaugePropertyDialogModel model = new GaugePropertyDialogModel();
      AssemblyPropertyService service = serviceWith(mock(GaugeVSAssembly.class), model);

      service.set("tok", principal(), "Gauge1",
                  Map.of("gaugeGeneralPaneModel.facePaneModel.face", 10220), "");

      assertEquals(10220, gaugeFace(model));
   }

   @Test
   void refusesAnUnknownGaugeFaceViaTheRawPath() {
      GaugePropertyDialogModel model = new GaugePropertyDialogModel();
      AssemblyPropertyService service = serviceWith(mock(GaugeVSAssembly.class), model);

      Exception thrown = assertThrows(IllegalArgumentException.class,
         () -> service.set("tok", principal(), "Gauge1",
                           Map.of("gaugeGeneralPaneModel.facePaneModel.face", 99999), ""));

      assertTrue(thrown.getMessage().contains("face"), thrown.getMessage());
   }

   /** A stale legacy id the engine still renders must round-trip. */
   @Test
   void allowsALegacyGaugeFaceTheEngineStillRenders() throws Exception {
      GaugePropertyDialogModel model = new GaugePropertyDialogModel();
      AssemblyPropertyService service = serviceWith(mock(GaugeVSAssembly.class), model);

      service.set("tok", principal(), "Gauge1", Map.of("face", 40), "");

      assertEquals(40, gaugeFace(model));
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

   // Bug #78198: the order check compares each static entry against the last STATIC one, so a
   // dynamic slot between two statics does not hide an out-of-order pair.
   private void assertGaugeRangeValuesRefused(String expectedIndex, String expectedValue,
                                              String... values)
   {
      GaugePropertyDialogModel model = new GaugePropertyDialogModel();
      AssemblyPropertyService service = serviceWith(mock(GaugeVSAssembly.class), model);

      Exception thrown = assertThrows(IllegalArgumentException.class,
         () -> service.set("tok", principal(), "Gauge1",
            Map.of("gaugeAdvancedPaneModel.rangePaneModel.rangeValues",
                   java.util.List.of(values)),
            ""));

      assertTrue(thrown.getMessage().contains("must be >= rangeValues[" + expectedIndex + "] (" +
                 expectedValue + ")"), thrown.getMessage());
   }

   private void assertGaugeRangeValuesAccepted(String... values) {
      GaugePropertyDialogModel model = new GaugePropertyDialogModel();
      AssemblyPropertyService service = serviceWith(mock(GaugeVSAssembly.class), model);

      assertDoesNotThrow(() -> service.set("tok", principal(), "Gauge1",
         Map.of("gaugeAdvancedPaneModel.rangePaneModel.rangeValues", java.util.List.of(values)),
         ""));
   }

   @Test
   void refusesOutOfOrderStaticsAcrossADynamicGaugeRangeValue() {
      assertGaugeRangeValuesRefused("0", "90000000",
                                    "90000000", "$(Spinner1)", "60000000", "", "");
   }

   @Test
   void refusesOutOfOrderStaticsAcrossSeveralDynamicGaugeRangeValues() {
      assertGaugeRangeValuesRefused("0", "90000000", "90000000", "$(A)", "$(B)", "60000000");
   }

   @Test
   void allowsInOrderAndEqualStaticsAcrossADynamicGaugeRangeValue() {
      assertGaugeRangeValuesAccepted("60000000", "$(Spinner1)", "90000000");
      assertGaugeRangeValuesAccepted("60000000", "$(Spinner1)", "60000000");
   }

   @Test
   void keepsTheAdjacentStaticGaugeRangeValueOrderMessage() {
      assertGaugeRangeValuesRefused("0", "90000000", "90000000", "70000000", "60000000");
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
    * Bug #78263: an unresolvable input binding reached the two pre-write guards through the
    * static resolver, whose MessageException escaped as a 500. It must surface as a named
    * IllegalArgumentException (400) and nothing may be written.
    */
   private void assertUnresolvableBindingRefused(
      AssemblyPropertyService service, TextInputPropertyDialogModel model,
      Map<String, Object> patch, String expected)
   {
      Exception thrown = assertThrows(IllegalArgumentException.class,
         () -> service.set("tok", principal(), "Input", patch, ""));
      assertTrue(thrown.getMessage().contains(expected),
                 "must name the unresolved binding: " + thrown.getMessage());
      assertFalse(thrown.getMessage().startsWith("Setting"),
                  "must be refused by the pre-write guard, before the setter runs");
   }

   @Test
   void bug78263ColumnValueUnknownVariableWithVariableFlagIsRefusedByName() {
      TextInputPropertyDialogModel model = new TextInputPropertyDialogModel();
      AssemblyPropertyService service =
         serviceWithTextInput(mock(TextInputVSAssembly.class), model, new Worksheet());
      Map<String, Object> patch = new LinkedHashMap<>();
      patch.put("dataInputPaneModel.columnValue", "$(Nope)");
      patch.put("dataInputPaneModel.variable", true);
      assertUnresolvableBindingRefused(service, model, patch, "Unknown variable 'Nope'");
   }

   @Test
   void bug78263ColumnValueUnknownVariableAloneIsRefusedByName() {
      TextInputPropertyDialogModel model = new TextInputPropertyDialogModel();
      AssemblyPropertyService service =
         serviceWithTextInput(mock(TextInputVSAssembly.class), model, new Worksheet());
      Map<String, Object> patch = new LinkedHashMap<>();
      patch.put("dataInputPaneModel.columnValue", "$(Nope)");
      assertUnresolvableBindingRefused(service, model, patch, "Unknown variable 'Nope'");
   }

   @Test
   void bug78579UnknownVariableRefusalNamesTheDataInputField() {
      TextInputPropertyDialogModel model = new TextInputPropertyDialogModel();
      AssemblyPropertyService service =
         serviceWithTextInput(mock(TextInputVSAssembly.class), model, new Worksheet());
      Map<String, Object> patch = new LinkedHashMap<>();
      patch.put("dataInputPaneModel.columnValue", "$(Nope)");

      Exception thrown = assertThrows(IllegalArgumentException.class,
         () -> service.set("tok", principal(), "Input", patch, ""));
      assertTrue(thrown.getMessage().contains("columnValue"), thrown.getMessage());
      assertTrue(thrown.getMessage().contains("Nope"), thrown.getMessage());
   }

   @Test
   void bug78263TableUnknownVariableWithVariableFlagIsRefusedByName() {
      TextInputPropertyDialogModel model = new TextInputPropertyDialogModel();
      AssemblyPropertyService service =
         serviceWithTextInput(mock(TextInputVSAssembly.class), model, new Worksheet());
      Map<String, Object> patch = new LinkedHashMap<>();
      patch.put("dataInputPaneModel.table", "$(Nope)");
      patch.put("dataInputPaneModel.variable", true);
      assertUnresolvableBindingRefused(service, model, patch, "Unknown variable 'Nope'");
   }

   @Test
   void bug78263UnknownPlainTableWithVariableFlagIsRefusedByName() {
      TextInputPropertyDialogModel model = new TextInputPropertyDialogModel();
      AssemblyPropertyService service =
         serviceWithTextInput(mock(TextInputVSAssembly.class), model, new Worksheet());
      Map<String, Object> patch = new LinkedHashMap<>();
      patch.put("dataInputPaneModel.table", "Nope");
      patch.put("dataInputPaneModel.variable", true);
      assertUnresolvableBindingRefused(service, model, patch, "Unknown table binding 'Nope'");
   }

   @Test
   void bug78263UnknownVariableOnComboboxIsRefusedByName() {
      ComboboxPropertyDialogModel model = new ComboboxPropertyDialogModel();
      AssemblyPropertyService service =
         serviceWithCombobox(mock(ComboBoxVSAssembly.class), model, new Worksheet());
      Map<String, Object> patch = new LinkedHashMap<>();
      patch.put("dataInputPaneModel.table", "$(Nope)");
      patch.put("dataInputPaneModel.variable", true);

      Exception thrown = assertThrows(IllegalArgumentException.class,
         () -> service.set("tok", principal(), "Input", patch, ""));
      assertTrue(thrown.getMessage().contains("Unknown variable 'Nope'"), thrown.getMessage());
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

   // The writable rows of an embedded table are getHeaderRowCount()..getRowCount()-1 -- the
   // same bounds InputVSAssemblyInfo.update() (InputVSAssemblyInfo.java:544) and the write-back
   // sandbox guard (ViewsheetSandbox.java:9173) apply. The header row and rowCount itself are
   // both rejected there, so accepting them here is the VOF-013 silent no-op (bug #76803 reopen).

   @Test
   void vof013HeaderRowValueIsRefused() {
      TextInputPropertyDialogModel model = new TextInputPropertyDialogModel();
      model.getDataInputPaneModel().setTable(EMBEDDED_TABLE);
      AssemblyPropertyService service =
         serviceWithTextInput(mock(TextInputVSAssembly.class), model, embeddedTableWorksheet());

      Map<String, Object> patch = new LinkedHashMap<>();
      patch.put("dataInputPaneModel.columnValue", "state");
      patch.put("dataInputPaneModel.rowValue", "0");

      Exception thrown = assertThrows(IllegalArgumentException.class,
         () -> service.set("tok", principal(), "StartDateInput", patch, ""));

      assertTrue(thrown.getMessage().contains("dataInputPaneModel.rowValue"),
                 "must name the refused field: " + thrown.getMessage());
   }

   @Test
   void vof013RowValueEqualToRowCountIsRefused() {
      TextInputPropertyDialogModel model = new TextInputPropertyDialogModel();
      model.getDataInputPaneModel().setTable(EMBEDDED_TABLE);
      AssemblyPropertyService service =
         serviceWithTextInput(mock(TextInputVSAssembly.class), model, embeddedTableWorksheet());

      Map<String, Object> patch = new LinkedHashMap<>();
      patch.put("dataInputPaneModel.columnValue", "state");
      patch.put("dataInputPaneModel.rowValue", "3");

      Exception thrown = assertThrows(IllegalArgumentException.class,
         () -> service.set("tok", principal(), "StartDateInput", patch, ""));

      assertTrue(thrown.getMessage().contains("dataInputPaneModel.rowValue"),
                 "must name the refused field: " + thrown.getMessage());
   }

   /** Control for the two refusals above: the last data row (rowCount - 1) is writable. */
   @Test
   void lastDataRowValueStillSucceeds() throws Exception {
      TextInputPropertyDialogModel model = new TextInputPropertyDialogModel();
      model.getDataInputPaneModel().setTable(EMBEDDED_TABLE);
      AssemblyPropertyService service =
         serviceWithTextInput(mock(TextInputVSAssembly.class), model, embeddedTableWorksheet());

      Map<String, Object> patch = new LinkedHashMap<>();
      patch.put("dataInputPaneModel.columnValue", "state");
      patch.put("dataInputPaneModel.rowValue", "2");

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

   // ── assembly-reference values: tipView / flyOverViews / popComponent (bug #78537) ──
   //
   // The native dialogs offer these through a <select> over the pane's own list, and the
   // *PropertyDialogService setters store whatever arrives -- so a name outside the list was
   // written verbatim, read back masked (null on a chart), and the prior view was lost.

   private static ChartPropertyDialogModel chartWithTipView(String tipView, boolean tipOption) {
      ChartPropertyDialogModel model = new ChartPropertyDialogModel();
      TipPaneModel tip = model.getChartGeneralPaneModel().getTipPaneModel();
      tip.setPopComponents(new String[] { "T1", "Img1" });
      tip.setFlyoverComponents(new String[] { "T1", "T2" });
      tip.setTipView(tipView);
      tip.setTipOption(tipOption);
      return model;
   }

   private static void assertNothingWrittenBy(List<Object> services) {
      for(Object service : services) {
         assertTrue(mockingDetails(service).getInvocations().stream()
                       .noneMatch(call -> call.getMethod().getName().startsWith("set")),
                    "a refused patch must not reach the native setter: " + service);
      }
   }

   @Test
   void refusesAChartTipViewOutsideItsPopComponentsAndKeepsThePriorView() throws Exception {
      for(String bad : new String[] { "NoSuchAssembly_xyz", "R1", "$(C1)", "=Chart1.x" }) {
         ChartPropertyDialogModel model = chartWithTipView("T1", true);
         AssemblyPropertyService service = serviceWith(mock(ChartVSAssembly.class), model);

         Exception e = assertThrows(IllegalArgumentException.class,
            () -> service.set("tok", principal(), "Chart1", Map.of("tipView", bad), ""));

         assertTrue(e.getMessage().contains("'tipView'"), e.getMessage());
         assertTrue(e.getMessage().contains("chartGeneralPaneModel.tipPaneModel.tipView"),
                    e.getMessage());
         assertTrue(e.getMessage().contains("\"T1\", \"Img1\""), e.getMessage());
         assertTrue(e.getMessage().contains("previous value is kept"), e.getMessage());
         assertEquals("T1", model.getChartGeneralPaneModel().getTipPaneModel().getTipView(), bad);
         assertNothingWrittenBy(lastDialogServices);
      }
   }

   @Test
   void refusesADynamicTipViewNamingTheScriptRoute() {
      AssemblyPropertyService service =
         serviceWith(mock(ChartVSAssembly.class), chartWithTipView("T1", true));

      Exception e = assertThrows(IllegalArgumentException.class,
         () -> service.set("tok", principal(), "Chart1", Map.of("tipView", "$(C1)"), ""));

      assertTrue(e.getMessage().contains("script"), e.getMessage());
   }

   @Test
   void refusesAnUnknownTableAndCrosstabTipView() {
      TableViewPropertyDialogModel table = new TableViewPropertyDialogModel();
      table.getTableAdvancedPaneModel().getTipPaneModel().setPopComponents(new String[] { "T2" });
      table.getTableAdvancedPaneModel().getTipPaneModel().setTipView("T2");
      AssemblyPropertyService tableService = serviceWithTable(mock(TableVSAssembly.class), table);

      assertThrows(IllegalArgumentException.class,
         () -> tableService.set("tok", principal(), "Table1", Map.of("tipView", "Ghost"), ""));
      assertEquals("T2", table.getTableAdvancedPaneModel().getTipPaneModel().getTipView());
      assertNothingWrittenBy(lastDialogServices);

      CrosstabPropertyDialogModel crosstab = new CrosstabPropertyDialogModel();
      crosstab.getCrosstabAdvancedPaneModel().getTipPaneModel()
         .setPopComponents(new String[] { "T2" });
      AssemblyPropertyService crosstabService =
         serviceWith(mock(CrosstabVSAssembly.class), crosstab);

      assertThrows(IllegalArgumentException.class,
         () -> crosstabService.set("tok", principal(), "Crosstab1", Map.of("tipView", "Ghost"),
                                   ""));
      assertNull(crosstab.getCrosstabAdvancedPaneModel().getTipPaneModel().getTipView());
      assertNothingWrittenBy(lastDialogServices);
   }

   @Test
   void refusesOnlyTheFlyOverViewsEntriesOutsideFlyoverComponents() {
      ChartPropertyDialogModel model = chartWithTipView("T1", true);
      model.getChartGeneralPaneModel().getTipPaneModel().setFlyOverViews(new String[] { "T2" });
      AssemblyPropertyService service = serviceWith(mock(ChartVSAssembly.class), model);

      Exception e = assertThrows(IllegalArgumentException.class,
         () -> service.set("tok", principal(), "Chart1",
                           Map.of("flyOverViews", List.of("T1", "Nope")), ""));

      assertTrue(e.getMessage().contains("'Nope'"), e.getMessage());
      assertFalse(e.getMessage().contains("'T1'"), e.getMessage());
      assertArrayEquals(new String[] { "T2" },
                        model.getChartGeneralPaneModel().getTipPaneModel().getFlyOverViews());
      assertNothingWrittenBy(lastDialogServices);
   }

   /**
    * The list is snapshotted before the patch loop, so a write to the list in the same patch
    * cannot widen what the reference check accepts.
    */
   @Test
   void refusesATipViewWhoseOnlyListingIsASamePatchWriteToPopComponents() {
      ChartPropertyDialogModel model = chartWithTipView("T1", true);
      AssemblyPropertyService service = serviceWith(mock(ChartVSAssembly.class), model);
      Map<String, Object> patch = new LinkedHashMap<>();
      patch.put("chartGeneralPaneModel.tipPaneModel.popComponents", List.of("Ghost"));
      patch.put("tipView", "Ghost");

      assertThrows(IllegalArgumentException.class,
         () -> service.set("tok", principal(), "Chart1", patch, ""));
      assertEquals("T1", model.getChartGeneralPaneModel().getTipPaneModel().getTipView());
      assertNothingWrittenBy(lastDialogServices);
   }

   private static TextPropertyDialogModel textWithPopComponent(String current) {
      TextPropertyDialogModel model = new TextPropertyDialogModel();
      model.getTextGeneralPaneModel().setPopComponents(new String[] { "T1" });
      model.getTextGeneralPaneModel().setPopComponent(current);
      return model;
   }

   /**
    * Text and Image store a literal {@code "null"} as a pop target with pop on -- unlike the tip
    * panes, where the native write treats it as none -- so it is refused and {@code ""} named.
    */
   @Test
   void refusesAnUnknownOrLiteralNullTextPopComponent() {
      for(String bad : new String[] { "null", "Ghost", "$(V)" }) {
         TextPropertyDialogModel model = textWithPopComponent("T1");
         AssemblyPropertyService service = serviceWith(mock(TextVSAssembly.class), model);

         Exception e = assertThrows(IllegalArgumentException.class,
            () -> service.set("tok", principal(), "Text1", Map.of("popComponent", bad), ""));

         assertTrue(e.getMessage().contains("textGeneralPaneModel.popComponent"), e.getMessage());
         assertTrue(e.getMessage().contains("\"\""), e.getMessage());
         assertEquals("T1", model.getTextGeneralPaneModel().getPopComponent(), bad);
         assertNothingWrittenBy(lastDialogServices);
      }
   }

   private static ImagePropertyDialogModel imageWithPopComponent(String current) {
      return ImagePropertyDialogModel.builder()
         .imageAdvancedPaneModel(ImageAdvancedPaneModel.builder()
                                    .popComponents(new String[] { "T1" })
                                    .popComponent(current)
                                    .build())
         .build();
   }

   @Test
   void refusesAnUnknownOrLiteralNullImagePopComponent() {
      for(String bad : new String[] { "Ghost", "null" }) {
         ImagePropertyDialogService imageService = mock(ImagePropertyDialogService.class);
         AssemblyPropertyService service = serviceWithImage(
            mock(ImageVSAssembly.class), imageWithPopComponent("T1"), imageService);

         Exception e = assertThrows(IllegalArgumentException.class,
            () -> service.set("tok", principal(), "Image1",
                              Map.of("imageAdvancedPaneModel.popComponent", bad), ""));

         assertTrue(e.getMessage().contains("imageAdvancedPaneModel.popComponent"),
                    e.getMessage());
         assertNothingWrittenBy(List.of(imageService));
      }
   }

   @Test
   void acceptsAListedAssemblyReferenceOnEachField() throws Exception {
      ChartPropertyDialogModel chart = chartWithTipView("T1", true);
      AssemblyPropertyService chartService = serviceWith(mock(ChartVSAssembly.class), chart);
      chartService.set("tok", principal(), "Chart1", Map.of("tipView", "Img1"), "");
      chartService.set("tok", principal(), "Chart1", Map.of("flyOverViews", List.of("T2")), "");
      assertEquals("Img1", chart.getChartGeneralPaneModel().getTipPaneModel().getTipView());
      assertArrayEquals(new String[] { "T2" },
                        chart.getChartGeneralPaneModel().getTipPaneModel().getFlyOverViews());

      TextPropertyDialogModel text = textWithPopComponent("");
      serviceWith(mock(TextVSAssembly.class), text)
         .set("tok", principal(), "Text1", Map.of("popComponent", "T1"), "");
      assertEquals("T1", text.getTextGeneralPaneModel().getPopComponent());

      ImagePropertyDialogService imageService = mock(ImagePropertyDialogService.class);
      serviceWithImage(mock(ImageVSAssembly.class), imageWithPopComponent(null), imageService)
         .set("tok", principal(), "Image1", Map.of("imageAdvancedPaneModel.popComponent", "T1"),
              "");
      ArgumentCaptor<ImagePropertyDialogModel> captor =
         ArgumentCaptor.forClass(ImagePropertyDialogModel.class);
      verify(imageService).setImagePropertyDialogModel(anyString(), anyString(), captor.capture(),
                                                       any(), any(), any());
      assertEquals("T1", captor.getValue().imageAdvancedPaneModel().popComponent());
   }

   @Test
   void acceptsBlankOnEachFieldAndLiteralNullOnTipView() throws Exception {
      for(String clear : new String[] { "", "   ", "null" }) {
         ChartPropertyDialogModel chart = chartWithTipView("T1", true);
         assertDoesNotThrow(() -> serviceWith(mock(ChartVSAssembly.class), chart)
            .set("tok", principal(), "Chart1", Map.of("tipView", clear), ""), clear);
      }

      for(String clear : new String[] { "", "   " }) {
         assertDoesNotThrow(() -> serviceWith(mock(TextVSAssembly.class), textWithPopComponent("T1"))
            .set("tok", principal(), "Text1", Map.of("popComponent", clear), ""), clear);
         assertDoesNotThrow(() -> serviceWithImage(mock(ImageVSAssembly.class),
                                                   imageWithPopComponent("T1"),
                                                   mock(ImagePropertyDialogService.class))
            .set("tok", principal(), "Image1",
                 Map.of("imageAdvancedPaneModel.popComponent", clear), ""), clear);
      }

      ChartPropertyDialogModel chart = chartWithTipView("T1", true);
      chart.getChartGeneralPaneModel().getTipPaneModel().setFlyOverViews(new String[] { "T2" });
      serviceWith(mock(ChartVSAssembly.class), chart)
         .set("tok", principal(), "Chart1", Map.of("flyOverViews", List.of()), "");
      assertArrayEquals(new String[0],
                        chart.getChartGeneralPaneModel().getTipPaneModel().getFlyOverViews());
   }

   /**
    * Echoing the value just read is the same store an unrelated-key patch makes, so it is
    * accepted even when the list no longer offers it -- and, being a no-op, it does not turn
    * view tips on either.
    */
   @Test
   void acceptsTheCurrentValueEchoedUnchangedWithoutImplyingTipOption() throws Exception {
      ChartPropertyDialogModel chart = chartWithTipView("Legacy", false);
      serviceWith(mock(ChartVSAssembly.class), chart)
         .set("tok", principal(), "Chart1", Map.of("tipView", "Legacy"), "");
      assertEquals("Legacy", chart.getChartGeneralPaneModel().getTipPaneModel().getTipView());
      assertFalse(chart.getChartGeneralPaneModel().getTipPaneModel().isTipOption(),
                  "an unchanged tipView is a no-op and must not flip tipOption");

      // Crosstab has no read mask, so a script-era dynamic value reads back verbatim.
      CrosstabPropertyDialogModel crosstab = new CrosstabPropertyDialogModel();
      TipPaneModel tip = crosstab.getCrosstabAdvancedPaneModel().getTipPaneModel();
      tip.setPopComponents(new String[] { "T2" });
      tip.setTipView("$(C1)");
      tip.setTipOption(true);
      serviceWith(mock(CrosstabVSAssembly.class), crosstab)
         .set("tok", principal(), "Crosstab1", Map.of("tipView", "$(C1)"), "");
      assertEquals("$(C1)", tip.getTipView());
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

   // ── #78396: selection measure written through set_assembly_properties ──────

   private static DataOutputService lastDataOutput;
   /** The chart/table/text/crosstab dialog-service mocks the last {@code serviceWith} built. */
   private static List<Object> lastDialogServices;
   private static LinePropertyDialogService lastLine;
   private static OvalPropertyDialogService lastOval;
   private static RectanglePropertyDialogService lastRectangle;
   private static SelectionListPropertyDialogService lastSelectionList;
   private static SelectionTreePropertyDialogService lastSelectionTree;
   private static RangeSliderPropertyDialogService lastRangeSlider;
   private static CalendarPropertyDialogService lastCalendar;
   private static inetsoft.web.viewsheet.service.VSInputService lastInput;

   private static void verifyNothingWritten() throws Exception {
      verify(lastSelectionList, never())
         .setSelectionListPropertyModel(any(), any(), any(), any(), any(), any());
      verify(lastSelectionTree, never())
         .setSelectionTreePropertyModel(any(), any(), any(), any(), any(), any());
   }

   private static void offerMeasures(String... names) throws Exception {
      List<OutputColumnRefModel> columns = new ArrayList<>();
      columns.add(new OutputColumnRefModel());

      for(String name : names) {
         OutputColumnRefModel column = new OutputColumnRefModel();
         column.setName(name);
         columns.add(column);
      }

      when(lastDataOutput.getOutputSelectionColumns(eq("rt1"), any(), any()))
         .thenReturn(columns.toArray(new OutputColumnRefModel[0]));
   }

   private static SelectionListPropertyDialogModel boundList() {
      SelectionListPropertyDialogModel model = new SelectionListPropertyDialogModel();
      model.getSelectionListPaneModel().setSelectedTable("ORDERS");
      model.getSelectionListPaneModel().getSelectionMeasurePaneModel().setMeasure("AMOUNT");
      return model;
   }

   private static String listMeasure(SelectionListPropertyDialogModel model) {
      return model.getSelectionListPaneModel().getSelectionMeasurePaneModel().getMeasure();
   }

   private static String treeMeasure(SelectionTreePropertyDialogModel model) {
      return model.getSelectionTreePaneModel().getSelectionMeasurePaneModel().getMeasure();
   }

   private static SelectionTreePropertyDialogModel boundTree() {
      SelectionTreePropertyDialogModel model = new SelectionTreePropertyDialogModel();
      model.getSelectionTreePaneModel().setSelectedTable("ORDERS");
      model.getSelectionTreePaneModel().getSelectionMeasurePaneModel().setMeasure("AMOUNT");
      return model;
   }

   private static void setList(SelectionListPropertyDialogModel model, Map<String, Object> patch)
      throws Exception
   {
      AssemblyPropertyService service =
         serviceWithSelectionList(mock(SelectionListVSAssembly.class), model);
      offerMeasures("STATE", "AMOUNT");
      service.set("tok", principal(), "List1", patch, "");
   }

   @Test
   void refusesAnUnknownSelectionListMeasureAndWritesNothing() throws Exception {
      for(String bad : new String[] {"$Foo", "Foo"}) {
         SelectionListPropertyDialogModel model = boundList();
         Exception thrown = assertThrows(IllegalArgumentException.class,
            () -> setList(model, Map.of("measure", bad)));

         assertTrue(thrown.getMessage().contains(bad));
         verifyNothingWritten();
      }
   }

   @Test
   void refusesAnUnknownSelectionTreeMeasureOnTheRawPath() throws Exception {
      SelectionTreePropertyDialogModel model = boundTree();
      AssemblyPropertyService service =
         serviceWithSelectionTree(mock(SelectionTreeVSAssembly.class), model);
      offerMeasures("STATE", "AMOUNT");

      Exception thrown = assertThrows(IllegalArgumentException.class,
         () -> service.set("tok", principal(), "Tree1",
            Map.of("selectionTreePaneModel.selectionMeasurePaneModel.measure", "$Foo"), ""));

      assertTrue(thrown.getMessage().contains("$Foo"));
      verifyNothingWritten();
   }

   @Test
   void refusesAnUnknownMeasureOnACapitalisedRawPath() throws Exception {
      SelectionListPropertyDialogModel model = boundList();

      assertThrows(IllegalArgumentException.class, () -> setList(model, Map.of(
         "SelectionListPaneModel.SelectionMeasurePaneModel.Measure", "$Foo")));
      verifyNothingWritten();
   }

   @Test
   void refusesABareEqualsSelectionMeasureOnTheAlias() throws Exception {
      SelectionListPropertyDialogModel model = boundList();

      Exception thrown = assertThrows(IllegalArgumentException.class,
         () -> setList(model, Map.of("measure", "=")));

      assertTrue(thrown.getMessage().contains("measure"));
      verifyNothingWritten();
   }

   @Test
   void canonicalisesASelectionMeasureToTheColumnName() throws Exception {
      SelectionListPropertyDialogModel model = boundList();
      model.getSelectionListPaneModel().getSelectionMeasurePaneModel().setMeasure(null);

      setList(model, Map.of("measure", "amount"));

      assertEquals("AMOUNT", listMeasure(model));
      ArgumentCaptor<SelectionListPropertyDialogModel> written =
         ArgumentCaptor.forClass(SelectionListPropertyDialogModel.class);
      verify(lastSelectionList).setSelectionListPropertyModel(
         any(), any(), written.capture(), any(), any(), any());
      assertEquals("AMOUNT", listMeasure(written.getValue()));
   }

   // ── #78756: gauge/text/image output aggregate ──────────────────────────────

   private static GaugePropertyDialogModel boundGauge(String aggregate) {
      GaugePropertyDialogModel model = new GaugePropertyDialogModel();
      model.getDataOutputPaneModel().setAggregate(aggregate);
      return model;
   }

   @Test
   void refusesAnUnknownGaugeAggregateOnTheAliasAndTheRawPath() {
      for(String key : new String[] {"aggregate", "dataOutputPaneModel.aggregate"}) {
         for(String bad : new String[] {"Foo", "Foo)"}) {
            GaugePropertyDialogModel model = boundGauge("Sum");
            AssemblyPropertyService service = serviceWith(mock(GaugeVSAssembly.class), model);

            Exception e = assertThrows(IllegalArgumentException.class,
               () -> service.set("tok", principal(), "Gauge1", Map.of(key, bad), ""));

            assertTrue(e.getMessage().contains(bad), e.getMessage());
            assertTrue(e.getMessage().contains("Sum"), e.getMessage());
            assertNothingWrittenBy(lastDialogServices);
         }
      }
   }

   @Test
   void refusesAnAliasAndItsRawSpellingInOnePatchInEitherOrderAndWritesNothing() {
      String raw = "dataOutputPaneModel.aggregate";

      for(Map<String, Object> patch : List.of(orderedPatch("aggregate", "Sum", raw, "Count"),
                                              orderedPatch(raw, "Count", "aggregate", "Sum"),
                                              orderedPatch("aggregate", "Sum", raw, "Sum")))
      {
         GaugePropertyDialogModel model = boundGauge("Sum");
         AssemblyPropertyService service = serviceWith(mock(GaugeVSAssembly.class), model);

         Exception e = assertThrows(IllegalArgumentException.class,
            () -> service.set("tok", principal(), "Gauge1", patch, ""));

         assertTrue(e.getMessage().contains("'aggregate'"), e.getMessage());
         assertTrue(e.getMessage().contains("'" + raw + "'"), e.getMessage());
         assertTrue(e.getMessage().contains("Nothing was changed"), e.getMessage());
         assertNothingWrittenBy(lastDialogServices);
      }
   }

   private static Map<String, Object> orderedPatch(String k1, Object v1, String k2, Object v2) {
      Map<String, Object> patch = new java.util.LinkedHashMap<>();
      patch.put(k1, v1);
      patch.put(k2, v2);
      return patch;
   }

   @Test
   void refusesAnUnknownTextAndImageAggregate() {
      TextPropertyDialogModel text = new TextPropertyDialogModel();
      AssemblyPropertyService textService = serviceWith(mock(TextVSAssembly.class), text);
      assertThrows(IllegalArgumentException.class,
         () -> textService.set("tok", principal(), "Text1", Map.of("aggregate", "Foo"), ""));
      assertThrows(IllegalArgumentException.class,
         () -> textService.set("tok", principal(), "Text1", Map.of("aggregate", "Foo)"), ""));

      ImagePropertyDialogService imageDialog = mock(ImagePropertyDialogService.class);
      AssemblyPropertyService imageService = serviceWithImage(
         mock(ImageVSAssembly.class), ImagePropertyDialogModel.builder().build(), imageDialog);
      assertThrows(IllegalArgumentException.class,
         () -> imageService.set("tok", principal(), "Image1", Map.of("aggregate", "Foo"), ""));
      assertNothingWrittenBy(List.of(imageDialog));
   }

   @Test
   void canonicalisesAKnownOutputAggregateAndAcceptsNoneAndDynamicValues() throws Exception {
      GaugePropertyDialogModel model = boundGauge("Max");
      AssemblyPropertyService service = serviceWith(mock(GaugeVSAssembly.class), model);

      service.set("tok", principal(), "Gauge1", Map.of("aggregate", "sum"), "");
      assertEquals("Sum", model.getDataOutputPaneModel().getAggregate());

      service.set("tok", principal(), "Gauge1", Map.of("aggregate", "Sum(Bar)"), "");
      assertEquals("Sum", model.getDataOutputPaneModel().getAggregate());

      for(String ok : new String[] {"none", "$(x)", "=1+1"}) {
         service.set("tok", principal(), "Gauge1", Map.of("aggregate", ok), "");
         assertEquals(ok, model.getDataOutputPaneModel().getAggregate());
      }
   }

   @Test
   void anUnrelatedPatchOverALegacyBadOutputAggregateIsNotBlocked() throws Exception {
      GaugePropertyDialogModel model = boundGauge("Foo");
      AssemblyPropertyService service = serviceWith(mock(GaugeVSAssembly.class), model);

      service.set("tok", principal(), "Gauge1", Map.of("aggregate", "Foo"), "");

      assertEquals("Foo", model.getDataOutputPaneModel().getAggregate());
   }

   // ── #79540: gauge/text/image output table/column binding ───────────────────

   private static Worksheet worksheetWithTables(String... names) {
      Worksheet ws = mock(Worksheet.class);
      List<Assembly> all = new ArrayList<>();

      for(String name : names) {
         TableAssembly table = mock(TableAssembly.class);
         when(table.getName()).thenReturn(name);
         when(ws.getAssembly(name)).thenReturn(table);
         all.add(table);
      }

      when(ws.getAssemblies()).thenReturn(all.toArray(new Assembly[0]));
      return ws;
   }

   private static GaugePropertyDialogModel gaugeBoundTo(String table, String column) {
      GaugePropertyDialogModel model = new GaugePropertyDialogModel();
      model.getDataOutputPaneModel().setTable(table);
      model.getDataOutputPaneModel().setColumn(column);
      model.getDataOutputPaneModel().setColumnType(XSchema.STRING);
      return model;
   }

   private static AssemblyPropertyService outputBindingService(
      VSAssembly assembly, Object model, Worksheet ws, VSOutputService outputService)
   {
      return serviceWith(assembly, model, null, ws, mock(ImagePropertyDialogService.class),
                         mock(GaugePropertyDialogService.class), outputService,
                         mock(VSColumnHandler.class));
   }

   private static VSOutputService outputServiceWithColumns(String table, ColumnSelection cols) {
      VSOutputService outputService = mock(VSOutputService.class);

      try {
         when(outputService.getOutputTableColumns(any(), eq(table), eq(true), any()))
            .thenReturn(cols);
      }
      catch(Exception e) {
         throw new IllegalStateException(e);
      }

      return outputService;
   }

   @Test
   void refusesANonexistentOutputColumnOnTheAliasAndTheRawPathAndWritesNothing() {
      for(String key : new String[] {"column", "dataOutputPaneModel.column"}) {
         GaugePropertyDialogModel model = gaugeBoundTo("Table1", "Total");
         VSOutputService outputService = outputServiceWithColumns(
            "Table1", columnsWithType("Total", XSchema.DOUBLE, "Cost", XSchema.DOUBLE));
         AssemblyPropertyService service = outputBindingService(
            mock(GaugeVSAssembly.class), model, worksheetWithTables("Table1"), outputService);

         Exception e = assertThrows(IllegalArgumentException.class,
            () -> service.set("tok", principal(), "Gauge1", Map.of(key, "Totl"), ""));

         assertTrue(e.getMessage().contains("Totl"), e.getMessage());
         assertTrue(e.getMessage().contains("Table1"), e.getMessage());
         assertTrue(e.getMessage().contains("Total, Cost"), e.getMessage());
         assertNothingWrittenBy(lastDialogServices);
      }
   }

   @Test
   void refusesANonexistentOutputTableAndWritesNothing() {
      GaugePropertyDialogModel model = gaugeBoundTo("Table1", "Total");
      AssemblyPropertyService service = outputBindingService(
         mock(GaugeVSAssembly.class), model, worksheetWithTables("Table1", "Table2"),
         mock(VSOutputService.class));

      Exception e = assertThrows(IllegalArgumentException.class,
         () -> service.set("tok", principal(), "Gauge1", Map.of("table", "NoSuchTable"), ""));

      assertTrue(e.getMessage().contains("NoSuchTable"), e.getMessage());
      assertTrue(e.getMessage().contains("Table1, Table2"), e.getMessage());
      assertNothingWrittenBy(lastDialogServices);
   }

   @Test
   void refusesANonexistentTextOutputColumn() {
      TextPropertyDialogModel model = new TextPropertyDialogModel();
      model.getDataOutputPaneModel().setTable("Table1");
      model.getDataOutputPaneModel().setColumn("Total");
      AssemblyPropertyService service = outputBindingService(
         mock(TextVSAssembly.class), model, worksheetWithTables("Table1"),
         outputServiceWithColumns("Table1", columnsWithType("Total", XSchema.DOUBLE)));

      Exception e = assertThrows(IllegalArgumentException.class,
         () -> service.set("tok", principal(), "Text1", Map.of("column", "Nope"), ""));

      assertTrue(e.getMessage().contains("Nope"), e.getMessage());
   }

   @Test
   void acceptsARealOutputColumnAndCanonicalisesItsCase() throws Exception {
      GaugePropertyDialogModel model = gaugeBoundTo("Table1", "Cost");
      AssemblyPropertyService service = outputBindingService(
         mock(GaugeVSAssembly.class), model, worksheetWithTables("Table1"),
         outputServiceWithColumns("Table1",
                                  columnsWithType("Total", XSchema.DOUBLE, "Cost", XSchema.DOUBLE)));

      service.set("tok", principal(), "Gauge1", Map.of("column", "Total"), "");
      assertEquals("Total", model.getDataOutputPaneModel().getColumn());

      service.set("tok", principal(), "Gauge1", Map.of("column", "cost"), "");
      assertEquals("Cost", model.getDataOutputPaneModel().getColumn());
   }

   @Test
   void aTableOnlyChangeToARealTableDoesNotRecheckTheStoredColumn() throws Exception {
      GaugePropertyDialogModel model = gaugeBoundTo("Table1", "Total");
      AssemblyPropertyService service = outputBindingService(
         mock(GaugeVSAssembly.class), model, worksheetWithTables("Table1", "Table2"),
         outputServiceWithColumns("Table2", columnsWithType("Other", XSchema.STRING)));

      service.set("tok", principal(), "Gauge1", Map.of("table", "Table2"), "");

      assertEquals("Table2", model.getDataOutputPaneModel().getTable());
   }

   @Test
   void dynamicAndBlankOutputBindingValuesPassThrough() throws Exception {
      GaugePropertyDialogModel model = gaugeBoundTo("Table1", "Total");
      AssemblyPropertyService service = outputBindingService(
         mock(GaugeVSAssembly.class), model, worksheetWithTables("Table1"),
         outputServiceWithColumns("Table1", columnsWithType("Total", XSchema.DOUBLE)));

      for(String ok : new String[] {"$(col)", "=field['x']", ""}) {
         service.set("tok", principal(), "Gauge1", Map.of("column", ok), "");
         assertEquals(ok, model.getDataOutputPaneModel().getColumn());
      }

      for(String ok : new String[] {"$(tbl)", "=tableName", ""}) {
         service.set("tok", principal(), "Gauge1", Map.of("table", ok), "");
         assertEquals(ok, model.getDataOutputPaneModel().getTable());
      }
   }

   @Test
   void anUnrelatedPatchOverALegacyBadOutputBindingIsNotBlocked() throws Exception {
      GaugePropertyDialogModel model = gaugeBoundTo("GoneTable", "GoneColumn");
      AssemblyPropertyService service = outputBindingService(
         mock(GaugeVSAssembly.class), model, worksheetWithTables("Table1"),
         mock(VSOutputService.class));

      service.set("tok", principal(), "Gauge1", Map.of("aggregate", "Sum"), "");

      assertEquals("GoneTable", model.getDataOutputPaneModel().getTable());
      assertEquals("GoneColumn", model.getDataOutputPaneModel().getColumn());
   }

   @Test
   void outputBindingIsNotJudgedWithoutABaseWorksheetOrWithAnEmptyColumnList() throws Exception {
      GaugePropertyDialogModel noWs = gaugeBoundTo("Table1", "Total");
      outputBindingService(mock(GaugeVSAssembly.class), noWs, null,
                           outputServiceWithColumns("Table1", columnsWithType("Total", XSchema.STRING)))
         .set("tok", principal(), "Gauge1", Map.of("column", "Anything"), "");
      assertEquals("Anything", noWs.getDataOutputPaneModel().getColumn());

      GaugePropertyDialogModel denied = gaugeBoundTo("Table1", "Total");
      outputBindingService(mock(GaugeVSAssembly.class), denied, worksheetWithTables("Table1"),
                           outputServiceWithColumns("Table1", new ColumnSelection()))
         .set("tok", principal(), "Gauge1", Map.of("column", "Anything"), "");
      assertEquals("Anything", denied.getDataOutputPaneModel().getColumn());
   }

   // ── #78678: selection measure formula ──────────────────────────────────────

   private static String listFormula(SelectionListPropertyDialogModel model) {
      return model.getSelectionListPaneModel().getSelectionMeasurePaneModel().getFormula();
   }

   @Test
   void refusesAnUnknownSelectionListFormulaOnTheAliasAndWritesNothing() throws Exception {
      SelectionListPropertyDialogModel model = boundList();
      model.getSelectionListPaneModel().getSelectionMeasurePaneModel().setFormula("Sum");

      Exception thrown = assertThrows(IllegalArgumentException.class,
         () -> setList(model, Map.of("formula", "Foo")));

      assertTrue(thrown.getMessage().contains("Foo"), thrown.getMessage());
      assertTrue(thrown.getMessage().contains("Count, Sum, Max, Min"), thrown.getMessage());
      verifyNothingWritten();
   }

   @Test
   void refusesAnUnbalancedSelectionListFormulaLikeAnUnknownOne() throws Exception {
      SelectionListPropertyDialogModel model = boundList();
      model.getSelectionListPaneModel().getSelectionMeasurePaneModel().setFormula("Sum");

      Exception thrown = assertThrows(IllegalArgumentException.class,
         () -> setList(model, Map.of("formula", "Foo)")));

      assertTrue(thrown.getMessage().contains("Foo)"), thrown.getMessage());
      assertTrue(thrown.getMessage().contains("Count, Sum, Max, Min"), thrown.getMessage());
      verifyNothingWritten();
   }

   @Test
   void refusesAnUnknownSelectionListFormulaOnTheRawPath() throws Exception {
      SelectionListPropertyDialogModel model = boundList();

      assertThrows(IllegalArgumentException.class, () -> setList(model, Map.of(
         "SelectionListPaneModel.SelectionMeasurePaneModel.Formula", "Foo")));
      verifyNothingWritten();
   }

   @Test
   void refusesAnUnknownSelectionTreeFormulaOnTheRawPath() throws Exception {
      SelectionTreePropertyDialogModel model = boundTree();
      AssemblyPropertyService service =
         serviceWithSelectionTree(mock(SelectionTreeVSAssembly.class), model);
      offerMeasures("STATE", "AMOUNT");

      Exception thrown = assertThrows(IllegalArgumentException.class,
         () -> service.set("tok", principal(), "Tree1",
            Map.of("selectionTreePaneModel.selectionMeasurePaneModel.formula", "Foo"), ""));

      assertTrue(thrown.getMessage().contains("Foo"));
      verifyNothingWritten();
   }

   @Test
   void acceptsAndCanonicalisesAKnownSelectionFormulaWithTheMeasure() throws Exception {
      SelectionListPropertyDialogModel model = boundList();

      setList(model, Map.of("measure", "AMOUNT", "formula", "sum"));

      assertEquals("Sum", listFormula(model));
      ArgumentCaptor<SelectionListPropertyDialogModel> written =
         ArgumentCaptor.forClass(SelectionListPropertyDialogModel.class);
      verify(lastSelectionList).setSelectionListPropertyModel(
         any(), any(), written.capture(), any(), any(), any());
      assertEquals("Sum", listFormula(written.getValue()));
   }

   @Test
   void doesNotKeepRawTextTheFormulaLookupToleratedAndRefusesAverage() throws Exception {
      SelectionListPropertyDialogModel model = boundList();
      setList(model, Map.of("formula", "Sum(Bar)"));
      assertEquals("Sum", listFormula(model));

      assertThrows(IllegalArgumentException.class,
         () -> setList(boundList(), Map.of("formula", "Average")));
   }

   @Test
   void anUnrelatedPatchOverALegacyBadSelectionFormulaIsNotBlocked() throws Exception {
      SelectionListPropertyDialogModel model = boundList();
      model.getSelectionListPaneModel().getSelectionMeasurePaneModel().setFormula("Foo");

      setList(model, Map.of("measure", "AMOUNT"));

      assertEquals("Foo", listFormula(model));
   }

   @Test
   void passesDynamicSelectionMeasuresThroughWithoutALookup() throws Exception {
      for(String ok : new String[] {"$(x)", "=1+1"}) {
         SelectionListPropertyDialogModel model = boundList();
         setList(model, Map.of("measure", ok));

         assertEquals(ok, listMeasure(model));
         verify(lastDataOutput, never()).getOutputSelectionColumns(any(), any(), any());
      }
   }

   @Test
   void aBlankSelectionMeasureStillClears() throws Exception {
      SelectionListPropertyDialogModel model = boundList();

      setList(model, Map.of("measure", ""));

      assertEquals("", listMeasure(model));
      verify(lastDataOutput, never()).getOutputSelectionColumns(any(), any(), any());
   }

   @Test
   void refusesANonBlankSelectionMeasureWhenNoTableIsBound() throws Exception {
      SelectionListPropertyDialogModel model = new SelectionListPropertyDialogModel();

      Exception thrown = assertThrows(IllegalArgumentException.class,
         () -> setList(model, Map.of("measure", "AMOUNT")));

      assertTrue(thrown.getMessage().contains("set_selection_source"));
      verifyNothingWritten();
      verify(lastDataOutput, never()).getOutputSelectionColumns(any(), any(), any());
   }

   /** Same candidate set as set_selection_source (#78472): the table plus its additional tables. */
   @Test
   void checksTheMeasureAcrossTheAdditionalTablesAndRefusesOneTheyDoNotShare() throws Exception {
      SelectionListPropertyDialogModel model = boundList();
      model.getSelectionListPaneModel().setAdditionalTables(List.of("CUSTOMERS"));

      Exception thrown = assertThrows(IllegalArgumentException.class,
         () -> setList(model, Map.of("measure", "AMOUNT_ONLY_IN_ORDERS")));

      assertTrue(thrown.getMessage().contains("AMOUNT_ONLY_IN_ORDERS"));
      verify(lastDataOutput).getOutputSelectionColumns(
         eq("rt1"), eq(List.of("ORDERS", "CUSTOMERS")), any());
      verifyNothingWritten();
   }

   /**
    * The measure is checked against the stored table: a table can't change in the same patch
    * (#78480, see {@link #refusesASelectedTableWrittenTogetherWithAMeasure}).
    */
   @Test
   void checksTheMeasureAgainstTheStoredTable() throws Exception {
      SelectionListPropertyDialogModel model = new SelectionListPropertyDialogModel();
      model.getSelectionListPaneModel().setSelectedTable("ORDERS");

      setList(model, Map.of("measure", "amount"));

      assertEquals("AMOUNT", listMeasure(model));
      verify(lastDataOutput).getOutputSelectionColumns(eq("rt1"), eq(List.of("ORDERS")), any());
   }

   @Test
   void refusesASelectedTableWrittenTogetherWithAMeasure() throws Exception {
      SelectionListPropertyDialogModel model = new SelectionListPropertyDialogModel();

      Exception thrown = assertThrows(IllegalArgumentException.class, () -> setList(model, Map.of(
         "selectionListPaneModel.selectedTable", "ORDERS", "measure", "amount")));

      assertTrue(thrown.getMessage().contains("set_selection_source"), thrown.getMessage());
      verify(lastDataOutput, never()).getOutputSelectionColumns(any(), any(), any());
      verifyNothingWritten();
   }

   // ── #78480: a selection's data source is set_selection_source's, not a raw path's ──

   private static OutputColumnRefModel column(String table, String attribute) {
      OutputColumnRefModel column = new OutputColumnRefModel();
      column.setTable(table);
      column.setEntity(table);
      column.setAttribute(attribute);
      column.setDataType("string");
      return column;
   }

   private static SelectionListPropertyDialogModel sourcedList() {
      SelectionListPropertyDialogModel model = new SelectionListPropertyDialogModel();
      model.getSelectionListPaneModel().setSelectedTable("CUSTOMERS");
      model.getSelectionListPaneModel().setAdditionalTables(new ArrayList<>(List.of("ORDERS")));
      model.getSelectionListPaneModel().setSelectedColumn(column("CUSTOMERS", "STATE"));
      return model;
   }

   private static SelectionTreePropertyDialogModel columnTree() {
      SelectionTreePropertyDialogModel model = new SelectionTreePropertyDialogModel();
      model.getSelectionTreePaneModel().setSelectedTable("CUSTOMERS");
      model.getSelectionTreePaneModel().setAdditionalTables(new ArrayList<>());
      model.getSelectionTreePaneModel().setMode(SelectionTreeVSAssemblyInfo.COLUMN);
      model.getSelectionTreePaneModel().setSelectedColumns(new OutputColumnRefModel[] {
         column("CUSTOMERS", "STATE"), column("CUSTOMERS", "CITY")});
      return model;
   }

   private static SelectionTreePropertyDialogModel idTree() {
      SelectionTreePropertyDialogModel model = new SelectionTreePropertyDialogModel();
      model.getSelectionTreePaneModel().setSelectedTable("EMPLOYEES");
      model.getSelectionTreePaneModel().setAdditionalTables(new ArrayList<>());
      model.getSelectionTreePaneModel().setMode(SelectionTreeVSAssemblyInfo.ID);
      model.getSelectionTreePaneModel().setParentId("BOSS_ID");
      model.getSelectionTreePaneModel().setId("ID");
      model.getSelectionTreePaneModel().setLabel("NAME");
      model.getSelectionTreePaneModel().setParentIdRef(column("EMPLOYEES", "BOSS_ID"));
      model.getSelectionTreePaneModel().setIdRef(column("EMPLOYEES", "ID"));
      model.getSelectionTreePaneModel().setLabelRef(column("EMPLOYEES", "NAME"));
      return model;
   }

   private static RangeSliderPropertyDialogModel sourcedSlider() {
      RangeSliderPropertyDialogModel model = new RangeSliderPropertyDialogModel();
      model.getRangeSliderDataPaneModel().setSelectedTable("ORDERS");
      model.getRangeSliderDataPaneModel().setAdditionalTables(new ArrayList<>());
      model.getRangeSliderDataPaneModel().setSelectedColumns(
         new OutputColumnRefModel[] {column("ORDERS", "ORDER_DATE")});
      return model;
   }

   private static CalendarPropertyDialogModel sourcedCalendar() {
      CalendarPropertyDialogModel model = new CalendarPropertyDialogModel();
      model.getCalendarDataPaneModel().setSelectedTable("ORDERS");
      model.getCalendarDataPaneModel().setAdditionalTables(new ArrayList<>());
      model.getCalendarDataPaneModel().setSelectedColumn(column("ORDERS", "ORDER_DATE"));
      return model;
   }

   private static void setTree(SelectionTreePropertyDialogModel model, Map<String, Object> patch)
      throws Exception
   {
      AssemblyPropertyService service =
         serviceWithSelectionTree(mock(SelectionTreeVSAssembly.class), model);
      service.set("tok", principal(), "Tree1", patch, "");
   }

   private static void setSlider(RangeSliderPropertyDialogModel model, Map<String, Object> patch)
      throws Exception
   {
      AssemblyPropertyService service =
         serviceWithRangeSlider(mock(TimeSliderVSAssembly.class), model);
      service.set("tok", principal(), "Slider1", patch, "");
   }

   private static void setCalendar(CalendarPropertyDialogModel model, Map<String, Object> patch)
      throws Exception
   {
      AssemblyPropertyService service =
         serviceWithCalendar(mock(CalendarVSAssembly.class), model);
      service.set("tok", principal(), "Calendar1", patch, "");
   }

   /** Refused naming the path and set_selection_source, and nothing reaches any apply. */
   private static void assertSourceRefused(String path, Executable write) throws Exception {
      Exception thrown = assertThrows(IllegalArgumentException.class, write);

      assertTrue(thrown.getMessage().contains("set_selection_source"), thrown.getMessage());
      assertTrue(thrown.getMessage().contains(path), thrown.getMessage());
      verifyNothingWritten();
      verify(lastRangeSlider, never())
         .setRangeSliderPropertyModel(any(), any(), any(), any(), any(), any());
      verify(lastCalendar, never())
         .setCalendarPropertyModel(any(), any(), any(), any(), any(), any());
   }

   @Test
   void refusesARawSelectionListSourceWriteInEverySpelling() throws Exception {
      Map<String, Object> writes = new LinkedHashMap<>();
      writes.put("selectionListPaneModel.additionalTables", List.of("NO_SUCH_TABLE"));
      writes.put("SelectionListPaneModel.AdditionalTables", List.of("PRODUCTS"));
      writes.put("selectionListPaneModel.selectedTable", "ORDERS");
      writes.put("selectionListPaneModel.selectedColumn.attribute", "CITY");
      // never read by the apply: refused rather than reported as an ok:true no-op
      writes.put("selectionListPaneModel.selectedColumn.table", "ORDERS");

      for(Map.Entry<String, Object> write : writes.entrySet()) {
         String path = PropertyAliases.lowerSegmentInitials(write.getKey());
         SelectionListPropertyDialogModel model = sourcedList();
         assertSourceRefused(path, () -> setList(model, Map.of(write.getKey(), write.getValue())));
      }
   }

   @Test
   void refusesAWriteIntoASelectionListTargetTree() throws Exception {
      SelectionListPropertyDialogModel model = sourcedList();
      model.getSelectionListPaneModel().setTargetTree(
         TreeNodeModel.builder().label("root").build());

      assertSourceRefused("selectionListPaneModel.targetTree", () -> setList(model, Map.of(
         "selectionListPaneModel.targetTree.label", "other")));
   }

   @Test
   void refusesARawSelectionTreeSourceWrite() throws Exception {
      assertSourceRefused("selectionTreePaneModel.selectedTable", () -> setTree(
         columnTree(), Map.of("selectionTreePaneModel.selectedTable", "ORDERS")));
      assertSourceRefused("selectionTreePaneModel.additionalTables", () -> setTree(
         columnTree(), Map.of("selectionTreePaneModel.additionalTables", List.of("ORDERS"))));
      assertSourceRefused("selectionTreePaneModel.parentIdRef.attribute", () -> setTree(
         idTree(), Map.of("selectionTreePaneModel.parentIdRef.attribute", "MANAGER")));
      assertSourceRefused("selectionTreePaneModel.parentId", () -> setTree(
         idTree(), Map.of("selectionTreePaneModel.parentId", "MANAGER")));
   }

   /** Switching modes rebuilds the columns from the other mode's fields: an empty tree. */
   @Test
   void refusesAModeChangeOnABoundTreeInEverySpelling() throws Exception {
      for(String key : List.of("mode", "selectionTreePaneModel.mode",
                               "SelectionTreePaneModel.Mode"))
      {
         SelectionTreePropertyDialogModel model = columnTree();
         assertSourceRefused("mode", () -> setTree(model, Map.of(key, "id")));
      }

      assertSourceRefused("mode", () -> setTree(idTree(), Map.of("mode", "column")));
   }

   @Test
   void refusesARawTimeSliderSourceWrite() throws Exception {
      assertSourceRefused("rangeSliderDataPaneModel.composite", () -> setSlider(
         sourcedSlider(), Map.of("rangeSliderDataPaneModel.composite", true)));
      assertSourceRefused("rangeSliderDataPaneModel.selectedTable", () -> setSlider(
         sourcedSlider(), Map.of("rangeSliderDataPaneModel.selectedTable", "CUSTOMERS")));

      // the slider apply streams the list with no null check: null is a change, not "empty"
      Map<String, Object> clear = new LinkedHashMap<>();
      clear.put("rangeSliderDataPaneModel.additionalTables", null);
      assertSourceRefused("rangeSliderDataPaneModel.additionalTables",
                          () -> setSlider(sourcedSlider(), clear));
   }

   @Test
   void refusesARawCalendarSourceWrite() throws Exception {
      assertSourceRefused("calendarDataPaneModel.selectedTable", () -> setCalendar(
         sourcedCalendar(), Map.of("calendarDataPaneModel.selectedTable", "CUSTOMERS")));
      assertSourceRefused("calendarDataPaneModel.selectedColumn.attribute", () -> setCalendar(
         sourcedCalendar(),
         Map.of("calendarDataPaneModel.selectedColumn.attribute", "SHIP_DATE")));
   }

   /** A write-back of what raw:true read is a no-op, not a source change. */
   @Test
   void acceptsAnUnchangedSourceWriteBack() throws Exception {
      setList(sourcedList(), Map.of(
         "selectionListPaneModel.additionalTables", List.of("ORDERS"),
         "selectionListPaneModel.selectedTable", "CUSTOMERS",
         "selectionListPaneModel.selectedColumn.attribute", "STATE"));
      verify(lastSelectionList).setSelectionListPropertyModel(
         any(), any(), any(), any(), any(), any());

      setTree(columnTree(), Map.of("mode", "column"));
      verify(lastSelectionTree).setSelectionTreePropertyModel(
         any(), any(), any(), any(), any(), any());

      setSlider(sourcedSlider(), Map.of("rangeSliderDataPaneModel.composite", false));
      verify(lastRangeSlider).setRangeSliderPropertyModel(
         any(), any(), any(), any(), any(), any());
   }

   /** Not the source: the measure (#78396's check) and the ID tree's selectChildren flag. */
   @Test
   void acceptsNonSourceWritesOnABoundSelection() throws Exception {
      SelectionTreePropertyDialogModel tree = idTree();
      setTree(tree, Map.of("selectChildren", true));
      assertTrue(tree.getSelectionTreePaneModel().isSelectChildren());

      SelectionListPropertyDialogModel list = sourcedList();
      list.getSelectionListPaneModel().setAdditionalTables(new ArrayList<>());
      setList(list, Map.of("measure", "amount"));
      assertEquals("AMOUNT", listMeasure(list));
   }

   /** VFL-009: a tree with no bound column may still switch modes (it binds nothing). */
   @Test
   void acceptsAModeChangeOnAnUnboundTree() throws Exception {
      SelectionTreePropertyDialogModel model = new SelectionTreePropertyDialogModel();
      model.getSelectionTreePaneModel().setMode(SelectionTreeVSAssemblyInfo.COLUMN);

      setTree(model, Map.of("mode", "id"));

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
    * Bug #78440 round 2: a path whose first letter is U+017F resolves to the same setter as the
    * ASCII spelling, so it must hit the same out-of-domain guard.
    */
   @Test
   void refusesAnOutOfDomainSortTypeThroughNonAsciiInitialPaths() {
      SelectionListPropertyDialogModel model = new SelectionListPropertyDialogModel();
      AssemblyPropertyService service =
         serviceWithSelectionList(mock(SelectionListVSAssembly.class), model);

      for(String path : new String[] { "selectionGeneralPaneModel.sortType",
                                       "selectionGeneralPaneModel.ſortType",
                                       "ſelectionGeneralPaneModel.sortType" })
      {
         Exception thrown = assertThrows(IllegalArgumentException.class,
            () -> service.set("tok", principal(), "Selection1", Map.of(path, 999), ""), path);
         assertTrue(thrown.getMessage().contains("999"), thrown.getMessage());
      }
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

   /**
    * Regression for bug #77041: {@code canonicalIntEnum} parses a numeric {@code sortType}
    * before {@code PropertyPath.coerce} ever runs, so coerce's own whole-number guard never sees
    * this path -- {@code sortType: 1.9} used to truncate to {@code 1} (an in-domain value,
    * {@code XConstants.SORT_ASC}) and silently succeed. Demonstrated live against a real
    * StyleBI build 2026-09-27 (docs/teams .../bug-77041/02-refute.md, finding P2-A) before this
    * fix; must be refused the same as an out-of-domain or non-numeric sortType.
    */
   @Test
   void refusesAFractionalSortTypeThatWouldTruncateOntoAnInDomainValue() {
      SelectionListPropertyDialogModel model = new SelectionListPropertyDialogModel();
      AssemblyPropertyService service =
         serviceWithSelectionList(mock(SelectionListVSAssembly.class), model);

      Exception thrown = assertThrows(IllegalArgumentException.class,
         () -> service.set("tok", principal(), "Selection1", Map.of("sortType", 1.9), ""));

      assertTrue(thrown.getMessage().contains("1.9"));
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
   void writesCalendarMinAndMaxThroughTheShortAliases() throws Exception {
      CalendarPropertyDialogModel model = new CalendarPropertyDialogModel();
      AssemblyPropertyService service =
         serviceWithCalendar(mock(CalendarVSAssembly.class), model);

      service.set("tok", principal(), "Calendar1",
                  Map.of("min", "2022-05-12", "max", "2022-06-12"), "");

      assertEquals("2022-05-12", model.getCalendarAdvancedPaneModel().getMin().getValue());
      assertEquals("2022-06-12", model.getCalendarAdvancedPaneModel().getMax().getValue());
   }

   @Test
   @SuppressWarnings("unchecked")
   void listsCalendarMinAndMaxWithASerializableCurrentValue() throws Exception {
      CalendarPropertyDialogModel model = new CalendarPropertyDialogModel();
      model.getCalendarAdvancedPaneModel().setMin(new DynamicValueModel("2022-05-12"));
      AssemblyPropertyService service =
         serviceWithCalendar(mock(CalendarVSAssembly.class), model);

      Map<String, Object> listed = service.list("tok", principal(), "Calendar1");
      java.util.List<Map<String, Object>> properties =
         (java.util.List<Map<String, Object>>) listed.get("properties");
      Map<String, Object> min = properties.stream()
         .filter(p -> "min".equals(p.get("name"))).findFirst().orElseThrow();

      assertEquals("calendarAdvancedPaneModel.min", min.get("path"));
      assertTrue(properties.stream().anyMatch(p -> "max".equals(p.get("name"))));
      String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(listed);
      assertTrue(json.contains("2022-05-12"), json);
   }

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

      // Canonicalized to the id, which is what the Composer UI stores.
      assertEquals("4611686018437387905", model.getTableViewGeneralPaneModel()
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


   private static TreeNodeModel styleLeaf(String label, String id) {
      return TreeNodeModel.builder().label(label).data(id).type("style").leaf(true).build();
   }

   private static TreeNodeModel treeOf(TreeNodeModel... leaves) {
      return TreeNodeModel.builder().children(java.util.List.of(
         TreeNodeModel.builder().label("Styles").type("folder").leaf(false)
            .children(java.util.List.of(leaves)).build())).build();
   }

   /** The built-in's label does not resolve at render, so it must be stored as the class name. */
   @Test
   void canonicalizesTheBuiltInCrosstabStyleLabelToItsClassNameOnACalcTable() throws Exception {
      CalcTablePropertyDialogModel model = new CalcTablePropertyDialogModel();
      model.getTableViewGeneralPaneModel().getTableStylePaneModel().setStyleTree(
         treeOf(styleLeaf("Default Style", "100"),
                styleLeaf("Crosstab Style", "inetsoft.report.style.CrosstabStyle")));
      AssemblyPropertyService service =
         serviceWithCalcTable(mock(CalcTableVSAssembly.class), model);

      service.set("tok", principal(), "CalcTable1", Map.of(TABLE_STYLE_PATH, "Crosstab Style"), "");

      assertEquals("inetsoft.report.style.CrosstabStyle", model.getTableViewGeneralPaneModel()
         .getTableStylePaneModel().getTableStyle());
   }

   @Test
   void refusesTheBuiltInStyleOnATableThatDoesNotOfferItWithATypeSpecificMessage() {
      TableViewPropertyDialogModel model = new TableViewPropertyDialogModel();
      model.getTableViewGeneralPaneModel().getTableStylePaneModel()
         .setStyleTree(sampleStyleTree());
      AssemblyPropertyService service = serviceWithTable(mock(TableVSAssembly.class), model);

      Exception thrown = assertThrows(IllegalArgumentException.class,
         () -> service.set("tok", principal(), "Table1",
            Map.of(TABLE_STYLE_PATH, "inetsoft.report.style.CrosstabStyle"), ""));

      assertTrue(thrown.getMessage().contains("built-in"), thrown.getMessage());
      assertFalse(thrown.getMessage().contains("does not match any table style"),
                  "must not claim the style is unknown: " + thrown.getMessage());
   }

   @Test
   void refusesAnAmbiguousStyleLabelListingTheIds() {
      TableViewPropertyDialogModel model = new TableViewPropertyDialogModel();
      model.getTableViewGeneralPaneModel().getTableStylePaneModel()
         .setStyleTree(treeOf(styleLeaf("Bands", "1"), styleLeaf("Bands", "2")));
      AssemblyPropertyService service = serviceWithTable(mock(TableVSAssembly.class), model);

      Exception thrown = assertThrows(IllegalArgumentException.class,
         () -> service.set("tok", principal(), "Table1", Map.of(TABLE_STYLE_PATH, "Bands"), ""));

      assertTrue(thrown.getMessage().contains("1") && thrown.getMessage().contains("2"),
                 thrown.getMessage());
   }

   @Test
   void unknownStyleMessagePointsAtBuiltInStylesAndTheAssemblyOptions() {
      TableViewPropertyDialogModel model = new TableViewPropertyDialogModel();
      model.getTableViewGeneralPaneModel().getTableStylePaneModel()
         .setStyleTree(sampleStyleTree());
      AssemblyPropertyService service = serviceWithTable(mock(TableVSAssembly.class), model);

      Exception thrown = assertThrows(IllegalArgumentException.class,
         () -> service.set("tok", principal(), "Table1", Map.of(TABLE_STYLE_PATH, "Nope"), ""));

      assertTrue(thrown.getMessage().contains("builtInStyles"), thrown.getMessage());
      assertTrue(thrown.getMessage().contains("list_assembly_properties"), thrown.getMessage());
   }

   @Test
   void listExposesTheAssemblysOwnTableStyleOptions() throws Exception {
      CalcTablePropertyDialogModel model = new CalcTablePropertyDialogModel();
      model.getTableViewGeneralPaneModel().getTableStylePaneModel().setStyleTree(
         treeOf(styleLeaf("Default Style", "100"),
                styleLeaf("Crosstab Style", "inetsoft.report.style.CrosstabStyle")));
      AssemblyPropertyService service =
         serviceWithCalcTable(mock(CalcTableVSAssembly.class), model);

      @SuppressWarnings("unchecked")
      java.util.List<Map<String, Object>> properties =
         (java.util.List<Map<String, Object>>) service.list("tok", principal(), "CalcTable1")
            .get("properties");
      Map<String, Object> tableStyle = properties.stream()
         .filter(p -> TABLE_STYLE_PATH.equals(p.get("path"))).findFirst().orElseThrow();

      @SuppressWarnings("unchecked")
      java.util.List<Map<String, Object>> options =
         (java.util.List<Map<String, Object>>) tableStyle.get("options");
      assertEquals(2, options.size());
      assertEquals("100", options.get(0).get("value"));
      assertEquals(Boolean.TRUE, options.get(1).get("builtIn"));
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

   /** Bug #78901: a group's width/height would be dropped, so the write is refused unapplied. */
   @Test
   void refusesAGroupContainerSizeWriteWithoutApplyingIt() {
      GroupContainerPropertyDialogModel model = groupContainerModelWithTree();
      model.getGroupContainerGeneralPane().getSizePositionPaneModel().setWidth(300);
      AssemblyPropertyService service =
         serviceWith(mock(GroupContainerVSAssembly.class), model);

      Exception e = assertThrows(IllegalArgumentException.class, () -> service.set(
         "tok", principal(), "Group1", Map.of("width", 600, "height", 400), ""));

      assertTrue(e.getMessage().contains("read-only on groupcontainer"), e.getMessage());
      assertEquals(300, model.getGroupContainerGeneralPane().getSizePositionPaneModel()
         .getWidth());
   }

   @Test
   void allowsClearingAGroupContainerBackgroundImage() {
      AssemblyPropertyService service =
         serviceWith(mock(GroupContainerVSAssembly.class), groupContainerModelWithTree());

      assertDoesNotThrow(() -> service.set("tok", principal(), "Group1",
                                           Map.of(GROUP_SELECTED_IMAGE_PATH, ""), ""));
   }

   /**
    * Bug #77028: switching a Gauge's {@code column} to one with a different real type must
    * re-derive {@code dataOutputPaneModel.columnType} from that column's actual data type --
    * mirroring what the native Composer's own {@code data-output-pane.component.ts} does
    * client-side (see {@code AssemblyPropertyService.deriveColumnTypeFromRealColumn}'s own doc
    * comment). Before this fix, the write left {@code columnType} at the OLD column's type
    * (STRING here), which {@code GaugePropertyDialogService.setGaugePropertyDialogModel} would
    * then persist verbatim into {@code ScalarBindingInfo.columnType}.
    */
   @Test
   void rederivesColumnTypeWhenColumnChanges() throws Exception {
      GaugePropertyDialogModel model = new GaugePropertyDialogModel();
      model.getDataOutputPaneModel().setTable("Table1");
      model.getDataOutputPaneModel().setColumn("OldStringCol");
      model.getDataOutputPaneModel().setColumnType(XSchema.STRING);

      GaugePropertyDialogService gaugeService = mock(GaugePropertyDialogService.class);
      VSOutputService outputService = mock(VSOutputService.class);
      when(outputService.getOutputTableColumns(any(), eq("Table1"), eq(true), any()))
         .thenReturn(columnsWithType("OldStringCol", XSchema.STRING,
                                     "NewNumCol", XSchema.INTEGER));

      AssemblyPropertyService service =
         serviceWithGauge(mock(GaugeVSAssembly.class), model, gaugeService, outputService);

      service.set("tok", principal(), "Gauge1", Map.of("column", "NewNumCol"), "");

      ArgumentCaptor<GaugePropertyDialogModel> captor =
         ArgumentCaptor.forClass(GaugePropertyDialogModel.class);
      verify(gaugeService).setGaugePropertyDialogModel(anyString(), anyString(), captor.capture(),
                                                       any(), any(), any());
      assertEquals(XSchema.INTEGER, captor.getValue().getDataOutputPaneModel().getColumnType(),
                  "columnType must reflect the newly-picked column's real type, not the old one");
   }

   /**
    * A caller who sets {@code dataOutputPaneModel.columnType} explicitly, in the same patch as
    * {@code column}, must always win -- the same "forgiving where intent is unambiguous, never
    * override an explicit value" rule {@code impliedSibling} already follows.
    */
   @Test
   void doesNotOverrideAnExplicitColumnType() throws Exception {
      GaugePropertyDialogModel model = new GaugePropertyDialogModel();
      model.getDataOutputPaneModel().setTable("Table1");
      model.getDataOutputPaneModel().setColumn("OldStringCol");
      model.getDataOutputPaneModel().setColumnType(XSchema.STRING);

      GaugePropertyDialogService gaugeService = mock(GaugePropertyDialogService.class);
      VSOutputService outputService = mock(VSOutputService.class);
      when(outputService.getOutputTableColumns(any(), eq("Table1"), eq(true), any()))
         .thenReturn(columnsWithType("OldStringCol", XSchema.STRING,
                                     "NewNumCol", XSchema.INTEGER));

      AssemblyPropertyService service =
         serviceWithGauge(mock(GaugeVSAssembly.class), model, gaugeService, outputService);

      Map<String, Object> patch = new LinkedHashMap<>();
      patch.put("column", "NewNumCol");
      patch.put("dataOutputPaneModel.columnType", XSchema.DOUBLE);
      service.set("tok", principal(), "Gauge1", patch, "");

      ArgumentCaptor<GaugePropertyDialogModel> captor =
         ArgumentCaptor.forClass(GaugePropertyDialogModel.class);
      verify(gaugeService).setGaugePropertyDialogModel(anyString(), anyString(), captor.capture(),
                                                       any(), any(), any());
      assertEquals(XSchema.DOUBLE, captor.getValue().getDataOutputPaneModel().getColumnType(),
                  "an explicit columnType in the same patch must not be overridden");
      verify(outputService, never()).getOutputTableColumns(any(), any(), anyBoolean(), any());
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

   // ── #78154 S6/S2b: TextInput column-option guards (same checks as set_column_options) ──

   private void setTextInputColumnOption(TextInputPropertyDialogModel model,
                                         Map<String, Object> patch)
   {
      AssemblyPropertyService service =
         serviceWithTextInput(mock(TextInputVSAssembly.class), model, new Worksheet());

      try {
         service.set("tok", principal(), "Input1", patch, "");
      }
      catch(RuntimeException ex) {
         throw ex;
      }
      catch(Exception ex) {
         throw new RuntimeException(ex);
      }
   }

   @Test
   void refusesAnUnparseableTextInputDateMinimum() {
      TextInputPropertyDialogModel model = new TextInputPropertyDialogModel();
      Map<String, Object> patch = new LinkedHashMap<>();
      patch.put("textInputColumnOptionPaneModel.type", "Date");
      patch.put("textInputColumnOptionPaneModel.dateEditorModel.minimum", "garbage-not-a-date");

      Exception e = assertThrows(IllegalArgumentException.class,
         () -> setTextInputColumnOption(model, patch));

      assertTrue(e.getMessage().contains("dateEditorModel.minimum"), e.getMessage());
      assertTrue(e.getMessage().contains("garbage-not-a-date"), e.getMessage());
   }

   @Test
   void refusesInvertedTextInputDateBounds() {
      TextInputPropertyDialogModel model = new TextInputPropertyDialogModel();
      Map<String, Object> patch = new LinkedHashMap<>();
      patch.put("textInputColumnOptionPaneModel.type", "Date");
      patch.put("textInputColumnOptionPaneModel.dateEditorModel.minimum", "2030-01-01");
      patch.put("textInputColumnOptionPaneModel.dateEditorModel.maximum", "2020-01-01");

      Exception e = assertThrows(IllegalArgumentException.class,
         () -> setTextInputColumnOption(model, patch));

      assertTrue(e.getMessage().contains("must not be greater than"), e.getMessage());
   }

   @Test
   void refusesInvertedTextInputIntegerAndFloatBounds() {
      Map<String, Object> ints = new LinkedHashMap<>();
      ints.put("textInputColumnOptionPaneModel.type", "Integer");
      ints.put("textInputColumnOptionPaneModel.integerEditorModel.minimum", 10);
      ints.put("textInputColumnOptionPaneModel.integerEditorModel.maximum", 1);
      Exception e = assertThrows(IllegalArgumentException.class,
         () -> setTextInputColumnOption(new TextInputPropertyDialogModel(), ints));
      assertTrue(e.getMessage().contains("integerEditorModel.minimum"), e.getMessage());

      Map<String, Object> floats = new LinkedHashMap<>();
      floats.put("textInputColumnOptionPaneModel.type", "Float");
      floats.put("textInputColumnOptionPaneModel.floatEditorModel.minimum", 5.5);
      floats.put("textInputColumnOptionPaneModel.floatEditorModel.maximum", 1.5);
      e = assertThrows(IllegalArgumentException.class,
         () -> setTextInputColumnOption(new TextInputPropertyDialogModel(), floats));
      assertTrue(e.getMessage().contains("floatEditorModel.minimum"), e.getMessage());
   }

   @Test
   void allowsValidAndEqualTextInputBoundsAndIgnoresTheUnselectedEditor() {
      TextInputPropertyDialogModel model = new TextInputPropertyDialogModel();
      Map<String, Object> patch = new LinkedHashMap<>();
      patch.put("textInputColumnOptionPaneModel.type", "Integer");
      patch.put("textInputColumnOptionPaneModel.integerEditorModel.minimum", 5);
      patch.put("textInputColumnOptionPaneModel.integerEditorModel.maximum", 5);
      // A bad value on an editor the pane's type does not select is never stored, so not checked.
      patch.put("textInputColumnOptionPaneModel.dateEditorModel.minimum", "garbage-not-a-date");

      assertDoesNotThrow(() -> setTextInputColumnOption(model, patch));
      assertEquals(5, model.getTextInputColumnOptionPaneModel().getIntegerEditorModel()
                      .getMinimum());
   }

   // ── #79095: TextInput Text/Password regex pattern ──

   private static TextInputPropertyDialogModel textInputWithType(String type) {
      TextInputPropertyDialogModel model = new TextInputPropertyDialogModel();
      model.getTextInputColumnOptionPaneModel().setType(type);
      return model;
   }

   private static Map<String, Object> patchOf(Object... keysAndValues) {
      Map<String, Object> patch = new LinkedHashMap<>();

      for(int i = 0; i < keysAndValues.length; i += 2) {
         patch.put((String) keysAndValues[i], keysAndValues[i + 1]);
      }

      return patch;
   }

   @Test
   void bug79095TextInputTextPatternUnclosedClassIsRefusedByName() {
      TextInputPropertyDialogModel model = textInputWithType("Text");
      Map<String, Object> patch =
         patchOf("textInputColumnOptionPaneModel.textEditorModel.pattern", "[");

      Exception e = assertThrows(IllegalArgumentException.class,
         () -> setTextInputColumnOption(model, patch));

      assertTrue(e.getMessage().contains("textEditorModel.pattern"), e.getMessage());
      assertTrue(e.getMessage().contains("Unclosed"), e.getMessage());
   }

   @Test
   void bug79095TextInputPasswordPatternUnclosedClassIsRefusedByName() {
      TextInputPropertyDialogModel model = textInputWithType("Password");
      Map<String, Object> patch =
         patchOf("textInputColumnOptionPaneModel.passwordEditorModel.pattern", "[");

      Exception e = assertThrows(IllegalArgumentException.class,
         () -> setTextInputColumnOption(model, patch));

      assertTrue(e.getMessage().contains("passwordEditorModel.pattern"), e.getMessage());
      assertTrue(e.getMessage().contains("Unclosed"), e.getMessage());
   }

   @Test
   void bug79095TextInputPatternIsRefusedUnderACapitalizedSpelling() {
      TextInputPropertyDialogModel model = textInputWithType("Text");
      Map<String, Object> patch =
         patchOf("TextInputColumnOptionPaneModel.TextEditorModel.Pattern", "(");

      Exception e = assertThrows(IllegalArgumentException.class,
         () -> setTextInputColumnOption(model, patch));

      assertTrue(e.getMessage().contains("textEditorModel.pattern"), e.getMessage());
   }

   @Test
   void bug79095TextInputValidNullAndEmptyPatternsAreAccepted() {
      for(String pattern : new String[] { "\\d{3}-\\d{4}", "(?i)abc", "", null }) {
         TextInputPropertyDialogModel text = textInputWithType("Text");
         assertDoesNotThrow(() -> setTextInputColumnOption(
            text, patchOf("textInputColumnOptionPaneModel.textEditorModel.pattern", pattern)),
            "Text pattern " + pattern);
         assertEquals(pattern, text.getTextInputColumnOptionPaneModel().getTextEditorModel()
                         .getPattern());

         TextInputPropertyDialogModel password = textInputWithType("Password");
         assertDoesNotThrow(() -> setTextInputColumnOption(
            password,
            patchOf("textInputColumnOptionPaneModel.passwordEditorModel.pattern", pattern)),
            "Password pattern " + pattern);
      }
   }

   @Test
   void bug79095TextInputPatternOfTheInactiveEditorIsNotChecked() {
      TextInputPropertyDialogModel model = textInputWithType("Integer");

      assertDoesNotThrow(() -> setTextInputColumnOption(
         model, patchOf("textInputColumnOptionPaneModel.passwordEditorModel.pattern", "[")));
      assertDoesNotThrow(() -> setTextInputColumnOption(
         textInputWithType("Password"),
         patchOf("textInputColumnOptionPaneModel.textEditorModel.pattern", "[")));
   }

   @Test
   void bug79095TextInputStoredBadPatternBlocksOnlyWritesThatTouchTheActivePane() {
      // Switching to another type, or writing another pane, is never blocked by stale state.
      for(Map<String, Object> patch : List.of(
         patchOf("textInputColumnOptionPaneModel.type", "Integer"),
         patchOf("textInputColumnOptionPaneModel.type", "Password"),
         patchOf("dataInputPaneModel.writeBackDirectly", true)))
      {
         TextInputPropertyDialogModel model = textInputWithType("Text");
         model.getTextInputColumnOptionPaneModel().getTextEditorModel().setPattern("[");
         assertDoesNotThrow(() -> setTextInputColumnOption(model, patch), patch.toString());
      }

      // A pane write while the active pattern is bad names the pattern (accepted side effect) ...
      TextInputPropertyDialogModel stuck = textInputWithType("Text");
      stuck.getTextInputColumnOptionPaneModel().getTextEditorModel().setPattern("[");
      Exception e = assertThrows(IllegalArgumentException.class, () -> setTextInputColumnOption(
         stuck, patchOf("textInputColumnOptionPaneModel.textEditorModel.errorMessage", "bad")));
      assertTrue(e.getMessage().contains("textEditorModel.pattern"), e.getMessage());

      // ... and the same patch carrying a valid pattern goes through.
      assertDoesNotThrow(() -> setTextInputColumnOption(stuck, patchOf(
         "textInputColumnOptionPaneModel.textEditorModel.errorMessage", "bad",
         "textInputColumnOptionPaneModel.textEditorModel.pattern", "\\d+")));
   }

   // ── #79094: combobox dateFormatPattern ──

   private static final String QDF = "dataInputPaneModel.queryDateFormat";
   private static final String DFP = "dataInputPaneModel.dateFormatPattern";

   /** A model in the state the real getter returns: the info's default pattern, format off. */
   private static ComboboxPropertyDialogModel seededComboboxModel(boolean qdf, String pattern) {
      ComboboxPropertyDialogModel model = new ComboboxPropertyDialogModel();
      model.getDataInputPaneModel().setQueryDateFormat(qdf);
      model.getDataInputPaneModel().setDateFormatPattern(pattern);
      return model;
   }

   private static void setCombobox(ComboboxPropertyDialogModel model,
                                   Map<String, Object> patch) throws Exception
   {
      serviceWithCombobox(mock(ComboBoxVSAssembly.class), model, new Worksheet())
         .set("tok", principal(), "Combo1", patch, "");
   }

   private void assertComboboxRefused(ComboboxPropertyDialogModel model,
                                      Map<String, Object> patch, String... fragments)
   {
      Exception e = assertThrows(IllegalArgumentException.class,
         () -> setCombobox(model, patch), patch.toString());

      assertTrue(e.getMessage().contains("dataInputPaneModel.dateFormatPattern"),
                 e.getMessage());
      assertFalse(e.getMessage().startsWith("Setting"),
                  "must be refused by the pre-write guard, before the setter runs");

      for(String fragment : fragments) {
         assertTrue(e.getMessage().contains(fragment), e.getMessage());
      }
   }

   @Test
   void bug79094InvalidPatternWithQueryDateFormatTrueInSamePatchIsRefused() {
      assertComboboxRefused(seededComboboxModel(false, "yyyy-MM-dd"),
                            patchOf(QDF, true, DFP, "abc"), "abc");
   }

   @Test
   void bug79094InvalidPatternAloneWithQueryDateFormatFalseIsRefused() {
      assertComboboxRefused(seededComboboxModel(false, "yyyy-MM-dd"), patchOf(DFP, "abc"),
                            "abc");
      assertComboboxRefused(seededComboboxModel(false, "yyyy-MM-dd"),
                            patchOf(DFP, "yyyy-MM-dd'"), "yyyy-MM-dd'");
   }

   @Test
   void bug79094InvalidPatternAloneWithQueryDateFormatTrueIsRefused() {
      assertComboboxRefused(seededComboboxModel(true, "yyyy-MM-dd"), patchOf(DFP, "abc"),
                            "abc");
   }

   @Test
   void bug79094PatternsThatCannotFormatADateAreRefused() {
      for(String pattern : new String[] { "yyyy-MM-dd HH:mm", "HH", "yyyy-MM-dd z",
                                          "YYYY-mm-dd" })
      {
         assertComboboxRefused(seededComboboxModel(false, "yyyy-MM-dd"),
                               patchOf(DFP, pattern), pattern);
      }
   }

   @Test
   void bug79094PatternsTheRuntimeHonoursAreAcceptedVerbatim() throws Exception {
      for(boolean qdf : new boolean[] { false, true }) {
         for(String pattern : new String[] { "yyyy-MM", "yyyy_MM_dd", "dd.MM.yyyy", "'abc'",
                                             "MMM d, yyyy", "yyyy-MM-dd 'HH:mm'" })
         {
            ComboboxPropertyDialogModel model = seededComboboxModel(qdf, "yyyy-MM-dd");
            setCombobox(model, patchOf(DFP, pattern));
            assertEquals(pattern, model.getDataInputPaneModel().getDateFormatPattern());
         }
      }
   }

   @Test
   void bug79094BlankPatternIsRefusedOnlyWhenQueryDateFormatIsOn() throws Exception {
      assertComboboxRefused(seededComboboxModel(true, "yyyy-MM-dd"), patchOf(DFP, ""));
      assertComboboxRefused(seededComboboxModel(true, "yyyy-MM-dd"), patchOf(DFP, "  "));
      assertComboboxRefused(seededComboboxModel(false, "yyyy-MM-dd"), patchOf(QDF, true, DFP, ""));
      assertComboboxRefused(seededComboboxModel(false, "yyyy-MM-dd"),
                            patchOf(QDF, true, DFP, "   "));

      Map<String, Object> blankNull = new LinkedHashMap<>();
      blankNull.put(DFP, null);
      assertComboboxRefused(seededComboboxModel(true, "yyyy-MM-dd"), blankNull);

      // With the format off a blank pattern is inactive; a whitespace-only one is stored as-is
      // by the setter, which only tests isEmpty().
      assertDoesNotThrow(() -> setCombobox(seededComboboxModel(false, "yyyy-MM-dd"),
                                           patchOf(DFP, "")));
      assertDoesNotThrow(() -> setCombobox(seededComboboxModel(false, "yyyy-MM-dd"),
                                           patchOf(DFP, "  ")));
      assertDoesNotThrow(() -> setCombobox(seededComboboxModel(false, "yyyy-MM-dd"), blankNull));
   }

   @Test
   void bug79094QueryDateFormatAloneChecksTheStoredPatternOnlyWhenTurningItOn()
      throws Exception
   {
      assertDoesNotThrow(() -> setCombobox(seededComboboxModel(false, "yyyy-MM-dd"),
                                           patchOf(QDF, true)));
      assertComboboxRefused(seededComboboxModel(false, null), patchOf(QDF, true));
      assertComboboxRefused(seededComboboxModel(false, "abc"), patchOf(QDF, true), "abc");
      assertDoesNotThrow(() -> setCombobox(seededComboboxModel(true, "abc"),
                                           patchOf(QDF, false)));
   }

   @Test
   void bug79094StoredBadPatternNeverBlocksAnUnrelatedWrite() {
      for(Map<String, Object> patch : List.of(
         patchOf("inputLabelPaneModel.labelText", "x"),
         patchOf("dataInputPaneModel.writeBackDirectly", true),
         patchOf(QDF, false),
         patchOf(DFP, "yyyy-MM-dd")))
      {
         assertDoesNotThrow(() -> setCombobox(seededComboboxModel(true, "abc"), patch),
                            patch.toString());
      }
   }

   @Test
   void bug79094CapitalizedSpellingsAreRefusedToo() {
      assertComboboxRefused(seededComboboxModel(true, "yyyy-MM-dd"),
                            patchOf("DataInputPaneModel.dateFormatPattern", "abc"));
      assertComboboxRefused(seededComboboxModel(true, "yyyy-MM-dd"),
                            patchOf("dataInputPaneModel.DateFormatPattern", "abc"));
   }

   // ── #78393: capitalised raw paths and list-input embedded/query-source checks ──

   /**
    * PropertyPath treats the first letter of every segment as case-insensitive, so
    * {@code TextInputColumnOptionPaneModel.*} writes the same field as the lowercase spelling --
    * the TextInput bounds guard must see it too.
    */
   @Test
   void refusesAnUnparseableTextInputDateMinimumUnderACapitalizedSpelling() {
      TextInputPropertyDialogModel model = new TextInputPropertyDialogModel();
      Map<String, Object> patch = new LinkedHashMap<>();
      patch.put("TextInputColumnOptionPaneModel.type", "Date");
      patch.put("TextInputColumnOptionPaneModel.dateEditorModel.minimum", "garbage-not-a-date");

      Exception e = assertThrows(IllegalArgumentException.class,
         () -> setTextInputColumnOption(model, patch));

      assertTrue(e.getMessage().contains("dateEditorModel.minimum"), e.getMessage());
      assertTrue(e.getMessage().contains("garbage-not-a-date"), e.getMessage());
   }

   @Test
   void refusesInvertedTextInputIntegerBoundsUnderACapitalizedSpelling() {
      TextInputPropertyDialogModel model = new TextInputPropertyDialogModel();
      Map<String, Object> patch = new LinkedHashMap<>();
      patch.put("TextInputColumnOptionPaneModel.type", "Integer");
      patch.put("TextInputColumnOptionPaneModel.IntegerEditorModel.minimum", 10);
      patch.put("TextInputColumnOptionPaneModel.IntegerEditorModel.maximum", 1);

      Exception e = assertThrows(IllegalArgumentException.class,
         () -> setTextInputColumnOption(model, patch));

      assertTrue(e.getMessage().contains("must not be greater than"), e.getMessage());
   }

   @Test
   void storesValidTextInputDateBoundsUnderACapitalizedSpelling() {
      TextInputPropertyDialogModel model = new TextInputPropertyDialogModel();
      Map<String, Object> patch = new LinkedHashMap<>();
      patch.put("TextInputColumnOptionPaneModel.type", "Date");
      patch.put("TextInputColumnOptionPaneModel.dateEditorModel.minimum", "2026-01-01");
      patch.put("TextInputColumnOptionPaneModel.dateEditorModel.maximum", "2026-06-01");

      setTextInputColumnOption(model, patch);

      assertEquals("2026-01-01",
                   model.getTextInputColumnOptionPaneModel().getDateEditorModel().getMinimum());
   }

   /** A capitalised leaf must not skip the closed {@code visible} domain. */
   @Test
   void refusesAnOutOfDomainVisibleUnderACapitalizedLeaf() {
      TextInputPropertyDialogModel model = new TextInputPropertyDialogModel();
      Map<String, Object> patch = Map.of(
         "textInputGeneralPaneModel.generalPropPaneModel.basicGeneralPaneModel.Visible", "no");

      assertThrows(IllegalArgumentException.class, () -> setTextInputColumnOption(model, patch));
   }

   /** A capitalised leaf must not skip the dead-field {@code locked} refusal. */
   @Test
   void refusesTheDeadLockedFieldUnderACapitalizedLeaf() {
      TextInputPropertyDialogModel model = new TextInputPropertyDialogModel();
      Map<String, Object> patch =
         Map.of("textInputGeneralPaneModel.sizePositionPaneModel.Locked", true);

      Exception e = assertThrows(IllegalArgumentException.class,
         () -> setTextInputColumnOption(model, patch));

      assertTrue(e.getMessage().contains("locked"), e.getMessage());
   }

   private static final String CHECKBOX_EDITOR =
      "checkboxGeneralPaneModel.listValuesPaneModel.comboBoxEditorModel";
   private static final String COMBOBOX_EDITOR =
      "comboboxGeneralPaneModel.listValuesPaneModel.comboBoxEditorModel";
   private static final String RADIO_EDITOR =
      "radioButtonGeneralPaneModel.listValuesPaneModel.comboBoxEditorModel";

   private static Map<String, Object> embeddedInteger(String editor, String... values) {
      Map<String, Object> patch = new LinkedHashMap<>();
      patch.put(editor + ".dataType", "integer");
      patch.put(editor + ".variableListDialogModel.dataType", "integer");
      patch.put(editor + ".variableListDialogModel.labels", java.util.List.of(values));
      patch.put(editor + ".variableListDialogModel.values", java.util.List.of(values));
      return patch;
   }

   private static void setList(AssemblyPropertyService service, Map<String, Object> patch) {
      try {
         service.set("tok", principal(), "List1", patch, "");
      }
      catch(RuntimeException ex) {
         throw ex;
      }
      catch(Exception ex) {
         throw new RuntimeException(ex);
      }
   }

   private static void assertListRefused(AssemblyPropertyService service,
                                         Map<String, Object> patch, String... fragments)
   {
      Exception e = assertThrows(IllegalArgumentException.class, () -> setList(service, patch));

      for(String fragment : fragments) {
         assertTrue(e.getMessage().contains(fragment), e.getMessage());
      }
   }

   @Test
   void refusesAnUnparseableEmbeddedValueOnEveryListInputType() {
      assertListRefused(serviceWithCheckbox(mock(CheckBoxVSAssembly.class),
                                            new CheckboxPropertyDialogModel(), new Worksheet()),
                        embeddedInteger(CHECKBOX_EDITOR, "1", "abc"), "values[1]", "'abc'");
      assertListRefused(serviceWithCombobox(mock(ComboBoxVSAssembly.class),
                                            new ComboboxPropertyDialogModel(), new Worksheet()),
                        embeddedInteger(COMBOBOX_EDITOR, "1", "abc"), "values[1]", "'abc'");
      assertListRefused(serviceWithRadioButton(mock(RadioButtonVSAssembly.class),
                                               new RadioButtonPropertyDialogModel(),
                                               new Worksheet()),
                        embeddedInteger(RADIO_EDITOR, "1", "abc"), "values[1]", "'abc'");
   }

   @Test
   void refusesAnUnparseableEmbeddedValueUnderACapitalizedSpelling() {
      AssemblyPropertyService service = serviceWithCombobox(
         mock(ComboBoxVSAssembly.class), new ComboboxPropertyDialogModel(), new Worksheet());

      assertListRefused(service, embeddedInteger(
         "ComboboxGeneralPaneModel.ListValuesPaneModel.ComboBoxEditorModel", "1", "abc"),
         "values[1]", "'abc'");
   }

   @Test
   void storesValidEmbeddedValuesOnAListInput() {
      ComboboxPropertyDialogModel model = new ComboboxPropertyDialogModel();
      AssemblyPropertyService service =
         serviceWithCombobox(mock(ComboBoxVSAssembly.class), model, new Worksheet());

      setList(service, embeddedInteger(COMBOBOX_EDITOR, "1", "2", "__null__"));

      assertArrayEquals(new String[] { "1", "2", "__null__" },
                        model.getComboboxGeneralPaneModel().getListValuesPaneModel()
                           .getComboBoxEditorModel().getVariableListDialogModel().getValues());
   }

   /** Changing the list's dataType re-checks the values already stored against it. */
   @Test
   void refusesADataTypeChangeTheStoredValuesDoNotParseAs() {
      CheckboxPropertyDialogModel model = new CheckboxPropertyDialogModel();
      model.getCheckboxGeneralPaneModel().getListValuesPaneModel().getComboBoxEditorModel()
         .getVariableListDialogModel().setValues(new String[] { "a", "b" });
      AssemblyPropertyService service =
         serviceWithCheckbox(mock(CheckBoxVSAssembly.class), model, new Worksheet());

      assertListRefused(service,
                        Map.of(CHECKBOX_EDITOR + ".variableListDialogModel.dataType", "integer"),
                        "values[0]", "'a'");
   }

   /** Values that no longer line up with the stored labels are refused, not left misaligned. */
   @Test
   void refusesValuesWhoseCountDisagreesWithTheStoredLabels() {
      CheckboxPropertyDialogModel model = new CheckboxPropertyDialogModel();
      model.getCheckboxGeneralPaneModel().getListValuesPaneModel().getComboBoxEditorModel()
         .getVariableListDialogModel().setLabels(new String[] { "A", "B" });
      AssemblyPropertyService service =
         serviceWithCheckbox(mock(CheckBoxVSAssembly.class), model, new Worksheet());

      assertListRefused(service, Map.of(CHECKBOX_EDITOR + ".variableListDialogModel.values",
                                        java.util.List.of("a", "b", "c")),
                        "labels' has 2 entries but 'values' has 3");
   }

   /** A legacy bad list must not block a write that does not touch the list. */
   @Test
   void leavesALegacyBadListAloneForAnUnrelatedWrite() {
      ComboboxPropertyDialogModel model = new ComboboxPropertyDialogModel();
      VariableListDialogModel list = model.getComboboxGeneralPaneModel().getListValuesPaneModel()
         .getComboBoxEditorModel().getVariableListDialogModel();
      list.setDataType("integer");
      list.setValues(new String[] { "abc" });
      AssemblyPropertyService service =
         serviceWithCombobox(mock(ComboBoxVSAssembly.class), model, new Worksheet());

      setList(service, Map.of("query", true));

      assertTrue(model.getComboboxGeneralPaneModel().getListValuesPaneModel()
                    .getComboBoxEditorModel().isQuery());
   }

   private static AssemblyPropertyService listInputServiceWithColumns(VSAssembly assembly,
                                                                       Object model)
   {
      VSColumnHandler columns = mock(VSColumnHandler.class);
      ColumnSelection selection = new ColumnSelection();
      selection.addAttribute(new ColumnRef(new AttributeRef(null, "COMPANY_NAME")));
      selection.addAttribute(new ColumnRef(new AttributeRef(null, "CUSTOMER_ID")));

      try {
         when(columns.getTableColumns(any(), eq("CUSTOMERS"), any())).thenReturn(selection);
         when(columns.getTableColumns(any(), eq("NOPE"), any())).thenReturn(new ColumnSelection());
      }
      catch(Exception e) {
         throw new IllegalStateException(e);
      }

      return serviceWith(assembly, model, model, new Worksheet(),
                         mock(ImagePropertyDialogService.class),
                         mock(GaugePropertyDialogService.class), mock(VSOutputService.class),
                         columns);
   }

   @Test
   void refusesAQuerySourceWithATableButNoColumnOnEveryListInputType() {
      Map<String, Object> patch = Map.of("query", true, "table", "CUSTOMERS");

      assertListRefused(listInputServiceWithColumns(mock(CheckBoxVSAssembly.class),
                                                    new CheckboxPropertyDialogModel()),
                        patch, "a CheckBox with query:true requires",
                        "selectionListEditorModel.column");
      assertListRefused(listInputServiceWithColumns(mock(ComboBoxVSAssembly.class),
                                                    new ComboboxPropertyDialogModel()),
                        patch, "a ComboBox with query:true requires",
                        "selectionListEditorModel.column");
      assertListRefused(listInputServiceWithColumns(mock(RadioButtonVSAssembly.class),
                                                    new RadioButtonPropertyDialogModel()),
                        patch, "a RadioButton with query:true requires",
                        "selectionListEditorModel.column");
   }

   @Test
   void refusesAQuerySourceWithAnUnknownTableOrColumn() {
      AssemblyPropertyService service = listInputServiceWithColumns(
         mock(ComboBoxVSAssembly.class), new ComboboxPropertyDialogModel());

      assertListRefused(service, Map.of("query", true, "table", "NOPE", "column", "X"),
                        "'NOPE' does not resolve");
      assertListRefused(service, Map.of("query", true, "table", "CUSTOMERS", "column", "X"),
                        "has no column named 'X'", "COMPANY_NAME");
   }

   @Test
   void bindsAQuerySourceAndDefaultsValueToColumn() {
      ComboboxPropertyDialogModel model = new ComboboxPropertyDialogModel();
      AssemblyPropertyService service =
         listInputServiceWithColumns(mock(ComboBoxVSAssembly.class), model);

      setList(service, Map.of("query", true, "table", "CUSTOMERS", "column", "COMPANY_NAME"));

      assertEquals("COMPANY_NAME", model.getComboboxGeneralPaneModel().getListValuesPaneModel()
         .getComboBoxEditorModel().getSelectionListDialogModel().getSelectionListEditorModel()
         .getValue());
   }

   /** The source is checked against the effective model: a stored table plus a new column. */
   @Test
   void acceptsAColumnOnlyWriteOverAStoredQueryTable() {
      ComboboxPropertyDialogModel model = new ComboboxPropertyDialogModel();
      model.getComboboxGeneralPaneModel().getListValuesPaneModel().getComboBoxEditorModel()
         .setQuery(true);
      model.getComboboxGeneralPaneModel().getListValuesPaneModel().getComboBoxEditorModel()
         .getSelectionListDialogModel().getSelectionListEditorModel().setTable("CUSTOMERS");
      AssemblyPropertyService service =
         listInputServiceWithColumns(mock(ComboBoxVSAssembly.class), model);

      setList(service, Map.of("column", "COMPANY_NAME"));

      assertEquals("COMPANY_NAME", model.getComboboxGeneralPaneModel().getListValuesPaneModel()
         .getComboBoxEditorModel().getSelectionListDialogModel().getSelectionListEditorModel()
         .getColumn());
   }

   /** Without query on, a table/column write is not a query source and is not checked. */
   @Test
   void leavesATableOnlyWriteAloneWithoutQuery() {
      ComboboxPropertyDialogModel model = new ComboboxPropertyDialogModel();
      AssemblyPropertyService service =
         listInputServiceWithColumns(mock(ComboBoxVSAssembly.class), model);

      setList(service, Map.of("table", "CUSTOMERS"));

      assertEquals("CUSTOMERS", model.getComboboxGeneralPaneModel().getListValuesPaneModel()
         .getComboBoxEditorModel().getSelectionListDialogModel().getSelectionListEditorModel()
         .getTable());
   }

   // ── #78393 verification: the VCO-003 spellings, every list type, and round trips ──

   /** VCO-003 D1 spells only the first segment capitalised; Float must be refused too. */
   @Test
   void refusesInvertedTextInputFloatBoundsUnderACapitalizedFirstSegment() {
      Map<String, Object> patch = new LinkedHashMap<>();
      patch.put("TextInputColumnOptionPaneModel.type", "Float");
      patch.put("TextInputColumnOptionPaneModel.floatEditorModel.minimum", 9.5);
      patch.put("TextInputColumnOptionPaneModel.floatEditorModel.maximum", 0.5);

      Exception e = assertThrows(IllegalArgumentException.class,
         () -> setTextInputColumnOption(new TextInputPropertyDialogModel(), patch));

      assertTrue(e.getMessage().contains("floatEditorModel.minimum"), e.getMessage());
      assertTrue(e.getMessage().contains("must not be greater than"), e.getMessage());
   }

   @Test
   void refusesInvertedTextInputIntegerBoundsUnderACapitalizedFirstSegment() {
      Map<String, Object> patch = new LinkedHashMap<>();
      patch.put("TextInputColumnOptionPaneModel.type", "Integer");
      patch.put("TextInputColumnOptionPaneModel.integerEditorModel.minimum", 10);
      patch.put("TextInputColumnOptionPaneModel.integerEditorModel.maximum", 1);

      Exception e = assertThrows(IllegalArgumentException.class,
         () -> setTextInputColumnOption(new TextInputPropertyDialogModel(), patch));

      assertTrue(e.getMessage().contains("integerEditorModel.minimum"), e.getMessage());
   }

   /** A capitalised path to an ordinary field still writes that field. */
   @Test
   void writesOrdinaryFieldsUnderCapitalizedSegments() {
      TextInputPropertyDialogModel model = new TextInputPropertyDialogModel();
      Map<String, Object> patch = new LinkedHashMap<>();
      patch.put("TextInputGeneralPaneModel.ToolTip", "Pick a value");
      patch.put("TextInputGeneralPaneModel.GeneralPropPaneModel.BasicGeneralPaneModel.Visible",
                "hide");

      setTextInputColumnOption(model, patch);

      assertEquals("Pick a value", model.getTextInputGeneralPaneModel().getToolTip());
      assertEquals("hide", model.getTextInputGeneralPaneModel().getGeneralPropPaneModel()
         .getBasicGeneralPaneModel().getVisible());
   }

   @Test
   void storesValidEmbeddedIntegerValuesExactlyOnEveryListInputType() {
      CheckboxPropertyDialogModel checkbox = new CheckboxPropertyDialogModel();
      setList(serviceWithCheckbox(mock(CheckBoxVSAssembly.class), checkbox, new Worksheet()),
              embeddedInteger(CHECKBOX_EDITOR, "1", "2"));
      assertArrayEquals(new String[] { "1", "2" },
                        checkbox.getCheckboxGeneralPaneModel().getListValuesPaneModel()
                           .getComboBoxEditorModel().getVariableListDialogModel().getValues());

      ComboboxPropertyDialogModel combobox = new ComboboxPropertyDialogModel();
      setList(serviceWithCombobox(mock(ComboBoxVSAssembly.class), combobox, new Worksheet()),
              embeddedInteger(COMBOBOX_EDITOR, "1", "2"));
      assertArrayEquals(new String[] { "1", "2" },
                        combobox.getComboboxGeneralPaneModel().getListValuesPaneModel()
                           .getComboBoxEditorModel().getVariableListDialogModel().getValues());

      RadioButtonPropertyDialogModel radio = new RadioButtonPropertyDialogModel();
      setList(serviceWithRadioButton(mock(RadioButtonVSAssembly.class), radio, new Worksheet()),
              embeddedInteger(RADIO_EDITOR, "1", "2"));
      assertArrayEquals(new String[] { "1", "2" },
                        radio.getRadioButtonGeneralPaneModel().getListValuesPaneModel()
                           .getComboBoxEditorModel().getVariableListDialogModel().getValues());
   }

   /** The patch the plugin posts for VCO-003's control: column mirrored into the raw value. */
   @Test
   void bindsTheQuerySourceThePluginPostsForTableAndColumn() {
      ComboboxPropertyDialogModel model = new ComboboxPropertyDialogModel();
      AssemblyPropertyService service =
         listInputServiceWithColumns(mock(ComboBoxVSAssembly.class), model);
      Map<String, Object> patch = new LinkedHashMap<>();
      patch.put("query", true);
      patch.put("table", "CUSTOMERS");
      patch.put("column", "COMPANY_NAME");
      patch.put(COMBOBOX_EDITOR + ".selectionListDialogModel.selectionListEditorModel.value",
                "COMPANY_NAME");

      setList(service, patch);

      var source = model.getComboboxGeneralPaneModel().getListValuesPaneModel()
         .getComboBoxEditorModel().getSelectionListDialogModel().getSelectionListEditorModel();
      assertEquals("CUSTOMERS", source.getTable());
      assertEquals("COMPANY_NAME", source.getColumn());
      assertEquals("COMPANY_NAME", source.getValue());
   }

   @Test
   void acceptsQueryAloneOnEveryListInputType() {
      Map<String, Object> patch = Map.of("query", true);

      CheckboxPropertyDialogModel checkbox = new CheckboxPropertyDialogModel();
      setList(listInputServiceWithColumns(mock(CheckBoxVSAssembly.class), checkbox), patch);
      assertTrue(checkbox.getCheckboxGeneralPaneModel().getListValuesPaneModel()
                    .getComboBoxEditorModel().isQuery());

      ComboboxPropertyDialogModel combobox = new ComboboxPropertyDialogModel();
      setList(listInputServiceWithColumns(mock(ComboBoxVSAssembly.class), combobox), patch);
      assertTrue(combobox.getComboboxGeneralPaneModel().getListValuesPaneModel()
                    .getComboBoxEditorModel().isQuery());

      RadioButtonPropertyDialogModel radio = new RadioButtonPropertyDialogModel();
      setList(listInputServiceWithColumns(mock(RadioButtonVSAssembly.class), radio), patch);
      assertTrue(radio.getRadioButtonGeneralPaneModel().getListValuesPaneModel()
                    .getComboBoxEditorModel().isQuery());
   }

   /** Bug #78590 P2: a table written with query off, then query turned on, binds nothing. */
   @Test
   void refusesTurningQueryOnOverAStoredTableWithNoColumnOnEveryListInputType() {
      assertQueryOnAfterTableRefused(mock(CheckBoxVSAssembly.class),
                                     new CheckboxPropertyDialogModel(), "a CheckBox");
      assertQueryOnAfterTableRefused(mock(ComboBoxVSAssembly.class),
                                     new ComboboxPropertyDialogModel(), "a ComboBox");
      assertQueryOnAfterTableRefused(mock(RadioButtonVSAssembly.class),
                                     new RadioButtonPropertyDialogModel(), "a RadioButton");
   }

   private static void assertQueryOnAfterTableRefused(VSAssembly assembly, Object model,
                                                      String control)
   {
      AssemblyPropertyService service = listInputServiceWithColumns(assembly, model);

      setList(service, Map.of("table", "CUSTOMERS"));
      assertListRefused(service, Map.of("query", true), control + " with query:true requires",
                        "selectionListEditorModel.column");
   }

   @Test
   void acceptsTurningQueryOnOverAStoredTableAndColumn() {
      CheckboxPropertyDialogModel model = new CheckboxPropertyDialogModel();
      AssemblyPropertyService service =
         listInputServiceWithColumns(mock(CheckBoxVSAssembly.class), model);

      setList(service, Map.of("table", "CUSTOMERS", "column", "COMPANY_NAME"));
      setList(service, Map.of("query", true));

      assertTrue(model.getCheckboxGeneralPaneModel().getListValuesPaneModel()
                    .getComboBoxEditorModel().isQuery());
   }

   /** Writing query:true back over a source that already has query on is not a transition. */
   @Test
   void doesNotRefuseAnEchoOfQueryTrueOverAnExistingQuerySource() {
      ComboboxPropertyDialogModel model = new ComboboxPropertyDialogModel();
      model.getComboboxGeneralPaneModel().getListValuesPaneModel().getComboBoxEditorModel()
         .setQuery(true);
      model.getComboboxGeneralPaneModel().getListValuesPaneModel().getComboBoxEditorModel()
         .getSelectionListDialogModel().getSelectionListEditorModel().setTable("CUSTOMERS");
      AssemblyPropertyService service =
         listInputServiceWithColumns(mock(ComboBoxVSAssembly.class), model);

      assertDoesNotThrow(() -> setList(service, Map.of("query", true)));
   }

   @Test
   void refusesTurningQueryOnWhenWrittenThroughTheRawPath() {
      AssemblyPropertyService service = listInputServiceWithColumns(
         mock(CheckBoxVSAssembly.class), new CheckboxPropertyDialogModel());

      setList(service, Map.of(CHECKBOX_EDITOR + ".selectionListDialogModel" +
                              ".selectionListEditorModel.table", "CUSTOMERS"));
      assertListRefused(service, Map.of(CHECKBOX_EDITOR + ".query", true),
                        "a CheckBox with query:true requires");
   }

   /**
    * The label/value count check must not break a get -> set round trip: writing back the list
    * as read, replacing values with the same count, or replacing both with a new count.
    */
   @Test
   void acceptsListRoundTripsThatKeepLabelsAndValuesAligned() {
      CheckboxPropertyDialogModel model = new CheckboxPropertyDialogModel();
      VariableListDialogModel list = model.getCheckboxGeneralPaneModel().getListValuesPaneModel()
         .getComboBoxEditorModel().getVariableListDialogModel();
      list.setDataType("integer");
      list.setLabels(new String[] { "One", "Two" });
      list.setValues(new String[] { "1", "2" });
      AssemblyPropertyService service =
         serviceWithCheckbox(mock(CheckBoxVSAssembly.class), model, new Worksheet());
      String listPath = CHECKBOX_EDITOR + ".variableListDialogModel";

      Map<String, Object> asRead = new LinkedHashMap<>();
      asRead.put(listPath + ".dataType", list.getDataType());
      asRead.put(listPath + ".labels", java.util.List.of(list.getLabels()));
      asRead.put(listPath + ".values", java.util.List.of(list.getValues()));
      setList(service, asRead);

      setList(service, Map.of(listPath + ".values", java.util.List.of("3", "4")));
      assertArrayEquals(new String[] { "3", "4" }, list.getValues());

      Map<String, Object> grown = new LinkedHashMap<>();
      grown.put(listPath + ".labels", java.util.List.of("A", "B", "C"));
      grown.put(listPath + ".values", java.util.List.of("5", "6", "7"));
      setList(service, grown);
      assertArrayEquals(new String[] { "A", "B", "C" }, list.getLabels());
      assertArrayEquals(new String[] { "5", "6", "7" }, list.getValues());
   }

   /** With no stored labels, a values-only write of any length is still accepted. */
   @Test
   void acceptsAValuesOnlyWriteWhenNoLabelsAreStored() {
      RadioButtonPropertyDialogModel model = new RadioButtonPropertyDialogModel();
      AssemblyPropertyService service =
         serviceWithRadioButton(mock(RadioButtonVSAssembly.class), model, new Worksheet());

      setList(service, Map.of(RADIO_EDITOR + ".variableListDialogModel.values",
                              java.util.List.of("a", "b", "c")));

      assertArrayEquals(new String[] { "a", "b", "c" },
                        model.getRadioButtonGeneralPaneModel().getListValuesPaneModel()
                           .getComboBoxEditorModel().getVariableListDialogModel().getValues());
   }

   // ── checkbox dead Data Input keys (bug #78579) ───────────────────────────

   private static Exception refuseCheckbox(CheckboxPropertyDialogModel model, Worksheet ws,
                                           Map<String, Object> patch)
   {
      AssemblyPropertyService service =
         serviceWithCheckbox(mock(CheckBoxVSAssembly.class), model, ws);

      return assertThrows(IllegalArgumentException.class,
         () -> service.set("tok", principal(), "Check1", patch, ""));
   }

   private static CheckboxPropertyDialogModel embBoundCheckbox() {
      CheckboxPropertyDialogModel model = new CheckboxPropertyDialogModel();
      model.getDataInputPaneModel().setTable(EMBEDDED_TABLE);
      return model;
   }

   private static void assertRefusedKeys(Exception thrown, String... keys) {
      String msg = thrown.getMessage();
      assertTrue(msg.startsWith("A checkbox cannot store a column, row or write-back target: " +
                                "refused " + String.join(", ", keys) + "."), msg);
   }

   @Test
   void checkboxColumnValueOnlyIsRefused() {
      assertRefusedKeys(refuseCheckbox(embBoundCheckbox(), embeddedTableWorksheet(),
         Map.of("dataInputPaneModel.columnValue", "state")),
         "dataInputPaneModel.columnValue");
   }

   @Test
   void checkboxRowValueOnlyIsRefused() {
      assertRefusedKeys(refuseCheckbox(embBoundCheckbox(), embeddedTableWorksheet(),
         Map.of("dataInputPaneModel.rowValue", "1")),
         "dataInputPaneModel.rowValue");
   }

   @Test
   void checkboxWriteBackDirectlyTrueIsRefused() {
      assertRefusedKeys(refuseCheckbox(embBoundCheckbox(), embeddedTableWorksheet(),
         Map.of("dataInputPaneModel.writeBackDirectly", true)),
         "dataInputPaneModel.writeBackDirectly");
   }

   @Test
   void checkboxThreeKeyPatchIsRefusedInOneMessageNamingExactlyTheRefusedKeys() {
      Map<String, Object> patch = new LinkedHashMap<>();
      patch.put("dataInputPaneModel.columnValue", "state");
      patch.put("dataInputPaneModel.rowValue", "1");
      patch.put("dataInputPaneModel.writeBackDirectly", true);

      assertRefusedKeys(refuseCheckbox(embBoundCheckbox(), embeddedTableWorksheet(), patch),
         "dataInputPaneModel.columnValue", "dataInputPaneModel.rowValue",
         "dataInputPaneModel.writeBackDirectly");
   }

   @Test
   void checkboxTwoKeyPatchDoesNotNameTheAcceptedKey() {
      Map<String, Object> patch = new LinkedHashMap<>();
      patch.put("dataInputPaneModel.columnValue", "state");
      patch.put("dataInputPaneModel.writeBackDirectly", false);

      assertRefusedKeys(refuseCheckbox(embBoundCheckbox(), embeddedTableWorksheet(), patch),
         "dataInputPaneModel.columnValue");
   }

   @Test
   void checkboxColumnValueIsNotACarrierWhenTheTableIsAVariableReference() {
      Worksheet ws = new Worksheet();
      ws.addAssembly(new DefaultVariableAssembly(ws, "v"));
      Map<String, Object> patch = new LinkedHashMap<>();
      patch.put("dataInputPaneModel.table", "$(v)");
      patch.put("dataInputPaneModel.columnValue", "x");

      assertRefusedKeys(refuseCheckbox(new CheckboxPropertyDialogModel(), ws, patch),
         "dataInputPaneModel.columnValue");
   }

   @Test
   void checkboxEmptyAndFalseValuesAreAcceptedNoOps() {
      Map<String, Object> patch = new LinkedHashMap<>();
      patch.put("dataInputPaneModel.columnValue", "");
      patch.put("dataInputPaneModel.rowValue", "");
      patch.put("dataInputPaneModel.writeBackDirectly", false);
      AssemblyPropertyService service = serviceWithCheckbox(
         mock(CheckBoxVSAssembly.class), embBoundCheckbox(), embeddedTableWorksheet());

      assertDoesNotThrow(() -> service.set("tok", principal(), "Check1", patch, ""));
   }

   @Test
   void checkboxVariablesFolderLabelCarrierStillBindsTheVariable() {
      Worksheet ws = new Worksheet();
      ws.addAssembly(new DefaultVariableAssembly(ws, "StartDate"));
      CheckboxPropertyDialogModel model = new CheckboxPropertyDialogModel();
      AssemblyPropertyService service =
         serviceWithCheckbox(mock(CheckBoxVSAssembly.class), model, ws);
      Map<String, Object> patch = new LinkedHashMap<>();
      patch.put("dataInputPaneModel.table", "Variables");
      patch.put("dataInputPaneModel.columnValue", "StartDate");

      assertDoesNotThrow(() -> service.set("tok", principal(), "Check1", patch, ""));
   }

   @Test
   void checkboxVariablesLabelIsNotACarrierWhenAWorksheetVariableIsNamedVariables() {
      Worksheet ws = new Worksheet();
      ws.addAssembly(new DefaultVariableAssembly(ws, "Variables"));
      ws.addAssembly(new DefaultVariableAssembly(ws, "StartDate"));
      Map<String, Object> patch = new LinkedHashMap<>();
      patch.put("dataInputPaneModel.table", "Variables");
      patch.put("dataInputPaneModel.columnValue", "StartDate");

      assertRefusedKeys(refuseCheckbox(new CheckboxPropertyDialogModel(), ws, patch),
         "dataInputPaneModel.columnValue");
   }

   @Test
   void checkboxDollarVariableColumnValueWithEmptyTableStillBinds() {
      Worksheet ws = new Worksheet();
      ws.addAssembly(new DefaultVariableAssembly(ws, "StartDate"));
      AssemblyPropertyService service = serviceWithCheckbox(
         mock(CheckBoxVSAssembly.class), new CheckboxPropertyDialogModel(), ws);

      assertDoesNotThrow(() -> service.set("tok", principal(), "Check1",
         Map.of("dataInputPaneModel.columnValue", "$(StartDate)"), ""));
   }

   @Test
   void checkboxUnknownDollarVariableKeepsTheUnknownVariableMessage() {
      Exception thrown = refuseCheckbox(new CheckboxPropertyDialogModel(), new Worksheet(),
         Map.of("dataInputPaneModel.columnValue", "$(nope)"));

      assertTrue(thrown.getMessage().contains("Unknown variable 'nope'"), thrown.getMessage());
   }

   @Test
   void otherInputTypesStillAcceptAllThreeKeys() {
      Map<String, Object> patch = new LinkedHashMap<>();
      patch.put("dataInputPaneModel.columnValue", "state");
      patch.put("dataInputPaneModel.rowValue", "1");
      patch.put("dataInputPaneModel.writeBackDirectly", true);

      TextInputPropertyDialogModel text = new TextInputPropertyDialogModel();
      text.getDataInputPaneModel().setTable(EMBEDDED_TABLE);
      RadioButtonPropertyDialogModel radio = new RadioButtonPropertyDialogModel();
      radio.getDataInputPaneModel().setTable(EMBEDDED_TABLE);
      ComboboxPropertyDialogModel combo = new ComboboxPropertyDialogModel();
      combo.getDataInputPaneModel().setTable(EMBEDDED_TABLE);

      assertDoesNotThrow(() -> serviceWithTextInput(
         mock(TextInputVSAssembly.class), text, embeddedTableWorksheet())
         .set("tok", principal(), "A", patch, ""));
      assertDoesNotThrow(() -> serviceWithRadioButton(
         mock(RadioButtonVSAssembly.class), radio, embeddedTableWorksheet())
         .set("tok", principal(), "A", patch, ""));
      assertDoesNotThrow(() -> serviceWithCombobox(
         mock(ComboBoxVSAssembly.class), combo, embeddedTableWorksheet())
         .set("tok", principal(), "A", patch, ""));
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

   /**
    * Bug #77028: lets a test both capture the written {@code GaugePropertyDialogModel} (via a
    * caller-supplied {@code gaugeService} mock, the same shape {@link #serviceWithImage} already
    * uses) and stub {@code outputService.getOutputTableColumns} to return a real column list, so
    * the {@code columnType} re-derivation has real column metadata to look up.
    */
   private static AssemblyPropertyService serviceWithGauge(
      VSAssembly assembly, GaugePropertyDialogModel model,
      GaugePropertyDialogService gaugeService, VSOutputService outputService)
   {
      try {
         when(gaugeService.getGaugePropertyDialogModel(anyString(), anyString(),
                                                       any(Principal.class)))
            .thenReturn(model);
      }
      catch(Exception e) {
         throw new IllegalStateException(e);
      }

      return serviceWith(assembly, model, null, null, mock(ImagePropertyDialogService.class),
                         gaugeService, outputService);
   }

   /** A {@code ColumnSelection} of alternating (name, dataType) pairs, for {@link #serviceWithGauge}. */
   private static ColumnSelection columnsWithType(String... nameThenType) {
      ColumnSelection selection = new ColumnSelection();

      for(int i = 0; i < nameThenType.length; i += 2) {
         ColumnRef ref = new ColumnRef(new AttributeRef(null, nameThenType[i]));
         ref.setDataType(nameThenType[i + 1]);
         selection.addAttribute(ref);
      }

      return selection;
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
      return serviceWith(assembly, model, inputModel, baseWorksheet, imageService,
                         mock(GaugePropertyDialogService.class), mock(VSOutputService.class));
   }

   private static AssemblyPropertyService serviceWith(
      VSAssembly assembly, Object model, Object inputModel,
      Worksheet baseWorksheet, ImagePropertyDialogService imageService,
      GaugePropertyDialogService gauge, VSOutputService outputService)
   {
      return serviceWith(assembly, model, inputModel, baseWorksheet, imageService, gauge,
                         outputService, mock(VSColumnHandler.class));
   }

   private static AssemblyPropertyService serviceWith(
      VSAssembly assembly, Object model, Object inputModel,
      Worksheet baseWorksheet, ImagePropertyDialogService imageService,
      GaugePropertyDialogService gauge, VSOutputService outputService,
      VSColumnHandler columnHandler)
   {
      lastDataOutput = mock(DataOutputService.class);
      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getAssembly(anyString())).thenReturn(assembly);
      when(vs.getBaseWorksheet()).thenReturn(baseWorksheet);
      when(vs.getAllVariables()).thenReturn(new inetsoft.uql.schema.UserVariable[0]);
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
      lastSelectionList = selectionList;

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
      lastSelectionTree = selectionTree;

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
      lastCalendar = calendar;

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
         else if(inputModel instanceof SliderPropertyDialogModel sliderModel) {
            when(inputService.getSliderPropertyDialogModel(anyString(), anyString(),
                                                            any(Principal.class)))
               .thenReturn(sliderModel);
         }
         else if(inputModel instanceof SpinnerPropertyDialogModel spinnerModel) {
            when(inputService.getSpinnerPropertyDialogModel(anyString(), anyString(),
                                                             any(Principal.class)))
               .thenReturn(spinnerModel);
         }

         lastInput = inputService;
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
      lastRangeSlider = rangeSlider;

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

      SelectionContainerPropertyDialogService selectionContainer =
         mock(SelectionContainerPropertyDialogService.class);

      if(model instanceof SelectionContainerPropertyDialogModel selectionContainerModel) {
         try {
            when(selectionContainer.getSelectionContainerPropertyModel(anyString(), anyString(),
                                                                        any(Principal.class)))
               .thenReturn(selectionContainerModel);
         }
         catch(Exception e) {
            throw new IllegalStateException(e);
         }
      }

      TextPropertyDialogService text = mock(TextPropertyDialogService.class);
      CrosstabPropertyDialogService crosstab = mock(CrosstabPropertyDialogService.class);

      try {
         if(model instanceof TextPropertyDialogModel textModel) {
            when(text.getTextPropertyDialogModel(anyString(), anyString(), any(Principal.class)))
               .thenReturn(textModel);
         }

         if(model instanceof CrosstabPropertyDialogModel crosstabModel) {
            when(crosstab.getCrosstabPropertyDialogModel(anyString(), anyString(),
                                                         any(Principal.class)))
               .thenReturn(crosstabModel);
         }
      }
      catch(Exception e) {
         throw new IllegalStateException(e);
      }

      lastDialogServices = List.of(chart, table, text, crosstab);
      lastLine = mock(LinePropertyDialogService.class);
      lastOval = mock(OvalPropertyDialogService.class);
      lastRectangle = mock(RectanglePropertyDialogService.class);

      return new AssemblyPropertyService(
         sessions, gauge, imageService,
         text,
         chart, table,
         crosstab,
         selectionList,
         selectionTree,
         inputService,
         rangeSlider,
         calendar, mock(TabPropertyDialogService.class),
         calcTable,
         groupContainer,
         lastLine, lastOval, lastRectangle,
         selectionContainer,
         mock(SubmitPropertyDialogService.class),
         outputService, columnHandler, lastDataOutput);
   }

   // ── calendar viewMode / chart line-style int-enum domains (bug #78194) ───

   @Test
   void canonicalizesDoubleTokenAndRefusesOutOfDomainCalendarViewMode() throws Exception {
      CalendarPropertyDialogModel model = new CalendarPropertyDialogModel();
      AssemblyPropertyService service =
         serviceWithCalendar(mock(CalendarVSAssembly.class), model);

      service.set("tok", principal(), "Calendar1", Map.of("viewMode", "double"), "");
      assertEquals(2, model.getCalendarAdvancedPaneModel().getViewMode());
      service.set("tok", principal(), "Calendar1", Map.of("viewMode", 1), "");
      assertEquals(1, model.getCalendarAdvancedPaneModel().getViewMode());

      for(Object bad : new Object[] { 999, 0, -5, "triple" }) {
         Exception thrown = assertThrows(IllegalArgumentException.class,
            () -> service.set("tok", principal(), "Calendar1", Map.of("viewMode", bad), ""));
         assertTrue(thrown.getMessage().contains("'single' (1)") &&
                    thrown.getMessage().contains("'double' (2)"), thrown.getMessage());
      }

      assertEquals(1, model.getCalendarAdvancedPaneModel().getViewMode());
   }

   @Test
   void canonicalizesAndRefusesChartLineStyles() throws Exception {
      ChartPropertyDialogModel model = new ChartPropertyDialogModel();
      AssemblyPropertyService service = serviceWith(mock(ChartVSAssembly.class), model);

      service.set("tok", principal(), "Chart1",
                  Map.of("trendLineStyle", "dashed", "diagonalLineStyle", 4097,
                         "quadrantGridLineStyle", "none"), "");
      assertEquals(GraphConstants.DASH_LINE, model.getChartLinePaneModel().getTrendLineStyle());
      assertEquals(GraphConstants.THIN_LINE, model.getChartLinePaneModel().getDiagonalLineStyle());
      assertEquals(0, model.getChartLinePaneModel().getQuadrantGridLineStyle());

      for(String key : new String[] { "trendLineStyle", "diagonalLineStyle",
                                      "quadrantGridLineStyle", "chartLinePaneModel.xGridLineStyle",
                                      "chartLinePaneModel.yGridLineStyle" })
      {
         for(Object bad : new Object[] { 999, -7, 1, "bogus" }) {
            Exception thrown = assertThrows(IllegalArgumentException.class,
               () -> service.set("tok", principal(), "Chart1", Map.of(key, bad), ""));
            assertTrue(thrown.getMessage().contains("'dashed' (" + GraphConstants.DASH_LINE + ")"),
                       "must name the valid values: " + thrown.getMessage());
         }
      }
   }

   @Test
   void allowsDefaultMinusOneOnlyOnTheTrendLineStyle() throws Exception {
      ChartPropertyDialogModel model = new ChartPropertyDialogModel();
      AssemblyPropertyService service = serviceWith(mock(ChartVSAssembly.class), model);

      service.set("tok", principal(), "Chart1", Map.of("trendLineStyle", -1), "");
      assertEquals(-1, model.getChartLinePaneModel().getTrendLineStyle());
      assertThrows(IllegalArgumentException.class,
         () -> service.set("tok", principal(), "Chart1", Map.of("diagonalLineStyle", -1), ""));
   }

   /** Every value a read can emit (the dropdown's ints) must be accepted back on write. */
   @Test
   void acceptsEveryChartLineStyleIntRoundTrip() throws Exception {
      ChartPropertyDialogModel model = new ChartPropertyDialogModel();
      AssemblyPropertyService service = serviceWith(mock(ChartVSAssembly.class), model);
      int[] styles = { 0, GraphConstants.THIN_LINE, GraphConstants.MEDIUM_LINE,
                       GraphConstants.THICK_LINE, GraphConstants.DOT_LINE, GraphConstants.DASH_LINE,
                       GraphConstants.THIN_THIN_LINE, GraphConstants.ULTRA_THIN_LINE,
                       GraphConstants.MEDIUM_DASH, GraphConstants.LARGE_DASH };

      for(String key : new String[] { "trendLineStyle", "diagonalLineStyle",
                                      "quadrantGridLineStyle", "chartLinePaneModel.xGridLineStyle",
                                      "chartLinePaneModel.yGridLineStyle" })
      {
         for(int style : styles) {
            service.set("tok", principal(), "Chart1", Map.of(key, style), "");
            assertEquals(style, lineStyle(model, key), key);
         }
      }

      service.set("tok", principal(), "Chart1", Map.of("trendLineStyle", -1), "");
      assertEquals(-1, model.getChartLinePaneModel().getTrendLineStyle());
   }

   private static int lineStyle(ChartPropertyDialogModel model, String key) {
      ChartLinePaneModel pane = model.getChartLinePaneModel();

      switch(key) {
      case "trendLineStyle": return pane.getTrendLineStyle();
      case "diagonalLineStyle": return pane.getDiagonalLineStyle();
      case "quadrantGridLineStyle": return pane.getQuadrantGridLineStyle();
      case "chartLinePaneModel.xGridLineStyle": return pane.getxGridLineStyle();
      default: return pane.getyGridLineStyle();
      }
   }

   private static Principal principal() {
      return () -> "admin";
   }

   // ── empty path segments (bug #78440) ─────────────────────────────────────

   @Test
   void refusesATrailingDotOnTheChartTrendLineStyleWithoutWritingIt() throws Exception {
      ChartPropertyDialogModel model = new ChartPropertyDialogModel();
      AssemblyPropertyService service = serviceWith(mock(ChartVSAssembly.class), model);
      int before = model.getChartLinePaneModel().getTrendLineStyle();

      for(String key : new String[] { "chartLinePaneModel.trendLineStyle.",
                                      "ChartLinePaneModel.TrendLineStyle..",
                                      "chartLinePaneModel..trendLineStyle", "." })
      {
         Exception e = assertThrows(IllegalArgumentException.class,
            () -> service.set("tok", principal(), "Chart1", Map.of(key, 999), ""), key);
         assertTrue(e.getMessage().contains("empty segment"), e.getMessage());
      }

      assertEquals(before, model.getChartLinePaneModel().getTrendLineStyle());
   }

   @Test
   void refusesATrailingDotOnCalendarViewModeSelectionSortTypeAndTreeMode() throws Exception {
      CalendarPropertyDialogModel calendar = new CalendarPropertyDialogModel();
      int viewMode = calendar.getCalendarAdvancedPaneModel().getViewMode();
      assertThrows(IllegalArgumentException.class,
         () -> serviceWithCalendar(mock(CalendarVSAssembly.class), calendar).set(
            "tok", principal(), "Calendar1",
            Map.of("calendarAdvancedPaneModel.viewMode.", 999), ""));
      assertEquals(viewMode, calendar.getCalendarAdvancedPaneModel().getViewMode());

      SelectionListPropertyDialogModel list = new SelectionListPropertyDialogModel();
      int sortType = list.getSelectionGeneralPaneModel().getSortType();
      assertThrows(IllegalArgumentException.class,
         () -> serviceWithSelectionList(mock(SelectionListVSAssembly.class), list).set(
            "tok", principal(), "Selection1",
            Map.of("selectionGeneralPaneModel.sortType.", 999), ""));
      assertEquals(sortType, list.getSelectionGeneralPaneModel().getSortType());

      SelectionTreePropertyDialogModel tree = new SelectionTreePropertyDialogModel();
      int mode = tree.getSelectionTreePaneModel().getMode();
      assertThrows(IllegalArgumentException.class,
         () -> serviceWithSelectionTree(mock(SelectionTreeVSAssembly.class), tree).set(
            "tok", principal(), "SelectionTree1",
            Map.of("selectionTreePaneModel.mode.", 7), ""));
      assertEquals(mode, tree.getSelectionTreePaneModel().getMode());
   }

   @Test
   void refusesATrailingDotOnTheDeadLockedFieldAndTheLockedFieldUnderAnyCapitalisation() {
      for(String key : new String[] {
         "textInputGeneralPaneModel.sizePositionPaneModel.locked.",
         "TextInputGeneralPaneModel.SizePositionPaneModel.Locked" })
      {
         assertThrows(IllegalArgumentException.class,
            () -> setTextInputColumnOption(new TextInputPropertyDialogModel(), Map.of(key, true)),
            key);
      }
   }

   @Test
   void refusesCapitalisedOutOfDomainIntEnumPaths() throws Exception {
      ChartPropertyDialogModel chart = new ChartPropertyDialogModel();
      AssemblyPropertyService chartService = serviceWith(mock(ChartVSAssembly.class), chart);

      for(String key : new String[] { "ChartLinePaneModel.trendLineStyle",
                                      "chartLinePaneModel.TrendLineStyle",
                                      "ChartLinePaneModel.XGridLineStyle" })
      {
         assertThrows(IllegalArgumentException.class,
            () -> chartService.set("tok", principal(), "Chart1", Map.of(key, 999), ""), key);
      }

      SelectionTreePropertyDialogModel tree = new SelectionTreePropertyDialogModel();
      assertThrows(IllegalArgumentException.class,
         () -> serviceWithSelectionTree(mock(SelectionTreeVSAssembly.class), tree).set(
            "tok", principal(), "SelectionTree1",
            Map.of("SelectionTreePaneModel.mode", 7), ""));
      CalendarPropertyDialogModel calendar = new CalendarPropertyDialogModel();
      assertThrows(IllegalArgumentException.class,
         () -> serviceWithCalendar(mock(CalendarVSAssembly.class), calendar).set(
            "tok", principal(), "Calendar1",
            Map.of("CalendarAdvancedPaneModel.viewMode", 999), ""));
      SelectionListPropertyDialogModel list = new SelectionListPropertyDialogModel();
      assertThrows(IllegalArgumentException.class,
         () -> serviceWithSelectionList(mock(SelectionListVSAssembly.class), list).set(
            "tok", principal(), "Selection1",
            Map.of("SelectionGeneralPaneModel.sortType", 999), ""));
   }

   // ── bug-79156: non-positive width/height silently ignored by the dialog services ──

   @Test
   void refusesNonPositiveWidthAndHeightWithoutWriting() throws Exception {
      for(Map.Entry<String, Object> bad : List.<Map.Entry<String, Object>>of(
         Map.entry("height", 0), Map.entry("width", 0), Map.entry("height", -3),
         Map.entry("height", "0")))
      {
         GaugePropertyDialogModel model = new GaugePropertyDialogModel();
         GaugePropertyDialogService gauge = mock(GaugePropertyDialogService.class);
         when(gauge.getGaugePropertyDialogModel(anyString(), anyString(), any(Principal.class)))
            .thenReturn(model);
         AssemblyPropertyService service = serviceWith(
            mock(GaugeVSAssembly.class), model, null, null, mock(ImagePropertyDialogService.class),
            gauge, mock(VSOutputService.class));

         Exception thrown = assertThrows(IllegalArgumentException.class,
            () -> service.set("tok", principal(), "Gauge1",
                              Map.of(bad.getKey(), bad.getValue()), ""));

         assertTrue(thrown.getMessage().startsWith(bad.getKey() + ": must be a positive"),
                    thrown.getMessage());
         verify(gauge, never()).setGaugePropertyDialogModel(
            anyString(), anyString(), any(), anyString(), any(Principal.class), any());
      }
   }

   @Test
   void appliesPositiveHeightAndLeavesPositionOnlyPatchesAlone() throws Exception {
      GaugePropertyDialogModel model = new GaugePropertyDialogModel();
      AssemblyPropertyService service = serviceWith(mock(GaugeVSAssembly.class), model);

      service.set("tok", principal(), "Gauge1", Map.of("height", 1, "left", 0, "top", 0), "");

      assertEquals(1, model.getGaugeGeneralPaneModel().getSizePositionPaneModel().getHeight());
   }

   // ── Slider / Spinner number range (bug #79247) ──────────────────────────────

   private static Object rangeModel(boolean slider, String min, String max, String increment) {
      Object model = slider ? new SliderPropertyDialogModel() : new SpinnerPropertyDialogModel();
      NumericRangePaneModel pane = rangePane(model);
      pane.setMinimum(min);
      pane.setMaximum(max);
      pane.setIncrement(increment);
      return model;
   }

   private static NumericRangePaneModel rangePane(Object model) {
      return model instanceof SliderPropertyDialogModel slider
         ? slider.getSliderGeneralPaneModel().getNumericRangePaneModel()
         : ((SpinnerPropertyDialogModel) model).getSpinnerGeneralPaneModel()
            .getNumericRangePaneModel();
   }

   private static void setRange(boolean slider, Object model, Map<String, Object> patch)
      throws Exception
   {
      AssemblyPropertyService service = slider
         ? serviceWith(mock(SliderVSAssembly.class), model, model, null)
         : serviceWith(mock(SpinnerVSAssembly.class), model, model, null);
      service.set("tok", principal(), slider ? "Slider1" : "Spinner1", patch, "");
   }

   private static void verifyRangeWritten(boolean slider, int times) throws Exception {
      if(slider) {
         verify(lastInput, times(times)).setSliderPropertyDialogModel(
            anyString(), anyString(), any(), any(), any(), any());
      }
      else {
         verify(lastInput, times(times)).setSpinnerPropertyDialogModel(
            anyString(), anyString(), any(), any(), any(), any());
      }
   }

   /** Refused, the message carries every expected fragment, and nothing reached the store. */
   private static void assertRangeRefused(boolean slider, String min, String max, String inc,
                                          Map<String, Object> patch, String... fragments)
      throws Exception
   {
      Object model = rangeModel(slider, min, max, inc);
      Exception e = assertThrows(IllegalArgumentException.class,
                                 () -> setRange(slider, model, patch), patch.toString());

      for(String fragment : fragments) {
         assertTrue(e.getMessage().contains(fragment), fragment + " missing: " + e.getMessage());
      }

      verifyRangeWritten(slider, 0);
   }

   private static void assertRangeAccepted(boolean slider, String min, String max, String inc,
                                           Map<String, Object> patch)
      throws Exception
   {
      setRange(slider, rangeModel(slider, min, max, inc), patch);
      verifyRangeWritten(slider, 1);
   }

   @Test
   void refusesAnInvertedOrEmptyNumberRange() throws Exception {
      for(boolean slider : new boolean[] { true, false }) {
         Map<String, Object> inverted = new LinkedHashMap<>();
         inverted.put("min", 10);
         inverted.put("max", 5);
         assertRangeRefused(slider, "0", "100", "10", inverted, "min", "max", "min=10", "max=5");

         Map<String, Object> equal = new LinkedHashMap<>();
         equal.put("min", 5);
         equal.put("max", 5);
         assertRangeRefused(slider, "0", "100", "10", equal, "min", "max");
      }
   }

   @Test
   void judgesASingleWrittenBoundAgainstTheStoredOne() throws Exception {
      for(boolean slider : new boolean[] { true, false }) {
         assertRangeRefused(slider, "10", "100", "10", Map.of("max", 5), "max", "min=10");
         assertRangeRefused(slider, "0", "100", "10", Map.of("min", 150), "min", "max=100");
      }
   }

   @Test
   void refusesANonPositiveIncrement() throws Exception {
      for(boolean slider : new boolean[] { true, false }) {
         assertRangeRefused(slider, "0", "100", "10", Map.of("increment", 0), "increment");
         assertRangeRefused(slider, "0", "100", "10", Map.of("increment", -1), "increment");
         assertRangeRefused(slider, "0", "100", "10", Map.of("increment", "abc"), "increment");
         assertRangeRefused(slider, "0", "100", "10", Map.of("increment", ""), "increment");
      }
   }

   @Test
   void refusesABoundThatIsNotAFiniteNumber() throws Exception {
      for(boolean slider : new boolean[] { true, false }) {
         for(String bad : new String[] { "abc", "", "NaN", "1e999", "Infinity", "1d", "0x1p3",
                                         "$(X", "1,5" })
         {
            assertRangeRefused(slider, "0", "100", "10", Map.of("min", bad), "min");
            assertRangeRefused(slider, "0", "100", "10", Map.of("max", bad), "max");
         }
      }
   }

   @Test
   void refusesANullBound() throws Exception {
      Map<String, Object> patch = new LinkedHashMap<>();
      patch.put("min", null);
      assertRangeRefused(true, "0", "100", "10", patch, "min");
   }

   @Test
   void acceptsDynamicRangeValues() throws Exception {
      for(boolean slider : new boolean[] { true, false }) {
         assertRangeAccepted(slider, "0", "100", "10", Map.of("min", "$(Lo)"));
         assertRangeAccepted(slider, "0", "100", "10", Map.of("max", "=Lo+10"));
         assertRangeAccepted(slider, "0", "100", "10", Map.of("min", "="));
         assertRangeAccepted(slider, "0", "100", "10", Map.of("increment", "=5"));
         assertRangeAccepted(slider, "0", "100", "10", Map.of("increment", "$(Step)"));
      }
   }

   @Test
   void acceptsAValidRangeAndDecimalSpellings() throws Exception {
      for(boolean slider : new boolean[] { true, false }) {
         Map<String, Object> valid = new LinkedHashMap<>();
         valid.put("min", 0);
         valid.put("max", 100);
         valid.put("increment", 10);
         assertRangeAccepted(slider, "5", "50", "1", valid);
      }

      assertRangeAccepted(true, "0", "100", "10", Map.of("increment", 0.5));
      assertRangeAccepted(false, "0", "100", "10", Map.of("min", "-2.5e1"));
   }

   @Test
   void aRangeAlreadySavedInvalidDoesNotBlockAnUnrelatedWrite() throws Exception {
      for(boolean slider : new boolean[] { true, false }) {
         assertRangeAccepted(slider, "10", "5", "10", Map.of("increment", 20));
         // min=1 is below the stored max of 5, so the written bound is itself fine.
         assertRangeAccepted(slider, "10", "5", "10", Map.of("min", 1));
         // The unwritten bound is dynamic or unreadable, so there is no order to judge.
         assertRangeAccepted(slider, "0", "$(Hi)", "10", Map.of("min", 500));
         assertRangeAccepted(slider, "0", "garbage", "10", Map.of("min", 500));
      }
   }

   @Test
   void namesTheRawPathLeafAndTheCallerAliasSpelling() {
      String sliderMin = "sliderGeneralPaneModel.numericRangePaneModel.minimum";
      Exception e = assertThrows(IllegalArgumentException.class, () -> setRange(
         true, rangeModel(true, "0", "100", "10"), Map.of(sliderMin, "abc")));
      assertTrue(e.getMessage().startsWith("minimum:"), e.getMessage());

      String spinnerInc = "spinnerGeneralPaneModel.numericRangePaneModel.increment";
      e = assertThrows(IllegalArgumentException.class, () -> setRange(
         false, rangeModel(false, "0", "100", "10"), Map.of(spinnerInc, 0)));
      assertTrue(e.getMessage().startsWith("increment:"), e.getMessage());

      Map<String, Object> inverted = new LinkedHashMap<>();
      inverted.put("sliderGeneralPaneModel.numericRangePaneModel.minimum", 10);
      inverted.put("sliderGeneralPaneModel.numericRangePaneModel.maximum", 5);
      e = assertThrows(IllegalArgumentException.class, () -> setRange(
         true, rangeModel(true, "0", "100", "10"), inverted));
      assertTrue(e.getMessage().contains("min") && e.getMessage().contains("max"),
                 e.getMessage());
   }

   // ── Gauge number range (bug #79539) ──────────────────────────────────────────

   private static GaugePropertyDialogModel gaugeRange(String min, String max, String minor,
                                                      String major)
   {
      GaugePropertyDialogModel model = new GaugePropertyDialogModel();
      var pane = model.getGaugeGeneralPaneModel().getNumberRangePaneModel();
      pane.setMin(min);
      pane.setMax(max);
      pane.setMinorIncrement(minor);
      pane.setMajorIncrement(major);
      return model;
   }

   private static Map<String, Object> gaugePatch(Object... keyValues) {
      Map<String, Object> patch = new LinkedHashMap<>();

      for(int i = 0; i < keyValues.length; i += 2) {
         patch.put((String) keyValues[i], keyValues[i + 1]);
      }

      return patch;
   }

   private static void assertGaugeRangeRefused(GaugePropertyDialogModel model,
                                               Map<String, Object> patch, String... fragments)
      throws Exception
   {
      GaugePropertyDialogService gaugeService = mock(GaugePropertyDialogService.class);
      AssemblyPropertyService service =
         serviceWithGauge(mock(GaugeVSAssembly.class), model, gaugeService, null);
      Exception e = assertThrows(IllegalArgumentException.class,
         () -> service.set("tok", principal(), "Gauge1", patch, ""), patch.toString());

      for(String fragment : fragments) {
         assertTrue(e.getMessage().contains(fragment), fragment + " missing: " + e.getMessage());
      }

      verify(gaugeService, never()).setGaugePropertyDialogModel(
         anyString(), anyString(), any(), any(), any(), any());
   }

   private static void assertGaugeRangeAccepted(GaugePropertyDialogModel model,
                                                Map<String, Object> patch)
      throws Exception
   {
      GaugePropertyDialogService gaugeService = mock(GaugePropertyDialogService.class);
      AssemblyPropertyService service =
         serviceWithGauge(mock(GaugeVSAssembly.class), model, gaugeService, null);
      service.set("tok", principal(), "Gauge1", patch, "");
      verify(gaugeService, times(1)).setGaugePropertyDialogModel(
         anyString(), anyString(), any(), any(), any(), any());
   }

   @Test
   void refusesAnInvertedOrEqualGaugeMinMax() throws Exception {
      assertGaugeRangeRefused(gaugeRange("0", "200", "10", "50"),
                              gaugePatch("min", "100", "max", "50"), "min", "max", "min=100",
                              "max=50");
      assertGaugeRangeRefused(gaugeRange("0", "200", "10", "50"),
                              gaugePatch("min", 7, "max", 7), "min=7", "max=7");
   }

   @Test
   void refusesALoneGaugeBoundThatCrossesTheStoredOne() throws Exception {
      assertGaugeRangeRefused(gaugeRange("0", "200", "10", "50"),
                              gaugePatch("min", "250"), "min", "min=250", "max=200");
      assertGaugeRangeRefused(gaugeRange("0", "200", "10", "50"),
                              gaugePatch("max", "-1"), "max", "max=-1", "min=0");
   }

   @Test
   void refusesABlankOrNonNumericGaugeBound() throws Exception {
      assertGaugeRangeRefused(gaugeRange("0", "200", "10", "50"),
                              gaugePatch("min", "abc"), "min:", "abc");
      assertGaugeRangeRefused(gaugeRange("0", "200", "10", "50"),
                              gaugePatch("max", "NaN"), "max:", "NaN");
      assertGaugeRangeRefused(gaugeRange("0", "200", "10", "50"),
                              gaugePatch("max", ""), "max:", "required");
   }

   @Test
   void refusesANonPositiveOrNonNumericGaugeIncrement() throws Exception {
      assertGaugeRangeRefused(gaugeRange("0", "200", "10", "50"),
                              gaugePatch("majorIncrement", "0"), "majorIncrement:");
      assertGaugeRangeRefused(gaugeRange("0", "200", "10", "50"),
                              gaugePatch("majorIncrement", -5), "majorIncrement:", "-5");
      assertGaugeRangeRefused(gaugeRange("0", "200", "10", "50"),
                              gaugePatch("majorIncrement", "abc"), "majorIncrement:", "abc");
      assertGaugeRangeRefused(gaugeRange("0", "200", "10", "50"),
                              gaugePatch("minorIncrement", "0"), "minorIncrement:");
      assertGaugeRangeRefused(gaugeRange("0", "200", "10", "50"),
                              gaugePatch("minorIncrement", "x"), "minorIncrement:", "x");
   }

   @Test
   void refusesAGaugeMajorIncrementNotAboveTheMinor() throws Exception {
      assertGaugeRangeRefused(gaugeRange("0", "200", "10", "50"),
                              gaugePatch("majorIncrement", "5", "minorIncrement", "10"),
                              "majorIncrement", "minorIncrement", "5", "10");
      assertGaugeRangeRefused(gaugeRange("0", "200", "10", "50"),
                              gaugePatch("majorIncrement", "10", "minorIncrement", "10"),
                              "majorIncrement", "minorIncrement");
      assertGaugeRangeRefused(gaugeRange("0", "200", "10", "50"),
                              gaugePatch("majorIncrement", "10"), "majorIncrement", "10");
      assertGaugeRangeRefused(gaugeRange("0", "200", "10", "50"),
                              gaugePatch("majorIncrement", "3"), "majorIncrement", "3");
      assertGaugeRangeRefused(gaugeRange("0", "200", "10", "50"),
                              gaugePatch("minorIncrement", "60"), "minorIncrement", "60",
                              "50");
      assertGaugeRangeRefused(gaugeRange("0", "200", "10", "50"),
                              gaugePatch("minorIncrement", "50"), "minorIncrement");
   }

   @Test
   void exemptsDynamicGaugeRangeValues() throws Exception {
      assertGaugeRangeAccepted(gaugeRange("0", "200", "10", "50"),
                               gaugePatch("min", "$(lo)", "max", "=hi()"));
      assertGaugeRangeAccepted(gaugeRange("0", "200", "10", "50"),
                               gaugePatch("max", "$(hi)"));
      assertGaugeRangeAccepted(gaugeRange("0", "200", "10", "50"),
                               gaugePatch("majorIncrement", "$(step)",
                                          "minorIncrement", "=small()"));
      assertGaugeRangeAccepted(gaugeRange("$(lo)", "=hi", "10", "50"),
                               gaugePatch("min", "500"));
   }

   @Test
   void acceptsAValidGaugeRange() throws Exception {
      assertGaugeRangeAccepted(gaugeRange("0", "200", "10", "50"),
                               gaugePatch("min", 0, "max", 100000000, "minorIncrement", "2.5",
                                          "majorIncrement", "1.0E1"));
      assertGaugeRangeAccepted(gaugeRange("0", "200", "10", "50"),
                               gaugePatch("min", "-20", "max", "20"));
      assertGaugeRangeAccepted(gaugeRange("0", "200", "10", "50"),
                               gaugePatch("majorIncrement", "100"));
   }

   @Test
   void allowsBlankGaugeIncrements() throws Exception {
      assertGaugeRangeAccepted(gaugeRange("0", "200", "10", "50"),
                               gaugePatch("minorIncrement", "", "majorIncrement", ""));
      assertGaugeRangeAccepted(gaugeRange("0", "200", "10", "50"),
                               gaugePatch("majorIncrement", ""));
   }

   @Test
   void skipsTheGaugeOrderCheckAgainstAnUnsetCounterpart() throws Exception {
      assertGaugeRangeAccepted(gaugeRange(null, null, null, null), gaugePatch("min", "5"));
      assertGaugeRangeAccepted(gaugeRange(null, null, null, null), gaugePatch("max", "5"));
      assertGaugeRangeAccepted(gaugeRange(null, null, null, null),
                               gaugePatch("minorIncrement", "5"));
      assertGaugeRangeAccepted(gaugeRange(null, null, null, null),
                               gaugePatch("majorIncrement", "5"));
   }

   @Test
   void unrelatedGaugeWritesAreNeverBlockedByAnInvalidStoredRange() throws Exception {
      assertGaugeRangeAccepted(gaugeRange("100", "50", "0", "-5"),
                               gaugePatch("showValue", false));
   }

   @Test
   void refusesGaugeRangeWrittenThroughRawDottedPaths() throws Exception {
      assertGaugeRangeRefused(gaugeRange("0", "200", "10", "50"),
         gaugePatch("gaugeGeneralPaneModel.numberRangePaneModel.min", "100",
                    "gaugeGeneralPaneModel.numberRangePaneModel.max", "50"), "min", "max");
   }

   // ── #79259 / #79260: cross-field guards (PatchGuards) ──

   private static final String SCALE_PANE = "imageAdvancedPaneModel.imageScalePaneModel.";

   private static TextPropertyDialogModel textEmbedPop(boolean embed, String pop) {
      TextPropertyDialogModel model = textWithPopComponent(pop);
      model.getTextGeneralPaneModel().getTextPaneModel().setUrl(embed);
      model.getTextGeneralPaneModel().setPopComponents(new String[] { "T1", "T2" });
      return model;
   }

   private static void setText(TextPropertyDialogModel model, Map<String, Object> patch)
      throws Exception
   {
      serviceWith(mock(TextVSAssembly.class), model)
         .set("tok", principal(), "Text1", patch, "");
   }

   private static void assertRefusedTextPatch(boolean embed, String pop, Map<String, Object> patch) {
      TextPropertyDialogModel model = textEmbedPop(embed, pop);
      AssemblyPropertyService service = serviceWith(mock(TextVSAssembly.class), model);

      Exception e = assertThrows(IllegalArgumentException.class,
         () -> service.set("tok", principal(), "Text1", patch, ""));

      assertTrue(e.getMessage().contains("embedUrl"), e.getMessage());
      assertTrue(e.getMessage().contains("popComponent"), e.getMessage());
      assertNothingWrittenBy(lastDialogServices);
   }

   @Test
   void refusesAPopComponentOnATextThatEmbedsAUrl() {
      assertRefusedTextPatch(true, "", Map.of("popComponent", "T1"));
   }

   @Test
   void refusesEmbeddingAUrlOnATextWithAPopComponent() {
      assertRefusedTextPatch(false, "T1", Map.of("embedUrl", true));
   }

   @Test
   void refusesEmbedAndPopComponentSetInOnePatch() {
      Map<String, Object> patch = new LinkedHashMap<>();
      patch.put("embedUrl", true);
      patch.put("popComponent", "T1");
      assertRefusedTextPatch(false, "", patch);
   }

   @Test
   void acceptsATextPatchThatClearsOneSideOfEmbedAndPop() throws Exception {
      TextPropertyDialogModel model = textEmbedPop(false, "T1");
      setText(model, Map.of("embedUrl", true, "popComponent", ""));
      assertTrue(model.getTextGeneralPaneModel().getTextPaneModel().isUrl());
      assertEquals("", model.getTextGeneralPaneModel().getPopComponent());

      model = textEmbedPop(true, "");
      setText(model, Map.of("embedUrl", false, "popComponent", "T1"));
      assertFalse(model.getTextGeneralPaneModel().getTextPaneModel().isUrl());
      assertEquals("T1", model.getTextGeneralPaneModel().getPopComponent());
   }

   @Test
   void acceptsEachOfEmbedAndPopComponentAlone() throws Exception {
      TextPropertyDialogModel model = textEmbedPop(false, "");
      setText(model, Map.of("embedUrl", true));
      assertTrue(model.getTextGeneralPaneModel().getTextPaneModel().isUrl());

      model = textEmbedPop(false, "");
      setText(model, Map.of("popComponent", "T1"));
      assertEquals("T1", model.getTextGeneralPaneModel().getPopComponent());
   }

   /** Content that already has both (the native dialog stores it) stays editable. */
   @Test
   void keepsALegacyBothSetTextEditable() throws Exception {
      TextPropertyDialogModel model = textEmbedPop(true, "T1");
      assertDoesNotThrow(() -> setText(model, Map.of("embedUrl", true)));
      assertDoesNotThrow(() -> setText(model, Map.of("popComponent", "T1")));
      assertDoesNotThrow(() -> setText(model, Map.of("text", "x")));
      // a patch that changes one side while both stay set is refused; clearing a side applies
      assertRefusedTextPatch(true, "T1", Map.of("popComponent", "T2"));
      setText(model, Map.of("popComponent", ""));
      assertEquals("", model.getTextGeneralPaneModel().getPopComponent());
   }

   private static ImagePropertyDialogModel imageWithScale(boolean scale, boolean tile,
                                                          boolean aspect, int top)
   {
      return ImagePropertyDialogModel.builder()
         .imageAdvancedPaneModel(ImageAdvancedPaneModel.builder()
            .popComponents(new String[] { "T1" })
            .imageScalePaneModel(ImageScalePaneModel.builder()
               .scaleImageChecked(scale).tile(tile).maintainAspectRatio(aspect).top(top)
               .build())
            .build())
         .build();
   }

   private static ImageScalePaneModel setImageScale(ImagePropertyDialogModel model,
                                                    Map<String, Object> patch)
      throws Exception
   {
      ImagePropertyDialogService imageService = mock(ImagePropertyDialogService.class);
      serviceWithImage(mock(ImageVSAssembly.class), model, imageService)
         .set("tok", principal(), "Image1", patch, "");
      ArgumentCaptor<ImagePropertyDialogModel> captor =
         ArgumentCaptor.forClass(ImagePropertyDialogModel.class);
      verify(imageService).setImagePropertyDialogModel(anyString(), anyString(), captor.capture(),
                                                       any(), any(), any());
      return captor.getValue().imageAdvancedPaneModel().imageScalePaneModel();
   }

   private static String refuseImageScale(ImagePropertyDialogModel model,
                                          Map<String, Object> patch)
   {
      ImagePropertyDialogService imageService = mock(ImagePropertyDialogService.class);
      AssemblyPropertyService service =
         serviceWithImage(mock(ImageVSAssembly.class), model, imageService);
      Exception e = assertThrows(IllegalArgumentException.class,
         () -> service.set("tok", principal(), "Image1", patch, ""));
      assertNothingWrittenBy(List.of(imageService));
      return e.getMessage();
   }

   @Test
   void refusesScaleAndTileTogetherOnAnImage() {
      String msg = refuseImageScale(imageWithScale(true, false, true, 0),
                                    Map.of(SCALE_PANE + "tile", true));
      assertTrue(msg.contains("scaleImageChecked") && msg.contains("tile"), msg);

      msg = refuseImageScale(imageWithScale(false, true, false, 0),
                             Map.of(SCALE_PANE + "scaleImageChecked", true));
      assertTrue(msg.contains("scaleImageChecked") && msg.contains("tile"), msg);

      Map<String, Object> both = new LinkedHashMap<>();
      both.put(SCALE_PANE + "scaleImageChecked", true);
      both.put(SCALE_PANE + "tile", true);
      refuseImageScale(imageWithScale(false, false, false, 0), both);
   }

   @Test
   void acceptsSwitchingBetweenScaleAndTileInOnePatch() throws Exception {
      Map<String, Object> patch = new LinkedHashMap<>();
      patch.put(SCALE_PANE + "tile", true);
      patch.put(SCALE_PANE + "scaleImageChecked", false);
      ImageScalePaneModel pane = setImageScale(imageWithScale(true, false, true, 0), patch);
      assertTrue(pane.tile());
      assertFalse(pane.scaleImageChecked());

      pane = setImageScale(imageWithScale(true, true, false, 0),
                           Map.of(SCALE_PANE + "tile", false));
      assertTrue(pane.scaleImageChecked());
      assertFalse(pane.tile());
   }

   @Test
   void keepsALegacyScaleAndTileImageEditable() throws Exception {
      Map<String, Object> patch = new LinkedHashMap<>();
      patch.put(SCALE_PANE + "scaleImageChecked", true);
      patch.put(SCALE_PANE + "maintainAspectRatio", false);
      patch.put(SCALE_PANE + "top", 10);
      ImageScalePaneModel pane = setImageScale(imageWithScale(true, true, true, 0), patch);
      assertEquals(10, pane.top());
   }

   /** A write that only echoes the stored scale and tile values of a legacy image is not refused. */
   @Test
   void acceptsABareEchoOfALegacyScaleAndTileImage() throws Exception {
      Map<String, Object> echo = new LinkedHashMap<>();
      echo.put(SCALE_PANE + "scaleImageChecked", true);
      echo.put(SCALE_PANE + "tile", true);
      ImageScalePaneModel pane = setImageScale(imageWithScale(true, true, true, 0), echo);
      assertTrue(pane.scaleImageChecked() && pane.tile());

      pane = setImageScale(imageWithScale(true, true, true, 0), Map.of(SCALE_PANE + "tile", true));
      assertTrue(pane.scaleImageChecked() && pane.tile());
   }

   @Test
   void refusesAnInsetWhileAspectRatioIsOnOrScaleIsOff() {
      String msg = refuseImageScale(imageWithScale(true, false, true, 0),
                                    Map.of(SCALE_PANE + "top", 10));
      assertTrue(msg.contains("top") && msg.contains("maintainAspectRatio") &&
                 msg.contains("scaleImageChecked"), msg);

      for(String leaf : new String[] { "left", "bottom", "right" }) {
         msg = refuseImageScale(imageWithScale(false, false, false, 0),
                                Map.of(SCALE_PANE + leaf, 10));
         assertTrue(msg.contains(leaf), msg);
      }
   }

   /** The message reports the post-patch scale and aspect values, not the stored ones. */
   @Test
   void reportsThePostPatchScaleStateInTheInsetMessage() {
      Map<String, Object> patch = new LinkedHashMap<>();
      patch.put(SCALE_PANE + "scaleImageChecked", false);
      patch.put(SCALE_PANE + "top", 10);
      String msg = refuseImageScale(imageWithScale(true, false, false, 0), patch);
      assertTrue(msg.contains("scaleImageChecked would be false"), msg);
   }

   @Test
   void acceptsInsetsTogetherWithScaleOnAndAspectOffInEitherKeyOrder() throws Exception {
      Map<String, Object> patch = new LinkedHashMap<>();
      patch.put(SCALE_PANE + "top", 10);
      patch.put(SCALE_PANE + "maintainAspectRatio", false);
      patch.put(SCALE_PANE + "scaleImageChecked", true);
      assertEquals(10, setImageScale(imageWithScale(false, false, false, 0), patch).top());

      patch = new LinkedHashMap<>();
      patch.put(SCALE_PANE + "scaleImageChecked", true);
      patch.put(SCALE_PANE + "maintainAspectRatio", false);
      patch.put(SCALE_PANE + "top", 10);
      assertEquals(10, setImageScale(imageWithScale(false, false, true, 0), patch).top());
   }

   @Test
   void acceptsAnUnchangedInsetEcho() throws Exception {
      ImageScalePaneModel pane = setImageScale(imageWithScale(true, false, true, 0),
                                               Map.of(SCALE_PANE + "top", 0));
      assertEquals(0, pane.top());
   }

   @Test
   void refusesANegativeInsetOnEachLeaf() {
      for(String leaf : new String[] { "top", "left", "bottom", "right" }) {
         String msg = refuseImageScale(imageWithScale(true, false, false, 0),
                                       Map.of(SCALE_PANE + leaf, -5));
         assertTrue(msg.contains(SCALE_PANE + leaf) && msg.contains("negative"), msg);
      }

      // a bad value is reported first, even when the mode is also wrong
      String msg = refuseImageScale(imageWithScale(false, false, false, 0),
                                    Map.of(SCALE_PANE + "top", -5));
      assertTrue(msg.contains("negative"), msg);
   }

   @Test
   void leavesAStoredNegativeInsetAloneWhenTheWriteEchoesIt() throws Exception {
      ImageScalePaneModel pane = setImageScale(
         imageWithScale(true, false, false, -3),
         Map.of(SCALE_PANE + "scaleImageChecked", true, SCALE_PANE + "top", -3));
      assertEquals(-3, pane.top());
   }

   @Test
   void doesNotEvaluateTheScaleRulesForAnUnrelatedImageWrite() {
      assertDoesNotThrow(() -> setImageScale(imageWithScale(true, true, true, -3),
                                             Map.of("imageAdvancedPaneModel.popComponent", "")));
   }

   private static GroupContainerPropertyDialogModel groupWithScale(boolean scale, boolean tile,
                                                                   boolean aspect, int top)
   {
      // populated the way GroupContainerPropertyDialogService.read does; a bare model silently
      // drops writes to imageScalePane.*
      GroupContainerPropertyDialogModel model = new GroupContainerPropertyDialogModel();
      model.setImageScalePane(ImageScalePaneModel.builder()
         .scaleImageChecked(scale).tile(tile).maintainAspectRatio(aspect).top(top).build());
      return model;
   }

   private static void assertGroupRefused(GroupContainerPropertyDialogModel model,
                                          Map<String, Object> patch, String... names)
   {
      AssemblyPropertyService service = serviceWith(mock(GroupContainerVSAssembly.class), model);
      Exception e = assertThrows(IllegalArgumentException.class,
         () -> service.set("tok", principal(), "Group1", patch, ""));

      for(String name : names) {
         assertTrue(e.getMessage().contains(name), e.getMessage());
      }

      assertNothingWrittenBy(lastDialogServices);
   }

   @Test
   void appliesTheScaleRulesToAGroupContainerImageScalePane() throws Exception {
      assertGroupRefused(groupWithScale(true, false, false, 0),
                         Map.of("imageScalePane.tile", true), "scaleImageChecked", "tile");
      assertGroupRefused(groupWithScale(true, false, true, 0),
                         Map.of("imageScalePane.top", 10), "top", "maintainAspectRatio");
      assertGroupRefused(groupWithScale(true, false, false, 0),
                         Map.of("imageScalePane.top", -5), "top", "negative");

      GroupContainerPropertyDialogModel model = groupWithScale(false, false, true, 0);
      Map<String, Object> patch = new LinkedHashMap<>();
      patch.put("imageScalePane.scaleImageChecked", true);
      patch.put("imageScalePane.maintainAspectRatio", false);
      patch.put("imageScalePane.top", 10);
      serviceWith(mock(GroupContainerVSAssembly.class), model)
         .set("tok", principal(), "Group1", patch, "");
      assertEquals(10, model.getImageScalePane().top());
   }

   // ── #79615: a locked Image/Line/Oval/Rectangle's geometry is read-only (LockedGeometryGuard) ──

   private static final String SHAPE_POSITION = "shapeGeneralPaneModel.sizePositionPaneModel.";
   private static final String IMAGE_POSITION = "imageGeneralPaneModel.sizePositionPaneModel.";
   private static final String[] LOCKABLE_TYPES = { "image", "rectangle", "oval", "line" };

   private static SizePositionPaneModel position(boolean locked) {
      SizePositionPaneModel pane = new SizePositionPaneModel();
      pane.setTop(40);
      pane.setLeft(50);
      pane.setWidth(200);
      pane.setHeight(150);
      pane.setLocked(locked);
      return pane;
   }

   /** One lockable assembly: its service, the pane the model holds and the native setter mock. */
   private record Lockable(String name, String prefix, AssemblyPropertyService service,
                           SizePositionPaneModel pane, Object writer)
   {
   }

   private static Lockable lockable(String type, boolean locked) throws Exception {
      SizePositionPaneModel pane = position(locked);

      switch(type) {
      case "image": {
         ImagePropertyDialogService imageService = mock(ImagePropertyDialogService.class);
         ImagePropertyDialogModel model = ImagePropertyDialogModel.builder()
            .imageGeneralPaneModel(
               ImageGeneralPaneModel.builder().sizePositionPaneModel(pane).build())
            .build();
         AssemblyPropertyService service =
            serviceWithImage(mock(ImageVSAssembly.class), model, imageService);
         return new Lockable("Image1", IMAGE_POSITION, service, pane, imageService);
      }
      case "rectangle": {
         RectanglePropertyDialogModel model = new RectanglePropertyDialogModel();
         model.getShapeGeneralPaneModel().setSizePositionPaneModel(pane);
         AssemblyPropertyService service = serviceWith(mock(RectangleVSAssembly.class), model);
         when(lastRectangle.getRectanglePropertyDialogModel(anyString(), anyString(),
                                                            any(Principal.class)))
            .thenReturn(model);
         return new Lockable("Rectangle1", SHAPE_POSITION, service, pane, lastRectangle);
      }
      case "oval": {
         OvalPropertyDialogModel model = new OvalPropertyDialogModel();
         model.getShapeGeneralPaneModel().setSizePositionPaneModel(pane);
         AssemblyPropertyService service = serviceWith(mock(OvalVSAssembly.class), model);
         when(lastOval.getOvalPropertyDialogModel(anyString(), anyString(),
                                                  any(Principal.class)))
            .thenReturn(model);
         return new Lockable("Oval1", SHAPE_POSITION, service, pane, lastOval);
      }
      default: {
         LinePropertyDialogModel model = new LinePropertyDialogModel();
         model.getShapeGeneralPaneModel().setSizePositionPaneModel(pane);
         AssemblyPropertyService service = serviceWith(mock(LineVSAssembly.class), model);
         when(lastLine.getLinePropertyDialogModel(anyString(), anyString(),
                                                  any(Principal.class)))
            .thenReturn(model);
         return new Lockable("Line1", SHAPE_POSITION, service, pane, lastLine);
      }
      }
   }

   private static void setOn(Lockable target, Map<String, Object> patch) throws Exception {
      target.service().set("tok", principal(), target.name(), patch, "");
   }

   private static void assertGeometryRefused(Lockable target, String key, Object value) {
      Exception e = assertThrows(IllegalArgumentException.class,
         () -> setOn(target, Map.of(key, value)), key);

      assertTrue(e.getMessage().contains("'" + key + "'"), e.getMessage());
      assertTrue(e.getMessage().contains(target.name()), e.getMessage());
      assertTrue(e.getMessage().contains("locked"), e.getMessage());
      assertTrue(e.getMessage().contains("set_lock"), e.getMessage());
      // the model is the read copy PropertyPath mutates in place; what is stored is what the
      // native setter received, and it received nothing
      assertNothingWrittenBy(List.of(target.writer()));
   }

   @Test
   void refusesGeometryOnALockedAssemblyByAliasAndByRawPath() throws Exception {
      for(String type : LOCKABLE_TYPES) {
         for(String leaf : new String[] { "top", "left", "width", "height" }) {
            assertGeometryRefused(lockable(type, true), leaf, 300);
            Lockable target = lockable(type, true);
            assertGeometryRefused(target, target.prefix() + leaf, 300);
         }
      }
   }

   @Test
   void refusesGeometryOnALockedAssemblyUnderACapitalizedLeaf() throws Exception {
      for(String type : LOCKABLE_TYPES) {
         Lockable target = lockable(type, true);
         assertGeometryRefused(target, target.prefix() + "Top", 300);
      }
   }

   @Test
   void refusesAMixedPatchWithoutWritingItsOtherFields() throws Exception {
      Lockable target = lockable("line", true);
      Map<String, Object> patch = new LinkedHashMap<>();
      patch.put("shadow", true);
      patch.put("top", 99);

      assertThrows(IllegalArgumentException.class, () -> setOn(target, patch));
      assertNothingWrittenBy(List.of(target.writer()));
   }

   @Test
   void appliesGeometryOnAnUnlockedAssembly() throws Exception {
      for(String type : LOCKABLE_TYPES) {
         Lockable target = lockable(type, false);
         setOn(target, Map.of("top", 300, "width", 80));
         assertEquals(300, target.pane().getTop(), type);
         assertEquals(80, target.pane().getWidth(), type);
      }
   }

   @Test
   void appliesANonGeometryPatchOnALockedAssembly() throws Exception {
      Lockable target = lockable("line", true);
      assertDoesNotThrow(() -> setOn(target, Map.of("shadow", true)));
      assertEquals(40, target.pane().getTop());
   }

   /** An echo of the current geometry is not a change, with or without a real change beside it. */
   @Test
   void doesNotRefuseAnUnchangedEchoOfALockedGeometry() throws Exception {
      for(String type : LOCKABLE_TYPES) {
         Lockable target = lockable(type, true);
         assertDoesNotThrow(
            () -> setOn(target, Map.of("top", 40, "left", 50, "width", 200, "height", 150)),
            type);
         assertDoesNotThrow(() -> setOn(target, Map.of("top", "40", "left", 50.0)), type);
      }

      Lockable line = lockable("line", true);
      assertDoesNotThrow(() -> setOn(line, Map.of("top", 40, "shadow", true)));
   }

   @Test
   void keepsLockedReadOnlyThroughThePropertyPath() throws Exception {
      Lockable target = lockable("rectangle", true);
      Exception e = assertThrows(IllegalArgumentException.class,
         () -> setOn(target, Map.of("locked", false)));
      assertTrue(e.getMessage().contains("read-only"), e.getMessage());
      assertTrue(target.pane().isLocked());
   }
}
