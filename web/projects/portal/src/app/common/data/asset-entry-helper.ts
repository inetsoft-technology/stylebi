/*
 * inetsoft-web - StyleBI is a business intelligence web application.
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
import { AssetType } from "../../../../../shared/data/asset-type";
import { AssetEntry } from "../../../../../shared/data/asset-entry";
import { Tool } from "../../../../../shared/util/tool";

export class AssetEntryHelper {
   /**
    * Cube column is dimension.
    */
   static get DIMENSIONS(): number {
      return 0;
   }

   /**
    * Cube column is measure.
    */
   static get MEASURES(): number {
      return 1;
   }

   /**
    * Cube column is date dimension.
    */
   static get DATE_DIMENSIONS(): number {
      return 2;
   }

   /**
    * Query scope asset.
    */
   public static get QUERY_SCOPE(): number {
      return 0;
   }

   /**
    * Global scope asset.
    */
   public static get GLOBAL_SCOPE(): number {
      return 1;
   }

   /**
    * Report scope asset.
    */
   public static get REPORT_SCOPE(): number {
      return 2;
   }

   /**
    * User scope asset.
    */
   public static get USER_SCOPE(): number {
      return 4;
   }

   /**
    * Temporary scope asset.
    */
   public static get TEMPORARY_SCOPE(): number {
      return 8;
   }

   /**
    * Temporary scope asset.
    */
   public static get COMPONENT_SCOPE(): number {
      return 16;
   }

   /**
    * Temporary scope asset.
    */
   public static get REPOSITORY_SCOPE(): number {
      return 32;
   }

   /**
    * Cube column type.
    */
   static get CUBE_COL_TYPE(): string {
      return "cube.column.type";
   }

   /**
    * Cube query type.
    */
   static get CUBE(): string {
      return "cube";
   }

   /**
    * Check if is root.
    * @return <code>true</code> if is root, <code>false</code> otherwise.
    */
   static isRoot(entry: AssetEntry): boolean {
      return entry && entry.path == "/";
   }

   /**
    * Check if is a folder entry.
    * @return <code>true</code> if yes, false otherwise.
    */
   static isFolder(entry: AssetEntry): boolean {
      return entry.folder;
   }

   /**
    * Check if is a worksheet entry.
    * @return <code>true</code> if yes, <code>false</code> otherwise.
    */
   static isWorksheet(entry: AssetEntry): boolean {
      return entry.type == AssetType.WORKSHEET;
   }

   /**
    * Check if is a tablestyle entry.
    * @return <code>true</code> if yes, <code>false</code> otherwise.
    */
   static isTableStyle(entry: AssetEntry): boolean {
      return entry.type == AssetType.TABLE_STYLE;
   }

   /**
    * Check if is a script entry.
    * @return <code>true</code> if yes, <code>false</code> otherwise.
    */
   static isScript(entry: AssetEntry): boolean {
      return entry.type == AssetType.SCRIPT;
   }

   /**
    * Check if is a script folder entry.
    * @return <code>true</code> if yes, <code>false</code> otherwise.
    */
   static isScriptFolder(entry: AssetEntry): boolean {
      return entry.type == AssetType.SCRIPT_FOLDER;
   }

   /**
    * Check if is a library folder entry.
    * @return <code>true</code> if yes, <code>false</code> otherwise.
    */
   static isLibraryFolder(entry: AssetEntry): boolean {
      return entry.type == AssetType.LIBRARY_FOLDER;
   }

   /**
    * Check if is a viewsheet entry.
    * @return <code>true</code> if yes, <code>false</code> otherwise.
    */
   static isViewsheet(entry: AssetEntry): boolean {
      return entry.type == AssetType.VIEWSHEET;
   }

   /**
    * Check if is a snapshot entry.
    * @return <code>true</code> if yes, <code>false</code> otherwise.
    */
   static isVSSnapshot(entry: AssetEntry): boolean {
      return entry.type == AssetType.VIEWSHEET_SNAPSHOT;
   }

   /**
    * Check if is a sheet entry.
    * @return <code>true</code> if yes, <code>false</code> otherwise.
    */
   static isSheet(entry: AssetEntry): boolean {
      return AssetEntryHelper.isWorksheet(entry) || AssetEntryHelper.isViewsheet(entry);
   }

   /**
    * Check if is a data entry.
    * @return <code>true</code> if yes, <code>false</code> otherwise.
    */
   static isData(entry: AssetEntry): boolean {
      return entry.type == AssetType.DATA;
   }

   /**
    * Check if is a column entry.
    * @return <code>true</code> if yes, <code>false</code> otherwise.
    */
   static isColumn(entry: AssetEntry): boolean {
      return entry.type == AssetType.COLUMN || entry.type == AssetType.PHYSICAL_COLUMN;
   }

   /**
    * Check if is a table entry.
    * @return <code>true</code> if yes, <code>false</code> otherwise.
    */
   static isTable(entry: AssetEntry): boolean {
      return entry.type == AssetType.TABLE;
   }

   /**
    * Check if is a query entry.
    * @return <code>true</code> if yes, <code>false</code> otherwise.
    */
   static isQuery(entry: AssetEntry): boolean {
      return entry.type == AssetType.QUERY;
   }

   /**
    * Check if is a logic model entry.
    * @return <code>true</code> if yes, <code>false</code> otherwise.
    */
   static isLogicModel(entry: AssetEntry): boolean {
      return entry.type == AssetType.LOGIC_MODEL;
   }

   /**
    * Check if is a physical folder entry.
    * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
    */
   static isPhysicalFolder(entry: AssetEntry): boolean {
      return entry.type == AssetType.PHYSICAL_FOLDER;
   }

   /**
    * Check if is a repository folder entry.
    * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
    */
   static isRepositoryFolder(entry: AssetEntry): boolean {
      return entry.type == AssetType.REPOSITORY_FOLDER;
   }

   /**
    * Check if is a physical table entry.
    * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
    */
   static isPhysicalTable(entry: AssetEntry): boolean {
      return entry.type == AssetType.PHYSICAL_TABLE;
   }

   /**
    * Check if is a data source entry.
    * @return <code>true</code> if yes, <code>false</code> otherwise.
    */
   static isDataSource(entry: AssetEntry): boolean {
      return entry.type == AssetType.DATA_SOURCE;
   }

   /**
    * Check if is a data source folder entry.
    * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
    */
   static isDataSourceFolder(entry: AssetEntry): boolean {
      return entry.type == AssetType.DATA_SOURCE_FOLDER;
   }

   /**
    * Check if is a variable entry.
    * @return <code>true</code> if yes, <code>false</code> otherwise.
    */
   static isVariable(entry: AssetEntry): boolean {
      return entry.type == AssetType.VARIABLE;
   }

   /**
    * Get the name of the asset entry.
    * @return the name of the asset entry.
    */
   static getEntryName(entry: AssetEntry): string {
      if(AssetEntryHelper.isRoot(entry)) {
         return "";
      }

      let ppath: string = AssetEntryHelper.getParentPath(entry);
      let path: string = entry.path;

      if(ppath == "/") {
         return path;
      }

      return Tool.replaceStr(path.substring(ppath.length + 1), "\\^_\\^", "/");
   }

   /**
    * Get the parent path of the asset entry.
    * @return the parent path of the asset entry.
    */
   static getParentPath(entry: AssetEntry): string {
      if(AssetEntryHelper.isRoot(entry)) {
         return null;
      }

      let name: string = null;
      let ppath: string = null;
      let path: string = entry.path || "";

      switch(entry.type) {
      case AssetType.COLUMN:
      case AssetType.PHYSICAL_COLUMN:
         name = entry["attribute"];
         break;
      case AssetType.TABLE:
      case AssetType.PHYSICAL_TABLE:
         name = entry["entity"];
         break;
      default:
         break;
      }

      let parr: string = entry["entry.paths"];

      if(parr != null) {
         let arr: Array<string> = parr.split(AssetEntryHelper.PATH_ARRAY_SEPARATOR);

         if(arr.length == 1) {
            ppath = "/";
         }
         else {
            ppath = "";

            for(let i = 0; i < arr.length - 1; i++) {
               if(i > 0) {
                  ppath += "/";
               }

               ppath += arr[i];
            }
         }
      }

      if(ppath == null && name != null && path.lastIndexOf(name) >= 0) {
         ppath = path.substring(0, path.length - name.length - 1);
      }

      if(ppath == null) {
         let index: number = path.lastIndexOf("/");
         ppath = index >= 0 ? path.substring(0, index) : "/";
      }

      return ppath;
   }

   /**
    * Get the parent entry of the asset entry.
    * @return the parent entry of the asset entry.
    */
   static getParent(entry: AssetEntry): AssetEntry {
      let ppath: string = AssetEntryHelper.getParentPath(entry);

      if(ppath == null) {
         return null;
      }

      let pentry = <AssetEntry> {
         scope: entry.scope,
         type: AssetType.FOLDER,
         path: ppath,
         user: entry.user
      };

      pentry.properties = Tool.clone(entry.properties);
      let parr: string = entry.properties["entry.paths"];

      if(parr != null) {
         let index: number = parr.lastIndexOf(AssetEntryHelper.PATH_ARRAY_SEPARATOR);
         pentry.properties["entry.paths"] = index > 0 ? parr.substring(0, index) : null;
      }

      return pentry;
   }

   static getParentName(entry: AssetEntry): string {
      let parentPath: string = AssetEntryHelper.getParentPath(entry);
      let parentName: string = "";

      if(parentPath && parentPath.lastIndexOf("/") != -1) {
         parentName =
            parentPath.substring(parentPath.lastIndexOf("/") + 1, parentPath.length);
      }

      return parentName;
   }

   /**
    * Check if is allowed to create cube expression measure.
    */
   static couldCreateCubeMeasure(entry: AssetEntry): boolean {
      if(entry) {
         let properties: any = entry.properties;

         return properties["CUBE_TABLE"] == "true" && properties["sqlServer"] == "true";
      }

      return false;
   }

   /**
    * Check if is allowed to edit/remove cube expression measure.
    */
   static couldEditCubeMeasure(entry: AssetEntry): boolean {
      if(entry) {
         let properties: any = entry.properties;

         return properties[AssetEntryHelper.CUBE_COL_TYPE] &&
            properties["sqlServer"] == "true" && properties["expression"];
      }

      return false;
   }

   /**
    * To string identifier.
    * @return the string identifier of the asset entry.
    */
   static toIdentifier(entry: AssetEntry): string {
      return entry ? entry.scope + "^" + entry.type + "^" +
         (entry.user == null ? "__NULL__" : entry.user) + "^" + entry.path : null;
   }

   /**
    * Check if entry is editable
    * @param entry the entry to check
    * @returns true if entry is editable, false otherwise
    */
   static isEditable(entry: AssetEntry): boolean {
      return entry.scope !== AssetEntryHelper.QUERY_SCOPE && !AssetEntryHelper.isRoot(entry);
   }

   static equalsEntry(entry1: AssetEntry, entry2: AssetEntry): boolean {
      return entry1.user == entry2.user && entry1.path == entry2.path &&
         entry1.type == entry2.type && entry1.scope == entry2.scope;
   }

   static PATH_ARRAY_SEPARATOR = "^_^";
}
