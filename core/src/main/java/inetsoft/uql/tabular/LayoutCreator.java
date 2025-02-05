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

import inetsoft.uql.XDataSource;
import inetsoft.uql.XQuery;
import inetsoft.uql.util.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.util.*;

/**
 * Creates layout from @View annotation.
 *
 * @version 12.2
 * @author InetSoft Technology Corp
 **/
public class LayoutCreator {
   /**
    * Create the layout for editing the properties.
    */
   public TabularView createLayout(final Object bean) {
      this.bean = bean;
      Class cls = bean.getClass();
      View view = (View) cls.getAnnotation(View.class);
      pmap = TabularUtil.getPropertyMap(cls);

      TabularView mainView = new TabularView();

      if(view == null) {
         createLayout(mainView);
      }
      else {
         createLayout(mainView, view.value(), view.vertical());
      }

      setValues(mainView.getViews());

      return mainView;
   }

   /**
    * Create a layout for the pane.
    * @param vertical true to arrange children vertically
    */
   private void createLayout(TabularView mainView, View1[] elems,
      boolean vertical)
   {
      int row = 0, col = 0;

      for(int i = 0; i < elems.length; i++) {
         View1 elem = elems[i];

         if(elem.col() >= 0) {
            if(elem.col() <= col && !vertical) {
               row++;
            }

            col = elem.col();
         }

         if(elem.row() >= 0) {
            if(elem.row() > row) {
               col = 0;
            }

            row = elem.row();
         }
         else if(vertical) {
            row++;

            if(elem.col() < 0) {
               col = 0;
            }
         }

         PropertyMeta prop = pmap.get(elem.value());

         if(prop == null && (elem.type() == ViewType.COMPONENT || elem.type() == ViewType.EDITOR)) {
            LOG.warn("Property not found: " + elem.value());
         }

         TabularView tView = createTabularView(elem, prop);

         if(elem.type() == ViewType.LABEL) {
            boolean last = i == elems.length - 1 || elems[i + 1].row() > row ||
               elems[i + 1].col() >= 0 && elems[i + 1].col() <= col;
            View1 next = (last || vertical) ? null : elems[i + 1];

            tView.setRow(row);
            tView.setCol(col);
            tView.setAlign(getHAlign(elem.align(), elem.type(), next));
            tView.setVerticalAlign(getVAlign(elem.verticalAlign(), elem.type(), next));
            mainView.addTabularView(tView);
         }
         else if(elem.type() == ViewType.EDITOR) {
            TabularEditor editor = tView.getEditor();
            editor.setRow(row);
            editor.setCol(col);
            editor.setAlign(getHAlign(elem.align(), ViewType.EDITOR, null));
            editor.setVerticalAlign(getVAlign(elem.verticalAlign(), ViewType.EDITOR, null));
            mainView.addTabularView(tView);
         }
         else if(elem.type() == ViewType.COMPONENT) {
            tView.setRow(row);
            tView.setCol(col);
            tView.setAlign(getHAlign(elem.align(), ViewType.LABEL, elem));
            tView.setVerticalAlign(getVAlign(elem.verticalAlign(), ViewType.LABEL, elem));
            col++;
            TabularEditor editor = tView.getEditor();

            if(editor == null) {
               throw new RuntimeException("Property missing for view: " + elem.value());
            }
            else {
               editor.setRow(row);
               editor.setCol(col);
               editor.setAlign(getHAlign(elem.align(), ViewType.EDITOR, null));
               editor.setVerticalAlign(getVAlign(elem.verticalAlign(), ViewType.EDITOR, null));
               mainView.addTabularView(tView);
            }
         }
         else if(elem.type() == ViewType.BUTTON) {
            tView.setRow(row);
            tView.setCol(col);
            tView.setAlign(getHAlign(elem.align(), ViewType.BUTTON, null));
            tView.setVerticalAlign(getVAlign(elem.verticalAlign(), ViewType.BUTTON, null));
            mainView.addTabularView(tView);
         }
         else if(elem.type() == ViewType.PANEL) {
            createLayout(tView, View1Proxy.wrap(elem.elements()), elem.vertical());

            tView.setRow(row);
            tView.setCol(col);
            tView.setAlign(getHAlign(elem.align(), ViewType.PANEL, null));
            tView.setVerticalAlign(getVAlign(elem.verticalAlign(), ViewType.PANEL, null));
            mainView.addTabularView(tView);
         }

         col++;
      }
   }

