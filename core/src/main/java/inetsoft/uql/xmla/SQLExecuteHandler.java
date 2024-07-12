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
package inetsoft.uql.xmla;

import java.util.ArrayList;

/**
 * SQLExecuteHandler is responsible for XMLA execute method in MS SAS.
 *
 * @version 10.3, 3/30/2010
 * @author InetSoft Technology Corp
 */
class SQLExecuteHandler extends ExecuteHandler {
   /**
    * Constructor.
    * @param handler XMLAHandler.
    */
   public SQLExecuteHandler(XMLAHandler handler) {
      super(handler);
   }

   /**
    * Get full caption.
    */
   @Override
   protected String getFullCaption(MemberObject mobj, XMLAQuery query0) {
      if(mobj.uName != null && mobj.uName.indexOf(".&[") < 0) {
         return getFullCaption(mobj.uName);
      }

      return super.getFullCaption(mobj, query0);
   }

   /**
    * Check if should cache all higher levels.
    */
   @Override
   protected boolean cacheHigherLevels(ArrayList<MemberObject> mbrs) {
      if(mbrs.size() == 0) {
         return false;
      }

      MemberObject mobj0 = mbrs.get(0);

      if(mobj0.uName != null && mobj0.uName.indexOf(".&[") >= 0) {
         return true;
      }

      return false;
   }

   /**
    * Truncate dimension name in unique name to use as caption.
    */
   private String getFullCaption(String caption) {
      int idx1 = caption.indexOf("[");
      int idx2 = caption.indexOf("].");

      if(idx1 >= 0 && idx2 > idx1) {
         return caption.substring(idx2 + 2);
      }

      return caption;
   }
}