import java.util.*;

/**
 * 統計分析器
 * 負責計算和分析GA演算法的執行結果統計
 */
public class StatisticsAnalyzer {
    private List<Double> fitnessHistory;
    private List<Long> executionTimes;  // 記錄每次執行的時間（毫秒）
    
    /**
     * 建構函數
     */
    public StatisticsAnalyzer() {
        this.fitnessHistory = new ArrayList<>();
        this.executionTimes = new ArrayList<>();
    }
    
    /**
     * 記錄一次執行的結果
     */
    public void recordExecution(double bestFitness, long executionTimeMs) {
        fitnessHistory.add(bestFitness);
        executionTimes.add(executionTimeMs);
    }
    
    /**
     * 清除所有記錄
     */
    public void clear() {
        fitnessHistory.clear();
        executionTimes.clear();
    }
    
    /**
     * 獲取最佳值
     */
    public double getBest() {
        if (fitnessHistory.isEmpty()) return 0.0;
        return Collections.min(fitnessHistory);
    }
    
    /**
     * 獲取最差值
     */
    public double getWorst() {
        if (fitnessHistory.isEmpty()) return 0.0;
        return Collections.max(fitnessHistory);
    }
    
    /**
     * 獲取平均值
     */
    public double getAverage() {
        if (fitnessHistory.isEmpty()) return 0.0;
        double sum = 0.0;
        for (double fitness : fitnessHistory) {
            sum += fitness;
        }
        return sum / fitnessHistory.size();
    }
    
    /**
     * 獲取標準差
     */
    public double getStandardDeviation() {
        if (fitnessHistory.size() <= 1) return 0.0;
        
        double mean = getAverage();
        double sumSquaredDiff = 0.0;
        
        for (double fitness : fitnessHistory) {
            double diff = fitness - mean;
            sumSquaredDiff += diff * diff;
        }
        
        return Math.sqrt(sumSquaredDiff / (fitnessHistory.size() - 1));
    }
    
    /**
     * 獲取平均執行時間（秒）
     */
    public double getAverageRunningTime() {
        if (executionTimes.isEmpty()) return 0.0;
        long sum = 0;
        for (long time : executionTimes) {
            sum += time;
        }
        return (sum / (double)executionTimes.size()) / 1000.0; // 轉換為秒
    }
    
    /**
     * 獲取執行次數
     */
    public int getExecutionCount() {
        return fitnessHistory.size();
    }
    
    /**
     * 獲取所有適應度值的副本
     */
    public List<Double> getFitnessHistory() {
        return new ArrayList<>(fitnessHistory);
    }
    
    /**
     * 獲取所有執行時間的副本
     */
    public List<Long> getExecutionTimes() {
        return new ArrayList<>(executionTimes);
    }
    
    /**
     * 產生統計報告
     */
    public StatisticsReport generateReport() {
        return new StatisticsReport(
            getBest(),
            getWorst(),
            getAverage(),
            getStandardDeviation(),
            getAverageRunningTime(),
            getExecutionCount()
        );
    }
    
    /**
     * 統計報告資料類別
     */
    public static class StatisticsReport {
        private final double best;
        private final double worst;
        private final double average;
        private final double standardDeviation;
        private final double averageRunningTime;
        private final int executionCount;
        
        public StatisticsReport(double best, double worst, double average, 
                              double standardDeviation, double averageRunningTime, 
                              int executionCount) {
            this.best = best;
            this.worst = worst;
            this.average = average;
            this.standardDeviation = standardDeviation;
            this.averageRunningTime = averageRunningTime;
            this.executionCount = executionCount;
        }
        
        // Getter methods
        public double getBest() { return best; }
        public double getWorst() { return worst; }
        public double getAverage() { return average; }
        public double getStandardDeviation() { return standardDeviation; }
        public double getAverageRunningTime() { return averageRunningTime; }
        public int getExecutionCount() { return executionCount; }
        
        /**
         * 格式化輸出統計結果
         */
        public String toString() {
            return String.format(
                "統計結果 (共執行 %d 次):\n" +
                "  最佳值 (Best): %.1f\n" +
                "  最差值 (Worst): %.1f\n" +
                "  平均值 (Avg): %.1f\n" +
                "  標準差 (sd): %.2f\n" +
                "  平均執行時間 (Avg. Running Time): %.4f 秒",
                executionCount, best, worst, average, standardDeviation, averageRunningTime
            );
        }
        
        /**
         * 格式化輸出為表格行
         */
        public String toTableRow(double heterogeneity) {
            return String.format("%.1f\t%.1f\t%.1f\t%.1f\t%.2f\t%.4f", 
                               heterogeneity, best, worst, average, standardDeviation, averageRunningTime);
        }
    }
    
    /**
     * 輸出詳細的統計資訊
     */
    public void printDetailedStatistics() {
        if (fitnessHistory.isEmpty()) {
            System.out.println("沒有執行記錄可以分析。");
            return;
        }
        
        System.out.println("\n=== 詳細統計分析 ===");
        System.out.println("執行次數: " + getExecutionCount());
        System.out.println("最佳適應度: " + String.format("%.2f", getBest()));
        System.out.println("最差適應度: " + String.format("%.2f", getWorst()));
        System.out.println("平均適應度: " + String.format("%.2f", getAverage()));
        System.out.println("標準差: " + String.format("%.2f", getStandardDeviation()));
        System.out.println("平均執行時間: " + String.format("%.4f", getAverageRunningTime()) + " 秒");
        
        // 顯示分佈資訊
        System.out.println("\n=== 適應度分佈 ===");
        Collections.sort(fitnessHistory);
        int quartileSize = fitnessHistory.size() / 4;
        if (quartileSize > 0) {
            System.out.println("第1四分位數 (Q1): " + String.format("%.2f", fitnessHistory.get(quartileSize)));
            System.out.println("中位數 (Q2): " + String.format("%.2f", fitnessHistory.get(fitnessHistory.size() / 2)));
            System.out.println("第3四分位數 (Q3): " + String.format("%.2f", fitnessHistory.get(3 * quartileSize)));
        }
    }
} 