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
package inetsoft.uql.odata;

import inetsoft.uql.VariableTable;
import inetsoft.uql.schema.XTypeNode;
import inetsoft.uql.tabular.*;
import inetsoft.uql.util.XUtil;
import inetsoft.util.Tool;
import org.w3c.dom.*;

import java.io.PrintWriter;
import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;

@View(vertical=true, value={
      @View1("entity"),
      @View1("function"),
      @View1(value = "functionParameters", visibleMethod = "functionSelected"),
      @View1("expanded"),
      @View1("select"),
      @View1("filter"),
      @View1("expand"),
      @View1("orderBy"),
      @View1("top"),
      @View1("skip")
   })
public class ODataQuery extends TabularQuery implements PropertyVariableReplacer {
   public ODataQuery() {
      super(ODataDataSource.TYPE);
   }

   @Property(label="Entity")
   @PropertyEditor(tagsMethod="getEntityRefs")
   public String getEntity() {
      return entity;
   }

   public void setEntity(String entity) {
      if(entity != null && !entity.equals(this.entity)) {
         functions = null;
      }

      this.entity = entity;

      if(entitySets != null) {
         this.entityURI = entity.isEmpty() ? null : entitySets.get(entity).toString();
      }
   }

   public String getEntityURI() {
      return entityURI;
   }

   @Property(label="Function")
   @PropertyEditor(tagsMethod="getFunctions", dependsOn = "entity")
   public String getFunction() {
      if(function != null) {
         return function.getName();
      }

      return "";
   }

   public void setFunction(String functionName) {
      if(functionName == null) {
         return;
      }
      else if(functionName.equals("")) {
         function = null;
         return;
      }

      if(functions == null) {
         loadSchema();
      }

      if(functions.stream().noneMatch(f -> f.getName().equals(functionName))) {
         this.function = functions.stream().filter(f -> f.getName().equals("")).findFirst()
            .orElse(null);
      }
      else if(functions != null && !(this.function != null && this.function.getName().equals(functionName))) {
         this.ofunction = this.function;
         this.function = functions.stream().filter(f -> f.getName().equals(functionName)).findFirst()
            .orElse(null);
         parametersChanged = true;
      }
   }

   @Property(label = "Function Parameters")
   @PropertyEditor(editorProperties = @EditorProperty(name = "queryOnly", value = "true"), dependsOn = "function")
   @SuppressWarnings("unused")
   public HttpParameter[] getFunctionParameters() {
      if(function != null) {
         return function.getParameters();
      }

      return null;
   }

   @SuppressWarnings("unused")
   public void setFunctionParameters(HttpParameter[] functionParameters) {
      boolean applyChanges = true;

      if(this.ofunction != null && this.ofunction.getParameters() == functionParameters) {
         applyChanges = false;
      }

      if(parametersChanged) {
         if(this.function != null && this.function.getParameters() != null) {
            HttpParameter[] oldParams = this.function.getParameters();

            if(oldParams.length == functionParameters.length) {
               for(int i = 0; i < oldParams.length; i ++) {
                  if(!oldParams[i].getName().equals(functionParameters[i].getName())) {
                     applyChanges = false;
                  }
               }
            }
            else {
               applyChanges = false;
            }
         }
         else {
            applyChanges = false;
         }

         parametersChanged = false;
      }

      if(this.function != null && applyChanges) {
         this.function.setParameters(functionParameters);
      }
   }

   /**
    * Substitutes variables in the filter OData-aware (Bug #79001). A reference inside a
    * single-quoted OData string literal has its quotes doubled, so a value such as O'Brien
    * stays inside the literal. A reference outside quotes is embedded only when its value is a
    * plain OData primitive token (number, true/false, null, guid, date, date-time offset, time of
    * day or duration, or a comma-separated list of them); any other value, such as text with
    * spaces or operators, is refused with an error naming the variable, because embedded raw it
    * would become filter syntax. Dates are embedded in ISO form (java.sql.Date as yyyy-MM-dd,
    * other dates as UTC yyyy-MM-ddTHH:mm:ssZ). A {@code $(@name)} reference is the explicit
    * opt-in: it is embedded as is, without checking or quote doubling, like VarSQL's raw embed.
    * Nothing is URL-encoded here: the URI builder encodes the filter once when the request is
    * built. A variable with no value is left as written, in or out of quotes. Select, expand and
    * order by keep the default substitution.
    */
   @Override
   public String replaceVariables(String property, String value, VariableTable vars) {
      if(!"filter".equals(property) || value == null) {
         return null;
      }

      StringBuilder result = new StringBuilder();
      boolean inLiteral = false;
      int i = 0;

      while(i < value.length()) {
         char c = value.charAt(i);

         if(c == '\'') {
            inLiteral = !inLiteral;
         }
         else if(c == '$' && value.startsWith("$(", i)) {
            int end = value.indexOf(')', i);

            if(end > i + 2) {
               boolean raw = value.startsWith("$(@", i);
               String name = value.substring(i + (raw ? 3 : 2), end);
               String replaced = replaceVariable(name, vars);

               if(replaced != null) {
                  if(raw) {
                     result.append(replaced);
                  }
                  else if(inLiteral) {
                     result.append(replaced.replace("'", "''"));
                  }
                  else if(isPrimitiveList(replaced)) {
                     result.append(replaced);
                  }
                  else {
                     throw new IllegalArgumentException(
                        "Variable $(" + name + ") is used outside quotes in the OData filter, " +
                        "but its value is not a plain number, boolean, null, guid, date, " +
                        "date-time, time or duration. Put the variable inside single quotes, " +
                        "e.g. '$(" + name + ")', or use $(@" + name + ") to embed it as is.");
                  }

                  i = end + 1;
                  continue;
               }
            }
         }

         result.append(c);
         i++;
      }

      return result.toString();
   }

