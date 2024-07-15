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
package inetsoft.uql.rest.xml;

import inetsoft.uql.rest.*;
import inetsoft.uql.rest.json.RestDataIteratorStrategyFactory;
import inetsoft.uql.rest.pagination.HttpResponseParameterParser;
import inetsoft.uql.rest.pagination.PagedIterationIteratorStrategy;

import static inetsoft.uql.rest.AbstractRestRuntime.isHttpUrl;

public class XMLRestDataIteratorStrategyFactory implements RestDataIteratorStrategyFactory<RestXMLQuery, Object> {
    public XMLRestDataIteratorStrategyFactory() {
        this(new HttpHandler("application/xml"));
    }

    public XMLRestDataIteratorStrategyFactory(IHttpHandler httpHandler) {
        this.httpHandler = httpHandler;
    }

    @Override
    public AbstractRestDataIteratorStrategy<RestXMLQuery, ? extends AbstractXMLStreamTransformer> createStrategy(
       RestXMLQuery query) throws Exception
    {
        final AbstractRestDataIteratorStrategy<RestXMLQuery, ? extends AbstractXMLStreamTransformer> strategy;
        final AbstractRestDataSource dataSource = ((AbstractRestDataSource) query.getDataSource());

        if(isHttpUrl(dataSource.getURL())) {
            strategy = createHttpRestDataIterator(query);
        }
        else {
            strategy = new XMLNonHttpIteratorStrategy(query, new XMLBasicStreamTransformer(query));
        }

        return strategy;
    }

    /**
     * Create the appropriate HTTP strategy to use based on the pagination type.
     */
    private AbstractRestDataIteratorStrategy<RestXMLQuery, ? extends AbstractXMLStreamTransformer> createHttpRestDataIterator(
       RestXMLQuery query) throws Exception
    {
        final AbstractRestDataIteratorStrategy<RestXMLQuery, ? extends AbstractXMLStreamTransformer> strategy;
        final RestErrorHandler errorHandler = new RestErrorHandler();

        switch(query.getPaginationType()) {
            case NONE:
                strategy =
                   new XMLUnpagedDataIterator(query, new XMLBasicStreamTransformer(query), httpHandler, errorHandler);
                break;
            case PAGE_COUNT:
                strategy = new XMLPagedPageCountIteratorStrategy(query, new XMLPagedStreamTransformer(query),
                   httpHandler, errorHandler);
                break;
            case ITERATION:
                final XMLIterationStreamTransformer transformer = new XMLIterationStreamTransformer(query);
                final HttpResponseParameterParser parser = new HttpResponseParameterParser(transformer);
                strategy = new PagedIterationIteratorStrategy<>(query, transformer, httpHandler, errorHandler, parser);
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + query.getPaginationType());
        }

        return strategy;
    }

    private final IHttpHandler httpHandler;
}
