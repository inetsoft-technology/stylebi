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
package inetsoft.report.internal.binding;

import inetsoft.report.ReportSheet;
import inetsoft.report.TableLens;
import inetsoft.report.composition.graph.OriginalOrder;
import inetsoft.report.filter.ConditionGroup;
import inetsoft.report.filter.SortOrder;
import inetsoft.report.internal.Util;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.UserVariable;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XNamedGroupInfo;
import inetsoft.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.PrintWriter;
import java.sql.Time;
import java.util.*;

/**
 * Group sort order class.
 * This class defines several sort constant and used to define specific
 * group sort order.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class OrderInfo implements java.io.Serializable, Cloneable, XMLSerializable {
   /**
    * Ascendent order.
    */
   public static final int SORT_ASC = SortOrder.SORT_ASC;
   /**
    * Descendent order.
    */
   public static final int SORT_DESC = SortOrder.SORT_DESC;
   /**
    * Sort by value ascendent order.
    */
   public static final int SORT_VALUE_ASC = XConstants.SORT_VALUE_ASC;
   /**
    * Sort by value descendent order.
    */
   public static final int SORT_VALUE_DESC = XConstants.SORT_VALUE_DESC;
   /**
    * Original order, treat the data as already sorted.
    */
   public static final int SORT_ORIGINAL = SortOrder.SORT_ORIGINAL;
   /**
    * Specific order, using named group.
    */
   public static final int SORT_SPECIFIC = SortOrder.SORT_SPECIFIC;
   /**
    * Use the original order.
    */
   public static final int SORT_NONE = SortOrder.SORT_NONE;

   /**
    * Other group option when using specific order: put all others together.
    */
   public static final int GROUP_OTHERS = SortOrder.GROUP_OTHERS;
   /**
    * Other group option when using specific order:
    * leave all other data in their own group.
    */
   public static final int LEAVE_OTHERS = SortOrder.LEAVE_OTHERS;

   /**
    * check if an order is valid.
    *
    * @param order the specified order
    * @return true if valid, false otherwise
    */
   public static boolean isValidOrder(int order) {
      order &= ~SORT_SPECIFIC;

      return order == SORT_ASC || order == SORT_DESC ||
         order == SORT_NONE || order == SORT_ORIGINAL;
   }

   /**
    * Create Sort Order object.
    * @param column if specified, the original order of the column in table is used for
    * NONE and ORIGINAL sorting.
    */
   public SortOrder createSortOrder(TableLens table, DataRef column) {
      SortOrder order = null;

      if(type == SORT_NONE || type == SORT_ORIGINAL) {
         OriginalOrder comp = null;

         if(column != null && table != null) {
            int col = Util.findColumn(table, column.getName());

            if(col >= 0) {
               comp = new OriginalOrder(table, col);
            }
         }

         OriginalOrder finalComp = comp;
         order = new SortOrder(this.type) {
            @Override
            public int compare(Object d1, Object d2) {
               if(finalComp != null) {
                  return finalComp.compare(d1, d2);
               }

               if(d1 instanceof Date && d2 == null) {
                  return super.compare((Date) d1, null);
               }
               else if(d1 == null && d2 instanceof Date) {
                  return super.compare(null, (Date) d2);
               }
               else if(d1 instanceof Date && d2 instanceof Date ) {
                  return super.compare((Date) d1, (Date) d2);
               }
               else if(d1 instanceof Date || d2 instanceof Date) {
                  return d1 instanceof Date ? 1 : -1;
               }

               return 0;
            }
         };
      }
      else {
         order = new SortOrder(type);
      }

      order.setOthers(getOthers());
      order.setInterval(getInterval(), getOption());

      if(isSpecific()) {
         // in case the old case sorted the groups of namedgroup caused by Feature #30805,
         // so use manual values to keep the order.
         String[] names = manualOrder != null && !manualOrder.isEmpty() ?
            manualOrder.toArray(new String[manualOrder.size()]) : getGroups();

         if(names != null) {
            names = Arrays.stream(names).map(v -> v == null ? "" : v).toArray(String[]::new);
         }

         order.setManual((info == null || info.isEmpty()) && cinfo != null && !cinfo.isEmpty());
         Map map = getManualOrderComparer();

         if(map != null && !map.isEmpty()) {
            String dtype = (String) map.get("dtype");
            List list = (List) map.get("list");

            // since namedgroup conditions created for date manual orders(equals to a specific
            // date value) are not fit for detail date values, so transform the conditions here
            // to fit for both date and date part manual sort.
            List<ConditionGroup> glist = new ArrayList<>();

            for(int i = 0; i < names.length; i++) {
               ConditionList cond = getNamedGroupInfo().getGroupCondition(names[i]);
               fixConditionList(cond);
               ConditionGroup grp = new ConditionGroup(table, cond);
               glist.add(grp);
               order.addGroupCondition(names[i], grp);
            }

            // set dtype to convert the string group name to date or date part to avoid
            // losing date format after replacing detail data by group value.
            order.setGroupNameDtype(dtype);
            order.setDateManual(true);
            order.setOthers(SortOrder.LEAVE_OTHERS);

            return order;
         }

         for(int i = 0; i < names.length; i++) {
            ConditionList cond = getNamedGroupInfo().getGroupCondition(names[i]);
            ConditionGroup grp = new ConditionGroup(table, cond);
            order.addGroupCondition(names[i], grp);
         }

         if(names.length > 0 && info instanceof AssetNamedGroupInfo) {
            order.setOthers(((AssetNamedGroupInfo) info).getOthers());
         }
      }
      else if((type & SORT_SPECIFIC) == SORT_SPECIFIC) {
         order.setOrder(type & ~SORT_SPECIFIC);
      }

      return order;
   }

   private void fixConditionList(ConditionList list) {
      if(list == null || list.getSize() == 0) {
         return;
      }

      for(int i = 0; i < list.getSize(); i++) {
         ConditionItem item = list.getConditionItem(i);
         XCondition cond = item.getXCondition();

         if(cond instanceof Condition) {
            cond = fixCondition((Condition) cond);
            item.setXCondition(cond);;
         }
      }
   }

   private AbstractCondition fixCondition(Condition cond) {
      List<Object> values = cond == null ? null : cond.getValues();

      if(values == null || values.size() != 1 || values.get(0) == null) {
         return cond;
      }

      if((option & DateRangeRef.PART_DATE_GROUP) != 0) {
         try {
            int partVal = Integer.parseInt(values.get(0) + "");
            return new DateCondition.PartDateCondition(option, partVal);
         }
         catch(NumberFormatException ex) {
            return cond;
         }
      }

      cond.setOperation(XCondition.BETWEEN);
      Date nvalue = getNextDate((Date) values.get(0));
      values.add(nvalue);

      return cond;
   }

   private Date getNextDate(Date date) {
      boolean Quarter = option == DateRangeRef.QUARTER_DATE_GROUP;
      int calendarLevel = getCalendarLevel(option);
      Calendar calendar = CoreTool.calendar.get();
      calendar.setTime(date);
      calendar.add(calendarLevel, (Quarter ? 3 : 1) * ((int) interval));

      // see Condition.evaluate function, between are <= and >=, so minus 1.
      if(date instanceof Time) {
         return new java.sql.Time(calendar.getTimeInMillis() - 1);
      }
      else if(date instanceof java.sql.Date) {
         calendar.setTimeInMillis(calendar.getTimeInMillis() - 1);
         int year = calendar.get(Calendar.YEAR);
         int month = calendar.get(Calendar.MONTH);
         int day = calendar.get(Calendar.DAY_OF_MONTH);
         // clear time part.
         calendar.clear();
         calendar.set(year, month, day);
         return calendar.getTime();
      }
      else {
         return new Date(calendar.getTimeInMillis() - 1);
      }
   }

   private int getCalendarLevel(int groupLevel) {
      switch(groupLevel) {
         case XConstants.YEAR_DATE_GROUP:
            return Calendar.YEAR;
         case XConstants.QUARTER_DATE_GROUP:
         case XConstants.MONTH_DATE_GROUP:
            return Calendar.MONTH;
         case XConstants.WEEK_DATE_GROUP:
            return Calendar.WEEK_OF_YEAR;
         case XConstants.DAY_DATE_GROUP:
            return Calendar.DAY_OF_YEAR;
         case XConstants.HOUR_DATE_GROUP:
            return Calendar.HOUR;
         case XConstants.MINUTE_DATE_GROUP:
            return Calendar.MINUTE;
         case XConstants.SECOND_DATE_GROUP:
            return Calendar.SECOND;
      }

      return -1;
   }

   private Map getManualOrderComparer() {
      if(info != null || cinfo == null || option == DateRangeRef.NONE_DATE_GROUP) {
         return null;
      }

      String[] names = cinfo.getGroups();

      for(int i = 0; i < names.length; i++) {
         ConditionList cond = cinfo.getGroupCondition(names[i]);

         if(cond == null || cond.getSize() == 0) {
            continue;
         }

         for(int j = 0; j < cond.getSize(); j++) {
            ConditionItem item = cond.getConditionItem(j);
            DataRef ref = item == null ? null :  item.getAttribute();
            String dtype = ref == null ? null : ref.getDataType();

            if(!XSchema.isDateType(dtype)) {
               return null;
            }

            List list = new ArrayList();

            try {
               for(int k = 0; k < manualOrder.size(); k++) {
                  String val = manualOrder.get(k);

                  if(val == null || val.isEmpty()) {
                     list.add(val);
                     continue;
                  }

                  if((option & DateRangeRef.PART_DATE_GROUP) == DateRangeRef.PART_DATE_GROUP) {
                     list.add(val);
                  }
                  else if(XSchema.DATE.equals(dtype)) {
                     list.add(Tool.parseDate(val));
                  }
                  else if(XSchema.TIME_INSTANT.equals(dtype)) {
                     list.add(Tool.parseDateTime(val));
                  }
                  else if(XSchema.TIME.equals(dtype)) {
                     list.add(Tool.parseTime(val));
                  }
               }
            }
            catch(Exception ignore) {
               LOG.debug(ignore.getMessage(), ignore);
            }

            if((option & DateRangeRef.PART_DATE_GROUP) != 0) {
               dtype = XSchema.INTEGER;
            }

            Map map = new HashMap();
            map.put("dtype", dtype);
            map.put("list", list);

            return map;
         }
      }

      return null;
   }

   /**
    * Create Sort Order object.
    * The column index is forcely specified.
    *
    * @param colidx the specified column index
    */
   public SortOrder createSortOrder(int colidx) {
      SortOrder order = new SortOrder(this.type);

      order.setOthers(getOthers());
      order.setInterval(getInterval(), getOption());

      if(isSpecific()) {
         String[] names = getGroups();

         for(int i = 0; i < names.length; i++) {
            ConditionList cond = getNamedGroupInfo().getGroupCondition(names[i]);
            ConditionGroup grp = new ConditionGroup(colidx, cond);
            order.addGroupCondition(names[i], grp);
         }

         if(names.length > 0 && info instanceof AssetNamedGroupInfo) {
            order.setOthers(((AssetNamedGroupInfo) info).getOthers());
         }
      }

      return order;
   }

   /**
    * Create Sort Order object.
    */
   public SortOrder createSortOrder(String[] headers) {
      DefaultTableLens table = new DefaultTableLens(2, headers.length);

      for(int i = 0; i < headers.length; i++) {
         table.setObject(0, i, headers[i]);
         table.setObject(1, i, "");
      }

      return createSortOrder(table, null);
   }

   /**
    * Create a default sorting order.
    */
   public OrderInfo() {
      this(SORT_ASC);
   }

   /**
    * Create a sort order object with order type.
    * @param type type of order, one of SORT_ASC,
    *             SORT_DESC, SORT_ORIGINAL and SORT_SPECIFIC.
    */
   public OrderInfo(int type) {
      this.type = type;
      this.others = OrderInfo.GROUP_OTHERS;
      otherLabel = Catalog.getCatalog().getString("Others");
      interval = 1;
      option = SortOrder.DAY_DATE_GROUP;
      manualOrder = new ArrayList<>();
   }

   /**
    * Get order type.
    * @return type of order.
    */
   public int getOrder() {
      return type;
   }

   /**
    * Set the type of order.
    * @param type type of order.
    */
   public void setOrder(int type) {
      this.type = type;
   }

   /**
    * Determine if the order type if ascendent.
    * @return true for ascendent.
    */
   public boolean isAsc() {
      return (type & SORT_ASC) == SORT_ASC;
   }

   /**
    * Determine if the order type if descendent.
    * @return true for descendent.
    */
   public boolean isDesc() {
      return (type & SORT_DESC) == SORT_DESC;
   }

   /**
    * Determine if the order type if sort by value ascendent.
    * @return true for sort by value ascendent.
    */
   public boolean isSortByValAsc() {
      return isSortByValAsc(type);
   }

   public static boolean isSortByValAsc(int type) {
      return (type & SORT_VALUE_ASC) == SORT_VALUE_ASC;
   }

   /**
    * Determine if the order type if sort by value descendent.
    * @return true for sort by value descendent.
    */
   public boolean isSortByValDesc() {
      return isSortByValDesc(type);
   }

   public static boolean isSortByValDesc(int type) {
      return (type & SORT_VALUE_DESC) == SORT_VALUE_DESC;
   }

   /**
    * Determine if the order type if sort by value.
    * @return true for sort by value.
    */
   public boolean isSortByVal() {
      return isSortByVal(type);
   }

   public static boolean isSortByVal(int type) {
      return isSortByValAsc(type) || isSortByValDesc(type);
   }

   /**
    * Determine if the order type if original sort.
    * @return true for original sort.
    */
   public boolean isOriginal() {
      return (type & SORT_ORIGINAL) == SORT_ORIGINAL;
   }

   /**
    * Determine if the order type is sort none.
    * @return true for sort none.
    */
   public boolean isSortNone() {
      // SORT_NONE is 0, any number & 0 = 0
      // return (type & SORT_NONE) == SORT_NONE;
      return type == SORT_NONE;
   }

   /**
    * Determine if using specific order.
    * @return true for using specific order.
    */
   public boolean isSpecific() {
      return (type & SORT_SPECIFIC) == SORT_SPECIFIC;
   }

   /**
    * Set specific order option.
    * @param b true for using specific order.
    */
   public void setSpecific(boolean b) {
      if(b) {
         type |= SORT_SPECIFIC;
      }
      else {
         type &= ~SORT_SPECIFIC;
      }
   }

   /**
    * Get specific group names.
    * @return group names.
    */
   public String[] getGroups() {
      XNamedGroupInfo cinfo = getNamedGroupInfo();
      return cinfo == null ? new String[0] : cinfo.getGroups();
   }

   /**
    * Get specific named group info.
    * @return group names.
    */
   public XNamedGroupInfo getNamedGroupInfo() {
      return cinfo == null? info : cinfo;
   }

   /**
    * Set specific named group info.
    */
   public void setNamedGroupInfo(XNamedGroupInfo in) {
      info = in;
   }

   /**
    * Get specific real named group info.
    * @return group names.
    */
   public XNamedGroupInfo getRealNamedGroupInfo() {
      return info;
   }

   /**
    * Get specific customer named group info.
    * @return group names.
    */
   public XNamedGroupInfo getCustomerNamedGroupInfo() {
      return cinfo;
   }

   /**
    * Set specific customer named group info.
    * @return group names.
    */
   public void setCustomerNamedGroupInfo(XNamedGroupInfo in) {
      cinfo = in;
   }

   /**
    * Set other groups option.
    * @param others other group option.
    */
   public void setOthers(int others) {
      this.others = others;
   }

   /**
    * Get other groups option.
    * @return other group option.
    */
   public int getOthers() {
      return others;
   }

   /**
    * Set the label for the 'others' group of named grouping.
    */
   public void setOtherLabel(String lbl) {
      this.otherLabel = lbl;
   }

   /**
    * Get the others group label.
    */
   public String getOtherLabel() {
      return otherLabel;
   }

   /**
    * Set date period interval and option.
    * @param d date period interval, e.g. number of years
    * @param opt date period option, e.g. year range
    */
   public void setInterval(double d, int opt) {
      this.interval = d;
      this.option = opt;
   }

   /**
    * Get date period interval.
    * @return date period interval.
    */
   public double getInterval() {
      return interval;
   }

   /**
    * Get date period option.
    * @return date period option.
    */
   public int getOption() {
      return option;
   }

   /**
    * Get sort by value column.
    * @return the sort column.
    */
   public int getSortByCol() {
      return sortByCol;
   }

   /**
    * Set sort by value column.
    * @param sortByCol the sort column.
    */
   public void setSortByCol(int sortByCol) {
      this.sortByCol = sortByCol;
   }

   /**
    * Get sort by value column.
    */
   public String getSortByColValue() {
      return sortByColValue;
   }

   /**
    * Set sort by value column.
    */
   public void setSortByColValue(String col) {
      sortByColValue = col;
   }

   public List<String> getManualOrder() {
      return manualOrder;
   }

   public void setManualOrder(List<String> manualOrder) {
      this.manualOrder = manualOrder;
   }

   /**
    * Update the asset named group info.
    * @param rep the specified asset repository.
    */
   public void update(AssetRepository rep, ReportSheet report) throws Exception {
      if(info instanceof AssetNamedGroupInfo) {
         ((AssetNamedGroupInfo) info).update(rep, report);
      }
   }

   @Override
   public OrderInfo clone() {
      try {
         OrderInfo order = (OrderInfo) super.clone();

         if(info != null) {
            order.info = (XNamedGroupInfo) info.clone();
         }

         if(cinfo != null) {
            order.cinfo = (XNamedGroupInfo) cinfo.clone();
         }

         if(manualOrder != null) {
            order.manualOrder = (List<String>) Tool.clone(manualOrder);
         }

         return order;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone order info", ex);
      }

      return null;
   }

   /**
    * Writer the sort order attributes to XML.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.println("<groupSort sort=\"" + getOrder() + "\" interval=\"" +
         getInterval() + "\" option=\"" + getOption() + "\" others=\"" +
         getOthers() + "\" sortByCol=\"" + getSortByCol() +
         "\" info=\"" + (info != null) +  "\" cinfo=\"" +
         (cinfo != null) + "\">" +
         "<otherLabel><![CDATA[" + otherLabel + "]]></otherLabel>");

      if(info != null) {
         ((XMLSerializable) info).writeXML(writer);
      }

      if(cinfo != null) {
         ((XMLSerializable) cinfo).writeXML(writer);
      }

      if(manualOrder != null) {
         ItemList mlist = new ItemList("manualOrderList");
         mlist.addAllItems(manualOrder);
         mlist.writeXML(writer);
      }

      writer.println("</groupSort>");
   }

   /**
    * Parse the xml segment.
    */
   @Override
   public final void parseXML(Element tag) throws Exception {
      String attr;

      if((attr = Tool.getAttribute(tag, "sort")) != null) {
         setOrder(Integer.parseInt(attr));
      }

      double interval = 0;
      int opt = 0;

      if((attr = Tool.getAttribute(tag, "interval")) != null) {
         interval = (Double.valueOf(attr)).doubleValue();
      }

      if((attr = Tool.getAttribute(tag, "option")) != null) {
         opt = Integer.parseInt(attr);
      }

      setInterval(interval, opt);

      if((attr = Tool.getAttribute(tag, "others")) != null) {
         setOthers(Integer.parseInt(attr));
      }

      if((attr = Tool.getAttribute(tag, "sortByCol")) != null) {
         setSortByCol(Integer.parseInt(attr));
      }

      boolean infoNull = false;
      boolean cinfoNull = false;

      if((attr = Tool.getAttribute(tag, "info")) != null) {
         infoNull = "false".equals(attr);
      }

      if((attr = Tool.getAttribute(tag, "cinfo")) != null) {
         cinfoNull = "false".equals(attr);
      }

      // parse named groups
      NodeList list = Tool.getChildNodesByTagName(tag, "namedgroups");

      if(list.getLength() > 0 && !infoNull) {
         Element tagn = (Element) list.item(0);
         String tstr = Tool.getAttribute(tagn, "type");
         int type = tstr == null ?
            XNamedGroupInfo.EXPERT_NAMEDGROUP_INFO : Integer.parseInt(tstr);

         if(type == XNamedGroupInfo.SIMPLE_NAMEDGROUP_INFO) {
            info = new SimpleNamedGroupInfo();
         }
         else if(type == XNamedGroupInfo.EXPERT_NAMEDGROUP_INFO) {
            info = new ExpertNamedGroupInfo();
         }
         else if(type == XNamedGroupInfo.ASSET_NAMEDGROUP_INFO_REF) {
            info = new AssetNamedGroupInfo();
         }
         else {
            throw new RuntimeException("Unsupported named group type found: " +
                                       type);
         }

         ((XMLSerializable) info).parseXML(tagn);
      }

      if(list.getLength() > 1 && !cinfoNull || infoNull && list.getLength() > 0) {
         Element tagn = (Element) list.item(infoNull ? 0 : 1);
         String tstr = Tool.getAttribute(tagn, "type");
         int type = tstr == null ?
            XNamedGroupInfo.EXPERT_NAMEDGROUP_INFO : Integer.parseInt(tstr);

         if(type == XNamedGroupInfo.EXPERT_NAMEDGROUP_INFO) {
            cinfo = new ExpertNamedGroupInfo();
         }
         else if(type == XNamedGroupInfo.SIMPLE_NAMEDGROUP_INFO) {
            cinfo = new SimpleNamedGroupInfo();
         }
         else {
            throw new RuntimeException("Unsupported named group type found: " +
                                       type);
         }

         ((XMLSerializable) cinfo).parseXML(tagn);
      }

      final Element node = Tool.getChildNodeByTagName(tag, "manualOrderList");

      if(node != null) {
         ItemList mlist = new ItemList();
         mlist.parseXML(node);
         manualOrder = new ArrayList(Arrays.asList(mlist.toArray()));
      }
   }

   /**
    * Check if equals another object.
    * @return <tt>true</tt> if equals, <tt>false</tt> otherwise.
    */
   public boolean equals(Object obj) {
      if(!(obj instanceof OrderInfo)) {
         return false;
      }

      OrderInfo info2 = (OrderInfo) obj;

      return others == info2.others &&
         type == info2.type &&
         option == info2.option &&
         interval == info2.interval &&
         sortByCol == info2.sortByCol &&
         Tool.equals(otherLabel, info2.otherLabel) &&
         Tool.equals(info, info2.info) &&
         Tool.equals(cinfo, info2.cinfo) &&
         Tool.equals(manualOrder, info2.manualOrder);
   }

   /**
    * Get the filter attr's variables.
    */
   public Vector<UserVariable> getAllVariables() {
      Vector<UserVariable> varVector = new Vector<>();

      if(isSpecific() || getNamedGroupInfo() != null) {
         XNamedGroupInfo ngroup = getNamedGroupInfo();
         String[] names = getGroups();

         for(int j = 0; j < names.length; j++) {
            ConditionList conditions = ngroup.getGroupCondition(names[j]);

            for(int k = 0; k < conditions.getSize(); k++) {
               XCondition cond = conditions.getXCondition(k);

               if(cond != null) {
                  UserVariable[] vars = cond.getAllVariables();

                  for(int m = 0; m < vars.length; m++) {
                     varVector.addElement(vars[m]);
                  }
               }
            }
         }
      }

      return varVector;
   }

   public boolean replaceVariables(VariableTable vars) {
      boolean replaced = false;

      if((isSpecific() || getNamedGroupInfo() != null) && vars != null) {
         XNamedGroupInfo ngroup = getNamedGroupInfo();
         String[] names = getGroups();

         for(int j = 0; j < names.length; j++) {
            ConditionList conditions = ngroup.getGroupCondition(names[j]);

            for(int k = 0; k < conditions.getSize(); k++) {
               XCondition cond = conditions.getXCondition(k);

               if(cond != null) {
                  cond.replaceVariable(vars);
                  replaced = true;
               }
            }
         }
      }

      return replaced;
   }

   public boolean printKey(PrintWriter writer) throws Exception {
      writer.print("OrderInfo[");
      writer.print(type + "," + sortByCol + "," + sortByColValue);
      return true;
   }

   private int others;
   private String otherLabel;
   private int type;
   private double interval;
   private int option;
   private int sortByCol; // summary column that sort by value based on
   private XNamedGroupInfo info;
   private XNamedGroupInfo cinfo;
   private List<String> manualOrder;
   private transient String sortByColValue;

   private static final Logger LOG = LoggerFactory.getLogger(OrderInfo.class);
}
