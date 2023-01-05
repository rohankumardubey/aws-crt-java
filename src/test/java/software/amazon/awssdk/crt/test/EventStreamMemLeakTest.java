package software.amazon.awssdk.crt.test;

import org.junit.Test;
import software.amazon.awssdk.crt.eventstream.*;
import software.amazon.awssdk.crt.io.ClientBootstrap;
import software.amazon.awssdk.crt.io.EventLoopGroup;
import software.amazon.awssdk.crt.io.ServerBootstrap;
import software.amazon.awssdk.crt.io.SocketOptions;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.*;

public class EventStreamMemLeakTest extends CrtTestFixture {
    public EventStreamMemLeakTest() {
    }

    static int connectionIndex = 0;
    static int connectionCount = 0;

    public void CreateConnections(ClientConnection[] clientConnectionArray, SocketOptions socketOptions,
            ClientBootstrap clientBootstrap) {
        connectionIndex = 0;
        System.out.print("\nStarting 100 connections");

        for (int i = 0; i < 100; ++i) {
            final Integer innerI = new Integer(i);
            ClientConnection.connect("127.0.0.1", (short) 8033, socketOptions, null, clientBootstrap,
                    new ClientConnectionHandler() {
                        @Override
                        protected void onConnectionSetup(ClientConnection connection, int errorCode) {
                            clientConnectionArray[innerI] = connection;
                        }

                        @Override
                        protected void onProtocolMessage(List<Header> headers, byte[] payload, MessageType messageType,
                                int messageFlags) {

                        }
                    });
        }
        System.out.print("\n100 connections started");
    }

    public void CloseConnections(ClientConnection[] clientConnectionArray) {
        System.out.print("\nClosing 100 connections");
        for (int i = 0; i < 100; ++i) {
            final Integer innerI = new Integer(i);
            clientConnectionArray[innerI].closeConnection(0);
        }
        System.out.print("\n100 connections closed");
    }

    public void SleepFor(int timeToSleep) throws InterruptedException {
        System.out.print("\nSleeping for " + timeToSleep + " seconds");
        for (int i = timeToSleep; i > 0; --i) {
            Thread.sleep(1000);
        }
    }

    public void ConnectionCycle(ClientConnection[] clientConnectionArray, SocketOptions socketOptions,
            ClientBootstrap clientBootstrap) {
        CreateConnections(clientConnectionArray, socketOptions, clientBootstrap);

        boolean allConnected = false;
        while (!allConnected) {
            if (connectionCount == 100) {
                allConnected = true;
            } else {
                try {
                    Thread.sleep(1000);
                    System.out.println("current connections: " + connectionCount);
                } catch (Exception e) {
                    System.out.print("\nException in sleep");
                }
            }
        }

        System.out.print("\nConnections completed. Sleeping for 30 seconds.\n");

        // Sleep to get handle count after first connections
        try {
            Thread.sleep(30000);
            System.out.println("current connections: " + connectionCount);
        } catch (Exception e) {
            System.out.print("\nException in sleep");
        }

        CloseConnections(clientConnectionArray);

        boolean allDisconnected = false;
        while (!allDisconnected) {
            if (connectionCount > 0) {
                try {
                    Thread.sleep(1000);
                } catch (Exception e) {
                    System.out.print("Exception in sleep");
                }
            } else {
                allDisconnected = true;
            }
        }
        System.out.print("\nAll Connections Closed. Sleeping for 30 seconds.\n");

        // Sleep to get handle count after first disconnections
        try {
            Thread.sleep(30000);
            System.out.println("current connections: " + connectionCount);
        } catch (Exception e) {
            System.out.print("\nException in sleep");
        }
    }

    @Test
    public void testConnectionHandling()
            throws ExecutionException, InterruptedException, IOException, TimeoutException {
        SocketOptions socketOptions = new SocketOptions();
        socketOptions.connectTimeoutMs = 3000;
        socketOptions.domain = SocketOptions.SocketDomain.IPv4;
        socketOptions.type = SocketOptions.SocketType.STREAM;

        EventLoopGroup elGroup = new EventLoopGroup(1);
        ServerBootstrap bootstrap = new ServerBootstrap(elGroup);
        ClientBootstrap clientBootstrap = new ClientBootstrap(elGroup, null);
        final ServerConnection[] serverConnections = new ServerConnection[100];

        final CompletableFuture<ServerConnection> serverConnectionAccepted = new CompletableFuture<>();

        ServerListener listener = new ServerListener("127.0.0.1", (short) 8033, socketOptions, null, bootstrap,
                new ServerListenerHandler() {
                    private ServerConnectionHandler connectionHandler = null;

                    public ServerConnectionHandler onNewConnection(ServerConnection serverConnection, int errorCode) {
                        // System.out.print("\nNew Connection Started with index:" + connectionIndex + " \n");
                        serverConnections[connectionIndex] = serverConnection;
                        connectionIndex++;
                        connectionCount++;

                        connectionHandler = new ServerConnectionHandler(serverConnection) {

                            @Override
                            protected void onProtocolMessage(List<Header> headers, byte[] payload,
                                    MessageType messageType, int messageFlags) {
                            }

                            @Override
                            protected ServerConnectionContinuationHandler onIncomingStream(
                                    ServerConnectionContinuation continuation, String operationName) {
                                return null;
                            }
                        };

                        serverConnectionAccepted.complete(serverConnection);
                        return connectionHandler;
                    }

                    public void onConnectionShutdown(ServerConnection serverConnection, int errorCode) {
                        // System.out.print("\nConnection " + connectionCount + " Shutdown\n");
                        connectionCount--;
                    }

                });

        System.out.print("\n\nServer Started\n\n");

        final ClientConnection[] clientConnectionArray = new ClientConnection[100];

        // Sleep to get baseline handle count
        System.out.print("\nSleeping for 30 seconds to get baseline reading");
        SleepFor(30);

        for (int i = 0; i < 50; ++i) {
            System.out.print("\nConnectioncycle() " + i + "Starting.");
            ConnectionCycle(clientConnectionArray, socketOptions, clientBootstrap);
            System.out.print("\nConnectioncycle() " + i + "Completed.");
        }

        System.out.print("\nConnection operations finished. Sleeping for 10 min");
        //Sleep for 10 min after last set of connections
        SleepFor(600);


        System.out.print("\nClosing Server and waiting for 10 min");
        // Close the server
        listener.close();
        listener.getShutdownCompleteFuture().get(1, TimeUnit.SECONDS);
        SleepFor(600);

        System.out.print("\nClosing bootstrap and and event loop then waiting for 10 min");
        // Close the bootstrap
        bootstrap.close();
        clientBootstrap.close();
        clientBootstrap.getShutdownCompleteFuture().get(1, TimeUnit.SECONDS);

        // Close the event loop group
        elGroup.close();
        elGroup.getShutdownCompleteFuture().get(1, TimeUnit.SECONDS);

        SleepFor(600);

        System.out.print("Closing the socket then waiting for 10 min");
        // Close the socket
        socketOptions.close();
        SleepFor(600);
    }
}
