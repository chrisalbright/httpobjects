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
package org.httpobjects.servlet;

import java.io.IOException;
import java.io.OutputStream;

import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import akka.dispatch.OnComplete;
import org.httpobjects.HttpObject;
import org.httpobjects.Request;
import org.httpobjects.Response;
import scala.concurrent.ExecutionContext;
import scala.concurrent.Future;

@SuppressWarnings("serial")
public final class ServletAdapter extends HttpServlet {
	private HttpObject p;
	public ServletAdapter(HttpObject p) {
		this.p = p;
	}


	private void returnResponse(Response r, HttpServletResponse resp) throws IOException {
		
		resp.setStatus(r.code().value());
		
		if(r.hasRepresentation()){
			OutputStream out = resp.getOutputStream();
			r.representation().write(out);
			out.close();
		}
	}

	private void returnResponse(Future<Response> futureResponse, final HttpServletResponse resp, ExecutionContext ctx) throws ServletException, IOException  {
		futureResponse.onComplete(new OnComplete<Response>() {
			@Override
			public void onComplete(Throwable failure, Response r) throws Throwable {
				if (null == failure)
					returnResponse(r, resp);
				else
					throw failure;
			}
		}, ctx);
	}

	@Override
	protected void doGet(HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
		returnResponse(p.get(wrap(req)), resp, p.getExecutionContext());
	}

	@Override
	protected long getLastModified(HttpServletRequest req) {
		// TODO Auto-generated method stub
		return super.getLastModified(req);
	}

	@Override
	protected void doHead(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		returnResponse(p.head(wrap(req)), resp, p.getExecutionContext());
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		returnResponse(p.post(wrap(req)), resp, p.getExecutionContext());
	}

	@Override
	protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		returnResponse(p.put(wrap(req)), resp, p.getExecutionContext());
	}

	@Override
	protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		returnResponse(p.delete(wrap(req)), resp, p.getExecutionContext());
	}

	@Override
	protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		returnResponse(p.options(wrap(req)), resp, p.getExecutionContext());
	}

	@Override
	protected void doTrace(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		returnResponse(p.trace(wrap(req)), resp, p.getExecutionContext());
	}
	
	private Request wrap(HttpServletRequest req){
		throw new RuntimeException("NOT IMPLEMENTED");
	}
	
	@Override
	protected final void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		super.service(req, resp);
	}

	@Override
	public final void service(ServletRequest req, ServletResponse res) throws ServletException, IOException {
		super.service(req, res);
	}
}
