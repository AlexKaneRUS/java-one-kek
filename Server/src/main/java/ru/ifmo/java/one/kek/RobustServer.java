package ru.ifmo.java.one.kek;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RobustServer extends Server {
    private final ExecutorService pool = Executors.newCachedThreadPool();
    private final ServerSocket serverSocket;
    private final List<Socket> clients = new CopyOnWriteArrayList<>();

    public RobustServer(MetricsGatherer gatherer, int port) throws IOException {
        super(gatherer);
        serverSocket = new ServerSocket(port);
    }

    public void run() {
        while (true) {
            try {
                Socket socket = serverSocket.accept();
                clients.add(socket);
                pool.submit(new RobustWorker(socket));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private class RobustWorker implements Runnable {
        private final InputStream input;
        private final OutputStream output;
        private final long clientStart;

        public RobustWorker(Socket socket) throws IOException {
            clientStart = gatherer.start();
            input = socket.getInputStream();
            output = socket.getOutputStream();
        }

        @Override
        public void run() {
            System.out.println("Robust server is running");
            while (true) {
                try {
                    System.out.println("Waiting for client!");
                    ServerProtocol.SortRequest request = ServerProtocol.SortRequest.parseDelimitedFrom(input);
                    System.out.println("Got client!");

                    long requestStart = gatherer.start();
                    List<Integer> values = new ArrayList<>(request.getValuesList());
                    List<Integer> result = Algorithms.sort(values);
                    gatherer.measureRequest(requestStart);
                    System.out.println("Algorithmically processed client!");

                    ServerProtocol.SortResponse.newBuilder()
                            .setN(result.size())
                            .addAllValues(result).
                            build().writeDelimitedTo(output);
                    gatherer.measureClient(clientStart);
                    System.out.println("Processed client!");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    public void close() {
        clients.forEach(x -> {
            try {
                x.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        pool.shutdown();
    }
}
