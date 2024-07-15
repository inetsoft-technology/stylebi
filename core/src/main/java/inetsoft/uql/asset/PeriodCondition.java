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
package inetsoft.uql.asset;

import inetsoft.uql.Condition;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.util.*;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.sql.Timestamp;
import java.util.*;

/**
 * Period condition as a <tt>DateCondition</tt> evaluates <tt>Date</tt> objects
 * in a period.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public class PeriodCondition extends DateCondition {
   /**
    * Constructor.
    */
   public PeriodCondition() {
      super();
   }

   /**
    * Get the date from.
    * @return the date from.
    */
   public Date getFrom() {
      return new Date(from - ZONE.getRawOffset());
   }

   /**
    * Set the date from.
    * @param from the specified date from.
    */
   public void setFrom(Date from) {
      from = from == null ? new Date() : from;
      this.from = from.getTime() + ZONE.getRawOffset();
      this.from = this.from - (this.from % ONE_DAY);
   }

   /**
    * Get the date to.
    * @return the date to.
    */
   public Date getTo() {
      return new Date(to - ZONE.getRawOffset());
   }

   /**
    * Set the date to.
    * @param to the specified date to.
    */
   public void setTo(Date to) {
      to = to == null ? new Date() : to;
      this.to = to.getTime() + ZONE.getRawOffset();
      this.to = this.to - (this.to % ONE_DAY);
   }

   /**
    * Check if the condition is a valid condition.
    * @return <tt>true</tt> if is valid, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isValid() {
      return to > from;
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

      long date = ((Date) value).getTime() + ZONE.getRawOffset();
      boolean rc = date >= from && date <= to;

      return isNegated() ? !rc : rc;
   }

   /**
    * Convert this condition to sql mergeable condition.
    */
   @Override
   public Condition toSqlCondition(boolean isTimestamp) {
      Calendar cal = CoreTool.calendar.get();
      cal.setTime(getFrom());
      Date date1 = isTimestamp ? new Timestamp(cal.getTimeInMillis()) : new java.sql.Date(cal.getTimeInMillis());
      cal.setTime(getTo());
      Date date2 = isTimestamp ? new Timestamp(cal.getTimeInMillis()) : new java.sql.Date(cal.getTimeInMillis());
      return createSqlCondition(date1, date2);
   }

   /**
    * Write the contents.
    * @param writer the specified print writer.
    */
   @Override
   public void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      // write desc for flash side
      String desc = getLabel();
      writer.print("<description>");
      writer.print("<![CDATA[" + desc + "]]>");
      writer.println("</description>");
   }

   /**
    * Parse the contents.
    * @param elem the specified xml element.
    */
   @Override
   public void parseContents(Element elem) throws Exception {
      super.parseContents(elem);
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
      from = Long.parseLong(Tool.getAttribute(elem, "from"));
      to = Long.parseLong(Tool.getAttribute(elem, "to"));
   }

   /**
    * Check if equals another object.
    * @return <tt>true</tt>if yes, <tt>false</tt> otherwise.
    */
   public boolean equals(Object obj) {
      if(!super.equals(obj)) {
         return false;
      }

      if(!(obj instanceof PeriodCondition)) {
         return false;
      }

      PeriodCondition condition = (PeriodCondition) obj;
      return from == condition.from && to == condition.to;
   }

   @Override
   public int hashCode() {
      return Objects.hash(super.hashCode(), from, to);
   }

   /**
    * Get the label.
    * @return the label of the date condition.
    */
   @Override
   public String getLabel() {
      Catalog catalog = Catalog.getCatalog();
      StringBuilder sb = new StringBuilder();
      sb.append(catalog.getString("From"));
      sb.append(" ");
      sb.append(AssetUtil.getDateFormat().format(getFrom()));
      sb.append(" ");
      sb.append(catalog.getString("To"));
      sb.append(" ");
      sb.append(AssetUtil.getDateFormat().format(getTo()));

      return sb.toString();
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

   private long from; // from date
   private long to; // to date, not included
}
