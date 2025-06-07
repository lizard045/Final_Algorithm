import java.io.*;
import java.util.*;

/**
 * DAG類別：管理任務依賴關係
 * 負責解析DAG檔案和管理任務圖結構
 */
public class DAG {
    private int processorCount;
    private int taskCount;
    private int edgeCount;
    private double[][] communicationRates; // 處理器間通訊成本
    private List<Task> tasks; // 任務列表
    private boolean isHomogeneous; // 是否為同質性系統
    
    public DAG() {
        this.tasks = new ArrayList<>();
    }
    
    /**
     * 從DAG檔案載入數據
     */
    public void loadFromFile(String filename) throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(filename));
        
        // 判斷是否為同質性系統
        this.isHomogeneous = filename.contains("n4_00");
        
        try {
            // 讀取所有有效數據行（跳過註解）
            List<String> dataLines = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                
                // 跳過註解行、空行和包含中文字符的行
                if (line.isEmpty() || line.startsWith("/*") || line.startsWith("*/") || 
                    line.contains("/*") || line.contains("===") || containsNonAscii(line)) {
                    continue;
                }
                
                dataLines.add(line);
            }
            
            // 解析數據
            parseDataLines(dataLines);
            
        } finally {
            reader.close();
        }
    }
    
    /**
     * 檢查字符串是否包含非ASCII字符
     */
    private boolean containsNonAscii(String str) {
        for (char c : str.toCharArray()) {
            if (c > 127) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 解析數據行
     */
    private void parseDataLines(List<String> dataLines) {
        int lineIndex = 0;
        
        // 解析基本參數
        processorCount = Integer.parseInt(dataLines.get(lineIndex++));
        taskCount = Integer.parseInt(dataLines.get(lineIndex++));
        edgeCount = Integer.parseInt(dataLines.get(lineIndex++));
        
        // 初始化數據結構
        communicationRates = new double[processorCount][processorCount];
        initializeTasks();
        
        // 解析通訊成本矩陣
        for (int i = 0; i < processorCount; i++) {
            String[] values = dataLines.get(lineIndex++).split("\\s+");
            for (int j = 0; j < processorCount; j++) {
                communicationRates[i][j] = Double.parseDouble(values[j]);
            }
        }
        
        // 解析計算成本矩陣
        for (int i = 0; i < taskCount; i++) {
            String[] values = dataLines.get(lineIndex++).split("\\s+");
            Task task = tasks.get(i);
            for (int j = 0; j < processorCount; j++) {
                task.setComputationCost(j, Double.parseDouble(values[j]));
            }
        }
        
        // 解析任務依賴關係
        while (lineIndex < dataLines.size()) {
            String[] parts = dataLines.get(lineIndex++).split("\\s+");
            int fromTask = Integer.parseInt(parts[0]);
            int toTask = Integer.parseInt(parts[1]);
            int dataVolume = Integer.parseInt(parts[2]);
            
            // 建立任務依賴關係
            if (fromTask < taskCount && toTask < taskCount) {
                tasks.get(fromTask).addSuccessor(toTask);
                tasks.get(toTask).addPredecessor(fromTask);
                
                // 設定數據傳輸量
                if (dataVolume > 0) {
                    tasks.get(fromTask).setDataTransferVolume(toTask, dataVolume);
                }
            }
        }
    }
    
    private void initializeTasks() {
        for (int i = 0; i < taskCount; i++) {
            tasks.add(new Task(i, processorCount));
        }
    }
    

    
    /**
     * 計算兩個任務間的通訊成本
     */
    public double getCommunicationCost(int fromTask, int toTask, int fromProcessor, int toProcessor) {
        if (fromProcessor == toProcessor) {
            return 0.0; // 同一處理器上無通訊成本
        }
        
        Task task = tasks.get(fromTask);
        int dataVolume = task.getDataTransferVolume(toTask);
        double commRate = communicationRates[fromProcessor][toProcessor];
        
        return dataVolume * commRate;
    }
    
    /**
     * 獲取拓撲排序結果
     */
    public List<Integer> getTopologicalOrder() {
        List<Integer> result = new ArrayList<>();
        boolean[] visited = new boolean[taskCount];
        
        for (int i = 0; i < taskCount; i++) {
            if (!visited[i]) {
                dfsTopological(i, visited, result);
            }
        }
        
        Collections.reverse(result);
        return result;
    }
    
    private void dfsTopological(int taskId, boolean[] visited, List<Integer> result) {
        visited[taskId] = true;
        
        for (int successor : tasks.get(taskId).getSuccessors()) {
            if (!visited[successor]) {
                dfsTopological(successor, visited, result);
            }
        }
        
        result.add(taskId);
    }
    
    // Getters
    public int getProcessorCount() { return processorCount; }
    public int getTaskCount() { return taskCount; }
    public int getEdgeCount() { return edgeCount; }
    public List<Task> getTasks() { return tasks; }
    public Task getTask(int taskId) { return tasks.get(taskId); }
    public boolean isHomogeneous() { return isHomogeneous; }
    public double[][] getCommunicationRates() { return communicationRates; }
    
    @Override
    public String toString() {
        return String.format("DAG{processors=%d, tasks=%d, edges=%d, homogeneous=%s}", 
                           processorCount, taskCount, edgeCount, isHomogeneous);
    }
} 