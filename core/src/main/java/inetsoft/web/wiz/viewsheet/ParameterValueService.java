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
            Object value = entry.getValue() == null ? null
               : entry.getValue().size() == 1 ? entry.getValue().get(0)
               : entry.getValue().toArray();
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

   private final ViewsheetSessionService sessions;
   private final ParameterDiscoveryService discovery;
   private final VSCollectParametersServiceProxy vsCollectParametersServiceProxy;
}
