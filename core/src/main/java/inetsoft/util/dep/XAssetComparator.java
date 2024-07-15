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

import inetsoft.util.Tool;

import java.util.*;

/**
 * A comparator compares assets in a dependencies list.
 *
 * @version 9.1
 * @author InetSoft Technology Corp
 */
public class XAssetComparator implements Comparator<XAsset> {
   /**
    * Constructor.
    * @param dependencies the dependencies list.
    */
   public XAssetComparator(List<XAssetDependency> dependencies) {
      this.dependencies = dependencies;
   }

   /**
    * Compare two XAsset nodes.
    */
   @Override
   public int compare(XAsset a1, XAsset a2) {
      int result = checkDependency(a1, a2);

      if(result != 0) {
         return result;
      }

      result = getTypePriority(a1.getType()) - getTypePriority(a2.getType());

      if(result != 0) {
         return result;
      }

      result = Tool.compare(a1.getUser(), a2.getUser(), false, true);

      if(result != 0) {
         return result;
      }

      return Tool.compare(a1.getPath(), a2.getPath(), false, true);
   }

   /**
    * Check dependency.
    * @return <tt>1</tt> if v1 is depended on by v2, or <tt>1</tt>
    * if v2 is depended on by v1, or <tt>0</tt> if no dependency.
    */
   private int checkDependency(XAsset v1, XAsset v2) {
      if(XAssetUtil.isDependedOn(v1, v2, new ArrayList<>(), dependencies)) {
         return -1;
      }
      else if(XAssetUtil.isDependedOn(v2, v1, new ArrayList<>(), dependencies)) {
         return 1;
      }
      else {
         return 0;
      }
   }

   /**
    * Get the priority of a importing asset according to its type.
    * The smaller the returned number is, the higher priority will be.
    */
   private int getTypePriority(String type) {
      int priority = 0;

      if(VirtualPrivateModelAsset.VPM.equalsIgnoreCase(type)) {
         priority = 1;
      }
      else if(XPartitionAsset.XPARTITION.equalsIgnoreCase(type)) {
         priority = 2;
      }
      else if(XLogicalModelAsset.XLOGICALMODEL.equalsIgnoreCase(type)) {
         priority = 3;
      }
      else if(XDataSourceAsset.XDATASOURCE.equalsIgnoreCase(type)) {
         priority = 4;
      }
      else if(XQueryAsset.XQUERY.equalsIgnoreCase(type)) {
         priority = 5;
      }
      else if(WorksheetAsset.WORKSHEET.equalsIgnoreCase(type)) {
         priority = 6;
      }
      else if(ScriptAsset.SCRIPT.equalsIgnoreCase(type)) {
         priority = 7;
      }
      else if(TableStyleAsset.TABLESTYLE.equalsIgnoreCase(type)) {
         priority = 8;
      }
      else if(DeviceAsset.DEVICE.equalsIgnoreCase(type)) {
         priority = 10;
      }
      else if(ViewsheetAsset.VIEWSHEET.equalsIgnoreCase(type)) {
         priority = 11;
      }
      else if(DataCycleAsset.DATACYCLE.equalsIgnoreCase(type)) {
         priority = 12;
      }
      else if(VSSnapshotAsset.VSSNAPSHOT.equalsIgnoreCase(type)) {
         priority = 16;
      }
      else if(DashboardAsset.DASHBOARD.equalsIgnoreCase(type)) {
         priority = 17;
      }
      else if(ScheduleTaskAsset.SCHEDULETASK.equalsIgnoreCase(type)) {
         priority = 19;
      }

      return priority;
   }

   private final List<XAssetDependency> dependencies;
}