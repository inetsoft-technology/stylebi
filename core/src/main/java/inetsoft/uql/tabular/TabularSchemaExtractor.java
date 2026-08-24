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
import inetsoft.uql.util.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;

/**
 * Derives a {@link TabularQuerySchema} — the parameters needed to build a query,
 * their types, their descriptions, and which of them apply when — from a
 * connector's query class.
 *
 * <p>Nothing here is connector-specific. Everything comes from three sources the
 * connector already provides:
 *
 * <ol>
 *    <li>reflection over {@code @Property}, which gives the authoritative set of
 *        settable parameters along with their types and validation;</li>
 *    <li>the {@code @View} layout, which gives descriptions resolved through the
 *        connector's resource bundle, grouping, and the format examples that sit
 *        beside a field as LABEL elements;</li>
 *    <li>probing, which gives the dependency matrix — see
 *        {@link #buildDependencyMatrix}.</li>
 * </ol>
 *
 * <p>Reflection and layout are combined rather than one being preferred: a class
 * that declares {@code @View} gets a tree covering only the properties that
 * annotation references, but any other {@code @Property} is still settable, so the
 * reflected set is the one that decides membership and the tree only enriches it.
 */
public class TabularSchemaExtractor {
   /**
    * Extracts the schema for a data source type, resolving the query class through
    * the connector's own classloader.
    *
    * @param dataSourceType the registered type key, e.g. {@code "Rest.GitHub"}
    *
    * @return the schema, or {@code null} if the type is not registered or its
    *         query class cannot be instantiated
    */
   public TabularQuerySchema extract(String dataSourceType) {
      try {
         Config config = Config.getConfig();
         String queryClass = config.getQueryClass(dataSourceType);

         if(queryClass == null) {
            LOG.debug("No query class registered for tabular type: {}", dataSourceType);
            return null;
         }

         // through the connector's classloader -- Class.forName would not find it
         Class<?> cls = config.getClass(dataSourceType, queryClass);
         TabularQuery prototype = (TabularQuery) cls.getDeclaredConstructor().newInstance();

         return extract(prototype, dataSourceType);
      }
      catch(Exception ex) {
         LOG.warn("Failed to extract tabular query schema for type: " + dataSourceType, ex);
         return null;
      }
   }

   /**
    * Extracts the schema from a query instance. The instance is read, and copies of
    * its class are created for probing, but the instance itself is not modified.
    */
   public TabularQuerySchema extract(TabularQuery prototype, String dataSourceType) {
      Class<?> cls = prototype.getClass();
      TabularQuerySchema schema = new TabularQuerySchema();
      schema.setDataSourceType(dataSourceType != null ? dataSourceType : prototype.getType());
      schema.setQueryClass(cls.getName());

      // reflection decides membership
      Map<String, TabularQuerySchema.Param> byName = new LinkedHashMap<>();

      for(PropertyMeta prop : TabularUtil.findProperties(cls)) {
         byName.put(prop.getName(), createParam(prop, new HashSet<>(), 0));
      }

      // the layout enriches, and gives the presentation order
      List<String> ordered = new ArrayList<>();
      TabularView root = new LayoutCreator().createLayout(prototype);
      enrich(root.getViews(), byName, ordered, schema.getNotes(), null, false);

      List<TabularQuerySchema.Param> params = new ArrayList<>();

      for(String name : ordered) {
         params.add(byName.get(name));
      }

      // properties the @View annotation never mentions are still settable
      for(Map.Entry<String, TabularQuerySchema.Param> e : byName.entrySet()) {
         if(!ordered.contains(e.getKey())) {
            params.add(e.getValue());
            schema.getUnreferencedParams().add(e.getKey());
         }
      }

      schema.setParams(params);
      schema.setDependencyMatrix(buildDependencyMatrix(cls, prototype.getDataSource(), params));

      return schema;
   }

