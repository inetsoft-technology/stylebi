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
package inetsoft.analytic.composition.event;

import inetsoft.analytic.composition.ViewsheetEvent;
import inetsoft.analytic.composition.command.*;
import inetsoft.report.TableDataPath;
import inetsoft.report.composition.*;
import inetsoft.report.composition.command.MessageCommand;
import inetsoft.report.composition.event.AssetEventUtil;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.erm.AbstractModelTrapContext.TrapInfo;
import inetsoft.uql.erm.*;
import inetsoft.uql.schema.UserVariable;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XSourceInfo;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.awt.*;
import java.io.PrintWriter;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;

/**
 * Drag asset event.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class DragVSAssetEvent extends ViewsheetEvent {
   /**
    * Constructor.
    */
   public DragVSAssetEvent() {
      super();
   }

   /**
    * Constructor.
    * @param entries asset entry.
    */
   public DragVSAssetEvent(AssetEntry[] entries) {
      this();
      this.entries = entries;
   }

   /**
    * Constructor.
    * @param entries asset entry.
    */
   public DragVSAssetEvent(AssetEntry[] entries, Point position) {
      this();
      put("x", "" + position.x);
      put("y", "" + position.y);
      this.entries = entries;
   }

   /**
    * Get the name of the asset event.
    * @return the name of the asset event.
    */
   @Override
   public String getName() {
      return catalog.getString("New Assembly");
   }

   /**
    * Check if is undoable/redoable.
    * @return <tt>true</tt> if undoable/redoable.
    */
   @Override
   public boolean isUndoable() {
      return true;
   }

   /**
    * Get the influenced assemblies.
    * @return the influenced assemblies, <tt>null</tt> means all.
    */
   @Override
   public String[] getAssemblies() {
      int x = Integer.parseInt((String) get("x"));
      int y = Integer.parseInt((String) get("y"));
      Viewsheet vs = rvs.getViewsheet();

      if(vs == null) {
         return new String[0];
      }

      VSAssembly vsassembly = (VSAssembly) vs.getAssembly(x, y);

      return vsassembly != null ? new String[] {vsassembly.getAbsoluteName()} :
         new String[0];
   }

   /**
    * Process event.
    */
   @Override
   public void process(RuntimeViewsheet rvs, AssetCommand command)
      throws Exception
   {
      AssetEvent.MAIN.set(this);
      this.rvs = rvs;
      int x = Integer.parseInt((String) get("x"));
      int y = Integer.parseInt((String) get("y"));
      boolean confirmed = "true".equals(get("confirmed"));
      Viewsheet vs = rvs.getViewsheet();
      ViewsheetSandbox box = rvs.getViewsheetSandbox();

      if(vs == null || box == null || entries.length == 0) {
         return;
      }

      Assembly cs = vs.getAssembly((String) get("cname"));
      CurrentSelectionVSAssembly cassembly =
         cs instanceof CurrentSelectionVSAssembly ?
         (CurrentSelectionVSAssembly) cs : null;

      // fix bug1245222404461, do not add in comp if is runtime
      if(cassembly == null && rvs.isRuntime()) {
         return;
      }

      AssetEntry entry = entries[0];

      // @by davyc, the logic is to support dnd column entry to embedded current
      // selection, in fact here should only be drag column entry, so i am not
      // maintain when entry is other type logic. First we get the embedded
      // current selection viewsheet, to add the new assembly, then
      // all process just as dnd to a non embedded viewsheet, except in the
      // VSEventUtil.execute, we should use the assembly absolute name
      if(cassembly != null && entry.isColumn()) {
         vs = cassembly.getViewsheet();

         if(vs == null) {
            return;
         }
      }

      Worksheet ws = vs.getBaseWorksheet();
      VSAssembly vsassembly = (VSAssembly) vs.getAssembly(x, y);

      List errors = (List) AssetRepository.ASSET_ERRORS.get();

      if(errors != null && errors.size() > 0) {
         StringBuilder sb = new StringBuilder();

         for(int i = 0; i < errors.size(); i++) {
            if(i > 0) {
               sb.append(", ");
            }

            sb.append(errors.get(i));
         }

         sb.append("(" + entry.getDescription() + ")");
         errors.clear();

         String msg = Catalog.getCatalog().getString(
            "common.mirrorAssemblies.updateFailed",
            sb.toString());
         MessageCommand mcmd = new MessageCommand(msg, MessageCommand.WARNING);
         command.addCommand(mcmd);
         return;
      }

      String target = (String) get("target");
      String option = (String) get("option");
      VSAssembly tobj = null;

      // drop on an existing component, check for colocate option
      if(target != null) {
         int index = target.indexOf(".");

         if(index != -1) {
            target = target.substring(0, index);
         }

         tobj = (VSAssembly) vs.getAssembly(target);

         if(tobj != null) {
            if("right".equals(option)) {
               x = tobj.getPixelOffset().x + tobj.getPixelSize().width + 1;
            }
            else if("down".equals(option)) {
               y = tobj.getPixelOffset().y + tobj.getPixelSize().height + 1;
            }
         }
      }

      String nname = null;

      // drop a component
      if(entry.getType() == AssetEntry.Type.COMPONENT ||
         entry.getType() == AssetEntry.Type.SHAPE)
      {
         String name = entry.getName();
         nname = VSEventUtil.createVSAssembly(rvs, this, name, getLinkURI(),
                                              command, new Point(x, y));

         if(nname == null) {
            return;
         }

         Assembly nassembly = vs.getAssembly(nname);
         processNewTab(vs, nname, option, tobj, command);

         if(nassembly instanceof FloatableVSAssembly && "move".equals(option)
            && entry.getType() == AssetEntry.Type.COMPONENT)
         {
            Point pixelOffset = new Point(
               Integer.parseInt((String) get("pixelX")),
               Integer.parseInt((String) get("pixelY")));
            ((FloatableVSAssembly) nassembly).setPixelOffset(pixelOffset);
         }

         Assembly[] arr = new Assembly[] {nassembly};
         ChangedAssemblyList clist =
            createList(false, this, command, rvs, getLinkURI());
         box.reset(null, arr, clist, false, false, null);
         VSEventUtil.execute(rvs, this, nname, getLinkURI(), clist, command,
                             true);

         for(int i = command.getCommandCount() - 1; i >= 0; i--) {
            if(command.getCommand(i) instanceof AddVSObjectCommand) {
               AddVSObjectCommand add = (AddVSObjectCommand) command.getCommand(i);
               VSAssemblyInfo cinfo = (VSAssemblyInfo) add.get("info");

               // open editing pane when adding new component
               if(cinfo.getAbsoluteName().equals(nassembly.getAbsoluteName())) {
                  if(nassembly instanceof ChartVSAssembly) {
                     add.put("editChart", "true");
                  }
                  else if(nassembly instanceof CrosstabVSAssembly) {
                     add.put("editCrosstab", "true");
                  }
                  else if(nassembly instanceof CalcTableVSAssembly) {
                     add.put("editCalc", "true");
                  }
               }
            }
         }
      }
      // drop a viewsheet (embedded)
      else if(entry.isViewsheet() && !entry.isVSSnapshot()) {
         AssetRepository engine = getViewsheetEngine().getAssetRepository();
         Viewsheet vassembly = (Viewsheet)
            engine.getSheet(entry, rvs.getUser(), true, AssetContent.ALL);
         VSEventUtil.syncEmbeddedTableVSAssembly(vassembly);
         nname = AssetUtil.getNextName(vs, vassembly.getAssemblyType());
         vassembly = vassembly.createVSAssembly(nname);
         vassembly.setPixelOffset(new Point(x, y));
         vassembly.setEntry(entry);
         vassembly.initDefaultFormat();

         vs.addAssembly(vassembly);
         processNewTab(vs, nname, option, tobj, command);

         rvs.initViewsheet(vassembly, false);
         VSEventUtil.refreshEmbeddedViewsheet(rvs, this, getLinkURI(), command);
         VSEventUtil.addDeleteVSObject(rvs, this, vassembly, getLinkURI(),
                                       command);
         VSEventUtil.initTable(rvs, command, getLinkURI(),
                               vassembly.getAssemblies(true, false));
         VSEventUtil.refreshVSAssembly(rvs, nname, command);
      }
      // drop a query table
      else if(entry.isTable()) {
         if(!VSEventUtil.BASE_WORKSHEET.equals(entry.getProperty("source")))
         {
            return;
         }

         String tableName = entry.getName();

         if(isModelEntry(entry)) {
            tableName = entry.getProperty("table");
         }

         tableName = VSUtil.getTableName(tableName);
         vs.convertToEmbeddedTable(ws, tableName);
         TableAssembly tassembly = (TableAssembly) ws.getAssembly(tableName);

         if(tassembly == null) {
            return;
         }

         SourceInfo sinfo = new SourceInfo();
         sinfo.setType(SourceInfo.ASSET);
         sinfo.setSource(tassembly.getName());
         ColumnSelection columns = getColumnSelection(tassembly, entry, rvs);
         // add calculate fields.
         CalculateRef[] calcs = vs == null ? null : vs.getCalcFields(
            entry.isTable() ? entry.getName() : tassembly.getName());

         if(calcs != null) {
            for(int i = 0; i < calcs.length; i++) {
               if(calcs[i].isBaseOnDetail()) {
                  columns.addAttribute(calcs[i]);
               }
            }
         }

         columns = VSUtil.getVSColumnSelection(columns);

         for(int i = 0; i < columns.getAttributeCount(); i++) {
            ColumnRef col = (ColumnRef) columns.getAttribute(i);
            col.setAlias(VSUtil.trimEntity(col.getAttribute(), null));
         }

         TableVSAssemblyInfo tinfo = new TableVSAssemblyInfo();
         tinfo.setColumnSelection(columns);
         VSModelTrapContext context = new VSModelTrapContext(rvs);
         TrapInfo trapInfo = context.isCheckTrap()
            ? context.checkTrap(null, tinfo) : null;

         if(!confirmed && trapInfo != null && trapInfo.showWarning()) {
            String msg = Catalog.getCatalog().getString(
               "designer.binding.continueTrap");
            VSTrapMessageCommand cmd =
               new VSTrapMessageCommand(msg, MessageCommand.CONFIRM);
            cmd.addEvent(this);
            command.addCommand(cmd);
            return;
         }

         if(context.isCheckTrap()) {
            DataRef[] refs = context.getGrayedFields();
            command.addCommand(new SetVSTreeGrayFieldsCommand(refs));
         }

         nname = AssetUtil.getNextName(vs, AbstractSheet.TABLE_VIEW_ASSET);
         TableVSAssembly tvassembly;

         // embedded table doesn't support selection so it could be
         // confusing to users if it's created by default
         /*
         if(VSEventUtil.isEmbeddedDataSource(tassembly)) {
            tvassembly = new EmbeddedTableVSAssembly(vs, nname);
            ((TableVSAssemblyInfo)
               tvassembly.getVSAssemblyInfo()).setEmbeddedTable(true);
         }
         else {
         */
         tvassembly = new TableVSAssembly(vs, nname);

         tvassembly.setTitleValue(isModelEntry(entry) ?
            entry.getName() : tassembly.getName().replace("^_^", "."));
         tvassembly.setSourceInfo(sinfo);
         tvassembly.setColumnSelection(columns);
         Dimension size = calculateSize(tassembly, columns, entry);

         size = new Dimension(size.width, Math.max(size.height, 6));
         tvassembly.setPixelSize(size);
         tvassembly.setPixelOffset(new Point(x, y));
         tvassembly.initDefaultFormat();

         vs.addAssembly(tvassembly);
         processNewTab(vs, nname, option, tobj, command);

         // only after add assembly over, could we refresh viewsheet
         VSModelTableContext tcontext = new VSModelTableContext(rvs);
         tcontext.process(this, command);

         /*
         boolean refreshed = tcontext.process(null, tinfo, this, command);

         if(!refreshed) {
            VSEventUtil.addDeleteVSObject(rvs, this, tvassembly, getLinkURI(),
                                          command);
            VSEventUtil.loadTableLens(rvs, this, nname, getLinkURI(), command);
            VSEventUtil.refreshVSAssembly(rvs, nname, command);
         }
         */
      }
      // drop columns
      else if(entry.isColumn()) {
         if(entries.length == 1 && entries[0].isFolder()) {
            entries = getSubEntries(entries[0]);
         }

         if(entries.length == 0) {
            return;
         }

         String source = entries[0].getProperty("source");

         // column may not have a source if dragged from a component
         if(source != null && !VSEventUtil.BASE_WORKSHEET.equals(source)) {
            return;
         }

         ColumnSelection columns = new ColumnSelection();
         int width = 0;
         String tablename = null;
         boolean sameTable = true;
         String dim = null;

         for(int i = 0; i < entries.length; i++) {
            if(entries[i].getType() != AssetEntry.Type.COLUMN ||
               Tool.equals(entries[i].getProperty("refType"),
               DataRef.CUBE_MEASURE + ""))
            {
               continue;
            }

            if(dim == null) {
               dim = entries[i].getProperty("entity");
            }
            else if(!Tool.equals(dim, entries[i].getProperty("entity"))) {
               continue;
            }

            AssetEntry centry = entries[i];
            ColumnRef ref = createColumnRef(centry);
            columns.addAttribute(ref);
            width++;

            String tname = entries[i].getProperty("table");

            if(tname == null) {
               tname = entries[i].getParent().getName();
            }

            if(tablename == null) {
               tablename = tname;
            }
            else if(sameTable) {
               sameTable = tablename.equals(tname);
            }
         }

         if(ws == null) {
            return;
         }

         tablename = VSUtil.getTableName(tablename);

         TableAssembly tassembly = (TableAssembly) ws.getAssembly(tablename);

         if(tassembly == null) {
            // new viewsheet without base worksheet, add a viewsheet which
            // contains a chart, then edit the chart, drag a column to current
            // viewsheet, will ignore it, so prompt a warning, we will not
            // especially trigger when the top viewsheet has a table whose name
            // just same as the embedded viewsheet table
            String msg = Catalog.getCatalog().getString(
               "viewer.viewsheet.tableNotFound", tablename);
            command.addCommand(new MessageCommand(msg, MessageCommand.WARNING));
            return;
         }

         // fix data type
         ColumnSelection ocolumns = tassembly.getColumnSelection(true);

         for(int i = 0; i < ocolumns.getAttributeCount(); i++) {
            ColumnRef column = (ColumnRef) ocolumns.getAttribute(i);
            column = VSUtil.getVSColumnRef(column);
            int index = columns.indexOfAttribute(column);

            if(index >= 0) {
               ColumnRef ncolumn = (ColumnRef) columns.getAttribute(index);
               ncolumn.setDataType(column.getDataType());
            }
         }

         String name = null;
         String isNoEmpty = tassembly.getProperty("noEmpty");

         if(columns.getAttributeCount() > 0) {
            String dtype = ((ColumnRef) columns.getAttribute(0)).getDataType();
            int type = 0;

            // alias not useful in selection assembly and causes binding problem
            // @by yuz, alias is necessary for a cube member
            /*for(int k = 0; k < columns.getAttributeCount(); k++) {
               ((ColumnRef) columns.getAttribute(k)).setAlias(null);
            }*/

            if(columns.getAttributeCount() > 1 && sameTable) {
               type = AbstractSheet.SELECTION_TREE_ASSET;

               if(!VSEventUtil.isValidDataRefs(tassembly.getName(),
                                               toArray(columns)))
               {
                  command.addCommand(new MessageCommand(
                     Catalog.getCatalog().getString(
                     "viewer.viewsheet.editSelectionTree")));

                  return;
               }
            }
            else if(XSchema.isDateType(dtype) || XSchema.isNumericType(dtype)) {
               type = AbstractSheet.TIME_SLIDER_ASSET;
            }
            else {
               type = AbstractSheet.SELECTION_LIST_ASSET;
            }

            name = AssetUtil.getNextName(vs, type);

            vsassembly = createSelectionVSAssembly(
               vs, type, dtype, name, tassembly.getName(), columns, cassembly);

            VSModelTrapContext context = new VSModelTrapContext(rvs);
            TrapInfo trapInfo = context.isCheckTrap()
               ? context.checkTrap(null, vsassembly.getVSAssemblyInfo())
               : null;

            if(!confirmed && trapInfo != null && trapInfo.showWarning()) {
               String msg = Catalog.getCatalog().getString(
                  "designer.binding.continueTrap");
               VSTrapMessageCommand cmd =
                  new VSTrapMessageCommand(msg, MessageCommand.CONFIRM);
               cmd.addEvent(this);
               command.addCommand(cmd);
               return;
            }

            if(context.isCheckTrap()) {
               DataRef[] refs = context.getGrayedFields();
               command.addCommand(new SetVSTreeGrayFieldsCommand(refs));
            }

            vsassembly.setPixelOffset(new Point(x, y));

            if(type == AbstractSheet.TIME_SLIDER_ASSET && "move".equals(option))
            {
               Point pixelOffset = new Point(
                  Integer.parseInt((String) get("pixelX")),
                  Integer.parseInt((String) get("pixelY")));
               ((FloatableVSAssembly) vsassembly).setPixelOffset(pixelOffset);
            }

            vsassembly.initDefaultFormat();
            vs.addAssembly(vsassembly);

            // first add the assembly to container, then add assembly to
            // viewsheet, to avoid current selection get dependence error
            if(cassembly != null) {
               updateCurrentSelection(cassembly, vsassembly, vs, x, y);
               VSLayoutEvent.updateOutSelection(rvs, command);
            }

            nname = name;
            processNewTab(vs, nname, option, tobj, command);
            VSEventUtil.addDeleteVSObject(rvs, this, vsassembly, getLinkURI(),
                                          command);
         }

         if(cassembly != null) {
            VSEventUtil.refreshVSAssembly(rvs, cassembly.getName(), command,
                                          true);
         }

         // here should use absolute name to execute, because in embedded
         // current selection, the vs has changed to the embedded viewsheet,
         // so the name/nname will be not contained the parent viewsheet path,
         // but rvs is the top viewsheet runtime viewsheet, so if use the
         // name/nname to execute, will cause problem
         String aname = vs.getAssembly(nname) == null ? null :
            vs.getAssembly(nname).getAbsoluteName();
         int hint = isLogicalModel(entry) ?
            VSAssembly.INPUT_DATA_CHANGED : VSAssembly.VIEW_CHANGED;
         VSEventUtil.execute(rvs, this, aname, getLinkURI(), hint, command);

         if(tassembly instanceof CubeTableAssembly) {
            tassembly.setProperty("noEmpty", isNoEmpty);
         }

         // fix bug1297897847912, only after add assembly over, could we
         // refresh viewsheet, because this logic may lead to command complete,
         // the back of all the commands are not executed
         VSModelTableContext tctx = new VSModelTableContext(rvs);
         tctx.process(null, vsassembly.getVSAssemblyInfo(), this, command);
      }
      else if(entry.isVariable()) {
         String vname = entry.getName();
         VariableAssembly vassembly = (VariableAssembly) ws.getAssembly(vname);

         if(vassembly == null) {
            return;
         }

         int display = vassembly.getVariable().getDisplayStyle();
         String name = null;
         String type = vassembly.getVariable().getTypeNode().getType();
         type = type == null ? XSchema.STRING : type;

         switch(display) {
         case UserVariable.NONE:
            if(type.equals(XSchema.BOOLEAN)) {
               name = AbstractSheet.CHECKBOX_ASSET + "";
            }
            else if(type.equals(XSchema.STRING) || type.equals(XSchema.CHAR) ||
               type.equals(XSchema.DATE) || type.equals(XSchema.TIME) ||
               type.equals(XSchema.TIME_INSTANT))
            {
               name = AbstractSheet.COMBOBOX_ASSET + "";
            }
            else if(type.equals(XSchema.BYTE) || type.equals(XSchema.INTEGER) ||
               type.equals(XSchema.FLOAT) || type.equals(XSchema.DOUBLE) ||
               type.equals(XSchema.LONG) || type.equals(XSchema.SHORT))
            {
               name = AbstractSheet.SPINNER_ASSET + "";
            }
            break;
         case UserVariable.CHECKBOXES:
         case UserVariable.LIST:
            name = AbstractSheet.CHECKBOX_ASSET + "";
            break;
         case UserVariable.RADIO_BUTTONS:
            name = AbstractSheet.RADIOBUTTON_ASSET + "";
            break;
         case UserVariable.DATE_COMBOBOX:
         case UserVariable.COMBOBOX:
         default:
            name = AbstractSheet.COMBOBOX_ASSET + "";
         }

         nname = VSEventUtil.createVSAssembly(rvs, this, name, getLinkURI(),
               command, new Point(x, y));

         if(nname == null) {
            return;
         }

         processNewTab(vs, nname, option, tobj, command);

         Assembly nassembly = vs.getAssembly(nname);
         Object dValue = vassembly.getVariable().getValueNode() == null ?
            null : vassembly.getVariable().getValueNode().getValue();

         if(nassembly instanceof InputVSAssembly) {
            InputVSAssembly iassembly = (InputVSAssembly) nassembly;
            iassembly.setTableName("$(" + vname + ")");
            iassembly.setDataType(type);
            iassembly.setVariable(true);

            if(iassembly instanceof ListInputVSAssembly) {
               ListInputVSAssembly lassembly = (ListInputVSAssembly) iassembly;
               AssetVariable avar = vassembly.getVariable();

               // fix bug1284005618690, if the varible has no table, and the
               // choice is from query, execute the varible
               if(avar != null && avar.getTableName() == null &&
                  avar.getChoiceQuery() != null)
               {
                  avar = (AssetVariable) avar.clone();
                  AssetQuerySandbox wbox = box.getAssetQuerySandbox();
                  AssetEventUtil.executeVariable(wbox, avar);
               }

               if(avar.getValues() != null && avar.getChoices() != null &&
                  avar.getValues().length > 0 && avar.getChoices().length > 0 &&
                  avar.getValues().length == avar.getChoices().length)
               {
                  String[] labels = new String[avar.getChoices().length];
                  Object[] values = new Object[avar.getValues().length];

                  for(int i = 0; i < avar.getChoices().length; i++) {
                     labels[i] = Tool.toString(avar.getChoices()[i]);
                     values[i] = Tool.getData(type, avar.getValues()[i]);
                  }

                  ListData data = new ListData();
                  data.setLabels(labels);
                  data.setValues(values);
                  lassembly.setListData(data);
                  lassembly.setSourceType(ListInputVSAssembly.EMBEDDED_SOURCE);
               }
               else if(avar.getValueAttribute() != null) {
                  ListBindingInfo binfo = new ListBindingInfo();
                  binfo.setTableName(avar.getTableName());
                  binfo.setLabelColumn(getVSRef(avar.getLabelAttribute()));
                  binfo.setValueColumn(getVSRef(avar.getValueAttribute()));
                  lassembly.setListBindingInfo(binfo);
                  lassembly.setSourceType(ListInputVSAssembly.BOUND_SOURCE);
               }

               if(lassembly instanceof CheckBoxVSAssembly) {
                  CheckBoxVSAssembly cass = (CheckBoxVSAssembly) iassembly;

                  if(cass.getListData() == null &&
                     cass.getBindingInfo() == null)
                  {
                     ListData data = new ListData();
                     data.setLabels(new String[]{"true", "false"});
                     data.setValues(new Object[]{true, false});
                     cass.setSourceType(ListInputVSAssembly.EMBEDDED_SOURCE);
                     cass.setListData(data);
                  }

                  cass.setPixelSize(new Dimension(2, 2));

                  if(dValue != null) {
                     ((CheckBoxVSAssemblyInfo) cass.getInfo()).
                        setSelectedObjects(new Object[]{dValue});
                  }
               }

               if(lassembly instanceof ComboBoxVSAssembly) {
                  if(!type.equals(XSchema.CHAR)) {
                     ((ComboBoxVSAssembly) lassembly).setTextEditable(true);
                  }

                  if(dValue != null) {
                     ((ComboBoxVSAssemblyInfo) lassembly.getInfo()).
                        setSelectedObject(dValue);
                  }
               }

               if(lassembly instanceof RadioButtonVSAssembly) {
                  if(dValue != null) {
                     ((RadioButtonVSAssemblyInfo) lassembly.getInfo()).
                        setSelectedObject(dValue);
                  }
               }
            }
            else if(iassembly instanceof NumericRangeVSAssembly) {
               if(dValue != null) {
                  ((SpinnerVSAssemblyInfo) iassembly.getInfo()).
                     setSelectedObject(dValue);
               }
            }
         }

         ChangedAssemblyList clist =
            createList(false, this, command, rvs, getLinkURI());
         box.resetRuntime();
         box.reset(clist);
         VSEventUtil.execute(rvs, this, nname, getLinkURI(), clist, command,
                             true);
      }

      VSEventUtil.layoutViewsheet(rvs, this, getID(), getLinkURI(), command);
   }

   /**
    * Process creating new tab.
    */
   private void processNewTab(Viewsheet vs, String nname, String option,
                              VSAssembly tobj, AssetCommand command)
         throws Exception
   {
      VSAssembly nassembly = (VSAssembly) vs.getAssembly(nname);

      if(tobj != null && nassembly != null && nassembly.supportsTab() &&
         "colocate".equals(option))
      {
         ChangeVSTabEvent event = null;
         // use absolute name
         nname = nassembly.getAbsoluteName();

         if(tobj instanceof TabVSAssembly) {
            event = new ChangeVSTabEvent(tobj.getName(), nname, null,
                                         AssetEvent.MOVE_IN_TAB);
         }
         else if(tobj.getContainer() instanceof TabVSAssembly) {
            event = new ChangeVSTabEvent(tobj.getContainer().getName(),
                                         nname, null,
                                         AssetEvent.MOVE_IN_TAB);
         }
         else {
            event = new ChangeVSTabEvent(null, nname, tobj.getName(),
                                         AssetEvent.MOVE_IN_TAB);
         }

         AssetCommand cmd = new AssetCommand(this);
         event.process(rvs, cmd);
         command.mergeCommand(cmd);
      }
   }

   private DataRef getVSRef(DataRef ref) {
      DataRef attr = ref;

      if(attr instanceof ColumnRef) {
         attr = VSUtil.getVSColumnRef((ColumnRef) ref);
      }
      else if(ref != null) {
         attr = new AttributeRef(null, ref.getAttribute());
         ((AttributeRef) attr).setDataType(ref.getDataType());
      }

      return attr;
   }

   /**
    * Calculate the size of the table.
    */
   private Dimension calculateSize(TableAssembly table, ColumnSelection columns,
                                   AssetEntry entry)
   {
      if(!isModelEntry(entry)) {
         return new Dimension(columns.getAttributeCount() * AssetUtil.defw, 4 * AssetUtil.defh);
      }

      TableAssembly ctable = (TableAssembly) table.clone();
      ctable.setColumnSelection(columns, true);
      Dimension msize = getMinimumSize(table, columns);
      Dimension size = table.getInfo().getPixelSize();

      if(msize.width > size.width || msize.height > size.height) {
         int width = Math.max(msize.width, size.width);
         int height = Math.max(msize.height, size.height);
         size = new Dimension(width, height);
      }

      if(((AbstractWSAssembly) table).isIconized()) {
         return new Dimension(Math.min(size.width, 2 * AssetUtil.defw), AssetUtil.defh);
      }

      return size;
   }

   /**
    * Get the minimum size of the table.
    */
   private Dimension getMinimumSize(TableAssembly table,
                                    ColumnSelection columns)
   {
      int width = 0;
      boolean pub = table.isRuntime() || table.isAggregate();

      for(int i = 0; i < columns.getAttributeCount(); i++) {
         ColumnRef ref = (ColumnRef) columns.getAttribute(i);

         if((table.isRuntime() || table.isLiveData()) && !ref.isVisible()) {
            continue;
         }

         width += ref.getWidth();
      }

      width = Math.max(AssetUtil.defw, width);
      return new Dimension(width, 3 * AssetUtil.defh);
   }

   /**
    * Check if the entry is from logical model.
    */
   private boolean isModelEntry(AssetEntry entry) {
      return (XSourceInfo.MODEL + "").equals(entry.getProperty("originType"));
   }

   /**
    * Init cell format, if the assembly is selection list, copy the object
    * format for the cell format, otherwise do nothing.
    */
   private void initCellFormat(VSAssembly assembly) {
      if(!(assembly instanceof SelectionListVSAssembly)) {
         return;
      }

      SelectionListVSAssemblyInfo info =
         (SelectionListVSAssemblyInfo) assembly.getInfo();
      FormatInfo finfo = info.getFormatInfo();
      VSCompositeFormat objfmt = finfo.getFormat(VSAssemblyInfo.OBJECTPATH);
      TableDataPath detailPath = new TableDataPath(-1, TableDataPath.DETAIL);
      VSCompositeFormat cellfmt = finfo.getFormat(detailPath);
      VSEventUtil.copyFormat(cellfmt.getUserDefinedFormat(), objfmt, false,
                             false);
   }

   /**
    * Update the current selection container assemblies.
    * @param cassembly the CurrentSelectionVSAssembly.
    * @param newAssembly the name of the assembly to be added.
    * @param vs the runtime viewsheet.
    * @param x the dimension x of current selection.
    * @param y the dimension y of current selection.
    */
   private void updateCurrentSelection(CurrentSelectionVSAssembly cassembly,
      VSAssembly newAssembly, Viewsheet vs, int x, int y)
   {
      VSAssemblyInfo info = newAssembly.getVSAssemblyInfo();
      int cw = cassembly.getPixelSize().width;

      // when a selection list is dropped in container, defaults to span the
      // entire width
      if(newAssembly instanceof SelectionListVSAssembly) {
         SelectionListVSAssemblyInfo info2 = (SelectionListVSAssemblyInfo)
            newAssembly.getInfo();
         initCellFormat(newAssembly);
      }

      if(newAssembly instanceof TimeSliderVSAssembly) {
         TimeSliderVSAssemblyInfo tinfo = (TimeSliderVSAssemblyInfo) info;
         tinfo.initDefaultFormat(true);
         newAssembly.setPixelSize(new Dimension(cw, 3 * AssetUtil.defh));
      }

      if(info instanceof DropDownVSAssemblyInfo) {
         DropDownVSAssemblyInfo dinfo = (DropDownVSAssemblyInfo) info;
         dinfo.setListHeight(newAssembly.getPixelSize().height / AssetUtil.defh - 1);
      }

      int idx = Math.max(0, Integer.parseInt((String) get("idx")));
      String[] assemblies = cassembly.getAssemblies();
      String[] newAssemblies = new String[assemblies.length + 1];
      idx = Math.min(idx, assemblies.length);

      if(idx < assemblies.length) {
         System.arraycopy(assemblies, 0, newAssemblies, 0, idx);
         System.arraycopy(assemblies, idx, newAssemblies, idx + 1,
                          assemblies.length - idx);
      }
      else {
         System.arraycopy(assemblies, 0, newAssemblies, 0, assemblies.length);
      }

      newAssembly.setPixelOffset(new Point(x, y + idx + 1));
      newAssembly.setPixelSize(new Dimension(cw, newAssembly.getPixelSize().height));
      newAssemblies[idx] = newAssembly.getName();
      cassembly.setAssemblies(newAssemblies);

      // if the new assembly causes a scrollbar to be added, collapse all others
      Point start = cassembly.getPixelOffset();
      Dimension all = cassembly.getPixelSize();
      int totalH = AssetUtil.defh;

      for(String name : newAssemblies) {
         totalH += AssetUtil.defh + (vs.getAssembly(name).getPixelSize().height - AssetUtil.defh) * AssetUtil.defh;
      }

      if(totalH > all.height) {
         for(String name : newAssemblies) {
            if(!name.equals(newAssembly.getName())) {
               Assembly aobj = vs.getAssembly(name);
               aobj.setPixelSize(new Dimension(aobj.getPixelSize().width, AssetUtil.defh));
            }
         }
      }
   }

   /**
    * Convert column selection list to an array.
    */
   private static DataRef[] toArray(ColumnSelection columns) {
      DataRef[] arr = new DataRef[columns.getAttributeCount()];

      for(int i = 0; i < arr.length; i++) {
         arr[i] = columns.getAttribute(i);
      }

      return arr;
   }

   /**
    * Get the sub entries of an asset table/query.
    * @param entry the specified asset table/query.
    * @return the sub entries of the asset table/entry.
    */
   private AssetEntry[] getSubEntries(AssetEntry entry) throws Exception {
      AssetRepository engine = getWorksheetEngine().getAssetRepository();
      AssetEntry.Type type = entry.getType();

      if(type != AssetEntry.Type.TABLE && type != AssetEntry.Type.QUERY &&
         type != AssetEntry.Type.PHYSICAL_TABLE)
      {
         return new AssetEntry[0];
      }

      return engine.getEntries(entry, getUser(), null);
   }

   /**
    * Write the contents of this object.
    * @param writer the output stream to which to write the XML data.
    */
   @Override
   protected void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      if(entries != null) {
         writer.print("<entries>");

         for(int i = 0; i < entries.length; i++) {
            entries[i].writeXML(writer);
         }

         writer.print("</entries>");
      }
   }

   /**
    * Read in the contents of this object from an xml tag.
    * @param tag the specified xml element.
    */
   @Override
   protected void parseContents(Element tag) throws Exception {
      super.parseContents(tag);
      Element node = Tool.getChildNodeByTagName(tag, "entries");

      if(node != null) {
         List list = new ArrayList();
         NodeList nodes =  node.getChildNodes();

         for(int i = 0; i < nodes.getLength(); i++) {
            list.add(AssetEntry.createAssetEntry((Element) nodes.item(i)));
         }

         entries = new AssetEntry[list.size()];
         list.toArray(entries);
      }
   }

   /**
    * Get title.
    */
   private static String getTitle(DataRef ref) {
      if((ref.getRefType() & DataRef.CUBE) == 0) {
         return VSUtil.trimEntity(ref.getAttribute(), null);
      }

      ref = DataRefWrapper.getBaseDataRef(ref);
      return ref.toView();
   }

   /**
    * Check if the entry is from logical model.
    */
   protected static boolean isLogicalModel(AssetEntry entry) {
      String type = entry.getProperty("originType");

      if(type == null) {
         return false;
      }

      return Integer.parseInt(type) == XSourceInfo.MODEL;
   }

   /**
    * Create column selection.
    */
   protected static ColumnRef createColumnRef(AssetEntry entry) {
      if(entry == null) {
         return new ColumnRef();
      }

      AttributeRef attr = new AttributeRef(
         isLogicalModel(entry) ? null : entry.getProperty("entity"),
         entry.getProperty("attribute"));

      if(entry.getProperty("caption") != null) {
         attr.setCaption(entry.getProperty("caption"));
      }

      // fix ref type, DIMENSION or MEASURE
      if(entry.getProperty("refType") != null) {
         attr.setRefType(Integer.parseInt(entry.getProperty("refType")));
      }

      ColumnRef ref = new ColumnRef(attr);
      ref.setDataType(entry.getProperty("dtype"));
      ref.setAlias(entry.getProperty("alias"));

      return ref;
   }

   /**
    * Create selection vs assembly.
    */
   protected static VSAssembly createSelectionVSAssembly(Viewsheet vs, int type,
      String dtype, String name, String tableName, ColumnSelection columns,
      CurrentSelectionVSAssembly cassembly)
   {
      VSAssembly vsassembly = null;

      if(type == AbstractSheet.SELECTION_LIST_ASSET) {
         SelectionListVSAssembly list = new SelectionListVSAssembly(vs, name);

         vsassembly = list;
         list.setTableName(tableName);
         list.setDataRef(columns.getAttribute(0));
         list.setTitleValue(getTitle(columns.getAttribute(0)));
      }
      else if(type == AbstractSheet.SELECTION_TREE_ASSET) {
         SelectionTreeVSAssembly tree = new SelectionTreeVSAssembly(vs, name);

         vsassembly = tree;
         tree.setTableName(tableName);
         tree.setDataRefs(toArray(columns));
         tree.setTitleValue(getTitle(columns.getAttribute(0)));
      }
      else {
         TimeSliderVSAssembly slider = new TimeSliderVSAssembly(vs, name);

         vsassembly = slider;
         DataRef ref = columns.getAttribute(0);
         int reftype = ref.getRefType();
         SingleTimeInfo tinfo = new SingleTimeInfo();
         tinfo.setDataRef(ref);

         if((reftype & DataRef.CUBE_DIMENSION) != 0 && !XSchema.isDateType(dtype)) {
            tinfo.setRangeTypeValue(TimeInfo.MEMBER);
         }
         else if(XSchema.isNumericType(dtype)) {
            // let TimeSliderVSAQuery to set the range size from data
            tinfo.setRangeTypeValue(TimeInfo.NUMBER);
         }
         else if(XSchema.TIME.equals(dtype)) {
            tinfo.setRangeTypeValue(TimeInfo.MINUTE_OF_DAY);
         }
         else {
            tinfo.setRangeTypeValue(TimeInfo.MONTH);
         }

         slider.setTimeInfo(tinfo);
         slider.setTableName(tableName);
         slider.setTitleValue(getTitle(columns.getAttribute(0)));

         // don't use pixel size in container
         if(cassembly != null) {
            slider.setPixelSize(null);
         }
      }

      return vsassembly;
   }

   /**
    * Get the ColumnSelection.
    */
   private ColumnSelection getColumnSelection(TableAssembly table,
                                              AssetEntry entry,
                                              RuntimeViewsheet rvs)
   {
      if(!isLogicalModel(entry)) {
         return table.getColumnSelection(true);
      }

      AssetRepository engine = getViewsheetEngine().getAssetRepository();
      ViewsheetSandbox box = rvs.getViewsheetSandbox();
      Viewsheet vs = rvs.getViewsheet();

      if(vs == null || box == null) {
         return new ColumnSelection();
      }

      Principal user = box.getUser();
      List<TableAssembly> list = VSEventUtil.createPseudoAssemblies(engine,
         vs.getBaseEntry(), user);

      for(TableAssembly tassembly : list) {
         if(tassembly.getName().equals(entry.getName())) {
            return (ColumnSelection) tassembly.getColumnSelection().clone();
         }
      }

      return new ColumnSelection();
   }

   private AssetEntry[] entries;
   private Catalog catalog = Catalog.getCatalog();
   private transient RuntimeViewsheet rvs;
}
