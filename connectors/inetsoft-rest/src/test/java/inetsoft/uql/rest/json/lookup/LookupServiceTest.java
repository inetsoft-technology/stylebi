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
package inetsoft.uql.rest.json.lookup;

import inetsoft.test.*;
import inetsoft.uql.rest.EndpointQuery;
import inetsoft.uql.rest.datasource.azureblob.AzureBlobQuery;
import inetsoft.uql.rest.json.*;
import inetsoft.uql.rest.xml.parse.MapNode;
import inetsoft.uql.rest.xml.parse.ValueNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, CredentialTestConfig.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
public class LookupServiceTest {
    @Test
    void lookupQuery() {
        final TestQuery query = new TestQuery();
        final JsonTransformer transformer = new JsonTransformer();
        final LookupService service = new LookupService();

        final JsonLookupEndpoint lookupEndpoint = query.getEndpointMap().get("Customers").getLookups().get(0);

        final JsonLookupQuery lookupQuery = new JsonLookupQuery();
        lookupQuery.setLookupEndpoint(lookupEndpoint);
        lookupQuery.setExpandArrays(true);
        lookupQuery.setTopLevelOnly(false);
        lookupQuery.setJsonPath("$.id");
        lookupQuery.setLookupQueries(Collections.emptyList());

        final String jsonStr = "[{\"id\": 1}]";
        final Object jsonArr = transformer.transform(
           new ByteArrayInputStream(jsonStr.getBytes(StandardCharsets.UTF_8)));
        final Object jsonEntity = JsonTransformer.getJsonProvider().getArrayIndex(jsonArr, 0);

        final EndpointJsonQuery<?> newQuery =
           (EndpointJsonQuery<?>) service.createQueryFromLookupQuery(query, query, lookupQuery, jsonEntity);

        assertEquals(newQuery.getSuffix(), "/customers/1/orders");
        assertEquals(newQuery.isExpanded(), lookupQuery.isExpandArrays());
        assertEquals(newQuery.isExpandTop(), lookupQuery.isTopLevelOnly());
        assertEquals(newQuery.getValidJsonPath(), lookupQuery.getJsonPath());
    }

    // Bug #81925
    @Test
    void xmlLookupQueryIsNullWithoutTheKeyElement() {
        final AzureBlobQuery query = azureBlobQuery();
        final JsonLookupQuery lookupQuery = query.getLookupQueries().get(0);
        final MapNode container = new MapNode();
        container.put("Other", new ValueNode("x"));

        assertNull(new LookupService().createXmlLookupQuery(query, query, lookupQuery, container));
    }

    @Test
    void xmlLookupQueryStillFailsForANestedKeyElement() {
        final AzureBlobQuery query = azureBlobQuery();
        final JsonLookupQuery lookupQuery = query.getLookupQueries().get(0);
        final MapNode container = new MapNode();
        container.put("Name", new MapNode());

        assertThrows(IllegalStateException.class,
                     () -> new LookupService().createXmlLookupQuery(query, query, lookupQuery, container));
    }

    @Test
    void xmlLookupQueryUsesTheKeyElement() {
        final AzureBlobQuery query = azureBlobQuery();
        final JsonLookupQuery lookupQuery = query.getLookupQueries().get(0);
        final MapNode container = new MapNode();
        container.put("Name", new ValueNode("c1"));

        final EndpointQuery newQuery =
           new LookupService().createXmlLookupQuery(query, query, lookupQuery, container);

        assertNotNull(newQuery);
        assertEquals("Blobs", newQuery.getEndpoint());
    }

    // Bug #81925
    @Test
    void customLookupQueryIsNullWithoutTheKey() {
        final RestJsonQuery query = customQuery("/customers/{param1}/orders", "id");

        assertNull(createCustom(query, "{\"name\":\"no id\"}"));
    }

    @Test
    void customLookupQueryWithAnEmptyKeyAndNoParameter() {
        final RestJsonQuery query = customQuery("/settings", "");
        final RestJsonQuery newQuery = createCustom(query, "{\"name\":\"no id\"}");

        assertNotNull(newQuery);
        assertEquals("/settings", newQuery.getSuffix());
    }

    @Test
    void customLookupQueryWithAKeyTheUrlDoesNotUse() {
        final RestJsonQuery query = customQuery("/settings", "id");
        final RestJsonQuery newQuery = createCustom(query, "{\"name\":\"no id\"}");

        assertNotNull(newQuery);
        assertEquals("/settings", newQuery.getSuffix());
    }

    // Bug #81927
    @Test
    void moveCollidingFieldWithoutACollision() {
        final Map<String, Object> entity = new LinkedHashMap<>(Map.of("Other", "own"));

        assertNull(new LookupService().moveCollidingField(entity, "Users"));
        assertEquals(Map.of("Other", "own"), entity);
    }

    // Bug #81927
    @Test
    void moveCollidingFieldRenamesTheField() {
        final Map<String, Object> entity = new LinkedHashMap<>(Map.of("Users", "own"));

        assertEquals("Users (Field)", new LookupService().moveCollidingField(entity, "Users"));
        assertEquals(Map.of("Users (Field)", "own"), entity);
    }

    @Test
    void moveCollidingFieldNumbersTheNameWhenItIsTaken() {
        final Map<String, Object> entity = new LinkedHashMap<>();
        entity.put("Users", "own");
        entity.put("Users (Field)", "other");

        assertEquals("Users (Field 2)", new LookupService().moveCollidingField(entity, "Users"));
        assertEquals("own", entity.get("Users (Field 2)"));
        assertEquals("other", entity.get("Users (Field)"));
        assertFalse(entity.containsKey("Users"));
    }

    @Test
    void moveCollidingFieldMovesANullValue() {
        final Map<String, Object> entity = new LinkedHashMap<>();
        entity.put("Users", null);

        assertEquals("Users (Field)", new LookupService().moveCollidingField(entity, "Users"));
        assertTrue(entity.containsKey("Users (Field)"));
        assertFalse(entity.containsKey("Users"));
    }

    @Test
    void moveCollidingFieldIsCaseSensitiveAndIgnoresNonMaps() {
        final Map<String, Object> entity = new LinkedHashMap<>(Map.of("users", "own"));

        assertNull(new LookupService().moveCollidingField(entity, "Users"));
        assertEquals(Map.of("users", "own"), entity);
        assertNull(new LookupService().moveCollidingField("Users", "Users"));
    }

    private static AzureBlobQuery azureBlobQuery() {
        final AzureBlobQuery query = new AzureBlobQuery();
        query.setEndpoint("Containers");
        query.setLookupEndpoint("Blobs", 0);
        return query;
    }

    private static RestJsonQuery customQuery(String url, String key) {
        final RestJsonQuery query = new RestJsonQuery();
        query.setLookupURL(0, url);
        query.setLookupJsonPath(0, "$");
        query.setLookupKey(0, key);
        return query;
    }

    private static RestJsonQuery createCustom(RestJsonQuery query, String json) {
        final Object entity = new JsonTransformer().transform(
           new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
        return new LookupService().createQueryFromLookupQuery(
           query, query, query.getLookupQueries().get(0), entity);
    }
}
