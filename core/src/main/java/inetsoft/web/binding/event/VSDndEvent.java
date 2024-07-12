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
package inetsoft.web.binding.event;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.SourceInfo;
import inetsoft.uql.erm.DataRef;
import inetsoft.web.binding.dnd.DataTransfer;
import inetsoft.web.binding.dnd.DropTarget;
import inetsoft.web.viewsheet.event.ViewsheetEvent;
import org.immutables.value.Value;

import javax.annotation.Nullable;

@Value.Immutable
@JsonSerialize(as = ImmutableVSDndEvent.class)
@JsonDeserialize(as = ImmutableVSDndEvent.class)
public interface VSDndEvent extends ViewsheetEvent {
   /**
    * Get the dnd transfer.
    * @return dnd transfer.
    */
   @Nullable
   DataTransfer getTransfer();
   /**
    * Get the drop target.
    * @return drop target.
    */
   @Nullable
   DropTarget getDropTarget();
   /**
    * Get the drop target.
    * @return drop target.
    */
   @Nullable
   AssetEntry[] getEntries();

   /**
    * Get the table name.
    * @return table name.
    */
   @Nullable
   String getTable();

   /**
    * Get the trap tag.
    * @return trap tag.
    */
   boolean checkTrap();

   /**
    * Get the source change tag.
    * @return source change tag.
    */
   boolean sourceChanged();

   @JsonIgnore
   default int getSourceType() {
      int sourceType = SourceInfo.ASSET;
      AssetEntry[] entries = getEntries();

      if(entries != null && entries.length > 0) {
         String ptype = entries[0].getProperty("type");

         if(ptype != null) {
            try {
               sourceType = Integer.parseInt(ptype);
            }
            catch(NumberFormatException e) {
            }
         }
      }

      return isCubeSource(sourceType) ? SourceInfo.ASSET : sourceType;
   }

   @JsonIgnore
   default boolean isCubeSource(int sourceType) {
      return sourceType == DataRef.CUBE
         || sourceType == DataRef.CUBE_DIMENSION
         || sourceType == DataRef.CUBE_MEASURE
         || sourceType == DataRef.CUBE_MODEL_DIMENSION
         || sourceType == DataRef.CUBE_MODEL_TIME_DIMENSION
         || sourceType == DataRef.CUBE_TIME_DIMENSION;
   }

   public static ImmutableVSDndEvent.Builder builder() {
      return ImmutableVSDndEvent.builder();
   }
}
