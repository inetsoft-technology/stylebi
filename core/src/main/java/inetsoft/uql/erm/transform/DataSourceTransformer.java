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
package inetsoft.uql.erm.transform;

import inetsoft.util.Tool;
import org.w3c.dom.Element;

/**
 * DataSourceTransformer, transforms a data source node.
 *
 * @version 10.1
 * @author InetSoft Technology Corp.
 */
public class DataSourceTransformer implements XElementTransformer {
   /**
    * Transform the element according to the descriptor.
    * @param elem the element.
    * @param descriptor the transform descriptor.
    */
   @Override
   public void transform(Element elem, TransformDescriptor descriptor) {
      Element dsNode = Tool.getChildNodeByTagName(elem, "ds_jdbc");
      int dbType = descriptor.getDBType();
      String driver = descriptor.getDriver();
      String url = descriptor.getURL();

      if(url != null && url.length() > 0) {
         dsNode.setAttribute("url", descriptor.getURL());
      }

      dsNode.setAttribute("driver", driver);
   }
}
