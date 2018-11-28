package timely.balancer.healthcheck;

import timely.balancer.connection.TimelyBalancedHost;

public interface ServerHealthCheck {

    boolean isServerHealthy(TimelyBalancedHost timelyBalancedHost);
}
