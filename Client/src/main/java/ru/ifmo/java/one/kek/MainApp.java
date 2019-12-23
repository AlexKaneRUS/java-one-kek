package ru.ifmo.java.one.kek;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

public class MainApp {
    public static void main(String[] args) throws IOException, ExecutionException, InterruptedException {
        String host = "localhost";

        if (args.length == 1) {
            host = args[0];
        }

        for (MainAppServerProtocol.TypeOfServer tos : MainAppServerProtocol.TypeOfServer.values()) {
            if (tos == MainAppServerProtocol.TypeOfServer.UNRECOGNIZED) {
                break;
            }

            System.out.println("Server type: " + tos.toString());

            System.out.println("Variating number of clients.");
            Map<StepConfig, StepMeasurements> clientsResult = new Tester(new InclusiveRange<>(1, 26, 2),
                    new InclusiveRange<>(10000, 10000, 2000),
                    new InclusiveRange<>(15L, 15L, 5L),
                    7, tos).conductTesting(host);

            printResult(clientsResult);

            System.out.println("Variating number of elements.");
            Map<StepConfig, StepMeasurements> elementsResult = new Tester(new InclusiveRange<>(11, 11, 5),
                    new InclusiveRange<>(1000, 18000, 1000),
                    new InclusiveRange<>(15L, 15L, 5L),
                    7, tos).conductTesting(host);

            printResult(elementsResult);

            System.out.println("Variating delta.");
            Map<StepConfig, StepMeasurements> deltaResult = new Tester(new InclusiveRange<>(11, 11, 5),
                    new InclusiveRange<>(10000, 10000, 2000),
                    new InclusiveRange<>(0L, 30L, 3L),
                    7, tos).conductTesting(host);

            printResult(deltaResult);
        }
    }

    private static void printResult(Map<StepConfig, StepMeasurements> result) {
        result.entrySet().stream().sorted(Comparator.comparing(x -> x.getKey().numberOfClients))
                .forEach(x -> System.out.println("(" + x.getKey().numberOfClients + ", " + x.getKey().numberOfElements
                        + ", " + x.getKey().delta + ") : (" + x.getValue().client + ", "
                        + x.getValue().request + ", " + x.getValue().serverResponse + ")"));
    }
}
