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

import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.script.BaseScriptable;
import inetsoft.report.script.PropertyDescriptor;
import inetsoft.sree.schedule.ScheduleInfo;
import inetsoft.uql.*;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.ContainerVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;
import inetsoft.util.script.*;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * The viewsheet assembly scriptable in viewsheet scope.
 *
 * @version 10.3
 * @author InetSoft Technology Corp
 */
public class VSAScriptable extends ScriptableObject
   implements Cloneable, DynamicScope, BaseScriptable
{
   /**
    * Create a viewsheet assembly scriptable.
    * @param box the specified viewsheet sandbox.
    */
   public VSAScriptable(ViewsheetSandbox box) {
      super();

      this.box = box;
      map = Collections.synchronizedMap(new HashMap<>());

      addProperty("taskName", null);
   }

   /**
    * Add properties.
    */
   private void init() {
      if(!inited && getVSAssemblyInfo() != null) {
         inited = true;
         addFunctions();
         addProperties();
      }
   }

   /**
    * Get the name of the set of objects implemented by this Java class.
    */
   @Override
   public String getClassName() {
      return "VSA";
   }

   /**
    * Get the viewsheet scope container.
    * @return the viewsheet scope container.
    */
   public ViewsheetScope getScope() {
      return scope;
   }

   /**
    * Set the viewsheet scope container.
    * @param scope the specified viewsheet scope contained.
    */
   void setScope(ViewsheetScope scope) {
      this.scope = scope;
   }

   /**
    * Get the vs specific prototype.
    */
   public VSAScriptable getVSPrototype() {
      return vsproto;
   }

   /**
    * Set the vs specific prototype.
    */
   public void setVSPrototype(VSAScriptable proto) {
      this.vsproto = proto;
   }

   /**
    * Get the name of the viewsheet assembly.
    * @return the name of the viewsheet assembly contained in this scriptable.
    */
   public String getAssembly() {
      return assembly;
   }

   /**
    * Set the name of the viewsheet assembly.
    * @param assembly the name of the viewsheet assembly contained in this
    * scriptable.
    */
   public void setAssembly(String assembly) {
      this.assembly = assembly;
   }

   /**
    * Set the property.
    * @param name the specified name.
    * @param val the specified val, which might be a String, Boolean,
    * Scriptable, etc.
    */
   protected void setProperty(String name, Object val) {
      if(val == null) {
         map.remove(name);
      }
      else {
         map.put(name, val);
      }
   }

   /**
    * Get the property.
    * @param name the specified name.
    * @return the property value, which might be a String, Boolean, Scriptable,
    * etc.
    */
   protected Object getProperty(String name) {
      return map.get(name);
   }

   /**
    * Copy all property values from sobj to this object.
    */
   protected void copyProperties(VSAScriptable sobj) {
      map.putAll(sobj.map);
   }

   /**
    * Sets a named property in this object.
    */
   @Override
   public void put(String name, Scriptable start, Object value) {
      init();

      Object val = propmap.get(name);

      try {
         if(val instanceof PropertyDescriptor) {
            PropertyDescriptor desc = (PropertyDescriptor) val;
            desc.set(element, value);
         }
         else {
            getVarMap().put(name, value);
         }
      }
      catch(IllegalArgumentException e) {
         LOG.error("Property value type is incorrect: " + name + "=" + value);
      }
      catch(Exception e) {
         LOG.error("Failed to set property value: " + name + "=" + value, e);
      }
   }

   /**
    * Get a named property from the object.
    */
   @Override
   public Object get(String name, Scriptable start) {
      final Map<String, Object> varMap = getVarMap();

      if(varMap.containsKey(name)) {
         return varMap.get(name);
      }

      if(map.containsKey(name)) {
         return map.get(name);
      }

      init();

      if(propmap.containsKey(name)) {
         try {
            Object val = propmap.get(name);

            if(val instanceof PropertyDescriptor) {
               PropertyDescriptor desc = (PropertyDescriptor) val;

               return desc.get(null);
            }

            return val;
         }
         catch(Exception e) {
            LOG.error("Failed to get property: " + name, e);
         }
      }

      if(vsproto != null) {
         Object rc = vsproto.get(name, start);

         if(rc != NOT_FOUND) {
            return rc;
         }
      }

      return super.get(name, start);
   }

   /**
    * Indicate whether or not a named property is defined in an object.
    */
   @Override
   public boolean has(String name, Scriptable start) {
      init();

      if(map.containsKey(name) || propmap.containsKey(name) || getVarMap().containsKey(name)) {
         return true;
      }

      if(vsproto != null && vsproto.has(name, start)) {
         return true;
      }

      return super.has(name, start);
   }

   /**
    * Get an array of property ids.
    */
   @Override
   public Object[] getIds() {
      init();

      Set<Object> ids = new HashSet<>();
      Object[] sids = includedAll ? super.getIds() : new Object[0];
      Object[] pids = (vsproto != null && (includedAll ||
         !(vsproto instanceof CubeVSAScriptable))) ?
         vsproto.getIds() : new Object[] {};

      ids.addAll(Arrays.asList(sids));
      ids.addAll(Arrays.asList(pids));
      ids.addAll(getVarMap().keySet());
      ids.addAll(map.keySet());

      synchronized(propmap) {
         for(Object id : propmap.keySet()) {
            if(isPublicProperty(id)) {
               ids.add(id);
            }
         }
      }

      return ids.toArray();
   }

   /**
    * Set if included ids from ScriptableObject.
    */
   public void setIncludeAllIDs(boolean included) {
      includedAll = included;
   }

   /**
    * Get the suffix of a property, may be "" or [].
    * @param prop the property.
    */
   public String getSuffix(Object prop) {
      if("isActionVisible".equals(prop) || "setActionVisible".equals(prop) ||
         "scheduleAction".equals(prop) || "addAction".equals(prop))
      {
         return "()";
      }

      if(get(prop + "", this) instanceof ArrayObject) {
         return "[]";
      }
      else {
         return "";
      }
   }

   /**
    * Get the visibility of the specific action.
    * @param name the name of the specific action.
    * @return the visibility of the action, <tt>true</tt> visible,
    * <tt>false</tt> otherwrise.
    */
   public boolean isActionVisible(String name) {
      if(name != null && name.length() > 0) {
         VSAssemblyInfo info = getVSAssemblyInfo();
         return info != null && info.isActionVisible(name);
      }

      return false;
   }

   /**
    * Set the visibility of the specific action.
    * @param name the name of the specific action.
    * @param visible the visibility of the action.
    */
   public void setActionVisible(String name, boolean visible) {
      if(name != null && name.length() > 0) {
         VSAssemblyInfo info = getVSAssemblyInfo();

         if(info != null) {
            info.setActionVisible(name, visible);
         }
      }
   }

   /**
    * Get VSAssembly.
    */
   protected VSAssembly getVSAssembly() {
      return getVSAssembly(this.assembly);
   }

   protected VSAssembly getVSAssembly(String assembly) {
      if(assembly == null) {
         return null;
      }

      Viewsheet vs = box.getViewsheet();
      VSAssembly vsobj = ViewsheetScope.VIEWSHEET_SCRIPTABLE.equals(assembly) ? vs
         : vs.getAssembly(assembly);

      if(vsobj == null) {
         LOG.error("Script assembly is not found: " + assembly + " in " +
                      Arrays.stream(vs.getAssemblies()).map(a -> a.getName()).collect(Collectors.toSet()));
      }

      return vsobj;
   }

   /**
    * Get the VSAssemblyInfo.
    */
   protected VSAssemblyInfo getVSAssemblyInfo() {
      VSAssembly vassembly = getVSAssembly();

      if(vassembly == null) {
         return null;
      }

      return (VSAssemblyInfo) vassembly.getInfo();
   }

   /**
    * Initialize the assembly properties.
    */
   protected void addProperties() {
      VSAssemblyInfo info = getVSAssemblyInfo();

      if(info == null) {
         return;
      }

      addProperty("background", "getBackground", "setBackground",
         Color.class, getClass(), this);
      addProperty("foreground", "getForeground", "setForeground",
         Color.class, getClass(), this);
      addProperty("alpha", "getAlpha", "setAlpha", int.class, getClass(), this);
      addProperty("borderColors", "getBorderColors", "setBorderColors",
         BorderColors.class, getClass(), this);
      addProperty("borders", "getBorders", "setBorders", Insets.class,
         getClass(), this);
      addProperty("font", "getFont", "setFont", Font.class, getClass(), this);
      addProperty("format", "getFormat", "setFormat", String.class,
         getClass(), this);
      addProperty("formatSpec", "getFormatExtent", "setFormatExtent",
         String.class, getClass(), this);
      addProperty("alignment", "getAlignment", "setAlignment", int.class,
         getClass(), this);
      addProperty("visible", "isVisible", "setVisible", String.class,
         getClass(), this);
      addProperty("enabled", "isEnabled", "setEnabled", boolean.class,
         VSAssemblyInfo.class, info);
      addProperty("size", "getSize", "setSize", Dimension.class, getClass(),
         this);
      addProperty("position", "getPosition", "setPosition", Point.class,
         getClass(), this);
      addProperty("scaledPosition", "getScaledPosition", null, Point.class,
                  getClass(), this);
      addProperty("scaledSize", "getScaledSize", null, Dimension.class,
                  getClass(), this);
   }

   /**
    * Set the size.
    * @param dim the dimension of size.
    */
   public void setSize(Dimension dim) {
      VSAssemblyInfo info = getVSAssemblyInfo();

      if(info == null) {
         LOG.warn("Could not set the size, the assembly info is null");
         return;
      }

      if(dim.height <= 0 || dim.width <= 0) {
         LOG.warn("Could not set the size, invalid dimension: " + dim);
         return;
      }

      if(box.isRuntime()) {
         Viewsheet vs = box.getViewsheet();
         Assembly assembly0 = vs.getAssembly(assembly);

         if(assembly0 != null) {
            Dimension msize = assembly0.getMinimumSize();
            dim.width = Math.max(msize.width, dim.width);
            dim.height = Math.max(msize.height, dim.height);
         }

         info.setPixelSize(dim);
      }
   }

   /**
    * Get the size.
    * @return the dimension of size.
    */
   public Dimension getSize() {
      VSAssemblyInfo info = getVSAssemblyInfo();

      if(info == null) {
         return null;
      }

      return info.getPixelSize();
   }

   public Dimension getScaledSize() {
      VSAssemblyInfo info = getVSAssemblyInfo();
      return info == null ? null : info.getLayoutSize(true);
   }

   /**
    * Set the position.
    * @param p the point of position.
    */
   public void setPosition(Point p) {
      VSAssemblyInfo info = getVSAssemblyInfo();

      if(info == null) {
         LOG.warn("Could not set the position, the assembly is null");
         return;
      }

      if(p.x < 0 || p.y < 0) {
         LOG.warn("Could not set the position, invalid point: " + p);
         return;
      }

      if(box.isRuntime()) {
         setPosition(info, p);
      }
   }

   protected void setPosition(VSAssemblyInfo info, Point p) {
      Point op = info.getPixelOffset();
      info.setPixelOffset(p);

      if(info instanceof ContainerVSAssemblyInfo) {
         String[] children = ((ContainerVSAssemblyInfo) info).getAssemblies();
         int offsetx = p.x - op.x;
         int offsety = p.y - op.y;

         Arrays.stream(children)
            .map(c -> getVSAssembly(c))
            .filter(c -> c != null)
            .map(c -> c.getVSAssemblyInfo())
            .forEach(c -> {
                  Point pos = c.getPixelOffset();

                  if(pos != null) {
                     if(c instanceof ContainerVSAssemblyInfo) {
                        setPosition(c, new Point(pos.x + offsetx, pos.y + offsety));
                     }
                     else {
                        c.setPixelOffset(new Point(pos.x + offsetx, pos.y + offsety));
                     }
                  }
               });
      }
   }

   /**
    * Get the position.
    * @return the point of position.
    */
   public Point getPosition() {
      VSAssemblyInfo info = getVSAssemblyInfo();
      return info == null ? null : info.getPixelOffset();
   }

   public Point getScaledPosition() {
      VSAssemblyInfo info = getVSAssemblyInfo();
      return info == null ? null : info.getLayoutPosition(true);
   }

   /**
    * Set the background.
    * @param color the specified background color.
    */
   public void setBackground(Color color) {
      getUserDefinedFormat().setBackground(color);
      applyFormat();
   }

   /**
    * Get the background.
    * @return the background of this format.
    */
   public Color getBackground() {
      return getVSCompositeFormat().getBackground();
   }

   public void setVisible(String visible) {
      VSAssemblyInfo info = getVSAssemblyInfo();

      if(info != null) {
         info.setVisible(visible);
         info.setControlByScript(true);
      }
   }

   public void setVisibleValue(String visible) {
      VSAssemblyInfo info = getVSAssemblyInfo();

      if(info != null) {
         info.setVisibleValue(visible);
         info.setControlByScript(true);
      }
   }

   public boolean isVisible() {
      VSAssemblyInfo info = getVSAssemblyInfo();
      Viewsheet vs = box.getViewsheet();
      boolean print = vs != null && vs.isPrintMode();

      return info != null && info.isVisible(print);
   }

   /**
    * Set the foreground.
    * @param foreground the specified foreground color.
    */
   public void setForeground(Color foreground) {
      getUserDefinedFormat().setForeground(foreground);
   }

   /**
    * Get the foreground.
    * @return the foreground of this format.
    */
   public Color getForeground() {
      return getVSCompositeFormat().getForeground();
   }


   /**
    * Check if should wrap text.
    * @return <tt>true</tt> if should wrap text, <tt>false</tt> otherwise.
    */
   public boolean isWrapping() {
      return getUserDefinedFormat().isWrapping();
   }

   /**
    * Set whether should wrap text.
    * @param wrap <tt>true</tt> if should wrap text, <tt>false</tt> otherwise.
    */
   public void setWrapping(boolean wrap) {
      getUserDefinedFormat().setWrapping(wrap);
   }

   /**
    * Get the alignment (horizontal and vertical).
    * @return the alignment of this format.
    */
   public int getAlignment() {
      return getVSCompositeFormat().getAlignment();
   }

   /**
    * Set the alignment (horizontal and vertical) to this format.
    * @param align the specified alignment.
    */
   public void setAlignment(int align) {
      getUserDefinedFormat().setAlignment(align);
      applyFormat();
   }

   /**
    * Get the format extent (pattern or predefined extent type).
    * @return the format extent of this format.
    */
   public String getFormatExtent() {
      return getUserDefinedFormat().getFormatExtent();
   }

   /**
    * Set the format extent to this format (pattern or predefined extent type).
    * @param fext the specified format extent.
    */
   public void setFormatExtent(String fext) {
      getUserDefinedFormat().setFormatExtent(fext);
      applyFormat();
   }

   /**
    * Get the borders.
    * @return the borders of this format.
    */
   public Insets getBorders() {
      return getVSCompositeFormat().getBorders();
   }

   /**
    * Set the borders to this format.
    * @param borders the specified borders.
    */
   public void setBorders(Insets borders) {
      getUserDefinedFormat().setBorders(borders);
   }

   /**
    * Get the border colors.
    * @return the border colors of this format.
    */
   public BorderColors getBorderColors() {
      return getVSCompositeFormat().getBorderColors();
   }

   /**
    * Set the border colors to this format.
    * @param bcolors the specified border colors.
    */
   public void setBorderColors(BorderColors bcolors) {
      getUserDefinedFormat().setBorderColors(bcolors);
   }

   /**
    * Set the alpha.
    */
   public void setAlpha(int trans) {
      getUserDefinedFormat().setAlpha(trans);
   }

   /**
    * Get the transparentcy.
    */
   public int getAlpha() {
      return getVSCompositeFormat().getAlpha();
   }

   /**
    * Get the format of the assembly.
    * @return the format of the assembly
    */
   public String getFormat() {
      return getUserDefinedFormat().getFormat();
   }

   /**
    * Set the format of the assembly.
    * @param fmt the specific format
    */
   public void setFormat(String fmt) {
      if(fmt == null) {
         return;
      }

      getUserDefinedFormat().setFormat(fmt);
      applyFormat();
   }

   /**
    * Get the font of the assembly.
    * @return the font of the assembly
    */
   public Font getFont() {
      return getVSCompositeFormat().getFont();
   }

   /**
    * Set the font of the assembly.
    * @param font the specific font
    */
   public void setFont(Font font) {
      if(font == null) {
         return;
      }

      getUserDefinedFormat().setFont(font);
      applyFormat();
   }

   /**
    * Add two methods to viewsheet script environment for controlling the
    * visibility of the assembly actions and assembly popup menus.
    */
   private void addFunctions() {
      try {
         if(hasActions()) {
            addFunctionProperty(getClass(), "isActionVisible", String.class);
            addFunctionProperty(getClass(), "setActionVisible", String.class, boolean.class);
         }

         addFunctionProperty(getClass(), "scheduleAction", boolean.class, Object.class);

         /* not supported in 13.1
         func = new FunctionObject("addAction",
            getClass().getMethod("addAction",
            new Class[] {String.class, String.class, String.class}), this);
         addProperty("addAction", func);
         */
      }
      catch(Exception e) {
         LOG.error("Failed to register functions", e);
      }
   }

   protected boolean hasActions() {
      return true;
   }

   /**
    * Add a property to this scriptable.
    * @param name property name.
    * @param getter getter method for retrieving the property value.
    * @param setter setter method for changing  the property value.
    * @param type property type.
    * @param cls the class of the element object.
    * @param object the target object to invoke setter/getter.
    */
   public void addProperty(String name, String getter, String setter,
                              Class type, Class cls, Object object) {
      try {
         propmap.put(name, new VSPropertyDescriptor(cls, getter, setter, type, object));
      }
      catch(Throwable e) {
         LOG.error("Failed to add property " + name + " on object " + object, e);
      }
   }

   /**
    * Add a property to a scriptable.
    * @param name property name.
    * @param obj value as a String, Boolean, Number, or Scriptable.
    */
   public void addProperty(String name, Object obj) {
      propmap.put(name, obj);
   }

   /**
    * Add a property to this scriptable.
    * @param name property name.
    * @param getter getter method for retrieving the property value.
    * @param setter setter method for changing  the property value.
    * @param type property type.
    * @param cls the class of the element object.
    */
   public void addProperty(String name, String getter, String setter,
                           Class type, Class cls) {
   }

   /**
    * Add a property to this scriptable.
    * @param name property name.
    * @param setter setter method for changing  the property value.
    * @param type property type.
    * @param cls the class of the element object.
    * @param value the value of the property.
    */
   public void addProperty(String name, String setter,
                              Class type, Class cls, Object value) {
      try {
         propmap.put(name, new VSPropertyDescriptor(cls, setter, type, value));
      }
      catch(Throwable e) {
         LOG.error("Failed to add property " + name + " with value " + value, e);
      }
   }

   /**
    * Add a property to this scriptable.
    * @param name property name.
    * @param getter getter method for retrieving the property value.
    * @param setter setter method for changing  the property value.
    * @param type property type.
    * @param cls the class of the element object.
    * @param params the parameters to call setter and getter. For setter,
    * the set value is appended to parameters.
    */
   public void addProperty(String name, String getter, String setter,
                           Class type, Class cls, Object[] params) {
   }

   /**
    * Remove a property definition.
    */
   protected void removeProperty(String name) {
      propmap.remove(name);
   }

   /**
    * Get the string representation.
    */
   public String toString() {
      return super.toString() + '[' + assembly + ']';
   }

   /**
    * Set the element, to set property value.
    */
   protected void setElement(Object element) {
      this.element = element;
   }

   /**
    * Check the alert condition and control whether a report or a viewsheet is
    * to be send to the recipients.
    */
   public void scheduleAction(boolean action, Object emails) throws Exception {
      ScheduleInfo scheduleInfo = new ScheduleInfo(true, null);
      scheduleInfo.setScheduleAction(action);

      if(JSObject.isArray(emails)) {
         Object[] emailsArr = JSObject.split(emails);
         StringBuilder address = new StringBuilder();

         for(int i = 0; i < emailsArr.length; i++) {
            if(emailsArr[i] instanceof String) {
               address.append(emailsArr[i]);

               if(i != emailsArr.length - 1) {
                  address.append(",");
               }
            }
         }

         scheduleInfo.setEmails(
            address.length() == 0 ? null : address.toString());
      }
      else if(emails instanceof String) {
         scheduleInfo.setEmails((String) emails);
      }

      box.setScheduleAction(scheduleInfo);
   }

   /**
    * Add action to viewsheet.
    */
   public void addAction(String icon, String label, String event) {
      if(icon != null && icon.length() > 0 &&
         label != null && label.length() > 0 &&
         event != null && event.length() > 0)
      {
         ViewsheetScope scope = getScope();

         if(scope != null) {
            scope.addAction(icon, label, event);
         }
      }
   }

   /**
    * Get user defined format.
    */
   protected VSFormat getUserDefinedFormat() {
      if(getVSAssemblyInfo() == null) {
         return new VSFormat();
      }

      VSFormat userFormat = getVSAssemblyInfo().getFormat().getUserDefinedFormat();
      return userFormat == null ? new VSFormat() : userFormat;
   }

   /**
    * Get vs composite format.
    */
   protected VSCompositeFormat getVSCompositeFormat() {
      if(getVSAssemblyInfo() == null) {
         return new VSCompositeFormat();
      }

      return getVSAssemblyInfo().getFormat();
   }

   /**
    * Apply changes on the object format.
    */
   private void applyFormat() {
      VSCompositeFormat cellfmt = getVSAssemblyInfo().
         getFormatInfo().getFormat(VSAssemblyInfo.OBJECTPATH);
      getVSAssemblyInfo().getFormatInfo().applyFormat(cellfmt);
   }

   /**
    * Check if this property should be exposed in script editor.
    */
   protected boolean isPublicProperty(Object name) {
      return true;
   }

   /**
    * Clear cached data, assuming the data in component has changed.
    */
   public void clearCache() {
   }

   /**
    * Clear cached data, assuming the data in component has changed.
    * @param type result type defined in DataMap.
    */
   public void clearCache(int type) {
   }

   /**
    * Get tipped assembly's tip condition.
    */
   protected Scriptable[] getTipConditions() {
      ConditionListWrapper wrapper = getVSAssembly().getTipConditionList();

      if(wrapper == null || wrapper.isEmpty()) {
         return null;
      }

      int size = wrapper.getConditionSize();
      ArrayList<Scriptable> items = new ArrayList<>();
      ConditionItem item;
      Condition cond;

      for(int i = 0; i < size; i++) {
         item = wrapper.getConditionItem(i);

         if(item == null) {
            continue;
         }

         cond = item.getCondition();
         String attr = item.getAttribute().getAttribute();
         Object value = cond.getValueCount() > 0 ? cond.getValue(0) : null;
         items.add(new TipDataCondition(attr, value));
      }

      return items.toArray(new Scriptable[0]);
   }

   protected Map<String, Object> getVarMap() {
      return map;
   }

   private static class TipDataCondition extends ScriptableObject {
      public TipDataCondition(String attr, Object value) {
         this.attr = attr;
         this.value = value;
      }

      @Override
      public String getClassName() {
         return "ConditionItem";
      }

      @Override
      public Object get(String id, Scriptable script) {
         if("attr".equals(id)) {
            return attr;
         }

         if("value".equals(id)) {
            return value;
         }

         return super.get(id, script);
      }

      private final String attr;
      private final Object value;
   }

   @Override
   public Object clone() {
      try {
         return super.clone();
      }
      catch(Exception ex) {
         LOG.error("Failed to clone object", ex);
      }

      return null;
   }

   protected ViewsheetSandbox box;
   protected String assembly;

   private ViewsheetScope scope;
   private VSAScriptable vsproto; // vs prototype
   private final Map<String, Object> map;
   private final Map<String, Object> propmap = Collections.synchronizedMap(new HashMap<>()); // properties map
   private boolean includedAll = true;
   private Object element = null;
   private boolean inited = false;

   private static final Logger LOG = LoggerFactory.getLogger(VSAScriptable.class);
}
