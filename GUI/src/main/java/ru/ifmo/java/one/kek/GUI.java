package ru.ifmo.java.one.kek;

import com.jidesoft.swing.RangeSlider;
import org.knowm.xchart.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.NumberFormatter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.NumberFormat;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

public class GUI {
    private static String host = "localhost";

    private static void start() {
        JFrame gui = new JFrame("One Kek Tester!");
        gui.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        gui.add(new MenuPane());
        gui.pack();
        gui.setLocationRelativeTo(null);
        gui.setVisible(true);
    }

    private static JButton createButton(String label, ActionListener listener) {
        JButton button = new JButton(label);
        button.setSize(200, 50);
        button.addActionListener(listener);
        return button;
    }

    public static void main(String[] args) throws InterruptedException {
        if (args.length == 1) {
            host = args[0];
        }

        start();
    }

    private static class MenuPane extends JPanel {
        MenuPane() {
            setBorder(new EmptyBorder(10, 10, 10, 10));
            setLayout(new GridBagLayout());

            GridBagConstraints gbc = new GridBagConstraints();
            gbc.gridwidth = GridBagConstraints.REMAINDER;
            gbc.anchor = GridBagConstraints.NORTH;

            add(new JLabel("<html><h1>One Kek Tester!</h1><hr></html>"), gbc);

            gbc.anchor = GridBagConstraints.CENTER;
            gbc.fill = GridBagConstraints.HORIZONTAL;

            JPanel buttons = new JPanel(new GridBagLayout());
            buttons.add(createButton("Commence testing!", e -> {
                ParametersFrame parametersFrame = new ParametersFrame();
                parametersFrame.setVisible(true);
            }), gbc);
            gbc.weighty = 1;
            add(buttons, gbc);
        }
    }

    private static class ParametersFrame extends JFrame {
        MainAppServerProtocol.TypeOfServer[] tos = new MainAppServerProtocol.TypeOfServer[1];

        boolean architectureChosen = false;
        boolean parametersChosen = false;
        boolean nRequestsChosen = false;

        RangeSlider nClients;

        JFormattedTextField dNClients;
        JFormattedTextField stepNClients;

        RangeSlider nElems;

        JFormattedTextField dNElems;
        JFormattedTextField stepNElems;

        RangeSlider delta;

        JFormattedTextField dDelta;
        JFormattedTextField stepDelta;

        JSlider numberOfRequests;

        int[] chosenParameter = new int[1];

        JButton test = new JButton("Test!");

        {
            test.setSize(200, 100);
            test.setEnabled(false);
        }

        ParametersFrame() {
            setSize(700, 400);

            // Get current screen size
            Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();

            // Get x coordinate on screen for make JWindow locate at center
            int x = (screenSize.width - getSize().width) / 2;

            // Get y coordinate on screen for make JWindow locate at center
            int y = (screenSize.height - getSize().height) / 2;

            // Set new location for JWindow
            setLocation(x, y);

            setLayout(new GridBagLayout());

            setupArchitectures();
            setupParametersChoice();
            setupNumberOfRequests();

            GridBagConstraints gbc = new GridBagConstraints();
            gbc.gridwidth = 3;
            gbc.anchor = GridBagConstraints.CENTER;
            gbc.fill = GridBagConstraints.HORIZONTAL;

            test.addActionListener(new TestListener(nClients, dNClients, stepNClients, nElems, dNElems,
                    stepNElems, delta, dDelta, stepDelta, numberOfRequests,
                    chosenParameter, tos, this));

            add(test, gbc);
        }

