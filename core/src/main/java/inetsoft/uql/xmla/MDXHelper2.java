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

import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.erm.ExpressionRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Helper class of XMLAQuery. It defines the API for generating MDX sentence
 * from an XMLAQuery, mainly for named group, calc measure and statistical op.
 *
 * @version 10.3
 * @author InetSoft Technology Corp
 */
public class MDXHelper2 extends MDXHelper {
   /**
    * Constructor.
    */
   protected MDXHelper2(XMLAQuery query) {
      super(query);
   }

   /**
    * Get measure name for rows.
    */
   @Override
   protected String getMeasureName(DataRef measure) {
      if(!(query instanceof XMLAQuery2)) {
         return super.getMeasureName(measure);
      }

      AggregateInfo ainfo = ((XMLAQuery2) query).getAggregateInfo();
      AggregateRef aref = ainfo.getAggregate(measure);

      if(aref != null && !AggregateFormula.NONE.equals(aref.getFormula())) {
         return XMLAUtil.getCalcName(aref);
      }
      else {
         ExpressionRef expRef = AssetUtil.getExpressionRef(measure);

         if(expRef != null) {
            return XMLAUtil.getCalcName(expRef);
         }
      }

      String measureName = XMLAUtil.getAttribute(measure);
      return getNoneFormulaMeasure(measureName);
   }

   /**
    * Get selection set name if any.
    */
   @Override
   protected String getSelectionSetName(String dim) {
      String selectionSet = super.getSelectionSetName(dim);
      String selectionSetAfterGroup = selectionSetNames.get(dim);

      return selectionSetAfterGroup == null ?
         selectionSet : selectionSetAfterGroup;
   }

   /**
    * Get selection set content.
    */
   @Override
   protected String getSelectionSetContent(String dim) {
      String selectionSetAfterGroup = selectionSetNames.get(dim);

      return selectionSetAfterGroup == null ?
         super.getSelectionSetContent(dim) : selectionSetAfterGroup;
   }

   /**
    * Check if has named group.
    */
   private boolean hasNamedGroup(String[][] calcMembers) {
      return calcMembers != null && calcMembers.length > 0;
   }

   /**
    * Generate calc member and measure in with section.
    */
   @Override
   protected String generateWithSection() {
      String conditionStr = super.generateWithSection();

      if(!(query instanceof XMLAQuery2)) {
         return conditionStr;
      }

      StringBuilder buffer = new StringBuilder();
      boolean hasCondition = conditionStr.trim().length() > 0;

      if(hasCondition) {
         buffer.append(conditionStr);
      }

      AggregateInfo ainfo = ((XMLAQuery2) query).getAggregateInfo();

      for(int i = 0; i < query.getMembersCount(); i++) {
         DataRef ref = query.getMemberRef(i);
         GroupRef group = ainfo.getGroup(ref);

         if(group == null) {
            continue;
         }

         buffer.append(generateCalcMembers(ref));
      }

      ArrayList<String> statements = new ArrayList<>();

      for(int i = 0; i < query.getMeasuresCount(); i++) {
         DataRef ref = query.getMeasureRef(i);
         AggregateRef[] aggs = ainfo.getAggregates(ref);

         for(int j = 0; j < aggs.length; j++) {
            AggregateRef aref = aggs[j];
            ColumnRef column = (ColumnRef) aref.getDataRef();
            ExpressionRef expRef = AssetUtil.getExpressionRef(column);

            if(expRef != null) {
               String statement = generateMeasureMember(expRef);

               if(!statements.contains(statement)) {
                  statements.add(statement);
                  buffer.append(statement);
               }
            }

            buffer.append(generateStatisticalMember(aref));
         }
      }

      Collection<Dimension> dims = query.getSelectedDimensions();
      Iterator<Dimension> it = dims.iterator();

      while(it.hasNext()) {
         String hierarchy = it.next().getIdentifier();
         boolean sql2000 = buffer.indexOf(".&[") < 0;
         buffer.append(generateGroupSelection(hierarchy));
         buffer.append(generateFinalSelection(hierarchy, sql2000));
      }

      String str = buffer.toString();

      if(!hasCondition && str.trim().length() > 0) {
         str = "with " + str;
      }

      return str;
   }

