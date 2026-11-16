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
package inetsoft.web.binding.handler;

import inetsoft.report.TableDataPath;
import inetsoft.uql.XConstants;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.VSUtil;

import java.util.*;
import java.util.function.BiConsumer;
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

   /**
    * Clears the alias written at exactly {@code dataPaths} -- for a caller that already resolved
    * which header cell it means (an aggregate that shares its name with another measure), where
    * {@link #clearAlias(DataRef, FormatInfo, int)} would clear every measure of that name.
    */
   public static void clearAliasAt(Collection<TableDataPath> dataPaths, FormatInfo formatInfo) {
      if(dataPaths == null || formatInfo == null || formatInfo.getFormatMap() == null) {
         return;
      }

      for(TableDataPath dataPath : dataPaths) {
         VSCompositeFormat format = formatInfo.getFormatMap().get(dataPath);

         if(format != null) {
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

   /**
    * {@link #processClearAliasFormat}, except that a vacated summary header path of a measure
    * still bound on {@code crosstab} keeps its label (bug #79331). Adding a deeper row level
    * re-keys the old detail path {@code [Year, Quarter, Sum(X)]} to the new detail, and that
    * same path is now the new quarter subtotal; clearing it left those rows with the default
    * name. Corner and dimension labels are cleared as before (bug #78530), and so is the path of
    * a measure the write removed. Evaluated against the crosstab's info when the sync runs, which
    * is the new binding.
    */
   public static BiConsumer<Map<TableDataPath, VSCompositeFormat>, TableDataPath>
      clearVacatedKeepingBoundMeasures(CrosstabVSAssembly crosstab)
   {
      return (formatMap, oldDataPath) -> {
         if(!isBoundMeasureHeader(crosstab.getVSCrosstabInfo(), oldDataPath)) {
            processClearAliasFormat(formatMap, oldDataPath);
         }
      };
   }

   private static boolean isBoundMeasureHeader(VSCrosstabInfo cinfo, TableDataPath dataPath) {
      String[] path = dataPath == null ? null : dataPath.getPath();

      if(cinfo == null || path == null || path.length == 0 ||
         dataPath.getType() != TableDataPath.HEADER || path[0].contains("Cell ["))
      {
         return false;
      }

      String last = path[path.length - 1];
      DataRef[] aggs = cinfo.getRuntimeAggregates();

      if(aggs == null || Arrays.stream(VSUtil.buildName(aggs, true))
         .noneMatch(name -> isAggregateSegment(name, last)))
      {
         return false;
      }

      List<DataRef> dims = new ArrayList<>();

      if(cinfo.getRuntimeRowHeaders() != null) {
         dims.addAll(Arrays.asList(cinfo.getRuntimeRowHeaders()));
      }

      if(cinfo.getRuntimeColHeaders() != null) {
         dims.addAll(Arrays.asList(cinfo.getRuntimeColHeaders()));
      }

      // syncSummaryHeader treats a last segment naming a dimension as a dimension header.
      return !Arrays.asList(VSUtil.buildName(dims.toArray(new DataRef[0]), false)).contains(last);
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

   // Package-visible (not private): SetTableHeaderAliasHandler.findHeaderPath reuses this exact
   // matching logic for the reverse direction (name -> TableDataPath rather than
   // TableDataPath -> name), rather than re-deriving the same regex/full-name match a second time.
   static boolean matchAgg(DataRef dataRef, String[] path) {
      return dataRef instanceof VSAggregateRef
         && isAggregateSegment(((VSAggregateRef) dataRef).getFullName(), path[path.length - 1]);
   }

   /**
    * Whether {@code segment}, the last segment of a header cell's path, is a rendering of the
    * aggregate named {@code fullName}: the name itself, or the name with the {@code ".N"} suffix
    * the crosstab lens appends to the Nth measure that shares a column and formula with an
    * earlier one ({@code Util.getDupHeader}). Which duplicate a suffixed segment belongs to is
    * not decided here -- see {@link SetTableHeaderAliasHandler#occurrence}.
    */
   public static boolean isAggregateSegment(String fullName, String segment) {
      if(fullName == null || segment == null || !segment.startsWith(fullName)) {
         return false;
      }

      return segment.length() == fullName.length() ||
         DUPLICATE_SUFFIX_PATTERN.matcher(segment.substring(fullName.length())).matches();
   }

   static boolean matchDim(DataRef dataRef, String[] path, int colIndex) {
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
   private static final Pattern DUPLICATE_SUFFIX_PATTERN = Pattern.compile("\\.\\d+");
}
