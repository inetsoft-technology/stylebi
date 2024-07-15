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
package inetsoft.uql.tabular;

import inetsoft.sree.SreeEnv;
import inetsoft.uql.*;
import inetsoft.uql.jdbc.util.VarSQL;
import inetsoft.uql.schema.UserVariable;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.tabular.oauth.Tokens;
import inetsoft.uql.util.Config;
import inetsoft.uql.util.XUtil;
import inetsoft.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.beans.*;
import java.io.File;
import java.lang.reflect.*;
import java.security.Principal;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Utility methods.
 *
 * @version 12.0, 11/15/2013
 * @author InetSoft Technology Corp
 */
public class TabularUtil {
   /**
    * Find all variables embedded in the properties of the bean.
    */
   public static List<UserVariable> findVariables(Object bean) {
      List<PropertyMeta> props = findProperties(bean.getClass());
      List<UserVariable> vars = new ArrayList<>();

      for(PropertyMeta prop : props) {
         if(prop.isAnnotated()) {
            Object val = prop.getValue(bean);

            if(val != null) {
               if(val instanceof String) {
                  if(prop.getProperty().sql()) {
                     for(String line : ((String) val).split("\n")) {
                        if(!line.trim().startsWith("--")) {
                           vars.addAll(XUtil.findVariables(line));
                        }
                     }
                  }
                  else {
                     boolean checkEnvVariables = prop.getProperty().checkEnvVariables();
                     vars.addAll(XUtil.findVariables((String) val).stream()
                                    .filter((var) -> {
                                       if(checkEnvVariables) {
                                          return SreeEnv.getProperty(var.getName()) == null &&
                                             System.getProperty(var.getName()) == null;
                                       }
                                       else {
                                          return true;
                                       }
                                    })
                                    .collect(Collectors.toList()));
                  }
               }
               else if(val instanceof QueryParameter) {
                  findVariables(vars, (QueryParameter) val);
               }
               else if(val instanceof QueryParameter[]) {
                  findVariables(vars, (QueryParameter[]) val);
               }
               else if(val instanceof HttpParameter) {
                  findVariables(vars, (HttpParameter) val);
               }
               else if(val instanceof HttpParameter[]) {
                  findVariables(vars, (HttpParameter[]) val);
               }
               else if(val instanceof RestParameters) {
                  RestParameters params = (RestParameters) val;
                  params.getParameters().stream().forEach(param -> findVariables(vars, param));
               }
               else if(Collection.class.isAssignableFrom(val.getClass())) {
                  Collection collection = (Collection) val;

                  for(Object obj : collection) {
                     if(obj instanceof QueryParameter) {
                        findVariables(vars, (QueryParameter) obj);
                     }
                     else if(obj instanceof HttpParameter) {
                        findVariables(vars, (HttpParameter) obj);
                     }
                  }
               }
            }
         }
      }

      return vars;
   }

   private static void findVariables(List<UserVariable> vars,
      QueryParameter ... parameters)
   {
      for(QueryParameter parameter : parameters) {
         if(parameter.isVariable()) {
            String varName = (String) parameter.getValue();
            varName = varName.substring(2, varName.length() - 1);
            UserVariable var = new UserVariable(varName);
            var.setTypeNode(XSchema.createPrimitiveType(
               parameter.getType().type()));
            vars.add(var);
         }
      }
   }

   private static void findVariables(List<UserVariable> vars, HttpParameter... parameters) {
      for(final HttpParameter parameter : parameters) {
         if(parameter != null) {
            final String name = parameter.getName();
            final String value = parameter.getValue();

            vars.addAll(XUtil.findVariables(name));
            vars.addAll(XUtil.findVariables(value));
         }
      }
   }

   private static void findVariables(List<UserVariable> vars, RestParameter... parameters) {
      for(final RestParameter parameter : parameters) {
         if(parameter != null) {
            final String name = parameter.getName();
            final String value = parameter.getValue();

            vars.addAll(XUtil.findVariables(name));
            vars.addAll(XUtil.findVariables(value));
         }
      }
   }

