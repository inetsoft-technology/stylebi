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
package inetsoft.report.composition;

import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.TableAssemblyOperator.Operator;
import inetsoft.uql.asset.internal.TableAssemblyInfo;
import inetsoft.util.Catalog;

import java.util.*;

/**
 * Build a AssemblyTreeModel of the worksheet.
 */
public class AssemblyTreeModelBuilder {
   /**
    * Constructor.
    */
   public AssemblyTreeModelBuilder(RuntimeWorksheet rws) {
      this.ws = rws.getWorksheet();

      comparator = new Comparator<TableAssembly>() {
         @Override
         public int compare(TableAssembly t1, TableAssembly t2) {
            return t1.getName().compareTo(t2.getName());
         }
      };
   }

   /**
    * Create assembly tree model.
    */
   public AssemblyTreeModel createAssemblyTreeModel() {
      TableAssemblyEntry entry = new TableAssemblyEntry();
      entry.setName("root");
      AssemblyTreeModel.Node root =  new AssemblyTreeModel.Node(entry);
      AssemblyTreeModel model = new AssemblyTreeModel(root);
      Assembly[] assemblies = ws.getAssemblies(true);
      List<TableAssembly> tasbs = new ArrayList<>();

      for(Assembly assembly : assemblies) {
         if(!(assembly instanceof TableAssembly)) {
            continue;
         }

         tasbs.add((TableAssembly) assembly);
      }

      Set<TableAssembly> tasbs2 = new HashSet<>();
      tasbs2.addAll(tasbs);

      for(TableAssembly assembly : tasbs) {
         if(!tasbs2.contains(assembly)) {
            continue;
         }

         // only remove the sub assembly
         removeAssemblies(assembly, tasbs2);
         tasbs2.add(assembly);
      }

      ArrayList<TableAssembly> assList = new ArrayList<>(tasbs2);
      Collections.sort(assList, comparator);

      for(TableAssembly assembly : assList) {
         createAssemblyTreeModel0(assembly, root);
      }

      return model;
   }

   /**
    * Remove the assembly and its sub assemblies from the set.
    */
   private void removeAssemblies(TableAssembly asbly, Set<TableAssembly> set) {
      set.remove(asbly);
      TableAssembly[] assemblies = getSubQueryAssemblies(asbly);

      for(TableAssembly assembly : assemblies) {
         removeAssemblies(assembly, set);
      }

      if(asbly instanceof ComposedTableAssembly) {
         ComposedTableAssembly comasbly = (ComposedTableAssembly) asbly;
         assemblies = comasbly.getTableAssemblies(false);

         for(TableAssembly assembly : assemblies) {
            removeAssemblies(assembly, set);
         }
      }
   }

   /**
    * Create the assembly tree model.
    */
   private void createAssemblyTreeModel0(TableAssembly assembly,
                                         AssemblyTreeModel.Node node)
   {
      AssemblyTreeModel.Node cnode = createNode(assembly);
      node.addNode(cnode);

      if(assembly instanceof ComposedTableAssembly) {
         TableAssembly[] assemblies =
            ((ComposedTableAssembly) assembly).getTableAssemblies(false);
         Arrays.sort(assemblies, comparator);

         for(TableAssembly assm : assemblies) {
            createAssemblyTreeModel0(assm, cnode);
         }
      }

      TableAssembly[] tassemblies = getSubQueryAssemblies(assembly);

      if(tassemblies.length > 0) {
         AssemblyTreeModel.Node snode = createSubQueryNode();
         cnode.addNode(snode);
         Arrays.sort(tassemblies, comparator);

         for(int i = 0; i < tassemblies.length; i++) {
            createAssemblyTreeModel0(tassemblies[i], snode);
         }
      }
   }

   /**
    * Get the subqueries of the table assembly.
    */
   private TableAssembly[] getSubQueryAssemblies(TableAssembly assembly) {
      Set<AssemblyRef> set = new HashSet<>();

      ConditionListWrapper wrapper = assembly.getPreConditionList();
      collectDependededs(wrapper, set);
      wrapper = assembly.getPostConditionList();
      collectDependededs(wrapper, set);
      wrapper = assembly.getRankingConditionList();
      collectDependededs(wrapper, set);

      List<TableAssembly> assemblies = new ArrayList<>();
      Iterator<AssemblyRef> refs = set.iterator();

      while(refs.hasNext()) {
         String name = refs.next().getEntry().getName();

         if(!assemblies.contains(ws.getAssembly(name))) {
            assemblies.add((TableAssembly) ws.getAssembly(name));
         }
      }

      return assemblies.toArray(new TableAssembly[] {});
   }

