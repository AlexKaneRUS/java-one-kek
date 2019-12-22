package ru.ifmo.java.one.kek;

import java.io.IOException;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ExecutionException;

public class MainApp {
    public static void main(String[] args) throws IOException, ExecutionException, InterruptedException {
        Map<StepConfig, StepMeasurements> result = new Tester(new InclusiveRange<>(10, 50, 5),
                   new InclusiveRange<>(10000, 10000, 1),
                   new InclusiveRange<>(50L, 50L, 1L),
                   30, MainAppServerProtocol.TypeOfServer.BLOCKING).conductTesting("localhost");

        result.entrySet().stream().sorted(Comparator.comparing(x -> x.getKey().numberOfClients))
                .forEach(x -> System.out.println(x.getKey().numberOfClients +
                    ":  (" + x.getValue().client + ", " + x.getValue().request + ", " +
                    x.getValue().serverResponse + ")"));
    }
}
