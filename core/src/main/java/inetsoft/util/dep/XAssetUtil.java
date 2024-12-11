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
package inetsoft.util.dep;

import inetsoft.sree.security.OrganizationManager;
import inetsoft.uql.asset.AssetEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * XAssetUtil provides utility methods for XAsset operations.
 *
 * @version 9.1
 * @author InetSoft Technology Corp
 */
public class XAssetUtil {
   /**
    * Create an XAsset by identifier.
    * @param identifier the specified asset identifier.
    * @return the created XAsset.
    */
   public static XAsset createXAsset(String identifier) {
      int idx = identifier.indexOf("^");
      String className = idx < 0 ? identifier : identifier.substring(0, idx);

      try {
         XAsset asset = (XAsset) Class.forName(className).newInstance();
         asset.parseIdentifier(identifier);
         return asset;
      }
      catch(Exception ex) {
         LOG.error("Failed to create asset: " + identifier, ex);
         return null;
      }
   }

   public static XAsset createXAsset(String identifier, boolean ignoreOrgId) {
      XAsset xAsset = createXAsset(identifier);

      if(!ignoreOrgId) {
         return xAsset;
      }

      if(xAsset instanceof AbstractSheetAsset) {
         AbstractSheetAsset sheetAsset = (AbstractSheetAsset) xAsset;
         AssetEntry assetEntry = sheetAsset.getAssetEntry();
         ((AbstractSheetAsset) xAsset).setAssetEntry(assetEntry.cloneAssetEntry(OrganizationManager.getInstance().getCurrentOrgID(), ""));
      }

      return xAsset;
   }

   /**
    * Get XAsset types.
    * @return a list of asset types.
    */
   public static List<String> getXAssetTypes(boolean includeAutoSave) {
      List<String> list = new ArrayList<>();
      list.add(XDataSourceAsset.XDATASOURCE);
      list.add(XLogicalModelAsset.XLOGICALMODEL);
      list.add(XPartitionAsset.XPARTITION);
      list.add(VirtualPrivateModelAsset.VPM);
      list.add(XQueryAsset.XQUERY);
      list.add(WorksheetAsset.WORKSHEET);
      list.add(TableStyleAsset.TABLESTYLE);
      list.add(ScriptAsset.SCRIPT);
      list.add(DeviceAsset.DEVICE);
      list.add(ViewsheetAsset.VIEWSHEET);

      if(includeAutoSave) {
         list.add(VSAutoSaveAsset.AUTOSAVEVS);
         list.add(WSAutoSaveAsset.AUTOSAVEWS);
      }

      list.add(DataCycleAsset.DATACYCLE);
      list.add(VSSnapshotAsset.VSSNAPSHOT);
      list.add(DashboardAsset.DASHBOARD);
      list.add(ScheduleTaskAsset.SCHEDULETASK);

      return list;
   }

   /**
    * Check if v1 is depended on by v2.
    * @param v1 the specified asset a.
    * @param v2 the specified asset b.
    * @param dependencies the specified dependencies.
    * @return <tt>true</tt> if v1 is depended on by v2, <tt>false</tt>
    * otherwise.
    */
   public static boolean isDependedOn(XAsset v1, XAsset v2, List<XAssetDependency> list,
                                      List<XAssetDependency> dependencies)
   {
      // use a list to avoid endless recursive check, if they have been checked,
      // don't check recursively
      list.add(new XAssetDependency(v1, v2, 1, "test"));

      for(int i = 0; i < dependencies.size(); i++) {
         XAssetDependency dependency = dependencies.get(i);
         XAsset depended = dependency.getDependedXAsset();
         XAsset depending = dependency.getDependingXAsset();

         if(!depended.equals(v1)) {
            continue;
         }

         if(depending.equals(v2)) {
            return true;
         }
         else if(list.contains(new XAssetDependency(depending, v2, 1, "test")))
         {
            continue;
         }
         else if(isDependedOn(depending, v2, list, dependencies)) {
            return true;
         }
      }

      return false;
   }

   private static final Logger LOG =
      LoggerFactory.getLogger(XAssetUtil.class);
}
