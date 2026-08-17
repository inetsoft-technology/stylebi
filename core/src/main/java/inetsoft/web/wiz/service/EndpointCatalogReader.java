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

package inetsoft.web.wiz.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.web.wiz.model.WizEndpointCatalog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.InputStream;

/**
 * Reads a connector's endpoint catalogue straight off its own classpath resource.
 *
 * <p>Deliberately not {@code EndpointJsonQuery.Endpoints.load}. That method binds each connector's
 * own {@code Endpoints} subclass, which core cannot reference, and it reads SreeEnv. More
 * importantly it converts a parse failure into an EMPTY map so the connector keeps starting — the
 * right trade for a query path, the wrong one for a catalogue read, where "empty" and "broken" must
 * stay distinguishable.</p>
 *
 * <p>The mapper is a plain one that ignores unknown properties, via the annotations on the model.
 * See {@code WizEndpointCatalogEntry} for why that is the opposite of the loader's requirement.</p>
 */
@Service
public class EndpointCatalogReader {
   /**
    * @param queryClass the connector's query class, which owns the resource.
    *
    * @return the catalogue, or null when this connector ships none — the same test
    *         {@code WizDatabaseController.classifyQueryClass} uses to answer ENDPOINT_CATALOG.
    *
    * @throws java.io.IOException when the resource exists but does not parse. A broken catalogue is
    *                             reported, never silently rendered as an empty one.
    */
   public WizEndpointCatalog read(Class<?> queryClass) throws java.io.IOException {
      try(InputStream input = queryClass.getResourceAsStream(RESOURCE)) {
         if(input == null) {
            return null;
         }

         WizEndpointCatalog catalog = MAPPER.readValue(input, WizEndpointCatalog.class);
         LOG.debug("Read {} endpoint(s) for {}",
                   catalog.endpoints() == null ? 0 : catalog.endpoints().size(), queryClass);
         return catalog;
      }
   }

   private static final String RESOURCE = "endpoints.json";
   private static final ObjectMapper MAPPER = new ObjectMapper();
   private static final Logger LOG = LoggerFactory.getLogger(EndpointCatalogReader.class);
}
