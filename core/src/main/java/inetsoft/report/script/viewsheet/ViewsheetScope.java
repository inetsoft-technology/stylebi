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
package inetsoft.report.script.viewsheet;

import inetsoft.mv.MVSession;
import inetsoft.report.TableLens;
import inetsoft.report.composition.*;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.composition.graph.VSDataSet;
import inetsoft.report.internal.license.LicenseManager;
import inetsoft.report.script.formula.CubeTableAssemblyScriptable;
import inetsoft.report.script.formula.FormulaFunctions;
import inetsoft.uql.VariableTable;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.*;
import inetsoft.uql.script.VariableScriptable;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.uql.util.XUtil;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;
import inetsoft.util.*;
import inetsoft.util.log.LogContext;
import inetsoft.util.script.*;
import inetsoft.web.viewsheet.command.MessageCommand;
import inetsoft.web.vswizard.model.VSWizardConstants;
import org.mozilla.javascript.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Array;
import java.security.Principal;
import java.util.*;
import java.util.function.Consumer;

/**
 * A scriptable used as the container for all assemblies in a viewsheet.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class ViewsheetScope extends ScriptableObject implements Cloneable, DynamicScope {
   /**
    * Viewsheet scope scriptable.
    */
   public static final String VIEWSHEET_SCRIPTABLE = "thisViewsheet";
   /**
    * Viewsheet scope parameter scriptable.
    */
   private static final String VIEWSHEET_PARAMETER = "parameter";

   /**
    * Create a scope for a viewsheet.
    */
   public ViewsheetScope(ViewsheetSandbox box, boolean withWS) {
      super();

      this.box = box;

      addProperties();
      addFunctions();

      senv = ScriptEnvRepository.getScriptEnv();
      senv.put("viewsheet", this);
      // an undocumented escape hatch
      senv.put("_viewsheet", box.getViewsheet());
   }

   /**
    * Add user defined functions to the scope.
    */
   private void addFunctions() {
      try {
         FunctionObject func = new FunctionObject2(this, getClass(), "runQuery",
            String.class, Object.class);
         propmap.put("runQuery", func);
         func = new FunctionObject2(this, getClass(), "toList", Object.class, String.class);
         propmap.put("toList", func);
         func = new FunctionObject2(this, getClass(), "addImage", String.class, Object.class);
         propmap.put("addImage", func);
         func = new FunctionObject2(this, getClass(), "createConnection",
            String.class, String.class, String.class);
         propmap.put("createConnection", func);
         /* not supported in 13.1
         func = new FunctionObject("addAction",
            getClass().getMethod("addAction",
            new Class[] {String.class, String.class, String.class}), this);
         propmap.put("addAction", func);
         */
         func = new FunctionObject2(this, getClass(), "refreshData");
         propmap.put("refreshData", func);
         func = new FunctionObject2(this, getClass(), "isCancelled");
         propmap.put("isCancelled", func);

         func = new FunctionObject("appendRow", getClass().getMethod("appendRow",
            new Class[] {String.class, Object.class}), this);
         propmap.put("appendRow", func);

         func = new FunctionObject("setCellValue", getClass().getMethod("setCellValue",
            new Class[] {String.class, int.class, int.class, Object.class}), this);
         propmap.put("setCellValue", func);

         func = new FunctionObject("saveWorksheet",
            getClass().getMethod("saveWorksheet"), this);
         propmap.put("saveWorksheet", func);

         func = new FunctionObject2(this, getClass(), "delayVisibility", int.class, Object.class);
         propmap.put("delayVisibility", func);
      }
      catch(Exception ex) {
         LOG.error("Failed to register viewsheet functions", ex);
      }
   }

   public void prepareVariables(VariableTable vtable) {
      setVariableTable(vtable);
   }

   /**
    * Execute a query.
    * @param name query name.
    * @param val query parameters as an array of pairs. Each pair is an
    * array of name and value.
    */
   public Object runQuery(String name, Object val) {
      return XUtil.runQuery(name, val, box.getUser(), null);
   }

   /**
    * Create a list from an array. The values may be sorted and duplicated
    * values removed.
    */
   public Object toList(Object arrObj, String options) {
      return FormulaFunctions.toList(arrObj, options);
   }

   /**
    * Add an image to the viewsheet. This can be used to dynamically change
    * the image of an element.
    * @param path image path to be used in an image assembly.
    */
   public void addImage(String path, Object img0) {
      ByteArrayOutputStream bout = new ByteArrayOutputStream();

      try {
         Image img = (Image) JavaScriptEngine.unwrap(img0);

         if(img != null) {
            CoreTool.writePNG(img, bout);
            box.getViewsheet().addUploadedImage(path, bout.toByteArray());
         }
         else {
            LOG.info("Failed to load image: " + path + " from " + img0);
         }
      }
      catch(Exception ex) {
         LOG.error("Failed to add image: " + path, ex);
      }
   }

   /**
    * Append row for the worksheet embedded table.
    * @param wstable the target worksheet embedded table assembly name.
    * @param data    the data used to update the target row.
    */
   public void appendRow(String wstable, Object data) {
      data = JavaScriptEngine.unwrap(data);

      if(!(data instanceof Object[])) {
         return;
      }

      Object[] values = (Object[]) data;
      XEmbeddedTable table = getEmbeddedTable(wstable);
      values = getValidData(values, table);
      int row = table.getRowCount();
      table.insertRow(row);

      for(int c = 0; c < values.length && c < table.getColCount(); c++) {
         table.setObject(row, c, values[c]);
      }
   }

   /**
    * Set cell value for worksheet embedded table.
    * @param wstable the target worksheet embedded table assembly name.
    * @param row     the specific row of the target embedded table.
    * @param col     the specific col of the target embedded table.
    * @param data    the data to set to the target cell.
    */
   public void setCellValue(String wstable, int row, int col, Object data) {
      data = JavaScriptEngine.unwrap(data);
      XEmbeddedTable table = getEmbeddedTable(wstable);

      if(table == null || col >= table.getColCount()) {
         return;
      }

      Map map = Tool.checkAndGetData(table.getDataType(col), data);

      if(map != null && !"true".equalsIgnoreCase(map.get("valid") + "")) {
         throw new RuntimeException(
            Catalog.getCatalog().getString("write.back.failed.invalidData"));
      }

      data = map.get("result");

      while(row >= table.getRowCount()) {
         table.insertRow(row);
      }

      table.setObject(row, col, data);
   }

   /**
    * Check if the form data is valid for the target embeddded table,
    * and return the values according to the data type.
    * @param values  the form data to append to embedded table.
    * @param table   the target ws embedded table.
    */
   private Object[] getValidData(Object[] values, XEmbeddedTable table) {
      if(values.length != table.getColCount()) {
         throw new RuntimeException(
            Catalog.getCatalog().getString("write.back.failed.invalidData"));
      }

      Object[] data = new Object[values.length];

      for(int i = 0; i < values.length; i++) {
         Map map = Tool.checkAndGetData(table.getDataType(i), values[i]);

         if(map == null || !"true".equalsIgnoreCase(map.get("valid") + "")) {
            throw new RuntimeException(
               Catalog.getCatalog().getString("write.back.failed.invalidData"));
         }

         data[i] = map.get("result");
      }

      return data;
   }

   /**
    * Get the embedded table for the target worksheet embedded table assembly.
    */
   private XEmbeddedTable getEmbeddedTable(String table) {
      Viewsheet vs = box.getViewsheet();
      Worksheet ws = vs == null || vs.isDirectSource() ? null : vs.getOriginalWorksheet();
      Assembly assembly = ws == null ? null : ws.getAssembly(table);

      if(assembly == null || !(assembly instanceof EmbeddedTableAssembly)) {
         throw new RuntimeException(
            Catalog.getCatalog().getString("write.back.failed.notFindTable", table));
      }

      return ((EmbeddedTableAssembly) assembly).getEmbeddedData();
   }

   /**
    * Save worksheet to write-back data to worksheet embedded table.
    */
   public void saveWorksheet() {
      Viewsheet vs = box.getViewsheet();
      Worksheet ws = vs == null ? null : vs.getOriginalWorksheet();

      if(ws == null || vs.isDirectSource()) {
         return;
      }

      WorksheetService service = WorksheetEngine.getWorksheetService();
      AssetRepository rep = service.getAssetRepository();

      try {
         box.saveWsData(rep, vs);
      }
      catch(Exception ex) {
         Tool.addUserMessage(new UserMessage(ex.getMessage(), ConfirmException.ERROR));
      }
   }

   /**
    * Create a connection to a certain database.
    * @param source data source name.
    * @param user user name to be logined.
    * @param passwd password to be logined.
    */
   public Object createConnection(String source, String user, String passwd) throws Exception {
      if(!LicenseManager.isComponentAvailable(LicenseManager.LicenseComponent.FORM)) {
         String msg = Catalog.getCatalog().getString("viewer.viewsheet.needFormLicense");
         JavaScriptEngine.alert(msg, MessageCommand.Type.INFO);
         throw new Exception();
      }

      if(db == null) {
         db = new DBScriptable(source, user, passwd, box.getUser());
      }
      else {
         db.init(source, user, passwd, box.getUser());
      }

      return db;
   }

   /**
    * Get current connection.
    */
   public Object getConnection() {
      return db;
   }

   /**
    * Get the script environment.
    * @return the script enrironment.
    */
   public ScriptEnv getScriptEnv() {
      return senv;
   }

   /**
    * Get the mode.
    * @return the mode of the scope.
    */
   public int getMode() {
      return mode;
   }

   /**
    * Set the mode.
    * @param mode the specified mode of the scope.
    */
   public void setMode(int mode) {
      this.mode = mode;
   }

   public void delayVisibility(int time, Object arrayObject) {
      if(!box.isRuntime()) {
         return;
      }

      if(time > 0 && arrayObject != null && JavaScriptEngine.unwrap(arrayObject).getClass().isArray()) {
         Set<String> names = new HashSet<>();
         Object array = JavaScriptEngine.unwrap(arrayObject);
         int len = Array.getLength(array);

         for(int i = 0; i < len; i++) {
            Object item = Array.get(array, i);

            if(item instanceof VSAScriptable) {
               String assemblyName = ((VSAScriptable) item).getAssembly();
               VSAssemblyInfo info =
                  box.getViewsheet().getAssembly(assemblyName).getVSAssemblyInfo();
               info.setVisible(false);
               info.setControlByScript(true);
               names.add(info.getAbsoluteName());
            }
         }

         if(!names.isEmpty()) {
            box.addDelayedVisibilityAssemblies(time, names);
         }
      }
   }

   /**
    * Get a property value.
    */
   @Override
   public Object get(String id, Scriptable start) {
      if("event".equals(id)) {
         ScriptEvent event = (ScriptEvent) vmap.get("event");

         if(event != null) {
            String name = event.getName();
            event.setSource(getVSAScriptable(name));
         }

         return event;
      }
      else if("OLD".equals(id)) {
         return FormTableRow.OLD;
      }
      else if("CHANGED".equals(id)) {
         return FormTableRow.CHANGED;
      }
      else if("ADDED".equals(id)) {
         return FormTableRow.ADDED;
      }
      else if("DELETED".equals(id)) {
         return FormTableRow.DELETED;
      }
      else if(propmap.containsKey(id)) {
         return propmap.get(id);
      }

      Object val = super.get(id, start);

      if(val == NOT_FOUND) {
         // this is to simulate dynamic scope for function. the execScriptable is the
         // scope actually executing the script. if var is not found until this point,
         // we look at the executing scope to see if it's there
         Scriptable execScriptable = JavaScriptEngine.getExecScriptable();

         if(execScriptable != null && execScriptable != this) {
            val = execScriptable.get(id, this);
         }
      }

      return val;
   }

   @Override
   public void put(String name, Scriptable start, Object value) {
      propmap.put(name, value);
   }

   /**
    * Add a variable.
    */
   public void addVariable(String name, Object variable) {
      vmap.put(name, variable);
   }

   /**
    * Remove variable.
    */
   public void removeVariable(String name) {
      vmap.remove(name);
   }

   /**
    * Indicate whether or not a named property is defined in an object.
    */
   @Override
   public boolean has(String name, Scriptable start) {
      if(propmap != null && propmap.containsKey(name)) {
         return true;
      }

      return super.has(name, start);
   }

   /**
    * Get an array of property ids.
    */
   @Override
   public Object[] getIds() {
      Set ids = new HashSet();

      Object[] pids = super.getIds();

      for(Object id : pids) {
         ids.add(id);
      }

      if(propmap != null) {
         for(Object id : propmap.keySet()) {
            ids.add(id);
         }
      }

      return ids.toArray();
   }

   /**
    * Reset this viewsheet scope.
    */
   private void addProperties() {
      VSAScriptable vsscope = createVSAScriptable(box.getViewsheet());
      propmap.put(VIEWSHEET_SCRIPTABLE, vsscope);
      addProperties0(box.getViewsheet().getAssemblies());
   }

   private void addProperties0(Assembly[] arr) {
      VSAScriptable vsscope = (VSAScriptable) propmap.get(VIEWSHEET_SCRIPTABLE);
      oldAssemblies.clear();

      // add vs scriptables to js runtime
      for(int i = 0; i < arr.length; i++) {
         addAssemblyScriptable(arr[i], vsscope);
      }

      propmap.put("confirmEvent", new ConfirmEventScriptable());
      propmap.put("param", new ParamScriptable(box));

      String viewsheetPath = box.getAssetEntry().getUser() != null ?
         Tool.MY_DASHBOARD + "/" + box.getAssetEntry().getPath() :
         box.getAssetEntry().getPath();
      propmap.put("viewsheetPath", viewsheetPath);
      propmap.put("viewsheetAlias", box.getAssetEntry().getAlias());

      if(box.getAssetEntry().getUser() != null) {
         propmap.put("viewsheetUser", box.getAssetEntry().getUser().name + " of " + box.getAssetEntry().getUser().orgID);
      }
      else {
         propmap.put("viewsheetUser", null);
      }

      refreshRuntimeScriptable();
      setVariableTable(box.getVariableTable());
   }

   private void addAssemblyScriptable(Assembly assembly, VSAScriptable vsscope) {
      if(VSWizardConstants.TEMP_CHART_NAME.equals(assembly.getAbsoluteName())) {
         return;
      }

      VSAScriptable scriptable = createVSAScriptable(assembly);
      scriptable.setParentScope(vsscope);
      propmap.put(assembly.getName(), scriptable);
      oldAssemblies.add(assembly.getName());
   }

   /**
    * Refresh runtime scriptable values.
    */
   public void refreshRuntimeScriptable() {
      propmap.put("exportFormat", box.getExportFormat());
   }

   /**
    * Clear assembly scriptables so they will be recreated when used.
    */
   public void refreshScriptable() {
      Assembly[] arr = box.getViewsheet().getAssemblies();

      synchronized(this) {
         for(int i = 0; i < oldAssemblies.size(); i++) {
            String name = oldAssemblies.get(i);
            propmap.remove(name);
         }

         addProperties0(arr);
      }
   }

   /**
    * Add action to viewsheet.
    */
   public void addAction(String icon, String label, String event) {
      Viewsheet vs = box == null ? null : box.getViewsheet();

      if(vs == null) {
         return;
      }

      ViewsheetInfo info = vs.getViewsheetInfo();
      info.addUserAction(icon, label, event);
   }

   /**
    * Invalid cached data.
    */
   public void refreshData() {
      box.setTouchTimestamp(System.currentTimeMillis());
      box.resetScriptable();

      MVSession session = box.getAssetQuerySandbox().getMVSession();

      if(session != null) {
         session.clearInitialized();
      }
   }

   public boolean isCancelled() {
      return box.isCancelled(executeStart);
   }

   /**
    * Reset chart scriptable.
    * @param assembly the chart assembly.
    */
   public void resetChartScriptable(ChartVSAssembly assembly) {
      String name = assembly.getName();
      VSAScriptable scriptable = createVSAScriptable(assembly);
      VSAScriptable vsscope = getVSAScriptable(VIEWSHEET_SCRIPTABLE);

      propmap.put(name, scriptable);
      scriptable.setParentScope(vsscope);
   }

   /**
    * Get the viewsheet assembly scriptable.
    * @param name the name of the viewsheet assembly.
    * @return the viewsheet assembly scriptable if any, <tt>null</tt> otherwise.
    */
   public VSAScriptable getVSAScriptable(String name) {
      Object obj = propmap.get(name);

      // safety check, assembly exist but missing in propmap, re-initialize. (46481)
      if(obj == null && box.getViewsheet().containsAssembly(name) &&
         !VSWizardConstants.TEMP_CHART_NAME.equals(name))
      {
         // Bug #62039, not safe to replace all scriptables, just create a scriptable for
         // this assembly
         Assembly assembly = box.getViewsheet().getAssembly(name);

         if(assembly != null) {
            addAssemblyScriptable(assembly, (VSAScriptable) propmap.get(VIEWSHEET_SCRIPTABLE));
         }

         obj = propmap.get(name);
      }

      if(!(obj instanceof VSAScriptable)) {
         return null;
      }

      return (VSAScriptable) obj;
   }

   /**
    * Get the variable scriptable which contains variables of the viewsheet.
    * @return a variable scriptable.
    */
   public VariableScriptable getVariableScriptable() {
      Object obj = propmap.get(VIEWSHEET_PARAMETER);

      if(!(obj instanceof VariableScriptable)) {
         return new VariableScriptable(new VariableTable());
      }

      return (VariableScriptable) obj;
   }

   /**
    * Set viewsheet parameters.
    * @param vtable which contains the variables of the viewsheet.
    */
   private void setVariableTable(VariableTable vtable) {
      if(vtable == null) {
         vtable = new VariableTable();
      }

      // per usa support's request, allow script to access user in viewsheet
      if(!vtable.contains("__principal__") && box.getUser() != null) {
         Principal principal = box.getUser();
         vtable.put("__principal__", principal);
         vtable.put("_USER_", XUtil.getUserName(principal));
         vtable.put("_ROLES_", XUtil.getUserRoleNames(principal));
         vtable.put("_GROUPS_", XUtil.getUserGroups(principal));
      }

      this.vtable = vtable;
      // @by stephenwebster, For bug1434039282803
      // set the parent scope in VariableScriptable for change made in get()
      VariableScriptable variableScriptable = new VariableScriptable(vtable);
      variableScriptable.setParentScope(getVSAScriptable(VIEWSHEET_SCRIPTABLE));

      propmap.put(VIEWSHEET_PARAMETER, variableScriptable);
   }

   /**
    * Reset cube table scriptable.
    */
   private void resetCubeScriptable(VSAScriptable scriptable0) {
      oldScriptable = scriptable0;
      String name = scriptable0.getAssembly();

      if(name == null || name.equals("thisViewsheet")) {
         return;
      }

      Viewsheet vs = box.getViewsheet();

      if(vs == null) {
         return;
      }

      Assembly vassembly = vs.getAssembly(name);

      if(vassembly == null || !(vassembly instanceof VSAssembly)) {
         return;
      }

      Worksheet ws = box.getWorksheet();

      if(ws == null) {
         return;
      }

      String tname = ((VSAssembly) vassembly).getTableName();
      Assembly assembly = ws.getCubeTableAssembly(tname);

      if(assembly == null || !(assembly instanceof CubeTableAssembly)) {
         return;
      }

      String tname0 = tname.replaceAll("[/. ]", "_");
      propmap.remove(tname0);

      try {
         Object data = box.getData(name);

         TableLens lens = data instanceof VSDataSet ?
            ((VSDataSet) data).getTable() :
            data instanceof TableLens ? (TableLens) data : null;

         if(lens != null) {
            Scriptable scriptable = new CubeTableAssemblyScriptable(
               tname, box.getAssetQuerySandbox(), mode, lens);
            propmap.put(tname0, scriptable);
         }
      }
      catch(Exception ex) {
         LOG.error("Failed to reset cub table data", ex);
      }
   }

   /**
    * Create viewsheet assembly script accordingly.
    * @param assembly the specified viewsheet assembly.
    * @return the created viewsheet assembly script.
    */
   private VSAScriptable createVSAScriptable(Assembly assembly) {
      if(assembly == null) {
         return null;
      }

      VSAScriptable scriptable;

      if(assembly instanceof RadioButtonVSAssembly) {
         scriptable = new RadioButtonVSAScriptable(box);
      }
      else if(assembly instanceof CheckBoxVSAssembly) {
         scriptable = new CheckBoxVSAScriptable(box);
      }
      else if(assembly instanceof SpinnerVSAssembly) {
         scriptable = new SpinnerVSAScriptable(box);
      }
      else if(assembly instanceof ComboBoxVSAssembly) {
         scriptable = new ComboBoxVSAScriptable(box);
      }
      else if(assembly instanceof SliderVSAssembly) {
         scriptable = new SliderVSAScriptable(box);
      }
      else if(assembly instanceof TextVSAssembly) {
         scriptable = new TextVSAScriptable(box);
      }
      else if(assembly instanceof CylinderVSAssembly) {
         scriptable = new CylinderVSAScriptable(box);
      }
      else if(assembly instanceof SlidingScaleVSAssembly) {
         scriptable = new SlidingScaleVSAScriptable(box);
      }
      else if(assembly instanceof GaugeVSAssembly) {
         scriptable = new GaugeVSAScriptable(box);
      }
      else if(assembly instanceof ThermometerVSAssembly) {
         scriptable = new ThermometerVSAScriptable(box);
      }
      else if(assembly instanceof ImageVSAssembly) {
         scriptable = new ImageVSAScriptable(box);
      }
      else if(assembly instanceof TimeSliderVSAssembly) {
         scriptable = new RangeSliderVSAScriptable(box);
      }
      else if(assembly instanceof CurrentSelectionVSAssembly) {
         scriptable = new SelectionContainerVSAScriptable(box);
      }
      else if(assembly instanceof SelectionListVSAssembly) {
         scriptable = new SelectionListVSAScriptable(box);
      }
      else if(assembly instanceof SelectionTreeVSAssembly) {
         scriptable = new SelectionTreeVSAScriptable(box);
      }
      else if(assembly instanceof CalendarVSAssembly) {
         scriptable = new CalendarVSAScriptable(box);
      }
      else if(assembly instanceof CrosstabVSAssembly) {
         scriptable = new CrosstabVSAScriptable(box);
      }
      else if(assembly instanceof TableVSAssembly) {
         scriptable = new TableVSAScriptable(box);
      }
      else if(assembly instanceof CalcTableVSAssembly) {
         scriptable = new CalcTableVSAScriptable(box);
      }
      else if(assembly instanceof TabVSAssembly) {
         scriptable = new TabVSAScriptable(box);
      }
      else if(assembly instanceof GroupContainerVSAssembly) {
         scriptable = new GroupContainerVSAScriptable(box);
      }
      else if(assembly instanceof ChartVSAssembly) {
         scriptable = new ChartVSAScriptable(box);
      }
      else if(assembly instanceof LineVSAssembly) {
         scriptable = new LineVSAScriptable(box);
      }
      else if(assembly instanceof OvalVSAssembly) {
         scriptable = new OvalVSAScriptable(box);
      }
      else if(assembly instanceof RectangleVSAssembly) {
         scriptable = new RectangleVSAScriptable(box);
      }
      else if(assembly instanceof TextInputVSAssembly) {
         scriptable = new TextInputVSAScriptable(box);
      }
      else if(assembly instanceof SubmitVSAssembly) {
         scriptable = new SubmitVSAScriptable(box);
      }
      else if(assembly instanceof Viewsheet) {
         scriptable = new ViewsheetVSAScriptable(box);
      }
      else {
         scriptable = new VSAScriptable(box);
      }

      scriptable.setParentScope(this);
      scriptable.setScope(this);

      if(assembly instanceof Viewsheet && !((Viewsheet) assembly).isEmbedded()) {
         scriptable.setAssembly(ViewsheetScope.VIEWSHEET_SCRIPTABLE);
      }
      else {
         scriptable.setAssembly(assembly.getName());
      }

      if(assembly instanceof CubeVSAssembly) {
         VSAScriptable proto = new CubeVSAScriptable(box);

         proto.setAssembly(assembly.getName());
         scriptable.setVSPrototype(proto);
      }

      return scriptable;
   }

   /**
    * Get the name of this scriptable.
    */
   @Override
   public String getClassName() {
      return "ViewsheetScope";
   }

   /**
    * Make a copy of this scope.
    */
   @Override
   public Object clone() {
      try {
         ViewsheetScope obj = (ViewsheetScope) super.clone();

         obj.vmap = Collections.synchronizedMap(new HashMap<>(vmap));
         obj.propmap = Collections.synchronizedMap(new HashMap<>(propmap));

         obj.addProperties();

         for(String name : obj.propmap.keySet()) {
            Object nval = obj.propmap.get(name);
            Object oval = propmap.get(name);

            if(nval instanceof VSAScriptable && oval instanceof VSAScriptable) {
               VSAScriptable nsobj = (VSAScriptable) nval;
               VSAScriptable osobj = (VSAScriptable) oval;

               nsobj.copyProperties(osobj);
            }
         }

         return obj;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone object", ex);
      }

      return null;
   }

   /**
    * Execute the script.
    * @param statement the specified script statement.
    */
   public Object execute(String statement, String assembly) throws Exception {
      return execute(statement, assembly, false);
   }

   /**
    * Execute the script.
    * @param statement the specified script statement.
    */
   public Object execute(String statement, String assembly, boolean skipForm) throws Exception {
      VSAScriptable scriptable = null;

      if(assembly != null) {
         scriptable = getVSAScriptable(assembly);

         if(scriptable == null) {
            LOG.debug("Assembly does not exist: " + assembly);
            return null;
         }

         if(Thread.currentThread() instanceof GroupedThread) {
            ((GroupedThread) Thread.currentThread())
               .addRecord(LogContext.ASSEMBLY, assembly);
         }
      }

      Boolean disableForm = null;

      if(scriptable instanceof TableDataVSAScriptable) {
         disableForm = ((TableDataVSAScriptable) scriptable).isDisableForm();
         ((TableDataVSAScriptable) scriptable).setDisableForm(skipForm);
      }

      Object result = execute(statement, scriptable);

      if(disableForm != null && scriptable instanceof TableDataVSAScriptable) {
         ((TableDataVSAScriptable) scriptable).setDisableForm(disableForm);
      }

      return result;
   }

   /**
    * Execute the script.
    * @param statement the specified script statement.
    * @param scriptable the specified scope.
    * @return the executed result.
    */
   public Object execute(String statement, VSAScriptable scriptable) throws Exception {
      return execute(statement, scriptable, true);
   }

   /**
    * Execute the script.
    * @param statement the specified script statement.
    * @param scriptable the specified scope.
    * @param resetCubeScriptable true if support reset cube scriptable, else not.
    *
    * @return the executed result.
    */
   public Object execute(String statement, VSAScriptable scriptable, boolean resetCubeScriptable)
      throws Exception
   {
      if(statement == null || statement.isEmpty()) {
         return null;
      }

      // per usa support's request, allow script to access user in viewsheet
      if(vtable != null && !vtable.contains("__principal__") && box.getUser() != null) {
         Principal principal = box.getUser();
         vtable.put("__principal__", principal);
         vtable.put("_USER_", XUtil.getUserName(principal));
         vtable.put("_ROLES_", XUtil.getUserRoleNames(principal));
         vtable.put("_GROUPS_", XUtil.getUserGroups(principal));
      }

      // always reset cube scriptable before compiling
      if(resetCubeScriptable && scriptable != null && oldScriptable != scriptable) {
         resetCubeScriptable(scriptable);
      }

      Object script = null;
      Consumer<Exception> handler = (Exception ex) -> {
         if(IGNORE_EXCEPTION.get() == Boolean.TRUE) {
            return;
         }

         String suggestion = senv.getSuggestion(ex, null, this);
         String msg = "Script compilation error: " + ex.getMessage() +
            (suggestion != null ? "\nTo fix: " + suggestion : "") +
            "\nScript failed:\n" + XUtil.numbering(statement);

         if(LOG.isDebugEnabled()) {
            LOG.debug(msg, ex);
         }
         else {
            LOG.warn(msg);
         }

         throw new ScriptException(msg, ex);
      };

      // execute the script object
      try {
         // compile the script
         script = scriptCache.get(statement, senv, handler);
         FormulaContext.setRestricted(true);
         Scriptable scope;

         if(scriptable != null) {
            scope = scriptable;
         }
         else {
            scope = this;
         }

         return senv.exec(script, scope, this, box.getViewsheet());
      }
      catch(Exception ex) {
         if(IGNORE_EXCEPTION.get() == Boolean.TRUE) {
            return null;
         }

         String suggestion = senv.getSuggestion(ex, null, this);
         String msg = "Script execution error: " + ex.getMessage() +
            (suggestion != null ? "\nTo fix: " + suggestion : "") +
            "\nScript failed:\n" + XUtil.numbering(statement);

         String assemblyName = scriptable != null ? scriptable.getAssembly() : "";
         String updateMessage = "Script execution error in assembly: " +
            assemblyName + (suggestion != null ? "\nTo fix: " + suggestion : "") +
            "\nScript failed:\n" + ex.getMessage();

         Exception updatedException = new ScriptException(updateMessage, ex);
         boolean cancelled = box.isCancelled(executeStart);

         if(cancelled || LOG.isDebugEnabled()) {
            LOG.debug(msg, ex);
         }
         else {
            // capture the exception which might be thrown multiple times during a viewsheet
            // execution to prevent it from being logged excessively.
            WorksheetEngine.ExceptionKey key =
               new WorksheetEngine.ExceptionKey(updatedException, box.getID());
            WorksheetEngine.ExceptionKey key2 = exceptionMap.get(key);

            if(key2 == null || key2.isTimeout()) {
               exceptionMap.put(key, key);
               LOG.warn(msg);
            }
         }

         if(cancelled) {
            return null;
         }

         throw updatedException;
      }
      finally {
         // if principal parameter is changed in script, make sure it's used in vtable
         if(vtable != null && box.getUser() != null) {
            vtable.copyParameters((XPrincipal) box.getUser());
         }

         FormulaContext.setRestricted(false);
      }
   }

   public void resetStartTime() {
      executeStart = System.currentTimeMillis();
   }

   private WeakHashMap<Object, WorksheetEngine.ExceptionKey> exceptionMap = new WeakHashMap<>();
   public static final ThreadLocal<Boolean> IGNORE_EXCEPTION = new ThreadLocal<>();
   private static final ScriptCache scriptCache = new ScriptCache(50, 60000);
   private ScriptEnv senv;
   private int mode;
   private ViewsheetSandbox box;
   private VSAScriptable oldScriptable;
   private VariableTable vtable;
   private Map<String, Object> vmap = Collections.synchronizedMap(new HashMap<>());
   private Map<String, Object> propmap = Collections.synchronizedMap(new HashMap<>());
   private Vector<String> oldAssemblies = new Vector<>();
   private DBScriptable db;
   private long executeStart = System.currentTimeMillis();

   private static final Logger LOG = LoggerFactory.getLogger(ViewsheetScope.class);
}
