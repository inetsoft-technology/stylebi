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
package inetsoft.report.composition.execution;

import inetsoft.report.TableLens;
import inetsoft.report.composition.VSTableLens;
import inetsoft.report.internal.Util;
import inetsoft.report.internal.table.TableHighlightAttr;
import inetsoft.report.internal.table.TableHyperlinkAttr;
import inetsoft.report.lens.AttributeTableLens;
import inetsoft.report.style.TableStyle;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import inetsoft.util.css.CSSParameter;
import inetsoft.util.css.CSSTableStyle;
import inetsoft.web.vswizard.model.VSWizardConstants;

/**
 * DataVSAQuery, the data viewsheet assembly query.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public abstract class DataVSAQuery extends VSAQuery {
   /**
    * Create a data viewsheet assembly query.
    * @param box the specified viewsheet sandbox.
    * @param detail <tt>true</tt> if show detail, <tt>false</tt> otherwise.
    * @param vname the specified viewsheet assembly to be processed.
    */
   public DataVSAQuery(ViewsheetSandbox box, String vname, boolean detail) {
      super(box, vname);

      this.detail = detail;
   }

   public static String getColumnLimitNotification() {
      return Tool.COLUMN_LIMIT_PREFIX + Catalog.getCatalog().getString(
         "common.limited.column", Util.getOrganizationMaxColumn())
         + Tool.COLUMN_LIMIT_SUFFIX;
   }

   /**
    * Check if this VSAQuery is for detail data.
    */
   @Override
   public boolean isDetail() {
      return detail;
   }

   /**
    * Get the default column selection.
    */
   public abstract ColumnSelection getDefaultColumnSelection() throws Exception;

   /**
    * Get the table.
    * @return the table of the query.
    */
   public abstract TableLens getTableLens() throws Exception;

   /**
    * Get the data.
    * @return the data of the query.
    */
   @Override
   public Object getData() throws Exception {
      return getTableLens();
   }

   /**
    * Get the view table lens. A view table lens is generated based on data
    * table lens, with table/style and format applied.
    * @param data the specified base table lens.
    * @return the viewsheet table lens.
    */
   protected VSTableLens getViewTableLens(TableLens data) throws Exception {
      final ViewsheetSandbox box = this.box;
      box.lockRead();

      try {
         boolean chart = getAssembly() instanceof ChartVSAssembly;
         boolean table = getAssembly() instanceof TableDataVSAssembly;
         TableLens base = data;

         // change from black to default border color
         if(table) {
            AttributeTableLens atbl = new AttributeTableLens(data);
            atbl.setRowBorderColor(VSAssemblyInfo.DEFAULT_BORDER_COLOR);
            atbl.setColBorderColor(VSAssemblyInfo.DEFAULT_BORDER_COLOR);
            data = atbl;
         }

         // for chart, we should not apply style or hyperlink
         if(table && !chart) {
            TableDataVSAssembly assembly = (TableDataVSAssembly) getAssembly();
            TableDataVSAssemblyInfo tinfo =
               (TableDataVSAssemblyInfo) assembly.getVSAssemblyInfo();
            String sname = tinfo.getTableStyle();
            TableStyle style = VSUtil.getTableStyle(sname);

            if(style != null) {
               style = (TableStyle) style.clone();
               style.setTable(data);
               data = style;
            }
            else {
               VSCSSFormat cssFormat = tinfo.getFormat().getCSSFormat();
               VSCompositeFormat vsCompositeFormat = cssFormat.getVSCompositeFormat();
               CSSParameter sheetParam = null;

               if(vsCompositeFormat != null) {
                  FormatInfo formatInfo = vsCompositeFormat.getFormatInfo();

                  if(formatInfo != null) {
                     VSCompositeFormat sheetFormat = formatInfo.getFormat(VSAssemblyInfo.SHEETPATH);

                     if(sheetFormat != null) {
                        sheetParam = sheetFormat.getCSSFormat().getCSSParam();
                     }
                  }
               }

               TableStyle tablestyle = new CSSTableStyle(cssFormat.getCSSID(), cssFormat.getCSSType(),
                       cssFormat.getCSSClass(), data, null, sheetParam);
               tablestyle.setTable(data);
               data = tablestyle;
            }

            TableHyperlinkAttr tattr = tinfo.getHyperlinkAttr();

            if(tattr != null && !tattr.isNull()) {
               data = new VSHyperlinkTableLens(data, tattr);
            }
         }

         if(getAssembly() instanceof TableVSAssembly) {
            TableVSAssembly assembly = (TableVSAssembly) getAssembly();
            // apply column alias. The alias is not pushed to query for vs so
            // the data paths are correct
            AttributeTableLens atbl = new AttributeTableLens(data);
            ColumnSelection cols = assembly.getColumnSelection();
            boolean hasAlias = false;
            int actualIndex = -1;

            for(int i = 0; i < cols.getAttributeCount(); i++) {
               ColumnRef col = (ColumnRef) cols.getAttribute(i);

               if(col.isVisible()) {
                  actualIndex++;
               }

               if(col.getAlias() != null && col.getAlias().length() > 0 && col.isVisible()) {
                  atbl.setObject(0, actualIndex, col.getAlias());
                  hasAlias = true;
               }
            }

            if(hasAlias) {
               data = atbl;
            }
         }

         if(table) {
            TableDataVSAssembly assembly = (TableDataVSAssembly) getAssembly();
            Tool.localizeHeader(data, assembly.getInfo(), VSUtil.getLocalMap(
               box.getViewsheet(), assembly.getName()));
            FormatInfo info = assembly.getFormatInfo();

            if(info != null && !info.isEmpty() && vname != null &&
               !vname.startsWith(VSWizardConstants.TEMP_CROSSTAB_NAME))
            {
               data = new VSFormatTableLens(box, vname, data, !chart);
            }

            TableDataVSAssemblyInfo tinfo =
               (TableDataVSAssemblyInfo) assembly.getVSAssemblyInfo();
            TableHighlightAttr highlight = tinfo.getHighlightAttr();

            if(highlight != null && !highlight.isNull()) {
               highlight.replaceVariables(box.getAllVariables());
               highlight.setConditionTable(base);
               data = highlight.createFilter(data);
            }
         }

         VSTableLens lens = new VSTableLens(data);
         lens.setLinkVarTable(box.getAllVariables());
         lens.setLinkSelections(box.getSelections());
         return lens;
      }
      finally {
         box.unlockRead();
      }
   }

   /**
    * Get source table name.
    */
   protected String getSourceTable() {
      SourceInfo sinfo = ((DataVSAssembly) getAssembly()).getSourceInfo();

      return sinfo == null || sinfo.isEmpty() ? null : sinfo.getSource();
   }

   /**
    * Check if the groupinfo of the current vs assembly support pushdown.
    *
    * @param table       the binding table.
    * @param groupInfo   the groupinfo of current vs assembly.
    * @return
    */
   protected boolean isGroupSupportPushdown(TableAssembly table, AggregateInfo groupInfo) {
      if(groupInfo == null || groupInfo.isEmpty() || groupInfo.getGroupCount() == 0 ||
         !isSQLite(table))
      {
         return true;
      }

      for(int i = 0; i < groupInfo.getGroupCount(); i++) {
         GroupRef group = groupInfo.getGroup(i);
         String dtype = group.getDataType();

         if(XSchema.isDateType(dtype) || XSchema.BOOLEAN.equals(dtype)) {
            return false;
         }
      }

      return true;
   }

   /**
    * Check if the target table source is SQLite.
    */
   protected boolean isSQLite(TableAssembly table) {
      try {
         return Util.isSQLite(AssetUtil.getDataSource(table));
      }
      catch(Exception ignore) {
      }

      return false;
   }

   private boolean detail;
}