   private void createLayout(TabularView mainView) {
      int row = 0;

      for(PropertyMeta prop : pmap.values()) {
         TabularView tView = createTabularView(null, prop);
         tView.setType(ViewType.COMPONENT);
         tView.setRow(row);
         tView.setCol(0);
         tView.setRowspan(1);
         tView.setColspan(1);
         tView.setAlign(ViewAlign.RIGHT);

         if(tView.getEditor().isDefined() && prop.isMultiline()) {
            tView.setVerticalAlign(ViewAlign.TOP);
         }
         else {
            tView.setVerticalAlign(getVAlign(ViewAlign.AUTO, ViewType.LABEL,
               null));
         }

         TabularEditor tEditor = tView.getEditor();
         tEditor.setRow(row);
         tEditor.setCol(1);
         tEditor.setAlign(getHAlign(ViewAlign.AUTO, ViewType.EDITOR, null));
         tEditor.setVerticalAlign(getVAlign(ViewAlign.AUTO, ViewType.EDITOR,
            null));
         mainView.addTabularView(tView);
         row++;
      }
   }

   private ViewAlign getHAlign(ViewAlign halign, ViewType type, View1 next) {
      if(halign == ViewAlign.AUTO) {
         if(type == ViewType.LABEL && next != null) {
            halign = ViewAlign.RIGHT;
         }
         else if(type == ViewType.PANEL) {
            halign = ViewAlign.FILL;
         }
         else {
            halign = ViewAlign.LEFT;
         }
      }

      return halign;
   }

   private ViewAlign getVAlign(ViewAlign valign, ViewType type, View1 next) {
      if(valign == ViewAlign.AUTO) {
         valign = ViewAlign.MIDDLE;

         if(type == ViewType.PANEL) {
            valign = ViewAlign.FILL;
         }
         else if(next != null) {
            PropertyMeta prop = pmap.get(next.value());

            if(prop != null && prop.isMultiline()) {
               valign = ViewAlign.TOP;
            }
         }
      }

      return valign;
   }

   private TabularView createTabularView(View1 view1, PropertyMeta propertyMeta) {
      TabularView tView = new TabularView();

      if(view1 != null) {
         tView.setType(view1.type());
         tView.setText(view1.text());
         tView.setColor(view1.color());
         tView.setFont(view1.font());
         tView.setValue(view1.value());
         tView.setRow(view1.row());
         tView.setCol(view1.col());
         tView.setRowspan(view1.rowspan());
         tView.setColspan(view1.colspan());
         tView.setAlign(view1.align());
         tView.setVerticalAlign(view1.verticalAlign());
         tView.setPaddingLeft(view1.paddingLeft());
         tView.setPaddingRight(view1.paddingRight());
         tView.setPaddingTop(view1.paddingTop());
         tView.setPaddingBottom(view1.paddingBottom());
         tView.setVisibleMethod(view1.visibleMethod());
         tView.setWrap(view1.wrap());
         tView.setVisible(true);
         tView.setAffectedViews(view1.affectedViews());
      }

      String label = null;

      if(propertyMeta != null) {
         Property property = propertyMeta.getProperty();

         if(property != null) {
            label = property.label();
            tView.setPassword(property.password());
            tView.setRequired(property.required());
            tView.setMin(property.min());
            tView.setMax(property.max());
            tView.setPattern(property.pattern());
         }

         PropertyEditor propertyEditor = propertyMeta.getEditor();

         if(tView.getType() == ViewType.EDITOR ||
            tView.getType() == ViewType.COMPONENT)
         {
            tView.setEditor(createTabularEditor(propertyEditor, propertyMeta));
         }

         if(tView.getValue() == null) {
            tView.setValue(propertyMeta.getName());
         }
      }

      if(tView.getType() == ViewType.BUTTON) {
         tView.setButton(createTabularButton(view1));
      }

      String displayLabel = tView.getText();

      if(label != null) {
         displayLabel = label;
      }

      if(displayLabel == null || displayLabel.length() == 0) {
         displayLabel = tView.getValue();
      }

      try {
         String dxType = null;

         if(bean instanceof XQuery) {
            dxType = ((XQuery) bean).getType();
         }
         else if(bean instanceof XDataSource) {
            dxType = ((XDataSource) bean).getType();
         }

         ResourceBundle bundle = Config.getResourceBundle(dxType);

         if(bundle != null) {
            displayLabel = bundle.getString(displayLabel);
         }
      }
      catch(MissingResourceException ex) {
         LOG.debug("String not found in the bundle: " + displayLabel);
      }

      tView.setDisplayLabel(displayLabel);

      return tView;
   }

