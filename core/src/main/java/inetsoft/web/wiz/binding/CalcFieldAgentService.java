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

import inetsoft.web.binding.controller.ModifyCalculateFieldServiceProxy;
import inetsoft.web.binding.drm.CalculateRefModel;
import inetsoft.web.binding.event.ImmutableModifyCalculateFieldEvent;
import inetsoft.web.binding.model.ExpressionRefModel;
import inetsoft.web.wiz.binding.model.BindableTable;
import inetsoft.web.wiz.viewsheet.ViewsheetSessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Create/edit/remove a VIEWSHEET-scoped calculated field ("calc field") -- the field the native
 * Composer's Formula Editor dialog creates, stored in {@code Viewsheet.calcmap} and visible only
 * to charts/crosstabs/calc-tables bound to the same source table in THIS viewsheet. Distinct
 * from a worksheet expression column, which is shared by every viewsheet built on that worksheet
 * -- this is the mechanism to reach for when a data need must NOT be visible to other dashboards
 * sharing the same worksheet (see {@code list_worksheet_dependents} for checking that claim
 * before assuming it).
 *
 * <p>Delegates the actual write to {@link ModifyCalculateFieldServiceProxy#modifyCalculateField},
 * the same service the Formula Editor dialog's OK button drives -- this class only builds the
 * event and validates the table name up front, rather than re-implementing the wizard/cube/
 * aggregate-info side effects that method already handles.
 */
@Service
public class CalcFieldAgentService {
   @Autowired
   public CalcFieldAgentService(ViewsheetSessionService sessions,
                                BindableFieldsService fieldsService,
                                ModifyCalculateFieldServiceProxy modifyCalculateFieldService)
   {
      this.sessions = sessions;
      this.fieldsService = fieldsService;
      this.modifyCalculateFieldService = modifyCalculateFieldService;
   }

   /**
    * @param table       the source table the calc field belongs to (required)
    * @param assembly    the chart/crosstab/calc-table this edit is being made from, or
    *                    {@code null} for one not tied to a specific assembly yet -- only affects
    *                    that assembly's own aggregate-info refresh, not where the field is stored
    * @param name        the calc field's current name (required) -- the name to create it under
    *                    when {@code create} is {@code true}, or the name to find when editing/
    *                    removing an existing one
    * @param newName     a new name, when renaming an existing calc field; {@code null} to keep
    *                    {@code name}. Ignored when {@code remove} is {@code true}.
    * @param expression  the formula text. Required unless {@code remove} is {@code true}.
    * @param dataType    the calc field's data type (e.g. {@code "double"}, {@code "string"}), or
    *                    {@code null} to leave it to the engine to infer.
    * @param sql         {@code true} for a native-SQL expression, {@code false}/{@code null} for
    *                    a JavaScript one.
    * @param baseOnDetail {@code true} (the default when {@code null}) for a per-row (detail)
    *                    calculation; {@code false} for one computed from an aggregate result.
    * @param remove      {@code true} to remove the named calc field. Fields other than
    *                    {@code table}/{@code name} are ignored.
    * @param create      {@code true} to create a new calc field rather than edit an existing one.
    */
   public record CalcFieldRequest(String table, String assembly, String name, String newName,
                                  String expression, String dataType, Boolean sql,
                                  Boolean baseOnDetail, boolean remove, boolean create) {}

   public void modify(String sessionToken, Principal agent, CalcFieldRequest req, String linkUri)
      throws Exception
   {
      if(req.table() == null || req.table().isBlank()) {
         throw new IllegalArgumentException(
            "A calc field requires 'table' -- the source table it belongs to, as reported by " +
            "list_bindable_fields.");
      }

      if(req.name() == null || req.name().isBlank()) {
         throw new IllegalArgumentException(
            "A calc field requires 'name' -- its current name when editing/removing, or the " +
            "name to create it under.");
      }

      if(!req.remove() && (req.expression() == null || req.expression().isBlank())) {
         throw new IllegalArgumentException(
            "Creating or editing a calc field requires 'expression'.");
      }

      String newName = req.newName() != null && !req.newName().isBlank()
         ? req.newName() : req.name();

      CalculateRefModel model = null;

      if(!req.remove()) {
         ExpressionRefModel exprModel = new ExpressionRefModel();
         exprModel.setName(newName);
         exprModel.setExp(req.expression());
         exprModel.setDType(req.dataType());

         model = new CalculateRefModel();
         model.setDataRefModel(exprModel);
         model.setBaseOnDetail(req.baseOnDetail() == null || req.baseOnDetail());
         model.setSql(Boolean.TRUE.equals(req.sql()));
         model.setDataType(req.dataType());
      }

      CalculateRefModel finalModel = model;

      sessions.mutate(sessionToken, agent, (rvs, runtimeId, dispatcher) -> {
         String tableName = requireBindableTable(runtimeId, req.table(), agent);

         ImmutableModifyCalculateFieldEvent event = ImmutableModifyCalculateFieldEvent.builder()
            .name(req.assembly())
            .confirmed(true)
            .calculateRef(finalModel)
            .tableName(tableName)
            .remove(req.remove())
            .create(req.create())
            .refName(req.name())
            .checkTrap(false)
            .wizard(false)
            .build();

         modifyCalculateFieldService.modifyCalculateField(
            runtimeId, event, agent, dispatcher, linkUri);
      });
   }

   /**
    * The write-after-write verification this class exists to enforce: {@code Viewsheet.addCalcField}
    * stores a calc field under any table name handed to it, with no check the name refers to a
    * table the viewsheet can actually bind to -- an orphaned calc field would otherwise be created
    * silently, visible in no field listing, matching how {@code BindableColumns} guards a similar
    * unchecked-name gap on the column side.
    *
    * <p>Matched case-insensitively, like {@code SelectionBindingService}/{@code TableBindingService}'s
    * own {@code resolveTable} helpers -- and, like both of those, this returns the LISTING's own
    * canonically-cased name rather than the caller's raw string. {@code Viewsheet.calcmap} is a
    * plain, case-sensitive {@code Map<String, List<CalculateRef>>}: storing under the caller's
    * casing instead of the canonical one would pass this check yet create a field under a key no
    * chart bound to the real table ever looks up -- silently orphaned, not merely misspelled.
    *
    * <p>Unscoped ({@code assembly: null}) deliberately, exactly like
    * {@code SelectionBindingService.resolveTable} -- passing {@code assembly} here would narrow
    * the listing to THAT assembly's own currently-bound source (see
    * {@code BindableFieldsService.list}/{@code VSTreeHandler.getChartTreeModel}), which would
    * wrongly refuse a real worksheet table whenever the given assembly happens to be bound to a
    * different one. {@code table} and {@code assembly} are independent by this class's own
    * contract -- {@code assembly} only affects that one assembly's aggregate-info refresh, not
    * where the field is stored or which tables are valid to store it under.
    */
   private String requireBindableTable(String runtimeId, String table, Principal agent)
      throws Exception
   {
      List<BindableTable> tables = fieldsService.list(runtimeId, null, agent);

      for(BindableTable candidate : tables) {
         if(table.equalsIgnoreCase(candidate.name())) {
            return candidate.name();
         }
      }

      throw new IllegalArgumentException(
         "'" + table + "' is not a bindable table in this viewsheet. Available: " +
         tables.stream().map(BindableTable::name).collect(Collectors.joining(", ")) + ".");
   }

   private final ViewsheetSessionService sessions;
   private final BindableFieldsService fieldsService;
   private final ModifyCalculateFieldServiceProxy modifyCalculateFieldService;
}
