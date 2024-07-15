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
package inetsoft.uql.viewsheet;

import inetsoft.uql.asset.Assembly;

/**
 * ContainerVSAssembly represents one viewsheet assembly which container's other
 * component as child.
 *
 * @version 10.1
 * @author InetSoft Technology Corp
 */
public interface ContainerVSAssembly extends VSAssembly {
   /**
    * Get the child assemblies name.
    * @return the child assemblies name.
    */
   public String[] getAssemblies();

   /**
    * Get the child assemblies' absolute name.
    * @return the child assemblies absolute name.
    */
   public String[] getAbsoluteAssemblies();

   /**
    * Set the chart assemblies name.
    * @param names of child assemblies name.
    */
   public void setAssemblies(String[] names);

   /**
    * Check if contains an assembly as a child.
    * @param assembly the specified assembly.
    * @return <tt>true</tt> if contains the assembly, <tt>false</tt> otherwise.
    */
   public boolean containsAssembly(String assembly);

   /**
    * Remove a child assembly from the container.
    */
   public boolean removeAssembly(String assembly);

   /**
    * Calc the sub component z index.
    */
   public void calcChildZIndex();

   /**
    * Layout the Container Assembly.
    * @return the names of the assemblies relocated.
    */
   public Assembly[] layout();
}
