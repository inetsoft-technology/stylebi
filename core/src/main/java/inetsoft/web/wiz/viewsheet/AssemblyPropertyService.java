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
import inetsoft.uql.asset.EmbeddedTableAssembly;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.CalendarVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.SelectionVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.web.composer.model.TreeNodeModel;
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
            Object value = canonicalShowType(type, entry.getValue(), patch.get(entry.getKey()));

            if(entry.getValue().endsWith(".tableStylePaneModel.tableStyle")) {
               requireKnownTableStyle(model, entry.getValue(), value);
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

         writeModel(runtimeId, type, assemblyName, model, linkUri, user, dispatcher);
      });
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
    * {@code showType}'s domain, keyed by the alias-resolved <b>full path</b> rather than by the
    * short property name both share -- {@code selectionGeneralPaneModel.showType} (SelectionList
    * and SelectionTree) and {@code calendarAdvancedPaneModel.showType} (Calendar) are aliased
    * under the identical short name {@code "showType"}, but their int domains are different and
    * overlapping: {@code 1} means "dropdown" for Selection ({@link
    * SelectionVSAssemblyInfo#DROPDOWN_SHOW_TYPE}) and "calendar" mode for Calendar ({@link
    * CalendarVSAssemblyInfo#CALENDAR_SHOW_TYPE}), whose own dropdown is {@code 2} ({@link
    * CalendarVSAssemblyInfo#DROPDOWN_SHOW_TYPE}). A table keyed by leaf name alone would silently
    * misapply "dropdown" on one of the two -- so this is keyed by the resolved path, the same
    * reason {@link #canonicalShowType} needs it rather than {@code PropertyPath}'s leaf-name-keyed
    * {@code CONSTRAINED_STRINGS}.
    *
    * <p>Only {@code showType} is covered here. {@code sortType} (a bitmask, more complex),
    * {@code mode}, {@code linkType}, {@code rangeType}, {@code refType} and {@code newObjectType}
    * are the same class of gap (a closed int domain with no alias/validation), but out of scope
    * for this fix.
    */
   private static final Map<String, Map<String, Integer>> SHOW_TYPE_DOMAINS;

   static {
      Map<String, Integer> selection = new LinkedHashMap<>();
      selection.put("list", SelectionVSAssemblyInfo.LIST_SHOW_TYPE);
      selection.put("dropdown", SelectionVSAssemblyInfo.DROPDOWN_SHOW_TYPE);

      Map<String, Integer> calendar = new LinkedHashMap<>();
      calendar.put("calendar", CalendarVSAssemblyInfo.CALENDAR_SHOW_TYPE);
      calendar.put("dropdown", CalendarVSAssemblyInfo.DROPDOWN_SHOW_TYPE);

      Map<String, Map<String, Integer>> domains = new LinkedHashMap<>();
      domains.put("selectionGeneralPaneModel.showType", selection);
      domains.put("calendarAdvancedPaneModel.showType", calendar);
      SHOW_TYPE_DOMAINS = Collections.unmodifiableMap(domains);
   }

   /**
    * Canonicalizes a {@code showType} value before it reaches {@code PropertyPath.set/coerce()},
    * the same way {@link ChartRegionPropertyService#canonicalRotation} pre-transforms
    * {@code rotation} for its own service: {@code showType} is a plain primitive {@code int} on
    * both models it can resolve to, so {@code PropertyPath.coerce()}'s numeric branch never
    * consults any alias/domain table -- a token like {@code "dropdown"} falls straight to
    * {@code Double.parseDouble} and fails with no valid values named, and an out-of-domain int
    * (e.g. {@code 2} on a SelectionList) is silently stored as-is.
    *
    * <p>Additive: any path with no entry in {@link #SHOW_TYPE_DOMAINS} (i.e. every property this
    * service writes except {@code showType}) is returned unchanged.
    */
   private static Object canonicalShowType(String type, String resolvedPath, Object value) {
      Map<String, Integer> domain = SHOW_TYPE_DOMAINS.get(resolvedPath);

      if(domain == null) {
         return value;
      }

      String text = value == null ? "" : String.valueOf(value).trim();

      for(Map.Entry<String, Integer> token : domain.entrySet()) {
         if(token.getKey().equalsIgnoreCase(text)) {
            return token.getValue();
         }
      }

      try {
         int parsed = (int) Double.parseDouble(text);

         if(domain.containsValue(parsed)) {
            return parsed;
         }
      }
      catch(NumberFormatException ignore) {
         // falls through to the error below
      }

      StringBuilder allowed = new StringBuilder();

      for(Map.Entry<String, Integer> token : domain.entrySet()) {
         if(allowed.length() > 0) {
            allowed.append(", ");
         }

         allowed.append("'").append(token.getKey()).append("' (").append(token.getValue())
            .append(")");
      }

      throw new IllegalArgumentException(
         "'showType' on a " + type + " accepts only " + allowed + "; '" + value +
         "' is not one of them.");
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
