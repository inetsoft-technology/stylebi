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

import inetsoft.report.TableDataPath;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.AggregateFormula;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.CrosstabVSAssembly;
import inetsoft.uql.viewsheet.FormatInfo;
import inetsoft.uql.viewsheet.TableDataVSAssembly;
import inetsoft.uql.viewsheet.TableVSAssembly;
import inetsoft.uql.viewsheet.VSCompositeFormat;
import inetsoft.uql.viewsheet.VSCrosstabInfo;
import inetsoft.uql.viewsheet.VSDataRef;
import inetsoft.uql.viewsheet.VSDimensionRef;
import inetsoft.uql.viewsheet.VSFormat;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;
import inetsoft.web.binding.drm.ColumnRefModel;
import inetsoft.web.binding.drm.DataRefModel;
import inetsoft.web.binding.model.BAggregateRefModel;
import inetsoft.web.binding.model.BDimensionRefModel;
import inetsoft.web.binding.model.table.BaseTableBindingModel;
import inetsoft.web.binding.model.table.CrosstabBindingModel;
import inetsoft.web.binding.model.table.TableBindingModel;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.UnaryOperator;

/**
 * Detection of numeric Table/Crosstab output that a whole-assembly date/time format would render
 * as a date (e.g. 1970-01-01), shared by the {@code set_format} refusal (bug #77597, a date
 * format over numeric cells) and the binding-write refusal (bug #79342, a numeric output added
 * under an existing date format). Nothing here throws or writes.
 */
public final class WizDateFormatOverNumeric {
   private WizDateFormatOverNumeric() {
   }

   /**
    * What a Table/Crosstab holds, by output type.
    *
    * @param numeric     numeric outputs (display names), the cells a date format would break
    * @param numericDims the numeric dimensions among {@code numeric} (Crosstab headers)
    * @param dateColumns date-valued Table columns
    */
   public record Scan(List<String> numeric, List<String> numericDims, List<String> dateColumns) {}

   /**
    * The user-defined date/time format type of the assembly's whole-object path, or null when
    * there is none (no object format, not user-defined, or not a date/time type).
    */
   public static String objectDateFormat(VSAssemblyInfo info) {
      FormatInfo formatInfo = info == null ? null : info.getFormatInfo();
      VSCompositeFormat object =
         formatInfo == null ? null : formatInfo.getFormat(VSAssemblyInfo.OBJECTPATH);
      VSFormat user = object == null ? null : object.getUserDefinedFormat();

      if(user == null || !user.isFormatValueDefined()) {
         return null;
      }

      String format = user.getFormat();

      return XConstants.DATE_FORMAT.equals(format) || XConstants.TIME_FORMAT.equals(format) ||
         XConstants.TIMEINSTANT_FORMAT.equals(format) ? format : null;
   }

   /**
    * Scans a Table or Crosstab as it is now.
    *
    * @param target {@code object}, {@code data} or {@code header}: which region's cells are
    *               asked about (see {@code set_format}).
    * @return the scan, or null for any other assembly or one with nothing to scan.
    */
   public static Scan scan(TableDataVSAssembly assembly, String target) {
      boolean object = "object".equals(target);
      List<String> numeric = new ArrayList<>();
      List<String> numericDims = new ArrayList<>();
      List<String> dateColumns = new ArrayList<>();

      if(assembly instanceof CrosstabVSAssembly crosstab) {
         VSCrosstabInfo cinfo = crosstab.getVSCrosstabInfo();

         if(cinfo == null) {
            return null;
         }

         if(!"data".equals(target)) {
            addNumeric(numericDims, runtimeOrDesign(cinfo.getRuntimeRowHeaders(),
                                                    cinfo.getRowHeaders()));
            addNumeric(numericDims, runtimeOrDesign(cinfo.getRuntimeColHeaders(),
                                                    cinfo.getColHeaders()));
         }

         numeric.addAll(numericDims);

         if(!"header".equals(target)) {
            addNumeric(numeric, runtimeOrDesign(cinfo.getRuntimeAggregates(),
                                                cinfo.getAggregates()));
         }
      }
      else if(assembly instanceof TableVSAssembly table) {
         if("header".equals(target)) {
            return null;
         }

         ColumnSelection columns = table.getColumnSelection();

         if(columns == null) {
            return null;
         }

         Set<String> ownFormat = object ? ownDetailFormatHeaders(table) : Set.of();

         for(int i = 0; i < columns.getAttributeCount(); i++) {
            DataRef ref = columns.getAttribute(i);

            if(ref instanceof ColumnRef column && !column.isVisible()) {
               continue;
            }

            if(WizFormatChecks.isNumeric(ref)) {
               if(!ownFormat.isEmpty() &&
                  candidateHeaders(ref).stream().anyMatch(ownFormat::contains))
               {
                  continue;
               }

               numeric.add(displayName(ref));
            }
            else if(WizFormatChecks.isDateLike(ref)) {
               dateColumns.add(displayName(ref));
            }
         }
      }
      else {
         return null;
      }

      return new Scan(numeric, numericDims, dateColumns);
   }

