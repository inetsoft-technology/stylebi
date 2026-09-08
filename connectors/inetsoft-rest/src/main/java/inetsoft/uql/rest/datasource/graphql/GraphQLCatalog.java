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
package inetsoft.uql.rest.datasource.graphql;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.tabular.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Turns one GraphQL introspection response's {@code data.__schema} node into the neutral
 * {@link TabularCatalogProvider} types. This is the ONLY place in this module that reads
 * introspection's {@code __Type}/{@code __Field}/{@code __InputValue} shape for the annotation
 * catalog -- everything downstream deals in {@code Tabular*} records only, never {@link JsonNode}.
 * Pure functions, no I/O -- see {@link GraphQLIntrospectionClient} for the request/response shell.
 *
 * <h2>Rule A -- what is a dataset</h2>
 * A dataset is a root {@code Query} field that returns a Relay-style connection (structurally
 * detected: the field's named type has an {@code edges} field whose item type has a {@code node}
 * field -- never a {@code *Connection} name test, and {@code pageInfo} is NOT required) or a plain
 * list (`[Foo!]!`, no connection wrapper). A single-object lookup (`customer(id: ID!): Customer`)
 * or a singleton (`shop: Shop`) is never a dataset -- there is no batch of rows to pull. What this
 * rule discards for one dataset (its nested connections, their target types, which edges were
 * dropped for having no root field) is recorded in that dataset's own
 * {@link TabularDatasetSchema#description()} as connector-composed STRUCTURAL notes -- see that
 * field's own javadoc for the carve-out this relies on, and why it is not the same thing as
 * inventing what the dataset MEANS.
 *
 * <h2>Custom scalars are never name-sniffed</h2>
 * Every GraphQL scalar outside the spec's own 5 built-ins ({@code ID}/{@code String}/{@code Int}/
 * {@code Float}/{@code Boolean}) lands on the SAME named default (see {@link #mapScalar}), with a
 * loud report -- never a per-name guess (`"DateTime"` -&gt; a time type, etc.). A custom scalar
 * name carries no cross-vendor contract on its wire shape: one API's {@code DateTime} may
 * serialize as an ISO-8601 string, another's as a Unix epoch integer, and the GraphQL spec
 * declines to say. Guessing would be indistinguishable, on a wrong guess, from the exact silent
 * wrong-type failure this reporting exists to prevent -- unlike Salesforce's {@code FieldType} or
 * GA4's dimension/metric lists, which are closed, vendor-published enumerations with documented
 * wire semantics, a GraphQL custom scalar is just an open string with no such contract.
 *
 * <h2>Depth limits, both deliberate and both degrade safely</h2>
 * <ul>
 *   <li>The {@code NON_NULL}/{@code LIST} {@code ofType} unwrap chain ({@link #unwrap}) is bounded
 *       to {@value #MAX_UNWRAP_DEPTH} layers, matching how deep this connector's own introspection
 *       query resolves {@code ofType}. A type nested deeper unwraps to an unrecognized kind/name
 *       and is treated as an unresolved type (the same named-default path as an unknown scalar),
 *       never a crash.</li>
 *   <li>Nested-object ("value object") flattening ({@link #flattenValueObject}) is exactly ONE
 *       level -- {@code totalPrice.tax.rate} is not reachable. A doubly-nested value object is
 *       dropped at DEBUG, not recursed into and not crashed.</li>
 * </ul>
 */
final class GraphQLCatalog {
   private GraphQLCatalog() {
   }

   /**
    * @param schemaRoot the {@code data.__schema} node {@link GraphQLIntrospectionClient#introspect}
    *                    returns.
    */
   static TabularCatalog listDatasets(JsonNode schemaRoot) {
      SchemaIndex index = SchemaIndex.build(schemaRoot);
      List<TabularDatasetRef> datasets = index.datasetOrder.stream()
         .map(TabularDatasetRef::new)
         .collect(Collectors.toUnmodifiableList());

      // A LinkedHashSet, not a List: a to-many field with a back-reference (analyzeManyReference)
      // and that SAME back-reference field's own independent to-one processing (analyzeOneReference,
      // reached when the loop below visits the CHILD dataset's own fields) compute the identical
      // TabularRelationship -- same name, same endpoints, same columns -- because they describe the
      // same edge from two directions with no protocol signal telling either side "the other side
      // will also report this". Deduplicated by full record equality, never by name alone: two
      // DIFFERENT relationships that happen to collide on NAME (a real defect, see G11) must still
      // surface as two distinct set entries so TabularCatalogService's uniqueness check catches it,
      // rather than one silently swallowing the other here.
      Set<TabularRelationship> relationships = new LinkedHashSet<>();

      // Relationships are derived from the SAME per-field analysis describeDataset uses for
      // columns/description -- see analyzeField. Only the relationship half is kept here.
      for(String datasetId : index.datasetOrder) {
         String nodeTypeName = index.datasetNodeType.get(datasetId);
         JsonNode nodeType = index.typesByName.get(nodeTypeName);

         for(JsonNode field : fieldsOf(nodeType)) {
            FieldAnalysis analysis = analyzeField(datasetId, nodeTypeName, field, index);

            if(analysis.relationship() != null) {
               relationships.add(analysis.relationship());
            }
         }
      }

      return new TabularCatalog(datasets, List.copyOf(relationships));
   }

   /**
    * @param schemaRoot the {@code data.__schema} node {@link GraphQLIntrospectionClient#introspect}
    *                    returns -- a FRESH introspection result; this class caches nothing between
    *                    calls (see {@link GraphQLIntrospectionClient}'s class javadoc for the cost).
    * @param datasetId a root {@code Query} field name previously returned by {@link #listDatasets}.
    */
   static TabularDatasetSchema describeDataset(JsonNode schemaRoot, String datasetId) {
      SchemaIndex index = SchemaIndex.build(schemaRoot);
      String nodeTypeName = index.datasetNodeType.get(datasetId);

      if(nodeTypeName == null) {
         throw new IllegalArgumentException("'" + datasetId + "' is not a valid dataset id for " +
            "this data source (either unknown, or not a root Query field whose result is a " +
            "connection or plain list -- see GraphQLCatalog's Rule A).");
      }

      JsonNode nodeType = index.typesByName.get(nodeTypeName);
      List<TabularColumn> columns = new ArrayList<>();
      List<String> descriptionClauses = new ArrayList<>();

      for(JsonNode field : fieldsOf(nodeType)) {
         FieldAnalysis analysis = analyzeField(datasetId, nodeTypeName, field, index);
         columns.addAll(analysis.columns());

         if(analysis.descriptionClause() != null) {
            descriptionClauses.add(analysis.descriptionClause());
         }
      }

      if(columns.isEmpty()) {
         throw new IllegalStateException("Dataset '" + datasetId + "' (GraphQL type '" +
            nodeTypeName + "') has no scalar/enum columns to report -- cannot annotate.");
      }

      String description = buildDescription(nodeType, descriptionClauses);
      Map<String, String> params = buildParams(index, datasetId);

      // keyColumns is always empty: vanilla introspection declares no primary key, and guessing
      // an ID-kind field is "the" key by name/position would be the exact inference the SPI
      // forbids when the source itself does not say so.
      return new TabularDatasetSchema(datasetId, List.copyOf(columns), List.of(), params, false,
         description);
   }

   // ===================================================================================
   // Per-field analysis, shared by listDatasets (wants only the relationship, if any) and
   // describeDataset (wants columns + description text, never the relationship object itself --
   // TabularDatasetSchema has no relationships field to put it in).
   // ===================================================================================

   private static FieldAnalysis analyzeField(String datasetId, String nodeTypeName, JsonNode field,
                                              SchemaIndex index)
   {
      String fieldName = field.path("name").asText(null);

      if(fieldName == null) {
         return FieldAnalysis.columnsOnly(List.of());
      }

      Unwrapped u = unwrap(field.path("type"));
      String fieldDescription = emptyToNull(field.path("description").asText(null));

      if("SCALAR".equals(u.kind())) {
         return FieldAnalysis.columnsOnly(List.of(
            new TabularColumn(fieldName, mapScalar(u.name()), fieldDescription, null, null)));
      }

      if("ENUM".equals(u.kind())) {
         // An enum value always serializes as a string -- a real, always-textual kind the spec
         // defines, not an "unknown" case, so no WARN here (contrast mapScalar's default branch).
         return FieldAnalysis.columnsOnly(List.of(
            new TabularColumn(fieldName, XSchema.STRING, fieldDescription, null, null)));
      }

      if(u.kind() == null) {
         // The ofType unwrap chain exceeded MAX_UNWRAP_DEPTH -- named default, never a crash.
         LOG.warn("GraphQL field '{}' on type '{}' has a type nested deeper than this " +
            "connector's introspection query resolves ({} NON_NULL/LIST layers); defaulting its " +
            "column type to {}", fieldName, nodeTypeName, MAX_UNWRAP_DEPTH, UNKNOWN_SCALAR_DEFAULT);
         return FieldAnalysis.columnsOnly(List.of(
            new TabularColumn(fieldName, UNKNOWN_SCALAR_DEFAULT, fieldDescription, null, null)));
      }

      if("INTERFACE".equals(u.kind()) || "UNION".equals(u.kind())) {
         // No discriminant channel exists in TabularRelationship to express "could be one of
         // several target datasets" -- dropped unconditionally, same treatment SForceCatalog
         // gives a polymorphic lookup field.
         return FieldAnalysis.withClause(List.of(), fieldName + " -> dropped: target type '" +
            u.name() + "' is polymorphic (interface/union)");
      }

      if(!"OBJECT".equals(u.kind()) || u.name() == null) {
         // INPUT_OBJECT or another kind that should never appear as an OUTPUT field's type in a
         // well-formed schema -- skip silently rather than guess at a column or a clause.
         return FieldAnalysis.columnsOnly(List.of());
      }

      // OBJECT: either a "many" reference (connection-shaped, or a plain list of objects), a
      // "one" reference to another dataset, or a nested value object (flattened, not a dataset).
      boolean manyShaped = u.isList();
      String manyTargetType = u.name();

      if(!manyShaped) {
         ConnectionInfo connection = detectConnectionShape(u.name(), index.typesByName);

         if(connection != null) {
            manyShaped = true;
            manyTargetType = connection.nodeTypeName();
         }
      }

      if(manyShaped) {
         return analyzeManyReference(datasetId, nodeTypeName, fieldName, manyTargetType, index);
      }

      if(index.isDatasetNodeType(u.name())) {
         return analyzeOneReference(datasetId, fieldName, u.name(), index);
      }

      // Neither a "many" shape nor a reference to another dataset -- a nested value object
      // (the totalPrice: MoneyV2 case). Flattened one level; never a description clause (only
      // reference fields -- the branches above -- get one; see class javadoc's Rule A note and
      // TabularDatasetSchema#description's carve-out).
      return FieldAnalysis.columnsOnly(flattenValueObject(fieldName, u.name(), index));
   }

   /**
    * A to-MANY reference field (connection-shaped, or a plain list of objects). GraphQL never
    * exposes a raw foreign-key scalar alongside a list/connection reference the way OData or
    * Salesforce do, so there is no column on THIS ("one") side to project -- the only way to
    * express the edge at all is from the CHILD's own side, when the child carries a to-one
    * back-reference to this dataset. With no such back-reference the edge is dropped and
    * recorded, never emitted with an invented {@code fromColumns}.
    */
   private static FieldAnalysis analyzeManyReference(String parentDatasetId, String parentNodeType,
                                                       String fieldName, String childTypeName,
                                                       SchemaIndex index)
   {
      if(!index.isDatasetNodeType(childTypeName)) {
         return FieldAnalysis.withClause(List.of(), fieldName + " -> dropped: target type '" +
            childTypeName + "' has no root Query field");
      }

      String childDatasetId = index.singleDatasetIdForNodeType(childTypeName);

      if(childDatasetId == null) {
         return FieldAnalysis.withClause(List.of(), fieldName + " -> dropped: target type '" +
            childTypeName + "' is exposed by more than one root Query field, ambiguous");
      }

      String backRefField = findToOneBackReference(childTypeName, parentNodeType, index);

      if(backRefField == null) {
         return FieldAnalysis.withClause(List.of(), fieldName + " -> dropped: target type '" +
            childTypeName + "' has no to-one back-reference to '" + parentNodeType +
            "' to project a join column from");
      }

      String parentIdField = findIdField(parentNodeType, index);

      if(parentIdField == null) {
         return FieldAnalysis.withClause(List.of(), fieldName + " -> dropped: target type '" +
            childTypeName + "' has a back-reference, but '" + parentNodeType + "' has no " +
            "ID-kind scalar field for it to join on");
      }

      // Recorded from the CHILD dataset's own perspective -- the child carries the projectable
      // FK (as its own back-reference field flattened to the parent's id, e.g. "order.id"), the
      // same directional convention OData/Salesforce use: the "many" side carries the key.
      String relName = childDatasetId + "." + backRefField;
      TabularRelationship relationship = new TabularRelationship(relName, childDatasetId,
         parentDatasetId, List.of(backRefField + "." + parentIdField), List.of(parentIdField));

      return FieldAnalysis.withRelationship(List.of(), fieldName + " -> relationship '" + relName +
         "' to dataset '" + childDatasetId + "' (expressed from '" + childDatasetId + "." +
         backRefField + "', since GraphQL exposes no join column on this -- the 'one' -- side)",
         relationship);
   }

   /**
    * A to-ONE reference field to another dataset's node type. Flattened to one column on THIS
    * dataset -- {@code fieldName + "." + idField} -- picking the target's own first ID-kind
    * scalar field (never a hardcoded {@code "id"}), because a to-one field never fans out, so
    * this is always a valid single-scalar-per-row projection.
    */
   private static FieldAnalysis analyzeOneReference(String datasetId, String fieldName,
                                                      String targetTypeName, SchemaIndex index)
   {
      String targetDatasetId = index.singleDatasetIdForNodeType(targetTypeName);

      if(targetDatasetId == null) {
         return FieldAnalysis.withClause(List.of(), fieldName + " -> dropped: target type '" +
            targetTypeName + "' is exposed by more than one root Query field, ambiguous");
      }

      String idField = findIdField(targetTypeName, index);

      if(idField == null) {
         return FieldAnalysis.withClause(List.of(), fieldName + " -> dropped: target type '" +
            targetTypeName + "' has no ID-kind scalar field to project as a join column");
      }

      String relName = datasetId + "." + fieldName;
      TabularRelationship relationship = new TabularRelationship(relName, datasetId,
         targetDatasetId, List.of(fieldName + "." + idField), List.of(idField));
      TabularColumn column = new TabularColumn(fieldName + "." + idField, mapScalar("ID"), null,
         null, null);

      return FieldAnalysis.withRelationship(List.of(column), fieldName + " -> relationship '" +
         relName + "' to dataset '" + targetDatasetId + "'", relationship);
   }

   /**
    * Flattens a nested, non-dataset object field ({@code totalPrice: MoneyV2}) one level: every
    * scalar/enum field of the value type becomes a dotted column. Depth-limited to exactly one
    * level -- see class javadoc.
    */
   private static List<TabularColumn> flattenValueObject(String fieldName, String valueTypeName,
                                                           SchemaIndex index)
   {
      JsonNode valueType = index.typesByName.get(valueTypeName);

      if(valueType == null) {
         return List.of();
      }

      List<TabularColumn> result = new ArrayList<>();

      for(JsonNode inner : fieldsOf(valueType)) {
         String innerName = inner.path("name").asText(null);

         if(innerName == null) {
            continue;
         }

         Unwrapped iu = unwrap(inner.path("type"));
         String innerDescription = emptyToNull(inner.path("description").asText(null));

         if("SCALAR".equals(iu.kind())) {
            result.add(new TabularColumn(fieldName + "." + innerName, mapScalar(iu.name()),
               innerDescription, null, null));
         }
         else if("ENUM".equals(iu.kind())) {
            result.add(new TabularColumn(fieldName + "." + innerName, XSchema.STRING,
               innerDescription, null, null));
         }
         else {
            LOG.debug("Dropping doubly-nested field '{}.{}' on value object '{}' -- flattening " +
               "is limited to one level", fieldName, innerName, valueTypeName);
         }
      }

      return result;
   }

   private static String findToOneBackReference(String childTypeName, String parentTypeName,
                                                 SchemaIndex index)
   {
      JsonNode childType = index.typesByName.get(childTypeName);

      for(JsonNode field : fieldsOf(childType)) {
         Unwrapped u = unwrap(field.path("type"));

         if("OBJECT".equals(u.kind()) && !u.isList() && parentTypeName.equals(u.name())) {
            String fieldName = field.path("name").asText(null);

            if(fieldName != null) {
               return fieldName;   // first match, in declaration order
            }
         }
      }

      return null;
   }

   private static String findIdField(String typeName, SchemaIndex index) {
      JsonNode type = index.typesByName.get(typeName);

      for(JsonNode field : fieldsOf(type)) {
         Unwrapped u = unwrap(field.path("type"));

         if("SCALAR".equals(u.kind()) && "ID".equals(u.name())) {
            String fieldName = field.path("name").asText(null);

            if(fieldName != null) {
               return fieldName;
            }
         }
      }

      return null;
   }

   private static String buildDescription(JsonNode nodeType, List<String> clauses) {
      String sourceDescription = emptyToNull(nodeType.path("description").asText(null));

      if(clauses.isEmpty()) {
         return sourceDescription;
      }

      String composed = String.join("; ", clauses);

      // The connector-composed clauses are a visibly SEPARATE second paragraph, never blended
      // into the source's own sentence -- what makes TabularDatasetSchema#description's
      // structural-notes carve-out honest rather than a blurring of "the source said" and "the
      // connector inferred".
      return sourceDescription == null ? composed : sourceDescription + "\n\n" + composed;
   }

   private static Map<String, String> buildParams(SchemaIndex index, String datasetId) {
      boolean connectionShaped = Boolean.TRUE.equals(index.datasetIsConnection.get(datasetId));
      JsonNode queryField = index.datasetQueryField.get(datasetId);
      String jsonPath = connectionShaped
         ? "$.data." + datasetId + ".edges[*].node"
         : "$.data." + datasetId + "[*]";

      List<String> argNames = new ArrayList<>();

      for(JsonNode arg : queryField.path("args")) {
         String argName = arg.path("name").asText(null);

         if(argName != null) {
            argNames.add(argName);
         }
      }

      boolean usePagination = false;
      boolean cursorPagination = false;
      String paginationVariable = "";
      String paginationCountPath = "";

      // A real, connector-specific HEURISTIC -- GraphQL has no universal pagination convention
      // the way OData has $top/$skip. Reads the root field's OWN argument names rather than
      // betting on Relay's first/after being the names a given API actually uses. A field whose
      // args match NEITHER pattern falls through to "no pagination" -- honestly reporting "this
      // field does not appear to support pagination" is safer than guessing a convention that
      // isn't there: a wrong paginationVariable would make every query built off this catalog
      // entry fail at bind time, which is worse than omitting pagination. See design doc §7.2 /
      // charter G21.
      if(connectionShaped) {
         for(String argName : argNames) {
            if(CURSOR_ARG_PATTERN.matcher(argName).matches()) {
               usePagination = true;
               cursorPagination = true;
               paginationVariable = argName;
               paginationCountPath = "$.data." + datasetId + ".pageInfo.endCursor";
               break;
            }
         }
      }

      if(!usePagination) {
         for(String argName : argNames) {
            if(OFFSET_ARG_PATTERN.matcher(argName).matches()) {
               usePagination = true;
               paginationVariable = argName;
               paginationCountPath = connectionShaped
                  ? "$.data." + datasetId + ".edges.length()"
                  : "$.data." + datasetId + ".length()";
               break;
            }
         }
      }

      // Keys are exactly TabularUtil.getPropertyMap(GraphQLQuery.class)'s derivation from its
      // real getters/setters -- queryString/variables/usePagination/cursorPagination/
      // paginationVariable/paginationCountPath -- never a paraphrase like "paginationJsonPath".
      Map<String, String> params = new LinkedHashMap<>();
      params.put("queryString", "");   // user intent -- the binding layer synthesizes it
      params.put("variables", "");     // user intent -- Q2
      params.put("jsonPath", jsonPath);
      params.put("usePagination", String.valueOf(usePagination));
      params.put("cursorPagination", String.valueOf(cursorPagination));
      params.put("paginationVariable", paginationVariable);
      params.put("paginationCountPath", paginationCountPath);
      return params;
   }

   // ===================================================================================
   // Type mapping (G6/G7)
   // ===================================================================================

   // The GraphQL spec's ENTIRE built-in scalar set -- exactly 5. Pinned by a canary test asserting
   // this set's size == 5, so a spec change (or a typo here) is caught, not silently absorbed.
   static final Set<String> BUILTIN_SCALARS = Set.of("ID", "String", "Int", "Float", "Boolean");
   static final String UNKNOWN_SCALAR_DEFAULT = XSchema.STRING;

   private static String mapScalar(String scalarName) {
      if(scalarName == null) {
         LOG.warn("GraphQL scalar type could not be resolved; defaulting its column type to {}",
            UNKNOWN_SCALAR_DEFAULT);
         return UNKNOWN_SCALAR_DEFAULT;
      }

      return switch(scalarName) {
         // ID serializes as a string on the wire (the GraphQL spec defines ID's response
         // format as a String), so XSchema.STRING is correct here even though it is also an
         // identifier -- not merely a fallback default. Kept consistent across every dataset.
         case "ID", "String" -> XSchema.STRING;
         case "Int" -> XSchema.INTEGER;      // GraphQL Int is 32-bit, matches XSchema.INTEGER
         case "Float" -> XSchema.DOUBLE;     // GraphQL Float is a double, matches XSchema.DOUBLE
         case "Boolean" -> XSchema.BOOLEAN;
         default -> {
            // Every custom scalar -- DateTime, Decimal, URL, JSON, anything vendor-defined --
            // lands HERE, never name-sniffed. See class javadoc for why.
            LOG.warn("GraphQL scalar '{}' is not one of the {} built-in scalars ({}); " +
               "defaulting its column type to {}", scalarName, BUILTIN_SCALARS.size(),
               BUILTIN_SCALARS, UNKNOWN_SCALAR_DEFAULT);
            yield UNKNOWN_SCALAR_DEFAULT;
         }
      };
   }

   // ===================================================================================
   // Structural helpers -- type unwrapping, connection detection, field lookup
   // ===================================================================================

   private static final int MAX_UNWRAP_DEPTH = 6;

   /**
    * The innermost named type once {@code NON_NULL}/{@code LIST} wrappers are stripped, and
    * separately whether exactly one {@code LIST} wrapper was crossed (needed to distinguish "a
    * list of Order" from "one Order"). Depth-bounded to {@link #MAX_UNWRAP_DEPTH} -- see class
    * javadoc.
    */
   private record Unwrapped(String kind, String name, boolean isList) {}

   private static Unwrapped unwrap(JsonNode typeRef) {
      JsonNode current = typeRef;
      boolean isList = false;

      for(int depth = 0; depth < MAX_UNWRAP_DEPTH; depth++) {
         if(current == null || current.isMissingNode() || current.isNull()) {
            return new Unwrapped(null, null, isList);
         }

         String kind = current.path("kind").asText(null);

         if("NON_NULL".equals(kind)) {
            current = current.get("ofType");
            continue;
         }

         if("LIST".equals(kind)) {
            isList = true;
            current = current.get("ofType");
            continue;
         }

         return new Unwrapped(kind, current.path("name").asText(null), isList);
      }

      // Exceeded MAX_UNWRAP_DEPTH NON_NULL/LIST layers -- deeper than this connector's
      // introspection query resolves. Never a crash -- treated as an unresolved type; see class
      // javadoc and charter G22.
      return new Unwrapped(null, null, isList);
   }

   private record ConnectionInfo(String nodeTypeName) {}

   /**
    * Structural (never name-based) test for "is {@code objectTypeName} a Relay-style connection":
    * does it have an {@code edges} field whose item type has a {@code node} field. {@code
    * pageInfo} is corroborating when present, never required -- requiring it would exclude a
    * connection-shaped-but-not-strictly-Relay-compliant field for no contract reason.
    */
   private static ConnectionInfo detectConnectionShape(String objectTypeName,
                                                         Map<String, JsonNode> typesByName)
   {
      JsonNode typeNode = typesByName.get(objectTypeName);

      if(typeNode == null) {
         return null;
      }

      JsonNode edgesField = findField(typeNode, "edges");

      if(edgesField == null) {
         return null;
      }

      Unwrapped edgesUnwrapped = unwrap(edgesField.path("type"));

      if(!"OBJECT".equals(edgesUnwrapped.kind()) || edgesUnwrapped.name() == null) {
         return null;
      }

      JsonNode edgeType = typesByName.get(edgesUnwrapped.name());

      if(edgeType == null) {
         return null;
      }

      JsonNode nodeField = findField(edgeType, "node");

      if(nodeField == null) {
         return null;
      }

      Unwrapped nodeUnwrapped = unwrap(nodeField.path("type"));

      if(!"OBJECT".equals(nodeUnwrapped.kind()) || nodeUnwrapped.name() == null) {
         return null;
      }

      return new ConnectionInfo(nodeUnwrapped.name());
   }

   private static JsonNode findField(JsonNode typeNode, String name) {
      for(JsonNode field : fieldsOf(typeNode)) {
         if(name.equals(field.path("name").asText(null))) {
            return field;
         }
      }

      return null;
   }

   private static JsonNode fieldsOf(JsonNode typeNode) {
      return typeNode == null ? MissingNode.getInstance() : typeNode.path("fields");
   }

   private static String emptyToNull(String s) {
      return s == null || s.isEmpty() ? null : s;
   }

   // ===================================================================================
   // Per-field analysis result
   // ===================================================================================

   private record FieldAnalysis(List<TabularColumn> columns, String descriptionClause,
                                 TabularRelationship relationship)
   {
      static FieldAnalysis columnsOnly(List<TabularColumn> columns) {
         return new FieldAnalysis(columns, null, null);
      }

      static FieldAnalysis withClause(List<TabularColumn> columns, String clause) {
         return new FieldAnalysis(columns, clause, null);
      }

      static FieldAnalysis withRelationship(List<TabularColumn> columns, String clause,
                                             TabularRelationship relationship)
      {
         return new FieldAnalysis(columns, clause, relationship);
      }
   }

   // ===================================================================================
   // Schema index -- built once per introspection call, shared by every helper above
   // ===================================================================================

   private static final class SchemaIndex {
      final Map<String, JsonNode> typesByName;
      final List<String> datasetOrder;
      final Map<String, String> datasetNodeType;
      final Map<String, JsonNode> datasetQueryField;
      final Map<String, Boolean> datasetIsConnection;
      final Map<String, List<String>> nodeTypeToDatasetIds;

      private SchemaIndex(Map<String, JsonNode> typesByName, List<String> datasetOrder,
                           Map<String, String> datasetNodeType,
                           Map<String, JsonNode> datasetQueryField,
                           Map<String, Boolean> datasetIsConnection,
                           Map<String, List<String>> nodeTypeToDatasetIds)
      {
         this.typesByName = typesByName;
         this.datasetOrder = datasetOrder;
         this.datasetNodeType = datasetNodeType;
         this.datasetQueryField = datasetQueryField;
         this.datasetIsConnection = datasetIsConnection;
         this.nodeTypeToDatasetIds = nodeTypeToDatasetIds;
      }

      boolean isDatasetNodeType(String typeName) {
         return nodeTypeToDatasetIds.containsKey(typeName);
      }

      /** Null when unknown, or when more than one root field shares this node type (ambiguous). */
      String singleDatasetIdForNodeType(String typeName) {
         List<String> ids = nodeTypeToDatasetIds.get(typeName);
         return ids != null && ids.size() == 1 ? ids.get(0) : null;
      }

      static SchemaIndex build(JsonNode schemaRoot) {
         if(schemaRoot == null || schemaRoot.isMissingNode() || schemaRoot.isNull()) {
            throw new IllegalStateException(
               "GraphQL introspection returned no usable __schema node.");
         }

         String queryTypeName = schemaRoot.path("queryType").path("name").asText(null);

         if(queryTypeName == null) {
            throw new IllegalStateException("GraphQL introspection's __schema.queryType.name " +
               "was missing -- cannot locate the root Query type.");
         }

         Map<String, JsonNode> typesByName = new LinkedHashMap<>();

         for(JsonNode type : schemaRoot.path("types")) {
            String name = type.path("name").asText(null);

            if(name != null) {
               typesByName.put(name, type);
            }
         }

         JsonNode queryType = typesByName.get(queryTypeName);

         if(queryType == null) {
            throw new IllegalStateException("GraphQL introspection's root Query type '" +
               queryTypeName + "' was not found among the introspected types.");
         }

         List<String> datasetOrder = new ArrayList<>();
         Map<String, String> datasetNodeType = new LinkedHashMap<>();
         Map<String, JsonNode> datasetQueryField = new LinkedHashMap<>();
         Map<String, Boolean> datasetIsConnection = new LinkedHashMap<>();

         // Field-declaration order is preserved (LinkedHashMap + a straight iteration) so
         // TabularCatalog.datasets() comes back in the source's own order, per the SPI contract --
         // introspection's own field order is exactly this, and does not vary between two calls
         // against an unchanged schema.
         for(JsonNode field : fieldsOf(queryType)) {
            String fieldName = field.path("name").asText(null);

            if(fieldName == null || datasetNodeType.containsKey(fieldName)) {
               continue;
            }

            Unwrapped u = unwrap(field.path("type"));

            if(!"OBJECT".equals(u.kind()) || u.name() == null) {
               continue;   // scalar/enum-returning or unresolved root field -- never a dataset
            }

            String nodeTypeName;
            boolean isConnection;

            if(u.isList()) {
               nodeTypeName = u.name();
               isConnection = false;
            }
            else {
               ConnectionInfo connection = detectConnectionShape(u.name(), typesByName);

               if(connection == null) {
                  continue;   // single-object lookup or singleton -- not a dataset (Q1)
               }

               nodeTypeName = connection.nodeTypeName();
               isConnection = true;
            }

            datasetOrder.add(fieldName);
            datasetNodeType.put(fieldName, nodeTypeName);
            datasetQueryField.put(fieldName, field);
            datasetIsConnection.put(fieldName, isConnection);
         }

         Map<String, List<String>> nodeTypeToDatasetIds = new LinkedHashMap<>();

         for(Map.Entry<String, String> e : datasetNodeType.entrySet()) {
            nodeTypeToDatasetIds.computeIfAbsent(e.getValue(), k -> new ArrayList<>())
               .add(e.getKey());
         }

         return new SchemaIndex(typesByName, List.copyOf(datasetOrder), datasetNodeType,
            datasetQueryField, datasetIsConnection, nodeTypeToDatasetIds);
      }
   }

   private static final Pattern CURSOR_ARG_PATTERN =
      Pattern.compile("^(after|cursor)$", Pattern.CASE_INSENSITIVE);
   private static final Pattern OFFSET_ARG_PATTERN =
      Pattern.compile("^(offset|skip|page)$", Pattern.CASE_INSENSITIVE);
   private static final Logger LOG =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
}
