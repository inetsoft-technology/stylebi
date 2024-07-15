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
package inetsoft.report.composition;

import inetsoft.analytic.composition.ViewsheetEvent;
import inetsoft.analytic.composition.event.VSRefreshEvent;
import inetsoft.uql.asset.SourceInfo;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.ScalarBindingInfo;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.Tool;

import java.util.HashSet;

/**
 * Model context for viewsheet.
 *
 * @version 10.3
 * @author InetSoft Technology Corp
 */
public class VSModelTableContext extends VSModelContext {
   /**
    * Constructor.
    */
   public VSModelTableContext(RuntimeViewsheet rvs) {
      super(rvs);
   }

   /**
    * Process.
    */
   public boolean process(VSAssemblyInfo oinfo, VSAssemblyInfo info,
      ViewsheetEvent event, AssetCommand command) throws Exception
   {
      String oname = getTableName(oinfo);
      String nname = getTableName(info);

      // @by cehnw, change from unbind to a bind assembly should be refresh
      if(!Tool.equals(oname, nname) && oname != null && nname != null) {
         return false;
      }

      int ocnt = getTableCount(oinfo);
      int ncnt = getTableCount(info);

      if(ocnt == ncnt) {
         return false;
      }

      return process(event, command);
   }

   /**
    * Process.
    */
   public boolean process(ViewsheetEvent event, AssetCommand command)
      throws Exception
   {

      VSRefreshEvent evt = new VSRefreshEvent();
      evt.put("initing", "false");
      evt.setID(event.getID());
      evt.setLinkURI(event.getLinkURI());
      evt.process(rvs, command);
      event.addConfirmExceptions(evt);
      return true;
   }

   /**
    * Get table count.
    */
   public int getTableCount() {
      return getTableCount(null);
   }

   /**
    * Get table count.
    */
   public int getTableCount(VSAssemblyInfo info) {
      HashSet<DataRef> all = new HashSet<>();
      getAllAttributes(info, all, new HashSet<>());
      DataRef[] allAttributes = new DataRef[all.size()];
      all.toArray(allAttributes);

      return getTables(allAttributes).length;
   }

   /**
    * Get table name.
    */
   private String getTableName(VSAssemblyInfo info) {
      String tableName = null;

      if(info instanceof DataVSAssemblyInfo) {
         DataVSAssemblyInfo dinfo = (DataVSAssemblyInfo) info;
         SourceInfo sinfo = dinfo.getSourceInfo();

         if(sinfo != null) {
            tableName = sinfo.getSource();
         }
      }
      else if(info instanceof OutputVSAssemblyInfo) {
         ScalarBindingInfo sbinfo =
            ((OutputVSAssemblyInfo) info).getScalarBindingInfo();

         if(sbinfo != null) {
            tableName = sbinfo.getTableName();
         }
      }

      return tableName;
   }
}
