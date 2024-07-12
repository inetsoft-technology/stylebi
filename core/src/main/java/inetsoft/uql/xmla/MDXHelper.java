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

import inetsoft.sree.SreeEnv;
import inetsoft.uql.*;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.NamedRangeRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Helper class of XMLAQuery. It defines the API for generating MDX sentence
 * from an XMLAQuery.
 *
 * @version 10.3
 * @author InetSoft Technology Corp
 */
public class MDXHelper {
   /**
    * Generate MDXHelper correspond to the data source type
    * specified by XMLAQuery.
    * @param query - XMLAQuery.
    * @return MDXHelper.
    */
   public static MDXHelper getMDXHelper(XMLAQuery query) {
      return query instanceof XMLAQuery2 ||
         XCube.SQLSERVER.equals(getCubeType(query)) ?
         new MDXHelper2(query) : new MDXHelper(query);
   }

   /**
    * Constructor.
    */
   protected MDXHelper(XMLAQuery query) {
      this.query = query;
   }

   /**
    * Set XMLA query.
    * @param query the specified XMLA query.
    */
   public void setXMLAQuery(XMLAQuery query) {
      this.query = query;
   }

   /**
    * Generate MDX statement.
    * @return MDX statement.
    */
   public final String generateSentence() {
      StringBuilder buffer = new StringBuilder();
      buffer.append(generateWithSection());
      buffer.append(generateSelectSection());
      buffer.append(generateFromSection());
      buffer.append(generateWhereSection());

      return XMLAUtil.encodingXML(buffer.toString(), XMLAUtil.encoding1);
   }

   /**
    * Get a map with structure dimension name=>filter node.
    */
   private HashMap<String, XNode> getFiltersMap() {
      XNode filterNode = query.getFilterNode();

      if(filterNode == null) {
         return new HashMap<>();
      }

      return ConditionListHandler.getFilters(filterNode);
   }

   /**
    * Generate drill through MDX statement.
    */
   final String[] generateDrillThrough() {
      HashMap<String, XNode> filters = getFiltersMap();

      if(filters.isEmpty()) {
         return new String[0];
      }

      Iterator<String> it = filters.keySet().iterator();
      HashMap<String, List<String>> dimsMap = new HashMap();
      List<String> measures = new ArrayList();

      while(it.hasNext()) {
         String dim = it.next();
         XNode node = filters.get(dim);
         Object val = node.getValue();

         if(val != null && val instanceof ConditionItem) {
            ConditionItem citem = (ConditionItem) val;
            DataRef ref = citem.getAttribute();

            if((ref.getRefType() & DataRef.MEASURE) == DataRef.MEASURE) {
               ref = query.convertDataRef(ref);
               measures.add(ref.getAttribute());
               continue;
            }
         }

         dimsMap.put(dim, mergeNode(node));
      }

      Iterator<List<String>> dimVals = dimsMap.values().iterator();
      List<List> drillList = null;

      while(dimVals.hasNext()) {
         List vals = dimVals.next();

         if(drillList == null) {
            drillList = new ArrayList();

            for(int i = 0; i < vals.size(); i++) {
               List list0 = new ArrayList();
               Object val = vals.get(i);
               list0.add(val == null ? null : Tool.toString(val));
               drillList.add(list0);
            }
         }
         else {
            drillList = mergeList(drillList, vals);
         }
      }

      if(drillList == null) {
         drillList = new ArrayList();

         if(measures.size() > 0) {
            drillList.add(new ArrayList());
         }
      }

      String[] drills = new String[drillList.size()];

      for(int i = 0; i < drills.length; i++) {
         StringBuilder buffer = new StringBuilder();
         buffer.append("DRILLTHROUGH MAXROWS ");
         buffer.append(SreeEnv.getProperty("olap.drillthrough.maxrows"));
         buffer.append(" SELECT ");
         buffer.append(getMDXString(drillList.get(i), measures));
         drills[i] = buffer.toString();
      }

      return drills;
   }

