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
package inetsoft.uql.odata;

import inetsoft.uql.tabular.*;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.*;

/**
 * Turns one parsed {@code $metadata} {@code <Schema>} node into the neutral
 * {@link inetsoft.uql.tabular.TabularCatalogProvider} types. This is the ONLY place in this module
 * that reads {@code <EntitySet>}/{@code <EntityType>}/{@code <Property>}/{@code <NavigationProperty>}
 * for the annotation catalog — everything downstream of {@link #parse} deals in
 * {@code Tabular*} records only, never {@code org.w3c.dom}.
 *
 * Reuses {@link ODataRuntime#getEntityType} indirectly by re-deriving the same entity-set to
 * entity-type mapping locally: {@code getEntityType} answers "what type does this ONE entity set
 * have", but the catalog needs the map in both directions for every entity set at once, so
 * re-walking the container here (rather than calling it once per entity set) keeps this a single
 * pass over the document.
 */
final class ODataCatalog {
   private ODataCatalog() {
   }

   /**
    * @param schemaNode the {@code <Schema>} element from {@link ODataRuntime#getSchemaNode}.
    * @return never {@code null}. Datasets and relationships may both be empty if the schema
    *         genuinely declares neither.
    */
   static ODataCatalogSnapshot parse(Node schemaNode) {
      if(schemaNode == null) {
         return new ODataCatalogSnapshot(new TabularCatalog(List.of(), List.of()), Map.of());
      }

      Map<String, Element> entityTypeBySimpleName = indexEntityTypes(schemaNode);

      // entitySetsByName preserves document order (LinkedHashMap over each container's
      // <EntitySet> children, containers visited in document order) so TabularCatalog.datasets()
      // comes back in the source's own order, per the SPI contract.
      Map<String, Element> entitySetsByName = new LinkedHashMap<>();
      Map<String, String> entitySetToTypeSimpleName = new LinkedHashMap<>();

      // Every entity set that exposes a given entity type, in document order — NOT just the
      // first. A relationship target can only fall back to this map when it names exactly one
      // entry; see addRelationshipIfExpressible.
      Map<String, List<String>> typeSimpleNameToEntitySets = new LinkedHashMap<>();

      NodeList containers = Tool.getChildNodesByTagName(schemaNode, "EntityContainer");

      for(int c = 0; c < containers.getLength(); c++) {
         NodeList entitySets = Tool.getChildNodesByTagName(containers.item(c), "EntitySet");

         for(int i = 0; i < entitySets.getLength(); i++) {
            Element entitySet = (Element) entitySets.item(i);
            String name = Tool.getAttribute(entitySet, "Name");

            if(name == null || entitySetsByName.containsKey(name)) {
               continue;
            }

            String simpleType = simpleName(Tool.getAttribute(entitySet, "EntityType"));
            entitySetsByName.put(name, entitySet);
            entitySetToTypeSimpleName.put(name, simpleType);
            typeSimpleNameToEntitySets.computeIfAbsent(simpleType, k -> new ArrayList<>())
               .add(name);
         }
      }

      List<TabularDatasetRef> datasets = new ArrayList<>();
      Map<String, TabularDatasetSchema> schemasByEntitySet = new LinkedHashMap<>();

      for(Map.Entry<String, String> entry : entitySetToTypeSimpleName.entrySet()) {
         String entitySetName = entry.getKey();
         Element entityType = entityTypeBySimpleName.get(entry.getValue());
         datasets.add(new TabularDatasetRef(entitySetName));

         if(entityType != null) {
            schemasByEntitySet.put(entitySetName, buildSchema(entitySetName, entityType));
         }
      }

      List<TabularRelationship> relationships = buildRelationships(
         entitySetsByName, entityTypeBySimpleName, typeSimpleNameToEntitySets);

      return new ODataCatalogSnapshot(
         new TabularCatalog(List.copyOf(datasets), relationships), Map.copyOf(schemasByEntitySet));
   }

   private static Map<String, Element> indexEntityTypes(Node schemaNode) {
      Map<String, Element> result = new LinkedHashMap<>();
      NodeList entityTypeNodes = Tool.getChildNodesByTagName(schemaNode, "EntityType");

      for(int i = 0; i < entityTypeNodes.getLength(); i++) {
         Element entityType = (Element) entityTypeNodes.item(i);
         String name = Tool.getAttribute(entityType, "Name");

         if(name != null) {
            result.put(name, entityType);
         }
      }

      return result;
   }

