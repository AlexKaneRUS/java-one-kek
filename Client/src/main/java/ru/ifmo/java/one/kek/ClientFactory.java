package ru.ifmo.java.one.kek;

import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class ClientFactory {
    public static List<Client> getListOfClients(int numberOfClients, int numberOfRequests, long delta,
                                                int numberOfElements, String serverHost) throws IOException {
        List<Client> clients = new ArrayList<>();

        for (int i = 0; i < numberOfClients; i++) {
            Socket socket = new Socket(serverHost, Constants.SERVER_PORT);
            clients.add(new Client(numberOfRequests, delta, numberOfElements, socket));
        }

        return clients;
    }

    private ClientFactory() {}
}