   /**
    * Generate member in slicer axis.
    */
   @Override
   protected String generateSlicerMember(String dim, String filterStr) {
      slicerMembers.put(dim, filterStr);

      return super.generateSlicerMember(dim, filterStr);
   }

   /**
    * Get filter string for a dimension on slicer axis.
    */
   @Override
   protected String getSlicerFilter(String filterStr) {
      if(filterStr == null || filterStr.indexOf(".&[") < 0) {
         return super.getSlicerFilter(filterStr);
      }

      StringBuilder buffer = new StringBuilder();
      buffer.append("aggregate(union(");
      buffer.append(filterStr);
      buffer.append(",{}))");

      return buffer.toString();
   }

   /**
    * Get virtual set name.
    */
   private String getVirtualSetName(String dim) {
      return "[virtual_set_" + convertDimension(
         XMLAUtil.encodingXML(dim, encoding1)) + "]";
   }

   /**
    * Generate final selection for measure aggregation.
    */
   private String generateFinalSelection(String dim, boolean sql2000) {
      String selectionSet = super.getSelectionSetName(dim);
      String[][] calcMembers = groupMembers.get(dim);
      String setName = hasNamedGroup(calcMembers) ?
         calcMembers[0][5] : selectionSet;

      if(setName == null || setName.trim().length() == 0) {
         return "";
      }

      String newSetName = setName.substring(0, setName.length() - 1);
      newSetName = newSetName + "____final___]";

      StringBuilder buffer = new StringBuilder();
      buffer.append(" set ");
      buffer.append(newSetName);
      buffer.append(" as 'distinct(generate(");
      buffer.append(setName);
      buffer.append(", ");

      if(sql2000) {
         buffer.append("StrToSet(");
      }

      for(int i = 0; calcMembers != null && i < calcMembers.length; i++) {
         String memberName = calcMembers[i][1];
         buffer.append("IIF(");
         buffer.append(dim);
         buffer.append(".currentmember IS ");
         buffer.append(memberName);
         buffer.append(", ");
         String current = "{" + getCurrentMember(dim) + "}";
         current = sql2000 ? setToStr(current) : current;
         buffer.append(current);
         buffer.append(", ");
      }

      DataRef[] refs = query.getMemberRefs(dim);
      String level = XMLAUtil.getAttribute(refs[refs.length - 1]);
      String ancestor = "ancestor(" + getCurrentMember(dim) + "," + level + ")";
      String cond = "count({" + ancestor + "}, INCLUDEEMPTY)=0";
      String set1 = "{" + getCurrentMember(dim) + "}";
      String set2 = "{" + ancestor + "}";
      String iif = generateIIF(cond, set1, set2, sql2000, false);
      buffer.append(iif);

      for(int i = 0; calcMembers != null && i < calcMembers.length; i++) {
         buffer.append(")");
      }

      if(sql2000) {
         buffer.append(")");
      }

      buffer.append("))'");
      selectionSetNames.put(dim, newSetName);

      return buffer.toString();
   }

