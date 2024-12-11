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
package inetsoft.sree;

import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.IdentityID;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.util.*;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.io.Serializable;
import java.security.Principal;
import java.text.Collator;
import java.util.Locale;

/**
 * Represents an entry in the replet repository. An entry can be a folder,
 * replet, or archived file.
 *
 * @author InetSoft Technology Corp.
 * @version 8.5
 */
public class RepositoryEntry implements Serializable, Comparable, Cloneable, XMLSerializable {
   /**
    * Flag indicating the entry is a folder.
    */
   public static final int FOLDER = 0x01;
   /**
    * Flag indicating the entry is a replet.
    */
   public static final int REPLET = 0x02;
   /**
    * Flag indicating the entry is a composite archived report.
    */
   public static final int COMPOSITE_ARCHIVE = 0x0C;
   /**
    * Flag indicating the entry is a deleted folder or archive.
    */
   public static final int TRASHCAN = 0x10;
   /**
    * Flag indicating the entry is a report file.
    */
   public static final int FILE = 0x20;
   /**
    * Flag indicating the entry is a viewsheet.
    */
   public static final int VIEWSHEET = 0x40;

   /**
    * Flag indicating the entry is a worksheet.
    */
   public static final int WORKSHEET = 0x100;
   /**
    * Flag indicating the entry is a worksheet folder.
    */
   public static final int WORKSHEET_FOLDER = 0x200 | FOLDER;
   /**
    * Folder for recycle bin
    */
   public static final int RECYCLEBIN_FOLDER = 0x400 << 3 | FOLDER;
   /**
    * Data source entry
    */
   public static final int DATA_SOURCE = 0x400;

   /**
    * Folder for data sources
    */
   public static final int DATA_SOURCE_FOLDER = (DATA_SOURCE << 1 | FOLDER);

   /**
    * Root Library Folder
    */
   public static final int LIBRARY_FOLDER = (DATA_SOURCE << 2 | FOLDER);

   /**
    * Script entry
    */
   public static final int SCRIPT = 0x4000 << 3;

   /**
    * Table style entry
    */
   public static final int TABLE_STYLE = SCRIPT << 1;

   /**
    * Global repository entry
    */
   public static final int REPOSITORY = TABLE_STYLE << 1;

   /**
    * My reports entry
    */
   public static final int USER = REPOSITORY << 1;

   /**
    * Prototype entry
    */
   public static final int PROTOTYPE = USER << 1;

   /**
    * Query for data source
    */
   public static final int QUERY = PROTOTYPE << 1;

   /**
    * Logical Model for data source
    */
   public static final int LOGIC_MODEL = QUERY << 1;

   /**
    * Partition/Physical Model for data source
    */
   public static final int PARTITION = LOGIC_MODEL << 1;

   /**
    * Virtual Private Model for data source
    */
   public static final int VPM = PARTITION << 1;

   /**
    * Virtual Private Model for data source
    */
   public static final int DASHBOARD = VPM << 1;

   /**
    * Cube for data source.
    */
   public static final int CUBE = DASHBOARD << 1;

   /**
    * Cube for data source.
    */
   public static final int DATA_MODEL = CUBE << 1;

   /**
    * Root Folder
    */
   public static final int DASHBOARD_FOLDER = (DASHBOARD | FOLDER);

   /**
    * Auto save file.
    */
   public static final int AUTO_SAVE_FILE = 0x20000000;

   /**
    * File for auto save vs recycle bin
    */
   public static final int AUTO_SAVE_VS = AUTO_SAVE_FILE | VIEWSHEET;
   /**
    * File for auto save ws recycle bin
    */
   public static final int AUTO_SAVE_WS = AUTO_SAVE_FILE | WORKSHEET;
   /**
    * Folder for auto save folder recycle bin
    */
   public static final int AUTO_SAVE_FOLDER = AUTO_SAVE_FILE | FOLDER;
   /**
    * Folder for auto save vs folder recycle bin
    */
   public static final int AUTO_SAVE_VS_FOLDER = AUTO_SAVE_VS | FOLDER;
   /**
    * Folder for auto save ws folder recycle bin
    */
   public static final int AUTO_SAVE_WS_FOLDER = AUTO_SAVE_WS | FOLDER;

