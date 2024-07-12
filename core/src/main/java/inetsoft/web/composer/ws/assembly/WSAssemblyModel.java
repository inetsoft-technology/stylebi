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
package inetsoft.web.composer.ws.assembly;

import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.uql.asset.AbstractWSAssembly;
import inetsoft.uql.asset.AssemblyRef;
import inetsoft.web.composer.model.ws.DependencyType;

import java.util.*;

public abstract class WSAssemblyModel {
   WSAssemblyModel(AbstractWSAssembly assembly, RuntimeWorksheet rws) {
      setName(assembly.getAbsoluteName());
      setDescription(assembly.getDescription());

      if(assembly.getPixelOffset() != null){
         setTop(assembly.getPixelOffset().getY());
         setLeft(assembly.getPixelOffset().getX());
      }

      if(rws.getWorksheet() != null) {
         setPrimary(assembly.equals(rws.getWorksheet().getPrimaryAssembly()));

         ArrayList<String> dependings = new ArrayList<>();

         for(AssemblyRef ref : rws.getWorksheet().getDependings(assembly.getAssemblyEntry())) {
            dependings.add(ref.getEntry().getAbsoluteName());
         }

         setDependings(dependings);
      }

      HashMap<String, Set<DependencyType>> augmentedDependeds = new HashMap<>();
      assembly.getAugmentedDependeds(augmentedDependeds);

      augmentedDependeds.forEach(
         (assemblyName, dependencyTypes) -> getDependeds().add(
            WSDependency.builder()
               .assemblyName(assemblyName)
               .types(dependencyTypes)
               .build()));
   }

   public abstract String getClassType();

   public abstract WSAssemblyInfoModel getInfo();

   public String getName() {
      return name;
   }

   public void setName(String name) {
      this.name = name;
   }

   public String getDescription() {
      return description;
   }

   public void setDescription(String desc) {
      this.description = desc;
   }

   public double getTop() {
      return top;
   }

   public void setTop(double top) {
      this.top = top;
   }

   public double getLeft() {
      return left;
   }

   public void setLeft(double left) {
      this.left = left;
   }

   public List<WSDependency> getDependeds() {
      if(dependeds == null) {
         dependeds = new ArrayList<>();
      }

      return dependeds;
   }

   public void setDependeds(List<WSDependency> dependeds) {
      this.dependeds = dependeds;
   }

   public List<String> getDependings() {
      if(dependings == null) {
         dependings = new ArrayList<>();
      }
      return dependings;
   }

   public void setDependings(List<String> dependings) {
      this.dependings = dependings;
   }

   public boolean getPrimary() {
      return primary;
   }

   public void setPrimary(boolean primary) {
      this.primary = primary;
   }

   private String name;
   private String description;
   private double top;
   private double left;
   private List<WSDependency> dependeds;
   private List<String> dependings;
   private boolean primary;
}