   /**
    * Generate selection set after named group.
    */
   private String generateGroupSelection(String dim) {
      String[][] calcMembers = groupMembers.get(dim);

      // do not touch selection set if not grouped
      if(!hasNamedGroup(calcMembers)) {
         return "";
      }

      String selectionSet = super.getSelectionSetName(dim);
      StringBuilder buffer = new StringBuilder();

      if(selectionSet == null || selectionSet.trim().length() == 0) {
         String set = super.getSelectionSetContent(dim);
         selectionSet = getVirtualSetName(dim);
         buffer.append("set ");
         buffer.append(selectionSet);
         buffer.append(" as '");
         buffer.append(set);
         buffer.append("'");
      }

      String newSetName = selectionSet.substring(0, selectionSet.length() - 1);
      newSetName = newSetName + "____group___]";
      boolean sql2000 = "true".equals(calcMembers[0][4]);

      buffer.append(" set ");
      buffer.append(newSetName);
      buffer.append(" as 'generate(");
      buffer.append(selectionSet);
      buffer.append(", ");

      if(sql2000) {
         buffer.append("StrToSet(");
      }

      String currentMbr = getCurrentMember(dim);

      for(int i = 0; i < calcMembers.length; i++) {
         String levelName = calcMembers[i][2];
         String memberName = calcMembers[i][1];
         String ancestor = getAncestor(currentMbr, levelName);
         String rank = getRanking(ancestor, calcMembers[i][0]);
         buffer.append("IIF(");
         buffer.append(rank);
         buffer.append(", ");
         memberName = "{" + memberName + "}";
         memberName = sql2000 ? setToStr(memberName) : memberName;
         buffer.append(memberName);
         buffer.append(", ");
      }

      currentMbr = "{" + currentMbr + "}";
      currentMbr = sql2000 ? setToStr(currentMbr) : currentMbr;
      buffer.append(currentMbr);

      for(int i = 0; i < calcMembers.length; i++) {
         buffer.append(")");
      }

      if(sql2000) {
         buffer.append(")");
      }

      buffer.append(")'");
      calcMembers[0][5] = newSetName;

      return buffer.toString();
   }

   /**
    * Generate calculated measure.
    */
   private String generateStatisticalMember(AggregateRef aref) {
      DataRef measure = aref.getDataRef();
      AggregateFormula formula = aref.getFormula();
      StringBuilder buffer = new StringBuilder();
      Collection<Dimension> dims = query.getSelectedDimensions();
      Iterator<Dimension> it = dims.iterator();
      String cellChildren = null;

      while(it.hasNext()) {
         String hierarchy = it.next().getIdentifier();
         String currentChildren = getCurrentChildren(hierarchy);
         cellChildren = cellChildren == null ?  currentChildren :
            " crossjoin(" + cellChildren + ", " + currentChildren + ")";
      }

      Iterator<String> slicerDims = slicerMembers.keySet().iterator();

      while(slicerDims.hasNext()) {
         String members = slicerMembers.get(slicerDims.next());
         cellChildren = cellChildren == null ?  members :
            " crossjoin(" + cellChildren + ", " + members + ")";
      }

      buffer.append(" member ");
      String measureName = XMLAUtil.getCalcName(aref);

      if(AggregateFormula.NONE.equals(formula)) {
         measureName = getNoneFormulaMeasure(measureName);
         formula = AggregateFormula.AGGREGATE;
      }

      buffer.append(measureName);
      buffer.append(" as '");
      buffer.append(formula.getCubeExpression(cellChildren,
         XMLAUtil.getAttribute(measure)));
      buffer.append("'");

      return buffer.toString();
   }

   /**
    * Get measure name of none formula.
    */
   private String getNoneFormulaMeasure(String measure) {
      String measureName = measure;
      measureName = measureName.substring(0, measureName.length() - 1);
      measureName = measureName + XMLAUtil.NONE_FORMULA + "]";

      return measureName;
   }

