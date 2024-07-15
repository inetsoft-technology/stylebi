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
package inetsoft.uql;

import inetsoft.sree.schedule.ScheduleParameterScope;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.SRPrincipal;
import inetsoft.uql.schema.*;
import inetsoft.uql.util.XUtil;
import inetsoft.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.io.Serializable;
import java.util.*;

/**
 * The VariableTable holds the variable values. It is passed to the
 * query engine during query execution. The variable table should
 * be populated for every variable in the query that require user
 * supplied values. The inetsoft.uql.builder.VariableEntry class can
 * be used to interactively prompt users for the values.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class VariableTable implements ContentObject, Serializable, Cloneable {
   /**
    * The name for the HttpServletRequest parameter. This parameter is
    * only available if the replet is running through the ServletRepository.
    * @deprecated use SERVICE_REQUEST.
    */
   @Deprecated
   public static final String HTTP_REQUEST = "__http_request__";
   /**
    * The name for the HttpServletResponse parameter. This parameter is
    * only available if the replet is running through the ServletRepository.
    * @deprecated use SERVICE_RESPONSE.
    */
   @Deprecated
   public static final String HTTP_RESPONSE = "__http_response__";

   /**
    * The name for the ServiceRequest parameter. This parameter is
    * only available if the replet is running through one of the web clients.
    * @see inetsoft.sree.web.ServiceRequest
    */
   public static final String SERVICE_REQUEST = "__service_request__";
   /**
    * The name for the ServiceResponse parameter. This parameter is
    * only available if the replet is running through one of the web clients.
    * @see inetsoft.sree.web.ServiceResponse
    */
   public static final String SERVICE_RESPONSE = "__service_response__";

   /**
    * Check if a variable is context variable, which does not require prompt,
    * for its value will be fetch from context.
    */
   public static boolean isContextVariable(String name) {
      return "_USER_".equals(name) || "_ROLES_".equals(name) ||
         "_GROUPS_".equals(name) || "__principal__".equals(name);
   }

   /**
    * Constructor.
    */
   public VariableTable() {
      super();
   }

   /**
    * Constructor.
    * @param runtimeValue determines whether to get variable's value
    */
   public VariableTable(boolean runtimeValue) {
      this();
      this.runtimeValue = runtimeValue;
   }

   /**
    * This method is called by the server to associate a table with
    * an user session. The session is used by execute any query
    * variables used by a query. Applications normally don't need to
    * set the session explicitly.
    */
   public void setSession(Object session) {
      this.session = session;
   }

   /**
    * Get the session associated with this table.
    */
   public Object getSession() {
      return session;
   }

   /**
    * Copy the parameters set in user principal.
    */
   public void copyParameters(XPrincipal user) {
      Enumeration params = user.getParameterNames();

      while(params.hasMoreElements()) {
         String name = (String) params.nextElement();

         // parameters in user should have a lower priority than variables
         // set in the onload or defined in report
         // if parameter in principal changed from the last time the values
         // were copied, copy again
         if(!contains(name) || copyParameterTS < user.getParameterTS(name)) {
            put(name, user.getParameter(name));
         }
      }

      copyParameterTS = System.currentTimeMillis();
   }

   /**
    * Set the base table for this variable table. The base table is
    * searched if a variable is not found in the current table.
    * @param table base variable table.
    */
   public void setBaseTable(VariableTable table) {
      if(!checkParent(table)) {
         return;
      }

      basetable = table;
   }

   /**
    * Check if the parent is valid.
    */
   private boolean checkParent(VariableTable table) {
      if(table == this) {
         return false;
      }

      while(table != null && table.getBaseTable() != null) {
         table = table.getBaseTable();

         if(table == this) {
            return false;
         }
      }

      return true;
   }

   /**
    * Get the base variable table.
    */
   public VariableTable getBaseTable() {
      return basetable;
   }

   /**
    * Add a table to the chain as the lowest priority table.
    */
   public void addBaseTable(VariableTable table) {
      if(basetable != null && basetable != table) {
         basetable.addBaseTable(table);
      }
      else {
         if(!checkParent(table)) {
            return;
         }

         basetable = table;
      }
   }

   /**
    * Remove a table from the base table chain.
    */
   public void removeBaseTable(VariableTable table) {
      if(basetable != null && basetable != table) {
         basetable.removeBaseTable(table);
      }
      else {
         basetable = null;
      }
   }

   /**
    * Remove tables matching the type from the base table chain.
    */
   public void removeBaseTable(Class<?> type) {
      if(basetable != null && !type.isAssignableFrom(basetable.getClass())) {
         basetable.removeBaseTable(type);
      }
      else {
         basetable = null;
      }
   }

   /**
    * Return the number of variables in the table.
    */
   public int size() {
      int cnt = vartable.size();

      if(basetable != null) {
         cnt += basetable.size();
      }

      return cnt;
   }

   /**
    * Add all variables.
    */
   public void addAll(VariableTable vars) throws Exception {
      if(vars == null) {
         return;
      }

      synchronized(vartable) {
         synchronized(vars.vartable) {
            Enumeration keys = vars.keys();

            while(keys.hasMoreElements()) {
               String key = (String) keys.nextElement();
               put(key, vars.get(key));
            }

            if(asIs != null && vars.asIs != null) {
               asIs.addAll(vars.asIs);
            }
         }
      }
   }

   /**
    * Set a variable value. The type of the value must match the type
    * declared in the corresponding UserVariable object.
    * @param name variable name.
    * @param value variable value.
    */
   public void put(String name, Object value) {
      // @by billh, for a multiple selection parameter, the value should be
      // an array, and the array only contains null should be taken as null,
      // then we are able to ignore the condition using this parameter in
      // both preprocess and post process
      if(value instanceof Object[]) {
         Object[] arr = (Object[]) value;
         boolean onlyNull = true;

         for(final Object val : arr) {
            if(val != null) {
               onlyNull = false;
               break;
            }
         }

         if(onlyNull) {
            value = null;
         }
      }

      synchronized(vartable) {
         vartable.put(name, value);
      }
   }

   /**
    * Remove all entries. The base table is not changed.
    */
   public void clear() {
      synchronized(vartable) {
         vartable.clear();
         asIs = null;
      }
   }

   /**
    * Check if the variable is inside this table.
    */
   public boolean contains(String name) {
      return vartable.containsKey(name) || basetable != null && basetable.contains(name);
   }

   /**
    * Get the value of a variable.
    * @param var UserVariable object.
    * @return variable value. Returns null if the variable is not defined.
    */
   public Object get(UserVariable var) throws Exception {
      if(var == null || var.getTypeNode() == null) {
         return null;
      }

      String varName = var.getName();

      if(varName == null) {
         return null;
      }

      String type = var.getTypeNode().getType();

      if(XSchema.DATE.equals(type) || XSchema.TIME_INSTANT.equals(type)) {
         Object val = get0(varName + "^_^" + type);

         if(val != null) {
            return val;
         }

         val = getBuiltinVariable(varName, type);

         if(val != null) {
            return val;
         }

         return get(varName);
      }
      else {
         return get(varName);
      }
   }

   /**
    * Get the value of a variable.
    * @param name variable name.
    * @return variable value. Returns null if the variable is not defined.
    */
   public Object get(String name) throws Exception {
      Object val = get0(name);

      if(val != null) {
         return val;
      }

      return getBuiltinVariable(name);
   }

   /**
    * Remove a variable from the table.
    * @param name variable name.
    */
   public void remove(String name) {
      synchronized(vartable) {
         vartable.remove(name);

         if(asIs != null) {
            asIs.remove(name);
         }
      }
   }

   /**
    * Get all variable names in the table.
    */
   public Enumeration<String> keys() {
      Set<String> keySet;

      synchronized(vartable) {
         // @by jasons, create copy of key set to prevent concurrent
         // modification exception on the result
         keySet = new HashSet<>(vartable.keySet());
      }

      Enumeration<String> tkeys = Collections.enumeration(keySet);
      final List<Enumeration<String>> names = basetable == null ?
         Collections.singletonList(tkeys) : Arrays.asList(tkeys, basetable.keys());

      return new Enumeration<String>() {
         @Override
         public boolean hasMoreElements() {
            if(idx < names.size()) {
               boolean more = names.get(idx).hasMoreElements();

               if(!more) {
                  idx++;
                  return hasMoreElements();
               }

               return true;
            }

            return false;
         }

         @Override
         public String nextElement() {
            return names.get(idx).nextElement();
         }

         private int idx = 0;
      };
   }

   /**
    * Get a variable table containing only the variables with the
    * specified prefix. The prefix is removed from the variable name
    * in the returned table. The prefix checking is ignored if the
    * variable name does not contain a dot '.'.
    */
   public VariableTable getSubset(String prefix) {
      VariableTable vars = new VariableTable();
      Iterator<String> keys = vartable.keySet().iterator();
      vars.session = session;

      // @by larryl, use get() in the loop instead of table.get() in case
      // get() is overriden by subclass

      try {
         while(keys.hasNext()) {
            String name = keys.next();

            if(name.startsWith(prefix)) {
               vars.put(name.substring(prefix.length()), get(name));
            }
            else if(name.indexOf('.') < 0) {
               // this should have lower priority than the variable with prefix
               if(!vars.contains(name)) {
                  vars.put(name, get(name));
                  vars.setAsIs(name, isAsIs(name));

                  if(isNotIgnoredNull(name)) {
                     vars.setNotIgnoredNull(name);
                  }
               }
            }
            // @by billh, if name contains dot, we could not ignore it.
            // In this way the returned variable table might contain some
            // useless key-value pairs, but it seems to do no harm
            else {
               if(!vars.contains(name)) {
                  vars.put(name, get(name));
                  vars.setAsIs(name, isAsIs(name));
               }
            }
         }
      }
      catch(Exception ex) {
         LOG.error("Failed to get subset variables", ex);
      }

      if(basetable != null) {
         vars.setBaseTable(basetable.getSubset(prefix));
      }

      return vars;
   }

   public void clearNullIgnored() {
      notIgnoreNull.clear();
   }

   public void setNotIgnoredNull(String name) {
      notIgnoreNull.add(name);
   }

   public boolean isNotIgnoredNull(String name) {
      return notIgnoreNull.contains(name);
   }

   /**
    * Set whether the value should be used as-is without further parsing
    * (e.g. splitting into array).
    */
   public void setAsIs(String name, boolean flag) {
      if(asIs == null && flag) {
         asIs = new HashSet<>();
      }

      if(flag) {
         asIs.add(name);
      }
      else if(asIs != null) {
         asIs.remove(name);
      }
   }

   /**
    * Check if the value should be used as=is.
    */
   public boolean isAsIs(String name) {
      return asIs != null && asIs.contains(name);
   }

   /**
    * Check if a variable is builtin.
    */
   public static boolean isBuiltinVariable(String name) {
      return builtins.contains(name);
   }

   /**
    * Process the builtin variables. Insert the default value to the variable
    * and turn of the prompting.
    */
   private Object getBuiltinVariable(String name) {
      return getBuiltinVariable(name, null);
   }

   /**
    * Process the builtin variables. Insert the default value to the variable
    * and turn of the prompting.
    */
   private Object getBuiltinVariable(String name, String type) {
      Object val = null;

      if(name.equals("_BEGINNING_OF_YEAR")) {
         val = ScheduleParameterScope.getBeginningOfThisYear();
      }
      else if(name.equals("_END_OF_YEAR")) {
         val = ScheduleParameterScope.getEndOfThisYear(type);
      }
      else if(name.equals("_BEGINNING_OF_QUARTER")) {
         val = ScheduleParameterScope.getBeginningOfThisQuarter();
      }
      else if(name.equals("_END_OF_QUARTER")) {
         val = ScheduleParameterScope.getEndOfThisQuarter(type);
      }
      else if(name.equals("_BEGINNING_OF_MONTH")) {
         val = ScheduleParameterScope.getBeginningOfThisMonth();
      }
      else if(name.equals("_END_OF_MONTH")) {
         val = ScheduleParameterScope.getEndOfThisMonth(type);
      }
      else if(name.equals("_BEGINNING_OF_WEEK")) {
         val = ScheduleParameterScope.getBeginningOfThisWeek();
      }
      else if(name.equals("_END_OF_WEEK")) {
         val = ScheduleParameterScope.getEndOfThisWeek(type);
      }
      else if(name.equals("_TODAY")) {
         val = ScheduleParameterScope.getToday();
      }

      if(val != null) {
         String key = type == null ? name : name + "^_^" + type;

         synchronized(vartable) {
            vartable.put(key, val);
         }
      }

      if(val == null && name.equals("_USER_")) {
         XPrincipal user = (XPrincipal) ThreadContext.getContextPrincipal();

         if(user != null) {
            String userId = user.getName();
            IdentityID userIdentity = IdentityID.getIdentityIDFromKey(userId);

            return userIdentity != null ? userIdentity.getName() : user.getName();
         }
      }
      else if(val == null && name.equals("_ROLES_")) {
         XPrincipal user = (XPrincipal) ThreadContext.getContextPrincipal();

         if(user != null) {
            return XUtil.getUserRoleNames(user);
         }
      }
      else if(val == null && name.equals("_GROUPS_")) {
         XPrincipal user = (XPrincipal) ThreadContext.getContextPrincipal();

         if(user != null) {
            return XUtil.getUserGroups(user);
         }
      }
      else if(val == null && name.equals("__principal__")) {
         return ThreadContext.getContextPrincipal();
      }

      return val;
   }

   /**
    * Clone.
    */
   @Override
   public VariableTable clone() {
      try {
         VariableTable vars = (VariableTable) super.clone();

         synchronized(vartable) {
            vars.vartable = new HashMap<>(vartable);
         }

         if(basetable != null) {
            vars.basetable = basetable.clone();
         }

         if(asIs != null) {
            vars.asIs = new HashSet<>(asIs);
         }

         return vars;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone VariableTable", ex);
      }

      return null;
   }

   /**
    * To string.
    */
   public String toString() {
      Enumeration key = keys();
      StringBuilder buf = new StringBuilder()
         .append("VariableTable").append(super.hashCode()).append("[");

      try {
         while(key.hasMoreElements()) {
            Object o = key.nextElement();
            buf.append("[").append(o.toString());
            Object val = get(o.toString());

            if(val instanceof Object[]) {
               val = Arrays.asList((Object[]) val);
            }

            if(val instanceof SRPrincipal) {
               val = ((SRPrincipal) val).toString(false);
            }

            buf.append(", ").append(val).append("]\n");
         }
      }
      catch(Exception e) {
         buf.append(e.toString());
      }

      buf.append("]");

      return buf.toString();
   }

   /**
    * Create a unique key to identify this variable table.
    */
   @Override
   public boolean printKey(PrintWriter writer) throws Exception {
      writer.print("VT[");
      Enumeration key = keys();
      int counter = 0;

      while(key.hasMoreElements()) {
         String vname = key.nextElement().toString();

         if(isIgnored(vname)) {
            continue;
         }

         if(counter > 0) {
            writer.print(",");
         }

         writer.print(vname);
         writer.print(":");

         Object obj = get(vname);

         if(obj instanceof Object[]) {
            Object[] arr = (Object[]) obj;

            for(int i = 0; i < arr.length; i++) {
               if(i > 0) {
                  writer.print(",");
               }

               writer.print(arr[i]);
            }

            writer.print("length:" + arr.length);
         }
         else if(obj == null) {
            writer.print(XConstants.CONDITION_REAL_NULL);
         }
         else {
            writer.print(obj);
         }

         counter++;
      }

      writer.print("]");
      return true;
   }

   /**
    * Check if equals another object in content.
    */
   @Override
   public boolean equalsContent(Object obj) {
      return equals(obj);
   }

   /**
    * Check if equals another object.
    * @param obj the specified object.
    * @return <tt>true</tt> if equals the object, <tt>false</tt> otherwise.
    */
   public boolean equals(Object obj) {
      if(this == obj) {
         return true;
      }

      if(!(obj instanceof VariableTable)) {
         return false;
      }

      VariableTable vars = (VariableTable) obj;
      return contains(vars.vartable, this.vartable) &&
         contains(this.vartable, vars.vartable) &&
         Tool.equals(basetable, vars.basetable) &&
         Objects.equals(asIs, vars.asIs);
   }

   /**
    * Check if the specified variable table contains all the variables in
    * another variable table.
    */
   private boolean contains(Map pvars, Map vars) {
      synchronized(vars) {
         for(Object o : vars.keySet()) {
            String key = (String) o;

            if(isIgnored(key)) {
               continue;
            }

            if(!Tool.equals(vars.get(key), pvars.get(key))) {
               return false;
            }
         }
      }

      return true;
   }

   /**
    * Get the hash code value.
    * @return the hash code value.
    */
   public int hashCode() {
      int hash = 0;

      for(String key : vartable.keySet()) {
         if(isIgnored(key)) {
            continue;
         }

         hash = hash ^ key.hashCode();
      }

      return hash;
   }

   /**
    * Get the value of a variable.
    * @param name variable name.
    * @return variable value. Returns null if the variable is not defined.
    */
   private Object get0(String name) throws Exception {
      Object val = vartable.get(name);

      // if null, check base table
      if(val == null && !vartable.containsKey(name) && basetable != null) {
         val = basetable.get0(name);
      }

      //if set it will get the runtime value of the variable
      //otherwise it will return the variable name
      if(runtimeValue && val != null) {
         if(val instanceof UserVariable) {
            XValueNode vnode = ((UserVariable) val).getValueNode();

            if(vnode != null) {
               val = vnode.getValue();

               if(val != null) {
                  put(name, val);
               }
            }
         }
      }

      return val;
   }

   /**
    * Check if a variable is ignored.
    * @param vname the specified variable name.
    */
   private boolean isIgnored(String vname) {
      if(ignored.contains(vname)) {
         return true;
      }

      // of the other predefined, the variables for max rows should not
      // be ignored, otherwise ignore them. It might be somewhat risky
      return !"__HINT_MAX_ROWS__".equals(vname) &&
         !"__HINT_INPUT_MAXROWS__".equals(vname) &&
         vname.startsWith("__") && vname.endsWith("__");
   }

   /**
    * Check if is internal parameter. An internal parameter should be ignored
    * when building a hyperlink or drill.
    */
   public boolean isInternalParameter(String name) {
      return "inetsoft.sree.web.SessionTimeoutListener".equals(name);
   }

   private static Set<String> builtins = new HashSet<>();
   private static final Set<String> ignored = new HashSet<>();

   static {
      builtins.add("_BEGINNING_OF_YEAR");
      builtins.add("_END_OF_YEAR");
      builtins.add("_BEGINNING_OF_QUARTER");
      builtins.add("_END_OF_QUARTER");
      builtins.add("_BEGINNING_OF_MONTH");
      builtins.add("_END_OF_MONTH");
      builtins.add("_BEGINNING_OF_WEEK");
      builtins.add("_END_OF_WEEK");
      builtins.add("_TODAY");
      builtins.add("_USER_");
      builtins.add("_ROLES_");
      builtins.add("_GROUPS_");

      ignored.add(HTTP_REQUEST);
      ignored.add(HTTP_RESPONSE);
      ignored.add(SERVICE_REQUEST);
      ignored.add(SERVICE_RESPONSE);
      ignored.add("__CACHED_DATA__"); // defined in XSession manager
      ignored.add("__HINT_TIMEOUT__"); // defined in XQuery
      ignored.add("__HINT_DEF_MAX_ROWS__"); // defined in XQuery
   }

   // hardcode serial id for 5.1 backward compatibility
   private static final long serialVersionUID = -590812805739315908L;
   private Object session = null; // session used for query variables
   private Map<String, Object> vartable = new HashMap<>();
   private Set<String> notIgnoreNull = new HashSet<>();
   private Set<String> asIs;
   private VariableTable basetable = null;
   private boolean runtimeValue = true;
   private long copyParameterTS = 0;

   private static final Logger LOG = LoggerFactory.getLogger(VariableTable.class);
}
