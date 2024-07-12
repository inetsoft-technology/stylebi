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

import inetsoft.uql.asset.AssemblyRef;
import inetsoft.uql.viewsheet.internal.AnnotationVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;

import java.io.PrintWriter;

/**
 * AnnotationVSAssembly represents one annotation assembly contained in a
 * <tt>Viewsheet</tt>.
 *
 * @version 11.4
 * @author InetSoft Technology Corp
 */
public class AnnotationVSAssembly extends AbstractVSAssembly {
   /**
    * Constructor.
    */
   public AnnotationVSAssembly() {
      super();
   }

   /**
    * Constructor.
    */
   public AnnotationVSAssembly(Viewsheet vs, String name) {
      super(vs, name);
   }

   /**
    * Get the type.
    * @return the type of the assembly.
    */
   @Override
   public int getAssemblyType() {
      return Viewsheet.ANNOTATION_ASSET;
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
    * Get the worksheet assemblies depended on.
    * @return the worksheet assemblies depended on.
    */
   @Override
   public AssemblyRef[] getDependedWSAssemblies() {
      return new AssemblyRef[0];
   }

   /**
    * Create assembly info.
    * @return the associated assembly info.
    */
   @Override
   protected VSAssemblyInfo createInfo() {
      return new AnnotationVSAssemblyInfo();
   }

   /**
    * Write the state.
    * @param writer the specified print writer.
    */
   @Override
   protected void writeStateContent(PrintWriter writer, boolean runtime) {
      super.writeStateContent(writer, runtime);

      if(noAnnotation()) {
         return;
      }

      AnnotationVSAssemblyInfo ainfo = (AnnotationVSAssemblyInfo) info;

      if(runtime && ainfo.getType() == AnnotationVSAssemblyInfo.VIEWSHEET) {
         ainfo.setLastDisplay(System.currentTimeMillis());
         ainfo.writeXML(writer);
      }
   }
}
