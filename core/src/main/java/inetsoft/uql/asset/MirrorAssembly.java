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

import java.security.Principal;

/**
 * MirrorAssembly, the mirror of an assembly.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public interface MirrorAssembly extends AssetObject {
   /**
    * Get the worksheet entry.
    * @return the worksheet entry of the mirror assembly.
    */
   AssetEntry getEntry();

   /**
    * Set the worksheet entry.
    * @param entry the specified worksheet entry.
    */
   void setEntry(AssetEntry entry);

   /**
    * Get the assembly name.
    * @return the assembly name.
    */
   String getAssemblyName();

   /**
    * Check if is outer mirror. An outer mirror is a mirror of an external 
    * asset outside of the containing worksheet.
    * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
    */
   boolean isOuterMirror();

   /**
    * Set last modified time.
    * @param modified the specified last modified time.
    */
   void setLastModified(long modified);

   /**
    * Get the last modified time.
    * @return the last modified time of the worksheet.
    */
   long getLastModified();

   /**
    * Check if is auto update.
    * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
    */
   boolean isAutoUpdate();

   /**
    * Set auto update.
    * @param auto <tt>true</tt> to open auto update.
    */
   void setAutoUpdate(boolean auto);

   /**
    * Get the assembly.
    * @return the assembly of the mirror assembly.
    */
   Assembly getAssembly();

   /**
    * Check if the mirror assembly is valid.
    */
   default void checkValidity() throws Exception {
      checkValidity(true);
   }

   /**
    * Check if the assembly is valid.
    *
    * @param checkCrossJoins {@code true} to check if an unintended cross join is present or
    *                        {@code false} to ignore cross joins.
    *
    * @throws Exception if the assembly is invalid.
    */
   void checkValidity(boolean checkCrossJoins) throws Exception;

   /**
    * Update the outer mirror.
    * @param engine the specified asset repository.
    * @param user the specified user.
    */
   void updateMirror(AssetRepository engine, Principal user)
      throws Exception;

   /**
    * Update the inner mirror.
    * @return <tt>true</tt> if successful, <tt>false</tt> otherwise.
    */
   boolean update();

   /**
    * Rename the assemblies depended on.
    * @param oname the specified old name.
    * @param nname the specified new name.
    */
   void renameDepended(String oname, String nname);

   /**
    * Clone the object.
    * @return the cloned object.
    */
   @Override
   Object clone();
}