   /**
    * Generate children set of current member in column axis.
    */
   private String getCurrentChildren(String hierarchy) {
      String selectionSet = super.getSelectionSetName(hierarchy);
      String[][] calcMembers = groupMembers.get(hierarchy);
      DataRef[] refs = query.getMemberRefs(hierarchy);

      // in slicer axis
      if(refs == null || refs.length == 0) {
         return "{" + getCurrentMember(hierarchy) + "}";
      }

      DataRef ref = refs[refs.length - 1];
      String ds = query.getDataSource().getName();
      String cubeName = query.getCube();
      Dimension dim = XMLAUtil.getDimension(ds, cubeName, ref);
      int levelNumber = XMLAUtil.getLevelNumber(ref, ds, cubeName);
      String offset = "1";

      if(!hasNamedGroup(calcMembers)) {
         String descendants =
            getDescendants(getCurrentMember(hierarchy), offset);
         return getStatisticalSet(descendants, selectionSet, levelNumber,
                                  hierarchy, offset);
      }

      boolean sql2000 = "true".equals(calcMembers[0][4]);
      StringBuilder buffer = new StringBuilder();

      for(int i = 0; i < calcMembers.length; i++) {
         String memberName = calcMembers[i][1];
         buffer.append("IIF(");
         buffer.append(hierarchy);
         buffer.append(".currentmember IS ");
         buffer.append(memberName);
         buffer.append(", ");
         buffer.append(sql2000 ?
            setToStr(calcMembers[i][3]) : calcMembers[i][3]);
         buffer.append(", ");
      }

      String currentChildren =
         getDescendants(getCurrentMember(hierarchy), offset);
      currentChildren = getStatisticalSet(currentChildren, selectionSet,
         levelNumber, hierarchy, offset);
      buffer.append(sql2000 ? setToStr(currentChildren) : currentChildren);

      for(int i = 0; i < calcMembers.length; i++) {
         buffer.append(")");
      }

      String str = buffer.toString();
      return sql2000 ? strToSet(str) : str;
   }

   /**
    * Get members set to do statistical operation.
    */
   private String getStatisticalSet(String currentChildren, String selection,
      int levelNumber, String hierarchy, String level)
   {
      if(selection == null) {
         return currentChildren;
      }

      boolean sql2000 = selection.indexOf(".&[") < 0;
      StringBuilder condBuffer = new StringBuilder();
      String currentMbr = getCurrentMember(hierarchy);
      condBuffer.append(getRanking(currentMbr, selection));

      for(int i = 1; i <= levelNumber; i++) {
         String ancestor = getAncestor(currentMbr, i);
         condBuffer.append(" OR ");
         condBuffer.append(getRanking(ancestor, selection));
      }

      String set1 = getDescendants(currentMbr, level);
      String set2 = "intersect(" + currentChildren + ","+ selection + ")";

      return generateIIF(condBuffer.toString(), set1, set2, sql2000);
   }

   /**
    * Generate a calc measure member.
    */
   private String generateMeasureMember(ExpressionRef expRef) {
      String expression = expRef.getExpression();

      if(expression == null || expression.length() == 0) {
         return "";
      }

      StringBuilder buffer = new StringBuilder();
      buffer.append(" member ");
      buffer.append(XMLAUtil.getCalcName(expRef));
      buffer.append(" as '");
      buffer.append(expression);
      buffer.append("'");

      return buffer.toString();
   }

