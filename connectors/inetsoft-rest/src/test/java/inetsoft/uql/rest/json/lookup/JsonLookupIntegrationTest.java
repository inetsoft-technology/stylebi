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

import inetsoft.report.lens.xnode.XNodeTableLens;
import inetsoft.test.TestEndpoint;
import inetsoft.uql.XTableNode;
import inetsoft.uql.rest.AbstractRestDataIteratorStrategy;
import inetsoft.uql.rest.AbstractRestQuery;
import inetsoft.uql.rest.EndpointJsonQueryRunner;
import inetsoft.uql.rest.InputTransformer;
import inetsoft.uql.rest.json.*;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JsonLookupIntegrationTest {
    @Test
    void lookupEndpoint() {
        final TestQuery query = new TestQuery();
        final TestEndpoint endpoint = query.getEndpointMap().get("Customers");
        assertEquals(endpoint.getLookups().size(), 1);

        final JsonLookupEndpoint lookup = endpoint.getLookups().get(0);

        final EndpointJsonQuery.Endpoint lookupEndpointReference = query.getEndpointMap().get(lookup.endpoint());

        assertEquals(query.getEndpointMap().get("Customer Orders"), lookupEndpointReference);
    }

    @Test
    void runsLookupQueries() {
        final JsonTransformer transformer = new JsonTransformer();
        final TestFactory factory = createTestFactory();
        final TestQuery query = createTestQuery();
        final LookupService lookupService = new LookupService();
        final EndpointJsonQueryRunner runner = new EndpointJsonQueryRunner(query, factory, lookupService, transformer);

        final XTableNode data = runner.run();
        final XNodeTableLens lens = new XNodeTableLens(data);
        lens.moreRows(Integer.MAX_VALUE);
//        Util.printTable(lens);
        assertEquals("supplier 1", lens.getObject(1, 2));
        assertEquals("supplier 2", lens.getObject(2, 2));
        assertEquals("supplier 3", lens.getObject(3, 2));
        assertEquals(lens.getRowCount(), 4);
    }

    private TestFactory createTestFactory() {
        final TestFactory factory = new TestFactory();
        
        final List<Object> customers = new ArrayList<>();
        customers.add(map("id", "customer 1"));
        customers.add(map("id", "customer 2"));

        final List<Object> orders1 = new ArrayList<>();
        orders1.add(map("id", "order 1"));
        orders1.add(map("id", "order 2"));

        final List<Object> orders2 = new ArrayList<>();
        orders2.add(map("id", "order 3"));

        final Map<String, String> supplier1 = map("name", "supplier 1");
        final Map<String, String> supplier2 = map("name", "supplier 2");
        final Map<String, String> supplier3 = map("name", "supplier 3");

        factory.register("/customers", new TestDataIteratorStrategy(customers));

        factory.register("/customers/customer%201/orders", new TestDataIteratorStrategy(orders1));
        factory.register("/customers/customer%202/orders", new TestDataIteratorStrategy(orders2));

        factory.register("/orders/order%201/supplier", new TestDataIteratorStrategy(supplier1));
        factory.register("/orders/order%202/supplier", new TestDataIteratorStrategy(supplier2));
        factory.register("/orders/order%203/supplier", new TestDataIteratorStrategy(supplier3));

        return factory;
    }

    private <K, V> Map<K, V> map(K key, V value) {
        final LinkedHashMap<K, V> map = new LinkedHashMap<>();
        map.put(key, value);
        return map;
    }

    private TestQuery createTestQuery() {
        final TestQuery query = new TestQuery();
        query.setDataSource(new RestJsonDataSource());
        query.setEndpoint("Customers");
        query.setExpandTop(false);

        final JsonLookupQuery orderSupplier = new JsonLookupQuery();
        orderSupplier.setLookupEndpoint(query.getEndpointMap().get("Customer Orders").getLookups().get(0));

        final JsonLookupQuery customerOrders = new JsonLookupQuery();
        customerOrders.setLookupEndpoint(query.getEndpointMap().get("Customers").getLookups().get(0));
        customerOrders.setLookupQueries(Collections.singletonList(orderSupplier));
        customerOrders.setExpandArrays(true);

        query.setLookupQueries(Collections.singletonList(customerOrders));

        return query;
    }

    private static final class TestFactory implements RestDataIteratorStrategyFactory<RestJsonQuery, Object> {
        @Override
        public TestDataIteratorStrategy createStrategy(RestJsonQuery query) {
            return suffixToStrategy.get(query.getSuffix());
        }

        void register(String suffix, TestDataIteratorStrategy strategy) {
            suffixToStrategy.put(suffix, strategy);
        }

        private final Map<String, TestDataIteratorStrategy> suffixToStrategy = new HashMap<>();
    }

    private static final class TestDataIteratorStrategy
       extends AbstractRestDataIteratorStrategy<AbstractRestQuery, InputTransformer>
    {
        protected TestDataIteratorStrategy(Object data) {
            super(null, null);
            this.data = data;
        }

        @Override
        public boolean hasNext() {
            return data != null;
        }

        @Override
        public Object next() {
            final Object temp = data;
            data = null;
            return temp;
        }

        @Override
        public void close() {
            // no-op
        }

        private Object data;
    }
}
