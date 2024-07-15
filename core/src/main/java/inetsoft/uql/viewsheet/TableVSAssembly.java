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
package inetsoft.uql.viewsheet;

import inetsoft.report.internal.table.TableHighlightAttr;
import inetsoft.report.internal.table.TableHyperlinkAttr;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.Tool;
import org.w3c.dom.Element;

import java.awt.*;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * TableVSAssembly represents one table assembly contained in a
 * <tt>Viewsheet</tt>.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class TableVSAssembly extends TableDataVSAssembly implements AggregateVSAssembly {
   /**
    * Constructor.
    */
   public TableVSAssembly() {
      super();
   }

   /**
    * Constructor.
    */
   public TableVSAssembly(Viewsheet vs, String name) {
      super(vs, name);
   }

   /**
    * Create assembly info.
    * @return the associated assembly info.
    */
   @Override
   protected VSAssemblyInfo createInfo() {
      return new TableVSAssemblyInfo();
   }

   /**
    * Get the type.
    * @return the type of the assembly.
    */
   @Override
   public int getAssemblyType() {
      return Viewsheet.TABLE_VIEW_ASSET;
   }

   /**
    * Get the column selection of the table.
    * @return the column selection of the table.
    */
   public ColumnSelection getColumnSelection() {
      return getTableInfo().getColumnSelection();
   }

   /**
    * Get visible column selection.
    */
   public ColumnSelection getVisibleColumns() {
      return getTableInfo().getVisibleColumns();
   }

   /**
    * Get the hidden column selection of the table.
    * @return the hidden column selection of the table.
    */
   public ColumnSelection getHiddenColumns() {
      return getTableInfo().getHiddenColumns();
   }

   /**
    * Set the column selection of the table.
    * @param columns the specified column selection of the table.
    */
   public int setColumnSelection(ColumnSelection columns) {
      getTableInfo().setColumnSelection(columns);
      return VSAssembly.INPUT_DATA_CHANGED | VSAssembly.BINDING_CHANGED;
   }

   /**
    * Check if it is a summary table.
    * @return <tt>true</tt> if it is summary table, <tt>false</tt> otherwise.
    */
   public boolean isSummaryTable() {
      return getTableInfo().isSummaryTable();
   }

   /**
    * Set whether it is a summary table.
    * @param summary <tt>true</tt> if it is a summary table,
    * <tt>false</tt> otherwise.
    */
   public void setSummaryTable(boolean summary) {
      getTableInfo().setSummaryTable(summary);
   }

   /**
    * Get the minimum size.
    * @return the minimum size of the assembly.
    */
   @Override
   public Dimension getMinimumSize() {
      return new Dimension(20, AssetUtil.defh * 3);
   }

   /**
    * Get table assembly info.
    * @return the table assembly info.
    */
   protected TableVSAssemblyInfo getTableInfo() {
      return (TableVSAssemblyInfo) getInfo();
   }

   /**
    * Write the state.
    * @param writer the specified print writer.
    */
   @Override
   protected void writeStateContent(PrintWriter writer, boolean runtime) {
      super.writeStateContent(writer, runtime);

      if(runtime) {
         ColumnSelection columns = getColumnSelection();

         if(columns != null) {
            writer.println("<state_columns>");
            columns.writeXML(writer);
            writer.println("</state_columns>");
         }

         FormatInfo finfo =  getFormatInfo();

         if(finfo != null) {
            writer.println("<state_tableformat>");
            finfo.writeContents(writer);
            writer.println("</state_tableformat>");
         }
      }
   }

   /**
    * Parse the state.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseStateContent(Element elem, boolean runtime)
      throws Exception
   {
      super.parseStateContent(elem, runtime);

      if(runtime) {
         Element columnNode = Tool.getChildNodeByTagName(elem, "state_columns");

         if(columnNode != null) {
            columnNode = Tool.getFirstChildNode(columnNode);
            ColumnSelection columns = new ColumnSelection();
            columns.parseXML(columnNode);
            setColumnSelection(columns);
         }

         Element fnode = Tool.getChildNodeByTagName(elem, "state_tableformat");

         if(fnode != null) {
            FormatInfo finfo = new FormatInfo();
            finfo.parseContents(fnode);
            setFormatInfo(finfo);
         }
     }
   }

   @Override
   public DataRef[] getBindingRefs() {
      List<DataRef> datarefs = new ArrayList<>();
      ColumnSelection csl = getColumnSelection();
      csl = (ColumnSelection) csl.clone();

      for(int i = 0; i < csl.getAttributeCount(); i++) {
         DataRef ref = csl.getAttribute(i);

         if(ref instanceof ColumnRef) {
            ((ColumnRef) ref).setAlias(null);
         }

         datarefs.add(ref);
      }

      return datarefs.toArray(new DataRef[] {});
   }

   /**
    * Get the highlight attr.
    */
   @Override
   protected TableHighlightAttr getHighlightAttr() {
      return getTableInfo().getHighlightAttr();
   }

   /**
    * Get the hyperlink attr.
    */
   @Override
   protected TableHyperlinkAttr getHyperlinkAttr() {
      return getTableInfo().getHyperlinkAttr();
   }

   @Override
   public void removeBindingCol(String ref) {
      super.removeBindingCol(ref);
      ColumnSelection csl = getColumnSelection();
      DataRef dref = csl.getAttribute(ref);

      if(dref != null) {
         csl.removeAttribute(dref);
      }

      if(csl.getAttributeCount() == 0) {
         setSourceInfo(null);
      }
   }

   @Override
   public void renameBindingCol(String oname, String nname) {
      super.renameBindingCol(oname, nname);
      ColumnSelection csl = getColumnSelection();
      DataRef dref = csl.getAttribute(oname);

      if(dref != null) {
         VSUtil.renameDataRef(dref, nname);
      }
   }

   /**
    * check show details of the table.
    */
   public boolean isShowDetail() {
      return showDetail;
   }

   /**
    * Set show details of the table.
    */
   public void setShowDetail(boolean showDetail) {
      this.showDetail = showDetail;
   }

   private boolean showDetail;
}