   /**
    * Cube for data source.
    */
   public static final int SCHEDULE_TASK = AUTO_SAVE_FILE << 1;

   /**
    * Flag indicating all types of entries.
    */
   public static final int ALL = FOLDER | COMPOSITE_ARCHIVE |
      TRASHCAN | FILE | VIEWSHEET | WORKSHEET |
      WORKSHEET_FOLDER | SCHEDULE_TASK;

   /**
    * Rename operation.
    */
   public static final int RENAME_OPERATION = 1;
   /**
    * Change folder operation.
    */
   public static final int CHANGE_FOLDER_OPERATION = 2;
   /**
    * Remove operation.
    */
   public static final int REMOVE_OPERATION = 3;
   /**
    * Repository folder.
    */
   public static final String REPOSITORY_FOLDER = "Repository";
   /**
    * Prototype folder.
    */
   public static final String PROTOTYPE_FOLDER = "Prototype";
   /**
    * Trashcan folder.
    */
   public static final String TRASHCAN_FOLDER = "Trashcan";
   /**
    * Users' folder.
    */
   public static final String USERS_FOLDER = "Users' Reports";
   /**
    * Users' folder.
    */
   public static final String USERS_DASHBOARD_FOLDER = "Users' Dashboards";

   // @by: ChrisSpagnoli feature1407788520831 2014-11-6
   /**
    * Worksheets folder.
    */
   public static final String WORKSHEETS_FOLDER = "Worksheets";

   /**
    * Flag indicating the user folders.
    */
   public static final int USER_FOLDERS = 0x01;
   /**
    * Flag indicating the live reports.
    */
   public static final int LIVE_REPORTS = 0x02;
   /**
    * Flag indicating the snapshots.
    */
   public static final int SNAPSHOTS = 0x04;
   /**
    * Flag indicating the viewsheets.
    */
   public static final int VIEWSHEETS = 0x10;
   /**
    * Flag indicating the worksheets.
    */
   public static final int WORKSHEETS = 0x20;

   /**
    * Constructor.
    */
   public RepositoryEntry() {
   }

   /**
    * Create a repository entry.
    * @param path the specified path.
    * @param type the specified entry type.
    */
   public RepositoryEntry(String path, int type) {
      this(path, type, null);
   }

   /**
    * Create a repository entry.
    * @param path the specified path.
    * @param type the specified entry type.
    * @param owner the specified owner.
    */
   public RepositoryEntry(String path, int type, IdentityID owner) {
      this.path = path;
      this.type = type;
      this.owner = owner;
   }

   /**
    * Check if supports an operation, which should be one of the predefined
    * operation like <tt>RENAME_OPERATION</tt>,
    * <tt>CHANGE_FOLDER_OPERATION</tt>, <tt>REMOVE_OPERATION</tt>, etc.
    * @param operation the specified operation.
    * @return <tt>true</tt> if supports the operation, false otherwise.
    */
   public boolean supportsOperation(int operation) {
      return false;
   }

   /**
    * Check if is root.
    * @return <tt>true</tt> if is root, <tt>false</tt> otherwise.
    */
   public boolean isRoot() {
      return path.equals("/");
   }

   /**
    * Get the name of the repository entry.
    * @return the name of the repository entry.
    */
   public String getName() {
      if(isRoot()) {
         return "";
      }

      int index = path.lastIndexOf("/");
      String name = index >= 0 ? path.substring(index + 1) : path;

      return name;
   }

   /**
    * Get the label of the repository entry.
    * @return the label of the repository entry.
    */
   public String getLabel() {
      Principal user = ThreadContext.getContextPrincipal();
      Catalog catalog = Catalog.getCatalog(user, Catalog.REPORT);
      return catalog.getString(getName());
   }

