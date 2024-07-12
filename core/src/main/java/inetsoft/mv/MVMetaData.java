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
package inetsoft.mv;

import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.erm.ExpressionRef;
import inetsoft.uql.viewsheet.CalculateRef;
import inetsoft.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.PrintWriter;
import java.io.Serializable;
import java.util.*;

/**
 * MVMetaData, class to store mv information.
 *
 * @author InetSoft Technology
 * @version 12.0
 */
public class MVMetaData implements XMLSerializable, Serializable, Cloneable {
   public MVMetaData() {
      super();
   }

   public MVMetaData(String wsId, String boundTable, String mvTable) {
      this();
      this.wsId = wsId;
      this.boundTable = boundTable;
      this.mvTable = mvTable;
   }

   /**
    * Return if create Materialized View for all users in the groups or not.
    */
   public boolean isGroupExpanded() {
      return groupExpanded;
   }

   /**
    * Set if create Materialized View for all users in the groups or not
    */
   public void setGroupExpanded(boolean expanded) {
      groupExpanded = expanded;
   }

   /**
    * Check if the VPM should be ignored when generating MV.
    */
   public boolean isBypassVPM() {
      return bypass;
   }

   /**
    * Set if the VPM should be ignored when generating MV.
    */
   public void setBypassVPM(boolean bypass) {
      this.bypass = bypass;
   }

   /**
    * Check if the MV should contain full data.
    */
   public boolean isFullData() {
      return full;
   }

   /**
    * Set if the MV should contain full data. If not, only data that's
    * necessary for the current viewsheet is included. If the viewsheet
    * is edited, the MV may not be usable anymore.
    */
   public void setFullData(boolean full) {
      this.full = full;
   }

   /**
    * Set ws table plan
    */
   public void setPlan(String plan) {
      this.plan = plan;
   }

   /**
    * Set mv break column.
    */
   public void setBreakColumn(String breakColumn) {
      this.breakColumn = breakColumn;
   }

   /**
    * Get ws name.
    */
   public String getWsId() {
      return wsId;
   }

   public String getWsPath() {
      return AssetEntry.createAssetEntry(wsId).getPath();
   }

   /**
    * Get bound table name.
    */
   public String getBoundTable() {
      return boundTable;
   }

   public boolean isWSMV() {
      return wsMV;
   }

   public void setWSMV(boolean wsMV) {
      this.wsMV = wsMV;
   }

   /**
    * Register a sheet to this mv.
    * @param sheetId the viewsheet that uses this MV.
    */
   public void register(String sheetId) {
      sheetIds.add(sheetId);
   }

   public void register(String sheetId, String tableName) {
      sheetIds.add(sheetId);
      Set<String> tableNames =
         tableNameMap.computeIfAbsent(sheetId, set -> new LinkedHashSet<>());
      tableNames.add(tableName);
   }

   public void register(MVMetaData data) {
      for(String sheetId : data.sheetIds) {
         register(sheetId);

         if(wsMV) {
            Set<String> tableNamesToAdd = data.tableNameMap.get(sheetId);

            if(tableNamesToAdd != null) {
               Set<String> tableNames =
                  tableNameMap.computeIfAbsent(sheetId, set -> new LinkedHashSet<>());
               tableNames.addAll(tableNamesToAdd);
            }
         }
      }
   }

   /**
    * Validate a sheet which is invalid in this mv.
    */
   public void validRegister(String... sheetIds) {
      for(String sheetId : sheetIds) {
         invalidSheets.remove(sheetId);
      }
   }

   /**
    * Invalid a sheet which is registered to this mv.
    */
   public void invalidRegister(String... sheetIds) {
      invalidSheets.addAll(Arrays.asList(sheetIds));
   }

   /**
    * Unregister a sheet from this mv.
    */
   public boolean unregister(String sheetId) {
      return unregister(sheetId, true);
   }

   public boolean unregister(String sheetId, boolean removeTableNames) {
      boolean changed = sheetIds.contains(sheetId) || invalidSheets.contains(sheetId);
      sheetIds.remove(sheetId);
      validRegister(sheetId);

      if(removeTableNames) {
         tableNameMap.remove(sheetId);
      }

      return changed;
   }

   public void renameRegistered(String oldSheetName, String newSheetName) {
      unregister(oldSheetName, false);
      register(newSheetName);

      if(wsMV) {
         tableNameMap.put(newSheetName, tableNameMap.remove(oldSheetName));
      }
   }

   /**
    * Check if a sheet is registered to this mv.
    */
   public boolean isRegistered(String sheetId) {
      return sheetIds.contains(sheetId);
   }

   public boolean isRegistered(String sheetId, String tableName) {
      if(!sheetIds.contains(sheetId)) {
         return false;
      }

      Set<String> tableNames = tableNameMap.get(sheetId);
      return tableNames != null && tableNames.contains(tableName);
   }

   public String[] getRegisteredSheets() {
      return sheetIds.toArray(new String[0]);
   }

