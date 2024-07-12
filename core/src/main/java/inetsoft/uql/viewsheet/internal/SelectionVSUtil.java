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
package inetsoft.uql.viewsheet.internal;

import inetsoft.report.composition.execution.SelectionPathIdentifier;
import inetsoft.report.composition.execution.SelectionTreeLevelTuple;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.util.algo.UndirectedGraph;
import inetsoft.web.composer.model.vs.OutputColumnRefModel;

import java.util.*;
import java.util.stream.Collectors;

/**
 * A class containing utility methods related to SelectionVSAssemblies.
 *
 * @since 13.1
 */
public class SelectionVSUtil {
   public static DataRef[] getDataRefs(SelectionVSAssembly assembly) {
      if(!(assembly instanceof SelectionTreeVSAssembly) ||
         !((SelectionTreeVSAssembly) assembly).isIDMode())
      {
         return assembly.getDataRefs();
      }

      SelectionTreeVSAssembly treeVSAssembly = (SelectionTreeVSAssembly) assembly;
      DataRef ref = treeVSAssembly.getDataRef(treeVSAssembly.getID());

      if(ref == null) {
         return null;
      }

      return new DataRef[] { ref };
   }

   public static List<List<String>> createDataRefSubLists(SelectionVSAssembly assembly) {
      DataRef[] refs = getDataRefs(assembly);

      if(refs == null || refs.length == 0) {
         return new ArrayList<>();
      }

      return createDataRefSubLists(refs);
   }

   /**
    * Create a {@code List} of sequential sublists of {@code DataRef} names.
    *
    * For example, if {@code dataRefs} is an array of DataRefs with names
    * {@code {"ref1", "ref2", "ref3"}}, this method will return the following list:
    * <pre>
    * {
    *    {"ref1"},
    *    {"ref1", "ref2"},
    *    {"ref1", "ref2", "ref3"}
    * }
    * </pre>
    *
    * @param dataRefs the dataRefs to create sublists of.
    * @return a {@code List} of sequential sublists of {@code DataRef} names.
    */
   public static List<List<String>> createDataRefSubLists(DataRef[] dataRefs) {
      final List<List<String>> dataRefSublists = new ArrayList<>(dataRefs.length);

      final List<String> dataRefNames = Arrays.stream(dataRefs)
         .map(DataRef::getName)
         .collect(Collectors.toList());

      for(int i = 0; i < dataRefNames.size(); i++) {
         final List<String> refSublist = new ArrayList<>();

         for(int j = 0; j <= i; j++) {
            refSublist.add(dataRefNames.get(j));
         }

         dataRefSublists.add(refSublist);
      }

      return dataRefSublists;
   }

   /**
    * @return <tt>true</tt> if the refs are compatible for a selection union, <tt>false</tt>
    * otherwise
    */
   public static boolean areColumnsCompatible(ColumnRef refA, ColumnRef refB) {
      return refA.getDisplayName().equals(refB.getDisplayName()) &&
         AssetUtil.isMergeable(refA.getDataType(), refB.getDataType()) &&
         (refA.getRefType() & DataRef.CUBE) == (refB.getRefType() & DataRef.CUBE);
   }

   /**
    * @return <tt>true</tt> if the refs are compatible for a selection union, <tt>false</tt>
    * otherwise
    */
   public static boolean areColumnsCompatible(OutputColumnRefModel refA, OutputColumnRefModel refB)
   {
      return refA.getName().equals(refB.getName()) &&
         AssetUtil.isMergeable(refA.getDataType(), refB.getDataType()) &&
         (refA.getRefType() & DataRef.CUBE) == (refB.getRefType() & DataRef.CUBE);
   }

