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
            updateHyperlink(info.getHyperlinkValue(), norg, oorg);
         }
      }
   }

   private static void updateTableHyperlinkAttr(TableDataVSAssemblyInfo info,
                                                Organization oorg, Organization norg)
   {
      TableHyperlinkAttr hyperlinkAttr = info.getHyperlinkAttr();
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
      String newIdentify = getNewIdentify(link, oorg, norg);

      if(!Tool.equals(link, newIdentify)) {
         hyperlink.setLink(newIdentify);
      }

      String bookmarkUser = hyperlink.getBookmarkUser();

      if(bookmarkUser != null) {
         IdentityID identityID = IdentityID.getIdentityIDFromKey(bookmarkUser);

         if(!Tool.equals(oorg.getName(), norg.getName())) {
            identityID.setOrganization(norg.getName());
         }

         String newIdentity = identityID.convertToKey();

         if(!Tool.equals(bookmarkUser, newIdentity)) {
            hyperlink.setBookmarkUser(newIdentify);
         }
      }
   }

   public static String getNewIdentify(String oidentify, Organization oorg, Organization norg) {
      String oId = oorg.getId();
      String nId = norg.getId();
      String oname = oorg.getName();
      String nname = norg.getName();
      AssetEntry assetEntry = AssetEntry.createAssetEntry(oidentify);
      assetEntry = !Tool.equals(oId, nId) ? assetEntry.cloneAssetEntry(nId) : assetEntry;
      IdentityID user = assetEntry.getUser();

      if(user != null && Tool.equals(oname, nname)) {
         user.setOrganization(nname);
      }

      return assetEntry.toIdentifier(true);
   }
}