   /**
    * Of the named parameters, those that do not apply to the query as it currently stands.
    *
    * <p>The counterpart of the dependency matrix, asked of one configured query rather than of a
    * class: it answers "given what is already set, is this parameter read?". Setting one that is
    * not is the failure mode a caller cannot see — the value is stored on the bean and never looked
    * at, so the request goes out as though it had never been given and the result is a plausible
    * wrong answer rather than an error.
    *
    * <p>A name this class does not recognize is not reported here. Whether a parameter exists is a
    * different question from whether it applies, and the caller checks it against the schema.
    */
   public Set<String> findInapplicable(TabularQuery query, Collection<String> names) {
      Set<String> inapplicable = new LinkedHashSet<>();

      if(names == null || names.isEmpty()) {
         return inapplicable;
      }

      try {
         TabularView root = new LayoutCreator().createLayout(query);
         TabularUtil.callViewMethods(root.getViews(), query);

         Probe visible = new Probe();
         collectVisible(root.getViews(), true, false, visible);

         for(String name : names) {
            // Only parameters the layout actually places can be judged. One the @View annotation
            // never references has no visibility condition to evaluate, so there is no ground to
            // call it inapplicable.
            if(visible.known.contains(name) && !visible.allVisible.contains(name)) {
               inapplicable.add(name);
            }
         }
      }
      catch(Exception ex) {
         // Reporting nothing is the safe direction: this check exists to warn, and a check that
         // could not run must not turn into a claim that every parameter is wrong.
         LOG.debug("Failed to evaluate parameter applicability for " + query.getClass(), ex);
      }

      return inapplicable;
   }

   /**
    * Builds the map from an enumerated parameter's value to the parameters that
    * value turns on.
    *
    * <p>This is done by probing rather than by reading the connector's source. For
    * each candidate value, a throwaway query is created, the value is set, a layout
    * is built, and {@link TabularUtil#callViewMethods} is asked to evaluate the
    * visibility conditions. That call only invokes {@code visibleMethod} and
    * {@code enabledMethod}, so probing stays local: it does not fetch dropdown
    * contents and does not reach the network.
    *
    * <p>Probing beats parsing the condition methods because it reports what those
    * methods actually return. A condition that ORs two cases, or that reads a
    * second parameter as well, needs no special handling here — it simply produces
    * the answer it produces.
    *
    * <p>Two passes. The first varies one parameter at a time. Any parameter that is
    * conditional but never appeared in that pass is gated on a combination rather
    * than a single value, so a second pass varies pairs, looking only for those.
    * Parameters that stay visible across every value of an axis are dropped from
    * that axis: they are not gated on it.
    */
   Map<String, Map<String, List<String>>> buildDependencyMatrix(
      Class<?> cls, XDataSource dataSource, List<TabularQuerySchema.Param> params)
   {
      Map<String, Map<String, List<String>>> matrix = new LinkedHashMap<>();
      List<Axis> axes = findAxes(params);
      Probe baseline = probe(cls, dataSource, Collections.emptyMap());

      if(axes.isEmpty() || baseline == null) {
         return matrix;
      }

      Map<String, Map<String, List<String>>> firstPass = new LinkedHashMap<>();

      for(Axis axis : axes) {
         Map<String, Set<String>> raw = new LinkedHashMap<>();

         for(Object value : axis.values) {
            Probe result = probe(cls, dataSource, Collections.singletonMap(axis.name, value));

            if(result == null || isSelfGating(axis.name, baseline, result)) {
               raw = null;
               break;
            }

            raw.put(String.valueOf(value), result.conditionalVisible);
         }

         Map<String, List<String>> gated = dropUngated(raw);

         if(!gated.isEmpty()) {
            firstPass.put(axis.name, gated);
         }
      }

      matrix.putAll(firstPass);
      addCombinationGates(cls, dataSource, params, axes, firstPass, matrix);

      return matrix;
   }

