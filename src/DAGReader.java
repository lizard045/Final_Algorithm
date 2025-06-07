import java.io.*;
import java.util.*;

/**
 * DAG資料讀取器
 * 用於讀取並解析任務調度的DAG檔案
 */
public class DAGReader {
    private int processorCount;      // 處理器數量
    private int taskCount;           // 任務數量
    private int edgeCount;           // 邊數量
    private double[][] commRate;     // 通訊速率矩陣
    private double[][] compCost;     // 計算成本矩陣
    private List<TaskEdge> edges;    // 任務間的邊

    /**
     * 任務邊的內部類別
     */
    public static class TaskEdge {
        public int from, to;
        public double dataVolume;
        
        public TaskEdge(int from, int to, double dataVolume) {
            this.from = from;
            this.to = to;
            this.dataVolume = dataVolume;
        }
    }

    /**
     * 讀取DAG檔案
     */
    public void readDAGFile(String filename) throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(filename));
        List<String> lines = new ArrayList<>();
        String line;
        
        // 讀取所有行
        while ((line = reader.readLine()) != null) {
            lines.add(line.trim());
        }
        reader.close();
        
        // 找到數據行（非註解行）
        List<String> dataLines = new ArrayList<>();
        for (String l : lines) {
            if (!l.startsWith("/*") && !l.startsWith("*/") && 
                !l.isEmpty() && !l.contains("=") && !l.contains("ID") &&
                !l.contains("(") && !l.contains(")") && !l.contains("�")) {
                dataLines.add(l);
            }
        }
        
        if (dataLines.size() < 3) {
            throw new IOException("檔案格式錯誤：缺少基本參數");
        }
        
        // 讀取基本參數
        processorCount = Integer.parseInt(dataLines.get(0));
        taskCount = Integer.parseInt(dataLines.get(1));
        edgeCount = Integer.parseInt(dataLines.get(2));
        
        int currentIndex = 3;
        
        // 讀取通訊速率矩陣
        commRate = new double[processorCount][processorCount];
        for (int i = 0; i < processorCount; i++) {
            if (currentIndex >= dataLines.size()) {
                throw new IOException("檔案格式錯誤：通訊速率矩陣不完整");
            }
            String[] values = dataLines.get(currentIndex++).split("\\s+");
            for (int j = 0; j < processorCount; j++) {
                commRate[i][j] = Double.parseDouble(values[j]);
            }
        }
        
        // 讀取計算成本矩陣
        compCost = new double[taskCount][processorCount];
        for (int i = 0; i < taskCount; i++) {
            if (currentIndex >= dataLines.size()) {
                throw new IOException("檔案格式錯誤：計算成本矩陣不完整");
            }
            String[] values = dataLines.get(currentIndex++).split("\\s+");
            for (int j = 0; j < processorCount; j++) {
                compCost[i][j] = Double.parseDouble(values[j]);
            }
        }
        
        // 讀取邊資訊
        edges = new ArrayList<>();
        for (int i = 0; i < edgeCount; i++) {
            if (currentIndex >= dataLines.size()) {
                throw new IOException("檔案格式錯誤：邊資訊不完整");
            }
            String[] values = dataLines.get(currentIndex++).split("\\s+");
            int from = Integer.parseInt(values[0]);
            int to = Integer.parseInt(values[1]);
            double dataVolume = Double.parseDouble(values[2]);
            edges.add(new TaskEdge(from, to, dataVolume));
        }
    }
    
    // Getter methods
    public int getProcessorCount() { return processorCount; }
    public int getTaskCount() { return taskCount; }
    public int getEdgeCount() { return edgeCount; }
    public double[][] getCommRate() { return commRate; }
    public double[][] getCompCost() { return compCost; }
    public List<TaskEdge> getEdges() { return edges; }
} 