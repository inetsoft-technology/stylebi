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

import inetsoft.uql.schema.UserVariable;
import inetsoft.util.CoreTool;
import inetsoft.web.composer.ws.assembly.VariableAssemblyModelInfo;
import inetsoft.web.viewsheet.controller.VSCollectParametersServiceProxy;
import inetsoft.web.viewsheet.event.CollectParametersOverEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code set_parameters}. Applies caller-given values to viewsheet-tree variables and refreshes
 * the sheet -- the same effect as answering StyleBI's own "Parameters" prompt dialog and clicking
 * OK, reusing {@link VSCollectParametersServiceProxy#collectParameters} (the exact production
 * code path that dialog's OK button drives) rather than reimplementing
 * fill-variable-table/reset/refresh here.
 */
@Service
public class ParameterValueService {
   @Autowired
   public ParameterValueService(ViewsheetSessionService sessions,
                                ParameterDiscoveryService discovery,
                                VSCollectParametersServiceProxy vsCollectParametersServiceProxy)
   {
      this.sessions = sessions;
      this.discovery = discovery;
      this.vsCollectParametersServiceProxy = vsCollectParametersServiceProxy;
   }

   public Map<String, Object> setValues(String sessionToken, Principal user,
                                        Map<String, List<Object>> values, String linkUri)
      throws Exception
   {
      if(values == null || values.isEmpty()) {
         throw new IllegalArgumentException(
            "'values' is required and must name at least one parameter -- collect_parameters " +
            "lists what is settable.");
      }

      List<String> applied = new ArrayList<>();

      sessions.mutate(sessionToken, user, (rvs, runtimeId, dispatcher) -> {
         List<UserVariable> discovered = discovery.discover(rvs);
         List<VariableAssemblyModelInfo> variables = new ArrayList<>();

         for(Map.Entry<String, List<Object>> entry : values.entrySet()) {
            UserVariable var = ParameterDiscoveryService.find(discovered, entry.getKey());
            // Must run here, before collectParameters: fillVariableTable's exceptions are
            // swallowed (the call still returns ok), so a bad value would otherwise be stored
            // lossily and silently.
            validate(var, entry.getKey(), entry.getValue());
            // Always build a real Object[], even for a single-element list -- collapsing a
            // size-1 list to its bare element (as this used to) is indistinguishable, once the
            // sole element is null, from "no value at all" to VariableAssemblyModelInfo's
            // two-arg constructor (its null-check runs on the collapsed scalar, not on the
            // list), which then means a caller's `[null]` clear request never reaches
            // fillVariableTable/refreshVariableTable at all and the stale value survives.
            // VariableAssemblyModelInfo already re-wraps a bare scalar into a one-element array
            // for every other case, so this is a no-op for a non-null single value.
            Object value = entry.getValue() == null ? null : entry.getValue().toArray();
            variables.add(new VariableAssemblyModelInfo(var, value));
            applied.add(entry.getKey());
         }

         CollectParametersOverEvent event = CollectParametersOverEvent.builder()
            .variables(variables)
            .disableAudit(false)
            .openVS(false)
            .build();

         vsCollectParametersServiceProxy.collectParameters(runtimeId, event, user, linkUri,
                                                           dispatcher);
      });

      Map<String, Object> result = new LinkedHashMap<>();
      result.put("ok", true);
      result.put("applied", applied);
      return result;
   }

   /**
    * Refuses values that {@code fillVariableTable} would store wrongly instead of rejecting.
    *
    * @throws IllegalArgumentException naming the parameter (and element index) on a null element
    *         in a multi-value array, an {@code __null__} sentinel inside one, or more than one
    *         value for a single-select parameter. A lone {@code [null]} / {@code ["__null__"]}
    *         stays legal: it is the explicit "clear this parameter" request.
    */
   public static void validate(UserVariable var, String name, List<Object> list) {
      if(list == null || list.size() <= 1) {
         return;
      }

      for(int i = 0; i < list.size(); i++) {
         Object element = list.get(i);

         if(element == null || CoreTool.FAKE_NULL.equals(element)) {
            throw new IllegalArgumentException(
               "Parameter '" + name + "' value[" + i + "] is " +
               (element == null ? "null" : "the null sentinel '" + CoreTool.FAKE_NULL + "'") +
               ". A null cannot be mixed into a multi-value array; send [null] alone to clear " +
               "the parameter.");
         }
      }

      if(!ParameterDiscoveryService.isMultiSelect(var)) {
         throw new IllegalArgumentException(
            "Parameter '" + name + "' is single-select but " + list.size() + " values were " +
            "given. Send exactly one value (collect_parameters reports multipleSelection).");
      }
   }

   private final ViewsheetSessionService sessions;
   private final ParameterDiscoveryService discovery;
   private final VSCollectParametersServiceProxy vsCollectParametersServiceProxy;
}
