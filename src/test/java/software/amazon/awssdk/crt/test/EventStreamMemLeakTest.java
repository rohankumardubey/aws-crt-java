package software.amazon.awssdk.crt.test;

import org.junit.Test;
import software.amazon.awssdk.crt.eventstream.*;
import software.amazon.awssdk.crt.io.ClientBootstrap;
import software.amazon.awssdk.crt.io.EventLoopGroup;
import software.amazon.awssdk.crt.io.ServerBootstrap;
import software.amazon.awssdk.crt.io.SocketOptions;

import java.io.IOException;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.Assert.*;

public class EventStreamMemLeakTest extends CrtTestFixture {
    public EventStreamMemLeakTest() {}
    static int connectionIndex = 0;
    static int connectionCount = 0;

    public void CreateConnections(ClientConnection[] clientConnectionArray, SocketOptions socketOptions, ClientBootstrap clientBootstrap){
        connectionIndex = 0;
        for(int i = 0; i < 100; ++i){
            System.out.print("\nStarting connection i:" + i + "\n");
            final Integer innerI = new Integer(i);
            // connectFuture =
            ClientConnection.connect("127.0.0.1", (short)8033, socketOptions, null, clientBootstrap, new ClientConnectionHandler() {
                @Override
                protected void onConnectionSetup(ClientConnection connection, int errorCode) {
                    System.out.print("\nclient connected and set to: "+ innerI + "\n");
                    clientConnectionArray[innerI] = connection;
                }

                @Override
                protected void onProtocolMessage(List<Header> headers, byte[] payload, MessageType messageType, int messageFlags) {

                }
            });
        }
    }

    public void CloseConnections(ClientConnection[] clientConnectionArray){
        for(int i = 0; i < 100; ++i){
            System.out.print("\nClosing connection i:" + i + "\n");
            final Integer innerI = new Integer(i);
            clientConnectionArray[innerI].closeConnection(0);
        }
    }

    @Test
    public void testConnectionHandling() throws ExecutionException, InterruptedException, IOException, TimeoutException {
        SocketOptions socketOptions = new SocketOptions();
        socketOptions.connectTimeoutMs = 3000;
        socketOptions.domain = SocketOptions.SocketDomain.IPv4;
        socketOptions.type = SocketOptions.SocketType.STREAM;

        EventLoopGroup elGroup = new EventLoopGroup(1);
        ServerBootstrap bootstrap = new ServerBootstrap(elGroup);
        ClientBootstrap clientBootstrap = new ClientBootstrap(elGroup, null);
        final ServerConnection[] serverConnections = new ServerConnection[100];

        final CompletableFuture<ServerConnection> serverConnectionAccepted = new CompletableFuture<>();

        ServerListener listener = new ServerListener("127.0.0.1", (short)8033, socketOptions, null, bootstrap, new ServerListenerHandler() {
            private ServerConnectionHandler connectionHandler = null;

            public ServerConnectionHandler onNewConnection(ServerConnection serverConnection, int errorCode) {
                System.out.print("\nNew Connection Started with index:"+ connectionIndex + " \n");
                serverConnections[connectionIndex] = serverConnection;
                connectionIndex++;
                connectionCount++;

                connectionHandler = new ServerConnectionHandler(serverConnection) {

                    @Override
                    protected void onProtocolMessage(List<Header> headers, byte[] payload, MessageType messageType, int messageFlags) {
                    }

                    @Override
                    protected ServerConnectionContinuationHandler onIncomingStream(ServerConnectionContinuation continuation, String operationName) {
                        return null;
                    }
                };

                serverConnectionAccepted.complete(serverConnection);
                return connectionHandler;
            }

            public void onConnectionShutdown(ServerConnection serverConnection, int errorCode) {
                System.out.print("\nConnection " + connectionCount + " Shutdown\n");
                connectionCount--;
            }

        });

        System.out.print("\n\nServer Started\n\n");

        final ClientConnection[] clientConnectionArray = new ClientConnection[100];
        // CompletableFuture<Void>[] connectFutures = new CompletableFuture<Void>[100];

        CreateConnections(clientConnectionArray, socketOptions, clientBootstrap);

        boolean allConnected = false;
        while(!allConnected){
            if (connectionCount == 100){
                allConnected = true;
            } else {
                Thread.sleep(1000);
                System.out.println("current connections: " + connectionCount);
            }
        }

        System.out.print("\nConnections completed\n");

        for(int i = 10; i > 0; --i){
            System.out.print("\nSleeping for " + i + " seconds");
            Thread.sleep(1000);
        }

        CloseConnections(clientConnectionArray);

        boolean allDisconnected = false;
        while(!allDisconnected){
            if(connectionCount > 0){
                Thread.sleep(1000);
            } else{
                allDisconnected = true;
            }
        }
        System.out.print("\nAll Connections Closed\n");

        for(int i = 30; i > 0; --i){
            System.out.print("\nSleeping for " + i + " seconds");
            Thread.sleep(1000);
        }

        CreateConnections(clientConnectionArray, socketOptions, clientBootstrap);

        allConnected = false;
        while(!allConnected){
            if (connectionCount == 100){
                allConnected = true;
            } else {
                Thread.sleep(1000);
                System.out.println("current connections: " + connectionCount);
            }
        }

        for(int i = 30; i > 0; --i){
            System.out.print("\nSleeping for " + i + " seconds");
            Thread.sleep(1000);
        }

        CloseConnections(clientConnectionArray);

        // CLEAN UP TEST //

        // Close the server
        listener.close();
        listener.getShutdownCompleteFuture().get(1, TimeUnit.SECONDS);

        // Close the bootstrap
        bootstrap.close();
        clientBootstrap.close();
        clientBootstrap.getShutdownCompleteFuture().get(1, TimeUnit.SECONDS);

        // Close the event loop group
        elGroup.close();
        elGroup.getShutdownCompleteFuture().get(1, TimeUnit.SECONDS);

        // Close the socket
        socketOptions.close();
    }
}
