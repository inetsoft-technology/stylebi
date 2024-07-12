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

import inetsoft.uql.viewsheet.DynamicValue;
import inetsoft.util.Tool;
import inetsoft.util.XMLSerializable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.util.*;

/**
 * DatePeriod, default a date range, start and end can be variable, expression and a special date.
 *
 * @version 13.5
 * @author InetSoft Technology Corp
 */
public class DatePeriod implements XMLSerializable, Cloneable {
   public DatePeriod() {
   }

   public DatePeriod(Date start, Date end) {
      setStart(start);
      setEnd(end);
   }

   public String getStartValue() {
      return start.getDValue();
   }

   public Date getStart() {
      return Tool.getDateData(start.getRValue());
   }

   public void setStart(Date start) {
      this.start.setRValue(start);
   }

   public void setStartValue(String start) {
      this.start.setDValue(start);
   }

   public Date getEnd() {
      return Tool.getDateData(end.getRValue());
   }

   public String getEndValue() {
      return end.getDValue();
   }

   public void setEnd(Date end) {
      this.end.setRValue(end);
   }

   public void setEndValue(String end) {
      this.end.setDValue(end);
   }

   public String getDescription() {
      return getStart() + " to " + getEnd();
   }

   @Override
   public void writeXML(PrintWriter writer) {
      writer.print("<startDate>");
      writer.print("<![CDATA[" + start.getDValue() + "]]>");
      writer.print("</startDate>");
      writer.print("<endDate>");
      writer.print("<![CDATA[" + end.getDValue() + "]]>");
      writer.print("</endDate>");
   }

   @Override
   public void parseXML(Element tag) throws Exception {
      String value = Tool.getChildValueByTagName(tag, "startDate");

      if(!Tool.isEmptyString(value)) {
         start.setDValue(value);
      }

      value = Tool.getChildValueByTagName(tag, "endDate");

      if(!Tool.isEmptyString(value)) {
         end.setDValue(value);
      }
   }

   @Override
   public boolean equals(Object obj) {
      if(!(obj instanceof DatePeriod)) {
         return false;
      }

      DatePeriod datePeriod = (DatePeriod) obj;

      return Tool.equals(start, datePeriod.start) &&
         Tool.equals(end, datePeriod.end);
   }

   @Override
   public DatePeriod clone() {
      try {
         DatePeriod clone = (DatePeriod) super.clone();
         clone.start = (DynamicValue) Tool.clone(start);
         clone.end = (DynamicValue) Tool.clone(end);

         return clone;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone DatePeriod", ex);
      }

      return null;
   }

   public List<DynamicValue> getDynamicValues() {
      List<DynamicValue> list = new ArrayList<>();
      list.add(start);
      list.add(end);

      return list;
   }

   private DynamicValue start = new DynamicValue();
   private DynamicValue end = new DynamicValue();

   private static final Logger LOG = LoggerFactory.getLogger(DatePeriod.class);
}
