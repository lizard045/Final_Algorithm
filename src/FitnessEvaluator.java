import java.util.*;

/**
 * 適應度評估器
 * 負責計算個體的適應度值（總執行時間）
 */
public class FitnessEvaluator {
    private DAGReader dagData;
    private Map<Integer, List<Integer>> predecessorMap;  // 前置任務映射
    private Map<Integer, List<Integer>> successorMap;    // 後續任務映射
    
    /**
     * 建構函數
     */
    public FitnessEvaluator(DAGReader dagData) {
        this.dagData = dagData;
        buildDependencyMaps();
    }
    
    /**
     * 建立任務相依性映射
     */
    private void buildDependencyMaps() {
        predecessorMap = new HashMap<>();
        successorMap = new HashMap<>();
        
        // 初始化所有任務的前置與後續列表
        for (int i = 0; i < dagData.getTaskCount(); i++) {
            predecessorMap.put(i, new ArrayList<>());
            successorMap.put(i, new ArrayList<>());
        }
        
        // 根據邊建立相依性關係
        for (DAGReader.TaskEdge edge : dagData.getEdges()) {
            if (edge.dataVolume > 0) { // 只考慮有資料傳輸的邊
                successorMap.get(edge.from).add(edge.to);
                predecessorMap.get(edge.to).add(edge.from);
            }
        }
    }
    
    /**
     * 計算個體的適應度值（總執行時間）
     */
    public double evaluateFitness(Individual individual) {
        int taskCount = dagData.getTaskCount();
        int processorCount = dagData.getProcessorCount();
        
        // 每個任務的開始和結束時間
        double[] startTime = new double[taskCount];
        double[] finishTime = new double[taskCount];
        
        // 每個處理器的可用時間
        double[] processorAvailableTime = new double[processorCount];
        
        // 使用拓撲排序來計算執行時間
        List<Integer> topologicalOrder = getTopologicalOrder();
        
        for (int taskId : topologicalOrder) {
            int assignedProcessor = individual.getProcessorAssignment(taskId);
            
            // 計算任務的最早開始時間
            double earliestStartTime = processorAvailableTime[assignedProcessor];
            
            // 檢查前置任務的完成時間和通訊時間
            for (int predecessorId : predecessorMap.get(taskId)) {
                int predecessorProcessor = individual.getProcessorAssignment(predecessorId);
                double communicationTime = 0.0;
                
                // 如果前置任務在不同處理器上，需要考慮通訊時間
                if (predecessorProcessor != assignedProcessor) {
                    communicationTime = getCommunicationTime(predecessorId, taskId, 
                                                          predecessorProcessor, assignedProcessor);
                }
                
                earliestStartTime = Math.max(earliestStartTime, 
                                           finishTime[predecessorId] + communicationTime);
            }
            
            startTime[taskId] = earliestStartTime;
            
            // 計算任務的執行時間
            double executionTime = dagData.getCompCost()[taskId][assignedProcessor];
            finishTime[taskId] = startTime[taskId] + executionTime;
            
            // 更新處理器的可用時間
            processorAvailableTime[assignedProcessor] = finishTime[taskId];
        }
        
        // 總執行時間是所有任務完成時間的最大值
        double makespan = 0.0;
        for (double time : finishTime) {
            makespan = Math.max(makespan, time);
        }
        
        return makespan;
    }
    
    /**
     * 計算通訊時間
     */
    private double getCommunicationTime(int fromTask, int toTask, int fromProcessor, int toProcessor) {
        // 尋找對應的邊
        for (DAGReader.TaskEdge edge : dagData.getEdges()) {
            if (edge.from == fromTask && edge.to == toTask) {
                if (edge.dataVolume > 0) {
                    double commRate = dagData.getCommRate()[fromProcessor][toProcessor];
                    return edge.dataVolume / commRate;
                }
                break;
            }
        }
        return 0.0;
    }
    
    /**
     * 獲取拓撲排序順序
     */
    private List<Integer> getTopologicalOrder() {
        List<Integer> result = new ArrayList<>();
        Map<Integer, Integer> inDegree = new HashMap<>();
        
        // 計算每個任務的入度
        for (int i = 0; i < dagData.getTaskCount(); i++) {
            inDegree.put(i, predecessorMap.get(i).size());
        }
        
        // 使用佇列進行拓撲排序
        Queue<Integer> queue = new LinkedList<>();
        for (int i = 0; i < dagData.getTaskCount(); i++) {
            if (inDegree.get(i) == 0) {
                queue.offer(i);
            }
        }
        
        while (!queue.isEmpty()) {
            int current = queue.poll();
            result.add(current);
            
            // 更新後續任務的入度
            for (int successor : successorMap.get(current)) {
                inDegree.put(successor, inDegree.get(successor) - 1);
                if (inDegree.get(successor) == 0) {
                    queue.offer(successor);
                }
            }
        }
        
        return result;
    }
    
    /**
     * 獲取任務的關鍵路徑長度（用於啟發式初始化）
     */
    public double getCriticalPathLength() {
        int taskCount = dagData.getTaskCount();
        double[] upwardRank = new double[taskCount];
        
        // 計算向上排名（從底部開始）
        List<Integer> topologicalOrder = getTopologicalOrder();
        Collections.reverse(topologicalOrder);
        
        for (int taskId : topologicalOrder) {
            double maxSuccessorRank = 0.0;
            
            for (int successorId : successorMap.get(taskId)) {
                double commTime = getMaxCommunicationTime(taskId, successorId);
                maxSuccessorRank = Math.max(maxSuccessorRank, 
                                          upwardRank[successorId] + commTime);
            }
            
            upwardRank[taskId] = getAverageComputationCost(taskId) + maxSuccessorRank;
        }
        
        // 返回入口任務的關鍵路徑長度
        double maxCriticalPath = 0.0;
        for (int i = 0; i < taskCount; i++) {
            if (predecessorMap.get(i).isEmpty()) {
                maxCriticalPath = Math.max(maxCriticalPath, upwardRank[i]);
            }
        }
        
        return maxCriticalPath;
    }
    
    /**
     * 獲取任務的平均計算成本
     */
    private double getAverageComputationCost(int taskId) {
        double sum = 0.0;
        for (int p = 0; p < dagData.getProcessorCount(); p++) {
            sum += dagData.getCompCost()[taskId][p];
        }
        return sum / dagData.getProcessorCount();
    }
    
    /**
     * 獲取最大通訊時間
     */
    private double getMaxCommunicationTime(int fromTask, int toTask) {
        double maxCommTime = 0.0;
        
        for (DAGReader.TaskEdge edge : dagData.getEdges()) {
            if (edge.from == fromTask && edge.to == toTask && edge.dataVolume > 0) {
                for (int p1 = 0; p1 < dagData.getProcessorCount(); p1++) {
                    for (int p2 = 0; p2 < dagData.getProcessorCount(); p2++) {
                        if (p1 != p2) {
                            double commTime = edge.dataVolume / dagData.getCommRate()[p1][p2];
                            maxCommTime = Math.max(maxCommTime, commTime);
                        }
                    }
                }
                break;
            }
        }
        
        return maxCommTime;
    }
} 