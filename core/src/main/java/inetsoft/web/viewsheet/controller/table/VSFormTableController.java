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
package inetsoft.web.viewsheet.controller.table;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.analytic.composition.event.VSEventUtil;
import inetsoft.report.composition.*;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.internal.Util;
import inetsoft.report.script.viewsheet.ScriptEvent;
import inetsoft.report.script.viewsheet.VSAScriptable;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.filereader.TextUtil;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.TableVSAssemblyInfo;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import inetsoft.web.composer.vs.objects.controller.ComposerVSTableController;
import inetsoft.web.viewsheet.LoadingMask;
import inetsoft.web.viewsheet.Undoable;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.viewsheet.event.table.*;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.NonNull;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.sql.Time;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Controller that processes vs form events.
 */
@Controller
public class VSFormTableController {
   /**
    * Creates a new instance of <tt>VSFormTableController</tt>.
    *
    * @param viewsheetService    engine for retrieving runtime viewsheets.
    * @param placeholderService  command/changes utility service
    * @param runtimeViewsheetRef reference to the runtime viewsheet associated
    *                            with the WebSocket session.
    */
   @Autowired
   public VSFormTableController(ViewsheetService viewsheetService,
                                RuntimeViewsheetRef runtimeViewsheetRef,
                                PlaceholderService placeholderService)
   {
      this.viewsheetService = viewsheetService;
      this.runtimeViewsheetRef = runtimeViewsheetRef;
      this.placeholderService = placeholderService;
   }

   private static String getRowLimitMessage() {
      return Catalog.getCatalog().getString(
         "common.oganization.rowMaxCount", Util.getOrganizationMaxRow());
   }

   /**
      * Insert/Append row in form table.
      *
      * @param event      the event parameters.
      * @param principal  a principal identifying the current user.
      * @param dispatcher the command dispatcher.
      *
      * @throws Exception if unable to retrieve/edit object.
      */
   @Undoable
   @LoadingMask
   @MessageMapping("/formTable/addRow")
   public void addRow(@Payload InsertTableRowEvent event, @LinkUri String linkUri,
                      CommandDispatcher dispatcher, Principal principal)
      throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(
         this.runtimeViewsheetRef.getRuntimeId(), principal);
      ViewsheetSandbox box = rvs.getViewsheetSandbox();
      String assemblyName = event.getAssemblyName();
      FormTableLens form = box.getFormTableLens(assemblyName);

      assert form != null;

      if(Util.getOrganizationMaxRow() > 0 && form.getRowCount() >= Util.getOrganizationMaxRow()) {
         MessageCommand command = new MessageCommand();
         command.setMessage(getRowLimitMessage());
         command.setType(MessageCommand.Type.WARNING);
         command.setAssemblyName(assemblyName);
         dispatcher.sendCommand(command);

         return;
      }

      if(event.insert()) {
         form.insertRow(event.row());
      }
      else {
         form.appendRow(event.row());
      }