   /**
    * Generate calc members for named groups on a dimension.
    */
   private String generateCalcMembers(DataRef ref) {
      SNamedGroupInfo groupInfo = groupByAncestor(ref);

      if(groupInfo == null || groupInfo.isEmpty()) {
         return "";
      }

      String hierarchy = XMLAUtil.getEntity(ref);
      String[][] calcMembers = groupMembers.get(hierarchy);

      if(hasNamedGroup(calcMembers)) {
         return "";
      }

      String[] groups = groupInfo.getGroups();
      StringBuilder buffer = new StringBuilder();
      calcMembers = new String[groups.length][6];
      groupMembers.put(hierarchy, calcMembers);

      for(int i = 0; i < groups.length; i++) {
         String setName = getSetName(groups[i], XMLAUtil.getAttribute(ref),
            GROUP_SET);
         List groupValues = groupInfo.getGroupValue(groups[i]);

         if(!shouldReGroup(ref)) {
            groupValues = fixGroupValues(ref, groupValues);
         }

         String groupSet = getGroupValues(groupValues);

         if(groupSet == null) {
            continue;
         }

         buffer.append(generateGroupSet(setName, groupSet));

         String childrenSetName = getSetName(groups[i],
            XMLAUtil.getAttribute(ref), GROUP_CHILDREN_SET);
         buffer.append(generateGroupChildrenSet(childrenSetName, setName,
                                                hierarchy));

         String aggSetName = getSetName(groups[i],
            XMLAUtil.getAttribute(ref), GROUP_AGG_SET);
         String condition = super.getSelectionSetName(hierarchy);
         boolean sql2000 = buffer.indexOf(".&[") < 0;
         buffer.append(getGroupAggChildren(aggSetName, childrenSetName,
            condition, ref, sql2000));

         String memberName = hierarchy + ".[" + groups[i] + "]";
         Collection<Dimension> dims = query.getTouchedDimensions();
         Dimension dim = XMLAUtil.getDimension(query.getDataSource().getName(),
            query.getCube(), ref);
         dims.remove(dim);

         buffer.append(generateGroupMember(memberName, aggSetName, dims));

         calcMembers[i][0] = setName;
         calcMembers[i][1] = memberName;
         calcMembers[i][2] = XMLAUtil.getAttribute(ref);
         calcMembers[i][3] = aggSetName;
         calcMembers[i][4] = sql2000 + "";
      }

      return buffer.toString();
   }

   /**
    * Check if the named group should merged into MDX.
    */
   private boolean shouldMerge(DataRef namedGroup) {
      DataRef[] refs = query.getMemberRefs(XMLAUtil.getEntity(namedGroup));

      if(refs == null || refs.length < 1) {
         return false;
      }

      int columnLevel = query.getLevelNumber(namedGroup);

      for(int i = 0; i < refs.length; i++) {
         ColumnRef col = (ColumnRef) refs[i];

         if(!col.isVisible()) {
            continue;
         }

         int level = query.getLevelNumber(col);

         if(level > columnLevel) {
            return false;
         }
      }

      return true;
   }

   /**
    * Check if a named group should re-grouped by ancestors.
    */
   private boolean shouldReGroup(DataRef namedGroup) {
      DataRef[] refs = query.getMemberRefs(XMLAUtil.getEntity(namedGroup));

      if(refs == null || refs.length < 1) {
         return false;
      }

      int columnLevel = query.getLevelNumber(namedGroup);

      for(int i = 0; i < refs.length; i++) {
         ColumnRef col = (ColumnRef) refs[i];

         if(!col.isVisible()) {
            continue;
         }

         int level = query.getLevelNumber(col);

         if(level < columnLevel) {
            return true;
         }
      }

      return false;
   }

   /**
    * Separate a group by members' ancestor.
    */
   private SNamedGroupInfo groupByAncestor(DataRef ref) {
      if(!shouldMerge(ref)) {
         return null;
      }

      SNamedGroupInfo groupInfo = XMLAUtil.getGroupInfo(ref);

      if(groupInfo == null || groupInfo.isEmpty() || !shouldReGroup(ref)) {
         return groupInfo;
      }

      DataRef[] refs = query.getMemberRefs(XMLAUtil.getEntity(ref));
      int[] offset = new int[refs.length - 1];

      for(int i = 0; i < offset.length; i++) {
         DataRef ancestor = refs[i];
         offset[i] = query.diffLevel(ref, ancestor);
      }

      SNamedGroupInfo ginfo0 = new SNamedGroupInfo();
      String[] groups = groupInfo.getGroups();

      for(int i = 0; i < groups.length; i++) {
         List groupValues = groupInfo.getGroupValue(groups[i]);
         List members = fixGroupValues(ref, groupValues);

         for(int j = 0; j < members.size(); j++) {
            if(!(members.get(j) instanceof MemberObject)) {
               continue;
            }

            MemberObject mobj = (MemberObject) members.get(j);
            Map<Integer, MemberObject> ancestors = new HashMap();

            try {
               for(int k = 0; k < offset.length; k++) {
                  MemberObject ancestor =
                     XMLAUtil.findAncestorMember(query, mobj, offset[k], false);
                  ancestors.put(offset[k], ancestor);
               }
            }
            catch(Exception ex) {
               LOG.error("Failed to find ancestor", ex);
            }

            String groupName = getGroupName(groups[i], refs, offset, ancestors);
            query.setAncestor(groupName, ancestors);
            List groupValue = ginfo0.getGroupValue(groupName);

            if(groupValue == null) {
               groupValue = new ArrayList();
               ginfo0.setGroupValue(groupName, groupValue);
            }

            groupValue.add(mobj);
         }
      }

      return ginfo0;
   }