   private static TabularDatasetSchema buildSchema(String entitySetName, Element entityType) {
      List<TabularColumn> columns = new ArrayList<>();
      NodeList propertyNodes = Tool.getChildNodesByTagName(entityType, "Property");

      for(int i = 0; i < propertyNodes.getLength(); i++) {
         Element property = (Element) propertyNodes.item(i);
         String name = Tool.getAttribute(property, "Name");
         String edmType = Tool.getAttribute(property, "Type");

         if(name == null || edmType == null) {
            continue;
         }

         columns.add(new TabularColumn(name, ODataRuntime.toXSchemaType(edmType)));
      }

      List<String> keyColumns = new ArrayList<>();
      Node keyNode = Tool.getChildNodeByTagName(entityType, "Key");

      if(keyNode != null) {
         NodeList propertyRefs = Tool.getChildNodesByTagName(keyNode, "PropertyRef");

         for(int i = 0; i < propertyRefs.getLength(); i++) {
            String name = Tool.getAttribute((Element) propertyRefs.item(i), "Name");

            if(name != null) {
               keyColumns.add(name);
            }
         }
      }

      return new TabularDatasetSchema(entitySetName, List.copyOf(columns), List.copyOf(keyColumns),
         Map.of("entity", entitySetName));
   }

   /**
    * Walks every entity set's {@code <NavigationProperty>}s and maps the ones that carry a
    * {@code <ReferentialConstraint>} to a {@link TabularRelationship}. See
    * {@code 12-spi-design.md} §7.3 for the cases that are honestly dropped instead: no
    * constraint, no resolvable target entity set, an AMBIGUOUS target entity set (see below),
    * containment navigation, a property declared on a {@code <ComplexType>} (structurally
    * excluded here — only {@code <EntityType>} children are ever visited), and OData v2/v3
    * {@code <Association>}/{@code <AssociationSet>} (not parsed at all this round — a v2 service
    * therefore yields an empty relationship list, unchanged from before this SPI existed).
    */
   private static List<TabularRelationship> buildRelationships(
      Map<String, Element> entitySetsByName, Map<String, Element> entityTypeBySimpleName,
      Map<String, List<String>> typeSimpleNameToEntitySets)
   {
      List<TabularRelationship> result = new ArrayList<>();
      Set<String> seen = new HashSet<>();

      for(Map.Entry<String, Element> entry : entitySetsByName.entrySet()) {
         String fromEntitySet = entry.getKey();
         Element entitySetElement = entry.getValue();
         String simpleType = simpleName(Tool.getAttribute(entitySetElement, "EntityType"));
         Element entityType = entityTypeBySimpleName.get(simpleType);

         if(entityType == null) {
            continue;
         }

         NodeList navProps = Tool.getChildNodesByTagName(entityType, "NavigationProperty");

         for(int i = 0; i < navProps.getLength(); i++) {
            Element nav = (Element) navProps.item(i);
            addRelationshipIfExpressible(nav, fromEntitySet, entitySetElement,
               typeSimpleNameToEntitySets, result, seen);
         }
      }

      return result;
   }

