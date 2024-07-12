/*
 * inetsoft-rest - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.uql.rest.json.lookup;

import inetsoft.uql.rest.json.*;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
