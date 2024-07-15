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
package inetsoft.report.io.helper;

import inetsoft.report.internal.binding.DataAttr;
import inetsoft.util.Tool;
import org.w3c.dom.Element;

/**
 * This class read filter from template file.
 *
 * @version 6.0, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class DataAttrHelper extends ReportHelper {
   /**
    * Parse the tag node and return a FilterAttr created.
    * @param tag the xml node with tag name "filter".
    * @param param this parameter should be null.
    */
   @Override
   public Object read(Element tag, Object param) throws Exception {
      String cls = Tool.getAttribute(tag, "class");

      if(cls == null) {
         return null;
      }
      else {
         cls = "inetsoft.report.internal.binding." + cls;

         DataAttr attr = (DataAttr) Class.forName(cls).newInstance();
         attr.parseXML(tag);

         return attr;
      }
   }
}