   /**
    * The numeric outputs a binding model WOULD produce once applied, as display names, so a
    * write can be judged before anything is committed (the assembly itself only reflects the new
    * binding after it is applied). Typed as {@link #scan} types the applied binding: a hidden
    * Table column is skipped, a date dimension takes its date level's type, an aggregate takes
    * its formula's result type (a date column under a formula other than a count is unknown), and
    * an unknown type is never counted. A model entry has no type of its own until it is applied,
    * so a source column's type comes from {@code sourceType}.
    *
    * @param ownFormat  the Table's {@code DETAIL} headers that already carry their own format,
    *                   which do not inherit the object format ({@link #ownDetailFormatHeaders});
    *                   ignored for a Crosstab, whose measures are checked by type regardless.
    * @param sourceType the data type of a source column by name, or null when unknown.
    */
   public static Set<String> plannedNumericOutputs(BaseTableBindingModel model,
                                                   Set<String> ownFormat,
                                                   UnaryOperator<String> sourceType)
   {
      Set<String> out = new LinkedHashSet<>();

      if(model instanceof CrosstabBindingModel crosstab) {
         for(BDimensionRefModel dim : nullToEmpty(crosstab.getRows())) {
            addPlannedDimension(out, dim, sourceType);
         }

         for(BDimensionRefModel dim : nullToEmpty(crosstab.getCols())) {
            addPlannedDimension(out, dim, sourceType);
         }

         for(BAggregateRefModel agg : nullToEmpty(crosstab.getAggregates())) {
            addPlannedAggregate(out, agg, sourceType);
         }
      }
      else if(model instanceof TableBindingModel table) {
         for(DataRefModel detail : nullToEmpty(table.getDetails())) {
            addPlannedDetail(out, detail, ownFormat, sourceType);
         }
      }

      return out;
   }

   private static <T> List<T> nullToEmpty(List<T> list) {
      return list == null ? List.of() : list;
   }

   private static void addPlannedDetail(Set<String> out, DataRefModel detail,
                                        Set<String> ownFormat, UnaryOperator<String> sourceType)
   {
      if(detail instanceof ColumnRefModel column && !column.isVisible()) {
         return;
      }

      String name = detail.getAttribute() != null ? detail.getAttribute() : detail.getName();
      String alias = detail instanceof ColumnRefModel column ? column.getAlias() : null;
      String type = detail.getDataType() != null ? detail.getDataType() : sourceType.apply(name);

      if(name == null || !XSchema.isNumericType(type)) {
         return;
      }

      Set<String> headers = new LinkedHashSet<>();
      headers.add(alias);
      headers.add(name);
      headers.add(detail.getName());
      headers.remove(null);

      if(headers.stream().noneMatch(ownFormat::contains)) {
         out.add(alias != null && !alias.isEmpty() ? alias : name);
      }
   }

   private static void addPlannedDimension(Set<String> out, BDimensionRefModel dim,
                                           UnaryOperator<String> sourceType)
   {
      String column = dim.getColumnValue() != null ? dim.getColumnValue() : dim.getName();

      if(column == null) {
         return;
      }

      VSDimensionRef ref = (VSDimensionRef) dim.createDataRef();
      ref.setDataType(dim.getDataType() != null ? dim.getDataType() : sourceType.apply(column));

      if(WizFormatChecks.isNumeric(ref)) {
         out.add(column);
      }
   }

   private static void addPlannedAggregate(Set<String> out, BAggregateRefModel agg,
                                           UnaryOperator<String> sourceType)
   {
      String column = agg.getColumnValue() != null ? agg.getColumnValue() : agg.getName();
      AggregateFormula formula = AggregateFormula.getFormula(agg.getFormula());

      if(column == null || formula == null) {
         return;
      }

      String srcType = agg.getDataType() != null ? agg.getDataType() : sourceType.apply(column);
      String type = formula.getDataType();

      if(type == null) {
         type = srcType;
      }
      else if(XSchema.isDateType(srcType) && formula != AggregateFormula.COUNT_ALL &&
              formula != AggregateFormula.COUNT_DISTINCT)
      {
         return;
      }

      if(XSchema.isNumericType(type)) {
         out.add(formula.getFormulaName() + "(" + column + ")");
      }
   }

