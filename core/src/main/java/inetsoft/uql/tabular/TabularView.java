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
package inetsoft.uql.tabular;

import inetsoft.util.Tool;
import inetsoft.util.XMLSerializable;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.awt.*;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Describes the layout information for views
 *
 * @version 12.2
 * @author InetSoft Technology Corp
 */
public class TabularView implements XMLSerializable {
   public TabularView() {
      this.views = new ArrayList<>();
   }

   /**
    * Adds a child tabular view to this view
    *
    * @param tView child tabular view
    */
   public void addTabularView(TabularView tView) {
      views.add(tView);
   }

   /**
    * Gets the type of this view
    *
    * @return the view type.
    */
   public ViewType getType() {
      return type;
   }

   /**
    * Sets the type of this view
    *
    * @param type the view type
    */
   public void setType(ViewType type) {
      this.type = type;
   }

   /**
    * Gets the text of this view
    *
    * @return the text
    */
   public String getText() {
      return text;
   }

   /**
    * Sets the text of this view
    *
    * @param text the text
    */
   public void setText(String text) {
      this.text = text;
   }

   /**
    * Gets the font color of this view
    *
    * @return the font color
    */
   public String getColor() {
      return color;
   }

   /**
    * Sets the font color of this view
    *
    * @param color the font color
    */
   public void setColor(String color) {
      this.color = color;
   }

   /**
    * Gets the font family of this view
    *
    * @return the font family
    */
   public String getFont() {
      return font;
   }

   /**
    * Sets the font family of this view
    *
    * @param font the font family
    */
   public void setFont(String font) {
      this.font = font;
   }

   /**
    * Gets the property name
    *
    * @return the property name
    */
   public String getValue() {
      return value;
   }

   /**
    * Sets the property name
    *
    * @param value the property name
    */
   public void setValue(String value) {
      this.value = value;
   }

   /**
    * Gets the layout row in which to place the label.
    *
    * @return the row index.
    */
   public int getRow() {
      return row;
   }

   /**
    * Sets the layout row in which to place the label.
    *
    * @param row the row index.
    */
   public void setRow(int row) {
      this.row = row;
   }

   /**
    * Gets the layout column in which to place the label.
    *
    * @return the column index.
    */
   public int getCol() {
      return col;
   }

   /**
    * Sets the layout column in which to place the label.
    *
    * @param col the column index.
    */
   public void setCol(int col) {
      this.col = col;
   }

   /**
    * Gets the number of rows to span in a grid.
    *
    * @return the row span.
    */
   public int getRowspan() {
      return rowspan;
   }

   /**
    * Sets the number of rows to span in a grid.
    *
    * @param rowspan the row span
    */
   public void setRowspan(int rowspan) {
      this.rowspan = rowspan;
   }

   /**
    * Gets the number of columns to span in a grid.
    *
    * @return the column span.
    */
   public int getColspan() {
      return colspan;
   }

   /**
    * Sets the number of columns to span in a grid.
    *
    * @param colspan the column span
    */
   public void setColspan(int colspan) {
      this.colspan = colspan;
   }

   /**
    * Gets the alignment for the label.
    *
    * @return the alignment.
    */
   public ViewAlign getAlign() {
      return align;
   }

   /**
    * Sets the alignment for the label.
    *
    * @param align the alignment.
    */
   public void setAlign(ViewAlign align) {
      this.align = align;
   }

   /**
    * Gets the vertical alignment for the label.
    *
    * @return the vertical alignment.
    */
   public ViewAlign getVerticalAlign() {
      return verticalAlign;
   }

   /**
    * Sets the vertical alignment for the label.
    *
    * @param verticalAlign the vertical alignment.
    */
   public void setVerticalAlign(ViewAlign verticalAlign) {
      this.verticalAlign = verticalAlign;
   }

   /**
    * Gets the left padding of the view.
    *
    * @return the left padding.
    */
   public int getPaddingLeft() {
      return paddingLeft;
   }

   /**
    * Sets the left padding of the view.
    *
    * @param paddingLeft  the left padding.
    */
   public void setPaddingLeft(int paddingLeft) {
      this.paddingLeft = paddingLeft;
   }

   /**
    * Gets the right padding of the view.
    *
    * @return the right padding.
    */
   public int getPaddingRight() {
      return paddingRight;
   }

   /**
    * Sets the right padding of the view.
    *
    * @param paddingRight  the right padding.
    */
   public void setPaddingRight(int paddingRight) {
      this.paddingRight = paddingRight;
   }

   /**
    * Gets the top padding of the view.
    *
    * @return the top padding.
    */
   public int getPaddingTop() {
      return paddingTop;
   }

   /**
    * Sets the top padding of the view.
    *
    * @param paddingTop  the top padding.
    */
   public void setPaddingTop(int paddingTop) {
      this.paddingTop = paddingTop;
   }

   /**
    * Gets the bottom padding of the view.
    *
    * @return the bottom padding.
    */
   public int getPaddingBottom() {
      return paddingBottom;
   }

