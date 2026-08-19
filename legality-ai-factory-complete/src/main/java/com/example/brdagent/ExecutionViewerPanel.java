package com.example.brdagent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class ExecutionViewerPanel extends JPanel {
    private final ObjectMapper mapper=new ObjectMapper(); private final DefaultTableModel model=new DefaultTableModel(new Object[]{"Schedule","Crew","Base","Evaluations","Violations","Needs Review","Errors","Status"},0){public boolean isCellEditable(int r,int c){return false;}}; private final JTable table=new JTable(model); private final JTextArea detail=new JTextArea(); private final List<JsonNode> results=new ArrayList<>(); private ScheduleViewerPanel.ExplanationHandler handler;
    public ExecutionViewerPanel(){super(new BorderLayout(5,5));table.setAutoCreateRowSorter(true);table.setRowHeight(24);detail.setFont(new Font(Font.MONOSPACED,Font.PLAIN,13));detail.setLineWrap(true);detail.setWrapStyleWord(true);JButton explain=new JButton("LLM Explain Selected Result");JPanel top=new JPanel(new FlowLayout(FlowLayout.LEFT));top.add(new JLabel("Click a schedule to view violation detail."));top.add(explain);add(top,BorderLayout.NORTH);JSplitPane split=new JSplitPane(JSplitPane.VERTICAL_SPLIT,new JScrollPane(table),new JScrollPane(detail));split.setResizeWeight(.45);add(split,BorderLayout.CENTER);table.getSelectionModel().addListSelectionListener(e->showSelected());explain.addActionListener(e->{JsonNode s=selected();if(s!=null&&handler!=null)handler.explain("executionResult",s.toPrettyString(),detail.getText());});}
    public void setExplanationHandler(ScheduleViewerPanel.ExplanationHandler h){handler=h;}
    public void loadJson(String json)throws Exception{model.setRowCount(0);results.clear();JsonNode arr=mapper.readTree(json).path("scheduleResults");if(!arr.isArray())throw new IllegalArgumentException("scheduleResults array missing");for(JsonNode sr:arr){results.add(sr);int v=0,n=0,e=0;for(JsonNode r:sr.path("results")){if(r.path("violation").asBoolean())v++;String status=JsonUiHelper.text(r,"status","");if("NEEDS_REVIEW".equalsIgnoreCase(status))n++;if("ERROR".equalsIgnoreCase(status))e++;}model.addRow(new Object[]{JsonUiHelper.text(sr,"scheduleId","UNKNOWN"),JsonUiHelper.text(sr,"crewId","UNKNOWN"),JsonUiHelper.text(sr,"base","UNKNOWN"),JsonUiHelper.size(sr.path("results")),v,n,e,v>0?"VIOLATIONS":e>0?"ERRORS":n>0?"REVIEW":"PASS"});}if(model.getRowCount()>0)table.setRowSelectionInterval(0,0);}
    private JsonNode selected(){int r=table.getSelectedRow();if(r<0)return null;int i=table.convertRowIndexToModel(r);return i>=0&&i<results.size()?results.get(i):null;}
    private void showSelected(){JsonNode s=selected();if(s!=null)detail.setText(JsonUiHelper.executionDetail(s));}
}
