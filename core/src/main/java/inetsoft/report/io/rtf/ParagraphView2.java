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
import javax.swing.text.html.HTML;
import javax.swing.text.html.ParagraphView;
import java.awt.*;

/**
 * ParagraphView2 can be used to get the header info.
 * such as H1, H2, ..., H6.
 *
 * @version 10.2, 7/21/2009
 * @author InetSoft Technology Corp
 */
public class ParagraphView2 extends ParagraphView {
   /**
    * Create a ParagraphView2.
    * @param elem Element object
    * @param header HTML.Tag object
    */
   public ParagraphView2(Element elem, HTML.Tag header) {
      super(elem);
      this.header = header;
   }

   /**
    * Paint a graphics.
    * @param g Graphics object
    * @param a Shape object
    */
   @Override
   public void paint(Graphics g, Shape a) {
      String liType = null;

      if(g.getFont() instanceof RichTextFont) {
         liType = ((RichTextFont) g.getFont()).getLIType();
      }

      RichTextFont font = new RichTextFont(g.getFont());
      font.setHeader(header);
      font.setLIType(liType);
      g.setFont(font);
      super.paint(g, a);
   }

   private HTML.Tag header;
}