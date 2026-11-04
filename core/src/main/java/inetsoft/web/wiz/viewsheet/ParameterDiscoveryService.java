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
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.uql.VariableTable;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.schema.UserVariable;
import inetsoft.uql.viewsheet.InputVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.web.viewsheet.service.ParameterService;
import inetsoft.web.wiz.viewsheet.model.ParameterModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Discovers the variables a viewsheet's own source query and viewsheet tree reference — the same
 * computation {@link ParameterService#getPromptParameters} performs for StyleBI's own "Parameters"
 * prompt dialog, called here with an EMPTY {@link VariableTable} so a variable that already has a
 * value still shows up (this is an inventory tool, not a "what's still unanswered" tool).
 *
 * <p>Shared by {@link ParameterCollectionService} (the read half) and
 * {@link ParameterValueService} (the write half, which uses {@link #find} to validate a caller's
 * variable name and recover its real {@link UserVariable} — type, choices — before writing it).
 */
@Service
public class ParameterDiscoveryService {
   @Autowired
   public ParameterDiscoveryService(ParameterService parameterService) {
      this.parameterService = parameterService;
   }

   /**
    * @return every variable the viewsheet's source/tree references, minus any already bound to an
    *         on-canvas {@link InputVSAssembly} — those are set via {@code set_input_value}
    *         instead, and re-listing them here would invite a caller to fight that tool over the
    *         same value.
    */
   public List<UserVariable> discover(RuntimeViewsheet rvs) throws Exception {
      Viewsheet vs = rvs == null ? null : rvs.getViewsheet();

      if(vs == null) {
         return List.of();
      }

      Optional<ViewsheetSandbox> box = rvs.getViewsheetSandbox();

      if(box.isEmpty()) {
         return List.of();
      }

      List<UserVariable> vars =
         parameterService.getPromptParameters(vs, box.get(), new VariableTable());

      Set<String> inputBoundNames = boundVariableNames(vs);

      return vars.stream()
         .filter(v -> !inputBoundNames.contains(v.getName()))
         .collect(Collectors.toList());
   }

   /** Variable names already driven by an on-canvas input assembly, walking sub-viewsheets too. */
   private Set<String> boundVariableNames(Viewsheet vs) {
      Set<String> names = new HashSet<>();
      collectBoundVariableNames(vs, names);
      return names;
   }

   private void collectBoundVariableNames(Viewsheet vs, Set<String> names) {
      for(Assembly assembly : vs.getAssemblies()) {
         if(assembly instanceof Viewsheet nested) {
            collectBoundVariableNames(nested, names);
            continue;
         }

         if(assembly instanceof InputVSAssembly input) {
            // getVariableTableKey() already handles both a "$(name)"-wrapped and a bare
            // (unwrapped) table name -- unwrapping by hand here (as VSCollectParametersService's
            // own reverse-direction code does) would throw on a short bare name and silently
            // truncate a longer one.
            String key = input.getVariableTableKey();

            if(key != null) {
               names.add(key);
            }
         }
      }
   }

   /**
    * @throws IllegalArgumentException naming {@code name} if it is not one of {@code discovered}
    *         -- a caller can only set a variable this discovery call would also report.
    */
   public static UserVariable find(List<UserVariable> discovered, String name) {
      return find(discovered, name, null);
   }

   /**
    * Like {@link #find(List, String)}, but a name {@link #missingReason} can classify (disabled /
    * input-bound) is refused with that specific reason instead of the generic text.
    *
    * @param vs the runtime viewsheet; may be null.
    */
   public static UserVariable find(List<UserVariable> discovered, String name, Viewsheet vs) {
      return discovered.stream()
         .filter(v -> v.getName().equals(name))
         .findFirst()
         .orElseThrow(() -> {
            String reason = missingReason(vs, name);

            return new IllegalArgumentException(reason != null ? reason :
               "'" + name + "' is not a parameter this viewsheet's source or viewsheet tree " +
               "references (or it is already bound to an on-canvas input assembly -- use " +
               "set_input_value for that). collect_parameters lists what is settable here.");
         });
   }

   /**
    * Explains why {@code name} is absent from {@link #discover}, for a name that is neither a
    * discovered variable nor unknown. Classified in this order: disabled via the viewsheet's
    * disabledParameters (nested viewsheets included), driven by an input assembly's variable
    * binding, or merely sharing an input assembly's name (the silent drops in
    * {@code getPromptParameters} / {@code VSEventUtil.refreshParameters}).
    *
    * @return the refusal text, or {@code null} when none of those apply (the caller then falls
    *         back to the generic unknown-name text of {@link #find}).
    */
   public static String missingReason(Viewsheet vs, String name) {
      if(vs == null || name == null) {
         return null;
      }

      boolean disabled = vs.getDisabledVariables().contains(name);
      InputVSAssembly keyOwner = findKeyOwner(vs, name);

      if(keyOwner != null) {
         String text = "'" + name + "' is driven by the on-canvas input assembly '" +
            keyOwner.getName() + "' (its variable binding), so set_parameters will not set it. " +
            "Use set_input_value on that assembly instead.";

         if(disabled) {
            text += " It is also listed in this viewsheet's disabledParameters.";
         }

         return text;
      }

      InputVSAssembly named = findNameOwner(vs, name);

      if(named != null) {
         String text = "'" + name + "' matches the name of input assembly '" + named.getName() +
            "'; collect_parameters does not offer a parameter that shares an input " +
            "assembly's name, so set_parameters will not set it. To set that input, use " +
            "set_input_value.";

         if(disabled) {
            text += " It is also listed in this viewsheet's disabledParameters.";
         }

         return text;
      }

      if(disabled) {
         return "'" + name + "' is listed in this viewsheet's disabledParameters, so " +
            "set_parameters will not set it. To make it settable, remove it from " +
            "disabledParameters with set_viewsheet_properties. collect_parameters lists what " +
            "is settable here.";
      }

      return null;
   }

   /** First input assembly (nested viewsheets included) whose variable binding is {@code name}. */
   private static InputVSAssembly findKeyOwner(Viewsheet vs, String name) {
      for(Assembly assembly : vs.getAssemblies()) {
         if(assembly instanceof Viewsheet nested) {
            InputVSAssembly found = findKeyOwner(nested, name);

            if(found != null) {
               return found;
            }
         }
         else if(assembly instanceof InputVSAssembly input &&
            name.equals(input.getVariableTableKey()))
         {
            return input;
         }
      }

      return null;
   }

   /**
    * First input assembly whose name equals {@code name}, by either notion the discovery filters
    * use: the leaf name inside each (nested) viewsheet, and the recursive names of
    * {@code getAssemblies(true, true)}.
    */
   private static InputVSAssembly findNameOwner(Viewsheet vs, String name) {
      InputVSAssembly leaf = findLeafNameOwner(vs, name);

      if(leaf != null) {
         return leaf;
      }

      for(Assembly assembly : vs.getAssemblies(true, true)) {
         if(assembly instanceof InputVSAssembly input && name.equals(assembly.getName())) {
            return input;
         }
      }

      return null;
   }

   private static InputVSAssembly findLeafNameOwner(Viewsheet vs, String name) {
      if(vs.containsAssembly(name) && vs.getAssembly(name) instanceof InputVSAssembly input) {
         return input;
      }

      for(Assembly assembly : vs.getAssemblies()) {
         if(assembly instanceof Viewsheet nested) {
            InputVSAssembly found = findLeafNameOwner(nested, name);

            if(found != null) {
               return found;
            }
         }
      }

      return null;
   }

   /** Maps one discovered variable to its agent-facing JSON shape. Pure, no I/O -- unit-tested. */
   public static ParameterModel toModel(UserVariable variable, boolean boundToInputAssembly,
                                        Object[] currentValue)
   {
      String type = variable.getTypeNode() != null ? variable.getTypeNode().getType() : "string";
      Object[] choicesArr = variable.getChoices();
      Object[] valuesArr = variable.getValues();

      // Arrays.asList, not List.of: a stored value array may hold a null element (List.of throws
      // NPE on one, which used to brick every collect_parameters/set_parameters call).
      // Only expose `values` when it is genuinely index-paired with `choices` -- a length
      // mismatch (or either side missing) means the two arrays cannot be correlated by
      // position, and reporting a misaligned `values` would be worse than reporting none.
      List<Object> valuesList = choicesArr != null && valuesArr != null &&
         valuesArr.length == choicesArr.length ? Arrays.asList(valuesArr) : null;

      return new ParameterModel(
         variable.getName(), variable.getAlias(), type, isMultiSelect(variable),
         boundToInputAssembly,
         choicesArr == null ? null : Arrays.asList(choicesArr),
         valuesList,
         currentValue == null ? null : Arrays.asList(currentValue));
   }

   /**
    * Whether the parameter accepts more than one value. A checkboxes variable is stored with
    * {@code multipleSelection=false} (native Composer does the same), so the display style is
    * consulted too -- the same predicate gates {@code set_parameters} and feeds the reported
    * {@code multipleSelection}.
    */
   public static boolean isMultiSelect(UserVariable variable) {
      int style = variable.getDisplayStyle();
      return style == UserVariable.LIST || style == UserVariable.CHECKBOXES ||
         variable.isMultipleSelection() || variable.isUsedInOneOf();
   }

   private final ParameterService parameterService;
}
