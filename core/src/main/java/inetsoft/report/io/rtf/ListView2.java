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
package inetsoft.report.io.rtf;

import javax.swing.text.*;
import javax.swing.text.html.*;
import java.awt.*;

/**
 * ListView2 can be used to set the HTML.Tag li info.
 *
 * @version 10.2, 7/21/2009
 * @author InetSoft Technology Corp
 */
public class ListView2 extends ListView {
   /**
    * Create a ListView2.
    * @param elem Element object
    */
   public ListView2(Element elem) {
      super(elem);
   }

   /**
    * Paint a graphics.
    * @param g Graphics object
    * @param a Shape object
    */
   @Override
   public void paint(Graphics g, Shape allocation) {
      super.paint(g, allocation);
   }

   /**
    * Paint a child view.
    * @param g Graphics object
    * @param alloc Rectangle object
    * @param index view index
    */
   @Override
   protected void paintChild(Graphics g, Rectangle alloc, int index) {
      View cv = this.getView(index);

      if(checkLI(cv)) {
         RichTextFont font = new RichTextFont(g.getFont());
         font.setLIType(getChildType(cv).toString());
         g.setFont(font);
      }

      super.paintChild(g, alloc, index);
   }

   /**
    * Check the view contains LI tag.
    * @param cv child view
    * @return the boolean
    */
   private boolean checkLI(View cv) {
      Object name = cv.getElement().getAttributes().getAttribute(
            StyleConstants.NameAttribute);

      if(name != HTML.Tag.LI) {
         return false;
      }

      return true;
   }

   /**
    * Get the LI type.
    * @param childView child view
    * @return the LI type
    */
   private Object getChildType(View childView) {
      Object childtype = ((AttributeSet) childView.getAttributes())
            .getAttribute(CSS.Attribute.LIST_STYLE_TYPE);

      if(childtype == null) {
         View v = childView.getParent();

         if(matchNameAttribute(v.getElement().getAttributes(), HTML.Tag.OL)) {
            childtype = RichTextFont.DECIMAL;
         }
         else {
            childtype = RichTextFont.DISC;
         }
      }

      return childtype;
   }

   /**
    * Check the tag is OL.
    * @param childView child view
    * @return the LI type
    */
   static boolean matchNameAttribute(AttributeSet attr, HTML.Tag tag) {
      Object o = attr.getAttribute(StyleConstants.NameAttribute);

      if(o instanceof HTML.Tag) {
         HTML.Tag name = (HTML.Tag) o;

         if(name == tag) {
            return true;
         }
      }

      return false;
   }
}
