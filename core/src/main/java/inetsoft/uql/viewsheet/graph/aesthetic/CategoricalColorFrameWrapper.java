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
package inetsoft.uql.viewsheet.graph.aesthetic;

import inetsoft.graph.aesthetic.*;
import inetsoft.graph.data.DataSet;
import inetsoft.graph.internal.GTool;
import inetsoft.report.composition.graph.GraphUtil;
import inetsoft.uql.util.XUtil;
import inetsoft.util.Tool;
import org.apache.commons.lang.StringEscapeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.awt.*;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.*;

/**
 * This class defines a color frame for categorical values.
 *
 * @version 10.0
 * @author InetSoft Technology
 */
public class CategoricalColorFrameWrapper extends ColorFrameWrapper {
   /**
    * Create the corresponding frame.
    */
   @Override
   protected VisualFrame createVisualFrame() {
      return new CategoricalColorFrame() {
         @Override
         public Color getColor(DataSet data, String col, int row) {
            col = getField() == null ? GraphUtil.getOriginalCol(col) : col;
            return super.getColor(data, col, row);
         }

         @Override
         public boolean isStatic(Object val) {
            boolean isStatic = super.isStatic(val);

            if(isStatic) {
               return true;
            }

            // Dimension may be null, this will allow it to be mapped to a color
            if(val == null) {
               isStatic = super.isStatic("null");

               if(isStatic) {
                  setColor(null, getColor("null"));
               }
            }
            else if(dateFormat != null) {
               SimpleDateFormat fmt =
                  XUtil.getDefaultDateFormat(dateFormat, Tool.TIME);

               if(fmt != null) {
                  try {
                     String newVal = fmt.format(val);
                     isStatic = super.isStatic(newVal);

                     if(!isStatic) {
                        fmt = XUtil.getDefaultDateFormat(dateFormat,
                                                         Tool.TIME_INSTANT);
                        if(fmt != null) {
                           newVal = fmt.format(val);
                           isStatic = super.isStatic(newVal);
                        }
                     }

                     if(isStatic) {
                        setColor(val, getColor(newVal));
                     }
                  }
                  catch(IllegalArgumentException ex) {
                     LOG.warn("Failed to parse date: " + val, ex);
                  }
               }
            }

            return isStatic;
         }
      };
   }

   /**
    * Default constructor.
    */
   public CategoricalColorFrameWrapper() {
   }

   @Override
   public VisualFrame getVisualFrame() {
      if(colorValueFrame) {
         if(frame2 == null) {
            frame2 = new ColorValueColorFrame();
            frame2.setDefaultColor(CategoricalColorFrame.COLOR_PALETTE[0]);
         }

         return frame2;
      }

      return super.getVisualFrame();
   }

   /**
    * Get the color at the specified index.
    */
   public Color getColor(int index) {
      CategoricalColorFrame frame = (CategoricalColorFrame) this.frame;

      if(index < frame.getColorCount()) {
         return frame.getColor(index);
      }

      return Color.BLACK;
   }

   /**
    * Set the color at the specified index.
    */
   public void setColor(int index, Color color) {
      CategoricalColorFrame frame = (CategoricalColorFrame) this.frame;

      if(index < frame.getColorCount()) {
         setChanged(isChanged() || frame.getColor(index) != color);
         frame.setColor(index, color);
      }
   }

   /**
    * Set the user color at the specified index.
    */
   public void setUserColor(int index, Color color) {
      CategoricalColorFrame frame = (CategoricalColorFrame) this.frame;

      if(index < frame.getColorCount()) {
         setChanged(true);
         frame.setUserColor(index, color);
      }
   }

   /**
    * Clear user defined colors
    */
   public void clearUserColors() {
      CategoricalColorFrame frame = (CategoricalColorFrame) this.frame;
      frame.setUserColors(new HashMap<>());
      setChanged(true);
   }

   public Map<Integer, Color> getUserColors() {
      CategoricalColorFrame frame = (CategoricalColorFrame) this.frame;
      return frame.getUserColors();
   }

   /**
    * Write contents.
    * @param writer the specified writer.
    */
   @Override
   protected void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      CategoricalColorFrame frame = (CategoricalColorFrame) this.frame;

      writer.println("<colors>");

      for(int i = 0; i < frame.getColorCount(); i++) {
         Color col = frame.getDefaultColor(i);
         writer.print("<color index=\"" + i + "\" ");

         if(col != null) {
            // make sure we only include RGB so comparison of the color
            // in actionscript equals() method is correct
            writer.print("value=\"" + (col.getRGB() & 0xFFFFFF) + "\" ");
         }

         writer.println("/>");
      }