   /**
    * Second probing pass, for parameters gated on two values at once.
    *
    * <p>Rather than trying every pair of parameters, this follows the shape such a
    * gate actually has: the second parameter is itself only relevant under the
    * first. A link relation matters only for a link-header link parameter, and a
    * link parameter matters only under link pagination — so the pairs worth trying
    * are exactly those the first pass already found, paired with the enumerated
    * parameters that same value turns on. That keeps this to a few dozen probes
    * instead of a combinatorial sweep, and it looks precisely where a second-level
    * gate can be.
    *
    * <p>A parameter gated on three conditions at once, or on two unrelated ones,
    * is still missed. It then appears in the schema as conditional, with a
    * visibility method and no matrix entry — which says "this applies sometimes,
    * and the condition is not described here" rather than claiming it never
    * applies.
    */
   private void addCombinationGates(Class<?> cls, XDataSource dataSource,
                                    List<TabularQuerySchema.Param> params, List<Axis> axes,
                                    Map<String, Map<String, List<String>>> firstPass,
                                    Map<String, Map<String, List<String>>> matrix)
   {
      Set<String> reached = new HashSet<>();
      firstPass.values().forEach(rows -> rows.values().forEach(reached::addAll));

      Set<String> unreached = new LinkedHashSet<>();

      for(TabularQuerySchema.Param param : params) {
         if(param.isConditional() && !reached.contains(param.getName())) {
            unreached.add(param.getName());
         }
      }

      if(unreached.isEmpty()) {
         return;
      }

      Map<String, Axis> byName = new HashMap<>();
      axes.forEach(axis -> byName.put(axis.name, axis));

      for(Map.Entry<String, Map<String, List<String>>> outer : firstPass.entrySet()) {
         for(Map.Entry<String, List<String>> row : outer.getValue().entrySet()) {
            for(String gatedName : row.getValue()) {
               Axis inner = byName.get(gatedName);

               if(inner == null || unreached.isEmpty()) {
                  continue;
               }

               Map<String, List<String>> found = new LinkedHashMap<>();

               for(Object value : inner.values) {
                  Map<String, Object> combo = new LinkedHashMap<>();
                  combo.put(outer.getKey(), row.getKey());
                  combo.put(inner.name, value);
                  Probe result = probe(cls, dataSource, combo);

                  if(result == null) {
                     continue;
                  }

                  Set<String> visible = result.conditionalVisible;
                  visible.retainAll(unreached);

                  if(!visible.isEmpty()) {
                     found.put(outer.getKey() + "=" + row.getKey() + " & " +
                                  inner.name + "=" + value,
                               new ArrayList<>(visible));
                  }
               }

               if(!found.isEmpty()) {
                  matrix.put(outer.getKey() + " & " + inner.name, found);
                  found.values().forEach(unreached::removeAll);
               }
            }
         }
      }
   }

   /**
    * Whether setting a parameter is what makes that parameter itself appear.
    *
    * <p>Some setters have side effects beyond the property — growing the list a
    * panel's visibility is computed from, for instance. Probing such a parameter
    * reports its own panel turning on, along with every sibling in it, and reads as
    * "this parameter gates its neighbours" when nothing of the sort is true: the
    * real gate is elsewhere, usually a button. Comparing against the baseline
    * catches it, and the axis is dropped rather than described wrongly.
    */
   private boolean isSelfGating(String axis, Probe baseline, Probe result) {
      return !baseline.allVisible.contains(axis) && result.allVisible.contains(axis);
   }

   /**
    * Sets the given values on a fresh query and evaluates the visibility conditions.
    *
    * @return what is visible under those values, or {@code null} if the probe could
    *         not be run
    */
   private Probe probe(Class<?> cls, XDataSource dataSource, Map<String, Object> values) {
      try {
         Object probe = cls.getDeclaredConstructor().newInstance();

         // The probe has to stand where the real query stands. A visibility condition may read the
         // data source -- a connector varies what it offers by which account it is pointed at --
         // and a probe built without one would answer for a query nobody is going to run.
         if(dataSource != null && probe instanceof TabularQuery) {
            ((TabularQuery) probe).setDataSource(dataSource);
         }

         Map<String, PropertyMeta> pmap = TabularUtil.getPropertyMap(cls);

         for(Map.Entry<String, Object> e : values.entrySet()) {
            PropertyMeta prop = pmap.get(e.getKey());

            if(prop == null) {
               return null;
            }

            prop.setValue(probe, e.getValue());
         }

         TabularView root = new LayoutCreator().createLayout(probe);
         TabularUtil.callViewMethods(root.getViews(), probe);

         Probe result = new Probe();
         collectVisible(root.getViews(), true, false, result);
         values.keySet().forEach(result.conditionalVisible::remove);

         return result;
      }
      catch(Exception ex) {
         LOG.debug("Failed to probe visibility for {} with {}", cls.getName(), values, ex);
         return null;
      }
   }