   /**
    * Get the set of associated assemblies in the selection cluster containing {@code sassembly}.
    *
    * Assemblies in a selection cluster are all neighbors of each other. The selection paths
    * determined by their data refs at least partially match each other, and they may not be
    * bound to the same tables.
    *
    * @param sassembly the assembly to find the cluster of,
    *
    * @return the set of associated assemblies in the selection cluster containing
    * {@code sassembly}.
    */
   public static Set<AssociatedSelectionVSAssembly> getAssociatedSelectionCluster(
      SelectionVSAssembly sassembly)
   {
      if(sassembly.getTableNames().size() == 0) {
         return new HashSet<>();
      }

      final Viewsheet vs = sassembly.getViewsheet();
      final String tableName = sassembly.getTableNames().get(0);
      final DataRef[] dataRefs = getDataRefs(sassembly);
      return getAssociatedSelectionCluster(vs, tableName, dataRefs);
   }

   /**
    * @param vs        the viewsheet
    * @param tableName the table to find the depending selection assemblies of
    *
    * @return the set of selection assemblies which are directly bound to the table
    */
   public static Set<SelectionVSAssembly> getDependingSelectionAssemblies(
      Viewsheet vs,
      String tableName)
   {
      return vs.getSelectionAssemblyStream()
         .filter((s) -> tableName.equals(s.getTableName()) || s.getTableNames().contains(tableName))
         .collect(Collectors.toSet());
   }

   /**
    * Find the assemblies which are not neighbors of {@code sassembly} which needs to be reset.
    */
   public static Set<SelectionVSAssembly> getNonNeighborAssembliesThatNeedReset(
      AssociatedSelectionVSAssembly sassembly)
   {
      final Viewsheet vs = sassembly.getViewsheet();
      final Set<AssociatedSelectionVSAssembly> cluster = getAssociatedSelectionCluster(sassembly);

      final Set<String> tablesRequiringReset = cluster.stream()
         .filter(SelectionVSAssembly::requiresReset)
         .map(SelectionVSAssembly::getTableNames)
         .flatMap(Collection::stream)
         .collect(Collectors.toSet());

      // Find non neighbor assemblies bound to the same table
      final Set<SelectionVSAssembly> nonNeighborAssemblies =
         vs.getSelectionAssemblyStream()
            .filter((s) -> !cluster.contains(s))
            .filter((s) -> tablesRequiringReset.stream().anyMatch(s.getTableNames()::contains))
            .collect(Collectors.toSet());

      final Set<SelectionVSAssembly> nonNeighborAssemblyClusters = new HashSet<>();

      // Add clusters of the matching non-neighbor assemblies so that the reset is propagated
      for(SelectionVSAssembly assembly : nonNeighborAssemblies) {
         if(!nonNeighborAssemblyClusters.contains(assembly)) {
            nonNeighborAssemblyClusters.addAll(getAssociatedSelectionCluster(assembly));
         }
      }

      return nonNeighborAssemblyClusters;
   }

   /**
    * Find the neighbor trees which need to be reset.
    */
   public static Set<SelectionTreeLevelTuple> getNeighborTreesThatNeedReset(
      AssociatedSelectionVSAssembly sassembly)
   {
      final Set<SelectionTreeLevelTuple> neighborsToReset = new HashSet<>();

      if(!(sassembly instanceof SelectionTreeVSAssembly) || !sassembly.requiresReset()) {
         return neighborsToReset;
      }

      final Set<AssociatedSelectionVSAssembly> cluster = getAssociatedSelectionCluster(sassembly);
      final Set<SelectionTreeVSAssembly> treeNeighbors = cluster.stream()
         .filter((s) -> !s.equals(sassembly))
         .filter(SelectionTreeVSAssembly.class::isInstance)
         .map(SelectionTreeVSAssembly.class::cast)
         .filter((s) -> !s.isIDMode())
         .collect(Collectors.toSet());

      final DataRef[] dataRefs = sassembly.getDataRefs();

      for(SelectionTreeVSAssembly treeNeighbor : treeNeighbors) {
         final DataRef[] neighborDataRefs = treeNeighbor.getDataRefs();
         int firstDifferentLevel = -1;

         for(int i = 1; i < dataRefs.length && i < neighborDataRefs.length; i++) {
            final String refName = dataRefs[i].getName();
            final String neighborRefName = neighborDataRefs[i].getName();

            if(!refName.equals(neighborRefName)) {
               firstDifferentLevel = i;
               break;
            }
         }

         if(firstDifferentLevel != -1) {
            neighborsToReset.add(new SelectionTreeLevelTuple(treeNeighbor, firstDifferentLevel));
         }
      }

      return neighborsToReset;
   }

