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
package inetsoft.report.io.viewsheet.snapshot;

import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.util.dep.*;

import java.util.List;

/**
 * Viewsheet asset for snapshot exporting.
  * @version 10.3
 * @author InetSoft Technology Corp
 */
public class ViewsheetAsset2 extends ViewsheetAsset {
   /**
    * Constructor.
    */
   public ViewsheetAsset2() {
      super();
   }

   /**
    * Constructor.
    * @param entry the viewsheet asset entry.
    */
   public ViewsheetAsset2(AssetEntry entry) {
      super(entry);
   }   

   /**
    * Get vs-ws dependency.
    */
   @Override
   protected void getWSDependency(AssetEntry wentry, List<XAssetDependency> dependencies) {
      String desc = generateDescription(entry.getDescription(),
         wentry.getDescription());
      dependencies.add(new XAssetDependency(new WorksheetAsset2(wentry),
        this, XAssetDependency.VIEWSHEET_WORKSHEET, desc));
   }
      
   /**
    * Get vs-query dependency.
    */
   @Override
   protected void getQueryDependency(AssetEntry wentry, List<XAssetDependency> dependencies) {
      convertQTL(wentry, dependencies);
   }   

   /**
    * Get vs-logical model dependency.
    */
   @Override
   protected void getModelDependency(AssetEntry wentry, List<XAssetDependency> dependencies,
                                     List<XAssetDependency> list)
   {
      convertQTL(wentry, dependencies);
   }

   /**
    * Get vs-physical table dependency.
    */
   @Override
   protected void getPhyDependency(AssetEntry wentry, List<XAssetDependency> dependencies) {
      convertQTL(wentry, dependencies);
   }

   /**
    * Get vs-vs dependency.
    */
   @Override
   protected void getVSDependency(Viewsheet vs, List<XAssetDependency> dependencies) {
      AssetEntry viewEntry = vs.getEntry();
      String desc = generateDescription(entry.getDescription(),
                                        viewEntry.getDescription());
      ViewsheetAsset vsAsset = new ViewsheetAsset2(viewEntry);
      vsAsset.setSheet(vs);
      dependencies.add(new XAssetDependency(vsAsset, this,
         XAssetDependency.VIEWSHEET_VIEWSHEET, desc));
   }

   /**
    * Convert Query/Physical Table/LogicalModel(QTL) entry to worksheet entry.
    */
   private void convertQTL(AssetEntry wentry, List<XAssetDependency> dependencies) {
      int scope = entry.getScope();
      scope = scope == AssetRepository.TEMPORARY_SCOPE ? 
         AssetRepository.GLOBAL_SCOPE : scope;
      wentry = new AssetEntry(scope, AssetEntry.Type.WORKSHEET,
                              wentry.getPath(), entry.getUser());
      WorksheetAsset wasset = new WorksheetAsset2(wentry);
      wasset.setSheet(((Viewsheet) sheet).getBaseWorksheet());
      String desc = generateDescription(entry.getDescription(),
                                        wentry.getDescription());
      dependencies.add(new XAssetDependency(wasset,
        this, XAssetDependency.VIEWSHEET_WORKSHEET, desc));                              
   }
}