   /**
    * Sets the bottom padding of the view.
    *
    * @param paddingBottom the bottom padding.
    */
   public void setPaddingBottom(int paddingBottom) {
      this.paddingBottom = paddingBottom;
   }

   /**
    * Gets the flag that determines whether this property is a password
    *
    * @return <tt>true</tt> if password; <tt>false</tt> otherwise.
    */
   public boolean isPassword() {
      return password;
   }

   /**
    * Sets the flag that determines whether this property is a password
    *
    * @param password <tt>true</tt> if password; <tt>false</tt> otherwise.
    */
   public void setPassword(boolean password) {
      this.password = password;
   }

   /**
    * Gets the display label of this view
    *
    * @return the display label
    */
   public String getDisplayLabel() {
      return displayLabel;
   }

   /**
    * Sets the display label of this view
    *
    * @param displayLabel the display label
    */
   public void setDisplayLabel(String displayLabel) {
      this.displayLabel = displayLabel;
   }

   /**
    * Gets the tabular editor associated with this view
    *
    * @return the tabular editor
    */
   public TabularEditor getEditor() {
      return editor;
   }

   /**
    * Sets the tabular editor associated with this view
    *
    * @param editor the tabular editor
    */
   public void setEditor(TabularEditor editor) {
      this.editor = editor;
   }

   /**
    * Gets the tabular button associated with this view
    *
    * @return the tabular button
    */
   public TabularButton getButton() {
      return button;
   }

   /**
    * Sets the tabular button associated with this view
    *
    * @param button the tabular button
    */
   public void setButton(TabularButton button) {
      this.button = button;
   }

   /**
    * Check if the value can be null or empty.
    */
   public boolean isRequired() {
      return required;
   }
   
   /**
    * Set if the value can be null or empty.
    */
   public void setRequired(boolean required) {
      this.required = required;
   }

   /**
    * Get the minimum value for the property. NaN to ignore.
    */
   public double getMin() {
      return min;
   }
   
   /**
    * Set the minimum value for the property.
    */
   public void setMin(double min) {
      this.min = min;
   }

   /**
    * Get the maximum value for the property. NaN to ignore.
    */
   public double getMax() {
      return max;
   }
   
   /**
    * Set the maximum value for the property.
    */
   public void setMax(double max) {
      this.max = max;
   }

   /**
    * Get the regular expression to match the value.
    */
   public String[] getPattern() {
      return pattern;
   }
   
   /**
    * Set the regular expression to match the value.
    */
   public void setPattern(String[] pattern) {
      this.pattern = pattern;
   }

   /**
    * Gets the children tabular views of this view
    *
    * @return the children tabular views
    */
   public TabularView[] getViews() {
      return views.toArray(new TabularView[0]);
   }

   public boolean isVisible() {
      return visible;
   }

   public void setVisible(boolean visible) {
      this.visible = visible;
   }

   public String getVisibleMethod() {
      return visibleMethod;
   }

   public void setVisibleMethod(String visibleMethod) {
      this.visibleMethod = visibleMethod;
   }

   public Component getComponent() {
      return component;
   }

   public void setComponent(Component component) {
      this.component = component;
   }

   public boolean isWrap() {
      return wrap;
   }

   public void setWrap(boolean wrap) {
      this.wrap = wrap;
   }

   @Override
   public void writeXML(PrintWriter writer) {
      writer.println("<tabularView class=\"" + getClass().getName()+ "\">");

      if(type != null) {
         writer.format("<type><![CDATA[%s]]></type>%n", type);
      }

      if(text != null) {
         writer.format("<text><![CDATA[%s]]></text>%n", text);
      }

      if(color != null) {
         writer.format("<color><![CDATA[%s]]></color>%n", color);
      }

      if(font != null) {
         writer.format("<font><![CDATA[%s]]></font>%n", font);
      }

      if(value != null) {
         writer.format("<value><![CDATA[%s]]></value>%n", value);
      }

      writer.format("<row><![CDATA[%s]]></row>%n", row);
      writer.format("<col><![CDATA[%s]]></col>%n", col);
      writer.format("<rowspan><![CDATA[%s]]></rowspan>%n", rowspan);
      writer.format("<colspan><![CDATA[%s]]></colspan>%n", colspan);
      writer.format("<required>%s</required>", required);
      writer.format("<wrap>%s</wrap>", wrap);

      if(!Double.isNaN(min)) {
         writer.format("<min>%s</min>", min);
      }

      if(!Double.isNaN(max)) {
         writer.format("<max>%s</max>", max);
      }

      if(pattern != null) {
         writer.println("<patterns>");

         for(int i = 0;i < pattern.length;i++) {
            writer.format("<pattern>%s</pattern>", pattern[i]);
         }

         writer.println("</patterns>");
      }

      if(align != null) {
         writer.format("<align><![CDATA[%s]]></align>%n", align);
      }

      if(verticalAlign != null) {
         writer.format("<verticalAlign><![CDATA[%s]]></verticalAlign>%n",
            verticalAlign);
      }

      if(displayLabel != null) {
         writer.format("<displayLabel><![CDATA[%s]]></displayLabel>%n",
                       displayLabel);
      }

      writer.format("<paddingLeft><![CDATA[%s]]></paddingLeft>%n", paddingLeft);
      writer.format("<paddingRight><![CDATA[%s]]></paddingRight>%n",
         paddingRight);
      writer.format("<paddingTop><![CDATA[%s]]></paddingTop>%n", paddingTop);
      writer.format("<paddingBottom><![CDATA[%s]]></paddingBottom>%n",
         paddingBottom);

      writer.format("<password><![CDATA[%s]]></password>%n", password);

      if(editor != null) {
         editor.writeXML(writer);
      }

      if(button != null) {
         button.writeXML(writer);
      }

      writer.println("<views>");

      for(TabularView view : views) {
         view.writeXML(writer);
      }

      writer.println("</views>");
      writer.println("</tabularView>");
   }

