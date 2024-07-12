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

import inetsoft.report.StyleConstants;
import inetsoft.report.TableDataPath;
import inetsoft.uql.CompositeValue;
import inetsoft.uql.erm.AbstractDataRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.*;
import inetsoft.util.CoreTool;
import inetsoft.util.Tool;
import inetsoft.util.css.CSSConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.awt.*;
import java.awt.geom.Point2D;
import java.io.PrintWriter;
import java.util.List;
import java.util.*;

/**
 * CalendarVSAssemblyInfo, the assembly info of a calendar assembly.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class CalendarVSAssemblyInfo extends SelectionVSAssemblyInfo
   implements TitledVSAssemblyInfo
{
   /**
    * Show a single calendar.
    */
   public static final int SINGLE_CALENDAR_MODE = 1;
   /**
    * Show two calendars side-by-side.
    */
   public static final int DOUBLE_CALENDAR_MODE = 2;

   /**
    * Show the calendar in full view.
    */
   public static final int CALENDAR_SHOW_TYPE = 1;
   /**
    * Show the calendar as a dropdown view.
    */
   public static final int DROPDOWN_SHOW_TYPE = 2;

   /**
    * Constructor.
    */
   public CalendarVSAssemblyInfo() {
      super();

      dates = new String[0];
      setPixelSize(new Dimension(300, 200));
   }

   /**
    * Initialize the default format.
    */
   @Override
   public void initDefaultFormat() {
      setFormatInfo((FormatInfo) normalDefault.clone());
      setCSSDefaults();
   }

   /**
    * Get the data ref.
    * @return the data ref.
    */
   public DataRef getDataRef() {
      return column;
   }

   /**
    * Set the column ref.
    * @param column column ref.
    */
   public void setDataRef(DataRef column) {
      this.column = column;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public DataRef[] getDataRefs() {
      final DataRef ref = getDataRef();

      if(ref == null) {
         return new DataRef[0];
      }

      return new DataRef[] {ref};
   }

   /**
    * Get the dates.
    * @return the dates.
    */
   public String[] getDates() {
      return dates;
   }

   /**
    * Set the dates.
    * @param dates the dates.
    */
   public void setDates(String[] dates) {
      setDates(dates, false);
   }

   /**
    * @damianwysocki created this so we can mimic flash behavior, because flash
    * does not check range before setting dates it leads to different behavior
    * Set the dates.
    * @param dates the dates.
    * @param force if it should force it to set dates
    */
   public void setDates(String[] dates, boolean force) {
      if(!force) {
         // @by stephenwebster, For Bug #1939 must be consistent on validating the
         // dates as it is possible that a setDate can come after a setRange
         // operation in the context when shared filters are applied.
         dates = checkDates(dates);
      }

      this.dates = dates == null ? new String[0] : dates;
      scriptValue = false;
   }

   /**
    * Get the dates.
    * @return the dates.
    */
   public String[] getRange() {
      String[] originalRange = getOriginalRange();
      String userDefinedMin = formatDefinedRangeDate(getMin());
      String userDefinedMax = formatDefinedRangeDate(getMax());
      Date minDate = CalendarUtil.parseStringToDate(userDefinedMin);
      Date maxDate = CalendarUtil.parseStringToDate(userDefinedMax);

      if(minDate != null && maxDate != null && minDate.getTime() > maxDate.getTime()) {
         String temp = userDefinedMin;
         userDefinedMin = userDefinedMax;
         userDefinedMax = temp;
      }

      if(originalRange == null || originalRange.length != 2) {
         return new String[] {userDefinedMin, userDefinedMax};
      }

      String omin = originalRange[0];
      String omax = originalRange[1];
      String min;
      String max;

      if(userDefinedMin == null) {
         min = omin;
      }
      else if(omin == null) {
         min = userDefinedMin;
      }
      else {
         min = compareDate(omin, userDefinedMin) > 0 ? omin : userDefinedMin;
      }

      if(userDefinedMax == null) {
         max = omax;
      }
      else if(omax == null) {
         max = userDefinedMax;
      }
      else {
         max = compareDate(omax, userDefinedMax) < 0 ? omax : userDefinedMax;
      }

      if(min == null && max == null) {
         return null;
      }

      return new String[] {min, max};
   }

   /**
    * Get the date range that do not apply the user defined min and max.
    *
    * @return the dates.
    */
   public String[] getOriginalRange() {
      return range;
   }

   /**
    * Set the range.
    * @param range the date ranges.
    */
   public void setRange(String[] range) {
      this.range = range == null ? new String[0] : range;
      // @by stephenwebster, For Bug #1939 must be consistent on validating the
      // dates as it is possible that a setRange can come after a setDate
      // operation in the context when shared filters are applied.
      this.dates = checkDates(this.dates);
      scriptValue = false;
   }

   /**
    * Verifies that the selected dates are in the current data range
    * @param dates the dates to validate
    * @return A modified list of dates that fall within the data range.
    */
   public String[] checkDates(String[] dates) {
      String[] range = getRange();

      if(dates != null && range != null && range.length == 2) {
         String min = range[0];
         String max = range[1];

         // @by stephenwebster, For Bug #1939, it is possible that dates
         // applied through shared filters are not in the range of the data
         // on the calendar.  Instead of throwing out the entire selection, use
         // whatever dates are in the range. The reasoning is that it would not
         // be possible to make selections from a user perspective, so in that
         // regard the behavior is correct to remove the values that are not in
         // the range, but we should keep the intersection of the shared filters.
         // @TODO find a way to notify the user that shared filters are partially
         // applied.
         ArrayList<String> newDates = new ArrayList<>();

         for(int i = 0; i < dates.length; i++) {
            newDates.add(forceInRange(dates[i], min, max));
         }

         dates = newDates.toArray(new String[newDates.size()]);
      }

      return dates;
   }

   /**
    * Get the date format for the selected date string.
    */
   public String getWeekFormat() {
      Object val = weekFormatValue.getRuntimeValue(true);
      return val == null ? null : val + "";
   }

   /**
    * Set the date format for the selected date string.
    */
   public void setWeekFormat(String format) {
      if(StringUtils.isEmpty(format)) {
         format = null;
      }

      weekFormatValue.setRValue(format);
   }

   /**
    * Set the week format of the calendar.
    */
   public void setWeekFormatValue(String format) {
      if(StringUtils.isEmpty(format)) {
         format = null;
      }

      weekFormatValue.setDValue(format);
   }

   /**
    * Get the date format for the selected date string.
    */
   public String getWeekFormatValue() {
      return weekFormatValue.getDValue();
   }

   /**
    * Check if is in range.
    */
   private String forceInRange(String date, String min, String max) {
      if(min != null && compareDate(date, min) < 0) {
         return changeRangeToSelectDate(date, min);
      }
      else if(max != null && compareDate(date, max) > 0) {
         return changeRangeToSelectDate(date, max);
      }

      return date;
   }

   private String changeRangeToSelectDate(String oldDate, String rangeDate) {
      if(oldDate != null && rangeDate != null) {
         Date minDate = CalendarUtil.parseStringToDate(rangeDate);

         if(minDate != null) {
            Calendar cal = new GregorianCalendar();
            cal.setTime(minDate);

            if(oldDate.startsWith("y")) {
               return "y" + cal.get(Calendar.YEAR);
            }
            else if(oldDate.startsWith("m")) {
               return "m" + cal.get(Calendar.YEAR) + "-" + cal.get(Calendar.MONTH);
            }
            else if(oldDate.startsWith("w")) {
               return "w" + cal.get(Calendar.YEAR) + "-" + cal.get(Calendar.MONTH) + "-" +
                  cal.get(Calendar.WEEK_OF_MONTH);
            }
         }
      }

      return "d" + rangeDate;
   }

   /**
    * Compare two dates.
    */
   private int compareDate(String date1, String date2) {
      Date date1Value = CalendarUtil.parseStringToDate(date1);
      Date date2Value = CalendarUtil.parseStringToDate(date2);

      if(date1Value == null && date2Value == null) {
         return 0;
      }
      else if(date1Value == null && date2Value != null) {
         return -1;
      }
      else if(date2Value == null && date1Value != null) {
         return 1;
      }

      long result = date1Value.getTime() - date2Value.getTime();

      if(result == 0) {
         return 0;
      }

      return result > 0 ? 1 : -1;
   }

   /**
    * Get the year and month parts.
    */
   private int[] getYM(String date) {
      int idx = date.indexOf("-");
      int idx2 = date.lastIndexOf("-");
      int delta = date.charAt(0) == 'w' || date.charAt(0) == 'm' ||
         date.charAt(0) == 'd' || date.charAt(0) == 'y' ? 1 : 0;
      int year = Integer.parseInt(
         idx == -1 ? date.substring(delta) : date.substring(delta, idx));
      int month = idx == -1 ? -1 : idx2 == idx ?
         Integer.parseInt(date.substring(idx + 1)) :
         Integer.parseInt(date.substring(idx + 1, idx2));
      return new int[] {year, month};
   }

   /**
    * Get the current calendar date.
    */
   public String getCurrentDate1() {
      return currdate1;
   }

   /**
    * Set the current calendar date.
    */
   public void setCurrentDate1(String date) {
      this.currdate1 = date;
   }

   /**
    * Get the current calendar date of the second calendar.
    */
   public String getCurrentDate2() {
      return currdate2;
   }

   /**
    * Set the current calendar date of the second calendar.
    */
   public void setCurrentDate2(String date) {
      this.currdate2 = date;
   }

   /**
    * Get the runtime show type.
    * @return the show type.
    */
   public int getShowType() {
      return typeValue.getIntValue(false, CALENDAR_SHOW_TYPE);
   }

   /**
    * Get the design time show type.
    * @return the show type.
    */
   public int getShowTypeValue() {
      return typeValue.getIntValue(true, CALENDAR_SHOW_TYPE);
   }

   /**
    * Set the runtime show type.
    * @param type the show type.
    */
   public void setShowType(int type) {
      typeValue.setRValue(type);
      typeSValid = true;
   }

   /**
    * Set the design time show type value.
    * @param type the show type value.
    */
   public void setShowTypeValue(int type) {
     this.typeValue.setDValue(type + "");
   }

   /**
    * Get the runtime view mode.
    * @return the view mode.
    */
   public int getViewMode() {
      return modeValue.getIntValue(false, SINGLE_CALENDAR_MODE);
   }

   /**
    * Get the design time view mode.
    * @return the view mode.
    */
   public int getViewModeValue() {
      return modeValue.getIntValue(true, SINGLE_CALENDAR_MODE);
   }

   /**
    * Set the runtime view mode.
    * @param mode the view mode.
    */
   public void setViewMode(int mode) {
      modeValue.setRValue(mode);
      modeSValid = true;
   }

   /**
    * Set the design time view mode value.
    * @param mode the view mode value.
    */
   public void setViewModeValue(int mode) {
      modeValue.setDValue(mode + "");
   }

   /**
    * Check if this runtime calendar is in year view (or monthly view).
    */
   public boolean isYearView() {
      return Boolean.valueOf(yearViewValue.getRuntimeValue(true) + "");
   }

   /**
    * Check if this design time calendar is in year view (or monthly view).
    */
   public boolean getYearViewValue() {
      return Boolean.valueOf(yearViewValue.getDValue());
   }

   /**
    * Set whether to show months (year view) or weeks.
    */
   public void setYearView(boolean year) {
      yearViewValue.setRValue(year);
   }

   /**
    * Set whether to show months (year view) or weeks.
    */
   public void setYearViewValue(boolean year) {
      yearViewValue.setDValue(year + "");
   }

   /**
    * Check if runtime day selection is allowed.
    */
   public boolean isDaySelection() {
      return Boolean.valueOf(daySelectionValue.getRuntimeValue(true) + "");
   }

   /**
    * Check if design time day selection is allowed.
    */
   public boolean getDaySelectionValue() {
      return Boolean.valueOf(daySelectionValue.getDValue());
   }

   /**
    * Set whether runtime day selection is allowed. This is only meaningful in
    * monthly view.
    */
   public void setDaySelection(boolean day) {
      daySelectionValue.setRValue(day);
   }

   /**
    * Set whether design time day selection is allowed. This is only meaningful
    * in monthly view.
    */
   public void setDaySelectionValue(boolean day) {
       daySelectionValue.setDValue(day + "");
   }

   /**
    * If it is period.
    * @return it is period.
    */
   public boolean isPeriod() {
      return period;
   }

   /**
    * Set if it is period.
    * @param period it is period.
    */
   public void setPeriod(boolean period) {
      this.period = period;
   }

   /**
    * Get the group title.
    * @return the title of the checkbox assembly.
    */
   @Override
   public String getTitle() {
      return titleInfo.getTitle(getFormatInfo().getFormat(TITLEPATH), getViewsheet(), getName());
   }

   /**
    * Get the group title value.
    * @return the title value of the calendar assembly.
    */
   @Override
   public String getTitleValue() {
      return titleInfo.getTitleValue();
   }

   /**
    * Set the group title value.
    * @param value the specified group title.
    */
   @Override
   public void setTitleValue(String value) {
      titleInfo.setTitleValue(value);
   }

   /**
    * Set the group title value.
    * @param value the specified group title.
    */
   @Override
   public void setTitle(String value) {
      titleInfo.setTitle(value);
   }

   /**
    * Check whether the calendar title is visible.
    * @return the title visible of the calendar assembly.
    */
   @Override
   public boolean isTitleVisible() {
      return titleInfo.isTitleVisible();
   }

   /**
    * Get the calendar title visible value.
    * @return the title visible value of the calendar assembly.
    */
   @Override
   public boolean getTitleVisibleValue() {
       return titleInfo.getTitleVisibleValue();
   }

   /**
    * Set the calendar title visible value.
    * @param visible the specified calendar title visible.
    */
   @Override
   public void setTitleVisible(boolean visible) {
      titleInfo.setTitleVisible(visible);
   }

   /**
    * Set the group titleVisible value.
    * @param visible the specified visibility.
    */
   @Override
   public void setTitleVisibleValue(boolean visible) {
      titleInfo.setTitleVisibleValue(visible + "");
   }

   /**
    * Get the calendar title height.
    * @return the title height of the calendar assembly.
    */
   @Override
   public int getTitleHeight() {
      return titleInfo.getTitleHeight();
   }

   /**
    * Get the calendar title height value.
    * @return the title height value of the calendar assembly.
    */
   @Override
   public int getTitleHeightValue() {
      return titleInfo.getTitleHeightValue();
   }

   /**
    * Set the calendar title height value.
    * @param value the specified calendar title height.
    */
   @Override
   public void setTitleHeightValue(int value) {
      titleInfo.setTitleHeightValue(value);
   }

   /**
    * Set the calendar title height.
    * @param value the specified calendar title height.
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
    * Check whether the mode script is valid
    * @return true if valid, otherwise false
    */
   public boolean isModeSValid() {
      return modeSValid;
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
   }

   /**
    * Get size scale ratio of this assembly.
    */
   @Override
   public Point2D.Double getSizeScale(Point2D.Double scaleRatio) {
      if(getShowType() == DROPDOWN_SHOW_TYPE) {
         return new Point2D.Double(scaleRatio.x, 1);
      }

      return scaleRatio;
   }

   /**
    * Get the date format for the selected date string.
    */
   public String getSelectedDateFormat() {
      Object val = selectedDateFormatValue.getRuntimeValue(true);
      return val == null || !CalendarUtil.isSepecificalFormats(val + "") &&
              CalendarUtil.invalidCalendarFormat(val + "") ? null : val + "";
   }

   /**
    * Set the date format for the selected date string.
    */
   public void setSelectedDateFormat(String format) {
      if(!CalendarUtil.isSepecificalFormats(format) &&  CalendarUtil.invalidCalendarFormat(format)) {
         format = null;
      }

      selectedDateFormatValue.setRValue(format);
   }

   /**
    * Set the date format for the selected date string.
    */
   public void setSelectedDateFormatValue(String format) {
      if(!CalendarUtil.isSepecificalFormats(format) &&  CalendarUtil.invalidCalendarFormat(format)) {
         format = null;
      }

      selectedDateFormatValue.setDValue(format);
   }

   /**
    * Get the date format for the selected date string.
    */
   public String getSelectedDateFormatValue() {
      Object val = selectedDateFormatValue.getDValue();
      return val == null || !CalendarUtil.isSepecificalFormats(val + "") &&
              CalendarUtil.invalidCalendarFormat(val + "") ? null : val + "";
   }

   /**
    * Write attributes.
    * @param writer the specified writer.
    */
   @Override
   protected void writeAttributes(PrintWriter writer) {
      super.writeAttributes(writer);

      writer.print(" type=\"" + getShowType() + "\"");
      writer.print(" typeValue=\"" + typeValue.getDValue() + "\"");
      writer.print(" mode=\"" + getViewMode() + "\"");
      writer.print(" modeValue=\"" + modeValue.getDValue() + "\"");
      writer.print(" modeSValid=\"" + modeSValid + "\"");
      writer.print(" month=\"" + isYearView() + "\"");
      writer.print(" monthValue=\"" + yearViewValue.getDValue() + "\"");
      writer.print(" daySelection=\"" + isDaySelection() + "\"");
      writer.print(" daySelectionValue=\"" +
         daySelectionValue.getDValue() + "\"");
      writer.print(" period=\"" + period + "\"");
      writer.print(" weekFormat=\"" + getWeekFormat() + "\"");
      writer.print(" weekFormatValue=\"" + weekFormatValue.getDValue() + "\"");

      if(getSelectedDateFormat() != null) {
         writer.print(" selectedDateFormat=\"" + getSelectedDateFormat() + "\"");
      }

      if(selectedDateFormatValue.getDValue() != null) {
         writer.print(" selectedDateFormatValue=\"" + selectedDateFormatValue.getDValue() + "\"");
      }

      if(currdate1 != null) {
         writer.print(" currdate1=\"" + currdate1 + "\"");
      }

      if(currdate2 != null) {
         writer.print(" currdate2=\"" + currdate2 + "\"");
      }

      fixCalendarSize();

      if(pixelsize != null) {
         writer.print(" rtpixelWidth=\"" + pixelsize.width + "\"");
         writer.print(" rtpixelHeight=\"" + pixelsize.height + "\"");
      }
   }

   /**
    * Parse attributes.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseAttributes(Element elem) {
      super.parseAttributes(elem);

      typeValue.setDValue(
         getAttributeStr(elem, "type", CALENDAR_SHOW_TYPE + ""));
      modeValue.setDValue(
         getAttributeStr(elem, "mode", SINGLE_CALENDAR_MODE + ""));
      yearViewValue.setDValue(getAttributeStr(elem, "month", "false"));
      daySelectionValue.setDValue(
         getAttributeStr(elem, "daySelection", "false"));
      period = "true".equals(Tool.getAttribute(elem, "period"));
      currdate1 = Tool.getAttribute(elem, "currdate1");
      currdate2 = Tool.getAttribute(elem, "currdate2");
      weekFormatValue.setDValue(getAttributeStr(elem, "weekFormat", null));
      selectedDateFormatValue.setDValue(getAttributeStr(elem, "selectedDateFormat", null));
   }

   /**
    * Write contents.
    * @param writer the specified writer.
    */
   @Override
   protected void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      if(column != null) {
         column.writeXML(writer);
      }

      if(dates != null && dates.length > 0) {
         writer.print("<dates>");

         for(int i = 0; i < dates.length; i++) {
            writer.print("<date>");
            writer.print("<![CDATA[" + dates[i] + "]]>");
            writer.print("</date>");
         }

         writer.println("</dates>");
      }

      // Bug #56350, don't save the range unless using period. if not period, the range needs to
      // come from the data, otherwise the calendar needs to be cleared everytime to get the
      // correct range. basically, don't save the range unless it comes from the user.
      if(period && range != null && range.length == 2) {
         writer.print("<range>");
         writer.print("<min>");
         writer.print("<![CDATA[" + range[0] + "]]>");
         writer.print("</min>");
         writer.print("<max>");
         writer.print("<![CDATA[" + range[1] + "]]>");
         writer.print("</max>");
         writer.println("</range>");
      }

      writer.print("<userDefinedRange>");

      if(userDefinedMin.getDValue() != null) {
         writer.print("<min>");
         writer.print("<![CDATA[" + userDefinedMin.getDValue() + "]]>");
         writer.print("</min>");
      }


      if(userDefinedMax.getDValue() != null) {
         writer.print("<max>");
         writer.print("<![CDATA[" + userDefinedMax.getDValue() + "]]>");
         writer.print("</max>");
      }

      writer.println("</userDefinedRange>");

      titleInfo.writeXML(writer, getFormatInfo().getFormat(TITLEPATH),
         getViewsheet(), getName());
   }

   /**
    * Parse contents.
    * @param elem the specified xml element.
    */
   @Override
   protected void parseContents(Element elem) throws Exception {
      super.parseContents(elem);

      Element cnode = Tool.getChildNodeByTagName(elem, "dataRef");

      if(cnode != null) {
         column = AbstractDataRef.createDataRef(cnode);
      }

      Element dnode = Tool.getChildNodeByTagName(elem, "dates");

      if(dnode != null) {
         NodeList dlist = Tool.getChildNodesByTagName(dnode, "date");

         if(dlist != null && dlist.getLength() > 0) {
            dates = new String[dlist.getLength()];

            for(int i = 0; i < dlist.getLength(); i++) {
               dates[i] = Tool.getValue(dlist.item(i));
            }
         }
      }

      Element rnode = Tool.getChildNodeByTagName(elem, "range");

      if(rnode != null) {
         range = new String[2];
         Element mnode = Tool.getChildNodeByTagName(rnode, "min");
         range[0] = Tool.getValue(mnode);
         mnode = Tool.getChildNodeByTagName(rnode, "max");
         range[1] = Tool.getValue(mnode);
      }

      Element userDefinedRangeNode = Tool.getChildNodeByTagName(elem, "userDefinedRange");

      if(userDefinedRangeNode != null) {
         userDefinedMin.setDValue(Tool.getChildValueByTagName(userDefinedRangeNode, "min"));
         userDefinedMax.setDValue(Tool.getChildValueByTagName(userDefinedRangeNode, "max"));
      }

      titleInfo = new TitleInfo();
      titleInfo.parseXML(elem);
   }

   /**
    * Clone this object.
    * @param shallow <tt>true</tt> to perform shallow clone,
    * <tt>false</tt> to perform deep clone.
    * @return the cloned object.
    */
   @Override
   public CalendarVSAssemblyInfo clone(boolean shallow) {
      try {
         CalendarVSAssemblyInfo info = (CalendarVSAssemblyInfo) super.clone(shallow);

         if(!shallow) {
            if(column != null) {
               info.column = (DataRef) column.clone();
            }

            if(titleInfo != null) {
               info.titleInfo = (TitleInfo) titleInfo.clone();
            }

            if(yearViewValue != null) {
               info.yearViewValue = (DynamicValue) yearViewValue.clone();
            }

            if(modeValue != null) {
               info.modeValue = (DynamicValue2) modeValue.clone();
            }

            if(typeValue != null) {
               info.typeValue = (DynamicValue2) typeValue.clone();
            }

            if(daySelectionValue != null) {
               info.daySelectionValue = (DynamicValue) daySelectionValue.clone();
            }

            if(weekFormatValue != null) {
               info.weekFormatValue = (DynamicValue) weekFormatValue.clone();
            }

            if(selectedDateFormatValue != null) {
               info.selectedDateFormatValue = (DynamicValue) selectedDateFormatValue.clone();
            }

            if(userDefinedMin != null) {
               info.userDefinedMin = (DynamicValue) userDefinedMin.clone();
            }

            if(userDefinedMax != null) {
               info.userDefinedMax = (DynamicValue) userDefinedMax.clone();
            }

            info.dates = (String[]) dates.clone();

            if(range != null) {
               info.range = (String[]) range.clone();
            }
         }

         return info;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone CalendarVSAssemblyInfo", ex);
      }

      return null;
   }

   /**
    * Copy the assembly info.
    * @param info the specified viewsheet assembly info.
    * @return the hint to reset view, data or worksheet data.
    */
   @Override
   public int copyInfo(VSAssemblyInfo info) {
      CalendarVSAssemblyInfo sinfo = (CalendarVSAssemblyInfo) info;
      boolean schanged = !Tool.equals(column, sinfo.column) ||
         !Tool.equals(getTableName(), sinfo.getTableName());
      boolean ychanged = !Tool.equals(getYearViewValue(), sinfo.getYearViewValue());
      int hint = super.copyInfo(info);

      // if source changed, let's discard selected date
      if(schanged) {
         setDates(null);
         setPeriod(false);
      }
      else if(ychanged) {
         setDates(null);
      }

      return hint;
   }

   /**
    * Copy the input data part assembly info.
    * @param info the specified viewsheet assembly info.
    * @return new hint.
    */
   @Override
   protected int copyInputDataInfo(VSAssemblyInfo info, int hint) {
      hint = super.copyInputDataInfo(info, hint);
      CalendarVSAssemblyInfo sinfo = (CalendarVSAssemblyInfo) info;

      if(!Tool.equals(column, sinfo.column)) {
         column = sinfo.column;
         hint |= VSAssembly.INPUT_DATA_CHANGED;
         hint |= VSAssembly.BINDING_CHANGED;
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
      CalendarVSAssemblyInfo sinfo = (CalendarVSAssemblyInfo) info;

      if(!typeValue.equals(sinfo.typeValue) ||
         getShowType() != sinfo.getShowType()) {
         // set the default formatting based on type
         if(getShowType() == CALENDAR_SHOW_TYPE) {
            if(getFormatInfo().equals(normalDefault)) {
               setFormatInfo((FormatInfo) dropdownDefault);
            }
         }
         else if(getFormatInfo().equals(dropdownDefault)) {
            setFormatInfo((FormatInfo) normalDefault);
         }

         typeSValid = sinfo.typeSValid;
         typeValue = (DynamicValue2)sinfo.typeValue.clone();
         result = true;
      }

      if(!modeValue.equals(sinfo.modeValue) ||
         getViewMode() != sinfo.getViewMode())
      {
         modeValue = (DynamicValue2)sinfo.modeValue.clone();
         modeSValid = sinfo.modeSValid;
         result = true;
      }

      if(!yearViewValue.equals(sinfo.yearViewValue) ||
         isYearView() != sinfo.isYearView())
      {
         yearViewValue = (DynamicValue) sinfo.yearViewValue.clone();
         result = true;
      }

      if(!Tool.equals(titleInfo, sinfo.titleInfo)) {
         titleInfo = (TitleInfo) sinfo.titleInfo.clone();
         result = true;
      }

      if(!Tool.equals(currdate1, sinfo.currdate1)) {
         currdate1 = sinfo.currdate1;
         result = true;
      }

      if(!Tool.equals(currdate2, sinfo.currdate2)) {
         currdate2 = sinfo.currdate2;
         result = true;
      }

      // dates changes should both view and output changes
      if(!Tool.equals(dates, sinfo.dates)) {
         result = true;
      }

      if(!Tool.equals(pixelsize, sinfo.pixelsize)) {
         pixelsize = sinfo.pixelsize;
         result = true;
      }

      if(!Tool.equals(weekFormatValue, sinfo.weekFormatValue) ||
         !Tool.equals(getWeekFormat(), sinfo.getWeekFormat()))
      {
         weekFormatValue = sinfo.weekFormatValue;
         result = true;
      }

      if(!Tool.equals(selectedDateFormatValue, sinfo.selectedDateFormatValue) ||
         !Tool.equals(getSelectedDateFormat(), sinfo.getSelectedDateFormat()))
      {
         selectedDateFormatValue = sinfo.selectedDateFormatValue;
         result = true;
      }

      if(!Tool.equals(userDefinedMin, sinfo.userDefinedMin) ) {
         userDefinedMin = sinfo.userDefinedMin;
         result = true;
      }

      if(!Tool.equals(userDefinedMax, sinfo.userDefinedMax) ) {
         userDefinedMax = sinfo.userDefinedMax;
         result = true;
      }

      return result;
   }

   /**
    * Copy the output data part assembly info.
    * @param info the specified viewsheet assembly info.
    * @return <tt>true</tt> if changed, <tt>false</tt> otherwise.
    */
   @Override
   protected boolean copyOutputDataInfo(VSAssemblyInfo info) {
      boolean result = super.copyOutputDataInfo(info);
      CalendarVSAssemblyInfo sinfo = (CalendarVSAssemblyInfo) info;

      if(!Tool.equals(dates, sinfo.dates)) {
         dates = sinfo.dates;
         result = true;
      }

      if(period != sinfo.period) {
         period = sinfo.period;
         result = true;
      }

      // this changes condition
      if(!daySelectionValue.equals(sinfo.daySelectionValue))
      {
         daySelectionValue = (DynamicValue) sinfo.daySelectionValue.clone();
         dates = new String[0]; // clear when selection mode is changed
         result = true;
      }
      else if(isDaySelection() != sinfo.isDaySelection()) {
         setDaySelectionValue(sinfo.isDaySelection());
         result = true;
      }

      return result;
   }

   @Override
   public List<DynamicValue> getDynamicValues() {
      List<DynamicValue> list = super.getDynamicValues();
      list.add(userDefinedMin);
      list.add(userDefinedMax);

      return list;
   }

   /**
    * Get the view dynamic values.
    * @return the view dynamic values.
    */
   @Override
   public List<DynamicValue> getViewDynamicValues(boolean all) {
      List<DynamicValue> list = super.getViewDynamicValues(all);
      list.addAll(titleInfo.getViewDynamicValues());

      return list;
   }

   /**
    * Get display value.
    */
   public String getDisplayValue() {
      return getDisplayValue(false);
   }

   /**
    * Get display value.
    * @param onlyList only get the selected values, not include title,
    * and not restrict by visible properties.
    * @return the string to represent the selected value.
    */
   public String getDisplayValue(boolean onlyList) {
      if(!isEnabled() && onlyList) {
         return null;
      }

      StringBuilder sb = new StringBuilder();
      String[] dates = getDates();
      boolean dual =
         Integer.parseInt(modeValue.getRValue() + "") == DOUBLE_CALENDAR_MODE;
      boolean period = isPeriod() && dual;
      boolean range = dual && !period;

      if(dates.length == 0) {
         return !onlyList ? getTitle() : null;
      }

      String[] limitRange = getRange();
      Date min = null;
      Date max = null;

      if(limitRange != null && limitRange.length > 0 && limitRange[0] != null) {
         try {
            min = CalendarUtil.parseStringToDate(limitRange[0]);
         }
         catch(Exception ignore){
         }
      }

      if(limitRange != null && limitRange.length > 1 && limitRange[1] != null) {
         try {
            max = CalendarUtil.parseStringToDate(limitRange[1]);
         }
         catch(Exception ignore){
         }
      }

      for(int i = 0; i < dates.length; i++) {
         Date start = CalendarVSAssembly.parseDate(dates[i], true);
         Calendar cal1 = CoreTool.calendar.get();
         cal1.setTime(start);
         int month1 = cal1.get(Calendar.MONTH) + 1;

         if(i > 0) {
            sb.append(period ? " & " : (range ? CalendarUtil.rangeSpliter : ","));
         }

         if(dates[i].startsWith("y")) {
            sb.append(cal1.get(Calendar.YEAR));
         }
         else if(dates[i].startsWith("m")) {
            sb.append(cal1.get(Calendar.YEAR) + "-" + month1);
         }
         else if(dates[i].startsWith("w")) {
            Date end = CalendarVSAssembly.parseDate(dates[i], false);
            Calendar cal2 = new GregorianCalendar();

            cal2.setTime(new Date(end.getTime() - 24 * 60 * 60 * 1000));

            // use the end of the week for the range
            if(range && i > 0) {
               cal1 = cal2;
            }

            Date currentDate = cal1.getTime();
            Date endDate = cal2.getTime();

            if(i == 0 && min != null && currentDate.getTime() < min.getTime() &&
               endDate.getTime() > min.getTime())
            {
               cal1.setTime(min);
            }
            else if(i > 0 && max != null && currentDate.getTime() > max.getTime() &&
               max.getTime() > start.getTime())
            {
               cal1.setTime(max);
            }

            sb.append(cal1.get(Calendar.YEAR) + "-");

            int month2 = cal2.get(Calendar.MONTH) + 1;
            if(cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR)) {
               sb.append((cal1.get(Calendar.MONTH) + 1) + "-");

               if(cal1.get(Calendar.MONTH) == cal2.get(Calendar.MONTH)) {
                  sb.append(cal1.get(Calendar.DAY_OF_MONTH));

                  if(!range) {
                     sb.append("." + cal2.get(Calendar.DAY_OF_MONTH));
                  }
                  else if(dates.length == 1){
                     sb.append(CalendarUtil.rangeSpliter + cal1.get(Calendar.YEAR) + "-" +
                     (cal1.get(Calendar.MONTH) + 1) + "-" +
                      cal2.get(Calendar.DAY_OF_MONTH)
                     );
                  }
               }
               else {
                  sb.append(cal1.get(Calendar.DAY_OF_MONTH));

                  if(!range) {
                     sb.append("." + month2 + "-" +
                               cal2.get(Calendar.DAY_OF_MONTH));
                  }
                  else if(dates.length == 1){
                     sb.append(CalendarUtil.rangeSpliter + cal1.get(Calendar.YEAR) + "-" +
                     (cal1.get(Calendar.MONTH) + 2) + "-" +
                      cal2.get(Calendar.DAY_OF_MONTH)
                     );
                  }
               }
            }
            else if(cal1.get(Calendar.MONTH) == cal2.get(Calendar.MONTH)) {
               sb.append(month1 + "-");
               sb.append(cal1.get(Calendar.DAY_OF_MONTH));

               if(!range) {
                  sb.append("." + month2 + "-" +
                            cal2.get(Calendar.DAY_OF_MONTH));
               }
            }
            else {
               sb.append(month1 + "-");
               sb.append(cal1.get(Calendar.DAY_OF_MONTH));

               if(!range) {
                  sb.append("." + cal2.get(Calendar.YEAR) + "-" +
                            month2 + "-" + cal2.get(Calendar.DAY_OF_MONTH));
               }
               else if(dates.length == 1){
                     sb.append(CalendarUtil.rangeSpliter + (cal1.get(Calendar.YEAR) + 1 ) + "-" +
                     (cal1.get(Calendar.MONTH) + 1) + "-" +
                      cal2.get(Calendar.DAY_OF_MONTH)
                     );
               }
            }
         }
         else if(dates[i].startsWith("d")) {
            sb.append(cal1.get(Calendar.YEAR) + "-" + month1 + "-" +
            cal1.get(Calendar.DAY_OF_MONTH));
         }
      }

      String dateFormat = CalendarUtil.getCalendarSelectedDateFormat(this);
      String dateString = CalendarUtil.formatSelectedDates(sb.toString(), dateFormat,
         getViewMode() == CalendarVSAssemblyInfo.DOUBLE_CALENDAR_MODE,
         isPeriod(), !isYearView());

      if(!onlyList && getTitle() != null) {
         return getTitle() + ": " + dateString;
      }

      return dateString;
   }

   /**
    * Fix calendar display size.
    */
   public void fixCalendarSize() {
      if(!typeSValid && !modeSValid) {
         return;
      }

      Dimension pixel = pixelsize == null ? getPixelSize() : pixelsize;
      pixelsize = pixel = pixel == null ? new Dimension(3 * 70, 9 * 18) : pixel;

      if(modeSValid && !(getViewMode() + "").equals(modeValue.getDValue())) {
         if(getViewMode() == DOUBLE_CALENDAR_MODE  &&
            getShowType() == DROPDOWN_SHOW_TYPE)
         {
            pixelsize = new Dimension(pixel.width, 18);
         }
         else if(getViewMode() == DOUBLE_CALENDAR_MODE  &&
            getShowType() == CALENDAR_SHOW_TYPE)
         {
            pixelsize = new Dimension(pixel.width * 2, pixel.height);
         }
         else if(getViewMode() == SINGLE_CALENDAR_MODE &&
            getShowType() == DROPDOWN_SHOW_TYPE)
         {
            pixelsize = new Dimension(pixel.width / 2, 18);
         }
         else if(getViewMode() == SINGLE_CALENDAR_MODE &&
            getShowType() == CALENDAR_SHOW_TYPE)
         {
            pixelsize = new Dimension(pixel.width / 2, pixel.height);
         }
      }

      if(getShowType() == DROPDOWN_SHOW_TYPE) {
         pixelsize = new Dimension(pixelsize.width, 18);
      }
      else if(getShowType() == CALENDAR_SHOW_TYPE) {
         pixelsize = new Dimension(pixelsize.width, 9 * 18);
      }

      setPixelSize(pixelsize);
   }

   private static FormatInfo normalDefault = new FormatInfo();
   private static FormatInfo dropdownDefault = new FormatInfo();
   public static final TableDataPath CALENDAR_TITLE_PATH =
      new TableDataPath(-1, TableDataPath.CALENDAR_TITLE);
   public static final TableDataPath CALENDAR_YEAR_PATH =
      new TableDataPath(-1, TableDataPath.YEAR_CALENDAR);
   public static final TableDataPath CALENDAR_MONTH_PATH =
      new TableDataPath(-1, TableDataPath.MONTH_CALENDAR);

   // setup the default formats
   static {
      VSCompositeFormat format = new VSCompositeFormat();
      VSCompositeFormat tformat = new VSCompositeFormat();
      Insets borders = new Insets(StyleConstants.THIN_LINE,
                                  StyleConstants.THIN_LINE,
                                  StyleConstants.THIN_LINE,
                                  StyleConstants.THIN_LINE);
      BorderColors bcolors = new BorderColors(DEFAULT_BORDER_COLOR,
                                              DEFAULT_BORDER_COLOR,
                                              DEFAULT_BORDER_COLOR,
                                              DEFAULT_BORDER_COLOR);

      format.getDefaultFormat().setBordersValue(borders);
      format.getDefaultFormat().setBorderColorsValue(bcolors);
      format.getDefaultFormat().setFontValue(getDefaultFont(Font.BOLD, 10));
      format.getCSSFormat().setCSSType(CSSConstants.CALENDAR);
      tformat.getDefaultFormat().setFontValue(getDefaultFont(Font.BOLD, 11));
      tformat.getDefaultFormat().setBordersValue(new Insets(0, 0, StyleConstants.THIN_LINE, 0));
      tformat.getDefaultFormat().setBorderColorsValue(
         new BorderColors(DEFAULT_BORDER_COLOR, new Color(0xC0C0C0),
                          DEFAULT_BORDER_COLOR, DEFAULT_BORDER_COLOR));
      tformat.getDefaultFormat().setBackgroundValue(DEFAULT_TITLE_BG);
      tformat.getDefaultFormat().setAlignmentValue(StyleConstants.H_LEFT | StyleConstants.V_CENTER);
      tformat.getCSSFormat().setCSSType(CSSConstants.CALENDAR + CSSConstants.TITLE);
      normalDefault.setFormat(OBJECTPATH, format);
      normalDefault.setFormat(TITLEPATH, tformat);

      VSCompositeFormat titleFormat = new VSCompositeFormat();
      titleFormat.getCSSFormat().setCSSType(CSSConstants.CALENDAR_HEADER);
      titleFormat.getDefaultFormat().setAlignmentValue((StyleConstants.V_CENTER |
                                                        StyleConstants.CENTER));
      titleFormat.getDefaultFormat().setBordersValue(new Insets(0, 0,
                                                                StyleConstants.THIN_LINE, 0));
      titleFormat.getDefaultFormat().setBorderColorsValue(bcolors);
      normalDefault.setFormat(CALENDAR_TITLE_PATH, titleFormat);

      VSCompositeFormat yearFormat = new VSCompositeFormat();
      yearFormat.getDefaultFormat().setFontValue(getDefaultFont(Font.BOLD, 18));
      yearFormat.getCSSFormat().setCSSType(CSSConstants.CALENDAR_MONTHS);
      yearFormat.getDefaultFormat().setAlignmentValue(StyleConstants.CENTER |
                                                      StyleConstants.V_CENTER);
      normalDefault.setFormat(CALENDAR_YEAR_PATH, yearFormat);

      VSCompositeFormat monthFormat = new VSCompositeFormat();
      monthFormat.getCSSFormat().setCSSType(CSSConstants.CALENDAR_DAYS);
      normalDefault.setFormat(CALENDAR_MONTH_PATH, monthFormat);

      format = new VSCompositeFormat();
      tformat = new VSCompositeFormat();

      format.getDefaultFormat().setFontValue(getDefaultFont(Font.BOLD, 10));
      format.getDefaultFormat().setBordersValue(borders);
      format.getDefaultFormat().setBorderColorsValue(bcolors);
      format.getDefaultFormat().setBackgroundValue("0xffffff");
      format.getCSSFormat().setCSSType(CSSConstants.CALENDAR);

      tformat.getDefaultFormat().setFontValue(getDefaultFont(Font.BOLD, 11));
      tformat.getDefaultFormat().setBordersValue(borders);
      tformat.getDefaultFormat().setBorderColorsValue(bcolors);
      tformat.getDefaultFormat().setBackgroundValue("0xffffff");
      tformat.getCSSFormat().setCSSType(CSSConstants.CALENDAR +
         CSSConstants.TITLE);
      dropdownDefault.setFormat(OBJECTPATH, format);
      dropdownDefault.setFormat(TITLEPATH, tformat);
   }

   /**
    * Get the object css default type.
    */
   @Override
   public String getObjCSSType() {
      return CSSConstants.CALENDAR;
   }

   /**
    * Reset runtime values.
    */
   @Override
   public void resetRuntimeValues() {
      super.resetRuntimeValues();

      typeValue.setRValue(null);
      modeValue.setRValue(null);
      yearViewValue.setRValue(null);
      daySelectionValue.setRValue(null);
      titleInfo.resetRuntimeValues();
      weekFormatValue.setRValue(null);
      selectedDateFormatValue.setRValue(null);
      userDefinedMin.setRValue(null);
      userDefinedMax.setRValue(null);
   }

   public boolean isScriptValue() {
      return scriptValue;
   }

   public void setScriptValue(boolean scriptValue) {
      this.scriptValue = scriptValue;
   }

   /**
    * Get the user defined runtime min value.
    *
    * @return min date value.
    */
   public Object getMin() {
      return userDefinedMin.getRValue();
   }

   private String formatDefinedRangeDate(Object date) {
      Calendar cal = new GregorianCalendar();
      Date dateValue = null;

      if(date instanceof Date) {
         dateValue = (Date) date;
      }
      else if(date instanceof String) {
         dateValue = CalendarUtil.parseStringToDate(date.toString(), true);
      }

      if(dateValue != null) {
         cal.setTime(dateValue);

         return cal.get(Calendar.YEAR) + "-" +
            cal.get(Calendar.MONTH) + "-" + cal.get(Calendar.DAY_OF_MONTH);
      }

      return null;
   }

   /**
    * Get the user defined min value.
    *
    * @return min date value string width yyyy-MM-dd format.
    */
   public String getMinValue() {
      return userDefinedMin.getDValue();
   }

   /**
    * Get the user defined max runtime value.
    *
    * @return max date value
    */
   public Object getMax() {
      return userDefinedMax.getRValue();
   }

   /**
    * Get the user defined max value.
    *
    * @return max date value string width yyyy-MM-dd format.
    */
   public String getMaxValue() {
      return userDefinedMax.getDValue();
   }

   /**
    * Set the user defined runtime min value.
    *
    * @param min date value string width yyyy-MM-dd format
    */
   public void setMin(String min) {
      userDefinedMin.setRValue(min);
   }

   /**
    * Set the user defined max runtime value.
    *
    * @param max date value string width yyyy-MM-dd format
    */
   public void setMax(String max) {
      userDefinedMax.setRValue(max);
   }

   /**
    * Set the user defined min value.
    *
    * @param min date value string width yyyy-MM-dd format
    */
   public void setMinValue(String min) {
      userDefinedMin.setDValue(min);
   }

   /**
    * Set the user defined max value.
    *
    * @param max date value string width yyyy-MM-dd format
    */
   public void setMaxValue(String max) {
      userDefinedMax.setDValue(max);
   }

   // view
   private DynamicValue2 typeValue = new DynamicValue2(CALENDAR_SHOW_TYPE + "", XSchema.INTEGER);
   private DynamicValue2 modeValue = new DynamicValue2(SINGLE_CALENDAR_MODE + "", XSchema.INTEGER);
   // year or monthly view
   private DynamicValue yearViewValue = new DynamicValue("false", XSchema.BOOLEAN);
   private DynamicValue daySelectionValue = new DynamicValue("false", XSchema.BOOLEAN);
   private DynamicValue weekFormatValue = new DynamicValue(null, XSchema.STRING);
   private DynamicValue selectedDateFormatValue = new DynamicValue(null, XSchema.STRING);

   // allow day selection in monthly view
   private TitleInfo titleInfo = new TitleInfo("Calendar");
   private String currdate1; // yyyy-mm or yyyy
   private String currdate2; // yyyy-mm or yyyy
   // input data
   private DataRef column;
   // output data
   private boolean period = false;
   private String[] dates;
   private String[] range;
   private DynamicValue userDefinedMin = new DynamicValue();
   private DynamicValue userDefinedMax = new DynamicValue();
   private boolean modeSValid = false; // to check whether mode script is valid
   private boolean typeSValid = false;
   private Dimension pixelsize; // runtime size
   private boolean scriptValue;

   private static final Logger LOG =
      LoggerFactory.getLogger(CalendarVSAssemblyInfo.class);
}
