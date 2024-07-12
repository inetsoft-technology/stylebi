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
package inetsoft.report.composition.execution;

/**
 * Information about a MV. This is used in testing.
 */
public interface MVInfo {
   /**
    * Check if the MV has been updated (recreated or incremental updated).
    */
   public boolean isUpdated();

   /**
    * Check if the MV has incremental condition.
    */
   public boolean isIncremental();

   /**
    * Get the name of this MVDef.
    */
   public String getName();

   /**
    * Get the table assemly.
    */
   public String getMVTable();

   /**
    * Get the bound table assemly.
    */
   public String getBoundTable();

   /**
    * Get the last update time.
    */
   public long getLastUpdateTime();

   /**
    * Check if is sub mv.
    */
   public boolean isSub();
}