   @Override
   public void parseXML(Element tag) throws Exception {
      String val = Tool.getChildValueByTagName(tag, "type");

      if(val != null) {
         type = ViewType.valueOf(val);
      }

      text = Tool.getChildValueByTagName(tag, "text");
      color = Tool.getChildValueByTagName(tag, "color");
      font = Tool.getChildValueByTagName(tag, "font");
      value = Tool.getChildValueByTagName(tag, "value");
      required = "true".equals(Tool.getChildValueByTagName(tag, "required"));
      wrap = "true".equals(Tool.getChildValueByTagName(tag, "wrap"));

      if((val = Tool.getChildValueByTagName(tag, "min")) != null) {
         min = Double.parseDouble(val);
      }

      if((val = Tool.getChildValueByTagName(tag, "max")) != null) {
         max = Double.parseDouble(val);
      }

      Element elem = Tool.getChildNodeByTagName(tag, "patterns");

      if(elem != null) {
         NodeList list = Tool.getChildNodesByTagName(elem, "pattern");
         pattern = new String[list.getLength()];

         for(int i = 0;i < list.getLength();i++) {
            pattern[i] = Tool.getValue((Element) list.item(i));
         }
      }

      if((val = Tool.getChildValueByTagName(tag, "row")) != null) {
         row = Integer.parseInt(val);
      }

      if((val = Tool.getChildValueByTagName(tag, "col")) != null) {
         col = Integer.parseInt(val);
      }

      val = Tool.getChildValueByTagName(tag, "rowspan");

      if(val != null) {
         rowspan = Integer.parseInt(val);
      }

      val = Tool.getChildValueByTagName(tag, "colspan");

      if(val != null) {
         colspan = Integer.parseInt(val);
      }

      val = Tool.getChildValueByTagName(tag, "align");

      if(val != null) {
         align = ViewAlign.valueOf(val);
      }

      val = Tool.getChildValueByTagName(tag, "verticalAlign");

      if(val != null) {
         verticalAlign = ViewAlign.valueOf(val);
      }

      val = Tool.getChildValueByTagName(tag, "paddingLeft");

      if(val != null) {
         paddingLeft = Integer.parseInt(val);
      }

      val = Tool.getChildValueByTagName(tag, "paddingRight");

      if(val != null) {
         paddingRight = Integer.parseInt(val);
      }

      val = Tool.getChildValueByTagName(tag, "paddingTop");

      if(val != null) {
         paddingTop = Integer.parseInt(val);
      }

      val = Tool.getChildValueByTagName(tag, "paddingBottom");

      if(val != null) {
         paddingBottom = Integer.parseInt(val);
      }

      password = "true".equals(Tool.getChildValueByTagName(tag, "password"));
      displayLabel = Tool.getChildValueByTagName(tag, "displayLabel");

      Element node = Tool.getChildNodeByTagName(tag, "tabularEditor");

      if(node != null) {
         editor = new TabularEditor();
         editor.parseXML(node);
      }

      node = Tool.getChildNodeByTagName(tag, "tabularButton");

      if(node != null) {
         button = new TabularButton();
         button.parseXML(node);
      }

      node = Tool.getChildNodeByTagName(tag, "views");

      if(node != null) {
         NodeList viewList = Tool.getChildNodesByTagName(node, "tabularView");

         for(int i = 0; i < viewList.getLength(); i++) {
            TabularView tView = new TabularView();
            tView.parseXML((Element) viewList.item(i));
            addTabularView(tView);
         }
      }
   }

   private ViewType type;
   private String text;
   private String color;
   private String font;
   private String value;
   private int row;
   private int col;
   private int rowspan;
   private int colspan;
   private ViewAlign align;
   private ViewAlign verticalAlign;
   private int paddingLeft;
   private int paddingRight;
   private int paddingTop;
   private int paddingBottom;
   private boolean password;
   private String displayLabel;
   private TabularEditor editor;
   private TabularButton button;
   private List<TabularView> views;
   private boolean required;
   private boolean visible;
   private String visibleMethod;
   private double min = Double.NaN;
   private double max = Double.NaN;
   private String[] pattern;
   private boolean wrap;
   private transient Component component;
}