        private void setupArchitectures() {
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.gridwidth = GridBagConstraints.REMAINDER;
            gbc.anchor = GridBagConstraints.NORTH;

            JPanel architectures = new JPanel(new GridBagLayout());
            architectures.add(new JLabel("Choose server architecture:"), gbc);

            gbc.anchor = GridBagConstraints.CENTER;
            gbc.fill = GridBagConstraints.HORIZONTAL;

            JPanel architecturesInner = new JPanel();
            architecturesInner.setLayout(new BoxLayout(architecturesInner, BoxLayout.X_AXIS));

            JButton robustButton = new JButton("Robust");
            JButton blockingButton = new JButton("Blocking");
            JButton nonBlockingButton = new JButton("Non-Blocking");

            robustButton.addActionListener(
                    e -> {
                        tos[0] = MainAppServerProtocol.TypeOfServer.ROBUST;
                        blockingButton.setEnabled(false);
                        nonBlockingButton.setEnabled(false);
                        architectureChosen = true;
                        checkForTest();
                    }
            );

            blockingButton.addActionListener(
                    e -> {
                        tos[0] = MainAppServerProtocol.TypeOfServer.BLOCKING;
                        robustButton.setEnabled(false);
                        nonBlockingButton.setEnabled(false);
                        architectureChosen = true;
                        checkForTest();
                    }
            );

            nonBlockingButton.addActionListener(
                    e -> {
                        tos[0] = MainAppServerProtocol.TypeOfServer.NON_BLOCKING;
                        robustButton.setEnabled(false);
                        blockingButton.setEnabled(false);
                        architectureChosen = true;
                        checkForTest();
                    }
            );

            architecturesInner.add(robustButton);
            architecturesInner.add(blockingButton);
            architecturesInner.add(nonBlockingButton);

            architectures.add(architecturesInner, gbc);

            gbc.weighty = 1;
            add(architectures, gbc);
        }

        private void setupParametersChoice() {
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.gridwidth = GridBagConstraints.REMAINDER;
            gbc.anchor = GridBagConstraints.NORTH;

            JPanel parameters = new JPanel(new GridBagLayout());
            parameters.add(new JLabel("Choose parameters for testing:"), gbc);

            gbc.anchor = GridBagConstraints.CENTER;
            gbc.fill = GridBagConstraints.HORIZONTAL;

            JPanel parametersInner = new JPanel();
            parametersInner.setLayout(new BoxLayout(parametersInner, BoxLayout.X_AXIS));

            nClients = new RangeSlider(0, 50);
            nClients.setPaintLabels(true);
            nClients.setMinorTickSpacing(5);
            nClients.setMajorTickSpacing(20);
            nClients.setPaintTicks(true);

            dNClients = field(0, 50, 25);
            dNClients.setEnabled(false);
            stepNClients = field(1, 50, 25);

            nElems = new RangeSlider(0, 30000);
            nElems.setPaintLabels(true);
            nElems.setMinorTickSpacing(5000);
            nElems.setMajorTickSpacing(10000);
            nElems.setPaintTicks(true);

            dNElems = field(0, 30000, 15000);
            dNElems.setEnabled(false);
            stepNElems = field(1, 30000, 15000);

            delta = new RangeSlider(0, 100);
            delta.setPaintLabels(true);
            delta.setMinorTickSpacing(5);
            delta.setMajorTickSpacing(20);
            delta.setPaintTicks(true);

            dDelta = field(0, 100, 50);
            dDelta.setEnabled(false);
            stepDelta = field(1, 100, 50);

            nClients.addChangeListener(e -> {
                nElems.setEnabled(false);
                dNElems.setEnabled(true);
                stepNElems.setEnabled(false);

                delta.setEnabled(false);
                dDelta.setEnabled(true);
                stepDelta.setEnabled(false);

                chosenParameter[0] = 0;
                parametersChosen = true;
                checkForTest();
            });

            nElems.addChangeListener(e -> {
                nClients.setEnabled(false);
                dNClients.setEnabled(true);
                stepNClients.setEnabled(false);

                delta.setEnabled(false);
                dDelta.setEnabled(true);
                stepDelta.setEnabled(false);

                chosenParameter[0] = 1;
                parametersChosen = true;
                checkForTest();
            });

            delta.addChangeListener(e -> {
                nClients.setEnabled(false);
                dNClients.setEnabled(true);
                stepNClients.setEnabled(false);

                nElems.setEnabled(false);
                dNElems.setEnabled(true);
                stepNElems.setEnabled(false);

                chosenParameter[0] = 2;
                parametersChosen = true;
                checkForTest();
            });

            parametersInner.add(parameterChooser("Number of clients", nClients, dNClients, stepNClients));
            parametersInner.add(Box.createHorizontalStrut(20));
            parametersInner.add(parameterChooser("Number of elements", nElems, dNElems, stepNElems));
            parametersInner.add(Box.createHorizontalStrut(20));
            parametersInner.add(parameterChooser("Delta", delta, dDelta, stepDelta));

            parameters.add(parametersInner);

            gbc.weighty = 3;
            add(parameters, gbc);
        }