   private static void addRelationshipIfExpressible(
      Element nav, String fromEntitySet, Element fromEntitySetElement,
      Map<String, List<String>> typeSimpleNameToEntitySets, List<TabularRelationship> result,
      Set<String> seen)
   {
      if("true".equalsIgnoreCase(Tool.getAttribute(nav, "ContainsTarget"))) {
         LOG.debug("Dropping containment navigation property '{}' on entity set '{}' — not a " +
                   "dataset-to-dataset edge", Tool.getAttribute(nav, "Name"), fromEntitySet);
         return;
      }

      NodeList constraints = Tool.getChildNodesByTagName(nav, "ReferentialConstraint");
      String navName = Tool.getAttribute(nav, "Name");

      if(constraints.getLength() == 0) {
         LOG.debug("Dropping navigation property '{}' on entity set '{}' — no " +
                   "<ReferentialConstraint>, so it has no columns to express as a relationship",
                   navName, fromEntitySet);
         return;
      }

      List<String> fromColumns = new ArrayList<>();
      List<String> toColumns = new ArrayList<>();

      for(int i = 0; i < constraints.getLength(); i++) {
         Element constraint = (Element) constraints.item(i);
         fromColumns.add(Tool.getAttribute(constraint, "Property"));
         toColumns.add(Tool.getAttribute(constraint, "ReferencedProperty"));
      }

      String toEntitySet = resolveBindingTarget(fromEntitySetElement, navName);

      if(toEntitySet == null) {
         // No <NavigationPropertyBinding> to consult — the ONLY thing that is correct when one
         // entity type is exposed by several entity sets (see resolveBindingTarget's javadoc).
         // Falling back to "the first one in document order" would silently guess a
         // structurally-valid, semantically-WRONG target in exactly that ambiguous case, which is
         // worse than dropping the edge — the same reasoning this method already applies to a
         // constraint-less or containment navigation property. So the fallback is only taken when
         // there is nothing to be ambiguous about: exactly one entity set exposes the type.
         String targetSimpleType = simpleName(stripCollection(Tool.getAttribute(nav, "Type")));
         List<String> candidates =
            typeSimpleNameToEntitySets.getOrDefault(targetSimpleType, List.of());

         if(candidates.size() == 1) {
            toEntitySet = candidates.get(0);
         }
         else if(candidates.size() > 1) {
            LOG.debug("Dropping navigation property '{}' on entity set '{}' — its target entity " +
                      "type '{}' is exposed by {} entity sets ({}) with no " +
                      "<NavigationPropertyBinding> to say which one this edge points to",
                      navName, fromEntitySet, targetSimpleType, candidates.size(), candidates);
            return;
         }
      }

      if(toEntitySet == null) {
         LOG.debug("Dropping navigation property '{}' on entity set '{}' — its target entity " +
                   "type has no entity set", navName, fromEntitySet);
         return;
      }

      // A v4 bidirectional relationship declares a Partner on both sides; emit only one edge per
      // (endpoints, columns) pair so a Partner pair does not double up in the reverse direction.
      String key = fromEntitySet + "|" + toEntitySet + "|" + fromColumns + "|" + toColumns;
      String reverseKey = toEntitySet + "|" + fromEntitySet + "|" + toColumns + "|" + fromColumns;

      if(seen.contains(key) || seen.contains(reverseKey)) {
         return;
      }

      seen.add(key);
      result.add(new TabularRelationship(fromEntitySet + "_" + navName, fromEntitySet, toEntitySet,
         List.copyOf(fromColumns), List.copyOf(toColumns)));
   }

   /**
    * The declaring entity set's own {@code <NavigationPropertyBinding Path="..." Target="..."/>}
    * — authoritative, and the only thing that is correct when one entity type is exposed as
    * several entity sets. {@code Target} may be a bare entity set name or a qualified
    * {@code "Container/EntitySet"} path; only the last segment matters here.
    */
   private static String resolveBindingTarget(Element entitySetElement, String navPropertyName) {
      NodeList bindings = Tool.getChildNodesByTagName(entitySetElement, "NavigationPropertyBinding");

      for(int i = 0; i < bindings.getLength(); i++) {
         Element binding = (Element) bindings.item(i);

         if(navPropertyName.equals(Tool.getAttribute(binding, "Path"))) {
            String target = Tool.getAttribute(binding, "Target");

            if(target == null) {
               return null;
            }

            int slash = target.lastIndexOf('/');
            return slash >= 0 ? target.substring(slash + 1) : target;
         }
      }

      return null;
   }

   private static String simpleName(String qualifiedName) {
      if(qualifiedName == null) {
         return null;
      }

      int dot = qualifiedName.lastIndexOf('.');
      return dot >= 0 ? qualifiedName.substring(dot + 1) : qualifiedName;
   }

   private static String stripCollection(String type) {
      if(type != null && type.startsWith("Collection(") && type.endsWith(")")) {
         return type.substring("Collection(".length(), type.length() - 1);
      }

      return type;
   }

   private static final Logger LOG = LoggerFactory.getLogger(ODataCatalog.class);
}
