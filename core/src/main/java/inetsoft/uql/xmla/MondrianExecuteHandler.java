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
package inetsoft.uql.xmla;

import java.util.ArrayList;

/**
 * MondrianExecuteHandler is responsible for XMLA discover method.
 *
 * @version 10.3, 3/30/2010
 * @author InetSoft Technology Corp
 */ 
class MondrianExecuteHandler extends ExecuteHandler {
   /**
    * Constructor.
    * @handler XMLAHandler.
    */   
   public MondrianExecuteHandler(XMLAHandler handler) {
      super(handler);
   }
   
   /**
    * Get full caption.
    */
   @Override
   protected String getFullCaption(MemberObject mobj, XMLAQuery query0) {
      return mobj.uName;
   }   

   /**
    * Check if should cache all higher levels.
    */
   @Override
   protected boolean cacheHigherLevels(ArrayList<MemberObject> mbrs) {
      return false;
   }
}