   /**
    * Get the parent entry of the repository entry.
    * @return the parent entry of the repository entry.
    */
   public RepositoryEntry getParent() {
      String ppath = getParentPath();
      return ppath == null ? null : new DefaultFolderEntry(ppath, owner);
   }

   /**
    * Get the parent path of the repository entry.
    * @return the parent path of the repository entry.
    */
   public String getParentPath() {
      if(isRoot()) {
         return null;
      }

      int index = path.lastIndexOf("/");
      return index >= 0 ? path.substring(0, index) : "/";
   }

   /**
    * Get repository entry path.
    * @return repository entry path.
    */
   public String getPath() {
      return path;
   }

   /**
    * Get repository entry type, which should be one of the predefined types
    * like <tt>FOLDER</tt>, <tt>REPLET</tt>, <tt>ARCHIVE</tt>, etc.
    * @return repository entry type.
    */
   public int getType() {
      return type;
   }

   /**
    * Check if is a folder entry.
    * @return <tt>true</tt> if yes, false otherwise.
    */
   public boolean isFolder() {
      return (type & FOLDER) != 0;
   }

   /**
    * Check if is a replet entry.
    * @return <tt>true</tt> if yes, false otherwise.
    */
   public boolean isReplet() {
      return (type & REPLET) != 0;
   }

   /**
    * Check if is a prototype entry.
    * @return <tt>true</tt> if yes, false otherwise.
    */
   public boolean isPrototype() {
      return (type & PROTOTYPE) != 0;
   }

   /**
    * Get the owner.
    * @return the owner of this entry.
    */
   public IdentityID getOwner() {
      return owner;
   }

   /**
    * Check if is stored in my report folder or is my report folder.
    * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
    */
   public boolean isMyReport() {
      return SUtil.isMyReport(getPath()) || getOwner() != null;
   }

   /**
    * Write the xml segment to print writer.
    * @param writer the destination print writer.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.print("<entry");
      writeAttributes(writer);
      writer.println(">");
      writeContents(writer);
      writer.println("</entry>");
   }

   /**
    * Method to parse an xml segment.
    */
   @Override
   public void parseXML(Element tag) throws Exception {
      parseAttributes(tag);
      parseContents(tag);
   }

   /**
    * Write attributes.
    * @param writer the destination print writer.
    */
   public void writeAttributes(PrintWriter writer) {
      writer.print(" class=\"" + getClass().getName() + "\"");
      writer.print(" type=\"" + type + "\"");
   }

   /**
    * Method to parse attributes.
    */
   public void parseAttributes(Element tag) throws Exception {
      type = Integer.parseInt(Tool.getAttribute(tag, "type"));
   }

   /**
    * Write contents.
    * @param writer the destination print writer.
    */
   public void writeContents(PrintWriter writer) {
      writeCDATA(writer, "path", Tool.byteEncode(path));
      writeCDATA(writer, "label", Tool.byteEncode(getLabel()));

      if(owner != null) {
         writeCDATA(writer, "owner", Tool.byteEncode(owner.convertToKey()));
      }
   }

   /**
    * Method to parse contents.
    */
   public void parseContents(Element tag) throws Exception {
      Element elem = Tool.getChildNodeByTagName(tag, "path");
      path = Tool.byteDecode(Tool.getValue(elem));

      elem = Tool.getChildNodeByTagName(tag, "owner");
      owner = IdentityID.getIdentityIDFromKey(Tool.byteDecode(Tool.getValue(elem)));
   }

   /**
    * Get the string representation.
    * @return the string representation.
    */
   public String toString() {
      if(isRoot()) {
         return "Repository";
      }
      else {
         return getName();
      }
   }

   /**
    * Get the hash code value.
    * @return the hash code value.
    */
   public int hashCode() {
      int code = path.hashCode() ^ type;
      return owner == null ? code : code ^ owner.hashCode();
   }

   /**
    * Check if equals another object.
    * @param obj the specified object.
    * @return <tt>true</tt> if equals another object, <tt>false</tt> otherwise.
    */
   public boolean equals(Object obj) {
      if(!(obj instanceof RepositoryEntry)) {
         return false;
      }

      RepositoryEntry entry2 = (RepositoryEntry) obj;

      return this.path.equals(entry2.path) && this.type == entry2.type &&
         Tool.equals(this.owner, entry2.owner);
   }

