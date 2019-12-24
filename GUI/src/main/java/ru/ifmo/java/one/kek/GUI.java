package ru.ifmo.java.one.kek;

import com.jidesoft.swing.RangeSlider;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.NumberFormatter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.NumberFormat;

public class GUI {
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

    public static void main(String[] args) {
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
        MainAppServerProtocol.TypeOfServer tos;
        boolean architectureChosen = false;
        boolean parametersChosen = false;
        boolean nRequestsChosen = false;

        JButton test = new JButton("Test!");

        {
            test.setSize(200, 100);
            test.setEnabled(false);
        }

        ParametersFrame() {
            setSize(1400, 800);
            setLayout(new GridBagLayout());

            setupArchitectures();
            setupParametersChoice();
            setupNumberOfRequests();

            GridBagConstraints gbc = new GridBagConstraints();
            gbc.gridwidth = 3;
            gbc.anchor = GridBagConstraints.CENTER;
            gbc.fill = GridBagConstraints.HORIZONTAL;

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
                        tos = MainAppServerProtocol.TypeOfServer.ROBUST;
                        blockingButton.setEnabled(false);
                        nonBlockingButton.setEnabled(false);
                        architectureChosen = true;
                        checkForTest();
                    }
            );

            blockingButton.addActionListener(
                    e -> {
                        tos = MainAppServerProtocol.TypeOfServer.BLOCKING;
                        robustButton.setEnabled(false);
                        nonBlockingButton.setEnabled(false);
                        architectureChosen = true;
                        checkForTest();
                    }
            );

            nonBlockingButton.addActionListener(
                    e -> {
                        tos = MainAppServerProtocol.TypeOfServer.NON_BLOCKING;
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

            RangeSlider nClients = new RangeSlider(0, 50);
            nClients.setPaintLabels(true);
            nClients.setMinorTickSpacing(5);
            nClients.setMajorTickSpacing(20);
            nClients.setPaintTicks(true);

            JFormattedTextField dNClients = field(0, 50, 25);
            dNClients.setEnabled(false);
            JFormattedTextField stepNClients = field(0, 50, 25);

            RangeSlider nElems = new RangeSlider(0, 30000);
            nElems.setPaintLabels(true);
            nElems.setMinorTickSpacing(5000);
            nElems.setMajorTickSpacing(10000);
            nElems.setPaintTicks(true);

            JFormattedTextField dNElems = field(0, 30000, 15000);
            dNElems.setEnabled(false);
            JFormattedTextField stepNElems = field(0, 30000, 15000);

            RangeSlider delta = new RangeSlider(0, 100);
            delta.setPaintLabels(true);
            delta.setMinorTickSpacing(5);
            delta.setMajorTickSpacing(20);
            delta.setPaintTicks(true);

            JFormattedTextField dDelta = field(0, 1000, 50);
            dDelta.setEnabled(false);
            JFormattedTextField stepDelta = field(0, 1000, 50);

            nClients.addChangeListener(e -> {
                nElems.setEnabled(false);
                dNElems.setEnabled(true);
                stepNElems.setEnabled(false);

                delta.setEnabled(false);
                dDelta.setEnabled(true);
                stepDelta.setEnabled(false);

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

            JSlider slider = new JSlider(0, 50, 25);
            slider.setPaintLabels(true);
            slider.setMinorTickSpacing(5);
            slider.setMajorTickSpacing(10);
            slider.setPaintTicks(true);

            slider.addChangeListener(e -> {
                nRequestsChosen = true;
                checkForTest();
            });

            inner.add(slider, gbc);

            gbc.weighty = 1;
            add(inner, gbc);
        }
    }
}
