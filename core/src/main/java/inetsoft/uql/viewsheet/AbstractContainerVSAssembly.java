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
package inetsoft.uql.viewsheet;

import inetsoft.uql.asset.Assembly;
import inetsoft.uql.asset.AssemblyRef;
import inetsoft.uql.viewsheet.internal.ContainerVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSUtil;

import java.awt.*;
import java.util.Map;
import java.util.Set;

/**
 * ContainerVSAssembly represents one container assembly contained in a
 * <tt>Viewsheet</tt>.
 *
 * @version 10.1
 * @author InetSoft Technology Corp
 */
public abstract class AbstractContainerVSAssembly extends AbstractVSAssembly
   implements ContainerVSAssembly
{
   /**
    * Constructor.
    */
   public AbstractContainerVSAssembly() {
      super();
   }

   /**
    * Constructor.
    */
   public AbstractContainerVSAssembly(Viewsheet vs, String name) {
      super(vs, name);
   }

   /**
    * Get the worksheet assemblies depended on.
    * @return the worksheet assemblies depended on.
    */
   @Override
   public AssemblyRef[] getDependedWSAssemblies() {
      return new AssemblyRef[0];
   }

   /**
    * Get the depending worksheet assemblies to modify.
    * @return the depending worksheet assemblies to modify.
    */
   @Override
   public AssemblyRef[] getDependingWSAssemblies() {
      return new AssemblyRef[0];
   }

   /**
    * Get the view assemblies depended on.
    * @param set the set stores the presentation assemblies depended on.
    * @param self <tt>true</tt> to include self, <tt>false</tt> otherwise.
    */
   @Override
   public void getViewDependeds(Set<AssemblyRef> set, boolean self) {
      getViewDependeds(set, self, true);
   }

   public void getViewDependeds(Set<AssemblyRef> set, boolean self, boolean children) {
      super.getViewDependeds(set, self);

      if(children) {
         String[] arr = getAssemblies();

         for(String str : arr) {
            Assembly assembly = getViewsheet().getAssembly(str);

            if(assembly != null) {
               set.add(new AssemblyRef(AssemblyRef.VIEW, assembly.getAssemblyEntry()));
            }
         }

         // add adhoc selection list that is currently showing outside of the
         // container. (62488)
         for(Assembly obj : getViewsheet().getAssemblies()) {
            if(obj instanceof AbstractSelectionVSAssembly) {
               Map<String, Object> ah = ((AbstractSelectionVSAssembly) obj).getAhFilterProperty();

               if(ah != null && getAbsoluteName().equals(ah.get("_container"))) {
                  set.add(new AssemblyRef(AssemblyRef.VIEW, obj.getAssemblyEntry()));
               }
            }
         }
      }
   }

   /**
    * Rename the depended. This method should be called when an assembly or
    * other named variables are renamed. It updates of the dynamic references
    * to use the new name.
    * @param oname the specified old name.
    * @param nname the specified new name.
    */
   @Override
   public void renameDepended(String oname, String nname) {
      String[] arr = getAssemblies();

      for(int i = 0; i < arr.length; i++) {
         if(arr[i].equals(oname)) {
            arr[i] = nname;
         }
      }

      setAssemblies(arr);
   }

   /**
    * Get the assemblies.
    * @return the assemblies of the tab assembly.
    */
   @Override
   public String[] getAssemblies() {
      return getContainerInfo().getAssemblies();
   }

   /**
    * Get the child assemblies' absolute name.
    * @return the child assemblies absolute name.
    */
   @Override
   public String[] getAbsoluteAssemblies() {
      return getContainerInfo().getAbsoluteAssemblies();
   }

   /**
    * Set the assemblies.
    * @param assemblies the specified value.
    */
   @Override
   public void setAssemblies(String[] assemblies) {
      getContainerInfo().setAssemblies(assemblies);
   }

   /**
    * Check if contains an assembly as a child.
    * @param assembly container specified assembly.
    * @return <tt>true</tt> if contains the assembly, <tt>false</tt> otherwise.
    */
   public boolean containsAssembly(Assembly assembly) {
      return containsAssembly(assembly.getName());
   }

   /**
    * Check if contains an assembly as a child.
    * @param assembly the specified assembly.
    * @return <tt>true</tt> if contains the assembly, <tt>false</tt> otherwise.
    */
   @Override
   public boolean containsAssembly(String assembly) {
      String[] arr = getAssemblies();

      for(int i = 0; i < arr.length; i++) {
         if(arr[i].equals(assembly)) {
            return true;
         }
      }

      return false;
   }

   /**
    * Remove a child assembly from the container.
    */
   @Override
   public boolean removeAssembly(String assembly) {
      String[] arr = getAssemblies();

      for(int i = 0; i < arr.length; i++) {
         if(arr[i].equals(assembly)) {
            String[] narr = new String[arr.length - 1];

            System.arraycopy(arr, 0, narr, 0, i);
            System.arraycopy(arr, i + 1, narr, i, arr.length - i - 1);
            setAssemblies(narr);
            return true;
         }
      }

      return false;
   }

   /**
    * Set the position.
    * @return the position of the assembly.
    */
   @Override
   public void setPixelOffset(Point pos) {
      setPixelOffset(pos, true);
   }

   public void setPixelOffset(Point pos, boolean updateChildren) {
      Point opos = getPixelOffset();
      super.setPixelOffset(pos);

      if(updateChildren) {
         updateChildPosition(opos);
      }
   }
   /**
    * Calc the sub component z index.
    */
   @Override
   public void calcChildZIndex() {
      String[] arr = getAssemblies();
      Assembly[] assemblies = new Assembly[arr.length];
      VSUtil.calcChildZIndex(assemblies, this.getZIndex());

      for(int i = 0; i < arr.length; i++) {
         assemblies[i] = getViewsheet().getAssembly(arr[i]);

         if(assemblies[i] instanceof ContainerVSAssembly) {
            ((ContainerVSAssembly) assemblies[i]).calcChildZIndex();
         }
      }
   }

   /**
    * Layout the Container Assembly.
    * @return the names of the assemblies relocated.
    */
   @Override
   public Assembly[] layout() {
      return new Assembly[0];
   }

   /**
    * Update the children position.
    */
   protected void updateChildPosition(Point opos) {
      String[] children = getAssemblies();
      Point pos = getPixelOffset();
      int xchange = pos.x - opos.x;
      int ychange = pos.y - opos.y;

      for(int i = 0; i < children.length; i++) {
         Assembly child = getViewsheet().getAssembly(children[i]);

         if(child == null) {
            continue;
         }

         Point cpos = child.getPixelOffset();
         child.setPixelOffset(new Point(cpos.x + xchange, cpos.y + ychange));
      }
   }

   /**
    * Get container assembly info.
    * @return the container assembly info.
    */
   protected ContainerVSAssemblyInfo getContainerInfo() {
      return (ContainerVSAssemblyInfo) info;
   }
}
