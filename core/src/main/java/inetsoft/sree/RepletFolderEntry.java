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
package inetsoft.sree;

import inetsoft.sree.security.IdentityID;
import inetsoft.uql.util.XUtil;
import inetsoft.util.Tool;
import org.w3c.dom.Element;

import java.io.PrintWriter;

/**
 * Represents a replet folder entry in the replet repository.
 *
 * @author InetSoft Technology Corp.
 * @version 8.5
 */
public class RepletFolderEntry extends DefaultFolderEntry {
   /**
    * Constructor.
    */
   public RepletFolderEntry() {
   }

   /**
    * Create a replet folder entry.
    * @param path the specified path.
    */
   public RepletFolderEntry(String path) {
      super(path);
   }

   /**
    * Create a replet folder entry.
    * @param path the specified path.
    * @param owner the specified owner.
    */
   public RepletFolderEntry(String path, IdentityID owner) {
      super(path, owner);
   }

   /**
    * Create a replet folder entry.
    * @param path the specified path.
    * @param type the specified entry type.
    * @param owner the specified owner.
    */
   public RepletFolderEntry(String path, int type, IdentityID owner) {
      super(path, type, owner);
   }

   /**
    * Check if supports an operation, which should be one of the predefined
    * operation like <tt>RENAME_OPERATION</tt>,
    * <tt>CHANGE_FOLDER_OPERATION</tt>, <tt>REMOVE_OPERATION</tt>, etc.
    * @param operation the specified operation.
    * @return <tt>true</tt> if supports the operation, false otherwise.
    */
   @Override
   public boolean supportsOperation(int operation) {
      if(operation == RENAME_OPERATION || operation == CHANGE_FOLDER_OPERATION ||
         operation == REMOVE_OPERATION)
      {
         return !isRoot() && !getPath().equals(Tool.MY_DASHBOARD);
      }
      else {
         return false;
      }
   }

   /**
    * Write attributes.
    * @param writer the destination print writer.
    */
   @Override
   public void writeAttributes(PrintWriter writer) {
      super.writeAttributes(writer);
   }

   /**
    * Method to parse attributes.
    */
   @Override
   public void parseAttributes(Element tag) throws Exception {
      super.parseAttributes(tag);
   }
}
