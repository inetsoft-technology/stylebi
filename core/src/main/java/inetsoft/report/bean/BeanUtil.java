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
package inetsoft.report.bean;

import inetsoft.report.*;
import inetsoft.report.internal.*;
import inetsoft.uql.XConstants;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.awt.*;
import java.io.PrintWriter;
import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Hashtable;

/**
 * Utility methods for bean element.
 *
 * @version 6.1, 7/20/2004
 * @author InetSoft Technology Corp
 */
public class BeanUtil {
   /**
    * Write tags for a property value.
    */
   public static void writePropertyValue(PrintWriter writer, String tag,
                                         String prop, Object value) {
      writer.print("<" + tag + " Name=\"" + Tool.escape(prop) + "\" ");

      // an object array might be a string array, here we try to narrow it
      if(value instanceof Object[]) {
         Object[] arr = (Object[]) value;
         boolean isstr = true;

         for(int i = 0; i < arr.length; i++) {
            if(arr[i] != null && !(arr[i] instanceof String)) {
               isstr = false;
               break;
            }
         }

         if(isstr) {
            String[] arr2 = new String[arr.length];

            for(int i = 0; i < arr.length; i++) {
               arr2[i] = (String) arr[i];
            }

            value = arr2;
         }
      }

      if(value instanceof Color) {
         Color obj = (Color) value;

         writer.print("Type=\"Color\" Value=\"" + obj.getRGB() + "\"/>");
      }
      else if(value instanceof Dimension) {
         Dimension obj = (Dimension) value;

         writer.print("Type=\"Dimension\" Value=\"" + obj.width + "x" +
            obj.height + "\"/>");
      }
      else if(value instanceof Font) {
         Font obj = (Font) value;

         writer.print("Type=\"Font\" Value=\"" +
                      Tool.escape(StyleFont.toString(obj)) + "\"/>");
      }
      else if(value instanceof Format) {
         Format obj = (Format) value;

         writer.print("Type=\"Format\"");
         writer.print(" FormatType=\"" +
                      Tool.escape(obj instanceof SimpleDateFormat ?
                      XConstants.DATE_FORMAT :
                      Common.getFormatType(obj)) + "\"");
         writer.print(" Format=\"" + Tool.escape(Common.getFormatPattern(obj)) +
                      "\"/>");
      }
      else if(value instanceof MetaImage) {
         ImageLocation iloc = ((MetaImage) value).getImageLocation();

         if(iloc != null && !iloc.isNone()) {
            writer.print("Type=\"Image\" PathType=\"" + iloc.getPathType() +
               "\">");

            // save the information of Path.
            writer.print("<![CDATA[");
            writer.print(iloc.getPath());
            writer.print("]]");
         }

         writer.println("></" + tag + ">");
      }
      else if(value instanceof Insets) {
         Insets obj = (Insets) value;

         writer.print("Type=\"Insets\" Value=\"" + obj.top + "," + obj.left +
            "," + obj.bottom + "," + obj.right + "\"/>");
      }
      else if(value instanceof Position) {
         Position obj = (Position) value;

         writer.print("Type=\"Position\" Value=\"" + obj.x + "," + obj.y +
            "\"/>");
      }
      else if(value instanceof Size) {
         Size obj = (Size) value;

         writer.print("Type=\"Size\" Value=\"" + obj.width + "x" + obj.height +
            "\"/>");
      }
      else if(value instanceof Hyperlink) {
         writer.print("Type=\"Hyperlink\" Value=\"" +
                      Tool.escape(value.toString()) + "\">");

         ((Hyperlink) value).writeXML(writer);

         writer.println("</" + tag + ">");
      }
      else if(value instanceof Boolean) {
         writer.print("Type=\"Boolean\" Value=\"" + value + "\"/>");
      }
      else if(value instanceof Double) {
         writer.print("Type=\"Double\" Value=\"" + value + "\"/>");
      }
      else if(value instanceof Float) {
         writer.print("Type=\"Float\" Value=\"" + value + "\"/>");
      }
      else if(value instanceof Integer) {
         writer.print("Type=\"Integer\" Value=\"" + value + "\"/>");
      }
      else if(value instanceof Long) {
         writer.print("Type=\"Long\" Value=\"" + value + "\"/>");
      }
      else if(value instanceof Date) {
         long time = ((Date) value).getTime();
         writer.print("Type=\"Date\" Value=\"" + time + "\"/>");
      }
      else if(value instanceof double[]) {
         double[] arr = (double[]) value;

         writer.print("Type=\"double[]\" Value=\"");

         for(int i = 0; i < arr.length; i++) {
            if(i > 0) {
               writer.print(",");
            }

            writer.print(arr[i]);
         }

         writer.print("\"/>");
      }
      else if(value instanceof String[]) {
         String[] arr = (String[]) value;

         writer.print("Type=\"String[]\">");

         for(int i = 0; i < arr.length; i++) {
            writer.print("<Value><![CDATA[" + Tool.escape(arr[i]) +
               "]]></Value>");
         }

         writer.print("</" + tag + ">");
      }
      else if(value == null) {
         writer.print("Type=\"Null\"></" + tag + ">");
      }
      else {
         writer.print("Type=\"String\"><![CDATA[" + value +
            "]]></" + tag + ">");
      }
   }

