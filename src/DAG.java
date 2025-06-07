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
    private List<Task> rankedTasksCache = null; // 快取欄位
    private boolean[][] reachabilityMatrix; // **NEW**: 可達性矩陣
    private double[][] octCache = null; // **NEW**: OCT 快取欄位
    
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
            // **NEW**: 計算可達性
            computeReachability();
            
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
     * **NEW**: 計算可達性矩陣 (Transitive Closure)
     * 使用 Floyd-Warshall 演算法來判斷任務間的依賴關係
     */
    private void computeReachability() {
        reachabilityMatrix = new boolean[taskCount][taskCount];
        // 初始化：直接相連的任務是可達的
        for (int i = 0; i < taskCount; i++) {
            for (int successorId : tasks.get(i).getSuccessors()) {
                reachabilityMatrix[i][successorId] = true;
            }
        }
        // Floyd-Warshall 演算法
        for (int k = 0; k < taskCount; k++) {
            for (int i = 0; i < taskCount; i++) {
                for (int j = 0; j < taskCount; j++) {
                    if (reachabilityMatrix[i][k] && reachabilityMatrix[k][j]) {
                        reachabilityMatrix[i][j] = true;
                    }
                }
            }
        }
    }
    
    /**
     * **NEW**: 檢查一個任務是否是另一個任務的後繼（無論直接或間接）
     */
    public boolean isReachable(int fromTaskId, int toTaskId) {
        if (fromTaskId < 0 || fromTaskId >= taskCount || toTaskId < 0 || toTaskId >= taskCount) {
            return false;
        }
        return reachabilityMatrix[fromTaskId][toTaskId];
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
    
    // --- 新增的快取相關方法 ---
    public double[][] getOctCache() {
        return this.octCache;
    }

    public void setOctCache(double[][] oct) {
        this.octCache = oct;
    }
    
    public List<Task> getRankedTasksCache() {
        return this.rankedTasksCache;
    }
    
    public void setRankedTasksCache(List<Task> rankedTasks) {
        this.rankedTasksCache = rankedTasks;
    }
    // -------------------------
    
    @Override
    public String toString() {
        return String.format("DAG{processors=%d, tasks=%d, edges=%d, homogeneous=%s}", 
                           processorCount, taskCount, edgeCount, isHomogeneous);
    }
} 