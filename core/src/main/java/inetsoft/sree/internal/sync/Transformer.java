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
import inetsoft.uql.asset.sync.DependencyTransformer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.*;

import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;

public interface Transformer {
   /**
    * write the document to file.
    */
   default void updateFile(Document doc, File file) {
      TransformerFactory transformerFactory = TransformerFactory.newInstance();
      javax.xml.transform.Transformer transformer;

      try {
         transformer = transformerFactory.newTransformer();
         DOMSource source = new DOMSource(doc);
         StreamResult result = new StreamResult(file);
         transformer.transform(source, result);
      }
      catch(Exception e) {
         throw new RuntimeException(e);
      }
   }

   default void replaceElementCDATANode(Node elem, String value) {
      DependencyTransformer.replaceElementCDATANode(elem, value);
   }

   default Element createAssetEntryElement(Document doc, AssetEntry entry)  {
      Element worksheetEntry = doc.createElement("worksheetEntry");
      Element assetEntry = doc.createElement("assetEntry");
      assetEntry.setAttribute("class", "inetsoft.uql.asset.AssetEntry");
      assetEntry.setAttribute("scope", entry.getScope() + "");
      assetEntry.setAttribute("type", entry.getType().id() + "");
      worksheetEntry.appendChild(assetEntry);

      Element path = doc.createElement("path");
      CDATASection data = doc.createCDATASection(entry.getPath());
      path.appendChild(data);
      assetEntry.appendChild(path);

      return worksheetEntry;
   }

   static final Logger LOG = LoggerFactory.getLogger(Transformer.class);
}