        private JPanel parameterChooser(String label, RangeSlider rangeSlider, JFormattedTextField d,
                                        JFormattedTextField step) {
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.gridwidth = GridBagConstraints.REMAINDER;
            gbc.anchor = GridBagConstraints.NORTH;

            JPanel inner = new JPanel(new GridBagLayout());
            inner.add(new JLabel(label), gbc);

            gbc.anchor = GridBagConstraints.CENTER;
            gbc.fill = GridBagConstraints.HORIZONTAL;

            JPanel def = new JPanel(new GridBagLayout());
            def.add(new JLabel("Default:"));
            def.add(d);

            JPanel range = new JPanel(new GridBagLayout());
            range.add(rangeSlider);

            JPanel ztep = new JPanel(new GridBagLayout());
            ztep.add(new JLabel("Step:"));
            ztep.add(step);

            inner.add(rangeSlider, gbc);
            inner.add(ztep, gbc);
            inner.add(def, gbc);

            return inner;
        }

        private JFormattedTextField field(int left, int right, int def) {
            NumberFormatter formatter = new NumberFormatter(NumberFormat.getInstance());

            formatter.setValueClass(Integer.class);
            formatter.setMinimum(left);
            formatter.setMaximum(right);
            formatter.setCommitsOnValidEdit(true);

            JFormattedTextField d = new JFormattedTextField(formatter);
            d.setValue(def);

            return d;
        }

        void checkForTest() {
            if (architectureChosen && parametersChosen && nRequestsChosen) {
                test.setEnabled(true);
            }
        }

        private void setupNumberOfRequests() {
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.gridwidth = GridBagConstraints.REMAINDER;
            gbc.anchor = GridBagConstraints.NORTH;

            JPanel inner = new JPanel(new GridBagLayout());
            inner.add(new JLabel("Choose number of requests:"), gbc);

            gbc.anchor = GridBagConstraints.CENTER;
            gbc.fill = GridBagConstraints.HORIZONTAL;

            numberOfRequests = new JSlider(0, 50, 25);
            numberOfRequests.setPaintLabels(true);
            numberOfRequests.setMinorTickSpacing(5);
            numberOfRequests.setMajorTickSpacing(10);
            numberOfRequests.setPaintTicks(true);

            numberOfRequests.addChangeListener(e -> {
                nRequestsChosen = true;
                checkForTest();
            });

            inner.add(numberOfRequests, gbc);

            gbc.weighty = 1;
            add(inner, gbc);
        }

        private static class TestListener implements ActionListener {
            final RangeSlider nClients;

            final JFormattedTextField dNClients;
            final JFormattedTextField stepNClients;

            final RangeSlider nElems;

            final JFormattedTextField dNElems;
            final JFormattedTextField stepNElems;

            final RangeSlider delta;

            final JFormattedTextField dDelta;
            final JFormattedTextField stepDelta;

            final JSlider numberOfRequests;

            final int[] chosenParameter;

            final MainAppServerProtocol.TypeOfServer[] tos;
            final JFrame from;

