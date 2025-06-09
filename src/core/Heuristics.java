package core;

import java.util.*;

/**
 * Heuristics類別：提供啟發式演算法來生成初始解
 */
public class Heuristics {

    /**
     * **NEW for Ant**: Calculates the upward rank for each task.
     * The upward rank of a task is the critical path length from that task to an exit task,
     * including the computation cost of the task itself.
     * @param dag The DAG.
     * @return An array of upward ranks, indexed by task ID.
     */
    public static double[] calculateUpwardRanks(DAG dag) {
        int taskCount = dag.getTaskCount();
        int processorCount = dag.getProcessorCount();
        
        // 1. Calculate average computation costs for all tasks
        double[] avgComputationCosts = new double[taskCount];
        for (int i = 0; i < taskCount; i++) {
            double sum = 0;
            for (int j = 0; j < processorCount; j++) {
                sum += dag.getTask(i).getComputationCost(j);
            }
            avgComputationCosts[i] = sum / processorCount;
        }

        // 2. Calculate upward ranks by traversing tasks in reverse topological order
        double[] upwardRanks = new double[taskCount];
        List<Integer> topologicalOrder = dag.getTopologicalOrder();
        Collections.reverse(topologicalOrder); // From exit tasks to entry tasks

        for (int taskId : topologicalOrder) {
            Task task = dag.getTask(taskId);
            double maxSuccessorRank = 0;
            for (int successorId : task.getSuccessors()) {
                // Calculate average communication cost
                double avgCommCost = 0;
                int dataVolume = task.getDataTransferVolume(successorId);
                if (dataVolume > 0) {
                    // This is a simplified avg comm cost, assuming non-zero transfer rates
                    avgCommCost = dataVolume * dag.getAverageCommunicationRate();
                }
                maxSuccessorRank = Math.max(maxSuccessorRank, avgCommCost + upwardRanks[successorId]);
            }
            upwardRanks[taskId] = avgComputationCosts[taskId] + maxSuccessorRank;
        }
        return upwardRanks;
    }

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
        
        // **NEW**: Use the dedicated method for rank calculation
        double[] upwardRanks = calculateUpwardRanks(dag);

        // 3. 根據向上排名對任務進行排序
        List<Task> rankedTasks = new ArrayList<>(dag.getTasks());
        rankedTasks.sort((t1, t2) -> Double.compare(upwardRanks[t2.getTaskId()], upwardRanks[t1.getTaskId()]));
        
        // 儲存結果到快取
        dag.setRankedTasksCache(rankedTasks);
        
        return rankedTasks;
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
            double minPredictedEFT = Double.MAX_VALUE;
            double actualEFTForBestProcessor = -1.0;
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
                
                // **CORRECTED LOGIC**: Use predicted EFT for decision making
                double predictedEFT = est + task.getComputationCost(pId) + oct[taskId][pId];
                
                if (predictedEFT < minPredictedEFT) {
                    minPredictedEFT = predictedEFT;
                    bestProcessorId = pId;
                    // **CORRECTED LOGIC**: Store the *actual* EFT for the best choice found so far
                    actualEFTForBestProcessor = est + task.getComputationCost(pId);
                }
            }
            assignment[taskId] = bestProcessorId;
            // **CORRECTED LOGIC**: Assign the *actual* finish time
            taskFinishTimes[taskId] = actualEFTForBestProcessor; 
            processors[bestProcessorId].setReadyTime(actualEFTForBestProcessor);
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