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
package inetsoft.uql.asset;

import inetsoft.uql.ColumnSelection;
import inetsoft.uql.VariableTable;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.erm.*;
import inetsoft.uql.schema.UserVariable;
import inetsoft.uql.viewsheet.CalculateRef;
import inetsoft.uql.viewsheet.XDimensionRef;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.util.ContentObject;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.PrintWriter;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Aggregate info contains the grouping and aggregation information of a
 * <tt>TableAssembly</tt>.
 *
 * @version 8.0
 * @author InetSoft Technology Corp
 */
public class AggregateInfo implements AssetObject, ContentObject {
   /**
    * Constructor.
    */
   public AggregateInfo() {
      super();
      groups = new CopyOnWriteArrayList<>();
      aggregates = new CopyOnWriteArrayList<>();
      aggregates2 = new CopyOnWriteArrayList<>();
      crosstab = false;
   }

   /**
    * Get the group.
    * @param ref the specified attribute.
    * @return the group of the attribute.
    */
   public GroupRef getGroup(DataRef ref) {
      for(int i = 0; i < groups.size(); i++) {
         GroupRef aref = groups.get(i);

         if(aref.getDataRef().equals(ref)) {
            return aref;
         }
      }

      return null;
   }

   /**
    * Get the group.
    * @param name the specified name.
    * @return the group of the attribute.
    */
   public GroupRef getGroup(String name) {
      for(int i = 0; i < groups.size(); i++) {
         GroupRef aref = groups.get(i);

         if(aref.getName().equals(name)) {
            return aref;
         }
      }

      return null;
   }

   /**
    * Get the group.
    * @param index the specified index.
    * @return the group of the attribute.
    */
   public GroupRef getGroup(int index) {
      return groups.get(index);
   }

   /**
    * Get all the groups.
    * @return all the groups.
    */
   public GroupRef[] getGroups() {
      return groups.toArray(new GroupRef[0]);
   }

   /**
    * Set all the groups.
    */
   public void setGroups(GroupRef[] gref) {
      removeGroups();
      addGroups(gref);
   }

   /**
    * Add all the groups.
    */
   public void addGroups(GroupRef[] gref) {
      for(int i = 0; i < gref.length; i++) {
         groups.add(gref[i]);
      }
   }

   /**
    * Get the group count.
    * @return the group count.
    */
   public int getGroupCount() {
      return groups.size();
   }

   /**
    * Check if contains the group.
    * @param ref the specified attribute.
    * @return <tt>true</tt> if contains, <tt>false</tt> otherwise.
    */
   public boolean containsGroup(DataRef ref) {
      return groups.contains(ref);
   }

   /**
    * Add one group.
    * @param ref the specified group.
    * @return <tt>true</tt> if successful, <tt>false</tt> otherwise.
    */
   public boolean addGroup(GroupRef ref) {
      return addGroup(ref, true);
   }

   /**
    * Add one group.
    * @param ref the specified group.
    * @param delSame if remove same column.
    * @return <tt>true</tt> if successful, <tt>false</tt> otherwise.
    */
   public boolean addGroup(GroupRef ref, Boolean delSame) {
      if(delSame) {
         //noinspection SuspiciousMethodCalls
         if(aggregates.contains(ref)) {
            return false;
         }

         int index = groups.indexOf(ref);

         if(index >= 0) {
            groups.remove(index);
            groups.add(index, ref);
         }
         else {
            groups.add(ref);
         }
      }
      else {
         groups.add(ref);
      }

      return true;
   }

   /**
    * Set the group reference at the specified position.
    */
   public void setGroup(int idx, GroupRef ref) {
      groups.add(idx, ref);
   }

   /**
    * Remove the group.
    * @param ref the specified group.
    * @return <tt>true</tt> if successful, <tt>false</tt> otherwise.
    */
   public boolean removeGroup(DataRef ref) {
      return groups.remove(ref);
   }

   /**
    * Remove the group.
    * @param index the specified index.
    * @return <tt>true</tt> if successful, <tt>false</tt> otherwise.
    */
   public boolean removeGroup(int index) {
      groups.remove(index);
      return true;
   }

   /**
    * Remove all the group.
    */
   public void removeGroups() {
      groups.clear();
   }