            private TestListener(RangeSlider nClients, JFormattedTextField dNClients, JFormattedTextField stepNClients,
                                 RangeSlider nElems, JFormattedTextField dNElems, JFormattedTextField stepNElems,
                                 RangeSlider delta, JFormattedTextField dDelta, JFormattedTextField stepDelta,
                                 JSlider numberOfRequests, int[] chosenParameter, MainAppServerProtocol.TypeOfServer[] tos, JFrame from) {
                this.nClients = nClients;
                this.dNClients = dNClients;
                this.stepNClients = stepNClients;
                this.nElems = nElems;
                this.dNElems = dNElems;
                this.stepNElems = stepNElems;
                this.delta = delta;
                this.dDelta = dDelta;
                this.stepDelta = stepDelta;
                this.numberOfRequests = numberOfRequests;
                this.chosenParameter = chosenParameter;
                this.tos = tos;
                this.from = from;
            }

            @Override
            public void actionPerformed(ActionEvent e) {
                InclusiveRange<Integer> cl;
                InclusiveRange<Integer> el;
                InclusiveRange<Long> d;

                String name;
                Function<StepConfig, Integer> getter;

                switch (chosenParameter[0]) {
                    case 0:
                        cl = new InclusiveRange<>(nClients.getLowValue(), nClients.getHighValue(),
                                (Integer) stepNClients.getValue());
                        el = new InclusiveRange<>((Integer) dNElems.getValue(), (Integer) dNElems.getValue(),
                                1);
                        d = new InclusiveRange<>(new Long((Integer) dDelta.getValue()),
                                new Long((Integer) dDelta.getValue()),
                                1L);
                        name = "Number of clients";
                        getter = x -> x.numberOfClients;
                        break;
                    case 1:
                        cl = new InclusiveRange<>((Integer) dNClients.getValue(), (Integer) dNClients.getValue(),
                                1);
                        el = new InclusiveRange<>(nElems.getLowValue(), nElems.getHighValue(),
                                (Integer) stepNElems.getValue());
                        d = new InclusiveRange<>(new Long((Integer) dDelta.getValue()),
                                new Long((Integer) dDelta.getValue()),
                                1L);
                        name = "Number of elements";
                        getter = x -> x.numberOfElements;
                        break;
                    case 2:
                        cl = new InclusiveRange<>((Integer) dNClients.getValue(), (Integer) dNClients.getValue(),
                                1);
                        el = new InclusiveRange<>(nElems.getLowValue(), nElems.getHighValue(),
                                1);
                        d = new InclusiveRange<>((long) delta.getLowValue(), (long) delta.getHighValue(),
                                new Long((Integer) stepDelta.getValue()));
                        name = "Delta";
                        getter = x -> (int) x.delta;
                        break;
                    default:
                        throw new RuntimeException();
                }

                from.dispatchEvent(new WindowEvent(from,
                        WindowEvent.WINDOW_CLOSING));

                Map<StepConfig, StepMeasurements> results = new HashMap<>();

                new SwingWorker<Void, String>() {
                    @Override
                    protected Void doInBackground() throws Exception {
                        try {
                            Tester tester = new Tester(cl, el, d, numberOfRequests.getValue(), tos[0]);
                            results.putAll(tester.conductTesting(host));
                        } catch (Throwable err) {
                            for (Window window : Window.getWindows()) {
                                if (window instanceof JDialog) {
                                    window.dispose();
                                }
                            }

                            JOptionPane.showMessageDialog(from, err.getMessage(),
                                    "Error!", JOptionPane.ERROR_MESSAGE);
                            err.printStackTrace();
                        }

                        return null;
                    }

                    @Override
                    public void done() {
                        if (!results.isEmpty()) {
                            for (Window window : Window.getWindows()) {
                                if (window instanceof JDialog) {
                                    window.dispose();
                                }
                            }

                            try {
                                writeResults(results, cl, el, d);
                            } catch (IOException ex) {
                                ex.printStackTrace();
                            }

                            showCharts(results, getter, name);
                        }
                    }

                }.execute();

                JOptionPane.showMessageDialog(from, "Getting your test results!",
                        "Testing!", JOptionPane.INFORMATION_MESSAGE, new ImageIcon(GUI.class.getResource("/inf.gif")));
            }
        }
    }

