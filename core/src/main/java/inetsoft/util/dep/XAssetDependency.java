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
package inetsoft.util.dep;

import inetsoft.sree.internal.SUtil;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;

import java.util.Objects;

/**
 * XAsset dependency describes the dependency details between 2 assets,
 * such as dependency type, detailed depending relationship.
 *
 * @version 9.1
 * @author InetSoft Technology Corp
 */
public class XAssetDependency {
   /**
    * A bindable element in a report depends on a data source.
    */
   public static final int REPORT_DATASOURCE = 4;
   /**
    * A bindable element in a report depends on a query.
    */
   public static final int REPORT_QUERY = 5;
   /**
    * A bindable element in a report depends on a worksheet.
    */
   public static final int REPORT_WORKSHEET = 7;
   /**
    * Viewsheet dashboard depends on a viewsheet.
    */
   public static final int DASHBOARD_VIEWSHEET = 11;
   /**
    * Schedule task depends on a replet.
    */
   public static final int SCHEDULETASK_REPLET = 14;
   /**
    * Logical model browsing data depends on a query.
    */
   public static final int XLOGICALMODEL_BROWSE_XQUERY = 16;
   /**
    * Logical model auto drill depends on a query.
    */
   public static final int XLOGICALMODEL_DRILL_XQUERY = 17;
   /**
    * Query depends on a data source.
    */
   public static final int XQUERY_XDATASOURCE = 18;
   /**
    * Query depends on another query.
    */
   public static final int XQUERY_XQUERY = 19;
   /**
    * Query auto drill depends on another query.
    */
   public static final int XQUERY_DRILL_XQUERY = 20;
   /**
    * Viewsheet based on a worksheet.
    */
   public static final int VIEWSHEET_WORKSHEET = 21;
   /**
    * Viewsheet data assembly depends on data source.
    */
   public static final int VIEWSHEET_DATASOURCE = 22;
   /**
    * Viewsheet depends on another viewsheet.
    */
   public static final int VIEWSHEET_VIEWSHEET = 23;
   /**
    * Worksheet data assembly depends on data source.
    */
   public static final int WORKSHEET_DATASOURCE = 24;
   /**
    * Worksheet depends on another worksheet.
    */
   public static final int WORKSHEET_WORKSHEET = 25;
   /**
    * Worksheet data assembly depends on a query.
    */
   public static final int WORKSHEET_XQUERY = 26;
   /**
    * Snapshot depends on a viewsheet.
    */
   public static final int SNAPSHOT_VIEWSHEET = 27;
   /**
    * Report is from a meta template.
    */
   public static final int REPORT_METAREPORT = 28;
   /**
    * Target depends on a metric.
    */
   public static final int TARGET_METRIC = 30;
   /**
    * A pre-generated replet depends on a data cycle.
    */
   public static final int REPLET_DATACYCLE = 31;
   /**
    * A schedule task depends on another schedule task in chained condition.
    */
   public static final int SCHEDULETASK_SCHEDULETASK = 32;
   /**
    * A schedule task depends on a data cycle in chained condition.
    */
   public static final int SCHEDULETASK_DATACYCLE = 33;
   /**
    * A report depends on a script in the script lib.
    */
   public static final int REPORT_SCRIPT = 34;
   /**
    * A script depends on another script in the script lib.
    */
   public static final int SCRIPT_SCRIPT = 35;
   /**
    * Physical view depends on data source.
    */
   public static final int XPARTITION_XDATASOURCE = 36;
   /**
    * VPM depends on data source.
    */
   public static final int VPM_XDATASOURCE = 37;
   /**
    * Logical model depends on physical view.
    */
   public static final int XLOGICALMODEL_XPARTITION = 38;
   /**
    * A report depends on a logical model.
    */
   public static final int REPORT_XLOGICALMODEL = 39;
   /**
    * Viewsheet data assembly depends on logical model.
    */
   public static final int VIEWSHEET_XLOGICALMODEL = 40;
   /**
    * Worksheet data assembly depends on logical model.
    */
   public static final int WORKSHEET_XLOGICALMODEL = 41;
   /**
    * Query depends on a physical view.
    */
   public static final int XQUERY_XPARTITION = 42;
   /**
    * Physical view depends on physical view.
    */
   public static final int XPARTITION_XPARTITION = 43;
   /**
    * Logical model depends on logical model.
    */
   public static final int XLOGICALMODEL_XLOGICALMODEL = 44;
   /**
    * Data source depends on data source.
    */
   public static final int XDATASOURCE_XDATASOURCE = 45;
   /**
    * Data source depends on VPM.
    */
   public static final int XDATASOURCE_VPM = 37;
   /**
    * VPM depends on physical view.
    */
   public static final int VPM_XPARTITION = 46;
   /**
    * Replet depends on Replet.
    */
   public static final int REPLET_REPLET = 47;
   /**
    * Script in the script lib depends on Replet.
    */
   public static final int SCRIPT_REPLET = 48;
   /**
    * Script in the script lib depends on Replet.
    */
   public static final int SCRIPT_QUERY = 49;
   /**
    * Script in the script lib depends on Worksheet.
    */
   public static final int SCRIPT_WORKSHEET = 50;
   /**
    * Viewsheet based on a query.
    */
   public static final int VIEWSHEET_QUERY = 51;
   /**
    * A worksheet depends on a script in the script lib.
    */
   public static final int WORKSHEET_SCRIPT = 52;
   /**
    * A viewsheet depends on a script in the script lib.
    */
   public static final int VIEWSHEET_SCRIPT = 53;
   /**
    * A viewsheet depends on table style.
    */
   public static final int VIEWSHEET_TABLESTYLE = 54;

