package ru.ifmo.java.one.kek;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.*;
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
//                System.out.println("Ready to send!");

//                ByteArrayOutputStream a = new ByteArrayOutputStream();
//                requestBuilder.setTaskId(i).build().writeDelimitedTo(a);
//
//                System.out.println(a.toByteArray().length);
//                for (byte b : a.toByteArray()) {
//                    String s1 = String.format("%8s", Integer.toBinaryString(b & 0xFF)).replace(' ', '0');
//                    System.out.println(s1);
//                }

                requestBuilder.setTaskId(i).build().writeDelimitedTo(socket.getOutputStream());
                starts.add(gatherer.time());
                Thread.sleep(delta);
            }

            int counter = numberOfRequests;

            while (counter > 0) {
                ServerProtocol.SortResponse response = ServerProtocol.SortResponse.parseDelimitedFrom(socket.getInputStream());
                gatherer.measureServerResponse(starts.get(response.getTaskId()), clientId);
//                List<Integer> res = new ArrayList<>(requestBuilder.setTaskId(0).build().getValuesList());
//                res.sort(Comparator.naturalOrder());
//                assert Arrays.equals(res.toArray(), response.getValuesList().toArray());
                counter--;
//                System.out.println("For " + clientId + " left " + counter);
            }

            socket.close();
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }
}