   /**
    * Replace variables in query using javascript date (timestamp) format.
    * @param val
    * @param vars
    * @return
    */
   private static String replaceJSDateVariables(String val, VariableTable vars) {
      TimeZone tz = TimeZone.getTimeZone("UTC");
      DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
      df.setTimeZone(tz);

      try {
         Enumeration enumeration = vars.keys();

         while(enumeration.hasMoreElements()) {
            Object key = enumeration.nextElement();

            if(key instanceof String) {
               String keyStr = (String) key;
               Object varValue = vars.get(keyStr);

               if(varValue instanceof java.util.Date) {
                  Timestamp ts = (varValue instanceof Timestamp) ? (Timestamp) varValue
                     : new Timestamp(((java.util.Date) varValue).getTime());
                  String isoDate = df.format(ts);
                  String isoDateEmb = "\"" + isoDate + "\"";
                  String pattern = Pattern.quote("$(@" + keyStr + ")");
                  String embPattern = Pattern.quote("$(" + keyStr + ")");
                  val = val.replaceAll(pattern, isoDate);
                  val = val.replaceAll(embPattern, isoDateEmb);
               }
            }
         }

         return val;
      }
      catch(Exception e) {
         LOG.error("Unable to parse mongo date format {}", e.getMessage());
      }

      return "";
   }

   /**
    * Replace variables embedded in the properties of the bean.
    */
   public static void replaceVariables(Object bean, VariableTable vars) {
      List<PropertyMeta> props = findProperties(bean.getClass());

      for(PropertyMeta prop : props) {
         if(prop.isAnnotated()) {
            Object val = prop.getValue(bean);

            if(val != null) {
               if(val instanceof String) {
                  if(prop.getProperty().sql()) {

                     if(prop.getProperty().jsDateFormat()) {
                        val = replaceJSDateVariables((String) val, vars);
                     }

                     VarSQL varsql = new VarSQL();
                     varsql.setSQLType(VarSQL.SQLType.STRING);
                     val = varsql.replaceVariables((String) val, vars);
                  }
                  else {
                     if(prop.getProperty().checkEnvVariables()) {
                        val = XUtil.replaceEnvVariable((String) val);
                     }

                     val = XUtil.replaceVariable((String) val, vars);
                  }

                  prop.setValue(bean, val);
               }
               else if(val instanceof QueryParameter) {
                  replaceVariables(vars, (QueryParameter) val);
               }
               else if(val instanceof QueryParameter[]) {
                  replaceVariables(vars, (QueryParameter[]) val);
               }
               else if(val instanceof HttpParameter) {
                  replaceVariables(vars, (HttpParameter) val);
               }
               else if(val instanceof HttpParameter[]) {
                  replaceVariables(vars, (HttpParameter[]) val);
               }
               else if(val instanceof RestParameters) {
                  RestParameters params = (RestParameters) val;
                  final List<RestParameter> parameters = params.getParameters();
                  parameters.forEach(param -> replaceVariables(vars, param));
                  params.setParameters(parameters);
                  prop.setValue(bean, val);
               }
               else if(Collection.class.isAssignableFrom(val.getClass())) {
                  Collection collection = (Collection) val;

                  for(Object obj : collection) {
                     if(obj instanceof QueryParameter) {
                        replaceVariables(vars, (QueryParameter) obj);
                     }
                     else if(obj instanceof HttpParameter) {
                        replaceVariables(vars, (HttpParameter) obj);
                     }
                  }
               }
            }
         }
      }
   }

   private static void replaceVariables(VariableTable vars,
      QueryParameter ... parameters)
   {
      for(QueryParameter parameter : parameters) {
         if(parameter.isVariable()) {
            String varName = (String) parameter.getValue();
            varName = varName.substring(2, varName.length() - 1);

            try {
               Object varValue = vars.get(varName);
               parameter.setValue(varValue);
               parameter.setVariable(false);
            }
            catch(Exception e) {
            }
         }
      }
   }

   private static void replaceVariables(VariableTable vars, HttpParameter... parameters) {
      for(final HttpParameter parameter : parameters) {
         final String name = parameter.getName();
         final String value = parameter.getValue();

         final String replacedName = XUtil.replaceVariable(name, vars);
         final String replacedValue = XUtil.replaceVariable(value, vars);

         parameter.setName(replacedName);
         parameter.setValue(replacedValue);
      }
   }

