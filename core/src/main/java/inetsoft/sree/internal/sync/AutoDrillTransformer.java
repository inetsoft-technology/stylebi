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
package inetsoft.sree.internal.sync;

import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.sync.UpdateDependencyHandler;
import org.apache.commons.lang.StringUtils;
import org.w3c.dom.*;

import java.io.File;
import java.util.HashMap;

public class AutoDrillTransformer implements Transformer {
   public AutoDrillTransformer() {
      super();
   }

   public void process(File file, HashMap<String, String> drillQueryMap) {
      Document doc = UpdateDependencyHandler.getXmlDocByFile(file);
      transform(doc, drillQueryMap);
      updateFile(doc, file);
   }

   public void processDocument(Document doc, String wsIdentifier, String qname) {
      HashMap<String, String> map = new HashMap<>();
      map.put(qname, wsIdentifier);
      transform(doc, map);
   }

   private void transform(Document doc, HashMap<String, String> drillQueryMap) {
      Element root = doc.getDocumentElement();
      NodeList list = UpdateDependencyHandler.getChildNodes(root, "//XDrillInfo/drillPath/subquery");

      for(int i = 0; i < list.getLength(); i++) {
         Element elem = (Element) list.item(i);
         String qname = elem.getAttribute("qname");

         if(!StringUtils.isEmpty(qname) && drillQueryMap.containsKey(qname)) {
            elem.removeAttribute("qname");
            AssetEntry entry = AssetEntry.createAssetEntry(drillQueryMap.get(qname));
            Element wsNode = createAssetEntryElement(elem.getOwnerDocument(), entry);

            if(wsNode != null) {
               elem.appendChild(wsNode);
            }
         }
      }
   }
}