   /**
    * Read in the name value pair of property value.
    * @param node value node.
    * @return name/value pair where arr[0] is name and arr[1] is value.
    */
   public static Object[] readPropertyValue(Element node) {
      String name = Tool.getAttribute(node, "Name");
      String type = Tool.getAttribute(node, "Type");
      Object value = null;

      if(type == null) {
         type = "String";
      }

      if(type.equals("Color")) {
         String vstr = Tool.getAttribute(node, "Value");

         value = new Color(Integer.parseInt(vstr));
      }
      else if(type.equals("Dimension")) {
         String vstr = Tool.getAttribute(node, "Value");
         String[] sa = Tool.split(vstr, 'x');

         value = new Dimension(Integer.parseInt(sa[0]),
                               Integer.parseInt(sa[1]));
      }
      else if(type.equals("Font")) {
         String vstr = Tool.getAttribute(node, "Value");

         value = StyleFont.decode(vstr);
      }
      else if(type.equals("Format")) {
         String tstr;

         if((tstr = Tool.getAttribute(node, "FormatType")) == null) {
            tstr = Common.DECIMALFORMAT;
         }

         value = Common.getFormat(tstr, Tool.getAttribute(node, "Format"));
      }
      else if(type.equals("Image")) {
         ImageLocation iloc = new ImageLocation(".");
         String pathValue = Tool.getValue(node);

         if(pathValue != null) {
            iloc.setPath(pathValue);
         }

         String prop;

         if((prop = Tool.getAttribute(node, "PathType")) != null) {
            iloc.setPathType(Integer.parseInt(prop));
         }

         try {
            value = new MetaImage(iloc);
         }
         catch(Exception ex) {
            LOG.warn("Failed to create meta-image for location: " + iloc, ex);
         }
      }
      else if(type.equals("Insets")) {
         String vstr = Tool.getAttribute(node, "Value");
         String[] sa = Tool.split(vstr, ',');

         value = new Insets(Integer.parseInt(sa[0]),
                            Integer.parseInt(sa[1]), Integer.parseInt(sa[2]),
                            Integer.parseInt(sa[3]));
      }
      else if(type.equals("Position")) {
         String vstr = Tool.getAttribute(node, "Value");
         String[] sa = Tool.split(vstr, ',');

         value = new Position(Float.valueOf(sa[0]).floatValue(),
                              Float.valueOf(sa[1]).floatValue());
      }
      else if(type.equals("Size")) {
         String vstr = Tool.getAttribute(node, "Value");
         String[] sa = Tool.split(vstr, 'x');

         value = new Size(Float.valueOf(sa[0]).floatValue(),
                          Float.valueOf(sa[1]).floatValue());
      }
      else if(type.equals("TOC")) {
         String vstr = null;

         try {
            vstr = Tool.getAttribute(node, "Value");
            value = Class.forName(vstr).newInstance();
         }
         catch(Exception ex) {
            LOG.warn("Failed to instantiate TOC class: " + vstr, ex);
         }
      }
      else if(type.equals("Hyperlink")) {
         String vstr = Tool.getAttribute(node, "Value");

         if(vstr != null && vstr.length() > 0) {
            value = new Hyperlink(vstr);

            try {
               Element htag = Tool.getChildNodeByTagName(node, "Hyperlink");

               if(htag != null) {
                  ((Hyperlink) value).parseXML(htag);
               }
            }
            catch(Exception ex) {
               LOG.warn("Failed to parse hyperlink XML", ex);
            }
         }
      }
      else if(type.equals("Boolean")) {
         String vstr = Tool.getAttribute(node, "Value");

         value = Boolean.valueOf(vstr);
      }
      else if(type.equals("Double")) {
         String vstr = Tool.getAttribute(node, "Value");

         value = Double.valueOf(vstr);
      }
      else if(type.equals("Float")) {
         String vstr = Tool.getAttribute(node, "Value");

         value = Float.valueOf(vstr);
      }
      else if(type.equals("Integer")) {
         String vstr = Tool.getAttribute(node, "Value");

         value = Integer.valueOf(vstr);
      }
      else if(type.equals("Long")) {
         String vstr = Tool.getAttribute(node, "Value");

         value = Long.valueOf(vstr);
      }
      else if(type.equals("Date")) {
         String vstr = Tool.getAttribute(node, "Value");

         value = new Date(Long.parseLong(vstr));
      }
      else if(type.equals("double[]")) {
         String vstr = Tool.getAttribute(node, "Value");

         String[] arr = Tool.split(vstr, ',');
         double[] darr = new double[arr.length];

         for(int j = 0; j < arr.length; j++) {
            darr[j] = Double.valueOf(arr[j]).doubleValue();
         }

         value = darr;
      }
      else if(type.equals("String[]")) {
         NodeList list = Tool.getChildNodesByTagName(node, "Value");
         String[] arr = new String[list.getLength()];

         for(int i = 0; i < list.getLength(); i++) {
            arr[i] = Tool.getValue((Element) list.item(i));
         }

         value = arr;
      }
      else if(type.equals("Null")) {
         value = null;
      }
      else {
         value = Tool.getAttribute(node, "Value");

         // as CDATA
         if(value == null) {
            value = Tool.getValue(node);
         }

         if(value != null && value.equals("null")) {
            value = null;
         }
      }

      return new Object[] {name, value};
   }

   /**
    * Convert normal bean property name to a human readable word.
    */
   public static String getBeanDisplayName(String name) {
      if(name == null || name.length() < 2) {
         return name;
      }

      String displayName = null;
      boolean firstUpper = Character.isUpperCase(name.charAt(0));
      boolean upperCase = firstUpper && Character.isUpperCase(name.charAt(1));

      // already capitalized or two initial upper cases, don't convert
      if(firstUpper || upperCase) {
         displayName = name;
      }
      else {
         displayName =
            Character.toUpperCase(name.charAt(0)) + name.substring(1);
      }

      return separateWords(displayName);
   }

   /**
    * If there are more than one words in the name, separate them to display.
    */
   private static String separateWords(String name) {
      for(int i = 0; i < name.length(); i++) {
         if(i > 0 && Character.isUpperCase(name.charAt(i))) {
            name = name.substring(0, i) + " " + name.substring(i);
            i++;
         }
      }

      return name;
   }

   private static final Logger LOG =
      LoggerFactory.getLogger(BeanUtil.class);
}
