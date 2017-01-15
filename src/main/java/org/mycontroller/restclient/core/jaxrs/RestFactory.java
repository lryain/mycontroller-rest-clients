/*
 * Copyright 2015-2016 Jeeva Kandasamy (jkandasa@gmail.com)
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.mycontroller.restclient.core.jaxrs;

import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;

import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContextBuilder;
import org.jboss.resteasy.client.jaxrs.ProxyBuilder;
import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.jboss.resteasy.client.jaxrs.engines.ApacheHttpClient4Engine;
import org.mycontroller.restclient.core.ClientInfo;
import org.mycontroller.restclient.core.TRUST_HOST_TYPE;
import org.mycontroller.restclient.core.jaxrs.fasterxml.jackson.JacksonObjectMapperProvider;
import org.mycontroller.restclient.core.jaxrs.fasterxml.jackson.MCJacksonJson2Provider;

import com.fasterxml.jackson.jaxrs.json.JacksonJaxbJsonProvider;

import lombok.extern.slf4j.Slf4j;

/**
 * @author Jeeva Kandasamy (jkandasa)
 * @since 2.0.0
 */

@Slf4j
public class RestFactory<T> {

    private final ClassLoader classLoader;
    private Class<T> apiClassType;

    public RestFactory(Class<T> clz) {
        classLoader = null;
        apiClassType = clz;
    }

    public RestFactory(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    public T createAPI(ClientInfo clientInfo) {
        final HttpClient httpclient;
        if (clientInfo.getEndpointUri().toString().startsWith("https")
                && clientInfo.getTrustHostType() == TRUST_HOST_TYPE.ANY) {
            httpclient = getHttpClient();
        } else {
            httpclient = HttpClientBuilder.create().build();
        }

        ApacheHttpClient4Engine engine = null;
        if (clientInfo.getUsername().isPresent() && clientInfo.getPassword().isPresent()) {
            HttpHost targetHost = new HttpHost(clientInfo.getEndpointUri().getHost(), clientInfo.getEndpointUri()
                    .getPort());
            CredentialsProvider credsProvider = new BasicCredentialsProvider();
            credsProvider.setCredentials(
                    new AuthScope(targetHost.getHostName(), targetHost.getPort()),
                    new UsernamePasswordCredentials(clientInfo.getUsername().get(), clientInfo.getPassword().get()));
            // Create AuthCache instance
            AuthCache authCache = new BasicAuthCache();
            // Generate BASIC scheme object and add it to the local auth cache
            BasicScheme basicAuth = new BasicScheme();
            authCache.put(targetHost, basicAuth);
            // Add AuthCache to the execution context
            HttpClientContext context = HttpClientContext.create();
            context.setCredentialsProvider(credsProvider);
            context.setAuthCache(authCache);
            engine = new ApacheHttpClient4Engine(httpclient, context);
        } else {
            engine = new ApacheHttpClient4Engine(httpclient);
        }
        final ResteasyClient client = new ResteasyClientBuilder().httpEngine(engine).build();
        client.register(JacksonJaxbJsonProvider.class);
        client.register(JacksonObjectMapperProvider.class);
        client.register(RestRequestFilter.class);
        client.register(new RequestHeadersFilter(clientInfo.getHeaders()));
        client.register(RestResponseFilter.class);
        client.register(MCJacksonJson2Provider.class);

        ProxyBuilder<T> proxyBuilder = client.target(clientInfo.getEndpointUri()).proxyBuilder(apiClassType);
        if (classLoader != null) {
            proxyBuilder = proxyBuilder.classloader(classLoader);
        }
        return proxyBuilder.build();
    }

    //trust any host
    private HttpClient getHttpClient() {
        SSLContextBuilder builder = new SSLContextBuilder();
        try {
            KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            builder.loadTrustMaterial(keyStore, new TrustStrategy() {
                @Override
                public boolean isTrusted(X509Certificate[] trustedCert, String nameConstraints)
                        throws CertificateException {
                    return true;
                }
            });
            SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(builder.build(),
                    new McRestAnyHostnameVerifier());
            CloseableHttpClient httpclient = HttpClients.custom().setSSLSocketFactory(sslsf).build();
            return httpclient;
        } catch (Exception ex) {
            _logger.error("Exception, ", ex);
            return null;
        }
    }

    //Trust all hostname
    class McRestAnyHostnameVerifier implements HostnameVerifier {
        @Override
        public boolean verify(String hostname, SSLSession session) {
            return true;
        }

    }
}