   /** The names a Table column's {@code DETAIL} path may be stored under (its rendered header). */
   private static Set<String> candidateHeaders(DataRef ref) {
      Set<String> names = new LinkedHashSet<>();

      if(ref instanceof ColumnRef column && column.getAlias() != null) {
         names.add(column.getAlias());
      }

      names.add(ref.getAttribute());
      names.add(ref.getName());
      names.remove(null);
      return names;
   }

   /**
    * The headers of a Table's {@code DETAIL} paths that already hold a user format value, read
    * with the non-mutating {@link FormatInfo#getFormat(TableDataPath)}. Such a column keeps its
    * own format and does not inherit the object format (VSFormatTableLens).
    */
   public static Set<String> ownDetailFormatHeaders(TableVSAssembly table) {
      VSAssemblyInfo info = table.getVSAssemblyInfo();
      FormatInfo formatInfo = info == null ? null : info.getFormatInfo();
      Set<String> headers = new LinkedHashSet<>();

      if(formatInfo == null) {
         return headers;
      }

      for(TableDataPath path : formatInfo.getPaths()) {
         if(path == null || path.getType() != TableDataPath.DETAIL || path.getPath() == null ||
            path.getPath().length == 0)
         {
            continue;
         }

         VSCompositeFormat format = formatInfo.getFormat(path);

         if(format != null && format.getUserDefinedFormat() != null &&
            format.getUserDefinedFormat().isFormatValueDefined())
         {
            headers.add(path.getPath()[0]);
         }
      }

      return headers;
   }

   private static DataRef[] runtimeOrDesign(DataRef[] runtime, DataRef[] design) {
      return runtime != null && runtime.length > 0 ? runtime : design;
   }

   private static void addNumeric(List<String> out, DataRef[] refs) {
      if(refs == null) {
         return;
      }

      for(DataRef ref : refs) {
         if(WizFormatChecks.isNumeric(ref)) {
            out.add(displayName(ref));
         }
      }
   }

   private static String displayName(DataRef ref) {
      if(ref instanceof VSDataRef vsRef && vsRef.getFullName() != null) {
         return vsRef.getFullName();
      }

      if(ref instanceof ColumnRef column && column.getAlias() != null &&
         !column.getAlias().isEmpty())
      {
         return column.getAlias();
      }

      return ref.getAttribute() != null ? ref.getAttribute() : ref.getName();
   }

   /**
    * Whether a Table column is numeric and has no {@code DETAIL} format of its own, i.e. would
    * inherit a whole-object date format.
    */
   public static boolean isNumericWithoutOwnFormat(TableVSAssembly table, DataRef ref) {
      return WizFormatChecks.isNumeric(ref) &&
         candidateHeaders(ref).stream().noneMatch(ownDetailFormatHeaders(table)::contains);
   }

   /** The refusal text of a binding write that adds {@code added} under {@code dateFormat}. */
   public static String refusalMessage(String assemblyName, String dateFormat,
                                       List<String> added)
   {
      return "'" + assemblyName + "' has a whole-assembly " + dateFormat + " (set_format " +
         "target:\"object\"), and this write would add numeric output that renders as a " +
         "1970-01-01 date under it: " + summarize(added) + ". The request was not applied. A " +
         "column or aggregate that does not exist yet cannot be given its own format first, so " +
         "change the whole-assembly format before this write: set_format target:\"object\" " +
         "with reset:true (clears the whole-assembly formatting), or with a non-date format. " +
         "Then repeat this write, and format the date fields alone with set_format " +
         "target:\"data\" and 'field' naming the date column (Table) or date-valued measure " +
         "(Crosstab), and target:\"header\" for Crosstab date dimension labels.";
   }

   /** Up to five distinct names, then a count of the rest. */
   public static String summarize(List<String> names) {
      List<String> distinct = new ArrayList<>(new LinkedHashSet<>(names));

      if(distinct.size() <= 5) {
         return String.join(", ", distinct);
      }

      return String.join(", ", distinct.subList(0, 5)) + " and " + (distinct.size() - 5) +
         " more";
   }
}