   /**
    * Get the aggregate.
    * @param ref the specified attribute.
    * @return the aggregate of the attribute.
    */
   public AggregateRef getAggregate(DataRef ref) {
      for(int i = 0; i < aggregates.size(); i++) {
         AggregateRef aref = aggregates.get(i);

         if(aref instanceof CompositeAggregateRef)  {
            ColumnRef column = (ColumnRef) aref.getDataRef();
            String name = getName(column);
            String name2 = getName(ref);

            if(Tool.equals(name, name2)) {
               return aref;
            }
         }
         else if(aref.getDataRef() == null) {
            continue;
         }
         else if(!(ref instanceof CalculateRef) && aref.getDataRef().equals(ref)) {
            return aref;
         }
         else if(ref instanceof CalculateRef && ref.equals(aref.getDataRef(), true)) {
            return aref;
         }
      }

      return null;
   }

   /**
    * Get the aggregates.
    * @param ref the specified attribute.
    * @return the aggregates of the attribute.
    */
   public AggregateRef[] getAggregates(DataRef ref) {
      Vector aggs = new Vector();
      Iterator iter = aggregates.iterator();

      while(iter.hasNext()) {
         AggregateRef aref = (AggregateRef) iter.next();

         if(aref instanceof CompositeAggregateRef)  {
            ColumnRef column = (ColumnRef) aref.getDataRef();
            String name = getName(column);
            String name2 = getName(ref);

            if(Tool.equals(name, name2)) {
               aggs.add(aref);
            }

            continue;
         }
         else if(aref.getDataRef().equals(ref)) {
            aggs.add(aref);
         }
      }

      AggregateRef[] ret = new AggregateRef[aggs.size()];
      return (AggregateRef[]) aggs.toArray(ret);
   }

   /**
    * Get the name of a data ref.
    * @return the name of the specified data ref.
    */
   private String getName(DataRef ref) {
      while(!(ref instanceof ColumnRef) && (ref instanceof DataRefWrapper)) {
         ref = ((DataRefWrapper) ref).getDataRef();
      }

      if(ref instanceof ColumnRef) {
         ColumnRef column = (ColumnRef) ref;
         String name = column.getAlias();

         if(name == null || name.length() == 0) {
            name = column.getAttribute();
         }

         return name;
      }
      else {
         return ref.getAttribute();
      }
   }

   /**
    * Get the aggregate.
    * @param index the specified index.
    * @return the aggregate of the attribute.
    */
   public AggregateRef getAggregate(int index) {
      return aggregates.get(index);
   }

   /**
    * Get all the aggregates.
    * @return all the aggregates.
    */
   public AggregateRef[] getAggregates() {
      AggregateRef[] refs = new AggregateRef[aggregates.size()];
      aggregates.toArray(refs);
      return refs;
   }

   /**
    * Get the aggregate count.
    * @return the aggregate count.
    */
   public int getAggregateCount() {
      return aggregates.size();
   }

   /**
    * Check if the aggregate info contains mergeable aggregate calc field.
    * @return <tt>true</tt> if mergeable, <tt>false</tt> otherwise.
    */
   public boolean isCalcMergeable() {
      boolean hasAggCalc = false;
      boolean hasNoneFormula = false;

      for(int i = 0; i < aggregates.size(); i++) {
         AggregateRef aref = getAggregate(i);

         if(VSUtil.isAggregateCalc(aref.getDataRef())) {
            hasAggCalc = true;
         }
         else {
            AggregateFormula formula = aref.getFormula();
            hasNoneFormula = hasNoneFormula ||
               formula == null || formula.equals(AggregateFormula.NONE);
         }
      }

      return hasAggCalc && !hasNoneFormula;
   }

   public List<DataRef> getFormulaFields() {
      List<DataRef> calcFields = new ArrayList<>();

      GroupRef[] groups = getGroups();
      AggregateRef[] aggregates = getAggregates();

      if(groups != null) {
         Arrays.stream(groups)
            .map(GroupRef::getDataRef)
            .filter(r -> (r instanceof CalculateRef))
            .forEachOrdered(calcFields::add);
      }

      if(aggregates != null) {
         Arrays.stream(aggregates)
            .map(AggregateRef::getDataRef)
            .filter(r -> (r instanceof CalculateRef))
            .forEachOrdered(calcFields::add);
      }

      return calcFields;
   }

