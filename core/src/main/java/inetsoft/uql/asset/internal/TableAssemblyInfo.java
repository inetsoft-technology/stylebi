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
package inetsoft.uql.asset.internal;

import inetsoft.report.internal.Util;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.viewsheet.VSCompositeFormat;
import inetsoft.util.Tool;
import inetsoft.util.xml.VersionControlComparators;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;

/**
 * TableAssemblyInfo stores basic table assembly information.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public class TableAssemblyInfo extends WSAssemblyInfo {
   /**
    * Constructor.
    */
   public TableAssemblyInfo() {
      super();

      iselection = new ColumnSelection();
      oselection = new ColumnSelection();
      mrow = -1;
      mdrow = -1;
      distinct = false;
      live = false;
      runtime = false;
      editMode = false;
      aggregate = false;
      mergeable = true;
      visibleTable = true;
      hasAggregate = false;
      hasCondition = false;
      hasExpression = false;
      hasSort = false;
      runtimeSelected = false;
   }

   /**
    * Check if the assembly is iconized.
    * @return <tt>true</tt> if iconized, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isIconized() {
      return super.isIconized() && !isRuntime();
   }

   /**
    * Get the private column selection.
    * @return the private column selection of the table assembly.
    */
   public ColumnSelection getPrivateColumnSelection() {
      return iselection;
   }

   /**
    * Set the private column selection.
    * @param selection the specified selection.
    */
   public void setPrivateColumnSelection(ColumnSelection selection) {
      this.iselection = selection;
   }

   /**
    * Get the public column selection.
    * @return the public column selection of the table assembly.
    */
   public ColumnSelection getPublicColumnSelection() {
      // is a null selection?
      boolean nflag = !"false".equals(oselection.getProperty("null"));

      if(oselection.getAttributeCount() == 0 && nflag) {
         oselection = iselection.clone();

         for(int i = oselection.getAttributeCount() - 1; i >= 0; i--) {
            ColumnRef column = (ColumnRef) oselection.getAttribute(i);

            if(!column.isVisible()) {
               oselection.removeAttribute(i);
            }
         }
      }

      return oselection;
   }

   /**
    * Set the public column selection.
    * @param selection the specified selection.
    */
   public void setPublicColumnSelection(ColumnSelection selection) {
      this.oselection = selection;
   }

   /**
    * Get the maximum rows.
    * @return the maximum rows of the table assembly.
    */
   public int getMaxRows() {
      // Feature #39140, always respect the global row limit
      return Util.getQueryLocalRuntimeMaxrow(mrow);
   }

   /**
    * Set the maximum rows.
    * @param row the specified maximum rows.
    */
   public void setMaxRows(int row) {
      this.mrow = row;
   }

   /**
    * Checks whether the max rows setting has been defined by a user.
    * @return <tt>true</tt> if max rows is user defined; <tt>false</tt> otherwise
    */
   public boolean isUserMaxRows() {
      return mrow > 0;
   }

   /**
    * Get the maximum display rows.
    * @return the maximum display rows of the table assembly.
    */
   public int getMaxDisplayRows() {
      return Util.getQueryLocalPreviewMaxrow(mdrow);
   }

   /**
    * Set the maximum display rows.
    * @param row the specified maximum display rows.
    */
   public void setMaxDisplayRows(int row) {
      this.mdrow = row;
   }

   /**
    * Check if only show distinct values.
    * @return <tt>true</tt> to show distinct values only,
    * <tt>false</tt> otherwise.
    */
   public boolean isDistinct() {
      return distinct;
   }

   /**
    * Set the distinct option.
    * @param distinct <tt>true</tt> to show distinct values only,
    * <tt>false</tt> otherwise.
    */
   public void setDistinct(boolean distinct) {
      this.distinct = distinct;
   }

   /**
    * Check if show live data.
    * @return <tt>true</tt> to show live data, <tt>false</tt> to show metadata.
    */
   public boolean isLiveData() {
      return live;
   }

   /**
    * Set the live data option.
    * @param live <tt>true</tt> to show live data, <tt>false</tt>
    * to show metadata.
    */
   public void setLiveData(boolean live) {
      this.live = live;
   }

   /**
    * Check if the table is in runtime mode.
    * @return <tt>true</tt> if in runtime mode, <tt>false</tt> otherwise.
    */
   public boolean isRuntime() {
      return runtime;
   }

   /**
    * Set the runtime mode.
    * @param runtime <tt>true</tt> if in runtime mode, <tt>false</tt> otherwise.
    */
   public void setRuntime(boolean runtime) {
      this.runtime = runtime;
   }

   /**
    * Check if the table is in edit mode.
    * @return <tt>true</tt> if in edit mode, <tt>false</tt> otherwise.
    */
   public boolean isEditMode() {
      return editMode;
   }

   /**
    * Set the edit mode.
    * @param editMode <tt>true</tt> if in edit mode, <tt>false</tt> otherwise.
    */
   public void setEditMode(boolean editMode) {
      this.editMode = editMode;
   }

   /**
    * Check if is an aggregate.
    * @return <tt>true</tt> if is an aggregate.
    */
   public boolean isAggregate() {
      return aggregate;
   }

   /**
    * Set the aggregate flag.
    * @param aggregate <tt>true</tt> if is an aggregate.
    */
   public void setAggregate(boolean aggregate) {
      this.aggregate = aggregate;
   }

   /**
    * Check if the sql query is mergeable.
    * @return <tt>true</tt> if the sql query is mergeable, <tt>false</tt>
    * otherwise.
    */
   public boolean isSQLMergeable() {
      return mergeable;
   }

   /**
    * Set whether the sql query is mergeable.
    * @param mergeable <tt>true</tt> if the sql query is mergeable,
    * <tt>false</tt> otherwise.
    */
   public void setSQLMergeable(boolean mergeable) {
      this.mergeable = mergeable;
   }

     /**
    * Check if the worksheet is block.
    * @return <tt>true</tt> if the worksheet is block, <tt>false</tt>
    * otherwise.
    */
   public boolean isVisibleTable() {
      return visibleTable;
   }

   /**
    * Set whether the worksheet is block.
    * @param visibleTable <tt>true</tt> if the worksheet is block,
    * <tt>false</tt> otherwise.
    */
   public void setVisibleTable(boolean visibleTable) {
      this.visibleTable = visibleTable;
   }

   /**
    * Check if grouping/aggregate is defined on the table.
    */
   public boolean isAggregateDefined() {
      return hasAggregate;
   }

   /**
    * Set whether grouping/aggregate is defined on the table.
    */
   public void setAggregateDefined(boolean flag) {
      this.hasAggregate = flag;
   }

   /**
    * Check if condition is defined on the table.
    */
   public boolean isConditionDefined() {
      return hasCondition;
   }

   /**
    * Set whether condition is defined on the table.
    */
   public void setConditionDefined(boolean flag) {
      this.hasCondition = flag;
   }

   /**
    * Check whether an expression is defined on the table.
    */
   public boolean isExpressionDefined() {
      return hasExpression;
   }

   /**
    * Set whether an expression is defined on the table.
    */
   public void setExpressionDefined(boolean hasExpression) {
      this.hasExpression = hasExpression;
   }

   /**
    * Check whether sorting on the table is defined.
    */
   public boolean isSortDefined() {
      return hasSort;
   }

   /**
    * Set whether sorting on the table is defined.
    */
   public void setSortDefined(boolean hasSort) {
      this.hasSort = hasSort;
   }

   /**
    * Check whether runtime is selected on this table.
    */
   public boolean isRuntimeSelected() {
      return runtimeSelected;
   }

   /**
    * Set whether runtime is selected on this table.
    */
   public void setRuntimeSelected(boolean runtimeSelected) {
      this.runtimeSelected = runtimeSelected;
   }

   /**
    * Write attributes.
    * @param writer the specified writer.
    */
   @Override
   protected void writeAttributes(PrintWriter writer) {
      super.writeAttributes(writer);

      if(mrow > 0) {
         writer.print(" maxRow=\"" + mrow + "\"");
      }

      if(mdrow > 0) {
         writer.print(" maxDisplayRow=\"" + mdrow + "\"");
      }

      if(distinct) {
         writer.print(" distinct=\"" + distinct + "\"");
      }

      if(live) {
         writer.print(" live=\"" + live + "\"");
      }

      if(runtime) {
         writer.print(" runtime=\"" + runtime + "\"");
      }

      if(editMode) {
         writer.print(" editMode=\"" + editMode + "\"");
      }

      if(aggregate) {
         writer.print(" aggregate=\"" + aggregate + "\"");
      }

      if(!mergeable) {
         writer.print(" mergeable=\"" + mergeable + "\"");
      }

      if(!visibleTable) {
         writer.print(" visibleTable=\"" + visibleTable + "\"");
      }

      if(hasAggregate) {
         writer.print(" hasAggregate=\"" + hasAggregate + "\"");
      }

      if(hasCondition) {
         writer.print(" hasCondition=\"" + hasCondition + "\"");
      }

      if(hasExpression) {
         writer.print(" hasExpression=\"" + hasExpression + "\"");
      }

      if(hasSort) {
         writer.print(" hasSort=\"" + hasSort + "\"");
      }

      if(runtimeSelected) {
         writer.print(" runtimeSelected=\"" + runtimeSelected + "\"");
      }
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseAttributes(Element elem) {
      super.parseAttributes(elem);

      String val = Tool.getAttribute(elem, "maxRow");
      val = val == null ? "-1" : val;
      mrow = Integer.parseInt(val);
      val = Tool.getAttribute(elem, "maxDisplayRow");
      val = val == null ? "-1" : val;
      mdrow = Integer.parseInt(val);
      distinct = "true".equals(Tool.getAttribute(elem, "distinct"));
      live = "true".equals(Tool.getAttribute(elem, "live"));
      runtime = "true".equals(Tool.getAttribute(elem, "runtime"));
      editMode = "true".equals(Tool.getAttribute(elem, "editMode"));
      aggregate = "true".equals(Tool.getAttribute(elem, "aggregate"));
      mergeable = !"false".equals(Tool.getAttribute(elem, "mergeable"));
      visibleTable = !"false".equals(Tool.getAttribute(elem, "visibleTable"));
      hasAggregate = "true".equals(Tool.getAttribute(elem,"hasAggregate"));
      hasCondition = "true".equals(Tool.getAttribute(elem,"hasCondition"));
      hasExpression = "true".equals(Tool.getAttribute(elem,"hasExpression"));
      hasSort = "true".equals(Tool.getAttribute(elem,"hasSort"));
      runtimeSelected = "true".equals(Tool.getAttribute(elem,"runtimeSelected"));
   }

   /**
    * Write contents.
    * @param writer the specified writer.
    */
   @Override
   protected void writeContents(PrintWriter writer) {
      super.writeContents(writer);
      boolean compact = Tool.isCompact();

      if(compact) {
         writer.println("<nc>");
         iselection.writeXML(writer);
         writer.println("</nc>");

         writer.println("<cc>");
         oselection.writeXML(writer);
         writer.println("</cc>");
      }
      else {
         writer.println("<normalColumnSelection>");
         iselection.writeXML(writer);
         writer.println("</normalColumnSelection>");

         writer.println("<crosstabColumnSelection>");
         oselection.writeXML(writer);
         writer.println("</crosstabColumnSelection>");
      }

      if(formatMap.size() > 0) {
         writer.print("<formatMap>");

         for(Map.Entry<String, VSCompositeFormat> entry
            : VersionControlComparators.sortStringKeyMap(formatMap))
         {
            String key = entry.getKey();
            VSCompositeFormat value = entry.getValue();
            writer.print("<formats>");

            if(value != null) {
               writer.print("<column>");
               writer.print("<![CDATA[" + key + "]]>");
               writer.print("</column>");
               value.writeXML(writer);
            }

            writer.println("</formats>");
         }

         writer.println("</formatMap>");
      }
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseContents(Element elem) throws Exception {
      super.parseContents(elem);

      Element snode = Tool.getChildNodeByTagName(elem, "normalColumnSelection");
      snode = snode == null ?  Tool.getChildNodeByTagName(elem, "nc") : snode;
      snode = Tool.getFirstChildNode(snode);
      iselection.parseXML(snode);
      snode = Tool.getChildNodeByTagName(elem, "crosstabColumnSelection");
      snode = snode == null ?  Tool.getChildNodeByTagName(elem, "cc") : snode;
      snode = Tool.getFirstChildNode(snode);
      oselection.parseXML(snode);
      Element formatNode = Tool.getChildNodeByTagName(elem, "formatMap");

      if(formatNode != null) {
         NodeList formatList = Tool.getChildNodesByTagName(
            formatNode, "formats");
         formatMap.clear();

         if(formatList != null && formatList.getLength() > 0) {
            for(int i = 0; i < formatList.getLength(); i++) {
               Element formatMapNode = (Element) formatList.item(i);
               Element cnode = Tool.getChildNodeByTagName(
                  formatMapNode, "column");
               Element fnode = Tool.getChildNodeByTagName(
                  formatMapNode, "VSCompositeFormat");
               String column = Tool.getValue(cnode);

               if(column != null && fnode != null) {
                  VSCompositeFormat format = new VSCompositeFormat();
                  format.parseXML(fnode);
                  formatMap.put(column, format);
               }
            }
         }
      }
   }

   /**
    * Clone the object.
    * @return the cloned object.
    */
   @Override
   public Object clone() {
      try {
         TableAssemblyInfo info = (TableAssemblyInfo) super.clone();
         info.iselection = (ColumnSelection) iselection.clone();
         info.oselection = (ColumnSelection) oselection.clone();
         return info;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone object", ex);
         return null;
      }
   }

   /**
    * Check is embedded table or not.
    */
   public boolean isEmbedded() {
      return false;
   }

   /**
    * Get the column format map.
    */
   public Map<String, VSCompositeFormat> getFormatMap() {
      return formatMap;
   }

   private final HashMap<String, VSCompositeFormat> formatMap = new HashMap<>();
   private ColumnSelection iselection; // private column selection
   private ColumnSelection oselection; // public column selection
   private int mrow; // max rows
   private int mdrow; // max display rows
   private boolean distinct; // distinct rows
   private boolean live; // live edit move
   private boolean runtime; // is runtime
   private boolean editMode; // is in edit mode
   private boolean aggregate; // aggregate mode
   private boolean mergeable; // sql mergeable
   private boolean visibleTable; // is data block
   private boolean hasAggregate; // has aggregate/grouping
   private boolean hasCondition; // has condition
   private boolean hasExpression; // has expression
   private boolean hasSort; // has sort
   private boolean runtimeSelected;

   private static final Logger LOG =
      LoggerFactory.getLogger(TableAssemblyInfo.class);
}