      writer.println("</colors>");

      writer.println("<cssColors>");
      Map<Integer, Color> cssColors = frame.getCSSColors();

      for(int i = 0; i < frame.getColorCount(); i++) {
         Color col = cssColors.get(i);

         if(col != null) {
            writer.print("<color index=\"" + i + "\" ");
            writer.print("value=\"" + (col.getRGB() & 0xFFFFFF) + "\" ");
            writer.println("/>");
         }
      }

      writer.println("</cssColors>");

      writer.println("<userColors>");
      Map<Integer, Color> userColors = frame.getUserColors();

      for(int i = 0; i < frame.getColorCount(); i++) {
         Color col = userColors.get(i);

         if(col != null) {
            writer.print("<color index=\"" + i + "\" ");
            writer.print("value=\"" + (col.getRGB() & 0xFFFFFF) + "\" ");
            writer.println("/>");
         }
      }

      writer.println("</userColors>");

      writer.println("<dimensionColors>");

      for(Object dim : frame.getStaticValues()) {
         Color col = frame.getColor(dim);

         if(col != null) {
            dim = (dim == null) ? GTool.toString(dim)
               : StringEscapeUtils.escapeXml((String) dim);

            writer.print("<color dim=\"" + dim + "\" ");
            writer.print("value=\"" + (col.getRGB() & 0xFFFFFF) + "\" ");
            writer.println("/>");
         }
      }

      writer.println("</dimensionColors>");

      if(dateFormat != null) {
         writer.println("<dateFormat value=\"" + dateFormat + "\" " + "/>");
      }

