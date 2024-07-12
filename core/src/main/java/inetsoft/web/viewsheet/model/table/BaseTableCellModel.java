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
package inetsoft.web.viewsheet.model.table;

import com.fasterxml.jackson.annotation.JsonInclude;
import inetsoft.report.*;
import inetsoft.report.composition.*;
import inetsoft.report.filter.DCMergeCell;
import inetsoft.report.filter.DCMergeDatesCell;
import inetsoft.report.internal.Util;
import inetsoft.report.internal.table.FormatTableLens;
import inetsoft.report.lens.AttributeTableLens;
import inetsoft.report.painter.HTMLPresenter;
import inetsoft.report.painter.PresenterPainter;
import inetsoft.uql.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.asset.internal.ColumnIndexMap;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;
import inetsoft.uql.xmla.MemberObject;
import inetsoft.util.Tool;
import inetsoft.util.script.ScriptUtil;
import inetsoft.web.composer.model.vs.HyperlinkModel;
import inetsoft.web.viewsheet.model.*;

import javax.annotation.Nullable;
import java.awt.*;
import java.sql.NClob;
import java.sql.Time;
import java.util.Iterator;
import java.util.Objects;
import java.util.function.Function;


@JsonInclude(JsonInclude.Include.NON_NULL)
public class BaseTableCellModel implements BaseTableCellModelPrototype,
                                           PrototypeModel<BaseTableCellModelPrototype>
{
   private BaseTableCellModel(boolean spanCell) {
      this.spanCell = spanCell ? spanCell : null;
      this.drillOp = null;
   }

   private BaseTableCellModel(Object cellData, Object cellLabel, int row, int col,
                              VSFormatModel vsFormatModel, String drillOp,
                              HyperlinkModel[] hyperlinks, TableDataPath path,
                              String presenter, boolean isImage, int rowPadding, int colPadding)
   {
      this.cellData = cellData;
      this.cellLabel = cellLabel;
      this.vsFormatModel = vsFormatModel;
      this.drillOp = drillOp == null || drillOp.isEmpty() ? null : drillOp;
      this.hyperlinks = hyperlinks != null && hyperlinks.length > 0 ? hyperlinks : null;
      this.dataPath = path;
      this.presenter = presenter;
      this.isImage = isImage ? isImage : null;
      this.rowPadding = rowPadding;
      this.colPadding = colPadding;
   }

   private BaseTableCellModel(Object cellData) {
      this.cellData = cellData;
      this.cellLabel = Tool.toString(cellData);
   }

   private BaseTableCellModel(Object cellData, Object cellLabel, int row, int col,
                              VSFormatModel vsFormatModel, HyperlinkModel[] hyperlinks,
                              TableDataPath path, String editorType, String[] options,
                              String presenter, boolean editable)
   {
      if(ColumnOption.BOOLEAN.equals(editorType) && path.getType() == TableDataPath.DETAIL) {
         this.cellData = Tool.getData("boolean", cellData);
         this.cellLabel = Tool.getData("boolean", cellLabel);
      }
      else {
         this.cellData = cellData;
         this.cellLabel = cellLabel;
      }

      this.vsFormatModel = vsFormatModel;
      this.dataPath = path;
      this.hyperlinks = hyperlinks != null && hyperlinks.length > 0 ? hyperlinks : null;
      this.editorType = editorType;
      this.options = options;
      this.presenter = presenter;
      this.editable = editable ? editable : null;
   }

   public static BaseTableCellModel createSimpleCell(XTable lens, int row, int col) {
      return new BaseTableCellModel(lens.getObject(row, col));
   }

   public static BaseTableCellModel createTableCell(VSAssemblyInfo assemblyInfo,
                                                    VSTableLens lens, int row, int col, int spanRow)
   {
      FormatTableLens formatTableLens = getFormatTableLens(lens.getTable());
      Object obj = lens.getObject(row, col);
      Object label = Tool.toString(obj);

      if(obj instanceof DCMergeDatesCell) {
         DCMergeDatesCell dcCell = (DCMergeDatesCell) obj;
         label = dcCell.getFormatedOriginalDate();
      }

      // date column may be grouped into integer
      if(label == null && obj != null) {
         label = obj;
      }

      Object data = formatTableLens == null ? obj : formatTableLens.getTable().getObject(row, col);

      VSFormat vsFormat = lens.getFormat(row, col, spanRow);
      VSFormatModel vsFormatModel = new VSFormatModel(vsFormat, assemblyInfo);
      vsFormatModel.setPadding(lens.getInsets(row, col));
      String drillOp = lens.getDrillOp(row, col);

      // Get any hyperlinks on the cell
      HyperlinkModel[] hyperlinks = getHyperlinks(lens, row, col);

      TableDataPath path = lens.getTableDataPath(row, col);
      String presenter = data instanceof PresenterPainter ?
         (((PresenterPainter) data).getPresenter() instanceof HTMLPresenter ? "H" : "I") :
         (obj instanceof PresenterPainter ?
         (((PresenterPainter) obj).getPresenter() instanceof HTMLPresenter ? "H" : "I") : null);
      boolean isImage = false;

      // When the data is a presenter type it messes up jackson serialization,
      // and we don't need this data anyway.
      if(presenter != null) {
         data = null;
      }

      if(data instanceof NClob) {
         data = AssetUtil.format(data);
      }

      if(data instanceof Image) {
         label = null;
         data = null;
         isImage = true;
      }

      int rowPadding = lens.getCSSRowPadding(row);
      int colPadding = lens.getCSSColumnPadding(col);
      return new BaseTableCellModel(data, label, row, col, vsFormatModel, drillOp, hyperlinks,
                                    path, presenter, isImage, rowPadding, colPadding);
   }

   public static PreviewTableCellModel createPreviewCell(TableLens lens, int row, int col,
                                                         boolean applyTableStyle, String alias)
   {
      return createPreviewCell(lens, row, col, applyTableStyle, alias, null);
   }

   public static PreviewTableCellModel createPreviewCell(TableLens lens, int row, int col,
                                                         boolean applyTableStyle, String alias,
                                                         Function<Object, Object> convertFun)
   {
      return createPreviewCell(lens, row, col, applyTableStyle, alias, convertFun, null);
   }

   public static PreviewTableCellModel createPreviewCell(TableLens lens, int row, int col,
                                                         boolean applyTableStyle, String alias,
                                                         Function<Object, Object> convertFun,
                                                         VariableTable vtable)
   {
      // Get any hyperlinks on the cell from drill info
      XDrillInfo drillInfo = lens.getXDrillInfo(row, col);
      HyperlinkModel[] hyperlinks = null;

      if(drillInfo != null) {
         DataRef dataCol = drillInfo.getColumn();
         hyperlinks = new HyperlinkModel[drillInfo.getDrillPathCount()];

         for(int i = 0; i < hyperlinks.length; i++) {
            DrillPath dpath = drillInfo.getDrillPath(i);
            DrillSubQuery query = dpath.getQuery();
            Hyperlink.Ref hyperlink = new Hyperlink.Ref(drillInfo.getDrillPath(i), lens, row, col);
            setParameter(lens, query, dataCol, hyperlink, row, col, vtable);
            hyperlinks[i] = HyperlinkModel.createHyperlinkModel(hyperlink);
         }
      }

      Object data = lens.getObject(row, col);

      if(convertFun != null) {
         data = convertFun.apply(data);
      }

      if(data instanceof DCMergeCell) {
         if(data instanceof DCMergeDatesCell) {
            data = ((DCMergeDatesCell) data).getFormatedOriginalDate(false);
         }
         else {
            data = ((DCMergeCell) data).getOriginalData();
         }
      }

      String presenter = data instanceof PresenterPainter ?
         (data instanceof HTMLPresenter ? "H" : "I") : null;

      // When the data is a presenter type it messes up jackson serialization,
      // and we don't need this data anyway.
      if(presenter != null) {
         data = null;
      }

      if(data instanceof Time || data instanceof java.sql.Date) {
         data = data.toString();
      }

      if(data instanceof MemberObject) {
         MemberObject mobj = (MemberObject) data;
         data = mobj.toView();
      }

      if(data instanceof Number) {
         data = Tool.toString(data);
      }

      data = ScriptUtil.getScriptValue(data);

      VSFormatModel fmt = null;

      if(applyTableStyle) {
         fmt = createFormatModel(lens, row, col);
      }

      data = alias == null ? data : alias;

      return new PreviewTableCellModel(data, hyperlinks, fmt);
   }

   private static VSFormatModel createFormatModel(TableLens lens, int r, int c) {
      VSCompositeFormat compositeFormat = new VSCompositeFormat();
      VSFormat userfmt = new VSFormat();

      Insets borders = new Insets(0, 0, 0, 0);
      borders.top = lens.getRowBorder(r - 1, c);
      borders.bottom = lens.getRowBorder(r, c);
      borders.left = lens.getColBorder(r, c - 1);
      borders.right = lens.getColBorder(r, c);

      Color topBColor = lens.getRowBorderColor(r - 1, c);
      Color bottomBColor = lens.getRowBorderColor(r, c);
      Color leftBColor = lens.getColBorderColor(r, c - 1);
      Color rightBColor = lens.getColBorderColor(r, c);
      BorderColors bcolors = new BorderColors(topBColor, bottomBColor, leftBColor, rightBColor);

      userfmt.setBorderColors(bcolors);
      userfmt.setBorders(borders);
      userfmt.setAlignment(lens.getAlignment(r, c));
      userfmt.setFont(lens.getFont(r, c));
      userfmt.setBackground(lens.getBackground(r, c));
      userfmt.setForeground(lens.getForeground(r, c));

      compositeFormat.setUserDefinedFormat(userfmt);
      VSFormatModel vsFormatModel = new VSFormatModel(compositeFormat);

      return vsFormatModel;
   }

   public static BaseTableCellModel createFormCell(VSAssemblyInfo info,
                                                   FormTableLens formLens,
                                                   VSTableLens lens, int row, int col)
   {
      Object obj = lens.getObject(row, col);
      Object label = formLens.getLabel(row, col) != null ?
         formLens.getLabel(row, col) : Tool.toString(obj);
      FormatTableLens formatTableLens = getFormatTableLens(lens.getTable());
      Object data = formatTableLens == null ? obj : formatTableLens.getTable().getObject(row, col);

      ColumnSelection columns = formLens.getVisibleColumns();
      FormRef ref = (FormRef) columns.getAttribute(col);
      ColumnOption option = ref.getOption();
      String type = option.getType();
      boolean editable = FormTableRow.ADDED == formLens.rows()[row].getRowState() ?
         formLens.getVisibleColumnOption(col).isForm() :
         formLens.isEdit() && formLens.getVisibleColumnOption(col).isForm();

      VSFormat vsFormat = lens.getFormat(row, col);
      VSCompositeFormat compositeFormat = new VSCompositeFormat();
      compositeFormat.setUserDefinedFormat(vsFormat);
      VSFormatModel vsFormatModel = new VSFormatModel(compositeFormat, info);
      vsFormatModel.setPadding(lens.getInsets(row, col));

      TableDataPath path = lens.getTableDataPath(row, col);

      String[] options = null;

      if(option instanceof ComboBoxColumnOption) {
         ComboBoxColumnOption comboBoxColumnOption = (ComboBoxColumnOption) option;
         ListData list = comboBoxColumnOption.getListData();

         if(list != null) {
            options = list.getLabels();
         }
      }

      HyperlinkModel[] hyperlinks = editable ? null : getHyperlinks(lens, row, col);
      String presenter = obj instanceof PresenterPainter ?
         (obj instanceof HTMLPresenter ? "H" : "I") : null;

      // When the data is a presenter type it messes up jackson serialization,
      // and we don't need this data anyway.
      if(presenter != null) {
         data = null;
      }

      return new BaseTableCellModel(data, label, row, col, vsFormatModel, hyperlinks,
                                    path, type, options, presenter, editable);
   }

   private static FormatTableLens getFormatTableLens(TableLens table) {
      if(table instanceof FormatTableLens) {
         return ((FormatTableLens) table);
      }
      else if(table instanceof TableFilter) {
         return getFormatTableLens(((TableFilter) table).getTable());
      }

      return null;
   }

   private static HyperlinkModel[] getHyperlinks(VSTableLens lens, int row, int col) {
      TableLens attrLens = Util.getNestedTable(lens, AttributeTableLens.class);
      return lens.getCellHyperlink(attrLens, row, col).stream()
         .map(HyperlinkModel::createHyperlinkModel)
         .toArray(HyperlinkModel[]::new);
   }

   /**
    *set parameter value for hyperlink.Ref
    */
   private static void setParameter(TableLens lens, DrillSubQuery query, DataRef dataCol,
                                    Hyperlink.Ref hyperlink, int row, int col, VariableTable vtable)
   {
      // Bug #41348. parameter value shouldn't apply format.
      Object value = (lens instanceof FormatTableLens ? ((FormatTableLens) lens).getTable() : lens)
         .getObject(row, col);
      String colType = Tool.getDataType(lens.getColType(col));

      // show keep the date type, else will cause error
      // when execute sub query when do auto drill.
      if(row >= lens.getHeaderRowCount() &&
         XSchema.isDateType(colType) &&
         !XSchema.isDateType(Tool.getDataType(value)))
      {
         value = Tool.getData(colType, value);
      }

      if(query != null) {
         hyperlink.setParameter(StyleConstants.SUB_QUERY_PARAM, value);
         String queryParam = null;

         if(dataCol != null) {
            queryParam = Util.findSubqueryVariable(query, dataCol.getName());
         }

         if(queryParam == null) {
            String tableHeader = lens.getColumnIdentifier(col);
            tableHeader = tableHeader == null ?
               (String) Util.getHeader(lens, col) : tableHeader;
            queryParam = Util.findSubqueryVariable(query, tableHeader);
         }

         if(queryParam != null) {
            hyperlink.setParameter(Tool.encodeWebURL(StyleConstants.SUB_QUERY_PARAM_PREFIX +
               queryParam), value);
         }

         Iterator<String> it = query.getParameterNames();
         ColumnIndexMap columnIndexMap = new ColumnIndexMap(lens, true);

         while(it.hasNext()) {
            String qvar = it.next();

            if(Tool.equals(qvar, queryParam)) {
               continue;
            }

            String header = query.getParameter(qvar);
            int column = Util.findColumn(columnIndexMap, header);

            if(column < 0) {
               continue;
            }

            hyperlink.setParameter(Tool.encodeWebURL(StyleConstants.SUB_QUERY_PARAM_PREFIX +
               qvar), value);
         }
      }

      if(hyperlink.isSendReportParameters()) {
         Util.addLinkParameter(hyperlink, vtable);
      }
   }

   /**
    * Static method to create a spanCell which contains a property that indicates that
    * it should not be rendered on the client side.
    * @return An empty BaseTableCellModel
    */
   public static BaseTableCellModel createSpanCell() {
      return new BaseTableCellModel(true);
   }

   @Override
   public BaseTableCellModelPrototype createModelPrototype() {
      return new PrototypedBaseTableCellModel(this);
   }

   @Override
   public void setModelPrototypeIndex(int index) {
      this.prototypeIndex = index;
      this.dataPath = null;
      this.vsFormatModel = null;
      this.field = null;
      this.bindingType = null;
   }


   public Object getCellData() {
      return cellData;
   }

   @Nullable
   public Object getCellLabel() {
      return !Objects.equals(cellData, cellLabel) ? cellLabel : null;
   }

   public void setCellLabel(Object cellLabel) {
      this.cellLabel = cellLabel;
   }

   public void formatCellData(Object formatCellData) {
      this.cellData = formatCellData;
   }

   @Override
   public VSFormatModel getVsFormatModel() {
      return vsFormatModel;
   }

   @Nullable
   public Boolean isSpanCell() {
      return spanCell;
   }

   @Nullable
   public Integer getRowSpan() {
      return rowSpan;
   }

   @Nullable
   public Integer getColSpan() {
      return colSpan;
   }

   public void setRowSpan(Integer rowSpan) {
      this.rowSpan = rowSpan != null && rowSpan != 1 ? rowSpan : null;
   }

   public void setColSpan(Integer colSpan) {
      this.colSpan = colSpan != null && colSpan != 1 ? colSpan : null;
   }

   @Nullable
   public String getDrillOp() {
      return drillOp;
   }

   @Nullable
   public HyperlinkModel[] getHyperlinks() {
      return hyperlinks;
   }

   @Nullable
   public Boolean isGrouped() {
      return grouped;
   }

   public void setGrouped(Boolean grouped) {
      this.grouped = grouped != null && grouped ? grouped : null;
   }

   @Override
   public TableDataPath getDataPath() {
      return dataPath;
   }

   public void setDataPath(TableDataPath dataPath) {
      this.dataPath = dataPath;
   }

   @Nullable
   public String getEditorType() {
      return editorType;
   }

   public void setEditorType(String editorType) {
      this.editorType = editorType;
   }

   @Nullable
   public String[] getOptions() {
      return options;
   }

   public void setOptions(String[] options) {
      this.options = options;
   }

   @Nullable
   public String getField() {
      return field;
   }

   public void setField(String field) {
      this.field = field;
   }

   public String getPresenter() {
      return presenter;
   }

   public void setPresenter(String presenter) {
      this.presenter = presenter;
   }

   @Nullable
   public Boolean isEditable() {
      return editable;
   }

   public void setEditable(Boolean editable) {
      this.editable = editable != null && editable ? editable : null;
   }

   @Nullable
   public Integer getBindingType() {
      return bindingType;
   }

   public void setBindingType(Integer bindingType) {
      this.bindingType = bindingType;
   }

   @Nullable
   public Boolean getIsImage() {
      return isImage;
   }

   public void setIsImage(Boolean isImage) {
      this.isImage = isImage != null && isImage ? true : null;
   }

   @Override
   public int getProtoIdx() {
      return prototypeIndex;
   }

   @Nullable
   public DrillLevel getDrillLevel() {
      return drillLevel;
   }

   public void setDrillLevel(DrillLevel drillLevel) {
      this.drillLevel = drillLevel == DrillLevel.None ? null : drillLevel;
   }

   @Nullable
   public Boolean getHasCalc() {
      return hasCalc;
   }

   public void setHasCalc(Boolean hasCalc) {
      this.hasCalc = hasCalc;
   }

   @Nullable
   public Boolean getPeriod() {
      return period;
   }

   public void setPeriod(Boolean period) {
      this.period = period;
   }

   @Nullable
   public Boolean isGrandTotalRow() {
      return grandTotalRow;
   }

   public void setGrandTotalRow(boolean grandTotalRow) {
      this.grandTotalRow = grandTotalRow ? true : null;
   }

   @Nullable
   public Boolean isGrandTotalCol() {
      return grandTotalCol;
   }

   public void setGrandTotalCol(boolean grandTotalCol) {
      this.grandTotalCol = grandTotalCol ? true : null;
   }

   @Nullable
   public Boolean isTotalRow() {
      return totalRow;
   }

   public void setTotalRow(boolean totalRow) {
      this.totalRow = totalRow ? true : null;
   }

   @Nullable
   public Boolean isTotalCol() {
      return totalCol;
   }

   public void setTotalCol(boolean totalCol) {
      this.totalCol = totalCol ? true : null;
   }

   @Nullable
   public Boolean isGrandTotalHeaderCell() {
      return grandTotalHeaderCell;
   }

   public void setGrandTotalHeaderCell(boolean grandTotalHeaderCell) {
      this.grandTotalHeaderCell = grandTotalHeaderCell ? true : null;
   }

   public int getRowPadding() {
      return rowPadding;
   }

   public void setRowPadding(int rowPadding) {
      this.rowPadding = rowPadding;
   }

   public int getColPadding() {
      return colPadding;
   }

   public void setColPadding(int colPadding) {
      this.colPadding = colPadding;
   }

   /**
    * Concrete prototype implementation with non-prototyped properties omitted.
    */
   public static class PrototypedBaseTableCellModel implements BaseTableCellModelPrototype {
      public PrototypedBaseTableCellModel(BaseTableCellModelPrototype prototype) {
         this.dataPath = prototype.getDataPath();
         this.vsFormatModel = prototype.getVsFormatModel();
         this.field = prototype.getField();
         this.bindingType = prototype.getBindingType();
      }

      @Override
      public TableDataPath getDataPath() {
         return dataPath;
      }

      @Override
      public VSFormatModel getVsFormatModel() {
         return vsFormatModel;
      }

      @Override
      public String getField() {
         return field;
      }

      @Override
      public Integer getBindingType() {
         return bindingType;
      }

      @Override
      public boolean equals(Object o) {
         if(this == o) {
            return true;
         }
         if(o == null || getClass() != o.getClass()) {
            return false;
         }
         PrototypedBaseTableCellModel that = (PrototypedBaseTableCellModel) o;
         return Objects.equals(dataPath, that.dataPath) &&
            Objects.equals(vsFormatModel, that.vsFormatModel) &&
            Objects.equals(field, that.field) &&
            Objects.equals(bindingType, that.bindingType);
      }

      @Override
      public int hashCode() {
         return Objects.hash(dataPath, vsFormatModel, field, bindingType);
      }

      private TableDataPath dataPath;
      private VSFormatModel vsFormatModel;
      private String field;
      private Integer bindingType;
   }

   private Object cellLabel;
   private Object cellData;
   private VSFormatModel vsFormatModel;
   private Integer rowSpan;
   private Integer colSpan;
   private Boolean spanCell;
   private String drillOp;
   private HyperlinkModel[] hyperlinks;
   private Boolean grouped;
   private TableDataPath dataPath;
   private String editorType;
   private String[] options;
   private String field;
   private String presenter;
   private Boolean editable;
   private Integer bindingType;
   private Boolean isImage;
   private int prototypeIndex = -1;
   private DrillLevel drillLevel = null;
   private Boolean hasCalc;
   private Boolean period;
   private Boolean grandTotalRow;
   private Boolean grandTotalCol;
   private Boolean totalRow;
   private Boolean totalCol;
   private Boolean grandTotalHeaderCell;
   private int rowPadding;
   private int colPadding;
}
