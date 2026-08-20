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
import inetsoft.uql.asset.AssetEntry;
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
         // The line and rectangle are reported under their annotation rather than beside it.
         // Flat, the three read as unrelated assemblies with nothing to say they are one
         // object, and an agent asked to "remove the annotation" would remove a third of it.
         if(assembly instanceof VSAssembly vsAssembly &&
            !AnnotationFamily.isSubordinatePart(vsAssembly))
         {
            nodes.add(toNode(vs, vsAssembly));
         }
      }

      return new ViewsheetModel(sheetName(rvs), nodes);
   }

   /**
    * The sheet's name, from its asset entry.
    *
    * <p>Deliberately not {@code Viewsheet.getName()}, which is the <em>assembly</em> name: it is
    * set only by {@code createVSAssembly(name)}, i.e. when a viewsheet is embedded in another
    * viewsheet as an assembly, so for a top-level sheet it is legitimately null and never answered
    * the question this field is asking.
    *
    * <p>It was worse than merely empty, because that null does not survive a save/reload.
    * {@code AssemblyInfo.writeAttributes} emits {@code "<name><![CDATA[" + name + "]]></name>"},
    * and concatenating a null yields the four characters {@code null} — so a reopened sheet
    * reported the <b>string</b> {@code "null"}, which a caller testing {@code if(name)} accepts as
    * a real name and renders to a user. Left the shared serialization alone: it is on the path of
    * every assembly in every sheet, and the one field that already carries a hand-patch for this
    * ({@code VSAssemblyInfo} maps {@code "null"} back to null when parsing {@code absoluteName2})
    * shows the flaw is older and wider than this read.
    */
   private static String sheetName(RuntimeViewsheet rvs) {
      AssetEntry entry = rvs.getEntry();
      return entry == null ? null : entry.getName();
   }

   private AssemblyNode toNode(Viewsheet vs, VSAssembly assembly) {
      Point offset = assembly.getPixelOffset();
      Dimension size = assembly.getPixelSize();
      Assembly container = assembly.getContainer();

      List<String> parts = AnnotationFamily.partsOf(assembly);

      return new AssemblyNode(
         assembly.getAbsoluteName(),
         typeName(assembly),
         offset == null ? 0 : offset.x,
         offset == null ? 0 : offset.y,
         size == null ? 0 : size.width,
         size == null ? 0 : size.height,
         assembly.getVSAssemblyInfo() == null ? 0 : assembly.getVSAssemblyInfo().getZIndex(),
         container == null ? null : container.getName(),
         assembly.isVisible(),
         parts.isEmpty() ? null : parts,
         AnnotationFamily.contentOf(vs, assembly));
   }

   /** "GaugeVSAssembly" reads as "Gauge" — the name the user sees in the Composer. */
   private String typeName(VSAssembly assembly) {
      String simple = assembly.getClass().getSimpleName();
      return simple.endsWith("VSAssembly")
         ? simple.substring(0, simple.length() - "VSAssembly".length())
         : simple;
   }
}
