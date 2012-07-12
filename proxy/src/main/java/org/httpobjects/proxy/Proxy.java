/**
 * Copyright (C) 2011, 2012 Commission Junction Inc.
 *
 * This file is part of httpobjects.
 *
 * httpobjects is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2, or (at your option)
 * any later version.
 *
 * httpobjects is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with httpobjects; see the file COPYING.  If not, write to the
 * Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 * 02110-1301 USA.
 *
 * Linking this library statically or dynamically with other modules is
 * making a combined work based on this library.  Thus, the terms and
 * conditions of the GNU General Public License cover the whole
 * combination.
 *
 * As a special exception, the copyright holders of this library give you
 * permission to link this library with independent modules to produce an
 * executable, regardless of the license terms of these independent
 * modules, and to copy and distribute the resulting executable under
 * terms of your choice, provided that you also meet, for each linked
 * independent module, the terms and conditions of the license of that
 * module.  An independent module is a module which is not derived from
 * or based on this library.  If you modify this library, you may extend
 * this exception to your version of the library, but you are not
 * obligated to do so.  If you do not wish to do so, delete this
 * exception statement from your version.
 */
package org.httpobjects.proxy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethodBase;
import org.apache.commons.httpclient.URI;
import org.apache.commons.httpclient.URIException;
import org.apache.commons.httpclient.methods.EntityEnclosingMethod;
import org.apache.commons.httpclient.methods.DeleteMethod;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.InputStreamRequestEntity;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.PutMethod;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.httpobjects.HttpObject;
import org.httpobjects.Representation;
import org.httpobjects.Request;
import org.httpobjects.Response;
import org.httpobjects.ResponseCode;
import org.httpobjects.header.HeaderField;
import org.httpobjects.header.OtherHeaderField;
import org.httpobjects.header.response.LocationField;
import org.httpobjects.header.response.SetCookieField;

public class Proxy extends HttpObject {
	private final Log log = LogFactory.getLog(getClass());
	private String base;
	private final String me;
	
	public Proxy(final String base, final String me) {
		super("/{path*}", null);
		setBase(base);
		this.me = me;
	}

    public void setBase(String base) {
		this.base = stripTrailingSlash(base);
    }

    public String getBase() {
        return base;
    }

	@Override
	public Response get(Request req) {
		return proxyRequest(req, new GetMethod());
	}

    @Override
	public Response delete(Request req){
		return proxyRequest(req, new DeleteMethod());
    }

	@Override
	public Response put(Request req) {
		PutMethod m = new PutMethod();

		setRequestRepresentation(req, m);
		return proxyRequest(req, m);
	}

	protected void setRequestRepresentation(Request req, EntityEnclosingMethod method){
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		req.representation().write(out);
		method.setRequestEntity(new InputStreamRequestEntity(new ByteArrayInputStream(out.toByteArray())));
	}
	
	@Override
	public Response post(Request req) {
		PostMethod m = new PostMethod();

		setRequestRepresentation(req, m);
		return proxyRequest(req, m);
	}
	
	protected Response proxyRequest(Request req, final HttpMethodBase method) {
		method.setFollowRedirects(false);
		
		String path = req.pathVars().valueFor("path");
		if(!path.startsWith("/")) path = "/" + path;
		String query = req.query();
		if(query==null){
			query = "";
		}else{
			query = "?" + query;
		}
		String url = base + path + query;
		log.debug("doing a " + method.getClass().getSimpleName() + " for " + url);
//		log.debug("Content type is " + req.representation().contentType());
		try {
			method.setURI(new URI(url, true));
		} catch (URIException e1) {
			throw new RuntimeException("Error with uri: " + url, e1);
		}

        addRequestHeaders(req, method);

        if(req.representation()!=null){
			method.addRequestHeader("Content-Type", req.representation().contentType());
		}
		
		for(Header next : method.getRequestHeaders()){
			log.debug("Sending header: " + next);
		}

        HttpClient client = createHttpClient();

        return executeMethod(client, method, req);

	}

    protected Response executeMethod(HttpClient client, HttpMethodBase method, Request req) {
        try {
            int codeValue = client.executeMethod(method);

            ResponseCode responseCode = ResponseCode.forCode(codeValue);
            if(responseCode==null){
                log.error("Unknown response code: " + codeValue);
            }else{
                log.debug("Response was " + responseCode);
            }

            List<HeaderField> headersReturned = extractResponseHeaders(method);

            return createResponse(method, responseCode, headersReturned);

        } catch (Exception e) {
			log.error("Error proxying", e);
			return BAD_GATEWAY();
		}
    }

    protected HttpClient createHttpClient() {
        return new HttpClient();
    }

    protected void addRequestHeaders(Request req, final HttpMethodBase method) {
        for(HeaderField next : req.header().fields()) {
        	method.addRequestHeader(next.name(), next.value());
        }
    }

    protected List<HeaderField> extractResponseHeaders(HttpMethodBase method) {
        List<HeaderField> headersReturned = new ArrayList<HeaderField>();
        for(Header h : method.getResponseHeaders()){
            log.debug("Found header: " + h.getName() + "=" + h.getValue());
            String name = h.getName();
            String value = h.getValue();
            if(name.equals("Set-Cookie")){
            	SetCookieField setCookieField = SetCookieField.fromHeaderValue(value);
                log.debug("Cookie found: " + setCookieField);
                headersReturned.add(setCookieField);
            }else if(name.equals("Location")){
                String a = value.replaceAll(Pattern.quote(base), me);
                log.debug("Redirecting to " + a);
                headersReturned.add(new LocationField(a));
            }else {
                headersReturned.add(new OtherHeaderField(name, value));
            }
        }
        return headersReturned;
    }

    protected Response createResponse(final HttpMethodBase method, ResponseCode responseCode, List<HeaderField> headersReturned) {
        return new Response(responseCode, new Representation(){
            @Override
            public String contentType() {
                Header h = method.getResponseHeader("Content-Type");
                return h==null?null:h.getValue();
            }

            @Override
            public void write(OutputStream out) {
                try {
                	if(method.getResponseBodyAsStream() != null){
                		
                		byte[] buffer = new byte[1024];
                		InputStream in = method.getResponseBodyAsStream();
                		for(int x=in.read(buffer);x!=-1;x=in.read(buffer)){
                			out.write(buffer, 0, x);
                		}
//                		IOUtils.copy(method.getResponseBodyAsStream(), out);
                	}
                		
                } catch (IOException e) {
                	e.printStackTrace();
                    throw new RuntimeException("Error writing response", e);
                }
            }
        },
        headersReturned.toArray(new HeaderField[]{}));
    }

    protected static final String stripTrailingSlash(String text){
		if(text.endsWith("/") && text.length()>1){
			return text.substring(0, text.length()-1);
		}else{
			return text;
		}
	}
}
