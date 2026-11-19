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
import inetsoft.uql.XTableNode;
import inetsoft.uql.rest.AbstractQueryRunner;
import inetsoft.uql.util.BaseJsonTable;
import inetsoft.uql.util.ExpandedJsonTable;
import inetsoft.uql.util.JsonTable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #81819: the lookup expand flag defaults to true and only governs the last lookup query, so
 * it must not turn Expand Arrays off into an expanded table when the query has no lookup chain.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, CredentialTestConfig.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
public class EndpointJsonQueryTableShapeTest {
    @Test
    void noLookupChainExpandArraysOffKeepsArraysInOneCell() {
        TestQuery query = endpointQuery(false);

        assertTrue(query.isLookupExpanded());
        assertTrue(query.getLookupQueries().isEmpty());
        assertEquals(JsonTable.class, tableOf(query).getClass());
    }

    @Test
    void lookupChainWithLookupExpandedExpandsOneLevel() {
        TestQuery query = endpointQuery(false);
        addLookupChain(query);

        BaseJsonTable table = tableOf(query);

        assertEquals(ExpandedJsonTable.class, table.getClass());
        assertEquals(1, ((ExpandedJsonTable) table).getExpandLevels());
    }

    @Test
    void lookupChainWithLookupExpandedOffKeepsArraysInOneCell() {
        TestQuery query = endpointQuery(false);
        addLookupChain(query);
        query.setLookupExpanded(false);

        assertEquals(JsonTable.class, tableOf(query).getClass());
    }

    @Test
    void expandArraysOnIsExpandedRegardlessOfLookupChain() {
        TestQuery query = endpointQuery(true);
        query.setLookupExpanded(false);

        assertEquals(ExpandedJsonTable.class, tableOf(query).getClass());
    }

    private static TestQuery endpointQuery(boolean expanded) {
        TestQuery query = new TestQuery();
        query.setEndpoint("Customers");
        query.setExpanded(expanded);
        return query;
    }

    private static void addLookupChain(TestQuery query) {
        query.addLookupQuery();
        query.setLookupEndpoint0("Customer Orders");
        assertFalse(query.getLookupQueries().isEmpty());
    }

    private static BaseJsonTable tableOf(TestQuery query) {
        TableAccess runner = new TableAccess(query);
        return runner.table();
    }

    private static final class TableAccess extends AbstractQueryRunner<TestQuery> {
        TableAccess(TestQuery query) {
            super(query);
        }

        BaseJsonTable table() {
            return getTable();
        }

        @Override
        public XTableNode runStream() {
            return null;
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public boolean isLiveMode() {
            return false;
        }

        @Override
        public long getTouchTimestamp() {
            return 0;
        }
    }
}
