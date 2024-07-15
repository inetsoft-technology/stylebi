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

import inetsoft.util.dep.WorksheetAsset;
import inetsoft.util.dep.XAssetDependency;
import inetsoft.uql.asset.*;

import java.util.List;

/**
 * Worksheet asset for snapshot exporting.
  * @version 10.3
 * @author InetSoft Technology Corp
 */
public class WorksheetAsset2 extends WorksheetAsset {
   /**
    * Constructor.
    */
   public WorksheetAsset2() {
      super();
   }

   /**
    * Constructor.
    * @param worksheet the worksheet asset entry.
    */
   public WorksheetAsset2(AssetEntry worksheet) {
      super(worksheet);
   }

   /**
    * Constructor.
    * @param worksheet the worksheet asset entry.
    * @param engine the specified asset engine.
    */
   public WorksheetAsset2(AssetEntry worksheet, AssetRepository engine) {
      super(worksheet, engine);
   }   
   
   /**
    * Get ws-data source dependency.
    */
   @Override
   protected void getDataDependency(Assembly assembly, List<XAssetDependency> dependencies) {
   }   

   /**
    * Get ws-ws dependency.
    */
   @Override
   protected void getWSDependency(Assembly assembly, List<XAssetDependency> dependencies) {
   }
      
   /**
    * Get ws-query dependency.
    */
   @Override
   protected void getQueryDependency(Assembly assembly, List<XAssetDependency> dependencies) {
   }

   /**
    * Get ws-query dependency.
    */
   @Override
   protected void getAutoDrillDependency(Assembly assembly, List<XAssetDependency> dependencies) {
   }
}