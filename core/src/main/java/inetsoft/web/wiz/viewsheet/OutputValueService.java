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
import inetsoft.report.internal.table.TableFormat;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.viewsheet.OutputVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.internal.OutputVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;

import java.security.Principal;
import java.text.Format;
import java.util.*;
import java.util.function.BiFunction;

/**
 * Feature #79534: the computed value of a data-bound output assembly (Image, Text, Gauge,
 * Thermometer, Cylinder, SlidingScale). The runtime value lives in
 * {@code OutputVSAssemblyInfo.getValue()}, which {@code ViewsheetSandbox.executeOutput} sets on each
 * refresh; the browser only ever shows it as a hover tooltip, and no other read endpoint returns it.
 *
 * <p>Read-only: goes through the checkpoint-free {@code sessions.read} and never refreshes. The value
 * is as of the last refresh of that assembly ({@code set_condition} on the assembly itself already
 * refreshes it); a change made through a different assembly's condition or a selection may not have
 * reached it yet.
 */
public class OutputValueService {
   public OutputValueService(ViewsheetSessionService sessions) {
      this(sessions, VSUtil::getBindingDescription);
   }

   /** Lets a test supply the binding description, as {@code VSUtil} needs the Spring context. */
   OutputValueService(ViewsheetSessionService sessions,
                      BiFunction<OutputVSAssemblyInfo, RuntimeViewsheet, String> bindings)
   {
      this.bindings = bindings;
      this.sessions = sessions;
   }

   /**
    * @param assemblyNames the outputs to read; null or empty reads every data-bound output assembly.
    */
   public Map<String, Object> getValues(String sessionToken, Principal user,
                                        List<String> assemblyNames)
      throws Exception
   {
      List<String> names = assemblyNames == null ? List.of() :
         assemblyNames.stream().filter(n -> n != null && !n.isBlank()).map(String::trim)
            .distinct().toList();

      return sessions.read(sessionToken, user, (rvs, runtimeId, dispatcher) -> {
         List<OutputVSAssembly> outputs = new ArrayList<>();

         if(names.isEmpty()) {
            Viewsheet vs = rvs == null ? null : rvs.getViewsheet();

            if(vs != null) {
               for(Assembly assembly : vs.getAssemblies()) {
                  if(assembly instanceof OutputVSAssembly output && isBound(output)) {
                     outputs.add(output);
                  }
               }
            }
         }
         else {
            for(String name : names) {
               outputs.add(requireOutput(rvs, name));
            }
         }

         List<Map<String, Object>> entries = new ArrayList<>();

         for(OutputVSAssembly assembly : outputs) {
            entries.add(describe(assembly, rvs));
         }

         Map<String, Object> result = new LinkedHashMap<>();
         result.put("outputs", entries);
         return result;
      });
   }

   private static boolean isBound(OutputVSAssembly assembly) {
      return assembly.getVSAssemblyInfo() instanceof OutputVSAssemblyInfo info &&
         info.getScalarBindingInfo() != null && !info.getScalarBindingInfo().isEmpty();
   }

   private Map<String, Object> describe(OutputVSAssembly assembly, RuntimeViewsheet rvs) {
      Map<String, Object> entry = new LinkedHashMap<>();
      entry.put("assembly", assembly.getAbsoluteName());
      entry.put("type", assembly.getClass().getSimpleName());
      boolean bound = isBound(assembly);
      entry.put("bound", bound);

      if(!(assembly.getVSAssemblyInfo() instanceof OutputVSAssemblyInfo info)) {
         return entry;
      }

      Object value = info.getValue();

      if(value instanceof String s && Tool.isEmptyString(s)) {
         value = null;
      }

      String binding = bound ? bindings.apply(info, rvs) : null;
      entry.put("binding", binding);
      entry.put("value", jsonValue(value));

      String formatted = null;

      if(value != null) {
         formatted = Tool.toString(value);
         Format fmt = format(info);

         if(fmt != null) {
            try {
               formatted = fmt.format(value);
            }
            catch(IllegalArgumentException ex) {
               // not a valid format for this value: keep the unformatted text, as the model does
            }
         }
      }

      entry.put("formattedValue", formatted);

      if(bound && value != null && !Tool.isEmptyString(binding) &&
         !Tool.isEmptyString(formatted))
      {
         entry.put("tooltip", binding + ": " + formatted);
      }

      if(value == null) {
         entry.put("note", bound
            ? "The binding produced no value at the last refresh (for example the aggregate " +
              "returned no rows). An Image keeps its prior value when the result is empty."
            : "The assembly has no data binding, so there is no computed value to read.");
      }

      return entry;
   }

   private static Object jsonValue(Object value) {
      return value == null || value instanceof Number || value instanceof Boolean ||
         value instanceof String ? value : Tool.toString(value);
   }

   /** The format the browser applies to the value, as {@code VSOutputModel.getInitialFormat}. */
   private static Format format(OutputVSAssemblyInfo info) {
      var vfmt = info.getFormat();
      Locale locale = Catalog.getCatalog().getLocale();
      locale = locale == null ? Locale.getDefault() : locale;
      Format fmt = vfmt == null ? null :
         TableFormat.getFormat(vfmt.getFormat(), vfmt.getFormatExtent(), locale);
      return fmt == null ? info.getDefaultFormat() : fmt;
   }

   private static OutputVSAssembly requireOutput(RuntimeViewsheet rvs, String assemblyName) {
      Viewsheet vs = rvs == null ? null : rvs.getViewsheet();
      VSAssembly assembly = vs == null ? null : vs.getAssembly(assemblyName);

      if(assembly == null) {
         throw new IllegalArgumentException(
            "Unknown assembly '" + assemblyName + "'. There is no output assembly with that name " +
            "to read.");
      }

      if(!(assembly instanceof OutputVSAssembly output)) {
         throw new IllegalArgumentException(
            "'" + assemblyName + "' is a " + assembly.getClass().getSimpleName() +
            ", not a data-bound output assembly. Output assemblies are images, texts, gauges, " +
            "thermometers, cylinders and sliding scales. Use get_input_value for inputs and " +
            "get_binding for charts, tables and crosstabs.");
      }

      return output;
   }

   private final ViewsheetSessionService sessions;
   private final BiFunction<OutputVSAssemblyInfo, RuntimeViewsheet, String> bindings;
}
