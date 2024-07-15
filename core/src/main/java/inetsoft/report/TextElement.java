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
package inetsoft.report;

/**
 * A TextElement represents a text string. It can be a short string, or
 * a multi-line paragraph. The text element can wrap into multiple lines,
 * and can wrap around other elements.
 */
public interface TextElement extends ReportElement, HyperlinkSupport {
   /**
    * Get the contents of this text element.
    */
   public String getText();

   /**
    * Set the contents of this text element.
    */
   public void setText(String text);

   /**
    * Set the contents of this text element.
    */
   public void setTextLens(TextLens text);

   /**
    * Return the text lens of this text element.
    */
   public TextLens getTextLens();

   /**
    * Get the line justify setting.
    * @return true if lines are justified.
    */
   public boolean isJustify();

   /**
    * Set the line justify setting.
    * @param justify true to justify lines.
    */
   public void setJustify(boolean justify);

   /**
    * Get the advance amount after a text element.
    */
   public int getTextAdvance();

   /**
    * Set the horizontal space after a text element.
    * @param textadv gap in points.
    */
   public void setTextAdvance(int textadv);

   /**
    * Get widow/orphan control.
    */
   public boolean isOrphanControl();

   /**
    * Set the widow/orphan control. If orphan control is turned on, single
    * line at the top and bottom of a page is avoided if text has multiple
    * lines.
    */
   public void setOrphanControl(boolean orphan);

   /**
    * Get the data in object, it's used for binding.
    */
   public Object getData();

   /**
    * Set the data in object, it's used for binding.
    */
   public void setData(Object val);

   /**
    * Get the textID, which is used for i18n support.
    */
   public String getTextID();

   /**
    * Set the textID, which is used for i18n support.
    */
   public void setTextID(String textID);
}