   /**
    * Merge list of condition values.
    */
   private List<List> mergeList(List<List> list1, List list2) {
      List<List> result = new ArrayList();

      for(int i = 0; i < list1.size(); i++) {
         for(int j = 0; j < list2.size(); j++) {
            List list0 = new ArrayList();
            Object val2 = list2.get(j);

            list0.addAll(list1.get(i));
            list0.add(val2 == null ? null : Tool.toString(val2));
            result.add(list0);
         }
      }

      return result;
   }

   /**
    * Merge node values for a same dimension.
    */
   private List<String> mergeNode(XNode node) {
      DataRef member = getAvailableMember(node);

      return mergeNode0(node, member);
   }

   /**
    * Get the lowest member which has condition and condition value is not null.
    */
   private DataRef getAvailableMember(XNode node) {
      DataRef member = null;

      if(node.getChildCount() == 0) {
         List<String> vals = getValues(getCondition(node));

         if(vals != null) {
            ConditionItem citem = (ConditionItem) node.getValue();
            return citem.getAttribute();
         }
      }
      else {
         DataRef member0 = getAvailableMember(node.getChild(0));

         for(int i = 1; member0 != null && i < node.getChildCount(); i++) {
            DataRef member1 = getAvailableMember(node.getChild(i));

            if(member1 != null && query.diffLevel(member0, member1) < 0) {
               member0 = member1;
            }
         }

         member = member0;
      }

      return member;
   }

   /**
    * Merge node values for a same dimension.
    */
   private List<String> mergeNode0(XNode node, DataRef member) {
      if(node.getChildCount() == 0) {
         List<String> vals = getValues(getCondition(node));

         if(vals == null) {
            return new ArrayList<>();
         }

         DataRef ref = getColumn(node);

         if(ref == member) {
            return vals;
         }

         XNode pnode = node.getParent();
         boolean hasAndOp = false;

         while(pnode instanceof XMLASet) {
            String relation = ((XMLASet) pnode).getRelation();

            if(relation.equals(XMLASet.AND)) {
               hasAndOp = true;
               break;
            }

            pnode = pnode.getParent();
         }

         if(!hasAndOp) {
            return vals;
         }

         int offset = query.diffLevel(member, ref);

         for(int i = 0; i < vals.size(); i++) {
            vals.set(i, "Descendants(" + vals.get(i) + ", " + offset + ")");
         }

         return vals;
      }

      List result = null;
      String relation = ((XMLASet) node).getRelation();

      XNode node0 = node.getChild(0);
      List<String> list0 = mergeNode0(node0, member);
      XNode node1 = node.getChild(1);
      List<String> list1 = mergeNode0(node1, member);
      boolean ancestor0 = containsProperty(list0, "Descendants");
      boolean ancestor1 = containsProperty(list1, "Descendants");
      boolean intersect0 = containsProperty(list0, "Intersect");
      boolean intersect1 = containsProperty(list1, "Intersect");

      if(relation.equals(XMLASet.AND)) {
         result = new ArrayList();

         if(ancestor0 || ancestor1) {
            result = getIntersectSet(list0, ancestor0, list1, ancestor1);
         }
         else if(intersect0 || intersect1) {
            result = getIntersectSet(list0, intersect0, list1, intersect1);
         }
         else {
            Iterator it = list0.iterator();

            while(it.hasNext()) {
               Object obj = it.next();

               if(list1.contains(obj)) {
                  result.add(obj);
               }
            }
         }
      }
      else {
         result = list0;
         Iterator it = list1.iterator();

         while(it.hasNext()) {
            Object obj = it.next();

            if(!result.contains(obj)) {
               result.add(obj);
            }
         }
      }

      return result;
   }

   /**
    * Get the intersect statement.
    */
   private List<String> getIntersectSet(List<String> list0, boolean contains0,
                                        List<String> list1, boolean contains1)
   {
      List<String> result = new ArrayList<>();

      for(int i = 0; i < list0.size(); i++) {
         String obj0 = list0.get(i);
         obj0 = contains0 ? obj0 : "{" + obj0 + "}";

         for(int j = 0; j < list1.size(); j++) {
            String obj1 = list1.get(j);
            obj1 = contains1 ? obj1 : "{" + obj1 + "}";
            result.add("Intersect(" + obj0 + ", " + obj1 + ")");
         }

         if(list1.size() == 0) {
            result.add(obj0);
         }
      }

      return result;
   }

