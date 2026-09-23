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
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.EmbeddedTableAssembly;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.uql.viewsheet.TimeInfo;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.CalendarVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.ImageVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.SelectionTreeVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.SelectionVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.web.adhoc.model.property.TargetInfo;
import inetsoft.web.composer.model.TreeNodeModel;
import inetsoft.web.composer.model.vs.ImagePreviewPaneModel;
import inetsoft.web.composer.model.vs.RangePaneModel;
import inetsoft.web.composer.model.vs.TableStylePaneModel;
import inetsoft.web.composer.model.vs.TipCustomizeDialogModel;
import inetsoft.web.composer.vs.dialog.*;
import inetsoft.web.viewsheet.service.VSInputService;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.Principal;
import java.util.*;

/**
 * Reads and writes assembly properties through the Composer's own property-dialog services.
 *
 * <p>Going through the dialog services rather than writing {@code VSAssemblyInfo} directly
 * keeps their validation and normalization, which is the whole reason the Phase 0 gate asked
 * whether they could be driven headlessly.
 *
 * <p><b>Dispatch names both methods explicitly per type</b>, because the convention they
 * appear to follow does not actually hold. The signatures are uniform — getters take
 * {@code (runtimeId, objectId, principal)}, setters take
 * {@code (runtimeId, objectId, model, linkUri, principal, dispatcher)} — but the names are not:
 *
 * <pre>
 *   gauge          getGaugePropertyDialogModel      setGaugePropertyDialogModel
 *   chart          getChartPropertyDialogModel      setChartPropertyModel        (no "Dialog")
 *   table          getTableViewPropertyDialogModel  setTablePropertyModel        (View / no View)
 *   selectionlist  getSelectionListPropertyModel    setSelectionListPropertyModel(no "Dialog")
 * </pre>
 *
 * <p>Deriving the names from the assembly type worked for gauge and text and would have failed
 * on every type added after them — silently for the getter, since a missing method would only
 * surface on the first live call. So each binding states both names, and
 * {@code AssemblyPropertyServiceTest} resolves every one of them reflectively, which turns a
 * composer rename into a build failure.
 *
 * <p>A patch is validated <b>whole</b> before anything is applied, so a typo in the fourth key
 * does not leave the first three written.
 */
@Service
public class AssemblyPropertyService {
   /**
    * One assembly type's dialog service and the two method names it actually uses.
    *
    * <p>{@code extraGetterArgs} covers the getters that take more than
    * {@code (runtimeId, objectId, principal)} — calc table's wants a scroll offset between
    * {@code objectId} and {@code principal}. Declared per binding rather than guessed, since
    * inserting an argument on a hunch is how a reflective call silently targets the wrong
    * overload.
    */
   record Binding(Object service, String getter, String setter, Object... extraGetterArgs) {
      Binding(Object service, String getter, String setter) {
         this(service, getter, setter, new Object[0]);
      }

      int getterArity() {
         return 3 + extraGetterArgs.length;
      }
   }

   @Autowired
   public AssemblyPropertyService(ViewsheetSessionService sessions,
                                  GaugePropertyDialogService gaugeService,
                                  ImagePropertyDialogService imageService,
                                  TextPropertyDialogService textService,
                                  ChartPropertyDialogService chartService,
                                  TableViewPropertyDialogService tableService,
                                  CrosstabPropertyDialogService crosstabService,
                                  SelectionListPropertyDialogService selectionListService,
                                  SelectionTreePropertyDialogService selectionTreeService,
                                  VSInputService inputService,
                                  RangeSliderPropertyDialogService rangeSliderService,
                                  CalendarPropertyDialogService calendarService,
                                  TabPropertyDialogService tabService,
                                  CalcTablePropertyDialogService calcTableService,
                                  GroupContainerPropertyDialogService groupContainerService,
                                  LinePropertyDialogService lineService,
                                  OvalPropertyDialogService ovalService,
                                  RectanglePropertyDialogService rectangleService,
                                  SelectionContainerPropertyDialogService containerService,
                                  SubmitPropertyDialogService submitService)
   {
      this.sessions = sessions;
      Map<String, Binding> map = new LinkedHashMap<>();
      map.put("gauge", new Binding(gaugeService, "getGaugePropertyDialogModel",
                                   "setGaugePropertyDialogModel"));
      // Immutables model — reachable since PropertyPath learned withX rebuilding.
      map.put("image", new Binding(imageService, "getImagePropertyDialogModel",
                                   "setImagePropertyDialogModel"));
      map.put("text", new Binding(textService, "getTextPropertyDialogModel",
                                  "setTextPropertyDialogModel"));
      map.put("chart", new Binding(chartService, "getChartPropertyDialogModel",
                                   "setChartPropertyModel"));
      map.put("table", new Binding(tableService, "getTableViewPropertyDialogModel",
                                   "setTablePropertyModel"));
      map.put("crosstab", new Binding(crosstabService, "getCrosstabPropertyDialogModel",
                                      "setCrosstabPropertyModel"));
      map.put("selectionlist", new Binding(selectionListService, "getSelectionListPropertyModel",
                                           "setSelectionListPropertyModel"));
      map.put("selectiontree", new Binding(selectionTreeService, "getSelectionTreePropertyModel",
                                           "setSelectionTreePropertyModel"));
      // The six input assemblies all live on one shared service. Note checkbox:
      // the getter capitalizes the B and the setter does not.
      map.put("checkbox", new Binding(inputService, "getCheckBoxPropertyModel",
                                      "setCheckboxPropertyModel"));
      map.put("combobox", new Binding(inputService, "getComboboxPropertyDialogModel",
                                      "setComboboxPropertyDialogModel"));
      map.put("radiobutton", new Binding(inputService, "getRadioButtonPropertyModel",
                                         "setRadioButtonPropertyModel"));
      map.put("slider", new Binding(inputService, "getSliderPropertyDialogModel",
                                    "setSliderPropertyDialogModel"));
      map.put("spinner", new Binding(inputService, "getSpinnerPropertyDialogModel",
                                     "setSpinnerPropertyDialogModel"));
      map.put("textinput", new Binding(inputService, "getTextInputPropertyDialogModel",
                                       "setTextInputPropertyDialogModel"));
      // The assembly class is TimeSliderVSAssembly; the dialog calls it a range slider.
      map.put("timeslider", new Binding(rangeSliderService, "getRangeSliderPropertyModel",
                                        "setRangeSliderPropertyModel"));
      map.put("calendar", new Binding(calendarService, "getCalendarPropertyModel",
                                      "setCalendarPropertyModel"));
      map.put("tab", new Binding(tabService, "getTabPropertyDialogModel",
                                 "setTabPropertyDialogModel"));
      // Calc table's getter wants a scroll offset that only the browser has a real value for;
      // 0 is the top of the sheet, which is what a headless read should see.
      map.put("calctable", new Binding(calcTableService, "getCalcTablePropertyDialogModel",
                                      "setCalcTablePropertyModel", 0d));
      map.put("groupcontainer",
              new Binding(groupContainerService, "getGroupContainerPropertyDialogModel",
                          "setGroupContainerPropertyDialogModel"));
      map.put("line", new Binding(lineService, "getLinePropertyDialogModel",
                                  "setLinePropertyDialogModel"));
      map.put("oval", new Binding(ovalService, "getOvalPropertyDialogModel",
                                  "setOvalPropertyDialogModel"));
      map.put("rectangle", new Binding(rectangleService, "getRectanglePropertyDialogModel",
                                       "setRectanglePropertyDialogModel"));
      map.put("selectioncontainer",
              new Binding(containerService, "getSelectionContainerPropertyModel",
                          "setSelectionContainerPropertyModel"));
      map.put("submit", new Binding(submitService, "getSubmitPropertyDialogModel",
                                    "setSubmitPropertyDialogModel"));
      this.bindings = Collections.unmodifiableMap(map);
   }

