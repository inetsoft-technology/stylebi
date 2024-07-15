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

import inetsoft.sree.security.IdentityID;
import inetsoft.uql.util.XUtil;
import inetsoft.util.*;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.security.Principal;

/**
 * Represents a replet entry in the replet repository.
 *
 * @author InetSoft Technology Corp.
 * @version 8.5
 */
public class RepletEntry extends RepositoryEntry {
   /**
    * Constructor.
    */
   public RepletEntry() {
      super();
   }

   /**
    * Create a replet entry.
    * @param path the specified path.
    */
   public RepletEntry(String path) {
      this(path, null);
   }

   /**
    * Create a replet entry.
    * @param path the specified path.
    * @param owner the specified owner.
    */
   public RepletEntry(String path, IdentityID owner) {
      super(path, RepositoryEntry.REPLET, owner);
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
      // is a file replet? any change is forbidden
      if(fileReplet) {
         return false;
      }

      if(operation == RENAME_OPERATION) {
         return true;
      }
      else if(operation == CHANGE_FOLDER_OPERATION) {
         return true;
      }
      else if(operation == REMOVE_OPERATION) {
         return true;
      }
      else {
         return false;
      }
   }

   /**
    * Set visible option.
    * @param visible <tt>true</tt> is visible, and <tt>false</tt> is invisible.
    */
   public void setVisible(boolean visible) {
      this.visible =  visible;
   }

   /**
    * Check if is visible.
    * @return <tt>true</tt> if is visible, <tt>false</tt> otherwise.
    */
   public boolean isVisible() {
      return visible;
   }

   /**
    * Set pregenerated replet option.
    * @param pregen <tt>true</tt> is pregenerated replet, <tt>false</tt>
    * otherwise.
    */
   public void setPregenerated(boolean pregen) {
      this.pregen = pregen;
   }

   /**
    * Check if is a pregenerated replet.
    * @return <tt>true</tt> is pregenerated replet, <tt>false</tt> otherwise.
    */
   public boolean isPregenerated() {
      return pregen;
   }

   /**
    * Set the report parameters only flag.
    */
   public void setParamOnly(boolean paramOnly) {
      this.paramOnly = paramOnly;
   }

   /**
    * Check if the replet is a parameters only report.
    */
   public boolean isParamOnly() {
      return paramOnly;
   }

   /**
    * Set the description of the replet.
    * @param desc the specified description.
    */
   public void setDescription(String desc) {
      this.desc = desc;
   }

   /**
    * Get the description of the replet.
    * @return the description of the replet.
    */
   public String getDescription() {
      return desc;
   }

   /**
    * Set file replet option.
    * @param fileReplet <tt>true</tt> indicates that the replet is a file
    * replet, <tt>false</tt> otherwise.
    */
   public void setFileReplet(boolean fileReplet) {
      this.fileReplet = fileReplet;
   }

   /**
    * Check if is a file relet.
    * @return <tt>true</tt> indicates that the replet is a file replet,
    * <tt>false</tt> otherwise.
    */
   public boolean isFileReplet() {
      return fileReplet;
   }

   /**
    * Set the alias of the replet.
    * @param alias the specified alias.
    */
   public void setAlias(String alias) {
      this.alias = alias;
   }

   /**
    * Get the alias of the replet.
    * @return the alias of the replet.
    */
   public String getAlias() {
      return alias;
   }

   /**
    * Write attributes.
    * @param writer the destination print writer.
    */
   @Override
   public void writeAttributes(PrintWriter writer) {
      super.writeAttributes(writer);

      writer.print(" visible=\"" + visible + "\"");
      writer.print(" fileReplet=\"" + fileReplet + "\"");
      writer.print(" pregen=\"" + pregen + "\"");
      writer.print(" paramOnly=\"" + paramOnly + "\"");
   }

   /**
    * Method to parse attributes.
    */
   @Override
   public void parseAttributes(Element tag) throws Exception {
      super.parseAttributes(tag);
      visible = "true".equals(Tool.getAttribute(tag, "visible"));
      fileReplet = "true".equals(Tool.getAttribute(tag, "fileReplet"));
      pregen = "true".equals(Tool.getAttribute(tag, "pregen"));
      paramOnly = "true".equals(Tool.getAttribute(tag, "paramOnly"));
   }

   /**
    * Write contents.
    * @param writer the destination print writer.
    */
   @Override
   public void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      Principal user = ThreadContext.getContextPrincipal();
      Catalog catalog = Catalog.getCatalog(user, Catalog.REPORT);

      if(alias != null) {
         writeCDATA(writer, "alias", Tool.byteEncode(alias));

         writeCDATA(writer, "alabel",
            Tool.byteEncode(catalog.getString(alias)));
      }

      if(desc != null) {
         writeCDATA(writer, "desc", Tool.byteEncode(catalog.getString(desc)));
      }
   }

   /**
    * Method to parse contents.
    */
   @Override
   public void parseContents(Element tag) throws Exception {
      super.parseContents(tag);

      Element elem = Tool.getChildNodeByTagName(tag, "alias");
      alias = Tool.byteDecode(Tool.getValue(elem));

      elem = Tool.getChildNodeByTagName(tag, "desc");
      desc = Tool.byteDecode(Tool.getValue(elem));
   }

   private boolean visible;
   private String alias;
   private boolean fileReplet;
   private boolean pregen;
   private boolean paramOnly;
   private String desc;
}