   /**
    * Check if an array contains specified values.
    */
   private boolean containsProperty(List<String> arr, String prop) {
      if(arr == null || arr.size() == 0 || arr.get(0) == null) {
         return false;
      }

      return arr.get(0).startsWith(prop + "(");
   }

   /**
    * Get normal MDX String.
    */
   private String getMDXString(List<String> dimList, List<String> measureList) {
      StringBuilder buffer = new StringBuilder();
      StringBuilder buffer0 = new StringBuilder();
      buffer0.append("{");

      for(int j = 0; j < measureList.size(); j++) {
         if(j > 0) {
            buffer0.append(", ");
         }

         buffer0.append(measureList.get(j));
      }

      buffer0.append("}");
      String measures = buffer0.toString();

      if(dimList.size() < 1) {
         if(XCube.MONDRIAN.equals(getCubeType(query))) {
            throw new RuntimeException(Catalog.getCatalog().getString(
               "viewer.mondrian.drillthrough.noDimensionError"));
         }

         buffer.append(measures);
         buffer.append(" ON COLUMNS");
      }
      else {
         buffer.append(getCrossJoinSentence(dimList));
         buffer.append(" ON COLUMNS, ");
         buffer.append(measures);
         buffer.append(" ON ROWS");
      }

      buffer.append(" FROM [");
      buffer.append(query.getCube());
      buffer.append("]");

      return buffer.toString();
   }

   /**
    * Get the crossJoin sentence.
    */
   private String getCrossJoinSentence(List<String> dimList) {
      String crossjoin = null;
      Iterator<String> it = dimList.iterator();

      while(it.hasNext()) {
         String dimensionValues = "{" + it.next() + "}";

         crossjoin = crossjoin == null ?
            dimensionValues :
            "CrossJoin(" + dimensionValues + ", " + crossjoin + ")";
      }

      return XMLAUtil.encodingXML(crossjoin, XMLAUtil.encoding1);
   }

   /**
    * Generate with section.
    */
   protected String generateWithSection() {
      HashMap<String, XNode> filters = getFiltersMap();

      if(filters.isEmpty()) {
         return "";
      }

      whereList = new ArrayList();
      axisList = new ArrayList();
      setOrder = new HashMap();
      Iterator it = filters.keySet().iterator();
      StringBuilder buffer = new StringBuilder();
      buffer.append("with ");

      while(it.hasNext()) {
         String dim = (String) it.next();
         XNode node = filters.get(dim);
         String filterStr = getDimFilter(node);
         filterStr = XMLAUtil.encodingXML(filterStr, encoding0);

         if(query.getMemberRefs(dim).length > 0) {
            buffer.append("set [");
            buffer.append(setPrefix);
            buffer.append(convertDimension(
               XMLAUtil.encodingXML(dim, encoding1)));
            buffer.append("] as '");
            buffer.append(filterStr);
            buffer.append("' ");
            axisList.add(getIdentifier(dim));

            // get set order
            XNode node0 = node;

            while(node0.getChildCount() > 0) {
               node0 = node0.getChild(0);
            }

            List<String> vals = getValues(getCondition(node0));

            if(vals != null && vals.size() > 0) {
               Object obj = vals.get(0);

               if(obj != null) {
                  setOrder.put(dim, obj);
               }
            }
         }
         else {
            buffer.append(generateSlicerMember(dim, filterStr));
         }
      }

      strSet.clear();

      return buffer.toString();
   }

   /**
    * Generate member in slicer axis.
    */
   protected String generateSlicerMember(String dim, String filterStr) {
      StringBuilder buffer = new StringBuilder();
      buffer.append(" member ");
      String dimStr = dim + "." + getWhereName(dim);
      buffer.append(dimStr);
      buffer.append(" as '");
      buffer.append(getSlicerFilter(filterStr));
      buffer.append("' ");
      whereList.add(dimStr);

      return buffer.toString();
   }

