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
package inetsoft.sree;

import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.IdentityID;
import inetsoft.util.*;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.security.Principal;

/**
 * Represents a folder entry in the replet repository.
 *
 * @author InetSoft Technology Corp.
 * @version 7.0
 */
public class DefaultFolderEntry extends RepositoryEntry implements FolderEntry {
   /**
    * Constructor.
    */
   public DefaultFolderEntry() {
   }

   /**
    * Create a folder entry.
    * @param path the specified path.
    */
   public DefaultFolderEntry(String path) {
      this(path, null);
   }

   /**
    * Create a folder entry.
    * @param path the specified path.
    * @param owner the specified owner.
    */
   public DefaultFolderEntry(String path, IdentityID owner) {
      super(path, RepositoryEntry.FOLDER, owner);
   }

   /**
    * Create a folder entry.
    * @param path the specified path.
    * @param type the specified entry type.
    * @param owner the specified owner.
    */
   public DefaultFolderEntry(String path, int type, IdentityID owner) {
      super(path, type, owner);
   }

   /**
    * Write contents.
    * @param writer the destination print writer.
    */
   @Override
   public void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      if(desc != null) {
         writeCDATA(writer, "desc", Tool.byteEncode(desc));
      }

      if(alias != null) {
         writeCDATA(writer, "alias", Tool.byteEncode(alias));

         Principal user = ThreadContext.getContextPrincipal();
         Catalog catalog = Catalog.getCatalog(user, Catalog.REPORT);
         writeCDATA(writer, "alabel",
            Tool.byteEncode(catalog.getString(alias)));
      }
   }

   /**
    * Method to parse contents.
    */
   @Override
   public void parseContents(Element tag) throws Exception {
      super.parseContents(tag);

      Element elem = Tool.getChildNodeByTagName(tag, "desc");
      desc = Tool.byteDecode(Tool.getValue(elem));

      elem = Tool.getChildNodeByTagName(tag, "alias");
      alias = Tool.byteDecode(Tool.getValue(elem));
   }

   /**
    * Set the description of the folder.
    * @param desc the specified description.
    */
   public void setDescription(String desc) {
      this.desc = desc;
   }

   /**
    * Get the description of the folder.
    * @return the description of the folder.
    */
   public String getDescription() {
      return desc;
   }

   /**
    * Get folder alias name.
    */
   public String getAlias() {
      return alias;
   }

   /**
    * Set folder alias name.
    */
   public void setAlias(String alias) {
      this.alias = alias;
   }

   /**
    * Get the label of the repository entry.
    * @return the label of the repository entry.
    */
   @Override
   public String getLabel() {
      if(label != null) {
         return label;
      }

      String label = SUtil.localize(getPath(), ThreadContext.getContextPrincipal(),
         false);
      int index = label.lastIndexOf("/");
      return index >= 0 ? label.substring(index + 1) : label;
   }

   /**
    * Set label.
    */
   public void setLabel(String label) {
      this.label = label;
   }

   private String desc;
   private String alias;
   private String label;
}