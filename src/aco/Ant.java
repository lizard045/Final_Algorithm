package aco;

import core.DAG;
import core.Heuristics;
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
    }

    public Ant() {
        // Schedule will be built during constructSolution
    }

    /**
     * 建構一個完整的解決方案 (一個調度)
     * 螞蟻會根據資訊素和啟發式資訊，一步步選擇任務並為其分配處理器。
     */
    public void constructSolution(DAG dag, double[][] pheromoneMatrix, double alpha, double beta) {
        int taskCount = dag.getTaskCount();
        int processorCount = dag.getProcessorCount();

        // 預先計算向上排名，作為核心啟發式資訊
        double[] upwardRanks = Heuristics.calculateUpwardRanks(dag);

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
            Candidate bestCandidate = selectNextMove(readyTasks, dag, pheromoneMatrix, alpha, beta, processors, processorAssignments, taskFinishTimes, upwardRanks);
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
     * 全局輪盤賭選擇，從所有可行的 (任務, 處理器) 組合中選出下一步。
     */
    private Candidate selectNextMove(List<Integer> readyTasks, DAG dag, double[][] pheromoneMatrix, double alpha, double beta, Processor[] processors, int[] currentAssignments, double[] taskFinishTimes, double[] upwardRanks) {
        List<Candidate> candidates = new ArrayList<>();
        double totalDesirability = 0.0;

        for (int taskId : readyTasks) {
            for (int pId = 0; pId < dag.getProcessorCount(); pId++) {
                double pheromone = Math.pow(pheromoneMatrix[taskId][pId], alpha);
                
                // 複合啟發式資訊
                double eft = calculateEFT(taskId, pId, dag, processors, currentAssignments, taskFinishTimes);
                double rank = upwardRanks[taskId];
                
                // 避免除以零
                if (eft == 0) eft = 0.0001;
                if (rank == 0) rank = 0.0001;

                double heuristic = Math.pow(rank, beta) * Math.pow(1.0 / eft, beta);
                
                double desirability = pheromone * heuristic;
                candidates.add(new Candidate(taskId, pId, desirability));
                totalDesirability += desirability;
            }
        }

        // 輪盤賭選擇
        double roll = random.nextDouble() * totalDesirability;
        double cumulative = 0.0;
        for (Candidate c : candidates) {
            cumulative += c.desirability;
            if (roll <= cumulative) {
                return c;
            }
        }
        
        // Fallback
        return candidates.get(random.nextInt(candidates.size()));
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
} 