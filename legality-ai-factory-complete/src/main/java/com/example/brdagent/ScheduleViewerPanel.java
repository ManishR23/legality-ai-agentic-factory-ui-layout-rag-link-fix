package com.example.brdagent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class ScheduleViewerPanel extends JPanel {
    public interface ExplanationHandler { void explain(String type,String json,String formatted); }
    private final ObjectMapper mapper=new ObjectMapper();
    private final DefaultTableModel model=new DefaultTableModel(new Object[]{"Schedule","Crew","Type","Base","Start","End","Pairings","Flights","Non-Flying","Expected Violations"},0){public boolean isCellEditable(int r,int c){return false;}};
    private final JTable table=new JTable(model); private final JTextArea detail=new JTextArea(); private final List<JsonNode> schedules=new ArrayList<>(); private ExplanationHandler handler;
    public ScheduleViewerPanel(){super(new BorderLayout(5,5)); table.setAutoCreateRowSorter(true); table.setRowHeight(24); detail.setFont(new Font(Font.MONOSPACED,Font.PLAIN,13)); detail.setLineWrap(true); detail.setWrapStyleWord(true); JButton explain=new JButton("LLM Explain Selected Schedule"); JPanel top=new JPanel(new FlowLayout(FlowLayout.LEFT)); top.add(new JLabel("Click a schedule to view pairings, duties, flights and non-flying detail.")); top.add(explain); add(top,BorderLayout.NORTH); JSplitPane split=new JSplitPane(JSplitPane.VERTICAL_SPLIT,new JScrollPane(table),new JScrollPane(detail)); split.setResizeWeight(.45); add(split,BorderLayout.CENTER); table.getSelectionModel().addListSelectionListener(e->showSelected()); explain.addActionListener(e->{JsonNode s=selected(); if(s!=null&&handler!=null)handler.explain("schedule",s.toPrettyString(),detail.getText());});}
    public void setExplanationHandler(ExplanationHandler h){handler=h;}
    public void loadJson(String json)throws Exception{model.setRowCount(0);schedules.clear();JsonNode arr=mapper.readTree(json).path("schedules");if(!arr.isArray())throw new IllegalArgumentException("schedules array missing");for(JsonNode s:arr){schedules.add(s);model.addRow(new Object[]{JsonUiHelper.text(s,"scheduleId","UNKNOWN"),JsonUiHelper.text(s,"crewId","UNKNOWN"),JsonUiHelper.text(s,"crewType","UNKNOWN"),JsonUiHelper.text(s,"base","UNKNOWN"),JsonUiHelper.text(s,"scheduleStart",""),JsonUiHelper.text(s,"scheduleEnd",""),JsonUiHelper.size(s.path("pairings")),JsonUiHelper.flightCount(s),JsonUiHelper.nonFlyingCount(s),JsonUiHelper.size(s.path("expectedViolations"))});}if(model.getRowCount()>0)table.setRowSelectionInterval(0,0);}
    private JsonNode selected(){int r=table.getSelectedRow();if(r<0)return null;int i=table.convertRowIndexToModel(r);return i>=0&&i<schedules.size()?schedules.get(i):null;}
    private void showSelected(){JsonNode s=selected();if(s!=null)detail.setText(JsonUiHelper.scheduleDetail(s));}
}