   public Set<String> removeFormulaFields(List<DataRef> calcFields) {
      Set<String> calcFieldsRefs = new HashSet<>(calcFields.size());

      for(DataRef ref : calcFields) {
         calcFieldsRefs.add(ref.getName());

         if(ref instanceof GroupRef) {
            removeGroup(ref);
         }
         else {
            removeAggregate(ref);
         }
      }

      return calcFieldsRefs;
   }

   /**
    * Test if the aggregate info is aggregated.
    * @param geoRefs ignore the refs of columns.
    * @return true if the aggregate info is aggregated, false otherwise.
    */
   public boolean isAggregated(DataRef... geoRefs) {
      if(groups.size() == 0 && aggregates.size() == 0) {
         return false;
      }

      // or only dimension for word cloud.
      if(aggregates.isEmpty()) {
         return true;
      }

      // true if there are non-geo aggregate that request aggregate (46752).
      boolean hasAggregate = false;

      for(int i = 0; i < aggregates.size(); i++) {
         AggregateRef aref = getAggregate(i);
         boolean geo = false;

         if(aref != null) {
            if(geoRefs != null) {
               for(int k = 0; k < geoRefs.length; k++) {
                  if(geoRefs[k].getName().equals(aref.getName()) ||
                     geoRefs[k].getName().equals(aref.getAttribute()))
                  {
                     geo = true;
                     break;
                  }
               }
            }

            if(!geo) {
               if(aref.isAggregated()) {
                  hasAggregate = true;
               }
               // non-geo measure with none aggregate, don't aggregate.
               else {
                  return false;
               }
            }
         }
      }

      boolean justGeoDimension = geoRefs != null && geoRefs.length > 0 &&
         Arrays.stream(geoRefs).allMatch(ref -> ref instanceof XDimensionRef);

      // only aggregate if all non-geo measures are aggregate.
      return hasAggregate || justGeoDimension;
   }

   /**
    * Check if the aggregate info supports aggregate on aggregate.
    * @return <tt>true</tt> if supports aggregate on aggregate, <tt>false</tt>
    * otherwise.
    */
   public boolean supportsAOA() {
      AggregateRef[] all = getAggregates();

      for(int i = 0; i < aggregates.size(); i++) {
         AggregateRef aref = aggregates.get(i);

         if(!VSUtil.supportsAOA(aref, all)) {
            return false;
         }
      }

      return true;
   }

   /**
    * Check if contains named group.
    */
   public boolean containsNamedGroup() {
      for(int i = 0; i < groups.size(); i++) {
         GroupRef group = groups.get(i);
         String name = group.getNamedGroupAssembly();

         if(name != null && name.length() > 0) {
            return true;
         }
      }

      return false;
   }

   /**
    * Check if the aggregate info contains percentage aggregate.
    * @return true if the aggregate info contains percentage aggregate,
    * false otherwise.
    */
   public boolean containsPercentage() {
      for(int i = 0; i < aggregates.size(); i++) {
         AggregateRef aref = aggregates.get(i);

         if(aref.isPercentage()) {
            return true;
         }
      }

      return false;
   }

   /**
    * Clear the percentage option of all aggregates.
    */
   public void clearPercentage() {
      for(int i = 0; i < aggregates.size(); i++) {
         AggregateRef aref = aggregates.get(i);
         aref.setPercentage(false);
      }
   }

   /**
    * Check if contains the aggregate.
    * @param ref the specified attribute.
    * @return <tt>true</tt> if contains, <tt>false</tt> otherwise.
    */
   public boolean containsAggregate(DataRef ref) {
      for(int i = 0; i < aggregates.size(); i++) {
         AggregateRef aref = aggregates.get(i);

         if(aref instanceof CompositeAggregateRef)  {
            ColumnRef column = (ColumnRef) aref.getDataRef();
            String name = column.getAlias();

            if(name == null || name.length() == 0) {
               name = column.getAttribute();
            }

            ColumnRef column2 = (ColumnRef) ref;
            String name2 = column2.getAlias();

            if(name2 == null || name2.length() == 0) {
               name2 = column2.getAttribute();
            }

            if(Tool.equals(name, name2)) {
               return true;
            }
         }
         else if(aref.equals(ref)) {
            return true;
         }
      }

      return false;
   }

