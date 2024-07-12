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
package inetsoft.uql.asset;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.storage.KeyValueEngine;
import inetsoft.sree.security.IdentityID;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.util.*;
import it.unimi.dsi.fastutil.objects.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.*;
import java.security.Principal;
import java.text.Collator;
import java.text.ParseException;
import java.util.*;

/**
 * AssetEntry locates a sheet or folder in <tt>AssetRepository</tt>. For a
 * user scope sheet/folder, the associated user is required.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
@JsonSerialize(using = AssetEntry.Serializer.class)
@JsonDeserialize(using = AssetEntry.Deserializer.class)
public class AssetEntry implements AssetObject, Comparable<AssetEntry>, DataSerializable {
   public enum Type {
      UNKNOWN(0, 0),
      FOLDER(1, 1),
      ACTUAL_FOLDER(2, 3),
      WORKSHEET(3, 2),
      DATA(4, 4),
      COLUMN(5, 8 | DATA.id, DATA.flags),
      TABLE(6, 16 | DATA.id | FOLDER.id, DATA.flags, FOLDER.flags),
      QUERY(7, 32 | DATA.id | FOLDER.id, DATA.flags, FOLDER.flags),
      LOGIC_MODEL(8, 64 | QUERY.id, QUERY.flags),
      DATA_SOURCE(9, 64 | DATA.id | FOLDER.id, DATA.flags, FOLDER.flags),
      VIEWSHEET(10, 128),
      PHYSICAL(11, 256),
      PHYSICAL_FOLDER(12, 512 | PHYSICAL.id | FOLDER.id, PHYSICAL.flags,
         FOLDER.flags, ACTUAL_FOLDER.flags),
      PHYSICAL_TABLE(13, 1024 | PHYSICAL.id | FOLDER.id,
         PHYSICAL.flags, FOLDER.flags),
      PHYSICAL_COLUMN(14, 2048 | PHYSICAL.id, PHYSICAL.flags),
      COMPONENT(15, 2048),
      SHAPE(16, 10000),
      REPOSITORY_FOLDER(17, 4096 | FOLDER.id, FOLDER.flags, ACTUAL_FOLDER.flags),
      VIEWSHEET_SNAPSHOT(18, 8192 | VIEWSHEET.id, VIEWSHEET.flags),
      VIEWSHEET_BOOKMARK(19, 16384),
      VARIABLE(20, 32768),
      DATA_SOURCE_FOLDER(21, 65536 | DATA_SOURCE.id | FOLDER.id,
         DATA_SOURCE.flags, FOLDER.flags, ACTUAL_FOLDER.flags),
      TABLE_STYLE(25, 0x80000),
      SCRIPT(26, 0x100000),
      REPORT_COMPONENT(27, TABLE_STYLE.id | SCRIPT.id,
         TABLE_STYLE.flags, SCRIPT.flags),
      TABLE_STYLE_FOLDER(31, TABLE_STYLE.id | FOLDER.id, TABLE_STYLE.flags,
         FOLDER.flags, ACTUAL_FOLDER.flags),
      SCRIPT_FOLDER(32, SCRIPT.id | FOLDER.id, SCRIPT.flags, FOLDER.flags,
         ACTUAL_FOLDER.flags),
      DATA_MODEL(33, 0x200000 | DATA.id | FOLDER.id, DATA.flags, FOLDER.flags),
      PARTITION(34, 0x400000 | DATA.id | FOLDER.id, DATA.flags, FOLDER.flags),
      EXTENDED_MODEL(35, 0x800000),
      EXTENDED_PARTITION(36, PARTITION.id | EXTENDED_MODEL.id, PARTITION.flags,
         EXTENDED_MODEL.flags),
      EXTENDED_LOGIC_MODEL(37, LOGIC_MODEL.id | EXTENDED_MODEL.id,
         LOGIC_MODEL.flags, EXTENDED_MODEL.flags),
      VPM(38, 0x1000000 | DATA.id, DATA.flags),
      QUERY_FOLDER(39, 0x2000000 | QUERY.id, QUERY.flags, ACTUAL_FOLDER.flags),
      REPORT_WORKSHEET_FOLDER(41, 0x8000000 | FOLDER.id, FOLDER.flags,
         ACTUAL_FOLDER.flags),
      EMBEDDED_PS_FOLDER(42, 0x10000000 | FOLDER.id, FOLDER.flags,
         ACTUAL_FOLDER.flags),
      REPLET(43, 0x20000000),
      DOMAIN(44, 0x40000000 | FOLDER.id, FOLDER.flags),
      ERM(45, DATA.id | DATA_SOURCE.id | DATA_MODEL.id | LOGIC_MODEL.id |
         PARTITION.id | QUERY_FOLDER.id | QUERY.id | VPM.id, DATA.flags,
         DATA_SOURCE.flags, DATA_MODEL.flags, LOGIC_MODEL.flags,
         PARTITION.flags, QUERY_FOLDER.flags, QUERY.flags, VPM.flags),
      SCHEDULE_TASK(46, 5),
      SCHEDULE_TASK_FOLDER(47, 6, FOLDER.flags, ACTUAL_FOLDER.flags),
      DATA_MODEL_FOLDER(48, 0x80000000, ACTUAL_FOLDER.flags, DATA_MODEL.flags),
      MV_DEF(49, 7),
      MV_DEF_FOLDER(50, 8, MV_DEF.flags, FOLDER.flags, ACTUAL_FOLDER.flags),
      DASHBOARD(51, 9),
      LIBRARY_FOLDER(52, 10, FOLDER.flags, ACTUAL_FOLDER.flags),
      DEVICE(53, 11);

      private final int id;
      private final int bitIndex;
      private final BitSet flags;

      Type(int bitIndex, int id, BitSet ... bitSets) {
         this.bitIndex = bitIndex;
         this.id = id;
         this.flags = new BitSet();
         this.flags.set(bitIndex);

         for(BitSet set : bitSets) {
            this.flags.or(set);
         }
      }

      public int id() {
         return id;
      }

      public static Type forId(int id) {
         Type type = null;

         for(Type value : values()) {
            if(value.id == id) {
               type = value;
               break;
            }
         }

         return type;
      }

      public boolean isFolder() { return flags.get(FOLDER.bitIndex); }
      public boolean isActualFolder() { return flags.get(ACTUAL_FOLDER.bitIndex); }
      public boolean isWorksheet() { return flags.get(WORKSHEET.bitIndex); }
      public boolean isViewsheet() { return flags.get(VIEWSHEET.bitIndex); }
      public boolean isVSSnapshot() { return flags.get(VIEWSHEET_SNAPSHOT.bitIndex); }
      public boolean isSheet() { return isWorksheet() || isViewsheet(); }
      public boolean isData() { return flags.get(DATA.bitIndex); }
      public boolean isColumn() { return flags.get(COLUMN.bitIndex); }
      public boolean isTable() { return flags.get(TABLE.bitIndex); }
      public boolean isQuery() { return flags.get(QUERY.bitIndex); }
      public boolean isLogicModel() { return flags.get(LOGIC_MODEL.bitIndex); }
      public boolean isPhysical() { return flags.get(PHYSICAL.bitIndex); }
      public boolean isDevice() { return flags.get(DEVICE.bitIndex); }
      public boolean isPhysicalFolder() { return flags.get(PHYSICAL_FOLDER.bitIndex); }
      public boolean isRepositoryFolder() { return flags.get(REPOSITORY_FOLDER.bitIndex); }
      public boolean isPhysicalTable() { return flags.get(PHYSICAL_TABLE.bitIndex); }
      public boolean isDataSource() { return flags.get(DATA_SOURCE.bitIndex); }
      public boolean isDataSourceFolder() { return flags.get(DATA_SOURCE_FOLDER.bitIndex); }
      public boolean isVariable() { return flags.get(VARIABLE.bitIndex); }
      public boolean isReplet() { return flags.get(REPLET.bitIndex); }
      public boolean isDomain() { return flags.get(DOMAIN.bitIndex); }
      public boolean isTableStyle() { return flags.get(TABLE_STYLE.bitIndex); }
      public boolean isScript() { return flags.get(SCRIPT.bitIndex); }
      public boolean isTableStyleFolder() {
         return flags.get(TABLE_STYLE_FOLDER.bitIndex);
      }
      public boolean isLibraryFolder() {
         return flags.get(LIBRARY_FOLDER.bitIndex);
      }
      public boolean isScriptFolder() { return flags.get(SCRIPT_FOLDER.bitIndex); }
      public boolean isDataModel() { return flags.get(DATA_MODEL.bitIndex); }
      public boolean isPartition() { return flags.get(PARTITION.bitIndex); }
      public boolean isExtendedPartition() { return flags.get(EXTENDED_PARTITION.bitIndex); }
      public boolean isExtendedLogicModel() { return flags.get(EXTENDED_LOGIC_MODEL.bitIndex); }
      public boolean isExtendedModel() { return flags.get(EXTENDED_MODEL.bitIndex); }
      public boolean isVPM() { return flags.get(VPM.bitIndex); }
      public boolean isQueryFolder() { return flags.get(QUERY_FOLDER.bitIndex); }
      public boolean isScheduleTask() { return flags.get(SCHEDULE_TASK.bitIndex); }

      public boolean isScheduleTaskFolder() { return flags.get(SCHEDULE_TASK_FOLDER.bitIndex); }
      public boolean isDataModelFolder() { return flags.get(DATA_MODEL_FOLDER.bitIndex); }
      public boolean isDashboard() { return flags.get(DASHBOARD.bitIndex); }
   }

   /**
    * Class that is used to select asset entries that match certain types.
    *
    * @since 12.2
    */
   public static final class Selector {
      public Selector(Type ... types) {
         flags = new BitSet();

         for(Type type : types) {
            flags.or(type.flags);
         }
      }

      /**
       * Determines if this selector matches the asset types.
       * The selector matches if the resulting bit set is not empty.
       *
       * @param types the types to check.
       *
       * @return <tt>true</tt> if the types match; <tt>false</tt> otherwise.
       */
      public boolean matches(Type ... types) {
         BitSet tFlags = new BitSet();

         for(Type type : types) {
            tFlags.or(type.flags);
         }

         BitSet flags = (BitSet) this.flags.clone();
         flags.and(tFlags);
         return !flags.isEmpty();
      }

      /**
       * Determines if this selector matches the asset types exactly.
       * The selector matches exactly if the resulting bit set is exactly
       * the same as the OR'd bit set of the given types.
       *
       * @param types the types to check.
       *
       * @return <tt>true</tt> if the types match; <tt>false</tt> otherwise.
       */
      public boolean matchesExactly(Type ... types) {
         BitSet tFlags = new BitSet();

         for(Type type : types) {
            tFlags.or(type.flags);
         }

         BitSet flags = (BitSet) this.flags.clone();
         flags.and(tFlags);
         return flags.equals(tFlags);
      }

      /**
       * Adds the types to the selector by applying a logical OR operation
       * on the selector's bit set with the bit set contained in the types.
       *
       * @param types the types to add to the selector
       */
      public void add(Type ... types) {
         for(Type type : types) {
            flags.or(type.flags);
         }
      }

      /**
       * Checks whether the selector's bit set is equal to the OR'd bit set
       * of the specified types
       *
       * @param types the types to check.
       *
       * @return <tt>true</tt> if the bit sets are equal; <tt>false</tt> otherwise.
       */
      public boolean isEqual(Type ... types) {
         BitSet tFlags = new BitSet();

         for(Type type : types) {
            tFlags.or(type.flags);
         }

         return flags.equals(tFlags);
      }

      @Override
      public boolean equals(Object obj) {
         return obj instanceof Selector && flags.equals(((Selector) obj).flags);
      }

      private BitSet flags;
   }

   /**
    * Worksheet type.
    */
   public static final String WORKSHEET_TYPE = "worksheet.type";

   /**
    * Worksheet description.
    */
   public static final String SHEET_DESCRIPTION = "worksheet.description";

   /**
    * Report data source.
    */
   public static final String REPORT_DATA_SOURCE = "report.datasource";

   /**
    * Path array.
    */
   public static final String PATH_ARRAY = "entry.paths";

   /**
    * Query type.
    */
   public static final String QUERY_TYPE = "query.type";

   /**
    * Current query.
    */
   public static final String CURRENT_QUERY = "query.current";

   /**
    * Normal query type.
    */
   public static final String NORMAL = "normal";

   /**
    * Pre-aggregate query type.
    */
   public static final String PRE_AGGREGATE = "pre_aggregate";

   /**
    * Cube query type.
    */
   public static final String CUBE = "cube";

   /**
    * Cube column type.
    */
   public static final String CUBE_COL_TYPE = "cube.column.type";

   /**
    * Datasource type.
    */
   public static final String DATA_SOURCE_TYPE = "datasource.type";

   /**
    * Cube column is dimension.
    */
   public static final int DIMENSIONS = 0;

   /**
    * Cube column is measure.
    */
   public static final int MEASURES = 1;

   /**
    * Cube column is date dimension.
    */
   public static final int DATE_DIMENSIONS = 2;

   /**
    * Check if is an ignored property.
    */
   public static boolean isIgnoredProperty(String key) {
      return key == null || key.equals(PATH_ARRAY) || key.equals("preview") ||
         key.equals("localStr") || key.equals("__oneoff__") || key.equals("Tooltip") ||
         key.equals("_description_") || key.equals("__bookmark_id__");
   }

   /**
    * Create an asset entry from a string identifier. Supports the
    * AssetEntry.toIdentifier() syntax (e.g. "1^2^__NULL__^WSName", as well as
    * the JavaScript runQuery-worksheet identifiers.
    *    ws:global:dir/WSName
    *    ws:USERNAME:UserWSName
    *
    * @param identifier the specified string identifier.
    * @return the created asset entry.
    */
   public static AssetEntry createAssetEntry(String identifier, String orgID) {
      int index = identifier.indexOf('^');

      if(index < 0) {
         // @davidd 04-2011, Added support for the runQuery-worksheet syntax
         if(identifier.startsWith("ws:")) {
            // support name with ":"
            String[] arr = Tool.splitWithQuote(identifier, ":", (char) 0);
            char singlequote = '\'';
            char doublequote = '"';

            for(int i = 0; i < arr.length; i++) {
               int len = arr[i].length();
               char first = len > 0 ? arr[i].charAt(0) : (char) -1;
               char last = len > 0 ? arr[i].charAt(len - 1) : (char) -1;

               if(first == singlequote && last == singlequote ||
                  first == doublequote && last == doublequote)
               {
                  arr[i] = arr[i].substring(1, len -1);
               }
            }

            String scope = "";
            String path;
            String tbl = null;

            if(arr.length >= 3) {
               scope = arr[1];
               path = arr[2];

               if(arr.length > 3) {
                  tbl = arr[3];
               }
            }
            else if(arr.length == 2) {
               path = arr[1];
            }
            else {
               throw new RuntimeException("Invalid worksheet name: " + identifier);
            }

            AssetEntry entry;

            if(scope.length() == 0) {
               entry = new AssetEntry(AssetRepository.REPORT_SCOPE,
                                      AssetEntry.Type.WORKSHEET, path, null, orgID);
            }
            else if(scope.equalsIgnoreCase("global")) {
               entry = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
                                      AssetEntry.Type.WORKSHEET, path, null, orgID);
            }
            else {
               entry = new AssetEntry(AssetRepository.USER_SCOPE,
                                      AssetEntry.Type.WORKSHEET, path, IdentityID.getIdentityIDFromKey(scope), orgID);
            }

            if(tbl != null) {
               entry.setProperty("table.name", tbl);
            }

            return entry;
         }
         else {
            return null;
         }
      }

      int scope = Integer.parseInt(identifier.substring(0, index));
      int lindex = index;
      index = identifier.indexOf('^', lindex + 1);
      Type type = Type.forId(Integer.parseInt(identifier.substring(lindex + 1, index)));

      lindex = index;
      index = identifier.indexOf('^', lindex + 1);

      String userKey = identifier.substring(lindex + 1, index);
      IdentityID user = NULL.equals(userKey) ? null : IdentityID.getIdentityIDFromKey(userKey);

      lindex = index;
      index = identifier.indexOf('^', lindex + 1);

      String path = index == -1 ?
         identifier.substring(lindex + 1) : identifier.substring(lindex + 1, identifier.lastIndexOf('^'));

      orgID = index == -1 ?
         orgID : identifier.substring(index + 1);

      return new AssetEntry(scope, type, path, user, orgID);
   }

   public static AssetEntry createAssetEntry(String identifier) {
      return AssetEntry.createAssetEntry(identifier, null);
   }

   public String getScriptRunQueryidentifier() {
      StringBuffer str = new StringBuffer();
      str.append("ws:");

      if(getScope() == AssetRepository.GLOBAL_SCOPE) {
         str.append("global:" + getPath());
      }
      else if(getScope() == AssetRepository.USER_SCOPE) {
         str.append("USERNAME:" + getPath());
      }
      else if(getScope() == AssetRepository.REPORT_SCOPE) {
         str.append(getPath());
      }

      if(!Tool.isEmptyString(getProperty("table.name"))) {
         str.append(":" + getProperty("table.name"));
      }

      return str.toString();
   }

   /**
    * Create an asset entry from an xml element.
    * @param elem the specified xml element.
    * @return the created asset entry.
    */
   public static AssetEntry createAssetEntry(Element elem) throws Exception {
      AssetEntry entry = new AssetEntry();
      entry.parseXML(elem);
      return entry;
   }

   /**
    * Create the root entry for the global scope.
    */
   public static AssetEntry createGlobalRoot() {
      return new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.FOLDER,
                            "/", null);
   }

   /**
    * Create the root entry for the user scope.
    */
   public static AssetEntry createUserRoot(Principal user) {
      return new AssetEntry(AssetRepository.USER_SCOPE, AssetEntry.Type.FOLDER,
                            "/", (user == null ? null : IdentityID.getIdentityIDFromKey(user.getName())));
   }

   /**
    * Create the root entry for the report scope.
    */
   public static AssetEntry createReportRoot() {
      AssetEntry entry = new AssetEntry(
         AssetRepository.REPORT_SCOPE, AssetEntry.Type.FOLDER, "/", null);

      return entry;
   }

   /**
    * Get the dependency prefix.
    * @return the dependency prefix of the asset entry.
    */
   public static String getDependencyPrefix() {
      return "<assetDependency><![CDATA[";
   }

   /**
    * Constructor.
    */
   public AssetEntry() {
      super();

      this.prop = Object2ObjectMaps.synchronize(new Object2ObjectOpenHashMap());
      this.hash = -1;
      this.type = Type.UNKNOWN;
   }

   /**
    * Constructor.
    */
   public AssetEntry(int scope, int type, String path, IdentityID user) {
      this(scope, Type.forId(type), path, user);
   }

   /**
    * Constructor.
    */
   public AssetEntry(int scope, Type type, String path, IdentityID user) {
      this(scope, type, path, user, null);
   }

   /**
    * Constructor.
    */
   public AssetEntry(int scope, Type type, String path, IdentityID user, String orgID) {
      this();

      if(orgID == null) {
         orgID = OrganizationManager.getInstance().getCurrentOrgID();
      }

      this.scope = scope;
      this.type = type;
      this.path = path;
      this.orgID = orgID;

      if(this.type == null) {
         this.type = Type.UNKNOWN;
      }

      // only user scope requires user
      if(scope == AssetRepository.USER_SCOPE) {
         this.user = user;
      }
   }

   /**
    * Check if is treated as a report data source.
    * @return <tt>true</tt> if treated as a report data source, <tt>false</tt>
    * otherwise.
    */
   public boolean isReportDataSource() {
      return !"false".equals(getProperty(REPORT_DATA_SOURCE));
   }

   /**
    * Set whether is treated as a report data source.
    * @param source <tt>true</tt> if treated as a report data source,
    * <tt>false</tt> otherwise.
    */
   public void setReportDataSource(boolean source) {
      setProperty(REPORT_DATA_SOURCE, source + "");
   }

   /**
    * Get the scope of the asset entry, which should be one of the predefined
    * types in <tt>AssetRepository<tt> like <tt>GLOBAL_SCOPE</tt>,
    * <tt>USER_SCOPE</tt>, etc.
    * @return the scope of the asset entry.
    */
   public int getScope() {
      return scope;
   }

   /**
    * Get the type of the asset entry, which should be one of the predefined
    * types like <tt>FOLDER</tt>, <tt>WORKSHEET</tt>, etc.
    * @return the type of the asset entry.
    */
   public Type getType() {
      return type;
   }

   /**
    * Get the path of the asset entry.
    * @return the path of the asset entry.
    */
   public String getPath() {
      return path;
   }

   /**
    * Get the lock path of the asset entry.
    * @return the lock path of the asset entry.
    */
   public String getLockPath() {
      return Tool.replaceAll(path, "/", "__");
   }

   /**
    * Get the user of the asset entry.
    * @return the user of the asset entry.
    */
   public IdentityID getUser() {
      return user;
   }

   /**
    * Get the organization id of the asset entry.
    * @return the organization id of the asset entry.
    */
   public String getOrgID() {
      return orgID;
   }

   /**
    * Get a property of the asset entry.
    * @param key the name of the property.
    * @return the value of the property.
    */
   public String getProperty(String key) {
      return prop.get(key);
   }

   /**
    * Set a property of the asset entry.
    * @param key the name of the property.
    * @param value the value of the property, <tt>null</tt> to remove the
    * property.
    */
   public void setProperty(String key, String value) {
      if(value == null) {
         prop.remove(key);
      }
      else {
         prop.put(key, value);
      }
   }

   /**
    * Get the name of the asset entry.
    * @return the name of the asset entry.
    */
   public String getName() {
      if(cachedName != null) {
         return cachedName;
      }

      if(isRoot()) {
         return cachedName = "";
      }

      String ppath = getParentPath();

      if(ppath.equals("/")) {
         return cachedName = path;
      }

      if(path.length() == ppath.length()) {
         return cachedName = "";
      }

      return cachedName = Tool.replaceAll(path.substring(ppath.length() + 1), "^_^", "/");
   }

   /**
    * Check if is root.
    * @return <tt>true</tt> if is root, <tt>false</tt> otherwise.
    */
   public boolean isRoot() {
      return path.equals("/");
   }

   /**
    * Check if is a folder entry.
    * @return <tt>true</tt> if yes, false otherwise.
    */
   public boolean isFolder() {
      return type.isFolder();
   }

   public boolean isActualFolder() {
      return type.isActualFolder() || isWorksheetFolder();
   }

   /**
    * Check if is a worksheet entry.
    * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
    */
   public boolean isWorksheet() {
      return type.isWorksheet();
   }

   /**
    * Check if is a viewsheet entry.
    * @return <code>true</code> if yes, <code>false</code> otherwise.
    */
   public boolean isViewsheet() {
      return type.isViewsheet();
   }

   /**
    * Check if is a viewsheet snapshot entry.
    * @return <code>true</code> if yes, <code>false</code> otherwise.
    */
   public boolean isVSSnapshot() {
      return type.isVSSnapshot();
   }

   /**
    * Check if is a sheet entry.
    * @return <code>true</code> if yes, <code>false</code> otherwise.
    */
   public boolean isSheet() {
      return type.isSheet();
   }

   /**
    * Check if is a data entry.
    * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
    */
   public boolean isData() {
      return type.isData();
   }

   /**
    * Check if is a column entry.
    * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
    */
   public boolean isColumn() {
      return type == Type.COLUMN || type == Type.PHYSICAL_COLUMN;
   }

   /**
    * Check if is a table entry.
    * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
    */
   public boolean isTable() {
      return type == Type.TABLE;
   }

   /**
    * Check if is a query entry.
    * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
    */
   public boolean isQuery() {
      return type == Type.QUERY;
   }

   /**
    * Check if is a logic model entry.
    * @return <code>true</code> if yes, <code>false</code> otherwise.
    */
   public boolean isLogicModel() {
      return type == Type.LOGIC_MODEL;
   }

   /**
    * Check if is a physical folder entry.
    * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
    */
   public boolean isPhysicalFolder() {
      return type == Type.PHYSICAL_FOLDER;
   }

   /**
    * Check if is a repository folder entry.
    * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
    */
   public boolean isRepositoryFolder() {
      return type == Type.REPOSITORY_FOLDER;
   }

   /**
    * Check if is a physical view.
    * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
    */
   public boolean isPhysical() {
      return type == Type.PHYSICAL;
   }

   /**
    * Check if is a physical table entry.
    * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
    */
   public boolean isPhysicalTable() {
      return type == Type.PHYSICAL_TABLE;
   }
   /**
    * Check if is a data source entry.
    * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
    */
   public boolean isDataSource() {
      return type == Type.DATA_SOURCE;
   }

   /**
    * Check if is a data source folder entry.
    * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
    */
   public boolean isDataSourceFolder() {
      return type == Type.DATA_SOURCE_FOLDER;
   }

   /**
    * Check if is a variable entry.
    * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
    */
   public boolean isVariable() {
      return type == Type.VARIABLE;
   }

   /**
    * Check if is a replet entry.
    * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
    */
   public boolean isReplet() {
      return type == Type.REPLET;
   }

   /**
    * Check if is a domain entry.
    * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
    */
   public boolean isDomain() {
      return type == Type.DOMAIN;
   }

   /**
    * Check if is a table style entry.
    * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
    */
   public boolean isTableStyle() {
      return type == Type.TABLE_STYLE;
   }

   /**
    * Check if is a Device entry.
    * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
    */
   public boolean isDevice() {
      return type == Type.DEVICE;
   }

   /**
    * Check if is a script function entry.
    * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
    */
   public boolean isScript() {
      return type == Type.SCRIPT;
   }

   /**
    * Check if is a table style folder entry.
    * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
    */
   public boolean isTableStyleFolder() {
      return type == Type.TABLE_STYLE_FOLDER;
   }

   /**
    * Check if is a table style folder entry.
    * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
    */
   public boolean isLibraryFolder() {
      return type == Type.LIBRARY_FOLDER;
   }

   /**
    * Check if is a table style sub folder entry.
    * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
    */
   public boolean isTableStyleSubFolder() {
      String folder = getProperty("folder");
      folder = ("").equals(folder) ? null : folder;
      return isTableStyleFolder() && folder != null;
   }

   /**
    * Check if is a script folder entry.
    * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
    */
   public boolean isScriptFolder() {
      return type == Type.SCRIPT_FOLDER;
   }

   /**
    * Check if is a schedule task.
    * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
    */
   public boolean isScheduleTask() {
      return type == Type.SCHEDULE_TASK;
   }

   /**
    * Check if is a schedule task folder.
    * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
    */
   public boolean isScheduleTaskFolder() {
      return type == Type.SCHEDULE_TASK_FOLDER;
   }

   /**
    * Check if is a local parameter sheet folder entry.
    * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
    */
   public boolean isEmbeddedPSFolder() {
      return type == Type.EMBEDDED_PS_FOLDER;
   }

   /**
    * Check if is a data model entry.
    * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
    */
   public boolean isDataModel() {
      return type == Type.DATA_MODEL;
   }

   /**
    * Check if is a data model folder entry.
    * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
    */
   public boolean isDataModelFolder() {
      return type == Type.DATA_MODEL_FOLDER;
   }

   /**
    * Check if is a partion entry.
    * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
    */
   public boolean isPartition() {
      return type == Type.PARTITION;
   }

   /**
    * Check if is a extended partion entry.
    * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
    */
   public boolean isExtendedPartition() {
      return type == Type.EXTENDED_PARTITION;
   }

   /**
    * Check if is a extended logic model entry.
    * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
    */
   public boolean isExtendedLogicModel() {
      return type == Type.EXTENDED_LOGIC_MODEL;
   }

   /**
    * Check if is a extended model entry.
    * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
    */
   public boolean isExtendedModel() {
      return type.isExtendedModel();
   }

   /**
    * Check if is a vpm entry.
    * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
    */
   public boolean isVPM() {
      return type == Type.VPM;
   }

   /**
    * Check if is a query folder entry.
    * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
    */
   public boolean isQueryFolder() {
      return type == Type.QUERY_FOLDER;
   }

   /**
    * Check if is a worksheet folder entry.
    * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
    */
   public boolean isWorksheetFolder() {
      return isFolder() && (scope == AssetRepository.GLOBAL_SCOPE ||
         scope == AssetRepository.REPORT_SCOPE) && !isEmbeddedPSFolder() &&
         !isQuery() && !isQueryFolder() && !isScheduleTaskFolder();
   }

   /**
    * Check if is a portal dashboard.
    * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
    */
   public boolean isDashboard() {
      return type.isDashboard();
   }

   /**
    * Check if is editable.
    * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
    */
   public boolean isEditable() {
      return scope != AssetRepository.QUERY_SCOPE && !isRoot();
   }

   /**
    * Get the parent path of the asset entry.
    * @return the parent path of the asset entry.
    */
   public String getParentPath() {
      if(isRoot()) {
         return null;
      }

      if(ppath == null) {
         String name = null;

         if(type != null) {
            switch(type) {
            case COLUMN:
            case PHYSICAL_COLUMN:
               name = getProperty("attribute");
               break;
            case TABLE:
            case PHYSICAL_TABLE:
            case PHYSICAL_FOLDER:
               name = getProperty("entity");
               break;
            }
         }

         String parr = getProperty(PATH_ARRAY);

         if(parr != null) {
            String[] arr = AssetUtil.split(parr, PATH_ARRAY_SEPARATOR);

            if(parr.endsWith(PATH_ARRAY_SEPARATOR) && Tool.isEmptyString(name)) {
               arr = Arrays.copyOf(arr, arr.length + 1);
               arr[arr.length - 1] = "";
            }

            if(arr.length == 1) {
               ppath = "/";
            }
            else {
               ppath = "";

               for(int i = 0; i < arr.length - 1; i++) {
                  if(i > 0) {
                     ppath += "/";
                  }

                  ppath += arr[i];
               }
            }
         }

         if(ppath == null && name != null && path.endsWith(name)) {
            ppath = path.substring(0, path.length() - name.length() - 1);
         }

         if(ppath == null) {
            int index = path.lastIndexOf("/");
            ppath = index >= 0 ? path.substring(0, index) : "/";
         }
      }

      return ppath;
   }

   /**
    * Get the parent entry of the asset entry.
    * @return the parent entry of the asset entry.
    */
   public AssetEntry getParent() {
      String ppath = getParentPath();

      if(ppath == null) {
         return null;
      }

      if(pentry == null) {
         Type type = isViewsheet() || isReplet() || isRepositoryFolder() ?
            Type.REPOSITORY_FOLDER : Type.FOLDER;
         type = isTableStyleFolder() || isTableStyle() ?
            Type.TABLE_STYLE_FOLDER : type;
         type = isScheduleTaskFolder() || isScheduleTask() ? Type.SCHEDULE_TASK_FOLDER : type;
         type = isPartition() && ppath.indexOf("Data Model/") > 0 ? Type.DATA_MODEL_FOLDER : type;
         type = this.type == Type.MV_DEF ? Type.MV_DEF_FOLDER : type;

         if(isColumn()) {
            String ptype = getProperty("type");

            if(ptype != null) {
               int itype = Integer.parseInt(ptype);

               switch(itype) {
               case SourceInfo.PHYSICAL_TABLE:
                  type = Type.PHYSICAL_TABLE;
                  break;
               case SourceInfo.MODEL:
                  type = Type.TABLE;
                  break;
               }
            }
         }

         pentry = new AssetEntry(scope, type, ppath, user, this.getOrgID());
         pentry.copyProperties(this);
         String parr = getProperty(PATH_ARRAY);

         if(parr != null) {
            int index = parr.lastIndexOf(PATH_ARRAY_SEPARATOR);

            if(index > 0) {
               pentry.setProperty(PATH_ARRAY, parr.substring(0, index));
            }
            else {
               pentry.setProperty(PATH_ARRAY, null);
            }
         }
      }

      return pentry;
   }

   /**
    * Check if is the ancestor of another entry.
    * @param entry the specified entry.
    * @return <tt>true</tt> if is the ancestor, <tt>false</tt> otherwise.
    */
   public boolean isAncestor(AssetEntry entry) {
      if(entry.getScope() != getScope()) {
         return false;
      }

      while(entry.getParent() != null) {
         entry = entry.getParent();

         if(entry.equals(this)) {
            return true;
         }
      }

      return false;
   }

   /**
    * Get the string representation.
    * @return the string representation.
    */
   public String toString() {
      if(isRoot()) {
         return getRootString();
      }
      else {
         return getName();
      }
   }

   /**
    * Get the string to display.
    * @return the string to display.
    */
   public String toView() {
      if(isRoot()) {
         return getRootString();
      }
      else {
         return getAlias() != null && getAlias().length() != 0 ?
            getAlias() : getName();
      }
   }

   /**
    * Get the string if it is root.
    */
   private String getRootString() {
      if(scope == AssetRepository.GLOBAL_SCOPE) {
         return "Global Asset Repository";
      }
      else if(scope == AssetRepository.REPORT_SCOPE) {
         return "Report Asset Repository";
      }
      else if(scope == AssetRepository.USER_SCOPE) {
         return "My Asset Repository";
      }
      else if(scope == AssetRepository.QUERY_SCOPE) {
         return "Query Repository";
      }
      else if(scope == AssetRepository.COMPONENT_SCOPE) {
         return "Component Repository";
      }
      else if(scope == AssetRepository.REPOSITORY_SCOPE) {
         return "Report Repository";
      }
      else {
         throw new RuntimeException("Unsupported scope found: " + scope);
      }
   }

   /**
    * Get the description for debug only.
    */
   public String toDescription() {
      return "AssetEntry@" + super.hashCode();
   }

   /**
    * Get a alias of the asset entry.
    * @return the alias of the folder.
    */
   public String getAlias() {
      return alias;
   }

   /**
    * Set a alias of the asset entry.
    * @param alias the alias of the property.
    */
   public void setAlias(String alias) {
      this.alias = alias;
   }

   /**
    * Get favoritesUser in asset entry
    */
   public String getFavoritesUser() {
      String users = "";

      for(int i = 0; i < favoritesUser.size(); i++) {
         String user = favoritesUser.get(i);

         if(i == 0) {
            users += user;
            continue;
         }

         users += "^_^" + user;
      }

      return users;
   }

   public List<String> getFavoritesUsers() {
      return Collections.unmodifiableList(favoritesUser);
   }

   /**
    * Add favoritesUser to FavoritesUser.
    */
   public void addFavoritesUser(String favoritesUser0) {
      if(favoritesUser0 == null) {
         return;
      }

      String[] users = favoritesUser0.split("\\^_\\^");

      for(String user: users) {
         if(!favoritesUser.contains(user)) {
            favoritesUser.add(user);
         }
      }
   }

   /**
   * delete favorites user.
   */
   public void deleteFavoritesUser(String favoritesUser0) {
      favoritesUser.remove(favoritesUser0);
   }

   /**
    * Get the description without localization.
    * @return the description.
    */
   public String getDescription() {
      return getDescription(true);
   }

   /**
    * Get the description without localization.
    * @param localize <code>true</code> if the description needs to localize.
    * @return the description.
    */
   public String getDescription(boolean localize) {
      return getDescription(localize, false);
   }

   /**
    * Get the description without localization.
    * @param localize <code>true</code> if the description needs to localize.
     @param isAsset <code>true</code> if the description is asset description.
    * @return the description.
    */
   public String getDescription(boolean localize, boolean isAsset) {
      Catalog gcata = Catalog.getCatalog();
      String desc;

      if(localize && (desc = getProperty("_description_")) != null) {
         return "true".equals(getProperty("preview")) ?
            (getString("Preview", gcata, true) + " " + desc) : desc;
      }

      StringBuilder root = new StringBuilder();
      String prop = getProperty("preview");

      if("true".equals(prop)) {
         root.append(getString("Preview", gcata, localize)).append(" ");
      }

      String name = getString("Worksheet", gcata, localize);

      if(type == Type.VIEWSHEET || type == Type.REPOSITORY_FOLDER ||
         type == Type.VIEWSHEET_SNAPSHOT)
      {
         name = getString("Dashboard", gcata, localize);
      }
      else if(type == Type.SCHEDULE_TASK_FOLDER) {
         name = getString("Tasks", gcata, localize);
      }
      else if(type == Type.SCRIPT) {
         name = getString("Library", gcata, localize);
      }
      else if(type == Type.SCHEDULE_TASK) {
         name = getString("", gcata, localize);
      }

      if(scope == AssetRepository.GLOBAL_SCOPE) {
         String scopeName = isAsset ? getString(name, gcata, localize) : name;
         root.append(scopeName);
      }
      else if(scope == AssetRepository.REPORT_SCOPE) {
         String scopeName = isAsset ?
            getString("Local" + " " + name, gcata, localize) :
            getString("Local", gcata, localize) + " " + name;
         root.append(scopeName);
      }
      else if(scope == AssetRepository.USER_SCOPE) {
         root.append(getString("User " + name, gcata, localize));
      }
      else if(scope == AssetRepository.QUERY_SCOPE) {
         root.append(getString("Data Source", gcata, localize));
      }
      else if(scope == AssetRepository.TEMPORARY_SCOPE) {
         root.append(getString("New", gcata, localize)).append(" ");
         root.append(name);
      }
      else if(scope == AssetRepository.COMPONENT_SCOPE) {
         if(type == Type.DEVICE) {
            root.append(getString("Device", gcata, localize));
         }
         else if(!noScope(type)) {
            root.append(getString("Component Repository", gcata, localize));
            root.append(name);
         }
      }
      else if(scope == AssetRepository.REPOSITORY_SCOPE) {
         root.append(getString("Report Repository", gcata, localize));
         root.append(name);
      }
      else {
         throw new RuntimeException("Unsuported scope found: " + scope);
      }

      if(isRoot()) {
         return root.toString();
      }

      if(!noScope(type))
      {
         root.append("/");
      }

      Principal user = ThreadContext.getContextPrincipal();
      Catalog ucata = Catalog.getCatalog(user, Catalog.REPORT);
      String[] temp = path == null ? null : type != Type.DEVICE ? Tool.split(path, '/') :
         Tool.split(SUtil.getDeviceName(path), '/');

      if(temp != null && temp.length > 0 && temp[0].isEmpty()) {
         temp = Arrays.copyOfRange(temp, 1, temp.length);
      }

      for(int i = 0; temp != null && i < temp.length; i++) {
         if(i != temp.length - 1) {
            root.append(getString(temp[i], ucata, localize));
         }

         if(i < temp.length - 1) {
            root.append("/");
         }
         else if(isAsset && getAlias() != null && getAlias().length() != 0) {
            root.append(getAlias());
         }
         else {
            String tempString = temp[i];

            if(isScheduleTask()) {
               tempString = SUtil.getTaskName(tempString);
            }

            root.append(getString(tempString, ucata, localize));
         }
      }

      return root.toString();
   }

   private boolean noScope(Type type) {
      return type == Type.TABLE_STYLE || type == Type.TABLE_STYLE_FOLDER ||
         type == Type.SCHEDULE_TASK || type == Type.SCRIPT;
   }

   /**
    * Get the localized string.
    * @param localize <code>true</code> if the text needs to localize.
    */
   private String getString(String text, Catalog cata, boolean localize) {
      return localize ? cata.getString(text) : text;
   }

   /**
    * Get the hash code value.
    * @return the hash code value.
    */
   public int hashCode() {
      if(hash < 0) {
         // hash is serialized so don't change the hashing logic

         if(path != null) {
            hash = path.hashCode();
         }

         hash = hash ^ type.id;
         hash = hash ^ scope;

         if(user != null) {
            hash = hash ^ user.hashCode();
         }

         hash = Math.abs(hash);
      }

      return hash;
   }

   /**
    * Check if equals another object.
    * @param obj the specified object.
    * @return <tt>true</tt> if equals another object, <tt>false</tt> otherwise.
    */
   public boolean equals(Object obj) {
      if(!(obj instanceof AssetEntry)) {
         return false;
      }

      AssetEntry entry2 = (AssetEntry) obj;

      return Tool.equals(this.path, entry2.path) &&
             Tool.equals(this.user, entry2.user) &&
             Tool.equals(this.orgID, entry2.orgID) &&
             this.type == entry2.type && this.scope == entry2.scope;
   }

   /**
    * Compare to another object.
    * @param obj the specified object.
    * @return compare result.
    */
   @Override
   public int compareTo(AssetEntry obj) {
      String order = SreeEnv.getProperty("repository.tree.sort");
      int delta = order.equals("Ascending") || order.equals("none") ? 1 : -1;

      int score1 = grade();
      int score2 = obj.grade();

      if(score1 != score2) {
         return score1 - score2;
      }

      String ppath = getParentPath();
      ppath = ppath == null || ppath.equals("/") ? "" : ppath + "/";
      String ppath2 = obj.getParentPath();
      ppath2 = ppath2 == null || ppath2.equals("/") ? "" : ppath2 + "/";
      boolean pathOnly = "true".equals(SreeEnv.getProperty("repository.tree.sort.pathOnly"));
      String sourcePath = ppath + (pathOnly ? getName() : toView());
      String targetPath = ppath2 + (pathOnly ? obj.getName() : obj.toView());
      int pathrc;

      if(CT == null) {
         pathrc = sourcePath.compareToIgnoreCase(targetPath);

         // if the paths are the same when ignoring case, we preserves the
         // original meaning and return an order if they are not identical
         if(pathrc == 0) {
            pathrc = sourcePath.compareTo(targetPath);
         }
      }
      else {
         pathrc = CT.compare(sourcePath, targetPath);
      }

      return pathrc * delta;
   }

   /**
    * Grade the asset entry.
    * @return the grade result.
    */
   private int grade() {
      int score = 0;

      // show folder first
      if(type == Type.FOLDER || type == Type.REPOSITORY_FOLDER) {
         score += 1;
      }
      // show assets second
      else if(type == Type.WORKSHEET) {
         score += 2;
      }
      // show viewsheet third
      else if(type == Type.VIEWSHEET || type == Type.VIEWSHEET_SNAPSHOT) {
        score += 3;
      }

      // show query scope assets first
      if(scope == AssetRepository.QUERY_SCOPE) {
         score += 10;
      }
      // show global scope assets second
      if(scope == AssetRepository.GLOBAL_SCOPE) {
         score += 20;
      }
      // show report scope assets third
      else if(scope == AssetRepository.REPORT_SCOPE) {
         score += 30;
      }
      // show user scope assets forth
      else if(scope == AssetRepository.USER_SCOPE) {
         score += 40;
      }

      return score;
   }

   /**
    * Check if the asset entry is valid.
    * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
    */
   public boolean isValid() {
      if(scope == AssetRepository.USER_SCOPE) {
         // a user scope asset entry should contain user info
         if(user == null) {
            return false;
         }
      }
      else if(scope == AssetRepository.REPORT_SCOPE) {
         return false;
      }
      else if(scope == AssetRepository.GLOBAL_SCOPE && isFolder() && path != null &&
         (path.isEmpty() || path.length() > 1 && path.charAt(0) == '/'))
      {
         // Bug #60134, our code wouldn't create this, but a customer messing around with internals
         // has
         return false;
      }

      return true;
   }

   /**
    * Get the keys of the properties.
    */
   public Set<String> getPropertyKeys() {
      return prop.keySet();
   }

   /**
    * Copy properties from another asset entry.
    * @param entry the specified asset entry.
    */
   public void copyProperties(AssetEntry entry) {
      for(String key : entry.prop.keySet()) {
         String val = entry.prop.get(key);

         if(!isIgnoredProperty(key)) {
            this.prop.put(key, val);
         }
      }
   }

   /**
    * Write the xml segment to print writer.
    * @param writer the destination print writer.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      boolean compact = Tool.isCompact();
      String cls = !compact ? CLS : "";

      writer.print("<assetEntry" + cls + " scope=\"" + scope +
                   "\" type=\"" + type.id + "\">");
      writer.print("<path>");
      writer.print("<![CDATA[" + path + "]]>");
      writer.println("</path>");

      if(!compact && alias != null && alias.length() != 0) {
         writer.print("<alias>");
         writer.print("<![CDATA[" + alias + "]]>");
         writer.println("</alias>");
      }

      if(!compact) {
         writer.print("<description>");
         writer.print("<![CDATA[" + getDescription() + "]]>");
         writer.println("</description>");

         if(favoritesUser != null) {
            writer.print("<favoritesUser>");
            writer.print("<![CDATA[" + getFavoritesUser() + "]]>");
            writer.println("</favoritesUser>");
         }

         if(createdUsername != null) {
            writer.print("<createdUsername>");
            writer.print("<![CDATA[" + getCreatedUsername() + "]]>");
            writer.println("</createdUsername>");
         }

         if(createdDate != null) {
            String dateStr = Tool.formatDateTime(createdDate);
            writer.print("<createdDate>");
            writer.print("<![CDATA[" + dateStr + "]]>");
            writer.println("</createdDate>");
         }

         if(modifiedUsername != null) {
            writer.print("<modifiedUsername>");
            writer.print("<![CDATA[" + getModifiedUsername() + "]]>");
            writer.println("</modifiedUsername>");
         }

         if(modifiedDate != null) {
            String dateStr = Tool.formatDateTime(modifiedDate);
            writer.print("<modifiedDate>");
            writer.print("<![CDATA[" + dateStr + "]]>");
            writer.println("</modifiedDate>");
         }
      }

      if(user != null) {
         writer.print("<user>");
         writer.print("<![CDATA[" + user.convertToKey() + "]]>");
         writer.print("</user>");
      }

      if(orgID != null) {
         writer.print("<organizationID>");
         writer.print("<![CDATA[" + orgID + "]]>");
         writer.print("</organizationID>");
      }

      writeProperties(writer);
      writer.println("</assetEntry>");
   }

   /**
    * Write data to a DataOutputStream.
    * @param dos the destination DataOutputStream.
    */
   @Override
   public void writeData(DataOutputStream dos) {
      try{
         dos.writeInt(scope);
         dos.writeInt(type.id);
         dos.writeUTF(path);

         dos.writeBoolean(alias == null);

         if(alias != null) {
            dos.writeUTF(alias);
         }

         dos.writeBoolean(user == null);

         if(user != null) {
            dos.writeUTF(user.convertToKey());
         }

         writeProperties2(dos);
      }
      catch(IOException ignore) {
      }
   }

   /**
    * Write properties.
    * @param dos the destination DataOutputStream.
    */
   public void writeProperties2(DataOutputStream dos) {
      try{
         dos.writeInt(prop.size());

         for(String key : prop.keySet()) {
            dos.writeUTF(key);
            String val = prop.get(key);
            dos.writeUTF(val);
         }
      }
      catch(IOException ignore) {
      }
   }

   /**
    * Write properties.
    * @param writer the destination print writer.
    */
   private void writeProperties(PrintWriter writer) {
      if(prop.size() == 0) {
         return;
      }

      writer.println("<properties>");

      for(String key : prop.keySet()) {
         writer.println("<property>");
         writer.print("<key>");
         writer.print("<![CDATA[" + key + "]]>");
         writer.print("</key>");
         String val = prop.get(key);
         writer.print("<value>");
         writer.print("<![CDATA[" + val + "]]>");
         writer.print("</value>");
         writer.println("</property>");
      }

      writer.println("</properties>");
   }

   /**
    * Method to parse an xml segment.
    * @param elem the specified xml element.
    */
   @Override
   public void parseXML(Element elem) throws Exception {
      this.scope = Integer.parseInt(Tool.getAttribute(elem, "scope"));
      this.type = Type.forId(Integer.parseInt(Tool.getAttribute(elem, "type")));
      this.user = IdentityID.getIdentityIDFromKey(Tool.getChildValueByTagName(elem, "user"));
      this.orgID = Tool.getChildValueByTagName(elem, "organizationID");
      if(orgID == null) {
         this.orgID = OrganizationManager.getInstance().getCurrentOrgID();
      }
      else {
         this.orgID = orgID;
      }

      Element pathnode = Tool.getChildNodeByTagName(elem, "path");
      this.path = Tool.getValue(pathnode, true);

      this.alias = Tool.getChildValueByTagName(elem, "alias");

      String favoritesUser = Tool.getChildValueByTagName(elem, "favoritesUser");
      this.addFavoritesUser(favoritesUser);

      Element tempNode = Tool.getChildNodeByTagName(elem, "createdUsername");
      createdUsername = Tool.getValue(tempNode);
      tempNode = Tool.getChildNodeByTagName(elem, "createdDate");
      createdDate = getDateData(tempNode);
      tempNode = Tool.getChildNodeByTagName(elem, "modifiedUsername");
      modifiedUsername = Tool.getValue(tempNode);
      tempNode = Tool.getChildNodeByTagName(elem, "modifiedDate");
      modifiedDate = getDateData(tempNode);
      this.hash = -1;

      Element propnode = Tool.getChildNodeByTagName(elem, "properties");

      if(propnode != null) {
         parseProperties(propnode);
      }
   }

   /**
    * Retrieves the data from the XML Element and converts it to a Date.
    * @param element the element with date data
    * @return  the date, or null if no data or parse error
    */
   private Date getDateData(Element element) {
      String value = Tool.getValue(element);

      try {
         if(value != null) {
            return Tool.parseDateTime(value);
         }
      }
      catch (ParseException e) {
         // ignore
      }

      return null;
   }

   /**
    * Parse the properties.
    * @param elem the specified xml element.
    */
   private void parseProperties(Element elem) throws Exception {
      NodeList list = elem.getChildNodes();

      for(int i = 0; i < list.getLength(); i++) {
         if(!(list.item(i) instanceof Element)) {
            continue;
         }

         Element propnode = (Element) list.item(i);
         Element keynode = Tool.getChildNodeByTagName(propnode, "key");
         Element valnode = Tool.getChildNodeByTagName(propnode, "value");
         String key = Tool.getValue(keynode);
         String val = Tool.getValue(valnode);

         // built-in property, shouldn't be persistent
         if(key != null && (key.equals("mv_ignored") || key.equals("mv_creation_failed"))) {
            continue;
         }

         setProperty(key, val);
      }
   }

   /**
    * Reset mv options.
    */
   public void resetMVOptions() {
      setProperty("mv_ignored", null);
      setProperty("mv_creation_failed", null);
   }

   /**
    * Parse data from an InputStream.
    * @param input the source DataInputStream.
    * @return <tt>true</tt> if successfully parsed, <tt>false</tt> otherwise.
    */
   @Override
   public boolean parseData(DataInputStream input) {
      // do nothing
      return true;
   }

   /**
    * To string identifier.
    * @return the string identifier of the asset entry.
    */
   public String toIdentifier() {
      if(identifier == null) {
         identifier = scope + "^" + type.id + "^" +
            (user == null ? NULL : user.convertToKey()) + "^" + path + "^" + orgID;
      }

      return identifier;
   }

   /**
    * If the entry is sheet, get fullpath and scope as the sheet name.
    */
   public String getSheetName() {
      if(type == null || !isSheet()) {
         return "";
      }

      StringBuilder root = new StringBuilder();

      if(isWorksheet()) {
         root.append("Worksheet: ");
      }
      else if(isViewsheet()) {
         root.append("Viewsheet: ");
      }

      if(scope == AssetRepository.GLOBAL_SCOPE) {
         root.append("(Global Scope) ");
      }
      else if(scope == AssetRepository.REPORT_SCOPE) {
         root.append("(Report Scope) ");
      }
      else if(scope == AssetRepository.USER_SCOPE) {
         root.append("(User Scope) ");
      }
      else if(scope == AssetRepository.QUERY_SCOPE) {
         root.append("(Query Repository Scope) ");
      }
      else if(scope == AssetRepository.TEMPORARY_SCOPE) {
         root.append("(Temporary Scope) ");
      }

      root.append(getPath());

      return root.toString();
   }

   /**
    * Clone the object.
    * @return the cloned object.
    */
   @Override
   public Object clone() {
      try {
         AssetEntry entry = (AssetEntry) super.clone();
         entry.prop = Object2ObjectMaps.synchronize(new Object2ObjectOpenHashMap<>(prop));
         return entry;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone object", ex);
         return null;
      }
   }

   /**
    * Create a clone of an asset entry, except with a different organization ID.
    * @return the created asset entry.
    */
   public AssetEntry cloneAssetEntry (String orgID) {
      AssetEntry newEntry = (AssetEntry) clone();
      newEntry.orgID = orgID;

      return newEntry;
   }

   /**
    * Get the created date
    * @return  the created date
    */
   public Date getCreatedDate() {
      return createdDate;
   }

   /**
    * Set the created date
    * @param createdDate   the created date
    */
   public void setCreatedDate(Date createdDate) {
      this.createdDate = createdDate;
   }

   /**
    * Get the created username
    * @return  the created username
    */
   public String getCreatedUsername() {
      return createdUsername;
   }

   /**
    * Set the created username
    * @param createdUsername  the created username
    */
   public void setCreatedUsername(String createdUsername) {
      this.createdUsername = createdUsername;
   }

   /**
    * Get the modified date
    * @return  the modified date
    */
   public Date getModifiedDate() {
      return modifiedDate;
   }

   /**
    * Set the modified date
    * @param modifiedDate  the modified date
    */
   public void setModifiedDate(Date modifiedDate) {
      this.modifiedDate = modifiedDate;
   }

   /**
    * Get the modified username
    * @return  the modified username
    */
   public String getModifiedUsername() {
      return modifiedUsername;
   }

   /**
    * Set modified username
    * @param modifiedUsername the modified username
    */
   public void setModifiedUsername(String modifiedUsername) {
      this.modifiedUsername = modifiedUsername;
   }

   public void copyMetaData(AssetEntry oentry) {
      setModifiedDate(oentry.getModifiedDate());
      setModifiedUsername(oentry.getModifiedUsername());
      setCreatedDate(oentry.getCreatedDate());
      setCreatedUsername(oentry.getCreatedUsername());
   }

   private static final String NULL = "__NULL__";
   private static final String CLS = " class=\"inetsoft.uql.asset.AssetEntry\"";

   private int scope;
   private Type type;
   private IdentityID user;
   private String path;
   private String orgID = null;
   private String alias;
   private List<String> favoritesUser = new ArrayList<>();
   private Object2ObjectMap<String, String> prop;
   private String createdUsername;  // @by davidd feature1333116098541
   private Date createdDate;
   private String modifiedUsername;
   private Date modifiedDate;

   private int hash;
   private transient String identifier;
   private transient String ppath;
   private transient AssetEntry pentry;
   private transient String cachedName;

   private static final Collator CT =
      Locale.getDefault().getLanguage().equals("en") ? null : Collator_CN.getCollator();

   private static final Logger LOG =
      LoggerFactory.getLogger(AssetEntry.class);

   public static final class Serializer extends StdSerializer<AssetEntry> {
      public Serializer() {
         super(AssetEntry.class);
      }

      @Override
      public void serialize(AssetEntry entry, JsonGenerator generator,
                            SerializerProvider provider) throws IOException
      {
         generator.writeStartObject();
         generator.writeNumberField("scope", entry.getScope());
         generator.writeStringField("type", entry.getType().name());
         generator.writeStringField("user", entry.getUser() == null ? null : entry.getUser().convertToKey());
         generator.writeStringField("path", entry.getPath());
         generator.writeStringField("alias", entry.getAlias());
         generator.writeStringField("favoritesUser", entry.getFavoritesUser());
         generator.writeStringField("identifier", entry.toIdentifier());
         generator.writeStringField("description", entry.getDescription());
         generator.writeBooleanField("folder", entry.isFolder());

         if(entry.getCreatedDate() != null) {
            generator.writeStringField("createdUsername", entry.getCreatedUsername());
            generator.writeNumberField("createdDate", entry.getCreatedDate().getTime());
         }

         if(entry.getModifiedDate() != null) {
            generator.writeStringField("modifiedUsername", entry.getModifiedUsername());
            generator.writeNumberField("modifiedDate", entry.getModifiedDate().getTime());
         }

         generator.writeObjectFieldStart("properties");

         for(String name : new HashSet<>(entry.getPropertyKeys())) {
            String value = entry.getProperty(name);
            String key = name;

            if(Boolean.TRUE.equals(provider.getAttribute(KeyValueEngine.ENCODE_PROPERTY_NAMES))) {
               // Bug #66067, avoid reserved names in storage backends
               key = "PROP_" + name;
            }

            generator.writeStringField(key, value);
         }

         generator.writeEndObject(); // properties
         generator.writeEndObject(); // asset entry
      }
   }

   public static final class Deserializer extends StdDeserializer<AssetEntry> {
      public Deserializer() {
         super(AssetEntry.class);
      }

      @Override
      public AssetEntry deserialize(JsonParser parser, DeserializationContext context)
         throws IOException
      {
         JsonNode node = parser.getCodec().readTree(parser);
         AssetEntry entry =
            AssetEntry.createAssetEntry(node.get("identifier").textValue());

         if(entry != null) {
            JsonNode child;

            if((child = node.get("alias")) != null) {
               entry.setAlias(child.textValue());
            }

            if((child = node.get("favoritesUser")) != null) {
               entry.addFavoritesUser(child.textValue());
            }

            if((child = node.get("createdUsername")) != null) {
               entry.setCreatedUsername(child.textValue());
            }

            if((child = node.get("createdDate")) != null) {
               entry.setCreatedDate(new Date(child.longValue()));
            }

            if((child = node.get("modifiedUsername")) != null) {
               entry.setModifiedUsername(child.textValue());
            }

            if((child = node.get("modifiedDate")) != null) {
               entry.setModifiedDate(new Date(child.longValue()));
            }

            if((child = node.get("properties")) != null) {
               for(Iterator<Map.Entry<String, JsonNode>> i = child.fields(); i.hasNext();)
               {
                  Map.Entry<String, JsonNode> e = i.next();
                  String name = e.getKey();

                  if(Boolean.TRUE.equals(context.getAttribute(KeyValueEngine.ENCODE_PROPERTY_NAMES)) &&
                     name.startsWith("PROP_"))
                  {
                      // Bug #66067, avoid reserved names in storage backends
                     name = name.substring(5);
                  }

                  String value = null;

                  if(e.getValue() != null) {
                     value = e.getValue().textValue();
                  }

                  entry.setProperty(name, value);
               }
            }
         }

         return entry;
      }
   }

   public static final String PATH_ARRAY_SEPARATOR = "^_^";
}