   private TabularEditor createTabularEditor(PropertyEditor editor, PropertyMeta propertyMeta) {
      TabularEditor tEditor = new TabularEditor();

      if(editor != null) {
         tEditor.setRows(editor.rows());
         tEditor.setColumns(editor.columns());
         tEditor.setTags(editor.tags());
         tEditor.setLabels(editor.labels());
         tEditor.setTagsMethod(editor.tagsMethod());
         tEditor.setDependsOn(editor.dependsOn());
         tEditor.setEnabledMethod(editor.enabledMethod());
         tEditor.setEnabled(editor.enabled());
         tEditor.setCustomEditor(editor.customEditor());
         tEditor.setDefined(true);
         tEditor.setLineWrap(editor.lineWrap());
         tEditor.setAutocomplete(editor.autocomplete());

         if(editor.editorProperties() == null) {
            tEditor.setEditorPropertyNames(null);
            tEditor.setEditorPropertyValues(null);
            tEditor.setEditorPropertyMethods(null);
         }
         else {
            EditorProperty[] properties = editor.editorProperties();
            String[] names = new String[properties.length];
            String[] values = new String[properties.length];
            String[] methods = new String[properties.length];

            for(int i = 0; i < properties.length; i++) {
               names[i] = properties[i].name();
               values[i] = "".equals(properties[i].value()) ?
                  null : properties[i].value();
               methods[i] = "".equals(properties[i].method()) ?
                  null : properties[i].method();
            }

            tEditor.setEditorPropertyNames(names);
            tEditor.setEditorPropertyValues(values);
            tEditor.setEditorPropertyMethods(methods);
         }
      }
      else {
         tEditor.setRows(1);
         tEditor.setColumns(0);
         tEditor.setTags(new String[]{});
         tEditor.setLabels(new String[]{});
         tEditor.setTagsMethod("");
         tEditor.setDependsOn(new String[]{});
         tEditor.setEnabledMethod("");
         tEditor.setEnabled(true);
         tEditor.setCustomEditor("");
         tEditor.setDefined(false);
         tEditor.setEditorPropertyNames(null);
         tEditor.setEditorPropertyValues(null);
         tEditor.setEditorPropertyMethods(null);
      }

      Class<?> cls = propertyMeta.getDescriptor().getPropertyType();

      if(cls.isPrimitive()) {
         cls = wrapPrimitive(cls);
      }

      tEditor.setPropertyType(cls.getName());

      if(cls.isArray()) {
         cls = cls.getComponentType();

         if(cls.isPrimitive()) {
            cls = wrapPrimitive(cls);
         }

         tEditor.setPropertySubtype(cls.getName());
      }
      else if(Collection.class.isAssignableFrom(cls)) {
         Method method = propertyMeta.getDescriptor().getReadMethod();
         ParameterizedType ptype = (ParameterizedType) method.
            getGenericReturnType();
         cls = (Class) ptype.getActualTypeArguments()[0];
         tEditor.setPropertySubtype(cls.getName());
      }

      TabularEditor.Type type = TabularUtil.getEditorType(tEditor);
      tEditor.setType(type);

      if(type == TabularEditor.Type.LIST) {
         tEditor.setSubtype(TabularUtil.getEditorSubtype(tEditor, tEditor.getPropertySubtype()));
      }

      return tEditor;
   }

   private TabularButton createTabularButton(View1 view) {
      TabularButton tButton = new TabularButton();
      tButton.setType(view.button().type());
      tButton.setStyle(view.button().style());
      tButton.setUrl(view.button().url());
      tButton.setMethod(view.button().method());
      tButton.setOauthServiceName(view.button().oauth().serviceName());
      tButton.setOauthUser(view.button().oauth().user());
      tButton.setOauthPassword(view.button().oauth().password());
      tButton.setOauthClientId(view.button().oauth().clientId());
      tButton.setOauthClientSecret(view.button().oauth().clientSecret());
      tButton.setOauthScope(view.button().oauth().scope());
      tButton.setOauthAuthorizationUri(view.button().oauth().authorizationUri());
      tButton.setOauthTokenUri(view.button().oauth().tokenUri());
      tButton.setOauthFlags(view.button().oauth().flags());
      tButton.setEnabledMethod(view.button().enabledMethod());
      tButton.setDependsOn(view.button().dependsOn());
      Map<String, String> parameters = new HashMap<>();

      for(Button.Parameter parameter : view.button().oauth().additionalParameters()) {
         parameters.put(parameter.from(), parameter.to());
      }

      tButton.setOauthAdditionalParameters(parameters);
      return tButton;
   }

   private Class<?> wrapPrimitive(Class<?> cls) {
      if(cls == boolean.class) {
         return Boolean.class;
      }
      else if(cls == byte.class) {
         return Byte.class;
      }
      else if(cls == char.class) {
         return Character.class;
      }
      else if(cls == short.class) {
         return Short.class;
      }
      else if(cls == int.class) {
         return Integer.class;
      }
      else if(cls == long.class) {
         return Long.class;
      }
      else if(cls == double.class) {
         return Double.class;
      }
      else if(cls == float.class) {
         return Float.class;
      }
      else {
         return Void.class;
      }
   }

   private void setValues(TabularView[] views) {
      for(TabularView tView : views) {
         if(tView.getEditor() != null) {
            String propName = tView.getValue();
            PropertyMeta propertyMeta = pmap.get(propName);

            if(propertyMeta != null) {
               Object value = propertyMeta.getValue(bean);

               if(value != null) {
                  tView.getEditor().setValue(value);
               }
            }
         }

         setValues(tView.getViews());
      }
   }

   private Map<String, PropertyMeta> pmap = new HashMap<>();
   private Object bean;

   private static final Logger LOG =
      LoggerFactory.getLogger(LayoutCreator.class);
}
