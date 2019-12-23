package ru.ifmo.java.one.kek;

import org.w3c.dom.ls.LSOutput;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

public class NonBlockingServer extends Server {
    private final ExecutorService taskPool = Executors.newFixedThreadPool(13);

    private final ServerSocketChannel serverSocketChannel;
    private final Selector readSelector;
    private final Selector writeSelector;

    private final Thread readerThread;
    private final Lock readerLock = new ReentrantLock();

    private final Thread writerThread;
    private final Lock writerLock = new ReentrantLock();

    private AtomicInteger numberOfMessages = new AtomicInteger(0);
    private AtomicInteger numberOfMessages1 = new AtomicInteger(0);

    public NonBlockingServer(MeasurementsGatherer gatherer, int port) throws IOException {
        super(gatherer);
        serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.bind(new InetSocketAddress(port));
        serverSocketChannel.configureBlocking(false);

        readSelector = Selector.open();
        writeSelector = Selector.open();

        readerThread = new Thread(new Reader());
        readerThread.start();

        writerThread = new Thread(new Writer());
        writerThread.start();
    }

    @Override
    void close() {
        try {
            readerThread.interrupt();
            writerThread.interrupt();
            readSelector.wakeup();
            writeSelector.wakeup();
            serverSocketChannel.close();
            readSelector.close();
            writeSelector.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        taskPool.shutdown();
    }

    @Override
    public void run() {
        while (!Thread.interrupted()) {
            try {
                SocketChannel clientChannel;

                clientChannel = serverSocketChannel.accept();

                if (clientChannel == null) {
                    continue;
                }

                clientChannel.configureBlocking(false);

                readerLock.lock();
                readSelector.wakeup();
                clientChannel.register(readSelector, SelectionKey.OP_READ,
                        new Client(clientChannel, gatherer));
                readerLock.unlock();

            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
        }
    }

    private static class Client {
        private final static int BUFFER_SIZE = 1024;

        private final List<ClientTaskBuffer> output = new ArrayList<>();

        private final SocketChannel channel;
        private final MeasurementsGatherer gatherer;

        private ByteBuffer messageSizeBuffer = ByteBuffer.allocate(4);
        private int messageSize = -1;
        private int len = -1;

        private ByteBuffer messageBuffer;

        private Map<ClientTask, Long> clientStarts = new HashMap<>();

        Client(SocketChannel channel, MeasurementsGatherer gatherer) {
            this.channel = channel;
            this.gatherer = gatherer;
        }

        private List<Integer> parseVarLen(byte a, byte b, byte c, byte d) {
            String as = String.format("%8s", Integer.toBinaryString(a & 0xFF)).replace(' ', '0');
            String bs = String.format("%8s", Integer.toBinaryString(b & 0xFF)).replace(' ', '0');
            String cs = String.format("%8s", Integer.toBinaryString(c & 0xFF)).replace(' ', '0');
            String ds = String.format("%8s", Integer.toBinaryString(d & 0xFF)).replace(' ', '0');

            List<String> res = new ArrayList<>();

            res.add(as.substring(1));
            int len = 1;
            if (as.charAt(0) == '1') {
                len++;
                res.add(0, bs.substring(1));
                if (bs.charAt(0) == '1') {
                    len++;
                    res.add(0, cs.substring(1));
                    if (cs.charAt(0) == '1') {
                        len++;
                        res.add(0, ds.substring(1));
                    }
                }
            }

            StringBuilder builder = new StringBuilder();

            for (String s : res) {
                builder.append(s);
            }

            return Arrays.stream(new Integer[]{Integer.parseInt(builder.toString(), 2), len})
                    .collect(Collectors.toList());
        }


        Optional<ServerProtocol.SortRequest> getSortRequest() throws IOException {
            if (messageSize == -1) {
                channel.read(messageSizeBuffer);

                if (messageSizeBuffer.hasRemaining()) {
                    return Optional.empty();
                }

                messageSizeBuffer.flip();
                List<Integer> crutch = parseVarLen(messageSizeBuffer.get(0), messageSizeBuffer.get(1), messageSizeBuffer.get(2),
                        messageSizeBuffer.get(3));

                messageSize = crutch.get(0);
                len = crutch.get(1);
                messageBuffer = ByteBuffer.allocate(messageSize - (4 - len));

                messageSizeBuffer.compact();

                return Optional.empty();
            }

            channel.read(messageBuffer);

            if (messageBuffer.hasRemaining()) {
                return Optional.empty();
            }

            ByteBuffer requestAsBytes = ByteBuffer.allocate(len + messageSize);
            messageSizeBuffer.flip();
            requestAsBytes.put(messageSizeBuffer);
            messageBuffer.flip();
            requestAsBytes.put(messageBuffer);

            messageSizeBuffer = ByteBuffer.allocate(4);
            messageSize = -1;
            len = -1;

            return Optional.of(ServerProtocol.SortRequest.parseDelimitedFrom(
                    new ByteArrayInputStream(requestAsBytes.array())));
        }

        void addToOutput(int clientId, int taskId, List<Integer> values) throws IOException {
            ByteArrayOutputStream out = new ByteArrayOutputStream();

            ServerProtocol.SortResponse.newBuilder()
                    .setTaskId(taskId)
                    .setN(values.size())
                    .addAllValues(values).build().writeDelimitedTo(out);

            byte[] res = out.toByteArray();

            output.add(new ClientTaskBuffer(new ClientTask(clientId, taskId),
                    ByteBuffer.allocate(res.length).put(res)));
        }

        void writeOutput() throws IOException {
            ClientTaskBuffer clientTaskBuffer = output.get(0);

            if (!clientTaskBuffer.measured) {
                gatherer.measureClient(clientStarts.get(clientTaskBuffer.clientTask),
                        clientTaskBuffer.clientTask.clientId);
                clientTaskBuffer.measured = true;
            }

            ByteBuffer buffer = clientTaskBuffer.buffer;
            buffer.flip();
            channel.write(buffer);
            buffer.compact();

            if (buffer.position() == 0) {
                System.out.println("Sent");
                output.remove(0);
            }
        }
    }

    private class Reader implements Runnable {
        @Override
        public void run() {
            while (!Thread.interrupted()) {
                readerLock.lock();
                readerLock.unlock();

                try {
                    readSelector.select();
                } catch (IOException e) {
                    e.printStackTrace();
                    return;
                }

                Set<SelectionKey> keys = readSelector.selectedKeys();
                Iterator<SelectionKey> it = keys.iterator();

                while (it.hasNext()) {
                    SelectionKey key = it.next();

                    if (key.isReadable()) {
                        Client client = (Client) key.attachment();
                        try {
                            Optional<ServerProtocol.SortRequest> requestO = client.getSortRequest();

                            if (requestO.isPresent()) {
                                ServerProtocol.SortRequest request = requestO.get();
                                System.out.println("Read message.");
                                System.out.println(numberOfMessages.incrementAndGet());
                                client.clientStarts.put(new ClientTask(request.getClientId(), request.getTaskId()),
                                        gatherer.time());
                                taskPool.submit(new Task(client, request));
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                            return;
                        }
                    }

                    it.remove();
                }
            }
        }
    }

    private class Writer implements Runnable {
        @Override
        public void run() {
            while (!Thread.interrupted()) {
                writerLock.lock();
                writerLock.unlock();

                try {
                    writeSelector.select();
                } catch (IOException e) {
                    e.printStackTrace();
                    return;
                }

                Set<SelectionKey> keys = writeSelector.selectedKeys();
                Iterator<SelectionKey> it = keys.iterator();

                while (it.hasNext()) {
                    SelectionKey key = it.next();

                    if (key.isWritable()) {
                        Client client = (Client) key.attachment();

                        synchronized (client.output) {
                            if (client.output.isEmpty()) {
                                key.cancel();
                            } else {
                                try {
                                    client.writeOutput();
                                } catch (IOException e) {
                                    e.printStackTrace();
                                    return;
                                }
                            }
                        }
                    }

                    it.remove();
                }
            }
        }
    }

    private class Task implements Runnable {
        private final Client client;

        private final int clientId;
        private final int taskId;
        private final List<Integer> values;

        Task(Client client, ServerProtocol.SortRequest request) {
            this.client = client;
            this.clientId = request.getClientId();
            this.taskId = request.getTaskId();
            this.values = new ArrayList<>(request.getValuesList());
        }

        @Override
        public void run() {
            long requestStart = gatherer.time();
            List<Integer> result = Algorithms.sort(values);
            gatherer.measureRequest(requestStart, clientId);

            synchronized (client.output) {
                try {
                    client.addToOutput(clientId, taskId, result);

                    try {
                        writerLock.lock();
                        writeSelector.wakeup();
                        client.channel.register(writeSelector, SelectionKey.OP_WRITE, client);
                        writerLock.unlock();
                    } catch (ClosedChannelException e) {
                        e.printStackTrace();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private static class ClientTask {
        private final int clientId;
        private final int taskId;

        private ClientTask(int clientId, int taskId) {
            this.clientId = clientId;
            this.taskId = taskId;
        }

        @Override
        public int hashCode() {
            Random r = new Random();

            r.setSeed(clientId);
            int a = r.nextInt();
            int c = r.nextInt();
            r.setSeed(taskId);
            int b = r.nextInt();

            return a + c * b;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof ClientTask)) {
                return false;
            }

            ClientTask realOther = (ClientTask) other;

            return clientId == realOther.clientId && taskId == realOther.taskId;
        }
    }

    private static class ClientTaskBuffer {
        private final ClientTask clientTask;
        private final ByteBuffer buffer;
        private boolean measured = false;

        private ClientTaskBuffer(ClientTask clientTask, ByteBuffer buffer) {
            this.clientTask = clientTask;
            this.buffer = buffer;
        }
    }
}

