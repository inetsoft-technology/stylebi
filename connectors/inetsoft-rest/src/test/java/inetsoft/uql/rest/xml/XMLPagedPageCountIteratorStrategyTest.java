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

import inetsoft.test.*;
import inetsoft.uql.rest.HttpResponse;
import inetsoft.uql.rest.IHttpHandler;
import inetsoft.uql.rest.RestErrorHandler;
import inetsoft.uql.rest.RestRequest;
import inetsoft.uql.rest.pagination.PaginationParamType;
import inetsoft.uql.rest.pagination.PaginationParameter;
import inetsoft.uql.rest.pagination.PaginationSpec;
import inetsoft.uql.rest.pagination.PaginationType;
import inetsoft.uql.rest.xml.parse.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, CredentialTestConfig.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
public class XMLPagedPageCountIteratorStrategyTest {
    @Test
    public void test() throws Exception {
        final RestXMLQuery query = new RestXMLQuery();
        final RestXMLDataSource datasource = new RestXMLDataSource();
        datasource.setURL("http://host/{{pageNumber}}");
        query.setDataSource(datasource);
        query.setXpath("/data/id/text()");
        final PaginationSpec spec = query.getPaginationSpec();
        spec.setType(PaginationType.PAGE_COUNT);
        spec.setPageNumberUrlVariable(new PaginationParameter("pageNumber", PaginationParamType.URL_VARIABLE));
        spec.setPageCountXpath(new PaginationParameter("/data/pageCount", PaginationParamType.XPATH));
        spec.setZeroBasedPageIndex(false);

        final List<RequestResponse> requestResponses = new ArrayList<>();
        final String page1 = "<data><pageCount>3</pageCount><id>1</id></data>";
        final String page2 = "<data><pageCount>3</pageCount><id>2</id></data>";
        final String page3 = "<data><pageCount>3</pageCount><id>3</id><id>4</id></data>";

        requestResponses.add(new RequestResponse(RestRequest.builder().query(query)
           .urlVariables(Collections.singletonMap("pageNumber", "1"))
           .build(), new TestHttpResponse(page1)));
        requestResponses.add(new RequestResponse(RestRequest.builder().query(query)
           .urlVariables(Collections.singletonMap("pageNumber", "2"))
           .build(), new TestHttpResponse(page2)));
        requestResponses.add(new RequestResponse(RestRequest.builder().query(query)
           .urlVariables(Collections.singletonMap("pageNumber", "3"))
           .build(), new TestHttpResponse(page3)));

        final TestHttpHandler httpHandler = new TestHttpHandler(requestResponses);
        final XMLPagedStreamTransformer transformer = new XMLPagedStreamTransformer(query);

        final XMLPagedPageCountIteratorStrategy strategy = new XMLPagedPageCountIteratorStrategy(
           query, transformer, httpHandler, new RestErrorHandler());

        List<Map<String, Object>> values = new ArrayList<>();
        values.add(new MapNode(Collections.singletonMap("#text", new ValueNode(1))));
        values.add(new MapNode(Collections.singletonMap("#text", new ValueNode(2))));
        values.add(new MapNode(Collections.singletonMap("#text", Arrays.asList(new ValueNode(3), new ValueNode(4)))));
        int count = 0;

        for(int i = 0; i < values.size(); i++, count++) {
            assertEquals(values.get(i), strategy.next());
        }

        assertFalse(strategy.hasNext());
        assertEquals(values.size(), count);
    }

    @Test
    public void attributePageCountOneBased() throws Exception {
        final String[] pages = {
           "<data pages=\"3\"><id>1</id></data>",
           "<data pages=\"3\"><id>2</id></data>",
           "<data pages=\"3\"><id>3</id></data>" };
        assertEquals(Arrays.asList(1, 2, 3), run("/data/@pages", "/data/id/text()", false, 1, pages));
    }

    @Test
    public void attributePageCountZeroBased() throws Exception {
        final String[] pages = {
           "<data pages=\"3\"><id>1</id></data>",
           "<data pages=\"3\"><id>2</id></data>",
           "<data pages=\"3\"><id>3</id></data>" };
        assertEquals(Arrays.asList(1, 2, 3), run("/data/@pages", "/data/id/text()", true, 0, pages));
    }

    @Test
    public void namespacedAttributePageCount() throws Exception {
        final String[] pages = new String[3];

        for(int i = 0; i < 3; i++) {
            pages[i] = "<wb:countries page=\"" + (i + 1) + "\" pages=\"3\" " +
               "xmlns:wb=\"http://www.worldbank.org\"><wb:country>" + (i + 1) +
               "</wb:country></wb:countries>";
        }

        assertEquals(Arrays.asList(1, 2, 3),
           run("/wb:countries/@pages", "/wb:countries/wb:country/text()", false, 1, pages));
        assertEquals(Arrays.asList(1, 2, 3),
           run("/*/@pages", "/wb:countries/wb:country/text()", false, 1, pages));
    }

