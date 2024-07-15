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
package inetsoft.util;

import inetsoft.uql.asset.AssetContent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

import java.util.Properties;

public class SheetTransformListener implements TransformListener {
   @Override
   public void transform(Document doc, String cname) {
      transform(doc, cname, null, null);
   }

   @Override
   public void transform(Document doc, String cname, String sourceName, TransformListener trans) {
      // no need to transform if assemblies are dropped.
      if(trans == AssetContent.CONTEXT) {
         return;
      }

      String ws = "inetsoft.uql.asset.Worksheet";
      String vs = "inetsoft.uql.viewsheet.Viewsheet";
      String type = null;

      if(cname.startsWith(ws)) {
         type = TransformerManager.WORKSHEET;
      }
      else if(cname.startsWith(vs)) {
         type = TransformerManager.VIEWSHEET;
      }

      if(type == null) {
         return;
      }

      try {
         TransformerManager transf = TransformerManager.getManager(type);
         Properties propsOut = new Properties();

         if(sourceName != null) {
            propsOut.setProperty("sourceName", sourceName);
         }

         transf.setProperties(propsOut);
         transf.transform(doc);
      }
      catch(Exception e) {
         LOG.error("Failed to transform {} XML {}", type, sourceName, e);
      }
   }

   private static final Logger LOG =
      LoggerFactory.getLogger(SheetTransformListener.class);
}