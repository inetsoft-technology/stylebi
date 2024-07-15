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
package inetsoft.web.portal.controller;

import inetsoft.util.Tool;
import inetsoft.web.portal.data.WorksheetBrowserInfo;
import inetsoft.web.portal.model.database.*;

import java.util.Comparator;
import java.util.List;

/**
 * SearchComparator.
 *
 * @since 13.4
 */
public class SearchComparator {
   private static class BaseComparator {
      public BaseComparator(String searchStr) {
         this.search = searchStr;
      }

      public int compare(String a, String b) {
         int ag = getMatchDegree(a);
         int bg = getMatchDegree(b);

         return bg - ag;
      }

      protected int getMatchDegree(String path) {
         String[] paths = path.split("/");
         int degree = 0;

         for(int i = 0; i < paths.length; i++) {
            String p = paths[i];
            degree = Math.max(degree, getDegree(p));
         }

         return degree;
      }

      /*
         The math degree for entry(search string is aa).
         4---the same(such as: aa)
         3---part has the same(such as: aa bb)
         2---start with(such as: aabb)
         1---has(such as: bbaa)
      */
      private int getDegree(String path) {
         String npath = path.toLowerCase();
         String searchStr = search.toLowerCase();

         if(Tool.equals(npath, searchStr)) {
            return 5;
         }

         if(npath.indexOf(" ") > 0) {
            String[] paths = npath.split(" ");

            for(int i = 0; i < paths.length; i++) {
               if(Tool.equals(paths[i].toLowerCase(), searchStr.toLowerCase())) {
                  return npath.startsWith(searchStr) && i == 0 ? 4 : 3;
               }
            }
         }

         int index = npath.indexOf(searchStr);

         if(index == 0) {
            return 2;
         }
         else if(index > 0) {
            return 1;
         }

         return 0;
      }

      private String search = null;
   }

   public static class StringComparator extends BaseComparator implements Comparator<String> {
      public StringComparator(String searchStr) {
         super(searchStr);
      }

      public int compare(String a, String b) {
         int ag = getMatchDegree(a);
         int bg = getMatchDegree(b);

         return bg - ag;
      }
   }

   public static class WorksheetBrowserComparator extends BaseComparator implements Comparator<WorksheetBrowserInfo> {
      public WorksheetBrowserComparator(String searchStr) {
         super(searchStr);
      }

      public int compare(WorksheetBrowserInfo a, WorksheetBrowserInfo b) {
         int ag = getMatchDegree(a.name());
         int bg = getMatchDegree(b.name());

         return bg - ag;
      }
   }

   public static class DataModelBrowserComparator extends BaseComparator implements Comparator<AssetItem> {
      public DataModelBrowserComparator(String searchStr) {
         super(searchStr);
      }

      public int compare(AssetItem a, AssetItem b) {
         int ag = getMatchItemDegree(a);
         int bg = getMatchItemDegree(b);

         return bg - ag;
      }

      /**
       * Get the max degree between item and children items.
       * @param a
       * @return
       */
      protected int getMatchItemDegree(AssetItem a) {
         int aggrete = getMatchDegree(a.getName());

         if(a instanceof LogicalModel) {
            LogicalModel logicalModel = (LogicalModel) a;
            List<LogicalModel> extendModels = logicalModel.getExtendModels();

            for(int i = 0; extendModels != null && i < extendModels.size(); i++) {
               if(extendModels.get(i) == null) {
                  continue;
               }

               aggrete = Math.max(aggrete, getMatchDegree(extendModels.get(i).getName()));
            }
         }

         if(a instanceof PhysicalModel) {
            PhysicalModel physicalModel = (PhysicalModel) a;
            List<PhysicalModel> extendViews = physicalModel.getExtendViews();

            for(int i = 0; extendViews != null && i < extendViews.size(); i++) {
               if(extendViews.get(i) == null) {
                  continue;
               }

               aggrete = Math.max(aggrete, getMatchDegree(extendViews.get(i).getName()));
            }
         }

         return aggrete;
      }
   }
}
