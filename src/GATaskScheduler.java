import java.io.*;
import java.util.*;

/**
 * GA任務調度器主程式
 * 整合所有組件，執行遺傳演算法解決任務調度問題
 */
public class GATaskScheduler {
    
    /**
     * 主函數
     */
    public static void main(String[] args) {
        GATaskScheduler scheduler = new GATaskScheduler();
        
        // 測試檔案列表
        String[] testFiles = {"n4_00.dag", "n4_02.dag", "n4_04.dag", "n4_06.dag"};
        double[] heterogeneityLevels = {0.0, 0.2, 0.4, 0.6};
        
        System.out.println("=== GA任務調度演算法測試 ===\n");
        
        // 輸出表格標題
        System.out.println("Heterogeneity\tBest\tWorst\tAvg\tsd\tAvg. Running Time (s)");
        System.out.println("============================================================================");
        
        // 對每個測試檔案執行多次實驗
        for (int fileIndex = 0; fileIndex < testFiles.length; fileIndex++) {
            String filename = testFiles[fileIndex];
            double heterogeneity = heterogeneityLevels[fileIndex];
            
            System.out.printf("\n正在處理檔案: %s (異質性: %.1f)\n", filename, heterogeneity);
            
                         StatisticsAnalyzer.StatisticsReport report = scheduler.runExperiments(filename, 3);
            
            // 輸出結果
            System.out.println(report.toTableRow(heterogeneity));
        }
        
        System.out.println("\n=== 實驗完成 ===");
    }
    
