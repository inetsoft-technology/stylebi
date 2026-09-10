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
import inetsoft.uql.asset.DefaultVariableAssembly;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.viewsheet.*;
import inetsoft.web.composer.model.vs.CalendarPropertyDialogModel;
import inetsoft.web.composer.model.vs.CheckboxPropertyDialogModel;
import inetsoft.web.composer.model.vs.GaugePropertyDialogModel;
import inetsoft.web.composer.model.vs.RadioButtonPropertyDialogModel;
import inetsoft.web.composer.model.vs.SelectionListPropertyDialogModel;
import inetsoft.web.composer.model.vs.TextInputPropertyDialogModel;
import inetsoft.web.composer.vs.dialog.*;
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
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
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

   // ── showType alias/domain (bug #76542) ───────────────────────────────────
   //
   // selectionGeneralPaneModel.showType and calendarAdvancedPaneModel.showType share the short
   // alias "showType" but have different, overlapping int domains (Selection: list=0/dropdown=1;
   // Calendar: calendar=1/dropdown=2) -- a leaf-name-only canonicalization would silently misapply
   // "dropdown" on whichever collides. canonicalShowType is keyed by the resolved full path
   // instead, so these tests exercise both paths explicitly.

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

   private static AssemblyPropertyService serviceWithSelectionList(
      VSAssembly assembly, SelectionListPropertyDialogModel model)
   {
      return serviceWith(assembly, model, null, null);
   }

   private static AssemblyPropertyService serviceWithCalendar(
      VSAssembly assembly, CalendarPropertyDialogModel model)
   {
      return serviceWith(assembly, model, null, null);
   }

   private static AssemblyPropertyService serviceWith(
      VSAssembly assembly, Object model, Object inputModel,
      Worksheet baseWorksheet)
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
      }
      catch(Exception e) {
         throw new IllegalStateException(e);
      }

      return new AssemblyPropertyService(
         sessions, gauge, mock(ImagePropertyDialogService.class),
         mock(TextPropertyDialogService.class),
         mock(ChartPropertyDialogService.class), mock(TableViewPropertyDialogService.class),
         mock(CrosstabPropertyDialogService.class),
         selectionList,
         mock(SelectionTreePropertyDialogService.class),
         inputService,
         mock(RangeSliderPropertyDialogService.class),
         calendar, mock(TabPropertyDialogService.class),
         mock(CalcTablePropertyDialogService.class),
         mock(GroupContainerPropertyDialogService.class),
         mock(LinePropertyDialogService.class), mock(OvalPropertyDialogService.class),
         mock(RectanglePropertyDialogService.class),
         mock(SelectionContainerPropertyDialogService.class),
         mock(SubmitPropertyDialogService.class));
   }

   private static Principal principal() {
      return () -> "admin";
   }
}