   /**
    * A view is only really shown when every panel containing it is shown too, so
    * visibility is carried down rather than read off each node. The same applies to
    * being conditional: a field with no condition of its own inside a conditional
    * panel is still conditional.
    */
   private void collectVisible(TabularView[] views, boolean ancestorVisible,
                               boolean ancestorConditional, Probe out)
   {
      if(views == null) {
         return;
      }

      for(TabularView view : views) {
         String method = view.getVisibleMethod();
         boolean conditional = ancestorConditional || method != null && !method.isEmpty();
         boolean visible = ancestorVisible && view.isVisible();

         if(view.getEditor() != null && view.getValue() != null) {
            out.known.add(view.getValue());

            if(visible) {
               out.allVisible.add(view.getValue());

               // a parameter with no condition anywhere above it applies regardless, so
               // listing it under every value of an axis would say nothing
               if(conditional) {
                  out.conditionalVisible.add(view.getValue());
               }
            }
         }

         collectVisible(view.getViews(), visible, conditional, out);
      }
   }

   /**
    * Keeps only the parameters an axis actually gates. One that is visible for
    * every value of the axis is visible for reasons of its own, and saying it
    * depends on the axis would be wrong.
    */
   private Map<String, List<String>> dropUngated(Map<String, Set<String>> raw) {
      Map<String, List<String>> gated = new LinkedHashMap<>();

      if(raw == null || raw.isEmpty()) {
         return gated;
      }

      Set<String> everywhere = null;

      for(Set<String> visible : raw.values()) {
         if(everywhere == null) {
            everywhere = new HashSet<>(visible);
         }
         else {
            everywhere.retainAll(visible);
         }
      }

      for(Map.Entry<String, Set<String>> e : raw.entrySet()) {
         List<String> names = new ArrayList<>(e.getValue());
         names.removeAll(everywhere);
         gated.put(e.getKey(), names);
      }

      boolean any = gated.values().stream().anyMatch(names -> !names.isEmpty());

      return any ? gated : new LinkedHashMap<>();
   }

   /**
    * The parameters worth varying: those with a fixed set of values. Booleans count
    * — they gate as often as enumerations do. Values fetched at runtime do not,
    * since they are not known here.
    */
   private List<Axis> findAxes(List<TabularQuerySchema.Param> params) {
      List<Axis> axes = new ArrayList<>();

      for(TabularQuerySchema.Param param : params) {
         boolean runtimeTags = param.getTagsMethod() != null && !param.getTagsMethod().isEmpty();

         if(!param.getTags().isEmpty() && !runtimeTags) {
            axes.add(new Axis(param.getName(), new ArrayList<Object>(param.getTags())));
         }
         else if("boolean".equals(param.getJavaType()) ||
                 Boolean.class.getName().equals(param.getJavaType()))
         {
            axes.add(new Axis(param.getName(), Arrays.<Object>asList(Boolean.TRUE, Boolean.FALSE)));
         }
      }

      return axes;
   }

   private TabularQuerySchema.Param createParam(PropertyMeta prop, Set<Class<?>> seen, int depth) {
      TabularQuerySchema.Param param = new TabularQuerySchema.Param();
      param.setName(prop.getName());
      param.setLabel(prop.getDisplayLabel());

      Class<?> type = prop.getDescriptor().getPropertyType();
      param.setJavaType(type == null ? null : type.getName());

      Property property = prop.getProperty();

      if(property != null) {
         param.setRequired(property.required());
         param.setPassword(property.password());
         param.setMin(Double.isNaN(property.min()) ? null : property.min());
         param.setMax(Double.isNaN(property.max()) ? null : property.max());
         param.setPattern(Arrays.asList(property.pattern()));
      }

      PropertyEditor editor = prop.getEditor();

      if(editor != null) {
         param.setTags(Arrays.asList(editor.tags()));
         param.setTagLabels(Arrays.asList(editor.labels()));
         param.setTagsMethod(emptyToNull(editor.tagsMethod()));
         param.setDependsOn(Arrays.asList(editor.dependsOn()));
         param.setEnabledMethod(emptyToNull(editor.enabledMethod()));
      }

      param.setElementParams(extractElementParams(type, seen, depth));

      return param;
   }