   private static void replaceVariables(VariableTable vars, RestParameter... parameters) {
      for(final RestParameter parameter : parameters) {
         final String name = parameter.getName();
         final String value = parameter.getValue();

         final String replacedName = XUtil.replaceVariable(name, vars);
         final String replacedValue = XUtil.replaceVariable(value, vars);

         parameter.setName(replacedName);
         parameter.setValue(replacedValue);
      }
   }

   /**
    * Find all properties in the class.
    */
   public static List<PropertyMeta> findProperties(Class cls) {
      List<PropertyMeta> props = new ArrayList<>();

      try {
         BeanInfo binfo = Introspector.getBeanInfo(cls);
         PropertyDescriptor[] descs = binfo.getPropertyDescriptors();

         for(PropertyDescriptor desc : descs) {
            PropertyMeta prop = new PropertyMeta(desc);

            if(prop.isAnnotated()) {
               props.add(prop);
            }
         }
      }
      catch(Exception ex) {
         LOG.error("Failed to introspect bean: {}", cls, ex);
      }

      return props;
   }

   /**
    * Gets a property map from the bean class
    */
   public static HashMap<String, PropertyMeta> getPropertyMap(Class cls) {
      List<PropertyMeta> props = TabularUtil.findProperties(cls);
      HashMap<String, PropertyMeta> pmap = new HashMap<>();

      for(PropertyMeta prop : props) {
         pmap.put(prop.getName(), prop);
      }

      return pmap;
   }

   /**
    * Gets editor's type from the given tabular view
    */
   public static TabularEditor.Type getEditorType(TabularEditor tEditor) {
      if(tEditor.getCustomEditor().length() > 0) {
         return TabularEditor.Type.CUSTOM;
      }

      TabularEditor.Type type = getEditorTypeFromClassName(tEditor.getPropertyType());

      if(type != TabularEditor.Type.LIST) {
         if(tEditor.isAutocomplete()) {
            return TabularEditor.Type.AUTOCOMPLETE;
         }
         else if(tEditor.getTags().length > 0 ||
            tEditor.getTagsMethod().length() > 0)
         {
            return TabularEditor.Type.TAGS;
         }
      }

      return type;
   }

   /**
    * Gets editor's subtype from the given tabular view
    */
   public static TabularEditor.Type getEditorSubtype(TabularEditor tEditor, String className) {
      if(tEditor.isAutocomplete()) {
         return TabularEditor.Type.AUTOCOMPLETE;
      }
      else if(tEditor.getTags().length > 0 ||
         tEditor.getTagsMethod().length() > 0)
      {
         return TabularEditor.Type.TAGS;
      }

      return getEditorTypeFromClassName(className);
   }

   /**
    * Gets editor's type from the given class name
    */
   public static TabularEditor.Type getEditorTypeFromClassName(String className)
   {
      Class cls;

      try {
         cls = Class.forName(className);
      }
      catch(ClassNotFoundException e) {
         return TabularEditor.Type.TEXT;
      }

      TabularEditor.Type result;

      if(cls == boolean.class || cls == Boolean.class) {
         result = TabularEditor.Type.BOOLEAN;
      }
      else if(cls == int.class || cls == Integer.class) {
         result = TabularEditor.Type.INT;
      }
      else if(cls == long.class || cls == Long.class) {
         result = TabularEditor.Type.LONG;
      }
      else if(cls == short.class || cls == Short.class) {
         result = TabularEditor.Type.SHORT;
      }
      else if(cls == byte.class || cls == Byte.class) {
         result = TabularEditor.Type.BYTE;
      }
      else if(cls == float.class || cls == Float.class) {
         result = TabularEditor.Type.FLOAT;
      }
      else if(cls == double.class || cls == Double.class ||
         Number.class.isAssignableFrom(cls)) {
         result = TabularEditor.Type.DOUBLE;
      }
      else if(Date.class.isAssignableFrom(cls)) {
         result = TabularEditor.Type.DATE;
      }
      else if(File.class.isAssignableFrom(cls)) {
         result = TabularEditor.Type.FILE;
      }
      else if(cls.isArray() &&
         ColumnDefinition.class.isAssignableFrom(cls.getComponentType()))
      {
         result = TabularEditor.Type.COLUMN;
      }
      else if((cls.isArray() || Collection.class.isAssignableFrom(cls))) {
         result = TabularEditor.Type.LIST;
      }
      else if(QueryParameter.class.isAssignableFrom(cls)) {
         result = TabularEditor.Type.PARAMETER;
      }
      else if(HttpParameter.class.isAssignableFrom(cls)) {
         result = TabularEditor.Type.HTTP_PARAMETER;
      }
      else if(RestParameters.class.isAssignableFrom(cls)) {
         result = TabularEditor.Type.REST_PARAMETERS;
      }
      else {
         result = TabularEditor.Type.TEXT;
      }

      return result;
   }

