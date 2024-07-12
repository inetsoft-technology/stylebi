/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.binding.handler;

import inetsoft.report.TableDataPath;
import inetsoft.uql.XConstants;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.*;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ClearTableHeaderAliasHandler {
   public static void clearHeaderAlias(DataRef dataRef, FormatInfo formatInfo, int colIndex) {
      if(dataRef == null || formatInfo == null || formatInfo.getFormatMap() == null) {
         return;
      }

      TableDataPath dataPath;

      for(Map.Entry<TableDataPath, VSCompositeFormat> entry : formatInfo.getFormatMap().entrySet())
      {
         dataPath = entry.getKey();
         String[] path = dataPath.getPath();

         if(path == null || path.length == 0) {
            continue;
         }

         if(matchAgg(dataRef, path) || matchDim(dataRef, path, colIndex)) {
            // match
            VSCompositeFormat format = entry.getValue();
            clearAlias(format);
         }
      }
   }

   public static void clearAlias(DataRef dataRef, FormatInfo formatInfo, int colIndex) {
      if(dataRef == null || formatInfo == null || formatInfo.getFormatMap() == null) {
         return;
      }

      TableDataPath dataPath;

      for(Map.Entry<TableDataPath, VSCompositeFormat> entry : formatInfo.getFormatMap().entrySet())
      {
         dataPath = entry.getKey();
         String[] path = dataPath.getPath();

         if(path == null || path.length == 0) {
            continue;
         }

         if(matchAgg(dataRef, path) || matchDim(dataRef, path, colIndex)) {
            // match
            VSCompositeFormat format = entry.getValue();

            if(format == null) {
               return;
            }

            VSCompositeFormat nformat = format.clone();
            formatInfo.getFormatMap().put(dataPath, nformat);
            clearAlias(nformat);
         }
      }
   }

   public static void processClearAliasFormat(Map<TableDataPath, VSCompositeFormat> formatMap,
                                              TableDataPath oldDataPath)
   {
      if(formatMap == null || oldDataPath == null) {
         return;
      }

      if(oldDataPath.getType() == TableDataPath.HEADER && oldDataPath.getPath() != null &&
         oldDataPath.getPath().length > 0 && "_CROSSTAB_".equals(oldDataPath.getPath()[0]))
      {
         return;
      }

      VSCompositeFormat format = formatMap.get(oldDataPath);

      if(format == null) {
         return;
      }

      format = format.clone();
      ClearTableHeaderAliasHandler.clearAlias(format);
      formatMap.put(oldDataPath, format);
   }

   public static void clearHeaderAliasByIndex(Map<TableDataPath, VSCompositeFormat> formatMap,
                                              int index)
   {
      if(formatMap == null || index < 0) {
         return;
      }

      String path = "Cell [0," + index + "]";
      TableDataPath oldDataPath = new TableDataPath(-1, TableDataPath.HEADER, XSchema.STRING,
                                              new String[] {path});

      VSCompositeFormat format = formatMap.get(oldDataPath);

      if(format == null) {
         return;
      }

      format = format.clone();
      ClearTableHeaderAliasHandler.clearAlias(format);
      formatMap.put(oldDataPath, format);
   }

   private static void clearAlias(VSCompositeFormat format) {
      if(format == null || format.getUserDefinedFormat() == null) {
         return;
      }

      VSFormat ufmt = format.getUserDefinedFormat();

      if(ufmt == null || !XConstants.MESSAGE_FORMAT.equals(ufmt.getFormatValue())) {
         return;
      }

      ufmt.setFormatValue(null);
      ufmt.setFormatExtentValue(null);
   }

   private static boolean matchAgg(DataRef dataRef, String[] path) {
      return dataRef instanceof VSAggregateRef
         && ((VSAggregateRef) dataRef).getFullName().equals(path[path.length - 1]);
   }

   private static boolean matchDim(DataRef dataRef, String[] path, int colIndex) {
      Matcher matcher = HEADER_ROW_PATH_PATTERN.matcher(path[path.length - 1]);

      if(!(dataRef instanceof VSDimensionRef) || !matcher.matches()) {
         return false;
      }

      int index = -1;

      try {
         index = Integer.parseInt(matcher.group(2));
      }
      catch(Exception ignore) {
      }

      return index == colIndex;
   }

   private static final Pattern HEADER_ROW_PATH_PATTERN = Pattern.compile("^Cell \\[(\\d+),(\\d+)]$");
}
