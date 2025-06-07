import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 主類別：用於運行實驗和參數調優
 */
public class Main {

    static class ExperimentResult {
        double best, worst, avg, sd, avgTime;
        public ExperimentResult(List<Double> results, double avgTime) {
            if (results.isEmpty()) return;
            Collections.sort(results);
            this.best = results.get(0);
            this.worst = results.get(results.size() - 1);
            double sum = results.stream().mapToDouble(Double::doubleValue).sum();
            this.avg = sum / results.size();
            double variance = results.stream().mapToDouble(val -> Math.pow(val - avg, 2)).sum() / results.size();
            this.sd = Math.sqrt(variance);
            this.avgTime = avgTime;
        }
    }

    public static void main(String[] args) {
        String[] dagFiles = {"n4_00.dag", "n4_02.dag", "n4_04.dag", "n4_06.dag"};
        Map<String, ExperimentResult> finalResults = new LinkedHashMap<>();
        
        // --- Final Strategy: Island Model with On-Demand Path-Relinking ---
        final int NUM_ISLANDS = 8;
        final int TOTAL_GENERATIONS = 800;
        final int MIGRATION_SIZE = 3;      // Number of elites for migration
        
        // Per-island configuration
        final int POPULATION_PER_ISLAND = 60; 
        final double MUTATION_RATE = 0.2;
        final double LOCAL_SEARCH_RATE = 0.7;

        for (String dagFile : dagFiles) {
            System.out.println("==========================================================");
            System.out.println("Running Island Model GA for: " + dagFile);
            System.out.println("==========================================================");

            final int RUN_COUNT = 5;
            List<Double> bestResults = new ArrayList<>();
            double totalRunningTime = 0;

            for (int i = 0; i < RUN_COUNT; i++) {
                System.out.println("\n--- Run " + (i + 1) + "/" + RUN_COUNT + " ---");
                long startTime = System.currentTimeMillis();
                
                IslandModelGA islandGA = new IslandModelGA(
                    NUM_ISLANDS,
                    TOTAL_GENERATIONS,
                    MIGRATION_SIZE,
                    POPULATION_PER_ISLAND,
                    MUTATION_RATE,
                    LOCAL_SEARCH_RATE,
                    dagFile
                );
                
                Schedule bestSchedule = islandGA.run();
                
                long endTime = System.currentTimeMillis();
                double runTime = (endTime - startTime) / 1000.0;
                
                if (bestSchedule != null) {
                    bestResults.add(bestSchedule.getMakespan());
                }
                totalRunningTime += runTime;

                System.out.printf("Run %d finished in %.2f seconds. Best makespan: %.2f\n", 
                                  i + 1, runTime, bestSchedule != null ? bestSchedule.getMakespan() : -1.0);
            }

            ExperimentResult result = new ExperimentResult(bestResults, totalRunningTime / RUN_COUNT);
            finalResults.put(dagFile, result);
            printStatistics(dagFile, result);
        }

        printFinalSummary(finalResults);
    }

    private static void printStatistics(String dagFile, ExperimentResult result) {
        System.out.println("\n----------------------------------------------------------");
        System.out.println("Run Statistics for: " + dagFile);
        System.out.printf("Best Makespan:     %.2f\n", result.best);
        System.out.printf("Worst Makespan:    %.2f\n", result.worst);
        System.out.printf("Average Makespan:  %.2f\n", result.avg);
        System.out.printf("Standard Deviation:%.2f\n", result.sd);
        System.out.printf("Avg. Running Time: %.2f s\n", result.avgTime);
        System.out.println("----------------------------------------------------------\n");
    }

    private static void printFinalSummary(Map<String, ExperimentResult> finalResults) {
        System.out.println("\n\n====================================================================================");
        System.out.println("                          FINAL EXPERIMENT SUMMARY");
        System.out.println("====================================================================================");
        System.out.printf("%-15s | %-12s | %-12s | %-12s | %-12s | %-15s\n", 
            "DAG File", "Best", "Worst", "Average", "Std Dev.", "Avg Time (s)");
        System.out.println("------------------------------------------------------------------------------------");
        for (Map.Entry<String, ExperimentResult> entry : finalResults.entrySet()) {
            String dagFile = entry.getKey();
            ExperimentResult result = entry.getValue();
            if (result != null) {
                System.out.printf("%-15s | %-12.2f | %-12.2f | %-12.2f | %-12.2f | %-15.2f\n",
                    dagFile, result.best, result.worst, result.avg, result.sd, result.avgTime);
            }
        }
        System.out.println("====================================================================================");
    }

    /**
     * 參數自動調優方法
     * @param dagFile 要進行調優的DAG檔案
     */
    public static void parameterTuning(String dagFile) {
        System.out.println("Starting parameter tuning for: " + dagFile);

        int[] populationSizes = {150};
        int[] numGenerations = {300, 500};
        double[] mutationRates = {0.10, 0.20, 0.30};
        double[] localSearchRates = {0.30, 0.50, 0.70};
        int numRunsPerSetting = 3;

        double bestOverallMakespan = Double.MAX_VALUE;
        String bestParams = "";

        for (int popSize : populationSizes) {
            for (int gens : numGenerations) {
                for (double mutRate : mutationRates) {
                    for (double lsRate : localSearchRates) {
                        System.out.printf("\n[Testing] Pop: %d, Gen: %d, Mutation: %.2f, LocalSearch: %.2f\n",
                                          popSize, gens, mutRate, lsRate);
                        
                        List<Double> runResults = new ArrayList<>();
                        for (int i = 0; i < numRunsPerSetting; i++) {
                            GA ga = new GA(popSize, gens, mutRate, lsRate, dagFile);
                            Schedule result = ga.run();
                            if (result != null) {
                                runResults.add(result.getMakespan());
                            }
                        }
                        
                        // 修正評估邏輯：我們關心的是這組參數能達到的最好結果，而不是平均結果
                        double bestMakespanInSet = runResults.stream().mapToDouble(d -> d).min().orElse(Double.MAX_VALUE);
                        System.out.printf("  => Best Makespan in this set of %d runs: %.2f\n", numRunsPerSetting, bestMakespanInSet);

                        if (bestMakespanInSet < bestOverallMakespan) {
                            bestOverallMakespan = bestMakespanInSet;
                            bestParams = String.format("Pop: %d, Gen: %d, Mutation: %.2f, LocalSearch: %.2f",
                                                       popSize, gens, mutRate, lsRate);
                        }
                    }
                }
            }
        }

        System.out.println("\n=====================================================================================");
        System.out.println("Tuning Complete for " + dagFile + ".");
        System.out.println(">> Best Overall Makespan found: " + String.format("%.2f", bestOverallMakespan));
        System.out.println(">> With parameters: " + bestParams);
        System.out.println("=====================================================================================");
    }
}