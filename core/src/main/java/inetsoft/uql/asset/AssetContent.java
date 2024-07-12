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
package inetsoft.uql.asset;

import inetsoft.util.TransformListener;
import org.w3c.dom.*;

import java.util.Arrays;

/**
 * This enum defines the different asset contents for loading.
 *
 * @version 10.2
 * @author InetSoft Technology Corp
 */
public enum AssetContent implements TransformListener {
   ALL(),
   CONTEXT(new String[] {"assemblies", "oneAssembly"}, new String[] {"state"}),
   NO_DATA(new String[] {"oneAssembly", "embeddedData"},
           new String[] {"oneAssembly", "assembly", "embeddedDatas"},
           new String[] {"embeddedImage"});

   AssetContent(String[]... rmtags) {
      this.rmtags = rmtags;
   }

   /**
    * Trim the XML tree.
    */
   @Override
   public void transform(Document node, String cname) {
      for(String[] tags : rmtags) {
         trim0(node.getDocumentElement(), tags);
      }
   }

   /**
    * Trim the XML tree.
    */
   @Override
   public void transform(Document node, String cname, String sourceName, TransformListener trans) {
      transform(node, cname);
   }

   /**
    * Remove the elements with the tags.
    */
   private void trim0(Element node, String[] tags) {
      String[] childTags = Arrays.copyOfRange(tags, 1, tags.length);
      String tag = tags[0];
      NodeList list = node.getElementsByTagName(tag);

      for(int i = list.getLength() - 1; i >= 0; i--) {
         final Element elem = (Element) list.item(i);

         if(childTags.length > 0) {
            trim0(node, childTags);
         }
         else {
            Node parentNode = elem.getParentNode();

            if(parentNode != null) {
               parentNode.removeChild(elem);
            }
         }
      }
   }

   private final String[][] rmtags;
}
