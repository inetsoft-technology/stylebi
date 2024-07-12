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
package inetsoft.analytic.composition.event;

import inetsoft.analytic.composition.ViewsheetEvent;
import inetsoft.analytic.composition.command.SetVSTreeGrayFieldsCommand;
import inetsoft.report.composition.*;
import inetsoft.report.composition.command.MessageCommand;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.internal.table.TableHighlightAttr;
import inetsoft.report.internal.table.TableHyperlinkAttr;
import inetsoft.report.script.viewsheet.ScriptEvent;
import inetsoft.report.script.viewsheet.VSAScriptable;
import inetsoft.uql.VariableTable;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.UserVariable;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.PrintWriter;
import java.util.*;

/**
 * Set input object value event.
 *
 * @version 8.0, 08/03/2005
 * @author InetSoft Technology Corp
 */
public class SetInputObjectValueEvent extends ViewsheetEvent {
   /**
    * Constructor.
    */
   public SetInputObjectValueEvent() {
      super();
   }

   /**
    * Constructor.
    */
   public SetInputObjectValueEvent(String name, String[] values) {
      this();
      put("name", name);
      this.values = values;
   }

   /**
    * Check if requires return.
    * @return <tt>true</tt> if requires, <tt>false</tt> otherwise.
    */
   @Override
   public boolean requiresReturn() {
      return false;
   }

   /**
    * Get the name of the asset event.
    * @return the name of the asset event.
    */
   @Override
   public String getName() {
      return Catalog.getCatalog().getString("Set InputObject Value");
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
      return get("name") != null ? new String[] {(String) get("name")} :
         new String[0];
   }

   /**
    * Process event.
    */
   @Override
   public void process(RuntimeViewsheet rvs, AssetCommand command)
      throws Exception
   {
      runnable = process0(rvs, command, this, (String) get("name"), values,
                          "true".equals(get("blockEvent")));
   }

   public void continueProcess() throws Exception {
      if(runnable != null) {
         runnable.run();

         if(runnable.ex != null) {
            throw runnable.ex;
         }
      }
   }

   private abstract static class ExecuteRunnable implements Runnable {
      Exception ex;
   }

   /**
    * Process event.
    */
   public static void process0(final RuntimeViewsheet rvs,
                               final AssetCommand command,
                               final ViewsheetEvent event,
                               String name, String[] values)
      throws Exception
   {
      process0(rvs, command, event, name, values, false);
   }