   /**
    * Get filter string for a dimension on slicer axis.
    */
   protected String getSlicerFilter(String filterStr) {
      if(isCondValueSet(filterStr)) {
         int idx0 = -1, idx1 = -1;

         while((idx0 = filterStr.indexOf("{")) >= 0 &&
            (idx1 = filterStr.lastIndexOf("}")) >= 0)
         {
            filterStr = filterStr.substring(idx0 + 1, idx1);
         }

         filterStr = Tool.replaceAll(filterStr, ",", "+");
      }
      else {
         filterStr = "sum(" + filterStr + ")";
      }

      return filterStr;
   }

   /**
    * Get dimension identifier.
    */
   private String getIdentifier(String dimName) {
      Dimension dim = XMLAUtil.getDimension(query.getDataSource().getFullName(),
         query.getCube(), dimName);

      return dim == null ? dimName : dim.getIdentifier();
   }

   /**
    * Generate select section.
    */
   private String generateSelectSection() {
      StringBuilder buffer = new StringBuilder();
      buffer.append(" SELECT " + (isNoEmpty() && !isRichList() ?
         "NON EMPTY " : ""));

      Collection<Dimension> dims = query.getSelectedDimensions();
      boolean containsDim = dims.size() > 0;

      if(containsDim) {
         inetsoft.util.Queue queue = new inetsoft.util.Queue();

         Iterator it = dims.iterator();

         while(it.hasNext()) {
            Dimension dim = (Dimension) it.next();
            queue.enqueue(getDimSelection(dim.getIdentifier()));
         }

         buffer.append(walk(queue));
         buffer.append(" ON COLUMNS");
      }

      if(containsDim && query.getMeasuresCount() > 0) {
         buffer.append(",");
      }

      for(int i = 0; i < query.getMeasuresCount(); i++) {
         if(i > 0)  {
            buffer.append(", ");
         }
         else {
            buffer.append(" {");
         }

         buffer.append(getMeasureName(query.getMeasureRef(i)));
      }

      if(query.getMeasuresCount() > 0) {
         buffer.append("} ON " + (containsDim ? "ROWS" : "COLUMNS"));
      }

      return buffer.toString();
   }

   /**
    * Generate from section.
    */
   private String generateFromSection() {
      return " FROM [" + query.getCube() + "]";
   }

   /**
    * Generate where section.
    */
   private String generateWhereSection() {
      if(whereList.size() == 0) {
         return "";
      }

      StringBuilder buffer = new StringBuilder();
      buffer.append(" where (");

      for(int i = 0; i < whereList.size(); i++) {
         if(i > 0) {
            buffer.append(",");
         }

         buffer.append(whereList.get(i));
      }

      buffer.append(")");

      return buffer.toString();
   }

   /**
    * Get measure name for rows.
    */
   protected String getMeasureName(DataRef measure) {
      if("true".equals(query.getProperty("richlist"))) {
         return XMLAUtil.getAttribute(measure);
      }

      return measure.getAttribute();
   }

   /**
    * Get filter string for a dimension.
    */
   private String getDimFilter(XNode node) {
      StringBuilder buffer = new StringBuilder();
      String dim = ConditionListHandler.getDimension(node);
      DataRef[] refs = query.getMemberRefs(dim, false);
      buffer.append("{");

      if(refs.length > 0) {
         String filterStr = null;

         for(int i = 0; i < refs.length; i++) {
            int offset = i + 1 == refs.length ?
               0 : query.diffLevel(refs[i + 1], refs[i]);
            int offset1 = i == 0 ? 1 :  query.diffLevel(refs[i], refs[i - 1]);
            String str = getColumnFilter(node, refs[i], offset1, offset, dim);

            if(str == null) {
               continue;
            }

            filterStr = filterStr == null ?
               str : "Union(" + filterStr + "," + str + ")";
         }

         if(filterStr != null) {
            buffer.append(filterStr);
         }
      }
      else {
         buffer.append(getFilterFromXNode(node));
      }

      buffer.append("}");

      return buffer.toString();
   }