   /**
    * Get group name.
    */
   private String getGroupName(String groupName, DataRef[] refs, int[] offset,
      Map<Integer, MemberObject> ancestors)
   {
      String suffix = "";

      for(int i = offset.length - 1; i >= 0; i--) {
         MemberObject ancestor = ancestors.get(offset[i]);
         DataRef pref = refs[i];
         SNamedGroupInfo groupInfo = XMLAUtil.getGroupInfo(pref);
         String mgroup = getMemberGroup(groupInfo, ancestor);
         suffix = mgroup == null ? suffix + "___^_^___" + ancestor.caption :
            suffix + "___^_^___" + mgroup;
      }

      return groupName + suffix;
   }

   /**
    * Get group name if a member is in it.
    */
   private String getMemberGroup(SNamedGroupInfo groupInfo, MemberObject mbr) {
      if(groupInfo == null || groupInfo.isEmpty()) {
         return null;
      }

      String[] groups = groupInfo.getGroups();

      for(int i = 0; i < groups.length; i++) {
         List groupValues = groupInfo.getGroupValue(groups[i]);
         String caption = mbr.caption;
         String fullCaption = mbr.fullCaption;
         fullCaption = XMLAUtil.getDisplayName(fullCaption);

         if(groupValues.contains(caption) ||
            groupValues.contains(fullCaption))
         {
            return groups[i];
         }
      }

      return null;
   }

   /**
    * Get expanded path(s).
    */
   @Override
   protected Set<String> getExpandedPaths(Map<String,Set<String>> expanded,
                                          DataRef ref) {
      Set<String> paths = super.getExpandedPaths(expanded, ref);
      
      if(paths == null) {
         return null;
      }
      
      SNamedGroupInfo groupInfo = XMLAUtil.getGroupInfo(ref);
      
      if(groupInfo == null || groupInfo.isEmpty()) {
         return paths;
      }
      
      Set<String> paths2 = new HashSet();
      Iterator<String> it = paths.iterator();
      
      while(it.hasNext()) {
         String path = it.next();
         int idx1 = path.lastIndexOf('[');
         int idx2 = path.lastIndexOf(']');
         String groupName = idx1 >= 0 && idx2 >=0 && idx2 > idx1 ?
            path.substring(idx1 + 1, idx2) : path;
         
         if(groupInfo.contains(groupName)) {
            List groupValues = groupInfo.getGroupValue(groupName);
            groupValues = fixGroupValues(ref, groupValues);
            
            for(int i = 0; i < groupValues.size(); i++) {
               Object obj = groupValues.get(i);
               
               if(obj instanceof MemberObject) {
                  paths2.add(((MemberObject) obj).uName);
               }
            }
         }
         else {
            paths2.add(path);
         }
      }
     
      return paths2;
   }
   
