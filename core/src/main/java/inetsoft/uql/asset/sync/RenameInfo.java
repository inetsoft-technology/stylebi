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
package inetsoft.uql.asset.sync;

import inetsoft.sree.security.IdentityID;
import inetsoft.util.Tool;
import inetsoft.util.XMLSerializable;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.io.Serializable;

/**
 * This class keeps information to update the dependencies of binding source.
 *
 * @author InetSoft Technology Corp
 * @version 13.1
 */
public class RenameInfo implements Serializable, XMLSerializable, Cloneable {
   /**
    * Source is query.
    */
   public static final int QUERY = 128;
   /**
    * Source is logic model.
    */
   public static final int LOGIC_MODEL = 256;
   /**
    * Source is worksheet.
    */
   public static final int ASSET = 512;
   /**
    * Source is sql table bound assembly.
    */
   public static final int SQL_TABLE = 1024;
   /**
    * Source is physical table.
    */
   public static final int PHYSICAL_TABLE = 2048;
   /**
    * Source is tabular source.
    */
   public static final int TABULAR_SOURCE = 4096;
   /**
    * Hyperlink dependency.
    */
   public static final int HYPERLINK = 4096 * 2;

   public static final int AUTO_DRILL = 4096 * 4;

   /**
    * Embed viewsheet dependency.
    */
   public static final int EMBED_VIEWSHEET = 4096 * 8;

   /**
    * meta template dependency.
    */
   public static final int CUBE = 4096 * 64;

   /**
    * Rename source(name of query, model, ws).
    */
   public static final int SOURCE = 1;
   /**
    * Rename table.
    */
   public static final int TABLE = 2;
   /**
    * Rename column.
    */
   public static final int COLUMN = 4;
   /**
    * Rename folder.
    */
   public static final int FOLDER = 8;
   /**
    * Rename datasource.
    */
   public static final int DATA_SOURCE = 16;
   /**
    * Rename datasource folder.
    */
   public static final int DATA_SOURCE_FOLDER = 32;

   /**
    * Change table option of jdbc datasource..
    */
   public static final int DATA_SOURCE_OPTION = 64;

   /**
    * Element binding
    */
   public static final int ELEMENT_BINDING = 128;

   /**
    * Asset named group
    */
   public static final int ASSET_NAMED_GROUP = 4096 * 64;

   /**
    * viewsheet
    */
   public static final int VIEWSHEET = 4096 * 128;

   /**
    * replet
    */
   public static final int REPLET = 4096 * 256;

   /**
    * partition
    */
   public static final int PARTITION = 4096 * 512;

   public static final int TASK = 4096 * 1024;
   /**
    * script funciton dependency.
    */
   public static final int SCRIPT_FUNCTION = TASK << 1;
   /**
    * table style dependency.
    */
   public static final int TABLE_STYLE = SCRIPT_FUNCTION << 1;

   /**
    * Bookmark dependency.
    */
   public static final int BOOKMARK = TABLE_STYLE << 1;

   /**
    * VPM dependency.
    */
   public static final int VPM = BOOKMARK << 1;

   public RenameInfo() {
      super();
   }

   /**
    * Constructor.
    */
   public RenameInfo(String oname, String nname, int type) {
      this(oname, nname, type, null);
   }

   public RenameInfo(RenameInfo info) {
      this(info.oname, info.nname, info.type);
   }

   /**
    * Constructor.
    */
   public RenameInfo(String oname, String nname, int type, String source) {
      this(oname, nname, type, source, null);
   }

   /**
    * Constructor.
    */
   public RenameInfo(String oname, String nname, int type, boolean rest) {
      this(oname, nname, type);
      this.rest = rest;
   }

   /**
    * Constructor.
    */
   public RenameInfo(String oname, String nname, int type, String source, String table) {
      this.oname = oname;
      this.nname = nname;
      this.type = type;
      this.source = source;
      this.table = table;
   }

   /**
    *
    * @param oname  the old column name.
    * @param nname  the new column name.
    * @param type   the rename type.
    * @param source source of the table.
    * @param table  table of the column.
    * @param entity entity of the column.
    */
   public RenameInfo(String oname, String nname, int type, String source, String table, String entity) {
      this(oname, nname, type, source, table);
      this.entity = entity;
   }

   public String getOldName() {
      return oname;
   }

   public void setPrefix(String pre) {
      this.prefix = pre;
   }

   public String getPrefix() {
      return prefix;
   }

   public void setNewName(String newName) {
      this.nname = newName;
   }

   public String getNewName() {
      return nname;
   }

   public int getType() {
      return type;
   }

   public String getEntity() {
      return entity;
   }

   public void setOldEntity(String oldEntity) {
      this.oentity = oldEntity;
   }

   public String getOldEntity() {
      return oentity;
   }

   /**
    * Set if current renamed source is a rest datasource.
    *
    * @param rest true if rest datasource, else not.
    */
   public void setRest(boolean rest) {
      this.rest = rest;
   }


   public boolean isHyperlink() {
      return (type & HYPERLINK) == HYPERLINK;
   }

