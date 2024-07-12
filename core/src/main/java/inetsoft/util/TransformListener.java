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
package inetsoft.util;

import org.w3c.dom.Document;

/**
 * Define an object which listens when getXMLSerializable.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public interface TransformListener {
   /**
    * Invoked when the target of the listener need transform.
    */
   public void transform(Document doc, String cname);

   /**
    * Invoked when the target of the listener need transform.
    * @param sourceName the source (e.g. asset path) to be transformed
    * @param trans the current transformation requested by caller.
    */
   public void transform(Document doc, String cname, String sourceName, TransformListener trans);
}