   private static String replaceVariable(String name, VariableTable vars) {
      try {
         Object obj = vars.get(name);

         if(obj instanceof java.sql.Date) {
            return obj.toString();
         }

         if(obj instanceof java.util.Date && !(obj instanceof java.sql.Time)) {
            return java.time.format.DateTimeFormatter.ISO_INSTANT.format(
               ((java.util.Date) obj).toInstant().truncatedTo(
                  java.time.temporal.ChronoUnit.SECONDS));
         }
      }
      catch(Exception ignore) {
      }

      String ref = "$(" + name + ")";
      String replaced = XUtil.replaceVariable(ref, vars, false);
      return ref.equals(replaced) ? null : replaced;
   }

   private static boolean isPrimitiveList(String text) {
      for(String token : text.split(",", -1)) {
         if(!PRIMITIVE.matcher(token).matches()) {
            return false;
         }
      }

      return true;
   }

   private static final java.util.regex.Pattern PRIMITIVE = java.util.regex.Pattern.compile(
      "-?\\d+(\\.\\d+)?([eE][+-]?\\d+)?|true|false|null|" +
      "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}|" +
      "\\d{4}-\\d{2}-\\d{2}(T\\d{2}:\\d{2}(:\\d{2}(\\.\\d+)?)?(Z|[+-]\\d{2}:\\d{2})?)?|" +
      "\\d{2}:\\d{2}(:\\d{2}(\\.\\d+)?)?|" +
      "-?P(?=\\d|T\\d)(\\d+D)?(T(\\d+H)?(\\d+M)?(\\d+(\\.\\d+)?S)?)?");

   @Property(label="Select")
   public String getSelect() {
      return select;
   }

   public void setSelect(String select) {
      this.select = select;
   }

   @Property(label="Filter")
   public String getFilter() {
      return filter;
   }

   public void setFilter(String filter) {
      this.filter = filter;
   }

   @Property(label="Expand")
   public String getExpand() {
      return expand;
   }

   public void setExpand(String expand) {
      this.expand = expand;
   }

   @Property(label="Order By")
   public String getOrderBy() {
      return orderBy;
   }

   public void setOrderBy(String orderBy) {
      this.orderBy = orderBy;
   }

   @Property(label="Skip", min=0)
   public int getSkip() {
      return skip;
   }

   public void setSkip(int skip) {
      this.skip = skip;
   }

   @Property(label="Top", min=0)
   public int getTop() {
      return top;
   }

   public void setTop(int top) {
      this.top = top;
   }

   @Property(label="Expand Arrays")
   public boolean isExpanded() {
      return expanded;
   }

   public void setExpanded(boolean flag) {
      this.expanded = flag;
   }


   @Override
   protected void writeAttributes(PrintWriter writer) {
      super.writeAttributes(writer);

      writer.print(" expanded=\"" + expanded + "\"");
      writer.print(" top=\"" + top + "\"");
      writer.print(" skip=\"" + skip + "\"");
   }

   @Override
   public void writeContents(PrintWriter writer) {
      super.writeContents(writer);

      if(entity != null) {
         writer.println("<entity><![CDATA[" + entity + "]]></entity>");
      }

      if(function != null) {
         function.writeXML(writer);
      }

      if(entityURI != null) {
         writer.println("<entityURI><![CDATA[" + entityURI + "]]></entityURI>");
      }

      if(select != null) {
         writer.println("<select><![CDATA[" + select + "]]></select>");
      }

      if(expand != null) {
         writer.println("<expand><![CDATA[" + expand + "]]></expand>");
      }

      if(filter != null) {
         writer.println("<filter><![CDATA[" + filter + "]]></filter>");
      }

      if(orderBy != null) {
         writer.println("<orderBy><![CDATA[" + orderBy + "]]></orderBy>");
      }
   }

