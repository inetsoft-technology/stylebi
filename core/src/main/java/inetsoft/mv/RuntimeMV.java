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
package inetsoft.mv;

import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.viewsheet.Viewsheet;

import java.io.Serializable;
import java.lang.ref.WeakReference;
import java.util.List;

/**
 * RuntimeMV, the runtime mv information.
 *
 * @version 10.2
 * @author InetSoft Technology Corp
 */
public final class RuntimeMV implements Serializable {
   /**
    * Create an instanceof RuntimeMV.
    * @param entry the specified asset entry.
    * @param vs the specified viewsheet.
    * @param vassembly the specified viewsheet assembly.
    * @param otable the specified bound table assembly.
    * @param mv null if not physical mv but logical mv.
    */
   public RuntimeMV(AssetEntry entry, Viewsheet vs, String vassembly,
                    String otable, String mv, boolean sub, long time,
                    List<String> parentVsIds)
   {
      super();

      this.entry = entry;
      this.vsref = new WeakReference(vs);
      this.vsassembly = vassembly;
      this.otable = otable;
      this.mv = mv;
      this.sub = sub;
      this.time = time;
      this.parentVsIds = parentVsIds;
   }

   /**
    * Get the asset entry.
    */
   public AssetEntry getEntry() {
      return entry;
   }

   /**
    * Get the viewsheet.
    */
   public Viewsheet getViewsheet() {
      return (Viewsheet) vsref.get();
   }

   /**
    * Get the viewsheet assembly.
    */
   public String getVSAssembly() {
      return vsassembly;
   }

   /**
    * Get the bound table.
    */
   public String getBoundTable() {
      return otable;
   }

   /**
    * Get the target mv.
    */
   public String getMV() {
      return mv;
   }

   /**
    * Check if this runtime mv is physical mv.
    */
   public boolean isPhysical() {
      return mv != null;
   }

   /**
    * Check if is sub mv.
    */
   public boolean isSub() {
      return sub;
   }

   /**
    * Get the mv def last update time.
    */
   public long getMVLastUpdateTime() {
      return time;
   }

   /**
    * Get the string representation.
    */
   public String toString() {
      return "RuntimeMV" + super.hashCode() + "<" + vsassembly + "," + mv + "," + sub + '>';
   }

   /**
    * Cache key for data cache.
    */
   public String createKey() {
      String parentVsIdsStr = parentVsIds == null ? "" : String.join("->", parentVsIds);
      // should ignore vsassembly since it just contains the vs obj name this mv is used for.
      // a mv's contents only depend on the mv file, not which vs is referencing it.
      return "RuntimeMV<" + entry + "," + otable + "," + mv + "," + sub + "," + time + "," +
         parentVsIdsStr + ">";
   }

   public boolean isWSMV() {
      return entry != null && entry.isWorksheet();
   }

   public List<String> getParentVsIds() {
      return parentVsIds;
   }

   private transient WeakReference vsref;
   private AssetEntry entry;
   private String vsassembly;
   private String otable;
   private String mv;
   private boolean sub;
   private long time = -1L;
   private final List<String> parentVsIds;
}
