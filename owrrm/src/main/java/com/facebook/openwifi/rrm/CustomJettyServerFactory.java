/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifi.rrm;

import java.util.concurrent.TimeUnit;

import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.ForwardedRequestCustomizer;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.util.thread.ThreadPool;
import spark.embeddedserver.jetty.JettyServerFactory;
import spark.embeddedserver.jetty.SocketConnectorFactory;
import spark.utils.Assert;

/**
 * Creates Jetty Server instances. Majority of the logic is taken from
 * JettyServerFactory. The additional feature is that this class will actually
 * set two connectors (original class doesn't set any connectors at all and
 * leaves it up to the serivce start logic). Since we set two connectors here
 * on the server, Spark uses the existing conectors instead of trying to spin
 * up its own connectors. The other difference is that it uses a different
 * ServerConnector constructor to avoid allocating additional threads that
 * aren't necessary ({@link #makeConnector})
 * @see EmbeddedJettyFactory
 */
public class CustomJettyServerFactory implements JettyServerFactory {
	// normally this is set in EmbeddedJettyServer but since we create our own connectors here,
	// we need the value here
	private boolean trustForwardHeaders = true; // true by default
	private final int internalPort;
	private final int externalPort;

	public CustomJettyServerFactory(int internalPort, int externalPort) {
		this.internalPort = internalPort;
		this.externalPort = externalPort;
	}

	public void setTrustForwardHeaders(boolean trustForwardHeaders) {
		this.trustForwardHeaders = trustForwardHeaders;
	}

	/**
	 * This is basically
	 * spark.embeddedserver.jetty.SocketConnectorFactory.createSocketConnector,
	 * the only difference being that we use a different constructor for the
	 * Connector and that the private methods called are just inlined.
	 */
	public Connector makeConnector(
		Server server,
		String host,
		int port,
		boolean trustForwardHeaders
	) {
		Assert.notNull(server, "'server' must not be null");
		Assert.notNull(host, "'host' must not be null");

		// spark.embeddedserver.jetty.SocketConnectorFactory.createHttpConnectionFactory
		HttpConfiguration httpConfig = new HttpConfiguration();
		httpConfig.setSecureScheme("https");
		if (trustForwardHeaders) {
			httpConfig.addCustomizer(new ForwardedRequestCustomizer());
		}
		HttpConnectionFactory httpConnectionFactory =
			new HttpConnectionFactory(httpConfig);

		ServerConnector connector = new ServerConnector(
			server,
			0, // acceptors, don't allocate separate threads for acceptor
			0, // selectors, use default number
			httpConnectionFactory
		);
		// spark.embeddedserver.jetty.SocketConnectorFactory.initializeConnector
		connector.setIdleTimeout(TimeUnit.HOURS.toMillis(1));
		connector.setHost(host);
		connector.setPort(port);

		return connector;
	}

	/**
	 * Creates a Jetty server.
	 *
	 * @param maxThreads          maxThreads
	 * @param minThreads          minThreads
	 * @param threadTimeoutMillis threadTimeoutMillis
	 * @return a new jetty server instance
	 */
	public Server create(
		int maxThreads,
		int minThreads,
		int threadTimeoutMillis
	) {
		Server server;

		if (maxThreads > 0) {
			int max = maxThreads;
			int min = (minThreads > 0) ? minThreads : 8;
			int idleTimeout =
				(threadTimeoutMillis > 0) ? threadTimeoutMillis : 60000;

			server = new Server(new QueuedThreadPool(max, min, idleTimeout));
		} else {
			server = new Server();
		}

		Connector internalConnector = null;
		if (internalPort != -1) {
			internalConnector = makeConnector(
				server,
				"localhost",
				internalPort,
				trustForwardHeaders
			);
		}

		Connector externalConnector = null;
		if (externalPort != -1) {
			externalConnector = makeConnector(
				server,
				"localhost",
				externalPort,
				trustForwardHeaders
			);
		}

		if (internalConnector == null) {
			server.setConnectors(new Connector[] { externalConnector });
		} else if (externalConnector == null) {
			server.setConnectors(new Connector[] { internalConnector });
		} else {
			server.setConnectors(
				new Connector[] { internalConnector, externalConnector }
			);
		}

		return server;
	}

	/**
	 * Creates a Jetty server with supplied thread pool
	 * @param threadPool thread pool
	 * @return a new jetty server instance
	 */
	@Override
	public Server create(ThreadPool threadPool) {
		return threadPool != null ? new Server(threadPool) : new Server();
	}
}
