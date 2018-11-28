package timely.balancer.resolver;

import timely.balancer.connection.TimelyBalancedHost;

public interface MetricResolver {

    TimelyBalancedHost getHostPortKeyIngest(String metric);

    TimelyBalancedHost getHostPortKey(String metric);
}
