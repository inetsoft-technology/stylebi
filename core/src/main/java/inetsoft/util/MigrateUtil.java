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

package inetsoft.util;

import inetsoft.report.Hyperlink;
import inetsoft.report.TableDataPath;
import inetsoft.report.internal.table.TableHyperlinkAttr;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.Organization;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.viewsheet.VSDataRef;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.*;

import java.util.Map;

import static inetsoft.sree.security.IdentityID.KEY_DELIMITER;

public class MigrateUtil {
   public static void updateAllAssemblyHyperlink(Viewsheet viewsheet, Organization oorg, Organization norg) {
      Assembly[] assemblies = viewsheet.getAssemblies();

      for(Assembly assembly : assemblies) {
         VSAssemblyInfo info = (VSAssemblyInfo) assembly.getInfo();

         if(info instanceof TableVSAssemblyInfo) {
            MigrateUtil.updateTableHyperlinkAttr(((TableDataVSAssemblyInfo) info), oorg, norg);
            TableVSAssemblyInfo tableVSAssemblyInfo = (TableVSAssemblyInfo) info;
            Hyperlink rowHyperlink = tableVSAssemblyInfo.getRowHyperlink();
            updateHyperlink(rowHyperlink, oorg, norg);
         }
         else if(info instanceof TableDataVSAssemblyInfo) {
            MigrateUtil.updateTableHyperlinkAttr(((TableDataVSAssemblyInfo) info), oorg, norg);
         }
         else if(info instanceof ChartVSAssemblyInfo) {
            ChartVSAssemblyInfo chartVSAssemblyInfo = (ChartVSAssemblyInfo) info;
            VSChartInfo vsChartInfo = chartVSAssemblyInfo.getVSChartInfo();
            VSDataRef[] bindingRefs = vsChartInfo.getBindingRefs(true);

            for(VSDataRef dataRef : bindingRefs) {
               if(dataRef instanceof VSChartDimensionRef) {
                  VSChartDimensionRef dimensionRef = (VSChartDimensionRef) dataRef;
                  updateHyperlink(dimensionRef.getHyperlink(), oorg, norg);
               }
               else if(dataRef instanceof VSChartAggregateRef) {
                  VSChartAggregateRef aggregateRef = (VSChartAggregateRef) dataRef;
                  updateHyperlink(aggregateRef.getHyperlink(), oorg, norg);
               }
            }
         }
         else {
            updateHyperlink(info.getHyperlinkValue(), oorg, norg);
         }
      }
   }

   private static void updateTableHyperlinkAttr(TableDataVSAssemblyInfo info,
                                                Organization oorg, Organization norg)
   {
      TableHyperlinkAttr hyperlinkAttr = info.getHyperlinkAttr();

      if(hyperlinkAttr == null) {
         return;
      }

      Map<TableDataPath, Hyperlink> hyperlinkMap = hyperlinkAttr.getHyperlinkMap();

      if(hyperlinkMap == null || hyperlinkMap.isEmpty()) {
         return;
      }

      for(TableDataPath tableDataPath : hyperlinkMap.keySet()) {
         Hyperlink hyperlink = hyperlinkMap.get(tableDataPath);
         updateHyperlink(hyperlink, oorg, norg);
      }
   }

   private static void updateHyperlink(Hyperlink hyperlink, Organization oorg, Organization norg) {
      if(hyperlink == null || hyperlink.getLinkType() != Hyperlink.VIEWSHEET_LINK) {
         return;
      }

      String link = hyperlink.getDLink().getDValue();
      String newIdentify = getNewIdentifier(link, norg);

      if(!Tool.equals(link, newIdentify)) {
         hyperlink.setLink(newIdentify);
      }

      String bookmarkUser = hyperlink.getBookmarkUser();

      if(bookmarkUser != null) {
         IdentityID identityID = IdentityID.getIdentityIDFromKey(bookmarkUser);

         String newIdentity = identityID.convertToKey();

         if(!Tool.equals(bookmarkUser, newIdentity)) {
            hyperlink.setBookmarkUser(newIdentify);
         }
      }
   }

   public static String getNewIdentifier(String oidentifier, Organization norg) {
      AssetEntry assetEntry = AssetEntry.createAssetEntry(oidentifier).cloneAssetEntry(norg);

      return assetEntry.toIdentifier(true);
   }

   public static String getNewOrgTaskName(String taskName, String oorgID, String norgID) {
      if(Tool.equals(norgID, oorgID)) {
         return taskName;
      }

      int index = taskName.indexOf(":");

      if(index > 0) {
         String name = taskName.substring(0, index);

         if(!name.contains(IdentityID.KEY_DELIMITER)) {
            return taskName;
         }

         IdentityID identityID = IdentityID.getIdentityIDFromKey(taskName.substring(0, index));

         if(Tool.equals(oorgID, identityID.orgID)) {
            identityID.setOrgID(norgID);
            taskName = identityID.convertToKey() + taskName.substring(index);
         }
      }

      return taskName;
   }

   public static String getNewUserTaskName(String taskName, String oName, String nName) {
      if(Tool.equals(oName, nName)) {
         return taskName;
      }

      String[] split = taskName.split(":");

      if(split.length == 2) {
         IdentityID identityID = IdentityID.getIdentityIDFromKey(split[0]);

         if(Tool.equals(oName, identityID.name)) {
            identityID.setName(nName);
            taskName = identityID.convertToKey() + ":" + split[1];
         }
      }

      return taskName;
   }
}