   /**
    * Find all assemblies related to {@code sassembly}. A selection assembly <b>A</b> is "related"
    * to another selection assembly <b>B</b> if they share a mutual bound table, or if both
    * assemblies are related to some other selection assembly <b>C</b>.
    *
    * @param sassembly the assembly to find the related assemblies of.
    *
    * @return the related selection assemblies of {@code sassembly}.
    */
   public static Set<SelectionVSAssembly> getRelatedSelectionAssemblies(
      AssociatedSelectionVSAssembly sassembly)
   {
      final Viewsheet vs = sassembly.getViewsheet();
      final Set<SelectionVSAssembly> candidateAssemblies =
         vs.getSelectionAssemblyStream().collect(Collectors.toSet());
      final Set<String> checkedTableNames = new HashSet<>();
      final Set<SelectionVSAssembly> visited = new LinkedHashSet<>();
      final Set<SelectionVSAssembly> frontier = new LinkedHashSet<>();

      frontier.add(sassembly);
      candidateAssemblies.remove(sassembly);

      while(!frontier.isEmpty()) {
         final SelectionVSAssembly assembly = frontier.iterator().next();
         frontier.remove(assembly);
         visited.add(assembly);

         for(String tname : assembly.getTableNames()) {
            if(!checkedTableNames.contains(tname)) {
               checkedTableNames.add(tname);
               final Iterator<SelectionVSAssembly> candidateIterator =
                  candidateAssemblies.iterator();

               while(candidateIterator.hasNext()) {
                  final SelectionVSAssembly candidate = candidateIterator.next();

                  if(candidate.getTableNames().contains(tname)) {
                     frontier.add(candidate);
                     candidateIterator.remove();
                  }
               }
            }
         }
      }

      return visited;
   }

   /**
    * @return the set of associated selection assemblies in the cluster containing the specified
    * {@code tableName} and {@code dataRefs}.
    */
   private static Set<AssociatedSelectionVSAssembly> getAssociatedSelectionCluster(
      Viewsheet vs,
      String tableName,
      DataRef[] dataRefs)
   {
      if(dataRefs == null || dataRefs.length == 0) {
         return new HashSet<>();
      }

      final Set<AssociatedSelectionVSAssembly> selectionAssemblies =
         Arrays.stream(vs.getAssemblies())
            .filter(AssociatedSelectionVSAssembly.class::isInstance)
            .map(AssociatedSelectionVSAssembly.class::cast)
            .collect(Collectors.toSet());

      final UndirectedGraph<SelectionPathIdentifier> graph = new UndirectedGraph<>();
      final Map<SelectionPathIdentifier, Set<AssociatedSelectionVSAssembly>> identifierMap =
         new HashMap<>();

      for(AssociatedSelectionVSAssembly sassembly : selectionAssemblies) {
         final DataRef[] refs = getDataRefs(sassembly);

         if(refs == null) {
            continue;
         }

         final Set<SelectionPathIdentifier> identifiers = new HashSet<>();

         for(String tname : sassembly.getTableNames()) {
            for(int i = 1; i <= refs.length; i++) {
               final List<DataRef> refSublist = Arrays.asList(Arrays.copyOfRange(refs, 0, i));
               identifiers.add(new SelectionPathIdentifier(tname, refSublist));
            }
         }

         graph.addCluster(identifiers);
         identifiers.forEach((identifier) -> {
            identifierMap.computeIfAbsent(identifier, (k) -> new HashSet<>())
               .add(sassembly);
         });
      }

      final Set<SelectionPathIdentifier> cluster =
         graph.getCluster(new SelectionPathIdentifier(tableName, Arrays.asList(dataRefs)));

      return cluster.stream()
         .map(identifierMap::get)
         .filter(Objects::nonNull)
         .flatMap(Collection::stream)
         .collect(Collectors.toSet());
   }

