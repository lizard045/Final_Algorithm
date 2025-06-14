package aco;

import core.DAG;
import core.Processor;
import core.Schedule;
import java.util.*;

/**
 * Ant類別：代表一隻螞蟻，用於建構一個調度解決方案
 * **NEW**: 採用整合決策機制，同時選擇任務和處理器。
 */
public class Ant {
    private Schedule schedule;
    private final Random random = new Random();
    private double[] upwardRanks; // **NEW**: Cache upward ranks for efficiency
    
    // **PERFORMANCE**: Object pool for candidates to reduce allocation overhead
    private final List<Candidate> candidatePool = new ArrayList<>();
    private int poolIndex = 0;

    // 用於儲存候選的 (任務, 處理器) 組合
    private static class Candidate {
        int taskId;
        int processorId;
        double desirability;

        Candidate(int t, int p, double d) {
            this.taskId = t;
            this.processorId = p;
            this.desirability = d;
        }
        
        // **PERFORMANCE**: Method to reuse existing candidate objects
        void set(int t, int p, double d) {
            this.taskId = t;
            this.processorId = p;
            this.desirability = d;
        }
    }

    public Ant() {
        // Schedule will be built during constructSolution
    }

    /**
     * 建構一個完整的解決方案 (一個調度)
     * 螞蟻會根據資訊素和啟發式資訊，一步步選擇任務並為其分配處理器。
     * **PERFORMANCE**: Now accepts pre-computed upward ranks to avoid redundant calculations.
     */
    public void constructSolution(DAG dag, double[][] pheromoneMatrix, double alpha, double beta, double q0, double[] precomputedUpwardRanks) {
        // **PERFORMANCE**: Use pre-computed upward ranks instead of calculating them
        this.upwardRanks = precomputedUpwardRanks;
        
        int taskCount = dag.getTaskCount();
        int processorCount = dag.getProcessorCount();

        // 用於追蹤調度建構過程的狀態
        int[] processorAssignments = new int[taskCount];
        Arrays.fill(processorAssignments, -1);
        List<Integer> constructedTaskOrder = new ArrayList<>();
        double[] taskFinishTimes = new double[taskCount];
        Processor[] processors = new Processor[processorCount];
        for (int i = 0; i < processorCount; i++) {
            processors[i] = new Processor(i);
        }

        List<Integer> readyTasks = new ArrayList<>();
        int[] inDegree = new int[taskCount];
        for (int i = 0; i < taskCount; i++) {
            inDegree[i] = dag.getTask(i).getPredecessors().size();
            if (inDegree[i] == 0) {
                readyTasks.add(i);
            }
        }

        while (constructedTaskOrder.size() < taskCount) {
            if (readyTasks.isEmpty() && constructedTaskOrder.size() < taskCount) {
                throw new RuntimeException("Error: No ready tasks but not all tasks are scheduled.");
            }

            // *** 核心步驟: 從所有可能的 (任務, 處理器) 組合中選擇最佳的一個 ***
            Candidate bestCandidate = selectNextMove(readyTasks, dag, pheromoneMatrix, alpha, beta, processors, processorAssignments, taskFinishTimes, q0);
            int currentTaskId = bestCandidate.taskId;
            int bestProcessorId = bestCandidate.processorId;

            readyTasks.remove(Integer.valueOf(currentTaskId));

            // 分配任務並更新狀態
            processorAssignments[currentTaskId] = bestProcessorId;
            constructedTaskOrder.add(currentTaskId);
            double est = calculateEST(currentTaskId, bestProcessorId, dag, processors, processorAssignments, taskFinishTimes);
            double finishTime = est + dag.getComputationCost(currentTaskId, bestProcessorId);
            taskFinishTimes[currentTaskId] = finishTime;
            processors[bestProcessorId].setReadyTime(finishTime);

            // 更新可執行任務列表
            for (int successorId : dag.getTask(currentTaskId).getSuccessors()) {
                inDegree[successorId]--;
                if (inDegree[successorId] == 0) {
                    readyTasks.add(successorId);
                }
            }
        }

        this.schedule = new Schedule(dag, processorAssignments, constructedTaskOrder);
    }

