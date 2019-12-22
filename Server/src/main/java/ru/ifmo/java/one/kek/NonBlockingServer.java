package ru.ifmo.java.one.kek;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class NonBlockingServer extends Server {
    private final ExecutorService taskPool = Executors.newFixedThreadPool(4);

    private final ServerSocketChannel serverSocketChannel;
    private final Selector readSelector;
    private final Selector writeSelector;

    private final Thread readerThread;
    private final Thread writerThread;

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
            serverSocketChannel.close();
            readSelector.close();
            writeSelector.close();
            taskPool.shutdown();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        while (true) {
            try {
                SocketChannel clientChannel;

                clientChannel = serverSocketChannel.accept();

                if (clientChannel == null) {
                    continue;
                }

                clientChannel.configureBlocking(false);
                clientChannel.register(readSelector, SelectionKey.OP_READ, new Client(clientChannel));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static class Client {
        private final static int BUFFER_SIZE = 1024;

        private final List<ByteBuffer> input = new ArrayList<>();
        private final List<ByteBuffer> output = new ArrayList<>();

        private final SocketChannel channel;

        private final ByteBuffer readBuffer = ByteBuffer.allocate(10 * BUFFER_SIZE);

        private int startTime;

        Client(SocketChannel channel) {
          this.channel = channel;
        }

        public void readInput() throws IOException {
            channel.read(readBuffer);
            readBuffer.flip();

            for (int i = 0; i < readBuffer.remaining(); i++) {
                ByteBuffer lastBuffer = input.get(input.size() - 1);

                if (lastBuffer.hasRemaining()) {
                    lastBuffer.put(readBuffer.get());
                } else {
                    ByteBuffer newLast = ByteBuffer.allocate(BUFFER_SIZE);
                    newLast.put(readBuffer.get());
                    input.add(newLast);
                }
            }
            readBuffer.compact();
        }

        Optional<ServerProtocol.SortRequest> getSortRequest() {
            if (input.size() == 0 || input.get(0).position() < 4) {
                return Optional.empty();
            }

            int sizeOfMessage = input.get(0).asIntBuffer().array()[0];

            boolean hasRequest = input.stream().mapToInt(Buffer::capacity).sum() >= sizeOfMessage + 4;

            if (!hasRequest) {
                return Optional.empty();
            }

            ByteBuffer requestAsBytes = ByteBuffer.allocate(sizeOfMessage);

            input.get(0).getInt();
            ByteBuffer buffer = input.get(0);
            buffer.flip();

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

            IntBuffer requestAsInts = requestAsBytes.asIntBuffer();

            int clientId = requestAsInts.get();
            int taskId = requestAsInts.get();
            int n = requestAsInts.get();
            List<Integer> values = Arrays.stream(requestAsInts.array()).boxed().collect(Collectors.toList());

            return Optional.of(ServerProtocol.SortRequest.newBuilder()
                    .setClientId(clientId)
                    .setTaskId(taskId)
                    .setN(n)
                    .addAllValues(values).build());
        }
    }

    private class Reader implements Runnable {
        @Override
        public void run() {
            while (true) {
                Set<SelectionKey> keys = readSelector.selectedKeys();
                Iterator<SelectionKey> it = keys.iterator();

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
                            // TODO: тут происходит сабмит таска в пул
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
            while (true) {
                try {
                    writeSelector.select();

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
                                    ByteBuffer buffer = client.output.get(0);
                                    buffer.flip();
                                    client.channel.write(buffer);
                                    buffer.compact();
                                }
                            }
                        }

                        it.remove();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}