   /**
    * A viewsheet depends on a device class definition.
    */
   public static final int VIEWSHEET_DEVICE = 55;

   /**
    * A viewsheet asset link.
    */
   public static final int VIEWSHEET_LINK = 56;

   /**
    * A replet asset link.
    */
   public static final int REPLET_LINK = 57;

   /**
    * Schedule task depends on a viewsheet.
    */
   public static final int SCHEDULETASK_VIEWSHEET = 58;

   /**
    * Schedule task depends on a viewsheet.
    */
   public static final int SCHEDULETASK_WORKSHEET = 59;

   /**
    * Schedule task depends on an asset to back up.
    */
   public static final int SCHEDULETASK_BACKUP = 60;

   /**
    * Constructor.
    */
   public XAssetDependency(XAsset dependedXAsset, XAsset dependingXAsset,
                           int type)
   {
      this(dependedXAsset, dependingXAsset, type, null);
   }

   /**
    * Constructor.
    */
   public XAssetDependency(XAsset dependedXAsset, XAsset dependingXAsset,
      int type, String description)
   {
      this.dependedXAsset = dependedXAsset;
      this.dependingXAsset = dependingXAsset;
      this.type = type;
      this.description = description;
      this.lastModifiedTime = dependedXAsset == null ? "" :
         Tool.formatDateTime(dependedXAsset.getLastModifiedTime());
   }

   /**
    * Get depended asset.
    * @return depended asset.
    */
   public XAsset getDependedXAsset() {
      return dependedXAsset;
   }

   /**
    * Get dependeing asset.
    * @return dependeing asset.
    */
   public XAsset getDependingXAsset() {
      return dependingXAsset;
   }

   /**
    * Get dependency type.
    * @return dependency type.
    */
   public int getType() {
      return type;
   }

   /**
    * Get the last modified time.
    * @return the last modified time.
    */
   public String getLastModifiedTime() {
      return lastModifiedTime;
   }

   /**
    * Get the string representation.
    * @return the string representation.
    */
   public String toString() {
      // ORACLE treats empty string as NULL
      if(description != null && !description.isEmpty()) {
         return description;
      }

      return Catalog.getCatalog().getString("common.xasset.depends",
         SUtil.getTaskNameWithoutOrg(dependingXAsset.getPath()),
         SUtil.getTaskNameWithoutOrg(dependedXAsset.getPath()));
   }

   @Override
   public boolean equals(Object o) {
      if(this == o) {
         return true;
      }

      if(o == null || getClass() != o.getClass()) {
         return false;
      }

      XAssetDependency that = (XAssetDependency) o;
      return type == that.type &&
         Objects.equals(dependedXAsset, that.dependedXAsset) &&
         Objects.equals(dependingXAsset, that.dependingXAsset) &&
         Objects.equals(description, that.description);
   }

   @Override
   public int hashCode() {
      return Objects.hash(dependedXAsset, dependingXAsset, type, description);
   }

   private XAsset dependedXAsset;
   private XAsset dependingXAsset;
   private int type;
   private String description;
   private String lastModifiedTime;
}