    private static void writeResults(Map<StepConfig, StepMeasurements> result, InclusiveRange<Integer> cl,
                                     InclusiveRange<Integer> el, InclusiveRange<Long> d) throws IOException {
        BufferedWriter writer = Files.newBufferedWriter(Paths.get("testing_results.res"));

        writer.write("Number of clients in range [" + cl.left + "; " + cl.right + "] with step " + cl.step);
        writer.newLine();
        writer.write("Number of elements in range [" + el.left + "; " + el.right + "] with step " + el.step);
        writer.newLine();
        writer.write("Delta in range [" + d.left + "; " + d.right + "] with step " + d.step);
        writer.newLine();

        for (Map.Entry<StepConfig, StepMeasurements> x : result.entrySet()) {
            writer.write(x.getKey().numberOfClients + ";" + x.getKey().numberOfElements + ";" +
                    x.getKey().delta + ";" + x.getValue().client + ";" +
                    x.getValue().request + ";" + x.getValue().serverResponse);
            writer.newLine();
        }

        writer.flush();
        writer.close();
    }

    private static void showCharts(Map<StepConfig, StepMeasurements> result, Function<StepConfig, Integer> getter, String name) {
        double[] xData = new double[result.size()];

        double[] clientData = new double[result.size()];
        double[] requestData = new double[result.size()];
        double[] serverResponseTimeData = new double[result.size()];

        result.entrySet().stream().sorted(Comparator.comparing(x -> getter.apply(x.getKey()))).forEach(
                new Consumer<Map.Entry<StepConfig, StepMeasurements>>() {
                    int counter = 0;

                    @Override
                    public void accept(Map.Entry<StepConfig, StepMeasurements> x) {
                        xData[counter] = getter.apply(x.getKey());
                        clientData[counter] = x.getValue().client;
                        requestData[counter] = x.getValue().request;
                        serverResponseTimeData[counter] = x.getValue().serverResponse;
                        counter++;
                    }
                });

        XYChart chart1 = new XYChartBuilder().width(600).height(500)
                .title("Client on server").xAxisTitle(name).yAxisTitle("time, ms").build();

        XYChart chart2 = new XYChartBuilder().width(600).height(500)
                .title("Task on server").xAxisTitle(name).yAxisTitle("time, ms").build();

        XYChart chart3 = new XYChartBuilder().width(600).height(500)
                .title("Server response time").xAxisTitle(name).yAxisTitle("time, ms").build();

        chart1.getStyler().setDefaultSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Scatter);
        chart1.getStyler().setLegendVisible(false);
        chart1.getStyler().setMarkerSize(16);

        chart1.addSeries("time(x)", xData, clientData);

        chart2.getStyler().setDefaultSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Scatter);
        chart2.getStyler().setLegendVisible(false);
        chart2.getStyler().setMarkerSize(16);

        chart2.addSeries("time(x)", xData, requestData);

        chart3.getStyler().setDefaultSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Scatter);
        chart3.getStyler().setLegendVisible(false);
        chart3.getStyler().setMarkerSize(16);

        chart3.addSeries("time(x)", xData, serverResponseTimeData);

        // Create and set up the window.
        JFrame frame = new JFrame("Testing results!");
        frame.setLayout(new BorderLayout());

        // charts
        JPanel chart1Panel = new XChartPanel<>(chart1);
        JPanel chart2Panel = new XChartPanel<>(chart2);
        JPanel chart3Panel = new XChartPanel<>(chart3);

        frame.add(chart1Panel, BorderLayout.WEST);
        frame.add(chart2Panel, BorderLayout.EAST);
        frame.add(chart3Panel, BorderLayout.SOUTH);

        // Display the window.
        frame.pack();
        frame.setVisible(true);
    }
}
