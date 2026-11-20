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

package inetsoft.test;

import inetsoft.uql.rest.*;
import inetsoft.uql.rest.json.*;
import inetsoft.uql.rest.json.lookup.LookupService;
import inetsoft.uql.rest.pagination.HttpHeader;
import inetsoft.uql.rest.pagination.HttpResponseParameterParser;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;
import java.util.function.Predicate;

/**
 * An HTTP handler that answers from a script instead of the network, and records the requests it
 * was asked for. A rule matches a request by its URL; the responses of a rule are returned in
 * order, and the last one is repeated. A request that no rule matches fails the test.
 */
public class ScriptedHttpHandler implements IHttpHandler {
   /** Answers the requests whose URL contains the text. */
   public ScriptedHttpHandler on(String urlContains, String... bodies) {
      return on(url -> url.contains(urlContains), bodies);
   }

   /** Answers the requests whose URL the predicate accepts. */
   public ScriptedHttpHandler on(Predicate<String> url, String... bodies) {
      Reply[] replies = new Reply[bodies.length];

      for(int i = 0; i < bodies.length; i++) {
         replies[i] = new Reply(bodies[i], List.of());
      }

      return onReplies(url, replies);
   }

   /** Answers the requests whose URL the predicate accepts, with replies that carry headers. */
   public ScriptedHttpHandler onReplies(Predicate<String> url, Reply... replies) {
      rules.add(new Rule(url, new ArrayDeque<>(Arrays.asList(replies))));
      return this;
   }

   /** A reply with one header. */
   public static Reply reply(String body, String header, String value) {
      return new Reply(body, List.of(new HttpHeader(header, value)));
   }

   /** The URLs requested so far, in order. */
   public List<String> requests() {
      return Collections.unmodifiableList(requests);
   }

   @Override
   public HttpResponse executeRequest(RestRequest request)
      throws IOException, URISyntaxException
   {
      URL url = URLCreator.fromRestRequest(request);
      String text = url.toString();
      requests.add(text);

      if(requests.size() > MAX_REQUESTS) {
         throw new AssertionError("More than " + MAX_REQUESTS + " requests, the pagination does not " +
                                  "stop; the last ones: " +
                                  requests.subList(requests.size() - 3, requests.size()));
      }

      for(Rule rule : rules) {
         if(rule.url.test(text)) {
            Reply reply = rule.replies.size() > 1 ? rule.replies.poll() : rule.replies.peek();
            return new TestHttpResponse(reply.body(), reply.headers());
         }
      }

      throw new AssertionError("No response scripted for " + text + "; requests so far: " + requests);
   }

   @Override
   public void close() {
      // no-op
   }

   /** A runtime that sends the requests of a query to the given handler. */
   public static class Runtime extends EndpointJsonRuntime {
      public Runtime(IHttpHandler handler) {
         this.handler = handler;
      }

      @Override
      protected QueryRunner getQueryRunner(AbstractRestQuery q) {
         JsonTransformer transformer = new JsonTransformer();
         JsonRestDataIteratorStrategyFactory factory = new JsonRestDataIteratorStrategyFactory(
            transformer, new HttpResponseParameterParser(transformer), () -> handler);
         return new EndpointJsonQueryRunner(
            (EndpointJsonQuery<?>) q, factory, new LookupService(), transformer);
      }

      private final IHttpHandler handler;
   }

   /** A response body and its headers. */
   public record Reply(String body, List<HttpHeader> headers) {
   }

   private record Rule(Predicate<String> url, Deque<Reply> replies) {
   }

   private static final int MAX_REQUESTS = 50;
   private final List<Rule> rules = new ArrayList<>();
   private final List<String> requests = new ArrayList<>();
}