   /**
    * Apply the values in tabularView to the bean, and then re-initialize the tabularView
    * values from the bean.
    */
   public static void refreshView(TabularView tabularView, Object bean, Collection<String> records, Principal principal) {
      HashMap<String, PropertyMeta> pmap = getPropertyMap(bean.getClass());
      setValuesToBean(tabularView.getViews(), bean, pmap);
      callButtonMethods(tabularView.getViews(), bean);
      callEditorMethods(tabularView.getViews(), bean, records, principal);
      callViewMethods(tabularView.getViews(), bean);
      setValuesToView(tabularView.getViews(), bean, pmap);
   }

   public static void refreshView(TabularView tabularView, Object bean) {
      refreshView(tabularView, bean, Collections.EMPTY_LIST, null);
   }

   public static void callViewMethods(TabularView[] views, Object bean) {
      for(TabularView tView : views) {
         callVisibleMethod(tView, bean);
         TabularButton button = tView.getButton();

         if(button != null && !button.getEnabledMethod().isEmpty()) {
            Boolean enabled = (Boolean) callMethod(bean, button.getEnabledMethod());
            button.setEnabled(enabled != null && enabled);
         }

         callViewMethods(tView.getViews(), bean);
      }
   }

   private static void callVisibleMethod(TabularView tView, Object bean) {
      String visibleMethod = tView.getVisibleMethod();

      if(visibleMethod != null && !visibleMethod.isEmpty()) {
         try {
            TabularEditor tabularEditor = tView.getEditor();
            final Component component = tView.getComponent();
            TabularButton button = tView.getButton();
            Boolean visible = (Boolean) callMethod(bean, visibleMethod);
            tView.setVisible(visible != null && visible);

            if(tabularEditor != null) {
               tabularEditor.setVisible(visible != null && visible);
               tabularEditor.setVisibleMethod(visibleMethod);
            }
            else if(component != null) {
               component.setVisible(visible != null && visible);
            }

            if(button != null) {
               button.setVisible(visible != null && visible);
            }
         }
         catch(Exception e) {
            LOG.error(e.getMessage());
         }
      }
   }

   private static Object callMethod(Object bean, String methodName) {
      try {
         Class<?> clazz = bean.getClass();
         Method method = clazz.getMethod(methodName);
         return method.invoke(bean);
      }
      catch(Exception e) {
         LOG.error(e.getMessage());
      }

      return null;
   }

   /**
    * Sets values from the views to the bean
    */
   public static void setValuesToBean(TabularView[] views, Object bean,
      Map<String, PropertyMeta> pmap)
   {
      for(TabularView tView : views) {
         if(tView.getEditor() != null) {
            String propName = tView.getValue();
            PropertyMeta propertyMeta = pmap.get(propName);

            //if(propertyMeta != null && tView.isVisible()) {
            // for lookup endpoints, the array in query is not deleted when the item on
            // screen is removed. need to clear out the array items when the dialog is
            // submitted. restricted the change to tags to avoid impacting others.
            boolean apply = tView.isVisible() ||
               tView.getEditor() != null && tView.getEditor().getType() == TabularEditor.Type.TAGS;

            if(propertyMeta != null && apply) {
               Object value = tView.getEditor().getValue();
               propertyMeta.setValue(bean, value);
            }
         }

         setValuesToBean(tView.getViews(), bean, pmap);
      }
   }

   /**
    * Sets values from the bean to the views
    */
   public static void setValuesToView(TabularView[] views, Object bean,
                                      Map<String, PropertyMeta> pmap)
   {
      for(TabularView tView : views) {
         if(tView.getEditor() != null) {
            String propName = tView.getValue();
            PropertyMeta propertyMeta = pmap.get(propName);

            if(propertyMeta != null) {
               Object value = propertyMeta.getValue(bean);
               tView.getEditor().setValue(value);
            }
         }

         setValuesToView(tView.getViews(), bean, pmap);
      }
   }

