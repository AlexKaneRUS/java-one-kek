package ru.ifmo.java.one.kek;

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
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

public class NonBlockingServer extends Server {
    private final ExecutorService taskPool = Executors.newFixedThreadPool(4);

    private final ServerSocketChannel serverSocketChannel;
    private final Selector readSelector;
    private final Selector writeSelector;

    private final Thread readerThread;
    private final Lock readerLock = new ReentrantLock();

    private final Thread writerThread;
    private final Lock writerLock = new ReentrantLock();

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
            readSelector.close();
            writeSelector.close();
            serverSocketChannel.close();
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
                System.out.println("Waking up");
                readSelector.wakeup();
                clientChannel.register(readSelector, SelectionKey.OP_READ,
                        new Client(clientChannel, gatherer));
                readerLock.unlock();

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static class Client {
        private final static int BUFFER_SIZE = 1024;

        private final List<ByteBuffer> input = new ArrayList<>();
        private final List<ClientTaskBuffer> output = new ArrayList<>();

        {
            input.add(ByteBuffer.allocate(BUFFER_SIZE));
        }

        private final SocketChannel channel;
        private final MeasurementsGatherer gatherer;

        private final ByteBuffer readBuffer = ByteBuffer.allocate(10 * BUFFER_SIZE);

        private Map<ClientTask, Long> clientStarts = new HashMap<>();

        Client(SocketChannel channel, MeasurementsGatherer gatherer) {
            this.channel = channel;
            this.gatherer = gatherer;
        }

        void readInput() throws IOException {
            System.out.println("Reading!");
            channel.read(readBuffer);
            readBuffer.flip();

            int bufSize = readBuffer.remaining();
            System.out.println(bufSize);
            for (int i = 0; i < bufSize; i++) {
                ByteBuffer lastBuffer = input.get(input.size() - 1);

                byte b = readBuffer.get();

                if (i < 4) {
                    String s1 = String.format("%8s", Integer.toBinaryString(b & 0xFF)).replace(' ', '0');
                    System.out.println(s1);
                }
                if (lastBuffer.hasRemaining()) {
                    lastBuffer.put(b);
                } else {
                    ByteBuffer newLast = ByteBuffer.allocate(BUFFER_SIZE);
                    newLast.put(b);
                    input.add(newLast);
                }
            }
            readBuffer.compact();
        }

        private int parseVarLen(byte a, byte b, byte c, byte d) {
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

            return Integer.parseInt(builder.toString(), 2) + len;
        }


        Optional<ServerProtocol.SortRequest> getSortRequest() {
            if (input.size() == 0 || input.get(0).position() < 4) {
                return Optional.empty();
            }

            ByteBuffer buffer = input.get(0);
            buffer.flip();

            int sizeOfMessage = parseVarLen(buffer.get(0), buffer.get(1), buffer.get(2), buffer.get(3));

            boolean hasRequest = input.stream().mapToInt(Buffer::capacity).sum() >= sizeOfMessage;

            if (!hasRequest) {
                buffer.compact();
                return Optional.empty();
            }

            ByteBuffer requestAsBytes = ByteBuffer.allocate(sizeOfMessage);

            while (requestAsBytes.hasRemaining()) {
                if (buffer.remaining() == 0) {
                    input.remove(0);
                    buffer = input.get(0);
                    buffer.flip();
                }

                requestAsBytes.put(buffer.get());
            }

            buffer.compact();

            if (buffer.position() == 0) {
                input.remove(0);
            }

            try {
                return Optional.of(ServerProtocol.SortRequest.parseDelimitedFrom(
                        new ByteArrayInputStream(requestAsBytes.array())));
            } catch (IOException e) {
                e.printStackTrace();
                return Optional.empty();
            }
        }

        void addToOutput(int clientId, int taskId, List<Integer> values) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();

            try {
                ServerProtocol.SortResponse.newBuilder()
                        .setTaskId(taskId)
                        .setN(values.size())
                        .addAllValues(values).build().writeDelimitedTo(out);
            } catch (IOException e) {
                return;
            }

            output.add(new ClientTaskBuffer(new ClientTask(clientId, taskId),
                    ByteBuffer.wrap(out.toByteArray())));
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
                output.remove(0);
                System.out.println("Processed client!");
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
                    System.out.println("Blocking");
                    readSelector.select();
                } catch (IOException e) {
                    return;
                }

                Set<SelectionKey> keys = readSelector.selectedKeys();
                Iterator<SelectionKey> it = keys.iterator();

                System.out.println(keys);

                while (it.hasNext()) {
                    SelectionKey key = it.next();

                    if (key.isReadable()) {
                        Client client = (Client) key.attachment();
                        try {
                            client.readInput();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }

                        Optional<ServerProtocol.SortRequest> requestO = client.getSortRequest();

                        if (requestO.isPresent()) {
                            ServerProtocol.SortRequest request = requestO.get();
                            System.out.println("Read message.");
                            client.clientStarts.put(new ClientTask(request.getClientId(), request.getTaskId()),
                                    gatherer.time());
                            taskPool.submit(new Task(client, request));
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
            this.values = request.getValuesList();
        }

        @Override
        public void run() {
            long requestStart = gatherer.time();
            List<Integer> result = Algorithms.sort(values);
            gatherer.measureRequest(requestStart, clientId);

            synchronized (client.output) {
                client.addToOutput(clientId, taskId, result);

                try {
                    // TODO: дебажим write
                    writerLock.lock();
                    writeSelector.wakeup();
                    client.channel.register(writeSelector, SelectionKey.OP_WRITE, client);
                    writerLock.unlock();
                } catch (ClosedChannelException e) {
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

