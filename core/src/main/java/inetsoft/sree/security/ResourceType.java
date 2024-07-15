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
package inetsoft.sree.security;

import inetsoft.report.LibManager;
import inetsoft.uql.*;
import inetsoft.uql.util.XUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

public enum ResourceType {
   ASSET(true) {
      @Override
      public Resource getParent(String path) {
         String folder = getParentPath(path, "/");

         if(folder != null) {
            return new Resource(ASSET, folder);
         }

         return null;
      }

      @Override
      public Resource getRoot() {
         return new Resource(ASSET, "/");
      }
   },
   CHART_TYPE(true) {
      @Override
      public Resource getParent(String path) {
         String parentPath = getParentPath(path, "/");

         if(parentPath != null && !parentPath.isEmpty()) {
            return new Resource(CHART_TYPE_FOLDER, parentPath);
         }

         if(parentPath == null && path != null) {
            return new Resource(CHART_TYPE_FOLDER, path);
         }

         return null;
      }
   },
   CHART_TYPE_FOLDER(false),
   COMPOSER(false),
   CROSS_JOIN(false),
   CUBE(true) {
      @Override
      public Resource getParent(String path) {
         String datasource = getParentPath(path, "::");

         if(datasource != null) {
            return new Resource(DATA_SOURCE, datasource);
         }

         return null;
      }
   },
   DASHBOARD(true) {
      @Override
      public Resource getParent(String path) {
         return null;
      }

      @Override
      public Resource getRoot() {
         return new Resource(DASHBOARD, "/");
      }
   },
   DATA_SOURCE(true) {
      @Override
      public Resource getParent(String path) {
         String dataSourceParent = getParentPath(path, "^");
         dataSourceParent = dataSourceParent == null ? getParentPath(path, "::")
            : dataSourceParent;

         if(dataSourceParent != null) {
            return new Resource(DATA_SOURCE, dataSourceParent);
         }

         String folder = getParentPath(path, "/");

         if(folder != null) {
            return new Resource(DATA_SOURCE_FOLDER, folder);
         }

         return getRoot();
      }

      @Override
      protected String getParentPath(String path, String delimiter) {
         if(path == null || path.isEmpty()) {
            return null;
         }

         int index = path.lastIndexOf(delimiter);

         if(index >= 0) {
            return path.substring(0, index);
         }

         return null;
      }

      @Override
      public Resource getRoot() {
         return new Resource(DATA_SOURCE_FOLDER, "/");
      }
   },
   DATA_SOURCE_FOLDER(true) {
      @Override
      public Resource getParent(String path) {
         String folder = getParentPath(path, "/");

         if(folder != null && !folder.isEmpty()) {
            return new Resource(DATA_SOURCE_FOLDER, folder);
         }

         return null;
      }

      @Override
      public Resource getRoot() {
         return new Resource(DATA_SOURCE_FOLDER, "/");
      }
   },
   DATA_SOURCE_LISTING(false),
   DEVICE(false),
   EM(false),
   EM_COMPONENT(true) {
      @Override
      public Resource getParent(String path) {
         String folder = getParentPath(path, "/");

         if(folder == null || isRoot(folder)) {
            return new Resource(EM, "*");
         }
         else {
            return new Resource(EM_COMPONENT, folder);
         }
      }

      @Override
      public Resource getRoot() {
         return new Resource(EM, "*");
      }

      @Override
      public boolean isRoot(String path) {
         return Objects.equals(getRoot().getPath(), path);
      }
   },
   FREE_FORM_SQL(false),
   LIBRARY(false),
   LOGIN_AS(false),
   MATERIALIZATION(false),
   CREATE_DATA_SOURCE(false),
   MY_DASHBOARDS(false),
   PHYSICAL_TABLE(false),
   PORTAL_REPOSITORY_TREE_DRAG_AND_DROP(false),
   PORTAL_TAB(false),
   PROFILE(false),
   PROTOTYPE(true) {
      @Override
      public Resource getRoot() {
         return new Resource(PROTOTYPE, "*");
      }
   },
   QUERY(true) {
      @Override
      public Resource getParent(String path) {
         try {
            int idx = path.lastIndexOf("::");

            if(idx > 0) {
               String datasource = path.substring(idx + 2);
               path = path.substring(0, idx);
               idx = datasource.indexOf(XUtil.DATAMODEL_FOLDER_SPLITER);
               String modelFolder = null;

               if(idx > 0) {
                  modelFolder = datasource.substring(idx + XUtil.DATAMODEL_FOLDER_SPLITER.length());
                  datasource = datasource.substring(0, idx);
               }

               if(modelFolder != null) {
                  return new Resource(DATA_MODEL_FOLDER, datasource + "/" + modelFolder);
               }

               idx = path.lastIndexOf('^');

               if(idx > 0) {
                  datasource += "^" + path.substring(0, idx);
               }

               return new Resource(DATA_SOURCE, datasource);
            }
         }
         catch(Exception ex) {
            LOG.error("Failed to get parent resource of query: {}", path, ex);
         }

         return null;
      }
   },
   QUERY_FOLDER(true) {
      @Override
      public Resource getParent(String path) {
         int idx = path.lastIndexOf("::");

         if(idx > 0) {
            String datasource = path.substring(idx + 2);
            return new Resource(DATA_SOURCE, datasource);
         }

         return null;
      }
   },
   DATA_MODEL_FOLDER(true) {
      @Override
      public Resource getParent(String path) {
         String folder = getParentPath(path, "/");

         if(folder != null) {
            return new Resource(DATA_SOURCE, folder);
         }

         return null;
      }
   },
   REPORT(true) {
      @Override
      public Resource getParent(String path) {
         String folder = getParentPath(path, "/");

         if(folder != null) {
            return new Resource(REPORT, folder);
         }

         return null;
      }

      @Override
      public Resource getRoot() {
         return new Resource(REPORT, "/");
      }
   },
   REPORT_EXPORT(false),
   SCHEDULER(false),
   SCHEDULE_CYCLE(false),
   SCHEDULE_OPTION(false),
   SCHEDULE_TASK(false),
   SCHEDULE_TASK_FOLDER(true) {
      @Override
      public Resource getParent(String path) {
         String folder = getParentPath(path, "/");

         if(folder != null && !folder.isEmpty()) {
            return new Resource(SCHEDULE_TASK_FOLDER, folder);
         }

         return null;
      }

      @Override
      public Resource getRoot() {
         return new Resource(SCHEDULE_TASK_FOLDER, "/");
      }
   },
   SCHEDULE_TIME_RANGE(false),
   SCRIPT(true) {
      @Override
      public Resource getParent(String path) {
         return new Resource(SCRIPT_LIBRARY, "*");
      }

      @Override
      public Resource getRoot() {
         return new Resource(LIBRARY, "*");
      }
   },
   SCRIPT_LIBRARY(true) {
      @Override
      public Resource getParent(String path) {
         return new Resource(LIBRARY, "*");
      }

      @Override
      public Resource getRoot() {
         return new Resource(LIBRARY, "*");
      }
   },
   SECURITY_GROUP(false),
   SECURITY_ROLE(false),
   SECURITY_USER(false),
   SECURITY_ORGANIZATION(false),
   SHARE(false),
   TABLE_STYLE(true) {
      @Override
      public Resource getParent(String path) {
         String folder = getParentPath(path, LibManager.SEPARATOR);

         if(folder == null || folder.equals("*")) {
            return new Resource(TABLE_STYLE_LIBRARY, "*");
         }
         else {
            return new Resource(TABLE_STYLE, folder);
         }
      }

      @Override
      public Resource getRoot() {
         return new Resource(LIBRARY, "*");
      }
   },
   TABLE_STYLE_LIBRARY(true) {
      @Override
      public Resource getParent(String path) {
         return new Resource(LIBRARY, "*");
      }

      @Override
      public Resource getRoot() {
         return new Resource(LIBRARY, "*");
      }
   },
   UPLOAD_DRIVERS(false),
   VIEWSHEET(false),
   VIEWSHEET_ACTION(false),
   VIEWSHEET_TOOLBAR_ACTION(false),
   WORKSHEET(false),
   CREATE_SCRIPT(false),
   CREATE_TABLE_STYLE(false);

   private final boolean hierarchical;

   ResourceType(boolean hierarchical) {
      this.hierarchical = hierarchical;
   }

   public boolean isHierarchical() {
      return hierarchical;
   }

   public Resource getParent(String path) {
      return null;
   }

   public Resource getRoot() {
      return null;
   }

   public boolean isRoot(String path) {
      return Objects.equals(getRoot(), new Resource(this, path));
   }

   protected String getParentPath(String path, String delimiter) {
      final Resource root = getRoot();

      if(path == null || path.isEmpty() || (root != null && Objects.equals(root.getPath(), path))) {
         return null;
      }

      int index = path.lastIndexOf(delimiter);

      if(index >= 0) {
         return path.substring(0, index);
      }

      return root != null ? root.getPath() : null;
   }

   private static final Logger LOG = LoggerFactory.getLogger(ResourceType.class);
}