    /**
     * **ENHANCED**: 採用偽隨機比例規則，從所有可行的 (任務, 處理器) 組合中選出下一步。
     * 新增向上排名作為啟發式資訊的一部分。
     * **PERFORMANCE**: Optimized mathematical operations.
     */
    private Candidate selectNextMove(List<Integer> readyTasks, DAG dag, double[][] pheromoneMatrix, double alpha, double beta, Processor[] processors, int[] currentAssignments, double[] taskFinishTimes, double q0) {
        List<Candidate> candidates = new ArrayList<>();
        double totalDesirability = 0.0;
        
        // **PERFORMANCE**: Reset pool index for reuse
        poolIndex = 0;

        for (int taskId : readyTasks) {
            double taskImportance = this.upwardRanks[taskId]; // **PERFORMANCE**: Cache outside inner loop
            
            for (int pId = 0; pId < dag.getProcessorCount(); pId++) {
                // **PERFORMANCE**: Reduced Math.pow calls
                double pheromone = (alpha == 1.0) ? pheromoneMatrix[taskId][pId] : Math.pow(pheromoneMatrix[taskId][pId], alpha);

                // **ENHANCED**: 啟發式資訊結合 EFT 和 Upward Rank
                double eft = calculateEFT(taskId, pId, dag, processors, currentAssignments, taskFinishTimes);
                if (eft == 0) eft = 0.0001; // Avoid division by zero

                // **NEW**: Enhanced heuristic = (1/EFT) × UpwardRank
                double heuristic = (1.0 / eft) * taskImportance;
                
                // **PERFORMANCE**: Optimized power calculation
                double desirability = pheromone * ((beta == 1.0) ? heuristic : Math.pow(heuristic, beta));

                if (Double.isFinite(desirability)) {
                    // **PERFORMANCE**: Use object pool to reduce allocation
                    Candidate candidate;
                    if (poolIndex < candidatePool.size()) {
                        candidate = candidatePool.get(poolIndex);
                        candidate.set(taskId, pId, desirability);
                    } else {
                        candidate = new Candidate(taskId, pId, desirability);
                        candidatePool.add(candidate);
                    }
                    poolIndex++;
                    
                    candidates.add(candidate);
                    totalDesirability += desirability;
                }
            }
        }

        if (candidates.isEmpty()) {
            // Fallback: choose a random ready task and assign to a random processor
            int randomTask = readyTasks.get(random.nextInt(readyTasks.size()));
            return new Candidate(randomTask, random.nextInt(dag.getProcessorCount()), 0);
        }

        // --- **NEW** Pseudo-random proportional rule ---
        if (random.nextDouble() < q0) {
            // Exploitation: Choose the best candidate
            return candidates.stream()
                             .max(Comparator.comparingDouble(c -> c.desirability))
                             .orElse(candidates.get(0)); // Should not be empty here
        } else {
            // Exploration: Roulette wheel selection
            if (totalDesirability == 0) {
                return candidates.get(random.nextInt(candidates.size()));
            }
            double roll = random.nextDouble() * totalDesirability;
            double cumulative = 0.0;
            for (Candidate c : candidates) {
                cumulative += c.desirability;
                if (roll <= cumulative) {
                    return c;
                }
            }
        }
        
        return candidates.get(random.nextInt(candidates.size())); // Fallback
    }
    
    /**
     * 計算在給定處理器上執行任務的EFT (Earliest Finish Time)
     */
    private double calculateEFT(int taskId, int processorId, DAG dag, Processor[] processors, int[] currentAssignments, double[] taskFinishTimes) {
        double est = calculateEST(taskId, processorId, dag, processors, currentAssignments, taskFinishTimes);
        return est + dag.getComputationCost(taskId, processorId);
    }

    /**
     * 計算在給定處理器上執行任務的EST (Earliest Start Time)
     */
    private double calculateEST(int taskId, int processorId, DAG dag, Processor[] processors, int[] currentAssignments, double[] taskFinishTimes) {
        double processorReadyTime = processors[processorId].getReadyTime();
        
        double maxDataReadyTime = 0.0;
        for (int predId : dag.getTask(taskId).getPredecessors()) {
            int predProcessorId = currentAssignments[predId];
            double predFinishTime = taskFinishTimes[predId];
            double commCost = dag.getCommunicationCost(predId, taskId, predProcessorId, processorId);
            double dataReadyTime = predFinishTime + commCost;
            if (dataReadyTime > maxDataReadyTime) {
                maxDataReadyTime = dataReadyTime;
            }
        }
        
        return Math.max(processorReadyTime, maxDataReadyTime);
    }

    public Schedule getSchedule() {
        return schedule;
    }

    /**
     * **NEW**: Sets the schedule for this ant. Used for injecting solutions.
     * @param schedule The new schedule.
     */
    public void setSchedule(Schedule schedule) {
        this.schedule = schedule;
    }
}