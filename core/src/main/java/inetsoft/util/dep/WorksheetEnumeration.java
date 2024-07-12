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

import java.util.Enumeration;

/**
 * WorksheetEnumeration implements the XAssetEnumeration interface,
 * generates a series of WorksheetAssets, one at a time.
 *
 * @version 9.1
 * @author InetSoft Technology Corp
 */
public class WorksheetEnumeration extends AbstractSheetEnumeration {
   /**
    * Constructor.
    */
   public WorksheetEnumeration() {
      wenum = getEnumeration();
   }

   @Override
   protected boolean entryExpected(AssetEntry entry) {
      if(entry.getParentPath() != null && "Recycle Bin".equals(entry.getParentPath())) {
         return false;
      }

      return super.entryExpected(entry);
   }

   /**
    * Tests if this enumeration contains more elements.
    * @return <code>true</code> if and only if this enumeration object contains
    * at least one more element to provide; <code>false</code> otherwise.
    */
   @Override
   public boolean hasMoreElements() {
      return wenum != null && wenum.hasMoreElements();
   }

   /**
    * Returns the next element of this enumeration if this enumeration object
    * has at least one more element to provide.
    * @return the next element of this enumeration.
    */
   @Override
   public XAsset nextElement() {
      return wenum.nextElement();
   }

   /**
    * Get folder type, should be AssetEntry.Type.FOLDER here.
    */
   @Override
   protected AssetEntry.Type getFolderType() {
      return AssetEntry.Type.FOLDER;
   }

   /**
    * Get selector.
    */
   @Override
   protected AssetEntry.Selector getSelector() {
      return new AssetEntry.Selector(AssetEntry.Type.FOLDER, AssetEntry.Type.WORKSHEET);
   }

   /**
    * Get corresponding XAsset class name.
    */
   @Override
   protected String getXAssetClassName() {
      return "inetsoft.util.dep.WorksheetAsset";
   }

   private Enumeration<XAsset> wenum;
}