    @Test
    public void nestedElementAttributePageCount() throws Exception {
        final String[] pages = {
           "<data><meta total=\"2\"/><id>1</id></data>",
           "<data><meta total=\"2\"/><id>2</id></data>" };
        assertEquals(Arrays.asList(1, 2), run("/data/meta/@total", "/data/id/text()", false, 1, pages));
    }

    @Test
    public void attributePageCountOfOneFetchesSinglePage() throws Exception {
        final String[] pages = { "<data pages=\"1\"><id>1</id></data>" };
        assertEquals(Collections.singletonList(1),
           run("/data/@pages", "/data/id/text()", false, 1, pages));
    }

    @Test
    public void absentPageCountFetchesSinglePageWithoutError() throws Exception {
        final String[] pages = { "<data><id>1</id></data>" };
        assertEquals(Collections.singletonList(1),
           run("/data/@pages", "/data/id/text()", false, 1, pages));
    }

    @Test
    public void unmatchedAttributeXpathFetchesSinglePage() throws Exception {
        final String[] pages = { "<data pages=\"3\"><id>1</id></data>" };
        assertEquals(Collections.singletonList(1),
           run("/data/@other", "/data/id/text()", false, 1, pages));
    }

    @Test
    public void multiMatchElementPageCountLastWins() throws Exception {
        final String[] pages = {
           "<data><n>1</n><n>2</n><id>1</id></data>",
           "<data><n>1</n><n>2</n><id>2</id></data>" };
        assertEquals(Arrays.asList(1, 2), run("//n", "/data/id/text()", false, 1, pages));
    }

    @Test
    public void failedFirstRequestDoesNotWarnAboutPageCount() throws Exception {
        final RestXMLQuery query = new RestXMLQuery();
        final RestXMLDataSource datasource = new RestXMLDataSource();
        datasource.setURL("http://host/{{pageNumber}}");
        query.setDataSource(datasource);
        query.setXpath("/data/id/text()");
        final PaginationSpec spec = query.getPaginationSpec();
        spec.setType(PaginationType.PAGE_COUNT);
        spec.setPageNumberUrlVariable(new PaginationParameter("pageNumber", PaginationParamType.URL_VARIABLE));
        spec.setPageCountXpath(new PaginationParameter("/data/@pages", PaginationParamType.XPATH));
        spec.setZeroBasedPageIndex(false);

        final IHttpHandler failing = new IHttpHandler() {
            @Override
            public HttpResponse executeRequest(RestRequest request) throws java.io.IOException {
                throw new java.io.IOException("boom");
            }

            @Override
            public void close() {
            }
        };

        final XMLPagedPageCountIteratorStrategy strategy = new XMLPagedPageCountIteratorStrategy(
           query, new XMLPagedStreamTransformer(query), failing, new RestErrorHandler());
        final ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger)
           org.slf4j.LoggerFactory.getLogger(XMLPagedPageCountIteratorStrategy.class);
        final ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>
           appender = new ch.qos.logback.core.read.ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            final Throwable thrown = assertThrows(Throwable.class, strategy::next);
            Throwable root = thrown;

            while(root.getCause() != null) {
                root = root.getCause();
            }

            assertEquals("boom", root.getMessage());
            assertTrue(appender.list.stream()
               .noneMatch(e -> e.getFormattedMessage().contains("matched nothing")));
        }
        finally {
            logger.detachAppender(appender);
        }
    }

    /**
     * Runs the strategy over the given pages and returns the integer values read.
     */
    private List<Integer> run(String pageCountXpath, String rowXpath, boolean zeroBased,
                              int firstPage, String[] pages) throws Exception
    {
        final RestXMLQuery query = new RestXMLQuery();
        final RestXMLDataSource datasource = new RestXMLDataSource();
        datasource.setURL("http://host/{{pageNumber}}");
        query.setDataSource(datasource);
        query.setXpath(rowXpath);
        final PaginationSpec spec = query.getPaginationSpec();
        spec.setType(PaginationType.PAGE_COUNT);
        spec.setPageNumberUrlVariable(new PaginationParameter("pageNumber", PaginationParamType.URL_VARIABLE));
        spec.setPageCountXpath(new PaginationParameter(pageCountXpath, PaginationParamType.XPATH));
        spec.setZeroBasedPageIndex(zeroBased);

        final List<RequestResponse> requestResponses = new ArrayList<>();

        for(int i = 0; i < pages.length; i++) {
            requestResponses.add(new RequestResponse(RestRequest.builder().query(query)
               .urlVariables(Collections.singletonMap("pageNumber", String.valueOf(firstPage + i)))
               .build(), new TestHttpResponse(pages[i])));
        }

        final XMLPagedPageCountIteratorStrategy strategy = new XMLPagedPageCountIteratorStrategy(
           query, new XMLPagedStreamTransformer(query), new TestHttpHandler(requestResponses),
           new RestErrorHandler());
        final List<Integer> values = new ArrayList<>();

        while(strategy.hasNext()) {
            final Object next = strategy.next();
            final Object text = next instanceof Map ? ((Map<?, ?>) next).get("#text") : next;
            values.add(Integer.parseInt(String.valueOf(((ValueNode) text).unwrap())));
        }

        return values;
    }
}