   /** The alias vocabulary for an assembly's type, with its current values. */
   public Map<String, Object> list(String sessionToken, Principal user, String assemblyName)
      throws Exception
   {
      RuntimeViewsheet rvs = sessions.resolve(sessionToken, user);
      String type = typeOf(rvs, assemblyName);
      PropertyAliases.TypeAliases entry = PropertyAliases.forType(type);
      Object model = readModel(rvs, type, assemblyName, user);
      List<Map<String, Object>> properties = new ArrayList<>();

      for(Map.Entry<String, String> alias : entry.aliases().entrySet()) {
         Map<String, Object> property = new LinkedHashMap<>();
         property.put("name", alias.getKey());
         property.put("path", alias.getValue());
         property.put("type",
                      PropertyPath.typeOf(entry.modelClass(), alias.getValue()).getSimpleName());
         property.put("value", PropertyPath.get(model, alias.getValue()));
         // The Composer UI's own caption for this alias, when its own name has none (bug #76809,
         // VTB-017) -- e.g. "primary" -> "Visible in External Viewsheets". Null, like every other
         // alias, when PropertyAliases has no better name to offer than the alias itself.
         property.put("label", PropertyAliases.labelFor(alias.getKey()));
         properties.add(property);
      }

      Map<String, Object> out = new LinkedHashMap<>();
      out.put("assembly", assemblyName);
      out.put("assemblyType", type);
      out.put("properties", properties);
      return out;
   }

   /** Current values, by alias. {@code raw} returns the whole dialog model instead. */
   public Object get(String sessionToken, Principal user, String assemblyName, boolean raw)
      throws Exception
   {
      RuntimeViewsheet rvs = sessions.resolve(sessionToken, user);
      String type = typeOf(rvs, assemblyName);
      Object model = readModel(rvs, type, assemblyName, user);

      if(raw) {
         return model;
      }

      PropertyAliases.TypeAliases entry = PropertyAliases.forType(type);
      Map<String, Object> values = new LinkedHashMap<>();

      for(Map.Entry<String, String> alias : entry.aliases().entrySet()) {
         values.put(alias.getKey(), PropertyPath.get(model, alias.getValue()));
      }

      return values;
   }

   /** Applies a patch of aliases and/or raw paths. One {@code mutate}, so one checkpoint. */
   public void set(String sessionToken, Principal user, String assemblyName,
                   Map<String, Object> patch, String linkUri) throws Exception
   {
      if(patch == null || patch.isEmpty()) {
         throw new IllegalArgumentException(
            "set_assembly_properties needs at least one property to set.");
      }

      sessions.mutate(sessionToken, user, (rvs, runtimeId, dispatcher) -> {
         String type = typeOf(rvs, assemblyName);
         Object model = readModel(rvs, type, assemblyName, user);

         // Resolved whole before anything is written: a typo in the fourth key must not
         // leave the first three applied, which would be a partial edit the caller has no
         // way to detect from the error alone.
         Map<String, String> resolved = new LinkedHashMap<>();

         for(String key : patch.keySet()) {
            // resolveForWrite, not resolve: its refusals are keyed by type and currently fire
            // only for the sheet, so this is a no-op for every assembly today. Going through the
            // write path anyway means a refusal added for an assembly type -- a script pane on
            // some future assembly, say -- applies here without anyone remembering to route it.
            resolved.put(key, PropertyAliases.resolveForWrite(type, key));
         }

         // PropertyPath.set returns the root the caller must keep using: if a future alias
         // ever targets a direct top-level field of an Immutables dialog model (no nested pane
         // to absorb the rebuild — see PropertyPath's own note), the wither produces a new
         // instance and the original reference silently stops reflecting the write.
         for(Map.Entry<String, String> entry : resolved.entrySet()) {
            Object value = canonicalIntEnum(type, entry.getValue(), patch.get(entry.getKey()));

            if(entry.getValue().endsWith(".tableStylePaneModel.tableStyle")) {
               requireKnownTableStyle(model, entry.getValue(), value);
            }

            if(entry.getValue().endsWith(".imagePreviewPaneModel.selectedImage")) {
               value = requireKnownSelectedImage(model, entry.getValue(), value);
            }

            model = PropertyPath.set(model, entry.getValue(), value);
         }

         if(PropertyAliases.derivesVariableFlagFromTable(type)) {
            model = normalizeVariableTableBinding(model);
         }

         if(type.equals("gauge") && resolved.values().stream()
            .anyMatch(path -> path.startsWith("gaugeAdvancedPaneModel.rangePaneModel")))
         {
            requireNoInteriorGapInGaugeRangeValues(model);
         }

         if(PropertyAliases.derivesVariableFlagFromTable(type) &&
            resolved.containsValue("dataInputPaneModel.variable"))
         {
            requireVariableFlagAchievable(type, model, rvs.getViewsheet());
         }

         if(PropertyAliases.derivesVariableFlagFromTable(type) &&
            (resolved.containsValue("dataInputPaneModel.columnValue") ||
             resolved.containsValue("dataInputPaneModel.rowValue")))
         {
            requireRowColumnValueValid(type, model, rvs.getViewsheet());
         }

         model = impliedSibling(model, resolved.values(), "tipView", "tipOption", true);
         model = impliedSibling(model, resolved.values(), "tipPaneModel.alpha",
                                 "tipPaneModel.tipOption", true);
         model = impliedSibling(model, resolved.values(), "customTip", "customRB",
                                 TipCustomizeDialogModel.TipFormat.CUSTOM);

         if(PropertyAliases.isListInputType(type)) {
            model = deriveEmbeddedFromStaticList(model, resolved.values());
         }

         if("chart".equals(type)) {
            markTargetsUnchanged(model);
         }

         writeModel(runtimeId, type, assemblyName, model, linkUri, user, dispatcher);
      });
   }

