/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.report.composition.command;

import inetsoft.report.composition.AssetCommand;
import inetsoft.uql.asset.internal.AssemblyInfo;

import java.util.Iterator;

/**
 * Grid command, the <tt>AssetCommand</tt> serves a grid.
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public abstract class GridCommand extends AssetCommand {
   /**
    * Constructor.
    */
   public GridCommand() {
      super();
   }

   /**
    * Get the grid id.
    */
   @Override
   public String getID() {
      return (String) get("__ID__");
   }

   /**
    * Set the grid id.
    */
   @Override
   public void setID(String id) {
      put("__ID__", id);
   }

   /**
    * Get the string representaion.
    * @return the string representation.
    */
   public String toString() {
     AssemblyInfo info = getAssemblyInfo(this);
     String val = super.toString();
     return info == null ? val : val + '(' + info.getAbsoluteName() + ')';
   }

   /**
    * Check if equals another object.
    * @param obj the specified object.
    * @return <tt>true</tt> if equals, </tt>false</tt> otherwise.
    */
   public boolean equals(Object obj) {
      if(!super.equals0(obj)) {
         return false;
      }

      AssemblyInfo info = getAssemblyInfo(this);
      GridCommand cmd2 = (GridCommand) obj;
      AssemblyInfo info2 = getAssemblyInfo(cmd2);

      if(info == null || info2 == null || info.getAbsoluteName() == null ||
         info2.getAbsoluteName() == null)
      {
         return false;
      }

      return info.getAbsoluteName().equals(info2.getAbsoluteName());
   }

   /**
    * Get the assembly info.
    * @param cmd the specified grid command.
    * @return the assembly info if any, <tt>null</tt> otherwise.
    */
   protected AssemblyInfo getAssemblyInfo(GridCommand cmd) {
      Iterator values = cmd.map.values().iterator();

      while(values.hasNext()) {
         Object obj = values.next();

         if(obj instanceof AssemblyInfo) {
            return (AssemblyInfo) obj;
         }
      }

      return null;
   }

}
