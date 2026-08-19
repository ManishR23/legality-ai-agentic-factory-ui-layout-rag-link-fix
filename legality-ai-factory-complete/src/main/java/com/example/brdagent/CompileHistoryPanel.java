package com.example.brdagent;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class CompileHistoryPanel extends JPanel {
    private final DefaultTableModel model=new DefaultTableModel(new Object[]{"Attempt","Errors","Files Changed","Result"},0){public boolean isCellEditable(int r,int c){return false;}}; private final JTable table=new JTable(model); private final JTextArea detail=new JTextArea(); private final List<String> details=new ArrayList<>();
    public CompileHistoryPanel(){super(new BorderLayout(5,5));table.setRowHeight(24);detail.setFont(new Font(Font.MONOSPACED,Font.PLAIN,13));detail.setLineWrap(true);detail.setWrapStyleWord(true);JSplitPane split=new JSplitPane(JSplitPane.VERTICAL_SPLIT,new JScrollPane(table),new JScrollPane(detail));split.setResizeWeight(.4);add(split,BorderLayout.CENTER);table.getSelectionModel().addListSelectionListener(e->{int r=table.getSelectedRow();if(r>=0){int i=table.convertRowIndexToModel(r);if(i<details.size())detail.setText(details.get(i));}});}
    public void clearHistory(){model.setRowCount(0);details.clear();detail.setText("");}
    public void addAttempt(int attempt,int errors,int changed,String result,String text){SwingUtilities.invokeLater(()->{model.addRow(new Object[]{attempt,errors,changed,result});details.add(text);if(model.getRowCount()==1)table.setRowSelectionInterval(0,0);});}
    public void setDetail(String text){SwingUtilities.invokeLater(()->detail.setText(text));}
}
