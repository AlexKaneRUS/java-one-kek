package ru.ifmo.java.one.kek;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MetricsGatherer {
    private final List<Long> requestMeasurements = new ArrayList<>();
    private final List<Long> clientMeasurements = new ArrayList<>();
    private final List<Long> serverResponseMeasurements = new ArrayList<>();

    private ExecutorService measurer = Executors.newSingleThreadExecutor();

    long start() {
        return System.currentTimeMillis();
    }

    void measureRequest(long start) {
        long finish = System.currentTimeMillis();
        measurer.submit(() -> requestMeasurements.add(finish - start));
    }

    void measureClient(long start) {
        long finish = System.currentTimeMillis();
        measurer.submit(() -> clientMeasurements.add(finish - start));
    }

    void measureServerResponse(long start) {
        long finish = System.currentTimeMillis();
        measurer.submit(() -> serverResponseMeasurements.add(finish - start));
    }

    List<Long> getRequestMeasurements() {
        return requestMeasurements;
    }

    List<Long> getClientMeasurements() {
        return clientMeasurements;
    }

    List<Long> getServerResponseMeasurements() {
        return serverResponseMeasurements;
    }

    void clean() {
        serverResponseMeasurements.clear();
        requestMeasurements.clear();
        clientMeasurements.clear();
    }
}
