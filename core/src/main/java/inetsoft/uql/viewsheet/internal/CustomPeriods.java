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
package inetsoft.uql.viewsheet.internal;

import inetsoft.uql.viewsheet.DynamicValue;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.*;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * CustomPeriods, Custom periods for date comparison, it contains many date range.
 *
 * @version 13.5
 * @author InetSoft Technology Corp
 */
public class CustomPeriods implements DateComparisonPeriods {
   public CustomPeriods() {}

   public List<DatePeriod> getDatePeriods() {
      return getDatePeriods(false);
   }

   public void setDatePeriods(List<DatePeriod> datePeriods) {
      this.datePeriods = datePeriods;
   }

   /**
    * Get date period applied the inclusive.
    * @param appliedInclusive whether apply the inclusive;
    */
   public List<DatePeriod> getDatePeriods(boolean appliedInclusive) {
      if(!appliedInclusive) {
         return datePeriods;
      }

      List<DatePeriod> appliedInclusivePeriods = new ArrayList<>();

      if(datePeriods == null) {
         return appliedInclusivePeriods;
      }

      for(int i = 0; i < datePeriods.size(); i++) {
         appliedInclusivePeriods.add(getDatePeriod(i));
      }

      return appliedInclusivePeriods;
   }

   /**
    * Get date period applied the inclusive.
    * @param index period index;
    */
   private DatePeriod getDatePeriod(int index) {
      if(datePeriods == null || index >= datePeriods.size()) {
         return null;
      }

      DatePeriod datePeriod = datePeriods.get(index);

      if(datePeriod == null) {
         return null;
      }

      datePeriod = datePeriod.clone();

      if(datePeriod == null) {
         return null;
      }

      return datePeriod;
   }

   @Override
   public String getDescription() {
      return "Date ranges in " +
         datePeriods.stream()
            .map(period -> period.getDescription())
            .collect(Collectors.joining(" " + Catalog.getCatalog().getString("and") + " "));
   }

   @Override
   public void writeXML(PrintWriter writer) {
      writer.print("<datePeriods>");

      if(datePeriods != null) {
         for(DatePeriod datePeriod : datePeriods) {
            if(datePeriod == null) {
               continue;
            }

            writer.print("<datePeriod>");
            datePeriod.writeXML(writer);
            writer.print("</datePeriod>");
         }
      }

      writer.print("</datePeriods>");
   }

   @Override
   public void parseXML(Element tag) throws Exception {
      datePeriods = new ArrayList<>();
      Element datePeriodsNode = Tool.getChildNodeByTagName(tag, "datePeriods");
      NodeList nodes = datePeriodsNode == null ?
         null : Tool.getChildNodesByTagName(datePeriodsNode, "datePeriod");

      if(nodes != null) {
         for(int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);

            if(node == null) {
               continue;
            }

            DatePeriod datePeriod = new DatePeriod();
            datePeriod.parseXML((Element) node);
            datePeriods.add(datePeriod);
         }
      }
   }

   @Override
   public CustomPeriods clone() {
      try {
         CustomPeriods customPeriods = (CustomPeriods) super.clone();
         customPeriods.datePeriods = Tool.deepCloneCollection(datePeriods);

         return customPeriods;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone CustomPeriods", ex);
      }

      return null;
   }

   @Override
   public List<DynamicValue> getDynamicValues() {
      List<DynamicValue> list = new ArrayList<>();

      if(datePeriods == null) {
         return list;
      }

      for(DatePeriod datePeriod : datePeriods) {
         if(datePeriod == null) {
            continue;
         }

         list.addAll(datePeriod.getDynamicValues());
      }

      return list;
   }

   @Override
   public boolean equals(Object obj) {
      if(!(obj instanceof CustomPeriods)) {
         return false;
      }

      CustomPeriods customPeriods = (CustomPeriods) obj;

      return Tool.equals(customPeriods.datePeriods, datePeriods);
   }

   private List<DatePeriod> datePeriods;

   private static final Logger LOG = LoggerFactory.getLogger(CustomPeriods.class);
}
