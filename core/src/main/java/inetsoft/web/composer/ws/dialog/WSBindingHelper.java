/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.web.composer.ws.dialog;

import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.sree.AnalyticRepository;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.*;
import inetsoft.uql.util.XSourceInfo;

import java.rmi.RemoteException;

public class WSBindingHelper {

    public static String getModelDefaultFormula(BoundTableAssembly assembly, ColumnRef ref,
                                                RuntimeWorksheet rws,
                                                AnalyticRepository analyticRepository)
          throws RemoteException
    {
       SourceInfo sourceInfo = assembly.getSourceInfo();

       if(sourceInfo != null && sourceInfo.getType() == XSourceInfo.MODEL){
          XLogicalModel lm = analyticRepository.getLogicalModel(sourceInfo.getSource()
             + "::" + sourceInfo.getPrefix(), rws.getUser());

          if(lm != null) {
             String name = ref.getName().replace(".", ":");
             String[] names = name.split(":");

             if(names.length == 2) {
                XEntity entity = lm.getEntity(names[0]);

                if(entity != null) {
                      XAttribute attr = entity.getAttribute(names[1]);

                   if(attr != null) {
                      String formula = attr.getDefaultFormula();
                      return "None".equalsIgnoreCase(formula) ? null : formula;
                   }
                }
             }
          }
       }

       return null;
    }
}
