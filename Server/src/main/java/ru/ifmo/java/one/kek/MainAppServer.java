package ru.ifmo.java.one.kek;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

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
                    server = new RobustServer(new MetricsGatherer(), Constants.SERVER_PORT + counter);
                    break;
                default:
                    continue;
            }

            MainAppServerProtocol.RunServerResponse.newBuilder()
                    .setStatus(MainAppServerProtocol.Status.OK)
                    .build().writeDelimitedTo(mainAppClientOutput);

            Thread serverThread = new Thread(server);
            serverThread.start();

            try {
                run(mainAppClientInput, mainAppClientOutput, server);
            } finally {
                serverThread.interrupt();
                mainAppClient.close();
            }
        }
    }

    private static void run(InputStream in, OutputStream out, Server server) throws IOException {
        while (true) {
            MainAppServerProtocol.BaseRequest request = MainAppServerProtocol.BaseRequest.parseDelimitedFrom(in);

            if (request.hasEndOfRoundRequest()) {
                processEndOfRoundRequest(out, server.getGatherer());
            } else if (request.hasEndOfTestingRequest()) {
                processEndOfTestingRequest(out, server);
            } else {
                System.out.println("Unknown request: " + request);
            }
        }
    }

    private static void processEndOfRoundRequest(OutputStream out, MetricsGatherer gatherer) throws IOException {
        List<Long> requestMeasurements = new ArrayList<>(gatherer.getRequestMeasurements());
        List<Long> clientMeasurements = new ArrayList<>(gatherer.getClientMeasurements());
        gatherer.clean();
        MainAppServerProtocol.EndOfRoundResponse.newBuilder()
                .addAllRequestMeasurements(requestMeasurements)
                .addAllClientMeasurements(clientMeasurements)
                .build().writeDelimitedTo(out);
    }

    private static void processEndOfTestingRequest(OutputStream out, Server server) throws IOException {
        server.close();

        MainAppServerProtocol.EndOfTestingRequest.newBuilder()
                .build().writeDelimitedTo(out);
    }
}