   /**
    * Clears {@code changed} on the chart's existing target lines before the model goes back.
    *
    * <p>Nothing in this class writes a target -- {@code chartTargets} is a {@code TargetInfo[]}
    * that {@code PropertyPath} cannot build, and {@code PropertyAliases} refuses the whole pane
    * (use {@code add_chart_target_line}). But every chart patch, however unrelated -- a title, a
    * tooltip, {@code showValues} -- reads the whole dialog model and writes it back, and
    * {@code ChartPropertyService.updateAllTargets} re-derives every target whose {@code changed}
    * flag is set. {@code getTargetInfo} never clears it and its default is {@code true}, so
    * every read-modify-write round trip re-derives all of them, and that round trip is lossy: a
    * statistics target's several labels are joined with commas on read and come back through
    * {@code updateStatLabel} as one escaped label, and a band's default fill is re-applied as an
    * explicit user colour. {@code updateAllTargets} skips an unchanged entry outright, so
    * clearing the flag is exactly a no-op for the write the caller asked for.
    *
    * <p>In place: the pane is mutable and the array is its own, so there is no rebuilt model to
    * hand back.
    */
   private static void markTargetsUnchanged(Object model) {
      Object targets = PropertyPath.get(
         model, "chartAdvancedPaneModel.chartTargetLinesPaneModel.chartTargets");

      if(!(targets instanceof TargetInfo[] existing)) {
         return;
      }

      for(TargetInfo target : existing) {
         if(target != null) {
            target.setChanged(false);
         }
      }
   }

   /**
    * Implies {@code siblingSuffix := impliedValue} whenever the resolved patch sets a path
    * ending in {@code suffix} to a non-blank value without ALSO setting that path's own sibling
    * ({@code siblingSuffix}, same parent) in the same patch. A caller who sets the sibling
    * explicitly (to any value) is always left alone -- this only fills in the one combination
    * that would otherwise silently do nothing, per {@code set_assembly_properties}'s own
    * "forgiving where the intent is unambiguous" rule; it does not guess at any other field.
    *
    * <p>Three Tip-pane field pairs share this exact shape, each confirmed by reading the real
    * property-dialog services (Redmine #76516):
    * <ul>
    * <li>{@code tipView}/{@code tipOption} -- {@code setChartPropertyModel} et al. force
    * {@code tipView} back to {@code null} whenever {@code tipOption} is {@code false} (read from
    * the model as a whole, not from this one patch), regardless of what this call itself just
    * wrote there.
    * <li>{@code tipPaneModel.alpha}/{@code tipPaneModel.tipOption} -- the same services only call
    * {@code setAlphaValue} inside the {@code tipOption == true} branch, so {@code alpha}/
    * {@code tipAlpha} set alone is silently dropped, not merely left at its old value.
    * <li>{@code customTip}/{@code customRB} -- {@code customTip} is only applied when
    * {@code customRB == TipFormat.CUSTOM}; otherwise it is actively nulled out, so
    * {@code tooltip} set alone (without {@code tooltipMode: "CUSTOM"} in the same patch) does not
    * merely no-op, it wipes any existing custom tooltip.
    * </ul>
    */
   private static Object impliedSibling(Object model, Collection<String> resolvedPaths,
                                         String suffix, String siblingSuffix, Object impliedValue)
   {
      for(String path : resolvedPaths) {
         if(!path.endsWith("." + suffix)) {
            continue;
         }

         Object value = PropertyPath.get(model, path);

         if(value == null || (value instanceof String str && str.isEmpty())) {
            continue;
         }

         String siblingPath = path.substring(0, path.length() - suffix.length()) + siblingSuffix;

         if(resolvedPaths.contains(siblingPath)) {
            continue;
         }

         model = PropertyPath.set(model, siblingPath, impliedValue);
      }

      return model;
   }

   /**
    * CheckBox/ComboBox/RadioButton's {@code VSInputService.setListValues} always writes the
    * static {@code variableListDialogModel.labels}/{@code .values} to the assembly, but only
    * <i>uses</i> them at render/bind time when {@code comboBoxEditorModel.embedded} (or
    * {@code .query}) is set -- with both false (the default), {@code sourceType} stays
    * {@code NONE_SOURCE} and the write is silently inert (Redmine #76699/VFO-016). A patch that
    * sets {@code labels}/{@code values} with no {@code query}/{@code table}/{@code column} in
    * the same call has unambiguous static-list intent, so {@code embedded} is implied true for
    * it, the same "forgiving where intent is unambiguous" shape {@link #impliedSibling} already
    * covers for the Tip panes.
    *
    * <p>A patch that ALSO sets {@code query}/{@code table}/{@code column} is left alone --
    * {@code embedded=false, query=true} is a real, distinct configuration ({@code BOUND_SOURCE},
    * driven purely by the query binding); silently forcing {@code embedded=true} there would
    * reclassify it into {@code MERGE_SOURCE} instead, a different, surprising outcome. A caller
    * who sets {@code embedded} explicitly (either value) is likewise always left alone.
    */
   private static Object deriveEmbeddedFromStaticList(Object model,
                                                       Collection<String> resolvedPaths)
   {
      for(String path : resolvedPaths) {
         String suffix;

         if(path.endsWith(".variableListDialogModel.labels")) {
            suffix = ".variableListDialogModel.labels";
         }
         else if(path.endsWith(".variableListDialogModel.values")) {
            suffix = ".variableListDialogModel.values";
         }
         else {
            continue;
         }

         String editorPrefix = path.substring(0, path.length() - suffix.length());
         String embeddedPath = editorPrefix + ".embedded";

         if(resolvedPaths.contains(embeddedPath)) {
            continue;
         }

         String queryPath = editorPrefix + ".query";
         String tablePath =
            editorPrefix + ".selectionListDialogModel.selectionListEditorModel.table";
         String columnPath =
            editorPrefix + ".selectionListDialogModel.selectionListEditorModel.column";

         if(resolvedPaths.contains(queryPath) || resolvedPaths.contains(tablePath) ||
            resolvedPaths.contains(columnPath))
         {
            continue;
         }

         model = PropertyPath.set(model, embeddedPath, true);
      }

      return model;
   }