   public static SelectionValue[] shrinkSelectionValues(SelectionVSAssembly assembly,
                                                        SelectionValue[] selectionValues)
   {
      return assembly instanceof SelectionTreeVSAssembly &&
         ((SelectionTreeVSAssembly) assembly).getMode() == SelectionTreeVSAssemblyInfo.ID ?
         shrinkSelectionTreeValues(selectionValues) :
         shrinkSelectionValues(selectionValues);
   }

   public static SelectionValue[] shrinkSelectionTreeValues(SelectionValue[] values) {
      final List<SelectionValue> newList = new ArrayList<>();

      for(SelectionValue value : values) {
         if(value instanceof CompositeSelectionValue) {
            SelectionList slist = ((CompositeSelectionValue) value).getSelectionList();
            SelectionValue[] subValues = slist.getSelectionValues();
            subValues = shrinkSelectionTreeValues(subValues);
            slist.setSelectionValues(null);
            newList.addAll(Arrays.asList(subValues));
         }

         if(value.isSelected()) {
            newList.add(value);
         }
      }

      return newList.toArray(new SelectionValue[0]);
   }

   public static SelectionValue[] shrinkSelectionValues(SelectionValue[] values) {
      return shrinkSelectionValues(values, true);
   }

   public static SelectionValue[] shrinkSelectionValues(SelectionValue[] values,
                                                        boolean shrinkUnselected)
   {
      final List<SelectionValue> newList = new ArrayList<>();

      for(SelectionValue value : values) {
         if(value.isSelected() || !shrinkUnselected) {
            if(value instanceof CompositeSelectionValue) {
               SelectionList slist = ((CompositeSelectionValue) value).getSelectionList();
               SelectionValue[] subValues = slist.getSelectionValues();
               subValues = shrinkSelectionValues(subValues, shrinkUnselected);
               slist.setSelectionValues(subValues);
            }

            newList.add(value);
         }
      }

      return newList.toArray(new SelectionValue[0]);
   }

   /**
    * Select the first item for the selection.
    *
    * @param selection selection list values of the selection.
    * @param stateSelection state selection list value of the selection.
    * @return <tt>true</tt> if select successfully.
    */
   public static boolean selectSelectionFirstItem(SelectionList selection,
                                                  SelectionList stateSelection)
   {
      if(selection.getSelectionValueCount() > 0) {
         SelectionValue sval = selection.getSelectionValue(0);

         if(!sval.isExcluded()) {
            if(sval.isIncluded()) {
               sval.setState((sval.getState() & ~SelectionValue.STATE_INCLUDED) |
                  SelectionValue.STATE_SELECTED);
            }
            else {
               sval.setState(sval.getState() | SelectionValue.STATE_SELECTED);
            }

            stateSelection.addSelectionValue(sval);
            return true;
         }
      }

      return false;
   }

   /**
    * Select the first item for the selection tree.
    *
    * @param rootValue selection tree values of the selection.
    * @param stateRootValue state selection tree value of the selection.
    * @return <tt>true</tt> if select successfully.
    */
   public static boolean selectSelectionTreeFirstItem(SelectionTreeVSAssemblyInfo sinfo,
                                                      CompositeSelectionValue rootValue,
                                                      CompositeSelectionValue stateRootValue)
   {
      int sortType = VSUtil.getSortType(sinfo);
      SelectionList selectionList = rootValue.getSelectionList();

      if(selectionList != null && selectionList.getSelectionValueCount() > 0) {
         SelectionList sortedList = (SelectionList) selectionList.clone();
         sortedList.sort(sortType);
         SelectionValue selectionValue = sortedList.getSelectionValue(0);
         selectionValue = selectionList.getSelectionValue(selectionValue);

         if(selectionValue != null && !selectionValue.isExcluded()) {

            if(stateRootValue != null) {
               SelectionValue cloneValue = (SelectionValue) selectionValue.clone();
               stateRootValue.getSelectionList().addSelectionValue(cloneValue);

               if(cloneValue instanceof CompositeSelectionValue) {
                  ((CompositeSelectionValue) cloneValue).getSelectionList().clear();
               }

               selectDescendantsValue0(sinfo, selectionValue, cloneValue, 0, -1);
            }
            else {
               selectDescendantsValue0(sinfo, selectionValue, null, 0, -1);
            }

            return true;
         }
      }

      return false;
   }