   public static ExecuteRunnable process0(final RuntimeViewsheet rvs,
                                          final AssetCommand command,
                                          final ViewsheetEvent event,
                                          String name, String[] values,
                                          boolean blockExecute)
      throws Exception
   {
      Viewsheet vs = rvs.getViewsheet();

      if(vs == null) {
         return null;
      }

      // @by ankitmathur, Fix Bug #4211, Need to maintain the old instances of
      // all VSCrosstabInfo's which can be used to sync the new/updated
      // TableDataPaths after the assembly is updated.
      Assembly[] assemblyList = vs.getAssemblies();
      Map<String, VSAssemblyInfo> oldCrosstabInfo = new HashMap<>();

      for(Assembly casembly : assemblyList) {
         if(casembly instanceof CrosstabVSAssembly) {
            oldCrosstabInfo.put(casembly.getName(), (VSAssemblyInfo)
               ((CrosstabVSAssembly) casembly).getVSAssemblyInfo().clone());
         }
      }

      // binding may be caused by variable, should retry mv if necessary
      rvs.resetMVOptions();
      final InputVSAssembly assembly = (InputVSAssembly) vs.getAssembly(name);

      if(assembly == null) {
         command.addCommand(new MessageCommand("", MessageCommand.OK));
         return null;
      }

      final VSModelTableContext context = new VSModelTableContext(rvs);
      final int ocnt = context.getTableCount();
      VSAssemblyInfo info = assembly.getVSAssemblyInfo();
      String type = assembly.getDataType();
      Object obj = values == null || values.length == 0 ? null :
         Tool.getData(type, values[0]);
      int hint0;

      if(info instanceof SliderVSAssemblyInfo) {
         hint0 = ((SliderVSAssemblyInfo) info).setSelectedObject(obj);
         VSEventUtil.refreshVSAssembly(rvs, info.getAbsoluteName(), command);
      }
      else if(info instanceof SpinnerVSAssemblyInfo) {
         hint0 = ((SpinnerVSAssemblyInfo) info).setSelectedObject(obj);
      }
      else if(info instanceof CheckBoxVSAssemblyInfo) {
         Object[] objs = values == null ? null : new Object[values.length];

         if(values != null) {
            for(int i = 0; i < values.length; i++) {
               objs[i] = Tool.getData(type, values[i]);
            }
         }

         hint0 = ((CheckBoxVSAssemblyInfo) info).setSelectedObjects(objs);
      }
      else if(info instanceof RadioButtonVSAssemblyInfo) {
         hint0 = ((RadioButtonVSAssemblyInfo) info).setSelectedObject(obj);
      }
      else if(info instanceof ComboBoxVSAssemblyInfo) {
         hint0 = ((ComboBoxVSAssemblyInfo) info).setSelectedObject(obj);
      }
      else if(info instanceof TextInputVSAssemblyInfo) {
         hint0 = ((TextInputVSAssemblyInfo) info).setSelectedObject(obj);
      }
      else {
         return null;
      }

      final int hint = hint0;
      String tname = assembly.getTableName();

      if(assembly.isVariable() && tname != null) {
         tname = tname.substring(2, tname.length() - 1);
      }

      final ViewsheetSandbox box = rvs.getViewsheetSandbox();

      if(box == null) {
         return null;
      }

      final boolean form = info instanceof TextInputVSAssemblyInfo ||
         info instanceof ListInputVSAssemblyInfo;
         
      // @by stephenwebster, fix bug1386097203077
      // instead of dispatching the event, only attach the event to the 
      // VSScope, otherwise, both the call to execute and the call to dispatchEvent
      // will execute the onLoad script.
      if(form) {
         ScriptEvent event0 = new InputScriptEvent(name, assembly);
         box.attachScriptEvent(event0);
      }

      final AssetQuerySandbox wbox = box.getAssetQuerySandbox();
      Worksheet ws = assembly.getViewsheet().getBaseWorksheet();
      final Assembly ass = ws.getAssembly(tname);
      refreshVariable(assembly, wbox, ws, vs);

      ExecuteRunnable execution = new ExecuteRunnable() {
         @Override
         public void run() {
            try {
               handle();
            }
            catch(Exception e) {
               ex = e;
            }
         }
         
         private void handle() throws Exception {
            ChangedAssemblyList clist = createList(true, event, command, rvs,
                  event.getLinkURI());

            if(wbox != null && (ass instanceof VariableAssembly)) {
               if(hint == VSAssembly.OUTPUT_DATA_CHANGED) {
                  wbox.setIgnoreFiltering(false);
                  VSEventUtil.refreshEmbeddedViewsheet(rvs, event,
                     event.getLinkURI(), command);
                  box.resetRuntime();
                  // @by stephenwebster, For Bug #6575
                  // refreshViewsheet() already calls reset() making this call redundant
                  // It also has a side-effect of making the viewsheet load twice.
                  // I double checked bug1391802567612 and it seems like it
                  // is working as expected
                  // box.reset(clist);
                  VSEventUtil.refreshViewsheet(rvs, event, event.getID(),
                     event.getLinkURI(), command, false, true, true, clist);
               }
            }
            else {
               // @by stephenwebster, For Bug #1726, remove the synchronize
               // on the VSAQueryLock.  getVGraphPair.init uses graphLock and
               // the VSAQueryLock is obtained after it to maintain correct
               // locking order.  Any calls to get data from a graph most likely
               // should be routed through the VGraphPair instead of getting it
               // direct.  I tested a similar asset related to bug1350539979627
               // and could not reproduce it.  This change is commented out below.

               //synchronized(box.getVSAQueryLock()) {
                  // here may be cause fire command, AddVSObjectCommand for chart,
                  // then GetChartAreaEvent will be fired in flex, VSEventUtil.execte
                  // may cause ViewsheetSandbox.cancel, if current time, the chart
                  // is in get data from GetChartAreaEvent, will cause no data returned
                  // see bug1350539979627
                  box.reset(clist);
                  VSEventUtil.execute(rvs, event, assembly.getAbsoluteName(),
                     event.getLinkURI(), hint | VSAssembly.VIEW_CHANGED, command);
               //}
            }

            // @davidd bug1364406849572, refactored processing of shared filters to
            // external and local.
            VSEventUtil.processExtSharedFilters(assembly, hint, event, rvs,command);
            rvs.getViewsheetSandbox().processSharedFilters(assembly, clist, true);

            // fix bug1368262989004, fix this bug same as bug1366884826731, now
            // no matter process share filter whether success or not, we should
            // also execute, or some dependency assembly will not refresh.
            VSEventUtil.execute(rvs, event, assembly.getName(),
               event.getLinkURI(), clist, command, true);

            VSEventUtil.layoutViewsheet(rvs, event, event.getID(),
               event.getLinkURI(), command);
            int ncnt = context.getTableCount();

            if(ocnt != ncnt) {
               context.process(event, command);
            }

            if(form) {
               box.detachScriptEvent();
            }

            VSModelTrapContext tcontext = new VSModelTrapContext(rvs);

            if(tcontext.isCheckTrap()) {
               tcontext.checkTrap(null, null);
               DataRef[] refs = tcontext.getGrayedFields();
               command.addCommand(new SetVSTreeGrayFieldsCommand(refs));
            }

            command.addCommand(new MessageCommand("", MessageCommand.OK));
         }
      };

      if(blockExecute) {
         return execution;
      }

      execution.run();

      if(execution.ex != null) {
         throw execution.ex;
      }

      // @by ankitmathur, Fix Bug #4211, Use the old VSCrosstabInfo's to sync
      // new TableDataPaths.
      for(Assembly cassembly : vs.getAssemblies()) {
         if(cassembly instanceof CrosstabVSAssembly) {
            try {
               box.updateAssembly(cassembly.getAbsoluteName());
               CrosstabVSAssembly cross = (CrosstabVSAssembly) cassembly;
               CrosstabVSAssemblyInfo ocinfo =
                  (CrosstabVSAssemblyInfo) oldCrosstabInfo.get(
                     cassembly.getName());
               FormatInfo finfo = cross.getFormatInfo();
               CrosstabVSAssemblyInfo ncinfo = (CrosstabVSAssemblyInfo)
                  cross.getVSAssemblyInfo();
               String objValue = obj == null ? null : obj.toString();

               if(isAggregateChange(ncinfo.getVSCrosstabInfo(), objValue)) {
                  continue;
               }

               TableHyperlinkAttr hyperlink = ncinfo.getHyperlinkAttr();
               TableHighlightAttr highlight = ncinfo.getHighlightAttr();

               if(finfo != null) {
                  synchronized(finfo.getFormatMap()) {
                     VSUtil.syncCrosstabPath(cross, ocinfo, false, finfo.getFormatMap(), true);
                  }
               }

               if(hyperlink != null) {
                  VSUtil.syncCrosstabPath(cross, ocinfo, false, hyperlink.getHyperlinkMap(), true);
               }

               if(highlight != null) {
                  VSUtil.syncCrosstabPath(cross, ocinfo, false, highlight.getHighlightMap(), true);
               }
            }
            catch(Exception ex) {
               LOG.warn(
                           "Failed to sync Crosstab paths", ex);
            }
         }
      }

      return null;
   }

