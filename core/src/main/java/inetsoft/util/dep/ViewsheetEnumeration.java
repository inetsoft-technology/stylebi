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

import inetsoft.uql.asset.AssetEntry;

import java.util.Enumeration;

/**
 * ViewsheetEnumeration implements the XAssetEnumeration interface,
 * generates a series of ViewsheetAssets, one at a time.
 *
 * @version 9.1
 * @author InetSoft Technology Corp
 */
public class ViewsheetEnumeration extends AbstractSheetEnumeration {
   /**
    * Constructor.
    */
   public ViewsheetEnumeration() {
      venum = getEnumeration();
   }

   /**
    * Tests if this enumeration contains more elements.
    * @return <code>true</code> if and only if this enumeration object contains
    * at least one more element to provide; <code>false</code> otherwise.
    */
   @Override
   public boolean hasMoreElements() {
      return venum != null && venum.hasMoreElements();
   }

   /**
    * Returns the next element of this enumeration if this enumeration object
    * has at least one more element to provide.
    * @return the next element of this enumeration.
    */
   @Override
   public XAsset nextElement() {
      return venum.nextElement();
   }

   /**
    * Get folder type, should be AssetEntry.Type.FOLDER here.
    */
   @Override
   protected AssetEntry.Type getFolderType() {
      return AssetEntry.Type.REPOSITORY_FOLDER;
   }

   /**
    * Get selector.
    */
   @Override
   protected AssetEntry.Selector getSelector() {
      return new AssetEntry.Selector(AssetEntry.Type.REPOSITORY_FOLDER,
         AssetEntry.Type.VIEWSHEET);
   }

   /**
    * Get corresponding XAsset class name.
    */
   @Override
   protected String getXAssetClassName() {
      return "inetsoft.util.dep.ViewsheetAsset";
   }

   private Enumeration<XAsset> venum;
}