   /**
    * Get filter string only from filter node, and ignore selected dimensions.
    */
   private String getFilterFromXNode(XNode node) {
      return getFilterString(node, null, null, 0, getLowestMember(node, null));
   }

   /**
    * Get filter string of a column.
    */
   private String getColumnFilter(XNode node, DataRef column, int poffset,
      int offset, String dim)
   {
      if(column == null) {
         return null;
      }

      String higherMembers = offset == 0 ?
         null : getHigherMembers(column, poffset, offset);

      String mbrs = higherMembers == null ?
         getUMembers(query, column, poffset) : higherMembers;

      if(node == null) {
         return mbrs;
      }

      return getFilterString(node, column, higherMembers, poffset,
                             getLowestMember(node, dim));
   }

   /**
    * Get higher level members with no descendants.
    */
   private String getHigherMembers(DataRef column, int poffset, int offset) {
      Map<String,Set<String>> expanded = query.getExpandedPaths();
      String allMembers = getUMembers(query, column, poffset);

      if(expanded != null) {
         Set<String> paths = getExpandedPaths(expanded, column);

         if(paths != null && paths.size() > 0) {
            StringBuilder buffer = new StringBuilder();
            buffer.append("{");

            for(String path : paths) {
               buffer.append(path);
               buffer.append(",");
            }

            buffer.deleteCharAt(buffer.length() - 1);
            buffer.append("}");
            String expmbrs = buffer.toString();
            expmbrs = getHigherMembers0(column, offset, expmbrs, true);

            return "Except(" + allMembers + ",{" + expmbrs + "})";
         }
      }

      return getHigherMembers0(column, offset, allMembers, false);
   }

   private String getHigherMembers0(DataRef column, int offset, String members,
      boolean hasChildren)
   {
      String op = hasChildren ? ">" : "=";
      StringBuilder buffer = new StringBuilder();
      buffer.append("Filter(");
      buffer.append(members);
      buffer.append(", count(Descendants(");
      buffer.append(XMLAUtil.getEntity(column));
      buffer.append(".currentMember, ");
      buffer.append(offset);
      buffer.append("), INCLUDEEMPTY)");
      buffer.append(op);
      buffer.append("0)");

      return buffer.toString();
   }

   /**
    * Get expanded path(s).
    */
   protected Set<String> getExpandedPaths(Map<String,Set<String>> expanded,
                                          DataRef ref) {
      String name = getFieldName(ref);
      return expanded.get(name);
   }

   /**
    * Get the reference to a dimension level members.
    */
   private String getUMembers(XMLAQuery query, DataRef column, int offset) {
      DataRef upper = query.getUpperMember(column, offset);
      Map<String,Set<String>> expanded = query.getExpandedPaths();

      if(expanded != null && upper != null) {
         Set<String> paths = getExpandedPaths(expanded, upper);

         if(paths != null && paths.size() > 0) {
            String expChildren = "";

            for(String path : paths) {
               String mbr = getDescendants(path, "" + offset);

               if(expChildren.length() == 0) {
                  expChildren = mbr;
               }
               else {
                  expChildren = expChildren + "," + mbr;
               }
            }

            return "{" + expChildren + "}";
         }
      }

      return XMLAUtil.getLevelUName(query, column) + ".members";
   }

   /**
    * Get the expand paths.
    */
   public static String getFieldName(DataRef column) {
      if(column instanceof ColumnRef &&
         getCaption((ColumnRef) column) != null)
      {
         return getCaption((ColumnRef) column);
      }

      return XMLAUtil.getDisplayName(XMLAUtil.getAttribute(column));
   }

   /**
    * Get the caption of the column.
    */
   private static String getCaption(ColumnRef column) {
      String caption = column.getCaption();

      if(caption != null) {
         return caption;
      }

      DataRef ref0 = column.getDataRef();

      if(ref0 instanceof NamedRangeRef) {
         ref0 = ((NamedRangeRef) ref0).getDataRef();

         if(ref0 instanceof AttributeRef) {
            caption = ((AttributeRef) ref0).getCaption();
         }
      }

      return caption;
   }

