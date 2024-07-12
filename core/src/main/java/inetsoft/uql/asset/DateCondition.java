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
package inetsoft.uql.asset;

import inetsoft.sree.SreeEnv;
import inetsoft.uql.*;
import inetsoft.uql.schema.UserVariable;
import inetsoft.uql.schema.XSchema;
import inetsoft.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.*;

/**
 * Date condition, a predefined condition evaluates <tt>Date</tt> objects.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public abstract class DateCondition extends AbstractCondition implements AssetObject {
   /**
    * Constants for specifying Date parts.
    */
   public enum REFERENCE {YEAR, QUARTER, MONTH}

   /**
    * One day milliseconds.
    */
   public static final long ONE_DAY = 24 * 60 * 60 * 1000;

   /**
    * Get all the built-in date conditions.
    * @return all the available date conditions.
    */
   public static DateCondition[] getBuiltinDateConditions() {
      return parseBuiltinDateConditions();
   }

   /**
    * Set all the built-in date conditions.
    * @param conditions the specified date conditions.
    * @return <tt>true</tt> if successful, <tt>false</tt> otherwise.
    */
   public boolean setBuiltinDateConditions(DateCondition[] conditions) {
      DateCondition.conditions = conditions;
      return saveBuiltinDateConditions(conditions);
   }

   /**
    * Get the year of a date.
    * @param date the specified date.
    * @return the year of the date.
    */
   public int getYear(Date date) {
      CALENDAR.setTime(date);
      return CALENDAR.get(Calendar.YEAR);
   }

   /**
    * Get the half year of a date.
    * @param date the specified date.
    * @return the half year of the date.
    */
   public int getHalfYear(Date date) {
      CALENDAR.setTime(date);
      return CALENDAR.get(Calendar.MONTH) / 6;
   }

   /**
    * Get the quarter of a date.
    * @param date the specified date.
    * @return the quarter of the date.
    */
   public int getQuarter(Date date) {
      CALENDAR.setTime(date);
      return CALENDAR.get(Calendar.MONTH) / 3;
   }

   /**
    * Get the month of a date.
    * @param date the specified date.
    * @return the month of the date.
    */
   public int getMonth(Date date) {
      CALENDAR.setTime(date);
      return CALENDAR.get(Calendar.MONTH);
   }

   /**
    * Get the month of a date.
    * @param date the specified date.
    * @return the month of the date.
    */
   public int getMonths(Date date) {
      CALENDAR.setTime(date);
      int year = CALENDAR.get(Calendar.YEAR);
      int month = CALENDAR.get(Calendar.MONTH);
      return year * 12 + month;
   }

   /**
    * Get the weeks of a date from 1970-01-01 on.
    * @param date the specified date.
    * @return the week of the date.
    */
   public int getWeeks(Date date) {
      int days = getDays(date);
      return (days + 4) / 7;
   }

   /**
    * Get the days of a date from 1970-01-01 on.
    * @param date the specified date.
    * @return the day of the date.
    */
   public int getDays(Date date) {
      long ts = date.getTime() + ZONE.getRawOffset() +
         (ZONE.inDaylightTime(date) ? ZONE.getDSTSavings() : 0);
      return (int) (ts / ONE_DAY);
   }

   /**
    * Get the Day Light Savings offset value. This is a workaround for a java bug,
    * it should only be applicable if the day we need to evaluate is the day
    * Day Light savings start.
    *
    * @param min the timestamp of the day we need to evaluate.
    * @return the day light savings offset.
    */
   public long getDayLightSavingsOffset(long min) {
      Date curDate = new Date(min);
      Date tomorrowDate = new Date(min);
      tomorrowDate.setTime(tomorrowDate.getTime() + ONE_DAY);

      if(ZONE.inDaylightTime(tomorrowDate) && !ZONE.inDaylightTime(curDate)) {
         return ZONE.getDSTSavings();
      }

      return 0;
   }

   /**
    * Parse built-in date conditions.
    * @return the parsed result.
    */
   public static synchronized DateCondition[] parseBuiltinDateConditions() {
      String path = SreeEnv.getProperty("asset.date.conditions");
      long lts = 0L;

      if(path != null) {
         lts = DataSpace.getDataSpace().getLastModified(null, path);
      }

      if(lts == ts && conditions != null) {
         return conditions;
      }

      ts = lts;

      try(InputStream in = getConditionInput(path)) {
         if(in == null) {
            LOG.error("No date conditions available!");
            return new DateCondition[0];
         }

         Document doc = Tool.parseXML(in);
         Element dsnode = doc.getDocumentElement();
         NodeList dnodes = Tool.getChildNodesByTagName(dsnode, "xCondition");
         conditions = new DateCondition[dnodes.getLength()];

         for(int i = 0; i < conditions.length; i++) {
            Element dnode = (Element) dnodes.item(i);
            conditions[i] = (DateCondition) AbstractCondition.createXCondition(dnode);
         }
      }
      catch(Exception ex) {
         LOG.error("Failed to parse built-in date conditions", ex);
         conditions = new DateCondition[0];
      }

      return conditions;
   }

   private static InputStream getConditionInput(String path) throws IOException {
      if(path != null) {
         return DataSpace.getDataSpace().getInputStream(null, path);
      }
      else {
         return DateCondition.class.getResourceAsStream(
            "/inetsoft/uql/asset/dateConditions.xml");
      }
   }

   /**
    * Save built-in date conditions.
    * @param conditions the specified date conditions.
    * @return <tt>true</tt> if successful, <tt>false</tt> otherwise.
    */
   private static synchronized boolean saveBuiltinDateConditions(
      DateCondition[] conditions)
   {
      String path = SreeEnv.getProperty("asset.date.conditions",
         "dateConditions.xml");

      try(DataSpace.Transaction tx = DataSpace.getDataSpace().beginTransaction();
          OutputStream out = tx.newStream(null, path) )
      {
         PrintWriter writer = new PrintWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
         writer.println("<?xml version=\"1.0\" encoding=\"utf-8\" ?>");
         writer.println("<dateConditions>");

         for(DateCondition condition : conditions) {
            condition.writeXML(writer);
         }

         writer.println("</dateConditions>");
         writer.flush();
         tx.commit();
         return true;
      }
      catch(Throwable exc) {
         LOG.error("Failed to save date condition", exc);
         return false;
      }
   }

   /**
    * Constructor.
    */
   public DateCondition() {
      super();
      type = XSchema.DATE;
      op = DATE_IN;
   }

   /**
    * Get the name.
    * @return the name of the date condition.
    */
   public String getName() {
      return name;
   }

   /**
    * Set the name.
    * @param name the specified name.
    */
   public void setName(String name) {
      this.name = name;
   }

   /**
    * Get the label.
    * @return the label of the date condition.
    */
   public String getLabel() {
      return label;
   }

   /**
    * Set the label.
    * @param label the specified label.
    */
   public void setLabel(String label) {
      this.label = label;
   }

   /**
    * Check if type is changeable.
    * @return <tt>true</tt> if changeable, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isTypeChangeable() {
      return true;
   }

   /**
    * Check if operation is changeable.
    * @return <tt>true</tt> if changeable, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isOperationChangeable() {
      return true;
   }

   /**
    * Check if equal is changeable.
    * @return <tt>true</tt> if changeable, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isEqualChangeable() {
      return false;
   }

   /**
    * Check if negated is changeable.
    * @return <tt>true</tt> if changeable, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isNegatedChangeable() {
      return true;
   }

   /**
    * Replace all embeded user variables.
    * @param vars the specified variable table.
    */
   @Override
   public void replaceVariable(VariableTable vars) {
   }

   /**
    * Get all variables in the condition value list.
    * @return the variable list.
    */
   @Override
   public UserVariable[] getAllVariables() {
      return new UserVariable[0];
   }

   /**
    * Write the contents.
    * @param writer the specified print writer.
    */
   @Override
   public void writeContents(PrintWriter writer) {
      if(name != null) {
         writer.print("<name>");
         writer.print("<![CDATA[" + name + "]]>");
         writer.println("</name>");
      }

      if(label != null) {
         writer.print("<label>");
         writer.print("<![CDATA[" + label + "]]>");
         writer.println("</label>");
      }
   }

   /**
    * Parse the contents.
    * @param elem the specified xml element.
    */
   @Override
   public void parseContents(Element elem) throws Exception {
      name = Tool.getChildValueByTagName(elem, "name");
      label = Tool.getChildValueByTagName(elem, "label");
   }

   /**
    * Convert this condition to sql mergeable condition.
    */
   public abstract Condition toSqlCondition(boolean isTimestamp);

   /**
    * Get the date(only includes year, month and day) from a calendar.
    */
   protected long getDate(Calendar cal) {
      int year = cal.get(Calendar.YEAR);
      int month = cal.get(Calendar.MONTH);
      int day = cal.get(Calendar.DAY_OF_MONTH);
      cal.clear();
      cal.set(year, month, day);

      return cal.getTimeInMillis();
   }

   /**
    * Create a sql condition, this condition is date type which is between date1
    * and date2.
    * @param date1 the earlier date.
    * @param date2 the later date.
    */
   protected Condition createSqlCondition(Date date1, Date date2) {
      Condition condition = new Condition();
      condition.setNegated(isNegated());
      condition.setOperation(XCondition.BETWEEN);
      condition.addValue(truncateMS(date1));
      condition.addValue(truncateMS(date2));
      return condition;
   }

   private static Date truncateMS(Date date1) {
      if(date1 instanceof Timestamp) {
         return new Timestamp((date1.getTime() / 1000) * 1000);
      }

      return new java.sql.Date((date1.getTime() / 1000) * 1000);
   }

   private static java.util.Date getStart(Calendar cal, boolean isTimestamp) {
      return getStart(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH),
                      cal.get(Calendar.DAY_OF_MONTH), isTimestamp);
   }

   // get the start of a day (at 00:00:00).
   private static java.util.Date getStart(int year, int month, int day, boolean isTimestamp) {
      Calendar cal = new GregorianCalendar();
      cal.set(year, month, day, 0, 0, 0);

      if(isTimestamp) {
         cal.set(Calendar.MILLISECOND, cal.getMinimum(Calendar.MILLISECOND));
         return new Timestamp(cal.getTimeInMillis());
      }

      return new java.sql.Date(cal.getTimeInMillis());
   }

   private static java.util.Date getEnd(Calendar cal, boolean isTimestamp) {
      return getEnd(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH),
                      cal.get(Calendar.DAY_OF_MONTH), isTimestamp);
   }

   // get the end of a day (at 23:59:59).
   private static java.util.Date getEnd(int year, int month, int day, boolean isTimestamp) {
      Calendar cal = new GregorianCalendar();
      cal.set(year, month, day, 23, 59, 59);

      if(isTimestamp) {
         cal.set(Calendar.MILLISECOND, cal.getMinimum(Calendar.MILLISECOND));
         return new Timestamp(cal.getTimeInMillis());
      }

      return new java.sql.Date(cal.getTimeInMillis());
   }

   /**
    * Get the string representation.
    * @return the string representation.
    */
   public String toString() {
      StringBuilder strbuff = new StringBuilder();
      Catalog catalog = Catalog.getCatalog();
      strbuff.append(" [");

      if(negated) {
         strbuff.append(catalog.getString("is not"));
      }
      else {
         strbuff.append(catalog.getString("is"));
      }

      strbuff.append("]");
      strbuff.append(" [");
      strbuff.append(catalog.getString("in range"));
      strbuff.append("]");
      strbuff.append(" [");
      strbuff.append(catalog.getString(getLabel()));
      strbuff.append("]");

      return strbuff.toString();
   }

   @Override
   public int hashCode() {
      return Objects.hash(super.hashCode(), name, label);
   }

   /**
    * Clone the object.
    * @return the cloned object.
    */
   @Override
   public DateCondition clone() {
      try {
         DateCondition cond = (DateCondition) super.clone();
         cond.CALENDAR = new GregorianCalendar();
         return cond;
      }
      catch(Exception ex) {
         return null;
      }
   }

   protected static final TimeZone ZONE = TimeZone.getDefault();
   private static DateCondition[] conditions;
   private static long ts;
   protected Calendar CALENDAR = new GregorianCalendar();
   private String name;
   private String label;

   /**
    * ToDateCondition represents a date range from a start reference (e.g.
    * beginning of year, quarter, month) until now. This range can ALSO be
    * offset by year, quarter, or month.
    */
   public static class ToDateCondition extends DateCondition {
      public int getMonthOffset() {
         return monthOffset;
      }

      public void setMonthOffset(int monthOffset) {
         this.monthOffset = monthOffset;
      }

      public int getQuarterOffset() {
         return quarterOffset;
      }

      public void setQuarterOffset(int quarterOffset) {
         this.quarterOffset = quarterOffset;
      }

      public int getYearOffset() {
         return yearOffset;
      }

      public void setYearOffset(int yearOffset) {
         this.yearOffset = yearOffset;
      }

      public REFERENCE getStartReference() {
         return startReference;
      }

      public void setStartReference(REFERENCE startReference) {
         this.startReference = startReference;
      }

      /**
       * @inheritDoc
       */
      @Override
      public Condition toSqlCondition(boolean isTimestamp) {
         long[] range = getRange(System.currentTimeMillis());
         Date date1 = isTimestamp ? new Timestamp(range[0]) : new java.sql.Date(range[0]);
         Date date2 = isTimestamp ? new Timestamp(range[1]) : new java.sql.Date(range[1]);
         return createSqlCondition(date1, date2);
      }

      /**
       * @inheritDoc
       */
      @Override
      public boolean evaluate(Object value) {
         if(!(value instanceof Date)) {
            return false;
         }

         long timestamp = ((Date) value).getTime();
         long[] range = getRange(System.currentTimeMillis());
         boolean isWithinRange = timestamp >= range[0] && timestamp < range[1];
         return isNegated() ? !isWithinRange : isWithinRange;
      }

      /**
       * Calculates the date range relative to the time provided.
       *
       * @param timeInMillis  the time to base the range on
       * @return  an array, index 0 is the start,
       *                    index 1 is the end of the range.
       */
      private long[] getRange(long timeInMillis) {
         Calendar cal = new GregorianCalendar();
         cal.setTimeInMillis(timeInMillis);

         // Calculate END
         int months = (yearOffset * 4 + quarterOffset) * 3 + monthOffset;
         cal.add(Calendar.MONTH, -months);
         long end = cal.getTimeInMillis();

         // Start should at 00:00:00
         int hours = cal.get(Calendar.HOUR_OF_DAY);
         cal.add(Calendar.HOUR_OF_DAY, -hours);
         int minutes = cal.get(Calendar.MINUTE);
         cal.add(Calendar.MINUTE, -minutes);
         int seconds = cal.get(Calendar.SECOND);
         cal.add(Calendar.SECOND, -seconds);
         int milliseconds = cal.get(Calendar.MILLISECOND);
         cal.add(Calendar.MILLISECOND, -milliseconds);

         // Calculate START
         switch(startReference) {
            case YEAR:
               cal.set(cal.get(Calendar.YEAR), 0, 1);
               break;
            case QUARTER:
               int quarter = cal.get(Calendar.MONTH) / 3;
               cal.set(cal.get(Calendar.YEAR), quarter * 3, 1);
               break;
            case MONTH:
               cal.set(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), 1);
               break;
         }

         return new long[] {cal.getTimeInMillis(), end};
      }

      /**
       * @inheritDoc
       */
      @Override
      public boolean isValid() {
         return true;
      }

      /**
       * Writer the attributes.
       * @param writer the specified print writer.
       */
      @Override
      public void writeAttributes(PrintWriter writer) {
         super.writeAttributes(writer);
         writer.print(" year=\"" + yearOffset + "\"");
         writer.print(" quarter=\"" + quarterOffset + "\"");
         writer.print(" month=\"" + monthOffset + "\"");
         writer.print(" start=\"" + startReference.name() + "\"");
      }

      /**
       * Parse the attributes.
       * @param elem the specified xml element.
       */
      @Override
      public void parseAttributes(Element elem) throws Exception {
         super.parseAttributes(elem);
         yearOffset = Integer.parseInt(Tool.getAttribute(elem, "year"));
         quarterOffset = Integer.parseInt(Tool.getAttribute(elem, "quarter"));
         monthOffset = Integer.parseInt(Tool.getAttribute(elem, "month"));
         startReference = REFERENCE.valueOf(Tool.getAttribute(elem, "start"));
      }

      /**
       * Print the key to identify this content object. If the keys of two
       * content objects are equal, the content objects are equal too.
       */
      @Override
      public boolean printKey(PrintWriter writer) throws Exception {
         super.printKey(writer);
         writer.print("[");
         writer.print(yearOffset);
         writer.print(",");
         writer.print(quarterOffset);
         writer.print(",");
         writer.print(monthOffset);
         writer.print(",");
         writer.print(startReference);
         writer.print("]");
         return true;
      }

      @Override
      public boolean equals(Object o) {
         if(this == o) return true;
         if(o == null || getClass() != o.getClass()) return false;
         if(!super.equals(o)) return false;

         ToDateCondition that = (ToDateCondition) o;

         if(monthOffset != that.monthOffset) return false;
         if(quarterOffset != that.quarterOffset) return false;
         if(yearOffset != that.yearOffset) return false;
         if(startReference != that.startReference) return false;
         return true;
      }

      @Override
      public int hashCode() {
         return Objects.hash(super.hashCode(), yearOffset, quarterOffset, monthOffset, startReference);
      }

      private int yearOffset;    // number of years to negatively offset
      private int quarterOffset; // number of quarters to negatively offset
      private int monthOffset;   // number of months to negatively offset
      private REFERENCE startReference;   // the start ref. of the range
   }

   /**
    * Year condition.
    */
   public static class YearCondition extends DateCondition {
      /**
       * Constructor.
       */
      public YearCondition() {
         super();
      }

      /**
       * Constructor.
       */
      public YearCondition(int yn) {
         this();
         setYearN(yn);
      }

      /**
       * Get the year yn.
       */
      public int getYearN() {
         return yn;
      }

      /**
       * Set the year n.
       */
      public void setYearN(int yn) {
         if(yn < 0) {
            return;
         }

         this.yn = yn;
      }

      /**
       * Print the key to identify this content object. If the keys of two
       * content objects are equal, the content objects are equal too.
       */
      @Override
      public boolean printKey(PrintWriter writer) throws Exception {
         super.printKey(writer);
         writer.print("[");
         writer.print(yn);
         writer.print("]");
         return true;
      }

      /**
       * Check if equals another object.
       * @return <tt>true</tt>if yes, <tt>false</tt> otherwise.
       */
      public boolean equals(Object obj) {
         if(!super.equals(obj)) {
            return false;
         }

         if(!(obj instanceof YearCondition)) {
            return false;
         }

         YearCondition cond = (YearCondition) obj;
         return cond.yn == yn;
      }

      @Override
      public int hashCode() {
         return Objects.hash(super.hashCode(), yn);
      }

      /**
       * Evaluate this condition against the specified value object.
       * @param value the value object this condition should be compared with.
       * @return <code>true</code> if the value object meets this condition.
       */
      @Override
      public boolean evaluate(Object value) {
         if(!(value instanceof Date)) {
            return false;
         }

         Date date = (Date) value;
         Date now = new Date(System.currentTimeMillis());

         boolean rc = getYear(now) - yn == getYear(date);
         return isNegated() ? !rc : rc;
      }

      /**
       * Convert this condition to sql mergeable condition.
       */
      @Override
      public Condition toSqlCondition(boolean isTimestamp) {
         Calendar cal = new GregorianCalendar();
         cal.setTimeInMillis(System.currentTimeMillis());
         int year = cal.get(Calendar.YEAR);

         Date date1 = DateCondition.getStart(year - yn, Calendar.JANUARY, 1, isTimestamp);
         Date date2 = DateCondition.getEnd(year - yn, Calendar.DECEMBER, 31, isTimestamp);
         return createSqlCondition(date1, date2);
      }

      /**
       * Check if the condition is a valid condition.
       * @return true if is valid, false otherwise.
       */
      @Override
      public boolean isValid() {
         return true;
      }

      /**
       * Writer the attributes.
       * @param writer the specified print writer.
       */
      @Override
      public void writeAttributes(PrintWriter writer) {
         super.writeAttributes(writer);
         writer.print(" year=\"" + yn + "\"");
      }

      /**
       * Parse the attributes.
       * @param elem the specified xml element.
       */
      @Override
      public void parseAttributes(Element elem) throws Exception {
         super.parseAttributes(elem);
         yn = Integer.parseInt(Tool.getAttribute(elem, "year"));
      }

      private int yn;
   }

   /**
    * Quarter condition.
    */
   public static class QuarterCondition extends DateCondition {
      /**
       * Constructor.
       */
      public QuarterCondition() {
         super();
      }

      /**
       * Constructor.
       */
      public QuarterCondition(int qn, int yn) {
         this();
         setQuarterN(qn);
         setYearN(yn);
      }

      /**
       * Get the quarter n.
       */
      public int getQuarterN() {
         return qn;
      }

      /**
       * Set the quarter n.
       */
      public void setQuarterN(int qn) {
         if(qn < 0) {
            return;
         }

         this.qn = qn;
      }

      /**
       * Get year n.
       */
      public int getYearN() {
         return yn;
      }

      /**
       * Set year n.
       */
      public void setYearN(int yn) {
         if(yn < 0) {
            return;
         }

         this.yn = yn;
      }

      /**
       * Print the key to identify this content object. If the keys of two
       * content objects are equal, the content objects are equal too.
       */
      @Override
      public boolean printKey(PrintWriter writer) throws Exception {
         super.printKey(writer);
         writer.print("[");
         writer.print(yn);
         writer.print(",");
         writer.print(qn);
         writer.print("]");
         return true;
      }

      /**
       * Check if equals another object.
       * @return <tt>true</tt>if yes, <tt>false</tt> otherwise.
       */
      public boolean equals(Object obj) {
         if(!super.equals(obj)) {
            return false;
         }

         if(!(obj instanceof QuarterCondition)) {
            return false;
         }

         QuarterCondition cond = (QuarterCondition) obj;
         return cond.yn == yn && cond.qn == qn;
      }

      @Override
      public int hashCode() {
         return Objects.hash(super.hashCode(), qn, yn);
      }

      /**
       * Evaluate this condition against the specified value object.
       * @param value the value object this condition should be compared with.
       * @return <code>true</code> if the value object meets this condition.
       */
      @Override
      public boolean evaluate(Object value) {
         if(!(value instanceof Date)) {
            return false;
         }

         Date date = (Date) value;
         Date now = new Date(System.currentTimeMillis());

         int nquarters = getYear(now) * 4 + getQuarter(now);
         int dquarters = getYear(date) * 4 + getQuarter(date);

         boolean rc = nquarters - qn == dquarters + yn * 4;
         return isNegated() ? !rc : rc;
      }

      /**
       * Convert this condition to sql mergeable condition.
       */
      @Override
      public Condition toSqlCondition(boolean isTimestamp) {
         Calendar cal = new GregorianCalendar();
         cal.setTimeInMillis(System.currentTimeMillis());
         cal.add(Calendar.MONTH, -(yn * 4 + qn) * 3);
         int year = cal.get(Calendar.YEAR);
         int quater = cal.get(Calendar.MONTH) / 3;
         Date date1 = DateCondition.getStart(year, quater * 3, 1, isTimestamp);
         Date date2 = DateCondition.getEnd(year, quater * 3 + 2,
                                             quater == 0 || quater == 3 ? 31 : 30, isTimestamp);
         return createSqlCondition(date1, date2);
      }

      /**
       * Check if the condition is a valid condition.
       * @return true if is valid, false otherwise.
       */
      @Override
      public boolean isValid() {
         return true;
      }

      /**
       * Writer the attributes.
       * @param writer the specified print writer.
       */
      @Override
      public void writeAttributes(PrintWriter writer) {
         super.writeAttributes(writer);
         writer.print(" year=\"" + yn + "\"");
         writer.print(" quarter=\"" + qn + "\"");
      }

      /**
       * Parse the attributes.
       * @param elem the specified xml element.
       */
      @Override
      public void parseAttributes(Element elem) throws Exception {
         super.parseAttributes(elem);
         yn = Integer.parseInt(Tool.getAttribute(elem, "year"));
         qn = Integer.parseInt(Tool.getAttribute(elem, "quarter"));
      }

      private int qn;
      private int yn;
   }

   /**
    * Nth quarter condition.
    */
   public static class NthQuarterCondition extends DateCondition {
      /**
       * Constructor.
       */
      public NthQuarterCondition() {
         super();
      }

      /**
       * Constructor.
       */
      public NthQuarterCondition(int qn, int yn) {
         this();
         setQuarterN(qn);
         setYearN(yn);
      }

      /**
       * Get the quarter n.
       */
      public int getQuarterN() {
         return qn;
      }

      /**
       * Set the quarter n.
       */
      public void setQuarterN(int qn) {
         if(qn < 0 || qn > 3 ) {
            return;
         }

         this.qn = qn;
      }

      /**
       * Get year n.
       */
      public int getYearN() {
         return yn;
      }

      /**
       * Set year n.
       */
      public void setYearN(int yn) {
         if(yn < 0) {
            return;
         }

         this.yn = yn;
      }

      /**
       * Print the key to identify this content object. If the keys of two
       * content objects are equal, the content objects are equal too.
       */
      @Override
      public boolean printKey(PrintWriter writer) throws Exception {
         super.printKey(writer);
         writer.print("[");
         writer.print(yn);
         writer.print(",");
         writer.print(qn);
         writer.print("]");
         return true;
      }

      /**
       * Check if equals another object.
       * @return <tt>true</tt>if yes, <tt>false</tt> otherwise.
       */
      public boolean equals(Object obj) {
         if(!super.equals(obj)) {
            return false;
         }

         if(!(obj instanceof NthQuarterCondition)) {
            return false;
         }

         NthQuarterCondition cond = (NthQuarterCondition) obj;
         return cond.yn == yn && cond.qn == qn;
      }

      @Override
      public int hashCode() {
         return Objects.hash(super.hashCode(), qn, yn);
      }

      /**
       * Evaluate this condition against the specified value object.
       * @param value the value object this condition should be compared with.
       * @return <code>true</code> if the value object meets this condition.
       */
      @Override
      public boolean evaluate(Object value) {
         if(!(value instanceof Date)) {
            return false;
         }

         Date date = (Date) value;
         Date now = new Date(System.currentTimeMillis());

         if(getYear(now) - yn != getYear(date)) {
            return isNegated();
         }

         boolean rc = getQuarter(date) == qn;
         return isNegated() ? !rc : rc;
      }

      /**
       * Convert this condition to sql mergeable condition.
       */
      @Override
      public Condition toSqlCondition(boolean isTimestamp) {
         Calendar cal = new GregorianCalendar();
         cal.setTimeInMillis(System.currentTimeMillis());
         cal.add(Calendar.YEAR, -yn);
         int year = cal.get(Calendar.YEAR);
         Date date1 = DateCondition.getStart(year, qn * 3, 1, isTimestamp);
         Date date2 = DateCondition.getEnd(year, qn * 3 + 2, qn == 0 || qn == 3 ? 31 : 30, isTimestamp);
         return createSqlCondition(date1, date2);
      }

      /**
       * Check if the condition is a valid condition.
       * @return true if is valid, false otherwise.
       */
      @Override
      public boolean isValid() {
         return true;
      }

      /**
       * Writer the attributes.
       * @param writer the specified print writer.
       */
      @Override
      public void writeAttributes(PrintWriter writer) {
         super.writeAttributes(writer);
         writer.print(" year=\"" + yn + "\"");
         writer.print(" quarter=\"" + qn + "\"");
      }

      /**
       * Parse the attributes.
       * @param elem the specified xml element.
       */
      @Override
      public void parseAttributes(Element elem) throws Exception {
         super.parseAttributes(elem);
         yn = Integer.parseInt(Tool.getAttribute(elem, "year"));
         qn = Integer.parseInt(Tool.getAttribute(elem, "quarter"));
      }

      private int qn;
      private int yn;
   }

   /**
    * Nth half year condition.
    */
   public static class NthHalfYearCondition extends DateCondition {
      /**
       * Constructor.
       */
      public NthHalfYearCondition() {
         super();
      }

      /**
       * Constructor.
       */
      public NthHalfYearCondition(int hn, int yn) {
         this();
         setHalfYearN(hn);
         setYearN(yn);
      }

      /**
       * Get the half year n.
       */
      public int getHalfYearN() {
         return hn;
      }

      /**
       * Set the half year n.
       */
      public void setHalfYearN(int hn) {
         if(hn < 0 || hn > 1) {
            return;
         }

         this.hn = hn;
      }

      /**
       * Get year n.
       */
      public int getYearN() {
         return yn;
      }

      /**
       * Set year n.
       */
      public void setYearN(int yn) {
         if(yn < 0) {
            return;
         }

         this.yn = yn;
      }

      /**
       * Print the key to identify this content object. If the keys of two
       * content objects are equal, the content objects are equal too.
       */
      @Override
      public boolean printKey(PrintWriter writer) throws Exception {
         super.printKey(writer);
         writer.print("[");
         writer.print(yn);
         writer.print(",");
         writer.print(hn);
         writer.print("]");
         return true;
      }

      /**
       * Check if equals another object.
       * @return <tt>true</tt>if yes, <tt>false</tt> otherwise.
       */
      public boolean equals(Object obj) {
         if(!super.equals(obj)) {
            return false;
         }

         if(!(obj instanceof NthHalfYearCondition)) {
            return false;
         }

         NthHalfYearCondition cond = (NthHalfYearCondition) obj;
         return cond.yn == yn && cond.hn == hn;
      }

      @Override
      public int hashCode() {
         return Objects.hash(super.hashCode(), hn, yn);
      }

      /**
       * Evaluate this condition against the specified value object.
       * @param value the value object this condition should be compared with.
       * @return <code>true</code> if the value object meets this condition.
       */
      @Override
      public boolean evaluate(Object value) {
         if(!(value instanceof Date)) {
            return false;
         }

         Date date = (Date) value;
         Date now = new Date(System.currentTimeMillis());

         if(getYear(now) - yn != getYear(date)) {
            return isNegated();
         }

         boolean rc = getHalfYear(date) == hn;
         return isNegated() ? !rc : rc;
      }

      /**
       * Convert this condition to sql mergeable condition.
       */
      @Override
      public Condition toSqlCondition(boolean isTimestamp) {
         Calendar cal = new GregorianCalendar();
         cal.setTimeInMillis(System.currentTimeMillis());
         int year = cal.get(Calendar.YEAR);
         Date date1 = null;
         Date date2 = null;

         if(hn == 0) {
            date1 = DateCondition.getStart(year - yn, 0, 1, isTimestamp);
            date2 = DateCondition.getEnd(year - yn, 5, 30, isTimestamp);
         }
         else {
            date1 = DateCondition.getStart(year - yn, 6, 1, isTimestamp);
            date2 = DateCondition.getEnd(year - yn, 11, 31, isTimestamp);
         }

         return createSqlCondition(date1, date2);
      }

      /**
       * Check if the condition is a valid condition.
       * @return true if is valid, false otherwise.
       */
      @Override
      public boolean isValid() {
         return true;
      }

      /**
       * Writer the attributes.
       * @param writer the specified print writer.
       */
      @Override
      public void writeAttributes(PrintWriter writer) {
         super.writeAttributes(writer);
         writer.print(" year=\"" + yn + "\"");
         writer.print(" halfYear=\"" + hn + "\"");
      }

      /**
       * Parse the attributes.
       * @param elem the specified xml element.
       */
      @Override
      public void parseAttributes(Element elem) throws Exception {
         super.parseAttributes(elem);
         yn = Integer.parseInt(Tool.getAttribute(elem, "year"));
         hn = Integer.parseInt(Tool.getAttribute(elem, "halfYear"));
      }

      private int hn;
      private int yn;
   }

   /**
    * Month condition.
    */
   public static class MonthCondition extends DateCondition {
      /**
       * Constructor.
       */
      public MonthCondition() {
         super();
      }

      /**
       * Constructor.
       */
      public MonthCondition(int mn, int yn) {
         this();
         setMonthN(mn);
         setYearN(yn);
      }

      /**
       * Get the month n.
       */
      public int getMonthN() {
         return mn;
      }

      /**
       * Set the month n.
       */
      public void setMonthN(int mn) {
         if(mn < 0) {
            return;
         }

         this.mn = mn;
      }

      /**
       * Get year n.
       */
      public int getYearN() {
         return yn;
      }

      /**
       * Set year n.
       */
      public void setYearN(int yn) {
         if(yn < 0) {
            return;
         }

         this.yn = yn;
      }

      /**
       * Print the key to identify this content object. If the keys of two
       * content objects are equal, the content objects are equal too.
       */
      @Override
      public boolean printKey(PrintWriter writer) throws Exception {
         super.printKey(writer);
         writer.print("[");
         writer.print(yn);
         writer.print(",");
         writer.print(mn);
         writer.print("]");
         return true;
      }

      /**
       * Check if equals another object.
       * @return <tt>true</tt>if yes, <tt>false</tt> otherwise.
       */
      public boolean equals(Object obj) {
         if(!super.equals(obj)) {
            return false;
         }

         if(!(obj instanceof MonthCondition)) {
            return false;
         }

         MonthCondition cond = (MonthCondition) obj;
         return cond.yn == yn && cond.mn == mn;
      }

      @Override
      public int hashCode() {
         return Objects.hash(super.hashCode(), mn, yn);
      }

      /**
       * Evaluate this condition against the specified value object.
       * @param value the value object this condition should be compared with.
       * @return <code>true</code> if the value object meets this condition.
       */
      @Override
      public boolean evaluate(Object value) {
         if(!(value instanceof Date)) {
            return false;
         }

         Date date = (Date) value;
         Date now = new Date(System.currentTimeMillis());

         int nmonths = getYear(now) * 12 + getMonth(now);
         int dmonths = getYear(date) * 12 + getMonth(date);

         boolean rc = nmonths - mn == dmonths + yn * 12;
         return isNegated() ? !rc : rc;
      }

      /**
       * Convert this condition to sql mergeable condition.
       */
      @Override
      public Condition toSqlCondition(boolean isTimestamp) {
         Calendar cal = new GregorianCalendar();
         cal.setTimeInMillis(System.currentTimeMillis());
         cal.add(Calendar.YEAR, -yn);
         cal.add(Calendar.MONTH, -mn);
         int year = cal.get(Calendar.YEAR);
         int month = cal.get(Calendar.MONTH);
         Date date1 = DateCondition.getStart(year, month, 1, isTimestamp);
         cal.setTime(date1);
         cal.add(Calendar.MONTH, 1);
         cal.add(Calendar.DATE, -1);
         Date date2 = DateCondition.getEnd(cal, isTimestamp);

         return createSqlCondition(date1, date2);
      }

      /**
       * Check if the condition is a valid condition.
       * @return true if is valid, false otherwise.
       */
      @Override
      public boolean isValid() {
         return true;
      }

      /**
       * Writer the attributes.
       * @param writer the specified print writer.
       */
      @Override
      public void writeAttributes(PrintWriter writer) {
         super.writeAttributes(writer);
         writer.print(" year=\"" + yn + "\"");
         writer.print(" month=\"" + mn + "\"");
      }

      /**
       * Parse the attributes.
       * @param elem the specified xml element.
       */
      @Override
      public void parseAttributes(Element elem) throws Exception {
         super.parseAttributes(elem);
         yn = Integer.parseInt(Tool.getAttribute(elem, "year"));
         mn  = Integer.parseInt(Tool.getAttribute(elem, "month"));
      }

      private int mn;
      private int yn;
   }

   /**
    * Nth month condition.
    */
   public static class NthMonthCondition extends DateCondition {
      /**
       * Constructor.
       */
      public NthMonthCondition() {
         super();
      }

      /**
       * Constructor.
       */
      public NthMonthCondition(int mn, int yn) {
         this();
         setMonthN(mn);
         setYearN(yn);
      }

      /**
       * Get the month n.
       */
      public int getMonthN() {
         return mn;
      }

      /**
       * Set the month n.
       */
      public void setMonthN(int mn) {
         if(mn < 0 || mn > 11) {
            return;
         }

         this.mn = mn;
      }

      /**
       * Get year n.
       */
      public int getYearN() {
         return yn;
      }

      /**
       * Set year n.
       */
      public void setYearN(int yn) {
         if(yn < 0) {
            return;
         }

         this.yn = yn;
      }

      /**
       * Print the key to identify this content object. If the keys of two
       * content objects are equal, the content objects are equal too.
       */
      @Override
      public boolean printKey(PrintWriter writer) throws Exception {
         super.printKey(writer);
         writer.print("[");
         writer.print(yn);
         writer.print(",");
         writer.print(mn);
         writer.print("]");
         return true;
      }

      /**
       * Check if equals another object.
       * @return <tt>true</tt>if yes, <tt>false</tt> otherwise.
       */
      public boolean equals(Object obj) {
         if(!super.equals(obj)) {
            return false;
         }

         if(!(obj instanceof NthMonthCondition)) {
            return false;
         }

         NthMonthCondition cond = (NthMonthCondition) obj;
         return cond.yn == yn && cond.mn == mn;
      }

      @Override
      public int hashCode() {
         return Objects.hash(super.hashCode(), mn, yn);
      }

      /**
       * Evaluate this condition against the specified value object.
       * @param value the value object this condition should be compared with.
       * @return <code>true</code> if the value object meets this condition.
       */
      @Override
      public boolean evaluate(Object value) {
         if(!(value instanceof Date)) {
            return false;
         }

         Date date = (Date) value;
         Date now = new Date(System.currentTimeMillis());

         if(getYear(now) - yn != getYear(date)) {
            return isNegated();
         }

         boolean rc = getMonth(date) == mn;
         return isNegated() ? !rc : rc;
      }

      /**
       * Convert this condition to sql mergeable condition.
       */
      @Override
      public Condition toSqlCondition(boolean isTimestamp) {
         Calendar cal = new GregorianCalendar();
         cal.setTimeInMillis(System.currentTimeMillis());
         int year = cal.get(Calendar.YEAR) - yn;
         Date date1 = DateCondition.getStart(year, mn, 1, isTimestamp);
         cal.setTime(date1);
         cal.add(Calendar.MONTH, 1);
         cal.add(Calendar.DATE, -1);
         Date date2 = DateCondition.getEnd(cal, isTimestamp);

         return createSqlCondition(date1, date2);
      }

      /**
       * Check if the condition is a valid condition.
       * @return <tt>true</tt> if is valid, <tt>false</tt> otherwise.
       */
      @Override
      public boolean isValid() {
         return true;
      }

      /**
       * Writer the attributes.
       * @param writer the specified print writer.
       */
      @Override
      public void writeAttributes(PrintWriter writer) {
         super.writeAttributes(writer);
         writer.print(" year=\"" + yn + "\"");
         writer.print(" month=\"" + mn + "\"");
      }

      /**
       * Parse the attributes.
       * @param elem the specified xml element.
       */
      @Override
      public void parseAttributes(Element elem) throws Exception {
         super.parseAttributes(elem);
         yn = Integer.parseInt(Tool.getAttribute(elem, "year"));
         mn = Integer.parseInt(Tool.getAttribute(elem, "month"));
      }

      private int mn;
      private int yn;
   }

   /**
    * Week condition.
    */
   public static class WeekCondition extends DateCondition {
      /**
       * Constructor.
       */
      public WeekCondition() {
         super();
      }

      /**
       * Constructor.
       */
      public WeekCondition(int wn) {
         this();
         setWeekN(wn);
      }

      /**
       * Get the week yn.
       */
      public int getWeekN() {
         return wn;
      }

      /**
       * Set the week n.
       */
      public void setWeekN(int wn) {
         if(wn < 0) {
            return;
         }

         this.wn = wn;
      }

      /**
       * Print the key to identify this content object. If the keys of two
       * content objects are equal, the content objects are equal too.
       */
      @Override
      public boolean printKey(PrintWriter writer) throws Exception {
         super.printKey(writer);
         writer.print("[");
         writer.print(wn);
         writer.print("]");
         return true;
      }

      /**
       * Check if equals another object.
       * @return <tt>true</tt>if yes, <tt>false</tt> otherwise.
       */
      public boolean equals(Object obj) {
         if(!super.equals(obj)) {
            return false;
         }

         if(!(obj instanceof WeekCondition)) {
            return false;
         }

         WeekCondition cond = (WeekCondition) obj;
         return cond.wn == wn;
      }

      @Override
      public int hashCode() {
         return Objects.hash(super.hashCode(), wn);
      }

      /**
       * Evaluate this condition against the specified value object.
       * @param value the value object this condition should be compared with.
       * @return <code>true</code> if the value object meets this condition.
       */
      @Override
      public boolean evaluate(Object value) {
         if(!(value instanceof Date)) {
            return false;
         }

         Date date = (Date) value;
         Date now = new Date(System.currentTimeMillis());

         int w1 = getWeeks(date);
         int w2 = getWeeks(now);

         boolean rc = w1 + wn == w2;
         return isNegated() ? !rc : rc;
      }

      /**
       * Convert this condition to sql mergeable condition.
       */
      @Override
      public Condition toSqlCondition(boolean isTimestamp) {
         Calendar cal = new GregorianCalendar();
         cal.setTimeInMillis(System.currentTimeMillis());
         cal.add(Calendar.WEEK_OF_YEAR, -wn);
         int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK);
         cal.add(Calendar.DATE, -dayOfWeek + 1); // first day of week
         Date date1 = DateCondition.getStart(cal, isTimestamp);
         cal.add(Calendar.DATE, 6);
         Date date2 = DateCondition.getEnd(cal, isTimestamp);

         return createSqlCondition(date1, date2);
      }

      /**
       * Check if the condition is a valid condition.
       * @return <tt>true</tt> if is valid, <tt>false</tt> otherwise.
       */
      @Override
      public boolean isValid() {
         return true;
      }

      /**
       * Writer the attributes.
       * @param writer the specified print writer.
       */
      @Override
      public void writeAttributes(PrintWriter writer) {
         super.writeAttributes(writer);
         writer.print(" week=\"" + wn + "\"");
      }

      /**
       * Parse the attributes.
       * @param elem the specified xml element.
       */
      @Override
      public void parseAttributes(Element elem) throws Exception {
         super.parseAttributes(elem);
         wn = Integer.parseInt(Tool.getAttribute(elem, "week"));
      }

      private int wn;
   }

   /**
    * Weeks condition.
    */
   public static class WeeksCondition extends DateCondition {
      /**
       * Constructor.
       */
      public WeeksCondition() {
         super();
      }

      /**
       * Constructor.
       */
      public WeeksCondition(int from, int to) {
         this();
         setFrom(from);
         setTo(to);
      }

      /**
       * Get the week from.
       */
      public int getFrom() {
         return from;
      }

      /**
       * Set the week from.
       */
      public void setFrom(int from) {
         if(from < 0) {
            return;
         }

         this.from = from;
      }

      /**
       * Get the week to.
       */
      public int getTo() {
         return to;
      }

      /**
       * Get the week to.
       */
      public void setTo(int to) {
         if(to < 0) {
            return;
         }

         this.to = to;
      }

      /**
       * Print the key to identify this content object. If the keys of two
       * content objects are equal, the content objects are equal too.
       */
      @Override
      public boolean printKey(PrintWriter writer) throws Exception {
         super.printKey(writer);
         writer.print("[");
         writer.print(from);
         writer.print(",");
         writer.print(to);
         writer.print("]");
         return true;
      }

      /**
       * Check if equals another object.
       * @return <tt>true</tt>if yes, <tt>false</tt> otherwise.
       */
      public boolean equals(Object obj) {
         if(!super.equals(obj)) {
            return false;
         }

         if(!(obj instanceof WeeksCondition)) {
            return false;
         }

         WeeksCondition cond = (WeeksCondition) obj;
         return cond.from == from && cond.to == to;
      }

      @Override
      public int hashCode() {
         return Objects.hash(super.hashCode(), from, to);
      }

      /**
       * Evaluate this condition against the specified value object.
       * @param value the value object this condition should be compared with.
       * @return <code>true</code> if the value object meets this condition.
       */
      @Override
      public boolean evaluate(Object value) {
         if(!(value instanceof Date)) {
            return false;
         }

         Date date = (Date) value;
         Date now = new Date(System.currentTimeMillis());

         int w1 = getWeeks(date);
         int w2 = getWeeks(now);

         boolean rc = w1 + from <= w2 && w1 + to > w2;
         return isNegated() ? !rc : rc;
      }

      /**
       * Convert this condition to sql mergeable condition.
       */
      @Override
      public Condition toSqlCondition(boolean isTimestamp) {
         Calendar cal = new GregorianCalendar();
         long current = System.currentTimeMillis();
         cal.setTimeInMillis(current);
         cal.add(Calendar.WEEK_OF_YEAR, -(to - 1));
         int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK);
         cal.add(Calendar.DATE, -dayOfWeek + 1);
         Date date1 = DateCondition.getStart(cal, isTimestamp);
         cal.setTimeInMillis(current);
         cal.add(Calendar.WEEK_OF_YEAR, -from);
         cal.add(Calendar.DATE, -dayOfWeek + 1);
         cal.add(Calendar.DATE, 6);
         Date date2 = DateCondition.getEnd(cal, isTimestamp);

         return createSqlCondition(date1, date2);
      }

      /**
       * Check if the condition is a valid condition.
       * @return <tt>true</tt> if is valid, <tt>false</tt> otherwise.
       */
      @Override
      public boolean isValid() {
         return from < to;
      }

      /**
       * Writer the attributes.
       * @param writer the specified print writer.
       */
      @Override
      public void writeAttributes(PrintWriter writer) {
         super.writeAttributes(writer);
         writer.print(" from=\"" + from + "\"");
         writer.print(" to=\"" + to + "\"");
      }

      /**
       * Parse the attributes.
       * @param elem the specified xml element.
       */
      @Override
      public void parseAttributes(Element elem) throws Exception {
         super.parseAttributes(elem);
         from = Integer.parseInt(Tool.getAttribute(elem, "from"));
         to = Integer.parseInt(Tool.getAttribute(elem, "to"));
      }

      private int from;
      private int to;
   }

   /**
    * Day condition.
    */
   public static class DayCondition extends DateCondition {
      /**
       * Constructor.
       */
      public DayCondition() {
         super();
      }

      /**
       * Constructor.
       */
      public DayCondition(int dn) {
         this();
         setDayN(dn);
      }

      /**
       * Get the day n.
       */
      public int getDayN() {
         return dn;
      }

      /**
       * Set the day n.
       */
      public void setDayN(int dn) {
         /*
         if(dn < 0) {
            return;
         }
         */

         this.dn = dn;
      }

      /**
       * Print the key to identify this content object. If the keys of two
       * content objects are equal, the content objects are equal too.
       */
      @Override
      public boolean printKey(PrintWriter writer) throws Exception {
         super.printKey(writer);
         writer.print("[");
         writer.print(dn);
         writer.print("]");
         return true;
      }

      /**
       * Check if equals another object.
       * @return <tt>true</tt>if yes, <tt>false</tt> otherwise.
       */
      public boolean equals(Object obj) {
         if(!super.equals(obj)) {
            return false;
         }

         if(!(obj instanceof DayCondition)) {
            return false;
         }

         DayCondition cond = (DayCondition) obj;
         return cond.dn == dn;
      }

      @Override
      public int hashCode() {
         return Objects.hash(super.hashCode(), dn);
      }

      /**
       * Evaluate this condition against the specified value object.
       * @param value the value object this condition should be compared with.
       * @return <code>true</code> if the value object meets this condition.
       */
      @Override
      public boolean evaluate(Object value) {
         if(!(value instanceof Date)) {
            return false;
         }

         Date date = (Date) value;
         Date now = new Date(System.currentTimeMillis());
         int d1 = getDays(date);
         int d2 = getDays(now);
         boolean rc = (d1 + dn) == d2;
         return isNegated() ? !rc : rc;
      }

      /**
       * Convert this condition to sql mergeable condition.
       */
      @Override
      public Condition toSqlCondition(boolean isTimestamp) {
         long evalDay = System.currentTimeMillis() - (dn * ONE_DAY);
         CALENDAR.setTime(new Date(evalDay));
         long curDay = getDate(CALENDAR);
         long max = curDay + ONE_DAY - TIME_OFFSET -
            getDayLightSavingsOffset(curDay);

         if(isTimestamp) {
            return createSqlCondition(new Timestamp(curDay), new Timestamp(max));
         }
         else {
            return createSqlCondition(new java.sql.Date(curDay), new java.sql.Date(max));
         }
      }

      /**
       * Check if the condition is a valid condition.
       * @return true if is valid, false otherwise.
       */
      @Override
      public boolean isValid() {
         return true;
      }

      /**
       * Writer the attributes.
       * @param writer the specified print writer.
       */
      @Override
      public void writeAttributes(PrintWriter writer) {
         super.writeAttributes(writer);
         writer.print(" day=\"" + dn + "\"");
      }

      /**
       * Parse the attributes.
       * @param elem the specified xml element.
       */
      @Override
      public void parseAttributes(Element elem) throws Exception {
         super.parseAttributes(elem);
         dn = Integer.parseInt(Tool.getAttribute(elem, "day"));
      }

      private int dn;
   }

   /**
    * Months condition.
    */
   public static class MonthsCondition extends DateCondition {
      /**
       * Constructor.
       */
      public MonthsCondition() {
         super();
      }

      /**
       * Constructor.
       */
      public MonthsCondition(int from, int to) {
         this();
         setFrom(from);
         setTo(to);
      }

      /**
       * Get the day from.
       */
      public int getFrom() {
         return from;
      }

      /**
       * Set the day from.
       */
      public void setFrom(int from) {
         this.from = from;
      }

      /**
       * Get the month to.
       */
      public int getTo() {
         return to;
      }

      /**
       * Set the month to.
       */
      public void setTo(int to) {
         this.to = to;
      }

      /**
       * Print the key to identify this content object. If the keys of two
       * content objects are equal, the content objects are equal too.
       */
      @Override
      public boolean printKey(PrintWriter writer) throws Exception {
         super.printKey(writer);
         writer.print("[");
         writer.print(from);
         writer.print(",");
         writer.print(to);
         writer.print("]");
         return true;
      }

      /**
       * Check if equals another object.
       * @return <tt>true</tt>if yes, <tt>false</tt> otherwise.
       */
      public boolean equals(Object obj) {
         if(!super.equals(obj)) {
            return false;
         }

         if(!(obj instanceof MonthsCondition)) {
            return false;
         }

         MonthsCondition cond = (MonthsCondition) obj;
         return cond.from == from && cond.to == to;
      }

      @Override
      public int hashCode() {
         return Objects.hash(super.hashCode(), from, to);
      }

      /**
       * Evaluate this condition against the specified value object.
       * @param value the value object this condition should be compared with.
       * @return <code>true</code> if the value object meets this condition.
       */
      @Override
      public boolean evaluate(Object value) {
         boolean isTimestamp = false;

         if(value instanceof Timestamp) {
            isTimestamp = true;
         }
         else if(!(value instanceof Date)) {
            return false;
         }

         long date = ((Date) value).getTime();
         CALENDAR.setTime(new Date(System.currentTimeMillis()));
         CALENDAR.add(Calendar.MONTH, -to + 1);
         long date1 =
            isTimestamp ? CALENDAR.getTimeInMillis() : getDate(CALENDAR);

         CALENDAR.setTime(new Date(System.currentTimeMillis()));
         CALENDAR.add(Calendar.MONTH, from);
         long date2 =
            isTimestamp ? CALENDAR.getTimeInMillis() : getDate(CALENDAR);

         boolean rc = date1 <= date && date2 >= date;
         return isNegated() ? !rc : rc;
      }

      /**
       * Convert this condition to sql mergeable condition.
       */
      @Override
      public Condition toSqlCondition(boolean isTimestamp) {
         CALENDAR.setTime(new Date(System.currentTimeMillis()));
         CALENDAR.add(Calendar.MONTH, -to + 1);
         Date date1 = DateCondition.getStart(CALENDAR, isTimestamp);

         CALENDAR.setTime(new Date(System.currentTimeMillis()));
         CALENDAR.add(Calendar.MONTH, from);
         Date date2 = DateCondition.getEnd(CALENDAR, isTimestamp);

         return createSqlCondition(date1, date2);
      }

      /**
       * Check if the condition is a valid condition.
       * @return true if is valid, false otherwise.
       */
      @Override
      public boolean isValid() {
         return from < to;
      }

      /**
       * Writer the attributes.
       * @param writer the specified print writer.
       */
      @Override
      public void writeAttributes(PrintWriter writer) {
         super.writeAttributes(writer);
         writer.print(" from=\"" + from + "\"");
         writer.print(" to=\"" + to + "\"");
      }

      /**
       * Parse the attributes.
       * @param elem the specified xml element.
       */
      @Override
      public void parseAttributes(Element elem) throws Exception {
         super.parseAttributes(elem);
         from = Integer.parseInt(Tool.getAttribute(elem, "from"));
         to = Integer.parseInt(Tool.getAttribute(elem, "to"));
      }

      private int from;
      private int to;
   }

   /**
    * Days condition.
    */
   public static class DaysCondition extends DateCondition {
      /**
       * Constructor.
       */
      public DaysCondition() {
         super();
      }

      /**
       * Constructor.
       */
      public DaysCondition(int from, int to) {
         this();
         setFrom(from);
         setTo(to);
      }

      /**
       * Get the day from.
       */
      public int getFrom() {
         return from;
      }

      /**
       * Set the day from.
       */
      public void setFrom(int from) {
         this.from = from;
      }

      /**
       * Get the day to.
       */
      public int getTo() {
         return to;
      }

      /**
       * Set the day to.
       */
      public void setTo(int to) {
         this.to = to;
      }

      /**
       * Print the key to identify this content object. If the keys of two
       * content objects are equal, the content objects are equal too.
       */
      @Override
      public boolean printKey(PrintWriter writer) throws Exception {
         super.printKey(writer);
         writer.print("[");
         writer.print(from);
         writer.print(",");
         writer.print(to);
         writer.print("]");
         return true;
      }

      /**
       * Check if equals another object.
       * @return <tt>true</tt>if yes, <tt>false</tt> otherwise.
       */
      public boolean equals(Object obj) {
         if(!super.equals(obj)) {
            return false;
         }

         if(!(obj instanceof DaysCondition)) {
            return false;
         }

         DaysCondition cond = (DaysCondition) obj;
         return cond.from == from && cond.to == to;
      }

      @Override
      public int hashCode() {
         return Objects.hash(super.hashCode(), from, to);
      }

      /**
       * Evaluate this condition against the specified value object.
       * @param value the value object this condition should be compared with.
       * @return <code>true</code> if the value object meets this condition.
       */
      @Override
      public boolean evaluate(Object value) {
         if(!(value instanceof Date)) {
            return false;
         }

         Date date = (Date) value;
         Date now = new Date(System.currentTimeMillis());

         int d1 = getDays(date);
         int d2 = getDays(now);

         boolean rc = d1 + from <= d2 && d1 + to > d2;
         return isNegated() ? !rc : rc;
      }

      /**
       * Convert this condition to sql mergeable condition.
       */
      @Override
      public Condition toSqlCondition(boolean isTimestamp) {
         long current = System.currentTimeMillis();
         GregorianCalendar cal = new GregorianCalendar();
         cal.setTimeInMillis(current - (to - 1) * ONE_DAY);
         Date date1 = DateCondition.getStart(cal, isTimestamp);
         cal.setTimeInMillis(current - from * ONE_DAY);
         Date date2 = DateCondition.getEnd(cal, isTimestamp);
         return createSqlCondition(date1, date2);
      }

      /**
       * Check if the condition is a valid condition.
       * @return true if is valid, false otherwise.
       */
      @Override
      public boolean isValid() {
         return from < to;
      }

      /**
       * Writer the attributes.
       * @param writer the specified print writer.
       */
      @Override
      public void writeAttributes(PrintWriter writer) {
         super.writeAttributes(writer);
         writer.print(" from=\"" + from + "\"");
         writer.print(" to=\"" + to + "\"");
      }

      /**
       * Parse the attributes.
       * @param elem the specified xml element.
       */
      @Override
      public void parseAttributes(Element elem) throws Exception {
         super.parseAttributes(elem);
         from = Integer.parseInt(Tool.getAttribute(elem, "from"));
         to = Integer.parseInt(Tool.getAttribute(elem, "to"));
      }

      private int from;
      private int to;
   }

   /**
    * Date Part condition.
    */
   public static class PartDateCondition extends DateCondition {
      /**
       * Constructor.
       */
      public PartDateCondition() {
         super();
      }

      /**
       * Constructor.
       */
      public PartDateCondition(int option, int partVal) {
         this();

         this.option = option;
         this.partVal = partVal;
      }

      /**
       * Print the key to identify this content object. If the keys of two
       * content objects are equal, the content objects are equal too.
       */
      @Override
      public boolean printKey(PrintWriter writer) throws Exception {
         super.printKey(writer);
         writer.print("[");
         writer.print(option);
         writer.print(",");
         writer.print(partVal);
         writer.print("]");
         return true;
      }

      /**
       * Check if equals another object.
       * @return <tt>true</tt>if yes, <tt>false</tt> otherwise.
       */
      public boolean equals(Object obj) {
         if(!super.equals(obj)) {
            return false;
         }

         if(!(obj instanceof PartDateCondition)) {
            return false;
         }

         PartDateCondition cond = (PartDateCondition) obj;
         return cond.option == option && cond.partVal == partVal;
      }

      @Override
      public int hashCode() {
         return Objects.hash(super.hashCode(), option, partVal);
      }

      /**
       * Evaluate this condition against the specified value object.
       * @param value the value object this condition should be compared with.
       * @return <code>true</code> if the value object meets this condition.
       */
      @Override
      public boolean evaluate(Object value) {
         if(!(value instanceof Date) && !(value instanceof Integer)) {
            return false;
         }

         int val = -1;

         if(value instanceof Date) {
            Date date = (Date) value;
            val = (int) DateRangeRef.getData(option, date);;
         }
         else if(value instanceof Integer) {
            val = (Integer) value;
         }

         boolean rc = val == partVal;
         return isNegated() ? !rc : rc;
      }

      /**
       * Convert this condition to sql mergeable condition.
       */
      @Override
      public Condition toSqlCondition(boolean isTimestamp) {
         return null;
      }

      /**
       * Check if the condition is a valid condition.
       * @return true if is valid, false otherwise.
       */
      @Override
      public boolean isValid() {
         return true;
      }

      /**
       * Writer the attributes.
       * @param writer the specified print writer.
       */
      @Override
      public void writeAttributes(PrintWriter writer) {
         super.writeAttributes(writer);
         writer.print(" option=\"" + option + "\"");
         writer.print(" partVal=\"" + partVal + "\"");
      }

      /**
       * Parse the attributes.
       * @param elem the specified xml element.
       */
      @Override
      public void parseAttributes(Element elem) throws Exception {
         super.parseAttributes(elem);
         option = Integer.parseInt(Tool.getAttribute(elem, "option"));
         partVal = Integer.parseInt(Tool.getAttribute(elem, "partVal"));
      }

      @Override
      public String toString() {
         StringBuilder strbuff = new StringBuilder();
         Catalog catalog = Catalog.getCatalog();
         strbuff.append(" [");

         if(negated) {
            strbuff.append(catalog.getString("is not"));
         }
         else {
            strbuff.append(catalog.getString("is"));
         }

         strbuff.append("]");
         strbuff.append(" [");
         strbuff.append(catalog.getString("equals to"));
         strbuff.append("]");
         strbuff.append(" [");
         strbuff.append(partVal);
         strbuff.append("]");

         return strbuff.toString();
      }

      private int option;
      private int partVal;
   }

   // normally, we use "1" to get "23:59:59.999" to step into the previous day,
   // but in Access, it uses float to store "datetime" type, the precision
   // is 1/300 s, which is 3.333ms, so we use 5 here to make it correct
   private static final int TIME_OFFSET = 5;
   private static final Logger LOG = LoggerFactory.getLogger(DateCondition.class);
}
