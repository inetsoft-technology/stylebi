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
package inetsoft.uql.asset.internal;

import inetsoft.uql.asset.*;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.SelectionListVSAssemblyInfo;
import inetsoft.util.Tuple;

import java.util.*;

/**
 * Assembly comparator compares two assembly dependency.
 *
 * <b>WARNING</b> this implementation violates the transitive constraint, so the default JDK sort
 * methods cannot be used. A merge sort implementation such as
 * {@link inetsoft.util.Tool#mergeSort(Object[], Comparator)} must be used with this comparator.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class DependencyComparator implements Comparator {
   /**
    * Constructor.
    *
    * @param sheet the specified sheet.
    */
   public DependencyComparator(AbstractSheet sheet) {
      this(sheet, false);
   }

   /**
    * Constructor.
    *
    * @param sheet the specified sheet.
    * @param asc   the specified sort order.
    */
   public DependencyComparator(AbstractSheet sheet, boolean asc) {
      this.sheet = sheet;
      this.asc = asc;
   }

   /**
    * Get the associated assembly.
    *
    * @param obj the specified object.
    *
    * @return the associated assembly.
    */
   private Assembly getAssembly(Object obj) {
      if(obj instanceof Assembly) {
         return (Assembly) obj;
      }
      else if(obj instanceof AssemblyEntry) {
         return sheet == null ? null : sheet.getAssembly((AssemblyEntry) obj);
      }
      else {
         throw new RuntimeException("Unsupported obj found: " + obj);
      }
   }

   /**
    * Compare two object.
    *
    * @param obja the object a.
    * @param objb the object b.
    */
   @Override
   public int compare(Object obja, Object objb) {
      Assembly a = getAssembly(obja);
      Assembly b = getAssembly(objb);
      return compareDep(a, b, new HashMap<>());
   }

   // compare dependency
   // @param results cached results
   private int compareDep(Assembly a, Assembly b, Map<Object, Long> results) {
      if(a == b) {
         return 0;
      }
      else if(a == null) {
         return -1;
      }
      else if(b == null) {
         return 1;
      }

      Object pairKey = new Tuple(a.getAssemblyEntry(), b.getAssemblyEntry());
      Long result = results.get(pairKey);

      if(result != null) {
         // check for infinite recursion, just compare name if found
         if(result == Long.MAX_VALUE) {
            return a.getAbsoluteName().compareTo(b.getAbsoluteName());
         }

         // use cached result
         return result.intValue();
      }
      else {
         // mark a and b is being compared
         results.put(pairKey, Long.MAX_VALUE);
      }

      int rc = compareDep0(a, b, results);
      // cache result
      results.put(pairKey, (long) rc);
      return rc;
   }

   private int compareDep0(Assembly a, Assembly b, Map<Object, Long> results) {
      // ws assembly always lower on the list
      if(a instanceof VSAssembly && !(b instanceof VSAssembly)) {
         return applyOrder(-1);
      }

      if(!(a instanceof VSAssembly) && b instanceof VSAssembly) {
         return applyOrder(1);
      }

      boolean aParent = isChild(a, b);
      boolean bParent = isChild(b, a);

      // ignore circular dependency otherwise sorting will fail
      if(aParent) {
         return applyOrder(1);
      }
      else if(bParent) {
         return applyOrder(-1);
      }

      final int parentComparison = compareParentsOf(a, b, results);

      if(parentComparison != 0) {
         // don't need to applyOrder() since compareParents calls compare().
         return parentComparison;
      }

      final int rankA = getExcludedSelectionRanking(a);
      final int rankB = getExcludedSelectionRanking(b);

      // force selections contains excluded selection to be processed before
      // others, then we may execute tables after them
      if(rankA != rankB) {
         return applyOrder(rankA - rankB);
      }

      // vs tab must be processed after its children, vs tab's visibility
      // depends on the number of it's visible children
      if(a instanceof VSAssembly && b instanceof VSAssembly) {
         if(isDescendant((VSAssembly) a, (VSAssembly) b)) {
            return applyOrder(-1);
         }
         else if(isDescendant((VSAssembly) b, (VSAssembly) a)) {
            return applyOrder(-1);
         }
      }

      if(a instanceof SelectionListVSAssembly && b instanceof SelectionListVSAssembly) {
         SelectionListVSAssemblyInfo selectionListInfoA =
            ((SelectionListVSAssembly) a).getSelectionListInfo();
         SelectionListVSAssemblyInfo selectionListInfoB =
            ((SelectionListVSAssembly) b).getSelectionListInfo();

         if(selectionListInfoA.isSelectFirstItem() && !selectionListInfoB.isSelectFirstItem()) {
            return applyOrder(-1);
         }
         else if(!selectionListInfoA.isSelectFirstItem() && selectionListInfoB.isSelectFirstItem())
         {
            return applyOrder(1);
         }
      }

      // need some kind of ordering to avoid violating sorting contract
      return applyOrder(a.getName().compareTo(b.getName()));
   }

   private int applyOrder(int res) {
      return asc ? res : -res;
   }

   private static boolean isDescendant(VSAssembly child, VSAssembly ascendant) {
      if(child.getContainer() == null) {
         return false;
      }

      if(child.getContainer() == ascendant && isDescendant(child.getContainer(), ascendant)) {
         return true;
      }

      return false;
   }

   /**
    * Get the excluded selection ranking of an assembly.
    *
    * @param assembly the specified assembly.
    *
    * @return the excluded selection ranking of this assembly.
    */
   private int getExcludedSelectionRanking(Assembly assembly) {
      if(!(assembly instanceof AssociatedSelectionVSAssembly)) {
         return 1;
      }

      AssociatedSelectionVSAssembly selection = (AssociatedSelectionVSAssembly) assembly;
      return selection.containsExcludedSelection() ? 0 : 1;
   }

   /**
    * Check if an assembly is the child of another one.
    *
    * @param p the specified assembly a.
    * @param c the specified assembly b.
    *
    * @return <tt>true</tt> if is the child, <tt>false</tt> otherwise.
    */
   private boolean isChild(Assembly p, Assembly c) {
      if(p == null || c == null) {
         return false;
      }

      final Assembly[] arr = deps.computeIfAbsent(
         p, k ->AssetUtil.getDependedAssemblies(sheet, p, false, true, true));

      for(final Assembly assembly : arr) {
         if(assembly == c) {
            return true;
         }
      }

      return false;
   }

   /**
    * Compare the parents of the assemblies level by level starting from the root parent assembly.
    */
   private int compareParentsOf(Assembly a, Assembly b, Map<Object, Long> results) {
      List<Assembly> parentsA = getParents(a);
      List<Assembly> parentsB = getParents(b);

      for(int i = 0; i < parentsA.size() && i < parentsB.size(); i++) {
         final Assembly parentA = parentsA.get(i);
         final Assembly parentB = parentsB.get(i);
         final int compare = compareDep(parentA, parentB, results);

         if(compare != 0) {
            return compare;
         }
      }

      if(parentsA.size() < parentsB.size()) {
         return compareDep(a, parentsB.get(parentsA.size()), results);
      }
      else if(parentsA.size() > parentsB.size()) {
         return compareDep(parentsA.get(parentsB.size()), b, results);
      }

      return 0;
   }

   /**
    * Get a path of parent assemblies. If an assembly has parent assemblies, the assembly with
    * the first lexicographic name is used.
    *
    * In this context, assembly A is a parent of B if B (e.g. VSTable) depends on A (e.g. WSTable).
    */
   private List<Assembly> getParents(Assembly assembly) {
      final List<Assembly> parents = new ArrayList<>();
      Assembly curr = assembly;

      while(curr != null) {
         Assembly parent = childToFirstParent.get(curr);

         if(parent == null && !childToFirstParent.containsKey(curr)) {
            final AssemblyRef[] arr = sheet.getDependeds(curr.getAssemblyEntry());
            Arrays.sort(arr, Comparator.comparing(a -> a.getEntry().getName()));

            parent = arr.length > 0 ? sheet.getAssembly(arr[0].getEntry()) : null;
            childToFirstParent.put(curr, parent);
         }

         if(parents.contains(parent)) {
            // cycle, should not happen but break out to avoid inf loop
            break;
         }

         if(parent != null) {
            parents.add(parent);
         }

         curr = parent;
      }

      // avoid recursive dependency
      parents.remove(assembly);
      Collections.reverse(parents);
      return parents;
   }

   private AbstractSheet sheet;
   private boolean asc;

   private final Map<Assembly, Assembly[]> deps = new HashMap<>();
   private final Map<Assembly, Assembly> childToFirstParent = new HashMap<>();
}
