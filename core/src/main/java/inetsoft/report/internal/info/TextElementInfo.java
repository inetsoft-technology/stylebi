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
package inetsoft.report.internal.info;

import inetsoft.report.filter.HighlightGroup;
import inetsoft.report.internal.table.PresenterRef;

/**
 * A TextElementInfo save the element info used in TextElement.
 */
public class TextElementInfo extends ElementInfo implements TextBasedInfo {
   /**
    * Default construct
    */
   public TextElementInfo() {
      super();
   }

   /**
    * Get the contents of this text element.
    */
   public String getText() {
      return text;
   }

   /**
    * Set the contents of this text element.
    */
   public void setText(String text) {
      this.text = text;
   }

   /**
    * Get the line justify setting.
    * @return true if lines are justified.
    */
   public boolean isJustify() {
      return justify;
   }

   /**
    * Set the line justify setting.
    * @param justify true to justify lines.
    */
   public void setJustify(boolean justify) {
      this.justify = justify;
   }

   /**
    * Get the advance amount after a text element.
    */
   public int getTextAdvance() {
      return textadv;
   }

   /**
    * Set the horizontal space after a text element.
    * @param textadv gap in points.
    */
   public void setTextAdvance(int textadv) {
      this.textadv = textadv;
   }

   /**
    * Get widow/orphan control.
    */
   public boolean isOrphanControl() {
      return orphan;
   }

   /**
    * Set the widow/orphan control. If orphan control is turned on, single
    * line at the top and bottom of a page is avoided if text has multiple
    * lines.
    */
   public void setOrphanControl(boolean orphan) {
      this.orphan = orphan;
   }

   /**
    * Set the highlight group setting.
    */
   @Override
   public HighlightGroup getHighlightGroup() {
      return hg;
   }

   /**
    * Get the highlight group setting.
    */
   @Override
   public void setHighlightGroup(HighlightGroup group) {
      hg = group;
   }

   /**
    * Get the presenter to be used in this element.
    */
   @Override
   public PresenterRef getPresenter() {
      return presenter;
   }

   /**
    * Set the presenter to be used in this element.
    */
   @Override
   public void setPresenter(PresenterRef ref) {
      this.presenter = ref;
   }

   /**
    * Clone the object.
    */
   @Override
   public Object clone() {
      TextElementInfo info2 = (TextElementInfo) super.clone();

      if(hg != null) {
         info2.hg = (HighlightGroup) hg.clone();
      }

      if(presenter != null) {
         info2.presenter = (PresenterRef) presenter.clone();
      }

      return info2;
   }

   /**
    * Get the name of the tag of the root of the properties xml tree.
    */
   @Override
   public String getTagName() {
      return "textElementInfo";
   }

   /**
    * Create an ElementInfo.
    */
   @Override
   protected ElementInfo create() {
      return new TextElementInfo();
   }

   /**
    * Get the default info in section.
    */
   @Override
   public  ElementInfo createInSection(boolean autoResize, String name) {
      TextElementInfo info =
         (TextElementInfo)super.createInSection(autoResize, name);

      if(autoResize) {
         info.setProperty("grow", "true");
      }

      info.text = "Label";
      return info;
   }

   @Override
   public void copy(ElementInfo info) {
      super.copy(info);

      if(info instanceof TextElementInfo) {
         TextElementInfo tinfo = (TextElementInfo) info;
         justify = tinfo.justify;
      }
      else if(info instanceof TextBoxElementInfo) {
         justify = ((TextBoxElementInfo) info).isJustify();
      }
   }

   //text saved for serialization, changes on this text will not effect on
   //the text lens reside in TextElementDef. If user want to change the
   //content in the TextElementDef, must call TextElementDef.setText explicitily.
   private String text = null;

   private HighlightGroup hg;
   private boolean justify;
   private int textadv;
   private boolean orphan = false; //widow/orphan control
   private PresenterRef presenter;
}
