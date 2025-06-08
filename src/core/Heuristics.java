package core;

import java.util.*;

/**
 * Heuristics類別：提供啟發式演算法來生成初始解
 */
public class Heuristics {

    /**
     * 計算任務的向上排名 (Upward Rank) 並返回排序後的任務列表
     * @param dag The DAG object
     * @return A list of tasks sorted by their upward rank in descending order.
     */
    public static List<Task> getRankedTasks(DAG dag) {
        // 檢查快取
        if (dag.getRankedTasksCache() != null) {
            return dag.getRankedTasksCache();
        }

        int taskCount = dag.getTaskCount();
        int processorCount = dag.getProcessorCount();
        
        // 1. 計算任務的平均計算成本
        double[] avgComputationCosts = new double[taskCount];
        for (int i = 0; i < taskCount; i++) {
            double sum = 0;
            for (int j = 0; j < processorCount; j++) {
                sum += dag.getTask(i).getComputationCost(j);
            }
            avgComputationCosts[i] = sum / processorCount;
        }

        // 2. 計算任務的向上排名 (Upward Rank)
        double[] upwardRanks = new double[taskCount];
        List<Integer> topologicalOrder = dag.getTopologicalOrder();
        Collections.reverse(topologicalOrder); // 從出口任務開始

        for (int taskId : topologicalOrder) {
            Task task = dag.getTask(taskId);
            double maxSuccessorRank = 0;
            for (int successorId : task.getSuccessors()) {
                double avgCommCost = 0;
                int dataVolume = task.getDataTransferVolume(successorId);
                if (dataVolume > 0) {
                    double totalCommRate = 0;
                    int pairs = 0;
                    for (int p1 = 0; p1 < processorCount; p1++) {
                        for (int p2 = 0; p2 < processorCount; p2++) {
                           if(p1 != p2) {
                               totalCommRate += dag.getCommunicationRates()[p1][p2];
                               pairs++;
                           }
                        }
                    }
                    if (pairs > 0) {
                         double avgCommRate = totalCommRate / pairs;
                         avgCommCost = dataVolume * avgCommRate;
                    }
                }
                maxSuccessorRank = Math.max(maxSuccessorRank, avgCommCost + upwardRanks[successorId]);
            }
            upwardRanks[taskId] = avgComputationCosts[taskId] + maxSuccessorRank;
        }

        // 3. 根據向上排名對任務進行排序
        List<Task> rankedTasks = new ArrayList<>(dag.getTasks());
        rankedTasks.sort((t1, t2) -> Double.compare(upwardRanks[t2.getTaskId()], upwardRanks[t1.getTaskId()]));
        
        // 儲存結果到快取
        dag.setRankedTasksCache(rankedTasks);
        
        return rankedTasks;
    }

    /**
     * **NEW**: 獨立計算並返回所有任務的向上排名(Upward Rank)。
     * 向上排名是任務調度中一個關鍵的啟發式指標。
     * @param dag The DAG object
     * @return A double array where the index is the task ID and the value is the upward rank.
     */
    public static double[] calculateUpwardRanks(DAG dag) {
        int taskCount = dag.getTaskCount();
        int processorCount = dag.getProcessorCount();
        
        // 1. 計算平均計算成本
        double[] avgComputationCosts = new double[taskCount];
        for (int i = 0; i < taskCount; i++) {
            double sum = 0;
            for (int j = 0; j < processorCount; j++) {
                sum += dag.getTask(i).getComputationCost(j);
            }
            avgComputationCosts[i] = sum / processorCount;
        }

        // 2. 計算向上排名
        double[] upwardRanks = new double[taskCount];
        List<Integer> topologicalOrder = dag.getTopologicalOrder();
        Collections.reverse(topologicalOrder); // 從出口任務開始

        for (int taskId : topologicalOrder) {
            Task task = dag.getTask(taskId);
            double maxSuccessorRank = 0;
            for (int successorId : task.getSuccessors()) {
                // 在我們的模型中，通訊成本的計算比較複雜，此處簡化為平均通訊成本
                // 實際應用中可根據需求調整
                int dataVolume = task.getDataTransferVolume(successorId);
                double avgCommCost = 0;
                if (dataVolume > 0) {
                    // 簡化的平均通訊成本估算
                    avgCommCost = dataVolume * Arrays.stream(dag.getCommunicationRates()).flatMapToDouble(Arrays::stream).average().orElse(0.0);
                }
                maxSuccessorRank = Math.max(maxSuccessorRank, avgCommCost + upwardRanks[successorId]);
            }
            upwardRanks[taskId] = avgComputationCosts[taskId] + maxSuccessorRank;
        }
        
        return upwardRanks;
    }

    /**
     * **NEW for ACO**: Gets a list of task IDs sorted by upward rank.
     * @param dag The DAG object
     * @return A list of task IDs sorted by their upward rank.
     */
    public static List<Integer> getRankedTasksIds(DAG dag) {
        List<Task> rankedTasks = getRankedTasks(dag);
        List<Integer> taskIds = new ArrayList<>();
        for (Task task : rankedTasks) {
            taskIds.add(task.getTaskId());
        }
        return taskIds;
    }