   /**
    * Fix group values.
    */
   private List fixGroupValues(DataRef ref, List groupValues) {
      List groupVals = new ArrayList();

      try {
         String cacheKey = XMLAUtil.getCacheKey(XMLAUtil.getSourceName(query),
                                                XMLAUtil.getAttribute(ref));
         XMLATableNode table =
            (XMLATableNode) XMLAUtil.getCachedResult(cacheKey);

         if(table == null) {
            return groupValues;
         }

         for(int i = 0; i < groupValues.size(); i++) {
            String name = (String) groupValues.get(i);
            MemberObject mobj = table.findMember(name, false);

            if(mobj == null) {
               groupVals.add(name);
               continue;
            }

            if(groupVals.contains(mobj)) {
               continue;
            }

            groupVals.add(mobj);
         }
      }
      catch(Exception ex) {
      }

      return groupVals;
   }

   /**
    * Generate a named group set.
    */
   private String generateGroupSet(String setName, String groupValues) {
      StringBuilder buffer = new StringBuilder();
      buffer.append(" set ");
      buffer.append(setName);
      buffer.append(" as '");
      buffer.append(groupValues);
      buffer.append("'");

      return buffer.toString();
   }

   /**
    * Generate group children set.
    */
   private String generateGroupChildrenSet(String childrenSetName,
      String groupSet, String hierarchy)
   {
      StringBuilder buffer = new StringBuilder();
      buffer.append(" set ");
      buffer.append(childrenSetName);
      buffer.append(" as 'generate(");
      buffer.append(groupSet);
      buffer.append(",");
      buffer.append(getDescendants(getCurrentMember(hierarchy), 1));
      buffer.append(")'");

      return buffer.toString();
   }

   private String getGroupAggChildren(String aggSetName, String groupChildren,
      String selection, DataRef namedGroup, boolean sql2000)
   {
      if(selection == null) {
         return " set " + aggSetName + " as '" + groupChildren + "' ";
      }

      String hierarchy = XMLAUtil.getEntity(namedGroup);
      int level = query.getLevelNumber(namedGroup);
      StringBuilder buffer = new StringBuilder();
      buffer.append(" set ");
      buffer.append(aggSetName);
      buffer.append(" as 'generate(");
      buffer.append(groupChildren);
      buffer.append(", ");

      String cond = checkGroupChild(hierarchy, selection, level);
      String set1 = "{" + getCurrentMember(hierarchy) + "}";
      String set2 = "{}";
      buffer.append(generateIIF(cond, set1, set2, sql2000));
      buffer.append(")'");

      return buffer.toString();
   }

   private String checkGroupChild(String hierarchy, String selection, int level)
   {
      StringBuilder buffer = new StringBuilder();
      buffer.append(getRanking(getCurrentMember(hierarchy), selection));

      for(int i = 1; i <= level; i++) {
         buffer.append(" OR ");
         String currentMbr = getCurrentMember(hierarchy);
         String ancestor = getAncestor(currentMbr, i);
         buffer.append(getRanking(ancestor, selection));
      }

      return buffer.toString();
   }

   /**
    * generate a named group member.
    */
   private String generateGroupMember(String memberName, String setName,
      Collection<Dimension> dims)
   {
      StringBuilder buffer = new StringBuilder();
      String cell = generateCrossjoinCell(setName, dims);
      buffer.append(" member ");
      buffer.append(memberName);
      buffer.append(" as '");
      buffer.append(generateAggregation(cell));
      buffer.append("'");

      return buffer.toString();
   }

   /**
    * Generate aggregated value for a calculated member.
    */
   private String generateAggregation(String dimensions) {
      StringBuilder buffer = new StringBuilder();
      buffer.append("IIF(count(crossjoin(");
      buffer.append(dimensions);
      buffer.append(", {[Measures].currentmember}), EXCLUDEEMPTY)=0, null, ");
      buffer.append("aggregate(");
      buffer.append(dimensions);
      buffer.append(", [Measures].currentmember))");

      return buffer.toString();
   }

