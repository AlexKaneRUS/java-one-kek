package ru.ifmo.java.one.kek;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class MainAppServer {
    public static void main(String[] args) throws IOException {
        ServerSocket serverSocket = new ServerSocket(Constants.MAIN_APP_SERVER_PORT);

        int counter = 0;
        while (true) {
            Socket mainAppClient = serverSocket.accept();

            InputStream mainAppClientInput = mainAppClient.getInputStream();
            OutputStream mainAppClientOutput = mainAppClient.getOutputStream();

            MainAppServerProtocol.RunServerRequest runServerRequest =
                    MainAppServerProtocol.RunServerRequest.parseDelimitedFrom(mainAppClientInput);

            Server server;

            switch (runServerRequest.getTypeOfServer()) {
                case ROBUST:
                    System.out.println("Setting up Robust server.");
                    server = new RobustServer(Constants.SERVER_PORT + counter);
                    break;
                default:
                    continue;
            }

            MainAppServerProtocol.RunServerResponse.newBuilder()
                    .setStatus(MainAppServerProtocol.Status.OK)
                    .build().writeDelimitedTo(mainAppClientOutput);

            Thread serverThread = new Thread(server);
            serverThread.start();

            MainAppServerProtocol.EndOfTestingRequest.parseDelimitedFrom(mainAppClientInput);

            server.close();
            serverThread.interrupt();

            MainAppServerProtocol.EndOfTestingRequest.newBuilder()
                    .build().writeDelimitedTo(mainAppClientOutput);

            mainAppClient.close();
        }
    }
}
