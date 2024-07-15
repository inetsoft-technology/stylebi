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
package inetsoft.report.io.helper;

import inetsoft.report.filter.*;
import inetsoft.util.Tool;
import org.w3c.dom.Element;

/**
 * This class read highligh attribute from template file.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class HighlightHelper extends ReportHelper {
   /**
    * Parse the tag node and return a HighlightAttr created.
    * @param tag the xml node with tag name "HighlightAttr".
    * @param param this parameter should be null.
    */
   @Override
   public Object read(Element tag, Object param) throws Exception {
      String name = Tool.getAttribute(tag, "name");

      if(name == null) {
         return null;
      }

      String type = Tool.getAttribute(tag, "type");

      if(type.equalsIgnoreCase(Highlight.TABLE)) {
         ColumnHighlight attr = new ColumnHighlight();
         attr.parseXML(tag);
         return attr;
      }
      else {
         TextHighlight attr = new TextHighlight();
         attr.parseXML(tag);
         return attr;
      }
   }
}

