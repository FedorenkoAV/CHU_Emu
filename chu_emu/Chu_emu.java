/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package chu_emu;

import java.awt.Color;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import javax.swing.JOptionPane;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import jssc.SerialPort;
import jssc.SerialPortEvent;
import jssc.SerialPortEventListener;
import jssc.SerialPortException;
import jssc.SerialPortList;

/**
 *
 * @author Fedorenko Aleksandr
 */
public class Chu_emu extends javax.swing.JFrame {

    private static final Logger LOGGER = Logger.getLogger(Chu_emu.class.getName());
    private static FileHandler fh = null;

    public final String NOP = "";
    public final String GETTX = "gettx\r\n";
    public final String GETTP = "gettp\r\n";
    public final String GETPAINFO = "getpainfo\r\n";
    public final String CLION = "cli on\r\n";
    public final String CLIOFF = "cli off\r\n";
    public final String CDMGR = "cd mgr\r\n";
    public final String SETTX = "settx";
    public final String SETTP = "settp";
    public final String SETPAINFO031 = "setpainfo 0 3 1\r\n";
    public final String SETPAINFO130 = "setpainfo 1 3 0\r\n";

    final String TX_POWER = "TX Power(mW): ";
    final String TX_FREQ = "TX frequency(Hz): ";
    SerialPort serialPort;
    String portName;
    int baudRate;
    int dataBits;
    int stopBits;
    int parity;

//    int command = NOP;
    String answerCollector = "";
    boolean waitInvite;
    String invite = "%>";

    // Стили редактора
    private Style bold = null; // стиль полужирного текста
    private Style normal = null; // стиль обычного текста

    private final String STYLE_heading = "heading",
            STYLE_normal = "normal",
            FONT_style = "Monospaced";

    String[] ports;
    boolean portIsOpen = false;
    RefreshPortsThread rpt;
    boolean txEnable = true;
    boolean cliIsOn = false;
    boolean cdMgrIsOn = false;

    /**
     * Creates new form MainJFrame
     */
    public Chu_emu() {
        try {
            initComponents();
            fh = new FileHandler(LocalDateTime.now().format(DateTimeFormatter.ofPattern("uuuuMMddHHmmss")) + "Chu_emu.log", false);
            Logger l = Logger.getLogger("");
            fh.setFormatter(new SimpleFormatter());
            l.addHandler(fh);
            l.setLevel(Level.CONFIG);
            createStyles(jTextPaneLog);
//        refreshPorts();
            rpt = new RefreshPortsThread();
            rpt.start();
        } catch (IOException | SecurityException ex) {
            LOGGER.log(Level.SEVERE, "Ошибка в конструкторе.", ex);

        }
    }

