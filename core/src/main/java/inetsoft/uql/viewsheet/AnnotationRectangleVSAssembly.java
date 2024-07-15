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
package inetsoft.uql.viewsheet;

import inetsoft.uql.asset.Assembly;
import inetsoft.uql.viewsheet.internal.*;

import java.io.PrintWriter;

/**
 * AnnotationRectangleVSAssembly represents one annotation rectangle assembly
 * contained in a <tt>Viewsheet</tt>.
 *
 * @version 11.4
 * @author InetSoft Technology Corp
 */
public class AnnotationRectangleVSAssembly extends RectangleVSAssembly {
   /**
    * Constructor.
    */
   public AnnotationRectangleVSAssembly() {
      super();
   }

   /**
    * Constructor.
    */
   public AnnotationRectangleVSAssembly(Viewsheet vs, String name) {
      super(vs, name);
   }

   /**
    * Get the type.
    * @return the type of the assembly.
    */
   @Override
   public int getAssemblyType() {
      return Viewsheet.ANNOTATION_RECTANGLE_ASSET;
   }

   /**
    * Create assembly info.
    * @return the associated assembly info.
    */
   @Override
   protected VSAssemblyInfo createInfo() {
      return new AnnotationRectangleVSAssemblyInfo();
   }

   /**
    * Write the state.
    * @param writer the specified print writer.
    */
   @Override
   protected void writeStateContent(PrintWriter writer, boolean runtime) {
      super.writeStateContent(writer, runtime);
      Assembly ass = AnnotationVSUtil.getAnnotationAssembly(
         getViewsheet(), info.getAbsoluteName());

      if(ass == null || noAnnotation()) {
         return;
      }

      AnnotationVSAssemblyInfo ainfo = (AnnotationVSAssemblyInfo) ass.getInfo();

      if(runtime && ainfo.getType() == AnnotationVSAssemblyInfo.VIEWSHEET) {
         info.writeXML(writer);
      }
   }
}
