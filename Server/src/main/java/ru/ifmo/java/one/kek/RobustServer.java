package ru.ifmo.java.one.kek;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class RobustServer extends Server {
    private final ExecutorService pool = Executors.newCachedThreadPool();
    private final ServerSocket serverSocket;
    private final List<Socket> clients = new ArrayList<>();

    private final AtomicInteger i = new AtomicInteger(0);

    public RobustServer(MeasurementsGatherer gatherer, int port) throws IOException {
        super(gatherer);
        serverSocket = new ServerSocket(port);
    }

    public void run() {
        while (!Thread.interrupted()) {
            try {
                Socket socket = serverSocket.accept();
                clients.add(socket);
                pool.submit(new RobustWorker(socket));
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
        }
    }

    private class RobustWorker implements Runnable {
        private final InputStream input;
        private final OutputStream output;

        public RobustWorker(Socket socket) throws IOException {
            input = socket.getInputStream();
            output = socket.getOutputStream();
        }

        @Override
        public void run() {
            while (!Thread.interrupted()) {
                ServerProtocol.SortRequest request;
                long clientStart;
                try {
                    request = ServerProtocol.SortRequest.parseDelimitedFrom(input);
                    clientStart = gatherer.time();

                    long requestStart = gatherer.time();
                    List<Integer> values = new ArrayList<>(request.getValuesList());
                    List<Integer> result = Algorithms.sort(values);
                    gatherer.measureRequest(requestStart, request.getClientId());

                    gatherer.measureClient(clientStart, request.getClientId());
                    ServerProtocol.SortResponse.newBuilder()
                            .setN(result.size())
                            .addAllValues(result)
                            .setTaskId(request.getTaskId())
                            .build().writeDelimitedTo(output);
                    System.out.println(i.incrementAndGet());
                } catch (IOException e) {
                    e.printStackTrace();
                    return;
                }
            }
        }
    }

    @Override
    public void close() {
        pool.shutdown();
        clients.forEach(x -> {
            try {
                x.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        try {
            serverSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