   /**
    * A parameter can hold a composite, or a list of them — an HTTP parameter, a
    * query parameter, a column definition. Filling one in means filling in its own
    * parameters, so they are described here too.
    */
   private List<TabularQuerySchema.Param> extractElementParams(Class<?> type, Set<Class<?>> seen,
                                                               int depth)
   {
      List<TabularQuerySchema.Param> elements = new ArrayList<>();

      if(type == null || depth >= MAX_NESTING) {
         return elements;
      }

      Class<?> element = type.isArray() ? type.getComponentType() : type;

      if(isScalar(element) || !seen.add(element)) {
         return elements;
      }

      try {
         for(PropertyMeta prop : TabularUtil.findProperties(element)) {
            elements.add(createParam(prop, seen, depth + 1));
         }
      }
      catch(Exception ex) {
         LOG.debug("Failed to describe element type: " + element.getName(), ex);
      }

      return elements;
   }

   /**
    * Walks the layout to attach what only the layout knows: the bundle-resolved
    * description, the panel a parameter sits in, its visibility condition, and the
    * examples placed beside it.
    *
    * <p>A LABEL is attached to the field it follows, which is where it is rendered
    * and what it is written to explain. One that follows no field is kept as a note
    * on the schema instead of being dropped.
    */
   private void enrich(TabularView[] views, Map<String, TabularQuerySchema.Param> byName,
                       List<String> ordered, List<String> notes, String group,
                       boolean ancestorConditional)
   {
      if(views == null) {
         return;
      }

      TabularQuerySchema.Param previous = null;

      for(TabularView view : views) {
         String method = view.getVisibleMethod();
         boolean conditional = ancestorConditional || method != null && !method.isEmpty();

         if(view.getType() == ViewType.LABEL) {
            String text = view.getDisplayLabel() != null ? view.getDisplayLabel() : view.getText();

            if(text != null && !text.isEmpty()) {
               if(previous != null) {
                  previous.getHints().add(text);
               }
               else {
                  notes.add(text);
               }
            }

            continue;
         }

         String name = view.getValue();
         TabularQuerySchema.Param param = name == null ? null : byName.get(name);

         if(param != null && view.getEditor() != null) {
            if(!ordered.contains(name)) {
               ordered.add(name);
            }

            if(view.getDisplayLabel() != null && !view.getDisplayLabel().isEmpty()) {
               param.setLabel(view.getDisplayLabel());
            }

            param.setGroup(group);
            param.setVisibleMethod(emptyToNull(method));
            param.setConditional(conditional);
            param.setRequired(param.isRequired() || view.isRequired());
            param.setEditorType(name(view.getEditor().getType()));
            param.setEditorSubtype(name(view.getEditor().getSubtype()));
            previous = param;
         }

         String childGroup = group;

         if(view.getType() == ViewType.PANEL) {
            String text = view.getDisplayLabel() != null ? view.getDisplayLabel() : view.getText();
            childGroup = text != null && !text.isEmpty() ? text : group;
         }

         enrich(view.getViews(), byName, ordered, notes, childGroup, conditional);
      }
   }

   private boolean isScalar(Class<?> cls) {
      return cls == null || cls.isPrimitive() || cls.isEnum() || cls == String.class ||
         Number.class.isAssignableFrom(cls) || cls == Boolean.class || cls == Character.class ||
         cls == File.class || Date.class.isAssignableFrom(cls) || cls.getName().startsWith("java.");
   }

   private String name(Enum<?> value) {
      return value == null ? null : value.name();
   }

   private String emptyToNull(String value) {
      return value == null || value.isEmpty() ? null : value;
   }

   /**
    * One parameter to vary while probing, and the values to try.
    */
   private static final class Axis {
      Axis(String name, List<Object> values) {
         this.name = name;
         this.values = values;
      }

      private final String name;
      private final List<Object> values;
   }

   /**
    * What one probe saw.
    */
   private static final class Probe {
      /** every parameter the layout places, whether or not it is currently shown */
      private final Set<String> known = new LinkedHashSet<>();
      private final Set<String> allVisible = new LinkedHashSet<>();
      private final Set<String> conditionalVisible = new LinkedHashSet<>();
   }

   private static final int MAX_NESTING = 2;

   private static final Logger LOG = LoggerFactory.getLogger(TabularSchemaExtractor.class);
}
