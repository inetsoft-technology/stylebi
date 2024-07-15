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
package inetsoft.web.viewsheet.handler;

import inetsoft.report.TableDataPath;
import inetsoft.report.composition.execution.VSAQuery;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.util.XUtil;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.CrosstabTree;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.web.graph.GraphBuilder;
import inetsoft.web.viewsheet.model.DrillFilterAction;

import java.util.*;

public abstract class BaseDrillHandler<T extends DrillFilterVSAssembly, R extends DrillFilterAction>
   implements DrillHandler<T, R>
{
   /**
    * Remove drill filter from all DrillFilterVSAssemblys of viewsheet.
    *    this is required, drill filter up and down may not be on the same assembly.
    * @param fieldRef field ref
    * @param vs viewsheet
    */
   public void removeDrillFilter(final VSDimensionRef fieldRef, Viewsheet vs) {
      Arrays.stream(vs.getAssemblies())
         .filter(assembly -> assembly instanceof DrillFilterVSAssembly
            && ((DrillFilterVSAssembly) assembly).hasDrillFilter()
            && assembly instanceof DataVSAssembly) // for get ColumnSelection
         .map(assembly -> ((DrillFilterVSAssembly) assembly))
         .forEach(assembly -> {
            boolean self = containsSelfFilter(assembly, fieldRef.getFullName()) ||
               // handle drill on named group (42831)
               containsSelfFilter(assembly, fieldRef.getName());
            removeDrillFilter(fieldRef, assembly, self, false);
         });
   }

   /**
    * Remove drill filter from special assembly
    * @param dataRef dataRef
    * @param assembly assembly
    * @param removeSelf remove self
    * @param parent
    */
   public void removeDrillFilter(DataRef dataRef, DrillFilterVSAssembly assembly,
                                 boolean removeSelf, boolean parent)
   {
      if(!(dataRef instanceof VSDimensionRef) || !(assembly instanceof DataVSAssembly)) {
         return;
      }

      VSDimensionRef fieldRef = (VSDimensionRef) dataRef;
      VSDimensionRef parentRef = VSUtil.getLastDrillLevelRef(fieldRef, assembly.getXCube());
      GroupRef groupRef = !removeSelf && parentRef != null ? parentRef.createGroupRef(null)
         : fieldRef.createGroupRef(null);

      if(groupRef == null) {
         return;
      }

      DataRef ref = groupRef.getDataRef();

      if(ref != null) {
         String name = CrosstabTree.getDrillFilterName((VSDimensionRef) dataRef,
                                                       assembly.getXCube(), false);

         // drill up from city could be at one of cases: state->city->city.self or state->city
         if(assembly.getDrillFilterConditionList(name) == null && name.endsWith(".self")) {
            name = name.substring(0, name.length() - 5);
         }
         else if(!name.endsWith(".self")) {
            // if drill up from a dimension, make sure the next level condition on self is cleared.
            // this is only necessary if the hierarchy has changed after a drill down. (43671)
            if(assembly.getDrillFilterConditionList(name + ".self") != null) {
               name = name + ".self";
            }

            // in process action, we don't insert next level dimension if it already exists.
            // for example, a chart has both year and quarter, drill down on year would
            // place the condition on quarter, drill up from year should remove the
            // condition on quarter instead of year. (43710)
            VSDimensionRef next = (VSDimensionRef) dataRef;

            while(assembly.getDrillFilterConditionList(name) == null && !name.endsWith(".self")) {
               next = VSUtil.getNextLevelRef(next, assembly.getXCube(), true);
               name = next != null ? NamedRangeRef.getBaseName(next.getFullName()) : name + ".self";
            }
         }

         assembly.setDrillFilterConditionList(name, null);

         // for multiple level drill down (year -> quarter -> month), when removing month, all
         // parent conditions should be removed. (42857)
         if(parentRef != null && parent) {
            removeDrillFilter(parentRef, assembly, removeSelf, true);
         }
      }
   }

   /**
    * update condition list for drill filter assembly
    */
   public void updateDrillFilterAssemblyCondition(Map<String, ConditionList> ndrillCondsn,
                                                  DrillFilterVSAssembly assembly)
   {
      ndrillCondsn.keySet().iterator().forEachRemaining((String name) ->
         assembly.setDrillFilterConditionList(name, ndrillCondsn.get(name)));
   }

   /**
    * merge condition list of the same column.
    */
   public void mergeDrillFilterConditionList(Map<String, ConditionList> ndrillConds, String name,
                                             ConditionList conditionList)
   {
      ConditionList drillConditionList = ndrillConds.get(name);

      if(drillConditionList != null) {
         drillConditionList =
            VSUtil.mergeConditionList(Arrays.asList(drillConditionList, conditionList), JunctionOperator.OR);
      }
      else {
         drillConditionList = conditionList;
      }

      ndrillConds.put(name, drillConditionList);
   }

   public static List<String> getDrillFiltersFields(Viewsheet vs) {
      Assembly[] assemblies = vs.getAssemblies();
      List<String> filterFields = new ArrayList<>();

      for(Assembly assembly : assemblies) {
         if(assembly instanceof DrillFilterVSAssembly) {
            DrillFilterVSAssembly drillFilterVSAssembly = (DrillFilterVSAssembly) assembly;
            ConditionList list = drillFilterVSAssembly.getAllDrillFilterConditions();
            DataRef[] refs = drillFilterVSAssembly.getDrillFilterAvailableRefs();
            XCube cube = null;

            if(drillFilterVSAssembly instanceof DataVSAssembly) {
               cube = GraphBuilder.getCube((DataVSAssembly) drillFilterVSAssembly,
                  drillFilterVSAssembly.getXCube());
            }

            updateDrillFiltersFields(list, cube, refs, filterFields);
         }
         else if(assembly instanceof Viewsheet) {
            filterFields.addAll(getDrillFiltersFields(((Viewsheet) assembly)));
         }
      }

      return filterFields;
   }

   private static void updateDrillFiltersFields(ConditionList list, XCube cube,
                                               DataRef[] fields, List<String> filterFields)
   {
      for(int i = 0; i < list.getSize(); i++) {
         ConditionItem item = list.getConditionItem(i);

         if(item == null) {
            continue;
         }

         for(DataRef ref : fields) {
            if(!(ref instanceof VSDimensionRef)
               || filterFields.contains(((VSDimensionRef) ref).getFullName()))
            {
               continue;
            }

            VSDimensionRef dimRef = (VSDimensionRef) ref;
            VSDimensionRef parent = VSUtil.getLastDrillLevelRef(dimRef, cube);
            String dim = NamedRangeRef.getBaseName(dimRef.getFullName());
            String condField = item.getAttribute().getName();

            if(parent != null && parent.getFullName().equals(condField) || dim.equals(condField)) {
               filterFields.add(dimRef.getFullName());
            }
            // this can happen if a drill-filter is performed, and then the hierarchy is
            // deleted. we should allow drilling up so the condition can be removed.
            else if(!filterFields.contains(condField)) {
               filterFields.add(condField);
            }
         }
      }
   }

   public static boolean containsSelfFilter(DrillFilterVSAssembly targetAssembly, String field) {
      ConditionList conditionList = targetAssembly.getAllDrillFilterConditions();

      for(int i = 0; i < conditionList.getConditionSize(); i++) {
         ConditionItem item = conditionList.getConditionItem(i);

         if(item != null && (field.equals(item.getAttribute().getName())
            || field.equals(XUtil.toView(item.getAttribute()))))
         {
            return true;
         }
      }

      return false;
   }

   // replace named group in condition with the base column. since condition is used in drill
   // down, and the parent group is no longer name grouped, the condition should be applied
   // to the base column.
   public static ConditionList replaceNamedGroup(ConditionList conds, VSAssembly assembly) {
      if(assembly instanceof CrosstabVSAssembly) {
         conds = VSAQuery.replaceGroupValues(conds, (CrosstabVSAssembly) assembly, false);
      }
      else {
         conds = VSAQuery.replaceGroupValues(conds, (ChartVSAssembly) assembly, false);
      }

      for(int i = 0; i < conds.getConditionSize(); i++) {
         ConditionItem item = conds.getConditionItem(i);

         if(item != null && item.getAttribute() instanceof ColumnRef) {
            ColumnRef column = (ColumnRef) item.getAttribute();

            if(column.getDataRef() instanceof NamedRangeRef) {
               column.setDataRef(((NamedRangeRef) column.getDataRef()).getDataRef());
            }
         }
      }

      return conds;
   }

   /**
    * Synchronize the table data path in this map.
    */
   protected <V> void syncPath(Map<TableDataPath, V> map, VSDataRef oref, VSDataRef nref) {
      List<TableDataPath> list = new ArrayList<>(map.keySet());
      String oname = oref.getFullName();
      String nname = nref.getFullName();

      for(TableDataPath tp : list) {
         String[] arr = tp.getPath();

         if(tp.getType() == TableDataPath.HEADER ||
            tp.getType() == TableDataPath.SUMMARY ||
            tp.getType() == TableDataPath.GROUP_HEADER ||
            tp.getType() == TableDataPath.GRAND_TOTAL)
         {
            String[] arr0 = replaceNameInPath(arr, oname, nname);

            if(arr0 != null) {
               TableDataPath ntp = (TableDataPath) tp.clone(arr0);
               V obj = map.get(ntp);

               if(obj == null) {
                  obj = map.get(tp);
                  map.put(ntp, obj);
               }
            }
         }
      }
   }

   private static String[] replaceNameInPath(String[] path, String oname, String nname) {
      for(int i = 0; i < path.length; i++) {
         if(path[i].equals(oname)) {
            path = path.clone();
            path[i] = nname;
            return path;
         }
      }

      return null;
   }
}
