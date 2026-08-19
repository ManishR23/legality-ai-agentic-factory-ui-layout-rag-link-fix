package com.example.brdagent;

import com.example.legality.runtime.LegalityRule;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class BrdAgentApp extends JFrame {
    private final DefaultListModel<File> sourceFiles=new DefaultListModel<>();
    private final DefaultListModel<File> ragFiles=new DefaultListModel<>();
    private final JTextField workspaceField=new JTextField(Path.of("factory-workspace").toAbsolutePath().toString(),55);
    private final JTextArea factoryPrompt=prompt("Create a United crew legality AI factory. Use source evidence only; extract testable requirements and do not invent legality rules.");
    private final JTextArea codingPrompt=prompt("Generate Java 17. One atomic rule per class. Use only com.example.legality.runtime APIs. All legality thresholds come from RuleDefinition parameters.");
    private final JTextArea schedulePrompt=prompt("Generate 25 United schedules. Bases ORD, DEN, IAH, EWR, SFO, LAX, IAD. Start/end at base, flying and non-flying, pairings at least 2 days with at least 2 flight legs, schedule at least 14 days, include 20% intentionally illegal examples.");
    private final JTextArea requirements=area(),validation=area(),brd=area(),normalized=area(),codeJson=area(),schedulesJson=area(),executionJson=area(),explanation=area(),ragQuestion=prompt("What United FA rest, duty, reserve, pairing, base, flying and non-flying rules are relevant?"),ragDetail=area(),ragEvidence=area();
    private final JTextArea workflow=area();
    private final JLabel status=new JLabel("Ready");
    private final JSpinner maxFixAttempts=new JSpinner(new SpinnerNumberModel(3,1,8,1));
    private final GeneratedJavaManager javaManager=new GeneratedJavaManager();
    private final RuleExecutionService executionService=new RuleExecutionService();
    private final RagKnowledgeBase ragKb=new RagKnowledgeBase();
    private final DefaultTableModel ragModel=new DefaultTableModel(new Object[]{"Score","Source","Section","Tags","Preview"},0){public boolean isCellEditable(int r,int c){return false;}};
    private final JTable ragTable=new JTable(ragModel); private List<RagChunk> ragResults=new ArrayList<>();
    private boolean ragEvidenceLinked=false;
    private final JLabel ragLinkStatus=new JLabel("RAG evidence not linked");
    private final ScheduleViewerPanel scheduleViewer=new ScheduleViewerPanel(); private final ExecutionViewerPanel executionViewer=new ExecutionViewerPanel(); private final CompileHistoryPanel compileHistory=new CompileHistoryPanel();

    public static void main(String[] args){
        try{JacksonRuntimeCheck.assertCompatible();System.out.println(JacksonRuntimeCheck.versionSummary());}
        catch(Exception ex){ex.printStackTrace();JOptionPane.showMessageDialog(null,ex.getMessage(),"Jackson Dependency Error",JOptionPane.ERROR_MESSAGE);return;}
        SwingUtilities.invokeLater(()->new BrdAgentApp().setVisible(true));
    }

    public BrdAgentApp(){super("Legality AI Agentic Factory - FA RAG + Auto Compile Fix");setDefaultCloseOperation(EXIT_ON_CLOSE);setSize(1500,1000);setLocationRelativeTo(null);buildUi();}

    private void buildUi(){
        JPanel root=new JPanel(new BorderLayout(8,8));root.setBorder(new EmptyBorder(8,8,8,8));setContentPane(root);
        JLabel title=new JLabel("Legality AI Agentic Factory");title.setFont(title.getFont().deriveFont(Font.BOLD,22f));
        JPanel header=new JPanel(new BorderLayout(5,5));
        JPanel ws=new JPanel(new FlowLayout(FlowLayout.LEFT));JButton browse=new JButton("Choose Workspace");
        ws.add(new JLabel("Workspace:"));ws.add(workspaceField);ws.add(browse);ws.add(Box.createHorizontalStrut(12));ws.add(ragLinkStatus);
        header.add(title,BorderLayout.NORTH);header.add(ws,BorderLayout.SOUTH);root.add(header,BorderLayout.NORTH);

        JList<File> files=new JList<>(sourceFiles);JPanel sourcePanel=new JPanel(new BorderLayout(4,4));sourcePanel.setBorder(BorderFactory.createTitledBorder("Factory Source Files"));sourcePanel.add(new JScrollPane(files),BorderLayout.CENTER);JPanel sb=new JPanel(new FlowLayout(FlowLayout.LEFT));JButton add=new JButton("Add Files"),remove=new JButton("Remove"),clear=new JButton("Clear");sb.add(add);sb.add(remove);sb.add(clear);sourcePanel.add(sb,BorderLayout.SOUTH);
        JTabbedPane prompts=new JTabbedPane();
        JScrollPane factoryPromptScroll=new JScrollPane(factoryPrompt);factoryPromptScroll.setPreferredSize(new Dimension(800,150));
        JScrollPane codingPromptScroll=new JScrollPane(codingPrompt);codingPromptScroll.setPreferredSize(new Dimension(800,150));
        JScrollPane schedulePromptScroll=new JScrollPane(schedulePrompt);schedulePromptScroll.setPreferredSize(new Dimension(800,150));
        prompts.addTab("Factory Prompt",factoryPromptScroll);prompts.addTab("Rule Coding Prompt",codingPromptScroll);prompts.addTab("Test Schedule Prompt",schedulePromptScroll);
        JSplitPane topSplit=new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,sourcePanel,prompts);topSplit.setResizeWeight(.28);topSplit.setDividerLocation(360);topSplit.setMinimumSize(new Dimension(600,170));

        workflow.setText("0 FA RAG -> 1 Requirements -> 2 Validation -> 3 BRD -> 4 Normalize -> 5 Code -> Materialize -> 6 Compile/Fix -> 7 Schedules -> 8 Execute -> 9 Explain\n\nRun agents individually or run 1-5 together. Compile + Auto Fix retries compilation without changing legality semantics.");
        JTabbedPane tabs=new JTabbedPane();tabs.addTab("0 FA RAG",buildRagPanel());tabs.addTab("Workflow",new JScrollPane(workflow));tabs.addTab("1 Requirements",new JScrollPane(requirements));tabs.addTab("2 Validation",new JScrollPane(validation));tabs.addTab("3 BRD",new JScrollPane(brd));tabs.addTab("4 Normalized Rules",new JScrollPane(normalized));tabs.addTab("5 Rule Code JSON",new JScrollPane(codeJson));tabs.addTab("6 Compile / Fix",compileHistory);tabs.addTab("7 Schedule JSON",new JScrollPane(schedulesJson));tabs.addTab("7A Schedule Table",scheduleViewer);tabs.addTab("8 Execution JSON",new JScrollPane(executionJson));tabs.addTab("8A Violation Table",executionViewer);tabs.addTab("9 Grounded Explanation",new JScrollPane(explanation));
        JSplitPane mainVertical=new JSplitPane(JSplitPane.VERTICAL_SPLIT,topSplit,tabs);mainVertical.setResizeWeight(0.0);mainVertical.setDividerLocation(210);mainVertical.setOneTouchExpandable(true);root.add(mainVertical,BorderLayout.CENTER);

        JPanel buttons=new JPanel(new FlowLayout(FlowLayout.LEFT));String[] names={"Run 1-5","1 Requirements","2 Validate","3 BRD","4 Normalize","5 Code","Materialize Java","6 Compile","Auto Fix Compile","Compile + Auto Fix","7 Generate Schedules","8 Execute Rules","Save Artifacts"};List<JButton> bs=new ArrayList<>();for(String n:names){JButton b=new JButton(n);buttons.add(b);bs.add(b);}buttons.add(new JLabel("Max Fix:"));buttons.add(maxFixAttempts);JPanel bottom=new JPanel(new BorderLayout());bottom.add(buttons,BorderLayout.WEST);bottom.add(status,BorderLayout.CENTER);root.add(bottom,BorderLayout.SOUTH);

        browse.addActionListener(e->chooseWorkspace());add.addActionListener(e->addFiles(sourceFiles));remove.addActionListener(e->files.getSelectedValuesList().forEach(sourceFiles::removeElement));clear.addActionListener(e->sourceFiles.clear());
        bs.get(0).addActionListener(e->runAll15());bs.get(1).addActionListener(e->runRequirements());bs.get(2).addActionListener(e->runValidation());bs.get(3).addActionListener(e->runBrd());bs.get(4).addActionListener(e->runNormalize());bs.get(5).addActionListener(e->runCode());bs.get(6).addActionListener(e->materialize());bs.get(7).addActionListener(e->compileOnly());bs.get(8).addActionListener(e->autoFixOnce());bs.get(9).addActionListener(e->compileAutoFix());bs.get(10).addActionListener(e->generateSchedules());bs.get(11).addActionListener(e->executeRules());bs.get(12).addActionListener(e->saveArtifacts());
        scheduleViewer.setExplanationHandler(this::explainSelected);executionViewer.setExplanationHandler(this::explainSelected);
    }

    private JPanel buildRagPanel(){
        JPanel root=new JPanel(new BorderLayout(6,6));JList<File> list=new JList<>(ragFiles);JPanel left=new JPanel(new BorderLayout(4,4));left.setPreferredSize(new Dimension(330,600));left.setBorder(BorderFactory.createTitledBorder("United FA / FAR / CBA Docs"));left.add(new JScrollPane(list),BorderLayout.CENTER);JPanel lb=new JPanel(new GridLayout(0,1,4,4));JButton add=new JButton("Add FA Docs"),build=new JButton("Build KB"),search=new JButton("Search KB"),evidenceBtn=new JButton("Create Evidence Package"),use=new JButton("Use Evidence in Factory Prompt"),save=new JButton("Save KB"),load=new JButton("Load KB");for(JButton b:List.of(add,build,search,evidenceBtn,use,save,load))lb.add(b);left.add(lb,BorderLayout.SOUTH);
        JPanel center=new JPanel(new BorderLayout(4,4));ragQuestion.setBorder(BorderFactory.createTitledBorder("RAG Question"));JScrollPane ragQuestionScroll=new JScrollPane(ragQuestion);ragQuestionScroll.setPreferredSize(new Dimension(700,95));center.add(ragQuestionScroll,BorderLayout.NORTH);ragTable.setRowHeight(23);ragTable.setAutoCreateRowSorter(true);center.add(new JScrollPane(ragTable),BorderLayout.CENTER);JSplitPane lower=new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,new JScrollPane(ragDetail),new JScrollPane(ragEvidence));lower.setResizeWeight(.5);lower.setPreferredSize(new Dimension(900,210));center.add(lower,BorderLayout.SOUTH);root.add(left,BorderLayout.WEST);root.add(center,BorderLayout.CENTER);
        add.addActionListener(e->addFiles(ragFiles));build.addActionListener(e->buildKb());search.addActionListener(e->searchKb());evidenceBtn.addActionListener(e->createEvidence());use.addActionListener(e->useEvidence());save.addActionListener(e->saveKb());load.addActionListener(e->loadKb());ragTable.getSelectionModel().addListSelectionListener(e->showRagSelected());return root;
    }

    private void buildKb(){runAsync("Building FA knowledge base...",()->{if(ragFiles.isEmpty())throw new IllegalStateException("Add FA source docs first");ragKb.clear();DocumentExtractor ex=new DocumentExtractor();for(int i=0;i<ragFiles.size();i++){SourceDocument d=ex.extract(ragFiles.get(i));ragKb.addDocument(d.fileName(),d.content());}Path p=workspace().resolve("fa-rag/fa-knowledge-base.json");ragKb.save(p);return "KB built: "+ragKb.size()+" chunks";});}
    private void searchKb(){ragResults=ragKb.search(ragQuestion.getText(),12);ragModel.setRowCount(0);for(RagChunk c:ragResults)ragModel.addRow(new Object[]{String.format(Locale.ROOT,"%.2f",c.score),c.source,c.section,String.join(",",c.tags),preview(c.text)});if(!ragResults.isEmpty())ragTable.setRowSelectionInterval(0,0);status.setText("Retrieved "+ragResults.size()+" chunks");}
    private void createEvidence(){if(ragResults.isEmpty())searchKb();ragEvidence.setText(ragKb.evidencePackage(ragQuestion.getText(),ragResults));}
    private void useEvidence(){
        if(ragEvidence.getText().isBlank())createEvidence();
        if(ragEvidence.getText().isBlank()){status.setText("No RAG evidence available");return;}
        ragEvidenceLinked=true;
        ragLinkStatus.setText("RAG evidence LINKED ("+ragResults.size()+" chunks)");
        ragLinkStatus.setForeground(new Color(0,128,0));
        status.setText("RAG evidence linked to Requirements Agent. Factory Prompt remains unchanged.");
    }
    private void showRagSelected(){int r=ragTable.getSelectedRow();if(r<0)return;int i=ragTable.convertRowIndexToModel(r);if(i<ragResults.size()){RagChunk c=ragResults.get(i);ragDetail.setText("Source: "+c.source+"\nSection: "+c.section+"\nTags: "+String.join(", ",c.tags)+"\nScore: "+c.score+"\n\n"+c.text);}}
    private void saveKb(){runAsync("Saving KB...",()->{Path p=workspace().resolve("fa-rag/fa-knowledge-base.json");ragKb.save(p);return "Saved "+p;});}
    private void loadKb(){JFileChooser ch=new JFileChooser(workspaceField.getText());if(ch.showOpenDialog(this)==JFileChooser.APPROVE_OPTION)runAsync("Loading KB...",()->{ragKb.load(ch.getSelectedFile().toPath());return "Loaded "+ragKb.size()+" chunks";});}

    private String combinedFactoryPrompt(){
        String base=factoryPrompt.getText()==null?"":factoryPrompt.getText();
        if(!ragEvidenceLinked || ragEvidence.getText()==null || ragEvidence.getText().isBlank())return base;
        return base+"\n\n===== RETRIEVED UNITED FA EVIDENCE - USE AS SOURCE EVIDENCE ONLY =====\n"+
                ragEvidence.getText()+
                "\n===== END RETRIEVED EVIDENCE =====\n"+
                "The RAG evidence is retrieval evidence only. Do not invent rules beyond the supplied evidence. Final legality execution is determined only by the deterministic legality engine.";
    }

    private void runRequirements(){runAsync("Running Requirements Agent...",()->{String out=agent().generateRequirementsJson(docs(),combinedFactoryPrompt());requirements.setText(out);return "Requirements complete";});}
    private void runValidation(){runAsync("Running Validation Agent...",()->{require(requirements,"Run Requirements first");validation.setText(agent().validateRulesAndConfidence(requirements.getText(),combinedFactoryPrompt()));return "Validation complete";});}
    private void runBrd(){runAsync("Running BRD Agent...",()->{require(requirements,"Need requirements");require(validation,"Need validation");brd.setText(agent().generateBrdFromArtifacts(requirements.getText(),validation.getText(),combinedFactoryPrompt()));return "BRD complete";});}
    private void runNormalize(){runAsync("Running Normalization Agent...",()->{require(brd,"Need BRD");normalized.setText(agent().normalizeRulesFromBrd(brd.getText(),requirements.getText(),validation.getText(),combinedFactoryPrompt()));return "Normalization complete";});}
    private void runCode(){runAsync("Running Rule Coding Agent...",()->{require(normalized,"Need normalized rules");codeJson.setText(agent().generateRuleCodeFromCatalog(brd.getText(),normalized.getText(),codingPrompt.getText()));return "Rule code generated";});}
    private void runAll15(){runAsync("Running agents 1-5...",()->{OpenAiBrdAgent a=agent();String r=a.generateRequirementsJson(docs(),combinedFactoryPrompt());requirements.setText(r);publish("Requirements ready");String v=a.validateRulesAndConfidence(r,combinedFactoryPrompt());validation.setText(v);publish("Validation ready");String b=a.generateBrdFromArtifacts(r,v,combinedFactoryPrompt());brd.setText(b);publish("BRD ready");String n=a.normalizeRulesFromBrd(b,r,v,combinedFactoryPrompt());normalized.setText(n);publish("Normalized rules ready");codeJson.setText(a.generateRuleCodeFromCatalog(b,n,codingPrompt.getText()));return "Agents 1-5 complete";});}
    private void materialize(){runAsync("Materializing Java...",()->{require(codeJson,"Need Rule Code JSON");int n=javaManager.materializeRuleCodingJson(workspace(),codeJson.getText());return "Materialized "+n+" Java files";});}
    private void compileOnly(){runAsync("Compiling generated rules...",()->{GeneratedJavaManager.CompileResult r=javaManager.compileGeneratedRules(workspace());int errors=countErrors(r.diagnostics());compileHistory.addAttempt(1,errors,0,r.success()?"PASSED":"FAILED",r.diagnostics());if(!r.success())throw new IllegalStateException(r.diagnostics());return "Compile passed";});}
    private void autoFixOnce(){runAsync("Running Compile Fix Agent...",()->{GeneratedJavaManager.CompileResult r=javaManager.compileGeneratedRules(workspace());if(r.success())return "Already compiles";String fix=agent().fixCompileErrors(r.diagnostics(),javaManager.sourceBundle(workspace()),normalized.getText());int changed=javaManager.applyCompileFixJson(workspace(),fix);compileHistory.setDetail("COMPILER ERRORS\n"+r.diagnostics()+"\n\nFIX AGENT OUTPUT\n"+fix);return "Compile Fix Agent changed "+changed+" file(s). Recompile next.";});}
    private void compileAutoFix(){runAsync("Compile + Auto Fix...",()->{compileHistory.clearHistory();int max=(Integer)maxFixAttempts.getValue();for(int attempt=1;attempt<=max;attempt++){GeneratedJavaManager.CompileResult r=javaManager.compileGeneratedRules(workspace());int errors=countErrors(r.diagnostics());if(r.success()){compileHistory.addAttempt(attempt,0,0,"PASSED",r.diagnostics());return "Compile passed on attempt "+attempt;}String fix=agent().fixCompileErrors(r.diagnostics(),javaManager.sourceBundle(workspace()),normalized.getText());int changed=javaManager.applyCompileFixJson(workspace(),fix);compileHistory.addAttempt(attempt,errors,changed,changed>0?"FIXED / RETRY":"HUMAN REVIEW",r.diagnostics()+"\n\nFIX AGENT OUTPUT\n"+fix);if(changed==0)return "Compile Fix Agent requires human review";}GeneratedJavaManager.CompileResult finalR=javaManager.compileGeneratedRules(workspace());if(finalR.success()){compileHistory.addAttempt(max+1,0,0,"PASSED",finalR.diagnostics());return "Compile passed after auto-fix";}throw new IllegalStateException("Still failing after "+max+" fix attempts. See Compile / Fix tab.");});}
    private void generateSchedules(){runAsync("Generating schedules...",()->{require(normalized,"Need normalized rules");String out=agent().generateTestSchedules(schedulePrompt.getText(),normalized.getText(),brd.getText());schedulesJson.setText(out);scheduleViewer.loadJson(out);Files.writeString(workspace().resolve("test-schedules.json"),out);return "Schedules generated";});}
    private void executeRules(){runAsync("Executing compiled rules...",()->{require(normalized,"Need normalized rules");require(schedulesJson,"Need schedules");List<LegalityRule> rules=javaManager.loadCompiledRules(workspace());String out=executionService.execute(normalized.getText(),schedulesJson.getText(),rules);executionJson.setText(out);executionViewer.loadJson(out);Files.writeString(workspace().resolve("execution-results.json"),out);return "Execution complete. Loaded "+rules.size()+" rules";});}
    private void explainSelected(String type,String json,String formatted){runAsync("Generating grounded explanation...",()->{String out=agent().explainWithGroundedData(type,json,formatted,normalized.getText(),executionJson.getText());explanation.setText(out);return "Explanation complete";});}

    private List<SourceDocument> docs()throws Exception{List<SourceDocument> out=new ArrayList<>();DocumentExtractor ex=new DocumentExtractor();for(int i=0;i<sourceFiles.size();i++)out.add(ex.extract(sourceFiles.get(i)));if(out.isEmpty()&&!ragEvidence.getText().isBlank())out.add(new SourceDocument("United_FA_RAG_Evidence.md",ragEvidence.getText()));if(out.isEmpty())throw new IllegalStateException("Add source files or create FA RAG evidence first");return out;}
    private OpenAiBrdAgent agent(){EnvConfig e=new EnvConfig();return new OpenAiBrdAgent(e.openAiKey(),e.model());}
    private Path workspace()throws Exception{Path p=Path.of(workspaceField.getText().trim());Files.createDirectories(p);return p;}
    private void chooseWorkspace(){JFileChooser ch=new JFileChooser();ch.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);if(ch.showOpenDialog(this)==JFileChooser.APPROVE_OPTION)workspaceField.setText(ch.getSelectedFile().getAbsolutePath());}
    private void addFiles(DefaultListModel<File> model){JFileChooser ch=new JFileChooser();ch.setMultiSelectionEnabled(true);if(ch.showOpenDialog(this)==JFileChooser.APPROVE_OPTION)for(File f:ch.getSelectedFiles())if(!model.contains(f))model.addElement(f);}
    private void saveArtifacts(){runAsync("Saving artifacts...",()->{Path w=workspace();String t=LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));save(w,"01-requirements-"+t+".json",requirements);save(w,"02-validation-"+t+".json",validation);save(w,"03-brd-"+t+".md",brd);save(w,"04-normalized-"+t+".json",normalized);save(w,"05-rule-code-"+t+".json",codeJson);save(w,"07-schedules-"+t+".json",schedulesJson);save(w,"08-execution-"+t+".json",executionJson);save(w,"09-explanation-"+t+".txt",explanation);return "Artifacts saved to "+w;});}
    private void save(Path w,String name,JTextArea a)throws Exception{if(!a.getText().isBlank())Files.writeString(w.resolve(name),a.getText());}
    private void require(JTextArea a,String msg){if(a.getText()==null||a.getText().isBlank())throw new IllegalStateException(msg);}
    private int countErrors(String s){if(s==null||s.isBlank())return 0;int n=0;for(String l:s.split("\\R"))if(l.contains("[ERROR]"))n++;return n==0?1:n;}
    private String preview(String s){if(s==null)return"";String x=s.replaceAll("\\s+"," ").trim();return x.length()>130?x.substring(0,130)+"...":x;}
    private static JTextArea area(){JTextArea a=new JTextArea();a.setFont(new Font(Font.MONOSPACED,Font.PLAIN,13));a.setLineWrap(true);a.setWrapStyleWord(true);return a;}
    private static JTextArea prompt(String text){JTextArea a=area();a.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,14));a.setText(text);return a;}
    private void runAsync(String start,Task task){status.setText(start);workflow.append("\n"+start);new SwingWorker<String,Void>(){protected String doInBackground()throws Exception{return task.run();}protected void done(){try{String m=get();status.setText(m);workflow.append("\n"+m);}catch(Exception ex){Throwable t=ex;while(t.getCause()!=null)t=t.getCause();String m=t.getMessage()==null?t.toString():t.getMessage();status.setText("Failed");workflow.append("\nERROR: "+m);JOptionPane.showMessageDialog(BrdAgentApp.this,m,"Failed",JOptionPane.ERROR_MESSAGE);}}}.execute();}
    private void publish(String m){SwingUtilities.invokeLater(()->{status.setText(m);workflow.append("\n"+m);});}
    private interface Task{String run()throws Exception;}
}
