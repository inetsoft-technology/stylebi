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
/*
 * This file is part of StyleBI.
 *
 * Copyright (c) 2024, InetSoft Technology Corp, All Rights Reserved.
 *
 * The software and information contained herein are copyrighted and
 * proprietary to InetSoft Technology Corp. This software is furnished
 * pursuant to a written license agreement and may be used, copied,
 * transmitted, and stored only in accordance with the terms of such
 * license and with the inclusion of the above copyright notice. Please
 * refer to the file "COPYRIGHT" for further copyright and licensing
 * information. This software and information or any other copies
 * thereof may not be provided or otherwise made available to any other
 * person.
 */
package inetsoft.util;

import org.w3c.dom.*;

import java.util.Properties;

/**
 * This interface can be used to transform xml document object.
 * For example: the document of stylereport template, source, and query.
 *
 * @version 6.1, 5/25/2004
 * @author robertl
 */
public abstract class XMLTransformer {
   /**
    * Constructor.
    */
   public XMLTransformer() {
      super();
   }

   /**
    * Get configuration properties.
    */
   public Properties getProperties() {
      return properties;
   }

   /**
    * Set configuration properties.
    */
   public void setProperties(Properties prop) {
      properties = prop == null ? new Properties() : prop;
   }

   /**
    * Return a transformed xml document
    * @param doc original xml document
    */
   public abstract Node transform(Node doc) throws Exception;

   protected void findElement(Node node, String name, FunctionCall call,
      boolean text)
   {
      boolean found = name.equals(node.getNodeName());
      NodeList list = node.getChildNodes();

      if(found && !text) {
         call.process(node);
         return;
      }

      for(int i = 0; i < list.getLength(); i++) {
         Node anode = list.item(i);

         if(!found) {
            findElement(anode, name, call, text);
         }
         else if(anode.getNodeType() == Element.TEXT_NODE ||
            anode.getNodeType() == Element.CDATA_SECTION_NODE)
         {
            call.process(anode);
            break;
         }
      }
   }

   protected static interface FunctionCall {
      public boolean process(Object obj);
   }

   protected Properties properties;
}
