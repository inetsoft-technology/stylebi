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
package inetsoft.uql.viewsheet.internal;

import inetsoft.graph.GraphConstants;
import inetsoft.graph.aesthetic.CategoricalColorFrame;
import inetsoft.report.StyleConstants;
import inetsoft.report.TableDataPath;
import inetsoft.uql.CompositeValue;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.*;
import inetsoft.util.Tool;
import inetsoft.util.css.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.awt.*;
import java.io.PrintWriter;
import java.util.List;

/**
 * This is the base class for SelectionListVSAssemblyInfo and
 * SelectionTreeVSAssemblyInfo.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public abstract class SelectionBaseVSAssemblyInfo extends MaxModeSelectionVSAssemblyInfo
   implements CompositeVSAssemblyInfo, DropDownVSAssemblyInfo
{
   /**
    * Constructor.
    */
   protected SelectionBaseVSAssemblyInfo() {
      super();
      setPixelSize(new Dimension(AssetUtil.defw, AssetUtil.defh * 6));
   }

   /**
    * Initialize the default format.
    */
   @Override
   public void initDefaultFormat() {
      setDefaultFormat(false);
      // getFormat().getDefaultFormat().setBackgroundValue("0xffffff");
      // getFormat().getDefaultFormat().setAlphaValue(30);
   }

   /**
    * Get the runtime show type.
    * @return the show type.
    */
   public int getShowType() {
     return showTypeValue.getIntValue(false, 0);
   }

   /**
    * Get the design time show type.
    * @return the show type.
    */
   public int getShowTypeValue() {
      return showTypeValue.getIntValue(true, 0);
   }

   /**
    * Set the runtime show type.
    * @param type the show type.
    */
   public void setShowType(int type) {
      showTypeValue.setRValue(type);
   }

   /**
    * Set the design time show type.
    * @param type the show type.
    */
   public void setShowTypeValue(int type) {
      showTypeValue.setDValue(type + "");
   }

   /**
    * Get the list height.
    * @return the list height.
    */
   @Override
   public int getListHeight() {
      return listHeight;
   }

   /**
    * Set the list height.
    * @param height the list height.
    */
   @Override
   public void setListHeight(int height) {
      this.listHeight = height;
   }

   /**
    * Get the cell height.
    * @return the cell height.
    */
   public int getCellHeight() {
      return cellHeight;
   }

   /**
    * Set the cell height.
    * @param cellHeight the cell height.
    */
   public void setCellHeight(int cellHeight) {
      this.cellHeight = cellHeight;
   }

   public Insets getCellPadding() {
      return cellPadding.get();
   }

   public void setCellPadding(Insets cellPadding, CompositeValue.Type type) {
      this.cellPadding.setValue(cellPadding, type);
   }

   @Override
   public double getListHeightScale() {
      return listHeightScale;
   }

   @Override
   public void setListHeightScale(double listHeightScale) {
      this.listHeightScale = listHeightScale;
   }

   /**
    * Get the runtime sort type.
    * @return the sort type.
    */
   public int getSortType() {
      return sortTypeValue.getIntValue(false, XConstants.SORT_SPECIFIC);
   }

   /**
    * Get the design time sort type.
    * @return the sort type.
    */
   public int getSortTypeValue() {
      return sortTypeValue.getIntValue(true, XConstants.SORT_SPECIFIC);
   }

   /**
    * Set the runtime sort type.
    * @param type the sort type, one of the following: XConstants.SORT_ASC,
    * XConstants.SORT_DESC, XConstants.SORT_SPECIFIC (selected items first),
    * XConstants.SORT_NONE.
    */
   public void setSortType(int type) {
      sortTypeValue.setRValue(type);
   }

   /**
    * Set the design time sort type.
    * @param type the sort type, one of the following: XConstants.SORT_ASC,
    * XConstants.SORT_DESC, XConstants.SORT_SPECIFIC (selected items first),
    * XConstants.SORT_NONE.
    */
   public void setSortTypeValue(int type) {
      sortTypeValue.setDValue(type + "");
   }

   /**
    * Check if design time suppress-empty-value is enabled.
    */
   public boolean isSuppressBlankValue() {
      return suppressBlank;
   }

   /**
    * Set if design time suppress-empty-value is enabled.
    */
   public void setSuppressBlankValue(boolean suppress) {
      this.suppressBlank = suppress;
   }

   /**
    * Get the run time title.
    * @return the title of the selection list assembly.
    */
   @Override
   public String getTitle() {
      return titleInfo.getTitle(getFormatInfo().getFormat(TITLEPATH), getViewsheet(), getName());
   }

   /**
    * Set the run time title.
    * @param value the specified selection list title.
    */
   @Override
   public void setTitle(String value) {
      titleInfo.setTitle(value);
   }

   /**
    * Get the design time title.
    * @return the title value of the selection list assembly.
    */
   @Override
   public String getTitleValue() {
      return titleInfo.getTitleValue();
   }

   /**
    * Set the design time title.
    * @param value the specified group title.
    */
   @Override
   public void setTitleValue(String value) {
      titleInfo.setTitleValue(value);
   }

   /**
    * Check whether the selection title is visible.
    * @return the title visible of the selection assembly.
    */
   @Override
   public boolean isTitleVisible() {
      return titleInfo.isTitleVisible();
   }

   /**
    * Set the run time selection title visible.
    * @param visible visible value of the specified selection title.
    */
   @Override
   public void setTitleVisible(boolean visible) {
      titleInfo.setTitleVisible(visible);
   }

   /**
    * Get the design time selection title visible value.
    * @return the title visible value of the selection assembly.
    */
   @Override
   public boolean getTitleVisibleValue() {
       return titleInfo.getTitleVisibleValue();
   }

   /**
    * Set the design time selection title visible.
    * @param visible visible value of the specified selection title.
    */
   @Override
   public void setTitleVisibleValue(boolean visible) {
      titleInfo.setTitleVisibleValue(visible + "");
   }

   /**
    * Get the selection title height.
    * @return the title height of the selection assembly.
    */
   @Override
   public int getTitleHeight() {
      return titleInfo.getTitleHeight();
   }

   /**
    * Get the selection title height value.
    * @return the title height value of the selection assembly.
    */
   @Override
   public int getTitleHeightValue() {
      return titleInfo.getTitleHeightValue();
   }

   /**
    * Set the selection title height value.
    * @param value the specified selection title height.
    */
   @Override
   public void setTitleHeightValue(int value) {
      titleInfo.setTitleHeightValue(value);
   }

   /**
    * Set the selection title height.
    * @param value the specified selection title height.
    */
   @Override
   public void setTitleHeight(int value) {
      titleInfo.setTitleHeight(value);
   }

   @Override
   public Insets getTitlePadding() {
      return titleInfo.getPadding();
   }

   @Override
   public void setTitlePadding(Insets padding, CompositeValue.Type type) {
      titleInfo.setPadding(padding, type);
   }

   /**
    * Check if measure value shown as text.
    */
   public boolean isShowText() {
      return Boolean.parseBoolean(mtextValue.getRuntimeValue(true) + "");
   }

   /**
    * Set if measure value shown as text.
    */
   public void setShowText(boolean flag) {
      mtextValue.setRValue(flag + "");
   }

   /**
    * Check if measure value shown as text.
    */
   public boolean isShowTextValue() {
      return Boolean.parseBoolean(mtextValue.getDValue() + "");
   }

   /**
    * Set if measure value shown as text.
    */
   public void setShowTextValue(boolean flag) {
      mtextValue.setDValue(flag + "");
   }

   /**
    * Check if measure value shown as text.
    */
   public boolean isShowBar() {
      return Boolean.parseBoolean(mbarValue.getRuntimeValue(true) + "");
   }

   /**
    * Set if measure value shown as text.
    */
   public void setShowBar(boolean flag) {
      mbarValue.setRValue(flag + "");
   }

   /**
    * Check if measure value shown as text.
    */
   public boolean isShowBarValue() {
      return Boolean.parseBoolean(mbarValue.getDValue() + "");
   }

   /**
    * Set if measure value shown as text.
    */
   public void setShowBarValue(boolean flag) {
      mbarValue.setDValue(flag + "");
   }

   /**
    * Get the bar width.
    */
   public int getBarSize() {
      return barsize;
   }

   /**
    * Set the bar width.
    */
   public void setBarSize(int barsize) {
      this.barsize = barsize;
   }

   /**
    * Get the measure label width.
    */
   public int getMeasureSize() {
      return mtextsize;
   }

   /**
    * Set the measure label width.
    */
   public void setMeasureSize(int mtextsize) {
      this.mtextsize = mtextsize;
   }

   /**
    * Get the measure column for displaying the value for selection items.
    */
   public String getMeasure() {
      Object obj = measureValue.getRuntimeValue(true);

      if(obj instanceof String) {
         return (String) obj;
      }

      return null;
   }

   /**
    * Set the measure column for displaying the value for selection items.
    */
   public void setMeasure(String measure) {
      measureValue.setRValue(measure);
   }

   /**
    * Get the measure column for displaying the value for selection items.
    */
   public String getMeasureValue() {
      return measureValue.getDValue();
   }

   /**
    * Set the measure column for displaying the value for selection items.
    */
   public void setMeasureValue(String measure) {
      measureValue.setDValue(measure);
   }

   /**
    * Get the formula for aggregating the measure column.
    */
   public String getFormula() {
      Object obj = formulaValue.getRuntimeValue(true);

      if(obj instanceof String) {
         return (String) obj;
      }

      return null;
   }

   /**
    * Set the formula for aggregating the measure column.
    */
   public void setFormula(String formula) {
      formulaValue.setRValue(formula);
   }

   /**
    * Get the formula for aggregating the measure column.
    */
   public String getFormulaValue() {
      return formulaValue.getDValue();
   }

   /**
    * Set the formula for aggregating the measure column.
    */
   public void setFormulaValue(String formula) {
      formulaValue.setDValue(formula);
   }

   /**
    * get if the selection element is currently bound with meta data
    */
   public boolean isUsingMetaData() {
      return this.usingMetaData;
   }

   /**
    *set if the selection element is currently bound with meta data
    */
   public void setUsingMetaData(boolean usingMetaData) {
      this.usingMetaData = usingMetaData;
   }


   @Override
   public boolean bindedCalcFields() {
      if(!Tool.isEmptyString(getMeasure()) && getFirstTableName() != null &&
         vs.getCalcField(getFirstTableName(), getMeasure()) != null)
      {
         return true;
      }

      return super.bindedCalcFields();
   }

   /**
    * Rename the depended. This method should be called when an assembly or
    * other named variables are renamed. It updates of the dynamic references
    * to use the new name.
    * @param oname the specified old name.
    * @param nname the specified new name.
    */
   @Override
   public void renameDepended(String oname, String nname, Viewsheet vs) {
      super.renameDepended(oname, nname, vs);

      titleInfo.renameDepended(oname, nname, vs);
      VSUtil.renameDynamicValueDepended(oname, nname, measureValue, vs);
      VSUtil.renameDynamicValueDepended(oname, nname, formulaValue, vs);
   }

   /**
    * Write attributes.
    * @param writer the specified writer.
    */
   @Override
   protected void writeAttributes(PrintWriter writer) {
      super.writeAttributes(writer);

      writer.print(" showType=\"" + getShowType() + "\"");
      writer.print(" showTypeValue=\"" + showTypeValue.getDValue() + "\"");
      writer.print(" sortType=\"" + getSortType() + "\"");
      writer.print(" sortTypeValue=\"" + sortTypeValue.getDValue() + "\"");
      writer.print(" showText=\"" + isShowText() + "\"");
      writer.print(" showTextValue=\"" + isShowTextValue() + "\"");
      writer.print(" showBar=\"" + isShowBar() + "\"");
      writer.print(" showBarValue=\"" + isShowBarValue() + "\"");
      writer.print(" listHeight=\"" + listHeight + "\"");
      writer.print(" cellHeight=\"" + cellHeight + "\"");
      writer.print(" listHeightScale=\"" + listHeightScale + "\"");
      writer.print(" barSize=\"" + barsize + "\"");
      writer.print(" textSize=\"" + mtextsize + "\"");
      writer.print(" suppressBlankValue=\"" + suppressBlank + "\"");
      writer.print(" cellPadding=\"" + cellPadding + "\"");
   }

   /**
    * Parse attributes.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseAttributes(Element elem) {
      super.parseAttributes(elem);

      String text = Tool.getAttribute(elem, "listHeight");
      listHeight = text == null ? 6 : Integer.parseInt(text);
      text = Tool.getAttribute(elem, "cellHeight");
      cellHeight = text == null ? AssetUtil.defh : Integer.parseInt(text);
      text = Tool.getAttribute(elem, "listHeightScale");
      listHeightScale = text == null ? 1D : Double.parseDouble(text);
      text = Tool.getAttribute(elem, "barSize");
      barsize = text == null ? -1 : Integer.parseInt(text);
      text = Tool.getAttribute(elem, "textSize");
      mtextsize = text == null ? -1 : Integer.parseInt(text);

      text = Tool.getAttribute(elem, "showTypeValue");
      text = text == null ? Tool.getAttribute(elem, "showType") : text;
      setShowTypeValue((text == null || "null".equals(text)) ?
         2 : Integer.parseInt(text));

      sortTypeValue.setDValue(
         getAttributeStr(elem, "sortType", XConstants.SORT_SPECIFIC + ""));
      mtextValue.setDValue(getAttributeStr(elem, "showTextValue", "true"));
      mbarValue.setDValue(getAttributeStr(elem, "showBarValue", "false"));
      suppressBlank = "true".equals(Tool.getAttribute(elem, "suppressBlankValue"));
      cellPadding.parse(Tool.getAttribute(elem, "cellPadding"));
   }

   /**
    * Write contents.
    * @param writer the specified writer.
    */
   @Override
   protected void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      if(titleInfo != null) {
         titleInfo.writeXML(writer, getFormatInfo().getFormat(TITLEPATH),
            getViewsheet(), getName());
      }

      if(getMeasure() != null && getMeasure().length() > 0) {
         writer.print("<measure>");
         writer.print("<![CDATA[" + getMeasure() + "]]>");
         writer.println("</measure>");
      }

      if(measureValue.getDValue() != null) {
         writer.print("<measureValue>");
         writer.print("<![CDATA[" + measureValue.getDValue() + "]]>");
         writer.println("</measureValue>");
      }

      if(formulaValue.getDValue() != null) {
         writer.print("<formulaValue>");
         writer.print("<![CDATA[" + formulaValue.getDValue() + "]]>");
         writer.println("</formulaValue>");
      }

      if(search2 != null && search2.length() != 0) {
         writer.print("<search2>");
         writer.print("<![CDATA[" + search2 + "]]>");
         writer.println("</search2>");
      }
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseContents(Element elem) throws Exception {
      super.parseContents(elem);

      titleInfo.parseXML(elem);

      Element node = Tool.getChildNodeByTagName(elem, "measureValue");
      measureValue.setDValue(Tool.getValue(node));

      node = Tool.getChildNodeByTagName(elem, "formulaValue");
      formulaValue.setDValue(Tool.getValue(node));
   }

   /**
    * Clone this object.
    * @param shallow <tt>true</tt> to perform shallow clone,
    * <tt>false</tt> to perform deep clone.
    * @return the cloned object.
    */
   @Override
   public SelectionBaseVSAssemblyInfo clone(boolean shallow) {
      try {
         SelectionBaseVSAssemblyInfo info = (SelectionBaseVSAssemblyInfo) super.clone(shallow);

         if(!shallow) {
            if(titleInfo != null) {
               info.titleInfo = (TitleInfo) titleInfo.clone();
            }

            if(measureValue != null) {
               info.measureValue = (DynamicValue) measureValue.clone();
            }

            if(formulaValue != null) {
               info.formulaValue = (DynamicValue) formulaValue.clone();
            }

            if(mtextValue != null) {
               info.mtextValue = (DynamicValue) mtextValue.clone();
            }

            if(mbarValue != null) {
               info.mbarValue = (DynamicValue) mbarValue.clone();
            }

            if(sortTypeValue != null) {
               info.sortTypeValue = (DynamicValue2) sortTypeValue.clone();
            }

            if(showTypeValue != null) {
               info.showTypeValue = (DynamicValue2) showTypeValue.clone();
            }

            info.cellPadding = (CompositeValue<Insets>) cellPadding.clone();
         }

         return info;
      }
      catch(Exception ex) {
         LOG.error(
                     "Failed to clone SelectionBaseVSAssemblyInfo", ex);
      }

      return null;
   }

   @Override
   public int copyInfo(VSAssemblyInfo info) {
      boolean dataChanged = false;

      for(int i = 0; i < 5; i++) {
         TableDataPath path = getMeasureTextPath(i);

         if(!Tool.equals(getFormatInfo().getFormat(path),
                         info.getFormatInfo().getFormat(path)))
         {
            dataChanged = true;
            break;
         }
      }

      int rc = super.copyInfo(info);

      if(dataChanged) {
         rc = rc | VSAssembly.OUTPUT_DATA_CHANGED;
      }

      return rc;
   }

   /**
    * Copy the input data part assembly info.
    * @param info the specified viewsheet assembly info.
    * @return new hint.
    */
   @Override
   protected int copyInputDataInfo(VSAssemblyInfo info, int hint) {
      hint = super.copyInputDataInfo(info, hint);
      SelectionBaseVSAssemblyInfo sinfo = (SelectionBaseVSAssemblyInfo) info;

      if(!Tool.equals(getTableNames(), sinfo.getTableNames())) {
         setTableNames(sinfo.getTableNames());
         hint |= VSAssembly.INPUT_DATA_CHANGED;
         hint |= VSAssembly.BINDING_CHANGED;
      }

      if(!Tool.equals(measureValue, sinfo.measureValue)) {
         measureValue = sinfo.measureValue;
         hint |= VSAssembly.INPUT_DATA_CHANGED;
      }

      if(!Tool.equals(formulaValue, sinfo.formulaValue)) {
         formulaValue = sinfo.formulaValue;
         hint |= VSAssembly.INPUT_DATA_CHANGED;
      }

      if(!Tool.equals(suppressBlank, sinfo.suppressBlank)) {
         suppressBlank = sinfo.suppressBlank;
         hint |= VSAssembly.INPUT_DATA_CHANGED;
      }

      return hint;
   }

   /**
    * Copy the view part assembly info.
    * @param info the specified viewsheet assembly info.
    * @return <tt>true</tt> if changed, <tt>false</tt> otherwise.
    */
   @Override
   protected boolean copyViewInfo(VSAssemblyInfo info, boolean deep) {
      boolean result = super.copyViewInfo(info, deep);
      SelectionBaseVSAssemblyInfo sinfo = (SelectionBaseVSAssemblyInfo) info;

      if(!Tool.equals(showTypeValue, sinfo.showTypeValue) ||
        (!Tool.equals(getShowType(), sinfo.getShowType())))
      {
         showTypeValue = sinfo.showTypeValue;
         result = true;
      }

      if(listHeight != sinfo.listHeight) {
         listHeight = sinfo.listHeight;
         result = true;
      }

      if(cellHeight != sinfo.cellHeight) {
         cellHeight = sinfo.cellHeight;
         result = true;
      }

      if(listHeightScale != sinfo.listHeightScale) {
         listHeightScale = sinfo.listHeightScale;
         result = true;
      }

      if(barsize != sinfo.barsize) {
         barsize = sinfo.barsize;
         result = true;
      }

      if(mtextsize != sinfo.mtextsize) {
         mtextsize = sinfo.mtextsize;
         result = true;
      }

      if(!titleInfo.equals(sinfo.titleInfo)) {
         titleInfo = (TitleInfo) sinfo.titleInfo.clone();
         result = true;
      }

      if(!Tool.equals(sortTypeValue, sinfo.sortTypeValue) ||
         !Tool.equals(getSortType(), sinfo.getSortType()))
      {
         sortTypeValue = sinfo.sortTypeValue;
         result = true;
      }

      if(isShowTextValue() != sinfo.isShowTextValue()) {
         mtextValue = sinfo.mtextValue;
         result = true;
      }

      if(isShowBarValue() != sinfo.isShowBarValue()) {
         mbarValue = sinfo.mbarValue;
         result = true;
      }

      return result;
   }

   /**
    * Get the dynamic property values. Dynamic properties are properties that
    * can be a variable or a script.
    */
   @Override
   public List<DynamicValue> getDynamicValues() {
      List<DynamicValue> list = super.getDynamicValues();
      list.add(measureValue);
      list.add(formulaValue);

      return list;
   }

   /**
    * Get the view dynamic values.
    */
   @Override
   public List<DynamicValue> getViewDynamicValues(boolean all) {
      List<DynamicValue> list = super.getViewDynamicValues(all);
      list.addAll(titleInfo.getViewDynamicValues());

      return list;
   }

   /**
    * Set the default vsobject format.
    * @param border border
    */
   @Override
   protected void setDefaultFormat(boolean border) {
      super.setDefaultFormat(border);

      VSCompositeFormat format = new VSCompositeFormat();

      // detail cells
      TableDataPath datapath = new TableDataPath(-1, TableDataPath.DETAIL);
      Font font = getDefaultFont(Font.PLAIN, 10);
      format.getCSSFormat().setCSSType(getObjCSSType() + CSSConstants.CELL);
      format.getDefaultFormat().setFontValue(font);
      format.getDefaultFormat().setForegroundValue("0x2b2b2b");
      format.getDefaultFormat().setBackgroundValue(null);
      format.getDefaultFormat().setAlignmentValue(StyleConstants.H_LEFT | StyleConstants.V_CENTER);
      getFormatInfo().setFormat(datapath, format);

      // title cell
      datapath = TITLEPATH;
      font = getDefaultFont(Font.BOLD, 11);
      format = new VSCompositeFormat();
      format.getCSSFormat().setCSSType(getObjCSSType() + CSSConstants.TITLE);
      format.getDefaultFormat().setFontValue(font);
      format.getDefaultFormat().setBordersValue(
         new Insets(GraphConstants.NONE, GraphConstants.NONE,
                    GraphConstants.THIN_LINE, GraphConstants.NONE));
      format.getDefaultFormat().setBorderColorsValue(
         new BorderColors(new Color(0xc0c0c0), new Color(0xc0c0c0),
                          new Color(0xc0c0c0), new Color(0xc0c0c0)));
      format.getDefaultFormat().setAlignmentValue(StyleConstants.H_LEFT | StyleConstants.V_CENTER);
      getFormatInfo().setFormat(datapath, format);
      font = getDefaultFont(Font.PLAIN, 11);

      for(int i = 0; i < 5; i++) {
         TableDataPath path = getMeasureTextPath(i);
         format = new VSCompositeFormat();
         format.getDefaultFormat().setAlignmentValue(StyleConstants.RIGHT);
         format.getDefaultFormat().setFontValue(font);
         format.getCSSFormat().setCSSType("MeasureText");
         getFormatInfo().setFormat(path, format);

         path = getMeasureBarPath(i);
         format = new VSCompositeFormat();
         format.getDefaultFormat().setForegroundValue(
            CategoricalColorFrame.COLOR_PALETTE[0].getRGB() + "");
         format.getDefaultFormat().setFontValue(font);
         format.getCSSFormat().setCSSType("MeasureBar");
         getFormatInfo().setFormat(path, format);

         path = getMeasureNBarPath(i);
         format = new VSCompositeFormat();
         format.getDefaultFormat().setForegroundValue(SOFT_RED.getRGB() + "");
         format.getDefaultFormat().setFontValue(font);
         format.getCSSFormat().setCSSType("MeasureNBar");
         getFormatInfo().setFormat(path, format);
      }
   }

   /**
    * Set the search string.
    */
   public void setSearchString(String search) {
      this.search = search;
      this.search2 = search;
   }

   /**
    * Get the search string.
    */
   public String getSearchString() {
      return search;
   }

   /**
    * Reset runtime values.
    */
   @Override
   public void resetRuntimeValues() {
      super.resetRuntimeValues();

      showTypeValue.setRValue(null);
      sortTypeValue.setRValue(null);
      titleInfo.resetRuntimeValues();
      measureValue.setRValue(null);
      formulaValue.setRValue(null);
   }

   /**
    * Get the meature text ratio as part of the total label.
    */
   public double getMeasureTextRatio() {
      SelectionList slist = null;

      if(this instanceof SelectionListVSAssemblyInfo) {
         slist = ((SelectionListVSAssemblyInfo) this).getSelectionList();
      }
      else if(this instanceof SelectionTreeVSAssemblyInfo) {
         SelectionTreeVSAssemblyInfo tree = (SelectionTreeVSAssemblyInfo) this;
         CompositeSelectionValue value = tree.getCompositeSelectionValue();
         slist = value != null ? value.getSelectionList() : null;
      }

      if(slist == null) {
         return -1;
      }

      double maxlabel = 0; // max label length
      double maxmtext = 0; // max measure text length

      for(SelectionValue sval : slist.getAllSelectionValues()) {
         maxlabel = Math.max(maxlabel, sval.getLabel().length());

         if(sval.getMeasureLabel() != null) {
            maxmtext = Math.max(maxmtext, sval.getMeasureLabel().length());
         }
      }

      return maxmtext / (maxlabel + maxmtext);
   }

   @Override
   public void updateCSSValues() {
      super.updateCSSValues();

      VSCompositeFormat cellFormat = getFormatInfo().getFormat(CELLPATH);

      if(cellFormat == null) {
         return;
      }

      VSCSSFormat cssCellFormat = cellFormat.getCSSFormat();
      CSSParameter[] cssParams = CSSParameter.getAllCSSParams(
         cssCellFormat.getParentCSSParams(), cssCellFormat.getCSSParam());
      CSSDictionary cssDictionary = CSSDictionary.getDictionary();

      if(cssDictionary.isPaddingDefined(cssParams)) {
         Insets padding = cssDictionary.getPadding(cssParams);
         cellPadding.setValue(padding, CompositeValue.Type.CSS);
      }
   }

   /**
    * Get the measure label data path.
    */
   public static TableDataPath getMeasureTextPath(int level) {
      return new TableDataPath(
         level, TableDataPath.DETAIL, XSchema.STRING,
         new String[] {"Measure Text"}, false, false);
   }

   /**
    * Get the measure bar data path.
    */
   public static TableDataPath getMeasureBarPath(int level) {
      return new TableDataPath(
         level, TableDataPath.DETAIL, XSchema.STRING,
         new String[] {"Measure Bar"}, false, false);
   }

   /**
    * Get the negatie measure bar data path.
    */
   public static TableDataPath getMeasureNBarPath(int level) {
      return new TableDataPath(
         level, TableDataPath.DETAIL, XSchema.STRING,
         new String[] {"Measure Bar(-)"}, false, false);
   }

   private static final Color SOFT_RED = new Color(0xFF4040);

   // view
   private DynamicValue2 showTypeValue = new DynamicValue2("0", XSchema.INTEGER);
   private int listHeight = 6;
   private int cellHeight = AssetUtil.defh;
   private CompositeValue<Insets> cellPadding = new CompositeValue<>(Insets.class, null);
   private double listHeightScale = 1D;
   private DynamicValue mtextValue = new DynamicValue("true", XSchema.BOOLEAN);
   private DynamicValue mbarValue = new DynamicValue("false", XSchema.BOOLEAN);
   private int barsize = -1;
   private int mtextsize = -1;
   // input data
   private DynamicValue2 sortTypeValue =
      new DynamicValue2(XConstants.SORT_SPECIFIC + "", XSchema.INTEGER);
   private TitleInfo titleInfo = new TitleInfo("SelectionList");
   private DynamicValue measureValue = new DynamicValue(null);
   private DynamicValue formulaValue = new DynamicValue(XConstants.COUNT_FORMULA);
   private boolean suppressBlank;
   // runtime data
   private String search;
   //only for display on vs.
   private String search2;
   private boolean usingMetaData = true;

   public static final TableDataPath CELLPATH = new TableDataPath(-1, TableDataPath.DETAIL);
   private static final Logger LOG =
      LoggerFactory.getLogger(SelectionBaseVSAssemblyInfo.class);
}