   /**
    * Check if contains the alias aggregate.
    * @param ref the specified attribute.
    * @return <tt>true</tt> if contains, <tt>false</tt> otherwise.
    */
   public boolean containsAliasAggregate(DataRef ref) {
      for(int i = 0; i < aggregates.size(); i++) {
         AggregateRef aggregate = aggregates.get(i);
         DataRef ref2 = aggregate.getDataRef();

         if(ref2 instanceof ColumnRef) {
            ref2 = ((ColumnRef) ref2).getDataRef();

            if(ref2 instanceof AliasDataRef) {
               ref2 = ((AliasDataRef) ref2).getDataRef();
            }
         }

         if(ref2.equals(ref)) {
            return true;
         }
      }

      return false;
   }

   /**
    * Add one aggregate.
    * @param ref the specified aggregate.
    * @return <tt>true</tt> if successful, <tt>false</tt> otherwise.
    */
   public boolean addAggregate(AggregateRef ref) {
      return addAggregate(ref, true);
   }

   /**
    * Add one aggregate.
    * @param ref the specified aggregate.
    * @param delSame if remove same column.
    * @return <tt>true</tt> if successful, <tt>false</tt> otherwise.
    */
   public boolean addAggregate(AggregateRef ref, Boolean delSame) {
      if(delSame) {
         //noinspection SuspiciousMethodCalls
         if(groups.contains(ref)) {
            return false;
         }

         int index = aggregates.indexOf(ref);

         if(index >= 0) {
            aggregates.remove(index);
            aggregates.add(index, ref);
         }
         else {
            aggregates.add(ref);
         }
      }
      else {
         aggregates.add(ref);
      }

      return true;
   }

   /**
    * Set the aggregate reference at the specified position.
    */
   public void setAggregate(int idx, AggregateRef ref) {
      aggregates.set(idx, ref);
   }

   /**
    * Set all the aggregates.
    */
   public void setAggregates(AggregateRef[] aref) {
      removeAggregates();

      for(int i = 0; i < aref.length; i++) {
         aggregates.add(aref[i]);
      }
   }

   /**
    * Remove the aggregate.
    * @param ref the specified group.
    * @return <tt>true</tt> if successful, <tt>false</tt> otherwise.
    */
   public boolean removeAggregate(DataRef ref) {
      return aggregates.remove(ref);
   }

   /**
    * Remove the aggregate.
    * @param index the specified index.
    * @return <tt>true</tt> if successful, <tt>false</tt> otherwise.
    */
   public boolean removeAggregate(int index) {
      aggregates.remove(index);
      return true;
   }

   /**
    * Remove all the aggregates.
    */
   public void removeAggregates() {
      aggregates.clear();
   }

   /**
    * Remove all the secondary aggregates.
    */
   public void removeSecondaryAggregates() {
      aggregates2.clear();
   }

   /**
    * Check if is a crosstab.
    * @return <tt>true</tt> if yes, <tt>false</tt> otherwise.
    */
   public boolean isCrosstab() {
      return crosstab && aggregates.size() > 0 && groups.size() > 1;
   }

   /**
    * Set the crosstab option.
    * @param crosstab <tt>true</tt> if a crosstab.
    */
   public void setCrosstab(boolean crosstab) {
      this.crosstab = crosstab;
   }

   /**
    * Update the group ref.
    * @param ws the associated worksheet.
    * @return <tt>true</tt> if successful, <tt>false</tt> otherwise.
    */
   public boolean update(Worksheet ws) {
      int size = groups.size();

      for(int i = 0; i < size; i++) {
         GroupRef ref = groups.get(i);

         if(!ref.update(ws)) {
            return false;
         }
      }

      return true;
   }

   /**
    * Replace all embeded user variables.
    * @param vars the specified variable table.
    */
   public void replaceVariables(VariableTable vars) {
      for(int i = 0; i < groups.size(); i++) {
         GroupRef ref = groups.get(i);
         ref.replaceVariables(vars);
      }
   }

