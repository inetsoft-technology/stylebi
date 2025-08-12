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
package inetsoft.uql.viewsheet;

import inetsoft.uql.*;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.CoreTool;
import inetsoft.util.Tool;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.awt.*;
import java.io.PrintWriter;
import java.util.List;
import java.util.*;

/**
 * CalendarVSAssembly represents one calendar assembly contained in a
 * <tt>Viewsheet</tt>.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class CalendarVSAssembly extends AbstractSelectionVSAssembly implements TitledVSAssembly {
   /**
    * Get the group title.
    * @return the title of the checkbox assembly.
    */
   @Override
   public String getTitle() {
      return getCalendarInfo().getTitle();
   }

   /**
    * Get the group title value.
    * @return the title value of the checkbox assembly.
    */
   @Override
   public String getTitleValue() {
      return getCalendarInfo().getTitleValue();
   }

   /**
    * Set the group title value.
    * @param value the specified group title.
    */
   @Override
   public void setTitleValue(String value) {
      getCalendarInfo().setTitleValue(value);
   }

   /**
    * create the condition list of a date array in a calendar.
    * @param index the specified start point in dates.
    * @param length the specified length in dates.
    * @param ref the specified data ref.
    * @return the condition list.
    */
   public static ConditionList createConditionList(String[] dates, int index,
                                                   int length, DataRef ref)
   {
      return createConditionList(dates, index, length, ref, null);
   }

   /**
    * create the condition list of a date array in a calendar.
    * @param index the specified start point in dates.
    * @param length the specified length in dates.
    * @param ref the specified data ref.
    * @param limitRange limit date range.
    *
    * @return the condition list.
    */
   public static ConditionList createConditionList(String[] dates, int index,
                                                   int length, DataRef ref, int[] limitRange) {
      ConditionList conds = new ConditionList();
      int startDay = -1; // day selection range
      int endDay = -1;

      for(int i = index; i < index + length; i++) {
         String date = dates[i];
         int idx = date.indexOf("-");
         int idx2 = date.lastIndexOf("-");
         int year = Integer.parseInt((idx < 0) ? date.substring(1)
                                     : date.substring(1, idx));
         int month = (idx < 0) ? 0 :
            (idx2 == idx ? Integer.parseInt(date.substring(idx + 1))
             : Integer.parseInt(date.substring(idx + 1, idx2)));
         int week = idx2 == idx ? -1 :
            Integer.parseInt(date.substring(idx2 + 1));

         int start, end;

         if(date.startsWith("y")) {
            start = encodeDate(year, month, 1, false, 0, 0);
            end = encodeDate(year, month, 1, false, Calendar.YEAR, 1);
         }
         else if(date.startsWith("m")) {
            start = encodeDate(year, month, 1, false, 0, 0);
            end = encodeDate(year, month, 1, false, Calendar.MONTH, 1);
         }
         else if(date.startsWith("w")) {
            start = encodeDate(year, month, week, true, Calendar.DATE, 0);
            end = encodeDate(year, month, week, true, Calendar.DATE, 7);
         }
         else if(date.startsWith("d")) {
            start = encodeDate(year, month, week, false, 0, 0);
            end = encodeDate(year, month, week, false, Calendar.DATE, 1);
         }
         else {
            throw new RuntimeException("Unsupported date found: " + date);
         }

         if(startDay < 0) {
            startDay = start;
            endDay = end;
            continue;
         }
         else if(start == endDay) {
            endDay = end;
            continue;
         }

         int level = length == 1 ? 0 : 1;
         appendCondition(conds, ref, startDay, endDay, level, limitRange);

         startDay = start;
         endDay = end;
      }

      if(startDay > 0) {
         int level = length == 1 ? 0 : 1;
         appendCondition(conds, ref, startDay, endDay, level, limitRange);
      }

      return conds;
   }

   /**
    * Get the date as an integer.
    * @param isweek true if the dayweek is week of month,
    * otherwise it's day of month.
    * @param type to increment.
    * @param inc increment to add to the type field.
    */
   private static int encodeDate(int year, int month, int dayweek,
                                 boolean isweek, int type, int inc)
   {
      GregorianCalendar calendar = new GregorianCalendar();

      calendar.clear();
      calendar.setFirstDayOfWeek(Tool.getFirstDayOfWeek());
      calendar.set(Calendar.YEAR, year);
      calendar.set(Calendar.MONTH, month);

      if(isweek) {
         calendar.set(Calendar.WEEK_OF_MONTH, dayweek);
      }
      else {
         calendar.set(Calendar.DAY_OF_MONTH, dayweek);
      }

      if(inc != 0) {
         calendar.add(type, inc);
      }

      year = calendar.get(Calendar.YEAR);
      month = calendar.get(Calendar.MONTH);
      int day = calendar.get(Calendar.DAY_OF_MONTH);

      return year * 10000 + month * 100 + day;
   }

   /**
    * Convert integer encoded date to Date object.
    */
   private static Date dayToDate(int day) {
      GregorianCalendar calendar = new GregorianCalendar();
      calendar.clear();
      calendar.set(Calendar.YEAR, day / 10000);
      calendar.set(Calendar.MONTH, (day / 100) % 100);
      calendar.set(Calendar.DAY_OF_MONTH, day % 100);

      return calendar.getTime();
   }

   /**
    * Append a range condition.
    */
   private static void appendCondition(ConditionList conds, DataRef ref,
                                       int start, int end, int level, int[] limitRange)
   {
      int conditionStartDay = start;
      int conditionEndDay = end;

      if(limitRange != null && limitRange.length == 2) {
         if(limitRange[0] > 0 && limitRange[0] > conditionStartDay) {
            conditionStartDay = limitRange[0];
         }

         if(limitRange[1] > 0 && limitRange[1] < conditionEndDay) {
            conditionEndDay = limitRange[1];
         }
      }

      Date mind = dayToDate(conditionStartDay);
      Date maxd = dayToDate(conditionEndDay);

      if(conds.getSize() > 0) {
         conds.append(new JunctionOperator(JunctionOperator.OR, 0));
      }

      String dataType = ref.getDataType();
      Condition mincond = new Condition(dataType);
      mincond.setOperation(Condition.GREATER_THAN);
      mincond.setEqual(true);
      mincond.addValue(convertToSqlDate(mind, dataType));
      conds.append(new ConditionItem(ref, mincond, level));
      conds.append(new JunctionOperator(JunctionOperator.AND, level));

      Condition maxcond = new Condition(dataType);
      maxcond.setOperation(Condition.LESS_THAN);
      maxcond.addValue(convertToSqlDate(maxd, dataType));
      conds.append(new ConditionItem(ref, maxcond, level));
   }

   /**
    * Parse a formatted date to date value.
    * @param date the formatted date.
    * @param floor <tt>true</tt> to return the floor date, <tt>false</tt> to
    * return the ceiling date.
    * @return the corresponding date value.
    */
   public static Date parseDate(String date, boolean floor) {
      int idx = date.indexOf("-");
      int idx2 = date.lastIndexOf("-");
      int year = Integer.parseInt((idx < 0) ? date.substring(1)
                                  : date.substring(1, idx));
      int month = (idx < 0) ? 0 :
         (idx2 == idx ?
          Integer.parseInt(date.substring(idx + 1)) :
          Integer.parseInt(date.substring(idx + 1, idx2)));
      int week = idx2 == idx ? -1 :
         Integer.parseInt(date.substring(idx2 + 1));
      Calendar calendar = null;

      if(date.startsWith("y")) {
         calendar = new GregorianCalendar(year, 0, 1);

         if(!floor) {
            calendar.add(Calendar.YEAR, 1);
         }

         return calendar.getTime();
      }
      else if(date.startsWith("m")) {
         calendar = new GregorianCalendar(year, month, 1);

         if(!floor) {
            calendar.add(Calendar.MONTH, 1);
         }

         return calendar.getTime();
      }
      else if(date.startsWith("w")) {
         calendar = new GregorianCalendar();
         calendar.setFirstDayOfWeek(Tool.getFirstDayOfWeek());
         calendar.clear();
         calendar.set(Calendar.YEAR, year);
         calendar.set(Calendar.MONTH, month);
         calendar.set(Calendar.WEEK_OF_MONTH, week);

         if(!floor) {
            calendar.add(Calendar.DATE, 7);
         }

         return calendar.getTime();
      }
      else if(date.startsWith("d")) {
         calendar = new GregorianCalendar();
         calendar.clear();
         calendar.set(Calendar.YEAR, year);
         calendar.set(Calendar.MONTH, month);
         calendar.set(Calendar.DAY_OF_MONTH, week);

         if(!floor) {
            calendar.add(Calendar.DATE, 1);
         }

         return calendar.getTime();
      }
      else {
         throw new RuntimeException("Unsupported date found: " + date);
      }
   }

   /**
    * Constructor.
    */
   public CalendarVSAssembly() {
      super();
   }

   /**
    * Constructor.
    */
   public CalendarVSAssembly(Viewsheet vs, String name) {
      super(vs, name);
   }

   /**
    * Create assembly info.
    * @return the associated assembly info.
    */
   @Override
   protected VSAssemblyInfo createInfo() {
      return new CalendarVSAssemblyInfo();
   }

   /**
    * Get the type.
    * @return the type of the assembly.
    */
   @Override
   public int getAssemblyType() {
      return Viewsheet.CALENDAR_ASSET;
   }

   /**
    * Get the name of the target table.
    * @return the name of the target table.
    */
   @Override
   public String getTableName() {
      return getCalendarInfo().getTableName();
   }

   /**
    * Set the name of the target table.
    * @param table the specified name of the target table.
    */
   @Override
   public void setTableName(String table) {
      getCalendarInfo().setTableName(table);
   }

   /**
    * Get the data ref.
    * @return the data ref.
    */
   public DataRef getDataRef() {
      return getCalendarInfo().getDataRef();
   }

   /**
    * Set the data ref.
    * @param column the data ref.
    */
   public void setDataRef(DataRef column) {
      getCalendarInfo().setDataRef(column);
   }

   /**
    * Get the dates.
    * @return the dates.
    */
   public String[] getDates() {
      return getCalendarInfo().getDates();
   }

   /**
    * Set the dates.
    * @param dates the dates.
    * @return the changed hint.
    */
   public int setDates(String[] dates) {
      if(!Tool.equals(getDates(), dates)) {
         getCalendarInfo().setDates(dates);
         return OUTPUT_DATA_CHANGED | VIEW_CHANGED;
      }

      return NONE_CHANGED;
   }

   /**
    * Get the dates.
    * @return the dates.
    */
   public String[] getRange() {
      return getCalendarInfo().getRange();
   }

   /**
    * Set the dates.
    * @param dates the dates.
    * @return the changed hint.
    */
   public int setRange(String[] dates) {
      if(!Tool.equals(getRange(), dates)) {
         getCalendarInfo().setRange(dates);
         return OUTPUT_DATA_CHANGED;
      }

      return NONE_CHANGED;
   }

   /**
    * Get the runtime show type.
    * @return the runtime show type.
    */
   public int getShowType() {
      return getCalendarInfo().getShowType();
   }

   /**
    * Get the design time show type.
    * @return the design time show type.
    */
   public int getShowTypeValue() {
      return getCalendarInfo().getShowTypeValue();
   }

   /**
    * Set show the calendar should be displayed, one of CALENDAR_SHOW_TYPE or
    * DROPDOWN_SHOW_TYPE defined in CalednarVSAssemblyInfo.
    * @param type the show type.
    */
   public void setShowTypeValue(int type) {
      getCalendarInfo().setShowTypeValue(type);
   }

   /**
    * Get the runtime view mode.
    * @return the runtime view mode.
    */
   public int getViewMode() {
      return getCalendarInfo().getViewMode();
   }

   /**
    * Get the design time view mode.
    * @return the design time view mode.
    */
   public int getViewModeValue() {
      return getCalendarInfo().getViewModeValue();
   }

   /**
    * Set whether to show a single calendar or double calendars, using options
    * SINGLE_CALENDAR_MODE or DOUBLE_CALENDAR_MODE defined in
    * CalendarVSAssemblyInfo respectively.
    * @param mode the view mode.
    */
   public int setViewModeValue(int mode) {
      if(getViewModeValue() != mode) {
         CalendarVSAssemblyInfo info = getCalendarInfo();
         getCalendarInfo().setViewModeValue(mode);
         // change pixel size to make sure assembly has pixel size
         // and no pixel size works same
         Dimension size = getViewsheet().getPixelSize(info);

         if(mode == CalendarVSAssemblyInfo.SINGLE_CALENDAR_MODE) {
            size.width = size.width == 0 ? 0 : (int) Math.ceil(size.width / 2);
         }
         else {
            size.width = size.width * 2;
         }

         info.setPixelSize(size);
         // @by davyc, when in shared filter, we change the view mode,
         // should also refresh the pixel size of the embedded viewsheet
         // see bug1245229499684
         VSUtil.refreshEmbeddedViewsheet(this);
         return OUTPUT_DATA_CHANGED | VIEW_CHANGED;
      }

      return NONE_CHANGED;
   }

   /**
    * Check if this calendar is in year view (or monthly view).
    */
   public boolean isYearView() {
      return getCalendarInfo().isYearView();
   }

   /**
    * Check if this calendar is in year view (or monthly view).
    */
   public boolean getYearViewValue() {
      return getCalendarInfo().getYearViewValue();
   }

   /**
    * Set whether to show months (year view) or weeks.
    */
   public int setYearViewValue(boolean year) {
      if(getYearViewValue() != year) {
         getCalendarInfo().setYearViewValue(year);
         return OUTPUT_DATA_CHANGED;
      }

      return NONE_CHANGED;
   }

   /**
    * Check if day selection is allowed.
    * runtime value.
    */
   public boolean isDaySelection() {
      return getCalendarInfo().isDaySelection();
   }

   /**
    * Check if day selection is allowed.
    * design time value.
    */
   public boolean getDaySelection() {
      return getCalendarInfo().getDaySelectionValue();
   }

   /**
    * Set whether day selection is allowed. This is only meaningful in monthly
    * view.
    */
   public void setDaySelectionValue(boolean day) {
      getCalendarInfo().setDaySelectionValue(day);
   }

   /**
    * If it is period.
    * @return it is period.
    */
   public boolean isPeriod() {
      return getCalendarInfo().isPeriod();
   }

   /**
    * Set if it is period.
    * @param period it is period.
    * @return the changed hint.
    */
   public int setPeriod(boolean period) {
      if(isPeriod() != period) {
         getCalendarInfo().setPeriod(period);
         return OUTPUT_DATA_CHANGED;
      }

      return NONE_CHANGED;
   }

   /**
    * Get calendar assembly info.
    * @return the calendar assembly info.
    */
   protected CalendarVSAssemblyInfo getCalendarInfo() {
      return (CalendarVSAssemblyInfo) getInfo();
   }

   /**
    * Get the selection.
    * @param map the container contains the selection of this selection
    * viewsheet assembly.
    * @return <tt>true</tt> if duplicated, <tt>false</tt> otherwise.
    */
   @Override
   public boolean getSelection(Map<String, Map<String, Collection<Object>>> map, boolean applied) {
      if(getDataRef() == null) {
         return false;
      }

      ConditionList conds = getConditionList();

      if(conds != null && conds.getSize() > 0) {
         final String rangeSelectionKey = RANGE + getDataRef().getName();
         final List<Object> rangeConditions =
            RangeCondition.createRangeConditions(conds, getName());

         for(String tableName : getTableNames()) {
            final Map<String, Collection<Object>> tableSelections =
               map.computeIfAbsent(tableName, (k) -> new HashMap<>());

            tableSelections.computeIfAbsent(rangeSelectionKey, (k) -> new Vector<>())
               .addAll(rangeConditions);
         }
      }

      return false;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public DataRef[] getDataRefs() {
      return getCalendarInfo().getDataRefs();
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public ConditionList getConditionList() {
      return getConditionList(getDataRefs());
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public ConditionList getConditionList(DataRef[] dataRefs) {
      if(!isEnabled() || dataRefs == null || dataRefs.length == 0) {
         return null;
      }

      String[] dates = getDates();
      DataRef ref = dataRefs[0];

      if(dates == null || dates.length == 0 || ref == null) {
         return null;
      }

      boolean dual =
         getViewMode() == CalendarVSAssemblyInfo.DOUBLE_CALENDAR_MODE;
      boolean period = isPeriod() && dual;
      boolean range = dual && !period;

      if((period && (dates.length % 2 != 0)) || (range && dates.length > 2)) {
         throw new RuntimeException("Incomplete date period selection: " +
                                    Arrays.asList(dates));
      }

      int[] limitRange = getLimitRange();

      // dual, period comparison?
      if(period) {
         int length = dates.length / 2;
         List<ConditionList> list = new ArrayList<>();
         ConditionList conds = createConditionList(dates, 0, length, ref, limitRange);
         list.add(conds);
         conds = createConditionList(dates, length, length, ref, limitRange);
         list.add(conds);
         return VSUtil.mergeConditionList(list, JunctionOperator.OR);
      }
      // dual, from min to max?
      else if(dual) {
         Date min = parseDate(dates[0], false);
         Date max = parseDate(dates.length < 2 ? dates[0] : dates[1], false);

         // min > max ? exchange the two
         if(min.getTime() > max.getTime()) {
            min = parseDate(dates[1], true); // floor date
            max = parseDate(dates[0], false); // ceiling date
         }
         else {
            min = parseDate(dates[0], true); // floor date
         }

         if(limitRange[0] > 0) {
            Date limitRangeMin = dayToDate(limitRange[0]);

            if(limitRangeMin.getTime() > min.getTime()) {
               min = limitRangeMin;
            }
         }

         if(limitRange[1] > 0) {
            Date limitRangeMax = dayToDate(limitRange[1]);

            if(limitRangeMax.getTime() < min.getTime()) {
               max = limitRangeMax;
            }
         }

         String dataType = ref.getDataType();
         ConditionList conds = new ConditionList();
         Condition mincond = new Condition(dataType);
         mincond.setOperation(Condition.GREATER_THAN);
         mincond.setEqual(true);
         mincond.addValue(convertToSqlDate(min, dataType));
         conds.append(new ConditionItem(ref, mincond, 0));
         conds.append(new JunctionOperator(JunctionOperator.AND, 0));
         Condition maxcond = new Condition(dataType);
         maxcond.setOperation(Condition.LESS_THAN);
         maxcond.addValue(convertToSqlDate(max, dataType));
         conds.append(new ConditionItem(ref, maxcond, 0));

         return conds;
      }
      // single, normal selection?
      else {
         return createConditionList(dates, 0, dates.length, ref, limitRange);
      }
   }

   private int[] getLimitRange() {
      int[] limitRange = new int[2];
      String[] range = getRange();

      if(range != null && range.length == 2) {
         limitRange[0] = getEncodeRangeDate(range[0], true);
         limitRange[1] = getEncodeRangeDate(range[1], false);
      }
      else {
         limitRange[0] = -1;
         limitRange[1] = -1;
      }

      return limitRange;
   }

   private int getEncodeRangeDate(String rangeDateStr, boolean start) {
      if(rangeDateStr == null) {
         return -1;
      }

      try {
         String[] rangeDate = rangeDateStr.split("-");
         int year = Integer.parseInt(rangeDate[0]);
         int month = Integer.parseInt(rangeDate[1]);
         int day = 0;

         if(rangeDate.length > 2) {
            day = Integer.parseInt(rangeDate[2]);
         }

         Calendar calendar = new GregorianCalendar();
         calendar.set(Calendar.YEAR, year);
         calendar.set(Calendar.MONTH, month);
         calendar.set(Calendar.DAY_OF_MONTH, day);
         calendar.add(Calendar.DAY_OF_MONTH, start ? 0 : 1);

         return encodeDate(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH), false, 0, 0);
      }
      catch(Exception ex) {
         return -1;
      }

   }

   /**
    * Reset the selection.
    * @return <tt>true</tt> if changed, <tt>false</tt> otherwise.
    */
   @Override
   public boolean resetSelection() {
      String[] dates = getDates();
      boolean changed = dates != null && dates.length > 0;
      setDates(null);
      return changed;
   }

   /**
    * Get the date type.
    * @return the date type.
    */
   public int getDateType(String date) {
      if(date == null || date.length() == 0) {
         return -1;
      }

      char c = date.charAt(0);

      switch(c) {
      case 'y':
         return Calendar.YEAR;
      case 'm':
         return Calendar.MONTH;
      case 'w':
         return Calendar.WEEK_OF_MONTH;
      case 'd':
         return Calendar.DAY_OF_MONTH;
      default:
         return -1;
      }
   }

   /**
    * Get the date type.
    * @return the date type.
    */
   public int getDateType() {
      String[] dates = getDates();

      if(dates == null || dates.length == 0) {
         return -1;
      }

      return getDateType(dates[0]);
   }

   /**
    * Write the state.
    * @param writer the specified print writer.
    */
   @Override
   protected void writeStateContent(PrintWriter writer, boolean runtime) {
      super.writeStateContent(writer, runtime);

      String[] dates = getDates();
      writer.println("<state_dates>");

      for(int i = 0; i < dates.length; i++) {
         writer.println("<date value=\"" + dates[i] + "\"/>");
      }

      writer.println("</state_dates>");

      // at runtime, when switch calendar from single to double, size will be
      // doubled too. In this case, we should save size when writing state
      if(runtime) {
         Dimension size = getPixelSize();

         if(size != null) {
            writer.print("<size width=\"" + size.width + "\" height=\"" +
                         size.height + "\"/>");
         }
      }

      writer.println("<state_period period=\"" + isPeriod() + "\" />");
      writer.println("<state_month month=\"" + getYearViewValue() + "\" />");
      writer.println("<state_view mode=\"" + getViewModeValue() + "\" />");
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

      Element dsnode = Tool.getChildNodeByTagName(elem, "state_dates");
      NodeList dnodes = Tool.getChildNodesByTagName(dsnode, "date");
      String[] dates = new String[dnodes.getLength()];

      for(int i = 0; i < dnodes.getLength(); i++) {
         Element dnode = (Element) dnodes.item(i);
         dates[i] = Tool.getAttribute(dnode, "value");
      }

      getCalendarInfo().setDates(dates, true);

      Element pnode = Tool.getChildNodeByTagName(elem, "state_period");

      if(pnode != null) {
         boolean period = "true".equals(Tool.getAttribute(pnode, "period"));
         setPeriod(period);
      }

      Element mnode = Tool.getChildNodeByTagName(elem, "state_month");

      if(mnode != null) {
         boolean month = "true".equals(Tool.getAttribute(mnode, "month"));
         setYearViewValue(month);
      }

      Element vnode = Tool.getChildNodeByTagName(elem, "state_view");

      if(vnode != null) {
         int mode = Integer.parseInt(Tool.getAttribute(vnode, "mode"));
         setViewModeValue(mode);
      }

      Element snode = Tool.getChildNodeByTagName(elem, "size");

      if(snode != null) {
         int w = Integer.parseInt(Tool.getAttribute(snode, "width"));
         int h = Integer.parseInt(Tool.getAttribute(snode, "height"));
         setPixelSize(new Dimension(w, h));
      }

      CalendarUtil.fixCurrentDates(getCalendarInfo());
   }

   /**
    * Copy the state selection from a selection viewsheet assembly.
    * @param assembly the specified selection viewsheet assembly.
    * @return the changed hint.
    */
   @Override
   public int copyStateSelection(SelectionVSAssembly assembly) {
      CalendarVSAssembly sassembly = (CalendarVSAssembly) assembly;
      int hint = setPeriod(sassembly.isPeriod());
      hint = hint | setDates(sassembly.getDates());
      hint = hint | setViewModeValue(sassembly.getViewModeValue());
      hint = hint | setYearViewValue(sassembly.getYearViewValue());
      return hint;
   }

   /**
    * Get display value.
    * @param onlyList only get the selected values, not include title,
    * and not restrict by visible properties.
    * @return the string to represent the selected value.
    */
   @Override
   public String getDisplayValue(boolean onlyList) {
      return getCalendarInfo().getDisplayValue(onlyList);
   }

   /**
    * Check if contains selection in this selection viewsheet assembly.
    * @return <tt>true</tt> if contains selection, <tt>false</tt>
    * otherwise.
    */
   @Override
   public boolean containsSelection() {
      if(!isEnabled()) {
         return false;
      }

      String[] dates = getDates();
      return dates != null && dates.length > 0;
   }

   @Override
   public void removeBindingCol(String ref) {
      setTableName(null);
   }

   @Override
   public void renameBindingCol(String oname, String nname) {
      // it only binding to to one col data, clear the col means clear table?
      DataRef dref = getCalendarInfo().getDataRef();

      if(dref != null && Tool.equals(oname, dref.getName())) {
         VSUtil.renameDataRef(dref, nname);
      }
   }

   /**
   * Update the selected date ranges.
   */
   public void updateSelectedRanges() {
      if(!isEnabled()) {
         return;
      }

      clearDateRanges();
      String[] dates = getDates();

      if(dates == null || dates.length == 0) {
         return;
      }

      boolean dual =
         getViewMode() == CalendarVSAssemblyInfo.DOUBLE_CALENDAR_MODE;
      boolean period = isPeriod() && dual;
      boolean range = dual && !period;

      if((period && (dates.length % 2 != 0)) || (range && dates.length > 2)) {
         throw new RuntimeException("Incomplete date period selection: " +
                     Arrays.asList(dates));
      }

      // double, date range ?
      if(!period && dual) {
         Date min = parseDate(dates[0], false);
         Date max = parseDate(dates.length < 2 ? dates[0] : dates[1], false);

         // min > max ? exchange the two
         if(min.getTime() > max.getTime()) {
            min = parseDate(dates[1], true);
            max = parseDate(dates[0], false);
         }
         else {
            min = parseDate(dates[0], true);
         }

         startDates.add(dateToString(min, true));
         endDates.add(dateToString(max, false));
      }
      // double, period comparison or single, normal selectionor ?
      else {
         updateDateRanges(dates, 0, dates.length);
      }
   }

    /**
    * Update the target selected date ranges.
    * @param dates target dates.
    * @param index the specified start point in dates.
    * @param length the specified length in dates.
    */
   public void updateDateRanges(String[] dates, int index, int length){
      if(isDaySelection() || isYearView()) {
         updateDaySelectionRanges(dates);
         return;
      }

      for(int i = index; i < index + length; i++) {
         Date start = parseDate(dates[i], true);
         Date end = parseDate(dates[i], false);
         startDates.add(dateToString(start, true));
         endDates.add(dateToString(end, false));
      }
   }

   private boolean isDateSelected(String date) {
      return date != null && date.startsWith("d");
   }

   /**
    * update day selection ranges.
    * @param dates target dates.
    */
   private void updateDaySelectionRanges(String[] dates) {
      if(dates == null || dates.length < 1) {
         return;
      }

      Vector ranges = new Vector();

      for(int i = 0; i < dates.length; i++) {
         Vector range = new Vector();
         range.add(dates[i]);
         updateDayRange(dates, dates[i], range);

         if(range.size() > 1) {
            ranges.add(range);
            i += range.size() - 1;
         }
      }

      for(int i = 0; i < ranges.size(); i++) {
         Vector range = (Vector) ranges.get(i);
         Date start = parseDate(range.get(0).toString(), true);
         String endStr = range.get(range.size() - 1).toString();
         boolean dateSelected = isDateSelected(endStr);
         Date end = parseDate(endStr, dateSelected);
         startDates.add(dateToString(start, true));
         endDates.add(dateToString(end, dateSelected));
      }

      ArrayList<String> vec = new ArrayList();

      for(int i = 0; i < ranges.size(); i++) {
         vec.addAll((Vector) ranges.get(i));
      }

      if(vec.size() > dates.length - 1) {
         return;
      }

      for(int i = 0; i < dates.length; i++) {
         if(!vec.contains(dates[i])) {
            Date start = parseDate(dates[i], true);
            Date end = parseDate(dates[i], isDateSelected(dates[i]));
            startDates.add(dateToString(start, true));
            endDates.add(dateToString(end, isDateSelected(dates[i])));
         }
      }
   }

   /**
    * Update day selection range.
    * @param dates target dates.
    * @param dayStr current the date string to find date range.
    * @param range save date range.
    */
   private void updateDayRange(String[] dates, String dayStr, Vector range) {
      if(dates == null || dates.length < 1 || range.size() > dates.length - 1) {
         return;
      }

      Date day = parseDate(dayStr, true);

      for(int i = 0; i < dates.length; i++) {
         Date d = null;

         if(isDateSelected(dayStr)) {
            d = new Date(day.getTime() + 24 * 60 * 60 * 1000);
         }
         else {
            Calendar cal = CoreTool.calendar.get();
            cal.setTime(day);
            cal.add(Calendar.MONTH, 1);
            d = cal.getTime();
         }

         if(dateToString(d, true).equals(dateToString(parseDate(dates[i], true), true))) {
            range.add(dates[i]);
            updateDayRange(dates, dates[i], range);
         }
      }
   }

   /**
    * Convert Data object to string as format yyyy-mm-dd.
    */
   private String dateToString(Date date, boolean start) {
      Date date0 = start ?
         date : new Date(date.getTime() - 24 * 60 * 60 * 1000);
      return Tool.formatDate(date0);
   }

   private static Object convertToSqlDate(Object value, String type) {
      if(!(value instanceof Date)) {
         return value;
      }

      if(Tool.equals(type, Tool.DATE)) {
         return new java.sql.Date(((Date) value).getTime());
      }
      else if(Tool.equals(type, Tool.TIME_INSTANT)) {
         return new java.sql.Timestamp(((Date) value).getTime());
      }

      return value;
   }

   /**
    * Get the selected start date ranges.
    */
   public ArrayList getStartDates() {
      return startDates;
   }

   /**
    * Get the selected end date ranges.
    */
   public ArrayList getEndDates() {
      return endDates;
   }

  /**
   * Clear the selected date ranges.
   */
   private void clearDateRanges() {
      startDates = new ArrayList();
      endDates = new ArrayList();
   }

   /**
    * Get the array of selected values defined in javascript
    */
   @Override
   public Object[] getScriptSelectedValues() {
      CalendarVSAssemblyInfo info = getCalendarInfo();
      return info.isScriptValue() ? (info.isPeriod() ? info.getRange() : info.getDates()) : null;
   }

   private ArrayList startDates = new ArrayList();
   private ArrayList endDates  = new ArrayList();
}
