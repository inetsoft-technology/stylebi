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
package inetsoft.report.filter;

import inetsoft.report.StyleFont;
import inetsoft.report.internal.Util;
import inetsoft.uql.ConditionList;
import inetsoft.util.Tool;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.awt.*;
import java.io.PrintWriter;

/**
 * Table HighLight class.
 * This class defines some method of the highlight attribute for Table. It is
 * a subclass of Highlight.
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class ColumnHighlight extends TextHighlight {
   /**
    * Create a default highlight.
    */
   public ColumnHighlight() {
   }

   /**
    * Create a highlight and copy highlight definition.
    */
   public ColumnHighlight(Highlight hl) {
      setFont(hl.getFont());
      setForeground(hl.getForeground());
      setBackground(hl.getBackground());
      setName(hl.getName());
      setConditionGroup(hl.getConditionGroup());
   }

   /**
    * Set the Border.
    * @param b int value
    */
   public void setBorder(int b) {
      border = b;
   }

   /**
    * Set the alignment.
    * @param a int value
    */
   public void setAlignment(int a) {
      alignment = a;
   }

   /**
    * Set the border.
    */
   public int getBorder() {
      return (border);
   }

   /**
    * Set the alignment.
    */
   public int getAlignment() {
      return alignment;
   }

   /**
    * Writer a group of Text Hightlight condition attributes to XML.
    *
    * <HighlightAttr name="XXX" type="TABLE" font="font" foreground="0"
    *                background="0">
    * <conditions></conditions>
    * </HighlightAttr>
    */
   @Override
   public void writeXML(PrintWriter writer) {
      if(isConditionEmpty()) {
         return;
      }

      writer.print("<HighlightAttr type=\"" + TABLE + "\"");

      if(getName() != null) {
         writer.print(" name=\"" + Tool.escape(getName()) + "\"");
      }

      Font font = getFont();

      if(font != null) {
         writer.print(" font=\"" + Tool.escape(StyleFont.toString(font)) +
                      "\"");
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

   @Override
   public Highlight clone() {
      ColumnHighlight attr = new ColumnHighlight();

      attr.setFont(getFont());
      attr.setForeground(getForeground());
      attr.setBackground(getBackground());
      attr.setAlignment(getAlignment());
      attr.setBorder(getBorder());
      attr.setName(getName());

      attr.conditions = conditions.clone();

      return attr;
   }

   protected int alignment;            // alignment attribute
   protected int border;
}

