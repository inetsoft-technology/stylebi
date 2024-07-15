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
package inetsoft.web.binding.handler;

import inetsoft.analytic.composition.event.VSEventUtil;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.asset.*;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.CalcTableVSAssemblyInfo;
import org.springframework.stereotype.Component;

import java.security.Principal;

@Component
public class VSTableDataHandler {
   /**
    * ConvertTableRefEvent.
    **/
   public void convertTableRef(AssetRepository engine, RuntimeViewsheet rvs, String name,
      String refName, int convertType, boolean sourceChange, SourceInfo sinfo,
      Principal principal) throws Exception
   {
      Viewsheet vs = rvs.getViewsheet();
      VSAssembly ass = vs == null ? null : (VSAssembly) vs.getAssembly(name);

      if(vs == null || ass == null) {
         return;
      }

      vs = ass.getViewsheet();
      AggregateInfo ainfo = null;

      if(ass instanceof CrosstabVSAssembly) {
         VSCrosstabInfo cinfo = ((CrosstabVSAssembly) ass).getVSCrosstabInfo();
         ainfo = cinfo.getAggregateInfo();
      }
      else if(ass instanceof CalcTableVSAssembly) {
         CalcTableVSAssemblyInfo calcInfo =
            (CalcTableVSAssemblyInfo) ass.getVSAssemblyInfo();
         ainfo = calcInfo.getAggregateInfo();
      }

      ainfo = sourceChange ? null : ainfo;

      // no aggregate info? create a default aggregate info
      if(ainfo == null || ainfo.isEmpty()) {
         ainfo = createAggregateInfo(engine, vs, name, sinfo, principal);
      }

      ((TableDataVSAssembly) ass).setSourceInfo(sinfo);

      VSEventUtil.fixAggInfoByConvertRef(ainfo, convertType, refName);
   }

   /**
    * CreateAggregateInfoEvent.
    **/
   private AggregateInfo createAggregateInfo(AssetRepository engine, Viewsheet vs,
      String name, SourceInfo sinfo, Principal principal)
   {
      TableAssembly tbl = VSEventUtil.getTableAssembly(vs, sinfo, engine, principal);

      if(tbl == null) {
         return null;
      }

      AggregateInfo nainfo = new AggregateInfo();
      // create default aggregte info, the old aggregate info is null
      VSEventUtil.createAggregateInfo(tbl, nainfo, null, vs, true);

      return nainfo;
   }
}
