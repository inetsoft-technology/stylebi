/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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
package inetsoft.web.assistant;

import com.fasterxml.jackson.core.io.JsonStringEncoder;
import inetsoft.sree.SreeEnv;
import inetsoft.web.viewsheet.service.LinkUriArgumentResolver;
import jakarta.annotation.PreDestroy;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactoryBuilder;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.io.entity.InputStreamEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.apache.hc.core5.util.Timeout;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.Set;

/**
 * Reverse proxy controller that forwards requests from the browser to the AI assistant server.
 * Maps {@code /api/assistant/proxy/**} to {@code {chat.app.internal.url}/**}.
 *
 * <p>Active only when {@code chat.app.internal.url} is configured (proxy mode). When only
 * {@code chat.app.server.url} is set the browser contacts the assistant directly (direct mode)
 * and this proxy is never reached. Returns 503 when {@code chat.app.internal.url} is not set.</p>
 */
@RestController
public class AssistantProxyController {

   private static final Set<String> FORWARD_REQ_HEADERS = Set.of(
      "authorization", "content-type", "accept", "x-client-id", "x-request-id");

   private static final Set<String> HOP_BY_HOP = Set.of(
      "connection", "transfer-encoding", "upgrade", "keep-alive", "te", "trailer",
      "content-length");

