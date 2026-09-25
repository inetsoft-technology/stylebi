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
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.Tool;
import inetsoft.web.viewsheet.service.VSInputService;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.*;

/**
 * An input assembly's value: combo box, check box, radio button, text input and spinner.
 *
 * <p><b>The unit's name overstated this half.</b> Every input assembly's action vocabulary is exactly
 * three items — {@code edit-script}, {@code properties}, {@code show-format-pane} — all already
 * shipped. There is no apply, clear, search or sort in any input's menu. What is missing is not menu
 * actions but the value itself, driven by direct interaction through four one-endpoint controllers
 * that all converge on {@code VSInputService}.
 *
 * <p>So this is one tool over one service method. {@code singleApplySelection} takes the value as a
 * bare {@code Object}, and a check box's several values arrive as that same parameter holding an
 * {@code Object[]} — which is why one method serves both the scalar and the multi-value inputs.
 *
 * <p><b>The endpoint is silent on everything.</b>
 * {@code applySelection} returns {@code 0} when the name is null, when the viewsheet is null, and —
 * the case that matters — when {@code !(vsAssembly instanceof InputVSAssembly)}. So pointing it at a
 * chart or a typo'd name reports success having done nothing. It is at least *consistently* silent,
 * unlike the selection verbs, which variously no-op, NPE and CCE. Either way there is nothing worth
 * forwarding, so this class resolves and type-checks first.
 *
 * <p><b>A value written here persists.</b> {@code CheckBoxVSAssembly}, {@code ComboBoxVSAssembly},
 * {@code RadioButtonVSAssembly} and {@code TextInputVSAssembly} all implement
 * {@code writeStateContent} the same way the selection assemblies do, and it is called on the save
 * path — so an input's value becomes what every future viewer opens with.
 *
 * <p><b>The assemblies' own setters adjust a value they cannot hold, without saying so.</b> A slider
 * or spinner ignores a non-number and clamps to its min/max
 * ({@code NumericRangeVSAssemblyInfo.setSelectedObject}); a check box drops values not in its list,
 * a radio button falls back to its first value and a non-editable combo box auto-selects its first
 * entry ({@code validate()} on each). The Composer UI only offers values those setters accept, so
 * this class refuses the rest up front ({@link #checkValue}) and reports back what was actually
 * stored, in {@code values}.
 */
@Service
public class InputValueService {
   public InputValueService(ViewsheetSessionService sessions, VSInputService inputs) {
      this.sessions = sessions;
      this.inputs = inputs;
   }

   /**
    * Sets an input assembly's value.
    *
    * @param values one value for a combo box, radio button, text input or spinner; one or more for a
    *               check box. Empty means "no value selected", which is a legitimate state and not
    *               the same as leaving it alone.
    */
   public Map<String, Object> setValue(String sessionToken, Principal user, String assemblyName,
                                       List<Object> values, String linkUri)
      throws Exception
   {
      if(assemblyName == null || assemblyName.isBlank()) {
         throw new IllegalArgumentException("'assembly' is required — name the input assembly.");
      }

      if(values == null) {
         throw new IllegalArgumentException(
            "'value' is required. Pass an empty list to clear the input rather than omitting it, so " +
            "that clearing is something you asked for rather than a default.");
      }

      final Map<String, Object> result = new LinkedHashMap<>();

      sessions.mutate(sessionToken, user, (rvs, runtimeId, dispatcher) -> {
         InputVSAssembly assembly = requireInput(rvs, assemblyName);
         boolean multi = assembly instanceof CheckBoxVSAssembly;

         if(!multi && values.size() > 1) {
            throw new IllegalArgumentException(
               "'" + assemblyName + "' is " + describe(assembly) + ", which holds one value, but " +
               values.size() + " were given. Only a check box accepts several.");
         }

         VSAssemblyInfo info0 = assembly.getVSAssemblyInfo();
         // setSelectedObject does not clamp to a dynamic max, so neither does the check
         boolean dynamicMax = info0 instanceof NumericRangeVSAssemblyInfo range &&
            VSUtil.isDynamicValue(range.getMaxValue());
         checkValue(info0, assembly.getDataType(), values, dynamicMax, assemblyName,
                    describe(assembly));

         result.put("assembly", assemblyName);
         result.put("type", describe(assembly));
         result.put("valueCount", values.size());

         // A check box's several values travel as an Object[] in the same 'selectedObject'
         // parameter the scalar inputs use, which is why one service method serves both.
         Object selected = multi
            ? values.toArray()
            : (values.isEmpty() ? null : values.get(0));

         inputs.singleApplySelection(runtimeId, assemblyName, selected, user, dispatcher, linkUri);

         // Read back what the assembly actually holds -- a slider can still snap to its increment.
         VSAssembly applied = rvs.getViewsheet().getAssembly(assemblyName);

         if(applied != null && applied.getVSAssemblyInfo() instanceof InputVSAssemblyInfo info) {
            Object[] stored = info.getSelectedObjects();
            result.put("values", stored == null ? List.of() :
               Arrays.stream(stored).filter(Objects::nonNull).toList());
         }
      });

      result.put("persistsOnSave", true);
      return result;
   }

