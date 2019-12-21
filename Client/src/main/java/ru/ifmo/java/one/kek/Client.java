package ru.ifmo.java.one.kek;

import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

public class Client implements Runnable {
    private final MeasurementsGatherer gatherer;

    private final int clientId;
    private final int numberOfRequests;
    private final long delta;
    private final ServerProtocol.SortRequest.Builder requestBuilder;

    private final Socket socket;

    public Client(int clientId, int numberOfRequests, long delta, int numberOfElements, Socket socket,
                  MeasurementsGatherer gatherer) {
        this.clientId = clientId;
        this.numberOfRequests = numberOfRequests;
        this.delta = delta;

        requestBuilder = ServerProtocol.SortRequest.newBuilder()
                .setClientId(clientId)
                .setN(numberOfElements)
                .addAllValues(new Random().ints(numberOfElements).boxed().collect(Collectors.toList()));

        this.socket = socket;
        this.gatherer = gatherer;
    }


    @Override
    public void run() {
        try {
            List<Long> starts = new ArrayList<>();

            for (int i = 0; i < numberOfRequests; i++) {
                System.out.println("Ready to send!");
                requestBuilder.setTaskId(i).build().writeDelimitedTo(socket.getOutputStream());
                starts.add(gatherer.time());
                System.out.println("Sent: " + i);
                Thread.sleep(delta);
            }

            int counter = numberOfRequests;

            while (counter > 0) {
                ServerProtocol.SortResponse response = ServerProtocol.SortResponse.parseDelimitedFrom(socket.getInputStream());
                gatherer.measureServerResponse(starts.get(response.getTaskId()), clientId);
                counter--;
            }

            socket.close();
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }
}
