/*
 * Copyright 2010-2013 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.ning.billing.jaxrs.resources;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HEAD;
import javax.ws.rs.OPTIONS;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ning.billing.jaxrs.util.Context;
import com.ning.billing.jaxrs.util.JaxrsUriBuilder;
import com.ning.billing.util.api.AuditUserApi;
import com.ning.billing.util.api.CustomFieldUserApi;
import com.ning.billing.util.api.TagUserApi;

import com.google.common.io.ByteStreams;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;

@Singleton
@Path(JaxrsResource.PLUGINS_PATH + "{subResources:.*}")
public class PluginResource extends JaxRsResourceBase {

    private static final Logger log = LoggerFactory.getLogger(PluginResource.class);
    private static final String UTF_8_STRING = "UTF-8";
    private static final Charset UTF_8 = Charset.forName(UTF_8_STRING);

    private final HttpServlet osgiServlet;

    @Inject
    public PluginResource(@Named("osgi") final HttpServlet osgiServlet,
                          final JaxrsUriBuilder uriBuilder,
                          final TagUserApi tagUserApi,
                          final CustomFieldUserApi customFieldUserApi,
                          final AuditUserApi auditUserApi,
                          final Context context) {
        super(uriBuilder, tagUserApi, customFieldUserApi, auditUserApi, context);
        this.osgiServlet = osgiServlet;
    }

    @DELETE
    public Response doDELETE(@javax.ws.rs.core.Context final HttpServletRequest request,
                             @javax.ws.rs.core.Context final HttpServletResponse response,
                             @javax.ws.rs.core.Context final ServletContext servletContext,
                             @javax.ws.rs.core.Context final ServletConfig servletConfig) throws ServletException, IOException {
        return serviceViaOSGIPlugin(request, response, servletContext, servletConfig);
    }

    @GET
    public Response doGET(@javax.ws.rs.core.Context final HttpServletRequest request,
                          @javax.ws.rs.core.Context final HttpServletResponse response,
                          @javax.ws.rs.core.Context final ServletContext servletContext,
                          @javax.ws.rs.core.Context final ServletConfig servletConfig) throws ServletException, IOException {
        return serviceViaOSGIPlugin(request, response, servletContext, servletConfig);
    }

    @OPTIONS
    public Response doOPTIONS(@javax.ws.rs.core.Context final HttpServletRequest request,
                              @javax.ws.rs.core.Context final HttpServletResponse response,
                              @javax.ws.rs.core.Context final ServletContext servletContext,
                              @javax.ws.rs.core.Context final ServletConfig servletConfig) throws ServletException, IOException {
        return serviceViaOSGIPlugin(request, response, servletContext, servletConfig);
    }

    @POST
    @Consumes("application/x-www-form-urlencoded")
    public Response doFormPOST(final MultivaluedMap<String, String> form,
                               @javax.ws.rs.core.Context final HttpServletRequest request,
                               @javax.ws.rs.core.Context final HttpServletResponse response,
                               @javax.ws.rs.core.Context final ServletContext servletContext,
                               @javax.ws.rs.core.Context final ServletConfig servletConfig) throws ServletException, IOException {
        return serviceViaOSGIPlugin(form, request, response, servletContext, servletConfig);
    }

    @POST
    public Response doPOST(@javax.ws.rs.core.Context final HttpServletRequest request,
                           @javax.ws.rs.core.Context final HttpServletResponse response,
                           @javax.ws.rs.core.Context final ServletContext servletContext,
                           @javax.ws.rs.core.Context final ServletConfig servletConfig) throws ServletException, IOException {
        return serviceViaOSGIPlugin(request, response, servletContext, servletConfig);
    }

    @PUT
    public Response doPUT(@javax.ws.rs.core.Context final HttpServletRequest request,
                          @javax.ws.rs.core.Context final HttpServletResponse response,
                          @javax.ws.rs.core.Context final ServletContext servletContext,
                          @javax.ws.rs.core.Context final ServletConfig servletConfig) throws ServletException, IOException {
        return serviceViaOSGIPlugin(request, response, servletContext, servletConfig);
    }

    @HEAD
    public Response doHEAD(@javax.ws.rs.core.Context final HttpServletRequest request,
                           @javax.ws.rs.core.Context final HttpServletResponse response,
                           @javax.ws.rs.core.Context final ServletContext servletContext,
                           @javax.ws.rs.core.Context final ServletConfig servletConfig) throws ServletException, IOException {
        serviceViaOSGIPlugin(request, response, servletContext, servletConfig);

        // Make sure to return 204
        return Response.noContent().build();
    }

    private Response serviceViaOSGIPlugin(final HttpServletRequest request, final HttpServletResponse response,
                                          final ServletContext servletContext, final ServletConfig servletConfig) throws ServletException, IOException {
        return serviceViaOSGIPlugin(request, request.getInputStream(), response, servletContext, servletConfig);
    }

    private Response serviceViaOSGIPlugin(final MultivaluedMap<String, String> form,
                                          final HttpServletRequest request, final HttpServletResponse response,
                                          final ServletContext servletContext, final ServletConfig servletConfig) throws ServletException, IOException {
        // form will contain form parameters, if any. Even if the request contains such parameters, it may be empty
        // if a filter (e.g. Shiro) has already consumed them (see kludge below)
        return serviceViaOSGIPlugin(request, createInputStream(request, form), response, servletContext, servletConfig);
    }

    private Response serviceViaOSGIPlugin(final HttpServletRequest request, final InputStream inputStream, final HttpServletResponse response,
                                          final ServletContext servletContext, final ServletConfig servletConfig) throws ServletException, IOException {
        prepareOSGIRequest(request, servletContext, servletConfig);
        osgiServlet.service(new OSGIServletRequestWrapper(request, inputStream), new OSGIServletResponseWrapper(response));

        if (response.isCommitted()) {
            if (response.getStatus() >= 400) {
                log.warn("{} responded {}", request.getPathInfo(), response.getStatus());
            }
            // Jersey will want to return 204, but the servlet should have done the right thing already
            return null;
        } else {
            return Response.status(response.getStatus()).build();
        }
    }

    private InputStream createInputStream(final HttpServletRequest request, final MultivaluedMap<String, String> form) throws IOException {
        // /!\ Kludge alert (pierre) /!\
        // This is awful... But because of various servlet filters we have in place, include Shiro,
        // the request parameters and/or body at this point have already been looked at.
        // We can't use @FormParam in PluginResource because we don't know the form parameter names
        // in advance.
        // So... We just stick them back in :-)
        // TODO Support application/x-www-form-urlencoded vs multipart/form-data
        final ByteArrayOutputStream out = new ByteArrayOutputStream();

        final Map<String, String> data = new HashMap<String, String>();
        for (final String key : request.getParameterMap().keySet()) {
            data.put(key, request.getParameter(key));
        }
        for (final String key : form.keySet()) {
            data.put(key, form.getFirst(key));
        }
        appendFormParametersToBody(out, data);

        ByteStreams.copy(request.getInputStream(), out);
        return new ByteArrayInputStream(out.toByteArray());
    }

    private void appendFormParametersToBody(final ByteArrayOutputStream out, final Map<String, String> data) throws IOException {
        int idx = 0;
        for (final String key : data.keySet()) {
            if (idx > 0) {
                out.write("&".getBytes(UTF_8));
            }

            out.write((key + "=" + URLEncoder.encode(data.get(key), UTF_8_STRING)).getBytes(UTF_8));
            idx++;
        }
    }

    private void prepareOSGIRequest(final HttpServletRequest request, final ServletContext servletContext, final ServletConfig servletConfig) {
        request.setAttribute("killbill.osgi.servletContext", servletContext);
        request.setAttribute("killbill.osgi.servletConfig", servletConfig);
    }

    // Request wrapper to hide the /plugins prefix to OSGI bundles and fiddle with the input stream
    private static final class OSGIServletRequestWrapper extends HttpServletRequestWrapper {

        private final InputStream inputStream;

        public OSGIServletRequestWrapper(final HttpServletRequest request, final InputStream inputStream) {
            super(request);
            this.inputStream = inputStream;
        }

        @Override
        public String getPathInfo() {
            return super.getPathInfo().replace(JaxrsResource.PLUGINS_PATH, "");
        }

        @Override
        public String getContextPath() {
            return JaxrsResource.PLUGINS_PATH;
        }

        @Override
        public String getServletPath() {
            return super.getServletPath().replace(JaxrsResource.PLUGINS_PATH, "");
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            return new ServletInputStreamWrapper(inputStream);
        }
    }

    private static final class ServletInputStreamWrapper extends ServletInputStream {

        private final InputStream inputStream;

        public ServletInputStreamWrapper(final InputStream inputStream) {
            this.inputStream = inputStream;
        }

        @Override
        public int read() throws IOException {
            return inputStream.read();
        }
    }

    private static final class OSGIServletResponseWrapper extends HttpServletResponseWrapper {

        public OSGIServletResponseWrapper(final HttpServletResponse response) {
            super(response);
        }
    }
}
