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

import inetsoft.report.*;
import inetsoft.report.filter.DCMergeDatePartFilter;
import inetsoft.report.internal.table.*;
import inetsoft.report.lens.CalcTableLens;
import inetsoft.report.script.viewsheet.TableDataVSAScriptable;
import inetsoft.report.script.viewsheet.ViewsheetScope;
import inetsoft.sree.SreeEnv;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.AggregateFormula;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import inetsoft.util.css.CSSDictionary;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.text.Format;
import java.util.List;
import java.util.*;

/**
 * VSFormatTableLens, the table lens applies the format info contained in a
 * table data viewsheet assembly.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public final class VSFormatTableLens extends FormatTableLens {
   /**
    * Create a format viewsheet table lens.
    */
   public VSFormatTableLens(ViewsheetSandbox box, String name, TableLens table, boolean tfmt) {
      super(table, tfmt);

      this.box = box;
      this.loc = Catalog.getCatalog().getLocale();

      if(this.loc == null) {
         this.loc = Locale.getDefault();
      }

      Viewsheet vs = box.getViewsheet();
      this.assembly = (TableDataVSAssembly) vs.getAssembly(name);
      FormatInfo info = (assembly == null) ? null : assembly.getFormatInfo();
      this.fmap = info == null ? null : Tool.deepCloneMap(info.getFormatMap());
      this.finfo = new FormatInfo();
      this.fcmap = new Object2ObjectOpenHashMap<>();
      this.userFormat = new Object2ObjectOpenHashMap<>();

      if(this.assembly instanceof TableVSAssembly) {
         ColumnSelection cols = ((TableVSAssembly) this.assembly).getColumnSelection();

         // if using checkbox, don't format the column value. otherwise the checkbox
         // won't display correctly
         if(box.isRuntime()) {
            for(int i = 0; i < cols.getAttributeCount(); i++) {
               ColumnRef col = (ColumnRef) cols.getAttribute(i);

               if(col instanceof FormRef &&
                  ((FormRef) col).getOption() instanceof BooleanColumnOption)
               {
                  disabledCols.set(i);
               }
            }
         }
      }
      else if(assembly instanceof CrosstabVSAssembly && fmap != null) {
         VSCrosstabInfo cinfo = ((CrosstabVSAssembly) assembly).getVSCrosstabInfo();

         if(cinfo != null) {
            setProperty("fillBlankWithZero", cinfo.isFillBlankWithZero());
         }

         for(TableDataPath path : fmap.keySet()) {
            VSCompositeFormat format = fmap.get(path);

            if(format != null && XConstants.CURRENCY_FORMAT.equals(format.getFormat()) &&
               path.getPath().length > 0 &&
               (path.getType() == TableDataPath.GRAND_TOTAL || path.getType() == TableDataPath.SUMMARY))
            {
               String aggrName = path.getPath()[path.getPath().length - 1];
               DataRef[] aggrs = cinfo.getAggregates();

               for(DataRef ref : aggrs) {
                  if(!(ref instanceof VSAggregateRef)) {
                     continue;
                  }

                  VSAggregateRef aref = (VSAggregateRef) ref;
                  AggregateFormula formula = aref.getFormula();

                  // if the formula is count with currency format then ignore the format
                  if((formula == AggregateFormula.COUNT_ALL ||
                     formula == AggregateFormula.COUNT_DISTINCT) &&
                     Tool.equals(aref.getFullName(), aggrName))
                  {
                     format.getUserDefinedFormat().setFormat(null);
                     format.getUserDefinedFormat().setFormatValue(null);
                  }
               }
            }
         }
      }
      else if(assembly instanceof CalcTableVSAssembly && fmap != null) {
         TableLayout tableLayout = ((CalcTableVSAssembly) assembly).getTableLayout();
         CalcTableVSAssemblyInfo calcInfo = (CalcTableVSAssemblyInfo) assembly.getVSAssemblyInfo();
         setProperty("fillBlankWithZero", calcInfo.isFillBlankWithZero());

         if(tableLayout != null) {
            for(int r = 0; r < tableLayout.getRowCount(); r++) {
               for(int c = 0; c < tableLayout.getColCount(); c++) {
                  CellBinding binding = tableLayout.getCellBinding(r, c);

                  if(binding instanceof TableCellBinding) {
                     TableCellBinding tableCellBinding = (TableCellBinding) binding;

                     if(XConstants.COUNT_FORMULA.equals(tableCellBinding.getFormula()) ||
                        XConstants.DISTINCTCOUNT_FORMULA.equals(tableCellBinding.getFormula()))
                     {
                        TableDataPath path = calcInfo.getCellDataPath(r, c);
                        VSCompositeFormat format = fmap.get(path);

                        // if the formula is count with currency format then ignore the format
                        if(format != null && XConstants.CURRENCY_FORMAT.equals(format.getFormat())) {
                           format.getUserDefinedFormat().setFormat(null);
                           format.getUserDefinedFormat().setFormatValue(null);
                        }
                     }
                  }
               }
            }
         }
      }

      // init descriptor and others
      checkInit();

      if(descriptor.getType() == TableDataDescriptor.CROSSTAB_TABLE) {
         buildSpanMap();
      }
   }

   /**
    * Get the locale.
    * @return the locale.
    */
   @Override
   protected final Locale getLocale() {
      return loc;
   }

   /**
    * Get the format map.
    * @return the format map.
    */
   @Override
   public final Map getFormatMap() {
      return fmap;
   }

   /**
    * Get table format at a table col.
    * @param col the specified col
    */
   @Override
   protected final TableFormat getColTableFormat(int col) {
      return null;
   }

   /**
    * Get table format at a table row.
    * @param row the specified row
    */
   @Override
   protected final TableFormat getRowTableFormat(int row) {
      return null;
   }

   /**
    * Get table format at a table cell.
    * @param row the specified row index
    * @param col the specified col index
    * @return table format if any, null otherwise
    */
   @Override
   protected final TableFormat getCellTableFormat0(int row, int col) {
      TableDataPath path = descriptor.getCellDataPath(row, col);
      TableFormat tfmt = fcmap.get(path);

      if(tfmt != null) {
         return tfmt;
      }

      if(assembly instanceof CalcTableVSAssembly) {
         final CalcTableLens calcTableLens = getCalcTableLens();

         // If a span cell, use the format set for the top left corner.
         // This is the same as FormatTableLens.
         if(calcTableLens != null) {
            SpanMap spanmap = calcTableLens.createSpanMap();
            Rectangle rect = spanmap.get(row, col);

            if(rect != null && (rect.x != 0 || rect.y != 0)) {
               return getCellTableFormat0(row + rect.y, col + rect.x);
            }
         }
      }

      return createCellFormat(row, col);
   }

   /**
    * Create table format at a table cell.
    * @param row the specified row index
    * @param col the specified col index
    */
   private TableFormat createCellFormat(int row, int col) {
      TableDataPath path = descriptor.getCellDataPath(row, col);
      VSCompositeFormat vfmt = path == null ? null : fmap.get(path);
      TableDataPath oldCellPath = path;

      if(vfmt == null && path != null && path.getType() == TableDataPath.DETAIL) {
         TableDataPath temp = new TableDataPath(0, TableDataPath.SUMMARY, path.getDataType(),
                                                path.getPath());
         vfmt = fmap.get(temp);
      }

      boolean crosstab = false;

      if(this.assembly instanceof CrosstabVSAssembly) {
         crosstab = true;
      }

      boolean perCell = vfmt != null;
      ViewsheetScope scope = box.getScope();
      TableDataVSAScriptable scriptable =
         (TableDataVSAScriptable) scope.getVSAScriptable(assembly.getName());
      int orow = 0;
      int ocol = 0;

      if(scriptable != null) {
         orow = scriptable.getRow();
         ocol = scriptable.getCol();
         scriptable.setRow(row);
         scriptable.setCol(col);
      }

      if(vfmt == null) {
         vfmt = new VSCompositeFormat();
         vfmt.setTableRuntimeMode(true);
      }

      boolean objAlignment = false;
      boolean objBackground = false;
      boolean objForeground = false;
      boolean objFont = false;
      boolean objFormat = false;
      boolean objBorders = false;
      boolean objBorderColors = false;
      boolean objWrapping = false;
      boolean objAlpha = false;
      boolean objPresenter = false;

      TableDataPath path0 = VSAssemblyInfo.OBJECTPATH;
      VSCompositeFormat objfmt = fmap.get(path0);

      if(objfmt == null && path0.getType() == TableDataPath.DETAIL) {
         path0 = new TableDataPath(0, TableDataPath.SUMMARY, path0.getDataType(), path0.getPath());
         objfmt = fmap.get(path0);
      }

      Font deffont = VSAssemblyInfo.getDefaultFont(Font.PLAIN, 11);

      if(objfmt != null) {
         VSFormat fmt = new VSFormat();
         styled = assembly.getTableStyle() != null ||
            CSSDictionary.getDictionary().checkPresent("TableStyle");

         VSFormat userf = objfmt.getUserDefinedFormat();
         objAlignment = userf.isAlignmentDefined() || userf.isAlignmentValueDefined();
         objBackground = userf.isBackgroundDefined() || userf.isBackgroundValueDefined();
         objForeground = userf.isForegroundDefined() || userf.isForegroundValueDefined();
         objFont = userf.isFontDefined() || userf.isFontValueDefined();
         objFormat = userf.isFormatDefined() || userf.isFormatValueDefined();
         objBorders = userf.isBordersDefined() || userf.isBordersValueDefined();
         objBorderColors = userf.isBorderColorsDefined() || userf.isBorderColorsValueDefined();
         objWrapping = userf.isWrappingDefined() || userf.isWrappingValueDefined();
         objAlpha = userf.isAlphaDefined() || userf.isAlphaValueDefined();
         objPresenter = userf.isPresenterDefined() || userf.isPresenterValueDefined();

         copyDefaultFormat(fmt, objfmt);

         // should not inherit default object styles or
         // table style may not work
         if(styled) {
            fmt.setBorderColors(null);
            fmt.setBorderColorsValue(null);
            objBorderColors = false;
         }
         else {
            fmt.setBorderColors(objfmt.getBorderColors());
            fmt.setBorderColorsValue(objfmt.getBorderColors());
         }

         if(styled) {
            objForeground = false;
            fmt.setForeground(null);
            fmt.setForegroundValue(null);
         }

	 /* this prevent Arial-Plain-11 to ever work as object font. If a user
	    wants to not use/set object font, the font can be reset on the
            format dialog so this logic shouldn't be necessary.
         if(deffont.equals(objfmt.getFont()) && styled) {
            objFont = false;
            fmt.setFont(null);
            fmt.setFontValue(null);
         }
	 */

         if(styled) {
            fmt.setBorders(null);
            fmt.setBordersValue(null);
            objBorders = false;
         }
         else {
            fmt.setBorders(vfmt.getDefaultFormat().getBorders());
            fmt.setBordersValue(vfmt.getDefaultFormat().getBordersValue());
         }

         // handle background being defined for tablestyles
         if(styled) {
            fmt.setBackground(null);
            fmt.setBackgroundValue(null);
            objBackground = false;
         }

         vfmt.setDefaultFormat(fmt);
      }

      if(oldCellPath != null) {
         finfo.setFormat(oldCellPath, vfmt);
      }

      Format format = getTable().getDefaultFormat(row, col);
      VSTableFormat fmt = new VSTableFormat();

      // vs table should defaults not not wrapping, same as the vsformat
      fmt.linewrap = false;

      fmt.format = vfmt.getFormat();
      fmt.format_spec = vfmt.getFormatExtent();

      if(format != null && vfmt.getFormat() == null) {
         TableFormat fmt0 = new TableFormat();
         fmt0.setFormat(format);
         fmt.format = fmt0.format;
         fmt.format_spec = fmt0.format_spec;
      }

      boolean withDynamic = false;

      if(perCell) {
         List<DynamicValue> dvals = vfmt.getUserDefinedFormat().getDynamicValues();

         try {
            java.util.List<DynamicValue> dlist = new ArrayList<>();

            if(dvals != null && !dvals.isEmpty()) {
               for(DynamicValue dval : dvals) {
                  if(dval != null && VSUtil.isDynamicValue(dval.getDValue())) {
                     dlist.add(dval);
                  }
               }
            }

            withDynamic = !dlist.isEmpty();

            for(DynamicValue dynamicValue : dlist) {
               box.executeDynamicValue(dynamicValue, scriptable);
            }
         }
         catch(RuntimeException ex) {
            throw ex;
         }
         catch(Exception ex) {
            LOG.warn("Failed to set dynamic values", ex);
            throw new RuntimeException(ex);
         }
      }

      VSFormat defFmt = vfmt.getDefaultFormat();
      VSFormat userFmt = vfmt.getUserDefinedFormat();
      VSCSSFormat cssFmt = vfmt.getCSSFormat();

      if(userFmt.isForegroundValueDefined() || cssFmt.isForegroundValueDefined() && !styled) {
         fmt.foreground = vfmt.getForeground();
      }
      else if(objForeground || !userFmt.isForegroundValueDefined() &&
         !cssFmt.isForegroundValueDefined())
      {
         fmt.foreground = defFmt.getForeground();
      }

      if(userFmt.isBackgroundValueDefined() || cssFmt.isBackgroundValueDefined() && !styled) {
         fmt.background = vfmt.getBackground();
      }
      else if(objBackground || !userFmt.isBackgroundValueDefined() &&
         !cssFmt.isBackgroundValueDefined())
      {
         fmt.background = defFmt.getBackground();
      }

      if(userFmt.isAlphaValueDefined() || cssFmt.isAlphaValueDefined()) {
         fmt.alpha = vfmt.getAlpha();
      }
      else if(objAlpha || !userFmt.isAlphaValueDefined() && !cssFmt.isAlphaValueDefined()) {
         fmt.alpha = defFmt.getAlpha();
         // @by stephenwebster, For bug1407424414404
         // If the background is set and the alpha is 100 (default value),
         // the alpha is considered not user defined.  Set default alpha to
         // false if the background is defined. This way it does not delegate
         // to the table style. See getAlpha()
         fmt.defAlpha = !(userFmt.isBackgroundValueDefined() ||
                          cssFmt.isBackgroundValueDefined()) && !objAlpha;

      }

      if(userFmt.isFontValueDefined() || cssFmt.isFontValueDefined() && !styled) {
         fmt.font = vfmt.getFont();
      }
      else if(objFont || !userFmt.isFontValueDefined() && !cssFmt.isFontValueDefined()) {
         fmt.font = defFmt.getFont();
         // if not objFont, it is a default font, can be override by any
         // other format font
         fmt.defFont = !objFont;
      }

      if(fmt.font == null) {
         fmt.font = deffont;
         fmt.defFont = true;
      }

      // @by stevenkuo bug1427874308246 2015-4-2
      // added a check to prevent default alignment values from being applied
      // when there's already an existing tablestyle.
      if(!styled && objfmt != null) {
         if(!objfmt.getUserDefinedFormat().isAlignmentValueDefined() &&
            !objfmt.getCSSFormat().isAlignmentValueDefined() &&
            !userFmt.isAlignmentValueDefined() &&
            !cssFmt.isAlignmentValueDefined() && crosstab)
         {
            int align = StyleConstants.H_CENTER | StyleConstants.V_TOP;
            int rvalue = defFmt.getAlignment();
            defFmt.setAlignmentValue(align);

            if (objAlignment) {
               defFmt.setAlignment(rvalue);
            }
         }

         if(objAlignment || !userFmt.isAlignmentValueDefined() &&
            !cssFmt.isAlignmentValueDefined())
         {
            fmt.alignment = defFmt.getAlignment();
         }
      }

      if(userFmt.isAlignmentValueDefined() || cssFmt.isAlignmentValueDefined()) {
         fmt.alignment = vfmt.getAlignment();
      }

      if(userFmt.isWrappingValueDefined() || cssFmt.isWrappingValueDefined()) {
         fmt.linewrap = vfmt.isWrapping();
      }
      else if(objWrapping || !userFmt.isWrappingValueDefined() &&
              !cssFmt.isWrappingValueDefined())
      {
         fmt.linewrap = defFmt.isWrapping();
      }

      boolean isUserFormat = false;

      if(userFmt.isFormatValueDefined()) {
         fmt.format = userFmt.getFormat();
         fmt.format_spec = userFmt.getFormatExtent();
         isUserFormat = true;
      }
      else if(objFormat) {
         fmt.format = defFmt.getFormat();
         fmt.format_spec = defFmt.getFormatExtent();
      }

      if(userFmt.isPresenterValueDefined()) {
         fmt.presenter = userFmt.getPresenter();
      }
      else if(objPresenter) {
         fmt.presenter = defFmt.getPresenter();
      }

      if(userFmt.isBordersValueDefined() || cssFmt.isBordersValueDefined() && !styled) {
         fmt.borders = vfmt.getBorders();
      }
      else if(objBorders || !userFmt.isBordersValueDefined() && !cssFmt.isBordersValueDefined()) {
         fmt.borders = defFmt.getBorders();
      }

      BorderColors bcolors = null;

      if(userFmt.isBorderColorsValueDefined() || cssFmt.isBorderColorsValueDefined() && !styled) {
         bcolors = vfmt.getBorderColors();
      }
      else if(objBorderColors || !userFmt.isBorderColorsValueDefined() &&
         !cssFmt.isBorderColorsValueDefined())
      {
         bcolors = defFmt.getBorderColors();
      }

      fmt.topBorderColor = bcolors == null ? null : bcolors.topColor;
      fmt.leftBorderColor = bcolors == null ? null : bcolors.leftColor;
      fmt.bottomBorderColor = bcolors == null ? null : bcolors.bottomColor;
      fmt.rightBorderColor = bcolors == null ? null : bcolors.rightColor;

      if(!userFmt.isBorderColorsValueDefined() &&
         !cssFmt.isBorderColorsValueDefined() &&
         objBorderColors)
      {
         // if the left side cell has user set right border color, don't
         // set the left border to obj's border or it may override the
         // per cell border setting in mergeLineColor
         if(col > 0) {
            TableDataPath path2 = descriptor.getCellDataPath(row, col - 1);
            VSCompositeFormat fmt2 = fmap.get(path2);

            if(fmt2 != null &&
               fmt2.getUserDefinedFormat().isBorderColorsValueDefined())
            {
               fmt.leftBorderColor = null;
            }
         }

         if(row > 0) {
            TableDataPath path2 = descriptor.getCellDataPath(row - 1, col);
            VSCompositeFormat fmt2 = fmap.get(path2);

            if(fmt2 != null && fmt2.getUserDefinedFormat().isBorderColorsValueDefined()) {
               fmt.topBorderColor = null;
            }
         }
      }

      if(scriptable != null) {
         scriptable.setRow(orow);
         scriptable.setCol(ocol);
      }

      if(!withDynamic) {
         fcmap.put(path, fmt);
         userFormat.put(path, isUserFormat);
      }

      return fmt;
   }

   /**
    * VS table doesn't support span cells.
    */
   @Override
   protected Rectangle getSpan0(int row, int col) {
      return null;
   }

   /**
    * Copy all format setting to this format.
    */
   private void copyDefaultFormat(VSFormat tfmt, VSCompositeFormat sfmt) {
      String clr;
      VSCSSFormat css = sfmt.getCSSFormat();
      VSFormat user = sfmt.getUserDefinedFormat();

      if((clr = sfmt.getBackgroundValue()) != null &&
         css.isBackgroundValueDefined() && !user.isBackgroundValueDefined())
      {
         tfmt.setBackgroundValue(clr);
      }

      if((clr = sfmt.getForegroundValue()) != null &&
         css.isForegroundValueDefined() && !user.isForegroundValueDefined())
      {
         tfmt.setForegroundValue(clr);
      }

      if(css.isAlignmentValueDefined() && !user.isAlignmentValueDefined()) {
        tfmt.setAlignmentValue(sfmt.getAlignmentValue());
      }

      if(css.isFontValueDefined() && !user.isFontValueDefined()) {
         tfmt.setFontValue(sfmt.getFontValue());
      }

      if(css.isWrappingValueDefined() && !user.isWrappingValueDefined()) {
         tfmt.setWrappingValue(sfmt.getWrappingValue());
      }

      if(css.isAlphaValueDefined() && !user.isAlphaValueDefined()) {
         tfmt.setAlphaValue(sfmt.getAlphaValue());
      }

      tfmt.setAlignment(sfmt.getAlignment());
      tfmt.setBackground(sfmt.getBackground());
      tfmt.setForeground(sfmt.getForeground());
      tfmt.setFont(sfmt.getFont());
      tfmt.setFormat(sfmt.getFormat());
      tfmt.setFormatExtent(sfmt.getFormatExtent());
      tfmt.setWrapping(sfmt.isWrapping());
      tfmt.setSpan(sfmt.getSpan());
      tfmt.setAlpha(sfmt.getAlpha());
      tfmt.setPresenter(sfmt.getPresenter());
   }

   /**
    * Get the initialized vsformats in VSFormatTableLens.
    */
   public FormatInfo getFormatInfo() {
      return finfo;
   }

   /**
    * Return the per cell background alpha. Return 100 to use default
    * value.
    * @param r row number.
    * @param c column number.
    * @return background alpha for the specified cell.
    */
   @Override
   public int getAlpha(int r, int c) {
      checkInit();
      TableFormat cellf = getCellTableFormat(r, c);
      int alpha = cellf == null || cellf.alpha == null ? 100 : cellf.alpha;

      // @by stephenwebster, Fix bug1407424414404
      // If no alpha is defined but the default one, take the alpha from
      // the table style if it exists.
      if(cellf instanceof VSTableFormat && ((VSTableFormat) cellf).defAlpha) {
         Color clr = table.getBackground(r, c);

         if(clr != null) {
            cellf.alpha = null;
            alpha = (int) ((clr.getAlpha() / 255.0) * 100);
         }
      }

      return mergeInt(alpha, -1, -1, cellf == null || cellf.alpha == null ? -1 : cellf.alpha);
   }

   @Override
   public Format getCellFormat(int r, int c, boolean cellOnly) {
      Format sfmt = getScriptCellFormat(r, c, cellOnly);

      if(sfmt != null) {
         return sfmt;
      }

      sfmt = getCrosstabDCPartFormat(r, c);

      if(sfmt != null) {
         return sfmt;
      }


      return super.getCellFormat(r, c, cellOnly);
   }

   private Format getCrosstabDCPartFormat(int r, int c) {
      Object value = table.getObject(getBaseRowIndex(r), c);

      if(value instanceof DCMergeDatePartFilter.MergePartCell) {
         TableFormat cellfmt = null;
         TableDataPath path = descriptor.getCellDataPath(r, c);

         if(Boolean.TRUE.equals(userFormat.get(path))) {
            cellfmt = getCellTableFormat0(r, c);
         }

         return new TableDateComparisonFormat(
            cellfmt != null ? cellfmt.getFormat(Catalog.getCatalog().getLocale()) : null);
      }

      return null;
   }

   /**
    * Return the per cell font. Return null to use default font.
    * @param r row number.
    * @param c column number.
    * @return font for the specified cell.
    */
   @Override
   public Font getFont(int r, int c) {
      checkInit();
      TableFormat cellf = getCellTableFormat(r, c);
      Font cfont = cellf == null ? null : cellf.font;
      Font dfont = null;

      // @by stephenwebster, For bug1407451628343 and bug1333958288725
      // The default behavior is to allow table style to override default font.
      // For pre-11.3 behavior, please set the property vs.font.asDefault
      if(!vsFontAsDefault && cellf instanceof VSTableFormat && ((VSTableFormat) cellf).defFont) {
         // a default font? can be override by any format font
         dfont = cfont;
         cfont = null;
      }

      Font f = mergeFont(table.getFont(r, c), null, null, cfont);
      return f == null ? dfont : f;
   }

   /**
    * Get calc tablelens filter.
    */
   private CalcTableLens getCalcTableLens() {
      TableLens table = getTable();

      while(table != null) {
         if(table instanceof CalcTableLens) {
            return((CalcTableLens) table);
         }

         table = (table instanceof TableFilter) ? ((TableFilter) table).getTable() : null;
      }

      return null;
   }

   @Override
   protected Object format(int r, int c, Object obj) {
      if(disabledCols.get(c) && box.getExportFormat() == null) {
         return obj;
      }

      return super.format(r, c, obj);
   }

   private static final class VSTableFormat extends TableFormat {
      private transient boolean defFont;
      private transient boolean defAlpha;
   }

   private boolean vsFontAsDefault = "true".equalsIgnoreCase(
      SreeEnv.getProperty("vs.font.asDefault", "false"));
   private TableDataVSAssembly assembly;
   private transient ViewsheetSandbox box;
   private Locale loc;
   private Map<TableDataPath, VSCompositeFormat> fmap;
   private Map<TableDataPath, TableFormat> fcmap;
   private Map<TableDataPath, Boolean> userFormat;
   private FormatInfo finfo;
   private boolean styled;
   private BitSet disabledCols = new BitSet();

   private static final Logger LOG = LoggerFactory.getLogger(VSFormatTableLens.class);
}
