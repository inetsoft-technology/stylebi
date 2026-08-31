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


import java.util.*;

/**
 * A machine-readable description of the parameters needed to build a
 * {@link TabularQuery}, produced by {@link TabularSchemaExtractor}.
 *
 * <p>This is the input contract a caller fills in; it is deliberately not the
 * {@link TabularView} tree. A TabularView carries layout (row/col/span/padding/
 * alignment) that a form renderer needs and a caller does not, and it is rebuilt
 * on every round trip. This schema carries only what is needed to decide what to
 * put in a parameter map.
 *
 * <p>Two things this schema does <em>not</em> cover, because they are not
 * {@code @Property} state on the query bean:
 * <ul>
 *    <li>the data source itself — it selects <em>which</em> query class is used,
 *        so it is chosen before a schema can be extracted at all;</li>
 *    <li>per-column type/format overrides, which live in the query's own
 *        {@code typemap}/{@code fmtmap} and are serialized separately.</li>
 * </ul>
 */
public class TabularQuerySchema {
   public String getDataSourceType() {
      return dataSourceType;
   }

   public void setDataSourceType(String dataSourceType) {
      this.dataSourceType = dataSourceType;
   }

   public String getQueryClass() {
      return queryClass;
   }

   public void setQueryClass(String queryClass) {
      this.queryClass = queryClass;
   }

   /**
    * All parameters, in the order the layout presents them, with any that the
    * {@code @View} annotation never references appended at the end.
    */
   public List<Param> getParams() {
      return params;
   }

   public void setParams(List<Param> params) {
      this.params = params;
   }

   /**
    * Which parameters become relevant for each value of an enumerated parameter.
    *
    * <p>Keyed by the name of the enumerated ("axis") parameter, then by its value;
    * the list holds the names of the parameters whose visibility that value turns
    * on. Parameters visible under every value of an axis are omitted, since they
    * do not actually depend on it.
    *
    * <p>Produced by probing, not by reading source: see
    * {@link TabularSchemaExtractor#buildDependencyMatrix}.
    */
   public Map<String, Map<String, List<String>>> getDependencyMatrix() {
      return dependencyMatrix;
   }

   public void setDependencyMatrix(Map<String, Map<String, List<String>>> dependencyMatrix) {
      this.dependencyMatrix = dependencyMatrix;
   }

   /**
    * Free-standing explanatory text from the {@code @View} layout that could not
    * be attached to any one parameter.
    */
   public List<String> getNotes() {
      return notes;
   }

   public void setNotes(List<String> notes) {
      this.notes = notes;
   }

   /**
    * Names of parameters that carry {@code @Property} but are never referenced by
    * the {@code @View} annotation, and so have no editor, no group and no
    * visibility condition. They remain settable.
    */
   public List<String> getUnreferencedParams() {
      return unreferencedParams;
   }

   public void setUnreferencedParams(List<String> unreferencedParams) {
      this.unreferencedParams = unreferencedParams;
   }

   public Param getParam(String name) {
      return params.stream()
         .filter(p -> Objects.equals(p.getName(), name))
         .findFirst()
         .orElse(null);
   }

   private String dataSourceType;
   private String queryClass;
   private List<Param> params = new ArrayList<>();
   private Map<String, Map<String, List<String>>> dependencyMatrix = new LinkedHashMap<>();
   private List<String> notes = new ArrayList<>();
   private List<String> unreferencedParams = new ArrayList<>();

   /**
    * One settable parameter on the query bean.
    */
   public static class Param {
      public String getName() {
         return name;
      }

      public void setName(String name) {
         this.name = name;
      }

      /**
       * The parameter's description. Sourced from {@code @Property(label=...)},
       * resolved through the connector's resource bundle, and falling back to the
       * property name. This is the only description the framework carries —
       * {@code @Property} has no separate documentation attribute.
       */
      public String getLabel() {
         return label;
      }

      public void setLabel(String label) {
         this.label = label;
      }

      /**
       * Fully qualified name of the bean property type. This is the authoritative
       * type: the editor type below is a rendering hint that silently degrades to
       * TEXT for classes the core classloader cannot see.
       */
      public String getJavaType() {
         return javaType;
      }

      public void setJavaType(String javaType) {
         this.javaType = javaType;
      }

      public String getEditorType() {
         return editorType;
      }

      public void setEditorType(String editorType) {
         this.editorType = editorType;
      }

      public String getEditorSubtype() {
         return editorSubtype;
      }