      box.resetDataMap(assemblyName);
      BaseTableController.loadTableData(rvs, assemblyName, box.getMode(),
                                        event.start(), 100, linkUri, dispatcher);
      this.placeholderService.refreshVSAssembly(rvs, assemblyName, dispatcher);
   }

   /**
    * Remove rows in form table.
    *
    * @param event      the event parameters.
    * @param principal  a principal identifying the current user.
    * @param dispatcher the command dispatcher.
    *
    * @throws Exception if unable to retrieve/edit object.
    */
   @Undoable
   @LoadingMask
   @MessageMapping("/formTable/deleteRows")
   public void deleteRows(@Payload DeleteTableRowsEvent event, @LinkUri String linkUri,
                          CommandDispatcher dispatcher, Principal principal)
      throws Exception
   {
      final String runtimeId = runtimeViewsheetRef.getRuntimeId();
      final RuntimeViewsheet rvs = viewsheetService.getViewsheet(runtimeId, principal);
      final ViewsheetSandbox box = rvs.getViewsheetSandbox();
      final String assemblyName = event.getAssemblyName();
      final FormTableLens form = box.getFormTableLens(assemblyName);

      assert form != null;

      ArrayList<Integer> rows = new ArrayList<>();

      for(int i = 0; i < event.rows().size(); i++) {
         rows.add(event.rows().get(i));
      }

      rows.sort(Collections.reverseOrder());
      rows.forEach(form::deleteRow);
      box.resetDataMap(assemblyName);
      BaseTableController.loadTableData(rvs, event.getAssemblyName(), box.getMode(),
                                        event.start(), 100, linkUri, dispatcher);
   }

   /**
    * Change form table input.
    *
    * @param event      the event parameters.
    * @param principal  a principal identifying the current user.
    * @param dispatcher the command dispatcher.
    *
    * @throws Exception if unable to retrieve/edit object.
    */
   @Undoable
   @LoadingMask
   @MessageMapping("/formTable/edit")
   public void changeFormInput(@Payload ChangeFormTableCellInputEvent event,
                               @LinkUri String linkUri, CommandDispatcher dispatcher,
                               Principal principal)
      throws Exception
   {
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(
         this.runtimeViewsheetRef.getRuntimeId(), principal);
      Viewsheet viewsheet = rvs.getViewsheet();
      ViewsheetSandbox box = rvs.getViewsheetSandbox();
      FormTableLens form = box.getFormTableLens(event.getAssemblyName());

      int col = event.col();
      int row = event.row();
      VSAssembly assembly = viewsheet.getAssembly(event.getAssemblyName());
      TableVSAssemblyInfo info = (TableVSAssemblyInfo) VSEventUtil.getAssemblyInfo(rvs, assembly);
      ColumnSelection cols = info.getColumnSelection();

      int cindex = ComposerVSTableController.getActualColIndex(cols, col);
      DataRef ref = cols.getAttribute(cindex);
      String dtype = ref.getDataType();
      String data = event.data();

      if(XSchema.STRING.equals(dtype) && data != null &&
         data.length() > Util.getOrganizationMaxCellSize())
      {
         data = data.substring(0, Util.getOrganizationMaxCellSize());
         MessageCommand command = new MessageCommand();
         command.setMessage(Util.getTextLimitMessage());
         command.setType(MessageCommand.Type.WARNING);
         dispatcher.sendCommand(command);
      }

      FormRef formRef = (FormRef) ref;
      ColumnOption option = formRef.getOption();
      boolean comboBoxOption = ColumnOption.COMBOBOX.equals(option.getType());
      String label = comboBoxOption ? data : null;
      boolean valid = true;
      String error = null;
      Class cls = form.getColType(col);
      Object val = data;

      if(comboBoxOption && option instanceof ComboBoxColumnOption) {
         // data is label for comboBox
         val = getComboBoxValue(val, (ComboBoxColumnOption) option);
      }

      // use the actual type of the column in table in case the type defined on DataRef
      // is not accurate. The type in TableLens is more accurate
      if(cls != String.class) {
         dtype = Tool.getDataType(cls);
      }

      // Don't validate on empty input
      if(data != null && !data.isEmpty()) {
         /*
          Return an actual boolean when the column option is a boolean
          Otherwise, the truthiness of the returned value will be used
         */
         if(ColumnOption.BOOLEAN.equals(option.getType())) {
            val = Tool.getData("boolean", data);
         }

         if(Tool.TIME_INSTANT.equals(dtype) || Tool.DATE.equals(dtype) ||
            Tool.TIME.equals(dtype) || ColumnOption.DATE.equals(option.getType()))
         {
            TextUtil.TypeFormat typeFormat = TextUtil.getDateType(data);
            Date date = null;

            if(val instanceof Date) {
               date = (Date) val;
            }
            else if(typeFormat != null) {
               SimpleDateFormat dateFormat = new SimpleDateFormat(typeFormat.getFormat());
               date = dateFormat.parse(data);

               if(!Tool.equals(dateFormat.format(date), data)) {
                  final Object dateData = Tool.getData(dtype, data);

                  if(dateData instanceof Date) {
                     date = (Date) dateData;
                  }
               }
            }
            else {
               try {
                  date = new Date(Long.parseLong(data));
               }
               catch(NumberFormatException e) {
                  // All else failed, check for formats like hh:mm:ss
                  final Object dateData = Tool.getData(dtype, data);

                  if(dateData instanceof Date) {
                     date = (Date) dateData;
                  }
               }
            }

            if(date != null && Tool.TIME.equals(dtype)) {
               val = new Time(date.getTime());
            }
            else if(date != null && Tool.TIME_INSTANT.equals(dtype)) {
               val = new java.sql.Timestamp(date.getTime());
            }
            else if(date != null && (Tool.DATE.equals(dtype)
               || ColumnOption.DATE.equals(option.getType())))
            {
               val = new java.sql.Date(date.getTime());
            }
            else {
               val = date;
            }
         }

         if(Tool.BOOLEAN.equals(dtype) && ColumnOption.TEXT.equals(option.getType())) {
            valid = option.validate(data);
         }
         else {
            valid = val != null && option.validate(val);
         }

         error = valid ? null : option.getErrorMessage(val);
      }

      if(error != null) {
         this.placeholderService.sendMessage(error, MessageCommand.Type.ERROR, dispatcher);
      }
      else {
         form.setObject(row, col, val);
         form.setLabel(row, col, label);

         TableScriptEvent scriptEvent = new TableScriptEvent(event.getAssemblyName(), row, col);
         placeholderService.dispatchEvent(scriptEvent, dispatcher, rvs);
      }

      BaseTableController.loadTableData(rvs, event.getAssemblyName(), box.getMode(),
                                        event.start(), 100, linkUri, dispatcher);
   }

   private Object getComboBoxValue(Object val, @NonNull ComboBoxColumnOption refOption) {
      ListData listData = refOption.getListData();

      if(listData != null && listData.getLabels() != null) {
         String[] labels = listData.getLabels();

         for(int i = 0; i < labels.length; i++) {
            if(Objects.equals(labels[i], val)) {
               return listData.getValues()[i];
            }
         }
      }

      return val;
   }

   /**
    * Change form table input.
    *
    * @param event      the event parameters.
    * @param principal  a principal identifying the current user.
    * @param dispatcher the command dispatcher.
    *
    * @throws Exception if unable to retrieve/edit object.
    */
   @Undoable
   @LoadingMask
   @MessageMapping("/formTable/apply")
   public void applyChanges(@Payload ApplyFormChangesEvent event,
                            @LinkUri String linkUri, CommandDispatcher dispatcher,
                            Principal principal)
      throws Exception
   {
      String assemblyName = event.getAssemblyName();
      RuntimeViewsheet rvs = viewsheetService.getViewsheet(
         this.runtimeViewsheetRef.getRuntimeId(), principal);
      Viewsheet viewsheet = rvs.getViewsheet();
      ViewsheetSandbox box = rvs.getViewsheetSandbox();
      TableVSAssembly table = (TableVSAssembly) viewsheet.getAssembly(assemblyName);
      box.writeBackFormData(table);

      try {
         box.saveWsData(viewsheetService.getAssetRepository(), table.getViewsheet());
      }
      catch(Exception ex) {
         MessageCommand mcmd = new MessageCommand();
         mcmd.setMessage(ex.getMessage());
         mcmd.setType(MessageCommand.Type.ERROR);
         dispatcher.sendCommand(mcmd);
      }

      rvs.resetRuntime();
      BaseTableController.loadTableData(rvs, assemblyName, box.getMode(),
         0, 100, linkUri, dispatcher);
      ChangedAssemblyList clist =
         placeholderService.createList(false, dispatcher, rvs, linkUri);
      this.placeholderService.refreshViewsheet(rvs, rvs.getID(), linkUri, dispatcher,
         false, false, true, clist);
   }

   private final ViewsheetService viewsheetService;
   private final RuntimeViewsheetRef runtimeViewsheetRef;
   private final PlaceholderService placeholderService;

   /**
    * Event for viewsheet script, it keeps event source, assembly and other
    * properties.
    */
   public static final class TableScriptEvent implements ScriptEvent {
      /**
       * Constructure.
       */
      public TableScriptEvent(String name, int row, int column) {
         this.name = name;
         this.row = row;
         this.column = column;
      }

      /**
       * Get source name.
       */
      @Override
      public String getName() {
         return name;
      }

      /**
       * Set source assembly VSAScriptable object.
       */
      @Override
      public void setSource(VSAScriptable source) {
         this.source = source;
      }

      public VSAScriptable source;     // source scriptable object
      public String name;              // assembly name
      public String type = "table";    // assembly type
      public int row;                  // row index
      public int column;               // column index;
   }
}
