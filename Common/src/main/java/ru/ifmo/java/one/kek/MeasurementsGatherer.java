package ru.ifmo.java.one.kek;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MeasurementsGatherer {
    private final List<Measurement> requestMeasurements = new ArrayList<>();
    private final List<Measurement> clientMeasurements = new ArrayList<>();
    private final List<Measurement> serverResponseMeasurements = new ArrayList<>();

    private ExecutorService measurer = Executors.newSingleThreadExecutor();

    long time() {
        return System.currentTimeMillis();
    }

    void measureRequest(long start, int clientId) {
        measureInner(start, clientId, requestMeasurements);
    }

    void measureClient(long start, int clientId) {
        measureInner(start, clientId, clientMeasurements);
    }

    void measureServerResponse(long start, int clientId) {
        measureInner(start, clientId, serverResponseMeasurements);
    }

    private void measureInner(long start, int clientId, List<Measurement> measurements) {
        long end = time();
        measurer.submit(() -> measurements.add(new Measurement(start, end, clientId)));
    }

    List<Measurement> getRequestMeasurements() {
        return requestMeasurements;
    }

    List<Measurement> getClientMeasurements() {
        return clientMeasurements;
    }

    List<Measurement> getServerResponseMeasurements() {
        return serverResponseMeasurements;
    }

    void clean() {
        serverResponseMeasurements.clear();
        requestMeasurements.clear();
        clientMeasurements.clear();
    }

    public static class Measurement {
        private final long start;
        private final long end;
        private final int clientId;

        public Measurement(long start, long end, int clientId) {
            this.start = start;
            this.end = end;
            this.clientId = clientId;
        }

        public long getStart() {
            return start;
        }

        public long getEnd() {
            return end;
        }

        public int getClientId() {
            return clientId;
        }

        MainAppServerProtocol.EndOfRoundResponse.ServerMeasurement toResponse() {
            return MainAppServerProtocol.EndOfRoundResponse.ServerMeasurement.newBuilder()
                    .setClientId(clientId)
                    .setStart(start)
                    .setEnd(end).build();
        }

        static Measurement fromResponse(MainAppServerProtocol.EndOfRoundResponse.ServerMeasurement response) {
            return new Measurement(response.getStart(), response.getEnd(), response.getClientId());
        }
    }
}
