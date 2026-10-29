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
import inetsoft.uql.rest.json.EndpointJsonQuery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins the real {@link EndpointJsonQuery} behavior that the worksheet edit path relies on when it
 * resets an omitted lookupExpandArrays / lookupTopLevelOnly to the connector default: a fresh
 * query defaults both flags to true, and clone() copies a previously set false.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, CredentialTestConfig.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
public class EndpointJsonQueryLookupFlagsTest {
    @Test
    void freshQueryDefaultsBothLookupFlagsToTrue() {
        TestQuery query = new TestQuery();

        assertTrue(query.isLookupExpanded());
        assertTrue(query.isLookupTopLevelOnly());
    }

    @Test
    void cloneCarriesPreviouslySetFalseFlags() {
        TestQuery query = new TestQuery();
        query.setLookupExpanded(false);
        query.setLookupTopLevelOnly(false);

        EndpointJsonQuery<?> copy = query.clone();

        assertFalse(copy.isLookupExpanded());
        assertFalse(copy.isLookupTopLevelOnly());
    }

    @Test
    void cloneFlagsAreIndependentOfTheOriginal() {
        TestQuery query = new TestQuery();
        query.setLookupExpanded(false);

        EndpointJsonQuery<?> copy = query.clone();
        copy.setLookupExpanded(true);

        assertFalse(query.isLookupExpanded());
        assertTrue(copy.isLookupExpanded());
    }
}