   /**
    * Compare to another object.
    * @param obj the specified object.
    * @return compare result.
    */
   @Override
   public int compareTo(Object obj) {
      if(!(obj instanceof RepositoryEntry)) {
         return 1;
      }

      RepositoryEntry entry2 = (RepositoryEntry) obj;
      int score1 = grade(this);
      int score2 = grade(entry2);

      if(score1 != score2) {
         return score2 - score1;
      }

      String order = SreeEnv.getProperty("repository.tree.sort");
      int delta = order.equals("Ascending") || order.equals("none") ? 1 : -1;
      Principal user = ThreadContext.getContextPrincipal();
      Boolean pathOnly = "true".equals(
         SreeEnv.getProperty("repository.tree.sort.pathOnly"));
      //@see feature feature1280909241331
      String path1 = SUtil.localize(path, user, this instanceof RepletEntry,
         getAssetEntry(), !pathOnly);
      String path2 = SUtil.localize(entry2.path, user,
         entry2 instanceof RepletEntry,
         entry2.getAssetEntry(),
         !pathOnly);

      if(!inited) {
         CT = Locale.getDefault().getLanguage().equals("en") ?
            null : Collator_CN.getCollator();
         inited = true;
      }

      return CT == null ? path1.compareToIgnoreCase(path2) * delta :
         CT.compare(path1, path2) * delta;
   }

   /**
    * Get the asset entry.
    */
   public AssetEntry getAssetEntry() {
      return entry;
   }

   public void setAssetEntry(AssetEntry entry) {
      this.entry = entry;
   }

   /**
    * Write a name-value pair cdata representation to an output stream.
    * @param writer the specified output stream.
    * @param name the specified name.
    * @param value the specified value.
    */
   protected void writeCDATA(PrintWriter writer, String name, String value) {
      writer.print("<" + name + ">");
      writer.print("<![CDATA[" + value + "]]>");
      writer.println("</" + name + ">");
   }

   /**
    * Grade a repository entry.
    */
   private int grade(RepositoryEntry entry) {
      int score = 0;

      if(entry.path.startsWith(Tool.MY_DASHBOARD)) {
         score += 10;
      }

      if(entry.owner != null) {
         score += 10;
      }

      if(entry.path.startsWith(SUtil.REPORT_FILE)) {
         score -= 10;
      }

      if(entry.isFolder()) {
         score += 1;
      }

      return score;
   }

   /**
    * Set htmlType.
    */
   public void setHtmlType(int htmlType) {
      this.htmlType = htmlType;
   }

   /**
    * Get htmlType.
    */
   public int getHtmlType() {
      return htmlType;
   }

   /**
    * Set type.
    */
   public void setType(int type) {
      this.type = type;
   }

   /**
    * Set owner.
    */
   public void setOwner(IdentityID owner) {
      this.owner = owner;
   }

   /**
    * Set path.
    */
   public void setPath(String path) {
      this.path = path;
   }

   /**
    * set favoritesUser.
    */
   public void setFavoritesUser(boolean favoritesUser) {
      this.favoritesUser = favoritesUser;
   }

   /**
    * get favoritesUser.
    */
   public boolean getFavoritesUser() {
      return this.favoritesUser;
   }

   public boolean isDefaultOrgAsset() {
      return defaultOrgAsset;
   }

   public void setDefaultOrgAsset(boolean global) {
      defaultOrgAsset = global;
   }

   // @by davyc, if init CT in static variable, KeyRing.init will be called,
   // KeyRing will call System.getProperty, this will casue security problem,
   // seems like should not call System.getProperty in static sequence in RMI
   private static Collator CT = null;
   private static boolean inited = false;
   private String path;
   private int type;
   private IdentityID owner;
   private int htmlType;
   private AssetEntry entry;
   private boolean favoritesUser = false;
   private boolean defaultOrgAsset = false;
}
