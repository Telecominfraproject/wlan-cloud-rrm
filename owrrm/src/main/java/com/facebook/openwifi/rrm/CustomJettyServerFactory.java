/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.openwifi.rrm;

import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.util.thread.ThreadPool;
import spark.embeddedserver.jetty.JettyServerFactory;
import spark.embeddedserver.jetty.SocketConnectorFactory;

/**
 * Creates Jetty Server instances. Majority of the logic is taken
 * from JettyServerFactory with port logic added.
 */
public class CustomJettyServerFactory implements JettyServerFactory {
	private final int internalPort;
	private final int externalPort;

	public CustomJettyServerFactory(int externalPort, int internalPort) {
		this.externalPort = externalPort;
		this.internalPort = internalPort;
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

		Connector externalConnector = SocketConnectorFactory
			.createSocketConnector(server, "localhost", externalPort);
		Connector internalConnector = SocketConnectorFactory
			.createSocketConnector(server, "localhost", internalPort);

		server.setConnectors(
			new Connector[] { externalConnector, internalConnector }
		);

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
