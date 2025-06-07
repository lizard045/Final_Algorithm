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
                         avgCommCost = dataVolume * totalCommRate / pairs;
                    }
                }
                maxSuccessorRank = Math.max(maxSuccessorRank, avgCommCost + upwardRanks[successorId]);
            }
            upwardRanks[taskId] = avgComputationCosts[taskId] + maxSuccessorRank;
        }

        // 3. 根據向上排名對任務進行排序
        List<Task> rankedTasks = new ArrayList<>(dag.getTasks());
        rankedTasks.sort((t1, t2) -> Double.compare(upwardRanks[t2.getTaskId()], upwardRanks[t1.getTaskId()]));
        return rankedTasks;
    }

    /**
     * HEFT (Heterogeneous Earliest Finish Time) 演算法
     * 產生一個高品質的初始調度方案
     */
    public static Schedule createHeftSchedule(DAG dag) {
        int taskCount = dag.getTaskCount();
        int processorCount = dag.getProcessorCount();
        
        List<Task> taskPriorityList = getRankedTasks(dag);

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

        return new Schedule(dag, assignment);
    }
} 