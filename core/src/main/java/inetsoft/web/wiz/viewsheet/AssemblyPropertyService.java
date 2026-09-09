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
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.web.composer.model.vs.RangePaneModel;
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
            model = PropertyPath.set(model, entry.getValue(), patch.get(entry.getKey()));
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

         writeModel(runtimeId, type, assemblyName, model, linkUri, user, dispatcher);
      });
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
   }

   /**
    * Bug #76530, relocated here from {@code VSInputService.resolveInputTableBinding} by bug
    * #76555: {@code table} left unset entirely while {@code columnValue} itself already carries
    * the {@code "$(varName)"} reference is the shape an AI caller naturally reaches for when only
    * one field looks like it should hold the variable. {@code VSInputService} is shared with the
    * interactive Composer UI's own property-dialog save path, so this AI-caller-specific
    * accommodation lives here instead, in the wiz-only layer -- {@code isKnownVariableName}
    * itself still runs inside {@code resolveInputTableBinding}'s own raw-{@code $(...)} branch
    * once the real setter is invoked downstream with the now-normalized {@code table}.
    *
    * <p>Runs for every patch to one of {@link PropertyAliases#derivesVariableFlagFromTable}'s
    * four types, not only when the patch touches {@code dataInputPaneModel.variable} specifically
    * -- {@code table} needs normalizing from {@code columnValue} regardless of which field this
    * particular patch happened to touch, matching how {@code VSInputService}'s own setters always
    * ran this unconditionally on every save. Must run before {@code requireVariableFlagAchievable}
    * and {@code writeModel} so both see the already-normalized {@code table}.
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
    * Bug #76530: textinput/combobox/slider/spinner's own setter never reads back
    * {@code dataInputPaneModel.variable} -- the real, persisted flag is always derived from
    * whether {@code dataInputPaneModel.table} (after {@code VSInputService}'s own
    * {@code resolveInputTableBinding} normalization, and, before that, this class's own
    * {@code normalizeVariableTableBinding} above) resolves to a {@code "$(varName)"} reference.
    * Only invoked when this call's own patch explicitly touches {@code dataInputPaneModel.variable}
    * (see the caller).
    *
    * <p>Bug #76552 (follow-up): the achievable state must be compared against the value the
    * patch actually <em>requested</em>, not just checked for being merely possible. Checking
    * achievability alone let {@code variable:false} through unguarded even when the table still
    * resolves to a variable (the real setter derives {@code true} from the table regardless, so
    * that write silently failed to take effect -- the same "reports success, changes nothing"
    * defect this whole check exists to catch, just from the opposite direction), and also
    * refused a harmless {@code variable:false} on a table that was never a variable to begin
    * with. {@code dataInputPaneModel.variable} is a primitive {@code boolean}
    * ({@link inetsoft.web.composer.model.vs.DataInputPaneModel#isVariable()}), and {@code model}
    * already has this call's patch applied by the caller, so the read below is always the
    * requested value, never null.
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
