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
package inetsoft.uql.rest.json;

import inetsoft.uql.rest.*;
import inetsoft.uql.rest.datasource.graphql.GraphQLQuery;
import inetsoft.uql.rest.datasource.twitter.TwitterQuery;
import inetsoft.uql.rest.pagination.HttpResponseParameterParser;
import inetsoft.uql.rest.pagination.PagedIterationIteratorStrategy;

/**
 * Factory which handles the creation of {@link RestDataIteratorStrategy} for RestJsonQuery.
 */
public class JsonRestDataIteratorStrategyFactory implements RestDataIteratorStrategyFactory<RestJsonQuery, Object> {
    public JsonRestDataIteratorStrategyFactory(JsonTransformer transformer, HttpResponseParameterParser parser) {
        this.transformer = transformer;
        this.parser = parser;
    }

    @Override
    public RestDataIteratorStrategy<Object> createStrategy(RestJsonQuery query) {
        final RestDataIteratorStrategy<Object> strategy;

        final AbstractRestDataSource dataSource = ((AbstractRestDataSource) query.getDataSource());
        final String url = dataSource.getURL();

        if(url == null || url.isEmpty()) {
            strategy = new NoopRestDataIterator<>();
        }
        else if(AbstractRestRuntime.isHttpUrl(url)) {
            strategy = createHttpRestDataIterator(query);
        }
        else {
            strategy = new JsonNonHttpIteratorStrategy(query, transformer);
        }

        return strategy;
    }

    /**
     * Create the appropriate HTTP strategy to use based on the pagination type.
     */
    private RestDataIteratorStrategy<Object> createHttpRestDataIterator(RestJsonQuery query) {
        final RestDataIteratorStrategy<Object> strategy;
        final IHttpHandler httpHandler = new HttpHandler();
        final RestErrorHandler errorHandler = new RestErrorHandler();

        switch(query.getPaginationType()) {
            case NONE:
                strategy = new JsonUnpagedRestDataIteratorStrategy(query, transformer, httpHandler, errorHandler);
                break;
            case PAGE:
                strategy = new JsonPagedPageNumberIteratorStrategy(query, transformer, httpHandler, errorHandler);
                break;
            case PAGE_COUNT:
                strategy =
                   new JsonPagedPageCountIteratorStrategy(query, transformer, httpHandler, errorHandler, parser);
                break;
            case OFFSET:
                strategy = new JsonPagedOffsetIteratorStrategy(query, transformer, httpHandler, errorHandler);
                break;
            case TOTAL_COUNT_AND_OFFSET:
                strategy =
                   new JsonTotalCountAndOffsetIteratorStrategy(query, transformer, httpHandler, errorHandler, parser);
                break;
            case TOTAL_COUNT_AND_PAGE:
                strategy = new JsonTotalCountAndPageIteratorStrategy(query, transformer, httpHandler, errorHandler, parser);
                break;
            case ITERATION:
                strategy = new PagedIterationIteratorStrategy<>(query, transformer, httpHandler, errorHandler, parser);
                break;
            case TWITTER_ITERATION:
                strategy = new TwitterRestDataIteratorStrategy((TwitterQuery) query, transformer, httpHandler,
                   errorHandler, parser);
                break;
            case LINK_ITERATION:
                strategy = new JsonPagedLinkIterationStrategy(query, transformer, httpHandler, errorHandler, parser);
                break;
            case GRAPHQL:
                strategy = new GraphQLIteratorStrategy((GraphQLQuery) query, transformer, httpHandler, errorHandler);
                break;
            case GRAPHQL_CURSOR:
                strategy = new GraphQLCursorIteratorStrategy((GraphQLQuery) query, transformer, httpHandler, errorHandler);
                break;
        default:
                throw new IllegalStateException("Unexpected value: " + query.getPaginationType());
        }

        return strategy;
    }

    private static final class NoopRestDataIterator<E> implements RestDataIteratorStrategy<E> {
        @Override
        public boolean hasNext() {
            return false;
        }

        @Override
        public E next() {
            return null;
        }

        @Override
        public void close() {
            // no-op
        }
    }

    private final JsonTransformer transformer;
    private final HttpResponseParameterParser parser;
}
