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
package inetsoft.uql.xmla;

import inetsoft.uql.asset.SNamedGroupInfo;
import inetsoft.uql.erm.DataRef;

import java.util.List;
import java.util.Map;

/**
 * SQLExecuteHandler2 is responsible for XMLA execute method, mainly for named
 * group(calculated member).
 *
 * @version 10.3
 * @author InetSoft Technology Corp
 */
class SQLExecuteHandler2 extends SQLExecuteHandler {
   /**
    * Constructor.
    * @param handler XMLAHandler.
    */
   public SQLExecuteHandler2(XMLAHandler handler) {
      super(handler);
   }

   /**
    * Process caption for named group.
    */
   @Override
   protected MemberObject processCaption(DataRef ref, MemberObject mobj) {
      SNamedGroupInfo groupInfo = XMLAUtil.getGroupInfo(ref);

      if(groupInfo == null || groupInfo.isEmpty()) {
         super.processCaption(ref, mobj);
         return mobj;
      }

      boolean groupMember = false;

      if(query.getMemberRefs(XMLAUtil.getEntity(ref)).length > 1) {
         String[] groups = groupInfo.getGroups();

         for(int i = 0; i < groups.length; i++) {
            List<String> groupValues = groupInfo.getGroupValue(groups[i]);
            String caption = mobj.caption;
            String fullCaption = getFullCaption(mobj, query);
            fullCaption = XMLAUtil.getDisplayName(fullCaption);

            if(groupValues.contains(caption) ||
               groupValues.contains(fullCaption))
            {
               mobj = (MemberObject) mobj.clone();
               mobj.caption = groups[i];
               groupMember = true;
               break;
            }
         }
      }

      if(groupMember) {
         mobj.fullCaption = XMLAUtil.isDisplayFullCaption() ?
            "[" +  mobj.caption + "]" : mobj.caption;
         mobj.uName = XMLAUtil.getEntity(ref) + ".[" +  mobj.caption + "]";
      }
      else {
         mobj = super.processCaption(ref, mobj);
      }

      return mobj;
   }

   /**
    * Fill level name to member object.
    */
   @Override
   protected void fillLevelName(MemberObject memberObj) {
      DataRef level = getDimensionLevel(memberObj);

      if(level == null) {
         super.fillLevelName(memberObj);
         return;
      }

      memberObj.lName = level.getAttribute();
   }

   /**
    * Fill level number to member object.
    */
   @Override
   protected void fillLevelNumber(MemberObject memberObj) {
      DataRef level = getDimensionLevel(memberObj);

      if(level == null) {
         super.fillLevelNumber(memberObj);
         return;
      }

      memberObj.lNum = query.getLevelNumber(level);
   }

   /**
    * Find ancestor.
    */
   @Override
   protected MemberObject findAncestor(MemberObject mobj, int offset) {
      Map<Integer, MemberObject> ancestors = query.getAncestor(mobj.caption);

      if(ancestors == null) {
         return super.findAncestor(mobj, offset);
      }

      return ancestors.get(offset);
   }

   /**
    * Get level data ref of this calculated member.
    */
   private DataRef getDimensionLevel(MemberObject calcMember) {
      DataRef[] refs = query.getMemberRefs(calcMember.hierarchy);
      int idx = refs.length - 1;
      DataRef lowestRef = refs[idx];
      SNamedGroupInfo groupInfo = XMLAUtil.getGroupInfo(lowestRef);

      for(int i = idx - 1; i >= 0 && groupInfo == null; i--) {
         if(query.diffLevel(refs[i], lowestRef) != 0) {
            break;
         }

         groupInfo = XMLAUtil.getGroupInfo(refs[i]);

         if(groupInfo != null) {
            break;
         }
      }

      return groupInfo != null &&
         groupInfo.contains(trimSuffix(calcMember.caption)) ?
         lowestRef : null;
   }
}