   /**
    * Verify whether the input value is a part of the runtime Aggregates for the
    * Crosstab. If so, no need to sync the old TableDataPath.
    *
    * @param ncinfo VSCrosstabInfo which is generated after the assembly has
    *               been updated.
    * @param changedValue The new input value.
    *
    * @return <tt>true</tt> if the new input value is a runtime aggregate.
    */
   private static boolean isAggregateChange(VSCrosstabInfo ncinfo,
                                            String changedValue)
   {
      if(ncinfo == null || ncinfo.getRuntimeAggregates() == null)
      {
         return false;
      }

      DataRef[] nAggRefs = ncinfo.getRuntimeAggregates();

      try {
         for(DataRef nagg : nAggRefs) {
            if(nagg.getName().contains(changedValue)) {
               return true;
            }
         }
      }
      catch(Exception ex) {
         //ignore the exception
      }

      return false;
   }

   private static void refreshVariable(VSAssembly assembly,
                                       AssetQuerySandbox wbox,
                                       Worksheet ws, Viewsheet vs)
      throws Exception
   {
      if(!(assembly instanceof InputVSAssembly) || wbox == null || ws == null) {
         return;
      }

      InputVSAssembly iassembly = (InputVSAssembly) assembly;
      Object cdata;
      Object mdata = null;

      if(iassembly instanceof SingleInputVSAssembly) {
         cdata = ((SingleInputVSAssembly) iassembly).getSelectedObject();
      }
      else if(iassembly instanceof CompositeInputVSAssembly) {
         Object[] objs =
            ((CompositeInputVSAssembly) iassembly).getSelectedObjects();
         cdata = objs == null || objs.length == 0 ? null : objs[0];
         mdata = objs == null || objs.length <= 1 ? null : objs;
      }
      else {
         throw new RuntimeException("Unsupported assembly found: " +
                                    assembly);
      }

      String tname = iassembly.getTableName();
      VariableTable vt = wbox.getVariableTable();

      if(iassembly.isVariable() && tname != null) {
         tname = tname.substring(2, tname.length() - 1);
      }

      if(wbox != null && tname != null) {
         Assembly ass = ws.getAssembly(tname);

         if(ass instanceof VariableAssembly) {
            vt.put(tname, mdata == null ? cdata : mdata);
            wbox.refreshVariableTable(vt);
         }
         else if(ws != null && tname != null) {
            ArrayList<UserVariable> variableList = new ArrayList<>();

            Viewsheet.mergeVariables(variableList, ws.getAllVariables());
            Viewsheet.mergeVariables(variableList, vs.getAllVariables());

            for(UserVariable var : variableList) {
               if(var != null && tname.equals(var.getName())) {
                  vt.put(tname, mdata == null ? cdata : mdata);
                  break;
               }
            }

            wbox.refreshVariableTable(vt);
         }
      }
      else if(wbox != null) {
         vt.put(iassembly.getName(), mdata == null ? cdata : mdata);
         wbox.refreshVariableTable(vt);
      }
   }

