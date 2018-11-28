package timely.balancer.netty.ws;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import timely.balancer.connection.TimelyBalancedHost;
import timely.balancer.connection.ws.WsClientPool;
import timely.client.websocket.subscription.WebSocketSubscriptionClient;

public class WsClientHolder {

    private static final Logger LOG = LoggerFactory.getLogger(WsClientHolder.class);
    private WebSocketSubscriptionClient client = null;
    private TimelyBalancedHost host = null;

    public WsClientHolder(TimelyBalancedHost host, WebSocketSubscriptionClient client) {
        this.host = host;
        this.client = client;
    }

    public synchronized WebSocketSubscriptionClient getClient() {
        return client;
    }

    public synchronized TimelyBalancedHost getHost() {
        return host;
    }

    public synchronized void close(WsClientPool wsClientPool) {
        if (client != null && host != null) {
            try {
                client.close();
            } catch (IOException e) {
                LOG.error("Error closing web socket client", e);
            }
            wsClientPool.returnObject(host, client);
            client = null;
            host = null;
        }
    }
}
