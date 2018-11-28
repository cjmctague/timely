package timely.balancer.connection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import timely.balancer.ArrivalRate;
import timely.balancer.BalancerConfiguration;

public class TimelyBalancedHost {

    private static final Logger LOG = LoggerFactory.getLogger(TimelyBalancedHost.class);

    private String host;
    private int tcpPort;
    private int httpPort;
    private int wsPort;
    private int udpPort;
    private boolean isUp = true;
    private BalancerConfiguration config;
    private int failures = 0;
    private int successes = 0;
    private ArrivalRate arrivalRate = new ArrivalRate();

    public TimelyBalancedHost() {

    }

    public TimelyBalancedHost(String host, int tcpPort, int httpPort, int wsPort, int udpPort) {
        this.host = host;
        this.tcpPort = tcpPort;
        this.httpPort = httpPort;
        this.wsPort = wsPort;
        this.udpPort = udpPort;
    }

    public TimelyBalancedHost(String host, int basePort) {
        this.host = host;
        this.tcpPort = basePort;
        this.httpPort = basePort + 1;
        this.wsPort = basePort + 2;
        this.udpPort = basePort + 3;
    }

    public static TimelyBalancedHost of(String host, int tcpPort, int httpPort, int wsPort, int udpPort) {
        return new TimelyBalancedHost(host, tcpPort, httpPort, wsPort, udpPort);
    }

    public static TimelyBalancedHost of(String host, int basePort) {
        return new TimelyBalancedHost(host, basePort);
    }

    public void setConfig(BalancerConfiguration config) {
        this.config = config;
    }

    public synchronized boolean isUp() {
        return isUp;
    }

    public void reportSuccess() {
        int serverSuccessesBeforeUp = config.getServerSuccessesBeforeUp();
        String h = host;
        int p = tcpPort;
        synchronized (this) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("success reported host:{} port:{} isUp:{} successes:{} serverSuccessesBeforeUp:{}", h, p,
                        isUp, (successes + 1), serverSuccessesBeforeUp);
            }
            if (!isUp) {
                if (++successes >= serverSuccessesBeforeUp) {
                    isUp = true;
                    successes = 0;
                    LOG.info("host up host:{} port:{}", h, p);
                }
            }
        }
    }

    public void reportFailure() {
        int serverFailuresBeforeDown = config.getServerFailuresBeforeDown();
        String h = host;
        int p = tcpPort;
        synchronized (this) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("failure reported host:{} port:{} isUp:{} successes:{} serverSuccessesBeforeUp:{}", h, p,
                        isUp, (failures + 1), serverFailuresBeforeDown);
            }
            if (isUp) {
                if (++failures >= serverFailuresBeforeDown) {
                    isUp = false;
                    failures = 0;
                    LOG.info("host down host:{} port:{}", h, p);
                }
            }
        }
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getUdpPort() {
        return udpPort;
    }

    public void setUdpPort(int udpPort) {
        this.udpPort = udpPort;
    }

    public int getTcpPort() {
        return tcpPort;
    }

    public void setTcpPort(int tcpPort) {
        this.tcpPort = tcpPort;
    }

    public int getHttpPort() {
        return httpPort;
    }

    public void setHttpPort(int httpPort) {
        this.httpPort = httpPort;
    }

    public int getWsPort() {
        return wsPort;
    }

    public void setWsPort(int wsPort) {
        this.wsPort = wsPort;
    }

    public void arrived() {
        arrivalRate.arrived();
    }

    public double getArrivalRate() {
        return arrivalRate.getRate();
    }

    public void calculateRate() {
        arrivalRate.calculateRate();
    }
}
