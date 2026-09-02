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
package inetsoft.web.viewsheet.model;

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.viewsheet.FormatInfo;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.internal.CompositeVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;

import java.awt.*;

public abstract class VSCompositeModel<T extends VSAssembly> extends VSObjectModel<T> {
   protected VSCompositeModel(T assembly, RuntimeViewsheet rvs) {
      super(assembly, rvs);
      CompositeVSAssemblyInfo assemblyInfo =
        (CompositeVSAssemblyInfo) assembly.getVSAssemblyInfo();
      FormatInfo finfo = ((VSAssemblyInfo) assemblyInfo).getFormatInfo();

      title = assemblyInfo.getTitle();
      titleFormat = new VSFormatModel(finfo.getFormat(VSAssemblyInfo.TITLEPATH, false),
                                      (VSAssemblyInfo) assemblyInfo);
      Dimension size = new Dimension((int) getObjectFormat().getWidth(),
                                     assemblyInfo.getTitleHeight());
      titleFormat.setPositions(new Point(0, 0), size);
      titleVisible = assemblyInfo.isTitleVisible();
   }

   public String getTitle() {
      return title;
   }

   public VSFormatModel getTitleFormat() {
      return titleFormat;
   }

   public boolean getTitleVisible() {
      return titleVisible;
   }

   @Override
   public String toString() {
      return "{" + super.toString() + " " +
         "title:" + title + " " +
         "titleFormat:" + titleFormat + "} ";
   }

   private String title;
   private VSFormatModel titleFormat;
   private boolean titleVisible;
}