   /**
    * Check if this mv is valid or not.
    */
   public boolean isValid() {
      return invalidSheets.isEmpty();
   }

   /**
    * Set the column list of this MV.
    */
   public void setColumns(ColumnSelection columns) {
      this.columns = columns;
   }

   /**
    * Get the column list of this MV.
    */
   public ColumnSelection getColumns() {
      return columns;
   }

   public Map<String, Set<String>> getTableNameMap() {
      return tableNameMap;
   }

   public void setWSMVPlan(String wsMVPlan) {
      this.wsMVPlan = wsMVPlan;
   }

   public boolean isSharedBy(MVMetaData data) {
      SHARE_HINT.remove();

      if(wsMV != data.wsMV) {
         return false;
      }

      if(wsMV) {
         if(!equalsString(data.wsMVPlan, wsMVPlan)) {
            return false;
         }

         if(!equalContainsCols(data)) {
            return false;
         }

         if(!bypass || !data.bypass) {
            // the query plan may contain the worksheet and table name in it but since it's
            // irrelevant for checking WS MV equality try to replace it here
            String dataPlan = data.plan.replace(data.wsId + "." + data.boundTable,
                                                wsId + "." + boundTable);

            if(!equalsString(dataPlan, plan)) {
               setHint(Catalog.getCatalog().getString("mv.vs.plan"));
               return false;
            }
         }
      }
      else {
         if(!equalsString(data.wsId, wsId)) {
            return false;
         }

         if(!equalsString(data.boundTable, boundTable)) {
            return false;
         }

         if(!equalsString(data.mvTable, mvTable)) {
            return false;
         }

         // target no break column or break column is same
         if(data.breakColumn != null && !equalsString(data.breakColumn, breakColumn)) {
            setHint(Catalog.getCatalog().getString("mv.vs.breakcolumn"));
            return false;
         }

         if(!equalContainsCols(data)) {
            return false;
         }

         if(!bypass || !data.bypass) {
            if(!equalsString(data.plan, plan)) {
               setHint(Catalog.getCatalog().getString("mv.vs.plan"));
               return false;
            }
         }
      }

      return true;
   }

   public String toString() {
      return "MVMetaData [" + wsId + " , " + mvTable +
         ", " + boundTable + ", " + breakColumn + ", " +
         columns + ", " + sheetIds + "]";
   }

   /**
    * Method to parse an xml segment.
    */
   @Override
   public void parseXML(Element tag) throws Exception {
      groupExpanded = "true".equals(Tool.getAttribute(tag, "groupExpanded"));
      bypass = "true".equals(Tool.getAttribute(tag, "bypass"));
      full = "true".equals(Tool.getAttribute(tag, "full"));
      wsMV = "true".equals(Tool.getAttribute(tag, "wsMV"));
      wsId = Tool.getChildValueByTagName(tag, "wsId");
      mvTable = Tool.getChildValueByTagName(tag, "mvTable");
      boundTable = Tool.getChildValueByTagName(tag, "boundTable");
      breakColumn = Tool.getChildValueByTagName(tag, "breakColumn");
      plan = Tool.getChildValueByTagName(tag, "plan");
      wsMVPlan = Tool.getChildValueByTagName(tag, "wsMVPlan");
      List<String> arr = parseArray(tag, "sheetIds", "sheetId");

      for(String str : arr) {
         sheetIds.add(str);
      }

      arr = parseArray(tag, "invalidSheets", "sheetId");

      for(String str : arr) {
         invalidSheets.add(str);
      }

      Element cnode = Tool.getChildNodeByTagName(tag, "ColumnSelection");

      if(cnode != null) {
         columns = new ColumnSelection();
         columns.parseXML(cnode);
      }

      Element tableNameMapNode = Tool.getChildNodeByTagName(tag, "tableNameMap");

      if(tableNameMapNode != null) {
         NodeList list = tableNameMapNode.getChildNodes();

         for(int i = 0; i < list.getLength(); i++) {
            if(!(list.item(i) instanceof Element)) {
               continue;
            }

            Element entryNode = (Element) list.item(i);
            Element sheetNameNode = Tool.getChildNodeByTagName(entryNode, "sheetId");
            String sheetId = Tool.getValue(sheetNameNode);
            Set<String> tableNames =
               new LinkedHashSet<>(parseArray(entryNode, "tableNames", "tableName"));
            tableNameMap.put(sheetId, tableNames);
         }
      }
   }

   private List<String> parseArray(Element tag, String nodeTag, String cdataTag)
   {
      List<String> arr = new ArrayList<>();
      Element vnames = Tool.getChildNodeByTagName(tag, nodeTag);

      if(vnames != null) {
         NodeList list = Tool.getChildNodesByTagName(vnames, cdataTag);

         for(int i = 0; i < list.getLength(); i++) {
            Element node = (Element) list.item(i);
            String value = Tool.getValue(node);

            if(value != null) {
               arr.add(value);
            }
         }
      }

      return arr;
   }