   private static boolean selectDescendantsValue0(SelectionTreeVSAssemblyInfo sinfo,
                                               SelectionValue selectionValue,
                                               SelectionValue stateSelectionValue, int level,
                                               int parentState)
   {
      if(selectionValue.isExcluded()) {
         return false;
      }

      boolean idMode = sinfo.getMode() == SelectionTreeVSAssemblyInfo.ID;

      if(level == 0 || idMode) {
         int state = selectionValue.getState();
         selectionValue.setState(state & ~SelectionValue.STATE_INCLUDED);
         selectionValue.setSelected(true);

         if(stateSelectionValue != null) {
            state = selectionValue.getState();
            stateSelectionValue.setState(state & ~SelectionValue.STATE_INCLUDED);
            stateSelectionValue.setSelected(true);
         }
      }
      else {
         int state = selectionValue.getState();

         if(!sinfo.isSingleSelectionLevel(level) && (state & SelectionValue.STATE_EXCLUDED) == 0 &&
            (parentState & SelectionValue.STATE_EXCLUDED) == 0 &&
            ((parentState & SelectionValue.STATE_SELECTED) != 0 ||
               (parentState & SelectionValue.STATE_INCLUDED) != 0))
         {
            state = state | SelectionValue.STATE_INCLUDED;
            selectionValue.setState(state);

            if(stateSelectionValue != null) {
               stateSelectionValue.setState(state);
            }
         }
         else if(sinfo.isSingleSelectionLevel(level)) {
            selectionValue.setSelected(true);

            if(stateSelectionValue != null) {
               stateSelectionValue.setSelected(true);
            }
         }
      }

      List<SelectionValue> addToStateValues = new ArrayList<>();

      if(selectionValue instanceof CompositeSelectionValue) {
         SelectionList selectionList = ((CompositeSelectionValue) selectionValue).getSelectionList();

         for(int i = 0; i < selectionList.getSelectionValueCount(); i++) {
            if(stateSelectionValue != null) {
               SelectionValue cselectionValue = selectionList.getSelectionValue(i);
               SelectionValue cselectionValueClone = cselectionValue == null ? null :
                  (SelectionValue) cselectionValue.clone();

               if(cselectionValueClone instanceof CompositeSelectionValue) {
                  ((CompositeSelectionValue) cselectionValueClone).getSelectionList().clear();
               }

               boolean hasSelected = selectDescendantsValue0(sinfo, selectionList.getSelectionValue(i),
                  cselectionValueClone, level + 1, selectionValue.getState());

               if(hasSelected) {
                  addToStateValues.add(cselectionValueClone);
               }
            }
            else {
               selectDescendantsValue0(sinfo, selectionList.getSelectionValue(i),
                  null, level + 1, selectionValue.getState());
            }

            if(sinfo.isSingleSelectionLevel(level + 1)) {
               break;
            }
         }
      }

      if(stateSelectionValue instanceof CompositeSelectionValue) {
         for(SelectionValue value : addToStateValues) {
            ((CompositeSelectionValue) stateSelectionValue).getSelectionList()
               .addSelectionValue(value);
         }
      }

      if(level == 0 || sinfo.isSingleSelectionLevel(level) || addToStateValues.size() > 0) {
         return true;
      }

      return false;
   }
}