   /**
    * Get all variables in the condition value list.
    * @return the variable list.
    */
   public UserVariable[] getAllVariables() {
      List list = new ArrayList();

      for(int i = 0; i < groups.size(); i++) {
         GroupRef ref = groups.get(i);
         UserVariable[] vars = ref.getAllVariables();

         for(int j = 0; j < vars.length; j++) {
            if(!list.contains(vars[j])) {
               list.add(vars[j]);
            }
         }
      }

      UserVariable[] vars = new UserVariable[list.size()];
      list.toArray(vars);
      return vars;
   }

   /**
    * Get the assemblies depended on.
    */
   public void getDependeds(Set set) {
      for(int i = 0; i < groups.size(); i++) {
         GroupRef ref = groups.get(i);
         ref.getDependeds(set);
      }
   }

   /**
    * Rename the assemblies depended on.
    * @param oname the specified old name.
    * @param nname the specified new name.
    */
   public void renameDepended(String oname, String nname) {
      for(int i = 0; i < groups.size(); i++) {
         GroupRef ref = groups.get(i);
         ref.renameDepended(oname, nname);
      }
   }

   /**
    * Get all the secondary aggregates.
    * @return all the secondary aggregates.
    */
   public AggregateRef[] getSecondaryAggregates() {
      AggregateRef[] aggs = new AggregateRef[aggregates2.size()];
      aggregates2.toArray(aggs);
      return aggs;
   }

   /**
    * Add one secondary aggregate.
    * @param ref the specified aggregate.
    */
   public void addSecondaryAggregate(AggregateRef ref) {
      aggregates2.add(ref);
   }

   /**
    * Check if the group info is empty.
    * @return <tt>true</tt> if empty, <tt>false</tt> otherwise.
    */
   public boolean isEmpty() {
      return groups.size() == 0 && aggregates.size() == 0;
   }

   /**
    * Clear the group info.
    */
   public void clear() {
      removeGroups();
      removeAggregates();
      removeSecondaryAggregates();
   }

   /**
    * Validate the group info.
    * @param columns the specified column selection.
    */
   public void validate(ColumnSelection columns) {
      for(int i = groups.size() - 1; i >= 0; i--) {
         GroupRef group = groups.get(i);
         DataRef columnRef = group.getDataRef();
         DataRef baseRef = (columnRef instanceof ColumnRef)
            ? ((ColumnRef) columnRef).getDataRef() : columnRef;

         // @by larryl, bug1432176571215, check for base col. in case of
         // sql query, if the query is submitted, the column list will be
         // refreshed from the SQL, and the dataRef in AggregateInfo may
         // be out of sync
         if(baseRef instanceof DataRefWrapper) {
            baseRef = ((DataRefWrapper) baseRef).getDataRef();
         }

         boolean validDateRange = AssetUtil.isDateRangeValid(columnRef, columns);

         if((!columns.containsAttribute(baseRef) || !validDateRange) &&
            !columns.containsAttribute(columnRef))
         {
            DataRef ref2 = columns.getAttribute(columnRef.getAttribute());

            // if the base table is replaced (by a mirror) so the entity part changes,
            // it's more reasonable to point to the new column (with a new table name)
            // than removing the group column
            if(ref2 != null) {
               group.setDataRef(ref2);
            }
            else {
               groups.remove(i);
            }
         }
         else {
            group.refreshDataRef(columns);
         }
      }

      for(int i = aggregates.size() - 1; i >= 0; i--) {
         AggregateRef aggregate = aggregates.get(i);

         if(aggregate instanceof CompositeAggregateRef) {
            continue;
         }

         DataRef aref = aggregate.getDataRef();
         DataRef aref2 = aggregate.getSecondaryColumn();
         final boolean twoColumns = Optional.ofNullable(aggregate.getFormula())
            .map(AggregateFormula::isTwoColumns)
            .orElse(false);
         boolean aggrCalcAlias = aref instanceof ColumnRef &&
            ((ColumnRef) aref).getDataRef() instanceof AliasDataRef &&
            ((AliasDataRef) ((ColumnRef) aref).getDataRef()).isAggrCalc();
         // if alias for an aggr calc field, use fuzzy matching since the name may be
         // changed to include base table name. (54390)
         boolean notfound1 = aggrCalcAlias ? columns.getAttribute(aref.getName(), true) == null
            : !columns.containsAttribute(aref);
         boolean notfound2 = twoColumns && (aref2 == null || !columns.containsAttribute(aref2));

         if(notfound1 || notfound2) {
            DataRef ref1 = aref != null ? columns.getAttribute(aref.getAttribute()) : null;
            DataRef ref2 = aref2 != null ? columns.getAttribute(aref2.getAttribute()) : null;
            boolean ok = false;

            if(notfound1 && notfound2) {
               if(ref1 != null && ref2 != null) {
                  aggregate.setDataRef(ref1);
                  aggregate.setSecondaryColumn(ref2);
                  ok = true;
               }
            }
            else if(notfound1) {
               if(ref1 != null) {
                  aggregate.setDataRef(ref1);
                  ok = true;
               }
            }
            else if(notfound2) {
               if(ref2 != null) {
                  aggregate.setSecondaryColumn(ref2);
                  ok = true;
               }
               else {
                  ok = !VSUtil.requiresTwoColumns(aggregate.getFormula());
               }
            }

            if(!ok) {
               // remove aggregates if their column reference is hidden
               aggregates.remove(i);
            }
         }
         else {
            aggregate.refreshDataRef(columns);
         }
      }
   }