   /**
    * A trailing blank {@code rangeValues} entry (e.g. {@code ["60","90","",""]}) is an
    * unambiguous "extend the last band to the gauge's own max" request -- the renderer
    * (fillRanges0) now handles that correctly on its own, so it is left to pass through
    * silently. A <em>blank followed by a populated entry</em> (e.g. {@code ["60","","150"]})
    * is genuinely ambiguous -- the renderer has no principled way to resolve it and would
    * silently collapse that band to nothing -- so that shape is refused here instead of
    * being allowed to reach a plausible-but-wrong render.
    *
    * <p>Also validates, against the same {@code fillRanges0} mechanics (Redmine #76717/VOF-001):
    * every populated boundary must be non-decreasing relative to the one before it (a
    * non-monotonic pair makes {@code fillRanges0} silently skip that band), must be strictly
    * greater than the gauge's own min (its skip condition is {@code ranges[i] <= info.getMin()}),
    * and must have a matching {@code rangeColorValues} entry (a missing one falls through to
    * {@code fillRanges0}'s default paint instead of erroring).
    *
    * <p>Only invoked by the caller when this call's own patch touches
    * {@code gaugeAdvancedPaneModel.rangePaneModel}. The human Composer GUI has no equivalent
    * validation, so a gauge can already have an interior gap saved from that path (or from a
    * call before this guard existed); an unrelated later patch (e.g. {@code max}) must not be
    * blocked by state it never touched.
    */
   private void requireNoInteriorGapInGaugeRangeValues(Object model) {
      Object rangePane = PropertyPath.get(model, "gaugeAdvancedPaneModel.rangePaneModel");

      if(!(rangePane instanceof RangePaneModel range)) {
         return;
      }

      String[] rangeValues = range.getRangeValues();
      int lastPopulated = -1;

      for(int i = 0; i < rangeValues.length; i++) {
         if(rangeValues[i] != null && !rangeValues[i].isEmpty()) {
            lastPopulated = i;
         }
      }

      for(int i = 0; i < lastPopulated; i++) {
         if(rangeValues[i] == null || rangeValues[i].isEmpty()) {
            throw new IllegalArgumentException(
               "rangeValues[" + i + "] is blank but a later entry is set -- rangeValues may " +
               "only have a blank trailing entry (meaning \"extend to the gauge's own max\"), " +
               "not a gap in the middle.");
         }
      }

      double[] parsed = new double[lastPopulated + 1];

      // A populated element that is itself an unresolved "$(...)"/"=..." dynamic reference
      // (bug #76886) is exempted from the numeric/monotonic/min checks below, same as
      // columnValue/rowValue's own VSUtil.isDynamicValue guards above -- it resolves at render
      // time, never here. It still counts as "populated" for the gap check above and still
      // needs a matching rangeColorValues entry below; only the numeric comparisons skip it.
      boolean[] dynamic = new boolean[lastPopulated + 1];

      for(int i = 0; i <= lastPopulated; i++) {
         if(VSUtil.isDynamicValue(rangeValues[i])) {
            dynamic[i] = true;
            continue;
         }

         try {
            parsed[i] = Double.parseDouble(rangeValues[i]);
         }
         catch(NumberFormatException e) {
            throw new IllegalArgumentException(
               "rangeValues[" + i + "] ('" + rangeValues[i] + "') is not a number.");
         }

         if(i > 0 && !dynamic[i - 1] && parsed[i] < parsed[i - 1]) {
            throw new IllegalArgumentException(
               "rangeValues[" + i + "] (" + rangeValues[i] + ") must be >= rangeValues[" +
               (i - 1) + "] (" + rangeValues[i - 1] + ") -- boundaries must be non-decreasing.");
         }
      }

      Double min = gaugeMin(model);

      if(min != null) {
         for(int i = 0; i <= lastPopulated; i++) {
            if(!dynamic[i] && parsed[i] <= min) {
               throw new IllegalArgumentException(
                  "rangeValues[" + i + "] (" + rangeValues[i] + ") must be greater than the " +
                  "gauge's min (" + min + ").");
            }
         }
      }

      String[] rangeColorValues = range.getRangeColorValues();
      boolean anyColorPopulated = false;

      for(String color : rangeColorValues) {
         if(color != null && !color.isEmpty()) {
            anyColorPopulated = true;
            break;
         }
      }

      // Only enforced once the caller has started customizing colors at all -- leaving
      // rangeColorValues entirely unset is its own legitimate state (every band paints with
      // fillRanges0's default color), not the reported defect. The reported defect is a
      // *partial* list: some boundaries colored, a later one silently not, which is genuinely
      // ambiguous the same way an interior rangeValues gap is.
      //
      // Bounds-checked, not indexed as if rangeColorValues were always padded to a fixed
      // length: set_assembly_properties writes both arrays at exactly the caller's length (no
      // padding), so "i >= rangeColorValues.length" is the normal way a missing color shows up,
      // not an unreachable edge case. The window stops at lastPopulated (not lastPopulated + 1)
      // so it does not demand a color for the implicit trailing auto-extend band -- any
      // rangeValues slot beyond lastPopulated is already forgiven by the gap check above, and
      // fillRanges0 itself tolerates a missing color there (falls through to its default paint),
      // consistent with that same forgiveness.
      if(anyColorPopulated) {
         for(int i = 0; i <= lastPopulated; i++) {
            if(i >= rangeColorValues.length || rangeColorValues[i] == null ||
               rangeColorValues[i].isEmpty())
            {
               throw new IllegalArgumentException(
                  "rangeColorValues[" + i + "] is required because rangeValues[" + i +
                  "] is set -- every populated boundary needs a matching color.");
            }
         }
      }
   }

   /**
    * The gauge's own min, read the same place {@code GaugePropertyDialogService} does
    * ({@code gaugeGeneralPaneModel.numberRangePaneModel.min}), falling back to {@code 0} when
    * unset to match {@code RangeOutputVSAssemblyInfo.getMin()}'s own default. Returns
    * {@code null} -- skip the check rather than block an unrelated write -- when the field holds
    * something this can't compare against (e.g. an unresolved {@code "$(...)"} variable
    * reference); that resolution only happens at render time, never here.
    */
   private static Double gaugeMin(Object model) {
      Object min = PropertyPath.get(model, "gaugeGeneralPaneModel.numberRangePaneModel.min");
      String text = min == null ? null : String.valueOf(min);

      if(text == null || text.isEmpty()) {
         return 0.0;
      }

      try {
         return Double.parseDouble(text);
      }
      catch(NumberFormatException e) {
         return null;
      }
   }

   /**
    * Normalizes an unset {@code table} to {@code columnValue} when the latter is already a
    * {@code "$(varName)"} reference (bug #76530/#76555) -- kept AI-only here since
    * {@code VSInputService} is shared with the interactive UI's save path. Must run before
    * {@code requireVariableFlagAchievable}/{@code writeModel}.
    */
   private Object normalizeVariableTableBinding(Object model) {
      String table = (String) PropertyPath.get(model, "dataInputPaneModel.table");
      String columnValue = (String) PropertyPath.get(model, "dataInputPaneModel.columnValue");

      if((table == null || table.isEmpty()) && columnValue != null &&
         columnValue.startsWith("$(") && columnValue.endsWith(")"))
      {
         return PropertyPath.set(model, "dataInputPaneModel.table", columnValue);
      }

      return model;
   }

