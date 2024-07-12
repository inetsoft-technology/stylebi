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
package inetsoft.uql.viewsheet;

import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;

import java.util.*;

/**
 * A comparator compares viewsheet assembly zindex.
 *
 * @version 10.1
 * @author InetSoft Technology Corp
 */
public class ZIndexComparator implements Comparator {
   /**
    * Constructor.
    * @param recursive true if compare all assemblies, false only compare
    * assemblies in current level.
    */
   public ZIndexComparator(boolean recursive, Viewsheet viewsheet) {
      this.recursive = recursive;
      this.viewsheet = viewsheet;
   }

   /**
    * Compare two viewsheet assembly.
    */
   @Override
   public int compare(Object obj0, Object obj1) {
      if(obj0 instanceof VSAssembly && obj1 instanceof VSAssembly) {
         VSAssembly assembly0 = (VSAssembly) obj0;
         VSAssembly assembly1 = (VSAssembly) obj1;

         if(recursive) {
            VSAssembly[] list0 = getDisplayList(assembly0);
            VSAssembly[] list1 = getDisplayList(assembly1);
            int length = Math.min(list0.length, list1.length);

            for(int i = 0; i < length; i++) {
               int idx0 = list0[i].getZIndex();
               int idx1 = list1[i].getZIndex();

               if(idx0 != idx1) {
                  return idx0 - idx1;
               }
            }

            return list0.length - list1.length;
         }
         else {
            return assembly0.getZIndex() - assembly1.getZIndex();
         }
      }

      return 0;
   }

   /**
    * Get assembly display list.
    */
   private VSAssembly[] getDisplayList(VSAssembly assembly) {
      VSAssemblyInfo info = assembly.getVSAssemblyInfo();
      String aname = info.getAbsoluteName();
      String vname = viewsheet.getAbsoluteName();

      if(vname != null) {
         aname = aname.indexOf(vname) >= 0 ? aname.substring(vname.length()) :
            aname;
      }
      
      StringTokenizer tokenizer = new StringTokenizer(aname, ".");
      ArrayList list = new ArrayList();
      String name = "";

      while(tokenizer.hasMoreTokens()) {
         name = name + tokenizer.nextToken();
         VSAssembly assembly0 = (VSAssembly) viewsheet.getAssembly(name);

         if(assembly0 != null) {
            list.add(assembly0);
         }

         if(tokenizer.hasMoreTokens()) {
            name = name + ".";
         }
      }

      addContainer(assembly, list);
      VSAssembly[] arr = new VSAssembly[list.size()];
      list.toArray(arr);

      return arr;
   }

   /**
    * Get assembly display list.
    */
   private void addContainer(VSAssembly assembly, ArrayList list) {
      int containerCount = 0;

      while(assembly != null) {
         assembly = assembly.getContainer();

         if(assembly != null) {
            list.add(list.size() == 0 ? 0 :
               list.size() - containerCount - 1, assembly);
         }

         containerCount++;
      }
   }

   private boolean recursive;
   private Viewsheet viewsheet;
}
