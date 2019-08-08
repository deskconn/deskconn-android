package org.deskconn.deskconn.network;

public class DiscoveredService {
    private String mHost;
    private int mPort;
    private String mName;
    private String mRealm;

    DiscoveredService(String host, int port, String name, String realm) {
        mHost = host;
        mPort = port;
        mName = name;
        mRealm = realm;
    }

    public String getHost() {
        return mHost;
    }

    public int getPort() {
        return mPort;
    }

    public String getName() {
        return mName;
    }

    public String getRealm() {
        return mRealm;
    }
}