   /**
    * Calls button methods
    */
   public static void callButtonMethods(TabularView[] views, Object bean) {
      for(TabularView tView : views) {
         TabularButton tButton = tView.getButton();

         if(tButton != null) {
            String methodName = tButton.getMethod();

            if(methodName != null && methodName.length() > 0) {
               // if the button type is URL then call the method regardless of
               // whether the button was clicked in order to set the url
               if(tButton.getType() == ButtonType.URL) {
                  try {
                     Class<?> clazz = bean.getClass();
                     Method method = clazz.getMethod(methodName);
                     Object value = method.invoke(bean);
                     tButton.setUrl(String.valueOf(value));

                     if(tButton.isClicked()) {
                        tButton.setClicked(false);
                        LOG.warn("Cannot open URL in current environment: " + tButton.getUrl());
                     }
                  }
                  catch(Throwable ex) {
                     LOG.error("Url method failed: {}", methodName, ex);
                  }
               }
               else if(tButton.getType() == ButtonType.OAUTH) {
                  if(tButton.isClicked()) {
                     tButton.setClicked(false);
                     LOG.warn("Cannot open OAuth URL in current environment");
                  }
               }
               else if(tButton.isClicked()) {
                  tButton.setClicked(false);

                  try {
                     Class<?> clazz = bean.getClass();
                     Method method = clazz.getMethod(methodName, String.class);
                     method.invoke(bean, sessionId.get());
                  }
                  catch(Throwable ex) {
                     LOG.error("Button method failed: {}", methodName, ex);
                  }
               }
            }
         }

         callButtonMethods(tView.getViews(), bean);
      }
   }

   public static Map<String, String> getOAuthParameters(String userProperty,
                                                        String passwordProperty,
                                                        String clientIdProperty,
                                                        String clientSecretProperty,
                                                        String scopeProperty,
                                                        String authorizationUriProperty,
                                                        String tokenUriProperty,
                                                        String flagsProperty, Object bean)
   {
      Map<String, PropertyMeta> properties = getPropertyMap(bean.getClass());
      String user = getOAuthParameter(userProperty, properties, bean);
      String password = getOAuthParameter(passwordProperty, properties, bean);
      String clientId = getOAuthParameter(clientIdProperty, properties, bean);
      String clientSecret = getOAuthParameter(clientSecretProperty, properties, bean);
      String scope = getOAuthParameter(scopeProperty, properties, bean);
      String authorizationUri = getOAuthParameter(authorizationUriProperty, properties, bean);
      String tokenUri = getOAuthParameter(tokenUriProperty, properties, bean);
      String flags = getOAuthParameter(flagsProperty, properties, bean);

      if(clientId != null && !clientId.isEmpty() &&
         clientSecret != null && !clientSecret.isEmpty() &&
         authorizationUri != null && !authorizationUri.isEmpty() &&
         tokenUri != null && !tokenUri.isEmpty())
      {
         Map<String, String> result = new HashMap<>();
         result.put("clientId", clientId);
         result.put("clientSecret", clientSecret);
         result.put("scope", scope);
         result.put("authorizationUri", authorizationUri);
         result.put("tokenUri", tokenUri);
         result.put("flags", flags);
         return result;
      }
      else if(user != null && !user.isEmpty() && password != null && !password.isEmpty() &&
         tokenUri != null && !tokenUri.isEmpty())
      {
         Map<String, String> result = new HashMap<>();
         result.put("user", user);
         result.put("password", password);
         result.put("tokenUri", tokenUri);
         // optional fields
         result.put("clientId", clientId);
         result.put("clientSecret", clientSecret);
         result.put("scope", scope);
         return result;
      }

      return null;
   }

   public static void setOAuthTokens(Tokens tokens, Object bean,
                                     String methodName, TabularView view)
   {
      try {
         Method method = bean.getClass().getMethod(methodName, Tokens.class);
         method.invoke(bean, tokens);
      }
      catch(Exception e) {
         LOG.error("Failed to set authorization tokens", e);
      }

      Map<String, PropertyMeta> metaProperties = getPropertyMap(bean.getClass());
      callButtonMethods(view.getViews(), bean);
      callEditorMethods(view.getViews(), bean);
      callViewMethods(view.getViews(), bean);
      setValuesToView(view.getViews(), bean, metaProperties);
   }

