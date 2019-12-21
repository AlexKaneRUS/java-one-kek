package ru.ifmo.java.one.kek;

import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

public class Client implements Runnable {
    private final int numberOfRequests;
    private final long delta;
    private final ServerProtocol.SortRequest request;

    private final Socket socket;

    public Client(int numberOfRequests, long delta, int numberOfElements, Socket socket) throws IOException {
        this.numberOfRequests = numberOfRequests;
        this.delta = delta;

        request = ServerProtocol.SortRequest.newBuilder()
                .setN(numberOfElements)
                .addAllValues(new Random().ints(numberOfElements).boxed().collect(Collectors.toList())).build();

        this.socket = socket;
    }


    @Override
    public void run() {
        try {
            for (int i = 0; i < numberOfRequests; i++) {
                System.out.println("Ready to send!");
                request.writeDelimitedTo(socket.getOutputStream());
                ServerProtocol.SortResponse.parseDelimitedFrom(socket.getInputStream());
                System.out.println("Sent: " + i);
                Thread.sleep(delta);
            }

            socket.close();
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }
}
