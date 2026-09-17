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
      return discovered.stream()
         .filter(v -> v.getName().equals(name))
         .findFirst()
         .orElseThrow(() -> new IllegalArgumentException(
            "'" + name + "' is not a parameter this viewsheet's source or viewsheet tree " +
            "references (or it is already bound to an on-canvas input assembly -- use " +
            "set_input_value for that). collect_parameters lists what is settable here."));
   }

   /** Maps one discovered variable to its agent-facing JSON shape. Pure, no I/O -- unit-tested. */
   public static ParameterModel toModel(UserVariable variable, boolean boundToInputAssembly,
                                        Object[] currentValue)
   {
      String type = variable.getTypeNode() != null ? variable.getTypeNode().getType() : "string";
      Object[] choicesArr = variable.getChoices();

      return new ParameterModel(
         variable.getName(), variable.getAlias(), type, variable.isMultipleSelection(),
         boundToInputAssembly,
         choicesArr == null ? null : List.of(choicesArr),
         currentValue == null ? null : List.of(currentValue));
   }

   private final ParameterService parameterService;
}