   public boolean isAutoDrill() {
      return (type & AUTO_DRILL) == AUTO_DRILL;
   }

   public boolean isAsset() {
      return (type & ASSET) == ASSET;
   }

   public boolean isBookmark() {
      return (type & BOOKMARK) == BOOKMARK;
   }

   public String getBookmarkVS() {
      return bookmarkVS;
   }

   public void setBookmarkVS(String bookmarkVS) {
      this.bookmarkVS = bookmarkVS;
   }

   public IdentityID getBookmarkUser() {
      return bookmarkUser;
   }

   public void setBookmarkUser(IdentityID bookmarkUser) {
      this.bookmarkUser = bookmarkUser;
   }

   public boolean isEmbedViewsheet() {
      return (type & EMBED_VIEWSHEET) == EMBED_VIEWSHEET;
   }

   public boolean isViewsheet() {
      return (type & VIEWSHEET) == VIEWSHEET;
   }

   public boolean isReplet() {
      return (type & REPLET) == REPLET;
   }

   public boolean isTask() {
      return (type & TASK) == TASK;
   }

   public boolean isScriptFunction() {
      return (type & SCRIPT_FUNCTION) == SCRIPT_FUNCTION;
   }

   public boolean isTableStyle() {
      return (type & TABLE_STYLE) == TABLE_STYLE;
   }

   /**
    * @return if current renamed source is a rest datasource.
    */
   public boolean isRest() {
      return this.rest;
   }

   public boolean isReportLink() {
      return this.reportLink;
   }

   public boolean isPrimaryTable() {
      return primaryTable;
   }

   public void setPrimaryTable(boolean primaryTable) {
      this.primaryTable = primaryTable;
   }

   public boolean isUpdateStorage() {
      return this.updateStorage;
   }

   public void setUpdateStorage(boolean updateStorage) {
      this.updateStorage = updateStorage;
   }

   public String getTable() {
      return table;
   }

   public void setTable(String t) {
      this.table = t;
   }

   public String getSource() {
      return source;
   }

   public void setSource(String source) {
      this.source = source;
   }

   public boolean isSource() {
      return (type & SOURCE) == SOURCE;
   }

   public boolean isTable() {
      return (type & TABLE) == TABLE;
   }

   public boolean isColumn() {
      return (type & COLUMN) == COLUMN;
   }

   public boolean isFolder() {
      return (type & FOLDER) == FOLDER;
   }

   public boolean isLogicalModel() {
      return (type & LOGIC_MODEL) == LOGIC_MODEL;
   }

   public boolean isVPM() {
      return (type & VPM) == VPM;
   }

   public boolean isEntity() {
      return (type & TABLE) == TABLE && (type & LOGIC_MODEL) == LOGIC_MODEL;
   }

   public boolean isDataSource() {
      return (type & DATA_SOURCE) == DATA_SOURCE;
   }

   public boolean isPartition() {
      return (type & PARTITION) == PARTITION;
   }

   public boolean isDataSourceFolder() {
      return (type & DATA_SOURCE_FOLDER) == DATA_SOURCE_FOLDER;
   }

   /**
    * If transformation is caused by table option changed.
    */
   public boolean isTableOption() {
      return (type & DATA_SOURCE_OPTION) == DATA_SOURCE_OPTION;
   }

   public boolean isElementBinding() {
      return (type & ELEMENT_BINDING) == ELEMENT_BINDING;
   }

   public boolean isQuery() {
      return (type & QUERY) == QUERY;
   }

   public boolean isCube() {
      return (type & CUBE) == CUBE;
   }

   public boolean isWorksheet() {
      return (type & ASSET) == ASSET;
   }

   public boolean isTabularSource() {
      return (type & TABULAR_SOURCE) == TABULAR_SOURCE;
   }

   public boolean isSqlTable() {
      return (type & SQL_TABLE) == SQL_TABLE;
   }

   public boolean isPhysicalTable() {
      return (type & PHYSICAL_TABLE) == PHYSICAL_TABLE;
   }

   public boolean isAssetNamedGroup() {
      return (type & ASSET_NAMED_GROUP) == ASSET_NAMED_GROUP;
   }

   public int getSourceIndex() {
      return sourceIndex;
   }

   public void setSourceIndex(int sourceIndex) {
      this.sourceIndex = sourceIndex;
   }

   public RenameInfo getParentRenameInfo() {
      return parentRenameInfo;
   }

   public void setParentRenameInfo(RenameInfo parentRenameInfo) {
      this.parentRenameInfo = parentRenameInfo;
   }

   public String getOldPath() {
      return opath;
   }

   public void setOldPath(String opath) {
      this.opath = opath;
   }

   public String getNewPath() {
      return npath;
   }

   public void setNewPath(String npath) {
      this.npath = npath;
   }

   public String getModelFolder() {
      return modelFolder;
   }

   public void setModelFolder(String folder) {
      this.modelFolder = folder;
   }

   public boolean isAlias() {
      return alias;
   }