   private static String getOAuthParameter(String propertyName,
                                           Map<String, PropertyMeta> properties, Object bean)
   {
      if(propertyName != null && !propertyName.isEmpty()) {
         PropertyMeta property = properties.get(propertyName);

         if(property != null) {
            return (String) property.getValue(bean);
         }
         else {
            try {
               BeanInfo beanInfo = Introspector.getBeanInfo(bean.getClass());

               for(PropertyDescriptor prop : beanInfo.getPropertyDescriptors()) {
                  if(prop.getName().equals(propertyName) && prop.getReadMethod() != null) {
                     return (String) prop.getReadMethod().invoke(bean);
                  }
               }
            }
            catch(Exception e) {
               LOG.warn("Failed to get OAuth parameter", e);
            }
         }
      }

      return null;
   }

   /**
    * Calls editor methods
    */
   public static void callEditorMethods(TabularView[] views, Object bean,
                                        Collection<String> records, Principal principal)
   {
      List<Thread> threads = new ArrayList<>();
      List<UserMessage> msgs = new ArrayList<>();
      Map<String, CountDownLatch> latchMap = new HashMap<>();

      for(TabularView tView : views) {
         TabularEditor tEditor = tView.getEditor();

         if(tEditor != null) {
            String tagsMethod = tEditor.getTagsMethod();

            // tags method often needs to query external source and can take a while
            if(tagsMethod != null && tagsMethod.length() > 0) {
               GroupedThread thread = new GroupedThread(principal) {
                  @Override
                  public void doRun() {
                     try {
                        records.forEach(this::addRecord);
                        ThreadContext.setContextPrincipal(principal);
                        String[] dependsOn = tEditor.getDependsOn();

                        // await for the threads on which this property depends on to finish first
                        if(dependsOn != null) {
                           for(String dependsOnProperty : dependsOn) {
                              CountDownLatch latch = latchMap.get(dependsOnProperty);

                              if(latch != null) {
                                 latch.await();
                              }
                           }
                        }

                        Class<?> clazz = bean.getClass();
                        Method method = clazz.getMethod(tagsMethod);
                        Object value = method.invoke(bean);
                        String[] tags = null;
                        String[] labels = null;

                        if(value instanceof String[]) {
                           tags = labels = (String[]) value;
                        }
                        else if(value instanceof String[][]) {
                           String[][] pairs = (String[][]) value;

                           tags = new String[pairs.length];
                           labels = new String[pairs.length];

                           for(int i = 0; i < pairs.length; i++) {
                              labels[i] = pairs[i][0];
                              tags[i] = pairs[i][1];
                           }
                        }

                        if(tags != null && tags.length > 0) {
                           setDefaultTagValue(tags);
                           tEditor.setTags(tags);
                           tEditor.setLabels(labels);
                        }
                     }
                     catch(Throwable ex) {
                        LOG.error("Tags method failed: {}", tagsMethod, ex);
                     }
                     finally {
                        msgs.add(CoreTool.getUserMessage());
                        ThreadContext.setContextPrincipal(null);
                        latchMap.get(tView.getValue()).countDown();
                     }
                  }

                  private void setDefaultTagValue(String[] tags) {
                     callVisibleMethod(tView, bean);

                     if(!tView.isVisible()) {
                        return;
                     }

                     try {
                        final HashMap<String, PropertyMeta> props =
                           getPropertyMap(bean.getClass());
                        final PropertyMeta propertyMeta = props.get(tView.getValue());
                        final PropertyDescriptor descriptor = propertyMeta.getDescriptor();
                        final Method readMethod = descriptor.getReadMethod();

                        if(readMethod != null && readMethod.invoke(bean) == null) {
                           final Method writeMethod = descriptor.getWriteMethod();
                           writeMethod.invoke(bean, tags[0]);
                        }
                     }
                     catch(IllegalAccessException | InvocationTargetException e) {
                        // should only happen if there's no getter/setter available
                        // in which case the tagsMethod wouldn't function properly anyway
                        LOG.debug("Failed to set default tag value for property", e);
                     }
                     catch(Exception ignored) {
                        // ignore, again shouldn't happen unless the metadata is screwed up
                        //ignored.printStackTrace();
                     }
                  }
               };

               latchMap.put(tView.getValue(), new CountDownLatch(1));
               thread.start();
               threads.add(thread);
            }

            String enabledMethod = tEditor.getEnabledMethod();

            if(enabledMethod != null && enabledMethod.length() > 0) {
               try {
                  Class<?> clazz = bean.getClass();
                  Method method = clazz.getMethod(enabledMethod);
                  Object value = method.invoke(bean);

                  if(value.getClass() == boolean.class ||
                     value.getClass() == Boolean.class)
                  {
                     tEditor.setEnabled((Boolean) value);
                  }
               }
               catch(Throwable ex) {
                  LOG.error("Enabled method failed: {}", enabledMethod, ex);
               }
            }

            if(tEditor.getEditorPropertyNames() != null) {
               for(int i = 0; i < tEditor.getEditorPropertyNames().length; i++) {
                  String methodName = tEditor.getEditorPropertyMethods()[i];

                  if(methodName != null) {
                     try {
                        Method method = bean.getClass().getMethod(methodName);
                        Object value = method.invoke(bean);
                        tEditor.getEditorPropertyValues()[i] =
                           value == null ? null : String.valueOf(value);
                     }
                     catch(Exception e) {
                        LOG.warn("Failed to get editor property value [" +
                              tEditor.getEditorPropertyNames()[i] + "]", e);
                     }
                  }
               }
            }
         }

         callEditorMethods(tView.getViews(), bean, records, principal);
      }

      // timeout in 30 seconds
      for(Thread thread : threads) {
         try {
            thread.join(30000);
         }
         catch(Exception ignore) {
         }
      }

      msgs.stream().filter(m -> m != null).forEach(m -> CoreTool.addUserMessage(m));
   }