   /**
    * Get cells defined by named group set and other dimension members.
    */
   private String generateCrossjoinCell(String setName,
      Collection<Dimension> dims)
   {
      String cells = setName;
      Iterator<Dimension> it = dims.iterator();

      while(it.hasNext()) {
         String dim = it.next().getIdentifier();
         StringBuilder buffer = new StringBuilder();
         buffer.append("crossjoin(");
         buffer.append(cells);
         buffer.append(", ");

         String members = slicerMembers.get(dim);

         if(members == null) {
            buffer.append("{");
            buffer.append(getCurrentMember(dim));
            buffer.append("}");
         }
         else {
            buffer.append(members);
         }

         buffer.append(")");
         cells = buffer.toString();
      }

      return cells;
   }

   /**
    * Get set name of a named group.
    */
   private String getSetName(String group, String levelName, int setType) {
      StringBuilder buffer = new StringBuilder();
      buffer.append("[");
      buffer.append(XMLAUtil.getDisplayName(levelName));
      buffer.append("_");
      buffer.append(group);

      if(setType == GROUP_CHILDREN_SET) {
         buffer.append("_children_");
      }
      else if(setType == GROUP_AGG_SET) {
         buffer.append("_aggregation_");
      }

      buffer.append(SET_SUFFIX);
      buffer.append("]");

      return buffer.toString();
   }

   /**
    * Get group values string representation.
    */
   private String getGroupValues(List groupVals) {
      if(groupVals == null || groupVals.size() == 0) {
         return null;
      }

      StringBuilder buffer = new StringBuilder();
      buffer.append("{");

      for(int i = 0; i < groupVals.size(); i++) {
         Object obj = groupVals.get(i);

         if(obj == null) {
            continue;
         }

         String str = obj + "";

         if(str.trim().length() == 0) {
            continue;
         }

         if(i > 0) {
            buffer.append(",");
         }

         buffer.append(str);
      }

      buffer.append("}");

      return buffer.toString();
   }

   /**
    * Convert set to string for sql2000.
    */
   private String setToStr(String set) {
      return "SetToStr(" + set + ")";
   }

   /**
    * Convert string to set for sql2000.
    */
   private String strToSet(String str) {
      return "StrToSet(" + str + ")";
   }

   /**
    * Get descendants of a member at the specified distance.
    */
   private String getDescendants(String member, int distance) {
      StringBuilder buffer = new StringBuilder();
      buffer.append("DESCENDANTS(");
      buffer.append(member);
      buffer.append(",");
      buffer.append(distance);
      buffer.append(")");

      return buffer.toString();
   }

   /**
    * Get ancestor of a member at the specified distance.
    */
   private String getAncestor(String member, int distance) {
      StringBuilder buffer = new StringBuilder();
      buffer.append("Ancestor(");
      buffer.append(member);
      buffer.append(",");
      buffer.append(distance);
      buffer.append(")");

      return buffer.toString();
   }

   /**
    * Generate iif function.
    */
   private String generateIIF(String cond, String set1, String set2,
                              boolean sql2000) {
      return generateIIF(cond, set1, set2, sql2000, true);
   }

   /**
    * Generate iif function.
    */
   private String generateIIF(String cond, String set1, String set2,
                              boolean sql2000, boolean force) {
      StringBuilder buffer = new StringBuilder();
      set1 = sql2000 ? setToStr(set1) : set1;
      set2 = sql2000 ? setToStr(set2) : set2;

      buffer.append("IIF(");
      buffer.append(cond);
      buffer.append(",");
      buffer.append(set1);
      buffer.append(",");
      buffer.append(set2);
      buffer.append(")");

      String iif = buffer.toString();
      return sql2000 && force? strToSet(iif) : iif;
   }

   private static final String SET_SUFFIX = "_Set";
   private static final int GROUP_SET = 0;
   private static final int GROUP_CHILDREN_SET = 1;
   private static final int GROUP_AGG_SET = 2;
   private static final Logger LOG =
      LoggerFactory.getLogger(MDXHelper2.class);
   private Map<String, String[][]> groupMembers = new Hashtable();
   private Map<String, String> selectionSetNames = new Hashtable();
   private Map<String, String> slicerMembers = new Hashtable();
}
