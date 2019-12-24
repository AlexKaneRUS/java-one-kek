package ru.ifmo.java.one.kek;

import java.io.IOException;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.stream.Collectors;

public class Tester {
    private final MeasurementsGatherer gatherer = new MeasurementsGatherer();
    private final ExecutorService poolOfClients = Executors.newCachedThreadPool();

    private final InclusiveRange<Integer> numberOfClientsRange;
    private final InclusiveRange<Integer> numberOfElementsRange;
    private final InclusiveRange<Long> deltaRange;
    private final int numberOfRequests;
    private final MainAppServerProtocol.TypeOfServer typeOfServer;

    public Tester(InclusiveRange<Integer> numberOfClientsRange,
                  InclusiveRange<Integer> numberOfElementsRange,
                  InclusiveRange<Long> deltaRange,
                  int numberOfRequests,
                  MainAppServerProtocol.TypeOfServer typeOfServer) {
        this.numberOfClientsRange = numberOfClientsRange;
        this.numberOfElementsRange = numberOfElementsRange;
        this.deltaRange = deltaRange;
        this.numberOfRequests = numberOfRequests;
        this.typeOfServer = typeOfServer;
    }

    public Map<StepConfig, StepMeasurements> conductTesting(String host) {
        Map<StepConfig, StepMeasurements> result = new HashMap<>();

        try (Socket socket = new Socket(host, Constants.MAIN_APP_SERVER_PORT)) {
            startTesting(socket);

            for (int numberOfClients = numberOfClientsRange.left; numberOfClients <= numberOfClientsRange.right; numberOfClients += numberOfClientsRange.step) {
                for (int numberOfElements = numberOfElementsRange.left; numberOfElements <= numberOfElementsRange.right; numberOfElements += numberOfElementsRange.step) {
                    for (long delta = deltaRange.left; delta <= deltaRange.right; delta += deltaRange.step) {
                        result.put(new StepConfig(numberOfClients, numberOfElements, delta),
                                conductStep(numberOfElements, numberOfClients, delta, host, socket));
                    }
                }
            }

            endTesting(socket);
        } catch (IOException | InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }

        return result;
    }

    public StepMeasurements conductStep(int numberOfElements, int numberOfClients, long delta, String host, Socket socket)
            throws IOException, ExecutionException, InterruptedException {
        List<Client> clients = ClientFactory.getListOfClients(numberOfClients, numberOfRequests,
                delta, numberOfElements, host, gatherer);

        List<Future<?>> futures = clients.stream().map(poolOfClients::submit).collect(Collectors.toList());

        for (Future<?> future : futures) {
            future.get();
        }

        MainAppServerProtocol.EndOfRoundResponse serverMeasurementsRaw = sendEndOfRoundRequest(socket);

        List<MeasurementsGatherer.Measurement> serverResponseMeasurements = filterRelevantMeasurements(
                gatherer.getServerResponseMeasurements());
        List<MeasurementsGatherer.Measurement> requestMeasurements = filterRelevantMeasurements(
                serverMeasurementsRaw.getRequestMeasurementsList().stream()
                .map(MeasurementsGatherer.Measurement::fromResponse)
                .collect(Collectors.toList()));
        List<MeasurementsGatherer.Measurement> clientMeasurements = filterRelevantMeasurements(
                serverMeasurementsRaw.getClientMeasurementsList().stream()
                .map(MeasurementsGatherer.Measurement::fromResponse)
                .collect(Collectors.toList()));

        return new StepMeasurements(requestMeasurements.stream().mapToLong(x -> x.getEnd() - x.getStart()).average().getAsDouble(),
                clientMeasurements.stream().mapToLong(x -> x.getEnd() - x.getStart()).average().getAsDouble(),
                serverResponseMeasurements.stream().mapToLong(x -> x.getEnd() - x.getStart()).map(x -> x / numberOfRequests).average()
                        .getAsDouble());
    }

    private List<MeasurementsGatherer.Measurement> filterRelevantMeasurements(List<MeasurementsGatherer.Measurement> measurements) {
        List<List<MeasurementsGatherer.Measurement>> byClient = new ArrayList<>(measurements.stream().
                collect(Collectors.groupingBy(MeasurementsGatherer.Measurement::getClientId)).values());

        long leftBorder = byClient.stream()
                .map(x -> x.stream()
                        .min(Comparator.comparing(MeasurementsGatherer.Measurement::getStart))
                        .get().getStart())
                .max(Comparator.comparing(Function.identity())).get();

        long rightBorder = byClient.stream()
                .map(x -> x.stream()
                        .max(Comparator.comparing(MeasurementsGatherer.Measurement::getEnd))
                        .get().getEnd())
                .min(Comparator.comparing(Function.identity())).get();

        return measurements.stream().filter(x -> leftBorder <= x.getStart() && x.getEnd() <= rightBorder)
                .collect(Collectors.toList());
    }

    private void startTesting(Socket socket) throws IOException {
        MainAppServerProtocol.RunServerRequest.newBuilder()
                .setTypeOfServer(typeOfServer)
                .build().writeDelimitedTo(socket.getOutputStream());

        MainAppServerProtocol.RunServerResponse.parseDelimitedFrom(socket.getInputStream());
    }

    private void endTesting(Socket socket) throws IOException {
        MainAppServerProtocol.BaseRequest.newBuilder().setEndOfTestingRequest(MainAppServerProtocol.EndOfTestingRequest
                .newBuilder()
                .build()).build().writeDelimitedTo(socket.getOutputStream());

        MainAppServerProtocol.EndOfTestingResponse.parseDelimitedFrom(socket.getInputStream());
    }

    private MainAppServerProtocol.EndOfRoundResponse sendEndOfRoundRequest(Socket socket) throws IOException {
        MainAppServerProtocol.BaseRequest.newBuilder().setEndOfRoundRequest(MainAppServerProtocol.EndOfRoundRequest.
                newBuilder().
                build()).build().writeDelimitedTo(socket.getOutputStream());

        return MainAppServerProtocol.EndOfRoundResponse.parseDelimitedFrom(socket.getInputStream());
    }
}
