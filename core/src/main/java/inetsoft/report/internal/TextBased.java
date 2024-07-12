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
package inetsoft.report.internal;

import inetsoft.report.*;
import inetsoft.report.filter.HighlightGroup;
import inetsoft.report.filter.TextHighlight;
import inetsoft.report.internal.table.PresenterRef;
import inetsoft.uql.XTableNode;

import java.text.Format;

/**
 * This interface has the methods for accessing text base element
 * contents and attributes.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public interface TextBased extends ReportElement {
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
    * Return the display text.
    */
   public String getDisplayText();

   /**
    * Return the text in the text lens.
    */
   public String getText();

   /**
    * Return the text lens.
    */
   public TextLens getTextLens();

   /**
    * Set the text contained in this text element.
    */
   public void setText(String text);

   /**
    * Get the textID, which is used for i18n support.
    */
   public String getTextID();

   /**
    * Set the textID, which is used for i18n support.
    */
   public void setTextID(String textID);

   /**
    * Get the hyper link on this element.
    */
   public Hyperlink getHyperlink();

   /**
    * Set the hyper link of this element.
    */
   public void setHyperlink(Hyperlink link);

   /**
    * Get the data in object, it's used for binding.
    */
   public Object getData();

   /**
    * Set the data in object, it's used for binding.
    */
   public void setData(Object val);

   /**
    * Set the data in object, it's used for binding.
    */
   public void setData(Object val, Format format);

   /**
    * Set the highlight group setting.
    */
   public HighlightGroup getHighlightGroup();

   /**
    * Get the highlight group setting.
    */
   public void setHighlightGroup(HighlightGroup group);

   /**
    * Set highlight.
    */
   public void setHighlight(TextHighlight highlight);

   /**
    * Get highlight.
    */
   public TextHighlight getHighlight();

   /**
    * Reset highlight.
    */
   public void resetHighlight();

   /**
    * Get the presenter to be used in this element.
    */
   public PresenterRef getPresenter();

   /**
    * Set the presenter to be used in this element.
    */
   public void setPresenter(PresenterRef ref);

   /**
    * Get the drill hyperlinks on this element.
    */
   public Hyperlink.Ref[] getDrillHyperlinks();

   /**
    * Set the drill hyperlinks of this element.
    */
   public void setDrillHyperlinks(Hyperlink.Ref[] links);

   public XTableNode getTableNode();

   public void setTableNode(XTableNode tableNode);
}