   /**
    * Get member lowest level.
    */
   private DataRef getLowestMember(XNode node, String dim) {
      DataRef column = null;

      if(dim != null) {
         DataRef[] refs = query.getMemberRefs(dim);
         column = refs == null || refs.length == 0 ?
            column : refs[refs.length - 1];
      }

      if(node.getChildCount() == 0) {
         ConditionItem citem = (ConditionItem) node.getValue();
         DataRef ref = citem.getAttribute();

         if(column != null && query.diffLevel(citem.getAttribute(), column) < 0)
         {
            ref = column;
         }

         return ref;
      }

      DataRef ref0 = getLowestMember(node.getChild(0), dim);

      for(int i = 1; i < node.getChildCount(); i++) {
         DataRef refi = getLowestMember(node.getChild(i), dim);

         if(query.diffLevel(ref0, refi) < 0) {
            ref0 = refi;
         }
      }

      return ref0;
   }

   /**
    * Get filter string for a dimension.
    */
   private String getFilterString(XNode node, DataRef column,
      String higherMembers, int poffset, DataRef lowest)
   {
      if(node == null) {
         return null;
      }

      // get values for a leaf node
      if(node.getChildCount() == 0) {
         Condition cond = getCondition(node);
         List<String> vals = getValues(cond);

         if(vals == null) {
            return null;
         }

         String result = getSetString(vals);

         if(lowest == null) {
            return result;
         }

         DataRef column0 = getColumn(node);

         if(cond.isNegated()) {
            String allMembers = getUMembers(query, column0, poffset);
            result = "Except(" + allMembers + ", " + result + ")";
         }

         // higher level selected
         if(higherMembers != null) {
            int offset = query.diffLevel(column0, column);

            if(offset > 0) {
               result = filterDescendants(column0, column, result, higherMembers);
            }
            else if(offset < 0) {
               result = filterAncestors(column0, result, higherMembers);
            }
            else {
               result = "Intersect(" + higherMembers + "," + result + ")";
            }
         }
         else {
            int offset = query.diffLevel(column0, lowest);

            if(offset > 0) {
               result = result;
            }
            else if(offset < 0) {
               result = getAncestor(lowest, -offset, result);
            }
            else {
               result = column != null && query.diffLevel(column, lowest) == 0 &&
                  higherMembers != null ?
                  "Intersect(" + higherMembers + "," + result + ")" : result;
            }
         }

         return result;
      }

      XMLASet set = (XMLASet) node;
      String relation = set.getRelation();

      XNode node0 = node.getChild(0);
      String filter0 = getFilterString(node0, column, higherMembers, poffset, lowest);

      XNode node1 = node.getChild(1);
      String filter1 = getFilterString(node1, column, higherMembers, poffset, lowest);

      if(filter0 == null || filter1 == null) {
         return filter0 != null ? filter0 : (filter1 != null ? filter1 : null);
      }
      else if(relation.equals(XMLASet.AND)) {
         return "Intersect(" + filter0 + "," + filter1 + ")";
      }
      else {
         return "Union(" + filter0 + "," + filter1 + ")";
      }
   }

   /**
    * Generate descendants filtered by condition.
    */
   private String filterDescendants(DataRef condRef, DataRef column,
                                    String condSet, String colSet) {
      String hierarchy = XMLAUtil.getEntity(condRef);
      String level = XMLAUtil.getLevelUName(query, condRef);
      StringBuilder buffer = new StringBuilder();
      buffer.append("generate(");
      buffer.append(colSet);
      buffer.append(", ");
      buffer.append("intersect(");
      buffer.append(getDescendants(getCurrentMember(hierarchy), level));
      buffer.append(", ");
      buffer.append(condSet);
      buffer.append(")");
      buffer.append(")");

      return buffer.toString();
   }

