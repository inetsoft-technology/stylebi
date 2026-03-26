/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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

package inetsoft.web.wiz;

import inetsoft.uql.asset.Assembly;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;

import java.awt.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class WizUtil {
   public static String decodeId(String id) {
      String decodedId;

      if(id == null || id.isEmpty()) {
         decodedId = null;
      }
      else {
         try {
            decodedId = new String(Base64.getDecoder().decode(id), StandardCharsets.UTF_8);
         }
         catch(IllegalArgumentException e) {
            decodedId = null;
         }
      }

      return decodedId;
   }

   /**
    * Applies max mode state to the primary assembly of a viewsheet without refreshing.
    * The caller is responsible for triggering a viewsheet refresh afterward.
    *
    * @param vs      the viewsheet.
    * @param maxSize the max mode dimensions.
    */
   public static void prepareMaxMode(Viewsheet vs, Dimension maxSize) {
      if(vs == null || vs.getWizInfo() == null || !vs.getWizInfo().isWizVisualization() ||
         maxSize == null || maxSize.width <= 0 || maxSize.height <= 0)
      {
         return;
      }

      for(Assembly assembly : vs.getAssemblies()) {
         if(!(assembly instanceof VSAssembly vsAssembly)) {
            continue;
         }

         VSAssemblyInfo info = vsAssembly.getVSAssemblyInfo();

         if(info instanceof ChartVSAssemblyInfo chartInfo) {
            chartInfo.setMaxSize(maxSize);
            vs.setMaxMode(true);
            setMaxModeZIndex(vs, info, maxSize);
            return;
         }
         else if(info instanceof TableDataVSAssemblyInfo tableInfo) {
            tableInfo.setMaxSize(maxSize);
            vs.setMaxMode(true);
            setMaxModeZIndex(vs, info, maxSize);
            return;
         }
      }
   }

   private static void setMaxModeZIndex(Viewsheet vs, VSAssemblyInfo info, Dimension maxSize) {
      if(maxSize == null) {
         return;
      }

      Assembly[] assemblies = vs.getAssemblies(true, true);

      if(assemblies == null || assemblies.length == 0) {
         return;
      }

      VSAssembly top = (VSAssembly) assemblies[assemblies.length - 1];
      int zIndex = top.getVSAssemblyInfo().getZIndex() + 1;

      if(info instanceof ChartVSAssemblyInfo chartInfo) {
         chartInfo.setMaxModeZIndex(zIndex);
      }
      else if(info instanceof TableDataVSAssemblyInfo tableInfo) {
         tableInfo.setMaxModeZIndex(zIndex);
      }
   }

   public static final String ANNOTATION_RAW_DATA_MAX_ROW = "annotation.rawdata.maxrow";
}