   @RequestMapping("/api/assistant/proxy/**")
   public void proxy(HttpServletRequest request, HttpMethod method, HttpServletResponse response)
      throws IOException
   {
      String proxiedPath = extractProxiedPath(request);

      // sso-complete.html must be served as a simple static page that postMessages the auth
      // code to the opener. If forwarded to nginx (assistant-client), nginx falls back to the
      // React SPA's index.html, causing an auth loop. Serve it inline instead.
      if("/sso-complete.html".equals(proxiedPath) && HttpMethod.GET.equals(method)) {
         serveSsoCompletePage(response);
         return;
      }

      // Reject oversized payloads early using Content-Length to avoid buffering the entire body.
      // Request body is streamed directly to the upstream without loading it into heap.
      long contentLength = request.getContentLengthLong(); // -1 if unknown/chunked

      if(contentLength > MAX_REQUEST_BODY_BYTES) {
         response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
         response.getOutputStream().write("Request body too large".getBytes(StandardCharsets.UTF_8));
         return;
      }

      // Proxy mode requires chat.app.internal.url. When only chat.app.server.url is set the
      // browser contacts the assistant directly and this proxy is never reached.
      String internalBase = SreeEnv.getProperty(AIAssistantController.CHAT_APP_INTERNAL_URL);

      if(internalBase == null || internalBase.trim().isEmpty()) {
         response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
         response.setContentType("text/plain;charset=UTF-8");
         response.getOutputStream().write(
            "Proxy not configured: chat.app.internal.url is not set".getBytes(StandardCharsets.UTF_8));
         return;
      }

      String queryString = request.getQueryString();

      // Derive the browser-facing proxy URL from the incoming request so it works on
      // any host, not just localhost. Used to override window.__ENV__.CHAT_APP_SERVER_URL
      // in proxied HTML responses so the assistant SPA routes API calls through the proxy.
      String styleBIUrl = LinkUriArgumentResolver.getLinkUri(request);

      if(styleBIUrl.endsWith("/")) {
         styleBIUrl = styleBIUrl.substring(0, styleBIUrl.length() - 1);
      }

      final String proxyUrl = styleBIUrl + AIAssistantController.PROXY_PATH_PREFIX;
      UriComponentsBuilder uriBuilder = UriComponentsBuilder
         .fromUriString(normalizeBase(internalBase) + proxiedPath);

      if(queryString != null) {
         uriBuilder.replaceQuery(queryString);
      }

      URI targetUri = uriBuilder.build(true).toUri();
      BasicClassicHttpRequest proxyRequest = new BasicClassicHttpRequest(method.name(), targetUri);

      // Forward selected request headers
      Collections.list(request.getHeaderNames()).stream()
         .filter(n -> FORWARD_REQ_HEADERS.contains(n.toLowerCase()))
         .forEach(n -> proxyRequest.addHeader(n, request.getHeader(n)));

      // Set x-forwarded-* headers so the upstream server (assistant) can reconstruct the
      // public-facing URL for redirect targets such as sso-complete.html.
      // Without these the assistant only sees its internal hostname (e.g. assistant-client)
      // instead of the public StyleBI hostname, causing redirect validation to fail in
      // non-localhost production deployments.
      String forwardedHost = request.getHeader(HttpHeaders.HOST);

      if(forwardedHost != null) {
         proxyRequest.setHeader("x-forwarded-host", forwardedHost);
      }

      proxyRequest.setHeader("x-forwarded-proto", request.getScheme());

      // Disable compression so the response can be streamed incrementally.
      // Gzip requires buffering the full stream before decompression.
      proxyRequest.setHeader("Accept-Encoding", "identity");

      // Stream request body to upstream without buffering the entire payload in heap.
      // contentLength is -1 for chunked/unknown bodies; InputStreamEntity handles both cases.
      // For chunked bodies wrap with LimitedInputStream to enforce MAX_REQUEST_BODY_BYTES even
      // when no Content-Length header is present.
      if(contentLength != 0) {
         InputStream bodyStream = contentLength < 0
            ? new LimitedInputStream(request.getInputStream(), MAX_REQUEST_BODY_BYTES)
            : request.getInputStream();
         proxyRequest.setEntity(new InputStreamEntity(bodyStream, contentLength, null));
      }

      CloseableHttpClient client = proxiedPath.contains("/api/chat")
         ? getChatClient() : getDefaultClient();

      // Capture a final copy of internalBase for use inside the response-handler lambda.
      final String upstreamBase = internalBase;

      HttpClientResponseHandler<Void> handler = upstreamResponse -> {
         response.setStatus(upstreamResponse.getCode());

         HttpHeaders upstreamHeaders = new HttpHeaders();

         for(var header : upstreamResponse.getHeaders()) {
            upstreamHeaders.add(header.getName(), header.getValue());
         }

         buildResponseHeaders(upstreamHeaders, upstreamBase).forEach((name, values) ->
            values.forEach(v -> response.addHeader(name, v)));

         HttpEntity entity = upstreamResponse.getEntity();

         if(entity != null) {
            String contentType = upstreamHeaders.getFirst(HttpHeaders.CONTENT_TYPE);
            boolean isHtml = contentType != null && contentType.toLowerCase().contains("text/html");

            if(isHtml) {
               // Buffer HTML so we can rewrite absolute asset paths to go through the proxy.
               // The assistant SPA references assets as /assets/... which would 404 at StyleBI's
               // root; rewrite them to /api/assistant/proxy/assets/... so they are proxied.
               byte[] bytes = entity.getContent().readAllBytes();
               String html = new String(bytes, StandardCharsets.UTF_8);
               html = rewriteProxiedHtml(html, proxyUrl);
               byte[] rewritten = html.getBytes(StandardCharsets.UTF_8);
               response.setContentLength(rewritten.length);
               response.getOutputStream().write(rewritten);
            }
            else {
               boolean isSse = contentType != null
                  && contentType.toLowerCase().contains("text/event-stream");

               // Disable Tomcat's output buffer so each chunk/event is sent immediately.
               response.setBufferSize(0);

               if(isSse) {
                  // Commit headers before the first read so the browser starts processing
                  // the event stream immediately rather than waiting for the first event.
                  response.flushBuffer();
               }

               try(InputStream inputStream = entity.getContent()) {
                  byte[] buffer = new byte[8192];
                  int bytesRead;

                  while((bytesRead = inputStream.read(buffer)) != -1) {
                     response.getOutputStream().write(buffer, 0, bytesRead);
                     response.getOutputStream().flush();
                  }
               }
            }
         }

         return null;
      };

      try {
         client.execute(proxyRequest, handler);
      }
      catch(RequestBodyTooLargeException e) {
         if(!response.isCommitted()) {
            response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
            response.getOutputStream().write("Request body too large".getBytes(StandardCharsets.UTF_8));
         }
      }
      catch(IOException e) {
         if(!response.isCommitted()) {
            response.setStatus(HttpServletResponse.SC_BAD_GATEWAY);
            response.getOutputStream().write(
               ("Assistant unreachable: " + e.getMessage()).getBytes(StandardCharsets.UTF_8));
         }
      }
   }

   /**
    * Serves the SSO completion page inline. The simple HTML reads the auth code from the URL
    * and postMessages it to the opener, then closes. Serving this from StyleBI directly avoids
    * nginx falling back to the React SPA's index.html (which causes an auth loop).
    */
   private void serveSsoCompletePage(HttpServletResponse response) throws IOException {
      response.setContentType("text/html;charset=UTF-8");
      response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
      response.setStatus(HttpServletResponse.SC_OK);
      response.getWriter().write(SSO_COMPLETE_HTML);
   }

