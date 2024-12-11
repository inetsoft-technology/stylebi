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

import java.io.PrintWriter;
import java.text.*;
import java.util.*;
import inetsoft.uql.viewsheet.SelectionList;
import inetsoft.uql.viewsheet.SelectionValue;
import inetsoft.util.ExtendedDateFormat;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class TimeSliderSelection {

   public TimeSliderSelection() {

   }

   public void writeXML(PrintWriter writer, SelectionList list) {
      writer.print("<TimeSliderSelection class=\"" + getClass().getName() + "\">");

      printStartEndValues(writer, list);

      writer.print("<increment>");
      writer.print(getIncrement());
      writer.print("</increment>");

      printLabelFormat(writer);

      if(valueFormat != null) {
         printValueFormat(writer);
      }

      if(dateLevels != null) {
         writer.print("<dateLevels>");

         for(int i = 0; i < dateLevels.length; i++) {
            writer.print("<dateLvl>");
            writer.print(dateLevels[i] + "");
            writer.print("</dateLvl>");
         }

         writer.print("</dateLevels>");
      }
      writer.print("</TimeSliderSelection>");
   }

   public void parseXML(Element elem, SelectionList list) throws Exception {
      Element incrementNode = Tool.getChildNodeByTagName(elem, "increment");
      Element labelFmtNode = Tool.getChildNodeByTagName(elem, "labelFmt");
      Element valueFmtNode = Tool.getChildNodeByTagName(elem, "valueFmt");
      Element dateLevelsNode = Tool.getChildNodeByTagName(elem, "dateLevels");
      Element startValueParent = Tool.getChildNodeByTagName(elem, "startValue");
      Element endValueParent = Tool.getChildNodeByTagName(elem, "endValue");
      Element firstSelectedParent = Tool.getChildNodeByTagName(elem, "firstSelected");
      Element lastSelectedParent = Tool.getChildNodeByTagName(elem, "lastSelected");

      if(startValueParent != null && endValueParent != null && firstSelectedParent != null
         && lastSelectedParent != null && incrementNode != null && labelFmtNode != null) {
         Element startVNode = Tool.getChildNodeByTagName(startValueParent, "VSValue");
         Element endVNode = Tool.getChildNodeByTagName(endValueParent, "VSValue");
         Element firstSelectedNode = Tool.getChildNodeByTagName(firstSelectedParent, "VSValue");
         Element lastSelectedNode = Tool.getChildNodeByTagName(lastSelectedParent, "VSValue");

         String cls = Tool.getAttribute(startVNode, "class");
         SelectionValue startVal = (SelectionValue) Class.forName(cls).newInstance();
         startVal.parseXML(startVNode);

         cls = Tool.getAttribute(endVNode, "class");
         SelectionValue endVal = (SelectionValue) Class.forName(cls).newInstance();
         endVal.parseXML(endVNode);

         cls = Tool.getAttribute(firstSelectedNode, "class");
         SelectionValue firstSelectedVal = (SelectionValue) Class.forName(cls).newInstance();
         firstSelectedVal.parseXML(firstSelectedNode);

         cls = Tool.getAttribute(lastSelectedNode, "class");
         SelectionValue lastSelectedVal = (SelectionValue) Class.forName(cls).newInstance();
         lastSelectedVal.parseXML(lastSelectedNode);

         Format lblfmt = null;
         Format valfmt = null;

         //labelformat
         String lblfmtPattern = labelFmtNode.getTextContent();
         cls = Tool.getAttribute(labelFmtNode, "class");

         try {
            lblfmt = (Format) Class.forName(cls).newInstance();

            if(lblfmt instanceof DecimalFormat) {
               ((DecimalFormat) lblfmt).applyPattern(lblfmtPattern);
            }
            else if(lblfmt instanceof ExtendedDateFormat) {
               ((ExtendedDateFormat) lblfmt).applyPattern(lblfmtPattern);
            }
            else if(lblfmt instanceof SimpleDateFormat) {
               ((SimpleDateFormat) lblfmt).applyPattern(lblfmtPattern);
            }
            else if(lblfmt instanceof MessageFormat) {
               lblfmt = new MessageFormat(lblfmtPattern);
            }
            else {
               lblfmt.parseObject(lblfmtPattern);
            }

            labelFormat = lblfmt;
         }
         catch(Exception e) {
            LOG.warn("label format not processed correctly, " + e);
         }

         //valueformat
         if(valueFmtNode != null) {
            String valfmtPattern = valueFmtNode.getTextContent();
            cls = Tool.getAttribute(valueFmtNode, "class");

            try {
               valfmt = (Format) Class.forName(cls).newInstance();

               if(valfmt instanceof DecimalFormat) {
                  ((DecimalFormat) valfmt).applyPattern(valfmtPattern);
               }
               else if(valfmt instanceof ExtendedDateFormat) {
                  ((ExtendedDateFormat) valfmt).applyPattern(valfmtPattern);
               }
               else if(valfmt instanceof SimpleDateFormat) {
                  ((SimpleDateFormat) valfmt).applyPattern(valfmtPattern);
               }
               else if(valfmt instanceof MessageFormat) {
                  valfmt = new MessageFormat(valfmtPattern);
               }
               else {
                  valfmt.parseObject(valfmtPattern);
               }

               valueFormat = valfmt;

            }
            catch(Exception e) {
               LOG.warn("label format not processed correctly, " + e);
            }
         }

         //datelevels
         int[] dateLvls = null;

         if(dateLevelsNode != null) {
            NodeList dateLvlNodes = Tool.getChildNodesByTagName(dateLevelsNode, "dateLvl");
            dateLvls = new int[dateLvlNodes.getLength()];

            for(int i = 0; i < dateLvlNodes.getLength(); i++) {
               Element dnode = (Element) dateLvlNodes.item(i);
               String lvlStr = dnode.getTextContent();

               try {
                  dateLvls[i] = Integer.parseInt(lvlStr);
               }
               catch(Exception e) {
                  LOG.warn("error parsing date levels, " + e);
               }
            }
         }

         this.increment = Double.parseDouble(incrementNode.getTextContent());
         this.dateLevels = dateLvls;

         List<SelectionValue> rList = repopulateList(startVal, endVal, firstSelectedVal, lastSelectedVal, lblfmt, valfmt, dateLvls);

         if(rList != null) {
            list.setSelectionValues(rList.toArray(new SelectionValue[0]));
         }
      }
   }

   private List<SelectionValue> repopulateList(SelectionValue startVal, SelectionValue endVal,
                                               SelectionValue firstSelected, SelectionValue lastSelected,
                                               Format labelfmt, Format valuefmt, int[] dateLevels){
      //repopulate as date
      if(dateLevels != null) {
         Date firstSelectedDate = null;
         Date lastSelectedDate = null;
         Date startDate = null;
         Date endDate = null;

         try {
            firstSelectedDate = (Date) valuefmt.parseObject(firstSelected.getValue());
            lastSelectedDate = (Date) valuefmt.parseObject(lastSelected.getValue());
            startDate = (Date) valuefmt.parseObject(startVal.getValue());
            endDate = (Date) valuefmt.parseObject(endVal.getValue());
         }
         catch(Exception e) {
            LOG.warn("dates not parsed correctly, selectionList readxml may fail:" + e);
         }

         return populateDateList(startDate, endDate, firstSelectedDate, lastSelectedDate, labelfmt, valuefmt, dateLevels);
      }
      //repopulate as number
      else if(isNumber(firstSelected.getValue()) && isNumber(lastSelected.getValue())) {
         return populateNumberList(Double.parseDouble(startVal.getValue()),Double.parseDouble(endVal.getValue()),
                                   Double.parseDouble(firstSelected.getValue()), Double.parseDouble(lastSelected.getValue()), labelfmt);
      }

      return null;
   }

   private ArrayList<SelectionValue> populateNumberList(double start, double end,
                                                        double firstSelected, double lastSelected, Format fmt){
      ArrayList<SelectionValue> repopList = new ArrayList<>();

      for(double s = start; s <= end; s += increment) {
         String val = Tool.toString(s);
         String label = (fmt != null) ? fmt.format(s) : Tool.toString(s);

         SelectionValue sval = new SelectionValue(label, val);
         sval.setLevel(0);
         sval.setSelected(true);
         repopList.add(sval);
      }

      return repopList;
   }

   private ArrayList<SelectionValue> populateDateList(Date startDate, Date endDate, Date firstSelected,
                                                      Date lastSelected, Format labelfmt, Format valuefmt, int[] datelevels) {
      ArrayList<SelectionValue> popList = new ArrayList<>();
      Calendar calendar = new GregorianCalendar();
      Calendar firstSelectedCal = new GregorianCalendar();
      Calendar lastSelectedCal = new GregorianCalendar();
      Calendar maxcal = new GregorianCalendar();
      Calendar omaxcal = null;
      Calendar currcal = null;
      int counter = 0;
      int pos = -1;

      maxcal.setTime(endDate);
      firstSelectedCal.setTime(firstSelected);
      lastSelectedCal.setTime(lastSelected);
      calendar.setTime(startDate);

      while(compareLevel(calendar, maxcal, datelevels) <= 0) {

         if(pos == -1 && currcal != null) {
            if(compareLevel(calendar, currcal, datelevels) == 0) {
               pos = counter;
            }
         }

         Object obj = calendar.getTime();
         String val = valuefmt.format(obj);
         String label = null;

         try {
            label = labelfmt.format(valuefmt.parseObject(val));
         }
         catch(ParseException ex) {
            label = labelfmt.format(obj);
         }

         SelectionValue sval = new SelectionValue(label, val);
         sval.setLevel(0);

         //if date between selected
         if(compareLevel(firstSelectedCal, calendar, datelevels) <= 0 &&
            compareLevel(calendar, lastSelectedCal, datelevels) >= 0) {
            sval.setSelected(true);
         }

         popList.add(sval);
         calendar.add(datelevels[0], (int) increment);
         counter++;
      }

      if(omaxcal != null) {
         calendar.add(datelevels[0], - (int) increment);

         // make the max in range always >= the max in the data
         if(compareLevel(calendar, omaxcal, datelevels) < 0) {
            calendar.add(datelevels[0], (int) increment);
            Object obj = calendar.getTime();
            String label = labelfmt.format(obj);
            String val = valuefmt.format(obj);
            SelectionValue sval = new SelectionValue(label, val);
            sval.setLevel(0);

            //if date between selected
            if(compareLevel(firstSelectedCal, omaxcal, datelevels) <= 0 &&
               compareLevel(omaxcal, lastSelectedCal, datelevels) >= 0) {
               sval.setSelected(true);
            }

            popList.add(sval);
         }
      }

      return popList;
   }

   /**
    * Compare two dates at the specified levels.
    * @return -1 if cal is less, 0 if equal, or 1 if greater than cal2.
    */
   private int compareLevel(Calendar cal1, Calendar cal2,
                            int... datelevels) {
      // date levels are arranged from low to high
      for(int i = datelevels.length - 1; i >= 0; i--) {
         int level = datelevels[i];
         int rc = cal1.get(level) - cal2.get(level);

         if(rc != 0) {
            return rc;
         }
      }

      return 0;
   }

   private boolean isNumber(String str) {
      try {
         double num = Double.parseDouble(str);
         return true;
      }
      catch(Exception e) {
         return false;
      }
   }

   private void printStartEndValues(PrintWriter writer, SelectionList selectionList) {
      List<SelectionValue> list = Arrays.asList(selectionList.getSelectionValues());
      SelectionValue startV = !list.isEmpty() ? list.get(0) : null;
      SelectionValue endV = !list.isEmpty() ? list.get(list.size()-1) : null;
      SelectionValue firstSelectedV = null;
      SelectionValue lastSelectedV = null;

      boolean firstFound = false;
      for(int i=0; i<list.size();i++) {
         if(!firstFound) {
            if(list.get(i).isSelected()) {
               firstSelectedV = list.get(i);
               firstFound = true;
            }
         }
         else {
            if(!list.get(i).isSelected()) {
               lastSelectedV = list.get(i - 1);
               break;
            }
         }
      }

      if(firstFound && lastSelectedV == null) {
         //fill with last element if selected to the end
         lastSelectedV = list.get(list.size()-1);
      }

      if(startV != null && endV != null) {
         writer.print("<startValue class=\"" + startV.getClass().getName() + "\">");
         startV.writeXML(writer, Integer.MAX_VALUE, selectionList);
         writer.print("</startValue>");

         writer.print("<endValue class=\"" + endV.getClass().getName() + "\">");
         endV.writeXML(writer, Integer.MAX_VALUE, selectionList);
         writer.print("</endValue>");
      }

      if(firstSelectedV != null && lastSelectedV != null) {
         writer.print("<firstSelected class=\"" + firstSelectedV.getClass().getName() + "\">");
         firstSelectedV.writeXML(writer, Integer.MAX_VALUE, selectionList);
         writer.print("</firstSelected>");

         writer.print("<lastSelected class=\"" + lastSelectedV.getClass().getName() + "\">");
         lastSelectedV.writeXML(writer, Integer.MAX_VALUE, selectionList);
         writer.print("</lastSelected>");
      }
   }

   private void printValueFormat(PrintWriter writer) {
      writer.print("<valueFmt class=\"" + valueFormat.getClass().getName() + "\">");

      if(valueFormat instanceof DecimalFormat) {
         writer.print(((DecimalFormat) valueFormat).toPattern());
      }
      else if(valueFormat instanceof ExtendedDateFormat) {
         writer.print(((ExtendedDateFormat) valueFormat).toPattern());
      }
      else if(valueFormat instanceof SimpleDateFormat) {
         writer.print(((SimpleDateFormat) valueFormat).toPattern());
      }
      else if(valueFormat instanceof MessageFormat) {
         writer.print(((MessageFormat) valueFormat).toPattern());
      }
      else {
         writer.print(valueFormat.toString());
         LOG.warn("unknown value format when writing xml, may fail: "+valueFormat.getClass().getName());
      }

      writer.print("</valueFmt>");
   }

   private void printLabelFormat(PrintWriter writer) {
      writer.print("<labelFmt class=\"" + labelFormat.getClass().getName() + "\">");

      if(labelFormat instanceof DecimalFormat) {
         writer.print(((DecimalFormat) labelFormat).toPattern());
      }
      else if(labelFormat instanceof ExtendedDateFormat) {
         writer.print(((ExtendedDateFormat) labelFormat).toPattern());
      }
      else if(labelFormat instanceof SimpleDateFormat) {
         writer.print(((SimpleDateFormat) labelFormat).toPattern());
      }
      else if(labelFormat instanceof MessageFormat) {
         writer.print(((MessageFormat) labelFormat).toPattern());
      }
      else {
         writer.print(labelFormat.toString());
         LOG.warn("unknown label format when writing xml, may fail: "+labelFormat.getClass().getName());
      }

      writer.print("</labelFmt>");
   }

   public Format getLabelFormat() {
      return labelFormat;
   }

   public void setLabelFormat(Format labelFormat) {
      this.labelFormat = labelFormat;
   }

   public Format getValueFormat() {
      return valueFormat;
   }

   public void setValueFormat(Format valueFormat) {
      this.valueFormat = valueFormat;
   }

   public double getIncrement() {
      return increment;
   }

   public void setIncrement(double increment) {
      this.increment = increment;
   }

   public int[] getDateLevels() {
      return dateLevels;
   }

   public void setDateLevels(int[] dateLevels) {
      this.dateLevels = dateLevels;
   }

   private Format labelFormat;
   private Format valueFormat;
   private double increment;
   private int[] dateLevels;

   private static final Logger LOG = LoggerFactory.getLogger(TimeSliderSelection.class);

}