    /**
     * HEFT (Heterogeneous Earliest Finish Time) 演算法
     * 產生一個高品質的初始調度方案
     */
    public static Schedule createHeftSchedule(DAG dag) {
        int taskCount = dag.getTaskCount();
        int processorCount = dag.getProcessorCount();
        
        List<Task> taskPriorityList = getRankedTasks(dag);
        List<Integer> taskOrder = new ArrayList<>();
        for (Task t : taskPriorityList) {
            taskOrder.add(t.getTaskId());
        }

        // 4. 依次將任務分配給能使其最早完成的處理器
        int[] assignment = new int[taskCount];
        double[] taskFinishTimes = new double[taskCount];
        Arrays.fill(taskFinishTimes, -1.0);
        
        Processor[] processors = new Processor[processorCount];
        for (int i = 0; i < processorCount; i++) {
            processors[i] = new Processor(i);
        }

        for (Task task : taskPriorityList) {
            int taskId = task.getTaskId();
            double minEFT = Double.MAX_VALUE;
            int bestProcessorId = -1;

            for (int pId = 0; pId < processorCount; pId++) {
                double earliestStartTime = processors[pId].getReadyTime();

                // 計算來自前驅任務的數據到達時間
                double dataReadyTime = 0;
                for (int predId : task.getPredecessors()) {
                    int predProcessorId = assignment[predId];
                    double predFinishTime = taskFinishTimes[predId];
                    double commCost = dag.getCommunicationCost(predId, taskId, predProcessorId, pId);
                    dataReadyTime = Math.max(dataReadyTime, predFinishTime + commCost);
                }

                earliestStartTime = Math.max(earliestStartTime, dataReadyTime);
                double finishTime = earliestStartTime + task.getComputationCost(pId);

                if (finishTime < minEFT) {
                    minEFT = finishTime;
                    bestProcessorId = pId;
                }
            }
            
            // 分配任務
            assignment[taskId] = bestProcessorId;
            taskFinishTimes[taskId] = minEFT;
            
            // 更新處理器狀態 (簡易模擬，不需精確的 TaskExecution)
            processors[bestProcessorId].setReadyTime(minEFT);
        }

        // 創建一個包含分配和排序的完整 Schedule
        Schedule schedule = new Schedule(dag, assignment);
        schedule.setTaskOrder(taskOrder);
        schedule.setMakespan(taskFinishTimes[taskOrder.get(taskOrder.size() - 1)]);
        
        return schedule;
    }

    /**
     * Performs Path-Relinking between a source and a guiding schedule.
     * This method explores the trajectory from the source to the guiding solution,
     * applying an intensive local search at each step to find better intermediate solutions.
     *
     * @param source The starting schedule for the relinking path.
     * @param guiding The target schedule that guides the search.
     * @return The best schedule found along the path from source to guiding.
     */
    public static Schedule pathRelinking(Schedule source, Schedule guiding) {
        Schedule current = source.clone();
        Schedule bestFound = source.clone();
        bestFound.evaluateFitness();

        int[] currentChromosome = current.getChromosome();
        int[] guidingChromosome = guiding.getChromosome();
        
        // Find differences between the two chromosomes
        List<Integer> diffIndices = new ArrayList<>();
        for (int i = 0; i < currentChromosome.length; i++) {
            if (currentChromosome[i] != guidingChromosome[i]) {
                diffIndices.add(i);
            }
        }

        // Traverse the path in a random order to avoid bias
        Collections.shuffle(diffIndices);

        for (int indexToChange : diffIndices) {
            // Move one step along the path
            currentChromosome[indexToChange] = guidingChromosome[indexToChange];
            
            // **恢復**：在中間解上執行完整的、高強度的局部搜尋
            current.evaluateFitness();
            current.criticalPathLocalSearch();

            if (current.getMakespan() < bestFound.getMakespan()) {
                bestFound = current.clone();
            }
        }
        
        return bestFound;
    }

    // --- PEFT Implementation ---