   /**
    * Generate ancestors filtered by condition.
    */
   private String filterAncestors(DataRef condRef, String condSet,
                                  String colSet) {
      StringBuilder buffer = new StringBuilder();
      String hierarchy = XMLAUtil.getEntity(condRef);
      String level = XMLAUtil.getLevelUName(query, condRef);
      buffer.append("Filter(");
      buffer.append(colSet);
      buffer.append(", ");

      String ancestor = getAncestor(getCurrentMember(hierarchy), level);
      buffer.append(getRanking(ancestor, condSet));
      buffer.append(")");

      return buffer.toString();
   }

   /**
    * Get current member.
    */
   protected String getCurrentMember(String hierarchy) {
      return  hierarchy + ".currentmember";
   }

   /**
    * Get ancestor of a member at the specified level.
    */
   protected String getAncestor(String member, String levelName) {
      StringBuilder buffer = new StringBuilder();
      buffer.append("Ancestor(");
      buffer.append(member);
      buffer.append(",");
      buffer.append(levelName);
      buffer.append(")");

      return buffer.toString();
   }

   /**
    * Get descendants of a member at the specified level.
    */
   protected String getDescendants(String member, String levelName) {
      StringBuilder buffer = new StringBuilder();
      buffer.append("DESCENDANTS(");
      buffer.append(member);
      buffer.append(",");
      buffer.append(levelName);
      buffer.append(")");

      return buffer.toString();
   }

   /**
    * Get ranking condition string.
    */
   protected String getRanking(String member, String set) {
      StringBuilder buffer = new StringBuilder();
      buffer.append("Rank(");
      buffer.append(member);
      buffer.append(",");
      buffer.append(set);
      buffer.append(") > 0 ");

      return buffer.toString();
   }

   /**
    * Get ancestor.
    */
   private String getAncestor(DataRef column, int offset, String set) {
      offset = Cube.SAP.equals(getCubeType(query)) ? -offset : offset;
      StringBuilder buffer = new StringBuilder();
      buffer.append("Filter(");
      buffer.append(getLowestMembers(column));
      buffer.append(", Rank(Ancestor(");
      buffer.append(XMLAUtil.getEntity(column));
      buffer.append(".currentmember, ");
      buffer.append(offset);
      buffer.append("), ");
      buffer.append(set);
      buffer.append(") > 0)");

      return buffer.toString();
   }

   /**
    * Get the lowest level members.
    */
   private String getLowestMembers(DataRef column) {
      int offset = 1;
      DataRef[] refs = query.getMemberRefs(XMLAUtil.getEntity(column));

      if(refs != null && refs.length > 1) {
         offset = query.diffLevel(refs[refs.length - 1], refs[refs.length - 2]);
      }

      return getUMembers(query, column, offset);
   }

   /**
    * Get cube type.
    */
   private static String getCubeType(XMLAQuery query) {
      if(query != null) {
         return XMLAUtil.getCube(
            query.getDataSource().getFullName(), query.getCube()).getType();
      }

      return "";
   }

   /**
    * Get condition from XNode if possible.
    */
   private Condition getCondition(XNode node) {
      Object val = node.getValue();

      if(val == null) {
         return null;
      }

      ConditionItem citem = (ConditionItem) val;

      return citem.getCondition();
   }

   /**
    * Get dimension members from condition item.
    */
   private List<String> getValues(Condition cond) {
      if(cond.getOperation() == Condition.EQUAL_TO ||
         cond.getOperation() == Condition.ONE_OF)
      {
         return cond.getValues().stream()
                    .map(String.class::cast)
                    .collect(Collectors.toCollection(ArrayList::new));
      }

      return null;
   }

   /**
    * Convert string array to Set format string necessary in MDX.
    */
   private String getSetString(List vec) {
      if(vec == null) {
         return null;
      }

      int size = vec.size();

      if(size == 0) {
         return null;
      }

      StringBuilder buffer = new StringBuilder();
      buffer.append("{");

      for(int i = 0; i < size; i++) {
         if(i > 0) {
            buffer.append(",");
         }

         buffer.append(vec.get(i));
      }

      buffer.append("}");
      String str = buffer.toString();
      strSet.add(str);

      return str;
   }