   public static void callEditorMethods(TabularView[] views, Object bean) {
      callEditorMethods(views, bean, Collections.EMPTY_LIST, null);
   }

   /**
    * Creates a query from the specified data source
    *
    * @param dataSource data source of the query
    * @return tabular query
    */
   public static TabularQuery createQuery(String dataSource) {
      TabularQuery query = null;

      try {
         XDataSource ds = XFactory.getRepository().getDataSource(dataSource);

         if(ds != null) {
            String queryClass = Config.getQueryClass(ds.getType());
            query = (TabularQuery) Config.getClass(ds.getType(), queryClass).newInstance();
            query.setDataSource(ds);
         }
      }
      catch(Exception e) {
         LOG.error("Failed to create a query class for the given data source: {}", dataSource, e);
      }

      return query;
   }

   public static void setSessionId(String sessionId) {
      TabularUtil.sessionId.set(sessionId);
   }

   public static int getMaxRows(XQuery query, VariableTable params) {
      try {
         String previewMax = (String) params.get(XQuery.HINT_MAX_ROWS);
         int max = query.getMaxRows();

         if(previewMax != null) {
            max = Integer.parseInt(previewMax);
         }

         return max;
      }
      catch(Exception ex) {
         LOG.warn("Failed to get max rows", ex);
         return 0;
      }
   }

   /**
    * Calculate a hash string based on annotated properties.
    */
   public static long hash(Object bean) {
      long hashv = bean.getClass().getName().hashCode();

      for(PropertyMeta prop : findProperties(bean.getClass())) {
         if(prop.isAnnotated()) {
            Object val = prop.getValue(bean);

            hashv = hashv * 31 + prop.getName().hashCode();
            hashv = hashv * 31 + hashCode(val);
         }
      }

      return hashv;
   }

   private static long hashCode(Object val) {
      if(val == null) {
         return 0;
      }
      else if(val.getClass().isArray()) {
         long hash = 0;
         int len = Array.getLength(val);

         for(int i = 0; i < len; i++) {
            hash += hashCode(Array.get(val, i));
         }

         return hash;
      }

      return val.hashCode();
   }

   private static ThreadLocal<String> sessionId = new ThreadLocal<>();
   private static final Logger LOG = LoggerFactory.getLogger(TabularUtil.class);
}
