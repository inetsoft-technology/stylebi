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
package inetsoft.web.viewsheet.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.viewsheet.LineVSAssembly;
import inetsoft.uql.viewsheet.internal.LineVSAssemblyInfo;
import inetsoft.util.Tool;
import org.springframework.stereotype.Component;

import java.awt.*;

@JsonIgnoreProperties(ignoreUnknown = true)
public class VSLineModel extends VSShapeModel<LineVSAssembly> {
   public VSLineModel(LineVSAssembly assembly, RuntimeViewsheet rvs) {
      super(assembly, rvs);

      LineVSAssemblyInfo info = (LineVSAssemblyInfo) assembly.getInfo();

      Point start = info.getLayoutStartPos() != null ?
         info.getLayoutStartPos() : info.getStartPos();
      Point end = info.getLayoutEndPos() != null ?
         info.getLayoutEndPos() : info.getEndPos();

      startLeft = start.x;
      startTop = start.y;
      endLeft = end.x;
      endTop = end.y;
      color = Tool.toString(info.getFormat().getForeground());
      startAnchorStyle = info.getBeginArrowStyle();
      endAnchorStyle = info.getEndArrowStyle();
   }

   public int getStartLeft() { return this.startLeft; }

   public int getStartTop() { return this.startTop; }

   public int getEndLeft() { return this.endLeft; }

   public int getEndTop() { return this.endTop; }

   public int getStartAnchorStyle() { return this.startAnchorStyle; }

   public int getEndAnchorStyle() { return this.endAnchorStyle; }

   public String getColor() { return this.color; }

   private int startLeft;
   private int startTop;
   private int endLeft;
   private int endTop;
   private int startAnchorStyle;
   private int endAnchorStyle;
   private String color;

   @Component
   public static final class VSLineModelFactory
      extends VSObjectModelFactory<LineVSAssembly, VSLineModel>
   {
      public VSLineModelFactory() {
         super(LineVSAssembly.class);
      }

      @Override
      public VSLineModel createModel(LineVSAssembly assembly, RuntimeViewsheet rvs) {
         return new VSLineModel(assembly, rvs);
      }
   }
}
