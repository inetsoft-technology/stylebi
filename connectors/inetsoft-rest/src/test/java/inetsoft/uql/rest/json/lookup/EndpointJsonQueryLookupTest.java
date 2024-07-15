/*
 * inetsoft-rest - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.uql.rest.json.lookup;

import inetsoft.uql.rest.json.EndpointJsonQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class EndpointJsonQueryLookupTest {
    private TestQuery query;

    @BeforeEach
    void setUp() {
        query = new TestQuery();
    }

    @Test
    void addLookupEndpointButtonVisibility() {
        query.setEndpoint(null);
        assertFalse(query.isAddLookupQueryButtonEnabled());
        query.setEndpoint("Order Products");
        assertFalse(query.isAddLookupQueryButtonEnabled());
        query.setEndpoint("Customers");
        assertTrue(query.isAddLookupQueryButtonEnabled());

        for(int i = 0; i < EndpointJsonQuery.LOOKUP_QUERY_LIMIT; i++) {
            query.addLookupQuery();
        }

        assertFalse(query.isAddLookupQueryButtonEnabled());
    }

    @Test
    void addLookupQueryNoopWhenEndpointNull() {
        query.addLookupQuery();
        assertNull(query.getLookupEndpoint0());
    }

    @Test
    void addLookupQueryNoopWhenEndpointHasNoLookupQueries() {
        query.setEndpoint("Order Products");
        assertTrue(query.getEndpointMap().get("Order Products").getLookups().isEmpty());
        query.addLookupQuery();
        assertNull(query.getLookupEndpoint0());
    }

    @Test
    void addLookupQuerySetsFirstLookupEndpoint() {
        query.setEndpoint("Customers");
        query.addLookupQuery();

        final String lookupEndpoint0 = query.getLookupEndpoint0();
        final String customersFirstLookupEndpoint =
           query.getEndpointMap().get("Customers").getLookups().get(0).endpoint();

        assertEquals(customersFirstLookupEndpoint, lookupEndpoint0);
    }

    @Test
    void removeLookupEndpointButtonVisibility() {
        assertFalse(query.isRemoveLookupQueryButtonEnabled());
        query.setEndpoint("Customers");
        query.addLookupQuery();
        assertTrue(query.isRemoveLookupQueryButtonEnabled());
    }

    @Test
    void removeLookupQuery() {
        query.setEndpoint("Customers");
        query.addLookupQuery();
        query.setLookupEndpoint0("Customer Orders");
        assertFalse(query.getLookupQueries().isEmpty());
        query.removeLookupQuery();
        assertTrue(query.getLookupQueries().isEmpty());
    }

    @Test
    void canSetLookupEndpointBeforeAdd() {
        query.setEndpoint("Customers");
        query.setLookupEndpoint0("Customer Orders");
        assertEquals("Customer Orders", query.getLookupEndpoint0());
    }

    @Test
    void canSetLookupEndpointAfterAdd() {
        query.setEndpoint("Customers");
        query.addLookupQuery();
        query.setLookupEndpoint0("Customer Orders");
        assertEquals("Customer Orders", query.getLookupEndpoint0());
    }

    @Test
    void lookupEndpointVisibility() {
        assertFalse(query.isLookupEndpointVisible0());
        query.setEndpoint("Customers");
        assertFalse(query.isLookupEndpointVisible0());
        query.addLookupQuery();
        assertTrue(query.isLookupEndpointVisible0());
    }

    @Test
    void settingEndpointClearsLookups() {
        query.setEndpoint("Customers");
        query.addLookupQuery();
        query.setLookupEndpoint0("Customer Orders");
        assertEquals("Customer Orders", query.getLookupEndpoint0());
        query.setEndpoint("Customer Orders");
        assertNull(query.getLookupEndpoint0());
    }
}
