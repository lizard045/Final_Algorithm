import aco.ACO;
import core.Schedule;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.DoubleSummaryStatistics;
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
            if (results == null || results.isEmpty()) {
                return;
            }

            DoubleSummaryStatistics stats = results.stream()
                                                   .mapToDouble(Double::doubleValue)
                                                   .summaryStatistics();
            this.best = stats.getMin();
            this.worst = stats.getMax();
            this.avg = stats.getAverage();
            this.avgTime = avgTime;

            double variance = results.stream()
                                     .mapToDouble(d -> d)
                                     .map(val -> Math.pow(val - avg, 2))
                                     .sum() / stats.getCount();
            this.sd = Math.sqrt(variance);
        }
    }

    private static final String[] DAG_FILES = {"n4_00.dag", "n4_02.dag", "n4_04.dag", "n4_06.dag"};
    private static final int RUN_COUNT = 5;
    
    // ACO Parameters
    private static final int NUM_ANTS = 55;
    private static final int ACO_GENERATIONS = 200;
    private static final double ALPHA = 1.0;
    private static final double BETA = 2.0;
    private static final double EVAPORATION_RATE = 0.3;
    private static final double PHEROMONE_SMOOTHING_FACTOR = 0.05;
    private static final double EXPLOITATION_FACTOR_Q0 = 0.8;
    private static final int NUM_RANKED_ANTS = 6;
    private static final double ELITIST_WEIGHT = 6.0;

    public static void main(String[] args) {
        Map<String, ExperimentResult> finalResults = new LinkedHashMap<>();

        for (String dagFile : DAG_FILES) {
            System.out.println("==========================================================");
            System.out.println("Running ACO for: " + dagFile);
            System.out.println("==========================================================");

            ExperimentResult result = runExperimentForDag(dagFile);
            finalResults.put(dagFile, result);
            printStatistics(dagFile, result);
        }

        printFinalSummary(finalResults);
    }

    private static ExperimentResult runExperimentForDag(String dagFile) {
        List<Double> bestResults = new ArrayList<>();
        double totalRunningTime = 0;

        for (int i = 0; i < RUN_COUNT; i++) {
            System.out.println("\n--- Run " + (i + 1) + "/" + RUN_COUNT + " ---");
            long startTime = System.currentTimeMillis();
            
            ACO aco = new ACO(
                NUM_ANTS,
                ACO_GENERATIONS,
                ALPHA,
                BETA,
                EVAPORATION_RATE,
                EXPLOITATION_FACTOR_Q0,
                ELITIST_WEIGHT,
                NUM_RANKED_ANTS,
                PHEROMONE_SMOOTHING_FACTOR,
                dagFile
            );
            Schedule bestSchedule = aco.run();
            
            long endTime = System.currentTimeMillis();
            double runTime = (endTime - startTime) / 1000.0;
            
            if (bestSchedule != null) {
                bestResults.add(bestSchedule.getMakespan());
            }
            totalRunningTime += runTime;

            System.out.printf("Run %d finished in %.2f seconds. Best makespan: %.2f\n", 
                              i + 1, runTime, bestSchedule != null ? bestSchedule.getMakespan() : -1.0);

            if (i == 0) {
                writeConvergenceDataToFile(dagFile + ".convergence.csv", aco.getConvergenceData());
            }
        }
        return new ExperimentResult(bestResults, totalRunningTime / RUN_COUNT);
    }

    /**
     * **NEW**: Writes the convergence data to a CSV file.
     * @param filename The name of the file to write to.
     * @param data The list of makespan values from each generation.
     */
    private static void writeConvergenceDataToFile(String filename, List<Double> data) {
        try (PrintWriter out = new PrintWriter(new FileWriter(filename))) {
            System.out.println("Writing convergence data to " + filename);
            out.println("Generation,Makespan");
            for (int i = 0; i < data.size(); i++) {
                out.printf("%d,%.2f\n", i + 1, data.get(i));
            }
        } catch (IOException e) {
            System.err.println("Error writing convergence data to file: " + e.getMessage());
        }
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
}