   /**
    * Refuses a value the assembly's own setter would silently ignore, clamp, drop or replace. Only
    * checks what the assembly's info makes unambiguous: a list input whose list has not been
    * computed yet (no values) is passed through, as is an editable or calendar combo box, which
    * accept values outside their list.
    */
   static void checkValue(VSAssemblyInfo info, String dataType, List<Object> values,
                          boolean dynamicMax, String assemblyName, String what)
   {
      if(info instanceof NumericRangeVSAssemblyInfo range) {
         if(values.isEmpty()) {
            throw new IllegalArgumentException(
               "'" + assemblyName + "' is " + what + ", which always holds a number and cannot " +
               "be cleared. Pass the value to set instead.");
         }

         Object value = values.get(0);
         Object number = value == null ? null : Tool.getData(dataType, value);

         if(!(number instanceof Number num)) {
            throw new IllegalArgumentException(
               "'" + assemblyName + "' is " + what + ", which only holds numbers — got " +
               quote(value) + ".");
         }

         double min = range.getMin();
         double max = range.getMax();

         if(num.doubleValue() < min || !dynamicMax && num.doubleValue() > max) {
            throw new IllegalArgumentException(
               "'" + assemblyName + "' is " + what + " with a range of " + format(min) + " to " +
               format(max) + ", and " + format(num.doubleValue()) + " is outside it. It would " +
               "be clamped to the nearest end rather than stored as given.");
         }

         return;
      }

      if(!(info instanceof ListInputVSAssemblyInfo list)) {
         return;
      }

      if(list instanceof ComboBoxVSAssemblyInfo combo &&
         (combo.isTextEditable() || combo.isCalendar()))
      {
         return;
      }

      Object[] choices = list.getValues();

      if(choices == null || choices.length == 0) {
         return;
      }

      boolean checkBox = list instanceof CheckBoxVSAssemblyInfo;

      if(values.isEmpty() && !checkBox) {
         throw new IllegalArgumentException(
            "'" + assemblyName + "' is " + what + ", which cannot be left empty — clearing it " +
            "would select one of its listed values instead. Pass the value to select.");
      }

      List<String> missing = new ArrayList<>();

      for(Object value : values) {
         Object converted = value == null ? null : Tool.getData(dataType, value);

         if(Arrays.stream(choices).noneMatch(c -> sameValue(converted, c))) {
            missing.add(quote(value));
         }
      }

      if(!missing.isEmpty()) {
         List<String> listed = new ArrayList<>();

         for(int i = 0; i < choices.length && i < MAX_LISTED; i++) {
            listed.add(quote(choices[i]));
         }

         throw new IllegalArgumentException(
            "'" + assemblyName + "' is " + what + " and " + String.join(", ", missing) +
            (missing.size() == 1 ? " is" : " are") + " not in its list, so " +
            (checkBox ? (missing.size() == 1 ? "it" : "they") + " would be dropped"
               : "it would be replaced by one of its listed values") +
            ". Its values are: " + String.join(", ", listed) +
            (choices.length > MAX_LISTED ? ", … (" + choices.length + " in all)" : "") + ".");
      }
   }

   /** The same comparison {@code ListInputVSAssemblyInfo.isSelectedObjectEqual} makes. */
   private static boolean sameValue(Object value, Object choice) {
      if(Tool.equals(value, choice)) {
         return true;
      }

      return value != null && choice != null &&
         Tool.equals(Tool.toString(value), Tool.toString(choice));
   }

   private static String quote(Object value) {
      return value instanceof String ? "\"" + value + "\"" : String.valueOf(value);
   }

   private static String format(double value) {
      return value == Math.rint(value) && !Double.isInfinite(value)
         ? String.valueOf((long) value) : String.valueOf(value);
   }

   private static InputVSAssembly requireInput(RuntimeViewsheet rvs, String assemblyName) {
      Viewsheet vs = rvs == null ? null : rvs.getViewsheet();
      VSAssembly assembly = vs == null ? null : vs.getAssembly(assemblyName);

      if(assembly == null) {
         throw new IllegalArgumentException(
            "Unknown assembly '" + assemblyName + "'. The input endpoint returns success and " +
            "changes nothing for a name it cannot find, so this is refused here instead.");
      }

      if(!(assembly instanceof InputVSAssembly input)) {
         throw new IllegalArgumentException(
            "'" + assemblyName + "' is a " + assembly.getClass().getSimpleName() +
            ", not an input assembly. Inputs are combo boxes, check boxes, radio buttons, text " +
            "inputs and spinners (a submit button is an output assembly and holds no value).");
      }

      return input;
   }

   private static String describe(InputVSAssembly assembly) {
      if(assembly instanceof CheckBoxVSAssembly) {
         return "a check box";
      }
      else if(assembly instanceof ComboBoxVSAssembly) {
         return "a combo box";
      }
      else if(assembly instanceof RadioButtonVSAssembly) {
         return "a radio button";
      }
      else if(assembly instanceof TextInputVSAssembly) {
         return "a text input";
      }
      else if(assembly instanceof SpinnerVSAssembly) {
         return "a spinner";
      }
      else if(assembly instanceof SliderVSAssembly) {
         return "a slider";
      }

      return "a " + assembly.getClass().getSimpleName();
   }

   private static final int MAX_LISTED = 20;

   private final ViewsheetSessionService sessions;
   private final VSInputService inputs;
}