   private static final String SSO_COMPLETE_HTML =
      "<!DOCTYPE html>\n" +
      "<html>\n" +
      "<head><meta charset=\"UTF-8\"><title>SSO Complete</title></head>\n" +
      "<body>\n" +
      "<script>\n" +
      "  var code = new URLSearchParams(window.location.search).get('code');\n" +
      "  var sent = false;\n" +
      "  if (window.opener) {\n" +
      "    var openerOrigin;\n" +
      "    try { openerOrigin = window.opener.location.origin; } catch(e) {}\n" +
      "    if (openerOrigin) {\n" +
      "      window.opener.postMessage({type:'sso_complete', code:code}, openerOrigin);\n" +
      "      setTimeout(function(){window.close();}, 500);\n" +
      "      sent = true;\n" +
      "    }\n" +
      "  }\n" +
      "  if (!sent) { document.body.textContent = 'Authentication complete. You may close this window.'; }\n" +
      "</script>\n" +
      "</body></html>\n";

   private String extractProxiedPath(HttpServletRequest request) {
      String uri = request.getRequestURI();
      String contextPath = request.getContextPath();
      String fullPrefix = contextPath + AIAssistantController.PROXY_PATH_PREFIX;

      if(uri.startsWith(fullPrefix)) {
         String path = uri.substring(fullPrefix.length());
         return path.isEmpty() ? "/" : path;
      }

      return "/";
   }

   private String normalizeBase(String base) {
      base = base.trim();
      return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
   }

   private HttpHeaders buildResponseHeaders(HttpHeaders upstream, String internalBase) {
      HttpHeaders out = new HttpHeaders();
      upstream.forEach((name, values) -> {
         if("set-cookie".equalsIgnoreCase(name)) {
            // Strip Domain= attribute so the cookie binds to the StyleBI domain
            values.stream()
               .map(v -> v.replaceAll("(?i);?\\s*Domain=[^;]*", ""))
               .forEach(v -> out.add(name, v));
         }
         else if("location".equalsIgnoreCase(name)) {
            // Rewrite Location headers so the browser follows redirects through the proxy.
            // Both root-relative (/path) and absolute upstream URLs are rewritten.
            values.stream()
               .map(v -> rewriteLocation(v, internalBase))
               .forEach(v -> out.add(name, v));
         }
         else if(!HOP_BY_HOP.contains(name.toLowerCase())) {
            out.put(name, values);
         }
      });
      return out;
   }

   /**
    * Rewrites a {@code Location} header value so the browser follows the redirect through
    * the proxy rather than contacting the upstream directly.
    * <ul>
    *   <li>Root-relative paths ({@code /path}) are prefixed with the proxy prefix.</li>
    *   <li>Absolute URLs that target the internal upstream base are stripped to their path
    *       and prefixed with the proxy prefix (handles HTTPS upstreams that return absolute
    *       redirects, e.g. {@code https://assistant:3002/sso-complete.html?code=xxx}).</li>
    *   <li>Protocol-relative ({@code //}), already-proxied, and unrelated absolute URLs
    *       are returned unchanged.</li>
    * </ul>
    */
   private String rewriteLocation(String location, String internalBase) {
      String prefix = AIAssistantController.PROXY_PATH_PREFIX;

      // Root-relative: /path → /api/assistant/proxy/path
      if(location.startsWith("/") && !location.startsWith("//") && !location.startsWith(prefix)) {
         return prefix + location;
      }

      // Absolute URL targeting the internal upstream: strip base and prepend proxy prefix.
      String base = normalizeBase(internalBase);

      if(location.toLowerCase().startsWith(base.toLowerCase())) {
         String rest = location.substring(base.length());

         // Ensure the match is at a path boundary (not a prefix of a longer hostname).
         if(rest.isEmpty() || rest.startsWith("/")) {
            String path = rest.isEmpty() ? "/" : rest;

            if(!path.startsWith(prefix)) {
               return prefix + path;
            }
         }
      }

      return location;
   }

   /**
    * Rewrites proxied HTML to:
    * 1. Inject a script that sets window.__ENV__.CHAT_APP_SERVER_URL to the proxy URL so the
    *    assistant SPA routes its API calls through StyleBI's proxy instead of hitting absolute
    *    paths at StyleBI's root (which would 404).
    * 2. Rewrite absolute URL paths in src/href attributes so asset requests go through the proxy
    *    instead of hitting StyleBI's root (e.g. /assets/foo.js → /api/assistant/proxy/assets/foo.js).
    */
   private static String rewriteProxiedHtml(String html, String proxyUrl) {
      String prefix = AIAssistantController.PROXY_PATH_PREFIX;

      // Inject just before </head> so it runs after env-config.js (synchronous script)
      // but before deferred module scripts that read window.__ENV__.CHAT_APP_SERVER_URL.
      // Use Jackson's JsonStringEncoder for JSON-correct escaping, then additionally
      // replace "</" with "<\/" to prevent </script> injection in the HTML context.
      String jsonEscaped = new String(JsonStringEncoder.getInstance().quoteAsString(proxyUrl))
         .replace("</", "<\\/");
      String envScript = "<script>window.__ENV__=window.__ENV__||{};"
         + "window.__ENV__.CHAT_APP_SERVER_URL=\"" + jsonEscaped + "\";</script>";
      String injected = html.replaceFirst("(?i)</head>",
         java.util.regex.Matcher.quoteReplacement(envScript + "</head>"));

      if(injected.equals(html)) {
         LOG.debug("Could not inject CHAT_APP_SERVER_URL override: </head> not found in proxied HTML");
      }

      html = injected;

      // Rewrite absolute paths in src/href attributes.
      // Skip protocol-relative (//) and already-proxied paths.
      return html.replaceAll(
         "((?:src|href)=['\"])(/(?!/|api/assistant/proxy))",
         "$1" + prefix + "$2");
   }