   /**
    * Refuses a {@code dataInputPaneModel.variable} write that doesn't match what the resolved
    * {@code table}/{@code columnValue} binding can actually achieve (bug #76530/#76552) -- the
    * real setter always derives the persisted flag from the binding, never from this field
    * directly, so a mismatched request would otherwise silently no-op or be wrongly refused.
    */
   private void requireVariableFlagAchievable(String type, Object model, Viewsheet vs) {
      boolean requested = (Boolean) PropertyPath.get(model, "dataInputPaneModel.variable");
      String table = (String) PropertyPath.get(model, "dataInputPaneModel.table");
      String columnValue = (String) PropertyPath.get(model, "dataInputPaneModel.columnValue");
      Worksheet ws = vs == null ? null : vs.getBaseWorksheet();
      boolean achieved = VSInputService.resolvesToVariableBinding(ws, vs, table, columnValue);

      if(requested != achieved) {
         throw new IllegalArgumentException(
            "'dataInputPaneModel.variable' cannot be set to " + requested + " on " + type +
            ". Its real, persisted value is always derived from whether " +
            "'dataInputPaneModel.table' (or 'columnValue', if shaped \"$(variableName)\") " +
            "resolves to an existing worksheet variable -- it currently " +
            (achieved ? "does" : "does not") + ", so this write would report success and leave " +
            "'variable' " + achieved + ". " + (requested
               ? "Set 'dataInputPaneModel.table' (or 'columnValue') to \"$(variableName)\" for " +
                 "a variable created with add_variable instead."
               : "Set 'dataInputPaneModel.table' (or 'columnValue') to a non-variable binding " +
                 "instead, or leave 'dataInputPaneModel.variable' out of the patch."));
      }
   }

   /**
    * Refuses a {@code dataInputPaneModel.columnValue}/{@code rowValue} write that does not
    * exist on the bound embedded table (bug #76803/VOF-013/VOF-014). {@code
    * InputVSAssemblyInfo.update(Viewsheet, ColumnSelection)} is the only place either field is
    * actually checked against the table's real columns/row count, and it only runs lazily, on
    * some later {@code set_input_value}-triggered refresh -- never at write time. Until then, a
    * bad {@code rowValue} is a silent, permanent no-op (nothing ever complains) and a bad
    * {@code columnValue} crashes the next unrelated {@code set_input_value} call with a generic
    * 500 that names neither the assembly nor the bad column. This mirrors {@code update()}'s own
    * checks so both are refused loud, here, instead.
    *
    * <p>Fails open (skips validation) whenever the binding cannot be resolved to a concrete
    * embedded table -- a variable binding, an unresolvable {@code table}, or a {@code table}
    * that resolves to something other than an {@link EmbeddedTableAssembly} -- rather than block
    * a patch this check cannot meaningfully validate; {@code requireVariableFlagAchievable}
    * already covers the variable-binding case.
    *
    * <p>Also skips a {@code columnValue}/{@code rowValue} that is dynamic ({@code
    * VSUtil#isDynamicValue}: a {@code "$(variableName)"} reference or a {@code "="}-prefixed
    * script expression) -- this check only has the raw design-time string, not a
    * {@code ViewsheetSandbox} to resolve it, so a scripted binding cannot be statically checked
    * without risking a false-positive refusal of a value {@code update()} would resolve and
    * accept at runtime.
    */
   private void requireRowColumnValueValid(String type, Object model, Viewsheet vs) {
      String table = (String) PropertyPath.get(model, "dataInputPaneModel.table");
      String columnValue = (String) PropertyPath.get(model, "dataInputPaneModel.columnValue");
      String rowValue = (String) PropertyPath.get(model, "dataInputPaneModel.rowValue");
      Worksheet ws = vs == null ? null : vs.getBaseWorksheet();

      if(VSInputService.resolvesToVariableBinding(ws, vs, table, columnValue)) {
         return;
      }

      if(table == null || table.isEmpty() || ws == null) {
         return;
      }

      String tname = table;

      if(tname.startsWith("$(") && tname.endsWith(")")) {
         tname = tname.substring(2, tname.length() - 1);
      }

      Object obj = ws.getAssembly(tname);

      if(obj == null && tname.endsWith("_O")) {
         obj = ws.getAssembly(tname.substring(0, tname.length() - 2));
      }

      if(!(obj instanceof EmbeddedTableAssembly assembly)) {
         return;
      }

      if(columnValue != null && !columnValue.isEmpty() && !VSUtil.isDynamicValue(columnValue)) {
         ColumnSelection columns = assembly.getColumnSelection(false);
         DataRef attr = columns.getAttribute(columnValue);

         if(attr == null) {
            StringBuilder names = new StringBuilder();

            for(int i = 0; i < columns.getAttributeCount(); i++) {
               names.append(i == 0 ? "" : ", ").append(columns.getAttribute(i).getAttribute());
            }

            throw new IllegalArgumentException(
               "'dataInputPaneModel.columnValue' ('" + columnValue + "') is not a column of '" +
               tname + "'. StyleBI resolves an unrecognised columnValue with no error until the " +
               "next set_input_value call, which then crashes with an unrelated, opaque error, " +
               "so this write would report success and leave the input permanently broken. " +
               "Real columns: " + names + ".");
         }
      }

      if(rowValue != null && !rowValue.isEmpty() && !VSUtil.isDynamicValue(rowValue)) {
         int row;

         try {
            row = Integer.parseInt(rowValue);
         }
         catch(NumberFormatException e) {
            return;
         }

         XEmbeddedTable data = assembly.getEmbeddedData();

         if(row < 0 || row > data.getRowCount()) {
            throw new IllegalArgumentException(
               "'dataInputPaneModel.rowValue' (" + row + ") is out of range for '" + tname +
               "', which has " + data.getRowCount() + " row(s) (0-indexed, header row " +
               "included). StyleBI accepts an out-of-range rowValue with no error and the " +
               "input's value silently never reaches any cell, so this write would report " +
               "success and be a permanent no-op.");
         }
      }
   }

