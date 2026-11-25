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
package inetsoft.web.wiz.binding;

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.XDataSource;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.asset.BoundTableAssembly;
import inetsoft.uql.asset.SourceInfo;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.erm.XDataModel;
import inetsoft.uql.erm.XLogicalModel;
import inetsoft.uql.jdbc.SQLHelper;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.web.binding.model.table.BaseTableBindingModel;
import inetsoft.web.wiz.binding.model.FieldRef;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Composer's Field Option "Include all rows" of a table or crosstab field.
 *
 * <p>The option belongs to the field's <em>table</em> (a logical model entity), not to the field:
 * the Composer stores the entity name in {@link BaseTableBindingModel#getAllRows()} and every
 * field of that entity shows the checkbox ticked. The wire shape ({@link FieldRef#allRows()}) is
 * per field because that is where a caller looks for it; this class maps between the two, and
 * refuses what the Composer would not offer or what would be ambiguous, instead of storing a
 * value that does nothing.
 */
final class TableAllRows {
   private TableAllRows() {
   }

   /**
    * The entity (logical model table) a field belongs to, from its {@code Entity:Attribute} name.
    *
    * @return the entity, or {@code null} for a column that names none.
    */
   static String entityOf(String column) {
      int colon = column == null ? -1 : column.indexOf(':');
      return colon > 0 ? column.substring(0, colon) : null;
   }

   /**
    * Reports the option on the fields of the tables that include all rows.
    *
    * @return the same fields, each marked {@code allRows: true} when its table includes all rows;
    *         a field of any other table is returned untouched.
    */
   static List<FieldRef> report(BaseTableBindingModel model, List<FieldRef> fields) {
      List<String> included = model == null ? null : model.getAllRows();

      if(included == null || included.isEmpty() || fields == null) {
         return fields;
      }

      List<FieldRef> out = new ArrayList<>(fields.size());

      for(FieldRef field : fields) {
         String entity = entityOf(field.column());
         out.add(entity != null && included.contains(entity) ? field.withAllRows(true) : field);
      }

      return out;
   }

   /**
    * Applies the option the incoming fields carry to the binding model.
    *
    * <p>Does nothing when no field carries it. Otherwise refuses, naming the field, when the
    * source is not a logical model, when the data source cannot do a full outer join (the
    * Composer hides the option then), when the field has no logical model table (a calculated or
    * formula field, which the Composer also excludes), or when two fields of one table disagree.
    *
    * @param model  the binding model to update.
    * @param fields the fields of the write, the option on them {@code null} when not specified.
    * @param rvs    the runtime viewsheet the binding belongs to.
    * @param source the assembly's source.
    */
   static void apply(BaseTableBindingModel model, List<FieldRef> fields, RuntimeViewsheet rvs,
                     SourceInfo source)
   {
      Map<String, Boolean> wanted = new LinkedHashMap<>();
      Map<String, Boolean> stated = new LinkedHashMap<>();
      List<String> included = model.getAllRows() == null ? List.of() : model.getAllRows();

      for(FieldRef field : fields) {
         if(field.allRows() == null) {
            continue;
         }

         String stateEntity = entityOf(field.column());
         Boolean earlier = stated.put(stateEntity == null ? field.column() : stateEntity,
                                      field.allRows());

         if(earlier != null && !earlier.equals(field.allRows())) {
            throw new IllegalArgumentException(
               "Include all rows applies to a whole table, but the fields of '" +
               (stateEntity == null ? field.column() : stateEntity) + "' in this write " +
               "disagree on 'allRows'. Send the same value for every field of that table, or " +
               "send it on one of them only.");
         }
      }

      for(FieldRef field : fields) {
         if(field.allRows() == null) {
            continue;
         }

         String stateEntity = entityOf(field.column());

         // what a read reports back (and an unchanged echo of it) is already the table's setting
         if(field.allRows() == (stateEntity != null && included.contains(stateEntity))) {
            continue;
         }

         if(rvs == null || source == null) {
            throw new IllegalArgumentException(
               "Field '" + field.column() + "' carries 'allRows' (Include all rows), which needs " +
               "the table's source to check; it cannot be applied here.");
         }

         XLogicalModel lmodel = requireSupported(field, rvs, source);
         String entity = entityOf(field.column());

         if(entity == null || lmodel.getEntity(entity) == null) {
            throw new IllegalArgumentException(
               "Field '" + field.column() + "' carries 'allRows' (Include all rows), but it " +
               "belongs to no table of the logical model '" + lmodel.getName() + "' -- a " +
               "calculated or formula field has no table whose rows could all be included. " +
               "Set allRows on a field of one of the model's tables.");
         }

         wanted.put(entity, field.allRows());
      }

      if(wanted.isEmpty()) {
         return;
      }

      List<String> current = model.getAllRows();
      List<String> next = current == null ? new ArrayList<>() : new ArrayList<>(current);

      for(Map.Entry<String, Boolean> e : wanted.entrySet()) {
         if(e.getValue()) {
            if(!next.contains(e.getKey())) {
               next.add(e.getKey());
            }
         }
         else {
            next.remove(e.getKey());
         }
      }

      model.setAllRows(next.isEmpty() ? null : next);
   }

   /**
    * The logical model of the source when its data source can do a full outer join, which is when
    * the Composer offers the option.
    */
   private static XLogicalModel requireSupported(FieldRef field, RuntimeViewsheet rvs,
                                                 SourceInfo source)
   {
      BoundTableAssembly bound = boundTable(rvs, source);

      if(bound == null || bound.getSourceInfo().getType() != SourceInfo.MODEL) {
         throw new IllegalArgumentException(
            "Field '" + field.column() + "' carries 'allRows' (Include all rows), which applies " +
            "only to a table or crosstab bound to a logical model; this one is bound to '" +
            source.getSource() + "', which is not one.");
      }

      try {
         XRepository repository = XRepository.getRepository();
         SourceInfo model = bound.getSourceInfo();
         XDataModel dataModel = repository.getDataModel(model.getPrefix());
         XLogicalModel lmodel = dataModel == null ? null :
            dataModel.getLogicalModel(model.getSource(), rvs.getUser());

         if(lmodel == null) {
            throw new IllegalArgumentException(
               "Field '" + field.column() + "' carries 'allRows' (Include all rows), but the " +
               "logical model '" + model.getSource() + "' cannot be found.");
         }

         XDataSource ds = repository.getDataSource(dataModel.getDataSource());

         if(ds != null && !SQLHelper.getSQLHelper(ds).supportsOperation(SQLHelper.FULL_OUTERJOIN)) {
            throw new IllegalArgumentException(
               "Field '" + field.column() + "' carries 'allRows' (Include all rows), but the " +
               "data source '" + ds.getName() + "' of this logical model does not support full " +
               "outer joins, so the Composer does not offer the option for it.");
         }

         return lmodel;
      }
      catch(RuntimeException e) {
         throw e;
      }
      catch(Exception e) {
         throw new IllegalStateException("Cannot check Include all rows: " + e.getMessage(), e);
      }
   }

   private static BoundTableAssembly boundTable(RuntimeViewsheet rvs, SourceInfo source) {
      Viewsheet vs = rvs.getViewsheet();
      Worksheet ws = vs == null ? null : vs.getBaseWorksheet();

      if(ws == null || source.getSource() == null) {
         return null;
      }

      Assembly assembly = ws.getAssembly(VSUtil.getTableName(source.getSource()));

      if(assembly == null) {
         assembly = ws.getAssembly(source.getSource());
      }

      return assembly instanceof BoundTableAssembly bound ? bound : null;
   }
}
