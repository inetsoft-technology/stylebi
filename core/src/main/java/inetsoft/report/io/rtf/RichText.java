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
package inetsoft.report.io.rtf;

import java.awt.*;

/**
 * RichText supports storing the attributes of text elements,
 * such as font, bgColor, fgColor, content, x, y, etc.
 *
 * @version 10.2, 7/21/2009
 * @author InetSoft Technology Corp
 */
public class RichText {
   /**
    * Creates a new RichText.
    */
   public RichText(Graphics2D g2D, String s, float x, float y) {
      this.content = s;
      this.x = x;
      this.y = y;
      this.fgColor = g2D.getColor();
      this.bgColor = g2D.getBackground();
      this.font = g2D.getFont();
   }

   /**
    * Creates a new RichText.
    */
   public RichText(String s, Font f, float x, float y) {
      this.content = s;
      this.font = f;
      this.x = x;
      this.y = y;
   }

   /**
    * Get the backgroundColor.
    */
   public Color getBgColor() {
      return bgColor;
   }

   /**
    * Get the foregroundColor.
    */
   public Color getFgColor() {
      return fgColor;
   }

   /**
    * Get the content.
    */
   public String getContent() {
      return content;
   }

   /**
    * Get the RichTextFont.
    */
   public Font getFont() {
      return font;
   }

   /**
    * Set the x coordinate.
    */
   public void setX(float x) {
      this.x = x;
   }

   /**
    * Get the x coordinate.
    */
   public float getX() {
      return x;
   }

   /**
    * Set the y coordinate.
    */
   public void setY(float y) {
      this.y = y;
   }

   /**
    * Get the y coordinate.
    */
   public float getY() {
      return y;
   }

   private Color bgColor;
   private Color fgColor;
   private String content;
   private Font font;
   private float x;
   private float y;
}