    /**
     * 執行多次實驗並收集統計資料
     */
    public StatisticsAnalyzer.StatisticsReport runExperiments(String filename, int runCount) {
        StatisticsAnalyzer statistics = new StatisticsAnalyzer();
        
        try {
            // 讀取DAG資料
            DAGReader dagReader = new DAGReader();
            dagReader.readDAGFile(filename);
            
            System.out.printf("任務數: %d, 處理器數: %d, 邊數: %d\n", 
                            dagReader.getTaskCount(), 
                            dagReader.getProcessorCount(), 
                            dagReader.getEdgeCount());
            
            // 建立核心組件
            FitnessEvaluator fitnessEvaluator = new FitnessEvaluator(dagReader);
            GeneticAlgorithm ga = new GeneticAlgorithm(dagReader, fitnessEvaluator);
            
            // 設定GA參數
            ga.setParameters(
                50,     // 族群大小 (減少)
                100,    // 最大世代數 (減少)
                0.8,    // 交叉率
                0.1,    // 突變率
                3,      // 菁英數 (減少)
                3       // 錦標賽大小 (減少)
            );
            
            System.out.println(ga.getParameterInfo());
            
            // 執行多次實驗
            for (int run = 1; run <= runCount; run++) {
                System.out.printf("\n--- 第 %d 次執行 ---\n", run);
                
                // 設定不同的隨機種子
                ga.setSeed(System.currentTimeMillis() + run * 1000);
                
                // 記錄開始時間
                long startTime = System.currentTimeMillis();
                
                // 執行GA演算法
                Individual bestSolution = ga.evolve();
                
                // 記錄結束時間
                long endTime = System.currentTimeMillis();
                long executionTime = endTime - startTime;
                
                // 記錄結果
                statistics.recordExecution(bestSolution.getFitness(), executionTime);
                
                System.out.printf("最佳解適應度: %.2f, 執行時間: %.3f 秒\n", 
                                bestSolution.getFitness(), executionTime / 1000.0);
                
                // 輸出最佳解的任務分配
                printSolutionDetails(bestSolution, run);
            }
            
            // 輸出詳細統計
            statistics.printDetailedStatistics();
            
        } catch (IOException e) {
            System.err.println("讀取檔案錯誤: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("執行錯誤: " + e.getMessage());
            e.printStackTrace();
        }
        
        return statistics.generateReport();
    }
    
    /**
     * 輸出解的詳細資訊
     */
    private void printSolutionDetails(Individual solution, int runNumber) {
        System.out.printf("第 %d 次執行的最佳任務分配:\n", runNumber);
        
        int[] chromosome = solution.getChromosome();
        StringBuilder sb = new StringBuilder();
        
        for (int taskId = 0; taskId < chromosome.length; taskId++) {
            sb.append(String.format("T%d->P%d ", taskId, chromosome[taskId]));
            if ((taskId + 1) % 10 == 0) {
                sb.append("\n");
            }
        }
        
        System.out.println(sb.toString());
        System.out.println();
    }
    
    /**
     * 單獨測試一個檔案的詳細分析
     */
    public void detailedAnalysis(String filename) {
        try {
            System.out.println("=== 詳細分析模式 ===");
            
            // 讀取資料
            DAGReader dagReader = new DAGReader();
            dagReader.readDAGFile(filename);
            
            System.out.printf("分析檔案: %s\n", filename);
            System.out.printf("任務數量: %d\n", dagReader.getTaskCount());
            System.out.printf("處理器數量: %d\n", dagReader.getProcessorCount());
            System.out.printf("邊數量: %d\n", dagReader.getEdgeCount());
            
            // 顯示計算成本矩陣
            System.out.println("\n計算成本矩陣:");
            printMatrix(dagReader.getCompCost(), "Task", "Processor");
            
            // 顯示通訊速率矩陣
            System.out.println("\n通訊速率矩陣:");
            printMatrix(dagReader.getCommRate(), "Processor", "Processor");
            
            // 建立適應度評估器
            FitnessEvaluator fitnessEvaluator = new FitnessEvaluator(dagReader);
            System.out.printf("\n關鍵路徑長度: %.2f\n", fitnessEvaluator.getCriticalPathLength());
            
            // 執行GA分析
            GeneticAlgorithm ga = new GeneticAlgorithm(dagReader, fitnessEvaluator);
            ga.setParameters(50, 100, 0.8, 0.1, 3, 3); // 較小的參數以便快速測試
            
            Individual bestSolution = ga.evolve();
            
            System.out.printf("\n最佳解適應度: %.2f\n", bestSolution.getFitness());
            System.out.println("最佳任務分配:");
            printSolutionDetails(bestSolution, 1);
            
            // 顯示收斂曲線
            List<Double> bestHistory = ga.getBestFitnessHistory();
            List<Double> avgHistory = ga.getAvgFitnessHistory();
            
            System.out.println("收斂過程 (每10世代):");
            System.out.println("世代\t最佳適應度\t平均適應度");
            for (int i = 0; i < bestHistory.size(); i += 10) {
                System.out.printf("%d\t%.2f\t\t%.2f\n", 
                                i, bestHistory.get(i), avgHistory.get(i));
            }
            
        } catch (Exception e) {
            System.err.println("詳細分析錯誤: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 輸出矩陣
     */
    private void printMatrix(double[][] matrix, String rowLabel, String colLabel) {
        System.out.printf("%-8s", "");
        for (int j = 0; j < matrix[0].length; j++) {
            System.out.printf("%s%d\t", colLabel.substring(0, 1), j);
        }
        System.out.println();
        
        for (int i = 0; i < matrix.length; i++) {
            System.out.printf("%s%d\t", rowLabel.substring(0, 1), i);
            for (int j = 0; j < matrix[i].length; j++) {
                System.out.printf("%.1f\t", matrix[i][j]);
            }
            System.out.println();
        }
    }
    
    /**
     * 參數敏感度分析
     */
    public void parameterSensitivityAnalysis(String filename) {
        System.out.println("=== 參數敏感度分析 ===");
        
        try {
            DAGReader dagReader = new DAGReader();
            dagReader.readDAGFile(filename);
            
            FitnessEvaluator fitnessEvaluator = new FitnessEvaluator(dagReader);
            
            // 測試不同的族群大小
            int[] populationSizes = {50, 100, 200};
            double[] crossoverRates = {0.6, 0.8, 0.9};
            double[] mutationRates = {0.05, 0.1, 0.2};
            
            System.out.println("族群大小\t交叉率\t突變率\t平均適應度");
            
            for (int popSize : populationSizes) {
                for (double crossRate : crossoverRates) {
                    for (double mutRate : mutationRates) {
                        GeneticAlgorithm ga = new GeneticAlgorithm(dagReader, fitnessEvaluator);
                        ga.setParameters(popSize, 100, crossRate, mutRate, 3, 3);
                        
                        double totalFitness = 0.0;
                        int runs = 3;
                        
                        for (int run = 0; run < runs; run++) {
                            ga.setSeed(System.currentTimeMillis() + run * 1000);
                            Individual solution = ga.evolve();
                            totalFitness += solution.getFitness();
                        }
                        
                        double avgFitness = totalFitness / runs;
                        System.out.printf("%d\t\t%.1f\t%.2f\t%.2f\n", 
                                        popSize, crossRate, mutRate, avgFitness);
                    }
                }
            }
            
        } catch (Exception e) {
            System.err.println("參數分析錯誤: " + e.getMessage());
        }
    }
} 