      public void setEditorSubtype(String editorSubtype) {
         this.editorSubtype = editorSubtype;
      }

      /**
       * Whether {@code @Property} declares the parameter required.
       *
       * <p>Read this as "required whenever it applies", not "always required". A
       * parameter that only applies under one value of some other parameter is
       * normally left unmarked here even though it is mandatory once that value is
       * chosen, because the annotation has no way to say so. Use
       * {@link TabularQuerySchema#getDependencyMatrix} to find what applies, and
       * expect the connector's own runtime to enforce the rest.
       */
      public boolean isRequired() {
         return required;
      }

      public void setRequired(boolean required) {
         this.required = required;
      }

      public boolean isPassword() {
         return password;
      }

      public void setPassword(boolean password) {
         this.password = password;
      }

      public Double getMin() {
         return min;
      }

      public void setMin(Double min) {
         this.min = min;
      }

      public Double getMax() {
         return max;
      }

      public void setMax(Double max) {
         this.max = max;
      }

      public List<String> getPattern() {
         return pattern;
      }

      public void setPattern(List<String> pattern) {
         this.pattern = pattern;
      }

      /**
       * The allowed values, when they are fixed. Empty when there is no fixed set
       * or when the values are only known at runtime — see {@link #getTagsMethod}.
       */
      public List<String> getTags() {
         return tags;
      }

      public void setTags(List<String> tags) {
         this.tags = tags;
      }

      public List<String> getTagLabels() {
         return tagLabels;
      }

      public void setTagLabels(List<String> tagLabels) {
         this.tagLabels = tagLabels;
      }

      /**
       * Non-empty when the allowed values are fetched at runtime (often over the
       * network) rather than fixed. A schema extracted offline cannot list them;
       * the query dialog's refreshView round trip can.
       */
      public String getTagsMethod() {
         return tagsMethod;
      }

      public void setTagsMethod(String tagsMethod) {
         this.tagsMethod = tagsMethod;
      }

      public List<String> getDependsOn() {
         return dependsOn;
      }

      public void setDependsOn(List<String> dependsOn) {
         this.dependsOn = dependsOn;
      }

      public String getEnabledMethod() {
         return enabledMethod;
      }

      public void setEnabledMethod(String enabledMethod) {
         this.enabledMethod = enabledMethod;
      }

      /**
       * The bean method that decides whether this parameter applies, if any.
       * The method name alone does not say under which values it returns true;
       * {@link TabularQuerySchema#getDependencyMatrix} answers that.
       */
      public String getVisibleMethod() {
         return visibleMethod;
      }

      public void setVisibleMethod(String visibleMethod) {
         this.visibleMethod = visibleMethod;
      }

      /**
       * True when this parameter, or a panel containing it, has a visibility
       * condition — that is, when it does not always apply.
       */
      public boolean isConditional() {
         return conditional;
      }

      public void setConditional(boolean conditional) {
         this.conditional = conditional;
      }

      /**
       * The {@code @View} panel this parameter sits in, if the layout groups it.
       */
      public String getGroup() {
         return group;
      }

      public void setGroup(String group) {
         this.group = group;
      }

      /**
       * Explanatory text the layout places next to this parameter — usually a
       * format example. These come from LABEL elements in {@code @View} and are
       * not reachable by reflecting over {@code @Property} alone.
       */
      public List<String> getHints() {
         return hints;
      }

      public void setHints(List<String> hints) {
         this.hints = hints;
      }

      /**
       * For a parameter whose value is a composite (or a list of composites), the
       * parameters of that element type. Empty for scalars.
       */
      public List<Param> getElementParams() {
         return elementParams;
      }

      public void setElementParams(List<Param> elementParams) {
         this.elementParams = elementParams;
      }

      @Override
      public String toString() {
         return name + " (" + javaType + ")";
      }

      private String name;
      private String label;
      private String javaType;
      private String editorType;
      private String editorSubtype;
      private boolean required;
      private boolean password;
      private Double min;
      private Double max;
      private List<String> pattern = new ArrayList<>();
      private List<String> tags = new ArrayList<>();
      private List<String> tagLabels = new ArrayList<>();
      private String tagsMethod;
      private List<String> dependsOn = new ArrayList<>();
      private String enabledMethod;
      private String visibleMethod;
      private boolean conditional;
      private String group;
      private List<String> hints = new ArrayList<>();
      private List<Param> elementParams = new ArrayList<>();
   }
}