   /**
    * Refuses a {@code tableStyle} write that does not match any real table style (bug
    * #76764/VTS-003). {@code PropertyPath}'s {@code CONSTRAINED_STRINGS} gate does not cover
    * {@code tableStyle} -- its domain is dynamic and per-organization, unlike the closed enums
    * that gate handles -- so an unresolvable name/ID was written through unchanged. At render
    * time, {@code DataVSAQuery}/{@code VSUtil.getTableStyle} resolve that to {@code null} and
    * silently fall back to CSS-only formatting: no error anywhere, and the bogus value is
    * echoed back on read, indistinguishable from a table that never had a style set.
    *
    * <p>Matches against both {@link TreeNodeModel#data()} (the style's internal ID -- what the
    * interactive Composer UI itself writes/matches) and {@link TreeNodeModel#label()} (the
    * folder-stripped display name -- what this plugin's own {@code tableStyleTools.ts} tells
    * callers to write). Both are genuinely resolvable at render time via
    * {@code LibManager.getTableStyle} (exact-ID lookup, then fuzzy name lookup); a validator
    * that accepted only one field would falsely refuse the other's real, currently-legitimate
    * calling convention.
    *
    * <p>Walks the model's own {@code tableStylePaneModel.styleTree}, already populated by
    * {@code readModel()} before this patch loop runs, rather than re-fetching it -- {@code
    * TableStylePaneModel} is a plain mutable POJO, so this reference is guaranteed unchanged
    * regardless of what else in the same patch has already been applied.
    */
   private void requireKnownTableStyle(Object model, String path, Object value) {
      if(value == null) {
         return;
      }

      String text = String.valueOf(value).trim();

      if(text.isEmpty()) {
         return;
      }

      String panePath = path.substring(0, path.length() - ".tableStyle".length());
      Object pane = PropertyPath.get(model, panePath);

      if(!(pane instanceof TableStylePaneModel styleModel) || styleModel.getStyleTree() == null) {
         return;
      }

      if(!styleTreeHasStyle(styleModel.getStyleTree(), text)) {
         throw new IllegalArgumentException(
            "'" + path + "' ('" + text + "') does not match any table style's id or name. " +
            "StyleBI resolves an unrecognised tableStyle to no style at render time with no " +
            "error -- the table just renders unstyled -- so this write would report success " +
            "and leave the table unchanged. Use a value from list_table_styles, or this " +
            "assembly's own current 'tableStyle' read via get_assembly_properties(raw:true).");
      }
   }

   private static boolean styleTreeHasStyle(TreeNodeModel node, String value) {
      if(node.leaf() &&
         (value.equals(String.valueOf(node.data())) || value.equals(node.label())))
      {
         return true;
      }

      for(TreeNodeModel child : node.children()) {
         if(styleTreeHasStyle(child, value)) {
            return true;
         }
      }

      return false;
   }

   /**
    * Refuses — or canonicalizes — a {@code selectedImage} write that does not name a real image
    * in the assembly's own image tree (VOF-011).
    *
    * <p>{@code ImagePropertyDialogService} stores whatever string it is handed
    * ({@code setImageValue} is a bare {@code DynamicValue.setDValue}), and nothing downstream
    * complains: {@code VSUtil.getVSImage} falls through every lookup and returns {@code null}
    * without logging for an unprefixed path, while {@code VSImageModel} sets
    * {@code noImageFlag} from {@code getImage() == null} — so a non-blank bogus value leaves the
    * flag false, the client asks for an image and receives nothing. An empty placeholder box,
    * {@code ok:true}, and no signal anywhere that the value itself was the problem.
    *
    * <p><b>The encoding is not guessable from the tree.</b> A leaf's stored value is its
    * {@code type} marker concatenated directly onto its {@code data} with no separator —
    * {@code "^UPLOADED^logo.png"}, {@code "^SKIN^background1.png"} — which is what
    * {@code image-preview-pane.component.ts} composes when a human picks a node. The
    * {@code "Skin"} and {@code "Uploaded"} nodes above them are untyped display folders that
    * never appear in the value, so the slash-joined {@code "Uploaded/logo.png"} that the tree's
    * shape suggests is not a real value. Rather than only refusing it, that form is accepted as
    * an alias and rewritten to the canonical one: it is the reading a caller naturally takes
    * from the tree, and normalizing costs less than expecting every caller to learn the prefix.
    *
    * <p>Dynamic values ({@code $...}/{@code =...}) pass through unresolved, exactly as the
    * Composer's own preview pane treats them — they name a variable or expression, not a node.
    *
    * <p>Walks the model's own {@code imagePreviewPaneModel.imageTree}, already populated by
    * {@code readModel()} before the patch loop runs, the same way
    * {@link #requireKnownTableStyle} walks {@code tableStylePaneModel.styleTree}.
    *
    * @return the canonical value to write, which may differ from {@code value} when an alias was
    *         given.
    */
   private Object requireKnownSelectedImage(Object model, String path, Object value) {
      if(value == null) {
         return null;
      }

      String text = String.valueOf(value).trim();

      // Blank clears the image, which is a legitimate state; a dynamic reference is resolved at
      // render time and has no node to match here.
      if(text.isEmpty() || text.startsWith("$") || text.startsWith("=")) {
         return value;
      }

      String panePath = path.substring(0, path.length() - ".selectedImage".length());
      Object pane = PropertyPath.get(model, panePath);

      if(!(pane instanceof ImagePreviewPaneModel previewModel) ||
         previewModel.imageTree() == null)
      {
         return value;
      }

      String canonical = canonicalImageValue(previewModel.imageTree(), null, text);

      if(canonical != null) {
         return canonical;
      }

      List<String> known = new ArrayList<>();
      collectImageValues(previewModel.imageTree(), known);

      throw new IllegalArgumentException(
         "'" + path + "' ('" + text + "') does not name any image in this assembly's image " +
         "tree. StyleBI stores an unrecognised value unchanged and renders an empty box with no " +
         "error, so this write would report success and show nothing. A value is the node's " +
         "type marker joined directly to its name, with no separator -- e.g. " +
         "\"^UPLOADED^logo.png\", not \"Uploaded/logo.png\" (that spelling is accepted as an " +
         "alias, but only for an image that exists). Known values: " +
         (known.isEmpty() ? "none -- this viewsheet has no images to choose from" : known) + ".");
   }

   /**
    * Finds the leaf {@code text} names and returns its canonical stored value, or {@code null}.
    *
    * @param parentName the enclosing folder's display name, for the {@code "Folder/name"} alias.
    */
   private static String canonicalImageValue(TreeNodeModel node, String parentName, String text) {
      if(node.leaf()) {
         String canonical = canonicalImageValue(node);

         if(canonical == null) {
            return null;
         }

         if(text.equals(canonical) ||
            parentName != null && text.equals(parentName + "/" + node.data()))
         {
            return canonical;
         }

         return null;
      }

      String name = node.label() != null ? node.label()
         : node.data() == null ? null : String.valueOf(node.data());

      for(TreeNodeModel child : node.children()) {
         String canonical = canonicalImageValue(child, name, text);

         if(canonical != null) {
            return canonical;
         }
      }

      return null;
   }

   private static void collectImageValues(TreeNodeModel node, List<String> into) {
      if(node.leaf()) {
         String canonical = canonicalImageValue(node);

         if(canonical != null) {
            into.add(canonical);
         }

         return;
      }

      for(TreeNodeModel child : node.children()) {
         collectImageValues(child, into);
      }
   }