    private void refreshPorts() {
        if (jComboBoxPortName.isPopupVisible()) {
            return;
        }
        try {
            SwingUtilities.invokeAndWait(new Runnable() {
                @Override
                public void run() {
                    jComboBoxPortName.setModel(new javax.swing.DefaultComboBoxModel<>(SerialPortList.getPortNames()));
                }
            });
//        appendText ("PopupVisible: " + jComboBoxPortName.isPopupVisible());
        } catch (InterruptedException ex) {
            Logger.getLogger(Chu_emu.class.getName()).log(Level.SEVERE, null, ex);
        } catch (InvocationTargetException ex) {
            Logger.getLogger(Chu_emu.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public class RefreshPortsThread extends Thread {

        @Override
        public void run() {
            while (true) {
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ex) {
                    javax.swing.JOptionPane.showMessageDialog(null, "Ошибка в RefreshPortsThread: " + ex.getMessage(), "Ошибка", JOptionPane.ERROR_MESSAGE);
                    appendBoldText("Ошибка в RefreshPortsThread: " + ex.getMessage());
                    printStackTraceElements(ex);
                }
                if (!portIsOpen) {
                    refreshPorts();
                }

            }

        }
    }

    public class SetPortSettingsThread extends Thread {

        @Override
        public void run() {
            if (setPortSettings()) {
                jButtonOpenClosePort.setVisible(true);
                jComboBoxPortName.setEnabled(false);
            }
        }
    }

    private void appendText(String str) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                insertText(jTextPaneLog, str + "\r\n", normal);
                LOGGER.log(Level.INFO, str);
            }
        });

    }

    private void appendBoldText(String str) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                insertText(jTextPaneLog, str + "\r\n", bold);
                LOGGER.log(Level.WARNING, str);
            }
        });

    }

    /**
     * Процедура формирования стилей редактора
     *
     * @param editor редактор
     */
    private void createStyles(JTextPane editor) {
        // Создание стилей
        normal = editor.addStyle(STYLE_normal, null);
        StyleConstants.setFontFamily(normal, FONT_style);
        StyleConstants.setFontSize(normal, 12);
        // Наследуем свойстdо FontFamily
        bold = editor.addStyle(STYLE_heading, normal);
//        StyleConstants.setFontSize(heading, 12);
        StyleConstants.setBold(bold, true);
        StyleConstants.setForeground(bold, Color.red);
    }

    /**
     * Процедура добавления в редактор строки определенного стиля
     *
     * @param editor редактор
     * @param string строка
     * @param style стиль
     */
    private void insertText(JTextPane editor, String string, Style style) {
        try {
            Document doc = editor.getDocument();
            doc.insertString(doc.getLength(), string, style);
        } catch (BadLocationException ex) {
            javax.swing.JOptionPane.showMessageDialog(this, "Ошибка в insertText(): " + ex.getMessage(), "Ошибка", JOptionPane.ERROR_MESSAGE);
            appendBoldText("Ошибка в insertText(): " + ex.getMessage());
            printStackTraceElements(ex);
        }
    }

    void waitSomeTime(int waitTime) {
        appendText("Жду " + waitTime + " секунд(ы)");
        try {
            Thread.sleep(waitTime * 1000);
        } catch (InterruptedException ex) {
            //Logger.getLogger(Chu_emu.class.getName()).log(Level.SEVERE, null, ex);
            javax.swing.JOptionPane.showMessageDialog(this, "Ошибка в waitSomeTime(): " + ex.getMessage(), "Ошибка", JOptionPane.ERROR_MESSAGE);
            appendBoldText("Ошибка в waitSomeTime(): " + ex.getMessage());
            printStackTraceElements(ex);
        }
    }

    void enableEventListener() throws SerialPortException {
        /*
        Добавляем прослушиватель событий.
        В качестве параметров в методе задаются:
            1) объект типа "SerialPortEventListener" 
        Этот объект должен быть должным образом описан, как он 
        будет отвечать за обработку произошедших событий.
            2) маска событий. чтобы сделать ее нужно 
        использовать переменные с префиксом "MASK_" например 
        "MASK_RXCHAR"
         */
        serialPort.addEventListener(new Reader(), SerialPort.MASK_RXCHAR
                | SerialPort.MASK_RXFLAG
                | SerialPort.MASK_CTS
                | SerialPort.MASK_DSR
                | SerialPort.MASK_RLSD);
    }

    boolean setPortSettings() {
        portName = jComboBoxPortName.getSelectedItem().toString();
        baudRate = SerialPort.BAUDRATE_115200;
        dataBits = SerialPort.DATABITS_8;
        stopBits = SerialPort.STOPBITS_1;
        parity = SerialPort.PARITY_NONE;
        serialPort = new SerialPort(portName); // в переменную serialPort заносим выбраный COM-порт        
        try {
            if (serialPort.openPort()) { // Пытаемся открыть порт, если он открывается, то
                appendText("Порт " + portName + " открыт.");
                if (serialPort.setParams(baudRate, dataBits, stopBits, parity)) { // пытаемся установить параметры порта, если они устанавливаются, то 
                    appendText("Параметры порта установлены.");
                    portIsOpen = true;
                    enableEventListener();

                    //jButtonOpenPort.setText("Close port"); // меняем надпись на кнопке на "Close port"
                    /*
                        Добавляем прослушиватель событий.
                        В качестве параметров в методе задаются:
                            1) объект типа "SerialPortEventListener" 
                        Этот объект должен быть должным образом описан, как он 
                        будет отвечать за обработку произошедших событий.
                            2) маска событий. чтобы сделать ее нужно 
                        использовать переменные с префиксом "MASK_" например 
                        "MASK_RXCHAR"
                     */
//                        serialPort.addEventListener(new Reader(), SerialPort.MASK_RXCHAR |
//                                                                  SerialPort.MASK_RXFLAG |
//                                                                  SerialPort.MASK_CTS |
//                                                                  SerialPort.MASK_DSR |
//                                                                  SerialPort.MASK_RLSD);
                    //enableControls(true);
//                        if(serialPort.isCTS()){
//                            jLabelCTS.setBorder(NimbusGui.borderStatusOn);
//                            jLabelCTS.setBackground(NimbusGui.colorStatusOnBG);
//                        }
//                        else {
//                            jLabelCTS.setBorder(NimbusGui.borderStatusOff);
//                            jLabelCTS.setBackground(NimbusGui.colorStatusOffBG);
//                        }
//                        if(serialPort.isDSR()){
//                            jLabelDSR.setBorder(NimbusGui.borderStatusOn);
//                            jLabelDSR.setBackground(NimbusGui.colorStatusOnBG);
//                        }
//                        else {
//                            jLabelDSR.setBorder(NimbusGui.borderStatusOff);
//                            jLabelDSR.setBackground(NimbusGui.colorStatusOffBG);
//                        }
//                        if(serialPort.isRLSD()){
//                            jLabelRLSD.setBorder(NimbusGui.borderStatusOn);
//                            jLabelRLSD.setBackground(NimbusGui.colorStatusOnBG);
//                        }
//                        else {
//                            jLabelRLSD.setBorder(NimbusGui.borderStatusOff);
//                            jLabelRLSD.setBackground(NimbusGui.colorStatusOffBG);
//                        }
//                        if(serialPort.setRTS(true)){
//                            jToggleButtonRTS.setSelected(true);
//                        }
//                        if(serialPort.setDTR(true)){
//                            jToggleButtonDTR.setSelected(true);
//                        }
                } else {
                    //DialogMessage dialogMessage = new DialogMessage(this, DialogMessage.TYPE_ERROR, "Setting parameters", "Can't set selected parameters.");
                    serialPort.closePort();
                    appendText("Порт " + portName + " закрыт");
                }
            }
        } catch (SerialPortException ex) {
            javax.swing.JOptionPane.showMessageDialog(this, "Ошибка в setPortSettings(): " + ex.getExceptionType(), "Порт: " + ex.getPortName(), JOptionPane.ERROR_MESSAGE);
            appendBoldText("Ошибка в setPortSettings(): " + ex.getMessage());
            printStackTraceElements(ex);
            return false;
        } catch (Exception ex) {
            //DialogMessage dialogMessage = new DialogMessage(this, DialogMessage.TYPE_ERROR, "Writing data", "Error occurred while writing data.");
            javax.swing.JOptionPane.showMessageDialog(this, "Ошибка в setPortSettings(): " + ex.getMessage(), "Ошибка", JOptionPane.ERROR_MESSAGE);
            appendBoldText("Ошибка в setPortSettings(): " + ex.getMessage());
            printStackTraceElements(ex);
            return false;
        }
        return true;
    }

    private void sendString(String str) {
        //String str = jTextFieldOut.getText();
        if (serialPort == null || serialPort.getPortName() == null) {
            appendBoldText("COM порт не установлен.");
            return;
        }
        appendText("Отправлен ответ: \n" + str);
        if (str.length() > 0) {
            try {
//                serialPort.writeBytes(str.getBytes());
                serialPort.writeString(str);
//                str = "";
            } catch (SerialPortException ex) {
                //DialogMessage dialogMessage = new DialogMessage(this, DialogMessage.TYPE_ERROR, "Writing data", "Error occurred while writing data.");
                javax.swing.JOptionPane.showMessageDialog(this, "Ошибка в sendString(): " + ex.getExceptionType(), "Порт: " + ex.getPortName(), JOptionPane.ERROR_MESSAGE);
                appendBoldText("Ошибка в sendString(): " + ex.getMessage());
                printStackTraceElements(ex);
            } catch (Exception ex) {
                //DialogMessage dialogMessage = new DialogMessage(this, DialogMessage.TYPE_ERROR, "Writing data", "Error occurred while writing data.");
                javax.swing.JOptionPane.showMessageDialog(this, "Ошибка в sendString(): " + ex.getMessage(), "Ошибка", JOptionPane.ERROR_MESSAGE);
                appendBoldText("Ошибка в sendString(): " + ex.getMessage());
                printStackTraceElements(ex);
            }
        }
    }

    void closePort() {
        if (serialPort != null && serialPort.isOpened()) {
            try {
                serialPort.closePort();
            } catch (SerialPortException ex) {
                //DialogMessage dialogMessage = new DialogMessage(this, DialogMessage.TYPE_ERROR, "Writing data", "Error occurred while writing data.");
                javax.swing.JOptionPane.showMessageDialog(this, "Ошибка в closePort(): " + ex.getExceptionType(), "Порт: " + ex.getPortName(), JOptionPane.ERROR_MESSAGE);
                appendBoldText("Ошибка в closePort(): " + ex.getMessage());
                printStackTraceElements(ex);
            } catch (Exception ex) {
                //DialogMessage dialogMessage = new DialogMessage(this, DialogMessage.TYPE_ERROR, "Writing data", "Error occurred while writing data.");
                javax.swing.JOptionPane.showMessageDialog(this, "Ошибка в closePort(): " + ex.getMessage(), "Ошибка", JOptionPane.ERROR_MESSAGE);
                appendBoldText("Ошибка в closePort(): " + ex.getMessage());
                printStackTraceElements(ex);
            }
            appendText("Порт " + portName + " закрыт");
        }
        portIsOpen = false;
        jComboBoxPortName.setEnabled(true);
        jButtonOpenClosePort.setVisible(false);
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jFileChooserOpen = new javax.swing.JFileChooser();
        jFileChooserSave = new javax.swing.JFileChooser();
        jDialog1 = new javax.swing.JDialog();
        jPanel1 = new javax.swing.JPanel();
        jLabelPortName = new javax.swing.JLabel();
        jComboBoxPortName = new javax.swing.JComboBox<>(SerialPortList.getPortNames());
        jButtonOpenClosePort = new javax.swing.JButton();
        jLabel3 = new javax.swing.JLabel();
        jScrollPane2 = new javax.swing.JScrollPane();
        jTextPaneLog = new javax.swing.JTextPane();

        jFileChooserOpen.setDialogTitle("Открыть файл");

        jFileChooserSave.setApproveButtonToolTipText("");
        jFileChooserSave.setDialogTitle("Сохранить файл");

        jDialog1.setTitle("Перезаписать существующий файл?");

        javax.swing.GroupLayout jDialog1Layout = new javax.swing.GroupLayout(jDialog1.getContentPane());
        jDialog1.getContentPane().setLayout(jDialog1Layout);
        jDialog1Layout.setHorizontalGroup(
            jDialog1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 400, Short.MAX_VALUE)
        );
        jDialog1Layout.setVerticalGroup(
            jDialog1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 300, Short.MAX_VALUE)
        );

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setTitle("CHU_Emu");
        setLocationByPlatform(true);
        setMinimumSize(new java.awt.Dimension(980, 560));
        setSize(new java.awt.Dimension(960, 540));
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent evt) {
                formWindowClosing(evt);
            }
        });

        jPanel1.setMinimumSize(new java.awt.Dimension(960, 520));
        jPanel1.setPreferredSize(new java.awt.Dimension(960, 520));

        jLabelPortName.setText("COM порт:");

        jComboBoxPortName.setModel(new javax.swing.DefaultComboBoxModel<>(SerialPortList.getPortNames()));
        jComboBoxPortName.setAutoscrolls(true);
        jComboBoxPortName.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jComboBoxPortNameActionPerformed(evt);
            }
        });

        jButtonOpenClosePort.setText("Закрыть порт");
        jButtonOpenClosePort.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButtonOpenClosePortActionPerformed(evt);
            }
        });
        jButtonOpenClosePort.setVisible(false);

        jLabel3.setText("Автор программы Федоренко Александр");

        new SmartScroller(jScrollPane2);

        jTextPaneLog.setEditable(false);
        jScrollPane2.setViewportView(jTextPaneLog);

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel3)
                    .addComponent(jLabelPortName)
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addComponent(jComboBoxPortName, javax.swing.GroupLayout.PREFERRED_SIZE, 62, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jButtonOpenClosePort)))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane2, javax.swing.GroupLayout.DEFAULT_SIZE, 749, Short.MAX_VALUE)
                .addContainerGap())
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addComponent(jScrollPane2)
                        .addContainerGap())
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addComponent(jLabelPortName)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(jComboBoxPortName, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(jButtonOpenClosePort))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 472, Short.MAX_VALUE)
                        .addComponent(jLabel3))))
        );

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jPanel1, javax.swing.GroupLayout.DEFAULT_SIZE, 980, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jPanel1, javax.swing.GroupLayout.DEFAULT_SIZE, 540, Short.MAX_VALUE)
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void formWindowClosing(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowClosing
        // TODO add your handling code here:
        closePort();
    }//GEN-LAST:event_formWindowClosing

    private void jButtonOpenClosePortActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButtonOpenClosePortActionPerformed
        // TODO add your handling code here:
        closePort();
    }//GEN-LAST:event_jButtonOpenClosePortActionPerformed

    private void jComboBoxPortNameActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jComboBoxPortNameActionPerformed
        // TODO add your handling code here:
        SetPortSettingsThread sps = new SetPortSettingsThread();
        sps.start();
        //        if (setPortSettings()) {
        //            jButtonOpenClosePort.setVisible(true);
        //            jComboBoxPortName.setEnabled(false);
        //        }
    }//GEN-LAST:event_jComboBoxPortNameActionPerformed

    private class Reader implements SerialPortEventListener { //Класс Reader реализует интерфейс SerialPortEventListener

        /*
        Метод serialEvent принимает в качестве параметра переменную типа SerialPortEvent
         */
        @Override
        public void serialEvent(SerialPortEvent spe) {
            if (spe.isRXCHAR() || spe.isRXFLAG()) { //Если установлены флаги RXCHAR и  RXFLAG                
                if (spe.getEventValue() > 0) { //Если число байт во входном буффере больше 0, то
                    //jTextAreaIn.append("В буфере есть " + spe.getEventValue() + " символов");
                    try {
                        String newMessage = serialPort.readString();//читаем из COM-порта строку
                        if (newMessage != null) {
                            answerCollector = answerCollector.concat(newMessage); //собираем в одну строку то, что пришло из входного буфера
                        } else {
                            newMessage = "";
                        }
                        appendText(newMessage);//и тут же выводим то что прочитали                        
                        if (answerCollector.contains("settx") && answerCollector.length() > 8) {
                            answerCollector = SETTX;
                        }
                        if (answerCollector.contains("settp") && answerCollector.length() == 15) {
                            answerCollector = SETTP;
                        }
                        appendText("answerCollector: " + answerCollector);
                        switch (answerCollector) {
                            case CLION:
                                if (!cliIsOn) {
                                    sendString("Hytera CHU board CLI v3.0 starts  (Version 5.1.04.008)\n"
                                            + "%>");
                                    cliIsOn = true;
                                    answerCollector = "";
                                    break;
                                }
                                sendString("Bad command input! type \"help\" for help!");
                                answerCollector = "";
                                break;
                            case CLIOFF:
                                if (cliIsOn) {
                                    sendString("Hytera CHU board CLI v5.0 stops");
                                    cliIsOn = false;
                                    cdMgrIsOn = false;
                                    answerCollector = "";
                                    break;
                                }
                                answerCollector = "";
                                break;
                            case CDMGR:
                                if (cdMgrIsOn) {
                                    sendString("Bad command input, type \"cd help\" for help!\n"
                                            + "%>");
                                    answerCollector = "";
                                    break;
                                }
                                sendString("msg		msg [on/off]    ---- print out MGR_Messages\n"
                                        + "gettx		gettx           ---- get the TX frequecy of CHU board\n"
                                        + "settx		settx xxxxxxxx  ---- set the TX frequecy of CHU board\n"
                                        + "getrx		getrx           ---- get the RX frequecy of CHU board\n"
                                        + "setrx		setrx xxxxxxxx  ---- set the RX frequecy of CHU board\n"
                                        + "gettp		gettp           ---- get the TX power of CHU board\n"
                                        + "settp		settp xxxxxxxx  ---- set the TX power of CHU board\n"
                                        + "getfec		getfec          ---- get the VoiceFec of CHU board\n"
                                        + "setfec		setfec [1-2] ---- set the VoiceFec of CHU board 1-SELP 2-AMBE\n"
                                        + "getfecsw	getfecsw          ---- get the AI encrypt switch of CHU board\n"
                                        + "setfecsw	setfecsw [0-1] ---- set the AI encrypt switch of CHU board 0-OFF 1-ON\n"
                                        + "getthreshold	getthreshold  ---- get the threshold of link detection\n"
                                        + "setthreshold	setthreshold [1-100] ---- set the threshold of link detection\n"
                                        + "getdiv		getdiv          ---- get the DivRecv of CHU board!\n"
                                        + "setdiv		setdiv [0-4] ---- set the DivRecv of CHU board!\n"
                                        + "getwmode	getwmode          ---- get the Work Mode of CHU board\n"
                                        + "setwmode	setwmode [0-1] ---- set the Work Mode of CHU board 0-PDT 1-DMR\n"
                                        + "getpainfo	getpainfo          ---- get the Sleep Config of CHU board and Phy channel state\n"
                                        + "setpainfo	setpainfo [0/1][1-60][0/1] ---- set the physic channel config of CHU board\n"
                                        + "getgd		getgd                  ---- get the TX DAgain SKYgain DaDelay SkyDelay of CHU board\n"
                                        + "setgd		setgd xxxx xxxx xx xx  ---- set the TX DAgain SKYgain DaDelay SkyDelay of CHU board\n"
                                        + "getdef		getdef                ---- get the default parameter table of CHU board\n"
                                        + "settcxo	settcxo xxxxxxxx     ---- set the default time adjust parameter of CHU board\n"
                                        + "getdefrssi	getdefrssi        ---- get the 100dB RSSI of CHU board\n"
                                        + "getfbwm	getfbwm              ---- get the Fall Back work mode of CHU board!\n"
                                        + "setfbwm	setfbwm [0-1]        ---- set the Fall Back work mode of CHU board!\n"
                                        + "getcallht	getcallht          ---- get the Terminator frame launch time of CHU board!\n"
                                        + "setcallht	setcallht [0-500][0-500] ---- set the Terminator frame launch time of CHU board!\n"
                                        + "setparam	setparam   ---- move the old parameters to new sites\n"
                                        + "getalarmswitch	getalarmswitch         ---- get the alarm enable switch\n"
                                        + "setalarmswitch	setalarmswitch [0-1]   ---- set the alarm enable switch 0-OFF 1-ON\n"
                                        + "getrssiswitch	getrssiswitch           ---- get the rssi detection enable switch\n"
                                        + "setrssiswitch	setrssiswitch [0-1]     ---- set the rssi detection enable switch 0-OFF 1-ON\n"
                                        + "getrssith	getrssith                   ---- get the rssi detection threshold\n"
                                        + "setrssith	setrssith [-120-0][-120-0][-120-0][-120-0][0-500][0-500][0-500][0-500][0-500]         ---- set the of rssi detection\n"
                                        + "getrssicnt	getrssicnt                 ---- get the rssi count value\n"
                                        + "getrssitmp	getrssitmp                 ---- get the rssi tmp value\n"
                                        + "100dbadjust	100dbadjust               ---- adjust -100db field strength\n"
                                        + "getpwctrl	getpwctrl                   ---- get the power control value\n"
                                        + "setpwctrl	setpwctrl [-120--60][-120--60][0/1] ---- set the power control value\n"
                                        + "getcrctag	getcrctag                   ---- get the crc tag\n"
                                        + "setcrctag	setcrctag [0-1]             ---- set the power control value\n"
                                        + "gettuner	gettuner                     ---- get current tuner paramter\n"
                                        + "enrssireport	enrssireport[0-1]        ---- turn on/off the switch of rssi report\n"
                                        + "settempswitch	settempswitch[0-1]      ---- set the switch of temperature control\n"
                                        + "gettempswitch	gettempswitch           ---- get the switch of temperature control\n"
                                        + "%>");
                                cdMgrIsOn = true;
                                answerCollector = "";
                                break;
                        }
                        if (cliIsOn) {
                            if (cdMgrIsOn) {
                                switch (answerCollector) {
                                    case NOP:
                                        answerCollector = "";
                                        break;
                                    case GETTX:
                                        sendString("%>");
                                        Thread.sleep(100);
                                        sendString("TX frequency(Hz): 16216");
                                        Thread.sleep(100);
                                        sendString("2496\n");
//                                appendText ("Пришла команда gettx нужно что-то делать.");
                                        answerCollector = "";
                                        break;
                                    case GETTP:
//                                appendText ("Пришла команда gettp нужно что-то делать.");
                                        sendString("%>");
                                        Thread.sleep(100);
                                        sendString("TX Power(mW): 10");
                                        Thread.sleep(100);
                                        sendString("00\n");
                                        answerCollector = "";
                                        break;
                                    case GETPAINFO:
//                                appendText ("Пришла команда getpainfo нужно что-то делать.");
                                        if (txEnable) {
                                            sendString("TxCtrl Enable = 1, Protect Time =");
                                            Thread.sleep(100);
                                            sendString(" 3s, Phy Channel Enable = 1\n"
                                                    + "%>");
                                        } else {
                                            sendString("TxCtrl Enable = 1, Protect Time =");
                                            Thread.sleep(100);
                                            sendString(" 3s, Phy Channel Enable = 0\n"
                                                    + "%>");
                                        }
                                        answerCollector = "";
                                        break;
                                    case SETTX:
//                                appendText ("Пришла команда getpainfo нужно что-то делать.");
                                        sendString("%>\n"
                                                + "set success!");
                                        answerCollector = "";
                                        break;
                                    case SETTP:
//                                appendText ("Пришла команда getpainfo нужно что-то делать.");
                                        sendString("%>\n"
                                                + "set success!");
                                        answerCollector = "";
                                        break;
                                    case SETPAINFO031:
//                                appendText ("Пришла команда getpainfo нужно что-то делать.");
                                        sendString("%>\n"
                                                + "set PHYInfo success!");
                                        txEnable = true;
                                        answerCollector = "";
                                        break;
                                    case SETPAINFO130:
//                                appendText ("Пришла команда getpainfo нужно что-то делать.");
                                        sendString("%>\n"
                                                + "set PHYInfo success!");
                                        txEnable = false;
                                        answerCollector = "";
                                        break;

                                }
                            } else {
//                                sendString("Bad command input! type \"help\" for help!");
//                                answerCollector = "";
                            }

                        }
                    } catch (SerialPortException ex) {
                        //DialogMessage dialogMessage = new DialogMessage(this, DialogMessage.TYPE_ERROR, "Writing data", "Error occurred while writing data.");
                        javax.swing.JOptionPane.showMessageDialog(null, "Ошибка в Reader: " + ex.getExceptionType(), "Порт: " + ex.getPortName(), JOptionPane.ERROR_MESSAGE);
                        appendBoldText("Ошибка в Reader: " + ex.getMessage());
                        printStackTraceElements(ex);
                    } catch (Exception ex) {
                        //DialogMessage dialogMessage = new DialogMessage(this, DialogMessage.TYPE_ERROR, "Writing data", "Error occurred while writing data.");
//                        javax.swing.JOptionPane.showMessageDialog(null, "Ошибка в Reader: " + ex.getMessage(), "Ошибка", JOptionPane.ERROR_MESSAGE);
                        appendBoldText("Ошибка в Reader: " + ex.getMessage());
                        printStackTraceElements(ex);
                    }

                }
            }

        }
    }

    private class Reader2 implements SerialPortEventListener { //Класс Reader реализует интерфейс SerialPortEventListener

        /*
        Метод serialEvent принимает в качестве параметра переменную типа SerialPortEvent
         */
        @Override
        public void serialEvent(SerialPortEvent spe) {
            if (spe.isRXCHAR() || spe.isRXFLAG()) { //Если установлены флаги RXCHAR и  RXFLAG                
                if (spe.getEventValue() > 0) {
                    try {
                        //Если число байт во входном буффере больше 0, то
                        String newMessage = serialPort.readString();//читаем из COM-порта строку
                        appendText(newMessage);//и тут же выводим то что прочитали
                        if (waitInvite) {//Если ожидаем приглашения
                            answerCollector(newMessage);
                        } else {//Если не ожидаем приглашения
                            answerCollector = newMessage; //берем строку символов
                        }
                    } catch (SerialPortException ex) {
                        Logger.getLogger(Chu_emu.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            }
        }

        private void answerCollector(String message) {
            answerCollector = answerCollector.concat(message); //собираем в одну строку то, что пришло из входного буфера
        }

        private void analizeAnswer(String answer) {
            if (answer.contains("%>")) {//терминал готов принять следующую команду

            }
            if (answer.contains("success!")) {//команда выполнена

            }

        }
    }

    void printStackTraceElements(Exception ex) {
        StackTraceElement[] stackTraceElements = ex.getStackTrace();
        for (int i = 0; i < stackTraceElements.length; i++) {
            appendBoldText(i + ": " + stackTraceElements[i].toString());
        }
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String args[]) {
        /* Set the Nimbus look and feel */
        //<editor-fold defaultstate="collapsed" desc=" Look and feel setting code (optional) ">
        /* If Nimbus (introduced in Java SE 6) is not available, stay with the default look and feel.
         * For details see http://download.oracle.com/javase/tutorial/uiswing/lookandfeel/plaf.html 
         */
        try {
            for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels()) {
//                jTextAreaIn.append (info);                
                if ("Windows Classic".equals(info.getName())) { //Windows Classic, Windows, CDE/Motif, Nimbus, Metal
                    javax.swing.UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
//            jTextAreaIn.append ();
//            jTextAreaIn.append ("System Look and feel: " + javax.swing.UIManager.getSystemLookAndFeelClassName());
//            javax.swing.UIManager.setLookAndFeel( javax.swing.UIManager.getSystemLookAndFeelClassName());
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | javax.swing.UnsupportedLookAndFeelException ex) {
            javax.swing.JOptionPane.showMessageDialog(null, "Ошибка в main(): " + ex.getMessage(), "Ошибка", JOptionPane.ERROR_MESSAGE);
        }
        //</editor-fold>
        //</editor-fold>

        //</editor-fold>
        //</editor-fold>

        /* Create and display the form */
        java.awt.EventQueue.invokeLater(new Runnable() {
            public void run() {
                new Chu_emu().setVisible(true);
            }
        });
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton jButtonOpenClosePort;
    private javax.swing.JComboBox<String> jComboBoxPortName;
    private javax.swing.JDialog jDialog1;
    private javax.swing.JFileChooser jFileChooserOpen;
    private javax.swing.JFileChooser jFileChooserSave;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabelPortName;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JTextPane jTextPaneLog;
    // End of variables declaration//GEN-END:variables
}
