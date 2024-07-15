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
package inetsoft.report.io.helper;

import inetsoft.report.*;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.report.style.XTableStyle;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.*;

import java.awt.*;
import java.io.IOException;

/**
 * This class read table style from template file.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class TableStyleHelper extends ReportHelper {
   /**
    * Parse the tag node and return a XTableStyle created.
    * @param tag the xml node with tag name "table-style".
    * @param param this parameter should be a TableLens or null.
    */
   @Override
   public Object read(Element tag, Object param) throws Exception {
      XTableStyle style = new XTableStyle((param == null) ?
         new DefaultTableLens(1, 1) :
         (TableLens) param);

      style.setID(Tool.byteDecode(Tool.getAttribute(tag, "id")));
      style.setName(Tool.byteDecode(Tool.getAttribute(tag, "name")));
      style.setCreatedBy(Tool.getAttribute(tag, "createdBy"));
      style.setLastModifiedBy(Tool.getAttribute(tag, "modifiedBy"));
      String attrVal = Tool.getAttribute(tag, "created");

      if(attrVal != null) {
         style.setCreated(Long.parseLong(attrVal));
      }

      attrVal = Tool.getAttribute(tag, "modified");

      if(attrVal != null) {
         style.setLastModified(Long.parseLong(attrVal));
      }

      NodeList list = tag.getChildNodes();

      for(int i = 0; i < list.getLength(); i++) {
         if(!(list.item(i) instanceof Element)) {
            continue;
         }

         tag = (Element) list.item(i);
         String tname = tag.getTagName();
         XTableStyle.Specification spec = null;

         if(tname.equals("specification")) {
            spec = style.new Specification();
            String prop;

            if((prop = Tool.getAttribute(tag, "index")) != null) {
               spec.setIndex(Integer.parseInt(prop));
            }

            if((prop = Tool.getAttribute(tag, "type")) != null) {
               spec.setType(Integer.parseInt(prop));
            }

            if((prop = Tool.getAttribute(tag, "row")) != null) {
               spec.setRow(prop.equalsIgnoreCase("true"));
            }

            if((prop = Tool.getAttribute(tag, "repeat")) != null) {
               spec.setRepeat(prop.equalsIgnoreCase("true"));
            }

            if((prop = Tool.getAttribute(tag, "range")) != null) {
               String[] sa = Tool.split(prop, ',');

               if(sa.length == 2) {
                  spec.setRange(new int[] {Integer.parseInt(sa[0]),
                     Integer.parseInt(sa[1])});
               }
            }

            style.addSpecification(spec);
         }

         NamedNodeMap keys = tag.getAttributes();

         for(int n = 0; n < keys.getLength(); n++) {
            String attr = keys.item(n).getNodeName();
            String val = Tool.getAttribute(tag, attr);

            if(attr.equals("border") || attr.equals("row-border") ||
               attr.equals("col-border")) {
               int line = StyleFont.decodeLineStyle(val);

               if(line < 0) {
                  throw new IOException("Unknow border style: " + val);
               }

               if(spec != null) {
                  spec.put(attr, line);
               }
               else {
                  style.put(tname + "." + attr, line);
               }
            }
            else if(attr.equals("color") || attr.equals("bcolor") ||
               attr.equals("rcolor") || attr.equals("ccolor") ||
               attr.equals("foreground") || attr.equals("background")) {
               try {
                  Color clr = new Color(Integer.decode(val).intValue(), true);

                  if(spec != null) {
                     spec.put(attr, clr);
                  }
                  else {
                     style.put(tname + "." + attr, clr);
                  }
               }
               catch(Exception e) {
                  LOG.error("Invalid color value: " + val, e);
                  throw new IOException("Color format error: " + val);
               }
            }
            else if(attr.equals("font")) {
               Font font = StyleFont.decode(val);

               if(font == null) {
                  throw new IOException("Font format error: " + val);
               }

               if(spec != null) {
                  spec.put(attr, font);
               }
               else {
                  style.put(tname + "." + attr, font);
               }
            }
            else if(attr.equals("alignment")) {
               int align = 0;

               if(Character.isDigit(val.charAt(0))) {
                  align = Integer.decode(val).intValue();
               }
               else {
                  if(val.indexOf("H_LEFT") >= 0) {
                     align |= StyleConstants.H_LEFT;
                  }
                  else if(val.indexOf("H_CENTER") >= 0) {
                     align |= StyleConstants.H_CENTER;
                  }
                  else if(val.indexOf("H_RIGHT") >= 0) {
                     align |= StyleConstants.H_RIGHT;
                  }

                  if(val.indexOf("V_TOP") >= 0) {
                     align |= StyleConstants.V_TOP;
                  }
                  else if(val.indexOf("V_CENTER") >= 0) {
                     align |= StyleConstants.V_CENTER;
                  }
                  else if(val.indexOf("V_BOTTOM") >= 0) {
                     align |= StyleConstants.V_BOTTOM;
                  }
               }

               if(spec != null) {
                  spec.put(attr, align);
               }
               else {
                  style.put(tname + "." + attr, align);
               }
            }
         }
      }

      return style;
   }

   private static final Logger LOG =
      LoggerFactory.getLogger(TableStyleHelper.class);
}

