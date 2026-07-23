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
package inetsoft.report.composition.execution;

import inetsoft.report.TableLens;
import inetsoft.report.composition.VSTableLens;
import inetsoft.report.internal.Util;
import inetsoft.report.internal.table.TableHighlightAttr;
import inetsoft.report.internal.table.TableHyperlinkAttr;
import inetsoft.report.lens.AttributeTableLens;
import inetsoft.report.style.TableStyle;
import inetsoft.report.style.XTableStyle;

import java.awt.Color;
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
            String orgID = box != null && box.getAssetEntry() != null ? box.getAssetEntry().getOrgID() : null;
            TableStyle style = VSUtil.getTableStyle(sname, orgID);

            if(style != null) {
               style = (TableStyle) style.clone();

               // overlay the modern structure palette onto the cloned Default Style so it acts as a
               // default the user cell/column/row format still overrides; non-default styles untouched.
               // sname is the canonical (non-localized) style name, so the constant match is locale-safe
               if(style instanceof XTableStyle &&
                  TableDataVSAssemblyInfo.DEFAULT_STYLE.equals(sname) &&
                  VSTableStructureDefaults.isModern())
               {
                  applyModernTableStructure((XTableStyle) style);
               }

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
    * Overlay the modern gridline/header/total colors onto a cloned Default Style. The header-column
    * and trailer keys affect only crosstabs (plain tables have no header/trailer bands); body borders
    * fall through for the regions not set explicitly. Every value is a default the user format beats.
    */
   private void applyModernTableStructure(XTableStyle style) {
      Color gridline = VSTableStructureDefaults.gridlineColor();
      Color separator = VSTableStructureDefaults.headerSeparator();
      style.put("body.rcolor", gridline);
      style.put("body.ccolor", gridline);
      style.put("header-row.rcolor", separator); // header→body horizontal rule, stronger for hierarchy
      style.put("header-row.ccolor", gridline);
      style.put("header-col.ccolor", separator); // crosstab row-header vertical rule
      style.put("top-border.color", gridline);
      style.put("bottom-border.color", gridline);
      style.put("left-border.color", gridline);
      style.put("right-border.color", gridline);
      style.put("header-row.background", VSTableStructureDefaults.headerBackground());
      style.put("header-row.foreground", VSTableStructureDefaults.headerForeground());
      style.put("header-col.background", VSTableStructureDefaults.headerBackground());
      style.put("header-col.foreground", VSTableStructureDefaults.headerForeground());
      style.put("trailer-row.background", VSTableStructureDefaults.totalBackground());
      style.put("trailer-col.background", VSTableStructureDefaults.totalBackground());
      applyModernGroupSubtotals(style);
   }

   /**
    * Prepend data-borne group-subtotal emphasis specs so interior crosstab subtotals get a distinct
    * background. Group-total specs must precede the shipped zebra spec (findSpec returns the first
    * match) so they win over alternating-row color on total cells. Levels 0-9 cover both axes; each
    * spec self-guards (matchRowGroup/matchColGroup return false for a non-crosstab lens or a level past
    * the header count), so plain tables are unaffected. Grand totals stay distinct: XTableStyle resolves
    * the trailer band before per-cell specs, so trailer-row/col.background (grand total) still wins.
    */
   private void applyModernGroupSubtotals(XTableStyle style) {
      Color subtotal = VSTableStructureDefaults.subtotalBackground();
      int pos = 0;

      for(int level = 0; level < 10; level++) {
         XTableStyle.Specification rowSpec = style.new Specification();
         rowSpec.setType(XTableStyle.Specification.ROW_GROUP_TOTAL);
         rowSpec.setIndex(level);
         rowSpec.put("background", subtotal);
         style.addSpecification(pos++, rowSpec);

         XTableStyle.Specification colSpec = style.new Specification();
         colSpec.setType(XTableStyle.Specification.COL_GROUP_TOTAL);
         colSpec.setIndex(level);
         colSpec.put("background", subtotal);
         style.addSpecification(pos++, colSpec);
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
