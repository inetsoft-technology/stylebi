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
import inetsoft.uql.schema.UserVariable;
import inetsoft.web.wiz.viewsheet.model.ParameterModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * {@code collect_parameters}. Lists the variables the connected viewsheet's source query and
 * viewsheet tree reference, with each one's current value if the live session has one -- see
 * {@link ParameterDiscoveryService} for what is and is not included.
 */
@Service
public class ParameterCollectionService {
   @Autowired
   public ParameterCollectionService(ViewsheetSessionService sessions,
                                     ParameterDiscoveryService discovery)
   {
      this.sessions = sessions;
      this.discovery = discovery;
   }

   public List<ParameterModel> list(String sessionToken, Principal user) throws Exception {
      RuntimeViewsheet rvs = sessions.resolve(sessionToken, user);
      List<UserVariable> discovered = discovery.discover(rvs);
      VariableTable live = liveVariableTable(rvs);
      List<ParameterModel> out = new ArrayList<>();

      for(UserVariable var : discovered) {
         Object current = live == null ? null : live.get(var.getName());
         Object[] currentArr = current == null ? null
            : current instanceof Object[] ? (Object[]) current : new Object[]{current};
         out.add(ParameterDiscoveryService.toModel(var, false, currentArr));
      }

      return out;
   }

   private VariableTable liveVariableTable(RuntimeViewsheet rvs) {
      Optional<ViewsheetSandbox> box =
         rvs == null ? Optional.empty() : rvs.getViewsheetSandbox();

      if(box.isEmpty() || box.get().getAssetQuerySandbox() == null) {
         return null;
      }

      return box.get().getAssetQuerySandbox().getVariableTable();
   }

   private final ViewsheetSessionService sessions;
   private final ParameterDiscoveryService discovery;
}