      writer.println("<useGlobal value=\"" + frame.isUseGlobal() + "\" " + "/>");
      writer.println("<shareColors value=\"" + frame.isShareColors() + "\" " + "/>");
      writer.println("<colorValueFrame value=\"" + colorValueFrame + "\" " + "/>");
   }

   /**
    * Parse contents.
    * @param tag the specified xml element.
    */
   @Override
   protected void parseContents(Element tag) throws Exception {
      super.parseContents(tag);

      Element clrsNode = Tool.getChildNodeByTagName(tag, "useGlobal");
      CategoricalColorFrame frame = (CategoricalColorFrame) this.frame;

      if(clrsNode != null) {
         final String value = clrsNode.getAttribute("value");
         frame.setUseGlobal("true".equals(value));
      }

      clrsNode = Tool.getChildNodeByTagName(tag, "shareColors");

      if(clrsNode != null) {
         final String value = clrsNode.getAttribute("value");
         frame.setShareColors("true".equals(value));
      }

      if((clrsNode = Tool.getChildNodeByTagName(tag, "colorValueFrame")) != null) {
         colorValueFrame = "true".equals(clrsNode.getAttribute("value"));
      }

      clrsNode = Tool.getChildNodeByTagName(tag, "colors");

      if(clrsNode != null) {
         NodeList list = Tool.getChildNodesByTagName(clrsNode, "color");
         List<Color> colors = new ArrayList<>();
         String val;

         for(int i = 0; i < list.getLength(); i++) {
            Element node = (Element) list.item(i);
            Color color;

            if((val = node.getAttribute("value")) == null || "".equals(val)) {
               color = null;
            }
            else {
               color = new Color(Integer.decode(val));
            }

            String attr = node.getAttribute("index");

            if(attr == null || attr.equals("")) {
               colors.add(color);
            }
            else {
               int index = Integer.parseInt(attr);
               frame.setColor(index, color);
            }
         }

         // if from color palette, replace the entire color array
         if(colors.size() > 0) {
            frame.init(new Object[0], colors.toArray(new Color[0]));
         }
      }

      clrsNode = Tool.getChildNodeByTagName(tag, "cssColors");

      if(clrsNode != null) {
         NodeList list = Tool.getChildNodesByTagName(clrsNode, "color");
         Map<Integer, Color> cssColors = new HashMap<>();

         for(int i = 0; i < list.getLength(); i++) {
            Element node = (Element) list.item(i);
            String val = node.getAttribute("value");
            String attr = node.getAttribute("index");

            if(val != null && !val.equals("") && attr != null &&
               !attr.equals(""))
            {
               Color color = new Color(Integer.decode(val));
               int index = Integer.parseInt(attr);
               cssColors.put(index, color);
            }
         }

         frame.setCSSColors(cssColors);
      }

      clrsNode = Tool.getChildNodeByTagName(tag, "userColors");

      if(clrsNode != null) {
         NodeList list = Tool.getChildNodesByTagName(clrsNode, "color");
         Map<Integer, Color> userColors = new HashMap<>();

         for(int i = 0; i < list.getLength(); i++) {
            Element node = (Element) list.item(i);
            String val = node.getAttribute("value");
            String attr = node.getAttribute("index");

            if(val != null && !val.equals("") && attr != null && !attr.equals("")) {
               Color color = new Color(Integer.decode(val));
               int index = Integer.parseInt(attr);
               userColors.put(index, color);
            }
         }

         frame.setUserColors(userColors);
      }

      clrsNode = Tool.getChildNodeByTagName(tag, "dimensionColors");

      if(clrsNode != null) {
         NodeList list = Tool.getChildNodesByTagName(clrsNode, "color");
         ArrayList<Color> colors = new ArrayList<>();

         for(int i = 0; i < list.getLength(); i++) {
            Element node = (Element) list.item(i);
            String val = node.getAttribute("value");
            String attr = node.getAttribute("dim");

            if(val != null && !val.equals("")) {
               attr = "".equals(attr) ? null : StringEscapeUtils.unescapeXml(attr);
               Color color = new Color(Integer.decode(val));

               if(!colors.contains(color)) {
                  colors.add(color);
                  frame.setColor(attr, color);
               }
            }
         }
      }

      clrsNode = Tool.getChildNodeByTagName(tag, "dateFormat");

      if(clrsNode != null) {
         String value = clrsNode.getAttribute("value");

         if(value != null) {
            try {
               dateFormat = Integer.parseInt(value);
            }
            catch(NumberFormatException e) {
               dateFormat = null;
            }
         }
      }

   }

   @Override
   public boolean equalsContent(Object obj) {
      return super.equalsContent(obj) &&
         colorValueFrame == ((CategoricalColorFrameWrapper) obj).colorValueFrame;
   }

   public Integer getDateFormat() {
      return this.dateFormat;
   }

   public void setDateFormat(Integer dateFormat) {
      this.dateFormat = dateFormat;
   }

   /**
    * @return the mapped colors to be displayed in the color mapping dialog
    */
   public Map<String, Color> getDimensionColors() {
      return dimensionColors;
   }

   public void setDimensionColors(Map<String, Color> dimensionColors) {
      this.dimensionColors = dimensionColors;
   }

   public boolean isShareColors() {
      CategoricalColorFrame frame = (CategoricalColorFrame) this.frame;
      return frame.isShareColors();
   }

   public Map<Integer, Color> getCSSColors() {
      CategoricalColorFrame frame = (CategoricalColorFrame) this.frame;
      return frame.getCSSColors();
   }

   public int getColorCount() {
      CategoricalColorFrame frame = (CategoricalColorFrame) this.frame;
      return frame.getColorCount();
   }

   public Color getDefaultColor(int index) {
      CategoricalColorFrame frame = (CategoricalColorFrame) this.frame;
      return frame.getDefaultColor(index);
   }

   public void setDefaultColor(int index, Color color) {
      CategoricalColorFrame frame = (CategoricalColorFrame) this.frame;
      frame.setDefaultColor(index, color);
   }

   public boolean isUseGlobal() {
      CategoricalColorFrame frame = (CategoricalColorFrame) this.frame;
      return frame.isUseGlobal();
   }

   public void setUseGlobal(boolean useGlobal) {
      CategoricalColorFrame frame = (CategoricalColorFrame) this.frame;
      frame.setUseGlobal(useGlobal);
   }

   public Set<Object> getStaticValues() {
      CategoricalColorFrame frame = (CategoricalColorFrame) this.frame;
      return frame.getStaticValues();
   }

   public void setShareColors(boolean shareColors) {
      CategoricalColorFrame frame = (CategoricalColorFrame) this.frame;
      frame.setShareColors(shareColors);
   }

   public void setColor(Object val, Color color) {
      CategoricalColorFrame frame = (CategoricalColorFrame) this.frame;
      frame.setColor(val, color);
   }

   public Color getColor(Object val) {
      CategoricalColorFrame frame = (CategoricalColorFrame) this.frame;
      return frame.getColor(val);
   }

   public boolean isColorValueFrame() {
      return colorValueFrame;
   }

   public void setColorValueFrame(boolean colorValueFrame) {
      this.colorValueFrame = colorValueFrame;
   }

   private Map<String, Color> dimensionColors = new HashMap<>();
   private Integer dateFormat;
   private boolean colorValueFrame;
   private ColorValueColorFrame frame2;
   private static final Logger LOG = LoggerFactory.getLogger(CategoricalColorFrameWrapper.class);
}