   /**
    * A leaf's stored value: its type marker joined directly to its data, mirroring
    * {@code image-preview-pane.component.ts}'s {@code getImageType(node.type) + node.data}.
    *
    * @return {@code null} for a node that is not selectable — one with no data, or the
    *         "Current Image" placeholder, which names the assembly's existing image rather than
    *         a new one and is not a value anything can be set to.
    */
   private static String canonicalImageValue(TreeNodeModel node) {
      if(node.data() == null || CURRENT_IMAGE_NODE_TYPE.equals(node.type())) {
         return null;
      }

      String type = node.type();
      // Set.of() rejects a null argument to contains(), and an untyped folder node has none.
      String prefix = type != null && IMAGE_TYPE_MARKERS.contains(type) ? type : "";

      return prefix + node.data();
   }

   /** The image tree's "Current Image" placeholder node, which is not a settable value. */
   private static final String CURRENT_IMAGE_NODE_TYPE = "current";

   /**
    * The three markers a tree node's {@code type} can carry that are genuinely part of the
    * stored value. Any other type — the "current" placeholder, or an untyped folder — is not.
    */
   private static final Set<String> IMAGE_TYPE_MARKERS = Set.of(
      ImageVSAssemblyInfo.SERVER_IMAGE, ImageVSAssemblyInfo.UPLOADED_IMAGE,
      ImageVSAssemblyInfo.SKIN_IMAGE);

   /**
    * A closed int-enum property's own display name (for its rejection message) plus its
    * token-to-int mapping. {@link #INT_ENUM_DOMAINS} keys these by <b>resolved path</b> rather
    * than by this name, since more than one property can share a short alias/leaf name across
    * unrelated assembly types -- see {@link #INT_ENUM_DOMAINS}'s own javadoc.
    */
   private record IntEnumDomain(String propertyName, Map<String, Integer> tokens) {}

   /**
    * A closed int-enum property's domain, keyed by the alias-resolved <b>full path</b> rather
    * than by the short property name it may share with an unrelated property on a different
    * assembly type -- e.g. {@code selectionGeneralPaneModel.showType} (SelectionList and
    * SelectionTree) and {@code calendarAdvancedPaneModel.showType} (Calendar) are both aliased
    * under the identical short name {@code "showType"}, but their int domains are different and
    * overlapping: {@code 1} means "dropdown" for Selection ({@link
    * SelectionVSAssemblyInfo#DROPDOWN_SHOW_TYPE}) and "calendar" mode for Calendar ({@link
    * CalendarVSAssemblyInfo#CALENDAR_SHOW_TYPE}), whose own dropdown is {@code 2} ({@link
    * CalendarVSAssemblyInfo#DROPDOWN_SHOW_TYPE}). A table keyed by leaf name alone would silently
    * misapply "dropdown" on one of the two -- so this is keyed by the resolved path, the same
    * reason {@link #canonicalIntEnum} needs it rather than {@code PropertyPath}'s
    * leaf-name-keyed {@code CONSTRAINED_STRINGS}.
    *
    * <p>{@code showType} (bug #76542), {@code selectionTreePaneModel.mode} and
    * {@code selectionGeneralPaneModel.sortType} (both bug #76925) are covered here.
    * {@code sortType}'s domain is narrowed to the four values its dialog (asc/desc/specific) and
    * its script API ({@code SelectionBaseVSAssemblyInfo#setSortType}'s own javadoc, plus
    * {@code SelectionListVSAScriptable}/{@code SelectionTreeVSAScriptable} exposing
    * {@code sortType} under this same name) can legitimately produce -- none of
    * {@link inetsoft.uql.XConstants}'s {@code SORT_ORIGINAL}/{@code SORT_VALUE_ASC}/
    * {@code SORT_VALUE_DESC} are ever written into a Selection assembly's own stored
    * {@code sortType} by either producer, so those three stay out of this domain.
    * {@code rangeType} (RangeSlider, bug #76936) is now covered here too, keyed by
    * {@code rangeSliderAdvancedPaneModel.rangeSliderSizePaneModel.rangeType} -- its domain is
    * {@link TimeInfo}'s own non-sequential constants ({@code YEAR=1, MONTH=2, NUMBER=3, MEMBER=4,
    * DAY=16, HOUR=17, MINUTE=18, HOUR_OF_DAY=20, MINUTE_OF_DAY=21}), the same "wrong guess is a
    * valid-looking value" hazard {@code sortType}/{@code mode} guard against above.
    * {@code linkType}, {@code refType} and {@code newObjectType} are the same class of gap (a
    * closed int domain with no alias/validation) but are not currently aliased in
    * {@link PropertyAliases} at all, so they are not reachable through this service's short-alias
    * vocabulary and remain out of scope.
    */
   private static final Map<String, IntEnumDomain> INT_ENUM_DOMAINS;

   static {
      Map<String, Integer> selectionShowType = new LinkedHashMap<>();
      selectionShowType.put("list", SelectionVSAssemblyInfo.LIST_SHOW_TYPE);
      selectionShowType.put("dropdown", SelectionVSAssemblyInfo.DROPDOWN_SHOW_TYPE);

      Map<String, Integer> calendarShowType = new LinkedHashMap<>();
      calendarShowType.put("calendar", CalendarVSAssemblyInfo.CALENDAR_SHOW_TYPE);
      calendarShowType.put("dropdown", CalendarVSAssemblyInfo.DROPDOWN_SHOW_TYPE);

      Map<String, Integer> mode = new LinkedHashMap<>();
      mode.put("column", SelectionTreeVSAssemblyInfo.COLUMN);
      mode.put("columns", SelectionTreeVSAssemblyInfo.COLUMN);
      mode.put("id", SelectionTreeVSAssemblyInfo.ID);

      Map<String, Integer> sortType = new LinkedHashMap<>();
      sortType.put("none", XConstants.SORT_NONE);
      sortType.put("unsorted", XConstants.SORT_NONE);
      sortType.put("asc", XConstants.SORT_ASC);
      sortType.put("ascending", XConstants.SORT_ASC);
      sortType.put("desc", XConstants.SORT_DESC);
      sortType.put("descending", XConstants.SORT_DESC);
      sortType.put("specific", XConstants.SORT_SPECIFIC);
      sortType.put("hideothers", XConstants.SORT_SPECIFIC);
      sortType.put("hide_others", XConstants.SORT_SPECIFIC);

      Map<String, Integer> rangeType = new LinkedHashMap<>();
      rangeType.put("year", TimeInfo.YEAR);
      rangeType.put("month", TimeInfo.MONTH);
      rangeType.put("number", TimeInfo.NUMBER);
      rangeType.put("member", TimeInfo.MEMBER);
      rangeType.put("day", TimeInfo.DAY);
      rangeType.put("hour", TimeInfo.HOUR);
      rangeType.put("minute", TimeInfo.MINUTE);
      rangeType.put("hourofday", TimeInfo.HOUR_OF_DAY);
      rangeType.put("hour_of_day", TimeInfo.HOUR_OF_DAY);
      rangeType.put("minuteofday", TimeInfo.MINUTE_OF_DAY);
      rangeType.put("minute_of_day", TimeInfo.MINUTE_OF_DAY);

      Map<String, IntEnumDomain> domains = new LinkedHashMap<>();
      domains.put("selectionGeneralPaneModel.showType",
                  new IntEnumDomain("showType", selectionShowType));
      domains.put("calendarAdvancedPaneModel.showType",
                  new IntEnumDomain("showType", calendarShowType));
      domains.put("selectionTreePaneModel.mode", new IntEnumDomain("mode", mode));
      domains.put("selectionGeneralPaneModel.sortType", new IntEnumDomain("sortType", sortType));
      domains.put("rangeSliderAdvancedPaneModel.rangeSliderSizePaneModel.rangeType",
                  new IntEnumDomain("rangeType", rangeType));
      INT_ENUM_DOMAINS = Collections.unmodifiableMap(domains);
   }

