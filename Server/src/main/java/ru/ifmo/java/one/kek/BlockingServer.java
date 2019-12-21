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

public class BlockingServer extends Server {
    private final ExecutorService pool = Executors.newCachedThreadPool();
    private final ExecutorService taskPool = Executors.newFixedThreadPool(4);

    private final ServerSocket serverSocket;
    private final List<Socket> clients = new ArrayList<>();

    public BlockingServer(MeasurementsGatherer gatherer, int port) throws IOException {
        super(gatherer);
        serverSocket = new ServerSocket(port);
    }

    public void run() {
        while (true) {
            try {
                Socket socket = serverSocket.accept();
                clients.add(socket);
                pool.submit(new BlockingWorker(socket, taskPool));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private class BlockingWorker implements Runnable {
        private final InputStream input;
        private final OutputStream output;
        private final ExecutorService sender = Executors.newSingleThreadExecutor();
        private final ExecutorService pool;

        public BlockingWorker(Socket socket, ExecutorService pool) throws IOException {
            input = socket.getInputStream();
            output = socket.getOutputStream();
            this.pool = pool;
        }

        @Override
        public void run() {
            while (true) {
                ServerProtocol.SortRequest request;
                long clientStart;
                try {
                    request = ServerProtocol.SortRequest.parseDelimitedFrom(input);
                    clientStart = gatherer.time();
                } catch (IOException e) {
                    break;
                }

                pool.submit(new Task(request.getValuesList(), request.getClientId(), request.getTaskId(), clientStart));
            }
        }

        private class Task implements Runnable {
            private final List<Integer> values;
            private final int clientId;
            private final int taskId;
            private final long clientStart;

            public Task(List<Integer> values, int clientId, int taskId, long clientStart) {
                this.values = new ArrayList<>(values);
                this.clientId = clientId;
                this.taskId = taskId;
                this.clientStart = clientStart;
            }

            @Override
            public void run() {
                long requestStart = gatherer.time();
                List<Integer> result = Algorithms.sort(values);
                gatherer.measureRequest(requestStart, clientId);

                sender.submit(() -> {
                    gatherer.measureClient(clientStart, clientId);
                    try {
                        ServerProtocol.SortResponse.newBuilder()
                                .setN(result.size())
                                .addAllValues(result)
                                .setTaskId(taskId)
                                .build().writeDelimitedTo(output);
                        System.out.println("Processed client!");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
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
        taskPool.shutdown();
    }
}

