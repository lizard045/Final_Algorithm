package ga;

import core.DAG;
import core.Schedule;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * CombinedMutationStrategy類別：實現組合突變策略
 * 對分配部分使用智慧突變，對排序部分使用受控的相鄰交換突變
 */
public class CombinedMutationStrategy implements MutationStrategy {
    private static final double ORDER_MUTATION_PROBABILITY = 0.1; // 整個順序被考慮進行突變的總機率
    private static final double ADJACENT_SWAP_PROBABILITY = 0.05; // 每對可交換的相鄰任務被交換的獨立機率

    @Override
    public void mutate(Schedule schedule, double mutationRate, DAG dag, Random random) {
        // 1. 對分配部分執行智慧突變
        smartMutateAssignments(schedule, mutationRate, dag, random);
        
        // 2. **NEW**: 對排序部分執行受控的相鄰交換突變
        localSwapMutateOrder(schedule, dag, random);
    }
    
    private void smartMutateAssignments(Schedule schedule, double mutationRate, DAG dag, Random random) {
        double[][] oct = dag.getOctCache();
        if (oct == null) {
            // 如果快取不存在，退回到僅基於計算成本的原始智慧突變
            legacySmartMutate(schedule, mutationRate, dag, random);
            return;
        }

        int[] chromosome = schedule.getChromosome();
        for (int i = 0; i < chromosome.length; i++) {
            if (random.nextDouble() < mutationRate) {
                int currentTask = i;
                int currentProc = chromosome[currentTask];
                
                // **PEFT-導向突變**: 尋找 OCT 成本最低的處理器
                double minOctCost = Double.MAX_VALUE;
                int bestProc = currentProc;

                for (int p = 0; p < dag.getProcessorCount(); p++) {
                    if (oct[currentTask][p] < minOctCost) {
                        minOctCost = oct[currentTask][p];
                        bestProc = p;
                    }
                }
                
                if (bestProc != currentProc) {
                    chromosome[currentTask] = bestProc;
                } else {
                    int newProc = random.nextInt(dag.getProcessorCount());
                    while (newProc == currentProc) {
                        newProc = random.nextInt(dag.getProcessorCount());
                    }
                    chromosome[currentTask] = newProc;
                }
            }
        }
    }
    
    // 保留原始的智慧突變作為備用方案
    private void legacySmartMutate(Schedule schedule, double mutationRate, DAG dag, Random random) {
        int[] chromosome = schedule.getChromosome();
        for (int i = 0; i < chromosome.length; i++) {
            if (random.nextDouble() < mutationRate) {
                int currentTask = i;
                int currentProc = chromosome[currentTask];
                double minCost = Double.MAX_VALUE;
                int bestProc = currentProc;
                for (int p = 0; p < dag.getProcessorCount(); p++) {
                    double cost = dag.getTask(currentTask).getComputationCost(p);
                    if (cost < minCost) {
                        minCost = cost;
                        bestProc = p;
                    }
                }
                if (bestProc != currentProc) {
                    chromosome[currentTask] = bestProc;
                } else {
                    int newProc = random.nextInt(dag.getProcessorCount());
                    while (newProc == currentProc) {
                        newProc = random.nextInt(dag.getProcessorCount());
                    }
                    chromosome[currentTask] = newProc;
                }
            }
        }
    }

    /**
     * **NEW**: 執行相鄰交換突變，只交換沒有相互依賴的相鄰任務。
     */
    private void localSwapMutateOrder(Schedule schedule, DAG dag, Random random) {
        if (random.nextDouble() > ORDER_MUTATION_PROBABILITY) {
            return; // 以較大機率跳過整個順序突變，專注於分配突變
        }

        List<Integer> order = schedule.getTaskOrder();
        if (order == null || order.size() < 2) return;
        
        boolean mutated = false;
        // **核心升級**：遍歷整個列表，對每一對可交換的相鄰任務都有機率進行交換
        for (int i = 0; i < order.size() - 1; i++) {
            int task1_id = order.get(i);
            int task2_id = order.get(i + 1);
            
            // 檢查依賴性：如果 task2 不依賴於 task1，則可以安全交換
            if (!dag.isReachable(task1_id, task2_id)) {
                if (random.nextDouble() < ADJACENT_SWAP_PROBABILITY) {
                    Collections.swap(order, i, i + 1);
                    mutated = true;
                }
            }
        }
        
        if (mutated) {
            schedule.setTaskOrder(order); // 如果發生了至少一次交換，才觸發重新評估
        }
    }
} 