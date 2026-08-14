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
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.web.composer.vs.dialog.*;
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
   /** One assembly type's dialog service and the two method names it actually uses. */
   record Binding(Object service, String getter, String setter) {}

   @Autowired
   public AssemblyPropertyService(ViewsheetSessionService sessions,
                                  GaugePropertyDialogService gaugeService,
                                  TextPropertyDialogService textService,
                                  ChartPropertyDialogService chartService,
                                  TableViewPropertyDialogService tableService,
                                  CrosstabPropertyDialogService crosstabService,
                                  SelectionListPropertyDialogService selectionListService,
                                  SelectionTreePropertyDialogService selectionTreeService)
   {
      this.sessions = sessions;
      this.bindings = Map.of(
         "gauge", new Binding(gaugeService, "getGaugePropertyDialogModel",
                              "setGaugePropertyDialogModel"),
         "text", new Binding(textService, "getTextPropertyDialogModel",
                             "setTextPropertyDialogModel"),
         "chart", new Binding(chartService, "getChartPropertyDialogModel",
                              "setChartPropertyModel"),
         "table", new Binding(tableService, "getTableViewPropertyDialogModel",
                              "setTablePropertyModel"),
         "crosstab", new Binding(crosstabService, "getCrosstabPropertyDialogModel",
                                 "setCrosstabPropertyModel"),
         "selectionlist", new Binding(selectionListService, "getSelectionListPropertyModel",
                                      "setSelectionListPropertyModel"),
         "selectiontree", new Binding(selectionTreeService, "getSelectionTreePropertyModel",
                                      "setSelectionTreePropertyModel"));
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
            resolved.put(key, PropertyAliases.resolve(type, key));
         }

         for(Map.Entry<String, String> entry : resolved.entrySet()) {
            PropertyPath.set(model, entry.getValue(), patch.get(entry.getKey()));
         }

         writeModel(runtimeId, type, assemblyName, model, linkUri, user, dispatcher);
      });
   }

   // ── convention dispatch ───────────────────────────────────────────────────

   private Object readModel(RuntimeViewsheet rvs, String type, String assemblyName,
                            Principal user)
   {
      Binding binding = bindingFor(type);
      Method getter = method(binding.service(), binding.getter(), 3);

      try {
         return getter.invoke(binding.service(), rvs.getID(), assemblyName, user);
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
