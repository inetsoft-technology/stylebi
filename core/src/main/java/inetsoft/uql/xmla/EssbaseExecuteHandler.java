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

import inetsoft.uql.asset.ColumnRef;

import java.security.Principal;
import java.util.*;

/**
 * EssbaseExecuteHandler is responsible for XMLA discover method.
 *
 * @version 10.3, 3/30/2010
 * @author InetSoft Technology Corp
 */
class EssbaseExecuteHandler extends ExecuteHandler {
   /**
    * Constructor.
    * @handler XMLAHandler.
    */
   public EssbaseExecuteHandler(XMLAHandler handler) {
      super(handler);
   }

   /**
    * Write down cached levels if necessary.
    */
   @Override
   protected void cacheLevelMembers(XMLAQuery query0, Principal user)
      throws Exception
   {
      if(checkCached(query0, user)) {
         return;
      }

      query0.setProperty("IgnoreLevel", "true");
      ColumnRef col = (ColumnRef) query0.getMemberRef(0);
      Dimension dim = XMLAUtil.getDimension(
         query0.getDataSource().getName(), query0.getCube(), col);
      ArrayList<MemberObject>[] members = new ArrayList[dim.getLevelCount()];

      Collection<MemberObject> c = handler.getMembers(query0, user);
      Iterator<MemberObject> it = c.iterator();

      while(it.hasNext()) {
         MemberObject mobj = it.next();

         if(members[mobj.lNum] == null) {
            members[mobj.lNum] = new ArrayList();
         }

         members[mobj.lNum].add(mobj);
      }

      for(int i = 0; i < members.length; i++) {
         for(int j = 0; j < members[i].size(); j++) {
            MemberObject mobj = members[i].get(j);
            mobj.fullCaption = XMLAUtil.getFullCaption(mobj, query0);
         }

         DimMember level = (DimMember) dim.getLevelAt(i);
         String header = dim.getName() + "." + level.getName();
         String src = XMLAUtil.getSourceName(query0);
         String ckey = XMLAUtil.getCacheKey(src,
            XMLAUtil.getLevelUName(dim.getIdentifier(), level.getUniqueName()));
         XMLAUtil.writeCachedResult(ckey,
            new XMLATableNode(members[i], header));
      }
   }
}