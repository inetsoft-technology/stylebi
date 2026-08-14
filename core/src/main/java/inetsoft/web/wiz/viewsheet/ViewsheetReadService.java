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
package inetsoft.web.wiz.viewsheet;

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.web.wiz.viewsheet.model.AssemblyNode;
import inetsoft.web.wiz.viewsheet.model.ViewsheetModel;
import org.springframework.stereotype.Service;

import java.awt.Dimension;
import java.awt.Point;
import java.util.ArrayList;
import java.util.List;

/** Builds the agent-facing layout model from a live viewsheet runtime. */
@Service
public class ViewsheetReadService {
   public ViewsheetModel read(RuntimeViewsheet rvs) {
      Viewsheet vs = rvs == null ? null : rvs.getViewsheet();

      if(vs == null) {
         return new ViewsheetModel(null, List.of());
      }

      List<AssemblyNode> nodes = new ArrayList<>();

      for(Assembly assembly : vs.getAssemblies()) {
         if(assembly instanceof VSAssembly vsAssembly) {
            nodes.add(toNode(vsAssembly));
         }
      }

      return new ViewsheetModel(vs.getName(), nodes);
   }

   private AssemblyNode toNode(VSAssembly assembly) {
      Point offset = assembly.getPixelOffset();
      Dimension size = assembly.getPixelSize();
      Assembly container = assembly.getContainer();

      return new AssemblyNode(
         assembly.getAbsoluteName(),
         typeName(assembly),
         offset == null ? 0 : offset.x,
         offset == null ? 0 : offset.y,
         size == null ? 0 : size.width,
         size == null ? 0 : size.height,
         assembly.getVSAssemblyInfo() == null ? 0 : assembly.getVSAssemblyInfo().getZIndex(),
         container == null ? null : container.getName(),
         assembly.isVisible());
   }

   /** "GaugeVSAssembly" reads as "Gauge" — the name the user sees in the Composer. */
   private String typeName(VSAssembly assembly) {
      String simple = assembly.getClass().getSimpleName();
      return simple.endsWith("VSAssembly")
         ? simple.substring(0, simple.length() - "VSAssembly".length())
         : simple;
   }
}