   /**
    * Write the contents of this object.
    * @param writer the output stream to which to write the XML data.
    */
   @Override
   protected void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      if(values != null) {
         writer.print("<values>");

         for(int i = 0; i < values.length; i++) {
            writer.print("<value>");
            writer.print("<![CDATA[" + values[i] + "]]>");
            writer.print("</value>");
         }

         writer.print("</values>");
      }
   }

   /**
    * Read in the contents of this object from an xml tag.
    * @param tag the specified xml element.
    */
   @Override
   protected void parseContents(Element tag) throws Exception {
      super.parseContents(tag);
      Element nnode = Tool.getChildNodeByTagName(tag, "values");

      if(nnode != null) {
         NodeList nodes = Tool.getChildNodesByTagName(nnode, "value");
         values = new String[nodes.getLength()];

         for(int i = 0; i < nodes.getLength(); i++) {
            Element node0 = (Element) nodes.item(i);
            values[i] = Tool.getValue(node0);
         }
      }
   }

   /**
    * Event for viewsheet script, it keeps event source, assembly and other
    * properties.
    */
   public static class InputScriptEvent implements ScriptEvent {
      /**
       * Constructure.
       */
      public InputScriptEvent(String name, VSAssembly assembly) {
         this.name = name;

         if(assembly instanceof TextInputVSAssembly) {
            this.type = "textinput";
         }
         else if(assembly instanceof CheckBoxVSAssembly) {
            this.type = "checkbox";
         }
         else if(assembly instanceof RadioButtonVSAssembly) {
            this.type = "radiobutton";
         }
         else if(assembly instanceof ComboBoxVSAssembly) {
            this.type = "combobox";
         }
         else if(assembly instanceof SelectionListVSAssembly) {
            this.type = "selectionlist";
         }
         else if(assembly instanceof SelectionTreeVSAssembly) {
            this.type = "selectiontree";
         }
         else if(assembly instanceof TimeSliderVSAssembly) {
            this.type = "rangeslider";
         }
         else if(assembly instanceof CalendarVSAssembly) {
            this.type = "calendar";
         }
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
      public String name;              // source assembly name
      public String type;              // assembly type
   }

   private String[] values;
   private ExecuteRunnable runnable;

   private static final Logger LOG =
      LoggerFactory.getLogger(SetInputObjectValueEvent.class);
}