   /**
    * Write the xml segment to print writer.
    * @param writer the destination print writer.
    */
   @Override
   public void writeXML(PrintWriter writer) {
      writer.println("<groupInfo crosstab=\"" + crosstab + "\">");
      writer.println("<groups>");

      for(int i = 0; i < groups.size(); i++) {
         GroupRef ref = groups.get(i);
         ref.writeXML(writer);
      }

      writer.println("</groups>");
      writer.println("<aggregates>");

      for(int i = 0; i < aggregates.size(); i++) {
         AggregateRef ref = aggregates.get(i);
         ref.writeXML(writer);
      }

      writer.println("</aggregates>");

      int size = aggregates2.size();

      if(size > 0) {
         writer.println("<secondaryAggregates>");

         for(int i = 0; i < size; i++) {
            AggregateRef ref = aggregates2.get(i);
            ref.writeXML(writer);
         }

         writer.println("</secondaryAggregates>");
      }

      writer.println("</groupInfo>");
   }

   /**
    * Method to parse an xml segment.
    * @param elem the specified xml element.
    */
   @Override
   public void parseXML(Element elem) throws Exception {
      this.crosstab = "true".equals(Tool.getAttribute(elem, "crosstab"));

      Element gsnode = Tool.getChildNodeByTagName(elem, "groups");
      NodeList gnodes = Tool.getChildNodesByTagName(gsnode, "dataRef");

      for(int i = 0; i < gnodes.getLength(); i++) {
         Element gnode = (Element) gnodes.item(i);
         GroupRef ref = (GroupRef) AbstractDataRef.createDataRef(gnode);
         groups.add(ref);
      }

      Element asnode = Tool.getChildNodeByTagName(elem, "aggregates");
      NodeList anodes = Tool.getChildNodesByTagName(asnode, "dataRef");

      for(int i = 0; i < anodes.getLength(); i++) {
         Element anode = (Element) anodes.item(i);
         AggregateRef ref = (AggregateRef) AbstractDataRef.createDataRef(anode);
         aggregates.add(ref);
      }

      asnode = Tool.getChildNodeByTagName(elem, "secondaryAggregates");

      if(asnode == null) {
         return;
      }

      anodes = Tool.getChildNodesByTagName(asnode, "dataRef");

      for(int i = 0; i < anodes.getLength(); i++) {
         Element anode = (Element) anodes.item(i);
         AggregateRef ref = (AggregateRef) AbstractDataRef.createDataRef(anode);
         aggregates2.add(ref);
      }
   }

   /**
    * Clone the object.
    * @return the cloned object.
    */
   @Override
   public Object clone() {
      try {
         AggregateInfo ginfo = (AggregateInfo) super.clone();
         ginfo.groups = cloneDataRefList((CopyOnWriteArrayList) groups);
         ginfo.aggregates = cloneDataRefList((CopyOnWriteArrayList) aggregates);
         ginfo.aggregates2 = cloneDataRefList((CopyOnWriteArrayList) aggregates2);

         return ginfo;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone object", ex);
         return null;
      }
   }

