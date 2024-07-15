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
package inetsoft.report.filter;

import inetsoft.report.StyleFont;
import inetsoft.report.internal.Util;
import inetsoft.uql.*;
import inetsoft.uql.schema.UserVariable;
import inetsoft.util.Tool;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.awt.*;
import java.io.PrintWriter;

/**
 * Text and Textbox HighLight class.
 * This class defines some method of the highlight attribute for Text and
 * TextBox. It is a subclass of Highlight.
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class TextHighlight implements Highlight {
   /**
    * Set the font.
    * @param f Font value;
    */
   @Override
   public void setFont(Font f) {
      tFont = f;
   }

   /**
    * Set the foreground.
    * @param fGround color value
    */
   @Override
   public void setForeground(Color fGround) {
      foreground = fGround;
   }

   /**
    * Set the background.
    * @param bGround color value
    */
   @Override
   public void setBackground(Color bGround) {
      background = bGround;
   }

   /**
    * Set the condition value.
    * @param con Vector value
    */
   @Override
   public void setConditionGroup(ConditionList con) {
      this.conditions = con;
   }

   /**
    * Set the name value.
    * @param name string value
    */
   @Override
   public void setName(String name) {
      this.name = name;
   }

   /**
    * Get the font.
    */
   @Override
   public Font getFont() {
      return tFont;
   }

   /**
    * Get the foreground.
    */
   @Override
   public Color getForeground() {
      return foreground;
   }

   /**
    * Get the background.
    */
   @Override
   public Color getBackground() {
      return background;
   }

   /**
    * Get the name value.
    */
   @Override
   public String getName() {
      return name;
   }

   /**
    * Check if the highlight is empty.
    */
   @Override
   public boolean isEmpty() {
      // @by amitm, 2004-09-22, bug1095840636361
      // Preserve the Highlight even if font, foreground,
      // background and conditions are not set.
      return getName() == null;
   }

   /**
    * Check if condition is empty.
    */
   @Override
   public boolean isConditionEmpty() {
      return conditions.getSize() == 0;
   }

   /**
    * Clear the condition.
    */
   @Override
   public void removeAllConditions() {
      conditions.removeAllItems();
   }

   /**
    * Clear the condition.
    */
   @Override
   public ConditionList getConditionGroup() {
      return conditions;
   }

   /**
    * Writer a group of Text Hightlight condition attributes to XML.
    *
    * <HighlightAttr name="XXX" type="TEXT" font="font" foreground="0"
    *                background="0">
    * <conditions></conditions>
    * </HighlightAttr>
    */
   @Override
   public void writeXML(PrintWriter writer) {
      if(isConditionEmpty()) {
         return;
      }

      writer.print("<HighlightAttr type=\"" + TEXT + "\"");

      if(getName() != null) {
         writer.print(" name=\"" + Tool.escape(getName()) + "\"");
      }

      Font font = getFont();

      if(font != null) {
         writer.print(" font=\"" + Tool.escape(StyleFont.toString(font)) + "\"");
      }

      Color fg = getForeground();

      if(fg != null) {
         writer.print(" foreground=\"" + fg.getRGB() + "\"");
      }

      Color bg = getBackground();

      if(bg != null) {
         writer.print(" background=\"" + bg.getRGB() + "\"");
      }

      writer.println(">");

      conditions.writeXML(writer);

      writer.println("</HighlightAttr>");
   }

   /**
    * Parse xml tree into this binding object.
    */
   @Override
   public void parseXML(Element tag) throws Exception {
      String name = Tool.getAttribute(tag, "name");
      setName(name);

      String font = Tool.getAttribute(tag, "font");
      setFont(font == null ? null : StyleFont.decode(font));

      String fg = Tool.getAttribute(tag, "foreground");
      setForeground(fg == null ? null : Color.decode(fg));

      String bg = Tool.getAttribute(tag, "background");
      setBackground(bg == null ? null : Color.decode(bg));

      // add conditions
      NodeList list = Tool.getChildNodesByTagName(tag, "conditions");

      if(list.getLength() > 0) {
         Element ctag = (Element) list.item(0);
         ConditionList condition = new ConditionList();

         condition.parseXML(ctag);
         setConditionGroup(Util.buildReportConditionList(condition));
      }
   }

   /**
    * get all variable from condition
    * @return user variable array
    */
   @Override
   public UserVariable[] getAllVariables() {
      return conditions.getAllVariables();
   }

   /**
    * replace variable with value user inputed
    * @param vart variable table
    */
   @Override
   public void replaceVariables(VariableTable vart) {
      conditions.replaceVariables(vart);

      for(int i = 0; i < conditions.getSize(); i += 2) {
         XCondition cond = conditions.getXCondition(i);

         if(cond != null && cond instanceof Condition) {
            ((Condition) cond).setIgnored(false);
         }
      }
   }

   @Override
   public Highlight clone() {
      TextHighlight attr = new TextHighlight();

      attr.setFont(getFont());
      attr.setForeground(getForeground());
      attr.setBackground(getBackground());
      attr.setName(getName());

      attr.conditions = conditions.clone();

      return attr;
   }

   /**
    * Check if equals another object.
    */
   public boolean equals(Object obj) {
      if(!(obj instanceof TextHighlight)) {
         return false;
      }

      TextHighlight hl2 = (TextHighlight) obj;

      if(!equals(tFont, hl2.tFont)) {
         return false;
      }

      if(!equals(foreground, hl2.foreground)) {
         return false;
      }

      if(!equals(background, hl2.background)) {
         return false;
      }

      if(!equals(conditions, hl2.conditions)) {
         return false;
      }

      return true;
   }

   /**
    * Check if two objects are equal.
    */
   private boolean equals(Object obj1, Object obj2) {
      return obj1 == null ? obj1 == obj2 : obj1.equals(obj2);
   }


   /**
    * Get the string representation.
    */
   public String toString() {
      return super.toString() + '<' + name + ": " + conditions + "=" + foreground + "," +
         background + "," + tFont + '>';
   }

   protected Font tFont = null;
   protected Color foreground = null;
   protected Color background = null;
   protected String name;
   protected ConditionList conditions = new ConditionList();
}