    /**
     * **NEW**: 創建一個基於 PEFT 演算法的排程
     */
    public static Schedule createPeftSchedule(DAG dag) {
        double[][] oct = computeOptimisticCostTable(dag);
        dag.setOctCache(oct); // Cache for later use in mutation

        List<Task> taskPriorityList = getPeftRankedTasks(dag, oct);
        List<Integer> taskOrder = new ArrayList<>();
        for (Task t : taskPriorityList) {
            taskOrder.add(t.getTaskId());
        }

        // --- Processor Selection Phase (similar to HEFT) ---
        int taskCount = dag.getTaskCount();
        int[] assignment = new int[taskCount];
        double[] taskFinishTimes = new double[taskCount];
        Arrays.fill(taskFinishTimes, -1.0);
        
        Processor[] processors = new Processor[dag.getProcessorCount()];
        for (int i = 0; i < dag.getProcessorCount(); i++) {
            processors[i] = new Processor(i);
        }

        for (Task task : taskPriorityList) {
            int taskId = task.getTaskId();
            double minEFT = Double.MAX_VALUE;
            int bestProcessorId = -1;

            for (int pId = 0; pId < dag.getProcessorCount(); pId++) {
                double earliestReadyTime = processors[pId].getReadyTime();
                double dataReadyTime = 0;
                for (int predId : task.getPredecessors()) {
                    int predProcessorId = assignment[predId];
                    double predFinishTime = taskFinishTimes[predId];
                    if (predFinishTime == -1.0) continue; // Skip predecessors not yet scheduled
                    double commCost = dag.getCommunicationCost(predId, taskId, predProcessorId, pId);
                    dataReadyTime = Math.max(dataReadyTime, predFinishTime + commCost);
                }
                double est = Math.max(earliestReadyTime, dataReadyTime);
                double eft = est + task.getComputationCost(pId) + oct[taskId][pId];
                
                if (eft < minEFT) {
                    minEFT = eft;
                    bestProcessorId = pId;
                }
            }
            assignment[taskId] = bestProcessorId;
            taskFinishTimes[taskId] = minEFT;
            processors[bestProcessorId].setReadyTime(minEFT);
        }

        Schedule schedule = new Schedule(dag, assignment);
        schedule.setTaskOrder(taskOrder);
        schedule.setMakespan(Arrays.stream(taskFinishTimes).max().orElse(0.0));
        return schedule;
    }
    
    private static List<Task> getPeftRankedTasks(DAG dag, double[][] oct) {
        // Calculate upward ranks based on OCT
        Map<Integer, Double> upwardRanks = new HashMap<>();
        List<Integer> topologicalOrder = dag.getTopologicalOrder();
        Collections.reverse(topologicalOrder); // Process from exit nodes

        for (int taskId : topologicalOrder) {
            double maxSuccRank = 0;
            for (int succId : dag.getTask(taskId).getSuccessors()) {
                maxSuccRank = Math.max(maxSuccRank, upwardRanks.getOrDefault(succId, 0.0));
            }
            // The rank is the average OCT plus the max successor rank
            double avgOct = Arrays.stream(oct[taskId]).average().orElse(0);
            upwardRanks.put(taskId, avgOct + maxSuccRank);
        }
        
        List<Task> rankedTasks = new ArrayList<>(dag.getTasks());
        rankedTasks.sort((t1, t2) -> upwardRanks.get(t2.getTaskId()).compareTo(upwardRanks.get(t1.getTaskId())));
        return rankedTasks;
    }

    private static double[][] computeOptimisticCostTable(DAG dag) {
        int taskCount = dag.getTaskCount();
        int processorCount = dag.getProcessorCount();
        double[][] oct = new double[taskCount][processorCount];
        List<Integer> topologicalOrder = dag.getTopologicalOrder();
        Collections.reverse(topologicalOrder); // from exit to entry

        for (int taskId : topologicalOrder) {
            Task task = dag.getTask(taskId);
            for (int pId = 0; pId < processorCount; pId++) {
                double avgCompCost = task.getComputationCost(pId);
                
                double maxSuccCost = 0;
                if (!task.getSuccessors().isEmpty()) {
                    for (int succId : task.getSuccessors()) {
                        double minSuccOct = Double.MAX_VALUE;
                        for (int succPId = 0; succPId < processorCount; succPId++) {
                            double commCost = dag.getCommunicationCost(taskId, succId, pId, succPId);
                            minSuccOct = Math.min(minSuccOct, oct[succId][succPId] + commCost);
                        }
                        maxSuccCost = Math.max(maxSuccCost, minSuccOct);
                    }
                }
                oct[taskId][pId] = avgCompCost + maxSuccCost;
            }
        }

        // This requires iterating until values converge. Let's do a few passes.
        // For a simple implementation, one pass might be enough, but let's do more.
        for(int iter = 0; iter < taskCount; iter++) { // Iterate to propagate ranks
             for (int taskId : topologicalOrder) {
                 for(int procId = 0; procId < dag.getProcessorCount(); procId++) {
                    double maxSuccCost = 0;
                    for (int succId : dag.getTask(taskId).getSuccessors()) {
                         double minSuccEft = Double.MAX_VALUE;
                         for(int succProcId = 0; succProcId < dag.getProcessorCount(); succProcId++) {
                             minSuccEft = Math.min(minSuccEft, oct[succId][succProcId] + dag.getCommunicationCost(taskId, succId, procId, succProcId));
                         }
                         maxSuccCost = Math.max(maxSuccCost, minSuccEft);
                    }
                    oct[taskId][procId] = dag.getComputationCost(taskId, procId) + maxSuccCost;
                 }
             }
        }

        return oct;
    }
} 