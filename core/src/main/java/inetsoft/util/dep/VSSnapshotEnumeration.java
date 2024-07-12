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

import inetsoft.uql.asset.AssetEntry;

/**
 * VSSnapshotEnumeration implements the XAssetEnumeration interface,
 * generates a series of VSSnapshotAssets, one at a time.
 *
 * @version 9.1
 * @author InetSoft Technology Corp
 */
public class VSSnapshotEnumeration extends ViewsheetEnumeration {
   /**
    * Constructor.
    */
   public VSSnapshotEnumeration() {
      super();
   }

   /**
    * Check if is the expected entry type.
    */
   @Override
   protected boolean entryExpected(AssetEntry entry) {
      return entry.isVSSnapshot();
   }

   /**
    * Get selector.
    */
   @Override
   protected AssetEntry.Selector getSelector() {
      return new AssetEntry.Selector(
         AssetEntry.Type.REPOSITORY_FOLDER, AssetEntry.Type.VIEWSHEET_SNAPSHOT);
   }

   /**
    * Get corresponding XAsset class name.
    */
   @Override
   protected String getXAssetClassName() {
      return "inetsoft.util.dep.VSSnapshotAsset";
   }
}