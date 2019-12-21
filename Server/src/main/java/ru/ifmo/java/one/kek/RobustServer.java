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

public class RobustServer implements Server {
    private final ExecutorService pool = Executors.newCachedThreadPool();
    private final ServerSocket serverSocket;
    private final List<Socket> clients = new CopyOnWriteArrayList<>();

    public RobustServer(int port) throws IOException {
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

    private static class RobustWorker implements Runnable {
        private final InputStream input;
        private final OutputStream output;

        public RobustWorker(Socket socket) throws IOException {
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
                    List<Integer> values = new ArrayList<>(request.getValuesList());
                    List<Integer> result = Algorithms.sort(values);
                    System.out.println("Algorithmically processed client!");
                    ServerProtocol.SortResponse.newBuilder()
                            .setN(result.size())
                            .addAllValues(result).
                            build().writeDelimitedTo(output);
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
