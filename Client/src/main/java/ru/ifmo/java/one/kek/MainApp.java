package ru.ifmo.java.one.kek;

import java.io.IOException;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

public class MainApp {
    public static void main(String[] args) throws IOException, ExecutionException, InterruptedException {
        Socket mainAppServer = new Socket("localhost", Constants.MAIN_APP_SERVER_PORT);
        MainAppServerProtocol.RunServerRequest.newBuilder()
                .setTypeOfServer(MainAppServerProtocol.TypeOfServer.ROBUST)
                .build().writeDelimitedTo(mainAppServer.getOutputStream());
        MainAppServerProtocol.RunServerResponse.parseDelimitedFrom(mainAppServer.getInputStream());


        int numberOfClients = 4;
        int numberOfElements = 10;
        long delta = 100;
        int numberOfRequests = 50;

        ExecutorService pool = Executors.newCachedThreadPool();
        List<Client> clients = ClientFactory.getListOfClients(numberOfClients, numberOfRequests, delta, numberOfElements, "localhost");

        List<Future<?>> futures = clients.stream().map(pool::submit).collect(Collectors.toList());

        for (Future<?> future : futures) {
            future.get();
        }

        MainAppServerProtocol.EndOfTestingRequest.newBuilder().build().writeDelimitedTo(mainAppServer.getOutputStream());
        MainAppServerProtocol.EndOfTestingResponse.parseDelimitedFrom(mainAppServer.getInputStream());

        mainAppServer.close();
    }
}
