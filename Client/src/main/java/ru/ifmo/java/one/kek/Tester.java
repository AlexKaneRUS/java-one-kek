package ru.ifmo.java.one.kek;

import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;

public class Tester {
    private final MetricsGatherer gatherer = new MetricsGatherer();

    private final InclusiveRange<Integer> numberOfClientsRange;
    private final InclusiveRange<Integer> numberOfElementsRange;
    private final InclusiveRange<Long> deltaRange;
    private final int numberOfRequests;
    private final MainAppServerProtocol.TypeOfServer typeOfServer;

    public Tester(InclusiveRange<Integer> numberOfClientsRange,
                  InclusiveRange<Integer> numberOfElementsRange,
                  InclusiveRange<Long> deltaRange,
                  int numberOfRequests,
                  MainAppServerProtocol.TypeOfServer typeOfServer) {
        this.numberOfClientsRange = numberOfClientsRange;
        this.numberOfElementsRange = numberOfElementsRange;
        this.deltaRange = deltaRange;
        this.numberOfRequests = numberOfRequests;
        this.typeOfServer = typeOfServer;
    }

    public void conductTesting(String host) {
        try (Socket socket = new Socket(host, Constants.MAIN_APP_SERVER_PORT)) {
            startTesting(socket);

            for (int numberOfClients = numberOfClientsRange.left; numberOfClients < numberOfClientsRange.right; numberOfClients += numberOfClientsRange.step) {
                for (int numberOfElements = numberOfElementsRange.left; numberOfElements < numberOfElementsRange.right; numberOfElements += numberOfElementsRange.step) {
                    for (long delta = deltaRange.left; delta < deltaRange.right; delta += deltaRange.step) {
                        conductStep(numberOfElements, numberOfClients, delta);
                    }
                }
            }

            endTesting(socket);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void conductStep(int numberOfElements, int numberOfClients, long delta) {
        // TODO: пишем тут логику сбора одного набора измерений, не забываем,
        //  что нужно измерять время, которое клиент прождал ответа сервера
    }

    private void startTesting(Socket socket) throws IOException {
        MainAppServerProtocol.RunServerRequest.newBuilder()
                .setTypeOfServer(typeOfServer)
                .build().writeDelimitedTo(socket.getOutputStream());

        MainAppServerProtocol.RunServerResponse.parseDelimitedFrom(socket.getInputStream());
    }

    private void endTesting(Socket socket) throws IOException {
        MainAppServerProtocol.EndOfTestingRequest
                .newBuilder()
                .build().writeDelimitedTo(socket.getOutputStream());
        MainAppServerProtocol.EndOfTestingResponse.parseDelimitedFrom(socket.getInputStream());
    }
}
