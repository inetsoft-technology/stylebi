/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
package inetsoft.web.wiz.controller;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import java.io.IOException;

import java.util.List;

/**
 * The logical model wire records of the data model bridge (design 3.4 and 4.1), owned by lane J2.
 *
 * <p>The definition is the canonical (alias-folded) shape: the plugin folds aliases, the bridge
 * never sees one. Every record refuses an unknown key (a 400 naming it) so a wrong-keyed field is
 * never silently dropped (charter C8).</p>
 *
 * <p>{@code oldName} is the rename marker of an entity or attribute. It has three states that the
 * JSON distinguishes: absent (no marker: identity when the stored model has that name, otherwise a
 * new element, and a removed+added look-alike pair is refused), an explicit JSON {@code null}
 * (deserialized to the empty string by {@link OldNameDeserializer}: an explicit new element), and a
 * name (a rename of, or identity with, that stored element).</p>
 */
public final class WizLogicalModelWire {
   private WizLogicalModelWire() {
   }

   /** {@code type} values of an attribute. */
   public static final String TYPE_COLUMN = "column";
   public static final String TYPE_EXPRESSION = "expression";

   /** {@code refType} values of an attribute, in the order of the server's 0, 1, 2. */
   public static final List<String> REF_TYPES = List.of("none", "dimension", "measure");

   /** {@code linkType} values of a drill path (server 1 and 8). */
   public static final String LINK_WEB = "web";
   public static final String LINK_VIEWSHEET = "viewsheet";

   /** The marker of an explicit {@code "oldName": null} after deserialization. */
   public static final String EXPLICIT_NEW = "";

   /**
    * Reads {@code oldName}: a string as is, an explicit JSON null as {@link #EXPLICIT_NEW}, and an
    * ABSENT member as null (Jackson's default would give an absent member the null value too,
    * which is exactly the distinction this marker needs).
    */
   public static final class OldNameDeserializer extends StdDeserializer<String> {
      public OldNameDeserializer() {
         super(String.class);
      }

      @Override
      public String deserialize(JsonParser parser, DeserializationContext context)
         throws IOException
      {
         return parser.getValueAsString();
      }

      @Override
      public String getNullValue(DeserializationContext context) {
         return EXPLICIT_NEW;
      }

      @Override
      public Object getAbsentValue(DeserializationContext context) {
         return null;
      }
   }

   @JsonIgnoreProperties(ignoreUnknown = false)
   public record GetRequest(String database, String name, String parent) {
   }

   @JsonIgnoreProperties(ignoreUnknown = false)
   public record CreateRequest(String database, Definition definition, Boolean dryRun) {
   }

   @JsonIgnoreProperties(ignoreUnknown = false)
   public record UpdateRequest(String database, String name, Definition definition,
                               Boolean dryRun, String baseRevision)
   {
      /** Without a base revision: the write is not checked for a concurrent change. */
      public UpdateRequest(String database, String name, Definition definition, Boolean dryRun) {
         this(database, name, definition, dryRun, null);
      }
   }

   @JsonIgnoreProperties(ignoreUnknown = false)
   public record Definition(String name, String description, String view, String folder,
                            String connection, String parent, Boolean extended,
                            List<Entity> entities)
   {
   }

   @JsonIgnoreProperties(ignoreUnknown = false)
   public record Entity(String name,
                        @JsonDeserialize(using = OldNameDeserializer.class) String oldName,
                        String description, Boolean visible, Boolean baseElement,
                        String errorMessage, List<Attribute> attributes)
   {
   }

   @JsonIgnoreProperties(ignoreUnknown = false)
   public record Attribute(String name,
                           @JsonDeserialize(using = OldNameDeserializer.class) String oldName,
                           String description, Boolean visible, String type, String table,
                           String column, String expression, Boolean aggregate,
                           Boolean parseable, String dataType, String refType, String formula,
                           Boolean browseData, String browseQuery, Format format, Drill drill,
                           Boolean baseElement, String errorMessage)
   {
   }

   @JsonIgnoreProperties(ignoreUnknown = false)
   public record Format(String format, String formatSpec, Boolean durationPadZeros) {
   }

   @JsonIgnoreProperties(ignoreUnknown = false)
   public record Drill(List<DrillPath> paths) {
   }

   @JsonIgnoreProperties(ignoreUnknown = false)
   public record DrillPath(String name, String linkType, String link, String targetFrame,
                           String tip, Boolean passParams, Boolean disablePrompting,
                           List<DrillParam> params, DrillQuery query, List<String> queryFields)
   {
   }

   @JsonIgnoreProperties(ignoreUnknown = false)
   public record DrillParam(String name, String field, String type) {
   }

   /**
    * A drill path's worksheet sub-query: the worksheet entry as its identifier string (it
    * round-trips exactly) and the parameter bindings.
    */
   @JsonIgnoreProperties(ignoreUnknown = false)
   public record DrillQuery(String entry, List<DrillQueryParam> params) {
   }

   @JsonIgnoreProperties(ignoreUnknown = false)
   public record DrillQueryParam(String key, String value) {
   }

   public record GetResponse(boolean ok, Definition definition, String folder,
                             boolean readOnly, String revision)
   {
   }

   @JsonInclude(JsonInclude.Include.NON_NULL)
   public record CreateResponse(boolean ok, String name, String folder, Definition definition,
                                List<String> warnings, String revision)
   {
   }

   /** One element's recorded dependents (the element is {@code Entity} or {@code Entity.Attr}). */
   public record ElementDependents(String element,
                                   List<WizDataModelWire.Dependent> dependents)
   {
   }

   @JsonInclude(JsonInclude.Include.NON_NULL)
   public record UpdateResponse(boolean ok, Definition definition, List<String> warnings,
                                List<ElementDependents> elementDependents,
                                List<WizDataModelWire.Dependent> viewDependents,
                                String revision)
   {
   }
}
