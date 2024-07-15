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

import javax.swing.text.Element;
import javax.swing.text.GlyphView;
import javax.swing.text.html.HTML;
import javax.swing.text.html.InlineView;
import java.awt.*;

/**
 * ParagraphView2 can be used to set the rich text info.
 *
 * @version 10.2, 7/21/2009
 * @author InetSoft Technology Corp
 */
public class InlineView2 extends InlineView {
   /**
    * Create a InlineView2.
    * @param elem Element object
    */
   public InlineView2(Element elem) {
      super(elem);
   }

   /**
    * Paint a graphics.
    * @param g Graphics object
    * @param a Shape object
    */
   @Override
   public void paint(Graphics g, Shape a) {
      HTML.Tag header = getHeader(g.getFont());
      String liType = getLIType(g.getFont());
      Graphics2D g2 = (Graphics2D) g;
      g2.setFont(getFont(header, liType));
      g2.setBackground(getBackground());
      g2.setColor(getForeground());
      super.paint(g2, a);
   }

   /**
    * Get the font.
    * @param header HTML.Tag
    * @param liType OL type
    */
   private Font getFont(HTML.Tag header, String liType) {
      sync(this);
      RichTextFont font = new RichTextFont(metrics.getFont().getName(),
               metrics.getFont().getStyle(), metrics.getFont().getSize());
      font.setHeader(header);
      font.setLIType(liType);
      font.setUnderline(isUnderline());
      font.setStrikethrough(isStrikeThrough());
      font.setSubscript(isSubscript());
      font.setSuperscript(isSuperscript());

      return font;
   }

   /**
    * Get the header.
    * @param font the font
    */
   private HTML.Tag getHeader(Font font) {
      return (font instanceof RichTextFont) ?
         ((RichTextFont) font).getHeader() : null;
   }

   /**
    * Get the liType.
    * @param font the font
    */
   private String getLIType(Font font) {
      return (font instanceof RichTextFont) ?
         ((RichTextFont) font).getLIType() : null;
   }

   /**
    * Set the view's metrics font.
    * @param v the GlyphView object
    */
   void sync(GlyphView v) {
      Font f = v.getFont();

      if((metrics == null) || (!f.equals(metrics.getFont()))) {
         // fetch a new FontMetrics
         Container c = v.getContainer();
         metrics = (c != null) ? c.getFontMetrics(f) : Toolkit
               .getDefaultToolkit().getFontMetrics(f);
      }
   }

   FontMetrics metrics;
}
