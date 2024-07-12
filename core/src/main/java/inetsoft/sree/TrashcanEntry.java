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
import inetsoft.util.Tool;
import org.w3c.dom.Element;

import java.io.PrintWriter;

/**
 * Represents an trashcan entry in the replet repository.
 *
 * @author InetSoft Technology Corp.
 * @version 8.5
 */
public class TrashcanEntry extends RepositoryEntry {
   /**
    * Create an trashcan entry.
    * @param path the specified path.
    */
   public TrashcanEntry(String path) {
      this(path, null);
   }

   /**
    * Create an trashcan entry.
    * @param path the specified path.
    * @param owner the specified owner.
    */
   public TrashcanEntry(String path, IdentityID owner) {
      super(path, TRASHCAN, owner);
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
      return operation == REMOVE_OPERATION;
   }

   /**
    * Get the name of the trashcan entry.
    * @return the name of the trashcan entry.
    */
   @Override
   public String getName() {
      String path = getPath();
      int index = path.indexOf(TRASHCAN_FOLDER);
      return index >= 0 ? path.substring(index + TRASHCAN_FOLDER.length() + 1) :
                          path;
   }

   /**
    * Get the parent entry of the trashcan entry.
    * @return the parent entry of the trashcan entry.
    */
   @Override
   public RepositoryEntry getParent() {
      return new DefaultFolderEntry(TRASHCAN_FOLDER, getOwner());
   }

   /**
    * Get the parent path of the trashcan entry.
    * @return the parent path of the trashcan entry.
    */
   @Override
   public String getParentPath() {
      return TRASHCAN_FOLDER;
   }

   /**
    * Check if is a folder.
    */
   @Override
   public boolean isFolder() {
      return folder;
   }

   /**
    * Set folder.
    */
   public void setFolder(boolean folder) {
      this.folder = folder;
   }

   /**
    * Write attributes.
    * @param writer the destination print writer.
    */
   @Override
   public void writeAttributes(PrintWriter writer) {
      super.writeAttributes(writer);
      writer.print(" isFolder=\"" + folder + "\"");
   }

   /**
    * Method to parse attributes.
    */
   @Override
   public void parseAttributes(Element tag) throws Exception {
      super.parseAttributes(tag);
      folder = "true".equals(Tool.getAttribute(tag, "isFolder"));
   }

   /**
    * Write contents.
    * @param writer the destination print writer.
    */
   @Override
   public void writeContents(PrintWriter writer) {
      super.writeContents(writer);
      writer.println("<archiveEntry>");

      writer.println("</archiveEntry>");
   }

   /**
    * Method to parse contents.
    */
   @Override
   public void parseContents(Element tag) throws Exception {
      super.parseContents(tag);
   }

   private boolean folder = false;
}