   private CloseableHttpClient buildClient(int connectSec, int readSec) {
      try {
         var cmBuilder = PoolingHttpClientConnectionManagerBuilder.create();

         // SSL verification is configurable via chat.app.server.ssl.verify.
         // Default: trust all, suitable for private-network deployments with self-signed certs
         // (matching the nginx "proxy_ssl_verify off" used in Docker Compose).
         // Set to "true" to use the JVM default trust store in production.
         // Changing this property requires a server restart.
         if(!AIAssistantController.isSslVerifyEnabled()) {
            SSLContext sslContext = SSLContextBuilder.create()
               .loadTrustMaterial(null, (chain, authType) -> true)
               .build();
            cmBuilder.setSSLSocketFactory(
               SSLConnectionSocketFactoryBuilder.create()
                  .setSslContext(sslContext)
                  .setHostnameVerifier(NoopHostnameVerifier.INSTANCE)
                  .build());
         }

         RequestConfig config = RequestConfig.custom()
            .setConnectTimeout(Timeout.ofSeconds(connectSec))
            .setResponseTimeout(Timeout.ofSeconds(readSec))
            .build();

         return HttpClients.custom()
            .setConnectionManager(cmBuilder.build())
            .setDefaultRequestConfig(config)
            .disableRedirectHandling()
            .build();
      }
      catch(NoSuchAlgorithmException | KeyManagementException | KeyStoreException e) {
         throw new RuntimeException("Failed to build proxy HTTP client", e);
      }
   }

   @PreDestroy
   public void destroy() throws IOException {
      if(defaultClient != null) {
         defaultClient.close();
      }

      if(chatClient != null) {
         chatClient.close();
      }
   }

   /**
    * Thrown by {@link LimitedInputStream} when the byte limit is exceeded.
    * Caught separately from {@link IOException} so the proxy can return 413 instead of 502.
    */
   private static final class RequestBodyTooLargeException extends IOException {
      RequestBodyTooLargeException() {
         super("Request body too large");
      }
   }

   /**
    * Wraps an {@link InputStream} and throws {@link RequestBodyTooLargeException} if more than
    * {@code limit} bytes are read. Used to enforce {@link #MAX_REQUEST_BODY_BYTES} on chunked
    * request bodies that carry no {@code Content-Length} header.
    */
   private static final class LimitedInputStream extends InputStream {
      private final InputStream delegate;
      private long remaining;

      LimitedInputStream(InputStream delegate, long limit) {
         this.delegate = delegate;
         this.remaining = limit;
      }

      @Override
      public int read() throws IOException {
         if(remaining <= 0) {
            throw new RequestBodyTooLargeException();
         }

         int b = delegate.read();

         if(b >= 0) {
            remaining--;
         }

         return b;
      }

      @Override
      public int read(byte[] b, int off, int len) throws IOException {
         if(remaining <= 0) {
            throw new RequestBodyTooLargeException();
         }

         int toRead = (int) Math.min(len, remaining);
         int n = delegate.read(b, off, toRead);

         if(n > 0) {
            remaining -= n;
         }

         return n;
      }

      @Override
      public void close() throws IOException {
         delegate.close();
      }
   }

   private volatile CloseableHttpClient defaultClient;
   private volatile CloseableHttpClient chatClient;
   private static final int MAX_REQUEST_BODY_BYTES = 50 * 1024 * 1024; // 50 MB
   private static final Logger LOG = LoggerFactory.getLogger(AssistantProxyController.class);

   private CloseableHttpClient getDefaultClient() {
      if(defaultClient == null) {
         synchronized(this) {
            if(defaultClient == null) {
               defaultClient = buildClient(10, 30);
            }
         }
      }

      return defaultClient;
   }

   private CloseableHttpClient getChatClient() {
      if(chatClient == null) {
         synchronized(this) {
            if(chatClient == null) {
               chatClient = buildClient(10, 300);
            }
         }
      }

      return chatClient;
   }
}
