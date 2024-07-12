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
package inetsoft.report.composition;

import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.util.Tool;

/**
 * Asset repository event, the asset event requires an asset repository as the
 * context.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public abstract class AssetRepositoryEvent extends AssetEvent {
   /**
    * Constructor.
    */
   public AssetRepositoryEvent() {
      super();
   }

   /**
    * Get the asset entry.
    * @return the asset entry of the asset repository event.
    */
   public AssetEntry getAssetEntry() {
      return (AssetEntry) get(ASSET_ENTRY);
   }

   /**
    * Set the asset entry to the asset repository event.
    * @param entry the specified asset entry.
    */
   public void setAssetEntry(AssetEntry entry) {
      put(ASSET_ENTRY, entry);
   }

   /**
    * Check if equals another object.
    * @param obj the specified object.
    * @return <tt>true</tt> if equals, <tt>false</tt> otherwise.
    */
   public boolean equals(Object obj) {
      if(!super.equals(obj)) {
         return false;
      }

      AssetEntry entry = getAssetEntry();
      AssetRepositoryEvent event2 = (AssetRepositoryEvent) obj;
      AssetEntry entry2 = event2.getAssetEntry();

      return Tool.equals(entry, entry2);
   }

   /**
    * Get the hash code.
    * @return the hash code of the asset event.
    */
   public int hashCode() {
      int hash = super.hashCode();
      AssetEntry entry = getAssetEntry();

      if(entry != null) {
         hash = hash ^ entry.hashCode();
      }

      return hash;
   }

   /**
    * Process this asset repository event.
    * @param engine the specified asset repository.
    */
   public abstract void process(AssetRepository engine, AssetCommand command)
      throws Exception;

   private static final String ASSET_ENTRY = "asset_entry";
}
