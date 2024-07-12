/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.util.dep;

import inetsoft.util.Tool;

import java.util.Enumeration;
import java.util.stream.Stream;

/**
 * An object that implements the XAssetEnumeration interface generates a series
 * of elements, one at a time.
 *
 * @version 9.1
 * @author InetSoft Technology Corp
 */
public interface XAssetEnumeration<T extends XAsset> extends Enumeration<T> {
   /**
    * Get XAssetEnumeration of the specified type.
    * @param type the specified asset type.
    * @return the corresponding enumeration for the specifed asset type.
    */
   static XAssetEnumeration getXAssetEnumeration(String type) {
      switch(type) {
      case DashboardAsset.DASHBOARD:
         return new DashboardEnumeration();
     case ScheduleTaskAsset.SCHEDULETASK:
         return new ScheduleTaskEnumeration();
      case TableStyleAsset.TABLESTYLE:
         return new TableStyleEnumeration();
      case ScriptAsset.SCRIPT:
         return new ScriptEnumeration();
      case DeviceAsset.DEVICE:
         return new DeviceEnumeration();
      case ViewsheetAsset.VIEWSHEET:
         return new ViewsheetEnumeration();
      case VSSnapshotAsset.VSSNAPSHOT:
         return new VSSnapshotEnumeration();
      case WorksheetAsset.WORKSHEET:
         return new WorksheetEnumeration();
      case XDataSourceAsset.XDATASOURCE:
         return new XDataSourceEnumeration();
      case DataCycleAsset.DATACYCLE:
         return new DataCycleEnumeration();
      case XLogicalModelAsset.XLOGICALMODEL:
         return new XLogicalModelEnumeration();
      case XPartitionAsset.XPARTITION:
         return new XPartitionEnumeration();
      case VirtualPrivateModelAsset.VPM:
         return new VirtualPrivateModelEnumeration();
      case VSAutoSaveAsset.AUTOSAVEVS:
         return new VSAutoSaveEnumeration();
      case WSAutoSaveAsset.AUTOSAVEWS:
         return new WSAutoSaveEnumeration();
      default:
         throw new RuntimeException("Invalid XAsset type found: " + type);
      }
   }

   /**
    * Tests if this enumeration contains more elements.
    * @return <code>true</code> if and only if this enumeration object contains
    * at least one more element to provide; <code>false</code> otherwise.
    */
   boolean hasMoreElements();

   /**
    * Returns the next element of this enumeration if this enumeration object
    * has at least one more element to provide.
    * @return the next element of this enumeration.
    */
   T nextElement();

   default Stream<T> stream() {
      return Tool.toStream(new Enumeration<T>() {
         @Override
         public boolean hasMoreElements() {
            return XAssetEnumeration.this.hasMoreElements();
         }

         @Override
         public T nextElement() {
            return XAssetEnumeration.this.nextElement();
         }
      });
   }
}