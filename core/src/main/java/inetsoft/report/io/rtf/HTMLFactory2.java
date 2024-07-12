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
package inetsoft.report.io.rtf;

import javax.swing.text.*;
import javax.swing.text.html.*;
import java.awt.*;

/**
 * A factory to build views for HTML. The following
 * table describes what this factory will build by
 * default.
 *
 * @version 10.2, 7/21/2009
 * @author InetSoft Technology Corp
 */
public class HTMLFactory2 extends HTMLEditorKit.HTMLFactory {
   /**
    * Creates a view from an element.
    *
    * @param elem the element
    * @return the view
    */
   @Override
   public View create(Element elem) {
      Object o = elem.getAttributes().getAttribute(
         StyleConstants.NameAttribute);

      if(o instanceof HTML.Tag) {
         HTML.Tag kind = (HTML.Tag) o;

         if(kind == HTML.Tag.CONTENT) {
            return new InlineView2(elem);
         }
         else if(kind == HTML.Tag.IMPLIED) {
            String ws = (String) elem.getAttributes().getAttribute(
                  CSS.Attribute.WHITE_SPACE);

            if((ws != null) && ws.equals("pre")) {
               return super.create(elem);
            }

            return new ParagraphView2(elem, null);
         }
         else if((kind == HTML.Tag.P) || (kind == HTML.Tag.H1)
               || (kind == HTML.Tag.H2) || (kind == HTML.Tag.H3)
               || (kind == HTML.Tag.H4) || (kind == HTML.Tag.H5)
               || (kind == HTML.Tag.H6) || (kind == HTML.Tag.DT)) {
            return new ParagraphView2(elem, 
               (kind == HTML.Tag.P || kind == HTML.Tag.DT) ? null : kind);
         }
         else if((kind == HTML.Tag.MENU) || (kind == HTML.Tag.DIR)
               || (kind == HTML.Tag.UL) || (kind == HTML.Tag.OL)) {
            return new ListView2(elem);
         }
         else if(kind == HTML.Tag.BODY) {
            return super.create(elem);
         }
         else if(kind == HTML.Tag.HTML) {
            return new BlockView(elem, View.Y_AXIS);
         }
         else if((kind == HTML.Tag.LI) || (kind == HTML.Tag.CENTER)
               || (kind == HTML.Tag.DL) || (kind == HTML.Tag.DD)
               || (kind == HTML.Tag.DIV) || (kind == HTML.Tag.BLOCKQUOTE)
               || (kind == HTML.Tag.PRE) || (kind == HTML.Tag.FORM)) {
            // vertical box
            return new BlockView(elem, View.Y_AXIS);
         }
         else if(kind == HTML.Tag.NOFRAMES) {
            return super.create(elem);
         }
         else if(kind == HTML.Tag.IMG) {
            return new ImageView(elem);
         }
         else if(kind == HTML.Tag.ISINDEX) {
            return super.create(elem);
         }
         else if(kind == HTML.Tag.HR) {
            return super.create(elem);
         }
         else if(kind == HTML.Tag.BR) {
            return super.create(elem);
         }
         else if(kind == HTML.Tag.TABLE) {
            return super.create(elem);
         }
         else if((kind == HTML.Tag.INPUT) || (kind == HTML.Tag.SELECT)
               || (kind == HTML.Tag.TEXTAREA)) {
            return new FormView(elem);
         }
         else if(kind == HTML.Tag.OBJECT) {
            return new ObjectView(elem);
         }
         else if(kind == HTML.Tag.FRAMESET) {
            if(elem.getAttributes().isDefined(HTML.Attribute.ROWS)) {
               return super.create(elem);
            }
            else if(elem.getAttributes().isDefined(HTML.Attribute.COLS)) {
               return super.create(elem);
            }

            throw new RuntimeException("Can't build a" + kind + ", " + elem
                  + ":" + "no ROWS or COLS defined.");
         }
         else if(kind == HTML.Tag.FRAME) {
            return super.create(elem);
         }
         else if(kind instanceof HTML.UnknownTag) {
            return super.create(elem);
         }
         else if(kind == HTML.Tag.COMMENT) {
            return super.create(elem);
         }
         else if(kind == HTML.Tag.HEAD) {
            // Make the head never visible, and never load its
            // children. For Cursor positioning,
            // getNextVisualPositionFrom is overriden to always return
            // the end offset of the element.
            return new BlockView(elem, View.X_AXIS) {
               @Override
               public float getPreferredSpan(int axis) {
                  return 0;
               }

               @Override
               public float getMinimumSpan(int axis) {
                  return 0;
               }

               @Override
               public float getMaximumSpan(int axis) {
                  return 0;
               }

               @Override
               protected void loadChildren(ViewFactory f) {
               }

               @Override
               public Shape modelToView(int pos, Shape a, Position.Bias b)
                     throws BadLocationException {
                  return a;
               }

               @Override
               public int getNextVisualPositionFrom(int pos,
                                                    Position.Bias b, Shape a, int direction,
                                                    Position.Bias[] biasRet) {
                  return getElement().getEndOffset();
               }
            };
         }
         else if((kind == HTML.Tag.TITLE) || (kind == HTML.Tag.META)
               || (kind == HTML.Tag.LINK) || (kind == HTML.Tag.STYLE)
               || (kind == HTML.Tag.SCRIPT) || (kind == HTML.Tag.AREA)
               || (kind == HTML.Tag.MAP) || (kind == HTML.Tag.PARAM)
               || (kind == HTML.Tag.APPLET)) {
            return super.create(elem);
         }
      }
      // If we get here, it's either an element we don't know about
      // or something from StyledDocument that doesn't have a mapping to HTML.
      String nm = elem.getName();

      if(nm != null) {
         if(nm.equals(AbstractDocument.ContentElementName)) {
            return new LabelView(elem);
         }
         else if(nm.equals(AbstractDocument.ParagraphElementName)) {
            return new javax.swing.text.html.ParagraphView(elem);
         }
         else if(nm.equals(AbstractDocument.SectionElementName)) {
            return new BoxView(elem, View.Y_AXIS);
         }
         else if(nm.equals(StyleConstants.ComponentElementName)) {
            return new ComponentView(elem);
         }
         else if(nm.equals(StyleConstants.IconElementName)) {
            return new IconView(elem);
         }
      }

      // default to text display
      return new LabelView(elem);
   }
}
