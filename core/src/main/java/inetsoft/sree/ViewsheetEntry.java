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
import inetsoft.uql.asset.AssetEntry;
import inetsoft.util.*;
import org.apache.commons.lang.StringUtils;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.security.Principal;

/**
 * Represents a viewsheet entry in the replet repository.
 *
 * @author InetSoft Technology Corp.
 * @version 8.5
 */
public class ViewsheetEntry extends RepositoryEntry {
   /**
    * Constructor.
    */
   public ViewsheetEntry() {
   }

   /**
    * Create a viewsheet entry.
    * @param path the specified path.
    */
   public ViewsheetEntry(String path) {
      this(path, null);
   }

   /**
    * Create a viewsheet entry.
    * @param path the specified path.
    * @param owner the specified owner.
    */
   public ViewsheetEntry(String path, IdentityID owner) {
      super(path, RepositoryEntry.VIEWSHEET, owner);
   }

   /**
    * Set the asset entry.
    */
   public void setAssetEntry(AssetEntry entry) {
      this.entry = entry;
   }

   /**
    * Get the asset entry.
    */
   @Override
   public AssetEntry getAssetEntry() {
      return entry;
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
    * Check if is on report.
    * @return <tt>true</tt> if on report, <tt>false</tt> otherwise.
    */
   public boolean isOnReport() {
      return onReport;
   }

   /**
    * Set whether is on report.
    * @param onReport <tt>true</tt> if on report, <tt>false</tt> otherwise.
    */
   public void setOnReport(boolean onReport) {
      this.onReport = onReport;
   }

   /**
    * Get identifier of the corresponding asset entry.
    * @return identifier of the corresponding asset entry.
    */
   public String getIdentifier() {
      return identifier;
   }

   /**
    * Set identifier of the corresponding asset entry.
    * @param identifier identifier of the corresponding asset entry.
    */
   public void setIdentifier(String identifier) {
      this.identifier = identifier;
   }

   /**
    * Check if is snapshot.
    * @return <tt>true</tt> if is snapshot, <tt>false</tt> otherwise.
    */
   public boolean isSnapshot() {
      return snapshot;
   }

   /**
    * Set whether is snapshot.
    * @param snapshot <tt>true</tt> if is snapshot, <tt>false</tt> otherwise.
    */
   public void setSnapshot(boolean snapshot) {
      this.snapshot = snapshot;
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
    * Set the alias of the viewsheet.
    * @param alias the specified viewsheet.
    */
   public void setAlias(String alias) {
      this.alias = alias;
   }

   /**
    * Get the alias of the viewsheet.
    * @return the alias of the viewsheet.
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

      writer.print(" onReport=\"" + onReport + "\"");
      writer.print(" snapshot=\"" + snapshot + "\"");
      writer.print(" identifier=\"" + Tool.byteEncode(identifier) + "\"");
   }

   /**
    * Method to parse attributes.
    */
   @Override
   public void parseAttributes(Element tag) throws Exception {
      super.parseAttributes(tag);
      onReport = "true".equals(Tool.getAttribute(tag, "onReport"));
      snapshot = "true".equals(Tool.getAttribute(tag, "snapshot"));
      identifier = Tool.byteDecode(Tool.getAttribute(tag, "identifier"));

      if(StringUtils.ordinalIndexOf(identifier, "^", 4) == -1) {
         identifier = AssetEntry.createAssetEntry(identifier).toIdentifier();
      }
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

      if(desc != null) {
         writeCDATA(writer, "desc", Tool.byteEncode(catalog.getString(desc)));
      }

      if(alias != null) {
         writeCDATA(writer, "alias", Tool.byteEncode(alias));
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

   private boolean onReport = true;
   private boolean snapshot = false;
   private String identifier = null;
   private String desc = null;
   private String alias = null;
   private AssetEntry entry;
}
