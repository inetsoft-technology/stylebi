/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
package inetsoft.web.composer;

import inetsoft.uql.asset.AssetEntry;

import java.io.Serializable;

/**
 * Cluster message that forwards an asset change, made on one node, to the
 * {@link AssetTreeRefreshController} of the other nodes, so that the asset tree of a user
 * connected to another node is refreshed too.
 */
public final class AssetTreeChangeMessage implements Serializable {
   public AssetTreeChangeMessage(int entryType, int changeType, AssetEntry assetEntry,
                                 String oldName)
   {
      this.entryType = entryType;
      this.changeType = changeType;
      this.assetEntry = assetEntry;
      this.oldName = oldName;
   }

   /**
    * Gets the type of entry to which the change was made.
    */
   public int getEntryType() {
      return entryType;
   }

   /**
    * Gets the type of change, one of the <tt>AssetChangeEvent</tt> constants.
    */
   public int getChangeType() {
      return changeType;
   }

   /**
    * Gets the changed entry.
    */
   public AssetEntry getAssetEntry() {
      return assetEntry;
   }

   /**
    * Gets the old identifier of the entry, or <tt>null</tt> if not renamed.
    */
   public String getOldName() {
      return oldName;
   }

   @Override
   public String toString() {
      return "AssetTreeChangeMessage{" +
         "entryType=" + entryType +
         ", changeType=" + changeType +
         ", assetEntry=" + assetEntry +
         ", oldName='" + oldName + '\'' +
         '}';
   }

   private final int entryType;
   private final int changeType;
   private final AssetEntry assetEntry;
   private final String oldName;
}