   public void setAlias(boolean alias) {
      this.alias = alias;
   }

   public void writeStartXml(PrintWriter writer) {
      writer.println("<renameInfo class=\"" + getClass().getName() + "\"");
      writeAttributes(writer);
      writer.println(">");
   }

   public void writeAttributes(PrintWriter writer) {
      if(oname != null) {
         writer.print(" oname=\"" + oname + "\"");
      }

      if(nname != null) {
         writer.print(" nname=\"" + nname + "\"");
      }

      if(table != null) {
         writer.print(" table=\"" + table + "\"");
      }

      if(source != null) {
         writer.print(" source=\"" + source + "\"");
      }

      writer.print(" type=\"" + type + "\"");

      if(rest) {
         writer.print(" rest=\"" + rest + "\"");
      }

      if(bookmarkVS != null) {
         writer.print(" bookmarkVS=\"" + bookmarkVS + "\"");
      }

      if(bookmarkUser != null) {
         writer.print(" bookmarkUser=\"" + bookmarkUser.convertToKey() + "\"");
      }

      writer.print(" alias=\"" + alias + "\"");
   }

   public void writeEndXml(PrintWriter writer) {
      writer.println("</renameInfo>");
   }

   public void writeContentXML(PrintWriter writer) {
      if(parentRenameInfo != null) {
         writer.println("<parentRenameInfo>");
         parentRenameInfo.writeXML(writer);
         writer.println("</parentRenameInfo>");
      }
   }

   /**
    * Write the xml segment to print writer.
    *
    * @param writer the destination print writer.
    */
   public void writeXML(PrintWriter writer) {
      writeStartXml(writer);
      writeContentXML(writer);
      writeEndXml(writer);
   }

   /**
    * Method to parse an xml segment.
    */
   public void parseXML(Element elem) throws Exception {
      parseAttribute(elem);
      parseContent(elem);
   }

   private void parseAttribute(Element elem) {
      oname = Tool.getAttribute(elem, "oname");
      nname = Tool.getAttribute(elem, "nname");
      table = Tool.getAttribute(elem, "table");
      entity = Tool.getAttribute(elem, "entity");
      oentity = Tool.getAttribute(elem, "oentity");
      source = Tool.getAttribute(elem, "source");
      prefix = Tool.getAttribute(elem, "prefix");
      opath = Tool.getAttribute(elem, "opath");
      npath = Tool.getAttribute(elem, "npath");
      modelFolder = Tool.getAttribute(elem, "modelFolder");
      bookmarkVS = Tool.getAttribute(elem, "bookmarkVS");
      bookmarkUser = IdentityID.getIdentityIDFromKey(Tool.getAttribute(elem, "bookmarkUser"));
      String val = Tool.getAttribute(elem, "type");

      if(val != null) {
         try {
            type = Integer.parseInt(val);
         }
         catch(NumberFormatException ex) {
         }
      }

      val  = Tool.getAttribute(elem, "sourceIndex");

      if(val != null) {
         try {
            sourceIndex = Integer.parseInt(val);
         }
         catch(NumberFormatException ex) {
         }
      }

      rest = "true".equalsIgnoreCase(Tool.getAttribute(elem, "rest"));
      rest = "true".equalsIgnoreCase(Tool.getAttribute(elem, "rest"));
      reportLink = "true".equalsIgnoreCase(Tool.getAttribute(elem, "reportLink"));
      primaryTable = "true".equalsIgnoreCase(Tool.getAttribute(elem, "primaryTable"));
      updateStorage = "true".equalsIgnoreCase(Tool.getAttribute(elem, "updateStorage"));
      alias = "true".equalsIgnoreCase(Tool.getAttribute(elem, "alias"));
   }

   private void parseContent(Element elem) throws Exception {
      Element parent = Tool.getChildNodeByTagName(elem, "parentRenameInfo");

      if(parent != null) {
         parentRenameInfo = new RenameInfo();
         parentRenameInfo.parseXML(parent);
      }
   }

   @Override
   public RenameInfo clone() throws CloneNotSupportedException {
      return (RenameInfo) super.clone();
   }

   @Override
   public String toString() {
      return "RenameInfo{" +
         "oname='" + oname + '\'' +
         ", nname='" + nname + '\'' +
         ", table='" + table + '\'' +
         ", source='" + source + '\'' +
         ", prefix='" + prefix + '\'' +
         ", type=" + type +
         ", rest=" + rest +
         ", reportLink=" + reportLink +
         '}';
   }

   protected String oname;
   protected String nname;
   protected String table;
   protected String entity;
   protected String oentity;
   protected String source;
   protected String prefix;
   protected String opath;
   protected String npath;
   protected String modelFolder;
   protected String bookmarkVS;
   protected IdentityID bookmarkUser;
   protected int type = 0;
   protected boolean rest;
   protected boolean reportLink;
   protected boolean primaryTable;
   protected boolean updateStorage;
   protected int sourceIndex;
   protected RenameInfo parentRenameInfo; // which caused the current rename info.
   protected boolean alias = false;
}