   /**
    * Write the xml segment to print writer.
    *
    * @param writer the destination print writer.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.print("<MVMetaData");
      writer.print(" groupExpanded=\"" + groupExpanded + "\"");
      writer.print(" bypass=\"" + bypass + "\"");
      writer.print(" full=\"" + full + "\"");
      writer.print(" wsMV=\"" + wsMV + "\"");
      writer.println(">");
      writeCDATA(writer, "wsId", wsId);
      writeCDATA(writer, "mvTable", mvTable);
      writeCDATA(writer, "boundTable", boundTable);
      writeCDATA(writer, "breakColumn", breakColumn);
      writeCDATA(writer, "plan", plan);
      writeCDATA(writer, "wsMVPlan", wsMVPlan);
      writeArray(writer, "sheetIds", "sheetId", sheetIds.toArray(new String[0]));
      writeArray(writer, "invalidSheets", "sheetId", invalidSheets.toArray(new String[0]));

      if(columns != null) {
         columns.writeXML(writer);
      }

      writer.println("<tableNameMap>");

      for(String sheetId : tableNameMap.keySet()) {
         writer.println("<entry>");
         writer.print("<sheetId>");
         writer.print("<![CDATA[" + sheetId + "]]>");
         writer.print("</sheetId>");

         Set<String> tableNames = tableNameMap.get(sheetId);
         writeArray(writer, "tableNames", "tableName", tableNames.toArray(new String[0]));

         writer.println("</entry>");
      }

      writer.println("</tableNameMap>");
      writer.println("</MVMetaData>");
   }

   private void writeArray(PrintWriter writer, String nodeTag,
                           String cdataTag, String[] arr)
   {
      if(arr == null) {
         return;
      }

      writer.println("<" + nodeTag + ">");

      for(String data : arr) {
         writeCDATA(writer, cdataTag, data);
      }

      writer.println("</" + nodeTag + ">");
   }

   /**
    * Make a copy of this MVMetaData.
    */
   @Override
   public Object clone() {
      try {
         MVMetaData def = (MVMetaData) super.clone();
         return def;
      }
      catch(Exception ex) {
         LOG.error(
            "Failed to clone materialized view meta data", ex);
      }

      return null;
   }

   private void writeCDATA(PrintWriter writer, String tag, String value) {
      if(value != null) {
         writer.print("<" + tag + "><![CDATA[" + value + "]]></" + tag + ">");
      }
   }

   private boolean equalContainsCols(MVMetaData data) {
      ColumnSelection cols = data.columns;

      if(cols == null) {
         return true;
      }

      if(columns == null) {
         return false;
      }

      boolean contains = true;

      for(int i = 0; i < cols.getAttributeCount(); i++) {
         DataRef ref = cols.getAttribute(i);

         if(!containsColumn(ref)) {
            contains = false;
            break;
         }
      }

      return contains;
   }

   private boolean containsColumn(DataRef ref) {
      DataRef ref2 = columns.findAttribute(ref);

      if(ref2 == null) {
         setHint(Catalog.getCatalog().getString(
            "mv.vs.column.notfound", ref.getName()));
         return false;
      }

      boolean calc = ref instanceof CalculateRef;
      boolean calc2 = ref2 instanceof CalculateRef;

      if(calc != calc2) {
         return false;
      }

      // not calculate field?
      if(!calc) {
         return true;
      }

      // calculate field
      ExpressionRef eref = getExpression(ref);
      ExpressionRef eref2 = getExpression(ref2);

      if(eref == null || eref2 == null) {
         return eref == eref2;
      }

      boolean sameExp = eref.equalsContent(eref2);

      if(!sameExp) {
         setHint(Catalog.getCatalog().getString("mv.vs.calc", ref.getName()));
      }

      return sameExp;
   }

   private ExpressionRef getExpression(DataRef ref) {
      ref = ((CalculateRef) ref).getDataRef();
      return ref instanceof ExpressionRef ? (ExpressionRef) ref : null;
   }

   private boolean equalsString(String str1, String str2) {
      if(str1 == null || str2 == null) {
         return str1 == str2;
      }

      return str1.equals(str2);
   }

   private void setHint(String hint) {
      SHARE_HINT.set(hint);
   }

   public static final ThreadLocal<String> SHARE_HINT = new ThreadLocal<>();
   private static final Logger LOG = LoggerFactory.getLogger(MVMetaData.class);
   private String wsId;
   private String mvTable;
   private String boundTable;
   private String breakColumn;
   private Set<String> sheetIds = new HashSet<>();
   private Set<String> invalidSheets = new HashSet<>();
   private Map<String, Set<String>> tableNameMap = new LinkedHashMap<>();
   private ColumnSelection columns;
   private boolean groupExpanded;
   private boolean bypass;
   private boolean full;
   private String plan;
   private String wsMVPlan;
   private boolean wsMV;
}
