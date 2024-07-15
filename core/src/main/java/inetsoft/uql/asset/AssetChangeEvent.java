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
package inetsoft.uql.asset;

import java.util.EventObject;

/**
 * Describe class AssetChangeEvent here.
 *
 * @author InetSoft Technology
 * @since  8.5
 */
public class AssetChangeEvent extends EventObject {
   /**
    * Change type that indicates that an entry was changed.
    */
   public static final int ASSET_MODIFIED = 1;

   /**
    * Change type that indicates that an entry was renamed.
    */
   public static final int ASSET_RENAMED = 2;

   /**
    * Change type that indicates that an entry was deleted.
    */
   public static final int ASSET_DELETED = 3;

   /**
    * Change type that indicates that an entry was to be deleted.
    */
   public static final int ASSET_TO_BE_DELETED = 4;
   
   /**
    * Change type that indicates that an auto save is recycle.
    */
   public static final int AUTO_SAVE_ADD = 5;

   /**
    * Creates a new <code>AssetChangeEvent</code> instance.
    *
    * @param source the object on which the event originally occurred.
    * @param entryType the type of entry to which the change was made.
    * @param changeType the type of change that was made to the entry.
    * @param assetEntry the modified asset entry.
    * @param oldName the old name of the entry, or <code>null</code> if the
    *                entry was not renamed.
    */
   public AssetChangeEvent(Object source, int entryType, int changeType,
                           AssetEntry assetEntry, String oldName, boolean root,
                           AbstractSheet sheet, String reason)
   {
      super(source);

      this.entryType = entryType;
      this.changeType = changeType;
      this.assetEntry = assetEntry;
      this.oldName = oldName;
      this.root = root;
      this.sheet = sheet;
      this.reason = reason;
   }

   /**
    * Gets the type of entry to which the change was made.
    *
    * @return one of the asset type constants defined in {@link AbstractSheet}.
    */
   public int getEntryType() {
      return entryType;
   }

   /**
    * Gets the type of change that was made.
    *
    * @return one of the change type constants defined in this class.
    */
   public int getChangeType() {
      return changeType;
   }

   /**
    * Gets the asset entry that was changed.
    *
    * @return the modified asset entry.
    */
   public AssetEntry getAssetEntry() {
      return assetEntry;
   }

   /**
    * Gets the old name of the entry.
    *
    * @return the original name, or <code>null</code> if the entry was not
    *         renamed.
    */
   public String getOldName() {
      return oldName;
   }

   /**
    * Gets whether the entry changed was the source cause of the changed asset.
    *
    * @return true if this changed asset is the root change, false otherwise
    */
   public boolean isRoot() {
      return root;
   }

   /**
    * Get the sheet if this asset is a worksheet or viewsheet.
    */
   public AbstractSheet getSheet() {
      return sheet;
   }

   /**
    * Get the description of this change.
    */
   public String getReason() {
      return reason;
   }

   @Override
   public String toString() {
      return "AssetChangeEvent{" +
         "entryType=" + entryType +
         ", changeType=" + changeType +
         ", assetEntry=" + assetEntry +
         ", oldName='" + oldName + '\'' +
         ", root=" + root +
         ", reason=" + reason +
         '}';
   }

   private int entryType = 0;
   private int changeType = 0;
   private AssetEntry assetEntry = null;
   private String oldName = null;
   private boolean root;
   private AbstractSheet sheet;
   private String reason;
}