   @Override
   protected void parseAttributes(Element tag) throws Exception {
      super.parseAttributes(tag);
      String prop;

      expanded = "true".equals(Tool.getAttribute(tag, "expanded"));

      if((prop = Tool.getAttribute(tag, "top")) != null) {
         top = Integer.parseInt(prop);
      }

      if((prop = Tool.getAttribute(tag, "skip")) != null) {
         skip = Integer.parseInt(prop);
      }
   }

   @Override
   public void parseContents(Element root) throws Exception {
      super.parseContents(root);
      Element node = Tool.getChildNodeByTagName(root, "entity");
      entity = Tool.getValue(node);
      node = Tool.getChildNodeByTagName(root, "function");

      if(node != null) {
         function = new ODataFunction("", ODataFunction.ReturnType.ENTITY);
         function.parseXML(node);
      }

      node = Tool.getChildNodeByTagName(root, "entityURI");
      entityURI = Tool.getValue(node);
      node = Tool.getChildNodeByTagName(root, "select");
      select = Tool.getValue(node);
      node = Tool.getChildNodeByTagName(root, "expand");
      expand = Tool.getValue(node);
      node = Tool.getChildNodeByTagName(root, "filter");
      filter = Tool.getValue(node);
      node = Tool.getChildNodeByTagName(root, "orderBy");
      orderBy = Tool.getValue(node);
   }

   /**
    * Get a list of all entities.
    */
   public String[][] getEntityRefs() {
      entitySets = ODataRuntime.getEntities((ODataDataSource) getDataSource(), true);
      ArrayList<String> entityList = new ArrayList<>();
      entityList.addAll(entitySets.keySet());

      final String[] entities = entityList.toArray(new String[0]);
      Arrays.sort(entities);

      final String[][] entityLabels = new String[entities.length + 1][2];
      entityLabels[0] = new String[]{" ", ""};

      for(int i = 0; i < entities.length; i ++) {
         entityLabels[i + 1] = new String[]{entities[i], entities[i]};
      }

      return entityLabels;
   }

   public String[][] getFunctions() {
      if(functions == null) {
         loadSchema();
      }

      List<String> functionNames = new ArrayList<>();
      functionNames.addAll(functions.stream().map(ODataFunction::getName).collect(Collectors.toList()));
      String[] names = functionNames.toArray(new String[0]);
      String[][] functionLabels = new String[names.length + 1][2];
      functionLabels[0] = new String[]{" ", ""};

      for(int i = 0; i < names.length; i ++) {
         functionLabels[i + 1] = new String[]{names[i], names[i]};
      }

      return functionLabels;
   }

   public boolean functionSelected() {
      return this.function != null;
   }

   public boolean isParameterString(int index) {
      return function.getParameterTypes()[index];
   }

   public boolean isBoundCollection() {
      return this.function.isBoundCollection();
   }

   public String getBoundEntityValue() {
      if(function != null && function.getParameters() != null && function.getParameters().length > 0) {
         return function.getParameters()[0].getValue();
      }

      return null;
   }

   public ODataFunction.ReturnType getReturnType() {
      return function.getReturnType();
   }

   public String getNameSpace() {
      if(nameSpace == null) {
         Node schema = ODataRuntime.getSchemaNode((ODataDataSource) getDataSource());
         nameSpace = Tool.getAttribute((Element) schema, "Namespace");
      }

      return nameSpace;
   }

   @Override
   public XTypeNode[] getOutputColumns() {
      XTypeNode[] columns = super.getOutputColumns();

      if((columns == null || columns.length == 0) && getDataSource() != null &&
         entity != null && !entity.isEmpty())
      {
         columns = fetchColumns();

         if(columns.length > 0) {
            setOutputColumns(columns);
         }
      }

      return columns;
   }

   private XTypeNode[] fetchColumns() {
      ODataDataSource ds = (ODataDataSource) getDataSource();
      Node schema = ODataRuntime.getSchemaNode(ds);
      String entityType = ODataRuntime.getEntityType(schema, entity);
      entityType = entityType == null ? "" : entityType;
      return ODataRuntime.getProperties(schema, entityType);
   }

   private void loadSchema() {
      Node schema = ODataRuntime.getSchemaNode((ODataDataSource) getDataSource());

      if(schema == null) {
         nameSpace = null;
         functions = new ArrayList<>();
         return;
      }

      nameSpace = Tool.getAttribute((Element) schema, "Namespace");
      String entityType = ODataRuntime.getEntityType(schema, entity);
      entityType = entityType == null ? "" : entityType;
      this.functions = ODataRuntime.getFunctions(schema, entityType);
   }

   private Map<String, URI> entitySets;
   private String entity;
   private String entityURI;
   private String select;
   private String filter;
   private String expand;
   private String orderBy;
   private int skip = 0;
   private int top = 0;
   private List<ODataFunction> functions;
   private ODataFunction function;
   private ODataFunction ofunction;
   private boolean expanded = false;
   private boolean parametersChanged = false;
   private String nameSpace;
}