   /**
    * Collect dependeds.
    */
   private void collectDependededs(ConditionListWrapper wrapper,
                                   Set<AssemblyRef> set)
   {
      if(wrapper == null) {
         return;
      }

      ConditionList list = wrapper.getConditionList();

      if(list == null) {
         return;
      }

      for(int i = 0; i < list.getConditionSize(); i += 2) {
         XCondition cond = list.getXCondition(i);

         if(!(cond instanceof AssetCondition)) {
            continue;
         }

         ((AssetCondition) cond).getDependeds(set);
      }
   }

   /**
    * Create the sub query node.
    */
   private AssemblyTreeModel.Node createSubQueryNode() {
      TableAssemblyEntry entry = new TableAssemblyEntry();
      entry.setType(TableAssemblyEntry.SUBQUERY);
      entry.setName(catalog.getString("subquery"));
      return new AssemblyTreeModel.Node(entry);
   }

   /**
    * Create an AssemblyTreeModel node.
    */
   private AssemblyTreeModel.Node createNode(TableAssembly assembly) {
      TableAssemblyEntry entry = new TableAssemblyEntry();
      TableAssemblyInfo info = assembly.getTableInfo();
      entry.setName(info.getName());
      setEntryType(entry, assembly);
      entry.setPosition(info.getPixelOffset());
      ToolTipGenerator gen = new ToolTipGenerator(assembly);
      entry.setTipContainer(gen.generateToolTip());
      entry.setAggregate(entry.getTipContainer().isAggregateDefined());
      entry.setCondition(entry.getTipContainer().isConditionDefined());
      return new AssemblyTreeModel.Node(entry);
   }

   /**
    * Set the entry type.
    */
   private void setEntryType(TableAssemblyEntry entry, TableAssembly assembly) {
      if(assembly instanceof BoundTableAssembly) {
         entry.setType(TableAssemblyEntry.PLAIN);
      }
      else if(assembly instanceof EmbeddedTableAssembly) {
         entry.setType(TableAssemblyEntry.EMBEDED);
      }
      else if(assembly instanceof MirrorTableAssembly) {
         entry.setType(TableAssemblyEntry.MIRROR);
      }
      else if(assembly instanceof RotatedTableAssembly) {
         entry.setType(TableAssemblyEntry.ROTATED);
      }
      else if(assembly instanceof UnpivotTableAssembly) {
         entry.setType(TableAssemblyEntry.UNPIVOT);
      }
      else if(assembly instanceof ConcatenatedTableAssembly) {
         ConcatenatedTableAssembly ct = (ConcatenatedTableAssembly) assembly;
         Operator op = ct.getOperator(0).getOperator(0);
         int oper = op.getOperation();

         if(oper == TableAssemblyOperator.UNION) {
            entry.setType(TableAssemblyEntry.UNION);
         }
         else if(oper == TableAssemblyOperator.INTERSECT) {
            entry.setType(TableAssemblyEntry.INTERSECT);
         }
         else if(oper == TableAssemblyOperator.MINUS){
            entry.setType(TableAssemblyEntry.MINUS);
         }
      }
      else if(assembly instanceof AbstractJoinTableAssembly) {
         boolean hasJoin = false;
         AbstractJoinTableAssembly jt = (AbstractJoinTableAssembly) assembly;
         Enumeration<TableAssemblyOperator> ops = jt.getOperators();

         // @by stephenwebster, For bug1428655718393
         // if at least one op is not a merge or cross join, mark the entry
         // as JOIN
         while(ops.hasMoreElements()) {
            TableAssemblyOperator op = ops.nextElement();

            if(!(op.isCrossJoin() || op.isMergeJoin())) {
               hasJoin = true;
               break;
            }
         }

         if(hasJoin) {
            entry.setType(TableAssemblyEntry.JOIN);
         }
         else if(jt.getOperator(0).isMergeJoin()) {
            entry.setType(TableAssemblyEntry.MERGEJOIN);
         }
         else if(jt.getOperator(0).isCrossJoin()) {
            entry.setType(TableAssemblyEntry.CROSSJOIN);
         }
      }
   }

   private Worksheet ws;
   private Comparator<TableAssembly> comparator;
   private Catalog catalog = Catalog.getCatalog();
}