   /**
    * Canonicalizes a closed int-enum property's value before it reaches
    * {@code PropertyPath.set/coerce()}, the same way
    * {@link ChartRegionPropertyService#canonicalRotation} pre-transforms {@code rotation} for
    * its own service: every property in {@link #INT_ENUM_DOMAINS} is a plain primitive
    * {@code int} on the model it resolves to, so {@code PropertyPath.coerce()}'s numeric branch
    * never consults any alias/domain table -- a token like {@code "dropdown"} falls straight to
    * {@code Double.parseDouble} and fails with no valid values named, and an out-of-domain int
    * (e.g. {@code 999} for {@code sortType}) is silently stored as-is.
    *
    * <p>Additive: any path with no entry in {@link #INT_ENUM_DOMAINS} (i.e. every property this
    * service writes except the ones covered here) is returned unchanged.
    */
   private static Object canonicalIntEnum(String type, String resolvedPath, Object value) {
      IntEnumDomain domain = INT_ENUM_DOMAINS.get(resolvedPath);

      if(domain == null) {
         return value;
      }

      String text = value == null ? "" : String.valueOf(value).trim();

      for(Map.Entry<String, Integer> token : domain.tokens().entrySet()) {
         if(token.getKey().equalsIgnoreCase(text)) {
            return token.getValue();
         }
      }

      try {
         int parsed = (int) Double.parseDouble(text);

         if(domain.tokens().containsValue(parsed)) {
            return parsed;
         }
      }
      catch(NumberFormatException ignore) {
         // falls through to the error below
      }

      StringBuilder allowed = new StringBuilder();

      for(Map.Entry<String, Integer> token : domain.tokens().entrySet()) {
         if(allowed.length() > 0) {
            allowed.append(", ");
         }

         allowed.append("'").append(token.getKey()).append("' (").append(token.getValue())
            .append(")");
      }

      throw new IllegalArgumentException(
         "'" + domain.propertyName() + "' on a " + type + " accepts only " + allowed + "; '" +
         value + "' is not one of them.");
   }

   // ── convention dispatch ───────────────────────────────────────────────────

   private Object readModel(RuntimeViewsheet rvs, String type, String assemblyName,
                            Principal user)
   {
      Binding binding = bindingFor(type);
      Method getter = method(binding.service(), binding.getter(), binding.getterArity());
      Object[] args = new Object[binding.getterArity()];
      args[0] = rvs.getID();
      args[1] = assemblyName;
      System.arraycopy(binding.extraGetterArgs(), 0, args, 2,
                       binding.extraGetterArgs().length);
      args[args.length - 1] = user;

      try {
         return getter.invoke(binding.service(), args);
      }
      catch(IllegalAccessException | InvocationTargetException e) {
         throw new IllegalArgumentException(
            "Reading " + type + " properties of '" + assemblyName + "' failed: " +
            rootMessage(e), e);
      }
   }

   private void writeModel(String runtimeId, String type, String assemblyName, Object model,
                           String linkUri, Principal user, CommandDispatcher dispatcher)
   {
      Binding binding = bindingFor(type);
      Method setter = method(binding.service(), binding.setter(), 6);

      try {
         setter.invoke(binding.service(), runtimeId, assemblyName, model, linkUri, user,
                       dispatcher);
      }
      catch(IllegalAccessException | InvocationTargetException e) {
         throw new IllegalArgumentException(
            "Setting " + type + " properties of '" + assemblyName + "' failed: " +
            rootMessage(e), e);
      }
   }

   Binding bindingFor(String type) {
      Binding binding = bindings.get(type);

      if(binding == null) {
         throw new IllegalArgumentException(
            "No property service wired for assembly type '" + type + "'. Wired types: " +
            String.join(", ", new TreeSet<>(bindings.keySet())) + ".");
      }

      return binding;
   }

   /** Package-visible so the convention test can assert every wired service satisfies it. */
   static Method method(Object service, String name, int parameterCount) {
      for(Method candidate : service.getClass().getMethods()) {
         if(candidate.getName().equals(name) && candidate.getParameterCount() == parameterCount) {
            return candidate;
         }
      }

      throw new IllegalStateException(
         service.getClass().getSimpleName() + " has no " + name + " taking " + parameterCount +
         " arguments. The binding for this type names that method explicitly; if the composer " +
         "has renamed it, update the binding.");
   }

   Map<String, Binding> wiredBindings() {
      return bindings;
   }

   private String typeOf(RuntimeViewsheet rvs, String assemblyName) {
      Viewsheet vs = rvs == null ? null : rvs.getViewsheet();
      Object assembly = vs == null ? null : vs.getAssembly(assemblyName);

      if(assembly == null) {
         throw new IllegalArgumentException("Unknown assembly '" + assemblyName + "'.");
      }

      String simple = assembly.getClass().getSimpleName();
      String type = simple.endsWith("VSAssembly")
         ? simple.substring(0, simple.length() - "VSAssembly".length()) : simple;
      String normalized = type.toLowerCase();

      if(!PropertyAliases.covers(normalized)) {
         throw new IllegalArgumentException(
            "'" + assemblyName + "' is a " + type + ", whose properties are not covered yet. " +
            "Covered types: " + String.join(", ", new TreeSet<>(PropertyAliases.coveredTypes())) +
            ".");
      }

      return normalized;
   }

   private static String rootMessage(Exception e) {
      Throwable cause = e instanceof InvocationTargetException invocation &&
         invocation.getCause() != null ? invocation.getCause() : e;
      return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
   }


   private final ViewsheetSessionService sessions;
   private final Map<String, Binding> bindings;
}