   /**
    * Clone data ref list.
    */
   private CopyOnWriteArrayList cloneDataRefList(CopyOnWriteArrayList list) {
      Object[] arr = list.toArray();

      for(int i = 0; i < arr.length; i++) {
         arr[i] = ((DataRef) arr[i]).clone();
      }

      return new CopyOnWriteArrayList(arr);
   }

   /**
    * Get the string representation.
    * @return the string representation.
    */
   public String toString() {
      return "AggregateInfo" + super.hashCode() + ":{" +
         groups + ", " + aggregates + ", " + crosstab + "}";
   }

   /**
    * Get the hash code value.
    * @return the hash code value.
    */
   public int hashCode() {
      return groups.hashCode();
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) {
         return true;
      }

      if(o == null || getClass() != o.getClass()) {
         return false;
      }

      AggregateInfo that = (AggregateInfo) o;

      if(crosstab != that.crosstab) {
         return false;
      }

      if(pglevel != that.pglevel) {
         return false;
      }

      if(groups != null ? !groups.equals(that.groups) : that.groups != null) {
         return false;
      }

      if(aggregates != null ? !aggregates.equals(that.aggregates) : that.aggregates != null) {
         return false;
      }

      return aggregates2 != null ? aggregates2.equals(that.aggregates2) : that.aggregates2 == null;
   }

   /**
    * Get the address.
    */
   public int addr() {
      return super.hashCode();
   }

   /**
    * Print the key to identify this content object. If the keys of two content
    * objects are equal, the content objects are equal too.
    */
   @Override
   public boolean printKey(PrintWriter writer) throws Exception {
      writer.print("AINFO");
      writer.print("[");
      writer.print(crosstab ? "T" : "F");
      writer.print(",");
      int gcnt = getGroupCount();
      writer.print(gcnt);
      writer.print(pglevel);

      for(int i = 0; i < gcnt; i++) {
         GroupRef gref = getGroup(i);
         gref.printKey(writer);
      }

      int acnt = getAggregateCount();
      writer.print(",");
      writer.print(acnt);

      for(int i = 0; i < getAggregateCount(); i++) {
         AggregateRef aref = getAggregate(i);
         aref.printKey(writer);
      }

      writer.print("]");
      return true;
   }

   /**
    * Check if equals another object in content.
    * @param obj the specified object.
    * @return <tt>true</tt> if equals the object in content, <tt>false</tt>
    * otherwise.
    */
   @Override
   public boolean equalsContent(Object obj) {
      if(!(obj instanceof AggregateInfo)) {
         return false;
      }

      AggregateInfo ginfo = (AggregateInfo) obj;

      if(crosstab != ginfo.crosstab) {
         return false;
      }

      if(getGroupCount() != ginfo.getGroupCount()) {
         return false;
      }

      for(int i = 0; i < getGroupCount(); i++) {
         GroupRef gref1 = getGroup(i);
         GroupRef gref2 = ginfo.getGroup(i);

         if(!gref1.equalsContent(gref2)) {
            return false;
         }
      }

      if(getAggregateCount() != ginfo.getAggregateCount()) {
         return false;
      }

      for(int i = 0; i < getAggregateCount(); i++) {
         AggregateRef aref1 = getAggregate(i);
         AggregateRef aref2 = ginfo.getAggregate(i);

         if(!aref1.equalsContent(aref2)) {
            return false;
         }
      }

      if(pglevel != ginfo.pglevel) {
         return false;
      }

      return true;
   }

   /**
    * Get percent group level.
    */
   public int getPercentGroupLevel() {
      return pglevel;
   }

   /**
    * Set percent group level.
    */
   public void setPercentGroupLevel(int pglevel) {
      this.pglevel = pglevel;
   }

   private List<GroupRef> groups;
   private List<AggregateRef> aggregates;
   private List<AggregateRef> aggregates2;
   private boolean crosstab;
   private int pglevel = 0;

   private static final Logger LOG = LoggerFactory.getLogger(AggregateInfo.class);
}
