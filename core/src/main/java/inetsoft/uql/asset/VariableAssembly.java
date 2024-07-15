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
package inetsoft.uql.asset;

/**
 * VariableAssembly, contains an <tt>AssetVariable</tt> for reuse.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public interface VariableAssembly extends AttachedAssembly, WSAssembly {
   /**
    * Get the asset variable.
    * @return the asset variable of the variable assembly.
    */
   public AssetVariable getVariable();

   /**
    * Set the asset variable.
    * @param var the specified asset variable.
    */
   public void setVariable(AssetVariable var);

   /**
    * Set the name.
    * @param name the specified name.
    * @param both <tt>true</tt> to rename both assembly name and variable name,
    * <tt>false</tt> to rename assembly name only.
    */
   public void setName(String name, boolean both);
}