   /**
    * Get selection set name if any.
    */
   protected String getSelectionSetName(String dim) {
      if(!axisList.contains(dim)) {
         return null;
      }

      return "[" + convertDimension(setPrefix +
         XMLAUtil.encodingXML(dim, encoding1)) + "]";
   }

   /**
    * Get selection set content.
    */
   protected String getSelectionSetContent(String dim) {
      DataRef[] refs = query.getMemberRefs(dim, false);
      String filter = null;

      for(int i = 0; i < refs.length; i++) {
         int offset = i + 1 == refs.length ?
            0 : query.diffLevel(refs[i + 1], refs[i]);
         int offset1 = i == 0 ? 1 :  query.diffLevel(refs[i], refs[i - 1]);
         String filter0 = getColumnFilter(null, refs[i], offset1, offset, dim);

         if(filter == null) {
            filter = filter0;
         }
         else {
            filter = "Union(" + filter + "," + filter0 + ")";
         }
      }

      return filter;
   }

   /**
    * Get dimension representation.
    */
   protected String getDimSelection(String dim) {
      if(axisList.contains(dim)) {
         String str = getSelectionSetName(dim);

         if(setOrder.get(dim) != null) {
            StringBuilder buffer = new StringBuilder();
            buffer.append("order(");
            buffer.append(str);
            buffer.append(", ");
            buffer.append(setOrder.get(dim));
            buffer.append(") ");
            str = buffer.toString();
         }

         return str;
      }

      return getSelectionSetContent(dim);
   }

   /**
    * Get no empty property.
    */
   public boolean isNoEmpty() {
      return !("false".equals(query.getProperty("noEmpty")));
   }

   /**
    * Get no richlist property.
    */
   public boolean isRichList() {
      return "true".equals(query.getProperty("richlist"));
   }

   /**
    * Walk through all dimensions.
    */
   private String walk(inetsoft.util.Queue queue) {
      Stack stack = new Stack();

      while(queue.peek() != null) {
         if(stack.empty()) {
            stack.push(queue.dequeue());
            continue;
         }

         if(queue.peek() == null) {
            break;
         }
         else {
            String istack = (String) stack.pop();
            String iqueue = (String) queue.dequeue();
            String result = "CrossJoin(" + istack + ", " + iqueue + ")";
            queue.add(0, result);
         }
      }

      return stack.empty() ? null : (String) stack.pop();
   }

   /**
    * Get column data ref of the node.
    */
   private DataRef getColumn(XNode node) {
      if(node.getChildCount() == 0) {
         ConditionItem citem = (ConditionItem) node.getValue();
         return citem.getAttribute();
      }

      return null;
   }

   /**
    * Get filter with a suffix to work well in essbase.
    */
   private String getWhereName(String dim) {
      StringBuilder buffer = new StringBuilder();
      buffer.append("[__Filter_");
      buffer.append(convertDimension(dim));
      buffer.append("__]");

      return buffer.toString();
   }

   /**
    * Get pure dimension name.
    */
   protected String convertDimension(String dim) {
      if(dim == null) {
         return null;
      }

      dim = Tool.replaceAll(dim, "[", "");
      dim = Tool.replaceAll(dim, "]", "_");
      dim = Tool.replaceAll(dim, ".", "_");

      return dim;
   }

   private boolean isCondValueSet(String setStr) {
      if(setStr == null) {
         return false;
      }

      if(strSet.contains(setStr)) {
         return true;
      }

      while(setStr.contains("{{")) {
         setStr = setStr.substring(1, setStr.length() - 1);

         if(strSet.contains(setStr)) {
            return true;
         }
      }

      return false;
   }

   private static final String setPrefix = "Set_";
   private static final String[][] encoding0 = {{"'"}, {"''"}};
   protected static String[][] encoding1 = {{" "}, {"_"}};

   protected XMLAQuery query;
   private ArrayList<String> whereList = new ArrayList<>();
   private ArrayList<String> axisList = new ArrayList<>();
   private Map<String, Object> setOrder = new HashMap<>();
   private final Set<String> strSet = new HashSet<>();
}
