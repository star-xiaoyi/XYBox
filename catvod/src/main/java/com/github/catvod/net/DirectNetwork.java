package com.github.catvod.net;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import com.github.catvod.Init;
import com.github.catvod.utils.Logger;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import javax.net.SocketFactory;
import okhttp3.Dns;

/** Per-socket direct routing; never binds the process or changes the updater's system route. */
public final class DirectNetwork {
    private static String lastRoute = "";
    private DirectNetwork() {}

    static final class Route {
        final Network network;
        final boolean vpn;
        Route(Network network, boolean vpn) { this.network = network; this.vpn = vpn; }
    }

    static int rank(NetworkCapabilities caps) {
        if (caps == null || caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
                || !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return -1;
        int score = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) ? 100 : 0;
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) return score + 30;
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return score + 20;
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) return score + 10;
        return -1;
    }

    static Route route() {
        Context context = Init.context();
        ConnectivityManager manager = context == null ? null : (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (manager == null) return new Route(null, false);
        NetworkCapabilities active = manager.getNetworkCapabilities(manager.getActiveNetwork());
        boolean vpn = active != null && active.hasTransport(NetworkCapabilities.TRANSPORT_VPN);
        Network best = null;
        int score = -1;
        for (Network network : manager.getAllNetworks()) {
            int candidate = rank(manager.getNetworkCapabilities(network));
            if (candidate > score) { best = network; score = candidate; }
        }
        String summary = "direct=" + (best == null ? "unavailable" : best) + " vpn=" + vpn;
        synchronized (DirectNetwork.class) {
            if (!summary.equals(lastRoute)) { lastRoute = summary; Logger.i("MediaNetwork: " + summary); }
        }
        return new Route(best, vpn);
    }

    public static Dns dns() {
        return host -> {
            if (loopback(host)) return Dns.SYSTEM.lookup(host);
            try {
                Route route = route();
                if (route.network != null) return Arrays.asList(route.network.getAllByName(host));
                if (!route.vpn) return Dns.SYSTEM.lookup(host);
                throw new UnknownHostException("没有可用于视频直连的 Wi-Fi 或移动网络");
            } catch (SecurityException error) {
                UnknownHostException failure = new UnknownHostException("系统未允许视频使用直连网络");
                failure.initCause(error); throw failure;
            }
        };
    }

    static boolean loopback(String host) {
        return host != null && (host.equalsIgnoreCase("localhost") || host.equals("::1") || host.equals("[::1]")
                || host.matches("127(?:\\.[0-9]{1,3}){3}"));
    }

    public static SocketFactory sockets() {
        return new SocketFactory() {
            @Override public Socket createSocket() { return new DirectSocket(); }
            @Override public Socket createSocket(String host, int port) throws IOException { return connect(host, port, null, 0); }
            @Override public Socket createSocket(String host, int port, InetAddress local, int localPort) throws IOException { return connect(host, port, local, localPort); }
            @Override public Socket createSocket(InetAddress host, int port) throws IOException { return connect(host, port, null, 0); }
            @Override public Socket createSocket(InetAddress host, int port, InetAddress local, int localPort) throws IOException {
                Socket socket = createSocket();
                try {
                    if (local != null) socket.bind(new InetSocketAddress(local, localPort));
                    socket.connect(new InetSocketAddress(host, port)); return socket;
                } catch (IOException error) { socket.close(); throw error; }
            }
            private Socket connect(InetAddress host, int port, InetAddress local, int localPort) throws IOException {
                return createSocket(host, port, local, localPort);
            }
            private Socket connect(String host, int port, InetAddress local, int localPort) throws IOException {
                IOException last = null;
                for (InetAddress address : dns().lookup(host)) {
                    try { return createSocket(address, port, local, localPort); }
                    catch (IOException error) { last = error; }
                }
                throw last == null ? new UnknownHostException(host) : last;
            }
        };
    }

    private static final class DirectSocket extends Socket {
        DirectSocket() { super(Proxy.NO_PROXY); }
        @Override public void connect(SocketAddress endpoint, int timeout) throws IOException {
            InetSocketAddress address = endpoint instanceof InetSocketAddress ? (InetSocketAddress) endpoint : null;
            boolean local = address != null && (loopback(address.getHostString())
                    || address.getAddress() != null && address.getAddress().isLoopbackAddress());
            if (!local) {
                try {
                    Route route = route();
                    if (route.network != null) route.network.bindSocket(this);
                    else if (route.vpn) throw new IOException("没有可用于视频直连的 Wi-Fi 或移动网络");
                } catch (SecurityException error) { throw new IOException("系统未允许视频使用直连网络", error); }
            }
            super.connect(endpoint, timeout);
        }
        @Override public void connect(SocketAddress endpoint) throws IOException { connect(endpoint, 0); }
    }
}
